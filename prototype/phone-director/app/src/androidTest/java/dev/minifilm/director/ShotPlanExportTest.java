package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Synthetic creator metadata + actual original-byte ZIP, without encoding or inference.
 * Snapshot labels are provenance supplied by the caller, not a claim of plan approval,
 * clip quality, current coverage, or an automatic correspondence to recorded media. */
@RunWith(AndroidJUnit4.class)
public final class ShotPlanExportTest {
    private static final String HERO = "plan-synthetic-current-hero", CLOSING = "plan-synthetic-current-closing";
    private static final String OLD = "plan-synthetic-earlier-hero";
    private static final String LABEL = "Synthetic manual plan · metadata only";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File source;
    private long bytes, mtime;
    private String hash;
    private Map<String, ?> preferences;

    @Before public void existingSyntheticFixtureOnly() throws Exception {
        denied(); preferences = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        source = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Existing labelled synthetic jacket source required", source.isFile());
        bytes = source.length(); mtime = source.lastModified(); hash = sha(new FileInputStream(source));
        assertTrue(bytes > 1000);
    }
    @After public void originalSourceAndPreferencesRemainIntact() throws Exception {
        if (hash != null) {
            assertTrue(source.isFile()); assertEquals(bytes, source.length()); assertEquals(mtime, source.lastModified());
            assertEquals(hash, sha(new FileInputStream(source)));
        }
        if (preferences != null) assertEquals(preferences, context.getSharedPreferences("shoot", 0).getAll());
        denied();
    }

    @Test(timeout = 15_000)
    public void actualDeviceEditSerializerKeepsFrozenPlanExactIdsAndOriginalTimelineFacts() throws Exception {
        List<Shot> mutable = plan(); Shot hero = mutable.get(0), closing = mutable.get(1);
        ShotPlanSnapshot frozen = ShotPlanSnapshot.capture(mutable, LABEL);
        Take mapped = take("synthetic-original-capture-private", "Private cut title", 500, 2500);
        mapped.reviewedShotIds.addAll(Arrays.asList(HERO, OLD, CLOSING));
        mapped.captionOrigin = "whisper-tiny.en-draft";
        mapped.subtitles.add(new SubtitleCue(800, 1200, "Synthetic words kept exactly"));
        Take unassigned = take(HERO, "Unassigned cut", 1000, 3000);
        List<Take> cuts = Arrays.asList(copy(mapped), copy(unassigned));
        mutatePlan(mutable, hero, closing); mapped.reviewedShotIds.clear(); mapped.reviewedShotIds.add("later-choice");
        JSONObject document = ReelExporter.editDocument("synthetic-plan-serializer", cuts,
                "Synthetic plan context", "Clean", Collections.emptyList(), frozen);
        assertPlan(document.getJSONObject("shotPlan"));
        assertEquals("minifilm.edit.v1", document.getString("schema")); assertTrue(document.getBoolean("preEventResearch"));
        assertEquals(4000, document.getLong("durationMs"));
        JSONObject first = document.getJSONArray("cuts").getJSONObject(0), second = document.getJSONArray("cuts").getJSONObject(1);
        assertEquals("synthetic-original-capture-private", first.getString("shotId")); assertEquals(HERO, second.getString("shotId"));
        assertEquals(mapped.uri.toString(), first.getString("sourceUri"));
        assertIds(first, Arrays.asList(HERO, OLD, CLOSING), "creator-reviewed"); assertIds(second, Collections.emptyList(), "unassigned");
        assertEquals("whisper-tiny.en-draft", first.getString("captionOrigin")); assertEquals(mapped.caption, first.getString("caption"));
        assertEquals(500, first.getLong("inMs")); assertEquals(2500, first.getLong("outMs")); assertEquals(0, first.getLong("timelineStartMs"));
        assertEquals(2000, second.getLong("timelineStartMs"));
        assertCue(first.getJSONArray("subtitles").getJSONObject(0), 300, 700);
        assertNull("Old role-like IDs cannot match a new plan", frozen.find(OLD));
        assertEquals("Hero", frozen.find(HERO).title);
        document.getJSONObject("shotPlan").getJSONArray("shots").getJSONObject(0).put("title", "JSON caller mutation");
        assertPlan(frozen.toJson());
        JSONObject legacy = ReelExporter.editDocument("synthetic-old-api", cuts, "Synthetic", "Clean", Collections.emptyList());
        assertEquals(0, legacy.getJSONObject("shotPlan").getJSONArray("shots").length());
        Log.i("MiniFilmShotPlanExportTest", "SERIALIZER_PASS synthetic=true planShots=2 frozenFields=true exactIdOnly=true rawMappingsPreserved=true originalShotIdPreserved=true timelineUnchanged=true approvalInferred=false");
    }

    @Test(timeout = 60_000)
    public void realZipFreezesPlanAndAssignmentsBeforeSourceReadAndAddsPrivateSafeShootNotes() throws Exception {
        List<Shot> mutable = plan(); Shot hero = mutable.get(0), closing = mutable.get(1);
        ShotPlanSnapshot frozen = ShotPlanSnapshot.capture(mutable, LABEL);
        Take mapped = take("synthetic-private-origin-capture", "Private cut title", 500, 2500);
        mapped.reviewedShotIds.addAll(Arrays.asList(HERO, OLD, CLOSING));
        mapped.captionOrigin = "whisper-tiny.en-draft";
        mapped.subtitles.add(new SubtitleCue(800, 1200, "Synthetic words kept exactly"));
        Take unassigned = take(HERO, "Unassigned cut", 1000, 3000);
        List<Take> dispatched = Arrays.asList(unassigned, mapped);
        String expectedNotes = frozen.readableNotes(Arrays.asList(copy(unassigned), copy(mapped)));
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger opens = new AtomicInteger();
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
            assertEquals(Uri.fromFile(source), uri); opens.incrementAndGet(); opened.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new java.io.IOException("Synthetic source gate timed out");
            return new ProjectPackager.Source(new FileInputStream(source), source.length(), "video/mp4");
        }, ProjectPackager.MAX_ORIGINAL_BYTES);
        Result result = new Result();
        boolean retained = false;
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> packager.export(dispatched,
                    "Synthetic plan package", "Clean", frozen, result));
            assertTrue(opened.await(10, TimeUnit.SECONDS));
            mutatePlan(mutable, hero, closing);
            mapped.reviewedShotIds.clear(); mapped.reviewedShotIds.add("later-choice"); unassigned.reviewedShotIds.add(HERO);
            mapped.inMs = 1500; mapped.caption = "Later caption"; mapped.subtitles.clear();
            release.countDown(); result.await(); assertEquals(1, opens.get()); assertEquals(bytes, result.originalBytes);
            try (ZipFile zip = new ZipFile(result.file.get())) {
                assertEquals(4, zip.size()); assertNotNull(zip.getEntry("shoot-notes.txt"));
                JSONObject document = new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals("minifilm.portable-edit.v1", document.getString("schema")); assertPlan(document.getJSONObject("shotPlan"));
                assertEquals(4000, document.getLong("durationMs")); assertEquals(bytes, document.getLong("originalBytes"));
                JSONArray cuts = document.getJSONArray("cuts"); assertEquals(2, cuts.length());
                JSONObject first = cuts.getJSONObject(0), second = cuts.getJSONObject(1);
                assertIds(first, Collections.emptyList(), "unassigned"); assertIds(second, Arrays.asList(HERO, OLD, CLOSING), "creator-reviewed");
                assertEquals("Unassigned cut", first.getString("title")); assertEquals("Private cut title", second.getString("title"));
                assertEquals(1000, first.getLong("inMs")); assertEquals(3000, first.getLong("outMs"));
                assertEquals(500, second.getLong("inMs")); assertEquals(2500, second.getLong("outMs"));
                assertEquals(2000, second.getLong("timelineStartMs")); assertEquals(4000, second.getLong("timelineEndMs"));
                assertEquals("Synthetic full cut words", second.getString("caption")); assertEquals("whisper-tiny.en-draft", second.getString("captionOrigin"));
                assertCue(second.getJSONArray("subtitles").getJSONObject(0), 2300, 2700);
                for (int i = 0; i < cuts.length(); i++) { assertFalse(cuts.getJSONObject(i).has("shotId")); assertFalse(cuts.getJSONObject(i).has("sourceUri")); }
                JSONObject media = document.getJSONArray("media").getJSONObject(0); assertEquals(1, document.getJSONArray("media").length());
                assertEquals(hash, media.getString("sha256")); assertEquals(hash, sha(zip.getInputStream(zip.getEntry(media.getString("path")))));
                String notes = read(zip.getInputStream(zip.getEntry("shoot-notes.txt"))); assertEquals(expectedNotes, notes);
                assertTrue(notes.contains("1. Hero")); assertTrue(notes.contains("2. Closing")); assertTrue(notes.contains("Direction: face left"));
                assertTrue(notes.contains("Creator assignment: 1. Hero")); assertTrue(notes.contains("Creator assignment: 2. Closing"));
                assertTrue(notes.contains("Unknown or earlier-plan assignment")); assertTrue(notes.contains("No creator-reviewed shot assignment"));
                assertTrue(notes.contains("0.500–2.500s")); assertFalse(notes.contains(OLD)); assertFalse(notes.contains("Private cut title"));
                for (String text : Arrays.asList(document.toString(), notes)) {
                    assertFalse(text.contains(source.getAbsolutePath())); assertFalse(text.contains("file://")); assertFalse(text.contains("content://"));
                    assertFalse(text.contains("synthetic-private-origin-capture")); assertFalse(text.contains("later-choice")); assertFalse(text.contains("Later caption"));
                }
            }
            if ("true".equals(InstrumentationRegistry.getArguments().getString("retainSyntheticPlanPackage"))) {
                originalSourceAndPreferencesRemainIntact();
                retainVerifiedSyntheticPackage(result.file.get());
                retained = true;
            }
            Log.i("MiniFilmShotPlanExportTest", "ZIP_PASS synthetic=true currentPlanShots=2 selectedCuts=2 uniqueSources=1 frozenPlanAndMappings=true readableNotes=true unknownEarlierIdsLabelled=true originalHashIntact=true privateOriginsOmitted=true approvalInferred=false");
        } finally {
            release.countDown();
            if (retained) InstrumentationRegistry.getInstrumentation().runOnMainSync(packager::close);
            else closeAndDeleteOwnResult(packager, result);
        }
    }

    @Test(timeout = 45_000)
    public void legacyPackageApiKeepsThreeEntriesAndExplicitEmptyPlan() throws Exception {
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) ->
                new ProjectPackager.Source(new FileInputStream(source), source.length(), "video/mp4"), ProjectPackager.MAX_ORIGINAL_BYTES);
        Result result = new Result();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> packager.export(
                    Collections.singletonList(take("synthetic-old-origin", "Legacy cut", 500, 2500)), "Synthetic legacy", "Clean", result));
            result.await();
            try (ZipFile zip = new ZipFile(result.file.get())) {
                assertEquals(3, zip.size()); assertNull(zip.getEntry("shoot-notes.txt"));
                JSONObject document = new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals(0, document.getJSONObject("shotPlan").getJSONArray("shots").length());
                assertEquals(2000, document.getLong("durationMs"));
                assertFalse(document.getJSONArray("cuts").getJSONObject(0).has("shotId"));
                JSONObject media = document.getJSONArray("media").getJSONObject(0);
                assertEquals(hash, sha(zip.getInputStream(zip.getEntry(media.getString("path")))));
            }
            Log.i("MiniFilmShotPlanExportTest", "LEGACY_PASS synthetic=true zipEntries=3 shootNotesAbsent=true emptyPlan=true originalHashIntact=true");
        } finally { closeAndDeleteOwnResult(packager, result); }
    }

    @Test(timeout = 100_000)
    public void actualEncodedReelPublishesNonemptyFrozenPlanAndExplicitMapping() throws Exception {
        final String title = "Synthetic shot-plan publication";
        List<Shot> mutable = plan(); Shot hero = mutable.get(0), closing = mutable.get(1);
        ShotPlanSnapshot frozen = ShotPlanSnapshot.capture(mutable, LABEL);
        Take take = take("synthetic-plan-publication-source", "Synthetic cut", 1000, 2000);
        take.caption = "My jacket"; take.reviewedShotIds.add(HERO);
        ReelExporter exporter = new ReelExporter(context);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Uri> videoUri = new AtomicReference<>(), editUri = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(); AtomicBoolean mainCallback = new AtomicBoolean();
        AtomicInteger progress = new AtomicInteger();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                exporter.export(Collections.singletonList(take), title, "Clean", Collections.emptyList(), frozen,
                        new ReelExporter.Listener() {
                            public void onProgress(int percent) { progress.set(percent); }
                            public void onComplete(Uri video, Uri edit) {
                                mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                                videoUri.set(video); editUri.set(edit); done.countDown();
                            }
                            public void onError(String message) { error.set(message); done.countDown(); }
                        });
                // Production has isolated selected cuts; the immutable plan was captured
                // before dispatch. Exercise both without altering or rereading the source.
                mutatePlan(mutable, hero, closing);
                take.reviewedShotIds.clear(); take.reviewedShotIds.add(OLD);
                take.inMs = 1500; take.caption = "Later caption";
            });
            assertTrue("Actual encoder/publisher callback timed out", done.await(80, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNull(error.get(), error.get()); assertNotNull(videoUri.get()); assertNotNull(editUri.get()); assertTrue(mainCallback.get());
            JSONObject edit = new JSONObject(read(context.getContentResolver().openInputStream(editUri.get())));
            // Detach only this callback-proven, completed, known synthetic pair. Retain
            // MP4/JSON for inspection; never let future Main startup adopt this test reel.
            detachOwnCompletedSyntheticJournal(edit, title, videoUri.get());
            assertEquals(100, progress.get()); assertPlan(edit.getJSONObject("shotPlan"));
            assertEquals(1000, edit.getLong("durationMs"));
            JSONArray cuts = edit.getJSONArray("cuts"); assertEquals(1, cuts.length());
            JSONObject cut = cuts.getJSONObject(0);
            assertIds(cut, Collections.singletonList(HERO), "creator-reviewed");
            assertEquals(1000, cut.getLong("inMs")); assertEquals(2000, cut.getLong("outMs")); assertEquals(0, cut.getLong("timelineStartMs"));
            assertEquals("My jacket", cut.getString("caption")); assertEquals("manual", cut.getString("captionOrigin"));
            assertEquals(0, cut.getJSONArray("subtitles").length());
            MediaMetadataRetriever metadata = new MediaMetadataRetriever(); long encodedDuration;
            try {
                metadata.setDataSource(context, videoUri.get());
                assertEquals("720", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                assertEquals("1280", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                encodedDuration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Independent encoded duration differs from one-second cut", Math.abs(encodedDuration - 1000) <= 200);
                Bitmap frame = metadata.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull("Output must decode a real frame", frame);
                try { assertEquals(720, frame.getWidth()); assertEquals(1280, frame.getHeight()); }
                finally { frame.recycle(); }
            } finally { metadata.release(); }
            Log.i("MiniFilmShotPlanExportTest", "ENCODED_PLAN_PASS synthetic=true currentPlanShots=2 selectedCuts=1 nominalDurationMs=1000 encodedDurationMs="
                    + encodedDuration + " width=720 height=1280 planAndMappingsFrozen=true mainCallback=true readableFrame=true completedPairRetained=true ownJournalDetached=true captureOpened=false modelInference=false approvalInferred=false");
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(exporter::cancel);
        }
    }

    private void detachOwnCompletedSyntheticJournal(JSONObject edit, String title, Uri video) throws Exception {
        assertEquals("minifilm.edit.v1", edit.getString("schema")); assertTrue(edit.getBoolean("preEventResearch"));
        assertEquals(title, edit.getString("title"));
        String id = edit.getString("exportId");
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        JSONArray cuts = edit.getJSONArray("cuts"); assertEquals(1, cuts.length());
        assertEquals(Uri.fromFile(source).toString(), cuts.getJSONObject(0).getString("sourceUri"));
        assertEquals("synthetic-plan-publication-source", cuts.getJSONObject(0).getString("shotId"));
        ExportRecovery.Journal journal = ExportRecovery.Journal.read(context, id);
        assertEquals("COMPLETE", journal.state); assertEquals(video, journal.videoUri);
        assertTrue(journal.expectedBytes > 1000); assertTrue(journal.edit().isFile());
        new AtomicFile(new File(context.getFilesDir(), "export-journal/" + id + ".json")).delete();
    }

    private void retainVerifiedSyntheticPackage(File zip) throws Exception {
        File cache = context.getCacheDir().getCanonicalFile();
        assertTrue(zip.getName().matches("minifilm-pack-[0-9]+\\.zip"));
        assertEquals(cache, zip.getCanonicalFile().getParentFile());
        assertEquals(zip.getName(), zip.getCanonicalFile().getName());
        assertFalse(java.nio.file.Files.isSymbolicLink(zip.toPath()));
        assertTrue(zip.isFile()); assertTrue(zip.length() > 1000);
        JSONObject marker = new JSONObject().put("schema", "minifilm.synthetic.plan-package-retain.v1")
                .put("label", "Synthetic plan package · test-produced original-byte ZIP")
                .put("syntheticFixture", true).put("zipBasename", zip.getName())
                .put("bytes", zip.length()).put("sha256", sha(new FileInputStream(zip)));
        File target = new File(cache, "synthetic-plan-package-retain.json");
        assertFalse("Refuse an existing retention marker or backup", target.exists());
        assertFalse(new File(target.getPath() + ".bak").exists());
        assertTrue("Reserve only a new test-owned marker", target.createNewFile());
        boolean complete = false;
        try {
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write(marker.toString(2).getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
            }
            complete = true;
        } finally {
            if (!complete) assertTrue("Remove only this incomplete reserved test marker", target.delete());
        }
        Log.i("MiniFilmShotPlanExportTest", "PACKAGE_RETAINED synthetic=true verifiedZip=true durableMarker=true sourceMetadataInMarker=false");
    }

    private static List<Shot> plan() { return new ArrayList<>(Arrays.asList(
            new Shot(HERO, "Hero", "face left", "My jacket", 4000), new Shot(CLOSING, "Closing", "face forward", "See you", 3000))); }
    private static void mutatePlan(List<Shot> plan, Shot hero, Shot closing) {
        hero.id = OLD; hero.title = "Later hero"; hero.instruction = "Later direction"; hero.caption = "Later plan words"; hero.targetDurationMs = 50000;
        closing.id = "later-closing"; closing.title = "Later closing"; plan.clear();
    }
    private Take take(String id, String title, long in, long out) { Take take = new Take(Uri.fromFile(source), id, title, "Synthetic full cut words", 5746); take.inMs = in; take.outMs = out; return take; }
    private static Take copy(Take take) { Take copy = new Take(take.uri, take.shotId, take.title, take.caption, take.durationMs); copy.inMs = take.inMs; copy.outMs = take.outMs; copy.captionOrigin = take.captionOrigin; copy.reviewedShotIds = ReelExporter.reviewedShotIdsSnapshot(take); for (SubtitleCue cue : take.subtitles) copy.subtitles.add(new SubtitleCue(cue.startMs, cue.endMs, cue.text)); return copy; }
    private static void assertPlan(JSONObject plan) throws Exception {
        assertEquals("minifilm.shot-plan.v1", plan.getString("schema")); assertEquals(LABEL, plan.getString("sourceLabel"));
        JSONArray shots = plan.getJSONArray("shots"); assertEquals(2, shots.length());
        String[] ids = { HERO, CLOSING }, names = { "Hero", "Closing" }, instructions = { "face left", "face forward" }, captions = { "My jacket", "See you" }; long[] durations = { 4000, 3000 };
        for (int i = 0; i < 2; i++) { JSONObject shot = shots.getJSONObject(i); assertEquals(i + 1, shot.getInt("order")); assertEquals(ids[i], shot.getString("id")); assertEquals(names[i], shot.getString("title")); assertEquals(instructions[i], shot.getString("instruction")); assertEquals(captions[i], shot.getString("caption")); assertEquals(durations[i], shot.getLong("targetDurationMs")); }
    }
    private static void assertIds(JSONObject cut, List<String> ids, String origin) throws Exception { JSONArray actual = cut.getJSONArray("reviewedShotIds"); assertEquals(ids.size(), actual.length()); for (int i = 0; i < ids.size(); i++) assertEquals(ids.get(i), actual.getString(i)); assertEquals(origin, cut.getString("reviewedShotMappingOrigin")); }
    private static void assertCue(JSONObject cue, long start, long end) throws Exception { assertEquals("Synthetic words kept exactly", cue.getString("text")); assertEquals(800, cue.getLong("sourceStartMs")); assertEquals(1200, cue.getLong("sourceEndMs")); assertTrue(cue.getBoolean("visibleInCut")); assertEquals(start, cue.getLong("timelineStartMs")); assertEquals(end, cue.getLong("timelineEndMs")); }
    private static final class Result implements ProjectPackager.Listener {
        final CountDownLatch done = new CountDownLatch(1); final AtomicReference<File> file = new AtomicReference<>(); final AtomicReference<String> error = new AtomicReference<>(); final AtomicBoolean main = new AtomicBoolean(); long originalBytes;
        public void onProgress(long bytes) { }
        public void onComplete(File result, long bytes) { main.set(Looper.myLooper() == Looper.getMainLooper()); file.set(result); originalBytes = bytes; done.countDown(); }
        public void onError(String message) { error.set(message); done.countDown(); }
        void await() throws Exception { assertTrue("Actual ZIP timed out", done.await(30, TimeUnit.SECONDS)); assertNull(error.get(), error.get()); assertNotNull(file.get()); assertTrue(main.get()); }
    }
    private static void closeAndDeleteOwnResult(ProjectPackager packager, Result result) { InstrumentationRegistry.getInstrumentation().runOnMainSync(packager::close); if (result.file.get() != null) assertTrue("Only caller-owned test ZIP removed", result.file.get().delete()); }
    private static String read(InputStream input) throws Exception { assertNotNull(input); try (InputStream stream = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) { byte[] block = new byte[4096]; int count; while ((count = stream.read(block)) != -1) bytes.write(block, 0, count); return bytes.toString(StandardCharsets.UTF_8.name()); } }
    private static String sha(InputStream input) throws Exception { try (InputStream stream = input) { MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[8192]; int count; while ((count = stream.read(block)) != -1) hash.update(block, 0, count); return ProjectPackager.hex(hash.digest()); } }
    private void denied() { assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA)); assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)); }
}
