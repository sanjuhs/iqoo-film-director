package dev.minifilm.director;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Actual CPU-model drafts from synthetic public inputs, never camera/microphone capture.
 * Assertions target these supplied facts and selected known failure modes, not general
 * factual accuracy, creator benefit, training, speech playback or semantic vision.
 */
@RunWith(AndroidJUnit4.class)
public final class ScenePlanningTest {
    private static final String[] TALKING_VERBS = {"Say|Tell|Share", "Tell|Explain|Share|Say",
            "Show|Hold|Point|Bring", "Explain|Tell|Share|Say", "Share|Say|Tell|Give"};
    private static final String[] INTRO_VERBS = {"Say|Tell|Introduce", "Tell|Explain|Show|Share",
            "Show|Hold|Point|Bring", "Share|Tell|Say", "Welcome|Wave|Smile|Say"};
    private static final String[] PRODUCT_VERBS = {"Hold|Show|Pose", "Turn|Lift|Bring|Show|Tilt|Move",
            "Show|Hold|Point|Bring", "Show|Hold|Lift|Tilt", "Say|Tell|Share|Give"};
    private static final String[] FASHION_VERBS = {"Stand|Pose|Face|Look|Smile", "Take|Turn|Walk|Move",
            "Show|Hold|Point|Bring", "Turn|Stand|Pose|Face", "Look|Smile|Pose|Stand|Wave"};

    @Test(timeout = 180_000) public void heldOutOvershirtAndJeansRetainsSuppliedOutfitWithCreatorChosenDetail() throws Exception {
        assertCaptureDeniedAndNoNetworkPermission();
        LocalPlanner planner = new LocalPlanner(context());
        try {
            AtomicReference<String> modelLabel = new AtomicReference<>();
            List<Shot> shots = generate(planner,
                    "Create a solo fashion reel while I wear my cream cotton overshirt with dark jeans. "
                            + "The overshirt has two chest pockets. No other colors, fabrics, fasteners, logos or patterns are supplied. "
                            + "Show the outfit with small body turns or one or two steps. "
                            + "The phone is already mounted; direct my performance without handling the filming device.",
                    "Fashion", modelLabel);
            assertBoundedRoles(shots, FASHION_VERBS, "Hero pose", "Movement", "Detail", "Side pose", "Closing");
            String text = signature(shots);
            assertTrue("The draft must retain the supplied overshirt", hasWord(text, "overshirt"));
            assertTrue("The draft must retain the supplied jeans, rather than discard half the outfit", hasWord(text, "jeans"));
            // Ordinary brief facts do not bypass the existing closed creator-choice Detail policy.
            // This is not learned selection of chest pockets; an explicit reviewed Detail is tested elsewhere.
            assertTrue("Generic feature selection must be disclosed", modelLabel.get().contains("creator-choice detail constraint"));
            assertFalse("An ordinary brief must not be labelled as retained manual reference cues",
                    modelLabel.get().contains("creator-authored reviewed cues retained"));
            String detail = shots.get(2).instruction;
            assertTrue("The creator chooses a visible garment detail",
                    hasWord(detail, "visible") && hasWord(detail, "garment") && hasWord(detail, "choose"));
            assertNoWords("No extra garment parts, fasteners, logos, patterns, fabric or named color are supplied", text,
                    "zipper", "zippers", "zip", "zipped", "button", "buttons", "buckle", "buckles",
                    "lapel", "lapels", "collar", "collars", "hem", "hems", "sleeve", "sleeves",
                    "logo", "logos", "pattern", "patterned", "stripe", "stripes", "striped", "floral",
                    "silk", "wool", "linen", "denim", "leather", "polyester", "nylon", "velvet", "satin",
                    "white", "black", "red", "green", "blue", "yellow", "pink", "purple", "orange",
                    "brown", "grey", "gray", "navy", "beige", "maroon", "teal", "silver", "gold");
            assertFalse("Whole-garment descriptors do not establish an individual pocket's color or fabric",
                    Pattern.compile("\\b(?:cream|cotton)\\s+(?:(?:cream|cotton|chest)\\s+){0,2}pockets?\\b|"
                            + "\\bpockets?\\s+(?:is|are|looks?|made\\s+of)\\s+(?:cream|cotton)\\b",
                            Pattern.CASE_INSENSITIVE).matcher(text).find());
            assertNoWords("Prompt examples and earlier fixtures must not leak into this outfit", text,
                    "raincoat", "jacket", "mug", "coffee", "pencil", "eraser", "cardboard", "Rae", "Mina");
            assertFalse("A worn-outfit performance must not become tabletop preparation",
                    text.toLowerCase(Locale.ROOT).contains("tabletop") || text.toLowerCase(Locale.ROOT).contains("flat surface"));
            for (Shot shot : shots) {
                assertNoMountedDeviceOperation(shot.instruction);
                assertNoWords("Captions describe the outfit or pose, not filming equipment", shot.caption,
                        "camera", "phone", "screen", "tripod", "gimbal", "drone");
            }
            Log.i("MiniFilmScenePlanningTest", "held_out_overshirt_jeans pass=true synthetic_input=true roles=5"
                    + " supplied_two_garments_retained=true creator_choice_detail_disclosed=true"
                    + " learned_pocket_selection_claim=false targeted_unsupplied_facts_absent=true general_accuracy_claim=false");
        } finally { planner.close(); assertCaptureDeniedAndNoNetworkPermission(); }
    }

    @Test(timeout = 180_000) public void talkingStoryRetainsSuppliedEventsAndFiveStoryRoles() throws Exception {
        LocalPlanner planner = new LocalPlanner(context());
        try {
            List<Shot> shots = generate(planner,
                    "Make a talking-head story: I missed the train, walked home, and decided to leave early next time. "
                            + "These are the only supplied events. Tell this story without inventing extra events or benefits.",
                    "Talking head");
            assertBoundedRoles(shots, TALKING_VERBS, "Hook", "Context", "Cutaway", "Key idea", "Takeaway");
            assertCreatorChoiceCutaway(shots.get(2));
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
            assertExplicitFuture(shots.get(3).instruction, "The departure is a next-time plan, not an already completed early departure");
            Log.i("MiniFilmScenePlanningTest", "talking_fixture pass=true shots=5 exact_roles=true bounded_cues=true"
                    + " supplied_train_walk_home_early=true targeted_unsupplied_events_absent=true");
        } finally { planner.close(); }
    }

    @Test(timeout = 180_000) public void heldOutUmbrellaStoryRetainsWetEventAndFutureForecastWithoutInventedCutaway() throws Exception {
        LocalPlanner planner = new LocalPlanner(context());
        try {
            List<Shot> shots = generate(planner,
                    "Make a talking-head story: I forgot my umbrella, got wet, and decided to check the forecast next time. "
                            + "These are the only supplied events. Do not invent another event, place or prop.", "Talking head");
            assertBoundedRoles(shots, TALKING_VERBS, "Hook", "Context", "Cutaway", "Key idea", "Takeaway");
            assertCreatorChoiceCutaway(shots.get(2));
            String hook = shotText(shots.get(0)), context = shotText(shots.get(1)), text = signature(shots);
            assertTrue("Hook must preserve the forgotten umbrella event", hasWord(hook, "umbrella")
                    && Pattern.compile("\\b(?:forgot|forgotten|forget|left\\s+(?:my|your|the)\\s+umbrella)\\b", Pattern.CASE_INSENSITIVE).matcher(hook).find());
            assertTrue("Context must retain getting wet", hasWord(context, "wet"));
            assertTrue("The supplied forecast belongs in the key idea", hasWord(shotText(shots.get(3)), "forecast"));
            assertExplicitFuture(shots.get(3).instruction, "Forecast checking is a next-time decision, not something already done");
            assertTrue("Takeaway must preserve the supplied forecast plan", hasWord(shotText(shots.get(4)), "forecast"));
            assertExplicitFuture(shots.get(4).instruction, "The final forecast cue must retain the future decision");
            assertNoWords("Forgotten umbrella and getting wet do not establish substitute props or transport", text,
                    "door", "doors", "opened", "train", "taxi", "uber", "bus", "cafe", "café", "raincoat", "towel", "sponsor", "brand");
            assertFalse("The story does not establish a completed forecast-checking event",
                    Pattern.compile("\\bchecked\\b[^.!?]{0,30}\\bforecast\\b", Pattern.CASE_INSENSITIVE).matcher(text).find());
            String cutaway = shotText(shots.get(2));
            assertNoWords("A forgotten umbrella is not an established available cutaway prop", cutaway, "umbrella", "forecast", "wet");
            Log.i("MiniFilmScenePlanningTest", "held_out_umbrella_story pass=true synthetic_input=true roles=5"
                    + " supplied_forgot_umbrella_wet_future_forecast=true creator_choice_cutaway_disclosed=true"
                    + " targeted_unsupplied_events_absent=true general_accuracy_claim=false");
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

    @Test(timeout = 180_000) public void heldOutStorageBoxProductRetainsSuppliedUseAndRequestsCreatorOpinionWithoutInventingParts() throws Exception {
        assertCaptureDeniedAndNoNetworkPermission();
        LocalPlanner planner = new LocalPlanner(context());
        try {
            List<Shot> shots = generate(planner,
                    "Create a solo product reveal for my cardboard storage box. It has a removable lid. "
                            + "I use it by putting index cards inside. Color, finish, printing and product benefits are unspecified. "
                            + "Ask me to choose one visible box detail and state my own honest opinion. "
                            + "The phone is already mounted; direct my hands with the box, never move or hold the filming device.",
                    "Product reveal");
            assertBoundedRoles(shots, PRODUCT_VERBS, "Hero", "Reveal", "Detail", "In use", "Verdict");
            assertTrue("Hero must direct the creator to present the supplied box", hasWord(shots.get(0).instruction, "box"));
            String text = signature(shots);
            assertTrue("The supplied removable lid should remain part of the reveal", hasWord(text, "lid"));
            String use = shots.get(3).instruction;
            assertTrue("In use must direct the stated index-card action, not generic product-use filler",
                    hasWord(use, "index") && (hasWord(use, "card") || hasWord(use, "cards"))
                            && Pattern.compile("\\b(?:put|puts|putting|place|places|placing|store|stores|storing|inside|into)\\b",
                                    Pattern.CASE_INSENSITIVE).matcher(use).find());
            String verdict = shots.get(4).instruction;
            assertTrue("Verdict must request the creator's view rather than invent a favorable endorsement",
                    Pattern.compile("\\b(?:your|own|honest)\\b", Pattern.CASE_INSENSITIVE).matcher(verdict).find()
                            && Pattern.compile("\\b(?:opinion|thoughts?|verdict|think|feel|view|take)\\b",
                                    Pattern.CASE_INSENSITIVE).matcher(verdict).find());
            assertNoWords("No color, coating, closure hardware, lining or product benefit is established", text,
                    "white", "black", "red", "green", "blue", "yellow", "pink", "purple", "orange",
                    "brown", "grey", "gray", "navy", "beige", "maroon", "teal", "silver", "gold",
                    "glossy", "matte", "shiny", "leather", "plastic", "metal", "wooden", "hinge", "hinges",
                    "lock", "locks", "latch", "latches", "lining", "lined", "zipper", "strap", "straps",
                    "waterproof", "durable", "premium", "luxury", "excellent", "guaranteed");
            assertNoWords("Product prompt examples must not become this storage-box scene", text,
                    "cup", "mug", "ceramic", "coffee", "handle", "handles", "bread", "Mina",
                    "pencil", "pencils", "eraser", "erasers", "draw", "drawing");
            for (Shot shot : shots) {
                assertNoMountedDeviceOperation(shot.instruction);
                assertNoWords("Typography should describe the product, not filming equipment", shot.caption,
                        "camera", "phone", "tripod", "gimbal", "drone");
            }
            Log.i("MiniFilmScenePlanningTest", "held_out_storage_box pass=true synthetic_input=true roles=5"
                    + " supplied_box_lid_index_card_use=true own_opinion_requested=true"
                    + " targeted_unsupplied_parts_benefits_absent=true mounted_phone=true general_accuracy_claim=false");
        } finally { planner.close(); assertCaptureDeniedAndNoNetworkPermission(); }
    }

    /** Independent bounded English fixtures: camera-facing/eyeline mentions remain legitimate.
     * This check does not establish complete physical feasibility or useful film direction. */
    private static void assertNoMountedDeviceOperation(String instruction) {
        assertFalse("Direct the product performance while the filming device remains mounted: " + instruction,
                Pattern.compile("\\b(?:hold|holding|move|moving|turn|turning|tilt|tilting|bring|bringing|point|pointing|"
                        + "take|taking|lift|lifting|adjust|adjusting|focus|focusing|pan|panning)\\s+"
                        + "(?:(?:a|the|your|my|filming|mounted)\\s+){0,3}(?:camera|phone|tripod|gimbal|drone)\\b",
                        Pattern.CASE_INSENSITIVE).matcher(instruction).find());
    }

    private static void assertCaptureDeniedAndNoNetworkPermission() throws Exception {
        Context context = context();
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Held-out planner must have no app Internet permission", Manifest.permission.INTERNET, permission);
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
        return generate(planner, brief, style, null);
    }

    private static List<Shot> generate(LocalPlanner planner, String brief, String style,
            AtomicReference<String> returnedLabel) throws Exception {
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
        if (returnedLabel != null) returnedLabel.set(label.get());
        JSONArray full = new JSONArray();
        for (Shot shot : result.get()) full.put(new JSONObject().put("id", shot.id).put("title", shot.title)
                .put("instruction", shot.instruction).put("caption", shot.caption).put("durationMs", shot.targetDurationMs));
        // These complete outputs and briefs are synthetic; log before targeted semantic assertions.
        Log.i("MiniFilmScenePlanningTest", new JSONObject().put("event", "FULL_SYNTHETIC_SCENE_PLAN")
                .put("syntheticInput", true).put("style", style).put("brief", brief).put("modelLabel", label.get())
                .put("elapsedMs", SystemClock.elapsedRealtime() - began).put("shots", full).put("generalAccuracyClaim", false).toString());
        assertTrue("Report the actual CPU backend", label.get().contains("local CPU"));
        for (Shot shot : result.get()) assertTrue("Output must carry the AI-generated ID", shot.id.startsWith("ai-shot-"));
        if (style.toLowerCase(Locale.ROOT).contains("talk") || style.toLowerCase(Locale.ROOT).contains("story"))
            assertTrue("Talking's creator-choice cutaway restriction must be disclosed", label.get().contains("creator-choice cutaway constraint"));
        return result.get();
    }

    private static void assertCreatorChoiceCutaway(Shot cutaway) {
        String instruction = cutaway.instruction.toLowerCase(Locale.ROOT);
        assertTrue("Cutaway must ask for an already available object", hasWord(instruction, "available") && hasWord(instruction, "object"));
        assertTrue("The creator chooses the actual object", hasWord(instruction, "choose"));
        assertTrue("Cutaway caption must remain generic story/choice wording", hasWord(cutaway.caption, "story"));
        assertNoWords("No specific cutaway place or unavailable story object is established", shotText(cutaway),
                "door", "opened", "train", "home", "umbrella", "forecast", "station", "platform", "rain", "car", "carriage");
    }
    private static void assertExplicitFuture(String instruction, String reason) {
        assertTrue(reason + ": " + instruction,
                Pattern.compile("\\b(?:next\\s+time|plan|intend|will|going\\s+to|future)\\b", Pattern.CASE_INSENSITIVE).matcher(instruction).find());
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
