package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Serial sparse-frame inspection of an explicitly selected local video. No temporal trend claim. */
public final class ReferenceBoardInspection implements AutoCloseable {
    public interface Listener {
        /** Main thread, completed moments numbered 1..total, in requested chronological order. */
        void onProgress(int completed, int total);
        /** Receiver owns the board, including its private thumbnails, and must eventually close it. */
        void onBoard(ReferenceBoard board, long elapsedMs);
        void onError(String message);
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object lifecycle = new Object();
    private long generation;
    private boolean closed;
    private Request active;

    private static final class Request {
        final long generation;
        final Uri uri;
        final long[] times;
        final Listener listener;
        final long began = SystemClock.elapsedRealtime();
        boolean cancelled;
        ReferenceFrameDecoder decoder;
        VisionReference vision;
        Request(long generation, Uri uri, long[] times, Listener listener) {
            this.generation = generation; this.uri = uri; this.times = times; this.listener = listener;
        }
    }
    private static final class Decoded {
        final CountDownLatch ready = new CountDownLatch(1);
        Bitmap frame;
        String error;
        boolean abandoned;
    }
    private static final class Observed {
        final CountDownLatch ready = new CountDownLatch(1);
        String notes, label, error;
    }
    private static final class Cancelled extends Exception { }

    public ReferenceBoardInspection(Context context) {
        if (context == null) throw new IllegalArgumentException("An app context is required.");
        this.context = context.getApplicationContext();
    }
    /** Copies the supplied times. Invalid or busy requests report an error; cancellation reports nothing. */
    public void inspect(Uri uri, long[] requestedTimesMs, Listener listener) {
        if (listener == null) throw new IllegalArgumentException("A reference board listener is required.");
        synchronized (lifecycle) {
            if (closed) return;
            final long[] times;
            try {
                ReferenceBoard.validateUri(uri);
                if (requestedTimesMs == null || requestedTimesMs.length < 1 || requestedTimesMs.length > 3)
                    throw new IllegalArgumentException("Choose one to three reference moments.");
                times = requestedTimesMs.clone();
                long previous = -1;
                for (long time : times) {
                    ReferenceBoard.validateTime(time);
                    if (time <= previous)
                        throw new IllegalArgumentException("Reference moments must be distinct and in time order.");
                    previous = time;
                }
                if (active != null) throw new IllegalArgumentException("Reference inspection is already working.");
            } catch (IllegalArgumentException failure) {
                invalid(listener, failure.getMessage(), generation);
                return;
            }
            Request request = new Request(++generation, uri, times, listener);
            active = request;
            // execute and shutdown are serialized by lifecycle, so no rejected task can retain active ownership.
            worker.execute(() -> run(request));
        }
    }
    public boolean isRunning() { synchronized (lifecycle) { return active != null; } }

    private void run(Request request) {
        ArrayList<ReferenceBoard.FrameDraft> drafts = new ArrayList<>();
        ReferenceBoard board = null;
        String error = null;
        try {
            synchronized (lifecycle) {
                check(request);
                // Fresh engines per batch: cancellation never reaches a later batch's decoder or model.
                request.decoder = new ReferenceFrameDecoder(context);
                request.vision = new VisionReference(context);
            }
            for (int i = 0; i < request.times.length; i++) {
                checkCurrent(request);
                Bitmap frame = decode(request, request.times[i]);
                try {
                    // A cancelled earlier engine may still be freeing its native model. Wait without loading another.
                    awaitPreviousVisionRelease(request);
                    Observed observation = observe(request, frame);
                    checkCurrent(request);
                    drafts.add(new ReferenceBoard.FrameDraft(request.times[i], observation.notes,
                            observation.label, frame));
                } finally { frame.recycle(); }
                progress(request, i + 1);
            }
            checkCurrent(request);
            board = new ReferenceBoard(request.uri, drafts);
        } catch (Cancelled ignored) {
            // Cancel/close intentionally publish neither an error nor a partial board.
        } catch (Exception | LinkageError failure) {
            error = failure instanceof IllegalArgumentException || failure instanceof IllegalStateException
                    ? failure.getMessage() : "This reference could not be inspected. Try fewer moments or add your own notes.";
            if (error == null || error.isEmpty()) error = "This reference could not be inspected. Add your own notes.";
        } finally {
            synchronized (lifecycle) { closeEngines(request); }
            for (ReferenceBoard.FrameDraft draft : drafts) draft.close();
        }
        final ReferenceBoard result = board;
        final String message = error;
        main.post(() -> {
            synchronized (lifecycle) {
                if (!current(request)) { if (result != null) result.close(); return; }
                active = null;
                if (result != null) request.listener.onBoard(result, SystemClock.elapsedRealtime() - request.began);
                else if (message != null) request.listener.onError(message);
            }
        });
    }

    private Bitmap decode(Request request, long time) throws Exception {
        Decoded decoded = new Decoded();
        synchronized (lifecycle) {
            check(request);
            request.decoder.decode(request.uri, time, new ReferenceFrameDecoder.Listener() {
                @Override public void onFrame(Bitmap frame, long requestedMs, long durationMs) {
                    synchronized (lifecycle) {
                        if (current(request) && !decoded.abandoned) decoded.frame = frame;
                        else frame.recycle();
                        decoded.ready.countDown();
                    }
                }
                @Override public void onError(String message) {
                    synchronized (lifecycle) { decoded.error = message; decoded.ready.countDown(); }
                }
            });
        }
        try {
            await(request, decoded.ready, 30_000, "This reference frame took too long to decode.");
            synchronized (lifecycle) {
                check(request);
                if (decoded.error != null) throw new IllegalArgumentException(decoded.error);
                if (decoded.frame == null) throw new IllegalStateException("This reference frame could not be decoded.");
                Bitmap result = decoded.frame; decoded.frame = null;
                return result;
            }
        } finally {
            synchronized (lifecycle) {
                decoded.abandoned = true;
                if (decoded.frame != null) { decoded.frame.recycle(); decoded.frame = null; }
            }
        }
    }

    private Observed observe(Request request, Bitmap frame) throws Exception {
        Observed observed = new Observed();
        synchronized (lifecycle) {
            check(request);
            request.vision.observe(frame, new VisionReference.Listener() {
                @Override public void onObservation(String notes, long elapsedMs, String label) {
                    synchronized (lifecycle) {
                        observed.notes = notes; observed.label = label; observed.ready.countDown();
                    }
                }
                @Override public void onError(String message) {
                    synchronized (lifecycle) { observed.error = message; observed.ready.countDown(); }
                }
            });
        }
        // VisionReference snapshots pixels synchronously and frees its native handle/lease before this callback.
        await(request, observed.ready, 190_000, "Local inspection took too long. Try one reference moment.");
        synchronized (lifecycle) {
            check(request);
            if (observed.error != null) throw new IllegalStateException(observed.error);
            if (observed.notes == null || observed.label == null)
                throw new IllegalStateException("Local inspection returned no frame notes.");
            return observed;
        }
    }
    private void awaitPreviousVisionRelease(Request request) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (VisionReference.isRunning()) {
            checkCurrent(request);
            if (SystemClock.elapsedRealtime() >= deadline)
                throw new IllegalStateException("The previous frame review is still stopping. Try again shortly.");
            // Worker-only timed wait; does not delay UI cancellation or progress delivery.
            new CountDownLatch(1).await(50, TimeUnit.MILLISECONDS);
        }
        checkCurrent(request);
    }
    private void await(Request request, CountDownLatch ready, long timeoutMs, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (!ready.await(50, TimeUnit.MILLISECONDS)) {
            checkCurrent(request);
            if (SystemClock.elapsedRealtime() >= deadline) throw new IllegalStateException(message);
        }
        checkCurrent(request);
    }
    private void progress(Request request, int completed) {
        main.post(() -> {
            synchronized (lifecycle) {
                if (current(request)) request.listener.onProgress(completed, request.times.length);
            }
        });
    }
    private void invalid(Listener listener, String message, long expectedGeneration) {
        main.post(() -> {
            synchronized (lifecycle) { if (!closed && generation == expectedGeneration) listener.onError(message); }
        });
    }
    private boolean current(Request request) {
        return !closed && !request.cancelled && active == request && generation == request.generation;
    }
    private void check(Request request) throws Cancelled { if (!current(request)) throw new Cancelled(); }
    private void checkCurrent(Request request) throws Cancelled { synchronized (lifecycle) { check(request); } }
    private void closeEngines(Request request) {
        if (request.decoder != null) request.decoder.close();
        if (request.vision != null) request.vision.close();
    }
    public void cancel() {
        synchronized (lifecycle) {
            generation++;
            if (active != null) {
                active.cancelled = true;
                closeEngines(active);
                active = null;
            }
        }
    }
    @Override public void close() {
        synchronized (lifecycle) { closed = true; cancel(); worker.shutdown(); }
    }
}
