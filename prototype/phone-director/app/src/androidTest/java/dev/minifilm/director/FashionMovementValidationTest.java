package dev.minifilm.director;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Two observed native Movement errors and two synthetic analogs; no native generation or recording. */
@RunWith(AndroidJUnit4.class)
public final class FashionMovementValidationTest {
    private static final String TAG = "MiniFilmFashionMovementTest";
    // First two phrases were observed native outputs; collar/sleeve phrases are synthetic analogs.
    private static final String[] BAD = {"Take the left pocket.", "Walk to the left pocket.",
            "Move towards your collar.", "Turn into the sleeve."};
    private static final String[] VALID = {"Take two small steps, then show your left pocket.",
            "Turn slightly, then show your collar."};
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void productionParserRejectsObservedGarmentDestinationsWithoutChangingOtherRoles() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        for (String instruction : BAD) {
            JSONArray draft = fiveShots(roles, instruction);
            assertMovementRejected(() -> parse(draft.toString(), roles, actions));
            assertEquals("Movement", draft.getJSONObject(1).getString("title"));
            assertEquals("Rejected text must remain available for creator correction", instruction, draft.getJSONObject(1).getString("instruction"));
        }
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "NATIVE_DRAFT_VALIDATION_OK syntheticInputsOnly=true cases=4 observedNativeExamples=2 syntheticAnalogs=2 nativeGeneration=false arbitrarySemanticValidityClaim=false");
    }

    @Test public void legitimateMovementThenDetailRemainsAcceptedAndExactInDraftAndReviewedCuePaths() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        for (String instruction : VALID) {
            List<Shot> plan = parse(fiveShots(roles, instruction).toString(), roles, actions);
            assertEquals(5, plan.size()); assertEquals("Movement", plan.get(1).title); assertEquals(instruction, plan.get(1).instruction);
            for (int i = 0; i < plan.size(); i++) { assertEquals(roles[i], plan.get(i).title); assertEquals(4000, plan.get(i).targetDurationMs); }
            // Inspect the same reviewed-cue parser used by public generate, without starting its native worker.
            Object cues = call(method("parseReviewedCues", String.class, String[].class), reviewedBrief(instruction), roles);
            Field retained = cues.getClass().getDeclaredField("instructions"); retained.setAccessible(true);
            String[] instructions = (String[]) retained.get(cues);
            assertEquals("Valid creator wording must survive unchanged", instruction, instructions[1]);
            for (int i = 0; i < instructions.length; i++) if (i != 1) assertNull("Unspecified roles must not acquire manual instructions", instructions[i]);
        }
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "LEGITIMATE_MOVEMENT_DETAIL_OK syntheticInputsOnly=true cases=2 exactDraftAndReviewedWording=true nativeGeneration=false arbitrarySemanticValidityClaim=false");
    }

    @Test(timeout = 20_000) public void publicReviewedMovementPreflightReportsSpecificErrorsWithoutLoadingModelAndCloseSuppressesQueuedError() throws Exception {
        LocalPlanner planner = new LocalPlanner(context);
        try {
            assertTrue("Installed model must be available so errors exercise movement validation, not missing-model fallback", planner.isModelAvailable());
            for (String instruction : BAD) {
                AtomicReference<String> error = new AtomicReference<>(); AtomicInteger errors = new AtomicInteger(), plans = new AtomicInteger();
                AtomicBoolean onMain = new AtomicBoolean(true); CountDownLatch done = new CountDownLatch(1);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> planner.generate(reviewedBrief(instruction), "Fashion", new LocalPlanner.Listener() {
                    @Override public void onPlan(List<Shot> shots, long elapsedMs, String label) { plans.incrementAndGet(); done.countDown(); }
                    @Override public void onError(String message) {
                        if (Looper.myLooper() != Looper.getMainLooper()) onMain.set(false);
                        error.set(message); errors.incrementAndGet(); done.countDown();
                    }
                }));
                assertTrue("Movement preflight should return before any inference", done.await(3, TimeUnit.SECONDS));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(0, plans.get()); assertEquals(1, errors.get()); assertTrue(onMain.get()); assertMovementMessage(error.get());
                assertNoResidentModelOrBusy(planner);
            }
            AtomicInteger callbacks = new AtomicInteger(); CountDownLatch queueDrained = new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                planner.generate(reviewedBrief(BAD[0]), "Fashion", new LocalPlanner.Listener() {
                    @Override public void onPlan(List<Shot> shots, long elapsedMs, String label) { callbacks.incrementAndGet(); }
                    @Override public void onError(String message) { callbacks.incrementAndGet(); }
                });
                planner.close(); planner.close();
                new android.os.Handler(Looper.getMainLooper()).post(queueDrained::countDown);
            });
            assertTrue(queueDrained.await(3, TimeUnit.SECONDS)); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals("Close must suppress an already queued movement validation error", 0, callbacks.get());
            assertTrue((Boolean) instanceField(planner, "closed")); assertNoResidentModelOrBusy(planner);
            Log.i(TAG, "REVIEWED_MOVEMENT_PREFLIGHT_OK syntheticInputsOnly=true cases=4 observedNativeExamples=2 syntheticAnalogs=2 errorCallbacksOnMain=true"
                    + " residentHandle=0 modelLeaseHeld=false nativeGeneration=false closedQueuedCallbacks=0 arbitrarySemanticValidityClaim=false");
        } finally { planner.close(); }
    }

    @Test public void wholeBenignEyelineIsAcceptedWhileMixedAndUnrelatedDeviceCuesAreRejected() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        String benign = "Look at the camera.";
        JSONArray accepted = fiveShots(roles, VALID[0]); accepted.getJSONObject(4).put("instruction", benign);
        assertEquals(benign, parse(accepted.toString(), roles, actions).get(4).instruction);
        Object reviewed = call(method("parseReviewedCues", String.class, String[].class), reviewedClosingBrief(benign), roles);
        Field retained = reviewed.getClass().getDeclaredField("instructions"); retained.setAccessible(true);
        assertEquals("An allowed eyeline cue must remain the exact creator wording", benign, ((String[]) retained.get(reviewed))[4]);

        JSONArray mixed = fiveShots(roles, VALID[0]);
        mixed.getJSONObject(4).put("instruction", "Look at the camera and move the phone.");
        assertPolicyRejected(() -> parse(mixed.toString(), roles, actions), "device");
        JSONArray unrelated = fiveShots(roles, VALID[0]);
        // Stand is a legal Hero verb, so rejection must reach the device guard rather than the verb schema.
        unrelated.getJSONObject(0).put("instruction", "Stand while holding the camera.");
        assertPolicyRejected(() -> parse(unrelated.toString(), roles, actions), "device");
        assertPolicyRejected(() -> call(method("parseReviewedCues", String.class, String[].class),
                reviewedClosingBrief("Hold the camera."), roles), "device");
        assertPolicyRejected(() -> call(method("parseReviewedCues", String.class, String[].class),
                reviewedClosingBrief("Look at the camera and move the phone."), roles), "device");
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "EYELINE_DEVICE_POLICY_OK syntheticInputsOnly=true wholeBenignCueExact=true mixedAndUnrelatedRejected=true"
                + " nativeGeneration=false arbitrarySemanticValidityClaim=false");
    }

    @Test public void captionLetterLabelsAreSpecificallyRejectedWhileMeaningfulShortPhrasesRemainAccepted() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        // The F output motivated this check. Punctuation/spacing and repeated S are synthetic analogs.
        for (String label : new String[] {"F", "F.", "F   "}) {
            JSONArray draft = fiveShots(roles, VALID[0]); draft.getJSONObject(0).put("caption", label);
            assertPolicyRejected(() -> parse(draft.toString(), roles, actions), "caption", "label");
        }
        JSONArray repeated = fiveShots(roles, VALID[0]);
        for (int i = 0; i < roles.length; i++) repeated.getJSONObject(i).put("caption", "S");
        assertPolicyRejected(() -> parse(repeated.toString(), roles, actions), "caption", "label");
        for (String phrase : new String[] {"My outfit", "AI style", "I wear this"}) {
            JSONArray draft = fiveShots(roles, VALID[0]); draft.getJSONObject(0).put("caption", phrase);
            List<Shot> parsed = parse(draft.toString(), roles, actions);
            assertEquals("Valid captions must survive without rewriting", phrase, parsed.get(0).caption);
            assertEquals(5, parsed.size());
        }
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "CAPTION_LABEL_POLICY_OK syntheticInputsOnly=true rejectedCases=4 acceptedPhrases=3"
                + " exactCaptionPreserved=true nativeGeneration=false arbitrarySemanticValidityClaim=false");
    }

    @Test public void boundedCameraOperationsAreRejectedWhileBodyFacingAndHoldPoseStayExact() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        // Only the first phrase is the observed plain-jacket output; remaining cases are synthetic analogs.
        String[] bodyCues = {"Stand facing the camera with the jacket on your chest.", "Stand in front of camera.",
                "Face camera.", "Look at camera and hold your pose.", "Look at camera and adjust your jacket.",
                "Take a step in front of camera.", "Point toward camera."};
        for (String cue : bodyCues) {
            int index = cameraCueRole(cue);
            JSONArray draft = fiveShots(roles, VALID[0]); draft.getJSONObject(index).put("instruction", cue);
            assertEquals("Body/eyeline cues must not be rewritten", cue, parse(draft.toString(), roles, actions).get(index).instruction);
            assertExactReviewedCue(roles[index], cue, index, roles);
        }
        String[] operations = {"Stand while holding the camera.", "Turn the camera and hold your pose.",
                "Look at camera and move it.", "Stand facing camera and adjust this.", "Pose facing camera and lift that.",
                "Stand with the camera held in your hands.", "Stand with camera in your hand.", "Stand while holding the cameras.",
                "Move the phone.", "Stand and adjust the tripod.", "Take the camera.", "Bring the camera closer.",
                "Point the camera down.", "Stand while focusing camera.", "Look at camera and take it.",
                "Stand facing camera and bring it closer.", "Look at camera and point it down.", "Stand facing camera and focus it."};
        for (String cue : operations) {
            int index = cameraCueRole(cue);
            JSONArray draft = fiveShots(roles, VALID[0]); draft.getJSONObject(index).put("instruction", cue);
            assertPolicyRejected(() -> parse(draft.toString(), roles, actions), "device");
            assertPolicyRejected(() -> call(method("parseReviewedCues", String.class, String[].class),
                    "I wear my jacket.\nCreator-reviewed reference moments: 0:01 " + roles[index] + ": " + cue, roles), "device");
        }
        // Pronoun coverage here is it/this/that. No claim about them or arbitrary semantic co-reference.
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "BOUNDED_CAMERA_OPERATIONS_OK syntheticInputsOnly=true bodyCases=7 observedBodyExample=1 syntheticBodyAnalogs=6"
                + " syntheticOperatorCases=18 reviewedAndDraftExact=true pronouns=it,this,that nativeGeneration=false arbitrarySemanticValidityClaim=false");
    }

    @Test public void unspecifiedFashionDetailConstraintRejectsInventedPartsButExplicitReviewedDetailCanSupplyOne() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        List<Shot> generic = constrainedParse(fiveShots(roles, VALID[0]).toString(), roles, actions);
        assertEquals("Show one visible garment detail you choose.", generic.get(2).instruction);
        for (String part : new String[] {"zipper", "pocket"}) {
            JSONArray draft = fiveShots(roles, VALID[0]); draft.getJSONObject(2).put("instruction", "Show the jacket " + part + ".").put("caption", "Jacket " + part);
            assertPolicyRejected(() -> constrainedParse(draft.toString(), roles, actions), "creator choice");
            // When the creator actually supplies a named Detail, its native path is unconstrained and
            // their validated wording is retained separately; this does not assert learned feature grounding.
            assertEquals("Show the jacket " + part + ".", parse(draft.toString(), roles, actions).get(2).instruction);
            assertExactReviewedCue("Detail", "Show the jacket " + part + ".", 2, roles);
        }
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "FASHION_DETAIL_POLICY_OK syntheticInputsOnly=true unknownDetailCreatorChoice=true explicitCreatorFeatureAllowed=true"
                + " nativeGeneration=false learnedFeatureGroundingClaim=false");
    }

    @Test public void laterStepWordsMustNotExemptTakingGarmentPartsOrStepsTowardThem() throws Exception {
        String[] roles = roles(); String[][] actions = actions(); StringBuilder failures = new StringBuilder();
        // All seven inputs are new synthetic regressions, distinct from the earlier observed BAD examples.
        String[] invalid = {"Take the left pocket, then take two steps.", "Take your collar and take two small steps.",
                "Take two small steps toward your pocket."};
        String[] valid = {"Take two small steps while showing your pocket.", "Take a step and point to your collar.",
                "Turn slightly, then point to your collar.", "Walk two small steps, then point to your collar."};
        for (String cue : invalid) {
            JSONArray draft = fiveShots(roles, cue);
            try { assertMovementRejected(() -> parse(draft.toString(), roles, actions)); }
            catch (AssertionError failure) { failures.append("draft: ").append(cue).append(" -> ").append(failure.getMessage()).append('\n'); }
            try { assertMovementRejected(() -> call(method("parseReviewedCues", String.class, String[].class), reviewedBrief(cue), roles)); }
            catch (AssertionError failure) { failures.append("reviewed: ").append(cue).append(" -> ").append(failure.getMessage()).append('\n'); }
        }
        for (String cue : valid) {
            try {
                List<Shot> parsed = parse(fiveShots(roles, cue).toString(), roles, actions);
                assertEquals("Movement", parsed.get(1).title); assertEquals("Valid movement wording must remain exact", cue, parsed.get(1).instruction);
                assertExactReviewedCue("Movement", cue, 1, roles);
            } catch (AssertionError failure) { failures.append("valid exact retention: ").append(cue).append(" -> ").append(failure.getMessage()).append('\n'); }
        }
        assertFalse(LocalModelLease.isHeld());
        // Keep every known invalid result in the red-test report rather than stopping at the first miss.
        Log.i(TAG, "MOVEMENT_STEP_CLAUSE_REGRESSION syntheticInputsOnly=true syntheticInvalidCases=3 syntheticValidCases=4"
                + " nativeGeneration=false arbitrarySemanticValidityClaim=false failures=" + failures.toString().replace('\n', '|'));
        assertTrue("Synthetic movement regressions must reject garment-taking/destinations and preserve valid later-pointing cues:\n" + failures,
                failures.length() == 0);
    }

    @Test public void recordedNativeMovementPhrasesRemainExactInDraftAndReviewedPaths() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        // Exact native outputs from docs/plain-fashion-camera-detail-repair.json, respectively:
        // plain jacket, held-out kurta, hybrid unspecified Detail, hybrid explicit Detail.
        // Replaying them is deterministic validation, not new generation or a quality/generalization claim.
        String[] recorded = {"Take two steps forward while keeping the jacket in place.",
                "Take two small steps while keeping the kurta still.", "Take two small steps while facing the lens.", "Take two small steps."};
        for (String cue : recorded) {
            JSONArray input = fiveShots(roles, cue); List<Shot> plan = parse(input.toString(), roles, actions);
            assertEquals(5, plan.size()); assertEquals("Movement", plan.get(1).title);
            assertEquals("The actual recorded native phrasing must stay exact", cue, plan.get(1).instruction);
            assertExactReviewedCue("Movement", cue, 1, roles);
            assertEquals("Input text must remain intact", cue, input.getJSONObject(1).getString("instruction"));
        }
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "RECORDED_NATIVE_MOVEMENT_REPLAY_OK actualNativePhrasesReplayed=4 source=plain-fashion-camera-detail-repair.json"
                + " originalGenerationInputsSynthetic=true surroundingParserFixtureSynthetic=true exactDraftAndReviewed=true"
                + " nativeGeneration=false newModelAccuracyClaim=false");
    }

    private static int cameraCueRole(String cue) {
        if (cue.startsWith("Look")) return 4;
        if (cue.startsWith("Take") || cue.startsWith("Turn") || cue.startsWith("Move")) return 1;
        if (cue.startsWith("Point") || cue.startsWith("Bring")) return 2;
        return 0; // Stand/Face/Pose remain legal Hero openings, including embedded focusing.
    }

    @SuppressWarnings("unchecked") private static List<Shot> constrainedParse(String json, String[] roles, String[][] actions) throws Exception {
        return (List<Shot>) call(method("parse", String.class, String[].class, String[][].class, boolean.class), json, roles, actions, true);
    }
    private static void assertExactReviewedCue(String role, String cue, int index, String[] roles) throws Exception {
        Object reviewed = call(method("parseReviewedCues", String.class, String[].class),
                "I wear my jacket.\nCreator-reviewed reference moments: 0:01 " + role + ": " + cue, roles);
        Field retained = reviewed.getClass().getDeclaredField("instructions"); retained.setAccessible(true);
        assertEquals(cue, ((String[]) retained.get(reviewed))[index]);
    }

    private static String reviewedClosingBrief(String instruction) {
        return "I wear my jacket.\nCreator-reviewed reference moments: 0:01 Closing: " + instruction;
    }
    private static void assertPolicyRejected(Checked action, String... errorParts) throws Exception {
        try { action.run(); fail("Targeted invalid cue must be rejected by its specific policy"); }
        catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage()); String message = expected.getMessage().toLowerCase(Locale.ROOT);
            for (String part : errorParts) assertTrue("Wrong rejection reason: " + expected.getMessage(), message.contains(part));
        }
    }

    private static JSONArray fiveShots(String[] roles, String movement) throws Exception {
        String[] instructions = {"Stand in a relaxed pose.", movement, "Show one visible garment detail you choose.",
                "Turn slightly to one side.", "Look forward and hold your pose."};
        String[] captions = {"Outfit hero", "Small movement", "Chosen detail", "Side view", "Final pose"};
        JSONArray draft = new JSONArray();
        for (int i = 0; i < roles.length; i++) draft.put(new JSONObject().put("title", roles[i]).put("instruction", instructions[i])
                .put("caption", captions[i]).put("duration_ms", 4000));
        return draft;
    }
    @SuppressWarnings("unchecked") private static List<Shot> parse(String json, String[] roles, String[][] actions) throws Exception {
        return (List<Shot>) call(method("parse", String.class, String[].class, String[][].class, boolean.class), json, roles, actions, false);
    }
    private static String reviewedBrief(String instruction) { return "I wear my jacket.\nCreator-reviewed reference moments: 0:01 Movement: " + instruction; }
    private static String[] roles() throws Exception { return (String[]) constant("FASHION_ROLES"); }
    private static String[][] actions() throws Exception { return (String[][]) constant("FASHION_ACTIONS"); }
    private static Object constant(String name) throws Exception { Field field = LocalPlanner.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static Object instanceField(Object target, String name) throws Exception { Field field = LocalPlanner.class.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static Method method(String name, Class<?>... types) throws Exception { Method method = LocalPlanner.class.getDeclaredMethod(name, types); method.setAccessible(true); return method; }
    private static Object call(Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause(); if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause; throw new IllegalStateException(cause);
        }
    }
    private static void assertMovementMessage(String message) {
        assertNotNull(message); assertTrue("Reject specifically for movement, not some other schema or missing-model error: " + message,
                message.toLowerCase(Locale.ROOT).contains("movement"));
    }
    private static void assertMovementRejected(Checked action) throws Exception {
        try { action.run(); fail("Observed impossible garment destination must be rejected"); }
        catch (IllegalArgumentException expected) { assertMovementMessage(expected.getMessage()); }
    }
    private static void assertNoResidentModelOrBusy(LocalPlanner planner) throws Exception {
        assertEquals(0L, ((Number) instanceField(planner, "handle")).longValue());
        assertFalse("Rejected cues must permit a subsequent request", ((AtomicBoolean) instanceField(planner, "busy")).get());
        assertFalse(LocalModelLease.isHeld());
    }
    private interface Checked { void run() throws Exception; }
}
