package dev.minifilm.director;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * Existing synthetic speech -> production decoder's 16k mono PCM -> real AAC encoder -> Whisper.
 * This proves the requested file-format path, not microphone or MediaRecorder acceptance.
 * No recording, playback, uploads, source rewrites, model download or UI preferences are used.
 */
@RunWith(AndroidJUnit4.class)
public final class VoiceBriefCodecTest {
    private static final int RATE = 16_000, CHANNELS = 1, BIT_RATE = 64_000;

    @Test(timeout = 120_000)
    public void syntheticPcmEncodedAsRequestedAacMono16k64kTranscribesWithinActualContainer() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File source = new File(context.getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Labelled padded offline-TTS fixture is required", source.isFile());
        String originalHash = sha(source);
        long originalBytes = source.length(), originalModified = source.lastModified();
        long originalDuration = duration(context, source);
        assertTrue(originalDuration >= 8546 && originalDuration <= 8946);
        ClipTranscriber transcriber = new ClipTranscriber(context);
        File audio = null;
        boolean workerStopped = false, deleted = false;
        Encoded encoded = null;
        long actualDuration = 0, asrElapsed = 0, decodeElapsed = 0;
        int segments = 0;
        try {
            assertTrue("Existing verified tiny.en runtime/model is required", transcriber.isModelAvailable());
            long decodeStarted = SystemClock.elapsedRealtime();
            Method decode = ClipTranscriber.class.getDeclaredMethod("decode", Uri.class);
            decode.setAccessible(true);
            Object decoded = decode.invoke(transcriber, Uri.fromFile(source));
            float[] pcm = (float[]) field(decoded, "samples");
            decodeElapsed = SystemClock.elapsedRealtime() - decodeStarted;
            assertTrue("Use only this bounded synthetic clip's 16k mono PCM", pcm.length >= RATE && pcm.length <= RATE * 10);
            assertEquals("Decoder must identify the known source container", originalDuration,
                    ((Number) field(decoded, "containerDurationMs")).longValue());
            for (float sample : pcm) assertTrue("Decoder PCM must be finite", Float.isFinite(sample));
            audio = File.createTempFile("synthetic-voice-codec-", ".m4a", context.getCacheDir());
            encoded = encode(pcm, audio);
            assertActualAudioTrack(context, audio);
            actualDuration = duration(context, audio);
            assertTrue("Encoded synthetic fixture stays within ten seconds", actualDuration <= 10_000);
            String encodedHash = sha(audio);
            Log.i("MiniFilmVoiceBriefCodecTest", new JSONObject().put("stage", "encoded_synthetic_fixture")
                    .put("syntheticSource", true).put("encoder", "MediaCodec AAC-LC")
                    .put("sampleRateHz", RATE).put("channels", CHANNELS).put("requestedBitRate", BIT_RATE)
                    .put("encoderReportedBitRate", encoded.reportedBitRate).put("pcmSamples", pcm.length)
                    .put("aacPackets", encoded.packets).put("firstEncoderPtsUs", encoded.firstPtsUs)
                    .put("timestampShiftUs", encoded.shiftUs).put("containerDurationMs", actualDuration)
                    .put("decodeElapsedMs", decodeElapsed).put("encodeElapsedMs", encoded.elapsedMs)
                    .put("microphoneOpened", false).put("mediaRecorderAcceptanceProven", false).toString());
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<List<SubtitleCue>> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            AtomicBoolean mainCallback = new AtomicBoolean();
            AtomicInteger callbacks = new AtomicInteger();
            AtomicLong elapsed = new AtomicLong();
            final Uri input = Uri.fromFile(audio);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> transcriber.transcribe(input,
                    new ClipTranscriber.Listener() {
                        @Override public void onComplete(List<SubtitleCue> cues, long ms) {
                            mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                            callbacks.incrementAndGet(); result.set(cues); elapsed.set(ms); done.countDown();
                        }
                        @Override public void onError(String message) {
                            mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                            callbacks.incrementAndGet(); error.set(message); done.countDown();
                        }
                    }));
            assertTrue("Exact-format offline transcription timed out", done.await(60, TimeUnit.SECONDS));
            assertNull("Actual AAC-LC source must transcribe", error.get());
            assertTrue(mainCallback.get()); assertEquals(1, callbacks.get());
            List<SubtitleCue> cues = result.get(); assertNotNull(cues); assertFalse(cues.isEmpty());
            StringBuilder text = new StringBuilder(); long previousStart = -1;
            for (SubtitleCue cue : cues) {
                assertTrue(cue.startMs >= 0 && cue.startMs >= previousStart);
                assertTrue("ASR drafts must fit the encoded file's measured duration",
                        cue.endMs > cue.startMs && cue.endMs <= actualDuration);
                assertFalse(cue.text.trim().isEmpty()); previousStart = cue.startMs;
                text.append(cue.text).append(' ');
            }
            String recognized = text.toString().toLowerCase(Locale.US);
            for (String known : new String[] {"jacket", "green", "outfit"})
                assertTrue("Missing known synthetic word: " + known, recognized.matches("(?s).*\\b" + known + "\\b.*"));
            assertEquals("ASR must not modify its encoded input", encodedHash, sha(audio));
            asrElapsed = elapsed.get(); segments = cues.size();
        } finally {
            transcriber.close();
            ExecutorService worker = (ExecutorService) field(transcriber, "worker");
            workerStopped = worker.awaitTermination(25, TimeUnit.SECONDS);
            // The synchronous decoder/encoder already released their codecs. A queued ASR callback
            // alone is insufficient: remove the exact owned file only after the actual worker stops.
            if (workerStopped) deleted = audio == null || !audio.exists() || audio.delete();
            assertEquals(originalBytes, source.length()); assertEquals(originalModified, source.lastModified());
            assertEquals("Original synthetic source must remain unchanged", originalHash, sha(source));
            assertTrue("Wait for the real ASR worker before input cleanup", workerStopped);
            assertTrue("Only the test-created AAC file should be removed", deleted);
        }
        Log.i("MiniFilmVoiceBriefCodecTest", new JSONObject().put("stage", "pipeline_pass")
                .put("syntheticSource", true).put("backend", "tiny.en local CPU")
                .put("sampleRateHz", RATE).put("channels", CHANNELS).put("requestedBitRate", BIT_RATE)
                .put("pcmSamples", encoded.inputSamples).put("aacPackets", encoded.packets)
                .put("containerDurationMs", actualDuration).put("segments", segments)
                .put("decodeElapsedMs", decodeElapsed).put("encodeElapsedMs", encoded.elapsedMs).put("asrElapsedMs", asrElapsed)
                .put("knownWordsPresent", true).put("cuesWithinContainer", true).put("mainCallback", true)
                .put("sourceUnchanged", true).put("testFileDeletedAfterWorker", deleted)
                .put("microphoneOpened", false).put("mediaRecorderAcceptanceProven", false).toString());
    }

    private static Encoded encode(float[] pcm, File destination) throws Exception {
        long startedAt = SystemClock.elapsedRealtime();
        MediaCodec codec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        MediaMuxer muxer = null; boolean codecStarted = false, muxerStarted = false;
        try {
            MediaFormat requested = MediaFormat.createAudioFormat("audio/mp4a-latm", RATE, CHANNELS);
            requested.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            requested.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            requested.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            codec.configure(requested, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            muxer = new MediaMuxer(destination.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            codec.start(); codecStarted = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false, outputEnded = false;
            int nextSample = 0, track = -1, packets = 0, reportedBitRate = -1;
            long firstPts = 0, shift = 0, lastPts = Long.MIN_VALUE;
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() - startedAt > 20_000)
                    throw new IllegalStateException("Synthetic AAC encoding timed out");
                if (!inputEnded) {
                    int input = codec.dequeueInputBuffer(10_000);
                    if (input >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(input); assertNotNull(buffer);
                        buffer.clear(); buffer.order(ByteOrder.LITTLE_ENDIAN);
                        long pts = nextSample * 1_000_000L / RATE;
                        if (nextSample == pcm.length) {
                            codec.queueInputBuffer(input, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            int count = Math.min(1024, Math.min(buffer.remaining() / 2, pcm.length - nextSample));
                            assertTrue(count > 0);
                            for (int i = 0; i < count; i++) {
                                float clamped = Math.max(-1f, Math.min(1f, pcm[nextSample + i]));
                                buffer.putShort((short) Math.round(clamped * 32767));
                            }
                            codec.queueInputBuffer(input, 0, count * 2, pts, 0); nextSample += count;
                        }
                    }
                }
                int output = codec.dequeueOutputBuffer(info, 10_000);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    assertFalse("Encoder must publish one output format", muxerStarted);
                    MediaFormat actual = codec.getOutputFormat();
                    assertEquals(RATE, actual.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    assertEquals(CHANNELS, actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    if (actual.containsKey(MediaFormat.KEY_BIT_RATE)) reportedBitRate = actual.getInteger(MediaFormat.KEY_BIT_RATE);
                    track = muxer.addTrack(actual); muxer.start(); muxerStarted = true;
                } else if (output >= 0) {
                    try {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            assertTrue("AAC CSD/output format must precede media packets", muxerStarted);
                            ByteBuffer buffer = codec.getOutputBuffer(output); assertNotNull(buffer);
                            buffer.position(info.offset); buffer.limit(info.offset + info.size);
                            if (packets == 0) { firstPts = info.presentationTimeUs; shift = firstPts < 0 ? Math.negateExact(firstPts) : 0; }
                            long pts = Math.addExact(info.presentationTimeUs, shift);
                            assertTrue("Rebased AAC packets must be chronological and nonnegative", pts >= 0 && pts >= lastPts);
                            MediaCodec.BufferInfo packet = new MediaCodec.BufferInfo();
                            packet.set(info.offset, info.size, pts, info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME);
                            muxer.writeSampleData(track, buffer, packet); lastPts = pts; packets++;
                            assertTrue("Bounded synthetic AAC packet count", packets <= 250);
                        }
                        outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } finally { codec.releaseOutputBuffer(output, false); }
                }
            }
            assertEquals(pcm.length, nextSample); assertTrue(inputEnded && packets > 0 && muxerStarted);
            muxer.stop(); muxerStarted = false;
            return new Encoded(pcm.length, packets, firstPts, shift, reportedBitRate,
                    SystemClock.elapsedRealtime() - startedAt);
        } finally {
            try { if (codecStarted) codec.stop(); }
            finally { try { codec.release(); } finally { if (muxer != null) muxer.release(); } }
        }
    }

    private static void assertActualAudioTrack(Context context, File file) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, Uri.fromFile(file), null);
            assertEquals("Encoded M4A must have one audio track and no video", 1, extractor.getTrackCount());
            MediaFormat actual = extractor.getTrackFormat(0);
            assertEquals("audio/mp4a-latm", actual.getString(MediaFormat.KEY_MIME));
            assertEquals(RATE, actual.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            assertEquals(CHANNELS, actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            ByteBuffer sharedConfig = actual.getByteBuffer("csd-0");
            assertNotNull("Actual AAC decoder configuration is required", sharedConfig);
            // AudioSpecificConfig: 5-bit object type, 4-bit frequency index, then 4-bit
            // channel configuration. Read a duplicate so the format's CSD position is untouched.
            ByteBuffer config = sharedConfig.duplicate(); config.position(0);
            assertTrue("AAC AudioSpecificConfig requires at least two bytes", config.remaining() >= 2);
            int first = config.get() & 255, second = config.get() & 255;
            assertEquals("Encoded AAC object type must actually be LC", 2, first >>> 3);
            assertEquals("Encoded AAC frequency index must actually identify 16kHz", 8,
                    ((first & 7) << 1) | (second >>> 7));
            assertEquals("Encoded AAC channel configuration must actually be mono", 1, (second >>> 3) & 15);
            if (actual.containsKey(MediaFormat.KEY_AAC_PROFILE))
                assertEquals(MediaCodecInfo.CodecProfileLevel.AACObjectLC, actual.getInteger(MediaFormat.KEY_AAC_PROFILE));
            extractor.selectTrack(0);
            assertTrue("An actual encoded packet is required; negative priming PTS is valid",
                    extractor.readSampleData(ByteBuffer.allocate(65_536), 0) > 0);
        } finally { extractor.release(); }
    }

    private static long duration(Context context, File file) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, Uri.fromFile(file));
            long result = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue(result > 0); return result;
        } finally { metadata.release(); }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : digest.digest()) text.append(String.format(Locale.US, "%02x", value & 255));
        return text.toString();
    }

    private static final class Encoded {
        final int inputSamples, packets, reportedBitRate;
        final long firstPtsUs, shiftUs, elapsedMs;
        Encoded(int samples, int packets, long first, long shift, int bitRate, long elapsed) {
            inputSamples = samples; this.packets = packets; firstPtsUs = first; shiftUs = shift;
            reportedBitRate = bitRate; elapsedMs = elapsed;
        }
    }
}
