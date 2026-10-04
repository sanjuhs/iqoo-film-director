package dev.minifilm.director;

import android.content.Context;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import static org.junit.Assert.*;

/** Retains a real, clearly synthetic research delivery. No activity, camera, mic or playback. */
@RunWith(AndroidJUnit4.class)
public final class SyntheticDeliveryTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final String TITLE = "Mini Film / synthetic research";

    @Test(timeout = 300_000) public void prepareRetainedSyntheticReelAndPortableEditKit() throws Exception {
        Map<String, ?> beforeState = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        Exported exported = null; PackageResult packaged = null; File directory = null; boolean delivered = false;
        try {
        List<Take> cuts = demo();
        cuts.get(0).captionOrigin = "manual-reviewed-synthetic-fixture";
        cuts.get(0).subtitles.add(new SubtitleCue(600, 1100, "Start with a confident pose."));
        cuts.get(0).subtitles.add(new SubtitleCue(1400, 1900, "Let the jacket lead."));
        File speechSource = new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
        assertTrue("Known locally synthesized English fixture is required", speechSource.isFile());
        long speechDuration = duration(Uri.fromFile(speechSource));
        assertTrue("Known synthetic speech fixture duration " + speechDuration,
                speechDuration >= 5546 && speechDuration <= 5946);
        long plannedDuration = 6000 + speechDuration;
        Speech sourceSpeech = transcribe(Uri.fromFile(speechSource));
        assertKnownWords(sourceSpeech.cues);
        assertCueBounds(sourceSpeech.cues, speechDuration);
        Take spoken = new Take(Uri.fromFile(speechSource), "synthetic-English-speech",
                "Locally synthesized speech / synthetic", "", speechDuration);
        spoken.inMs = 0; spoken.outMs = speechDuration; spoken.subtitles.addAll(sourceSpeech.cues);
        spoken.captionOrigin = "offline-English-synthetic-draft"; cuts.add(spoken);
        List<String> sourceHashes = new ArrayList<>(); long originalBytes = 0;
        for (Take cut : cuts) {
            File source = new File(cut.uri.getPath()); sourceHashes.add(sha(new FileInputStream(source))); originalBytes += source.length();
        }

        exported = export(cuts);
        JSONObject edit = json(exported.edit);
        assertTrue(edit.getBoolean("preEventResearch")); assertEquals(plannedDuration, edit.getLong("durationMs"));
        JSONArray saved = edit.getJSONArray("cuts"); assertEquals(4, saved.length());
        verifyTiming(saved);
        JSONObject videoMetrics = verifyEncodedReel(exported.video, plannedDuration);
        Speech outputSpeech = transcribe(exported.video); assertKnownWords(outputSpeech.cues);
        assertCueBounds(outputSpeech.cues, videoMetrics.getLong("durationMs"));

        packaged = packageCuts(cuts);
        assertEquals(originalBytes, packaged.originalBytes);
        JSONObject portable = verifyPackage(packaged.zip, cuts, sourceHashes, plannedDuration);
        directory = new File(new File(context.getFilesDir(), "delivery"), "synthetic-research-" + UUID.randomUUID());
        assertTrue(directory.mkdirs());
        File retainedVideo = new File(directory, "synthetic-research-reel.mp4");
        File retainedEdit = new File(directory, "synthetic-research-edit.json");
        File retainedZip = new File(directory, "synthetic-research-portable.zip");
        copy(exported.video, retainedVideo); copy(exported.edit, retainedEdit); copy(Uri.fromFile(packaged.zip), retainedZip);
        assertEquals(sha(context.getContentResolver().openInputStream(exported.video)), sha(new FileInputStream(retainedVideo)));
        assertEquals(sha(context.getContentResolver().openInputStream(exported.edit)), sha(new FileInputStream(retainedEdit)));
        assertEquals(sha(new FileInputStream(packaged.zip)), sha(new FileInputStream(retainedZip)));
        verifyPackage(retainedZip, cuts, sourceHashes, plannedDuration);
        assertEquals(videoMetrics.getLong("durationMs"), duration(Uri.fromFile(retainedVideo)));

        JSONArray sources = new JSONArray();
        for (int i = 0; i < cuts.size(); i++) {
            Take cut = cuts.get(i); File source = new File(cut.uri.getPath());
            assertTrue(source.isFile()); assertEquals(sourceHashes.get(i), sha(new FileInputStream(source)));
            sources.put(new JSONObject().put("portablePath", portable.getJSONArray("media").getJSONObject(i).getString("path"))
                    .put("sourceLabel", i < 3 ? "DemoAssets locally generated numbered AVC scene " + (i + 1)
                            : "Known English jacket script synthesized locally with offline Samantha TTS")
                    .put("type", "synthetic").put("bytes", source.length()).put("sha256", sourceHashes.get(i))
                    .put("inMs", cut.inMs).put("outMs", cut.outMs).put("captionOrigin", cut.captionOrigin));
        }
        JSONObject metrics = new JSONObject().put("schema", "minifilm.synthetic-delivery.v1").put("preEventResearch", true)
                .put("syntheticOnly", true).put("cameraUsed", false).put("microphoneUsed", false)
                .put("audioPlaybackUsed", false).put("uploadsUsed", false).put("look", "Clean")
                .put("video", videoMetrics).put("plannedDurationMs", plannedDuration).put("selectedSources", sources)
                .put("captionInsideOutsideVerified", true).put("sourceToTimelineBoundsVerified", true)
                .put("originalBytesUnchanged", true).put("portableProjectUsesRelativePaths", true)
                .put("sourceClipTranscriptionElapsedMs", sourceSpeech.elapsedMs).put("exportClipTranscriptionElapsedMs", outputSpeech.elapsedMs)
                .put("exportSpeechKnownWordsVerified", new JSONArray().put("jacket").put("green"))
                .put("sourceSpeechSegments", sourceSpeech.cues.size()).put("exportSpeechSegments", outputSpeech.cues.size())
                .put("reelSha256", sha(new FileInputStream(retainedVideo)))
                .put("editSha256", sha(new FileInputStream(retainedEdit)))
                .put("portableZipSha256", sha(new FileInputStream(retainedZip)))
                .put("portableZipBytes", retainedZip.length()).put("originalBytesInZip", originalBytes)
                .put("exportId", edit.getString("exportId"));
        write(new File(directory, "metrics.json"), metrics.toString(2));
        write(new File(directory, "README.txt"),
                "MINI FILM — SYNTHETIC RESEARCH DEMO\n\n"
                + "This is pre-event preparation, not event-written competition code or a submission receipt.\n"
                + "All four source clips are synthetic. Three numbered scenes were generated locally by DemoAssets.\n"
                + "The English jacket script was synthesized locally with offline Samantha TTS; no person was recorded.\n"
                + "No private/public-person footage, camera, microphone, audio playback, uploads or aircraft actions were used.\n\n"
                + "synthetic-research-reel.mp4: actual local H.264/AAC, 720x1280.\n"
                + String.format(Locale.US, "Reviewed timeline %.2f seconds; actual encoded duration %.3f seconds.\n",
                        plannedDuration / 1000.0, videoMetrics.getLong("durationMs") / 1000.0)
                + "Three reviewed 0.5–2.5-second visual cuts lead into the full known synthesized English speech fixture.\n"
                + "The first visual cut uses manually reviewed fixture captions; English captions remain offline ASR drafts.\n"
                + "Actual decoded frames verify caption visibility inside and outside reviewed ranges.\n"
                + "Offline transcription of the completed MP4 confirms the known words jacket and green survived assembly.\n"
                + "Nothing is played automatically by this check.\n\n"
                + "synthetic-research-edit.json: exact export cuts, source/timeline subtitle ranges and research provenance.\n"
                + "Its phone source references describe the original export; use the ZIP for a portable copy.\n"
                + "synthetic-research-portable.zip: four full unchanged originals, relative-path project.json and README.txt.\n"
                + "Unzip locally; media/source-001.mp4 through source-004.mp4 resolve all reviewed cuts.\n"
                + "SHA-256 verifies full originals, including source content outside the trims. No weights/keys are included.\n"
                + "This JSON is an editable description, not a verified third-party editor importer or Office Kit transfer.\n"
                + "metrics.json records actual codec/duration, checks and file hashes without hardware identifiers.\n\n"
                + "Clean preserves the synthetic colors. This demo does not prove automatic grading quality, attended filming,\n"
                + "AirPods routing, real speech accuracy, iQOO/NPU execution or working drone control.\n");
        assertEquals("Delivery must not replace the saved shoot", beforeState, context.getSharedPreferences("shoot", 0).getAll());
        // A durable verified delivery owns these references. Do not replace the user's saved
        // project on next startup by leaving this test-only latest-completed recovery pointer.
        new AtomicFile(new File(new File(context.getFilesDir(), "export-journal"), edit.getString("exportId") + ".json")).delete();
        packaged.zip.delete(); // Returned package cache only; durable ZIP and every original stay.
        Log.i("MiniFilmSyntheticDelivery", "SYNTHETIC_DELIVERY_READY relativeDirectory=delivery/" + directory.getName()
                + " video=synthetic-research-reel.mp4 edit=synthetic-research-edit.json zip=synthetic-research-portable.zip"
                + " durationMs=" + videoMetrics.getLong("durationMs") + " dimensions=720x1280 speechWords=jacket,green"
                + " sources=4 originalHashesUnchanged=true noUiOrPrefsChanges=true");
        delivered = true;
        } finally {
            if (!delivered) {
                if (exported != null) cleanupFailedPublishedResult(exported);
                if (directory != null) {
                    for (String name : new String[] { "synthetic-research-reel.mp4", "synthetic-research-edit.json",
                            "synthetic-research-portable.zip", "metrics.json", "README.txt" }) new File(directory, name).delete();
                    directory.delete();
                }
            }
            if (packaged != null) packaged.zip.delete();
            assertEquals("Fixture cleanup must preserve the saved shoot", beforeState, context.getSharedPreferences("shoot", 0).getAll());
        }
    }

    private void cleanupFailedPublishedResult(Exported result) throws Exception {
        assertEquals(context.getPackageName() + ".files", result.edit.getAuthority());
        String name = result.edit.getLastPathSegment(); assertNotNull(name);
        assertTrue("Cleanup must identify this callback's exact export", name.matches(
                "MiniFilm-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json"));
        String id = name.substring(9, name.length() - 5);
        assertEquals(id, json(result.edit).getString("exportId"));
        cleanupVerifiedSyntheticExport(id, result.video);
    }

    private void cleanupVerifiedSyntheticExport(String id, Uri expectedVideo) throws Exception {
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        File edit = new File(new File(context.getFilesDir(), "exports"), "MiniFilm-" + id + ".json");
        assertTrue("Synthetic export proof must exist before cleanup", edit.isFile());
        assertTrue(edit.length() <= 1_048_576);
        JSONObject proof = new JSONObject(text(new FileInputStream(edit)));
        assertEquals("minifilm.edit.v1", proof.getString("schema")); assertEquals(id, proof.getString("exportId"));
        assertTrue(proof.getBoolean("preEventResearch")); assertEquals(TITLE, proof.getString("title"));
        JSONArray cuts = proof.getJSONArray("cuts"); assertEquals(4, cuts.length());
        for (int i = 0; i < 4; i++) {
            File original = i < 3 ? new File(new File(context.getFilesDir(), "synthetic-demo"), "synthetic-" + (i + 1) + ".mp4")
                    : new File(context.getFilesDir(), "fixtures/jacket-speech.mp4");
            assertEquals(i < 3 ? "synthetic-" + (i + 1) : "synthetic-English-speech", cuts.getJSONObject(i).getString("shotId"));
            assertEquals(Uri.fromFile(original), Uri.parse(cuts.getJSONObject(i).getString("sourceUri")));
            assertTrue("Original fixture must remain", original.isFile());
        }
        try (Cursor row = context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                new String[] { MediaStore.Video.Media._ID, MediaStore.Video.Media.OWNER_PACKAGE_NAME },
                MediaStore.Video.Media.DISPLAY_NAME + "=? AND " + MediaStore.Video.Media.RELATIVE_PATH + "=?",
                new String[] { "MiniFilm-" + id + ".mp4", "Movies/MiniFilm/" }, null)) {
            assertNotNull(row); assertTrue("Ambiguous gallery output must be preserved", row.getCount() <= 1);
            if (row.moveToFirst()) {
                assertEquals("Only this app's fixture output may be removed", context.getPackageName(), row.getString(1));
                Uri exact = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, row.getLong(0));
                if (expectedVideo != null) assertEquals("Callback row must match verified synthetic export", expectedVideo, exact);
                assertEquals(1, context.getContentResolver().delete(exact, null, null));
            }
        }
        // Private outputs are UUID-derived from this verified export, never supplied source paths.
        assertTrue(edit.delete());
        File temp = new File(context.getCacheDir(), "reel-" + id + ".mp4"); if (temp.exists()) assertTrue(temp.delete());
        new AtomicFile(new File(new File(context.getFilesDir(), "export-journal"), id + ".json")).delete();
    }

    private JSONObject verifyEncodedReel(Uri uri, long plannedDuration) throws Exception {
        MediaMetadataRetriever video = new MediaMetadataRetriever();
        try {
            video.setDataSource(context, uri);
            assertEquals("720", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("1280", video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            long duration = Long.parseLong(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            assertTrue("Actual encoded duration " + duration + " differs from reviewed timeline " + plannedDuration,
                    Math.abs(duration - plannedDuration) <= 300);
            int inside = captionPixels(video, 300_000), outside = captionPixels(video, 750_000), next = captionPixels(video, 1_100_000);
            assertTrue("First reviewed caption missing", inside > 20); assertEquals("Caption outside its reviewed range", 0, outside);
            assertTrue("Second reviewed caption missing", next > 20);
            MediaExtractor extractor = new MediaExtractor(); boolean h264 = false, aac = false;
            try {
                extractor.setDataSource(context, uri, null);
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                    h264 |= "video/avc".equals(mime); aac |= "audio/mp4a-latm".equals(mime);
                }
            } finally { extractor.release(); }
            assertTrue(h264); assertTrue(aac);
            return new JSONObject().put("durationMs", duration).put("width", 720).put("height", 1280)
                    .put("videoCodec", "video/avc").put("audioCodec", "audio/mp4a-latm")
                    .put("firstCaptionWhitePixels", inside).put("gapCaptionWhitePixels", outside).put("secondCaptionWhitePixels", next);
        } finally { video.release(); }
    }
    private static int captionPixels(MediaMetadataRetriever video, long us) {
        Bitmap frame = video.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST); assertNotNull(frame);
        try {
            int count = 0;
            for (int y = 1000; y < 1120; y++) for (int x = 50; x < 670; x++) {
                int color = frame.getPixel(x, y);
                if (Color.red(color) > 220 && Color.green(color) > 220 && Color.blue(color) > 220) count++;
            }
            return count;
        } finally { frame.recycle(); }
    }
    private static void verifyTiming(JSONArray cuts) throws Exception {
        JSONObject cue = cuts.getJSONObject(0).getJSONArray("subtitles").getJSONObject(0);
        assertEquals(600, cue.getLong("sourceStartMs")); assertEquals(1100, cue.getLong("sourceEndMs"));
        assertEquals(100, cue.getLong("timelineStartMs")); assertEquals(600, cue.getLong("timelineEndMs"));
        for (int i = 0; i < 4; i++) assertEquals(i < 3 ? i * 2000 : 6000, cuts.getJSONObject(i).getLong("timelineStartMs"));
    }
    private JSONObject verifyPackage(File file, List<Take> cuts, List<String> hashes, long plannedDuration) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            assertEquals(6, zip.size()); assertNotNull(zip.getEntry("README.txt"));
            JSONObject project = new JSONObject(text(zip.getInputStream(zip.getEntry("project.json"))));
            assertEquals("minifilm.portable-edit.v1", project.getString("schema")); assertTrue(project.getBoolean("preEventResearch"));
            assertEquals(plannedDuration, project.getLong("durationMs")); JSONArray media = project.getJSONArray("media"), saved = project.getJSONArray("cuts");
            assertEquals(4, media.length()); assertEquals(4, saved.length()); verifyTiming(saved);
            for (int i = 0; i < 4; i++) {
                JSONObject item = media.getJSONObject(i); String path = item.getString("path");
                assertEquals(String.format(Locale.US, "media/source-%03d.mp4", i + 1), path);
                ZipEntry entry = zip.getEntry(path); assertNotNull(entry);
                assertEquals(new File(cuts.get(i).uri.getPath()).length(), entry.getSize());
                assertEquals(hashes.get(i), item.getString("sha256")); assertEquals(hashes.get(i), sha(zip.getInputStream(entry)));
                JSONObject cut = saved.getJSONObject(i); assertEquals(path, cut.getString("mediaPath"));
                assertEquals(cuts.get(i).inMs, cut.getLong("inMs")); assertEquals(cuts.get(i).outMs, cut.getLong("outMs"));
                assertFalse(cut.has("sourceUri"));
            }
            assertFalse(project.toString().contains("file://")); assertFalse(project.toString().contains("content://"));
            return project;
        }
    }
    private static void assertCueBounds(List<SubtitleCue> cues, long durationMs) {
        for (SubtitleCue cue : cues) {
            assertTrue("Subtitle starts within the actual container", cue.startMs >= 0 && cue.startMs < durationMs);
            assertTrue("Subtitle preserves a positive range", cue.endMs > cue.startMs);
            assertTrue("Default draft ends within the actual container", cue.endMs <= durationMs);
        }
    }
    private static void assertKnownWords(List<SubtitleCue> cues) {
        StringBuilder words = new StringBuilder(); for (SubtitleCue cue : cues) words.append(cue.text).append(' ');
        String transcript = words.toString().toLowerCase(Locale.US);
        assertTrue("Known synthetic jacket word missing", transcript.contains("jacket"));
        assertTrue("Known synthetic green word missing", transcript.contains("green"));
    }
    private List<Take> demo() throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<List<Take>> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        DemoAssets.create(context, new DemoAssets.Listener() {
            public void onReady(List<Take> takes) { result.set(takes); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS)); assertNull(error.get(), error.get()); return result.get();
    }
    private Speech transcribe(Uri uri) throws Exception {
        ClipTranscriber transcriber = new ClipTranscriber(context); assertTrue("Local tiny.en is required", transcriber.isModelAvailable());
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Speech> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        try {
            transcriber.transcribe(uri, new ClipTranscriber.Listener() {
                public void onComplete(List<SubtitleCue> cues, long elapsed) { result.set(new Speech(cues, elapsed)); done.countDown(); }
                public void onError(String message) { error.set(message); done.countDown(); }
            });
            assertTrue(done.await(120, TimeUnit.SECONDS)); assertNull(error.get(), error.get()); return result.get();
        } finally { transcriber.close(); }
    }
    private Exported export(List<Take> cuts) throws Exception {
        ReelExporter exporter = new ReelExporter(context); CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Exported> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        main.post(() -> exporter.export(cuts, TITLE, "Clean", new ReelExporter.Listener() {
            public void onProgress(int percent) { }
            public void onComplete(Uri video, Uri edit) { result.set(new Exported(video, edit)); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        }));
        try { assertTrue(done.await(90, TimeUnit.SECONDS)); assertNull(error.get(), error.get()); return result.get(); }
        finally { main.post(exporter::cancel); }
    }
    private PackageResult packageCuts(List<Take> cuts) throws Exception {
        ProjectPackager packager = new ProjectPackager(context); CountDownLatch done = new CountDownLatch(1);
        AtomicReference<PackageResult> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        main.post(() -> packager.export(cuts, TITLE, "Clean", new ProjectPackager.Listener() {
            public void onProgress(long bytes) { }
            public void onComplete(File zip, long bytes) { result.set(new PackageResult(zip, bytes)); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        }));
        try { assertTrue(done.await(45, TimeUnit.SECONDS)); assertNull(error.get(), error.get()); return result.get(); }
        finally { main.post(packager::close); }
    }
    private long duration(Uri uri) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try { metadata.setDataSource(context, uri); return Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { metadata.release(); }
    }
    private JSONObject json(Uri uri) throws Exception { return new JSONObject(text(context.getContentResolver().openInputStream(uri))); }
    private static String text(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count; while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count); return out.toString("UTF-8");
        }
    }
    private void copy(Uri source, File destination) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(source); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024]; int count; while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count); out.getFD().sync();
        }
    }
    private static void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(value.getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
    }
    private static String sha(InputStream input) throws Exception {
        try (InputStream in = input) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = in.read(buffer)) != -1) hash.update(buffer, 0, count); return ProjectPackager.hex(hash.digest());
        }
    }
    private static final class Exported { final Uri video, edit; Exported(Uri video, Uri edit) { this.video = video; this.edit = edit; } }
    private static final class Speech { final List<SubtitleCue> cues; final long elapsedMs; Speech(List<SubtitleCue> cues, long ms) { this.cues = cues; elapsedMs = ms; } }
    private static final class PackageResult { final File zip; final long originalBytes; PackageResult(File zip, long bytes) { this.zip = zip; originalBytes = bytes; } }
}
