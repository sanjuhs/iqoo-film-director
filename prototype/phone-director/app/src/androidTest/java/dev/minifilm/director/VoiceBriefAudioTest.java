package dev.minifilm.director;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
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
 * Synthetic offline-TTS AAC remux acceptance only. No microphone, MediaRecorder, capture,
 * playback or upload. Passing proves audio-file transcription, not recorded voice-brief acceptance.
 */
@RunWith(AndroidJUnit4.class)
public final class VoiceBriefAudioTest {
    @Test(timeout = 120_000)
    public void syntheticAudioOnlyAacTranscribesOnMainWithContainerBoundsAndPreservedSource() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File source = new File(context.getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Labelled offline-TTS padded speech fixture is required", source.isFile());
        String originalHash = sha(source);
        long originalBytes = source.length(), originalModified = source.lastModified();
        long sourceDuration = duration(context, source);
        assertTrue("Known padded synthetic source duration", sourceDuration >= 8546 && sourceDuration <= 8946);
        File audio = File.createTempFile("synthetic-voice-brief-", ".m4a", context.getCacheDir());
        ClipTranscriber transcriber = null;
        boolean workerStopped = true;
        boolean remuxDeleted = false;
        long audioDuration = 0, elapsedMs = 0;
        RemuxTiming timing = null;
        int segments = 0, packets = 0;
        try {
            timing = remuxAudio(context, source, audio);
            PacketSummary originalPackets = audioPackets(context, source, false);
            PacketSummary copiedPackets = audioPackets(context, audio, true);
            assertEquals("Remux must preserve every AAC packet", originalPackets.count, copiedPackets.count);
            assertEquals("Remux must preserve encoded AAC bytes", originalPackets.hash, copiedPackets.hash);
            packets = copiedPackets.count;
            String remuxHash = sha(audio);
            audioDuration = duration(context, audio);
            // The standalone container includes retained priming packets whose negative source
            // timestamps are rebased. Its measured duration is not the video's edit-list duration.
            assertTrue("Audio-only fixture must fit the transcriber's three-minute limit", audioDuration <= 180_000);
            Log.i("MiniFilmVoiceBriefAudioTest", new JSONObject().put("stage", "synthetic_remux")
                    .put("syntheticSource", true).put("sourceContainerDurationMs", sourceDuration)
                    .put("audioContainerDurationMs", audioDuration).put("aacPackets", packets)
                    .put("firstSourcePtsUs", timing.firstSourcePtsUs)
                    .put("lastSourcePtsUs", timing.lastSourcePtsUs)
                    .put("timestampShiftUs", timing.timestampShiftUs).toString());
            final long measuredDuration = audioDuration;
            transcriber = new ClipTranscriber(context);
            assertTrue("Existing verified local tiny.en runtime/model is required", transcriber.isModelAvailable());
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<List<SubtitleCue>> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            AtomicBoolean mainCallback = new AtomicBoolean();
            AtomicInteger callbacks = new AtomicInteger();
            AtomicLong elapsed = new AtomicLong();
            ClipTranscriber request = transcriber;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> request.transcribe(Uri.fromFile(audio),
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
            assertTrue("Audio-only transcription timed out", done.await(75, TimeUnit.SECONDS));
            assertNull("Audio-only source must decode/transcribe", error.get());
            assertTrue("Transcription callback must be on main", mainCallback.get());
            assertEquals(1, callbacks.get());
            List<SubtitleCue> cues = result.get();
            assertNotNull(cues); assertFalse("Known synthetic speech must yield draft text", cues.isEmpty());
            StringBuilder text = new StringBuilder();
            long previousStart = -1;
            for (SubtitleCue cue : cues) {
                assertTrue(cue.startMs >= 0 && cue.startMs >= previousStart);
                assertTrue("Draft endpoint must fit the measured audio-only container",
                        cue.endMs > cue.startMs && cue.endMs <= measuredDuration);
                assertFalse(cue.text.trim().isEmpty());
                previousStart = cue.startMs; text.append(cue.text).append(' ');
            }
            String recognized = text.toString().toLowerCase(Locale.US);
            for (String known : new String[] {"jacket", "green", "outfit"}) {
                assertTrue("Missing known synthetic speech word: " + known,
                        recognized.matches("(?s).*\\b" + known + "\\b.*"));
            }
            assertEquals("Transcription must not alter its AAC input", remuxHash, sha(audio));
            elapsedMs = elapsed.get(); segments = cues.size();
        } finally {
            if (transcriber != null) {
                transcriber.close();
                // Completion can be queued before native-request finally cleanup. Await the actual
                // private worker rather than deleting a decoder input merely because a callback ran.
                Field workerField = ClipTranscriber.class.getDeclaredField("worker");
                workerField.setAccessible(true);
                workerStopped = ((ExecutorService) workerField.get(transcriber)).awaitTermination(30, TimeUnit.SECONDS);
            }
            if (workerStopped) remuxDeleted = !audio.exists() || audio.delete();
            assertEquals(originalBytes, source.length());
            assertEquals(originalModified, source.lastModified());
            assertEquals("Original synthetic video must remain byte-for-byte unchanged", originalHash, sha(source));
            assertTrue("Worker must terminate before removing its test-only input", workerStopped);
            assertTrue("Only the owned synthetic M4A should be removed", remuxDeleted);
        }
        Log.i("MiniFilmVoiceBriefAudioTest", new JSONObject()
                .put("syntheticSource", true).put("audioOnlyAacRemux", true)
                .put("backend", "tiny.en local CPU").put("containerDurationMs", audioDuration)
                .put("sourceContainerDurationMs", sourceDuration)
                .put("firstSourcePtsUs", timing.firstSourcePtsUs).put("timestampShiftUs", timing.timestampShiftUs)
                .put("aacPacketsPreserved", packets).put("segments", segments).put("elapsedMs", elapsedMs)
                .put("knownWordsPresent", true).put("cuesWithinContainer", true).put("mainCallback", true)
                .put("sourceUnchanged", true).put("testRemuxDeletedAfterWorker", remuxDeleted)
                .put("microphoneOpened", false).put("mediaRecorderAcceptanceProven", false).toString());
    }

    private static RemuxTiming remuxAudio(Context context, File source, File destination) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(context, Uri.fromFile(source), null);
            MediaFormat format = selectAudio(extractor, false);
            assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME));
            muxer = new MediaMuxer(destination.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outputTrack = muxer.addTrack(format);
            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocate(1_048_576);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long firstTime = 0, lastTime = Long.MIN_VALUE, shift = 0;
            int count = 0;
            while (true) {
                assertTrue("Synthetic packet exceeds the bounded buffer", extractor.getSampleSize() <= buffer.capacity());
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break; // EOF is a read result, not a negative valid priming PTS.
                assertTrue(size > 0); assertTrue(extractor.getSampleTrackIndex() >= 0);
                int flags = extractor.getSampleFlags();
                assertEquals("Synthetic AAC must be unencrypted complete frames", 0,
                        flags & (MediaExtractor.SAMPLE_FLAG_ENCRYPTED | MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME));
                buffer.position(0); buffer.limit(size);
                long time = extractor.getSampleTime();
                assertTrue("AAC samples must remain chronological", time >= lastTime);
                if (count == 0) { firstTime = time; shift = time < 0 ? Math.negateExact(time) : 0; }
                long outputTime = Math.addExact(time, shift);
                assertTrue("Rebased muxer timestamps must be nonnegative", outputTime >= 0);
                info.set(0, size, outputTime, (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                muxer.writeSampleData(outputTrack, buffer, info);
                lastTime = time; count++;
                assertTrue("Bounded synthetic fixture packet count", count <= 2000);
                if (!extractor.advance()) break;
            }
            assertTrue(count > 0);
            // Let the muxer infer sample duration from encoded packet timing. Do not force the
            // video container's edit-list endpoint onto the rebased standalone AAC stream.
            muxer.stop();
            return new RemuxTiming(firstTime, lastTime, shift);
        } finally {
            if (muxer != null) muxer.release();
            extractor.release();
        }
    }

    private static PacketSummary audioPackets(Context context, File source, boolean audioOnly) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, Uri.fromFile(source), null);
            MediaFormat format = selectAudio(extractor, audioOnly);
            assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(1_048_576);
            int count = 0;
            while (true) {
                assertTrue(extractor.getSampleSize() <= buffer.capacity());
                buffer.clear(); int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                assertTrue(extractor.getSampleTrackIndex() >= 0);
                assertTrue(size > 0); buffer.position(0); buffer.limit(size); digest.update(buffer);
                count++; assertTrue(count <= 2000);
                if (!extractor.advance()) break;
            }
            assertTrue(count > 0);
            return new PacketSummary(count, hex(digest.digest()));
        } finally { extractor.release(); }
    }

    private static MediaFormat selectAudio(MediaExtractor extractor, boolean audioOnly) {
        if (audioOnly) assertEquals("Remux must contain exactly one audio track and no video", 1, extractor.getTrackCount());
        MediaFormat audio = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat track = extractor.getTrackFormat(i);
            String mime = track.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                assertNull("Fixture must have one unambiguous audio track", audio);
                audio = track; extractor.selectTrack(i);
            }
        }
        assertNotNull("Fixture requires an encoded audio track", audio);
        return audio;
    }

    private static long duration(Context context, File file) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.fromFile(file));
            long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue(duration > 0); return duration;
        } finally { retriever.release(); }
    }

    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int size; (size = input.read(buffer)) >= 0;) if (size > 0) digest.update(buffer, 0, size);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] digest) {
        StringBuilder text = new StringBuilder();
        for (byte value : digest) text.append(String.format(Locale.US, "%02x", value & 255));
        return text.toString();
    }

    private static final class PacketSummary {
        final int count;
        final String hash;
        PacketSummary(int count, String hash) { this.count = count; this.hash = hash; }
    }

    private static final class RemuxTiming {
        final long firstSourcePtsUs, lastSourcePtsUs, timestampShiftUs;
        RemuxTiming(long first, long last, long shift) {
            firstSourcePtsUs = first; lastSourcePtsUs = last; timestampShiftUs = shift;
        }
    }
}
