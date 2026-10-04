package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real phone tests with locally generated fixtures; no camera/microphone input. */
@RunWith(AndroidJUnit4.class)
public final class MediaWorkflowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 180_000) public void syntheticSequenceExportsReviewedCutsAndManualCaptions() throws Exception {
        List<Take> takes = generate();
        Exported exported = export(takes, "Clean");
        MediaMetadataRetriever video = new MediaMetadataRetriever();
        try {
            video.setDataSource(context, exported.video);
            assertEquals("720", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("1280", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            long duration = Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue("Actual output duration " + duration, duration >= 5900 && duration <= 6100);
            int[] expected = { 0xFF151D32, 0xFF252032, 0xFF13302C };
            for (int i = 0; i < 3; i++) {
                Bitmap frame = frame(video, (i * 2000 + 750) * 1000L);
                assertColorNear(expected[i], frame.getPixel(20, 600), 8);
                assertTrue("Manual caption words must be visible", captionWhitePixels(frame) > 20);
                frame.recycle();
            }
            JSONObject json = json(exported.edit);
            assertEquals(6000, json.getLong("durationMs"));
            JSONArray cuts = json.getJSONArray("cuts"); assertEquals(3, cuts.length());
            for (int i = 0; i < cuts.length(); i++) {
                JSONObject cut = cuts.getJSONObject(i);
                assertEquals("synthetic-" + (i + 1), cut.getString("shotId"));
                assertEquals(500, cut.getLong("inMs")); assertEquals(2500, cut.getLong("outMs"));
                assertEquals(i * 2000, cut.getLong("timelineStartMs"));
                assertEquals("manual", cut.getString("captionOrigin"));
                assertTrue(new File(Uri.parse(cut.getString("sourceUri")).getPath()).isFile());
            }
            Log.i("MiniFilmMediaTest", "SEQUENCE_PASS durationMs=" + duration
                    + " dimensions=720x1280 order=01,02,03 originalFilesIntact=true manualCaptions=true");
        } finally { video.release(); }
    }

    @Test(timeout = 180_000) public void subtitleTimingRespectsTrimAndSecondClipTimelineOffset() throws Exception {
        List<Take> takes = new ArrayList<>(generate().subList(0, 2));
        for (Take take : takes) {
            take.caption = ""; take.captionOrigin = "whisper-cpu-draft";
            take.subtitles.add(new SubtitleCue(600, 1100, "Timed caption fixture"));
        }
        Exported exported = export(takes, "Clean");
        MediaMetadataRetriever video = new MediaMetadataRetriever();
        try {
            video.setDataSource(context, exported.video);
            for (int i = 0; i < 2; i++) {
                Bitmap within = frame(video, (i * 2000 + 300) * 1000L);
                Bitmap outside = frame(video, (i * 2000 + 900) * 1000L);
                assertTrue("Timed caption missing within clipped cue on item " + i, captionWhitePixels(within) > 20);
                assertEquals("Caption should disappear outside clipped cue on item " + i, 0, captionWhitePixels(outside));
                within.recycle(); outside.recycle();
            }
            JSONArray cuts = json(exported.edit).getJSONArray("cuts");
            for (int i = 0; i < 2; i++) {
                JSONObject cue = cuts.getJSONObject(i).getJSONArray("subtitles").getJSONObject(0);
                assertEquals(600, cue.getLong("sourceStartMs")); assertEquals(1100, cue.getLong("sourceEndMs"));
                assertEquals(i * 2000 + 100, cue.getLong("timelineStartMs"));
                assertEquals(i * 2000 + 600, cue.getLong("timelineEndMs"));
            }
            Log.i("MiniFilmMediaTest", "TIMING_PASS clippedInMs=500 sourceCue=600..1100"
                    + " firstTimelineCue=100..600 secondTimelineCue=2100..2600 visibleInside=true absentOutside=true");
        } finally { video.release(); }
    }

    @Test(timeout = 240_000) public void offlineEnglishSpeechFixtureTranscribesAndExportsClippedCaptions() throws Exception {
        File fixture = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Parent must install labelled synthetic spoken fixture", fixture.isFile());
        ClipTranscriber transcriber = new ClipTranscriber(context);
        assertTrue("Parent must install verified local tiny.en weights and native runtime", transcriber.isModelAvailable());
        AtomicReference<List<SubtitleCue>> cues = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
        long[] elapsed = new long[1];
        try {
            transcriber.transcribe(Uri.fromFile(fixture), new ClipTranscriber.Listener() {
                public void onComplete(List<SubtitleCue> result, long elapsedMs) { cues.set(result); elapsed[0] = elapsedMs; done.countDown(); }
                public void onError(String message) { failure.set(message); done.countDown(); }
            });
            assertTrue("Offline ASR timed out", done.await(150, TimeUnit.SECONDS));
            assertNull(failure.get(), failure.get()); assertNotNull(cues.get()); assertFalse(cues.get().isEmpty());
            MediaMetadataRetriever source = new MediaMetadataRetriever(); long sourceDuration;
            try {
                source.setDataSource(fixture.getAbsolutePath());
                sourceDuration = Long.parseLong(source.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            } finally { source.release(); }
            StringBuilder words = new StringBuilder();
            for (SubtitleCue cue : cues.get()) {
                assertTrue(cue.startMs >= 0); assertTrue(cue.endMs > cue.startMs);
                assertTrue("Cue beyond source duration", cue.endMs <= sourceDuration + 100);
                words.append(cue.text).append(' ');
            }
            String transcript = words.toString().toLowerCase(Locale.US);
            assertTrue("Missing expected jacket word: " + transcript, transcript.contains("jacket"));
            assertTrue("Missing expected green word: " + transcript, transcript.contains("green"));
            assertTrue("Missing expected outfit word: " + transcript, transcript.contains("outfit"));
            Take take = new Take(Uri.fromFile(fixture), "synthetic-speech", "Synthetic spoken fixture", "", sourceDuration);
            take.inMs = 1000; take.outMs = sourceDuration;
            take.captionOrigin = "whisper-cpu-draft"; take.subtitles.addAll(cues.get());
            Exported exported = export(java.util.Collections.singletonList(take), "Warm");
            JSONObject cut = json(exported.edit).getJSONArray("cuts").getJSONObject(0);
            assertEquals(1000, cut.getLong("inMs"));
            JSONArray saved = cut.getJSONArray("subtitles"); assertEquals(cues.get().size(), saved.length());
            long firstVisibleTimeMs = -1;
            for (int i = 0; i < saved.length(); i++) {
                JSONObject cue = saved.getJSONObject(i);
                if (cue.getBoolean("visibleInCut")) {
                    assertEquals(Math.max(1000, cue.getLong("sourceStartMs")) - 1000, cue.getLong("timelineStartMs"));
                    if (firstVisibleTimeMs < 0) firstVisibleTimeMs = (cue.getLong("timelineStartMs") + cue.getLong("timelineEndMs")) / 2;
                }
            }
            assertTrue("No ASR captions overlap clipped take", firstVisibleTimeMs >= 0);
            MediaMetadataRetriever output = new MediaMetadataRetriever();
            try {
                output.setDataSource(context, exported.video);
                long duration = Long.parseLong(output.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Clipped speech duration mismatch", Math.abs(duration - (sourceDuration - 1000)) <= 150);
                Bitmap captionFrame = frame(output, firstVisibleTimeMs * 1000);
                assertTrue("ASR caption must be burned into clipped output", captionWhitePixels(captionFrame) > 20);
                captionFrame.recycle();
            } finally { output.release(); }
            Log.i("MiniFilmMediaTest", "ASR_EXPORT_PASS backend=CPU elapsedMs=" + elapsed[0]
                    + " segments=" + cues.get().size() + " expectedWords=jacket,green,outfit"
                    + " sourceDurationMs=" + sourceDuration + " trimmedStartMs=1000 timedOverlayVisible=true");
        } finally { transcriber.close(); }
    }

    @Test(timeout = 120_000) public void cancellationLeavesOriginalsAndNoTemporaryVideo() throws Exception {
        List<Take> sources = generate();
        List<Take> repeated = new ArrayList<>();
        for (int i = 0; i < 12; i++) repeated.add(sources.get(i % sources.size()));
        java.util.Set<String> before = cacheVideos();
        ReelExporter exporter = new ReelExporter(context);
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> result = new AtomicReference<>();
        main.post(() -> {
            exporter.export(repeated, "Synthetic cancellation check", "Clean", new ReelExporter.Listener() {
                public void onProgress(int percent) {}
                public void onComplete(Uri video, Uri edit) { result.set("Unexpected completion before cancellation"); done.countDown(); }
                public void onError(String message) { result.set(message); done.countDown(); }
            });
            main.postDelayed(exporter::cancel, 30);
        });
        assertTrue("Cancellation did not return", done.await(30, TimeUnit.SECONDS));
        assertNotNull(result.get()); assertTrue(result.get(), result.get().toLowerCase(Locale.US).contains("cancelled"));
        assertEquals("Temporary MP4 left after cancellation", before, cacheVideos());
        for (Take source : sources) assertTrue(new File(source.uri.getPath()).isFile());
        Log.i("MiniFilmMediaTest", "CANCEL_PASS originalFilesIntact=true newTemporaryVideos=0");
    }

    private java.util.Set<String> cacheVideos() {
        java.util.Set<String> names = new java.util.HashSet<>();
        File[] files = context.getCacheDir().listFiles();
        if (files != null) for (File file : files)
            if (file.getName().startsWith("reel-") && file.getName().endsWith(".mp4")) names.add(file.getName());
        return names;
    }

    private List<Take> generate() throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<List<Take>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        DemoAssets.create(context, new DemoAssets.Listener() {
            public void onReady(List<Take> takes) { result.set(takes); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue("Fixture generation timed out", done.await(60, TimeUnit.SECONDS));
        assertNull(error.get(), error.get()); return result.get();
    }

    private Exported export(List<Take> takes, String look) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Exported> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(); ReelExporter exporter = new ReelExporter(context);
        main.post(() -> exporter.export(takes, "Synthetic media verification", look, new ReelExporter.Listener() {
            public void onProgress(int percent) {}
            public void onComplete(Uri video, Uri edit) { result.set(new Exported(video, edit)); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        }));
        try {
            assertTrue("Video export timed out", done.await(90, TimeUnit.SECONDS));
            assertNull(error.get(), error.get()); assertNotNull(result.get()); return result.get();
        } finally { main.post(exporter::cancel); }
    }

    private JSONObject json(Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            assertNotNull(input); byte[] block = new byte[4096]; int count;
            while ((count = input.read(block)) != -1) bytes.write(block, 0, count);
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
    }
    private static Bitmap frame(MediaMetadataRetriever video, long timeUs) {
        Bitmap bitmap = video.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
        assertNotNull("Output frame not decodable at " + timeUs, bitmap); return bitmap;
    }
    private static void assertColorNear(int expected, int actual, int tolerance) {
        assertTrue("Scene red channel mismatch", Math.abs(Color.red(expected) - Color.red(actual)) <= tolerance);
        assertTrue("Scene green channel mismatch", Math.abs(Color.green(expected) - Color.green(actual)) <= tolerance);
        assertTrue("Scene blue channel mismatch", Math.abs(Color.blue(expected) - Color.blue(actual)) <= tolerance);
    }
    private static int captionWhitePixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 1000; y < 1120; y++) for (int x = 50; x < 670; x++) {
            int color = bitmap.getPixel(x, y);
            if (Color.red(color) > 220 && Color.green(color) > 220 && Color.blue(color) > 220) count++;
        }
        return count;
    }
    private static final class Exported {
        final Uri video, edit;
        Exported(Uri video, Uri edit) { this.video = video; this.edit = edit; }
    }
}
