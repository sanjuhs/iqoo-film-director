package dev.minifilm.director;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Headless actual analysis -> explicit application -> encoded output; synthetic sources only. */
@RunWith(AndroidJUnit4.class)
public final class SpeechTrimExportTest {
    private static final String TITLE = "Synthetic reviewed speech trim verification";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 180_000) public void reviewedCandidateExportsMappedCaptionsAndPreservesSpeechAndOriginal() throws Exception {
        File source = new File(context.getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Known offline padded TTS fixture is required", source.isFile());
        String originalHash = sha(source); long sourceDuration = duration(Uri.fromFile(source));
        assertTrue(sourceDuration >= 8546 && sourceDuration <= 8946);
        Map<String, ?> originalState = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        ClipTranscriber transcriber = new ClipTranscriber(context); ReelExporter exporter = new ReelExporter(context);
        AtomicReference<Exported> published = new AtomicReference<>();
        try {
            assertTrue(transcriber.isModelAvailable()); SpeechTrim trim = suggestion(transcriber, source, sourceDuration);
            assertTrue(trim.reason, trim.hasSuggestion); assertTrue(trim.suggestedInMs >= 900 && trim.suggestedInMs <= 1700);
            assertTrue(trim.suggestedOutMs >= 6900 && trim.suggestedOutMs <= 8000);
            Take take = new Take(Uri.fromFile(source), "synthetic-padded-reviewed-speech", "", "Creator reviewed text", sourceDuration);
            take.captionOrigin = "creator-reviewed-synthetic-manual";
            List<SubtitleCue> reviewed = new ArrayList<>();
            SubtitleCue opening = new SubtitleCue(trim.suggestedInMs - 200, trim.suggestedInMs + 650, "Reviewed opening.");
            SubtitleCue ending = new SubtitleCue(trim.suggestedOutMs - 650, trim.suggestedOutMs + 200, "Reviewed ending.");
            reviewed.add(opening); reviewed.add(ending); take.subtitles = reviewed;
            long openingStart = opening.startMs, endingEnd = ending.endMs;

            // A candidate from another URI or an earlier range cannot change any current edit.
            Take stale = new Take(Uri.parse("file:///synthetic/other-source.mp4"), take.shotId, "Other", "Keep", sourceDuration);
            stale.subtitles = reviewed; assertFalse(trim.applyTo(stale)); assertEquals(0, stale.inMs); assertEquals(sourceDuration, stale.outMs);
            Take changed = new Take(take.uri, take.shotId, "Changed", "Keep", sourceDuration); changed.inMs = 20;
            assertFalse(trim.applyTo(changed)); assertEquals(20, changed.inMs); assertEquals(sourceDuration, changed.outMs);
            assertTrue(trim.applyTo(take)); assertFalse("Applying the same snapshot twice must reject stale bounds", trim.applyTo(take));
            assertSame(reviewed, take.subtitles); assertSame(opening, take.subtitles.get(0)); assertSame(ending, take.subtitles.get(1));
            assertEquals(openingStart, opening.startMs); assertEquals(endingEnd, ending.endMs);
            assertEquals("creator-reviewed-synthetic-manual", take.captionOrigin); assertEquals("Creator reviewed text", take.caption);
            assertTrue(take.selected); long plannedDuration = take.outMs - take.inMs;

            CountDownLatch exportDone = new CountDownLatch(1); AtomicReference<String> exportError = new AtomicReference<>();
            main.post(() -> exporter.export(Collections.singletonList(take), TITLE, "Clean", new ReelExporter.Listener() {
                @Override public void onProgress(int percent) {}
                @Override public void onComplete(Uri video, Uri edit) { published.set(new Exported(video, edit)); exportDone.countDown(); }
                @Override public void onError(String message) { exportError.set(message); exportDone.countDown(); }
            }));
            assertTrue("Reviewed speech export timed out", exportDone.await(90, TimeUnit.SECONDS)); assertNull(exportError.get());
            Exported output = published.get(); assertNotNull(output); JSONObject project = json(output.edit);
            assertEquals(plannedDuration, project.getLong("durationMs")); JSONObject cut = project.getJSONArray("cuts").getJSONObject(0);
            assertEquals(take.inMs, cut.getLong("inMs")); assertEquals(take.outMs, cut.getLong("outMs"));
            assertEquals("creator-reviewed-synthetic-manual", cut.getString("captionOrigin"));
            JSONArray captions = cut.getJSONArray("subtitles"); assertEquals(2, captions.length());
            JSONObject first = captions.getJSONObject(0), last = captions.getJSONObject(1);
            assertEquals("Reviewed opening.", first.getString("text")); assertEquals(openingStart, first.getLong("sourceStartMs"));
            assertEquals(0, first.getLong("timelineStartMs")); assertEquals(650, first.getLong("timelineEndMs"));
            assertEquals("Reviewed ending.", last.getString("text")); assertEquals(endingEnd, last.getLong("sourceEndMs"));
            assertEquals(plannedDuration - 650, last.getLong("timelineStartMs")); assertEquals(plannedDuration, last.getLong("timelineEndMs"));
            assertTrue(first.getBoolean("visibleInCut")); assertTrue(last.getBoolean("visibleInCut"));
            long encodedDuration; MediaMetadataRetriever video = new MediaMetadataRetriever();
            try {
                video.setDataSource(context, output.video);
                assertEquals("720", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                assertEquals("1280", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                encodedDuration = Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Encoded duration must follow reviewed bounds", Math.abs(encodedDuration - plannedDuration) <= 150);
                assertTrue("Opening caption absent", whitePixels(video, 300) > 20);
                assertTrue("Ending caption absent", whitePixels(video, plannedDuration - 300) > 20);
                assertEquals("No reviewed caption belongs in the middle", 0, whitePixels(video, plannedDuration / 2));
            } finally { video.release(); }
            List<SubtitleCue> outputCues = transcribe(transcriber, output.video); StringBuilder words = new StringBuilder();
            for (SubtitleCue cue : outputCues) { assertTrue(cue.startMs >= 0); assertTrue(cue.endMs <= encodedDuration); words.append(cue.text).append(' '); }
            String transcript = words.toString().toLowerCase(Locale.US);
            for (String word : new String[] { "jacket", "green", "outfit" }) assertTrue("Known speech removed: " + transcript, transcript.contains(word));
            assertSame(reviewed, take.subtitles); assertEquals(openingStart, opening.startMs); assertEquals(endingEnd, ending.endMs);
            assertEquals(originalHash, sha(source));
            Log.i("MiniFilmSpeechTrimExportTest", "SPEECH_TRIM_EXPORT_OK sourceDurationMs=" + sourceDuration
                    + " appliedInMs=" + take.inMs + " appliedOutMs=" + take.outMs + " encodedDurationMs=" + encodedDuration
                    + " captionTimingClipped=true reviewedCaptionsUnchanged=true originalHashUnchanged=true"
                    + " knownSpeechWords=jacket,green,outfit staleCandidatesRejected=true noUiOrPlayback=true");
        } finally {
            transcriber.close(); CountDownLatch cancelled = new CountDownLatch(1);
            main.post(() -> { exporter.cancel(); cancelled.countDown(); }); assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            if (published.get() != null) removeOwnFixtureJournal(published.get(), source);
            assertEquals(originalHash, sha(source));
            assertEquals("Headless check must not replace the saved shoot", originalState, context.getSharedPreferences("shoot", 0).getAll());
        }
    }

    private SpeechTrim suggestion(ClipTranscriber transcriber, File source, long duration) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<SpeechTrim> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        transcriber.analyzeForTrim(Uri.fromFile(source), 0, duration, new ClipTranscriber.TrimListener() {
            @Override public void onComplete(SpeechTrim suggestion, long elapsedMs) { result.set(suggestion); done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS)); assertNull(error.get()); assertNotNull(result.get()); return result.get();
    }
    private List<SubtitleCue> transcribe(ClipTranscriber transcriber, Uri video) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<List<SubtitleCue>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        transcriber.transcribe(video, new ClipTranscriber.Listener() {
            @Override public void onComplete(List<SubtitleCue> cues, long elapsedMs) { result.set(cues); done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS)); assertNull(error.get()); assertNotNull(result.get()); return result.get();
    }
    private void removeOwnFixtureJournal(Exported output, File source) throws Exception {
        JSONObject proof = json(output.edit); assertEquals(TITLE, proof.getString("title")); assertTrue(proof.getBoolean("preEventResearch"));
        assertEquals("minifilm.edit.v1", proof.getString("schema")); String id = proof.getString("exportId");
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertEquals("MiniFilm-" + id + ".json", output.edit.getLastPathSegment());
        JSONArray cuts = proof.getJSONArray("cuts"); assertEquals(1, cuts.length());
        assertEquals(Uri.fromFile(source).toString(), cuts.getJSONObject(0).getString("sourceUri"));
        assertEquals("synthetic-padded-reviewed-speech", cuts.getJSONObject(0).getString("shotId"));
        // Retain this synthetic result for inspection, but never hydrate it into the user's last output.
        new AtomicFile(new File(new File(context.getFilesDir(), "export-journal"), id + ".json")).delete();
    }
    private long duration(Uri uri) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try { metadata.setDataSource(context, uri); return Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
    }
    private JSONObject json(Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            assertNotNull(input); byte[] block = new byte[4096]; for (int n; (n = input.read(block)) != -1;) bytes.write(block, 0, n);
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
    }
    private static int whitePixels(MediaMetadataRetriever video, long timeMs) {
        Bitmap frame = video.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST); assertNotNull(frame);
        try {
            int white = 0;
            for (int y = 1000; y < 1120; y++) for (int x = 50; x < 670; x++) {
                int rgb = frame.getPixel(x, y); if (Color.red(rgb) > 220 && Color.green(rgb) > 220 && Color.blue(rgb) > 220) white++;
            }
            return white;
        } finally { frame.recycle(); }
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] block = new byte[8192]; for (int n; (n = input.read(block)) != -1;) digest.update(block, 0, n);
        }
        StringBuilder hash = new StringBuilder(); for (byte value : digest.digest()) hash.append(String.format(Locale.US, "%02x", value & 255));
        return hash.toString();
    }
    private static final class Exported {
        final Uri video, edit; Exported(Uri video, Uri edit) { this.video = video; this.edit = edit; }
    }
}
