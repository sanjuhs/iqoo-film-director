package dev.minifilm.director;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Prospective three-brief evaluation, not a creator-benefit or general accuracy estimate.
 * Only these explicitly synthetic briefs and their outputs may enter the evidence file/log.
 * All three attempts are retained before the final assertion; there is no retry or fallback.
 */
@RunWith(AndroidJUnit4.class)
public final class FreshFashionPlannerTest {
    private static final String TAG = "MiniFilmFreshPlanner";
    private static final String MODEL_SHA = "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final long MODEL_BYTES = 563036064L;
    private static final String[] ROLES = {"Hero pose", "Movement", "Detail", "Side pose", "Closing"};
    private static final String[] VERBS = {"Stand|Pose|Face|Look|Smile", "Take|Turn|Walk|Move",
            "Show|Hold|Point|Bring", "Turn|Stand|Pose|Face", "Look|Smile|Pose|Stand|Wave"};
    private static final String REVIEWED_DETAIL = "Show the moon patch on my pullover.";
    private static final Fixture[] FIXTURES = {
        new Fixture("minimal-jumpsuit",
                "Make a five-shot daily solo fashion reel while I wear my jumpsuit. "
                        + "Its color and material are unspecified. Ask me to choose a visible garment detail. "
                        + "Small body turns or one or two steps are allowed. The phone is already mounted.",
                "Retain jumpsuit; worn solo performance; unspecified Detail is a disclosed creator choice. "
                        + "Manually inspect all other facts, parts, props, movement and captions."),
        new Fixture("named-moon-patch",
                "Make a five-shot daily solo fashion reel while I wear my navy pullover with a small moon patch. "
                        + "No logos or stripes are supplied. Small body turns are allowed; the phone is mounted. "
                        + "Creator-reviewed reference moments: 0:01 Detail: " + REVIEWED_DETAIL,
                "Retain navy/pullover; exact Detail is creator-authored retention, not native grounding. "
                        + "Inspect other four directions and all native captions for unsupplied facts."),
        new Fixture("stationary-talking-fashion",
                "Make a stationary talking-fashion reel while I wear my burgundy waistcoat. "
                        + "I want to describe my own layering choice. Stay in one marked spot: no walking or steps. "
                        + "Small body turns are fine. No new props. The phone is already mounted.",
                "Retain waistcoat/burgundy; practical speech direction about my own layering choice. "
                        + "Stay planted without walking/steps; inspect new props and invented reasons/benefits.")
    };
    // Independent, deliberately bounded English checks; these do not prove arbitrary actions
    // or facts valid. Full output remains available for a separate direct semantic review.
    private static final Pattern EQUIPMENT_OPERATION = Pattern.compile(
            "\\b(?:hold(?:ing)?|held|take|taking|move|moving|adjust|adjusting|refocus|reposition|"
                    + "carry|carrying|lift|lifting|bring|bringing|aim|aiming|tilt|tilting|point|pointing|"
                    + "focus|focusing|pan|rotate|raise|lower|turn)\\s+"
                    + "(?:(?:a|an|the|your|my|our|this|that|filming|recording)\\s+){0,3}"
                    + "(?:camera|phone|tripod|gimbal|drone)\\b|"
                    + "\\bcamera\\s+(?:held|handheld|in\\s+(?:(?:your|my|the|one|both)\\s+){0,2}hands?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCOMOTION = Pattern.compile(
            "\\b(?:walk(?:s|ing|ed)?|steps?|stepping|stepped|stroll(?:ing)?|run(?:ning)?)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test(timeout = 540_000) public void threeProspectiveFashionBriefsRecordActualDefaultCpuDraftsAndTargetedFindings() throws Exception {
        Context context = context();
        assertNoCaptureOrInternet();
        assertFalse("Run sequentially after other local-model jobs have released", LocalModelLease.isHeld());
        File model = new File(context.getFilesDir(), "director-model.gguf");
        assertTrue("Existing default model must already be installed", model.isFile());
        assertEquals(MODEL_BYTES, model.length());
        assertEquals(MODEL_SHA, sha256(model));
        long originalModified = model.lastModified();
        Map<String, ?> originalPreferences = new HashMap<>(context.getSharedPreferences("shoot", 0).getAll());
        File evidenceDir = new File(context.getFilesDir(), "test-evidence");
        assertTrue(evidenceDir.isDirectory() || evidenceDir.mkdirs());
        File evidenceFile = File.createTempFile("fresh-fashion-planner-", ".json", evidenceDir);
        JSONArray records = new JSONArray();
        JSONObject evidence = new JSONObject().put("schema", "minifilm.prospective-fashion-evaluation.v1")
                .put("syntheticInputsOnly", true).put("nativeBackend", "CPU")
                .put("defaultModelSha256", MODEL_SHA).put("defaultModelBytes", MODEL_BYTES)
                .put("retries", 0).put("fallback", false).put("generalAccuracyClaim", false)
                .put("userBenefitClaim", false).put("manualSemanticReviewPending", true).put("cases", records);
        List<String> findings = new ArrayList<>();
        try {
            for (Fixture fixture : FIXTURES) {
                JSONObject record = new JSONObject().put("id", fixture.id).put("brief", fixture.brief)
                        .put("style", "Fashion").put("prospectiveCriteria", fixture.criteria)
                        .put("manualSemanticReviewPending", true).put("status", "pending")
                        .put("detailInstructionProvenance", fixture.id.equals("named-moon-patch")
                                ? "deterministic creator-authored override after native generation; not native understanding"
                                : "native closed creator-choice constraint; not learned feature selection")
                        .put("otherFourInstructionsProvenance", "actual native model draft")
                        .put("allCaptionsProvenance", "actual native model draft");
                records.put(record); writeEvidence(evidenceFile, evidence);
                Log.i(TAG, "synthetic_input=true prospective_case=" + record);
                evaluateOne(fixture, record, evidenceFile, evidence, findings);
            }
        } finally {
            // Fixed messages avoid printing any creator preference values on a failed check.
            boolean preferencesPreserved = originalPreferences.equals(context.getSharedPreferences("shoot", 0).getAll());
            boolean modelMetadataPreserved = model.isFile() && model.length() == MODEL_BYTES && model.lastModified() == originalModified;
            boolean modelHashPreserved = modelMetadataPreserved && MODEL_SHA.equals(sha256(model));
            evidence.put("preferencesPreserved", preferencesPreserved).put("modelMetadataPreserved", modelMetadataPreserved)
                    .put("modelHashPreserved", modelHashPreserved).put("modelLeaseHeldAtEnd", LocalModelLease.isHeld());
            if (!preferencesPreserved) findings.add("environment: creator preferences changed");
            if (!modelHashPreserved) findings.add("environment: default model changed");
            if (LocalModelLease.isHeld()) findings.add("environment: local model lease still held");
            evidence.put("findings", new JSONArray(findings)); writeEvidence(evidenceFile, evidence);
            assertNoCaptureOrInternet();
        }
        Log.i(TAG, "synthetic_input=true prospective_evaluation_complete cases=3 retries=0 findings="
                + new JSONArray(findings) + " manual_semantic_review_pending=true evidence_file=" + evidenceFile.getName());
        assertTrue("Prospective targeted findings (all attempts retained): " + findings, findings.isEmpty());
    }

    private void evaluateOne(Fixture fixture, JSONObject record, File evidenceFile, JSONObject evidence,
            List<String> allFindings) throws Exception {
        // Pure-model evaluator: preserve the original native-topic failure criteria.
        LocalPlanner planner = new LocalPlanner(context(), false);
        List<String> checks = new ArrayList<>();
        AtomicReference<List<Shot>> returned = new AtomicReference<>();
        AtomicReference<String> label = new AtomicReference<>(), error = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicBoolean callbackOnMain = new AtomicBoolean(true);
        AtomicLong nativeElapsed = new AtomicLong(-1), loadedHandleAtCallback = new AtomicLong();
        CountDownLatch done = new CountDownLatch(1);
        long began = SystemClock.elapsedRealtime();
        try {
            if (!planner.isModelAvailable()) {
                checks.add("existing default model/runtime unavailable");
                record.put("status", "preflight_failed");
            } else {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> planner.generate(fixture.brief, "Fashion", new LocalPlanner.Listener() {
                    public void onPlan(List<Shot> shots, long elapsedMs, String modelLabel) {
                        callbacks.incrementAndGet();
                        callbackOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                        returned.set(shots); label.set(modelLabel); nativeElapsed.set(elapsedMs);
                        loadedHandleAtCallback.set(handle(planner)); done.countDown();
                    }
                    public void onError(String message) {
                        callbacks.incrementAndGet(); callbackOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                        error.set(message); done.countDown();
                    }
                }));
                boolean completed = done.await(150, TimeUnit.SECONDS);
                record.put("wallElapsedMs", SystemClock.elapsedRealtime() - began)
                        .put("nativeElapsedMs", nativeElapsed.get()).put("callbackOnMain", callbackOnMain.get())
                        .put("callbackCount", callbacks.get()).put("modelLabel", label.get() == null ? JSONObject.NULL : label.get())
                        .put("error", error.get() == null ? JSONObject.NULL : error.get())
                        .put("loadedNativeHandleAtCallback", loadedHandleAtCallback.get() != 0)
                        .put("shots", encodeShots(returned.get()))
                        .put("preMergeNativeDraftAvailable", false)
                        .put("status", !completed ? "callback_timeout" : error.get() != null ? "production_error" : "draft_returned");
                // Preserve complete synthetic drafts BEFORE any targeted semantic checks.
                writeEvidence(evidenceFile, evidence);
                logRecord(record);
                check(completed, checks, "actual production callback timed out");
                check(callbacks.get() == 1, checks, "expected exactly one production callback");
                check(callbackOnMain.get(), checks, "production callback must run on main");
                check(error.get() == null, checks, "production generation failed: " + error.get());
                if (returned.get() == null) checks.add("no actual five-shot draft returned");
                else {
                    check(loadedHandleAtCallback.get() != 0, checks, "actual native handle was not resident at callback");
                    check(label.get() != null && label.get().startsWith(LocalPlanner.MODEL_LABEL)
                            && label.get().contains("local CPU"), checks, "default actual CPU model label required");
                    inspectDraft(fixture, returned.get(), label.get(), checks);
                }
            }
        } finally {
            planner.close();
            ExecutorService worker = (ExecutorService) field(planner, "worker");
            boolean closed = worker.awaitTermination(15, TimeUnit.SECONDS);
            record.put("workerTerminatedAfterClose", closed).put("nativeHandleZeroAfterClose", handle(planner) == 0)
                    .put("leaseReleasedAfterClose", !LocalModelLease.isHeld());
            check(closed && handle(planner) == 0 && !LocalModelLease.isHeld(), checks, "closed planner must release handle/worker/model lease");
            for (String finding : checks) allFindings.add(fixture.id + ": " + finding);
            record.put("targetedFindings", new JSONArray(checks)).put("targetedChecksPassed", checks.isEmpty())
                    .put("status", checks.isEmpty() ? "targeted_checks_passed_manual_review_pending" : "targeted_findings");
            writeEvidence(evidenceFile, evidence); logRecord(record);
        }
    }

    private static void inspectDraft(Fixture fixture, List<Shot> shots, String modelLabel, List<String> findings) {
        check(shots.size() == 5, findings, "exact five Fashion roles required");
        if (shots.size() != 5) return;
        StringBuilder text = new StringBuilder();
        boolean practicalLayeringSpeech = false;
        for (int i = 0; i < 5; i++) {
            Shot shot = shots.get(i);
            if (shot == null) { findings.add("null shot at role " + ROLES[i]); continue; }
            check(ROLES[i].equals(shot.title), findings, "role order mismatch at " + ROLES[i]);
            check(shot.id != null && shot.id.startsWith("ai-shot-"), findings, "actual model shot source required at " + ROLES[i]);
            check(shot.instruction != null && !shot.instruction.trim().isEmpty() && shot.instruction.length() <= 90,
                    findings, "bounded nonempty direction required at " + ROLES[i]);
            // Talking Closing may begin with Tell after the explicit-intent repair. The
            // recorded pre-repair failure is retained separately; speech/topic and stationary
            // criteria below stay unchanged. This accepts the requested action, not a silent pose.
            String verbs = fixture.id.equals("stationary-talking-fashion") && i == 4
                    ? VERBS[i] + "|Tell" : VERBS[i];
            if (!(fixture.id.equals("named-moon-patch") && i == 2))
                check(shot.instruction != null && shot.instruction.matches("(?:" + verbs + ") \\S.*"), findings, "performer opening verb at " + ROLES[i]);
            check(shot.caption != null && !shot.caption.trim().isEmpty() && shot.caption.length() <= 30,
                    findings, "bounded nonempty caption required at " + ROLES[i]);
            check(shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000 && shot.targetDurationMs % 1000 == 0,
                    findings, "bounded whole-second duration at " + ROLES[i]);
            check(FramingTarget.SCENE_DEFAULT.equals(shot.framingTarget), findings, "model must not invent a creator-selected framing target");
            String instruction = shot.instruction == null ? "" : shot.instruction;
            String caption = shot.caption == null ? "" : shot.caption;
            check(!EQUIPMENT_OPERATION.matcher(instruction).find(), findings, "bounded equipment-operation phrase at " + ROLES[i]);
            check(!hasWord(caption, "camera|phone|screen|tripod|gimbal|drone"), findings, "equipment caption at " + ROLES[i]);
            check(!Pattern.compile("[0-9]{1,3}:[0-5][0-9]").matcher(caption).find(), findings, "source clock copied into caption");
            check(!caption.trim().toLowerCase(Locale.ROOT).matches("(?s).*\\bis\\.?$"), findings, "unfinished caption fragment at " + ROLES[i]);
            text.append(instruction).append(' ').append(caption).append('\n');
            if (hasWord(instruction, "talk|talking|speak|speaking|describe|describing|explain|explaining|tell|say|share")
                    && hasWord(instruction, "layer|layers|layering|layered")) practicalLayeringSpeech = true;
        }
        String all = text.toString();
        check(!hasWord(all, "jacket|raincoat|kurta|mug|pencil|eraser|Rae|Mina"), findings, "unrelated previous-example noun mentioned; inspect full draft");
        check(!all.toLowerCase(Locale.ROOT).contains("tabletop") && !all.toLowerCase(Locale.ROOT).contains("flat surface"),
                findings, "worn performance became tabletop preparation");
        if (fixture.id.equals("named-moon-patch")) {
            check(hasWord(all, "pullover") && hasWord(all, "navy"), findings, "supplied pullover and navy not both retained");
            check(REVIEWED_DETAIL.equals(shots.get(2).instruction), findings, "exact creator-authored Detail not retained");
            check(modelLabel != null && modelLabel.contains("creator-authored reviewed cues retained")
                    && !modelLabel.contains("creator-choice detail constraint"), findings, "explicit Detail provenance labels required");
            check(!hasWord(all, "logo|logos|stripe|stripes|striped"), findings, "unsupplied logo/stripe wording requires review");
        } else {
            check(modelLabel != null && modelLabel.contains("creator-choice detail constraint")
                    && !modelLabel.contains("creator-authored reviewed cues retained"), findings, "ordinary Detail choice must be disclosed");
            String detail = shots.get(2) == null ? "" : String.valueOf(shots.get(2).instruction);
            check(hasWord(detail, "garment") && hasWord(detail, "visible") && hasWord(detail, "choose"), findings, "disclosed Detail must ask creator to choose");
            if (fixture.id.equals("minimal-jumpsuit")) check(hasWord(all, "jumpsuit"), findings, "supplied worn jumpsuit lost");
            else {
                check(hasWord(all, "waistcoat") && hasWord(all, "burgundy"), findings, "supplied waistcoat and burgundy not both retained");
                check(practicalLayeringSpeech, findings, "no actual speaking direction about creator's layering choice");
                // Remove only explicit English negations before detecting locomotion wording.
                // This is a prospective lexical signal for direct manual review, not a semantic proof.
                String movementText = all.replaceAll("(?i)\\b(?:no|without)\\s+(?:walking|steps)(?:\\s+(?:or|and)\\s+(?:walking|steps))?\\b", "")
                        .replaceAll("(?i)\\b(?:never|not|do not|don't|without)\\s+(?:walk|walking|step|steps|take\\s+(?:any\\s+)?steps)\\b", "");
                check(!LOCOMOTION.matcher(movementText).find(), findings, "locomotion wording despite stationary brief; inspect full draft");
                check(!hasWord(all, "chair|stool|table|bag|hat|scarf|mirror|cup"), findings, "new prop wording despite no-new-props brief");
            }
        }
    }

    private static JSONArray encodeShots(List<Shot> shots) throws Exception {
        JSONArray array = new JSONArray();
        if (shots != null) for (Shot shot : shots) array.put(shot == null ? JSONObject.NULL : new JSONObject()
                .put("id", shot.id).put("title", shot.title).put("instruction", shot.instruction)
                .put("caption", shot.caption).put("durationMs", shot.targetDurationMs).put("framingTarget", shot.framingTarget));
        return array;
    }
    private static void logRecord(JSONObject record) throws Exception {
        // One shot per log entry avoids logcat truncating a full record. Every string is synthetic.
        Log.i(TAG, "synthetic_input=true case=" + record.getString("id") + " status=" + record.optString("status")
                + " native_ms=" + record.optLong("nativeElapsedMs", -1) + " wall_ms=" + record.optLong("wallElapsedMs", -1)
                + " model=" + record.optString("modelLabel") + " error=" + record.optString("error")
                + " findings=" + record.optJSONArray("targetedFindings"));
        JSONArray shots = record.optJSONArray("shots");
        if (shots != null) for (int i = 0; i < shots.length(); i++) Log.i(TAG,
                "synthetic_input=true case=" + record.getString("id") + " shot=" + (i + 1) + " actual=" + shots.get(i));
    }
    private static void writeEvidence(File file, JSONObject evidence) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(evidence.toString(2).getBytes(StandardCharsets.UTF_8)); stream.flush();
        }
    }
    private static void check(boolean condition, List<String> findings, String message) { if (!condition) findings.add(message); }
    private static boolean hasWord(String text, String alternatives) {
        return Pattern.compile("\\b(?:" + alternatives + ")\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (Exception failure) { throw new AssertionError("Could not inspect local-model ownership state", failure); }
    }
    private static long handle(LocalPlanner planner) { return (Long) field(planner, "handle"); }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] bytes = new byte[65536]; int count; while ((count = stream.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        StringBuilder out = new StringBuilder(); for (byte value : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
    private static void assertNoCaptureOrInternet() throws Exception {
        Context context = context();
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions)
            assertNotEquals("Target must not request Internet permission", Manifest.permission.INTERNET, permission);
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static final class Fixture {
        final String id, brief, criteria;
        Fixture(String id, String brief, String criteria) { this.id = id; this.brief = brief; this.criteria = criteria; }
    }
}
