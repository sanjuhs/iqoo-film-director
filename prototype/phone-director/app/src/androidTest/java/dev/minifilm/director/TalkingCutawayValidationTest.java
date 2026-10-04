package dev.minifilm.director;

import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

/** Deterministic observed Talking cutaway regression, not learned prop availability or story accuracy. */
@RunWith(AndroidJUnit4.class)
public final class TalkingCutawayValidationTest {
    private static final String TAG = "MiniFilmTalkingCutawayTest";
    private static final String[] CHOICE_INSTRUCTIONS = {
            "Show an available object you choose for your story.", "Hold an available object you choose for your story."};
    private static final String[] CHOICE_CAPTIONS = {"A story detail", "Chosen story object", "Your story detail"};

    @Test public void observedInventedDoorPairAndIndependentlyInventedInstructionOrCaptionAreRejected() throws Exception {
        String[] roles = roles(); String[][] actions = actions();
        // The actual failed synthetic train-story output named a door/opening not supplied by its brief.
        JSONArray observed = story(roles, "Show the door you opened.", "The door");
        assertCutawayRejected(() -> parse(observed.toString(), roles, actions));
        assertEquals("Rejected output must remain available for diagnosis", "Show the door you opened.", observed.getJSONObject(2).getString("instruction"));
        assertEquals("The door", observed.getJSONObject(2).getString("caption"));
        // Separate analogs prevent a valid instruction from laundering an invented caption, or vice versa.
        JSONArray captionOnly = story(roles, CHOICE_INSTRUCTIONS[0], "The door");
        assertCutawayRejected(() -> parse(captionOnly.toString(), roles, actions));
        JSONArray instructionOnly = story(roles, "Show the door you opened.", CHOICE_CAPTIONS[0]);
        assertCutawayRejected(() -> parse(instructionOnly.toString(), roles, actions));
        assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "TALKING_CUTAWAY_REJECTION_OK syntheticInputsOnly=true observedNativePair=1 syntheticIsolationAnalogs=2"
                + " nativeGeneration=false noOutputRewrite=true learnedAvailabilityClaim=false");
    }

    @Test public void creatorChoiceCutawayVariantsRemainExactAndDoNotRewriteOtherStoryRoles() throws Exception {
        String[] roles = roles(); String[][] actions = actions(); int combinations = 0;
        for (String instruction : CHOICE_INSTRUCTIONS) for (String caption : CHOICE_CAPTIONS) {
            JSONArray input = story(roles, instruction, caption); String originalJson = input.toString();
            List<Shot> plan = parse(originalJson, roles, actions); assertEquals(5, plan.size());
            for (int i = 0; i < plan.size(); i++) {
                JSONObject original = input.getJSONObject(i); Shot actual = plan.get(i);
                assertEquals("Every story role must preserve its title", original.getString("title"), actual.title);
                assertEquals("Validation must not replace the native/fixture instructions", original.getString("instruction"), actual.instruction);
                assertEquals("Validation must not replace captions", original.getString("caption"), actual.caption);
                assertEquals(original.getLong("duration_ms"), actual.targetDurationMs); assertEquals("ai-shot-" + (i + 1), actual.id);
            }
            assertEquals("Input JSON must remain unchanged", originalJson, input.toString()); combinations++;
        }
        assertEquals(6, combinations); assertFalse(LocalModelLease.isHeld());
        Log.i(TAG, "TALKING_CUTAWAY_CHOICE_OK syntheticInputsOnly=true combinations=6 exactFieldsPreserved=true otherRolesUnchanged=true"
                + " nativeGeneration=false learnedStoryGroundingClaim=false");
    }

    private static JSONArray story(String[] roles, String cutaway, String caption) throws Exception {
        String[] instructions = {"Say you missed the train.", "Tell how you walked home.", cutaway,
                "Explain your plan to leave early next time.", "Share your next time departure plan."};
        String[] captions = {"Missed my train", "Walking home", caption, "Next time plan", "My next step"};
        JSONArray result = new JSONArray();
        for (int i = 0; i < roles.length; i++) result.put(new JSONObject().put("title", roles[i]).put("instruction", instructions[i])
                .put("caption", captions[i]).put("duration_ms", 4000));
        return result;
    }
    @SuppressWarnings("unchecked") private static List<Shot> parse(String json, String[] roles, String[][] actions) throws Exception {
        Method parser = LocalPlanner.class.getDeclaredMethod("parse", String.class, String[].class, String[][].class, boolean.class);
        parser.setAccessible(true);
        try { return (List<Shot>) parser.invoke(null, json, roles, actions, false); }
        catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause(); if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause; throw new IllegalStateException(cause);
        }
    }
    private static String[] roles() throws Exception { return (String[]) constant("TALKING_ROLES"); }
    private static String[][] actions() throws Exception { return (String[][]) constant("TALKING_ACTIONS"); }
    private static Object constant(String name) throws Exception { Field field = LocalPlanner.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static void assertCutawayRejected(Checked action) throws Exception {
        try { action.run(); fail("Unconfirmed door cutaway must be rejected rather than rewritten"); }
        catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage()); String message = expected.getMessage().toLowerCase(Locale.ROOT);
            assertTrue("Reject specifically for cutaway: " + expected.getMessage(), message.contains("cutaway"));
            assertTrue("Reject specifically for creator choice: " + expected.getMessage(), message.contains("creator choice"));
        }
    }
    private interface Checked { void run() throws Exception; }
}
