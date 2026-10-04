package dev.minifilm.director;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/**
 * Select methods separately: phase1ArmActualEncoderAndAwaitParentForceStop must be INTERRUPTED
 * by an externally recorded force-stop; it never passes. Then run phase2RecoverKilledEncoder.
 * No staged interruption, mocked/paused exporter, app launch, capture, models or prefs changes.
 * A fresh PID alone is not kill provenance; the parent's live inspection/force-stop log is required.
 * Marker: files/process-death-test/live-export.json; read-only host inspection is permitted.
 * A normal Phase1 exit cancels encoding; raced published success stays intact for parent review.
 */
@RunWith(AndroidJUnit4.class)
public final class ExportProcessDeathTest {
    private static final String TITLE = "Synthetic process-death verification";
    private static final String SOURCE = "fixtures/jacket-speech-padded.mp4";
    private static final String MEDIA_PATH = "Movies/MiniFilm/";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 120_000)
    public void phase1ArmActualEncoderAndAwaitParentForceStop() throws Exception {
        assertDenied();
        AtomicFile marker = marker();
        assertFalse("An earlier fixture needs Phase2/inspection before a new run", marker.getBaseFile().exists());
        assertFalse(new File(marker.getBaseFile() + ".bak").exists());
        JSONObject baseline = snapshotBaseline();
        File source = source();
        JSONObject original = sourceSnapshot(source);
        File sentinel = new File(context.getCacheDir(), "reel-untracked-process-death-" + UUID.randomUUID() + ".mp4");
        byte[] guard = new byte[3072]; for (int i = 0; i < guard.length; i++) guard[i] = (byte) (i * 31 + 17);
        try (FileOutputStream out = new FileOutputStream(sentinel)) { out.write(guard); out.getFD().sync(); }
        JSONObject sentinelState = new JSONObject().put("name", sentinel.getName()).put("bytes", sentinel.length())
                .put("modifiedMs", sentinel.lastModified()).put("sha256", sha(sentinel));
        ReelExporter exporter = new ReelExporter(context);
        AtomicReference<String> terminal = new AtomicReference<>();
        AtomicInteger observedProgress = new AtomicInteger(-1), armedProgress = new AtomicInteger(-1);
        AtomicBoolean actualStartZeroObserved = new AtomicBoolean();
        AtomicReference<ExportRecovery.Journal> owned = new AtomicReference<>();
        AtomicReference<Thread> encoderThread = new AtomicReference<>();
        boolean armed = false;
        try {
            long duration = original.getLong("durationMs");
            List<Take> cuts = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Take cut = new Take(Uri.fromFile(source), "synthetic-death-" + i,
                        "Synthetic cut " + (i + 1), "Synthetic recovery fixture", duration);
                cut.inMs = 0; cut.outMs = 3000; cuts.add(cut);
            }
            ui(() -> {
                exporter.export(cuts, TITLE, "Clean", new ReelExporter.Listener() {
                    @Override public void onProgress(int percent) {
                        if (percent == 0) actualStartZeroObserved.set(true);
                        observedProgress.set(percent);
                    }
                    @Override public void onComplete(Uri video, Uri edit) { terminal.set("completed"); }
                    @Override public void onError(String message) { terminal.set("export_error"); }
                });
                owned.set((ExportRecovery.Journal) field(exporter, "activeJournal"));
                Object transformer = field(exporter, "transformer");
                if (transformer != null) {
                    // Verified in official androidx/media tag1.5.1 Transformer.java853 and
                    // TransformerInternal.java128, not inferred from an unpinned version.
                    Object internal = field(transformer, "transformerInternal");
                    assertNotNull(internal);
                    encoderThread.set((Thread) field(internal, "internalHandlerThread"));
                }
            });
            ExportRecovery.Journal journal = owned.get(); assertNotNull("Real exporter must create its own journal", journal);
            assertEquals("ENCODING", journal.state);
            assertJournalSet(baseline, journal.id);
            long waitStarted = SystemClock.elapsedRealtime(), previousBytes = -1, previousAt = 0;
            while (!armed && SystemClock.elapsedRealtime() - waitStarted < 45_000) {
                assertNull("A completed/failed export cannot establish killed-encoder evidence", terminal.get());
                long now = SystemClock.elapsedRealtime(), bytes = journal.temp().length();
                int publicProgress = observedProgress.get();
                if (bytes >= 4096 && previousBytes >= 4096 && bytes > previousBytes && now - previousAt >= 75
                        && publicProgress > 0 && publicProgress < 95) {
                    long observedBytes = bytes;
                    long priorObservedBytes = previousBytes;
                    AtomicReference<Throwable> armFailure = new AtomicReference<>();
                    ui(() -> {
                        try {
                            assertDenied();
                            assertNotNull("Actual Media3 encoder must still be running", field(exporter, "transformer"));
                            int progressAtArm = observedProgress.get();
                            assertTrue("The actual public start callback must first publish zero", actualStartZeroObserved.get());
                            assertTrue("Public export progress must have advanced but not completed", progressAtArm > 0 && progressAtArm < 95);
                            ExportRecovery.Journal persisted = ExportRecovery.Journal.read(context, journal.id);
                            assertEquals("ENCODING", persisted.state); assertNull(persisted.videoUri);
                            assertTrue(journal.temp().length() >= observedBytes);
                            JSONObject manifest = new JSONObject().put("schema", "minifilm.synthetic.process-death.v1")
                                    .put("syntheticSource", true).put("phase1Pid", Process.myPid()).put("exportId", journal.id)
                                    .put("state", "ENCODING").put("tempBytesObserved", observedBytes)
                                    .put("earlierTempBytesObserved", priorObservedBytes)
                                    .put("progressObserved", progressAtArm)
                                    .put("startZeroObserved", actualStartZeroObserved.get())
                                    .put("cuts", 12).put("timelineMs", 36000).put("source", original)
                                    .put("sentinel", sentinelState).put("baseline", baseline);
                            durableWrite(marker, manifest);
                            armedProgress.set(progressAtArm);
                        } catch (Throwable failure) { armFailure.set(failure); }
                    });
                    if (armFailure.get() != null) throw new AssertionError("Encoder was not live at durable arming", armFailure.get());
                    armed = true;
                    Log.i("MiniFilmProcessDeathTest", "LIVE_ENCODER_READY syntheticSource=true encoderActive=true stateEncoding=true"
                            + " tempBytes=" + observedBytes + " growthObserved=true startZeroObserved=true progressObserved=" + armedProgress.get()
                            + " cuts=12 timelineMs=36000 parentForceStopRequired=true");
                }
                previousBytes = bytes; previousAt = now;
                if (!armed) SystemClock.sleep(100);
            }
            assertTrue("No growing live encoder output appeared; do not claim a process-kill test", armed);
            long armedAt = SystemClock.elapsedRealtime();
            while (SystemClock.elapsedRealtime() - armedAt < 45_000) {
                assertNull("Parent missed the encoding window; successful output is not interrupted evidence", terminal.get());
                assertEquals("ENCODING", ExportRecovery.Journal.read(context, journal.id).state);
                SystemClock.sleep(100);
            }
            fail("Parent must force-stop the actual encoder; a normally finishing Phase1 is never a pass");
        } finally {
            // force-stop bypasses this block. Do not cancel a raced publication thread or delete
            // its successful media. Media3 1.5.1 cancel blocks for encoder/muxer release; additionally
            // join its captured internal thread before this test's own journal/guard cleanup.
            AtomicBoolean cancelledEncoder = new AtomicBoolean();
            ui(() -> {
                ExportRecovery.Journal journal = owned.get();
                if (journal != null && "ENCODING".equals(journal.state) && field(exporter, "transformer") != null) {
                    exporter.cancel(); cancelledEncoder.set(true);
                }
            });
            Thread thread = encoderThread.get();
            if (thread != null) { thread.join(10_000); assertFalse("Encoder worker must stop before fixture cleanup", thread.isAlive()); }
            assertDenied();
            assertSource(original, source); assertSentinel(sentinelState, sentinel);
            ExportRecovery.Journal journal = owned.get();
            if (journal != null && (cancelledEncoder.get() || "export_error".equals(terminal.get())) && row(journal.id) == null) {
                ExportRecovery.Journal persisted = ExportRecovery.Journal.read(context, journal.id);
                if ("ABORTED".equals(persisted.state)) {
                    assertFalse(journal.temp().exists()); assertFalse(journal.edit().exists());
                    journal.atomic().delete(); marker.delete(); assertTrue(sentinel.delete());
                }
            }
            // Any raced published/pending pair and sentinel are retained; the live manifest exists
            // only if arming actually succeeded. Parent can inspect the synthetic title/cut IDs.
            if (baseline.length() > 0) assertBaseline(baseline);
        }
    }

    @Test(timeout = 60_000)
    public void phase2RecoverKilledEncoder() throws Exception {
        assertDenied();
        AtomicFile marker = marker();
        assertTrue("Phase1 must have armed durably before external force-stop", marker.getBaseFile().isFile());
        assertTrue(marker.getBaseFile().length() > 0 && marker.getBaseFile().length() <= 16_384);
        JSONObject manifest = new JSONObject(new String(marker.readFully(), StandardCharsets.UTF_8));
        assertEquals("minifilm.synthetic.process-death.v1", manifest.getString("schema"));
        assertTrue(manifest.getBoolean("syntheticSource")); assertEquals("ENCODING", manifest.getString("state"));
        assertTrue("Recovery must run in a fresh process", manifest.getInt("phase1Pid") != Process.myPid());
        String id = manifest.getString("exportId"); assertTrue(id.matches(UUID_PATTERN));
        assertEquals(12, manifest.getInt("cuts")); assertEquals(36000, manifest.getLong("timelineMs"));
        int progressObserved = manifest.getInt("progressObserved");
        assertTrue("Marker must retain the actual public zero-start observation", manifest.getBoolean("startZeroObserved"));
        assertTrue("Marker must prove actual advancing public encoder progress", progressObserved > 0 && progressObserved < 95);
        long observed = manifest.getLong("tempBytesObserved"), earlier = manifest.getLong("earlierTempBytesObserved");
        assertTrue(observed > earlier && earlier >= 4096);
        JSONObject original = manifest.getJSONObject("source"), baseline = manifest.getJSONObject("baseline");
        File source = source(); assertSource(original, source);
        JSONObject guard = manifest.getJSONObject("sentinel");
        String name = guard.getString("name");
        assertTrue(name.matches("reel-untracked-process-death-" + UUID_PATTERN + "\\.mp4"));
        File sentinel = new File(context.getCacheDir(), name); assertSentinel(guard, sentinel);
        assertJournalSet(baseline, id);
        if (baseline.length() > 0) assertBaseline(baseline);
        ExportRecovery.Journal journal = ExportRecovery.Journal.read(context, id);
        assertEquals("Raced publication cannot pass as interrupted encoding", "ENCODING", journal.state);
        assertNull(journal.videoUri); assertTrue(journal.temp().length() >= observed);
        assertFalse(journal.edit().exists()); assertNull(row(id));
        ExportRecovery.Result result = ExportRecovery.reconcile(context);
        assertEquals("Only the known interrupted encoding should be cleaned", 1, result.cleaned);
        assertTrue("No ambiguous ownership should be reported", result.warning.isEmpty());
        assertFalse(journal.temp().exists()); assertFalse(journal.edit().exists());
        assertFalse(journal.atomic().getBaseFile().exists());
        assertFalse(new File(journal.atomic().getBaseFile() + ".bak").exists());
        assertNull(row(id)); assertSource(original, source); assertSentinel(guard, sentinel);
        if (baseline.length() > 0) {
            assertBaseline(baseline);
            assertEquals(Uri.parse(baseline.getJSONObject("row").getString("uri")), result.videoUri);
            ExportRecovery.Journal completed = ExportRecovery.Journal.read(context, baseline.getJSONObject("journal").getString("id"));
            assertEquals(FileProvider.getUriForFile(context, context.getPackageName() + ".files", completed.edit()), result.editListUri);
        } else { assertNull(result.videoUri); assertNull(result.editListUri); }
        assertJournalSet(baseline, null);
        assertDenied();
        Log.i("MiniFilmProcessDeathTest", "POST_KILL_RECOVERY_PASS syntheticSource=true freshProcess=true"
                + " encoderBytesPreviouslyObserved=" + observed + " exactInterruptedJournalRemoved=true"
                + " progressObserved=" + progressObserved
                + " originalHashSizeMtimePreserved=true untrackedSentinelPreserved=true"
                + " baselineCompletePairPreserved=" + (baseline.length() > 0) + " externalKillProvenanceRequired=true");
        // Preserve guards through every recovery assertion; cleanup only this test's own markers.
        assertTrue(sentinel.delete()); marker.delete();
    }

    /** Explicit cleanup for a reviewed missed/raced trial, never process-death acceptance. */
    @Test(timeout = 60_000)
    public void phase3CleanupReviewedRacedSyntheticFixture() throws Exception {
        assertDenied();
        AtomicFile marker = marker();
        assertTrue("Only a durably armed synthetic trial can be cleaned", marker.getBaseFile().isFile());
        assertTrue(marker.getBaseFile().length() > 0 && marker.getBaseFile().length() <= 16_384);
        JSONObject manifest = new JSONObject(new String(marker.readFully(), StandardCharsets.UTF_8));
        assertEquals("minifilm.synthetic.process-death.v1", manifest.getString("schema"));
        assertTrue(manifest.getBoolean("syntheticSource")); assertEquals("ENCODING", manifest.getString("state"));
        assertTrue("Never clean outputs while the original trial process might own them", manifest.getInt("phase1Pid") != Process.myPid());
        assertTrue(manifest.getBoolean("startZeroObserved"));
        int progress = manifest.getInt("progressObserved"); assertTrue(progress > 0 && progress < 95);
        assertEquals(12, manifest.getInt("cuts")); assertEquals(36000, manifest.getLong("timelineMs"));
        assertTrue(manifest.getLong("tempBytesObserved") > manifest.getLong("earlierTempBytesObserved"));
        assertTrue(manifest.getLong("earlierTempBytesObserved") >= 4096);
        String id = manifest.getString("exportId"); assertTrue(id.matches(UUID_PATTERN));
        JSONObject original = manifest.getJSONObject("source"), baseline = manifest.getJSONObject("baseline");
        File source = source(); assertSource(original, source);
        assertJournalSet(baseline, id);
        if (baseline.length() > 0) assertBaseline(baseline);
        Field activeField = ExportRecovery.class.getDeclaredField("ACTIVE"); activeField.setAccessible(true);
        assertTrue("No same-process export may be active during explicit fixture cleanup", ((Set<?>) activeField.get(null)).isEmpty());
        JSONObject guard = manifest.getJSONObject("sentinel"); String name = guard.getString("name");
        assertTrue(name.matches("reel-untracked-process-death-" + UUID_PATTERN + "\\.mp4"));
        File sentinel = new File(context.getCacheDir(), name); assertSentinel(guard, sentinel);
        ExportRecovery.Journal journal = ExportRecovery.Journal.read(context, id);
        assertTrue(journal.state.matches("ENCODING|SAVING|READY|COMPLETE|ABORTED"));
        String reviewedState = journal.state;
        boolean verifiedEdit = false;
        if (journal.edit().exists()) {
            assertTrue(journal.edit().isFile() && journal.edit().length() > 0 && journal.edit().length() <= 1_048_576);
            JSONObject project = new JSONObject(new String(Files.readAllBytes(journal.edit().toPath()), StandardCharsets.UTF_8));
            assertEquals("minifilm.edit.v1", project.getString("schema")); assertEquals(id, project.getString("exportId"));
            assertTrue(project.getBoolean("preEventResearch")); assertEquals(TITLE, project.getString("title"));
            assertEquals("Clean", project.getString("look")); assertEquals(36000, project.getLong("durationMs"));
            assertEquals(720, project.getInt("width")); assertEquals(1280, project.getInt("height"));
            JSONArray cuts = project.getJSONArray("cuts"); assertEquals(12, cuts.length());
            String fixedSource = Uri.fromFile(source).toString();
            for (int i = 0; i < cuts.length(); i++) {
                JSONObject cut = cuts.getJSONObject(i);
                assertEquals("synthetic-death-" + i, cut.getString("shotId"));
                assertEquals(fixedSource, cut.getString("sourceUri"));
                assertEquals("Synthetic cut " + (i + 1), cut.getString("title"));
                assertEquals("Synthetic recovery fixture", cut.getString("caption"));
                assertEquals("manual", cut.getString("captionOrigin"));
                assertEquals(0, cut.getLong("inMs")); assertEquals(3000, cut.getLong("outMs"));
                assertEquals(i * 3000L, cut.getLong("timelineStartMs")); assertEquals(0, cut.getJSONArray("subtitles").length());
            }
            verifiedEdit = true;
        }
        JSONObject ownRow = row(id);
        if (ownRow != null) {
            // row() already enforces exact UUID name/path and app ownership; a valid synthetic
            // cut list is additionally mandatory even for a partial pending copy.
            assertTrue("Never remove a row without the exact synthetic cut-list proof", verifiedEdit);
            Uri uri = Uri.parse(ownRow.getString("uri"));
            if (journal.videoUri != null) assertEquals(journal.videoUri, uri);
            if (!ownRow.getBoolean("pending")) {
                assertTrue("Published fixture requires the committed expected byte count", journal.expectedBytes >= 1000);
                assertEquals(journal.expectedBytes, ownRow.getLong("bytes"));
                assertTrue(journal.state.equals("READY") || journal.state.equals("COMPLETE"));
            } else if (journal.expectedBytes > 0) {
                assertTrue("Partial fixture cannot exceed its recorded expected copy", ownRow.getLong("bytes") <= journal.expectedBytes);
            }
            assertEquals("Remove only the fully verified synthetic output row", 1,
                    context.getContentResolver().delete(uri, null, null));
        }
        assertNull(row(id));
        if (journal.temp().exists()) assertTrue(journal.temp().delete());
        if (journal.edit().exists()) assertTrue(journal.edit().delete());
        journal.atomic().delete();
        assertFalse(journal.temp().exists()); assertFalse(journal.edit().exists());
        assertFalse(journal.atomic().getBaseFile().exists()); assertFalse(new File(journal.atomic().getBaseFile() + ".bak").exists());
        assertSource(original, source); assertSentinel(guard, sentinel);
        if (baseline.length() > 0) assertBaseline(baseline);
        assertJournalSet(baseline, null); assertDenied();
        assertTrue(sentinel.delete()); marker.delete(); assertFalse(marker.getBaseFile().exists());
        Log.i("MiniFilmProcessDeathTest", "REVIEWED_RACED_FIXTURE_CLEANUP syntheticSource=true reviewedState=" + reviewedState
                + " exactOwnedRowRemoved=" + (ownRow != null) + " exactOwnedFilesRemoved=true"
                + " originalPreserved=true baselinePreserved=true processDeathRecoveryProven=false");
    }

    private JSONObject snapshotBaseline() throws Exception {
        Set<String> names = journalNames();
        assertTrue("Allow at most one pre-existing verified COMPLETE result", names.size() <= 1);
        if (names.isEmpty()) return new JSONObject();
        String id = names.iterator().next(); ExportRecovery.Journal journal = ExportRecovery.Journal.read(context, id);
        assertEquals("Unknown/unfinished baseline must not be reconciled by this fixture", "COMPLETE", journal.state);
        JSONObject result = new JSONObject().put("journal", journalJson(id));
        JSONObject row = row(id); assertNotNull("Complete baseline requires its exact published owned row", row);
        assertFalse(row.getBoolean("pending")); assertNotNull(journal.videoUri);
        assertEquals(journal.videoUri.toString(), row.getString("uri"));
        assertEquals(journal.expectedBytes, row.getLong("bytes")); assertTrue(journal.expectedBytes >= 1000);
        JSONObject edit = editSnapshot(journal);
        result.put("row", row).put("edit", edit); return result;
    }

    private void assertBaseline(JSONObject baseline) throws Exception {
        JSONObject expectedJournal = baseline.getJSONObject("journal"); String id = expectedJournal.getString("id");
        assertTrue(id.matches(UUID_PATTERN));
        assertJsonFields(expectedJournal, journalJson(id));
        JSONObject actualRow = row(id); assertNotNull(actualRow); assertJsonFields(baseline.getJSONObject("row"), actualRow);
        assertJsonFields(baseline.getJSONObject("edit"), editSnapshot(ExportRecovery.Journal.read(context, id)));
    }

    private JSONObject journalJson(String id) throws Exception {
        ExportRecovery.Journal journal = ExportRecovery.Journal.read(context, id);
        assertEquals("COMPLETE", journal.state);
        return new JSONObject(new String(journal.atomic().readFully(), StandardCharsets.UTF_8));
    }

    private JSONObject editSnapshot(ExportRecovery.Journal journal) throws Exception {
        File edit = journal.edit(); assertTrue(edit.isFile() && edit.length() > 0 && edit.length() <= 1_048_576);
        JSONObject project = new JSONObject(new String(Files.readAllBytes(edit.toPath()), StandardCharsets.UTF_8));
        assertEquals("minifilm.edit.v1", project.getString("schema")); assertEquals(journal.id, project.getString("exportId"));
        assertTrue(project.getLong("durationMs") > 0); assertTrue(project.getJSONArray("cuts").length() > 0);
        return new JSONObject().put("bytes", edit.length()).put("sha256", sha(edit));
    }

    private JSONObject row(String id) throws Exception {
        assertTrue(id.matches(UUID_PATTERN));
        String name = "MiniFilm-" + id + ".mp4";
        try (Cursor cursor = context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                new String[] {MediaStore.Video.Media._ID, MediaStore.Video.Media.OWNER_PACKAGE_NAME,
                        MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.RELATIVE_PATH, MediaStore.Video.Media.IS_PENDING},
                MediaStore.Video.Media.DISPLAY_NAME + "=? AND " + MediaStore.Video.Media.RELATIVE_PATH + "=?",
                new String[] {name, MEDIA_PATH}, null)) {
            assertNotNull(cursor); assertTrue("Exact owned row query must not be ambiguous", cursor.getCount() <= 1);
            if (!cursor.moveToFirst()) return null;
            assertEquals(context.getPackageName(), cursor.getString(1)); assertEquals(name, cursor.getString(2));
            assertEquals(MEDIA_PATH, cursor.getString(3));
            Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0));
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long bytes = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                assertNotNull(input); byte[] buffer = new byte[8192];
                for (int n; (n = input.read(buffer)) >= 0;) if (n > 0) {
                    bytes += n; assertTrue("Baseline fixture read budget", bytes <= 64L * 1024 * 1024); digest.update(buffer, 0, n);
                }
            }
            return new JSONObject().put("uri", uri.toString()).put("owner", cursor.getString(1))
                    .put("name", name).put("path", MEDIA_PATH).put("pending", cursor.getInt(4) != 0)
                    .put("bytes", bytes).put("sha256", hex(digest.digest()));
        }
    }

    private void assertJournalSet(JSONObject baseline, String owned) {
        Set<String> allowed = new HashSet<>();
        if (baseline.length() > 0) allowed.add(baseline.optJSONObject("journal").optString("id"));
        if (owned != null) allowed.add(owned); assertEquals("No unrelated journal may enter fixture recovery", allowed, journalNames());
    }

    private Set<String> journalNames() {
        Set<String> result = new HashSet<>(); File[] files = new File(context.getFilesDir(), "export-journal").listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName(); if (name.endsWith(".bak")) name = name.substring(0, name.length() - 4);
            if (name.endsWith(".json")) { String id = name.substring(0, name.length() - 5); assertTrue(id.matches(UUID_PATTERN)); result.add(id); }
        }
        assertTrue(result.size() <= 2); return result;
    }

    private JSONObject sourceSnapshot(File source) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, Uri.fromFile(source));
            assertNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
            long duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue(duration >= 8546 && duration <= 8946); assertTrue(source.length() > 1000 && source.length() <= 4 * 1024 * 1024);
            return new JSONObject().put("fixture", SOURCE).put("bytes", source.length()).put("modifiedMs", source.lastModified())
                    .put("sha256", sha(source)).put("durationMs", duration);
        } finally { metadata.release(); }
    }

    private void assertSource(JSONObject expected, File source) throws Exception {
        assertEquals(SOURCE, expected.getString("fixture")); assertJsonFields(expected, sourceSnapshot(source));
    }

    private static void assertSentinel(JSONObject expected, File sentinel) throws Exception {
        assertTrue(sentinel.isFile()); assertEquals(expected.getString("name"), sentinel.getName());
        assertEquals(expected.getLong("bytes"), sentinel.length()); assertEquals(expected.getLong("modifiedMs"), sentinel.lastModified());
        assertEquals(expected.getString("sha256"), sha(sentinel));
    }

    private static void assertJsonFields(JSONObject expected, JSONObject actual) throws Exception {
        assertEquals(expected.length(), actual.length()); JSONArray keys = expected.names();
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i); Object before = expected.get(key), after = actual.get(key);
            if (before instanceof Number && after instanceof Number) {
                Number a = (Number) before, b = (Number) after;
                assertEquals("Snapshot integers must not lose a fraction", (double) a.longValue(), a.doubleValue(), 0);
                assertEquals("Snapshot integers must not lose a fraction", (double) b.longValue(), b.doubleValue(), 0);
                assertEquals("Changed snapshot field " + key, a.longValue(), b.longValue());
            } else assertEquals("Changed snapshot field " + key, before, after);
        }
    }

    private File source() { File file = new File(context.getFilesDir(), SOURCE); assertTrue(file.isFile()); return file; }
    private void assertDenied() {
        assertEquals("Synthetic process test requires denied camera permission", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals("Synthetic process test requires denied microphone permission", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
    }
    private AtomicFile marker() { return new AtomicFile(new File(context.getFilesDir(), "process-death-test/live-export.json")); }
    private static void durableWrite(AtomicFile file, JSONObject json) throws Exception {
        File parent = file.getBaseFile().getParentFile(); assertTrue(parent.isDirectory() || parent.mkdirs());
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8); assertTrue(bytes.length <= 16_384);
        FileOutputStream stream = file.startWrite();
        try { stream.write(bytes); file.finishWrite(stream); }
        catch (Exception error) { file.failWrite(stream); throw error; }
    }
    private static Object field(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static void ui(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) { byte[] buffer = new byte[8192]; for (int n; (n = input.read(buffer)) >= 0;) if (n > 0) digest.update(buffer, 0, n); }
        return hex(digest.digest());
    }
    private static String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 255)); return result.toString(); }
}
