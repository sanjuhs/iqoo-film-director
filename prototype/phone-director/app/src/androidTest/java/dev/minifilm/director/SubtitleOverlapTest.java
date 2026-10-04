package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Synthetic headless subtitle validation and real local encoding; no capture or playback. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleOverlapTest {
    private static final String TITLE = "Synthetic adjacent subtitle verification";
    private static final String FIRST = "ONE";
    private static final String SECOND = "SECOND ADJACENT WORDS";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 10_000)
    public void nestedPartialAndSameStartRejectWhileUnsortedAdjacentIntervalsStayExact() {
        for (long[][] pair : overlaps()) {
            for (boolean reversed : new boolean[] { false, true }) {
                List<SubtitleCue> cues = pair(pair, reversed);
                String before = signature(cues);
                try { SubtitleTimeline.requireNonOverlapping(cues); fail("Overlapping visible words were accepted"); }
                catch (IllegalArgumentException error) { assertEquals(SubtitleTimeline.OVERLAP_MESSAGE, error.getMessage()); }
                assertEquals("Validation must keep all words, times and creator list order", before, signature(cues));
            }
        }
        SubtitleCue later = new SubtitleCue(1100, 1600, "  Later words  ");
        SubtitleCue earlier = new SubtitleCue(600, 1100, "Earlier words");
        List<SubtitleCue> adjacent = new ArrayList<>(Arrays.asList(later, earlier));
        // Caller-specific null, empty and invalid-interval policies remain unchanged.
        adjacent.add(null); adjacent.add(new SubtitleCue(500, 1800, " \n "));
        adjacent.add(new SubtitleCue(1000, 1000, "Invalid zero duration"));
        adjacent.add(new SubtitleCue(400, 1700, null));
        String before = signature(adjacent);
        SubtitleTimeline.requireNonOverlapping(adjacent);
        SubtitleTimeline.requireNonOverlapping(null);
        assertSame(later, adjacent.get(0)); assertSame(earlier, adjacent.get(1));
        assertEquals(before, signature(adjacent));
        // Extreme endpoints must not overflow an arithmetic overlap check.
        SubtitleTimeline.requireNonOverlapping(Arrays.asList(
                new SubtitleCue(0, Long.MAX_VALUE, "First"),
                new SubtitleCue(Long.MIN_VALUE, 0, "Earlier")));
        Log.i("MiniFilmSubtitleTest", "INTERVALS_PASS overlapShapes=3 reversedOrder=true adjacency=true unchanged=true");
    }

    @Test(timeout = 45_000)
    public void overlapRejectsExportAndPackageBeforeEncoderProviderOrOutputCreation() throws Exception {
        assertCaptureDenied();
        File fixture = fixture(); FileState original = new FileState(fixture);
        Outputs before = outputs();
        int cases = 0;
        for (long[][] pair : overlaps()) for (boolean reversed : new boolean[] { false, true }) {
            Take take = take(fixture, "synthetic-overlap-rejection");
            take.subtitles.addAll(pair(pair, reversed));
            String wordsBefore = signature(take.subtitles);
            AtomicInteger progress = new AtomicInteger();
            ReelExporter exporter = new ReelExporter(context);
            ExportOutcome video = export(exporter, Collections.singletonList(take), progress);
            assertNull(video.video.get()); assertNull(video.edit.get());
            assertEquals(SubtitleTimeline.OVERLAP_MESSAGE, video.error.get());
            assertTrue(video.mainCallback.get());
            assertEquals("Invalid subtitles must not start Transformer", 0, progress.get());
            AtomicInteger opened = new AtomicInteger();
            ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
                opened.incrementAndGet(); throw new AssertionError("Overlap preflight opened source media");
            }, ProjectPackager.MAX_ORIGINAL_BYTES);
            try {
                PackageOutcome zip = pack(packager, Collections.singletonList(take));
                assertNull(zip.file.get()); assertEquals(SubtitleTimeline.OVERLAP_MESSAGE, zip.error.get());
                assertTrue(zip.mainCallback.get()); assertEquals(0, zip.progress.get()); assertEquals(0, opened.get());
            } finally { main.post(packager::close); idle(); }
            assertEquals(wordsBefore, signature(take.subtitles));
            before.assertSame(outputs()); original.assertSame(fixture); cases++;
        }
        assertCaptureDenied();
        Log.i("MiniFilmSubtitleTest", "PREFLIGHT_PASS cases=" + cases
                + " encoderStarted=false providerOpened=false newOutputs=0 originalUnchanged=true");
    }

    @Test(timeout = 180_000)
    public void unsortedAdjacentCuesRenderBothWordsAcrossTrimmedCutsAndRemainPortable() throws Exception {
        assertCaptureDenied();
        File fixture = fixture(); FileState original = new FileState(fixture);
        List<Take> takes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Take take = take(fixture, "synthetic-adjacent-" + i);
            // Deliberately reverse chronological list order; validation must not sort the source list.
            take.subtitles.add(new SubtitleCue(1100, 1600, SECOND));
            take.subtitles.add(new SubtitleCue(600, 1100, FIRST)); takes.add(take);
        }
        String wordsBefore = signature(takes.get(0).subtitles);
        ReelExporter exporter = new ReelExporter(context);
        ExportOutcome exported = export(exporter, takes, new AtomicInteger());
        assertNull(exported.error.get(), exported.error.get());
        assertNotNull(exported.video.get()); assertNotNull(exported.edit.get()); assertTrue(exported.mainCallback.get());
        JSONObject edit = json(exported.edit.get());
        detachOnlyThisSyntheticJournal(edit, takes);
        assertTimeline(edit.getJSONArray("cuts"), false);
        assertEquals(4000, edit.getLong("durationMs"));
        MediaMetadataRetriever video = new MediaMetadataRetriever();
        long duration;
        try {
            video.setDataSource(context, exported.video.get());
            assertEquals("720", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("1280", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            duration = Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue("Independent encoded duration", Math.abs(duration - 4000) <= 150);
            for (int i = 0; i < 2; i++) {
                int early = whitePixels(video, i * 2000 + 300);
                int later = whitePixels(video, i * 2000 + 800);
                assertTrue("Short adjacent cue missing on cut " + i, early > 20);
                assertTrue("Distinct longer adjacent cue missing on cut " + i, later > early + 50);
                assertEquals("No timed caption before the first cue", 0, whitePixels(video, i * 2000 + 40));
                assertEquals("No timed caption after the second cue", 0, whitePixels(video, i * 2000 + 1300));
            }
        } finally { video.release(); main.post(exporter::cancel); idle(); }
        ProjectPackager packager = new ProjectPackager(context);
        PackageOutcome packaged = pack(packager, takes);
        try {
            assertNull(packaged.error.get(), packaged.error.get()); assertNotNull(packaged.file.get());
            assertTrue(packaged.mainCallback.get());
            try (ZipFile zip = new ZipFile(packaged.file.get())) {
                JSONObject project = new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals(4000, project.getLong("durationMs")); assertTimeline(project.getJSONArray("cuts"), true);
                JSONArray media = project.getJSONArray("media"); assertEquals(1, media.length());
                JSONObject source = media.getJSONObject(0);
                assertEquals(fixture.length(), source.getLong("bytes")); assertEquals(original.hash, source.getString("sha256"));
                assertEquals(original.hash, sha(zip.getInputStream(zip.getEntry(source.getString("path")))));
            }
        } finally {
            main.post(packager::close); idle();
            if (packaged.file.get() != null) assertTrue("Delete only returned test ZIP", packaged.file.get().delete());
            original.assertSame(fixture); assertCaptureDenied();
        }
        for (Take take : takes) assertEquals(wordsBefore, signature(take.subtitles));
        Log.i("MiniFilmSubtitleTest", "ADJACENT_PASS encodedDurationMs=" + duration
                + " cuts=2 unsortedOrderRetained=true bothCaptionShapesVisible=true mappedTimeline=true portableSourceHash=true");
    }

    private static List<long[][]> overlaps() {
        return Arrays.asList(new long[][] { { 600, 1800 }, { 900, 1200 } },
                new long[][] { { 600, 1200 }, { 1000, 1600 } },
                new long[][] { { 600, 1200 }, { 600, 1600 } });
    }
    private static List<SubtitleCue> pair(long[][] pair, boolean reversed) {
        SubtitleCue first = new SubtitleCue(pair[0][0], pair[0][1], "Opening words");
        SubtitleCue second = new SubtitleCue(pair[1][0], pair[1][1], "Later words");
        return new ArrayList<>(reversed ? Arrays.asList(second, first) : Arrays.asList(first, second));
    }
    private static String signature(List<SubtitleCue> cues) {
        StringBuilder result = new StringBuilder();
        for (SubtitleCue cue : cues) result.append(cue == null ? "null" : cue.startMs + ":" + cue.endMs + ":" + cue.text).append('\n');
        return result.toString();
    }
    private File fixture() throws Exception {
        File fixture = new File(context.getFilesDir(), "synthetic-demo/synthetic-1.mp4");
        assertTrue("Install the existing labelled synthetic demo; this test does not generate media", fixture.isFile());
        assertTrue(fixture.length() > 1000);
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(fixture.getAbsolutePath());
            long duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue("Known three-second synthetic source", Math.abs(duration - 3000) <= 100);
        } finally { metadata.release(); }
        return fixture;
    }
    private static Take take(File file, String id) {
        Take take = new Take(Uri.fromFile(file), id, "Synthetic subtitle fixture", "", 3000);
        take.inMs = 500; take.outMs = 2500; take.captionOrigin = "manual-reviewed"; return take;
    }
    private ExportOutcome export(ReelExporter exporter, List<Take> takes, AtomicInteger progress) throws Exception {
        ExportOutcome result = new ExportOutcome(); CountDownLatch done = new CountDownLatch(1);
        main.post(() -> exporter.export(takes, TITLE, "Clean", new ReelExporter.Listener() {
            public void onProgress(int percent) { progress.incrementAndGet(); }
            public void onComplete(Uri video, Uri edit) {
                result.mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                result.video.set(video); result.edit.set(edit); done.countDown();
            }
            public void onError(String message) {
                result.mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                result.error.set(message); done.countDown();
            }
        }));
        boolean complete = done.await(90, TimeUnit.SECONDS);
        if (!complete) { main.post(exporter::cancel); idle(); }
        assertTrue("Synthetic export callback timed out", complete); idle(); return result;
    }
    private PackageOutcome pack(ProjectPackager packager, List<Take> takes) throws Exception {
        PackageOutcome result = new PackageOutcome(); CountDownLatch done = new CountDownLatch(1);
        main.post(() -> packager.export(takes, TITLE, "Clean", new ProjectPackager.Listener() {
            public void onProgress(long bytes) { result.progress.incrementAndGet(); }
            public void onComplete(File file, long bytes) {
                result.mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                result.file.set(file); done.countDown();
            }
            public void onError(String message) {
                result.mainCallback.set(Looper.myLooper() == Looper.getMainLooper());
                result.error.set(message); done.countDown();
            }
        }));
        boolean complete = done.await(60, TimeUnit.SECONDS);
        if (!complete) { main.post(packager::cancel); idle(); }
        assertTrue("Synthetic package callback timed out", complete); idle(); return result;
    }
    private void detachOnlyThisSyntheticJournal(JSONObject edit, List<Take> takes) throws Exception {
        assertEquals("minifilm.edit.v1", edit.getString("schema"));
        assertEquals(TITLE, edit.getString("title")); assertTrue(edit.getBoolean("preEventResearch"));
        String id = edit.getString("exportId");
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        JSONArray cuts = edit.getJSONArray("cuts"); assertEquals(takes.size(), cuts.length());
        for (int i = 0; i < takes.size(); i++) {
            assertEquals(takes.get(i).uri.toString(), cuts.getJSONObject(i).getString("sourceUri"));
            assertEquals("synthetic-adjacent-" + i, cuts.getJSONObject(i).getString("shotId"));
        }
        // Retain this completed synthetic MP4/JSON, but never adopt it into saved creator
        // state on a later Activity launch. No other journal, gallery row or source is touched.
        new AtomicFile(new File(context.getFilesDir(), "export-journal/" + id + ".json")).delete();
    }
    private static void assertTimeline(JSONArray cuts, boolean portable) throws Exception {
        assertEquals(2, cuts.length());
        for (int i = 0; i < 2; i++) {
            JSONObject cut = cuts.getJSONObject(i); assertEquals(500, cut.getLong("inMs")); assertEquals(2500, cut.getLong("outMs"));
            assertEquals(i * 2000, cut.getLong("timelineStartMs"));
            if (portable) assertFalse(cut.has("sourceUri"));
            JSONArray cues = cut.getJSONArray("subtitles"); assertEquals(2, cues.length());
            JSONObject later = cues.getJSONObject(0), early = cues.getJSONObject(1);
            assertEquals(SECOND, later.getString("text")); assertEquals(1100, later.getLong("sourceStartMs")); assertEquals(1600, later.getLong("sourceEndMs"));
            assertEquals(FIRST, early.getString("text")); assertEquals(600, early.getLong("sourceStartMs")); assertEquals(1100, early.getLong("sourceEndMs"));
            assertTrue(later.getBoolean("visibleInCut")); assertTrue(early.getBoolean("visibleInCut"));
            assertEquals(i * 2000 + 600, later.getLong("timelineStartMs")); assertEquals(i * 2000 + 1100, later.getLong("timelineEndMs"));
            assertEquals(i * 2000 + 100, early.getLong("timelineStartMs")); assertEquals(i * 2000 + 600, early.getLong("timelineEndMs"));
        }
    }
    private static int whitePixels(MediaMetadataRetriever video, long timeMs) {
        Bitmap frame = video.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
        assertNotNull(frame);
        try {
            int count = 0;
            for (int y = 1000; y < 1120; y++) for (int x = 50; x < 670; x++) {
                int pixel = frame.getPixel(x, y);
                if (Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) count++;
            }
            return count;
        } finally { frame.recycle(); }
    }
    private Outputs outputs() {
        Set<String> cache = new HashSet<>();
        File[] files = context.getCacheDir().listFiles();
        if (files != null) for (File file : files)
            if (file.getName().startsWith("reel-") || file.getName().startsWith("minifilm-pack-")) cache.add(file.getName());
        Set<Long> gallery = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                new String[] { MediaStore.Video.Media._ID },
                MediaStore.Video.Media.OWNER_PACKAGE_NAME + "=? AND " + MediaStore.Video.Media.RELATIVE_PATH + "=?",
                new String[] { context.getPackageName(), "Movies/MiniFilm/" }, null)) {
            assertNotNull("Owned gallery metadata query unavailable", cursor);
            assertTrue("Bound owned gallery query", cursor.getCount() <= 256);
            while (cursor.moveToNext()) gallery.add(cursor.getLong(0));
        }
        return new Outputs(cache, names(new File(context.getFilesDir(), "exports")),
                names(new File(context.getFilesDir(), "export-journal")), gallery);
    }
    private static Set<String> names(File folder) {
        Set<String> result = new HashSet<>(); File[] files = folder.listFiles();
        if (files != null) for (File file : files) result.add(file.getName()); return result;
    }
    private JSONObject json(Uri uri) throws Exception { return new JSONObject(read(context.getContentResolver().openInputStream(uri))); }
    private static String read(InputStream input) throws Exception {
        assertNotNull(input);
        try (InputStream source = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096]; int count;
            while ((count = source.read(block)) != -1) bytes.write(block, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static String sha(InputStream input) throws Exception {
        try (InputStream source = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[8192]; int count;
            while ((count = source.read(block)) != -1) digest.update(block, 0, count);
            return ProjectPackager.hex(digest.digest());
        }
    }
    private void assertCaptureDenied() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
    }
    private static void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private static final class FileState {
        final long bytes, mtime; final String hash;
        FileState(File file) throws Exception { bytes = file.length(); mtime = file.lastModified(); hash = sha(new FileInputStream(file)); }
        void assertSame(File file) throws Exception {
            assertTrue(file.isFile()); assertEquals(bytes, file.length()); assertEquals(mtime, file.lastModified()); assertEquals(hash, sha(new FileInputStream(file)));
        }
    }
    private static final class Outputs {
        final Set<String> cache, edits, journals; final Set<Long> gallery;
        Outputs(Set<String> cache, Set<String> edits, Set<String> journals, Set<Long> gallery) {
            this.cache = cache; this.edits = edits; this.journals = journals; this.gallery = gallery;
        }
        void assertSame(Outputs actual) { assertEquals(cache, actual.cache); assertEquals(edits, actual.edits); assertEquals(journals, actual.journals); assertEquals(gallery, actual.gallery); }
    }
    private static final class ExportOutcome {
        final AtomicReference<Uri> video = new AtomicReference<>(), edit = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>(); final AtomicBoolean mainCallback = new AtomicBoolean();
    }
    private static final class PackageOutcome {
        final AtomicReference<File> file = new AtomicReference<>(); final AtomicReference<String> error = new AtomicReference<>();
        final AtomicInteger progress = new AtomicInteger(); final AtomicBoolean mainCallback = new AtomicBoolean();
    }
}
