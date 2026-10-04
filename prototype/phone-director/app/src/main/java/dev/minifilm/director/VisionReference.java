package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** One local frame, not temporal video analysis. Creator reviews all model observations. */
public final class VisionReference implements AutoCloseable {
    public interface Listener {
        void onObservation(String editableObservations, long elapsedMs, String modelLabel);
        void onError(String message);
    }
    public static final String MODEL_LABEL = "Qwen3.5 0.8B + pose landmark heuristic · local CPU · review this frame";
    private static final Semaphore SERIAL = new Semaphore(1);
    private static final boolean RUNTIME_LOADED;
    static {
        boolean loaded = false;
        try { System.loadLibrary("director_llm"); loaded = true; } catch (LinkageError ignored) { }
        RUNTIME_LOADED = loaded;
    }
    private final File modelFile;
    private final File projectorFile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object lifecycle = new Object();
    private boolean closed;
    private Request active;
    private static final class Request {
        final AtomicBoolean cancelled = new AtomicBoolean();
        long handle;
        LocalModelLease.Token modelLease;
    }
    public VisionReference(Context context) {
        modelFile = new File(context.getFilesDir(), "director-model.gguf");
        projectorFile = new File(context.getFilesDir(), "models/director-mmproj.gguf");
    }
    public boolean isModelAvailable() {
        return RUNTIME_LOADED && modelFile.isFile() && modelFile.length() > 10_000_000
                && projectorFile.isFile() && projectorFile.length() > 1_000_000;
    }
    /** Remains true until this job's native resources are freed, including after cancellation. */
    public static boolean isRunning() { return SERIAL.availablePermits() == 0; }
    public void observe(Bitmap frame, Listener listener) {
        if (listener == null) throw new IllegalArgumentException("A result listener is required");
        Request request = new Request();
        synchronized (lifecycle) {
            if (closed) { error(listener, "Frame review is closed."); return; }
            if (!isModelAvailable()) { error(listener, "Local vision files aren't installed. Add your own reference notes."); return; }
            if (active != null || !SERIAL.tryAcquire()) { error(listener, "Local frame review is already working."); return; }
            active = request;
        }
        long began = SystemClock.elapsedRealtime();
        final byte[] rgb;
        final int width;
        final int height;
        Bitmap scaled = null;
        try {
            if (frame == null || frame.isRecycled() || frame.getWidth() <= 0 || frame.getHeight() <= 0)
                throw new IllegalArgumentException("A decoded frame is required");
            float factor = Math.min(1f, 512f / Math.max(frame.getWidth(), frame.getHeight()));
            width = Math.max(1, Math.round(frame.getWidth() * factor));
            height = Math.max(1, Math.round(frame.getHeight() * factor));
            scaled = Bitmap.createScaledBitmap(frame, width, height, true);
            int[] pixels = new int[width * height];
            scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            rgb = new byte[pixels.length * 3];
            for (int i = 0; i < pixels.length; i++) {
                int argb = pixels[i], alpha = argb >>> 24;
                // Composite transparent pixels over black; do not interpret hidden RGB values.
                rgb[i * 3] = (byte) (((argb >>> 16) & 255) * alpha / 255);
                rgb[i * 3 + 1] = (byte) (((argb >>> 8) & 255) * alpha / 255);
                rgb[i * 3 + 2] = (byte) ((argb & 255) * alpha / 255);
            }
        } catch (RuntimeException failure) {
            release(request);
            error(listener, "This frame couldn't be decoded for local review.");
            return;
        } finally { if (scaled != null && scaled != frame) scaled.recycle(); }
        try {
            worker.execute(() -> {
                String result = null, failureMessage = null;
                try {
                    request.modelLease = LocalModelLease.acquire("vision", request.cancelled::get, 5000);
                    FramePoseFraming.Result framing = FramePoseFraming.inspect(rgb, width, height, request.cancelled::get);
                    synchronized (lifecycle) {
                        if (closed || request.cancelled.get()) throw new IllegalStateException("Cancelled");
                        request.handle = nativeCreate();
                    }
                    byte[] output = nativeObserve(request.handle, modelFile.getAbsolutePath(),
                            projectorFile.getAbsolutePath(), rgb, width, height);
                    if (request.cancelled.get()) throw new IllegalStateException("Cancelled");
                    JSONObject object = new JSONObject(new String(output, StandardCharsets.UTF_8));
                    if (object.length() != 2) throw new IllegalArgumentException("Invalid frame schema");
                    result = "Subject: " + field(object, "subject") + "\nFraming: " + framing.label
                            + "\nUncertainty: " + field(object, "uncertainty");
                    Log.i("MiniFilmReferenceAI", "frame_complete backend=cpu width=" + width + " height=" + height
                            + " elapsed_ms=" + (SystemClock.elapsedRealtime() - began)
                            + " framing_source=pose_landmark_heuristic label=" + framing.label);
                } catch (Exception | LinkageError failure) {
                    failureMessage = request.cancelled.get() ? "Frame review cancelled."
                            : "Local vision couldn't review this frame. Add your own reference notes.";
                    Log.w("MiniFilmReferenceAI", "frame_failed type=" + failure.getClass().getSimpleName());
                } finally { release(request); }
                final String observations = result;
                final String error = failureMessage;
                main.post(() -> {
                    synchronized (lifecycle) { if (closed) return; }
                    if (request.cancelled.get()) listener.onError("Frame review cancelled.");
                    else if (error != null) listener.onError(error);
                    else listener.onObservation(observations, SystemClock.elapsedRealtime() - began, MODEL_LABEL);
                });
            });
        } catch (RejectedExecutionException failure) {
            release(request);
            error(listener, "Frame review is closed.");
        }
    }
    private static String field(JSONObject object, String key) throws Exception {
        Object value = object.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException("Expected text");
        String text = ((String) value).trim();
        if (text.isEmpty() || text.length() > 80 || text.contains("<|"))
            throw new IllegalArgumentException("Invalid observation");
        return text;
    }
    private void error(Listener listener, String message) {
        main.post(() -> { synchronized (lifecycle) { if (closed) return; } listener.onError(message); });
    }
    private void release(Request request) {
        synchronized (lifecycle) {
            if (request.handle != 0) { nativeFree(request.handle); request.handle = 0; }
            if (request.modelLease != null) { request.modelLease.close(); request.modelLease = null; }
            if (active == request) { active = null; SERIAL.release(); }
        }
    }
    public void cancel() {
        synchronized (lifecycle) {
            if (active != null) {
                active.cancelled.set(true);
                if (active.handle != 0) nativeCancel(active.handle);
            }
        }
    }
    @Override public void close() {
        synchronized (lifecycle) { closed = true; cancel(); }
        worker.shutdown(); // Worker releases native resources after inference stops.
    }
    private static native long nativeCreate();
    private static native byte[] nativeObserve(long handle, String corePath, String projectorPath, byte[] rgb, int width, int height);
    private static native void nativeCancel(long handle);
    private static native void nativeFree(long handle);
}
