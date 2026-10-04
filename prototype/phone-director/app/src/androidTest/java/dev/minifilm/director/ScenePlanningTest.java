package dev.minifilm.director;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Two actual CPU-model drafts from synthetic public inputs, never camera/microphone capture.
 * Assertions target these supplied facts and selected known failure modes, not general
 * factual accuracy, creator benefit, training, speech playback or semantic vision.
 */
@RunWith(AndroidJUnit4.class)
public final class ScenePlanningTest {
    private static final String[] TALKING_VERBS = {"Say|Tell|Share", "Tell|Explain|Share|Say",
            "Show|Hold|Point|Bring", "Explain|Tell|Share|Say", "Share|Say|Tell|Give"};
    private static final String[] INTRO_VERBS = {"Say|Tell|Introduce", "Tell|Explain|Show|Share",
            "Show|Hold|Point|Bring", "Share|Tell|Say", "Welcome|Wave|Smile|Say"};

    @Test(timeout = 180_000) public void talkingStoryRetainsSuppliedEventsAndFiveStoryRoles() throws Exception {
        LocalPlanner planner = new LocalPlanner(context());
        try {
            List<Shot> shots = generate(planner,
                    "Make a talking-head story: I missed the train, walked home, and decided to leave early next time. "
                            + "These are the only supplied events. Tell this story without inventing extra events or benefits.",
                    "Talking head");
            assertBoundedRoles(shots, TALKING_VERBS, "Hook", "Context", "Cutaway", "Key idea", "Takeaway");
            String text = signature(shots).toLowerCase(Locale.ROOT);
            assertTrue("The draft should retain the supplied train event", hasWord(text, "train"));
            assertTrue("The draft should retain walking home", text.contains("walk") && hasWord(text, "home"));
            assertTrue("The draft should retain the supplied leave-early lesson", hasWord(text, "early"));
            assertNoWords("This story has no supplied substitute transport or commercial affiliation", text,
                    "taxi", "uber", "bus", "bicycle", "flight", "airplane", "sponsor", "sponsored", "brand");
            assertFalse("Missing a train does not provide access to an empty train car for a cutaway",
                    Pattern.compile("\\bempty[\\s-]+train[\\s-]*(?:car|carriage)\\b", Pattern.CASE_INSENSITIVE)
                            .matcher(text).find());
            assertNoWords("No door or door-opening event is supplied in this story", text, "door", "doors", "opened");
            assertFalse("A decision to leave early next time must not become a past walked-out-early event",
                    Pattern.compile("\\bwalked\\s+out\\s+early\\b", Pattern.CASE_INSENSITIVE).matcher(text).find());
            String keyIdea = shotText(shots.get(3));
            assertTrue("The key idea must retain the supplied early-departure decision", hasWord(keyIdea, "early"));
            assertTrue("The key idea must frame the departure as a decision or future plan",
                    Pattern.compile("\\b(?:next\\s+time|plan|decision|decided|intend|will|going\\s+to|future)\\b", Pattern.CASE_INSENSITIVE)
                            .matcher(keyIdea).find());
            Log.i("MiniFilmScenePlanningTest", "talking_fixture pass=true shots=5 exact_roles=true bounded_cues=true"
                    + " supplied_train_walk_home_early=true targeted_unsupplied_events_absent=true");
        } finally { planner.close(); }
    }

    @Test(timeout = 180_000) public void creatorIntroductionRetainsNameWorkAndPersonalFactWithoutInventedCredentials() throws Exception {
        LocalPlanner planner = new LocalPlanner(context());
        try {
            List<Shot> shots = generate(planner,
                    "Introduce Rae. Rae makes ceramic bowls and likes coffee. These are the only supplied facts. "
                            + "No employer, school, credentials, awards, business or product benefits are supplied.",
                    "Introduction");
            assertBoundedRoles(shots, INTRO_VERBS, "Name", "Work", "Detail", "Personal fact", "Welcome");
            assertTrue("Name take must retain the supplied creator name", hasWord(shotText(shots.get(0)), "Rae"));
            String work = shotText(shots.get(1)).toLowerCase(Locale.ROOT);
            assertTrue("Work take must retain the supplied ceramic-bowl work", work.contains("ceramic") || work.contains("bowl"));
            String detail = shotText(shots.get(2)).toLowerCase(Locale.ROOT);
            // 'Your work' has a clear ceramic-bowls antecedent established in the preceding take.
            boolean namedWork = detail.contains("ceramic") || detail.contains("bowl");
            boolean explicitWorkReference = Pattern.compile("\\b(?:detail|feature)s?\\b[^.!?]{0,40}\\byour\\s+work\\b|"
                    + "\\byour\\s+work\\b[^.!?]{0,40}\\b(?:detail|feature)s?\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(detail).find();
            assertTrue("Detail must name the supplied work or explicitly ask for a feature/detail of your work",
                    namedWork || explicitWorkReference);
            assertNoWords("No specific ceramic-bowl part was supplied; the creator chooses the visible detail",
                    detail, "rim", "rims");
            assertTrue("Personal fact take must retain the supplied coffee preference", hasWord(shotText(shots.get(3)), "coffee"));
            assertNoWords("The fixture supplies no institution, employer, credential or commercial benefit", signature(shots),
                    "university", "college", "degree", "certified", "award", "awards", "sponsor", "sponsored",
                    "Nike", "Google", "Microsoft", "Adidas", "CEO", "founder", "doctor", "expert", "master",
                    "clients", "guaranteed", "cure", "heals", "unbreakable");
            Log.i("MiniFilmScenePlanningTest", "introduction_fixture pass=true shots=5 exact_roles=true bounded_cues=true"
                    + " supplied_name_work_coffee=true targeted_unsupplied_credentials_absent=true");
        } finally { planner.close(); }
    }

    private static void assertBoundedRoles(List<Shot> shots, String[] roleVerbs, String... titles) {
        assertEquals(5, shots.size());
        assertEquals(titles.length, roleVerbs.length);
        for (int i = 0; i < titles.length; i++) {
            Shot shot = shots.get(i);
            assertEquals("Each story role must retain its exact position", titles[i], shot.title);
            assertTrue("Direction must begin with an action appropriate to " + titles[i] + ": " + shot.instruction,
                    shot.instruction.matches("(?:" + roleVerbs[i] + ") \\S.*"));
            assertTrue("Earbud direction must remain concise", shot.instruction.length() <= 90);
            assertFalse("Caption cannot be empty", shot.caption.trim().isEmpty());
            assertTrue("Caption must remain bounded", shot.caption.length() <= 30);
            assertFalse("Caption must be human-readable text, not a numeric duration: " + shot.caption,
                    Pattern.compile("\\b\\d+(?:\\.\\d+)?\\s*(?:ms|milliseconds?|secs?|seconds?|s)\\b", Pattern.CASE_INSENSITIVE)
                            .matcher(shot.caption).find());
            assertFalse("Caption must not be a numeric-only label", shot.caption.trim().matches("\\d+(?:\\.\\d+)?"));
            assertTrue("Generated take must last between three and eight seconds",
                    shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000);
            assertEquals("Grammar emits whole-second durations", 0, shot.targetDurationMs % 1000);
        }
    }

    private static List<Shot> generate(LocalPlanner planner, String brief, String style) throws Exception {
        assertTrue("Install the existing local director-model.gguf before running this test", planner.isModelAvailable());
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<List<Shot>> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<String> label = new AtomicReference<>();
        long began = SystemClock.elapsedRealtime();
        planner.generate(brief, style, new LocalPlanner.Listener() {
            @Override public void onPlan(List<Shot> shots, long elapsedMs, String modelLabel) {
                result.set(shots); label.set(modelLabel); finished.countDown();
            }
            @Override public void onError(String message) { error.set(message); finished.countDown(); }
        });
        assertTrue("Actual local generation must complete within its bounded deadline", finished.await(150, TimeUnit.SECONDS));
        assertNull("A template fallback does not satisfy this model-execution test: " + error.get(), error.get());
        assertNotNull(result.get());
        assertNotNull(label.get());
        assertTrue("Report the actual CPU backend", label.get().contains("local CPU"));
        for (Shot shot : result.get()) assertTrue("Output must carry the AI-generated ID", shot.id.startsWith("ai-shot-"));
        Log.i("MiniFilmScenePlanningTest", "scene_case style=" + style + " synthetic_input=true model=" + label.get()
                + " elapsed_ms=" + (SystemClock.elapsedRealtime() - began));
        // Log only the new synthetic fixtures, never a real user's brief or footage.
        Log.i("MiniFilmScenePlanningTest", "synthetic_plan=" + signature(result.get()).replace('\n', ' '));
        return result.get();
    }

    private static String shotText(Shot shot) { return shot.instruction + " " + shot.caption; }

    private static String signature(List<Shot> shots) {
        StringBuilder text = new StringBuilder();
        for (Shot shot : shots) text.append(shot.title).append(" | ").append(shotText(shot)).append('\n');
        return text.toString();
    }

    private static boolean hasWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private static void assertNoWords(String message, String text, String... words) {
        for (String word : words) assertFalse(message + ": " + word, hasWord(text, word));
    }

    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
}
