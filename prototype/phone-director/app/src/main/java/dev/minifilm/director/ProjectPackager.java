package dev.minifilm.director;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Local portable edit package; no editor import or vendor transfer is implied.
 * Read only creator-selected file/content URIs. Android documents providers can
 * themselves be cloud-backed: the creator should choose local files in the picker.
 * Originals, including their embedded metadata, are copied byte-for-byte.
 * See developer.android.com/training/data-storage/shared/documents-files and
 * ContentResolver.openAssetFileDescriptor. No provider URI is stored in the ZIP.
 */
public final class ProjectPackager implements AutoCloseable {
    public interface Listener {
        void onProgress(long originalBytesCopied);
        /** Caller owns this completed cache file; copy to chosen destination, then delete it. */
        void onComplete(File cacheZip, long totalOriginalBytes);
        void onError(String message);
    }
    public static final long MAX_ORIGINAL_BYTES = 512L * 1024 * 1024;
    private static final long SPACE_RESERVE = 16L * 1024 * 1024;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService cancellationWorker = Executors.newSingleThreadExecutor();
    private final SourceOpener opener;
    private final long byteLimit;
    private Run active;
    private long generation;
    private boolean closed;

    public ProjectPackager(Context context) {
        this(context, (uri, signal) -> {
            AssetFileDescriptor descriptor = context.getContentResolver()
                    .openAssetFileDescriptor(uri, "r", signal);
            if (descriptor == null) throw new IOException("Source unavailable");
            try {
                String mime = "file".equals(uri.getScheme()) ? localFileMime(uri)
                        : context.getContentResolver().getType(uri);
                return new Source(descriptor.createInputStream(), descriptor.getLength(),
                        mime);
            } catch (Exception error) { descriptor.close(); throw error; }
        }, MAX_ORIGINAL_BYTES);
    }

    /** Inspect the actual local container; never infer it from a filename suffix. */
    private static String localFileMime(Uri uri) {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            String path = uri.getPath();
            if (path == null) return null;
            metadata.setDataSource(path);
            return metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
        } catch (RuntimeException unknownContainer) {
            return null;
        } finally {
            try { metadata.release(); } catch (IOException ignored) { }
        }
    }

    // Inject only local synthetic streams in instrumentation; production uses Android URI grants.
    interface SourceOpener { Source open(Uri uri, CancellationSignal signal) throws Exception; }
    static final class Source implements AutoCloseable {
        final InputStream input;
        final long length;
        final String mime;
        private final AtomicBoolean closed = new AtomicBoolean();
        Source(InputStream input, long length, String mime) {
            this.input = input; this.length = length; this.mime = mime;
        }
        @Override public void close() throws IOException {
            // Cancellation and try-with-resources can race; close this stream only once.
            if (closed.compareAndSet(false, true) && input != null) input.close();
        }
    }
    ProjectPackager(Context context, SourceOpener opener, long byteLimit) {
        this.context = context.getApplicationContext(); this.opener = opener;
        if (byteLimit < 1 || byteLimit > MAX_ORIGINAL_BYTES) throw new IllegalArgumentException("Invalid size limit");
        this.byteLimit = byteLimit;
    }

    /** List order is the reviewed timeline order. This snapshots mutable Take/caption fields. */
    public void export(List<Take> takes, String title, String look, Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> export(takes, title, look, listener)); return;
        }
        if (closed) { listener.onError("Package exporter is closed."); return; }
        if (active != null) { listener.onError("A package is already being prepared. Cancel it first."); return; }
        try {
            List<Take> cuts = snapshot(takes);
            String safeTitle = text(title, 120, "Project title");
            String safeLook = text(look, 100, "Look");
            if (context.getCacheDir().getUsableSpace() < SPACE_RESERVE)
                throw new IOException("Free more phone storage before preparing this package.");
            Run run = new Run(++generation, listener);
            run.partial = File.createTempFile("minifilm-pack-", ".partial", context.getCacheDir());
            active = run;
            worker.execute(() -> build(run, cuts, safeTitle, safeLook));
        } catch (Exception error) { listener.onError(message(error)); }
    }

    public void cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::cancel); return; }
        Run run = active;
        if (run == null) return;
        active = null; ++generation; run.cancelled = true;
        Source source = run.activeSource.getAndSet(null);
        cancellationWorker.execute(() -> {
            // Provider reads can block the packing worker. Closing from another thread
            // lets a cooperative provider unblock without waiting on Android's UI.
            // Provider close/cancel itself remains outside our control.
            try { if (source != null) source.close(); } catch (Exception ignored) { }
            finally { try { run.signal.cancel(); } catch (RuntimeException ignored) { } }
        });
        // Only the run's exclusively-created cache file is ever deleted here.
        if (run.partial != null) run.partial.delete();
        run.listener.onError("Package cancelled. Original clips are unchanged.");
    }

    @Override public void close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::close); return; }
        if (closed) return;
        closed = true; cancel(); worker.shutdown(); cancellationWorker.shutdown();
    }

    private void build(Run run, List<Take> cuts, String title, String look) {
        File complete = null;
        try {
            LinkedHashMap<Uri, JSONObject> media = new LinkedHashMap<>();
            long total = 0;
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(run.partial))) {
                // Media is already compressed; low deflate work keeps streaming predictable.
                zip.setLevel(0);
                byte[] block = new byte[64 * 1024];
                for (Take cut : cuts) {
                    check(run);
                    if (media.containsKey(cut.uri)) continue;
                    try (Source source = opener.open(cut.uri, run.signal)) {
                        run.activeSource.set(source);
                        // Cancel may have happened while the provider was opening.
                        // The resource is closed by this block before any read in that case.
                        check(run);
                        if (source.input == null) throw new IOException("Source unavailable");
                        if (source.length > byteLimit - total) throw new LimitException();
                        if (source.length >= 0 && source.length + SPACE_RESERVE
                                > context.getCacheDir().getUsableSpace())
                            throw new IOException("Free more phone storage before preparing this package.");
                        String mime = safeMime(source.mime);
                        String path = String.format(Locale.US, "media/source-%03d%s", media.size() + 1, extension(mime));
                        zip.putNextEntry(new ZipEntry(path));
                        MessageDigest hash = MessageDigest.getInstance("SHA-256");
                        long bytes = 0;
                        int count;
                        while ((count = source.input.read(block)) != -1) {
                            check(run);
                            if (count == 0) continue;
                            if (count > byteLimit - total) throw new LimitException();
                            if (context.getCacheDir().getUsableSpace() < SPACE_RESERVE + count)
                                throw new IOException("Free more phone storage before preparing this package.");
                            zip.write(block, 0, count); hash.update(block, 0, count);
                            bytes += count; total += count;
                            long now = android.os.SystemClock.elapsedRealtime();
                            if (now - run.lastProgress >= 250) {
                                run.lastProgress = now; postProgress(run, total);
                            }
                        }
                        if (bytes == 0) throw new IOException("A selected source is empty. Choose that clip again.");
                        zip.closeEntry();
                        JSONObject entry = new JSONObject();
                        entry.put("path", path); entry.put("mimeType", mime);
                        entry.put("bytes", bytes); entry.put("sha256", hex(hash.digest()));
                        entry.put("provenance", "original bytes from creator-selected local source; metadata retained");
                        media.put(cut.uri, entry);
                    } finally { run.activeSource.set(null); }
                }
                check(run);
                JSONObject project = project(cuts, media, title, look, total);
                writeEntry(zip, "project.json", project.toString(2));
                writeEntry(zip, "README.txt", "Mini Film Director portable edit package — pre-event research\n"
                        + "Unzip locally. project.json uses only relative media paths. SHA-256 covers complete original clips.\n"
                        + "Cuts are in reviewed list order; in/out timestamps are source milliseconds.\n"
                        + "Timed subtitles retain source timing and clipped timeline timing. Manual captions apply to the whole cut\n"
                        + "when no timed subtitles exist. Text/timing/looks remain editable; ASR drafts require creator review.\n"
                        + "This is an interchange description, not an editor project importer or Office Kit integration.\n"
                        + "No multi-camera synchronization is inferred. Full originals can include metadata and footage outside trims.\n");
            }
            check(run);
            complete = new File(run.partial.getParentFile(), run.partial.getName().replace(".partial", ".zip"));
            if (!run.partial.renameTo(complete)) throw new IOException("Could not finish the local package.");
            final File result = complete;
            final long bytes = total;
            main.post(() -> {
                if (!current(run)) { result.delete(); return; }
                active = null;
                run.listener.onProgress(bytes);
                run.listener.onComplete(result, bytes);
            });
        } catch (Exception error) {
            if (complete != null) complete.delete();
            run.partial.delete();
            main.post(() -> {
                if (!current(run)) return;
                active = null;
                run.listener.onError(error instanceof LimitException
                        ? "Selected originals exceed the package size limit (512 MiB). Choose smaller clips."
                        : error instanceof SecurityException
                        ? "Access to a selected clip was revoked. Choose that local clip again."
                        : message(error));
            });
        } finally {
            if (run.cancelled) run.partial.delete();
        }
    }

    private boolean current(Run run) { return active == run && generation == run.generation && !run.cancelled && !closed; }
    private void postProgress(Run run, long bytes) {
        main.post(() -> { if (current(run)) run.listener.onProgress(bytes); });
    }
    private static void check(Run run) throws IOException {
        if (run.cancelled || run.signal.isCanceled()) throw new IOException("Package cancelled.");
    }

    private static List<Take> snapshot(List<Take> takes) {
        if (takes == null) throw new IllegalArgumentException("Choose at least one reviewed cut.");
        List<Take> result = new ArrayList<>(); long duration = 0;
        for (Take take : takes) {
            if (take == null || !take.selected) continue;
            if (take.uri == null || !("file".equals(take.uri.getScheme()) || "content".equals(take.uri.getScheme())))
                throw new IllegalArgumentException("Choose a local video through the phone's file picker.");
            if (take.durationMs <= 0 || take.inMs < 0 || take.outMs > take.durationMs
                    || take.outMs <= take.inMs || take.outMs - take.inMs > 180_000)
                throw new IllegalArgumentException("Review the selected cut's in/out points.");
            Take copy = new Take(take.uri, "", text(take.title, 140, "Cut title"),
                    text(take.caption, 1000, "Caption"), take.durationMs);
            copy.inMs = take.inMs; copy.outMs = take.outMs;
            copy.captionOrigin = text(take.captionOrigin, 100, "Caption provenance");
            if (take.subtitles != null) {
                if (take.subtitles.size() > 500) throw new IllegalArgumentException("Use at most 500 subtitle segments per cut.");
                for (SubtitleCue cue : take.subtitles) {
                    if (cue == null || cue.startMs < 0 || cue.endMs <= cue.startMs || cue.endMs > take.durationMs)
                        throw new IllegalArgumentException("Review subtitle timestamps before packaging.");
                    copy.subtitles.add(new SubtitleCue(cue.startMs, cue.endMs, text(cue.text, 1000, "Subtitle")));
                }
            }
            SubtitleTimeline.requireNonOverlapping(copy.subtitles);
            result.add(copy); duration += copy.outMs - copy.inMs;
            if (result.size() > 12 || duration > 180_000)
                throw new IllegalArgumentException("Choose up to 12 cuts and a timeline of at most 3 minutes.");
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Choose at least one reviewed cut.");
        return result;
    }

    private static JSONObject project(List<Take> cuts, Map<Uri, JSONObject> media,
            String title, String look, long originalBytes) throws Exception {
        JSONObject project = new JSONObject();
        project.put("schema", "minifilm.portable-edit.v1"); project.put("preEventResearch", true);
        project.put("title", title); project.put("look", look);
        project.put("timeUnit", "milliseconds"); project.put("originalBytes", originalBytes);
        project.put("selection", "creator-selected cuts; timestamps and captions remain editable");
        project.put("crop", "center_9_16_review_framing");
        project.put("width", 720); project.put("height", 1280);
        project.put("audio", "sequential original audio; no multi-camera synchronization");
        project.put("captionPolicy", "manual full-cut caption only when timed subtitle list is empty; ASR is an editable draft");
        JSONArray files = new JSONArray(); for (JSONObject item : media.values()) files.put(item);
        project.put("media", files);
        JSONArray list = new JSONArray(); long timeline = 0;
        for (int i = 0; i < cuts.size(); i++) {
            Take cut = cuts.get(i); JSONObject item = new JSONObject();
            item.put("order", i + 1); item.put("mediaPath", media.get(cut.uri).getString("path"));
            item.put("sourceDurationMs", cut.durationMs); item.put("title", cut.title);
            item.put("inMs", cut.inMs); item.put("outMs", cut.outMs);
            item.put("timelineStartMs", timeline); item.put("timelineEndMs", timeline + cut.outMs - cut.inMs);
            item.put("caption", cut.caption); item.put("captionOrigin", cut.captionOrigin);
            JSONArray cues = new JSONArray();
            for (SubtitleCue cue : cut.subtitles) {
                JSONObject entry = new JSONObject();
                entry.put("sourceStartMs", cue.startMs); entry.put("sourceEndMs", cue.endMs); entry.put("text", cue.text);
                long start = Math.max(cut.inMs, cue.startMs), end = Math.min(cut.outMs, cue.endMs);
                entry.put("visibleInCut", end > start);
                if (end > start) {
                    entry.put("timelineStartMs", timeline + start - cut.inMs);
                    entry.put("timelineEndMs", timeline + end - cut.inMs);
                }
                cues.put(entry);
            }
            item.put("subtitles", cues); list.put(item); timeline += cut.outMs - cut.inMs;
        }
        project.put("durationMs", timeline); project.put("cuts", list); return project;
    }

    private static String text(String value, int max, String label) {
        if (value == null) return "";
        if (value.length() > max) throw new IllegalArgumentException(label + " is too long. Shorten it before packaging.");
        return value;
    }
    private static String safeMime(String mime) {
        return mime != null && mime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")
                ? mime.toLowerCase(Locale.US) : "application/octet-stream";
    }
    private static String extension(String mime) {
        switch (mime) {
            case "video/mp4": return ".mp4";
            case "video/quicktime": return ".mov";
            case "video/webm": return ".webm";
            case "video/x-matroska": return ".mkv";
            default: return ".bin";
        }
    }
    private static void writeEntry(ZipOutputStream zip, String path, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(Locale.US, "%02x", value & 255));
        return text.toString();
    }
    private static String message(Exception error) {
        if (error instanceof IllegalArgumentException) return error.getMessage();
        // Never leak private URI/path or provider exception details into UI/logs.
        return "Could not read a selected local clip or write the package. Choose the clip again and check free storage.";
    }
    private static final class LimitException extends IOException {}
    private static final class Run {
        final long generation; final Listener listener;
        final CancellationSignal signal = new CancellationSignal();
        final AtomicReference<Source> activeSource = new AtomicReference<>();
        volatile boolean cancelled;
        File partial; long lastProgress;
        Run(long generation, Listener listener) { this.generation = generation; this.listener = listener; }
    }
}
