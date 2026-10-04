package dev.minifilm.director;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Local VLM subject notes plus separately measured pose framing on three bounded fixtures. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceVisionTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test(timeout = 690_000) public void cpuVisionDistinguishesPublicPersonFrameFromSyntheticBlackFrame() throws Exception {
        assertNoInternetPermission();
        File fixture = new File(context().getFilesDir(), "public-pose-fixture.png");
        assertTrue("Root must copy the attributed public Google MLKit fixture", fixture.isFile());
        assertEquals("012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21", sha256(fixture));
        assertEquals("57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
                sha256(new File(context().getFilesDir(), "director-model.gguf")));
        assertEquals("91388cbe4ccde93acd902d7ce32776d14c32bd71a462d4affc1d2e226d81cada",
                sha256(new File(context().getFilesDir(), "models/director-mmproj.gguf")));
        VisionReference reviewer = new VisionReference(context());
        assertTrue("Exact-source projector must be installed in files/models/director-mmproj.gguf", reviewer.isModelAvailable());
        try {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
            Bitmap publicFrame = BitmapFactory.decodeFile(fixture.getAbsolutePath(), options);
            assertNotNull(publicFrame);
            // Input preprocessing only: a held-out top 32% crop shows head/shoulders,
            // excludes waist/feet, and is never named or shown as an example in the prompt.
            Bitmap portrait = Bitmap.createBitmap(publicFrame, 0, 0, publicFrame.getWidth(),
                    Math.max(1, Math.round(publicFrame.getHeight() * .32f)));
            try {
                String person = observe(reviewer, publicFrame, "google_mlkit_public_full_body");
                assertPerson(person);
                assertEquals("Pose framing must reflect the visible head and both feet", "full-body", framing(person));
                String cropped = observe(reviewer, portrait, "google_mlkit_public_held_out_head_shoulders_crop_top32pct");
                assertPerson(cropped);
                String portraitFraming = framing(cropped);
                assertTrue("Insufficient crop landmarks must abstain rather than invent a larger body extent",
                        portraitFraming.equals("head-and-shoulders") || portraitFraming.equals("review needed"));
                assertNotEquals("Cropped feet cannot establish a full body", "full-body", portraitFraming);
                assertNotEquals("The portrait crop excludes the waist", "waist-up", portraitFraming);
                assertNotEquals("Cropping changes actual framing", framing(person), framing(cropped));
                Bitmap blackFrame = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888);
                blackFrame.eraseColor(Color.BLACK);
                String black = observe(reviewer, blackFrame, "synthetic_black_512x384");
                boolean explicitAbsence = subject(black).toLowerCase(Locale.ROOT)
                        .matches("(?s).*\\b(no|none|nothing|empty|blank|black|unidentifiable)\\b.*");
                // Unknown is a conservative abstention, not evidence of absence recognition.
                // Accept it only with both an unclear pose label and exact unknown uncertainty.
                boolean unknownAbstention = subject(black).equals("unknown")
                        && framing(black).equals("empty or unclear") && uncertainty(black).equals("unknown");
                assertTrue("Black-frame notes must explicitly describe absence or jointly abstain, not invent a scene",
                        explicitAbsence || unknownAbstention);
                assertEquals("Blank pixels establish no visible shot framing", "empty or unclear", framing(black));
                assertNotEquals("Different pixels must result in distinct frame observations", person, black);
                assertEquals("Public source file must remain unchanged", "012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21", sha256(fixture));
                Log.i("MiniFilmReferenceTest", "actual_reference_cases=3 backend=cpu subject_backend=localVLM"
                        + " framing_backend=pose_landmark_heuristic public_person=true synthetic_black=true"
                        + " full_body=true held_out_head_shoulders_crop_input=true empty_unclear=true"
                        + " portrait_policy=conservative_fallback portrait_requires_review=" + portraitFraming.equals("review needed")
                        + " portrait_head_shoulders_label=" + portraitFraming.equals("head-and-shoulders")
                        + " black_outcome=" + (explicitAbsence ? "explicit_absence" : "uncertain_abstention")
                        + " network_permission=false frame_semantics_fixture_only=true");
            } finally {
                if (!publicFrame.isRecycled()) publicFrame.recycle();
                if (!portrait.isRecycled()) portrait.recycle();
            }
        } finally { reviewer.close(); }
    }

    @Test(timeout = 45_000) public void cancellationStopsLocalRequestWithoutDeliveringSuccessfulObservation() throws Exception {
        VisionReference reviewer = new VisionReference(context());
        assertTrue("Local files must be installed before cancellation evidence", reviewer.isModelAvailable());
        Bitmap synthetic = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        synthetic.eraseColor(Color.BLACK);
        CountDownLatch ended = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<String> success = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                reviewer.observe(synthetic, new VisionReference.Listener() {
                    @Override public void onObservation(String text, long elapsed, String label) {
                        success.set(text); callbacks.incrementAndGet(); ended.countDown();
                    }
                    @Override public void onError(String message) {
                        error.set(message); callbacks.incrementAndGet(); ended.countDown();
                    }
                });
                reviewer.cancel();
            });
            synthetic.recycle(); // API promises synchronous snapshot ownership.
            assertTrue("Cancellation should release the request promptly", ended.await(30, TimeUnit.SECONDS));
            assertNull("Cancelled work must never deliver a success", success.get());
            assertNotNull(error.get());
            assertTrue(error.get().toLowerCase(Locale.ROOT).contains("cancel"));
            assertEquals(1, callbacks.get());
            assertFalse("Native resources and serialized job permit must be released before callback", VisionReference.isRunning());
            Log.i("MiniFilmReferenceTest", "cancellation_fixture pass=true success_delivered=false callbacks=1 capture=false");
        } finally { if (!synthetic.isRecycled()) synthetic.recycle(); reviewer.close(); }
    }

    private String observe(VisionReference reviewer, Bitmap frame, String fixtureLabel) throws Exception {
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<String> observations = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<String> modelLabel = new AtomicReference<>();
        AtomicReference<Long> latency = new AtomicReference<>();
        try {
            reviewer.observe(frame, new VisionReference.Listener() {
                @Override public void onObservation(String text, long elapsed, String label) {
                    observations.set(text); latency.set(elapsed); modelLabel.set(label); ended.countDown();
                }
                @Override public void onError(String message) { error.set(message); ended.countDown(); }
            });
        } finally { frame.recycle(); }
        assertTrue("Actual vision should finish within its bounded native deadline", ended.await(215, TimeUnit.SECONDS));
        assertNull("Inference must succeed, not return manual notes: " + error.get(), error.get());
        assertNotNull(observations.get());
        assertTrue(modelLabel.get().contains("local CPU"));
        assertTrue("Combined notes must attribute framing to a separate pose heuristic",
                modelLabel.get().toLowerCase(Locale.ROOT).contains("pose landmark heuristic"));
        assertTrue(observations.get().length() <= 300);
        String[] lines = observations.get().split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("Subject: "));
        assertTrue(lines[1].startsWith("Framing: "));
        assertTrue(lines[2].startsWith("Uncertainty: "));
        assertTrue("Framing must come from the bounded pose labels", java.util.Arrays.asList("full-body", "head-and-shoulders",
                "waist-up", "empty or unclear", "review needed").contains(framing(observations.get())));
        for (String line : lines) assertTrue(line.substring(line.indexOf(':') + 2).length() <= 80);
        assertFalse(VisionReference.isRunning());
        // Only these public/synthetic test observations are logged, never creator reference media.
        Log.i("MiniFilmReferenceTest", "vision_fixture=" + fixtureLabel + " elapsed_ms=" + latency.get()
                + " backend=cpu subject_backend=localVLM framing_backend=pose_landmark_heuristic observations="
                + observations.get().replace('\n', '|'));
        return observations.get();
    }
    private String subject(String observations) { return observations.split("\n")[0].substring("Subject: ".length()); }
    private String framing(String observations) { return observations.split("\n")[1].substring("Framing: ".length()); }
    private String uncertainty(String observations) { return observations.split("\n")[2].substring("Uncertainty: ".length()); }
    private void assertPerson(String observations) {
        String text = subject(observations).toLowerCase(Locale.ROOT);
        String personWords = "person|woman|man|human|individual|figure|girl|boy|child|adult|kid";
        assertTrue("The public input depicts a person; local observations must recognize that subject",
                text.matches("(?s).*\\b(" + personWords + ")\\b.*"));
        assertFalse("An absence phrase must not count as recognizing the public person",
                text.matches("(?s).*\\b(no|not|without)\\b(?:\\s+[a-z]+){0,3}\\s+\\b(" + personWords + ")\\b.*")
                        || text.matches("(?s).*\\b(none|nothing|unidentifiable|unidentified)\\b.*")
                        || text.contains("not visible") || text.contains("not identifiable"));
    }
    private void assertNoInternetPermission() throws Exception {
        PackageInfo info = context().getPackageManager().getPackageInfo(context().getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Vision runs locally without internet permission", "android.permission.INTERNET", permission);
    }
    private String sha256(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) != -1) sha.update(buffer, 0, count);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : sha.digest()) text.append(String.format(Locale.US, "%02x", value & 255));
        return text.toString();
    }
}
