package dev.minifilm.director;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual bundled CPU pose on attributed public pixels and black; no live capture/taste claim. */
@RunWith(AndroidJUnit4.class)
public final class FramePoseFramingTest {
    private static final String SHA = "012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 30_000) public void publicFullBodyAndHeldOutCropPermitConservativeAbstention() throws Exception {
        assertNoInternet(); File file = fixture(); assertEquals(SHA, digest(file));
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
        Bitmap full = BitmapFactory.decodeFile(file.getAbsolutePath(), options); assertNotNull(full);
        Bitmap crop = Bitmap.createBitmap(full, 0, 0, full.getWidth(), Math.max(1, Math.round(full.getHeight() * .32f)));
        try {
            FramePoseFraming.Result whole = inspect(full, "google_public_full_body");
            assertEquals("Confident visible nose/shoulders/hips/knees/ankles must support this full frame", "full-body", whole.label);
            assertTrue(whole.confidence >= .75f && whole.confidence <= 1);
            assertTrue(whole.visibleLandmarks >= 9);
            FramePoseFraming.Result head = inspect(crop, "google_public_top32_percent_crop");
            assertNotEquals("This crop must never be labelled full-body", "full-body", head.label);
            assertNotEquals("This crop must never be labelled waist-up", "waist-up", head.label);
            if ("head-and-shoulders".equals(head.label)) {
                assertTrue("A supported head label requires the unchanged landmark threshold",
                        head.confidence >= .75f && head.confidence <= 1);
                assertTrue(head.reason.contains("upper=3/3 hips=0/2 lower=0/4"));
            } else {
                assertEquals("Insufficient crop evidence must explicitly abstain", "review needed", head.label);
                assertEquals("Abstention must not imply framing confidence", 0, head.confidence, 0);
                assertTrue("Abstention must explain visible group evidence", head.reason.contains("upper="));
                assertTrue(head.reason.contains("hips=")); assertTrue(head.reason.contains("lower="));
                assertTrue(head.reason.contains("incomplete") || head.reason.contains("partial"));
            }
            // This gate proves conservative fallback on one public crop, not head-classification accuracy.
            assertNotEquals(whole.label, head.label);
            assertEquals("Public fixture bytes must remain unchanged", SHA, digest(file));
        } finally { full.recycle(); if (!crop.isRecycled()) crop.recycle(); }
    }

    @Test(timeout = 20_000) public void blackPixelsDoNotInventVisiblePersonOrFraming() throws Exception {
        assertNoInternet();
        long began = SystemClock.elapsedRealtime();
        FramePoseFraming.Result black = FramePoseFraming.inspect(new byte[512 * 384 * 3], 512, 384, () -> false);
        assertEquals("empty or unclear", black.label); assertEquals(0, black.visibleLandmarks);
        assertEquals(0, black.confidence, 0);
        assertTrue(black.provenance.contains("CPU")); assertTrue(black.provenance.contains("uncalibrated"));
        Log.i("MiniFilmFramePoseTest", "fixture=synthetic_black_512x384 label=" + black.label
                + " visible=0 elapsedMs=" + (SystemClock.elapsedRealtime() - began) + " framing=pose_landmark_heuristic");
    }

    @Test(timeout = 10_000) public void cancellationAfterTaskStartsReturnsReviewNeededWithoutGuessingAFrameLabel() throws Exception {
        Bitmap source = BitmapFactory.decodeFile(fixture().getAbsolutePath()); assertNotNull(source);
        try {
            RGB rgb = rgb(source); AtomicInteger polls = new AtomicInteger();
            long began = SystemClock.elapsedRealtime();
            FramePoseFraming.Result cancelled = FramePoseFraming.inspect(rgb.bytes, rgb.width, rgb.height,
                    () -> polls.incrementAndGet() > 1);
            assertEquals("review needed", cancelled.label); assertEquals(0, cancelled.confidence, 0);
            assertTrue(cancelled.provenance.contains("cancelled"));
            assertTrue("Cancellation must not wait for the whole five-second pose deadline",
                    SystemClock.elapsedRealtime() - began < 5000);
            // Pending input lifetime is maintained by the task-completion cleanup in production;
            // the helper's internal bitmap is not exposed as a misleading test observation.
        } finally { source.recycle(); }
    }

    @Test public void rejectsOversizedOrMalformedRgbAndPreCancelledRequestDoesNotClaimInference() {
        for (int[] shape : new int[][] {{513, 2}, {2, 513}, {0, 2}, {-1, 2}}) {
            try { FramePoseFraming.inspect(new byte[12], shape[0], shape[1], () -> false); fail("Invalid dimensions accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try { FramePoseFraming.inspect(new byte[11], 2, 2, () -> false); fail("Invalid RGB length accepted"); }
        catch (IllegalArgumentException expected) { }
        FramePoseFraming.Result cancelled = FramePoseFraming.inspect(new byte[12], 2, 2, () -> true);
        assertEquals("review needed", cancelled.label); assertTrue(cancelled.provenance.contains("cancelled before inference"));
    }

    private FramePoseFraming.Result inspect(Bitmap bitmap, String name) {
        RGB rgb = rgb(bitmap); long began = SystemClock.elapsedRealtime();
        FramePoseFraming.Result result = FramePoseFraming.inspect(rgb.bytes, rgb.width, rgb.height, () -> false);
        // Only fixture label and aggregate evidence are logged, never creator media/coordinates.
        Log.i("MiniFilmFramePoseTest", "fixture=" + name + " label=" + result.label + " visible=" + result.visibleLandmarks
                + " minRequiredLikelihood=" + result.confidence + " elapsedMs=" + (SystemClock.elapsedRealtime() - began)
                + " width=" + rgb.width + " height=" + rgb.height + " reason=" + result.reason
                + " provenance=" + result.provenance);
        return result;
    }
    private static RGB rgb(Bitmap source) {
        float scale = Math.min(1f, 512f / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, Math.round(source.getWidth() * scale)), height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        try {
            int[] pixels = new int[width * height]; scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            byte[] bytes = new byte[pixels.length * 3];
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i], alpha = pixel >>> 24;
                bytes[i * 3] = (byte) (((pixel >>> 16) & 255) * alpha / 255);
                bytes[i * 3 + 1] = (byte) (((pixel >>> 8) & 255) * alpha / 255);
                bytes[i * 3 + 2] = (byte) ((pixel & 255) * alpha / 255);
            }
            return new RGB(bytes, width, height);
        } finally { if (scaled != source) scaled.recycle(); }
    }
    private File fixture() {
        File file = new File(context.getFilesDir(), "public-pose-fixture.png");
        assertTrue("Root must install the attributed public Google MLKit image", file.isFile()); return file;
    }
    private void assertNoInternet() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Bundled framing runs without app Internet permission", "android.permission.INTERNET", permission);
    }
    private static String digest(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (FileInputStream source = new FileInputStream(file)) {
            byte[] block = new byte[8192]; int count; while ((count = source.read(block)) != -1) hash.update(block, 0, count);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : hash.digest()) text.append(String.format(Locale.US, "%02x", value & 255));
        return text.toString();
    }
    private static final class RGB {
        final byte[] bytes; final int width, height;
        RGB(byte[] bytes, int width, int height) { this.bytes = bytes; this.width = width; this.height = height; }
    }
}
