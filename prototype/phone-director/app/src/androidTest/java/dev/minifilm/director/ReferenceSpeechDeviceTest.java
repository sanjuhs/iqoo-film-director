package dev.minifilm.director;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.MediaMetadataRetriever;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Existing labelled synthetic video + installed CPU models only. No Activity, microphone,
 * playback, preference changes or downloads. Named instructions are creator-authored retention,
 * not evidence of learned reference-speech grounding or general film-direction quality. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceSpeechDeviceTest {
    @Test(timeout = 200_000)
    public void actualReferenceAudioDraftExplicitCorrectionAndOneLocalPlanPreserveSourceAndNamedCues() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertDeniedCapture(context);
        File source = new File(context.getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Existing labelled synthetic English-video fixture required", source.isFile());
        long originalLength = source.length(), originalModified = source.lastModified();
        String originalHash = sha256(source); Uri sourceUri = Uri.fromFile(source);
        long sourceDurationMs;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, sourceUri);
            sourceDurationMs = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally { metadata.release(); }
        assertTrue("Existing labelled synthetic fixture duration", sourceDurationMs > 8500 && sourceDurationMs < 9000);
        ClipTranscriber reader = new ClipTranscriber(context);
        LocalPlanner planner = new LocalPlanner(context);
        assertTrue("Existing installed tiny.en model required", reader.isModelAvailable());
        assertTrue("Existing default local Qwen model required", planner.isModelAvailable());
        assertFalse("Headless pipeline requires no unrelated resident core", LocalModelLease.isHeld());
        CountDownLatch speechDone = new CountDownLatch(1);
        AtomicReference<ReferenceSpeechContext.Draft> draft = new AtomicReference<>();
        AtomicReference<String> speechError = new AtomicReference<>();
        AtomicReference<Throwable> constructionFailure = new AtomicReference<>();
        AtomicInteger speechCallbacks = new AtomicInteger(); AtomicBoolean speechMain = new AtomicBoolean();
        AtomicLong speechMs = new AtomicLong();
        CountDownLatch planDone = new CountDownLatch(1);
        AtomicReference<List<Shot>> plan = new AtomicReference<>();
        AtomicReference<String> planError = new AtomicReference<>(), modelLabel = new AtomicReference<>();
        AtomicInteger planCallbacks = new AtomicInteger(); AtomicBoolean planMain = new AtomicBoolean();
        AtomicLong planMs = new AtomicLong();
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> reader.transcribe(sourceUri,
                    new ClipTranscriber.Listener() {
                        public void onComplete(List<SubtitleCue> cues, long elapsedMs) {
                            speechCallbacks.incrementAndGet(); speechMain.set(Looper.myLooper() == Looper.getMainLooper());
                            try { draft.set(new ReferenceSpeechContext.Draft(sourceUri, cues, elapsedMs)); speechMs.set(elapsedMs); }
                            catch (Throwable failure) { constructionFailure.set(failure); }
                            finally { speechDone.countDown(); }
                        }
                        public void onError(String message) { speechCallbacks.incrementAndGet(); speechError.set(message); speechDone.countDown(); }
                    }));
            assertTrue("Synthetic reference speech timed out", speechDone.await(80, TimeUnit.SECONDS));
            assertNull(speechError.get()); assertNull(constructionFailure.get());
            assertEquals(1, speechCallbacks.get()); assertTrue(speechMain.get()); assertNotNull(draft.get());
            assertEquals(sourceUri, draft.get().sourceUri); assertFalse(draft.get().cues.isEmpty());
            String timedWords = draft.get().formatTimedText().toLowerCase(Locale.ROOT);
            assertTrue(timedWords.contains("jacket")); assertTrue(timedWords.contains("green")); assertTrue(timedWords.contains("outfit"));
            for (ReferenceSpeechContext.Cue cue : draft.get().cues)
                assertTrue(cue.startMs >= 0 && cue.endMs > cue.startMs && cue.endMs <= sourceDurationMs);

            // This explicit test-authored correction is not an automatic ASR summary.
            ReferenceSpeechContext.Reviewed corrected = draft.get().reviewed("The reference talks about a green jacket outfit.");
            assertTrue(corrected.matchesSource(sourceUri));
            assertEquals(corrected.text, ReferenceSpeechContext.Reviewed.fromJson(corrected.toJson(), sourceUri).text);
            String visualNotes = "0:01 Hero: face left; 0:05 Closing: face forward";
            String prompt = ReferenceSpeechContext.composePlanBrief("I wear my green jacket for a solo fashion reel.",
                    visualNotes, true, corrected);
            assertTrue(prompt.length() <= 500); assertTrue(prompt.endsWith(visualNotes));
            assertTrue(prompt.indexOf("Creator-reviewed reference speech:") < prompt.indexOf("Creator-reviewed reference moments:"));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> planner.generate(prompt, "Fashion",
                    new LocalPlanner.Listener() {
                        public void onPlan(List<Shot> shots, long elapsedMs, String label) {
                            planCallbacks.incrementAndGet(); planMain.set(Looper.myLooper() == Looper.getMainLooper());
                            plan.set(shots); modelLabel.set(label); planMs.set(elapsedMs); planDone.countDown();
                        }
                        public void onError(String message) { planCallbacks.incrementAndGet(); planError.set(message); planDone.countDown(); }
                    }));
            assertTrue("One bounded production planner request timed out", planDone.await(120, TimeUnit.SECONDS));
            Log.i("MiniFilmReferenceSpeechTest", "REFERENCE_SPEECH_PLAN_CALLBACK speech_elapsed_ms=" + speechMs.get()
                    + " source_duration_ms=" + sourceDurationMs + " cue_count=" + draft.get().cues.size()
                    + " prompt_chars=" + prompt.length() + " plan_elapsed_ms=" + planMs.get()
                    + " outcome=" + (planError.get() == null ? "draft" : "rejected"));
            // Only this labelled synthetic input's generated draft is retained in ignored device
            // evidence. Log before semantic assertions; no source URI or recognized words.
            if (plan.get() != null) {
                JSONArray output = new JSONArray();
                for (Shot shot : plan.get()) output.put(new JSONObject().put("title", shot.title)
                        .put("instruction", shot.instruction).put("caption", shot.caption)
                        .put("duration_ms", shot.targetDurationMs));
                Log.i("MiniFilmReferenceSpeechTest", "SYNTHETIC_PLAN_DRAFT model_label=" + modelLabel.get() + " shots=" + output);
            }
            assertNull("Production parser/generation failures must remain visible", planError.get());
            assertEquals(1, planCallbacks.get()); assertTrue(planMain.get()); assertNotNull(plan.get());
            assertEquals(5, plan.get().size());
            String[] titles = {"Hero pose", "Movement", "Detail", "Side pose", "Closing"};
            for (int i = 0; i < 5; i++) {
                Shot shot = plan.get().get(i); assertEquals(titles[i], shot.title);
                assertFalse(shot.instruction.isEmpty()); assertTrue(shot.instruction.length() <= 90);
                assertFalse(shot.caption.isEmpty()); assertTrue(shot.caption.length() <= 30);
                assertTrue(Character.isLetter(shot.caption.charAt(0)));
                assertFalse(shot.caption.matches(".*[0-9]{1,3}:[0-5][0-9].*"));
                assertTrue(shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000);
            }
            assertEquals("face left", plan.get().get(0).instruction);
            assertEquals("face forward", plan.get().get(4).instruction);
            assertTrue(modelLabel.get().contains("creator-authored reviewed cues retained"));
            assertTrue(modelLabel.get().contains("creator-choice detail constraint"));
            assertEquals(originalLength, source.length()); assertEquals(originalModified, source.lastModified());
            assertEquals(originalHash, sha256(source)); assertDeniedCapture(context);
            Log.i("MiniFilmReferenceSpeechTest", "REFERENCE_SPEECH_PIPELINE_OK backend=CPU asr=tiny.en"
                    + " speech_elapsed_ms=" + speechMs.get() + " cue_count=" + draft.get().cues.size()
                    + " prompt_chars=" + prompt.length() + " plan_elapsed_ms=" + planMs.get()
                    + " shots=5 named_cues=creator_authored source_unchanged=true capture=false playback=false");
        } finally {
            reader.close(); planner.close();
            assertTrue(worker(reader).awaitTermination(10, TimeUnit.SECONDS));
            assertTrue(worker(planner).awaitTermination(10, TimeUnit.SECONDS));
            assertEquals(originalLength, source.length()); assertEquals(originalModified, source.lastModified());
            assertEquals(originalHash, sha256(source)); assertDeniedCapture(context);
            assertFalse("Native core ownership must release after the pipeline", LocalModelLease.isHeld());
        }
    }

    private static ExecutorService worker(Object component) throws Exception {
        Field field = component.getClass().getDeclaredField("worker"); field.setAccessible(true);
        return (ExecutorService) field.get(component);
    }
    private static void assertDeniedCapture(Context context) {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }
}
