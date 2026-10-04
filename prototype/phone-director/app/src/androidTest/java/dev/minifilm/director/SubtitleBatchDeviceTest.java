package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real tiny.en CPU reads and Media3 encoding of existing synthetic speech fixtures only.
 * No Activity, recording, audio playback, preferences or downloads. Drafts remain unreviewed.
 * This proves the bounded fixture pipeline, not general ASR accuracy or attended capture. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleBatchDeviceTest {
    private static final String TITLE = "Synthetic batch subtitle verification";
    private static final String ORIGIN = "whisper-tiny.en-draft";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 240_000)
    public void actualSerialUniqueSpeechReadsDeliverIndependentDraftsAndExportThreeSourceTimelines() throws Exception {
        assertCaptureDenied();
        File shortFile = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        File paddedFile = new File(context.getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Existing labelled synthetic short speech fixture required", shortFile.isFile());
        assertTrue("Existing labelled synthetic padded speech fixture required", paddedFile.isFile());
        FileState shortOriginal = new FileState(shortFile), paddedOriginal = new FileState(paddedFile);
        long shortDuration = duration(shortFile), paddedDuration = duration(paddedFile);
        assertTrue("Known synthetic short duration", Math.abs(shortDuration - 5746) <= 200);
        assertTrue("Known synthetic padded duration", Math.abs(paddedDuration - 8759) <= 200);
        Take first = take(shortFile, shortDuration, "synthetic-batch-short");
        Take second = take(paddedFile, paddedDuration, "synthetic-batch-padded");
        Take duplicate = take(shortFile, shortDuration, "synthetic-batch-duplicate");
        Take manual = take(shortFile, shortDuration, "synthetic-batch-manual-skip");
        manual.subtitles.add(new SubtitleCue(500, 1000, "Existing creator manual words"));
        manual.captionOrigin = "creator-reviewed-manual";
        Take unselected = take(paddedFile, paddedDuration, "synthetic-batch-unselected-skip");
        unselected.selected = false;
        List<Take> inputs = Arrays.asList(first, second, duplicate, manual, unselected);
        List<Take> exportedTakes = Arrays.asList(first, second, duplicate);
        List<SubtitleCue> manualBefore = manual.subtitles, unselectedBefore = unselected.subtitles;

        ClipTranscriber probe = new ClipTranscriber(context);
        boolean modelAvailable = probe.isModelAvailable(); probe.close();
        assertTrue("Existing installed tiny.en weights and CPU runtime required", modelAvailable);
        AtomicInteger reads = new AtomicInteger(), inFlight = new AtomicInteger(), maximumInFlight = new AtomicInteger();
        AtomicInteger idleReaders = new AtomicInteger();
        Set<Uri> readSources = Collections.synchronizedSet(new HashSet<>());
        List<ClipTranscriber> realReaders = Collections.synchronizedList(new ArrayList<>());
        SubtitleBatch batch = new SubtitleBatch(context, new SubtitleBatch.ReaderFactory() {
            public boolean isModelAvailable() { return modelAvailable; }
            public SubtitleBatch.Reader create() {
                ClipTranscriber reader = new ClipTranscriber(context); realReaders.add(reader);
                AtomicBoolean closedOnce = new AtomicBoolean();
                return new SubtitleBatch.Reader() {
                    public void transcribe(Uri source, ClipTranscriber.Listener listener) {
                        reads.incrementAndGet(); readSources.add(source);
                        maximumInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        reader.transcribe(source, listener);
                    }
                    public void closeWhenIdle(Runnable idle) {
                        reader.closeWhenIdle(() -> {
                            if (closedOnce.compareAndSet(false, true)) { inFlight.decrementAndGet(); idleReaders.incrementAndGet(); }
                            idle.run();
                        });
                    }
                };
            }
        });
        CountDownLatch batchDone = new CountDownLatch(1);
        IdentityHashMap<Take, List<SubtitleCue>> delivered = new IdentityHashMap<>();
        IdentityHashMap<Take, Long> speechElapsed = new IdentityHashMap<>();
        List<Integer> progress = new ArrayList<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        AtomicReference<String> batchError = new AtomicReference<>();
        AtomicInteger draftCount = new AtomicInteger(), failedCount = new AtomicInteger(), skippedCount = new AtomicInteger();
        AtomicInteger completes = new AtomicInteger(), cancellations = new AtomicInteger();
        AtomicBoolean allCallbacksMain = new AtomicBoolean(true);
        long started = SystemClock.elapsedRealtime(), batchElapsed;
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertTrue(batch.isModelAvailable());
                batch.start(inputs, new SubtitleBatch.Listener() {
                    private void mainCallback() { if (Looper.myLooper() != Looper.getMainLooper()) allCallbacksMain.set(false); }
                    public void onProgress(int completed, int total) {
                        mainCallback();
                        try { assertEquals(2, total); assertTrue(completed >= 0 && completed <= total); progress.add(completed); }
                        catch (Throwable error) { callbackFailure.compareAndSet(null, error); }
                    }
                    public void onDraft(Take take, List<SubtitleCue> cues, long elapsedMs) {
                        mainCallback();
                        try {
                            assertTrue(take == first || take == second || take == duplicate);
                            assertFalse("One draft per Take", delivered.containsKey(take));
                            assertTrue("Resource drain precedes a delivered draft", inFlight.get() == 0);
                            delivered.put(take, cues); speechElapsed.put(take, elapsedMs);
                            // Same in-memory application as Main; no save() or preferences used.
                            take.subtitles = cues; take.captionOrigin = ORIGIN;
                        } catch (Throwable error) { callbackFailure.compareAndSet(null, error); }
                    }
                    public void onComplete(int drafted, int failed, int skipped) {
                        mainCallback(); draftCount.set(drafted); failedCount.set(failed); skippedCount.set(skipped);
                        completes.incrementAndGet(); batchDone.countDown();
                    }
                    public void onCancelled(int drafted) { mainCallback(); cancellations.incrementAndGet(); batchDone.countDown(); }
                    public void onError(String message) { mainCallback(); batchError.set(message); batchDone.countDown(); }
                });
            });
            assertTrue("Actual serial synthetic transcription timed out", batchDone.await(100, TimeUnit.SECONDS));
            idle(); batchElapsed = SystemClock.elapsedRealtime() - started;
            assertNull(batchError.get(), batchError.get()); assertNull(callbackFailure.get());
            assertEquals(1, completes.get()); assertEquals(0, cancellations.get()); assertTrue(allCallbacksMain.get());
            assertEquals(3, draftCount.get()); assertEquals(0, failedCount.get()); assertEquals(2, skippedCount.get());
            assertEquals(2, reads.get()); assertEquals(2, realReaders.size()); assertEquals(2, idleReaders.get());
            assertEquals(0, inFlight.get()); assertEquals("Real native readers must never overlap", 1, maximumInFlight.get());
            assertEquals(new HashSet<>(Arrays.asList(first.uri, second.uri)), readSources);
            assertFalse(progress.isEmpty()); assertEquals(Integer.valueOf(2), progress.get(progress.size() - 1));
            for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) >= progress.get(i - 1));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> assertFalse(batch.isRunning()));
            for (Take take : exportedTakes) {
                assertSame(delivered.get(take), take.subtitles); assertEquals(ORIGIN, take.captionOrigin);
                assertCues(take.subtitles, take.durationMs); assertTrue(speechElapsed.get(take) > 0);
            }
            assertNotSame(first.subtitles, duplicate.subtitles); assertEquals(first.subtitles.size(), duplicate.subtitles.size());
            for (int i = 0; i < first.subtitles.size(); i++) {
                SubtitleCue a = first.subtitles.get(i), b = duplicate.subtitles.get(i);
                assertNotSame(a, b); assertEquals(a.startMs, b.startMs); assertEquals(a.endMs, b.endMs); assertEquals(a.text, b.text);
            }
            assertSame(manualBefore, manual.subtitles); assertEquals(1, manual.subtitles.size());
            assertEquals("Existing creator manual words", manual.subtitles.get(0).text);
            assertEquals(500, manual.subtitles.get(0).startMs); assertEquals(1000, manual.subtitles.get(0).endMs);
            assertEquals("creator-reviewed-manual", manual.captionOrigin);
            assertSame(unselectedBefore, unselected.subtitles); assertTrue(unselected.subtitles.isEmpty()); assertFalse(unselected.selected);
            shortOriginal.assertSame(shortFile); paddedOriginal.assertSame(paddedFile);

            long nominal = shortDuration * 2 + paddedDuration;
            long exportStarted = SystemClock.elapsedRealtime();
            Exported exported = export(exportedTakes);
            long exportElapsed = SystemClock.elapsedRealtime() - exportStarted;
            JSONObject json = json(exported.edit); detachOnlyThisSyntheticCompleteJournal(json, exported, exportedTakes);
            assertEquals(nominal, json.getLong("durationMs"));
            JSONArray cuts = json.getJSONArray("cuts"); assertEquals(3, cuts.length());
            MediaMetadataRetriever video = new MediaMetadataRetriever(); long actualDuration;
            int[] captionPixels = new int[3];
            try {
                video.setDataSource(context, exported.video);
                assertEquals("720", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                assertEquals("1280", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                actualDuration = Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Independently measured encoded duration", Math.abs(actualDuration - nominal) <= 300);
                long timeline = 0;
                for (int i = 0; i < exportedTakes.size(); i++) {
                    Take take = exportedTakes.get(i); JSONObject cut = cuts.getJSONObject(i);
                    assertEquals(take.shotId, cut.getString("shotId")); assertEquals(take.uri.toString(), cut.getString("sourceUri"));
                    assertEquals(0, cut.getLong("inMs")); assertEquals(take.durationMs, cut.getLong("outMs"));
                    assertEquals(timeline, cut.getLong("timelineStartMs")); assertEquals(ORIGIN, cut.getString("captionOrigin"));
                    JSONArray cues = cut.getJSONArray("subtitles"); assertEquals(take.subtitles.size(), cues.length());
                    for (int j = 0; j < cues.length(); j++) {
                        SubtitleCue expected = take.subtitles.get(j); JSONObject cue = cues.getJSONObject(j);
                        assertEquals(expected.text, cue.getString("text")); assertEquals(expected.startMs, cue.getLong("sourceStartMs"));
                        assertEquals(expected.endMs, cue.getLong("sourceEndMs")); assertTrue(cue.getBoolean("visibleInCut"));
                        assertEquals(timeline + expected.startMs, cue.getLong("timelineStartMs"));
                        assertEquals(timeline + expected.endMs, cue.getLong("timelineEndMs"));
                    }
                    SubtitleCue sample = longest(take.subtitles);
                    captionPixels[i] = whitePixels(video, timeline + (sample.startMs + sample.endMs) / 2);
                    assertTrue("Decoded synthetic caption appearance missing on cut " + i, captionPixels[i] > 20);
                    timeline += take.durationMs;
                }
            } finally { video.release(); }
            shortOriginal.assertSame(shortFile); paddedOriginal.assertSame(paddedFile); assertCaptureDenied();
            Log.i("MiniFilmSubtitleBatchTest", new JSONObject().put("stage", "pipeline_pass")
                    .put("syntheticSources", true).put("backend", "tiny.en local CPU")
                    .put("uniqueNativeReads", reads.get()).put("maxReadersInFlight", maximumInFlight.get())
                    .put("draftedTakes", draftCount.get()).put("failedTakes", failedCount.get()).put("skippedTakes", skippedCount.get())
                    .put("shortSourceDurationMs", shortDuration).put("paddedSourceDurationMs", paddedDuration)
                    .put("shortAsrElapsedMs", speechElapsed.get(first)).put("paddedAsrElapsedMs", speechElapsed.get(second))
                    .put("batchElapsedMs", batchElapsed).put("exportElapsedMs", exportElapsed)
                    .put("nominalTimelineMs", nominal).put("encodedDurationMs", actualDuration)
                    .put("captionPixels", new JSONArray(captionPixels)).put("independentDuplicateDrafts", true)
                    .put("draftsReviewed", false).put("mainCallbacks", allCallbacksMain.get())
                    .put("originalsUnchanged", true).put("microphoneOpened", false).toString());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(batch::close);
            for (ClipTranscriber reader : new ArrayList<>(realReaders)) {
                reader.close();
                Field workerField = ClipTranscriber.class.getDeclaredField("worker"); workerField.setAccessible(true);
                assertTrue("Real reader worker must finish", ((ExecutorService) workerField.get(reader)).awaitTermination(25, TimeUnit.SECONDS));
            }
            shortOriginal.assertSame(shortFile); paddedOriginal.assertSame(paddedFile); assertCaptureDenied();
        }
    }

    private static Take take(File file, long duration, String id) {
        return new Take(Uri.fromFile(file), id, "Synthetic batch source", "", duration);
    }
    private static void assertCues(List<SubtitleCue> cues, long duration) {
        assertNotNull(cues); assertFalse(cues.isEmpty()); StringBuilder words = new StringBuilder();
        for (SubtitleCue cue : cues) {
            assertNotNull(cue); assertTrue(cue.startMs >= 0); assertTrue(cue.endMs > cue.startMs); assertTrue(cue.endMs <= duration);
            assertNotNull(cue.text); assertFalse(cue.text.trim().isEmpty()); words.append(cue.text).append(' ');
        }
        SubtitleTimeline.requireNonOverlapping(cues);
        String text = words.toString().toLowerCase(Locale.ROOT);
        for (String known : new String[] { "jacket", "green", "outfit" }) assertTrue("Known synthetic speech word missing", text.matches("(?s).*\\b" + known + "\\b.*"));
    }
    private static SubtitleCue longest(List<SubtitleCue> cues) {
        SubtitleCue longest = cues.get(0);
        for (SubtitleCue cue : cues) if (cue.endMs - cue.startMs > longest.endMs - longest.startMs) longest = cue;
        return longest;
    }
    private static long duration(File file) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try { metadata.setDataSource(file.getAbsolutePath()); return Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
    }
    private Exported export(List<Take> takes) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Exported> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(); AtomicBoolean onMain = new AtomicBoolean();
        ReelExporter exporter = new ReelExporter(context);
        main.post(() -> exporter.export(takes, TITLE, "Clean", new ReelExporter.Listener() {
            public void onProgress(int percent) { }
            public void onComplete(Uri video, Uri edit) { onMain.set(Looper.myLooper() == Looper.getMainLooper()); result.set(new Exported(video, edit)); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        }));
        try {
            assertTrue("Actual three-cut synthetic export timed out", done.await(90, TimeUnit.SECONDS)); idle();
            assertNull(error.get(), error.get()); assertNotNull(result.get()); assertTrue(onMain.get()); return result.get();
        } finally { main.post(exporter::cancel); idle(); }
    }
    private void detachOnlyThisSyntheticCompleteJournal(JSONObject edit, Exported exported, List<Take> takes) throws Exception {
        assertEquals("minifilm.edit.v1", edit.getString("schema")); assertEquals(TITLE, edit.getString("title"));
        assertTrue(edit.getBoolean("preEventResearch")); String id = edit.getString("exportId");
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        JSONArray cuts = edit.getJSONArray("cuts"); assertEquals(3, cuts.length());
        for (int i = 0; i < takes.size(); i++) {
            assertEquals(takes.get(i).uri.toString(), cuts.getJSONObject(i).getString("sourceUri"));
            assertEquals(takes.get(i).shotId, cuts.getJSONObject(i).getString("shotId"));
        }
        File marker = new File(context.getFilesDir(), "export-journal/" + id + ".json");
        JSONObject journal = new JSONObject(read(new FileInputStream(marker)));
        assertEquals(1, journal.getInt("schema")); assertEquals(id, journal.getString("id"));
        assertEquals(context.getPackageName(), journal.getString("package")); assertEquals("COMPLETE", journal.getString("state"));
        assertEquals(exported.video.toString(), journal.getString("videoUri")); assertTrue(journal.getLong("expectedBytes") > 1000);
        // Retain successful synthetic MP4/JSON; detach only this verified callback marker so
        // a later Main startup cannot replace the creator's saved last-output preferences.
        new AtomicFile(marker).delete();
    }
    private JSONObject json(Uri uri) throws Exception { return new JSONObject(read(context.getContentResolver().openInputStream(uri))); }
    private static String read(InputStream input) throws Exception {
        assertNotNull(input);
        try (InputStream source = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096]; int count; while ((count = source.read(block)) != -1) bytes.write(block, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static int whitePixels(MediaMetadataRetriever video, long timeMs) {
        Bitmap frame = video.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST); assertNotNull(frame);
        try {
            assertEquals(720, frame.getWidth()); assertEquals(1280, frame.getHeight()); int count = 0;
            for (int y = 1000; y < 1120; y++) for (int x = 50; x < 670; x++) {
                int color = frame.getPixel(x, y);
                if (Color.red(color) > 220 && Color.green(color) > 220 && Color.blue(color) > 220) count++;
            }
            return count;
        } finally { frame.recycle(); }
    }
    private static String sha(File file) throws Exception {
        try (InputStream source = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[8192]; int count;
            while ((count = source.read(bytes)) != -1) digest.update(bytes, 0, count); return ProjectPackager.hex(digest.digest());
        }
    }
    private void assertCaptureDenied() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
    }
    private static void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private static final class FileState {
        final long bytes, mtime; final String hash;
        FileState(File file) throws Exception { bytes = file.length(); mtime = file.lastModified(); hash = sha(file); }
        void assertSame(File file) throws Exception { assertTrue(file.isFile()); assertEquals(bytes, file.length()); assertEquals(mtime, file.lastModified()); assertEquals(hash, sha(file)); }
    }
    private static final class Exported {
        final Uri video, edit; Exported(Uri video, Uri edit) { this.video = video; this.edit = edit; }
    }
}
