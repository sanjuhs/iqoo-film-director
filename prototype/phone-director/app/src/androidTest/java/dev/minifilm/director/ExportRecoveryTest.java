package dev.minifilm.director;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Deterministic staged interruption states; no live process kill or private capture. */
@RunWith(AndroidJUnit4.class)
public final class ExportRecoveryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 60_000) public void abandonedEncodingCleansOnlyItsTempAndSkipsActiveExport() throws Exception {
        ExportRecovery.Journal journal = ExportRecovery.begin(context);
        File guard = new File(context.getFilesDir(), "recovery-original-" + UUID.randomUUID() + ".bin");
        File untracked = new File(context.getCacheDir(), "reel-untracked-" + UUID.randomUUID() + ".mp4");
        try {
            write(guard, "original source guard".getBytes(StandardCharsets.UTF_8));
            write(untracked, new byte[2048]); write(journal.temp(), new byte[2048]);
            byte[] original = Files.readAllBytes(guard.toPath());
            ExportRecovery.reconcile(context);
            assertTrue("Same-process active export must be skipped", journal.temp().exists());
            journal.stageInterruption();
            ExportRecovery.Result result = ExportRecovery.reconcile(context);
            assertFalse(journal.temp().exists()); assertFalse(journal.atomic().getBaseFile().exists());
            assertTrue(result.cleaned >= 1); assertTrue(untracked.exists());
            assertArrayEquals(original, Files.readAllBytes(guard.toPath()));
            Log.i("MiniFilmRecoveryTest", "ENCODING_STAGE_PASS scopedTempRemoved=true activeSkipped=true untrackedAndOriginalPreserved=true");
        } finally { cleanup(journal, null); guard.delete(); untracked.delete(); }
    }

    @Test(timeout = 120_000) public void interruptedPendingInsertAndReadyCopyAreRemovedWithoutTouchingOtherRows() throws Exception {
        byte[] playable = Files.readAllBytes(generatedSource().toPath());
        Uri unrelated = insert("MiniFilm-unrelated-" + UUID.randomUUID() + ".mp4");
        try (OutputStream out = context.getContentResolver().openOutputStream(unrelated)) { out.write(playable); }
        publish(unrelated);
        try {
            for (boolean copied : new boolean[] { false, true }) {
                ExportRecovery.Journal journal = ExportRecovery.begin(context); Uri row = null;
                try {
                    journal.saving(); write(journal.temp(), new byte[2048]);
                    write(journal.edit(), "unfinished private edit".getBytes(StandardCharsets.UTF_8));
                    row = insert(journal.videoName());
                    try (OutputStream out = context.getContentResolver().openOutputStream(row)) { out.write(new byte[2048]); }
                    if (copied) { journal.rowInserted(row); journal.ready(2048); }
                    // false explicitly stages death between insert() and journal.rowInserted().
                    journal.stageInterruption(); ExportRecovery.Result result = ExportRecovery.reconcile(context);
                    assertTrue(result.cleaned >= 1); assertFalse(exists(row));
                    assertFalse(journal.temp().exists()); assertFalse(journal.edit().exists());
                    assertTrue("Different gallery row must stay", exists(unrelated));
                } finally { cleanup(journal, row); }
            }
            Log.i("MiniFilmRecoveryTest", "PENDING_STAGES_PASS insertGapAndReadyCopy=true ownedPendingOnly=true unrelatedRowPreserved=true");
        } finally { context.getContentResolver().delete(unrelated, null, null); }
    }

    @Test(timeout = 120_000) public void publishedReadyCommitGapRecoversValidPairAndRetainsLatestPointer() throws Exception {
        File source = generatedSource(); ExportRecovery.Journal journal = ExportRecovery.begin(context); Uri row = null;
        try {
            byte[] original = Files.readAllBytes(source.toPath());
            journal.saving(); write(journal.temp(), original); writeProject(journal);
            row = insert(journal.videoName()); journal.rowInserted(row);
            try (OutputStream out = context.getContentResolver().openOutputStream(row)) { out.write(original); }
            journal.ready(original.length); publish(row);
            // Death after IS_PENDING=0 but before COMPLETE/UI state persistence.
            journal.stageInterruption(); ExportRecovery.Result result = ExportRecovery.reconcile(context);
            assertEquals(row, result.videoUri); assertNotNull(result.editListUri);
            assertTrue(exists(row)); assertTrue(journal.edit().exists()); assertFalse(journal.temp().exists());
            assertTrue("Newest pointer must survive until activity persists it", journal.atomic().getBaseFile().exists());
            ExportRecovery.Result again = ExportRecovery.reconcile(context);
            assertEquals(result.videoUri, again.videoUri); assertEquals(result.editListUri, again.editListUri);
            assertArrayEquals("Source preserved", original, Files.readAllBytes(source.toPath()));
            ExportRecovery.Journal persisted = ExportRecovery.Journal.read(context, journal.id);
            assertEquals("COMPLETE", persisted.state);
            Log.i("MiniFilmRecoveryTest", "PUBLISHED_COMMIT_STAGE_PASS validSyntheticPairRecovered=true repeatedStartupPointer=true sourceUnchanged=true");
        } finally { cleanup(journal, row); }
    }

    @Test(timeout = 120_000) public void publishedButUnverifiablePairIsPreservedWithWarning() throws Exception {
        byte[] playable = Files.readAllBytes(generatedSource().toPath());
        ExportRecovery.Journal journal = ExportRecovery.begin(context); Uri row = null;
        try {
            journal.saving(); write(journal.temp(), playable);
            write(journal.edit(), "invalid cut list".getBytes(StandardCharsets.UTF_8));
            row = insert(journal.videoName()); journal.rowInserted(row);
            try (OutputStream out = context.getContentResolver().openOutputStream(row)) { out.write(playable); }
            journal.ready(playable.length); publish(row); journal.stageInterruption();
            ExportRecovery.Result result = ExportRecovery.reconcile(context);
            assertFalse(result.warning.isEmpty()); assertTrue(exists(row));
            assertTrue(journal.edit().exists()); assertTrue(journal.temp().exists());
            assertTrue(journal.atomic().getBaseFile().exists());
            Log.i("MiniFilmRecoveryTest", "AMBIGUOUS_STAGE_PASS publishedUnverifiableMediaPreserved=true warning=true");
        } finally { cleanup(journal, row); }
    }

    @Test(timeout = 60_000) public void malformedAndOversizedRecordsCannotChooseDeletionPaths() throws Exception {
        File directory = new File(context.getFilesDir(), "export-journal"); directory.mkdirs();
        File bad = new File(directory, UUID.randomUUID() + ".json");
        File oversized = new File(directory, UUID.randomUUID() + ".json");
        File guard = new File(context.getFilesDir(), "recovery-source-guard-" + UUID.randomUUID() + ".bin");
        try {
            byte[] original = "preserve this source".getBytes(StandardCharsets.UTF_8); write(guard, original);
            JSONObject forged = new JSONObject().put("schema", 1).put("package", context.getPackageName())
                    .put("id", "../../" + guard.getName()).put("state", "ENCODING")
                    .put("tempPath", guard.getAbsolutePath()).put("editPath", guard.getAbsolutePath());
            write(bad, forged.toString().getBytes(StandardCharsets.UTF_8)); write(oversized, new byte[16_385]);
            ExportRecovery.Result result = ExportRecovery.reconcile(context);
            assertFalse(result.warning.isEmpty()); assertTrue(bad.exists()); assertTrue(oversized.exists());
            assertArrayEquals(original, Files.readAllBytes(guard.toPath()));
            Log.i("MiniFilmRecoveryTest", "UNTRUSTED_STAGE_PASS forgedPathAndOversizedRecordSkipped=true originalPreserved=true");
        } finally { bad.delete(); oversized.delete(); guard.delete(); }
    }

    private File generatedSource() throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<List<Take>> takes = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        DemoAssets.create(context, new DemoAssets.Listener() {
            public void onReady(List<Take> result) { takes.set(result); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS)); assertNull(error.get(), error.get());
        return new File(takes.get().get(0).uri.getPath());
    }

    private void writeProject(ExportRecovery.Journal journal) throws Exception {
        JSONObject project = new JSONObject().put("schema", "minifilm.edit.v1").put("exportId", journal.id)
                .put("durationMs", 3000).put("cuts", new JSONArray().put(new JSONObject().put("shotId", "synthetic")));
        write(journal.edit(), project.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void write(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile(); if (!parent.exists()) assertTrue(parent.mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.getFD().sync(); }
    }
    private Uri insert(String name) {
        ContentValues values = new ContentValues(); values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MiniFilm/"); values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri); return uri;
    }
    private void publish(Uri uri) {
        ContentValues values = new ContentValues(); values.put(MediaStore.Video.Media.IS_PENDING, 0);
        assertEquals(1, context.getContentResolver().update(uri, values, null, null));
    }
    private boolean exists(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(MediaStore.setIncludePending(uri),
                new String[] { MediaStore.Video.Media._ID }, null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }
    private void cleanup(ExportRecovery.Journal journal, Uri uri) {
        journal.stageInterruption(); journal.atomic().delete(); journal.temp().delete(); journal.edit().delete();
        if (uri != null && exists(uri)) context.getContentResolver().delete(uri, null, null);
    }
}
