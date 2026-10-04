package dev.minifilm.director;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
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
import static org.junit.Assert.*;

/** Exact synthetic jacket diagnostics: raw native results, never retained-cue merged plans. */
@RunWith(AndroidJUnit4.class)
public final class RawReviewedBoardDiagnosticTest {
    private static final String TAG = "MiniFilmRawBoardDiagnostic";
    private static final String SHA = "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final long BYTES = 563036064L;
    private static final String PIN = "ggml-org/Qwen3.5-0.8B-GGUF@8fea620810c4afa23dd6443f999a48574c1611a3";
    private static final String BRIEF = "A solo fashion reel wearing my jacket.\nCreator-reviewed reference moments: "
            + "0:01 Hero: face right; 0:04.500 Detail: show the jacket left pocket; 0:07.500 Closing: face forward";

    private static final String PLAIN_BRIEF = "Help me perform a five-shot reel while wearing my jacket. I have not supplied its color, "
            + "material, pattern, or fasteners. Give direct performer instructions without adding those details.";

    @Test(timeout = 180_000) public void exactExplicitDetailFixtureLogsRawNativeBeforeProductionParser() throws Exception {
        diagnose("explicit-reviewed-detail", BRIEF, true, false);
    }

    @Test(timeout = 180_000) public void exactPlainUnknownJacketFixtureLogsRawNativeBeforeProductionParser() throws Exception {
        diagnose("plainjacket", PLAIN_BRIEF, false, true);
    }

    private void diagnose(String fixtureId, String brief, boolean reviewedMoments, boolean constrainDetail) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions) assertNotEquals("android.permission.INTERNET", permission);
        File model = new File(context.getFilesDir(), "director-model.gguf"); assertTrue(model.isFile());
        assertEquals(BYTES, model.length()); assertEquals("Baseline must match its publisher pin before load", SHA, sha(model));
        String[] roles = (String[]) constant("FASHION_ROLES"); String[][] actions = (String[][]) constant("FASHION_ACTIONS");
        Method load = method("nativeLoad", String.class), free = method("nativeFree", long.class);
        Method generate = method("nativeGenerate", long.class, byte[].class, byte[].class, int.class);
        Method promptBuilder = method("buildPrompt", String.class, String.class, String[].class, String[][].class, boolean.class, boolean.class, boolean.class);
        Method grammarBuilder = method("grammarForRoles", String[].class, String[][].class, boolean.class);
        Method parser = method("parse", String.class, String[].class, String[][].class, boolean.class);
        String prompt = (String) call(promptBuilder, brief, "Fashion", roles, actions, reviewedMoments, constrainDetail, true);
        String grammar = (String) call(grammarBuilder, roles, actions, constrainDetail);
        LocalModelLease.Token lease = LocalModelLease.acquire("raw-reviewed-board-diagnostic", () -> Thread.currentThread().isInterrupted(), 5000);
        long handle = 0; boolean nativeFreed = false; long began = SystemClock.elapsedRealtime();
        try {
            handle = ((Number) call(load, model.getAbsolutePath())).longValue(); assertNotEquals("Native load must own a real handle", 0L, handle);
            byte[] output = (byte[]) call(generate, handle, prompt.getBytes(StandardCharsets.UTF_8), grammar.getBytes(StandardCharsets.UTF_8), 580);
            assertNotNull("Native failure must not become an empty fallback", output);
            String raw = new String(output, StandardCharsets.UTF_8);
            // Log the complete raw synthetic output before any parse or creator-instruction merge.
            logComplete(fixtureId, new JSONObject().put("event", "RAW_REVIEWED_BOARD_DIAGNOSTIC").put("fixtureId", fixtureId).put("syntheticInputsOnly", true)
                    .put("model", "Qwen3.5 0.8B Q4_0").put("pin", PIN).put("sha", SHA).put("bytes", BYTES)
                    .put("backend", "localCPU").put("threads", 4).put("context", 1536).put("maxOutputTokens", 580)
                    .put("promptMode", "full_production_style").put("whitespaceBound", 8).put("emptyThinkAdapter", true)
                    .put("fixedFixture", brief).put("reviewedMoments", reviewedMoments).put("constrainDetail", constrainDetail).put("elapsedMs", SystemClock.elapsedRealtime() - began)
                    .put("noManualMerge", true).put("rawJSON", raw).toString());
            try {
                @SuppressWarnings("unchecked") List<Shot> shots = (List<Shot>) call(parser, raw, roles, actions, constrainDetail);
                assertEquals(5, shots.size());
                Log.i(TAG, new JSONObject().put("event", "RAW_BOARD_PARSE_RESULT").put("fixtureId", fixtureId).put("syntheticInputsOnly", true)
                        .put("nativeShapePass", true).put("creatorCueFaithfulnessOrBenefitClaim", false).toString());
            } catch (Exception | Error failure) {
                Log.e(TAG, new JSONObject().put("event", "RAW_BOARD_PARSE_RESULT").put("fixtureId", fixtureId).put("syntheticInputsOnly", true)
                        .put("nativeShapePass", false).put("actualFailureClass", failure.getClass().getName())
                        .put("actualFailureMessage", String.valueOf(failure.getMessage())).toString());
                throw failure; // This diagnostic must actually fail on an invalid raw model result.
            }
        } catch (Exception | Error failure) {
            Log.e(TAG, new JSONObject().put("event", "RAW_BOARD_DIAGNOSTIC_FAILED").put("fixtureId", fixtureId).put("syntheticInputsOnly", true)
                    .put("actualFailureClass", failure.getClass().getName()).put("actualFailureMessage", String.valueOf(failure.getMessage()))
                    .put("elapsedMs", SystemClock.elapsedRealtime() - began).toString());
            throw failure;
        } finally {
            // JNI calls are synchronous. Free happens only after load/generation/parsing has returned.
            // If free itself fails, intentionally retain the lease rather than lend out a possibly live core.
            if (handle != 0) { call(free, handle); nativeFreed = true; }
            else nativeFreed = true; // A failed nativeLoad never returned an owned handle.
            if (nativeFreed) lease.close();
            Log.i(TAG, "RAW_BOARD_NATIVE_CLEANUP fixtureId=" + fixtureId + " syntheticInputsOnly=true nativeFreeReturned=" + nativeFreed + " leaseReleased=" + !LocalModelLease.isHeld());
            assertEquals("Diagnostic must preserve the original model", BYTES, model.length()); assertEquals(SHA, sha(model));
        }
    }

    private static void logComplete(String fixtureId, String json) {
        // Logcat has a per-entry limit. Numbered chunks preserve even a verbose failed response in full.
        List<String> chunks = new ArrayList<>();
        for (int offset = 0; offset < json.length();) {
            int end = Math.min(json.length(), offset + 768); // Bound UTF-8 bytes as well as Java characters.
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;
            chunks.add(json.substring(offset, end)); offset = end;
        }
        for (int i = 0; i < chunks.size(); i++) Log.i(TAG, "RAW_BOARD_PAYLOAD fixtureId=" + fixtureId + " part=" + (i + 1) + "/" + chunks.size() + " " + chunks.get(i));
    }
    private static Object constant(String name) throws Exception { Field field = LocalPlanner.class.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static Method method(String name, Class<?>... types) throws Exception { Method method = LocalPlanner.class.getDeclaredMethod(name, types); method.setAccessible(true); return method; }
    private static Object call(Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause(); if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof Exception) throw (Exception) cause; throw new IllegalStateException(cause);
        }
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) { byte[] bytes = new byte[1024 * 1024]; for (int n; (n = input.read(bytes)) != -1;) digest.update(bytes, 0, n); }
        StringBuilder text = new StringBuilder(); for (byte value : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", value & 255)); return text.toString();
    }
}
