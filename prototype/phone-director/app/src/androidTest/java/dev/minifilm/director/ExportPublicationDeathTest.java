package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.util.Log;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Select phases separately. Phase1 NEVER passes normally: the parent must record an actual
 * external force-stop, then Phase2 must run in a fresh process. Real Media3 and real Reel-save
 * write the JSON/gallery copy; ONLY the test's main-queue task waits before publication.
 * This proves a test-controlled READY/pending window, not natural timing or every kill window.
 * No Activity, provider mock, stageInterruption, capture, playback, models or preference writes.
 * Marker: files/publication-death-test/live-ready.json. PREPARING is not permission to kill.
 * Timeout/failure releases the bounded queue barrier and retains generated media for explicit
 * Phase3 review. A new PID is necessary but is not itself external-kill provenance. */
@RunWith(AndroidJUnit4.class)
public final class ExportPublicationDeathTest {
    private static final String SCHEMA = "minifilm.synthetic.publication-death.v1";
    private static final String WINDOW = "test-controlled-main-queue-ready-pending";
    private static final String TITLE = "Synthetic publication-death verification";
    private static final String SOURCE = "fixtures/jacket-speech-padded.mp4";
    private static final String MEDIA_PATH = "Movies/MiniFilm/";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 150_000) public void phase1ArmRealReadyPendingCopyAndAwaitParentForceStop() throws Exception {
        assertUnattended();
        AtomicFile marker = marker();
        assertFalse("Inspect/recover the prior synthetic trial first", marker.getBaseFile().exists());
        assertFalse(new File(marker.getBaseFile() + ".bak").exists());
        JSONObject baseline = snapshotBaseline();
        File original = source(); JSONObject originalState = fixtureSnapshot(original, SOURCE, true);
        File sentinel = new File(context.getCacheDir(), "reel-untracked-publication-death-" + UUID.randomUUID() + ".mp4");
        byte[] guard = new byte[3072]; for (int i = 0; i < guard.length; i++) guard[i] = (byte) (i * 29 + 11);
        try (FileOutputStream output = new FileOutputStream(sentinel)) { output.write(guard); output.getFD().sync(); }
        JSONObject sentinelState = fileSnapshot(sentinel).put("name", sentinel.getName());
        ReelExporter exporter = new ReelExporter(context);
        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch barrierEntered = new CountDownLatch(1), barrierRelease = new CountDownLatch(1), barrierExited = new CountDownLatch(1);
        AtomicBoolean barrierActive = new AtomicBoolean(), sawZero = new AtomicBoolean(), saw95 = new AtomicBoolean();
        AtomicLong barrierDeadlineElapsedMs = new AtomicLong();
        AtomicReference<String> terminal = new AtomicReference<>();
        AtomicReference<ExportRecovery.Journal> owned = new AtomicReference<>();
        AtomicReference<Thread> encoderThread = new AtomicReference<>();
        boolean markerWritten = false;
        try {
            List<Take> cuts = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Take cut = new Take(Uri.fromFile(original), "synthetic-publication-death-" + i,
                        "Synthetic cut " + (i + 1), "Synthetic publication recovery fixture", originalState.getLong("durationMs"));
                cut.outMs = 3000; cuts.add(cut);
            }
            ui(() -> {
                exporter.export(cuts, TITLE, "Clean", new ReelExporter.Listener() {
                    public void onProgress(int percent) {
                        if (percent == 0) sawZero.set(true);
                        if (percent == 95 && saw95.compareAndSet(false, true)) {
                            // Posted AFTER real Transformer completion; ReelExporter starts its
                            // genuine publisher worker after this listener returns. Its eventual
                            // publish runnable therefore queues behind this test-only task.
                            main.post(() -> {
                                barrierDeadlineElapsedMs.set(SystemClock.elapsedRealtime() + 90_000);
                                barrierActive.set(true); barrierEntered.countDown();
                                try { barrierRelease.await(90, TimeUnit.SECONDS); }
                                catch (InterruptedException interruption) { Thread.currentThread().interrupt(); }
                                finally { barrierActive.set(false); barrierExited.countDown(); }
                            });
                        }
                    }
                    public void onComplete(Uri video, Uri edit) { terminal.set("completed"); }
                    public void onError(String message) { terminal.set("export_error"); }
                });
                owned.set((ExportRecovery.Journal) field(exporter, "activeJournal"));
                Object transformer = field(exporter, "transformer");
                if (transformer != null) {
                    // Same fields verified against official Media3 tag1.5.1 in the encoder probe.
                    encoderThread.set((Thread) field(field(transformer, "transformerInternal"), "internalHandlerThread"));
                }
            });
            ExportRecovery.Journal journal = owned.get(); assertNotNull("Production export must own a journal", journal);
            assertJournalSet(baseline, journal.id);
            JSONObject manifest = new JSONObject().put("schema", SCHEMA).put("window", WINDOW)
                    .put("syntheticSource", true).put("phase1Pid", Process.myPid()).put("exportId", journal.id)
                    .put("state", "PREPARING").put("barrierActive", false).put("startZeroObserved", sawZero.get())
                    .put("completion95Observed", false).put("cuts", 12).put("timelineMs", 36000)
                    .put("source", originalState).put("sentinel", sentinelState).put("baseline", baseline);
            durableWrite(marker, manifest); markerWritten = true;
            long deadline = SystemClock.elapsedRealtime() + 45_000;
            ExportRecovery.Journal persisted = null;
            while (SystemClock.elapsedRealtime() < deadline) {
                assertNull("A raced terminal is not pending publication evidence", terminal.get());
                persisted = readJournal(journal.id);
                if (barrierActive.get() && persisted.state.equals("READY")) break;
                SystemClock.sleep(50);
            }
            assertTrue("The actual completed encoder must publish zero then 95", sawZero.get() && saw95.get());
            assertEquals(0, barrierEntered.getCount()); assertTrue(barrierActive.get());
            assertNotNull(persisted); assertEquals("READY", persisted.state); assertNotNull(persisted.videoUri);
            JSONObject edit = ownEditSnapshot(persisted, original);
            JSONObject row = row(journal.id); assertNotNull(row); assertTrue(row.getBoolean("pending"));
            assertEquals(persisted.videoUri.toString(), row.getString("uri"));
            JSONObject temp = fileSnapshot(journal.temp());
            assertTrue(persisted.expectedBytes >= 1000); assertEquals(persisted.expectedBytes, temp.getLong("bytes"));
            assertEquals(persisted.expectedBytes, row.getLong("bytes")); assertEquals(temp.getString("sha256"), row.getString("sha256"));
            assertUnattended(); assertSource(originalState); assertSentinel(sentinelState); assertBaseline(baseline);
            assertJournalSet(baseline, journal.id); assertTrue(barrierActive.get()); assertNull(terminal.get());
            long armedElapsedMs = SystemClock.elapsedRealtime();
            assertTrue("Retain a verifiable external-stop window", barrierDeadlineElapsedMs.get() - armedElapsedMs >= 2000);
            manifest.put("state", "READY").put("barrierActive", true).put("completion95Observed", true)
                    .put("barrierDeadlineElapsedMs", barrierDeadlineElapsedMs.get()).put("markerArmedElapsedMs", armedElapsedMs)
                    .put("journal", journalJson(journal.id)).put("row", row).put("edit", edit).put("temp", temp);
            durableWrite(marker, manifest);
            Log.i("MiniFilmPublicationDeathTest", "LIVE_PUBLICATION_READY syntheticSource=true testControlledQueueWindow=true"
                    + " actualEncoderCompleted=true actualPublisherCopy=true stateReady=true pending=true barrierActive=true"
                    + " startZeroObserved=true completion95Observed=true bytes=" + persisted.expectedBytes
                    + " cuts=12 timelineMs=36000 parentForceStopRequired=true");
            long armedAt = SystemClock.elapsedRealtime();
            while (SystemClock.elapsedRealtime() - armedAt < 45_000) {
                assertTrue("Queue pause expired; do not claim interrupted pending publication", barrierActive.get());
                assertNull(terminal.get()); assertUnattended();
                assertEquals("READY", readJournal(journal.id).state);
                SystemClock.sleep(100);
            }
            fail("Phase1 requires externally documented force-stop; a normal timeout is never a pass");
        } finally {
            // External force-stop skips this finally. All normal exits release the test-only
            // main task; raced successful/pending outputs remain for explicit reviewed cleanup.
            barrierRelease.countDown();
            if (barrierEntered.getCount() == 0) assertTrue(barrierExited.await(10, TimeUnit.SECONDS));
            ui(() -> { if (field(exporter, "transformer") != null) exporter.cancel(); });
            Thread encoder = encoderThread.get();
            if (encoder != null) { encoder.join(10_000); assertFalse("Encoder must release before normal test exit", encoder.isAlive()); }
            assertSource(originalState); assertSentinel(sentinelState); assertBaseline(baseline); assertUnattended();
            if (!markerWritten && owned.get() == null) assertTrue(sentinel.delete());
        }
    }

    @Test(timeout = 60_000) public void phase2RecoverKilledReadyPendingPublication() throws Exception {
        assertUnattended(); JSONObject manifest = readManifest(true);
        assertFreshProcess(manifest); assertTrue(manifest.getBoolean("barrierActive"));
        assertTrue(manifest.getBoolean("startZeroObserved")); assertTrue(manifest.getBoolean("completion95Observed"));
        assertTrue(manifest.getLong("markerArmedElapsedMs") > 0);
        long barrierWindowRemaining = manifest.getLong("barrierDeadlineElapsedMs") - manifest.getLong("markerArmedElapsedMs");
        assertTrue(barrierWindowRemaining >= 2000 && barrierWindowRemaining <= 90_000);
        String id = manifest.getString("exportId"); JSONObject baseline = manifest.getJSONObject("baseline");
        assertSource(manifest.getJSONObject("source")); assertSentinel(manifest.getJSONObject("sentinel"));
        assertBaseline(baseline); assertJournalSet(baseline, id); assertNoActiveExports();
        ExportRecovery.Journal journal = readJournal(id);
        assertEquals("Only actual READY pending may pass", "READY", journal.state);
        assertJsonFields(manifest.getJSONObject("journal"), journalJson(id));
        assertJsonFields(manifest.getJSONObject("edit"), ownEditSnapshot(journal, source()));
        JSONObject actualRow = row(id); assertNotNull(actualRow); assertTrue("Raced publication is not a pass", actualRow.getBoolean("pending"));
        assertJsonFields(manifest.getJSONObject("row"), actualRow);
        assertJsonFields(manifest.getJSONObject("temp"), fileSnapshot(journal.temp()));
        assertEquals(journal.expectedBytes, actualRow.getLong("bytes"));
        ExportRecovery.Result recovered = ExportRecovery.reconcile(context);
        assertEquals("Clean exactly the actual interrupted pending copy", 1, recovered.cleaned); assertTrue(recovered.warning.isEmpty());
        assertNull(row(id)); assertFalse(journal.temp().exists()); assertFalse(journal.edit().exists());
        assertFalse(journal.atomic().getBaseFile().exists()); assertFalse(new File(journal.atomic().getBaseFile() + ".bak").exists());
        assertSource(manifest.getJSONObject("source")); assertSentinel(manifest.getJSONObject("sentinel")); assertBaseline(baseline);
        assertRecoveredBaseline(baseline, recovered); assertJournalSet(baseline, null); assertUnattended();
        Log.i("MiniFilmPublicationDeathTest", "POST_KILL_PUBLICATION_RECOVERY_PASS syntheticSource=true freshProcess=true"
                + " testControlledQueueWindow=true exactReadyPendingPairRemoved=true cleaned=1 originalHashSizeMtimePreserved=true"
                + " sentinelPreserved=true baselineCompletePairPreserved=" + (baseline.length() > 0)
                + " externalKillProvenanceRequired=true naturalWindowTimingProven=false");
        removeOwnMarkerAndSentinel(manifest);
    }

    /** Restricted trial cleanup after a missed window/normal failure, NOT recovery evidence. */
    @Test(timeout = 60_000) public void phase3CleanupReviewedRacedSyntheticPublicationFixture() throws Exception {
        assertUnattended(); JSONObject manifest = readManifest(false); assertFreshProcess(manifest); assertNoActiveExports();
        String id = manifest.getString("exportId"); JSONObject baseline = manifest.getJSONObject("baseline");
        assertSource(manifest.getJSONObject("source")); assertSentinel(manifest.getJSONObject("sentinel"));
        assertBaseline(baseline); assertJournalSet(baseline, id);
        ExportRecovery.Journal journal = readJournal(id);
        assertTrue(journal.state.matches("ENCODING|SAVING|READY|COMPLETE|ABORTED")); String reviewedState = journal.state;
        boolean verifiedEdit = false;
        if (journal.edit().exists()) { ownEditSnapshot(journal, source()); verifiedEdit = true; }
        JSONObject ownedRow = rowMetadata(id);
        if (ownedRow != null) {
            assertTrue("Only fully verified synthetic cut lists authorize row cleanup", verifiedEdit);
            if (journal.videoUri != null) assertEquals(journal.videoUri.toString(), ownedRow.getString("uri"));
            ownedRow = row(id); assertNotNull(ownedRow);
            if (!ownedRow.getBoolean("pending")) {
                assertTrue(journal.state.equals("READY") || journal.state.equals("COMPLETE"));
                assertTrue(journal.expectedBytes >= 1000); assertEquals(journal.expectedBytes, ownedRow.getLong("bytes"));
            } else if (journal.expectedBytes > 0) assertTrue(ownedRow.getLong("bytes") <= journal.expectedBytes);
            assertEquals(1, context.getContentResolver().delete(Uri.parse(ownedRow.getString("uri")), null, null));
        }
        assertNull(rowMetadata(id));
        if (journal.temp().exists()) assertTrue(journal.temp().delete());
        if (journal.edit().exists()) assertTrue(journal.edit().delete());
        journal.atomic().delete(); assertFalse(journal.atomic().getBaseFile().exists());
        assertFalse(new File(journal.atomic().getBaseFile() + ".bak").exists());
        assertSource(manifest.getJSONObject("source")); assertSentinel(manifest.getJSONObject("sentinel")); assertBaseline(baseline);
        assertJournalSet(baseline, null); assertUnattended(); removeOwnMarkerAndSentinel(manifest);
        Log.i("MiniFilmPublicationDeathTest", "REVIEWED_PUBLICATION_TRIAL_CLEANUP syntheticSource=true reviewedState=" + reviewedState
                + " exactOwnedRowRemoved=" + (ownedRow != null) + " baselineAndOriginalPreserved=true processDeathRecoveryProven=false");
    }

    private JSONObject snapshotBaseline() throws Exception {
        Set<String> ids = journalNames(); assertTrue("Refuse additional or unfinished baselines", ids.size() <= 1);
        if (ids.isEmpty()) return new JSONObject();
        String id = ids.iterator().next(); ExportRecovery.Journal journal = readJournal(id);
        assertEquals("COMPLETE", journal.state);
        assertFalse("Baseline must have no unexplained completed temporary output", journal.temp().exists());
        // Validate all cut-list sources BEFORE reading any gallery media bytes.
        JSONObject edit = baselineEditSnapshot(journal);
        JSONObject row = row(id); assertNotNull(row); assertFalse(row.getBoolean("pending"));
        assertNotNull(journal.videoUri); assertEquals(journal.videoUri.toString(), row.getString("uri"));
        assertTrue(journal.expectedBytes >= 1000); assertEquals(journal.expectedBytes, row.getLong("bytes"));
        return new JSONObject().put("journal", journalJson(id)).put("edit", edit).put("row", row);
    }

    private void assertBaseline(JSONObject baseline) throws Exception {
        if (baseline.length() == 0) return;
        String id = baseline.getJSONObject("journal").getString("id");
        assertTrue(id.matches(UUID_PATTERN)); ExportRecovery.Journal journal = readJournal(id);
        assertEquals("COMPLETE", journal.state);
        assertJsonFields(baseline.getJSONObject("journal"), journalJson(id));
        assertJsonFields(baseline.getJSONObject("edit"), baselineEditSnapshot(journal));
        JSONObject actualRow = row(id); assertNotNull(actualRow); assertJsonFields(baseline.getJSONObject("row"), actualRow);
    }

    private JSONObject baselineEditSnapshot(ExportRecovery.Journal journal) throws Exception {
        JSONObject project = editProject(journal); JSONArray cuts = project.getJSONArray("cuts");
        assertTrue(project.getBoolean("preEventResearch")); assertTrue(cuts.length() > 0 && cuts.length() <= 12);
        assertTrue(project.getLong("durationMs") > 0 && project.getLong("durationMs") <= 180_000);
        JSONArray sources = new JSONArray(); Set<String> seen = new HashSet<>();
        for (int i = 0; i < cuts.length(); i++) {
            Uri uri = Uri.parse(cuts.getJSONObject(i).getString("sourceUri"));
            assertEquals("Only labelled synthetic file sources may be read", "file", uri.getScheme());
            assertTrue(uri.getAuthority() == null || uri.getAuthority().isEmpty()); assertNotNull(uri.getPath());
            File file = new File(uri.getPath()); String relative = syntheticRelative(file);
            if (seen.add(relative)) sources.put(fixtureSnapshot(file, relative, false));
        }
        // sources are encoded canonically as text to retain exact file hashes/size/mtime while
        // keeping the integer-only flat snapshot comparison helper unambiguous.
        return fileSnapshot(journal.edit()).put("sources", sources.toString());
    }

    private String syntheticRelative(File file) throws Exception {
        String[] allowed = {"fixtures/jacket-speech.mp4", SOURCE, "synthetic-demo/synthetic-1.mp4",
                "synthetic-demo/synthetic-2.mp4", "synthetic-demo/synthetic-3.mp4"};
        File canonicalRoot = context.getFilesDir().getCanonicalFile();
        // Android legitimately aliases its app-data ROOT (/data/user/0 <-> /data/data).
        // Normalize only that trusted root, never an arbitrary input or child path. Every
        // accepted spelling must resolve to this Context's actual app-files directory.
        File[] roots = {context.getFilesDir().getAbsoluteFile(), canonicalRoot,
                new File("/data/data/" + context.getPackageName() + "/files"),
                new File("/data/user/0/" + context.getPackageName() + "/files")};
        for (File root : roots) {
            if (!root.getCanonicalFile().equals(canonicalRoot)) continue;
            for (String relative : allowed) {
                File spelled = new File(root, relative).getAbsoluteFile();
                if (file.getAbsoluteFile().equals(spelled)) {
                    File expected = new File(canonicalRoot, relative).getAbsoluteFile();
                    assertEquals("No child-file/subdirectory symlink may redirect synthetic reads",
                            expected, spelled.getCanonicalFile());
                    return relative;
                }
            }
        }
        throw new AssertionError("Refuse unknown/private baseline sources");
    }

    private JSONObject ownEditSnapshot(ExportRecovery.Journal journal, File original) throws Exception {
        JSONObject project = editProject(journal);
        assertTrue(project.getBoolean("preEventResearch")); assertEquals(TITLE, project.getString("title"));
        assertEquals("Clean", project.getString("look")); assertEquals(720, project.getInt("width")); assertEquals(1280, project.getInt("height"));
        assertEquals(36000, project.getLong("durationMs")); JSONArray cuts = project.getJSONArray("cuts"); assertEquals(12, cuts.length());
        for (int i = 0; i < cuts.length(); i++) {
            JSONObject cut = cuts.getJSONObject(i); assertEquals("synthetic-publication-death-" + i, cut.getString("shotId"));
            assertEquals(Uri.fromFile(original).toString(), cut.getString("sourceUri"));
            assertEquals("Synthetic cut " + (i + 1), cut.getString("title"));
            assertEquals("Synthetic publication recovery fixture", cut.getString("caption")); assertEquals("manual", cut.getString("captionOrigin"));
            assertEquals(0, cut.getLong("inMs")); assertEquals(3000, cut.getLong("outMs")); assertEquals(i * 3000L, cut.getLong("timelineStartMs"));
            assertEquals(0, cut.getJSONArray("subtitles").length());
        }
        return fileSnapshot(journal.edit());
    }

    private JSONObject editProject(ExportRecovery.Journal journal) throws Exception {
        File edit = journal.edit(); assertTrue(edit.isFile() && edit.length() > 0 && edit.length() <= 1_048_576);
        JSONObject project = new JSONObject(new String(Files.readAllBytes(edit.toPath()), StandardCharsets.UTF_8));
        assertEquals("minifilm.edit.v1", project.getString("schema")); assertEquals(journal.id, project.getString("exportId")); return project;
    }

    private JSONObject rowMetadata(String id) throws Exception {
        assertTrue(id.matches(UUID_PATTERN)); String name = "MiniFilm-" + id + ".mp4";
        try (Cursor cursor = context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.OWNER_PACKAGE_NAME, MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.IS_PENDING},
                MediaStore.Video.Media.DISPLAY_NAME + "=? AND " + MediaStore.Video.Media.RELATIVE_PATH + "=?", new String[]{name, MEDIA_PATH}, null)) {
            assertNotNull(cursor); assertTrue("Exact row must be unambiguous", cursor.getCount() <= 1); if (!cursor.moveToFirst()) return null;
            assertEquals(context.getPackageName(), cursor.getString(1)); assertEquals(name, cursor.getString(2)); assertEquals(MEDIA_PATH, cursor.getString(3));
            assertFalse(cursor.isNull(4));
            return new JSONObject().put("uri", ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0)).toString())
                    .put("owner", cursor.getString(1)).put("name", name).put("path", MEDIA_PATH).put("pending", cursor.getInt(4) != 0);
        }
    }

    private JSONObject row(String id) throws Exception {
        JSONObject result = rowMetadata(id); if (result == null) return null;
        long statBytes;
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(Uri.parse(result.getString("uri")), "r")) {
            assertNotNull(descriptor); statBytes = descriptor.getStatSize(); assertTrue("Real owned row length must be known", statBytes >= 0);
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long bytes = 0;
        try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(result.getString("uri")))) {
            assertNotNull(input); byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) if (count > 0) {
                bytes += count; assertTrue("Synthetic gallery read budget", bytes <= 64L * 1024 * 1024); digest.update(buffer, 0, count);
            }
        }
        assertEquals("Independent FD length and complete synthetic stream must agree", statBytes, bytes);
        return result.put("bytes", bytes).put("statBytes", statBytes).put("sha256", hex(digest.digest()));
    }

    private JSONObject fixtureSnapshot(File file, String relative, boolean padded) throws Exception {
        assertEquals(relative, syntheticRelative(file)); assertTrue(file.isFile() && file.length() > 1000 && file.length() <= 4 * 1024 * 1024);
        MediaMetadataRetriever metadata = new MediaMetadataRetriever(); long duration;
        try { metadata.setDataSource(context, Uri.fromFile(file)); assertNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
            duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
        assertTrue(duration > 0 && duration <= 180_000); if (padded) assertTrue(duration >= 8546 && duration <= 8946);
        return fileSnapshot(file).put("fixture", relative).put("durationMs", duration);
    }

    private void assertSource(JSONObject expected) throws Exception { assertEquals(SOURCE, expected.getString("fixture")); assertJsonFields(expected, fixtureSnapshot(source(), SOURCE, true)); }
    private static JSONObject fileSnapshot(File file) throws Exception {
        assertTrue(file.isFile()); assertTrue("Bound synthetic file reads", file.length() >= 0 && file.length() <= 64L * 1024 * 1024);
        return new JSONObject().put("bytes", file.length()).put("modifiedMs", file.lastModified()).put("sha256", sha(file));
    }
    private void assertSentinel(JSONObject expected) throws Exception {
        String name = expected.getString("name"); assertTrue(name.matches("reel-untracked-publication-death-" + UUID_PATTERN + "\\.mp4"));
        assertJsonFields(expected, fileSnapshot(new File(context.getCacheDir(), name)).put("name", name));
    }
    private ExportRecovery.Journal readJournal(String id) throws Exception {
        // AtomicFile itself has no cross-thread lock. Never inspect/recover its .new file
        // while the real publisher is in startWrite/finishWrite under the production lock.
        synchronized (field(ExportRecovery.class, "LOCK")) { return ExportRecovery.Journal.read(context, id); }
    }
    private JSONObject journalJson(String id) throws Exception {
        synchronized (field(ExportRecovery.class, "LOCK")) {
            ExportRecovery.Journal journal = ExportRecovery.Journal.read(context, id);
            return new JSONObject(new String(journal.atomic().readFully(), StandardCharsets.UTF_8));
        }
    }
    private void assertJournalSet(JSONObject baseline, String own) {
        Set<String> allowed = new HashSet<>(); if (baseline.length() > 0) allowed.add(baseline.optJSONObject("journal").optString("id"));
        if (own != null) allowed.add(own); assertEquals("No unrelated journals may enter recovery", allowed, journalNames());
    }
    private Set<String> journalNames() {
        Set<String> ids = new HashSet<>(); File[] files = new File(context.getFilesDir(), "export-journal").listFiles();
        if (files != null) for (File file : files) { String name = file.getName();
            if (name.endsWith(".bak")) name = name.substring(0, name.length() - 4);
            assertTrue("Refuse unexpected journal artifacts", name.endsWith(".json"));
            String id = name.substring(0, name.length() - 5); assertTrue(id.matches(UUID_PATTERN)); ids.add(id);
        }
        assertTrue(ids.size() <= 2); return ids;
    }
    private JSONObject readManifest(boolean ready) throws Exception {
        AtomicFile marker = marker(); assertTrue(marker.getBaseFile().isFile()); assertTrue(marker.getBaseFile().length() > 0 && marker.getBaseFile().length() <= 16_384);
        JSONObject result = new JSONObject(new String(marker.readFully(), StandardCharsets.UTF_8));
        assertEquals(SCHEMA, result.getString("schema")); assertEquals(WINDOW, result.getString("window")); assertTrue(result.getBoolean("syntheticSource"));
        assertTrue(result.getString("exportId").matches(UUID_PATTERN)); assertEquals(12, result.getInt("cuts")); assertEquals(36000, result.getLong("timelineMs"));
        if (ready) assertEquals("READY", result.getString("state")); else assertTrue(result.getString("state").matches("PREPARING|READY"));
        return result;
    }
    private static void assertFreshProcess(JSONObject manifest) throws Exception { assertTrue("Old process must be gone before reconciliation/cleanup", manifest.getInt("phase1Pid") != Process.myPid()); }
    private static void assertNoActiveExports() { assertTrue(((Set<?>) field(ExportRecovery.class, "ACTIVE")).isEmpty()); }
    private void assertRecoveredBaseline(JSONObject baseline, ExportRecovery.Result result) throws Exception {
        if (baseline.length() == 0) { assertNull(result.videoUri); assertNull(result.editListUri); return; }
        assertEquals(Uri.parse(baseline.getJSONObject("row").getString("uri")), result.videoUri);
        ExportRecovery.Journal journal = readJournal(baseline.getJSONObject("journal").getString("id"));
        assertEquals(FileProvider.getUriForFile(context, context.getPackageName() + ".files", journal.edit()), result.editListUri);
    }
    private void removeOwnMarkerAndSentinel(JSONObject manifest) throws Exception {
        assertSentinel(manifest.getJSONObject("sentinel")); assertTrue(new File(context.getCacheDir(), manifest.getJSONObject("sentinel").getString("name")).delete());
        marker().delete(); assertFalse(marker().getBaseFile().exists()); assertFalse(new File(marker().getBaseFile() + ".bak").exists());
    }
    private void assertUnattended() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertNotNull(keyguard); assertTrue("Do not run external-stop fixture on an attended/unlocked device", keyguard.isKeyguardLocked());
    }
    private File source() { File result = new File(context.getFilesDir(), SOURCE); assertTrue(result.isFile()); return result; }
    private AtomicFile marker() { return new AtomicFile(new File(context.getFilesDir(), "publication-death-test/live-ready.json")); }
    private static void durableWrite(AtomicFile marker, JSONObject json) throws Exception {
        File parent = marker.getBaseFile().getParentFile(); assertTrue(parent.isDirectory() || parent.mkdirs());
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8); assertTrue(bytes.length <= 16_384);
        FileOutputStream output = marker.startWrite(); try { output.write(bytes); marker.finishWrite(output); } catch (Exception failure) { marker.failWrite(output); throw failure; }
    }
    private static void assertJsonFields(JSONObject expected, JSONObject actual) throws Exception {
        assertEquals(expected.length(), actual.length()); JSONArray keys = expected.names();
        for (int i = 0; i < keys.length(); i++) { String key = keys.getString(i); Object a = expected.get(key), b = actual.get(key);
            if (a instanceof Number && b instanceof Number) { Number before = (Number) a, after = (Number) b;
                assertEquals((double) before.longValue(), before.doubleValue(), 0); assertEquals((double) after.longValue(), after.doubleValue(), 0);
                assertEquals("Changed snapshot integer " + key, before.longValue(), after.longValue()); }
            else assertEquals("Changed snapshot field " + key, a, b);
        }
    }
    private static Object field(Object target, String name) {
        try { Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass(); Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target instanceof Class<?> ? null : target); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static void ui(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); try (InputStream input = new FileInputStream(file)) { byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) if (count > 0) digest.update(buffer, 0, count); }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder text = new StringBuilder(); for (byte value : bytes) text.append(String.format(Locale.US, "%02x", value & 255)); return text.toString(); }
}
