package dev.minifilm.director;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.AtomicFile;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Small private export journal. Never scans/deletes source takes or imported media. */
public final class ExportRecovery {
    private static final Object LOCK = new Object();
    private static final Set<String> ACTIVE = new HashSet<>();
    private static final int MAX_JOURNALS = 128, MAX_JOURNAL_BYTES = 16_384;
    private static final String PATH = "Movies/MiniFilm/";

    public static final class Result {
        public Uri videoUri, editListUri;
        public int cleaned;
        public String warning = "";
    }

    /** Call at startup; currently active same-process exports are skipped. */
    public static Result reconcile(Context context) {
        synchronized (LOCK) {
            Result result = new Result();
            List<Journal> completed = new ArrayList<>();
            Journal latest = null;
            Set<String> names = journalNames(context);
            if (names.size() > MAX_JOURNALS) {
                result.warning = "Too many export records to check safely. Existing media was preserved.";
                return result;
            }
            for (String id : names) {
                if (ACTIVE.contains(id)) continue;
                try {
                    Journal journal = Journal.read(context, id);
                    Row row = findRow(journal);
                    if (row != null && row.published) {
                        if (!(journal.state.equals("READY") || journal.state.equals("COMPLETE"))
                                || !validPair(journal, row)) {
                            warn(result); continue;
                        }
                        try { journal.complete(); } catch (Exception ignored) { warn(result); }
                        remove(journal.temp());
                        completed.add(journal);
                        if (latest == null || journal.createdMs > latest.createdMs) {
                            latest = journal; result.videoUri = row.uri;
                            result.editListUri = FileProvider.getUriForFile(context,
                                    context.getPackageName() + ".files", journal.edit());
                        }
                    } else if (journal.state.equals("COMPLETE")) {
                        // A creator may have removed their published video. Keep their cut list.
                        remove(journal.temp()); warn(result);
                    } else {
                        if (row != null && context.getContentResolver().delete(row.uri, null, null) != 1)
                            throw new IOException("Pending export could not be removed");
                        remove(journal.temp()); remove(journal.edit()); journal.atomic().delete();
                        result.cleaned++;
                    }
                } catch (Exception ignored) { warn(result); }
            }
            // Retain the newest durable result until MainActivity can save it. Older media stays.
            for (Journal journal : completed) if (journal != latest) journal.atomic().delete();
            return result;
        }
    }

    private static void warn(Result result) {
        result.warning = "Some export records could not be verified. Their media was preserved; you can retry after reopening.";
    }

    static Journal begin(Context context) throws Exception {
        synchronized (LOCK) {
            if (journalNames(context).size() >= MAX_JOURNALS)
                throw new IOException("Too many export records. Reopen the app before exporting again.");
            Journal journal = new Journal(context.getApplicationContext(), UUID.randomUUID().toString());
            journal.write(); // Durable names exist before any output file is created.
            ACTIVE.add(journal.id);
            return journal;
        }
    }

    private static Set<String> journalNames(Context context) {
        Set<String> names = new HashSet<>();
        File[] files = new File(context.getFilesDir(), "export-journal").listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".json.bak")) name = name.substring(0, name.length() - 4);
            if (name.endsWith(".json")) names.add(name.substring(0, name.length() - 5));
            if (names.size() > MAX_JOURNALS) break;
        }
        return names;
    }

    private static Row findRow(Journal journal) throws Exception {
        Uri collection = MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        String[] columns = { MediaStore.Video.Media._ID, MediaStore.Video.Media.OWNER_PACKAGE_NAME,
                MediaStore.Video.Media.IS_PENDING, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH };
        try (Cursor cursor = journal.context.getContentResolver().query(collection, columns,
                MediaStore.Video.Media.DISPLAY_NAME + "=? AND " + MediaStore.Video.Media.RELATIVE_PATH + "=?",
                new String[] { journal.videoName(), PATH }, null)) {
            if (cursor == null) throw new IOException("Export ownership query unavailable");
            if (cursor.getCount() > 1) throw new IOException("Ambiguous export record");
            if (!cursor.moveToFirst()) return null;
            if (!journal.context.getPackageName().equals(cursor.getString(1))
                    || !journal.videoName().equals(cursor.getString(3)) || !PATH.equals(cursor.getString(4)))
                throw new IOException("Export ownership cannot be verified");
            Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
            if (journal.videoUri != null && !uri.equals(journal.videoUri))
                throw new IOException("Export row differs from journal");
            return new Row(uri, cursor.getInt(2) == 0);
        }
    }

    private static boolean validPair(Journal journal, Row row) throws Exception {
        if (journal.expectedBytes < 1000 || !journal.edit().isFile()
                || journal.edit().length() > 1_048_576) return false;
        JSONObject json = new JSONObject(new String(Files.readAllBytes(journal.edit().toPath()), StandardCharsets.UTF_8));
        if (!"minifilm.edit.v1".equals(json.optString("schema")) || !journal.id.equals(json.optString("exportId"))
                || json.optLong("durationMs") <= 0 || json.optJSONArray("cuts") == null) return false;
        try (ParcelFileDescriptor fd = journal.context.getContentResolver().openFileDescriptor(row.uri, "r")) {
            return fd != null && fd.getStatSize() == journal.expectedBytes;
        }
    }

    private static void remove(File file) throws IOException {
        if (file.exists() && !file.delete()) throw new IOException("Export file could not be removed");
    }

    private static final class Row {
        final Uri uri; final boolean published;
        Row(Uri uri, boolean published) { this.uri = uri; this.published = published; }
    }

    static final class Journal {
        final Context context; final String id;
        long createdMs = System.currentTimeMillis(), expectedBytes;
        String state = "ENCODING";
        Uri videoUri;
        private boolean aborted;
        private boolean publishing;

        private Journal(Context context, String id) throws IOException {
            if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                throw new IOException("Untrusted export record name");
            this.context = context; this.id = id;
        }
        File temp() { return new File(context.getCacheDir(), "reel-" + id + ".mp4"); }
        File edit() { return new File(new File(context.getFilesDir(), "exports"), "MiniFilm-" + id + ".json"); }
        String videoName() { return "MiniFilm-" + id + ".mp4"; }
        AtomicFile atomic() { return new AtomicFile(new File(new File(context.getFilesDir(), "export-journal"), id + ".json")); }

        void saving() throws Exception {
            synchronized (LOCK) { transition("SAVING"); publishing = true; }
        }
        void rowInserted(Uri uri) throws Exception {
            synchronized (LOCK) { checkActive(); videoUri = uri; write(); }
        }
        void ready(long bytes) throws Exception {
            synchronized (LOCK) {
                checkActive(); if (bytes < 1000) throw new IOException("Incomplete export output");
                expectedBytes = bytes; state = "READY"; write();
            }
        }
        void complete() throws Exception {
            synchronized (LOCK) { checkActive(); state = "COMPLETE"; write(); ACTIVE.remove(id); }
        }
        private void transition(String next) throws Exception {
            synchronized (LOCK) { checkActive(); state = next; write(); }
        }
        private void checkActive() throws IOException { if (aborted) throw new IOException("Export cancelled"); }

        /** Keep a durable aborted record: a cancelled worker may still be unwinding. */
        void abort() {
            synchronized (LOCK) {
                if (state.equals("COMPLETE")) { ACTIVE.remove(id); return; }
                aborted = true; state = "ABORTED";
                try { write(); } catch (Exception ignored) { }
                if (!publishing) ACTIVE.remove(id);
            }
        }

        void settled() { synchronized (LOCK) { publishing = false; ACTIVE.remove(id); } }

        // Staged interruption fixtures release only the same-process active guard.
        void stageInterruption() { synchronized (LOCK) { ACTIVE.remove(id); } }

        private void write() throws Exception {
            File parent = atomic().getBaseFile().getParentFile();
            if (!parent.exists() && !parent.mkdirs()) throw new IOException("Export journal folder unavailable");
            JSONObject json = new JSONObject().put("schema", 1).put("package", context.getPackageName())
                    .put("id", id).put("createdMs", createdMs).put("state", state).put("expectedBytes", expectedBytes);
            if (videoUri != null) json.put("videoUri", videoUri.toString());
            AtomicFile file = atomic(); FileOutputStream stream = null;
            try {
                stream = file.startWrite(); stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
                file.finishWrite(stream);
            } catch (Exception e) { if (stream != null) file.failWrite(stream); throw e; }
        }

        static Journal read(Context context, String id) throws Exception {
            Journal journal = new Journal(context.getApplicationContext(), id);
            File base = journal.atomic().getBaseFile();
            if (base.length() > MAX_JOURNAL_BYTES || new File(base + ".bak").length() > MAX_JOURNAL_BYTES)
                throw new IOException("Oversized export record");
            byte[] bytes = journal.atomic().readFully();
            if (bytes.length > MAX_JOURNAL_BYTES) throw new IOException("Oversized export record");
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (json.optInt("schema") != 1 || !id.equals(json.optString("id"))
                    || !context.getPackageName().equals(json.optString("package")))
                throw new IOException("Untrusted export record");
            journal.state = json.getString("state");
            if (!journal.state.matches("ENCODING|SAVING|READY|COMPLETE|ABORTED")) throw new IOException("Unknown export state");
            journal.createdMs = json.getLong("createdMs"); journal.expectedBytes = json.getLong("expectedBytes");
            if (json.has("videoUri")) journal.videoUri = Uri.parse(json.getString("videoUri"));
            return journal;
        }
    }
}
