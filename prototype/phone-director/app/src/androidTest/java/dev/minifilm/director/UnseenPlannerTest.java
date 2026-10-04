package dev.minifilm.director;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Two fresh synthetic briefs. Targeted regressions, not a general accuracy estimate. */
@RunWith(AndroidJUnit4.class)
public final class UnseenPlannerTest {
    private static final String TAG = "MiniFilmUnseenPlannerTest";
    private static final String VERBS = "Stand|Pose|Take|Turn|Show|Hold|Look|Walk|Say|Tell|Explain|Share|Introduce|Welcome|Give|"
            + "Move|Lift|Face|Point|Bring|Keep|Smile|Wave|Tilt|Pause";

    @Test(timeout = 180_000) public void freshKurtaBriefDirectsTheWearerWithoutInventedColorMaterialOrFasteners() throws Exception {
        LocalPlanner planner = planner();
        try {
            List<Shot> shots = generate(planner,
                    "Make a five-shot solo fashion reel while I am wearing my kurta. "
                            + "Its color, material and fasteners are unspecified. Ask me to choose any visible garment detail.",
                    "Fashion");
            checkRoles(shots, "Hero pose", "Movement", "Detail", "Side pose", "Closing");
            String text = allText(shots);
            assertTrue("Fresh draft should retain the supplied kurta", word(text, "kurta"));
            deny(text, "white", "black", "red", "green", "blue", "yellow", "pink", "purple", "orange",
                    "brown", "grey", "gray", "navy", "beige", "maroon", "teal", "cyan", "magenta", "silver",
                    "gold", "golden", "cream", "tan", "ivory", "turquoise", "burgundy", "lavender", "khaki");
            deny(text, "cotton", "silk", "wool", "woolen", "linen", "denim", "leather", "polyester", "nylon",
                    "velvet", "satin", "rayon", "fleece", "viscose", "knitted", "embroidered", "embroidery",
                    "button", "buttons", "buttoned", "zip", "zipper", "zippers", "zipped", "zippered", "buckle", "buckles");
            assertFalse("Worn-kurta shoot should not become tabletop preparation",
                    text.contains("flat surface") || text.contains("tabletop"));
            for (Shot shot : shots) deny(shot.instruction, "camera", "phone", "screen", "tripod");
            String detail = shots.get(2).instruction;
            assertTrue("Creator-chosen detail should relate to the worn garment, not an invented material/fastener",
                    word(detail, "kurta") || word(detail, "garment") || word(detail, "outfit") || word(detail, "detail"));
            Log.i(TAG, "held_out_kurta targeted_checks=PASS synthetic_input=true general_accuracy_claim=false");
        } finally { planner.close(); }
    }

    @Test(timeout = 180_000) public void freshDevIntroductionKeepsComicsAndBadmintonWithoutExampleLeakageOrCredentials() throws Exception {
        LocalPlanner planner = planner();
        try {
            List<Shot> shots = generate(planner,
                    "I am Dev. I draw comics. I enjoy badminton. Make a five-shot introduction with direct performer cues. "
                            + "Use only these personal facts and let me choose a visible detail of my work.",
                    "Introduction");
            checkRoles(shots, "Name", "Work", "Detail", "Personal fact", "Welcome");
            assertTrue("Name role should preserve Dev rather than an example person's name", word(shotText(shots.get(0)), "Dev"));
            assertTrue("Work role should retain comics; the detail role may use grounded co-reference",
                    word(shotText(shots.get(1)), "comics") || word(shotText(shots.get(1)), "comic"));
            assertTrue("Personal fact role should retain badminton", word(shotText(shots.get(3)), "badminton"));
            String text = allText(shots);
            deny(text, "Rae", "Mina", "ceramic", "bread", "coffee", "hiking");
            // These supplied facts establish no degree, awards, seniority or professional credentials.
            deny(text, "degree", "degrees", "certified", "accredited", "award", "awards", "professional",
                    "expert", "founder", "CEO", "graduate", "graduated", "university", "professor", "doctor");
            assertFalse("Do not invent a number of years of professional experience",
                    Pattern.compile("\\b(?:[0-9]+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+years?\\b",
                            Pattern.CASE_INSENSITIVE).matcher(text).find());
            Log.i(TAG, "held_out_dev_intro targeted_checks=PASS synthetic_input=true general_accuracy_claim=false");
        } finally { planner.close(); }
    }

    private LocalPlanner planner() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Fresh planner fixture must execute without app Internet permission", "android.permission.INTERNET", permission);
        LocalPlanner planner = new LocalPlanner(context);
        assertTrue("Root must install the verified local model/runtime before this actual inference test", planner.isModelAvailable());
        return planner;
    }

    private List<Shot> generate(LocalPlanner planner, String brief, String style) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<List<Shot>> plan = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(), model = new AtomicReference<>();
        long began = SystemClock.elapsedRealtime();
        planner.generate(brief, style, new LocalPlanner.Listener() {
            public void onPlan(List<Shot> shots, long elapsedMs, String modelLabel) {
                plan.set(shots); model.set(modelLabel); done.countDown();
            }
            public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue("Fresh actual CPU generation timed out", done.await(150, TimeUnit.SECONDS));
        assertNull("Actual inference must succeed rather than use a template: " + error.get(), error.get());
        assertNotNull(plan.get()); assertNotNull(model.get()); assertTrue(model.get().contains("local CPU"));
        JSONArray logged = new JSONArray();
        for (Shot shot : plan.get()) logged.put(new JSONObject().put("title", shot.title)
                .put("instruction", shot.instruction).put("caption", shot.caption).put("durationMs", shot.targetDurationMs));
        // Full outputs here come ONLY from these synthetic briefs, never a creator's private content.
        Log.i(TAG, "synthetic_input=true held_out_style=" + style + " elapsedMs="
                + (SystemClock.elapsedRealtime() - began) + " model=" + model.get() + " full_plan=" + logged);
        return plan.get();
    }

    private static void checkRoles(List<Shot> shots, String... roles) {
        assertEquals("Exactly five recorded story roles", 5, shots.size());
        for (int i = 0; i < roles.length; i++) {
            Shot shot = shots.get(i);
            assertEquals("Role order must stay usable", roles[i], shot.title);
            assertTrue("Actual model source required", shot.id.startsWith("ai-shot-"));
            assertTrue("Direct performer command required: " + shot.instruction,
                    shot.instruction.matches("(?:" + VERBS + ") \\S.*"));
            assertTrue("Earbud directions must fit 90 characters", shot.instruction.length() <= 90);
            assertTrue("Recorded cut duration must be bounded", shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000);
            assertNotNull(shot.caption); assertFalse(shot.caption.trim().isEmpty());
            assertTrue("Caption draft must stay bounded", shot.caption.length() <= 30);
            assertFalse("Reject observed unfinished caption fragment", shot.caption.trim().toLowerCase(Locale.ROOT).matches("(?s).*\\bis\\.?$"));
        }
    }

    private static String shotText(Shot shot) { return shot.instruction + " " + shot.caption; }
    private static String allText(List<Shot> shots) {
        StringBuilder text = new StringBuilder();
        for (Shot shot : shots) text.append(shotText(shot)).append('\n');
        return text.toString().toLowerCase(Locale.ROOT);
    }
    private static boolean word(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    private static void deny(String text, String... words) {
        for (String word : words) assertFalse("Unsupplied fixture detail/example leakage: " + word, word(text, word));
    }
}
