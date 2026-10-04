package dev.minifilm.director;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;
import java.util.List;
import java.util.Set;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual local model execution on synthetic public inputs; opens no mic/camera. */
@RunWith(AndroidJUnit4.class)
public final class LocalAITest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void bundledPoseRunsOnSyntheticEmptyImageWithoutInventingAPerson() throws Exception {
        PoseDetector detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
        Bitmap empty = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888);
        empty.eraseColor(Color.BLACK);
        try {
            for (int i = 0; i < 2; i++) {
                long began = SystemClock.elapsedRealtime();
                Pose result = Tasks.await(detector.process(InputImage.fromBitmap(empty, 0)), 90, TimeUnit.SECONDS);
                assertNotNull(result);
                assertTrue("An empty black frame should not invent a person", result.getAllPoseLandmarks().isEmpty());
                Log.i("MiniFilmLocalAITest", "pose_negative_case pass=true backend_preference=cpu input=synthetic_black_480x640 run=" + i +
                        " landmarks=" + result.getAllPoseLandmarks().size() + " elapsed_ms=" + (SystemClock.elapsedRealtime() - began));
            }
        } finally { detector.close(); empty.recycle(); }
    }

    @Test public void bundledPoseFindsLandmarksInAttributedPublicDocumentationFixture() throws Exception {
        File fixture = new File(context().getFilesDir(), "public-pose-fixture.png");
        assertTrue("Root must copy the attributed public fixture into the target sandbox", fixture.isFile());
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (FileInputStream source = new FileInputStream(fixture)) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = source.read(buffer)) != -1) sha.update(buffer, 0, count);
        }
        StringBuilder checksum = new StringBuilder();
        for (byte value : sha.digest()) checksum.append(String.format(Locale.US, "%02x", value & 255));
        assertEquals("012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21", checksum.toString());
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
        Bitmap bitmap = BitmapFactory.decodeFile(fixture.getAbsolutePath(), options);
        assertNotNull(bitmap);
        PoseDetector detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                .setPreferredHardwareConfigs(PoseDetectorOptions.CPU).build());
        try {
            long began = SystemClock.elapsedRealtime();
            Pose result = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 90, TimeUnit.SECONDS);
            assertEquals("A detected person should return the full landmark schema", 33, result.getAllPoseLandmarks().size());
            int visible = 0; float totalConfidence = 0;
            for (PoseLandmark point : result.getAllPoseLandmarks()) {
                float probability = point.getInFrameLikelihood();
                assertTrue(probability >= 0 && probability <= 1);
                totalConfidence += probability;
                if (probability >= .65f && point.getPosition().x >= 0 && point.getPosition().x <= bitmap.getWidth() &&
                        point.getPosition().y >= 0 && point.getPosition().y <= bitmap.getHeight()) visible++;
            }
            assertTrue("At least twelve landmarks should be confidently inside this public full-body frame", visible >= 12);
            Log.i("MiniFilmLocalAITest", "pose_public_fixture pass=true source=google_mlkit_documentation_annotated_image backend_preference=cpu landmarks=33 visible_at_065=" + visible +
                    " mean_inframe=" + (totalConfidence / 33f) + " elapsed_ms=" + (SystemClock.elapsedRealtime() - began));
        } finally { detector.close(); bitmap.recycle(); }
    }

    @Test public void offlineEnglishVoiceSynthesizesSyntheticCueToLocalFile() throws Exception {
        CountDownLatch initialized = new CountDownLatch(1);
        AtomicReference<TextToSpeech> engine = new AtomicReference<>();
        AtomicReference<Integer> status = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> engine.set(new TextToSpeech(context(), value -> {
            status.set(value); initialized.countDown();
        })));
        assertTrue("TTS engine initialization must complete", initialized.await(30, TimeUnit.SECONDS));
        TextToSpeech tts = engine.get();
        File output = new File(context().getCacheDir(), "synthetic-director-cue.wav");
        try {
            assertEquals(Integer.valueOf(TextToSpeech.SUCCESS), status.get());
            Set<Voice> voices = tts.getVoices(); Voice offline = null;
            if (voices != null) for (Voice candidate : voices) {
                if (!candidate.isNetworkConnectionRequired() && candidate.getLocale().getLanguage().equals("en")) {
                    offline = candidate; break;
                }
            }
            assertNotNull("An installed offline English voice is needed for local cues", offline);
            assertFalse(offline.isNetworkConnectionRequired());
            assertEquals(TextToSpeech.SUCCESS, tts.setVoice(offline));
            CountDownLatch synthesized = new CountDownLatch(1);
            AtomicReference<Integer> synthesisError = new AtomicReference<>();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { synthesized.countDown(); }
                @Override public void onError(String utteranceId) { synthesisError.set(-1); synthesized.countDown(); }
                @Override public void onError(String utteranceId, int code) { synthesisError.set(code); synthesized.countDown(); }
            });
            long began = SystemClock.elapsedRealtime();
            assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile("Relax your shoulders. Look at the lens. Hold your pose.", new Bundle(), output, "synthetic-cue"));
            assertTrue("Offline cue should synthesize within thirty seconds", synthesized.await(30, TimeUnit.SECONDS));
            assertNull("Offline synthesis should complete rather than error", synthesisError.get());
            assertTrue("Actual local audio bytes should exist", output.isFile() && output.length() > 1000);
            Log.i("MiniFilmLocalAITest", "tts_synthesis pass=true network_voice=false synthetic_input=true playback=false bytes=" + output.length() +
                    " elapsed_ms=" + (SystemClock.elapsedRealtime() - began));
        } finally { tts.shutdown(); }
    }

    @Test(timeout = 320_000) public void freshLocalPlannerAdaptsTwoSyntheticBriefsWithoutInternetPermission() throws Exception {
        PackageInfo info = context().getPackageManager().getPackageInfo(context().getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Target app must not have network permission", "android.permission.INTERNET", permission);
        LocalPlanner planner = new LocalPlanner(context());
        assertTrue("Root must first install files/director-model.gguf", planner.isModelAvailable());
        try {
            List<Shot> fashion = generate(planner, "Create a five-shot fashion reel for a bright yellow raincoat. Emphasize the raincoat and yellow color.", "Fashion");
            List<Shot> product = generate(planner, "Create a five-shot product reel for a blue ceramic coffee mug. Emphasize the coffee mug and its handle.", "Product reveal");
            assertOrderedPerformerShots(fashion, "Hero pose", "Movement", "Detail", "Side pose", "Closing");
            assertOrderedPerformerShots(product, "Hero", "Reveal", "Detail", "In use", "Verdict");
            assertNotEquals("Different scene briefs should produce different drafts", signature(fashion), signature(product));
            String fashionText = signature(fashion).toLowerCase(Locale.ROOT);
            String productText = signature(product).toLowerCase(Locale.ROOT);
            assertTrue("Fashion draft should reference the supplied raincoat or yellow color", fashionText.contains("raincoat") || fashionText.contains("yellow"));
            assertNoWords("Yellow raincoat fixture must not acquire unsupplied black/red colors", fashionText, "black", "red");
            assertNoWords("Yellow raincoat fixture supplies no pattern", fashionText, "pattern", "patterned");
            for (Shot shot : fashion) {
                String caption = shot.caption == null ? "" : shot.caption.trim().toLowerCase(Locale.ROOT);
                assertFalse("Caption must not end with the observed incomplete bare 'is' fragment: " + shot.caption,
                        caption.matches("(?s).*\\bis\\.?$"));
            }
            assertFalse("Fashion shots must not turn a worn-outfit shoot into tabletop preparation", fashionText.contains("flat surface") || fashionText.contains("tabletop"));
            int performanceCues = 0;
            for (Shot shot : fashion) {
                String instruction = shot.instruction.toLowerCase(Locale.ROOT);
                if (instruction.matches(".*\\b(stand|pose|step|steps|walk|turn|look|hold|face)\\b.*")) performanceCues++;
            }
            assertTrue("At least three fashion takes should direct the person's performance", performanceCues >= 3);
            assertTrue("Product draft should reference the supplied mug or coffee", productText.contains("mug") || productText.contains("coffee"));
            assertNoWords("The ceramic mug fixture supplies no finish, grip texture, bowl or cap details", productText,
                    "cap", "capped", "ridged", "grip", "glossy", "finish", "texture", "bowl");
            // The brief explicitly supplies a handle, but the mug's overall blue color
            // does not establish that handle's color. Keep 'hold the blue mug by its handle' valid.
            assertFalse("Do not propagate whole-mug color into an invented blue handle",
                    productText.matches("(?s).*\\bblue(?:[- ]colou?red)?\\s+(?:ceramic\\s+)?handle\\b.*")
                            || productText.matches("(?s).*\\bhandle\\s+(?:is\\s+|looks\\s+)?blue\\b.*"));
            Log.i("MiniFilmLocalAITest", "adaptive_plans pass=true cases=2 shots_each=5 brief_specific=true"
                    + " exact_role_order=true imperative_prefixes=true targeted_fixture_errors_absent=true network_permission=false");
        } finally { planner.close(); }
    }

    @Test(timeout = 180_000) public void unknownColorJacketProducesOrderedPerformerCuesWithoutInventingNamedColors() throws Exception {
        LocalPlanner planner = new LocalPlanner(context());
        assertTrue("Root must first install files/director-model.gguf", planner.isModelAvailable());
        try {
            List<Shot> plan = generate(planner,
                    "Help me perform a five-shot reel while wearing my jacket. I have not supplied its color, "
                            + "material, pattern, or fasteners. Give direct performer instructions without adding those details.",
                    "Fashion");
            assertOrderedPerformerShots(plan, "Hero pose", "Movement", "Detail", "Side pose", "Closing");
            String text = signature(plan).toLowerCase(Locale.ROOT);
            assertTrue("The draft should stay connected to the supplied jacket", text.contains("jacket"));
            assertNoWords("Unknown-color fixture must not invent a named garment/background color", text,
                    "white", "black", "red", "green", "blue", "yellow", "pink", "purple", "orange",
                    "brown", "grey", "gray", "navy", "beige", "maroon", "teal", "cyan", "magenta",
                    "silver", "gold", "golden", "cream", "tan", "ivory", "turquoise", "burgundy",
                    "lavender", "khaki");
            assertFalse("Worn-jacket fixture should direct a person rather than tabletop preparation",
                    text.contains("tabletop") || text.contains("flat surface"));
            for (Shot shot : plan) {
                // Conservative solo-performer boundary: lens eyeline is fine, equipment handling is not.
                // These actual-output checks cover the previously observed 'hold the camera steady'
                // and 'standing with camera'; they do not call the production validity guard.
                assertNoWords("A stationary-phone shoot must not ask the creator to operate filming equipment",
                        shot.instruction, "camera", "phone", "screen", "tripod");
            }
            assertTrue("Hero should give a body/eyeline pose, not recording setup",
                    plan.get(0).instruction.matches("(?:Stand|Pose|Face|Look|Smile) \\S.*"));
            assertTrue("Movement should direct a physical performer action",
                    plan.get(1).instruction.matches("(?:Take|Turn|Walk|Move) \\S.*"));
            assertTrue("The detail take should show the supplied garment rather than operate equipment",
                    plan.get(2).instruction.toLowerCase(Locale.ROOT)
                            .matches(".*\\b(jacket|garment|outfit|detail)\\b.*"));
            assertTrue("Side view should direct the person's stance or orientation",
                    plan.get(3).instruction.matches("(?:Turn|Stand|Pose|Face) \\S.*"));
            assertTrue("Closing should direct an expression, eyeline or pose",
                    plan.get(4).instruction.matches("(?:Look|Smile|Pose|Stand|Wave) \\S.*"));
            Log.i("MiniFilmLocalAITest", "unknown_jacket_fixture pass=true shots=5 exact_role_order=true"
                    + " imperative_prefixes=true instructions_max_chars=90 invented_named_colors=false"
                    + " solo_performer_role_actions=true filming_equipment_instructions=false synthetic_input=true");
        } finally { planner.close(); }
    }

    private void assertOrderedPerformerShots(List<Shot> shots, String... expectedTitles) {
        assertEquals(expectedTitles.length, shots.size());
        final String verbs = "Stand|Pose|Take|Turn|Show|Hold|Look|Walk|Say|Tell|Explain|Share|Introduce|Welcome|Give|"
                + "Move|Lift|Face|Point|Bring|Keep|Smile|Wave|Tilt|Pause";
        for (int i = 0; i < shots.size(); i++) {
            Shot shot = shots.get(i);
            assertEquals("Shot " + (i + 1) + " must preserve its reviewed story role", expectedTitles[i], shot.title);
            assertTrue("Shot " + (i + 1) + " must begin with a direct command: " + shot.instruction,
                    shot.instruction.matches("(?:" + verbs + ") \\S.*"));
            assertTrue("Direction must fit the concise earbud cue limit", shot.instruction.length() <= 90);
        }
    }

    private void assertNoWords(String message, String text, String... words) {
        for (String word : words) assertFalse(message + ": " + word,
                java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(word) + "\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text).find());
    }

    private List<Shot> generate(LocalPlanner planner, String brief, String style) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<List<Shot>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<String> model = new AtomicReference<>();
        long began = SystemClock.elapsedRealtime();
        planner.generate(brief, style, new LocalPlanner.Listener() {
            @Override public void onPlan(List<Shot> shots, long elapsedMs, String modelLabel) {
                result.set(shots); model.set(modelLabel); finished.countDown();
            }
            @Override public void onError(String message) { error.set(message); finished.countDown(); }
        });
        assertTrue("Local planner should complete within its bounded deadline", finished.await(150, TimeUnit.SECONDS));
        assertNull("Local planning must succeed rather than silently selecting templates: " + error.get(), error.get());
        assertNotNull(result.get());
        assertEquals(5, result.get().size());
        assertTrue(model.get().contains("local CPU"));
        for (Shot shot : result.get()) {
            assertTrue(shot.id.startsWith("ai-shot-"));
            assertFalse(shot.title.trim().isEmpty());
            assertFalse(shot.instruction.trim().isEmpty());
            assertFalse(shot.caption.trim().isEmpty());
            assertTrue(shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000);
        }
        Log.i("MiniFilmLocalAITest", "planner_case style=" + style + " synthetic_input=true model=" + model.get() +
                " elapsed_ms=" + (SystemClock.elapsedRealtime() - began) + " shots=" + result.get().size());
        // These are generated from the synthetic test briefs, never private user content.
        Log.i("MiniFilmLocalAITest", "synthetic_plan=" + signature(result.get()).replace('\n', ' '));
        return result.get();
    }

    private String signature(List<Shot> shots) {
        StringBuilder result = new StringBuilder();
        for (Shot shot : shots) result.append(shot.title).append(" | ").append(shot.instruction).append(" | ").append(shot.caption).append('\n');
        return result.toString();
    }
}
