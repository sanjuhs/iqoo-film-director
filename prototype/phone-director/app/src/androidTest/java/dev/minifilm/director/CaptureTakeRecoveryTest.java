package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.util.AtomicFile;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real container copies + durable store, no Activity/recording/model/playback. A recreated
 * store is not a killed CameraX recorder; unfinalized MP4 repair is not claimed. */
@RunWith(AndroidJUnit4.class)
public final class CaptureTakeRecoveryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File directory, fixture; private CaptureTakeStore store;
    private long originalBytes, originalMtime; private String originalHash;
    private Map<String, ?> originalPreferences;
    private boolean retainStagedFixture, ownStageMarker;
    private static final String STAGE_MARKER = "synthetic-capture-pending-stage.json";
    private static final String STAGE_SCHEMA = "minifilm.synthetic.capture-pending.v1";
    private static final String STAGED_ID = "synthetic-staged-capture";
    private static final String STAGED_TITLE = "Synthetic staged pending take";
    private static final String STAGED_CAPTION = "Synthetic pending story caption";
    private final List<File> ownedSources = new ArrayList<>();
    private final List<File> ownedOther = new ArrayList<>();

    @Before public void ownSandboxAndExistingSyntheticFixtureOnly() throws Exception {
        assertDenied(); originalPreferences = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        fixture = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Existing labelled synthetic English fixture required", fixture.isFile());
        originalBytes = fixture.length(); originalMtime = fixture.lastModified(); originalHash = hash(fixture);
        directory = new File(context.getCacheDir(), "synthetic-capture-recovery-" + UUID.randomUUID()).getCanonicalFile();
        assertFalse(directory.exists()); assertTrue(directory.mkdir()); store = new CaptureTakeStore(directory);
    }

    @After public void preserveOriginalsAndRemoveOnlyOwnedFixturePaths() throws Exception {
        try { assertOriginal(); assertDenied(); assertEquals(originalPreferences, context.getSharedPreferences("shoot", 0).getAll()); }
        finally {
            if (!retainStagedFixture) {
                for (File file : ownedSources) {
                    store.abort(file); new AtomicFile(new File(file.getPath() + ".json")).delete();
                    Files.deleteIfExists(file.toPath());
                }
                for (File file : ownedOther) Files.deleteIfExists(file.toPath());
                if (ownStageMarker) new AtomicFile(new File(context.getCacheDir(), STAGE_MARKER)).delete();
                if (directory != null) assertTrue("Exclusive sandbox has no untracked files to delete", directory.delete());
            }
        }
    }

    /** Select separately: successful staging intentionally retains a pending synthetic record.
     * The parent may observe normal instrumentation process termination or explicitly stop
     * the verified synthetic test process. Neither is a real CameraX recording kill. */
    @Test(timeout = 30_000)
    public void stageSyntheticPendingRecordForFreshProcess() throws Exception {
        File marker = new File(context.getCacheDir(), STAGE_MARKER);
        assertFalse("Do not replace another pending staged fixture", marker.exists());
        assertFalse(new File(marker.getPath() + ".new").exists()); assertFalse(new File(marker.getPath() + ".bak").exists());
        File target = owned(); store.prepare(target, new Shot(STAGED_ID, STAGED_TITLE, "Hold still", STAGED_CAPTION, 6000));
        copy(fixture, target);
        File sentinel = new File(directory, "synthetic-staged-sentinel.bin"); ownedOther.add(sentinel);
        Files.write(sentinel.toPath(), "Synthetic unrelated sentinel stays intact".getBytes(StandardCharsets.UTF_8));
        assertTrue("Active pending source must not be recovered in its originating process", store.recover(Collections.emptyList()).recovered.isEmpty());
        JSONObject pending = metadata(target); assertEquals("pending", pending.getString("state"));
        assertEquals(STAGED_ID, pending.getString("shotId")); assertEquals(STAGED_TITLE, pending.getString("title")); assertEquals(STAGED_CAPTION, pending.getString("caption"));
        File sidecar = new File(target.getPath() + ".json");
        long actualDuration = CaptureController.finalizedVideoDuration(target); assertTrue(Math.abs(actualDuration - 5746) <= 200);
        JSONObject proof = new JSONObject().put("schema", STAGE_SCHEMA).put("state", "pending").put("syntheticFixture", true)
                .put("stagePid", Process.myPid()).put("sandboxName", directory.getName()).put("takeName", target.getName())
                .put("fixtureRelativePath", "fixtures/jacket-speech.mp4").put("originalBytes", originalBytes)
                .put("originalMtime", originalMtime).put("originalSha256", originalHash)
                .put("copyBytes", target.length()).put("copyMtime", target.lastModified()).put("copySha256", hash(target))
                .put("metadataBytes", sidecar.length()).put("metadataMtime", sidecar.lastModified()).put("metadataSha256", hash(sidecar))
                .put("sentinelBytes", sentinel.length()).put("sentinelMtime", sentinel.lastModified()).put("sentinelSha256", hash(sentinel))
                .put("expectedSourceDurationMs", actualDuration).put("shotId", STAGED_ID).put("title", STAGED_TITLE).put("caption", STAGED_CAPTION)
                .put("activePendingSkipped", true).put("cameraPermissionDenied", true).put("microphonePermissionDenied", true);
        assertOriginal(); assertDenied();
        AtomicFile atomic = new AtomicFile(marker); FileOutputStream output = atomic.startWrite();
        try { output.write(proof.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(output); }
        catch (Exception error) { atomic.failWrite(output); throw error; }
        ownStageMarker = true;
        JSONObject durable = new JSONObject(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
        assertEquals(proof.toString(), durable.toString()); retainStagedFixture = true;
        Log.i("MiniFilmCaptureRecoveryTest", "PENDING_STAGE_PASS synthetic=true actualDurationMs=" + actualDuration
                + " activePendingSkipped=true durableMetadata=true awaitsFreshProcess=true cameraOpened=false microphoneOpened=false");
    }

    /** Fresh-process persisted-state check, not a live recorder/encoder interruption proof. */
    @Test(timeout = 30_000)
    public void recoverStagedSyntheticPendingRecordInFreshProcess() throws Exception {
        File marker = new File(context.getCacheDir(), STAGE_MARKER); assertTrue("Run the explicit stage selector first", marker.isFile());
        assertFalse(Files.isSymbolicLink(marker.toPath())); assertTrue(marker.length() > 0 && marker.length() < 8192);
        JSONObject proof = new JSONObject(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
        assertEquals(STAGE_SCHEMA, proof.getString("schema")); assertEquals("pending", proof.getString("state"));
        assertTrue(proof.getBoolean("syntheticFixture")); assertTrue(proof.getBoolean("activePendingSkipped"));
        assertTrue(proof.getBoolean("cameraPermissionDenied")); assertTrue(proof.getBoolean("microphonePermissionDenied"));
        assertNotEquals("Require actual fresh process/static state", proof.getInt("stagePid"), Process.myPid());
        assertEquals("fixtures/jacket-speech.mp4", proof.getString("fixtureRelativePath"));
        assertEquals(originalBytes, proof.getLong("originalBytes")); assertEquals(originalMtime, proof.getLong("originalMtime")); assertEquals(originalHash, proof.getString("originalSha256"));
        assertEquals(STAGED_ID, proof.getString("shotId")); assertEquals(STAGED_TITLE, proof.getString("title")); assertEquals(STAGED_CAPTION, proof.getString("caption"));
        String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        assertTrue(proof.getString("sandboxName").matches("synthetic-capture-recovery-" + uuid));
        assertTrue(proof.getString("takeName").matches("take-" + uuid + "\\.mp4"));
        File stagedDirectory = new File(context.getCacheDir(), proof.getString("sandboxName"));
        assertFalse(Files.isSymbolicLink(stagedDirectory.toPath())); stagedDirectory = stagedDirectory.getCanonicalFile();
        assertEquals(context.getCacheDir().getCanonicalFile(), stagedDirectory.getParentFile());
        File target = new File(stagedDirectory, proof.getString("takeName")); File sidecar = new File(target.getPath() + ".json");
        File sentinel = new File(stagedDirectory, "synthetic-staged-sentinel.bin");
        assertSnapshot(target, proof, "copy"); assertSnapshot(sidecar, proof, "metadata"); assertSnapshot(sentinel, proof, "sentinel");
        assertEquals(originalHash, hash(target));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(target.getName(), sidecar.getName(), sentinel.getName())),
                new java.util.HashSet<>(java.util.Arrays.asList(stagedDirectory.list())));
        JSONObject pending = metadata(target); assertEquals("pending", pending.getString("state")); assertEquals(STAGED_ID, pending.getString("shotId"));
        assertEquals(STAGED_TITLE, pending.getString("title")); assertEquals(STAGED_CAPTION, pending.getString("caption"));
        assertTrue(directory.delete()); directory = stagedDirectory; store = new CaptureTakeStore(directory);
        ownedSources.add(target); ownedOther.add(sentinel); ownStageMarker = true; retainStagedFixture = true;
        CaptureTakeStore.Result result = store.recover(Collections.emptyList()); assertEquals(0, result.unreadable); assertEquals(1, result.recovered.size());
        Take take = result.recovered.get(0); assertFalse(take.selected); assertEquals(Uri.fromFile(target), take.uri);
        assertEquals(STAGED_ID, take.shotId); assertEquals(STAGED_TITLE, take.title); assertEquals(STAGED_CAPTION, take.caption);
        assertEquals(proof.getLong("expectedSourceDurationMs"), take.durationMs); assertEquals(0, take.inMs); assertEquals(take.durationMs, take.outMs);
        assertEquals("ready", metadata(target).getString("state")); assertEquals(take.durationMs, metadata(target).getLong("durationMs"));
        assertSnapshot(target, proof, "copy"); assertSnapshot(sentinel, proof, "sentinel"); assertOriginal(); assertDenied();
        assertTrue(store.recover(Collections.singletonList(take)).recovered.isEmpty());
        retainStagedFixture = false;
        Log.i("MiniFilmCaptureRecoveryTest", "PENDING_FRESH_PROCESS_PASS synthetic=true recovered=1 unselected=true shotFactsRetained=true"
                + " actualDurationMs=" + take.durationMs + " originalsAndSentinelIntact=true realRecordingKill=false");
    }

    private static void assertSnapshot(File file, JSONObject proof, String prefix) throws Exception {
        assertFalse(Files.isSymbolicLink(file.toPath())); assertTrue(file.isFile());
        assertEquals(proof.getLong(prefix + "Bytes"), file.length()); assertEquals(proof.getLong(prefix + "Mtime"), file.lastModified());
        assertEquals(proof.getString(prefix + "Sha256"), hash(file));
    }

    @Test(timeout = 30_000)
    public void activeFlushSkipsThenDurableShotFactsAndActualDurationRecoverWithoutChangingListedEdits() throws Exception {
        File target = owned();
        Shot shot = new Shot("synthetic-saved-hero", "Synthetic saved hero", "Face left", "Exact synthetic caption\nSecond line", 6000);
        store.prepare(target, shot);
        JSONObject pending = metadata(target); assertEquals("pending", pending.getString("state"));
        assertEquals(shot.id, pending.getString("shotId")); assertEquals(shot.caption, pending.getString("caption"));
        shot.id = "later-plan-id"; shot.title = "Later plan title"; shot.caption = "Later plan caption";
        copy(fixture, target);
        long copiedMtime = target.lastModified();
        CaptureTakeStore recreated = new CaptureTakeStore(directory);
        ArrayList<Take> empty = new ArrayList<>();
        assertTrue("Still-flushing same-process pending source must stay isolated", recreated.recover(empty).recovered.isEmpty());
        assertTrue(empty.isEmpty());
        long actual = store.complete(target); assertTrue(Math.abs(actual - 5746) <= 200);
        assertEquals("ready", metadata(target).getString("state")); assertEquals(actual, metadata(target).getLong("durationMs"));
        CaptureTakeStore.Result recovered = recreated.recover(empty); assertTrue(empty.isEmpty()); assertEquals(0, recovered.unreadable);
        assertEquals(1, recovered.recovered.size()); Take take = recovered.recovered.get(0);
        assertEquals(Uri.fromFile(target), take.uri); assertEquals("synthetic-saved-hero", take.shotId);
        assertEquals("Synthetic saved hero", take.title); assertEquals("Exact synthetic caption\nSecond line", take.caption);
        assertEquals(actual, take.durationMs); assertEquals(0, take.inMs); assertEquals(actual, take.outMs); assertFalse(take.selected);
        assertEquals(originalHash, hash(target)); assertEquals(copiedMtime, target.lastModified());

        // Existing aliases must keep creator edits rather than gain a duplicate/replacement.
        take.uri = Uri.fromFile(new File(directory, "./" + target.getName()));
        take.title = "Creator edited title"; take.caption = "Creator edited caption"; take.selected = true;
        take.inMs = 1001; take.outMs = actual - 1; take.captionOrigin = "creator-reviewed-manual";
        take.subtitles.add(new SubtitleCue(1100, 1400, "Creator reviewed words"));
        List<SubtitleCue> existingCues = take.subtitles; List<Take> listed = new ArrayList<>(Collections.singletonList(take));
        assertTrue(recreated.recover(listed).recovered.isEmpty()); assertSame(take, listed.get(0)); assertEquals(1, listed.size());
        assertEquals("Creator edited title", take.title); assertEquals("Creator edited caption", take.caption); assertTrue(take.selected);
        assertEquals(1001, take.inMs); assertEquals(actual - 1, take.outMs); assertSame(existingCues, take.subtitles);
        assertEquals("Creator reviewed words", take.subtitles.get(0).text); assertEquals("creator-reviewed-manual", take.captionOrigin);
        assertEquals(originalHash, hash(target)); assertEquals(copiedMtime, target.lastModified()); assertOriginal();
        Log.i("MiniFilmCaptureRecoveryTest", "DURABLE_PASS synthetic=true actualDurationMs=" + actual
                + " pendingLiveSkipped=true shotSnapshotRetained=true recoveredUnselected=true existingAliasEditsPreserved=true");
    }

    @Test(timeout = 30_000)
    public void invalidUnknownAndSymlinkSourcesStayIntactWhileLegacyValidSourceRecoversUnselected() throws Exception {
        File incomplete = owned(); store.prepare(incomplete, new Shot("synthetic-incomplete", "Incomplete", "Hold still", "", 3000));
        Files.write(incomplete.toPath(), "Synthetic deliberately incomplete MP4".getBytes(StandardCharsets.UTF_8));
        String incompleteHash = hash(incomplete); long incompleteMtime = incomplete.lastModified();
        assertEquals(0, store.complete(incomplete)); store.abort(incomplete);
        assertFalse(new File(incomplete.getPath() + ".json").exists()); assertEquals(incompleteHash, hash(incomplete));
        File legacy = owned(); copy(fixture, legacy); long legacyMtime = legacy.lastModified();
        // Corrupt own metadata cannot supply fabricated story facts; the valid source is kept.
        File malformed = new File(legacy.getPath() + ".json");
        Files.write(malformed.toPath(), "{\"version\":999,\"title\":\"Invented title\"}".getBytes(StandardCharsets.UTF_8));
        String malformedHash = hash(malformed); long malformedMtime = malformed.lastModified();
        File unknown = new File(directory, "unrelated-synthetic.mp4"); ownedOther.add(unknown); copy(fixture, unknown);
        File link = owned(); Files.createSymbolicLink(link.toPath(), fixture.getCanonicalFile().toPath());
        CaptureTakeStore.Result result = new CaptureTakeStore(directory).recover(Collections.emptyList());
        assertEquals(1, result.recovered.size()); assertEquals(Uri.fromFile(legacy), result.recovered.get(0).uri);
        assertFalse(result.recovered.get(0).selected); assertEquals("recovered", result.recovered.get(0).shotId);
        assertEquals("Recovered take", result.recovered.get(0).title); assertEquals("", result.recovered.get(0).caption);
        assertTrue(Math.abs(result.recovered.get(0).durationMs - 5746) <= 200);
        assertEquals("Incomplete source, retained malformed marker and rejected child symlink", 3, result.unreadable);
        assertEquals(malformedHash, hash(malformed)); assertEquals(malformedMtime, malformed.lastModified());
        assertEquals(incompleteHash, hash(incomplete)); assertEquals(incompleteMtime, incomplete.lastModified());
        assertEquals(originalHash, hash(legacy)); assertEquals(legacyMtime, legacy.lastModified()); assertEquals(originalHash, hash(unknown));
        assertTrue(Files.isSymbolicLink(link.toPath())); assertOriginal();
        File escaped = new File(directory, "nested/" + legacy.getName());
        try { store.prepare(escaped, null); fail("Non-direct child source was accepted"); } catch (java.io.IOException expected) { }
        Log.i("MiniFilmCaptureRecoveryTest", "GUARDS_PASS synthetic=true legacyValid=1 unreadable=3 originalsIntact=true childSymlinkRejected=true malformedMetadataPreserved=true unknownSourcePreserved=true noRepairClaim=true");
    }

    private File owned() { File file = new File(directory, "take-" + UUID.randomUUID() + ".mp4"); ownedSources.add(file); return file; }
    private static JSONObject metadata(File source) throws Exception { return new JSONObject(new String(Files.readAllBytes(new File(source.getPath() + ".json").toPath()), StandardCharsets.UTF_8)); }
    private static void copy(File source, File destination) throws Exception {
        try (InputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] block = new byte[8192]; int count; while ((count = input.read(block)) != -1) output.write(block, 0, count); output.getFD().sync();
        }
    }
    private static String hash(File file) throws Exception {
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[8192]; int count;
            while ((count = input.read(block)) != -1) hash.update(block, 0, count); return ProjectPackager.hex(hash.digest());
        }
    }
    private void assertOriginal() throws Exception { if (fixture != null) { assertTrue(fixture.isFile()); assertEquals(originalBytes, fixture.length()); assertEquals(originalMtime, fixture.lastModified()); assertEquals(originalHash, hash(fixture)); } }
    private void assertDenied() { assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA)); assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)); }
}
