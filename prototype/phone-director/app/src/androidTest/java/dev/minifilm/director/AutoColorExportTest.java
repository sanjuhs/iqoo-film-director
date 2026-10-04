package dev.minifilm.director;

import android.content.Context;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Actual encoded synthetic comparison, not a general color-correction quality evaluation. */
@RunWith(AndroidJUnit4.class)
public final class AutoColorExportTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 240_000) public void actualEncodedAutoBalanceReducesSyntheticCastLiftsDimAndKeepsIntentionalFlatColor() throws Exception {
        Take cast = AutoColorBalanceTest.createClip(context, 0), dim = AutoColorBalanceTest.createClip(context, 1),
                intentional = AutoColorBalanceTest.createClip(context, 2);
        List<Take> cuts = Arrays.asList(cast, dim, intentional);
        List<String> hashes = new ArrayList<>(); for (Take cut : cuts) hashes.add(sha(Files.readAllBytes(new File(cut.uri.getPath()).toPath())));
        Exported clean = null, auto = null;
        try {
            List<AutoColorBalance.Balance> measured = analyze(cuts);
            assertTrue(measured.get(0).applied); assertTrue(measured.get(1).applied); assertFalse(measured.get(2).applied);
            clean = export(cuts, "Clean", Collections.emptyList());
            // Deliberately reverse measurements: pairing must use source/shot/range, never index.
            List<AutoColorBalance.Balance> reversed = new ArrayList<>(measured); Collections.reverse(reversed);
            auto = export(cuts, "Auto balance", reversed);
            float[][] before = means(clean.video), after = means(auto.video);
            float oldCast = Math.abs(before[0][0] - before[0][2]), newCast = Math.abs(after[0][0] - after[0][2]);
            assertTrue("Actual encoded neutral red/blue spread must decrease: " + oldCast + " -> " + newCast,
                    newCast < oldCast - .8f);
            assertTrue("Encoded red cast should decrease", after[0][0] < before[0][0] - .2f);
            assertTrue("Encoded blue deficit should decrease", after[0][2] > before[0][2] + .2f);
            for (int channel = 0; channel < 3; channel++)
                assertTrue("Cast correction must remain small", Math.abs(after[0][channel] - before[0][channel]) < 12);
            float lift = luma(after[1]) - luma(before[1]);
            assertTrue("Actual dim fixture must brighten modestly: " + lift, lift > 1 && lift < 25);
            for (int channel = 0; channel < 3; channel++)
                assertEquals("Abstention must preserve intentional flat color", before[2][channel], after[2][channel], 2.5f);
            JSONArray saved = auto.project.getJSONArray("cuts"); assertEquals(3, saved.length());
            for (int i = 0; i < 3; i++) {
                AutoColorBalance.Balance balance = measured.get(i); JSONObject metadata = saved.getJSONObject(i).getJSONObject("colorBalance");
                assertEquals("deterministic-neutral-exposure-heuristic-v1", metadata.getString("method"));
                assertEquals("local-cpu-frame-statistics", metadata.getString("backend"));
                assertFalse(metadata.getBoolean("learned")); assertTrue(metadata.getBoolean("reviewRequired"));
                assertEquals(cuts.get(i).uri.toString(), metadata.getString("sourceUri"));
                assertEquals(cuts.get(i).shotId, metadata.getString("shotId"));
                assertEquals(100, metadata.getLong("inMs")); assertEquals(900, metadata.getLong("outMs"));
                assertEquals(balance.applied, metadata.getBoolean("applied")); assertEquals(balance.reason, metadata.getString("reason"));
                assertEquals(balance.redScale, metadata.getDouble("redScale"), .000001);
                assertEquals(balance.greenScale, metadata.getDouble("greenScale"), .000001);
                assertEquals(balance.blueScale, metadata.getDouble("blueScale"), .000001);
                assertEquals(balance.brightness, metadata.getDouble("brightnessLinearBt709"), .000001);
                assertEquals(balance.neutralRed, metadata.getDouble("neutralRed"), .000001);
                assertEquals(balance.neutralGreen, metadata.getDouble("neutralGreen"), .000001);
                assertEquals(balance.neutralBlue, metadata.getDouble("neutralBlue"), .000001);
                assertEquals(balance.meanLuma, metadata.getDouble("meanLuma"), .000001);
                assertEquals(balance.neutralFraction, metadata.getDouble("neutralFraction"), .000001);
                assertEquals(balance.clippedFraction, metadata.getDouble("clippedFraction"), .000001);
                assertEquals(balance.lumaStdDev, metadata.getDouble("lumaStdDev"), .000001);
                assertEquals(3, metadata.getInt("sampledFrames")); assertEquals(3, metadata.getJSONArray("sampledAtSourceMs").length());
                assertEquals(hashes.get(i), sha(Files.readAllBytes(new File(cuts.get(i).uri.getPath()).toPath())));
            }
            Log.i("MiniFilmAutoExportTest", "AUTO_ENCODE_PASS castSpread=" + oldCast + "->" + newCast
                    + " dimLumaLift=" + lift + " flatColorIdentity=true exactSourcePairing=true metadata=true"
                    + " originalsShaUnchanged=true cleanOutputSha=" + sha(bytes(clean.video)) + " autoOutputSha=" + sha(bytes(auto.video)));
        } finally {
            cleanup(clean); cleanup(auto); for (Take cut : cuts) new File(cut.uri.getPath()).delete();
        }
    }

    @Test(timeout = 120_000) public void missingOrChangedSourceShotAndTrimMeasurementsRejectWithoutOutputs() throws Exception {
        Take original = AutoColorBalanceTest.createClip(context, 0);
        String hash = sha(Files.readAllBytes(new File(original.uri.getPath()).toPath()));
        try {
            List<AutoColorBalance.Balance> measured = analyze(Collections.singletonList(original));
            assertRejected(original, Collections.emptyList());
            for (int change = 0; change < 4; change++) {
                Take cut = new Take(original.uri, original.shotId, original.title, "", 1000); cut.inMs = 100; cut.outMs = 900;
                if (change == 0) cut.uri = Uri.parse("file:///different-synthetic-source.mp4");
                if (change == 1) cut.shotId = "different-shot";
                if (change == 2) cut.inMs = 200;
                if (change == 3) cut.outMs = 800;
                assertRejected(cut, measured);
            }
            assertEquals(hash, sha(Files.readAllBytes(new File(original.uri.getPath()).toPath())));
            Log.i("MiniFilmAutoExportTest", "AUTO_MATCH_REJECTION_PASS missingAndUriShotInOutChanges=true noNewOutputs=true originalsShaUnchanged=true");
        } finally { new File(original.uri.getPath()).delete(); }
    }

    private void assertRejected(Take cut, List<AutoColorBalance.Balance> measured) throws Exception {
        Set<String> cache = names(context.getCacheDir()), edits = names(new File(context.getFilesDir(), "exports")),
                journals = names(new File(context.getFilesDir(), "export-journal"));
        int gallery = galleryCount(); CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>(); AtomicInteger progress = new AtomicInteger();
        main.post(() -> new ReelExporter(context).export(Collections.singletonList(cut), "", "Auto balance", measured,
                new ReelExporter.Listener() {
                    public void onProgress(int percent) { progress.incrementAndGet(); }
                    public void onComplete(Uri video, Uri edit) { error.set("Unexpected successful export"); done.countDown(); }
                    public void onError(String message) { error.set(message); done.countDown(); }
                }));
        assertTrue(done.await(10, TimeUnit.SECONDS)); assertNotNull(error.get());
        assertTrue(error.get(), error.get().contains("Analyze this selection again")); assertEquals(0, progress.get());
        assertEquals(cache, names(context.getCacheDir())); assertEquals(edits, names(new File(context.getFilesDir(), "exports")));
        assertEquals(journals, names(new File(context.getFilesDir(), "export-journal"))); assertEquals(gallery, galleryCount());
    }
    private List<AutoColorBalance.Balance> analyze(List<Take> cuts) throws Exception {
        AutoColorBalance analyzer = new AutoColorBalance(context); CountDownLatch done = new CountDownLatch(1);
        AtomicReference<List<AutoColorBalance.Balance>> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        try {
            analyzer.analyze(cuts, new AutoColorBalance.Listener() {
                public void onComplete(List<AutoColorBalance.Balance> balances, long elapsed) { result.set(balances); done.countDown(); }
                public void onError(String message) { error.set(message); done.countDown(); }
            });
            assertTrue(done.await(45, TimeUnit.SECONDS)); assertNull(error.get(), error.get()); return result.get();
        } finally { analyzer.close(); }
    }
    private Exported export(List<Take> cuts, String look, List<AutoColorBalance.Balance> balances) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Uri> video = new AtomicReference<>(), edit = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(); ReelExporter exporter = new ReelExporter(context);
        main.post(() -> exporter.export(cuts, "", look, balances, new ReelExporter.Listener() {
            public void onProgress(int percent) { }
            public void onComplete(Uri v, Uri e) { video.set(v); edit.set(e); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        }));
        try {
            assertTrue(done.await(90, TimeUnit.SECONDS)); assertNull(error.get(), error.get());
            return new Exported(video.get(), edit.get(), new JSONObject(new String(bytes(edit.get()), StandardCharsets.UTF_8)));
        } finally { main.post(exporter::cancel); }
    }
    private float[][] means(Uri video) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, video);
            assertEquals("720", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("1280", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            float[][] result = new float[3][3];
            for (int i = 0; i < 3; i++) {
                Bitmap frame = retriever.getScaledFrameAtTime((300 + i * 800) * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST, 144, 256); assertNotNull(frame);
                try {
                    int count = 0;
                    for (int y = 8; y < frame.getHeight() - 8; y += 2) for (int x = 8; x < frame.getWidth() - 8; x += 2) {
                        int color = frame.getPixel(x, y); result[i][0] += Color.red(color);
                        result[i][1] += Color.green(color); result[i][2] += Color.blue(color); count++;
                    }
                    for (int c = 0; c < 3; c++) result[i][c] /= count;
                } finally { frame.recycle(); }
            }
            return result;
        } finally { retriever.release(); }
    }
    private byte[] bytes(Uri uri) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count; while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count); return out.toByteArray();
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder text = new StringBuilder();
        for (byte b : digest) text.append(String.format(java.util.Locale.US, "%02x", b & 255)); return text.toString();
    }
    private static float luma(float[] rgb) { return .2126f * rgb[0] + .7152f * rgb[1] + .0722f * rgb[2]; }
    private static Set<String> names(File directory) {
        Set<String> result = new HashSet<>(); File[] files = directory.listFiles(); if (files != null) for (File file : files) result.add(file.getName()); return result;
    }
    private int galleryCount() {
        try (Cursor cursor = context.getContentResolver().query(MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                new String[] { MediaStore.Video.Media._ID }, MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?", new String[] { "MiniFilm-%" }, null)) {
            assertNotNull(cursor); return cursor.getCount();
        }
    }
    private void cleanup(Exported output) {
        if (output == null) return;
        context.getContentResolver().delete(output.video, null, null);
        new File(new File(context.getFilesDir(), "exports"), "MiniFilm-" + output.project.optString("exportId") + ".json").delete();
        new AtomicFile(new File(new File(context.getFilesDir(), "export-journal"), output.project.optString("exportId") + ".json")).delete();
    }
    private static final class Exported {
        final Uri video, edit; final JSONObject project;
        Exported(Uri video, Uri edit, JSONObject project) { this.video = video; this.edit = edit; this.project = project; }
    }
}
