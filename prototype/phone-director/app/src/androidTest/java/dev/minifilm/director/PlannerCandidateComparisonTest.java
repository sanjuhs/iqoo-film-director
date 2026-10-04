package dev.minifilm.director;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Raw native model comparison on three synthetic inputs, not product benefit or general accuracy. */
@RunWith(AndroidJUnit4.class)
public final class PlannerCandidateComparisonTest {
    private static final String TAG = "MiniFilmPlannerComparison";
    private static final String BASELINE_SHA = "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final String CANDIDATE_SHA = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";
    private static final long BASELINE_BYTES = 563036064L, CANDIDATE_BYTES = 1117320736L;
    private static final Fixture[] FIXTURES = {
            new Fixture("reviewed-jacket", "Fashion", "FASHION_ROLES", "FASHION_ACTIONS",
                    "I wear my jacket, which has a left pocket. No color or material is given. "
                            + "Creator-reviewed moments: 0:02 Hero: face left; 0:05 Detail: show the left pocket; "
                            + "0:08 Side pose: pause in profile; 0:11 Closing: face forward."),
            new Fixture("new-person-introduction", "Introduction", "INTRO_ROLES", "INTRO_ACTIONS",
                    "I am Nila. I repair bicycles. I enjoy chess. Introduce me using only these facts; "
                            + "I can choose a visible detail of my bicycle work."),
            new Fixture("new-notebook-product", "Product reveal", "PRODUCT_ROLES", "PRODUCT_ACTIONS",
                    "My notebook has an elastic closure and a ribbon bookmark. I use it for lists. "
                            + "Show the elastic closure in Detail. No color, material or benefit is supplied; ask for my own opinion.")
    };
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test(timeout = 600_000) public void existingQwen35CoreProducesThreeRawUnmergedComparisonDrafts() throws Exception {
        compare(new File(context.getFilesDir(), "director-model.gguf"), BASELINE_BYTES, BASELINE_SHA,
                "Qwen3.5 0.8B Q4_0", "ggml-org/Qwen3.5-0.8B-GGUF@8fea620810c4afa23dd6443f999a48574c1611a3", true);
    }

    @Test(timeout = 600_000) public void pinnedOfficialQwen25CandidateProducesTheSameThreeRawComparisonDraftsIfPresent() throws Exception {
        File candidate = new File(context.getFilesDir(), "models/planner-qwen25-1.5b-q4km.gguf");
        if (!candidate.isFile()) Log.i(TAG, "CANDIDATE_NOT_RUN reason=verified_candidate_not_present syntheticInputsOnly=true");
        Assume.assumeTrue("Parent must install the separately pinned candidate before comparison", candidate.isFile());
        compare(candidate, CANDIDATE_BYTES, CANDIDATE_SHA, "Qwen2.5 1.5B Instruct Q4_K_M",
                "Qwen/Qwen2.5-1.5B-Instruct-GGUF@91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf", false);
    }

    private void compare(File model, long expectedBytes, String expectedSha, String name, String pin, boolean emptyThinkAdapter) throws Exception {
        assertNoInternetPermission(); assertTrue("Pinned model must be a private local file", model.isFile());
        assertEquals("Model byte count must match before native load", expectedBytes, model.length());
        assertEquals("Model SHA must match before native load", expectedSha, sha(model));
        Api api = new Api(); List<String> hardFailures = new ArrayList<>(); JSONArray results = new JSONArray();
        LocalModelLease.Token lease = LocalModelLease.acquire("raw-planner-comparison", () -> Thread.currentThread().isInterrupted(), 5000);
        long handle = 0; boolean released = false; String loadFailure = null;
        long loadStarted = SystemClock.elapsedRealtime();
        try {
            try { handle = ((Number) invoke(api.load, model.getAbsolutePath())).longValue(); if (handle == 0) throw new IllegalStateException("Native load returned no handle"); }
            catch (Exception | LinkageError failure) { loadFailure = describe(failure); }
            Log.i(TAG, "COMPARISON_MODEL syntheticInputsOnly=true model=" + name + " pin=" + pin + " sha=" + expectedSha
                    + " bytes=" + expectedBytes + " backend=localCPU threads=4 context=1536 maxOutputTokens=580"
                    + " promptMode=full_production_style whitespaceBound=8"
                    + " chatAdapter=" + (emptyThinkAdapter ? "ChatML_empty_think" : "ChatML")
                    + " loadElapsedMs=" + (SystemClock.elapsedRealtime() - loadStarted) + " noManualMerge=true");
            for (Fixture fixture : FIXTURES) {
                long began = SystemClock.elapsedRealtime(); JSONObject record = new JSONObject().put("case", fixture.id);
                try {
                    if (loadFailure != null) throw new IllegalStateException(loadFailure);
                    String[] roles = (String[]) value(fixture.rolesField); String[][] actions = (String[][]) value(fixture.actionsField);
                    // Historical raw experiment: this Fashion brief explicitly supplies a named pocket Detail;
                    // keep its unconstrained Detail path, rather than silently turn the comparison into a starter rule.
                    String grammar = (String) invoke(api.grammar, roles, actions, false);
                    String prompt = (String) invoke(api.prompt, fixture.brief, fixture.style, roles, actions,
                            fixture.id.equals("reviewed-jacket"), false, emptyThinkAdapter);
                    byte[] output = (byte[]) invoke(api.generate, handle, prompt.getBytes(StandardCharsets.UTF_8), grammar.getBytes(StandardCharsets.UTF_8), 580);
                    if (output == null) throw new IllegalStateException("Native generation returned null");
                    String raw = new String(output, StandardCharsets.UTF_8); long elapsed = SystemClock.elapsedRealtime() - began;
                    // Complete outputs are synthetic. Preserve raw bytes as JSON text even if parsing/shape later fails.
                    Log.i(TAG, new JSONObject().put("event", "RAW_NATIVE_COMPARISON").put("syntheticInputsOnly", true)
                            .put("model", name).put("pin", pin).put("sha", expectedSha).put("backend", "localCPU")
                            .put("threads", 4).put("case", fixture.id).put("brief", fixture.brief).put("elapsedMs", elapsed)
                            .put("promptChars", prompt.length()).put("promptMode", "full_production_style").put("whitespaceBound", 8)
                            .put("constrainDetail", false).put("reviewedMoments", fixture.id.equals("reviewed-jacket"))
                            .put("detailExperiment", fixture.id.equals("reviewed-jacket") ? "explicit_supplied_pocket_raw_unmerged" : "not_fashion")
                            .put("noManualMerge", true).put("rawJSON", raw).toString());
                    @SuppressWarnings("unchecked") List<Shot> shots = (List<Shot>) invoke(api.parse, raw, roles, actions, false);
                    checkShape(shots, roles);
                    JSONObject quality = quality(fixture.id, shots); boolean qualityPass = true;
                    java.util.Iterator<String> keys = quality.keys(); while (keys.hasNext()) if (!quality.getBoolean(keys.next())) qualityPass = false;
                    record.put("elapsedMs", elapsed).put("nativeShapePass", true).put("targetedQuality", quality)
                            .put("targetedQualityPass", qualityPass).put("manualSemanticReviewRequired", true);
                    Log.i(TAG, new JSONObject().put("event", "TARGETED_COMPARISON_QUALITY").put("model", name)
                            .put("syntheticInputsOnly", true).put("result", record).put("generalAccuracyClaim", false).toString());
                } catch (Exception | LinkageError | AssertionError failure) {
                    String problem = fixture.id + ": " + describe(failure); hardFailures.add(problem);
                    record.put("elapsedMs", SystemClock.elapsedRealtime() - began).put("nativeShapePass", false).put("failure", describe(failure));
                    Log.w(TAG, new JSONObject().put("event", "COMPARISON_NATIVE_OR_SHAPE_FAILURE").put("model", name)
                            .put("syntheticInputsOnly", true).put("result", record).toString());
                }
                results.put(record); // Semantic failures must not drop either subsequent brief.
            }
        } finally {
            if (handle != 0) {
                try { invoke(api.cancel, handle); } catch (Exception | LinkageError failure) { hardFailures.add("cancel: " + describe(failure)); }
                // Calls are synchronous: generation has returned before free. Cancellation alone is never completion.
                try { invoke(api.free, handle); released = true; }
                catch (Exception | LinkageError failure) { hardFailures.add("free: " + describe(failure)); }
            } else released = true;
            if (released) lease.close(); // Fail closed if nativeFree itself fails; never lend out a potentially live core.
        }
        Log.i(TAG, new JSONObject().put("event", "RAW_COMPARISON_SUMMARY").put("model", name).put("pin", pin)
                .put("syntheticInputsOnly", true).put("cases", results).put("hardFailures", new JSONArray(hardFailures))
                .put("modelReleased", released).put("promptMode", "full_production_style").put("whitespaceBound", 8)
                .put("manualSemanticReviewRequired", true).put("generalAccuracyClaim", false).toString());
        assertEquals("Comparison must retain every synthetic result", 3, results.length());
        assertEquals("Original model file must remain intact", expectedBytes, model.length()); assertEquals(expectedSha, sha(model));
        assertTrue("Native/shape failures after collecting all briefs: " + hardFailures, hardFailures.isEmpty());
        assertTrue("Native core must be freed before lease release", released); assertFalse(LocalModelLease.isHeld());
    }

    private static JSONObject quality(String id, List<Shot> shots) throws Exception {
        JSONObject result = new JSONObject(); String text = allText(shots);
        result.put("noUnsuppliedColorsMaterialsDevices", !has(text, "red|blue|green|yellow|black|white|orange|purple|pink|brown|grey|gray|navy|beige|"
                + "leather|cotton|denim|wool|velvet|silk|linen|polyester|paper|plastic|metal|wood|glass|ceramic|camera|phone|screen|tripod|gimbal|drone"));
        result.put("noUnfinishedCaption", shots.stream().noneMatch(s -> s.caption.trim().toLowerCase(Locale.ROOT).matches("(?s).*\\b(is|the|a|and|of|with)\\.?$")));
        if (id.equals("reviewed-jacket")) {
            result.put("heroLeftDirectionKeyword", has(shots.get(0).instruction, "left"));
            result.put("detailLeftPocket", has(shotText(shots.get(2)), "left") && has(shotText(shots.get(2)), "pocket"));
            result.put("sideProfileKeyword", has(shots.get(3).instruction, "profile"));
            result.put("closingForwardKeyword", has(shots.get(4).instruction, "forward"));
            result.put("noUnsuppliedGarmentParts", !has(text, "lapels?|collars?|buttons?|zippers?|buckles?|hems?|sleeves?"));
            result.put("noMovementDestinationGarmentPart", !Pattern.compile("\\b(?:walk|move|take|turn)\\b.{0,50}\\b(?:to|toward|towards|into)\\b.{0,25}\\b(?:pocket|lapel|collar|button|zipper|buckle|hem|sleeve)\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(shots.get(1).instruction).find());
        } else if (id.equals("new-person-introduction")) {
            result.put("nameNila", has(shotText(shots.get(0)), "Nila"));
            result.put("workBicycleRepair", has(shotText(shots.get(1)), "bicycles?|bikes?") && has(shotText(shots.get(1)), "repair|repairs|repairing|fix|fixing"));
            result.put("detailRelatesToBicycleWork", has(shotText(shots.get(2)), "bicycles?|bikes?"));
            result.put("personalChess", has(shotText(shots.get(3)), "chess"));
            result.put("noExampleFactOrCredentialLeakage", !has(text, "Mina|Dev|Rae|bread|comics?|badminton|hiking|doctor|degree|certified|award|expert|founder|university")
                    && !Pattern.compile("\\b(?:[0-9]+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+years?\\b", Pattern.CASE_INSENSITIVE).matcher(text).find());
        } else {
            result.put("heroNotebook", has(shotText(shots.get(0)), "notebook"));
            result.put("detailElasticClosure", has(shotText(shots.get(2)), "elastic") && has(shotText(shots.get(2)), "closure"));
            result.put("useLists", has(shotText(shots.get(3)), "lists?|list-making"));
            result.put("verdictRequestsOwnOpinion", has(shots.get(4).instruction, "your|own") && has(shotText(shots.get(4)), "opinion|think|thoughts|feel"));
            result.put("noUnsupportedBenefitOrProductPart", !has(text, "durable|premium|waterproof|luxury|smooth|stylish|perfect|sturdy|useful|leather|pocket|zipper|handle|cap|cup|bottle|ceramic"));
        }
        return result; // Keyword/lexical checks are targeted review aids, not complete semantics or accuracy.
    }
    private static void checkShape(List<Shot> shots, String[] roles) {
        assertEquals(5, shots.size());
        for (int i = 0; i < shots.size(); i++) {
            Shot shot = shots.get(i); assertEquals(roles[i], shot.title); assertEquals("ai-shot-" + (i + 1), shot.id);
            assertFalse(shot.instruction.trim().isEmpty()); assertTrue(shot.instruction.length() <= 90);
            assertFalse(shot.caption.trim().isEmpty()); assertTrue(shot.caption.length() <= 30); assertTrue(Character.isLetter(shot.caption.charAt(0)));
            assertFalse(Pattern.compile("[0-9]{1,3}:[0-5][0-9](?:\\.[0-9]{1,3})?").matcher(shot.caption).find());
            assertTrue(shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000); assertEquals(0, shot.targetDurationMs % 1000);
        }
    }
    private static final class Api {
        final Method load = method("nativeLoad", String.class), generate = method("nativeGenerate", long.class, byte[].class, byte[].class, int.class);
        final Method cancel = method("nativeCancel", long.class), free = method("nativeFree", long.class);
        final Method grammar = method("grammarForRoles", String[].class, String[][].class, boolean.class);
        final Method prompt = method("buildPrompt", String.class, String.class, String[].class, String[][].class, boolean.class, boolean.class, boolean.class);
        final Method parse = method("parse", String.class, String[].class, String[][].class, boolean.class);
        Api() throws Exception { }
    }
    private static Method method(String name, Class<?>... types) throws Exception { Method method = LocalPlanner.class.getDeclaredMethod(name, types); method.setAccessible(true); return method; }
    private static Object value(String name) throws Exception { Field field = LocalPlanner.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static Object invoke(Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause(); if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause; throw new IllegalStateException(cause);
        }
    }
    private static final class Fixture {
        final String id, style, rolesField, actionsField, brief;
        Fixture(String id, String style, String rolesField, String actionsField, String brief) {
            this.id = id; this.style = style; this.rolesField = rolesField; this.actionsField = actionsField; this.brief = brief;
        }
    }
    private static String shotText(Shot shot) { return shot.instruction + " " + shot.caption; }
    private static String allText(List<Shot> shots) { StringBuilder text = new StringBuilder(); for (Shot shot : shots) text.append(shotText(shot)).append('\n'); return text.toString(); }
    private static boolean has(String text, String words) { return Pattern.compile("\\b(?:" + words + ")\\b", Pattern.CASE_INSENSITIVE).matcher(text).find(); }
    private static String describe(Throwable failure) { return failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()); }
    private void assertNoInternetPermission() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions) assertNotEquals("android.permission.INTERNET", permission);
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) { byte[] bytes = new byte[1024 * 1024]; for (int n; (n = input.read(bytes)) != -1;) digest.update(bytes, 0, n); }
        StringBuilder text = new StringBuilder(); for (byte value : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", value & 255)); return text.toString();
    }
}
