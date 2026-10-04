package dev.minifilm.director;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline English draft captions from the selected clip's audio; never opens a microphone. */
public final class ClipTranscriber {
    public interface Listener {
        void onComplete(List<SubtitleCue> cues, long elapsedMs);
        void onError(String message);
    }
    private static final long MODEL_SIZE = 77_704_715L;
    private static final int MAX_SAMPLES = 16_000 * 180;
    private static final boolean NATIVE_AVAILABLE;
    static {
        boolean ready;
        try { System.loadLibrary("minifilm_whisper"); ready = true; }
        catch (UnsatisfiedLinkError e) { ready = false; Log.w("MiniFilmASR", "Offline ASR runtime unavailable", e); }
        NATIVE_AVAILABLE = ready;
    }
    private final Context context;
    private final File model;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean closed;

    public ClipTranscriber(Context context) {
        this.context = context.getApplicationContext();
        model = new File(this.context.getFilesDir(), "models/ggml-tiny.en.bin");
    }
    public boolean isModelAvailable() { return NATIVE_AVAILABLE && model.isFile() && model.length() == MODEL_SIZE; }

    public void transcribe(Uri uri, Listener listener) {
        if (closed) { main.post(() -> listener.onError("Transcriber is closed.")); return; }
        if (!isModelAvailable()) { main.post(() -> listener.onError("Install the verified local tiny.en speech model first.")); return; }
        if (!busy.compareAndSet(false, true)) { main.post(() -> listener.onError("Another clip is being transcribed.")); return; }
        worker.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            try {
                DecodedAudio audio = decode(uri);
                if (closed) return;
                String result = nativeTranscribe(model.getAbsolutePath(), audio.samples);
                if (closed) return;
                JSONObject json = new JSONObject(result);
                if (json.has("error")) throw new IllegalStateException(json.getString("error"));
                JSONArray segments = json.getJSONArray("segments");
                List<SubtitleCue> cues = new ArrayList<>();
                long duration = audio.samples.length * 1000L / 16_000;
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject segment = segments.getJSONObject(i);
                    long start = Math.max(0, Math.min(duration, segment.getLong("startMs")));
                    long end = Math.max(start, Math.min(duration, segment.getLong("endMs")));
                    String text = segment.getString("text").trim();
                    // Codec padding can exceed the container; apply source offset before
                    // capping so every default draft is valid in the clip's review/export UI.
                    long sourceStart = Math.min(audio.containerDurationMs, start + audio.offsetMs);
                    long sourceEnd = Math.min(audio.containerDurationMs, end + audio.offsetMs);
                    if (!text.isEmpty() && sourceEnd > sourceStart)
                        cues.add(new SubtitleCue(sourceStart, sourceEnd, text));
                }
                long elapsed = SystemClock.elapsedRealtime() - started;
                Log.i("MiniFilmASR", "ASR_OK backend=CPU model=tiny.en samples=" + audio.samples.length
                        + " segments=" + cues.size() + " elapsedMs=" + elapsed);
                main.post(() -> { if (!closed) listener.onComplete(cues, elapsed); });
            } catch (Exception e) {
                Log.e("MiniFilmASR", "Clip transcription failed", e);
                main.post(() -> { if (!closed) listener.onError("Transcription failed: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); });
            } finally { busy.set(false); }
        });
    }

    public void close() {
        closed = true;
        if (NATIVE_AVAILABLE) nativeCancel();
        worker.shutdownNow();
    }

    private DecodedAudio decode(Uri uri) throws Exception {
        long containerDurationMs = containerDuration(uri);
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null; boolean decoderStarted = false;
        try {
            extractor.setDataSource(context, uri, null);
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { format = candidate; extractor.selectTrack(i); break; }
            }
            if (format == null) throw new IllegalArgumentException("This clip has no audio track.");
            if (format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 180_000_000L)
                throw new IllegalArgumentException("Transcribe clips up to three minutes long.");
            String mime = format.getString(MediaFormat.KEY_MIME);
            if ("audio/raw".equals(mime)) throw new IllegalArgumentException("Use a video with encoded audio, such as AAC.");
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0); decoder.start(); decoderStarted = true;
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            Resampler samples = new Resampler(sampleRate);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEnded = false, outputEnded = false;
            long offsetMs = -1;
            long deadline = SystemClock.elapsedRealtime() + 120_000;
            while (!outputEnded) {
                if (closed || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Cancelled.");
                if (SystemClock.elapsedRealtime() > deadline) throw new IllegalStateException("Audio decoding timed out.");
                if (!inputEnded) {
                    int input = decoder.dequeueInputBuffer(10_000);
                    if (input >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(input);
                        if (buffer == null) throw new IllegalStateException("Decoder input unavailable.");
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(input, 0, size, extractor.getSampleTime(), 0); extractor.advance();
                        }
                    }
                }
                int output = decoder.dequeueOutputBuffer(info, 10_000);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat decoded = decoder.getOutputFormat();
                    int actualRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding = decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                    if (samples.size != 0 && actualRate != sampleRate) throw new IllegalStateException("Audio sample rate changed mid-clip.");
                    sampleRate = actualRate; samples.setRate(sampleRate);
                } else if (output >= 0) {
                    ByteBuffer buffer = decoder.getOutputBuffer(output);
                    if (buffer != null && info.size > 0) {
                        if (offsetMs < 0) offsetMs = Math.max(0, info.presentationTimeUs / 1000);
                        buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.order(ByteOrder.LITTLE_ENDIAN);
                        int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
                        if (encoding != AudioFormat.ENCODING_PCM_FLOAT && encoding != AudioFormat.ENCODING_PCM_16BIT)
                            throw new IllegalStateException("Unsupported decoder PCM encoding " + encoding);
                        if (channels < 1 || channels > 8) throw new IllegalStateException("Unsupported audio channel count.");
                        while (buffer.remaining() >= bytes * channels) {
                            float mono = 0;
                            for (int c = 0; c < channels; c++) mono += bytes == 4 ? buffer.getFloat() : buffer.getShort() / 32768f;
                            samples.add(Math.max(-1f, Math.min(1f, mono / channels)));
                        }
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(output, false);
                }
            }
            if (samples.size < 1600) throw new IllegalArgumentException("The audio is too short to transcribe.");
            return new DecodedAudio(Arrays.copyOf(samples.data, samples.size), Math.max(0, offsetMs), containerDurationMs);
        } finally {
            if (decoder != null) { if (decoderStarted) try { decoder.stop(); } catch (Exception ignored) {} decoder.release(); }
            extractor.release();
        }
    }

    private long containerDuration(Uri uri) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, uri);
            long durationMs = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (durationMs <= 0) throw new IllegalArgumentException("This clip has no usable duration.");
            if (durationMs > 180_000) throw new IllegalArgumentException("Transcribe clips up to three minutes long.");
            return durationMs;
        } finally { metadata.release(); }
    }

    private static final class DecodedAudio {
        final float[] samples; final long offsetMs, containerDurationMs;
        DecodedAudio(float[] samples, long offsetMs, long containerDurationMs) {
            this.samples = samples; this.offsetMs = offsetMs; this.containerDurationMs = containerDurationMs;
        }
    }
    private static final class Resampler {
        float[] data = new float[16_000 * 10]; int size;
        long inputIndex; double nextOutput, step; float previous;
        Resampler(int rate) { setRate(rate); }
        void setRate(int rate) {
            if (rate < 8000 || rate > 192000) throw new IllegalArgumentException("Unsupported audio sample rate.");
            step = rate / 16000.0;
        }
        void add(float sample) {
            while (nextOutput <= inputIndex) {
                if (size >= MAX_SAMPLES) throw new IllegalArgumentException("Transcribe clips up to three minutes long.");
                if (size == data.length) data = Arrays.copyOf(data, Math.min(MAX_SAMPLES, data.length * 2));
                double mix = inputIndex == 0 ? 1 : nextOutput - (inputIndex - 1);
                data[size++] = previous + (sample - previous) * (float) mix;
                nextOutput += step;
            }
            previous = sample; inputIndex++;
        }
    }
    private static native String nativeTranscribe(String modelPath, float[] samples);
    private static native void nativeCancel();
}
