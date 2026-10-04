package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Looper;
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

/** Creator-authored synthetic mapping metadata only: pure real edit serializer + actual ZIP.
 * No model generation, automatic association, MP4 encoding, playback, Activity or recording.
 * ZIP retains its established omission of device-bound sourceUri/original shotId fields. */
@RunWith(AndroidJUnit4.class)
public final class ShotMappingExportTest {
    private static final String HERO = "plan-synthetic-hero", CLOSING = "plan-synthetic-closing";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File source; private long originalBytes, originalMtime; private String originalHash;
    private Map<String, ?> originalPreferences;

    @Before public void requireExistingLabelledSourceAndDeniedCapture() throws Exception {
        assertDenied(); originalPreferences = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        source = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Existing labelled synthetic source required; no source generation in this test", source.isFile());
        originalBytes = source.length(); originalMtime = source.lastModified(); originalHash = sha(new FileInputStream(source));
    }
    @After public void preserveSourcesPermissionsAndPreferences() throws Exception {
        if (originalHash != null) {
            assertTrue(source.isFile()); assertEquals(originalBytes, source.length()); assertEquals(originalMtime, source.lastModified());
            assertEquals(originalHash, sha(new FileInputStream(source)));
        }
        if (originalPreferences != null) assertEquals(originalPreferences, context.getSharedPreferences("shoot", 0).getAll());
        assertDenied();
    }

    @Test(timeout = 15_000)
    public void deviceEditSerializerKeepsOriginalShotFactsAndExplicitIndependentMultiMappings() throws Exception {
        Take mapped = take("synthetic-original-capture", "Mapped synthetic cut", 500, 2500);
        mapped.captionOrigin = "whisper-tiny.en-draft";
        mapped.subtitles.add(new SubtitleCue(800, 1200, "Synthetic unreviewed speech words"));
        mapped.reviewedShotIds.add(HERO); mapped.reviewedShotIds.add(CLOSING);
        // An original shotId matching a plan ID must not create an explicit reviewed mapping.
        Take unmapped = take(HERO, "Unmapped synthetic cut", 1000, 3000);
        Take mappedSnapshot = snapshot(mapped), unmappedSnapshot = snapshot(unmapped), independentSnapshot = snapshot(mapped);
        assertNotSame(mapped.reviewedShotIds, mappedSnapshot.reviewedShotIds);
        assertNotSame(mappedSnapshot.reviewedShotIds, independentSnapshot.reviewedShotIds);
        mapped.reviewedShotIds.clear(); mapped.reviewedShotIds.add("later-creator-choice");
        assertEquals(Arrays.asList(HERO, CLOSING), mappedSnapshot.reviewedShotIds);
        assertEquals(Arrays.asList(HERO, CLOSING), independentSnapshot.reviewedShotIds);
        JSONObject edit = ReelExporter.editDocument("synthetic-mapping-serializer", Arrays.asList(mappedSnapshot, unmappedSnapshot),
                "Synthetic shot-mapping test", "Clean", Collections.emptyList());
        JSONArray cuts = edit.getJSONArray("cuts"); assertEquals(2, cuts.length());
        assertEquals("minifilm.edit.v1", edit.getString("schema")); assertTrue(edit.getBoolean("preEventResearch"));
        assertEquals(4000, edit.getLong("durationMs"));
        assertTrue(edit.getString("shotMappingPolicy").contains("Explicit creator"));
        assertTrue(edit.getString("shotMappingPolicy").contains("do not establish quality or a current plan match"));
        JSONObject first = cuts.getJSONObject(0), second = cuts.getJSONObject(1);
        assertEquals("synthetic-original-capture", first.getString("shotId")); assertEquals(HERO, second.getString("shotId"));
        assertEquals(mapped.uri.toString(), first.getString("sourceUri")); assertEquals(unmapped.uri.toString(), second.getString("sourceUri"));
        assertMapping(first, Arrays.asList(HERO, CLOSING), "creator-reviewed"); assertMapping(second, Collections.emptyList(), "unassigned");
        assertEquals("whisper-tiny.en-draft", first.getString("captionOrigin")); assertEquals("manual", second.getString("captionOrigin"));
        assertEquals(mapped.caption, first.getString("caption")); assertEquals(unmapped.caption, second.getString("caption"));
        assertEquals(500, first.getLong("inMs")); assertEquals(2500, first.getLong("outMs")); assertEquals(0, first.getLong("timelineStartMs"));
        assertEquals(1000, second.getLong("inMs")); assertEquals(3000, second.getLong("outMs")); assertEquals(2000, second.getLong("timelineStartMs"));
        JSONObject cue = first.getJSONArray("subtitles").getJSONObject(0);
        assertEquals("Synthetic unreviewed speech words", cue.getString("text")); assertEquals(800, cue.getLong("sourceStartMs")); assertEquals(1200, cue.getLong("sourceEndMs"));
        assertTrue(cue.getBoolean("visibleInCut")); assertEquals(300, cue.getLong("timelineStartMs")); assertEquals(700, cue.getLong("timelineEndMs"));
        mappedSnapshot.reviewedShotIds.clear(); independentSnapshot.reviewedShotIds.remove(0);
        assertMapping(first, Arrays.asList(HERO, CLOSING), "creator-reviewed");
        assertEquals("synthetic-original-capture", mapped.shotId); assertEquals(HERO, unmapped.shotId);
        Take legacyNull = take("synthetic-legacy", "Legacy", 0, 3000); legacyNull.reviewedShotIds = null;
        assertTrue(ReelExporter.reviewedShotIdsSnapshot(legacyNull).isEmpty());
        Log.i("MiniFilmShotMappingExportTest", "SERIALIZER_PASS synthetic=true multiMappings=2 unmapped=1 originalShotIdPreserved=true independentLists=true captionOriginUnchanged=true automaticInference=false");
    }

    @Test(timeout = 60_000)
    public void realPortableZipKeepsReviewedMappingSnapshotAndTimelineWhileOriginalIdentifiersStayPrivate() throws Exception {
        Take mapped = take("synthetic-original-private-capture", "Mapped synthetic cut", 500, 2500);
        mapped.reviewedShotIds.add(HERO); mapped.reviewedShotIds.add(CLOSING);
        mapped.captionOrigin = "creator-reviewed-manual";
        mapped.subtitles.add(new SubtitleCue(800, 1200, "Synthetic timed words"));
        Take unmapped = take("synthetic-original-private-import", "Unmapped synthetic cut", 1000, 3000);
        Take excluded = take("synthetic-excluded", "Excluded", 0, 3000); excluded.selected = false; excluded.reviewedShotIds.add("excluded-mapping");
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicInteger sourceOpens = new AtomicInteger(); AtomicReference<File> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(); AtomicBoolean mainCallback = new AtomicBoolean();
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
            assertEquals(Uri.fromFile(source), uri); sourceOpens.incrementAndGet(); opened.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new java.io.IOException("Synthetic reader gate timed out");
            return new ProjectPackager.Source(new FileInputStream(source), source.length(), "video/mp4");
        }, ProjectPackager.MAX_ORIGINAL_BYTES);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> packager.export(Arrays.asList(unmapped, mapped, excluded),
                    "Synthetic portable shot mappings", "Clean", new ProjectPackager.Listener() {
                        public void onProgress(long bytes) { }
                        public void onComplete(File file, long bytes) { mainCallback.set(Looper.myLooper() == Looper.getMainLooper()); result.set(file); done.countDown(); }
                        public void onError(String message) { error.set(message); done.countDown(); }
                    }));
            assertTrue("Real ZIP source opener timed out", opened.await(10, TimeUnit.SECONDS));
            // The true package snapshot already exists, but no media bytes/JSON were written
            // past this source gate. Later caller choices must not replace that snapshot.
            mapped.reviewedShotIds.clear(); mapped.reviewedShotIds.add("later-mapped-choice");
            unmapped.reviewedShotIds.add("later-unmapped-choice"); excluded.reviewedShotIds.clear();
            release.countDown(); assertTrue("Actual portable ZIP timed out", done.await(30, TimeUnit.SECONDS));
            assertNull(error.get(), error.get()); assertNotNull(result.get()); assertTrue(mainCallback.get()); assertEquals(1, sourceOpens.get());
            try (ZipFile zip = new ZipFile(result.get())) {
                JSONObject project = new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals("minifilm.portable-edit.v1", project.getString("schema")); assertTrue(project.getBoolean("preEventResearch"));
                assertEquals(4000, project.getLong("durationMs")); assertEquals(originalBytes, project.getLong("originalBytes"));
                JSONArray cuts = project.getJSONArray("cuts"); assertEquals(2, cuts.length());
                JSONObject first = cuts.getJSONObject(0), second = cuts.getJSONObject(1);
                assertEquals("Unmapped synthetic cut", first.getString("title")); assertEquals("Mapped synthetic cut", second.getString("title"));
                assertMapping(first, Collections.emptyList(), "unassigned"); assertMapping(second, Arrays.asList(HERO, CLOSING), "creator-reviewed");
                for (int i = 0; i < cuts.length(); i++) {
                    JSONObject cut = cuts.getJSONObject(i); assertFalse(cut.has("sourceUri")); assertFalse(cut.has("shotId"));
                    assertEquals(i + 1, cut.getInt("order")); assertEquals(i * 2000, cut.getLong("timelineStartMs")); assertEquals(i * 2000 + 2000, cut.getLong("timelineEndMs"));
                }
                assertEquals(1000, first.getLong("inMs")); assertEquals(3000, first.getLong("outMs"));
                assertEquals(500, second.getLong("inMs")); assertEquals(2500, second.getLong("outMs"));
                assertEquals(unmapped.caption, first.getString("caption")); assertEquals(mapped.caption, second.getString("caption"));
                assertEquals("manual", first.getString("captionOrigin")); assertEquals("creator-reviewed-manual", second.getString("captionOrigin"));
                JSONObject cue = second.getJSONArray("subtitles").getJSONObject(0);
                assertEquals("Synthetic timed words", cue.getString("text")); assertEquals(800, cue.getLong("sourceStartMs")); assertEquals(1200, cue.getLong("sourceEndMs"));
                assertEquals(2300, cue.getLong("timelineStartMs")); assertEquals(2700, cue.getLong("timelineEndMs"));
                JSONArray media = project.getJSONArray("media"); assertEquals(1, media.length()); JSONObject original = media.getJSONObject(0);
                assertEquals(originalBytes, original.getLong("bytes")); assertEquals(originalHash, original.getString("sha256"));
                assertEquals(originalHash, sha(zip.getInputStream(zip.getEntry(original.getString("path")))));
                String serialized = project.toString(); assertFalse(serialized.contains(source.getAbsolutePath()));
                assertFalse(serialized.contains("file://")); assertFalse(serialized.contains("content://"));
                assertFalse(serialized.contains(mapped.shotId)); assertFalse(serialized.contains(unmapped.shotId));
                assertFalse(serialized.contains("later-mapped-choice")); assertFalse(serialized.contains("later-unmapped-choice")); assertFalse(serialized.contains("excluded-mapping"));
            }
            assertEquals(Collections.singletonList("later-mapped-choice"), mapped.reviewedShotIds);
            assertEquals(Collections.singletonList("later-unmapped-choice"), unmapped.reviewedShotIds);
            Log.i("MiniFilmShotMappingExportTest", "ZIP_PASS synthetic=true selectedCuts=2 deduplicatedSources=1 creatorMappings=2 unassigned=1 laterCallerEditsIsolated=true originalHashIntact=true privateOriginIdsOmitted=true automaticInference=false");
        } finally {
            release.countDown(); InstrumentationRegistry.getInstrumentation().runOnMainSync(packager::close);
            if (result.get() != null) assertTrue("Delete only returned test ZIP", result.get().delete());
        }
    }

    private Take take(String id, String title, long in, long out) { Take take = new Take(Uri.fromFile(source), id, title, "Synthetic manual words", 5746); take.inMs = in; take.outMs = out; return take; }
    private static Take snapshot(Take source) {
        Take copy = new Take(source.uri, source.shotId, source.title, source.caption, source.durationMs); copy.inMs = source.inMs; copy.outMs = source.outMs;
        copy.captionOrigin = source.captionOrigin; copy.reviewedShotIds = ReelExporter.reviewedShotIdsSnapshot(source);
        for (SubtitleCue cue : source.subtitles) copy.subtitles.add(new SubtitleCue(cue.startMs, cue.endMs, cue.text)); return copy;
    }
    private static void assertMapping(JSONObject cut, List<String> expected, String origin) throws Exception { JSONArray ids = cut.getJSONArray("reviewedShotIds"); assertEquals(expected.size(), ids.length()); for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), ids.getString(i)); assertEquals(origin, cut.getString("reviewedShotMappingOrigin")); }
    private static String read(InputStream input) throws Exception { assertNotNull(input); try (InputStream source = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) { byte[] block = new byte[4096]; int count; while ((count = source.read(block)) != -1) bytes.write(block, 0, count); return bytes.toString(StandardCharsets.UTF_8.name()); } }
    private static String sha(InputStream input) throws Exception { try (InputStream source = input) { MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[8192]; int count; while ((count = source.read(block)) != -1) hash.update(block, 0, count); return ProjectPackager.hex(hash.digest()); } }
    private void assertDenied() { assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA)); assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)); }
}
