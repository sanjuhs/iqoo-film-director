package dev.minifilm.director;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

/** Explicit, main-thread selected-clip job. Never changes a Take or opens a microphone.
 * Each URI has a fresh reader, and its idle acknowledgement precedes any next read. */
public final class SubtitleBatch {
    public interface Listener {
        void onProgress(int completed, int total);
        void onDraft(Take target, List<SubtitleCue> cues, long elapsedMs);
        void onComplete(int drafted, int failed, int skipped);
        void onCancelled(int drafted);
        void onError(String message);
    }
    public interface Reader {
        void transcribe(Uri source, ClipTranscriber.Listener listener);
        /** Resource-only acknowledgement, possibly on a worker, only after all read work stops. */
        void closeWhenIdle(Runnable idle);
    }
    public interface ReaderFactory {
        boolean isModelAvailable();
        Reader create();
    }
    private static final int MAX_TAKES = 12, MAX_CUES = 500;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ReaderFactory factory;
    private long generation;
    private boolean closed;
    private Run current;

    public SubtitleBatch(Context context) { this(context, production(context)); }
    SubtitleBatch(Context context, ReaderFactory factory) {
        if (context == null || factory == null) throw new NullPointerException("Context and reader factory are required");
        this.factory = factory;
    }
    private static ReaderFactory production(Context context) {
        Context app = context.getApplicationContext();
        return new ReaderFactory() {
            @Override public boolean isModelAvailable() {
                ClipTranscriber probe = new ClipTranscriber(app);
                try { return probe.isModelAvailable(); } finally { probe.close(); }
            }
            @Override public Reader create() {
                ClipTranscriber reader = new ClipTranscriber(app);
                return new Reader() {
                    @Override public void transcribe(Uri source, ClipTranscriber.Listener listener) { reader.transcribe(source, listener); }
                    @Override public void closeWhenIdle(Runnable idle) { reader.closeWhenIdle(idle); }
                };
            }
        };
    }
    public boolean isModelAvailable() {
        requireMain();
        if (closed) return false;
        try { return factory.isModelAvailable(); } catch (RuntimeException | LinkageError failure) { return false; }
    }
    public boolean isRunning() { requireMain(); return current != null; }

    /** Progress counts unique URI reads; terminal counts count Takes, including skipped inputs. */
    public void start(List<Take> takes, Listener listener) {
        requireMain();
        if (listener == null) throw new NullPointerException("Listener is required");
        if (closed) return;
        if (current != null) { preflight(listener, "Subtitle drafts are already being prepared."); return; }
        generation++;
        if (!isModelAvailable()) { preflight(listener, "Install the verified local speech model first."); return; }
        final Run run;
        try { run = snapshot(takes, listener); }
        catch (IllegalArgumentException failure) { preflight(listener, failure.getMessage()); return; }
        current = run;
        notifyListener(run, () -> listener.onProgress(0, run.groups.size()));
        if (current != run) return;
        if (run.cancelled) finishCancelled(run);
        else beginNext(run);
    }

    public void cancel() {
        requireMain();
        generation++;
        Run run = current;
        if (run == null || closed) return;
        run.cancelled = true;
        if (run.active != null) retire(run, run.active);
        else finishCancelled(run);
    }
    /** Silent destruction; callbacks are suppressed immediately, resources still await real idle. */
    public void close() {
        requireMain();
        if (closed) return;
        closed = true;
        generation++;
        Run run = current;
        if (run == null) return;
        run.cancelled = true;
        if (run.active != null) retire(run, run.active);
        else current = null;
    }

    private void beginNext(Run run) {
        if (current != run || closed) return;
        if (run.cancelled) { finishCancelled(run); return; }
        if (run.next == run.groups.size()) {
            current = null;
            notifyListener(run, () -> run.listener.onComplete(run.drafted, run.failed, run.skipped));
            return;
        }
        Group group = run.groups.get(run.next++);
        run.active = group;
        try {
            group.creating = true;
            try { group.reader = factory.create(); } finally { group.creating = false; }
            if (group.reader == null) throw new IllegalStateException("Reader unavailable");
            if (run.cancelled || closed) { retire(run, group); return; }
            group.reader.transcribe(group.uri, new ClipTranscriber.Listener() {
                @Override public void onComplete(List<SubtitleCue> cues, long elapsedMs) {
                    List<SubtitleCue> copy;
                    try { copy = copiedCues(cues, group.durationMs); }
                    catch (RuntimeException failure) { copy = null; }
                    List<SubtitleCue> result = copy;
                    main.post(() -> result(run, group, result, elapsedMs));
                }
                @Override public void onError(String ignored) { main.post(() -> result(run, group, null, 0)); }
            });
        } catch (RuntimeException | LinkageError failure) {
            if (run.cancelled || closed) retire(run, group);
            else result(run, group, null, 0);
        }
    }

    private void result(Run run, Group group, List<SubtitleCue> cues, long elapsedMs) {
        if (current != run || run.active != group || group.responded || run.cancelled || closed) return;
        group.responded = true;
        group.cues = elapsedMs >= 0 ? cues : null;
        group.elapsedMs = elapsedMs;
        retire(run, group);
    }
    private void retire(Run run, Group group) {
        if (group.creating) return; // A reentrant factory cancellation must first reclaim its returned reader.
        if (group.retiring) return;
        group.retiring = true;
        if (group.reader == null) { main.post(() -> idle(run, group)); return; }
        try { group.reader.closeWhenIdle(() -> main.post(() -> idle(run, group))); }
        catch (RuntimeException | LinkageError failure) {
            // An unacknowledged reader must never be treated as idle or followed by another.
            // Keep this run occupied; close still silences it. A broken injected/provider close
            // cannot establish resource release merely by throwing an exception.
            run.cancelled = true;
        }
    }
    private void idle(Run run, Group group) {
        if (current != run || run.active != group || group.idle) return;
        group.idle = true;
        run.active = null;
        group.reader = null;
        if (closed) { current = null; return; }
        if (run.cancelled) { finishCancelled(run); return; }
        for (Entry entry : group.entries) {
            if (!entry.unchanged()) run.skipped++;
            else if (group.cues == null) run.failed++;
            else if (group.cues.isEmpty()) run.skipped++;
            else {
                run.drafted++;
                List<SubtitleCue> independent = copiedCues(group.cues, entry.durationMs);
                notifyListener(run, () -> run.listener.onDraft(entry.target, independent, group.elapsedMs));
            }
            if (current != run || closed) return;
            if (run.cancelled) { finishCancelled(run); return; }
        }
        run.completed++;
        notifyListener(run, () -> run.listener.onProgress(run.completed, run.groups.size()));
        if (current != run || closed) return;
        if (run.cancelled) finishCancelled(run);
        else beginNext(run);
    }
    private void finishCancelled(Run run) {
        if (current != run || run.active != null) return;
        current = null;
        if (!closed) notifyListener(run, () -> run.listener.onCancelled(run.drafted));
    }
    private void notifyListener(Run run, Runnable callback) {
        if (closed) return;
        try { callback.run(); }
        catch (RuntimeException failure) {
            run.cancelled = true;
            if (current == run) {
                if (run.active != null) retire(run, run.active);
                else finishCancelled(run);
            }
        }
    }
    private void preflight(Listener listener, String message) {
        long expected = generation;
        main.post(() -> { if (!closed && expected == generation) listener.onError(message); });
    }

    private static Run snapshot(List<Take> takes, Listener listener) {
        if (takes == null) throw new IllegalArgumentException("Choose clips for subtitle drafts.");
        Run run = new Run(listener);
        LinkedHashMap<Uri, Group> grouped = new LinkedHashMap<>();
        IdentityHashMap<Take, Boolean> seen = new IdentityHashMap<>();
        int eligible = 0;
        for (Take take : new ArrayList<>(takes)) {
            if (take == null) throw new IllegalArgumentException("Choose valid local clips for subtitle drafts.");
            if (seen.put(take, Boolean.TRUE) != null || !take.selected || hasWords(take.subtitles)) { run.skipped++; continue; }
            validateUri(take.uri);
            if (take.durationMs <= 0 || take.durationMs > 180_000)
                throw new IllegalArgumentException("Each selected missing-subtitle clip must be no longer than three minutes.");
            if (++eligible > MAX_TAKES) throw new IllegalArgumentException("Choose at most twelve missing-subtitle clips at a time.");
            Group group = grouped.get(take.uri);
            if (group == null) { group = new Group(take.uri, take.durationMs); grouped.put(take.uri, group); }
            group.durationMs = Math.min(group.durationMs, take.durationMs);
            group.entries.add(new Entry(take));
        }
        run.groups.addAll(grouped.values());
        return run;
    }
    private static void validateUri(Uri uri) {
        if (uri == null || uri.toString().length() > 2048
                || !("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))
                || "content".equals(uri.getScheme()) && (uri.getAuthority() == null || uri.getAuthority().isEmpty())
                || "file".equals(uri.getScheme()) && (uri.getPath() == null || !uri.getPath().startsWith("/")
                    || uri.getAuthority() != null && !uri.getAuthority().isEmpty()))
            throw new IllegalArgumentException("Choose clips saved on your phone.");
    }
    private static boolean hasWords(List<SubtitleCue> cues) {
        if (cues == null) return false;
        for (SubtitleCue cue : cues) if (cue != null && cue.text != null && !cue.text.trim().isEmpty()) return true;
        return false;
    }
    private static List<SubtitleCue> copiedCues(List<SubtitleCue> cues, long durationMs) {
        if (cues == null || cues.size() > MAX_CUES) throw new IllegalArgumentException("Invalid subtitle draft");
        ArrayList<SubtitleCue> copy = new ArrayList<>();
        long previousEnd = 0;
        for (SubtitleCue cue : cues) {
            if (cue == null || cue.startMs < previousEnd || cue.endMs <= cue.startMs || cue.endMs > durationMs
                    || cue.text == null || cue.text.trim().isEmpty() || cue.text.length() > 16_000)
                throw new IllegalArgumentException("Invalid subtitle draft");
            for (int i = 0; i < cue.text.length(); i++) if (Character.isISOControl(cue.text.charAt(i))
                    && cue.text.charAt(i) != '\n' && cue.text.charAt(i) != '\r' && cue.text.charAt(i) != '\t')
                throw new IllegalArgumentException("Invalid subtitle draft");
            copy.add(new SubtitleCue(cue.startMs, cue.endMs, cue.text));
            previousEnd = cue.endMs;
        }
        return copy;
    }
    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Subtitle batch API requires the main thread");
    }
    private static final class Run {
        final Listener listener;
        final List<Group> groups = new ArrayList<>();
        Group active;
        int next, completed, drafted, failed, skipped;
        boolean cancelled;
        Run(Listener listener) { this.listener = listener; }
    }
    private static final class Group {
        final Uri uri;
        final List<Entry> entries = new ArrayList<>();
        long durationMs, elapsedMs;
        Reader reader;
        List<SubtitleCue> cues;
        boolean responded, retiring, idle, creating;
        Group(Uri uri, long durationMs) { this.uri = uri; this.durationMs = durationMs; }
    }
    private static final class Entry {
        final Take target;
        final Uri uri;
        final long durationMs;
        final List<SubtitleCue> missingIdentity;
        Entry(Take take) { target = take; uri = take.uri; durationMs = take.durationMs; missingIdentity = take.subtitles; }
        boolean unchanged() {
            return target.selected && uri.equals(target.uri) && target.durationMs == durationMs
                    && target.subtitles == missingIdentity && !hasWords(target.subtitles);
        }
    }
}
