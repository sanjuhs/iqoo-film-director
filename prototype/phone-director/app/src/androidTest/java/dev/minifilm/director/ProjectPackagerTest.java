package dev.minifilm.director;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Synthetic fixtures only; no picker, network, permissions, camera or private footage. */
@RunWith(AndroidJUnit4.class)
public final class ProjectPackagerTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 60_000) public void originalsDeduplicateAndRelativePathsTimelineCaptionsAndHashesResolve() throws Exception {
        File fixture = fixture();
        Take first = take(); first.inMs = 500; first.outMs = 2500;
        first.caption = "Synthetic manual caption";
        first.captionOrigin = "manual";
        Take second = take(); second.title = "Second reviewed cut";
        second.inMs = 1000; second.outMs = 3000; second.caption = "Fallback text";
        second.captionOrigin = "whisper-cpu-draft";
        second.subtitles.add(new SubtitleCue(800, 1400, "Clipped spoken cue"));
        second.subtitles.add(new SubtitleCue(100, 500, "Outside trimmed cut"));
        List<Take> cuts = new ArrayList<>(); cuts.add(second); cuts.add(first);
        String originalHash = sha(new FileInputStream(fixture));
        ProjectPackager packager = new ProjectPackager(context);
        Outcome result = export(packager, cuts);
        try {
            assertNull(result.error.get(), result.error.get()); assertNotNull(result.file.get());
            assertEquals(fixture.length(), result.bytes);
            try (ZipFile zip = new ZipFile(result.file.get())) {
                JSONObject project = new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals("minifilm.portable-edit.v1", project.getString("schema"));
                assertTrue(project.getBoolean("preEventResearch"));
                assertEquals(4000, project.getLong("durationMs"));
                JSONArray media = project.getJSONArray("media"); assertEquals(1, media.length());
                JSONObject source = media.getJSONObject(0);
                String path = source.getString("path");
                assertEquals("media/source-001.mp4", path);
                assertEquals("video/mp4", source.getString("mimeType"));
                ZipEntry actual = zip.getEntry(path); assertNotNull(actual);
                assertEquals(fixture.length(), actual.getSize());
                assertEquals(originalHash, source.getString("sha256"));
                assertEquals(originalHash, sha(zip.getInputStream(actual)));
                JSONArray saved = project.getJSONArray("cuts"); assertEquals(2, saved.length());
                for (int i = 0; i < saved.length(); i++) {
                    JSONObject cut = saved.getJSONObject(i);
                    assertEquals(i + 1, cut.getInt("order")); assertNotNull(zip.getEntry(cut.getString("mediaPath")));
                    assertEquals(i * 2000, cut.getLong("timelineStartMs"));
                    assertEquals(i * 2000 + 2000, cut.getLong("timelineEndMs"));
                    assertFalse(cut.has("sourceUri")); assertFalse(cut.has("shotId"));
                }
                JSONObject timed = saved.getJSONObject(0);
                assertEquals("Second reviewed cut", timed.getString("title"));
                assertEquals(1000, timed.getLong("inMs")); assertEquals(3000, timed.getLong("outMs"));
                assertEquals("whisper-cpu-draft", timed.getString("captionOrigin"));
                JSONObject cue = timed.getJSONArray("subtitles").getJSONObject(0);
                assertEquals(800, cue.getLong("sourceStartMs")); assertEquals(1400, cue.getLong("sourceEndMs"));
                assertTrue(cue.getBoolean("visibleInCut"));
                assertEquals(0, cue.getLong("timelineStartMs")); assertEquals(400, cue.getLong("timelineEndMs"));
                JSONObject invisible = timed.getJSONArray("subtitles").getJSONObject(1);
                assertFalse(invisible.getBoolean("visibleInCut")); assertFalse(invisible.has("timelineStartMs"));
                assertEquals("Synthetic manual caption", saved.getJSONObject(1).getString("caption"));
                assertEquals("manual", saved.getJSONObject(1).getString("captionOrigin"));
                String manifest = project.toString();
                assertFalse(manifest.contains(fixture.getAbsolutePath()));
                assertFalse(manifest.contains("file://")); assertFalse(manifest.contains("content://"));
                assertFalse(manifest.contains("synthetic-private-id"));
                assertNotNull(zip.getEntry("README.txt"));
                assertEquals(3, zip.size());
            }
            main.post(packager::close); idle();
            assertTrue("Closing must retain the successfully returned cache ZIP for the save picker", result.file.get().isFile());
            assertEquals(originalHash, sha(new FileInputStream(fixture)));
        } finally { main.post(packager::close); if (result.file.get() != null) result.file.get().delete(); }
    }

    @Test(timeout = 60_000) public void unknownProviderLengthEnforcesStreamingByteLimitAndPreservesSource() throws Exception {
        File fixture = fixture(); Set<String> before = packages();
        long smallLimit = Math.min(32 * 1024, fixture.length() - 1);
        assertTrue("Synthetic fixture needs at least two bytes", smallLimit > 0);
        ProjectPackager packager = new ProjectPackager(context,
                (uri, signal) -> new ProjectPackager.Source(new FileInputStream(fixture), -1, "video/mp4"), smallLimit);
        try {
            Outcome result = export(packager, Collections.singletonList(take()));
            assertNull(result.file.get()); assertNotNull(result.error.get());
            assertTrue(result.error.get(), result.error.get().contains("size limit"));
            assertEquals(before, packages()); assertTrue(fixture.isFile());
        } finally { main.post(packager::close); }
    }

    @Test(timeout = 60_000) public void revokedSelectedSourceRejectsAndDeletesOnlyItsPartial() throws Exception {
        Set<String> before = packages(); File fixture = fixture();
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
            throw new SecurityException("Do not expose this synthetic source URI: " + uri);
        }, ProjectPackager.MAX_ORIGINAL_BYTES);
        try {
            Outcome result = export(packager, Collections.singletonList(take()));
            assertNull(result.file.get()); assertNotNull(result.error.get());
            assertTrue(result.error.get(), result.error.get().contains("revoked"));
            assertFalse(result.error.get().contains(fixture.getAbsolutePath()));
            assertEquals(before, packages()); assertTrue(fixture.isFile());
        } finally { main.post(packager::close); }
    }

    @Test(timeout = 60_000) public void cancelThenNewGenerationSuppressesStaleCompletionAndPreservesReturnedZip() throws Exception {
        File fixture = fixture(); Set<String> before = packages();
        CountDownLatch readEntered = new CountDownLatch(1), streamClosed = new CountDownLatch(1);
        AtomicReference<Boolean> closedOnMain = new AtomicReference<>();
        AtomicInteger opens = new AtomicInteger(), oldCompletions = new AtomicInteger(), oldErrors = new AtomicInteger();
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
            FileInputStream input = new FileInputStream(fixture);
            if (opens.incrementAndGet() > 1) return new ProjectPackager.Source(input, -1, "video/mp4");
            return new ProjectPackager.Source(new java.io.FilterInputStream(input) {
                @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                    readEntered.countDown();
                    try {
                        if (!streamClosed.await(20, TimeUnit.SECONDS)) throw new java.io.IOException("Synthetic close timeout");
                    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                    throw new java.io.IOException("Synthetic provider read unblocked by close");
                }
                @Override public void close() throws java.io.IOException {
                    closedOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                    try { super.close(); } finally { streamClosed.countDown(); }
                }
            }, -1, "video/mp4");
        }, ProjectPackager.MAX_ORIGINAL_BYTES);
        Outcome first = new Outcome(); Outcome next = new Outcome();
        try {
            main.post(() -> packager.export(Collections.singletonList(takeUnchecked()), "Synthetic cancellation", "Clean",
                    new ProjectPackager.Listener() {
                        public void onProgress(long bytes) {}
                        public void onComplete(File zip, long bytes) { oldCompletions.incrementAndGet(); first.file.set(zip); first.done.countDown(); }
                        public void onError(String error) { oldErrors.incrementAndGet(); first.error.set(error); first.done.countDown(); }
                    }));
            assertTrue("Synthetic blocking stream was not entered", readEntered.await(10, TimeUnit.SECONDS));
            main.post(() -> {
                packager.cancel();
                packager.export(Collections.singletonList(takeUnchecked()), "Fresh synthetic generation", "Clean", next);
            });
            assertTrue(first.done.await(10, TimeUnit.SECONDS));
            assertTrue(first.error.get(), first.error.get().contains("cancelled"));
            assertTrue("Cancel must close the blocked stream without the test releasing it", streamClosed.await(5, TimeUnit.SECONDS));
            assertEquals("Provider close must not block the main thread", Boolean.FALSE, closedOnMain.get());
            assertTrue("New generation did not finish", next.done.await(20, TimeUnit.SECONDS));
            assertNull(next.error.get(), next.error.get()); assertNotNull(next.file.get()); idle();
            assertEquals(0, oldCompletions.get()); assertEquals(1, oldErrors.get());
            Set<String> after = packages(); after.remove(next.file.get().getName()); assertEquals(before, after);
            try (ZipFile zip = new ZipFile(next.file.get())) {
                JSONObject project = new JSONObject(read(zip.getInputStream(zip.getEntry("project.json"))));
                assertEquals("Fresh synthetic generation", project.getString("title"));
                assertEquals(sha(new FileInputStream(fixture)), project.getJSONArray("media").getJSONObject(0).getString("sha256"));
            }
            main.post(packager::close); idle(); assertTrue(next.file.get().isFile()); assertTrue(fixture.isFile());
        } finally {
            main.post(packager::close);
            if (next.file.get() != null) next.file.get().delete(); if (first.file.get() != null) first.file.get().delete();
        }
    }

    @Test(timeout = 60_000) public void excessCutsTimelineAndRemoteUriRejectBeforeOpeningAnySource() throws Exception {
        AtomicInteger opens = new AtomicInteger(); Set<String> before = packages();
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
            opens.incrementAndGet(); throw new AssertionError("Invalid selection must not open a source");
        }, ProjectPackager.MAX_ORIGINAL_BYTES);
        try {
            List<Take> tooMany = new ArrayList<>(); for (int i = 0; i < 13; i++) tooMany.add(take());
            Outcome count = export(packager, tooMany); assertTrue(count.error.get().contains("12 cuts"));
            Take longCut = take(); longCut.durationMs = 180_001; longCut.inMs = 0; longCut.outMs = 180_001;
            Outcome length = export(packager, Collections.singletonList(longCut)); assertNotNull(length.error.get());
            Take remote = take(); remote.uri = Uri.parse("https://example.invalid/synthetic.mp4");
            Outcome network = export(packager, Collections.singletonList(remote)); assertTrue(network.error.get().contains("local video"));
            assertEquals(0, opens.get()); assertEquals(before, packages());
        } finally { main.post(packager::close); }
    }

    @Test(timeout = 60_000) public void providerReturningAfterCancelClosesWithoutReadingAndNextGenerationFinishes() throws Exception {
        File fixture = fixture(); Set<String> before = packages();
        CountDownLatch opening = new CountDownLatch(1), cancellation = new CountDownLatch(1), lateClosed = new CountDownLatch(1);
        AtomicInteger opens = new AtomicInteger(), lateReads = new AtomicInteger();
        ProjectPackager packager = new ProjectPackager(context, (uri, signal) -> {
            FileInputStream file = new FileInputStream(fixture);
            if (opens.incrementAndGet() > 1) return new ProjectPackager.Source(file, -1, "video/mp4");
            signal.setOnCancelListener(cancellation::countDown);
            opening.countDown();
            if (!cancellation.await(10, TimeUnit.SECONDS)) { file.close(); throw new java.io.IOException("Synthetic opening cancel timeout"); }
            // Simulate a provider returning a usable descriptor despite racing with cancel.
            return new ProjectPackager.Source(new java.io.FilterInputStream(file) {
                @Override public int read(byte[] bytes, int offset, int length) throws java.io.IOException {
                    lateReads.incrementAndGet(); return super.read(bytes, offset, length);
                }
                @Override public void close() throws java.io.IOException {
                    try { super.close(); } finally { lateClosed.countDown(); }
                }
            }, -1, "video/mp4");
        }, ProjectPackager.MAX_ORIGINAL_BYTES);
        Outcome cancelled = new Outcome(), next = new Outcome();
        try {
            main.post(() -> packager.export(Collections.singletonList(takeUnchecked()), "Late open fixture", "Clean", cancelled));
            assertTrue("Synthetic opener was not entered", opening.await(5, TimeUnit.SECONDS));
            main.post(() -> {
                packager.cancel();
                packager.export(Collections.singletonList(takeUnchecked()), "Next after late open", "Clean", next);
            });
            assertTrue(cancelled.done.await(5, TimeUnit.SECONDS));
            assertTrue(cancelled.error.get(), cancelled.error.get().contains("cancelled"));
            assertTrue("Late returned source must be closed", lateClosed.await(5, TimeUnit.SECONDS));
            assertEquals("Cancelled late source must never be read", 0, lateReads.get());
            assertTrue("Next generation must make progress", next.done.await(20, TimeUnit.SECONDS));
            assertNull(next.error.get(), next.error.get()); assertNotNull(next.file.get());
            idle(); assertNull(cancelled.file.get());
            Set<String> after = packages(); after.remove(next.file.get().getName()); assertEquals(before, after);
            assertTrue(fixture.isFile());
        } finally { main.post(packager::close); if (next.file.get() != null) next.file.get().delete(); }
    }

    private File fixture() {
        File file = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Root must install the labelled synthetic spoken fixture", file.isFile()); return file;
    }
    private Take take() throws Exception {
        File source = fixture(); MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        long duration;
        try { metadata.setDataSource(source.getAbsolutePath()); duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
        assertTrue("Fixture must support synthetic 3-second cuts", duration >= 3000);
        Take take = new Take(Uri.fromFile(source), "synthetic-private-id", "Synthetic reviewed cut", "Synthetic caption", duration);
        take.inMs = 0; take.outMs = 2000; return take;
    }
    // No metadata work on the UI thread; fixture's measured duration is not needed for these 2s synthetic cuts.
    private Take takeUnchecked() {
        Take take = new Take(Uri.fromFile(fixture()), "synthetic-private-id", "Synthetic reviewed cut", "", 3000);
        take.outMs = 2000; return take;
    }
    private Outcome export(ProjectPackager packager, List<Take> takes) throws Exception {
        Outcome result = new Outcome();
        main.post(() -> packager.export(takes, "Synthetic portable package", "Clean", result));
        assertTrue("Package result timed out", result.done.await(30, TimeUnit.SECONDS)); return result;
    }
    private Set<String> packages() {
        Set<String> result = new HashSet<>(); File[] files = context.getCacheDir().listFiles();
        if (files != null) for (File file : files) if (file.getName().startsWith("minifilm-pack-")) result.add(file.getName());
        return result;
    }
    private static String sha(InputStream input) throws Exception {
        try (InputStream stream = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[8192]; int count;
            while ((count = stream.read(block)) != -1) digest.update(block, 0, count);
            return ProjectPackager.hex(digest.digest());
        }
    }
    private static String read(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096]; int count; while ((count = stream.read(block)) != -1) bytes.write(block, 0, count);
            return bytes.toString("UTF-8");
        }
    }
    private static void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private static final class Outcome implements ProjectPackager.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<File> file = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
        long bytes;
        public void onProgress(long bytes) {}
        public void onComplete(File zip, long total) { file.set(zip); bytes = total; done.countDown(); }
        public void onError(String message) { error.set(message); done.countDown(); }
    }
}
