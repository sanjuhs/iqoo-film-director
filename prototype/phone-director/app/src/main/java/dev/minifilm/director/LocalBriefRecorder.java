package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.activity.ComponentActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.function.BooleanSupplier;

/** Explicit foreground voice brief only. Never starts automatically or changes audio routing. */
public final class LocalBriefRecorder implements AutoCloseable {
    public static final long MAX_DURATION_MS = 45_000;
    private static final int MAX_OWNED_FILES = 4;
    private static final String LIMIT_MESSAGE = "45-second limit reached. Record a shorter brief or type your brief.";
    private static final String RELEASE_MESSAGE = "The voice recorder could not be released. Leave this screen and type your brief.";
    // False protects a recorder-owned file. True permits safe caller cleanup after transfer/release.
    private static final Map<File, Boolean> OWNED = new HashMap<>();
    private static final Set<File> ORPHANS = new HashSet<>();
    private static boolean reconciled;
    private static boolean reconciliationFailed;
    public interface Listener {
        void onStarted();
        /** Recorder is stopped/released. Ownership transfers to the caller until ASR has finished. */
        void onReady(File file);
        void onError(String message);
        void onCanceled();
    }
    interface RecorderBackend { void start() throws Exception; void stop() throws Exception; void release(); }
    interface Factory { RecorderBackend create(File output, Runnable error, Runnable limit) throws Exception; }
    private static final class Run {
        File file; RecorderBackend backend; Runnable timeout; boolean started;
    }
    private final Context context;
    private final Factory factory;
    private final BooleanSupplier permitted;
    private final long maxDurationMs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private Run active;
    private final List<Run> unresolved = new ArrayList<>();
    private boolean closed;

    public LocalBriefRecorder(ComponentActivity activity, Listener listener) {
        this(activity.getApplicationContext(), listener, productionFactory(activity.getApplicationContext()),
                () -> activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                        && ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
                MAX_DURATION_MS);
    }

    /** Test seam supplies no microphone backend. Production always uses the foreground/permission guard. */
    LocalBriefRecorder(Context context, Listener listener, Factory factory, BooleanSupplier permitted, long maxDurationMs) {
        if (listener == null || factory == null || permitted == null || maxDurationMs <= 0 || maxDurationMs > MAX_DURATION_MS)
            throw new IllegalArgumentException("Choose a recorder, listener and duration of at most 45 seconds.");
        this.context = context.getApplicationContext(); this.listener = listener; this.factory = factory;
        this.permitted = permitted; this.maxDurationMs = maxDurationMs;
    }

    public boolean start() {
        checkMain();
        if (closed || active != null) return false;
        retryUnresolved();
        if (!unresolved.isEmpty()) { listener.onError(RELEASE_MESSAGE); return false; }
        if (!permitted.getAsBoolean()) { listener.onError("Keep the app open and allow microphone access before recording a brief."); return false; }
        Run run = new Run(); active = run;
        try {
            run.file = reserve(context);
            if (run.file == null) { detach(run); listener.onError("Earlier voice briefs are still finishing. Wait before recording another."); return false; }
            // Always post: a backend error during creation cannot reenter before ownership is assigned.
            run.backend = factory.create(run.file,
                    () -> main.post(() -> fail(run, "The local voice recording stopped unexpectedly. Type your brief or try again.")),
                    () -> main.post(() -> fail(run, LIMIT_MESSAGE)));
            if (run.backend == null) throw new IllegalStateException("No recorder backend");
            run.backend.start(); run.started = true;
        } catch (Exception error) {
            if (active == run) { detach(run); release(run); delete(run); listener.onError(run.backend != null ? RELEASE_MESSAGE : "The local voice brief could not start. Type your brief or try again."); }
            return false;
        }
        run.timeout = () -> fail(run, LIMIT_MESSAGE);
        main.postDelayed(run.timeout, maxDurationMs);
        listener.onStarted();
        return active == run && !closed;
    }

    public boolean isRecording() { checkMain(); return active != null && active.started && !closed; }
    public boolean hasUnreleasedResources() { checkMain(); return !unresolved.isEmpty(); }

    public void finish() {
        checkMain(); Run run = active; if (closed || run == null) return;
        detach(run);
        boolean ok = stopAndRelease(run);
        if (!ok || run.file == null || !run.file.isFile() || run.file.length() == 0) {
            delete(run); listener.onError(run.backend != null ? RELEASE_MESSAGE : "No usable voice brief was saved. Record a little longer or type your brief."); return;
        }
        File ready = run.file; transferable(ready); run.file = null;
        listener.onReady(ready);
    }

    public void cancel() {
        checkMain(); retryUnresolved(); Run run = active; if (closed || run == null) return;
        detach(run); stopAndRelease(run); delete(run);
        if (run.backend != null) listener.onError(RELEASE_MESSAGE); else listener.onCanceled();
    }

    @Override public void close() {
        checkMain(); retryUnresolved(); if (closed) return;
        closed = true; Run run = active;
        if (run != null) { detach(run); stopAndRelease(run); delete(run); }
        listener = null;
    }

    private void fail(Run run, String message) {
        if (closed || active != run) return;
        detach(run); stopAndRelease(run); delete(run);
        listener.onError(run.backend != null ? RELEASE_MESSAGE : message);
    }
    private void detach(Run run) {
        if (active == run) active = null;
        if (run.timeout != null) main.removeCallbacks(run.timeout);
        run.timeout = null;
    }
    private static boolean stopAndRelease(Run run) {
        boolean ok = true;
        try { if (run.started && run.backend != null) run.backend.stop(); }
        catch (Exception ignored) { ok = false; }
        run.started = false;
        return release(run) && ok;
    }
    private static boolean release(Run run) {
        RecorderBackend backend = run.backend;
        if (backend == null) return true;
        try { backend.release(); run.backend = null; return true; } catch (RuntimeException ignored) { return false; }
    }
    private static File reserve(Context context) throws IOException {
        synchronized (OWNED) {
            reconcile(context.getCacheDir());
            for (File orphan : new ArrayList<>(ORPHANS)) discard(orphan);
            if (OWNED.size() >= MAX_OWNED_FILES) return null;
            File file = File.createTempFile("minifilm-brief-", ".m4a", context.getCacheDir());
            OWNED.put(file, false); return file;
        }
    }
    private static void reconcile(File cache) throws IOException {
        if (!reconciled) {
            reconciled = true;
            File[] files;
            try { files = cache.listFiles(); }
            catch (SecurityException denied) { reconciliationFailed = true; throw new IOException("Voice cache could not be reconciled"); }
            if (files == null) reconciliationFailed = true;
            else for (File file : files) {
                // File.createTempFile uses a numeric random component on Android. Reject
                // directories, links and all other cache naming; never rescan a live process.
                if (!isOwnedTempName(file.getName())
                        || !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) || OWNED.containsKey(file)) continue;
                OWNED.put(file, true); ORPHANS.add(file); discard(file);
            }
        }
        if (reconciliationFailed) throw new IOException("Voice cache could not be reconciled");
    }
    private static boolean isOwnedTempName(String name) {
        if (!name.matches("minifilm-brief-(0|[1-9][0-9]{0,18})\\.m4a")) return false;
        try { Long.parseLong(name.substring("minifilm-brief-".length(), name.length() - ".m4a".length())); return true; }
        catch (NumberFormatException invalid) { return false; }
    }
    /** Only for isolated synthetic process-restart tests, before any owned recording exists. */
    static void resetReconciliationForTest() {
        checkMain();
        synchronized (OWNED) {
            if (!OWNED.isEmpty() || !ORPHANS.isEmpty()) throw new IllegalStateException("Owned recordings must be cleaned before reset.");
            reconciled = false; reconciliationFailed = false;
        }
    }
    private static void transferable(File file) { synchronized (OWNED) { if (OWNED.containsKey(file)) OWNED.put(file, true); } }
    /** Call only after the caller's ASR reader has unwound. Unrelated or active files are rejected. */
    static boolean discard(File file) {
        synchronized (OWNED) {
            if (file == null || !Boolean.TRUE.equals(OWNED.get(file))) return false;
            if (file.exists() && !file.delete()) return false;
            OWNED.remove(file); ORPHANS.remove(file); return true;
        }
    }
    private void delete(Run run) {
        if (run.backend != null) {
            if (!unresolved.contains(run)) unresolved.add(run);
            return;
        }
        if (run.file != null) { transferable(run.file); discard(run.file); run.file = null; }
    }
    private void retryUnresolved() {
        for (Run run : new ArrayList<>(unresolved)) {
            if (release(run)) { unresolved.remove(run); delete(run); }
        }
    }
    private static void checkMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Voice recorder operations belong on the main thread.");
    }

    private static Factory productionFactory(Context context) {
        return (output, error, limit) -> {
            MediaRecorder recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            return new RecorderBackend() {
                @Override public void start() throws Exception {
                    recorder.setOnErrorListener((r, what, extra) -> error.run());
                    recorder.setOnInfoListener((r, what, extra) -> { if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) limit.run(); });
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    recorder.setMaxDuration((int) MAX_DURATION_MS);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    recorder.setAudioChannels(1); recorder.setAudioSamplingRate(16_000); recorder.setAudioEncodingBitRate(64_000);
                    recorder.setOutputFile(output.getAbsolutePath()); recorder.prepare(); recorder.start();
                }
                @Override public void stop() { recorder.stop(); }
                @Override public void release() { recorder.release(); }
            };
        };
    }
}
