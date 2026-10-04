package dev.minifilm.director;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Three requested moments, not full-video semantics; public/synthetic sources, no activity or playback. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceBoardTest {
    private static final String SOURCE_SHA = "eede24ef7294525a6779a5873ccc011fe976a44d3a665c8f22cd484a5841168f";
    private static final Uri TEXT_ONLY_SOURCE = Uri.parse("file:///synthetic/reference-metadata-only.mp4");
    private static final String FIXTURE_LABEL = "Synthetic metadata fixture; local CPU + pose landmark heuristic";
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test(timeout = 720_000) public void threeActualMomentsStayTimedAndCancelledBatchCannotPublishIntoReplacement() throws Exception {
        assertNoInternetPermission(); File source = fixture(); assertEquals(SOURCE_SHA, sha(source));
        assertSourceMetadata(source); ReferenceBoardInspection inspection = new ReferenceBoardInspection(context);
        AtomicReference<ReferenceBoard> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        AtomicReference<Long> inspectionElapsedMs = new AtomicReference<>();
        AtomicInteger cancelledCallbacks = new AtomicInteger(), boards = new AtomicInteger(); AtomicBoolean callbacksOnMain = new AtomicBoolean(true);
        List<Integer> progress = new ArrayList<>(); List<Long> completionElapsedMs = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1); long began = SystemClock.elapsedRealtime();
        long[] requested = {1000, 4500, 7500};
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                inspection.inspect(Uri.fromFile(source), new long[] {1000, 4500, 7500}, countingListener(cancelledCallbacks));
                inspection.cancel();
                inspection.inspect(Uri.fromFile(source), requested, new ReferenceBoardInspection.Listener() {
                    @Override public void onProgress(int completed, int total) {
                        if (Looper.myLooper() != Looper.getMainLooper() || total != 3) callbacksOnMain.set(false);
                        progress.add(completed); completionElapsedMs.add(SystemClock.elapsedRealtime() - began);
                    }
                    @Override public void onBoard(ReferenceBoard board, long elapsedMs) {
                        if (Looper.myLooper() != Looper.getMainLooper()) callbacksOnMain.set(false);
                        inspectionElapsedMs.set(elapsedMs);
                        if (boards.incrementAndGet() == 1) result.set(board); else board.close();
                        done.countDown();
                    }
                    @Override public void onError(String message) { error.set(message); done.countDown(); }
                });
                requested[0] = 2000; // The public API must already own a snapshot of caller times.
            });
            assertTrue("Three local moments must complete within per-frame bounds", done.await(660, TimeUnit.SECONDS));
            assertNull(error.get()); assertEquals(1, boards.get()); assertEquals(0, cancelledCallbacks.get());
            assertTrue(callbacksOnMain.get()); assertEquals(Arrays.asList(1, 2, 3), progress);
            assertTrue(completionElapsedMs.get(0) > 0); assertTrue(completionElapsedMs.get(1) > completionElapsedMs.get(0));
            assertTrue(completionElapsedMs.get(2) > completionElapsedMs.get(1));
            ReferenceBoard board = result.get(); assertNotNull(board); assertEquals(Uri.fromFile(source), board.sourceUri);
            assertEquals(3, board.frames.size()); assertFalse("Native notes must still require creator review", board.reviewed);
            assertRejected(board::planningSummary); assertRejected(board::toJson);
            long[] expected = {1000, 4500, 7500}; int[] imageHashes = new int[3];
            for (int i = 0; i < 3; i++) {
                ReferenceBoard.FrameDraft frame = board.frames.get(i); assertEquals(expected[i], frame.requestedTimeMs);
                assertEquals(frame.originalNotes, frame.notes); assertTrue(frame.selected);
                assertTrue(frame.modelLabel.contains("local CPU")); assertTrue(frame.modelLabel.contains("pose landmark heuristic"));
                assertEquals(3, frame.notes.split("\n").length); assertTrue(frame.notes.length() <= 300);
                Bitmap image = frame.thumbnailCopy(); assertNotNull("A decoded moment needs a review thumbnail", image);
                try {
                    assertTrue(Math.max(image.getWidth(), image.getHeight()) <= 256);
                    int[] pixels = new int[image.getWidth() * image.getHeight()];
                    image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
                    imageHashes[i] = Arrays.hashCode(pixels);
                    if (i == 2) {
                        int center = image.getPixel(image.getWidth() / 2, image.getHeight() / 2);
                        assertTrue("Last requested moment must decode actual black pixels",
                                Color.red(center) < 16 && Color.green(center) < 16 && Color.blue(center) < 16);
                    }
                } finally { image.recycle(); }
            }
            assertNotEquals(imageHashes[0], imageHashes[1]); assertNotEquals(imageHashes[1], imageHashes[2]);
            String full = board.frames.get(0).notes, crop = board.frames.get(1).notes, black = board.frames.get(2).notes;
            assertPerson(full); assertEquals("full-body", field(full, "Framing")); assertPerson(crop);
            String croppedFraming = field(crop, "Framing");
            assertTrue("Portrait must be proven or conservatively abstain", croppedFraming.equals("head-and-shoulders") || croppedFraming.equals("review needed"));
            assertNotEquals("full-body", croppedFraming); assertNotEquals("waist-up", croppedFraming);
            assertEquals("empty or unclear", field(black, "Framing"));
            boolean absent = field(black, "Subject").toLowerCase(Locale.ROOT).matches("(?s).*\\b(no|none|nothing|empty|blank|black|unidentifiable)\\b.*");
            boolean abstained = field(black, "Subject").equals("unknown") && field(black, "Uncertainty").equals("unknown");
            assertTrue("Black must show absence or exact joint abstention", absent || abstained);
            assertFalse(inspection.isRunning()); assertFalse(VisionReference.isRunning()); assertFalse(LocalModelLease.isHeld());
            assertEquals(SOURCE_SHA, sha(source));
            Log.i("MiniFilmReferenceBoardTest", "ACTUAL_REFERENCE_BOARD_OK moments=1000,4500,7500 count=3"
                    + " completionElapsedMs=" + completionElapsedMs + " inspectionElapsedMs=" + inspectionElapsedMs.get()
                    + " sourceHashUnchanged=true"
                    + " backend=localCPU subject_backend=localVLM framing_backend=pose_landmark_heuristic"
                    + " crop_policy=conservative_fallback crop_requires_review=" + croppedFraming.equals("review needed")
                    + " black_outcome=" + (absent ? "explicit_absence" : "uncertain_abstention")
                    + " manualReviewRequired=true cancelledBatchCallbacks=0 noInternetPermission=true sparseMomentsOnly=true");
        } finally {
            inspection.close(); if (result.get() != null) result.get().close();
            assertEquals("Reference original must remain unchanged", SOURCE_SHA, sha(source));
        }
    }

    @Test(timeout = 15_000) public void closeSuppressesQueuedFramesAndValidationErrorsWithoutOpeningModel() throws Exception {
        File source = fixture(); AtomicInteger callbacks = new AtomicInteger();
        ReferenceBoardInspection inspection = new ReferenceBoardInspection(context);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            inspection.inspect(Uri.fromFile(source), new long[] {1000, 4500, 7500}, countingListener(callbacks));
            inspection.close();
            inspection.inspect(Uri.fromFile(source), new long[] {1000}, countingListener(callbacks));
        });
        drainQueuedWork(); assertEquals("Closed batches must not deliver stale boards/progress/errors", 0, callbacks.get());
        assertFalse(inspection.isRunning()); assertFalse(VisionReference.isRunning()); assertEquals(SOURCE_SHA, sha(source));
        ReferenceBoardInspection invalid = new ReferenceBoardInspection(context);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            invalid.inspect(Uri.fromFile(source), new long[] {4500, 1000}, countingListener(callbacks));
            invalid.close();
        });
        drainQueuedWork(); assertEquals("Close must invalidate an already queued validation error", 0, callbacks.get());
    }

    @Test(timeout = 45_000) public void invalidOrderAndOutsideActualVideoAreRejectedBeforeVision() throws Exception {
        File source = fixture(); ReferenceBoardInspection inspection = new ReferenceBoardInspection(context);
        try {
            assertInspectionRejected(inspection, Uri.fromFile(source), new long[] {4500, 1000});
            assertInspectionRejected(inspection, Uri.fromFile(source), new long[] {9000});
            assertInspectionRejected(inspection, Uri.parse("https://example.invalid/reference.mp4"), new long[] {1000});
            assertFalse(inspection.isRunning()); assertFalse(VisionReference.isRunning()); assertEquals(SOURCE_SHA, sha(source));
        } finally { inspection.close(); }
    }

    @Test public void reviewSelectionRoundTripAndThumbnailOwnershipPreserveMomentAssociation() throws Exception {
        Bitmap input = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888); input.eraseColor(Color.GREEN);
        List<ReferenceBoard.FrameDraft> originals = drafts(input); ReferenceBoard board = null, reviewed = null, restored = null;
        Bitmap independent = null;
        try {
            board = new ReferenceBoard(TEXT_ONLY_SOURCE, originals); for (ReferenceBoard.FrameDraft draft : originals) draft.close();
            input.eraseColor(Color.RED); input.recycle();
            independent = board.frames.get(0).thumbnailCopy(); assertNotNull(independent); assertEquals(Color.GREEN, independent.getPixel(0, 0));
            List<String> corrections = new ArrayList<>(Arrays.asList("Hero:  face left", "Side pose: pause in profile", "Closing:\nface forward"));
            List<Boolean> selected = new ArrayList<>(Arrays.asList(true, false, true));
            reviewed = board.reviewedCopy(corrections, selected); corrections.set(0, "Changed after confirmation"); selected.set(0, false);
            assertTrue(reviewed.reviewed); assertFalse(board.reviewed); assertTrue(board.frames.get(1).selected);
            assertEquals("Fixture first", board.frames.get(0).notes); assertEquals("Hero:  face left", reviewed.frames.get(0).notes);
            String summary = reviewed.planningSummary(); assertTrue(summary.length() <= 210);
            assertTrue(summary.contains("0:01 Hero: face left")); assertTrue(summary.contains("0:07.500 Closing: face forward"));
            assertFalse(summary.contains("profile")); assertFalse(summary.contains("0:04.500"));
            String json = reviewed.toJson(); assertFalse(json.contains("thumbnail")); assertFalse(json.contains("originalNotes"));
            restored = ReferenceBoard.fromJson(json); assertTrue(restored.reviewed); assertEquals(summary, restored.planningSummary());
            assertEquals(TEXT_ONLY_SOURCE, restored.sourceUri); assertEquals(3, restored.frames.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(reviewed.frames.get(i).requestedTimeMs, restored.frames.get(i).requestedTimeMs);
                assertEquals(reviewed.frames.get(i).notes, restored.frames.get(i).notes);
                assertEquals(reviewed.frames.get(i).modelLabel, restored.frames.get(i).modelLabel);
                assertEquals(reviewed.frames.get(i).selected, restored.frames.get(i).selected);
                assertNull("Restored metadata must not invent or serialize pixel data", restored.frames.get(i).thumbnailCopy());
            }
            board.close(); assertNull(board.frames.get(0).thumbnailCopy());
            assertFalse("A caller-owned thumbnail must survive board disposal", independent.isRecycled());
            assertEquals(Color.GREEN, independent.getPixel(0, 0)); Bitmap reviewedImage = reviewed.frames.get(0).thumbnailCopy();
            assertNotNull("Reviewed copy must own its independent thumbnail", reviewedImage); reviewedImage.recycle();
        } finally {
            if (!input.isRecycled()) input.recycle(); for (ReferenceBoard.FrameDraft frame : originals) frame.close();
            if (independent != null) independent.recycle(); if (board != null) board.close(); if (reviewed != null) reviewed.close(); if (restored != null) restored.close();
        }
    }

    @Test public void invalidReviewReorderingAndCorruptPersistenceCannotBypassReviewOrBounds() throws Exception {
        List<ReferenceBoard.FrameDraft> frames = drafts(null);
        try (ReferenceBoard board = new ReferenceBoard(TEXT_ONLY_SOURCE, frames)) {
            assertRejected(board::planningSummary); assertRejected(board::toJson);
            assertRejected(() -> new ReferenceBoard(TEXT_ONLY_SOURCE, Arrays.asList(frames.get(1), frames.get(0), frames.get(2))));
            assertRejected(() -> new ReferenceBoard(TEXT_ONLY_SOURCE, Arrays.asList(frames.get(0), frames.get(0))));
            assertRejected(() -> new ReferenceBoard(Uri.parse("https://example.invalid/video"), frames));
            assertRejected(() -> board.reviewedCopy(Arrays.asList("First", "Second")));
            assertRejected(() -> board.reviewedCopy(Arrays.asList("First", "Second", "Third"), Arrays.asList(false, false, false)));
            String longNote = repeat('x', 200);
            assertRejected(() -> board.reviewedCopy(Arrays.asList(longNote, longNote, longNote)));
            assertEquals("Failed review must not change original notes", "Fixture first", board.frames.get(0).notes);
            try (ReferenceBoard confirmed = board.reviewedCopy(Arrays.asList("Hero", "Profile", "Forward"))) {
                String json = confirmed.toJson(); JSONObject valid = new JSONObject(json);
                List<String> invalid = new ArrayList<>();
                invalid.add(""); invalid.add("{}"); invalid.add("[]"); invalid.add(repeat('x', 8001));
                invalid.add(new JSONObject(json).put("version", 2).toString());
                invalid.add(new JSONObject(json).put("reviewed", false).toString());
                invalid.add(new JSONObject(json).put("sourceUri", "https://example.invalid/video").toString());
                invalid.add(new JSONObject(json).put("extra", "untrusted").toString());
                JSONObject fractional = new JSONObject(json); fractional.getJSONArray("frames").getJSONObject(0).put("requestedTimeMs", 1000.5); invalid.add(fractional.toString());
                JSONObject reorder = new JSONObject(json); JSONArray reordered = reorder.getJSONArray("frames");
                Object first = reordered.get(0); reordered.put(0, reordered.get(1)); reordered.put(1, first); invalid.add(reorder.toString());
                JSONObject bound = new JSONObject(json); bound.getJSONArray("frames").getJSONObject(2).put("requestedTimeMs", 180000); invalid.add(bound.toString());
                JSONObject typed = new JSONObject(json); typed.getJSONArray("frames").getJSONObject(0).put("selected", "true"); invalid.add(typed.toString());
                JSONObject corrupt = new JSONObject(json); corrupt.getJSONArray("frames").getJSONObject(0).put("notes", "bad\u0000control"); invalid.add(corrupt.toString());
                for (String bad : invalid) assertRejected(() -> ReferenceBoard.fromJson(bad));
                assertEquals(3, valid.getJSONArray("frames").length()); assertTrue(confirmed.planningSummary().length() <= 210);
            }
        } finally { for (ReferenceBoard.FrameDraft frame : frames) frame.close(); }
    }

    @Test(timeout = 190_000) public void nativeDraftRetainsExactCreatorCuesAndDisclosesUnknownDetailConstraint() throws Exception {
        assertNoInternetPermission(); List<ReferenceBoard.FrameDraft> originals = drafts(null);
        LocalPlanner planner = new LocalPlanner(context);
        try (ReferenceBoard draft = new ReferenceBoard(TEXT_ONLY_SOURCE, originals);
             ReferenceBoard reviewed = draft.reviewedCopy(Arrays.asList("Hero: face left", "Side pose: pause in profile", "Closing: face forward"))) {
            assertTrue(planner.isModelAvailable()); String summary = reviewed.planningSummary(); assertTrue(summary.length() <= 210);
            PlanResult generated = generateActualPlan(planner, summary);
            // These named instructions are retained creator edits after native draft generation. This does
            // not test learned interpretation of the reviewed directions or public-frame grounding.
            logHybridPlan("UNSPECIFIED_DETAIL", generated);
            assertPlanShapeAndSuppliedFacts(generated.shots, false);
            assertTrue(generated.label.contains("local CPU"));
            assertTrue("Retained manual instructions must be disclosed", generated.label.contains("creator-authored reviewed cues retained"));
            assertTrue("Unknown-feature restriction must be disclosed", generated.label.contains("creator-choice detail constraint"));
            assertEquals("face left", generated.shots.get(0).instruction);
            assertEquals("pause in profile", generated.shots.get(3).instruction);
            assertEquals("face forward", generated.shots.get(4).instruction);
            String detail = generated.shots.get(2).instruction.toLowerCase(Locale.ROOT);
            assertTrue("Unspecified garment detail must let the creator choose a visible feature: " + detail,
                    detail.matches("(?s).*\\b(choose|chosen|choice|pick|select|selected)\\b.*"));
            Log.i("MiniFilmReferenceBoardTest", "HYBRID_REVIEWED_CUES_OK syntheticManualNotes=true roles=5"
                    + " exactCreatorInstructions=hero,side,closing generatedFields=otherInstructions,captions,durations"
                    + " unknownDetailConstraintDisclosed=true creatorRetentionDisclosed=true elapsedMs=" + generated.elapsedMs
                    + " summaryChars=" + summary.length() + " backend=localCPU learnedGroundingClaim=false");
        } finally {
            for (ReferenceBoard.FrameDraft frame : originals) frame.close(); closePlannerAndAwaitRelease(planner);
        }
    }

    @Test(timeout = 190_000) public void validExplicitDetailRetriesAfterPreflightFailureAndRetainsOnlyCreatorNamedInstructions() throws Exception {
        assertNoInternetPermission(); List<ReferenceBoard.FrameDraft> originals = drafts(null);
        LocalPlanner planner = new LocalPlanner(context);
        try (ReferenceBoard draft = new ReferenceBoard(TEXT_ONLY_SOURCE, originals);
             ReferenceBoard reviewed = draft.reviewedCopy(Arrays.asList("Hero: face right", "Detail: show the jacket left pocket", "Closing: face forward"))) {
            assertTrue(planner.isModelAvailable());
            // Retry on this same planner after a real asynchronous validation error. A malformed manual
            // direction must neither start native work nor leave busy set and block a later valid plan.
            assertPlannerPreflightRejected(planner, "0:01 Hero: spin right", "short action");
            PlanResult generated = generateActualPlan(planner, reviewed.planningSummary());
            logHybridPlan("EXPLICIT_CREATOR_DETAIL", generated);
            assertPlanShapeAndSuppliedFacts(generated.shots, true);
            assertTrue(generated.label.contains("local CPU"));
            assertTrue(generated.label.contains("creator-authored reviewed cues retained"));
            assertFalse("An explicitly supplied Detail must bypass the unknown-feature restriction",
                    generated.label.contains("creator-choice detail constraint"));
            assertEquals("face right", generated.shots.get(0).instruction);
            assertEquals("show the jacket left pocket", generated.shots.get(2).instruction);
            assertEquals("face forward", generated.shots.get(4).instruction);
            Log.i("MiniFilmReferenceBoardTest", "HYBRID_EXPLICIT_DETAIL_OK syntheticManualNotes=true roles=5"
                    + " exactCreatorInstructions=hero,detail,closing generatedFields=movement,side,captions,durations"
                    + " suppliedFeature=leftPocket unknownDetailConstraintDisclosed=false creatorRetentionDisclosed=true"
                    + " invalidPreflightThenValidNativeRetry=true elapsedMs=" + generated.elapsedMs
                    + " backend=localCPU learnedGroundingClaim=false");
        } finally {
            for (ReferenceBoard.FrameDraft frame : originals) frame.close(); closePlannerAndAwaitRelease(planner);
        }
    }

    @Test(timeout = 45_000) public void malformedReviewedCuesAndMomentOrderFailWithoutModelLoadOrFallback() throws Exception {
        LocalPlanner planner = new LocalPlanner(context);
        try {
            assertTrue("Validation must be exercised with the real model available", planner.isModelAvailable());
            String[][] invalid = {
                    {"0:01 Hero: spin left", "short action"},
                    {"0:01 Hero: hold camera", "filming device"},
                    {"0:01 Hero: face left; 0:04 Hero pose: face right", "one reviewed cue"},
                    {"0:04 Hero: face left; 0:01 Closing: face forward", "distinct times"},
                    {"0:01 Hero: face left; 0:01 Closing: face forward", "distinct times"},
                    {"Hero: face left", "reference times"},
                    {"0:01 Hero:", "short action"},
                    {"0:01 Hero: face <|left|>", "short action"},
                    {"0:01 Hero: face {left}", "short action"},
                    {"0:01 Hero: face " + repeat('x', 90), "short action"},
                    {"0:01 First; 0:02 Second; 0:03 Third; 0:04 Fourth", "one to three"},
                    {"3:00 Hero: face left", "reference times"},
                    {"0:01.5 Hero: face left", "reference times"},
                    {"0:01 Hero:  face left", "short notes"},
                    {"0:01 Hero: face left; 0:04 Closing: face forward Creator-reviewed reference moments: 0:07 Detail: show a feature", "one reviewed reference"}
            };
            for (String[] row : invalid) assertPlannerPreflightRejected(planner, row[0], row[1]);
            Log.i("MiniFilmReferenceBoardTest", "REVIEWED_CUE_PREFLIGHT_OK syntheticManualNotes=true invalidCases=" + invalid.length
                    + " modelHandle=0 modelLeaseHeld=false successCallbacks=0 fallbackCallbacks=0 callbacksOnMain=true");
        } finally { closePlannerAndAwaitRelease(planner); }
    }

    private static final class PlanResult {
        final List<Shot> shots; final String label; final long elapsedMs;
        PlanResult(List<Shot> shots, String label, long elapsedMs) { this.shots = shots; this.label = label; this.elapsedMs = elapsedMs; }
    }
    private PlanResult generateActualPlan(LocalPlanner planner, String summary) throws Exception {
        String brief = "A solo fashion reel wearing my jacket.\nCreator-reviewed reference moments: " + summary;
        assertTrue("Every reviewed direction must fit the native input without truncation", brief.length() <= 500);
        CountDownLatch done = new CountDownLatch(1); AtomicReference<PlanResult> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>(); AtomicInteger callbacks = new AtomicInteger();
        AtomicBoolean onMain = new AtomicBoolean(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> planner.generate(brief, "Fashion", new LocalPlanner.Listener() {
            @Override public void onPlan(List<Shot> shots, long elapsedMs, String label) {
                if (Looper.myLooper() != Looper.getMainLooper()) onMain.set(false);
                result.set(new PlanResult(shots, label, elapsedMs)); callbacks.incrementAndGet(); done.countDown();
            }
            @Override public void onError(String message) {
                if (Looper.myLooper() != Looper.getMainLooper()) onMain.set(false);
                error.set(message); callbacks.incrementAndGet(); done.countDown();
            }
        }));
        assertTrue("Actual native draft timed out", done.await(150, TimeUnit.SECONDS));
        assertTrue(onMain.get()); assertEquals(1, callbacks.get()); assertNull(error.get()); assertNotNull(result.get());
        assertNotNull(result.get().label); assertTrue(result.get().elapsedMs > 0);
        return result.get();
    }
    private static void logHybridPlan(String path, PlanResult result) throws Exception {
        JSONArray generated = new JSONArray();
        for (Shot shot : result.shots) generated.put(new JSONObject().put("id", shot.id).put("title", shot.title)
                .put("instruction", shot.instruction).put("caption", shot.caption).put("durationMs", shot.targetDurationMs));
        // Log before retention/factual assertions so failures retain the authoritative final output.
        Log.i("MiniFilmReferenceBoardTest", "GENERATED_HYBRID_REVIEWED_BOARD_PLAN syntheticManualNotes=true path=" + path
                + " modelLabel=" + result.label + " shots=" + generated + " learnedGroundingClaim=false");
    }
    private static void assertPlanShapeAndSuppliedFacts(List<Shot> shots, boolean leftPocketSupplied) throws Exception {
        String[] roles = {"Hero pose", "Movement", "Detail", "Side pose", "Closing"}; assertEquals(5, shots.size());
        for (int i = 0; i < shots.size(); i++) {
            Shot shot = shots.get(i); assertEquals(roles[i], shot.title); assertEquals("ai-shot-" + (i + 1), shot.id);
            assertTrue(shot.targetDurationMs >= 3000 && shot.targetDurationMs <= 8000); assertEquals(0, shot.targetDurationMs % 1000);
            assertFalse(shot.instruction.isEmpty()); assertTrue(shot.instruction.length() <= 90);
            assertFalse(shot.caption.isEmpty()); assertTrue(shot.caption.length() <= 30);
            assertTrue("Captions must be story words, not pure numeric labels: " + shot.caption, Character.isLetter(shot.caption.charAt(0)));
            assertFalse("Source timestamps must not become story captions: " + shot.caption,
                    shot.caption.matches("(?s).*\\d{1,3}:\\d{2}(?:[.,]\\d+)?\\b.*"));
            String wording = (shot.instruction + " " + shot.caption).toLowerCase(Locale.ROOT);
            assertFalse("Plan invented an unsupplied color/material: " + wording,
                    wording.matches("(?s).*\\b(red|blue|green|yellow|black|white|orange|purple|pink|brown|grey|gray|navy|beige|"
                            + "leather|cotton|denim|wool|velvet|silk|linen|polyester|fabric|metal|plastic|wood|glass)\\b.*"));
            assertTrue("Fashion directions must follow the bounded equipment-operation policy: " + shot.instruction,
                    followsFashionDevicePolicy(shot.instruction));
            assertFalse("Hardware words must not become story captions: " + shot.caption,
                    shot.caption.toLowerCase(Locale.ROOT).matches("(?s).*\\b(camera|phone|screen|tripod|gimbal|drone)s?\\b.*"));
            // Only the explicit fixture supplies a left pocket. Every other named part remains unconfirmed.
            String unknownParts = "lapels?|collars?|buttons?|zippers?|buckles?|hems?|sleeves?" + (leftPocketSupplied ? "|pockets" : "|pockets?");
            assertFalse("This brief does not establish that garment part: " + wording, wording.matches("(?s).*\\b(" + unknownParts + ")\\b.*"));
            if (leftPocketSupplied) assertFalse("Only a left pocket was supplied: " + wording, wording.matches("(?s).*\\bright\\s+pocket\\b.*"));
        }
    }
    // Policy alignment only, not independent semantic validation. Separate synthetic operator/body
    // fixtures exercise expected behavior; unknown facts and hardware captions remain independent checks.
    private static boolean followsFashionDevicePolicy(String instruction) throws Exception {
        java.lang.reflect.Method predicate = LocalPlanner.class.getDeclaredMethod("invalidFashionDeviceCue", String.class);
        predicate.setAccessible(true);
        return !((Boolean) predicate.invoke(null, instruction));
    }
    private void assertPlannerPreflightRejected(LocalPlanner planner, String summary, String expectedErrorPart) throws Exception {
        assertFalse("Invalid input must not acquire another model owner's lease", LocalModelLease.isHeld());
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> error = new AtomicReference<>();
        AtomicInteger plans = new AtomicInteger(), errors = new AtomicInteger(); AtomicBoolean onMain = new AtomicBoolean(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> planner.generate(
                "A solo fashion reel wearing my jacket.\nCreator-reviewed reference moments: " + summary, "Fashion", new LocalPlanner.Listener() {
                    @Override public void onPlan(List<Shot> shots, long elapsedMs, String label) { plans.incrementAndGet(); done.countDown(); }
                    @Override public void onError(String message) {
                        if (Looper.myLooper() != Looper.getMainLooper()) onMain.set(false);
                        error.set(message); errors.incrementAndGet(); done.countDown();
                    }
                }));
        assertTrue("Reviewed-cue preflight should not wait for native inference", done.await(3, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("Invalid cues must not publish native or fallback plans", 0, plans.get()); assertEquals(1, errors.get());
        assertTrue(onMain.get()); assertNotNull(error.get());
        assertTrue("Unexpected validation error: " + error.get(), error.get().toLowerCase(Locale.ROOT).contains(expectedErrorPart));
        java.lang.reflect.Field handle = LocalPlanner.class.getDeclaredField("handle"); handle.setAccessible(true);
        assertEquals("A rejected manual cue must not load a resident model", 0L, handle.getLong(planner));
        assertFalse("A rejected manual cue must not acquire the native model lease", LocalModelLease.isHeld());
    }
    private static void closePlannerAndAwaitRelease(LocalPlanner planner) {
        planner.close(); long deadline = SystemClock.elapsedRealtime() + 5000;
        while (LocalModelLease.isHeld() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20);
        assertFalse("Headless planner must release the resident core before the next case", LocalModelLease.isHeld());
    }

    private List<ReferenceBoard.FrameDraft> drafts(Bitmap image) {
        return Arrays.asList(new ReferenceBoard.FrameDraft(1000, "Fixture first", FIXTURE_LABEL, image),
                new ReferenceBoard.FrameDraft(4500, "Fixture middle", FIXTURE_LABEL, image),
                new ReferenceBoard.FrameDraft(7500, "Fixture last", FIXTURE_LABEL, image));
    }
    private void assertInspectionRejected(ReferenceBoardInspection inspection, Uri source, long[] times) throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> error = new AtomicReference<>(); AtomicInteger unexpected = new AtomicInteger();
        inspection.inspect(source, times, new ReferenceBoardInspection.Listener() {
            @Override public void onProgress(int completed, int total) { unexpected.incrementAndGet(); }
            @Override public void onBoard(ReferenceBoard board, long elapsedMs) { board.close(); unexpected.incrementAndGet(); done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS)); assertNotNull(error.get()); assertEquals(0, unexpected.get());
    }
    private ReferenceBoardInspection.Listener countingListener(AtomicInteger callbacks) {
        return new ReferenceBoardInspection.Listener() {
            @Override public void onProgress(int completed, int total) { callbacks.incrementAndGet(); }
            @Override public void onBoard(ReferenceBoard board, long elapsedMs) { callbacks.incrementAndGet(); board.close(); }
            @Override public void onError(String message) { callbacks.incrementAndGet(); }
        };
    }
    private void drainQueuedWork() throws Exception {
        CountDownLatch drained = new CountDownLatch(1); main.postDelayed(drained::countDown, 750);
        assertTrue(drained.await(3, TimeUnit.SECONDS)); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private File fixture() { File file = new File(context.getFilesDir(), "fixtures/reference-three-moments.mp4"); assertTrue(file.isFile()); assertEquals(49836, file.length()); return file; }
    private void assertSourceMetadata(File source) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(source.getAbsolutePath()); assertEquals("360", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("640", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            assertEquals("9000", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally { metadata.release(); }
    }
    private static String field(String notes, String key) {
        for (String line : notes.split("\n")) if (line.startsWith(key + ": ")) return line.substring(key.length() + 2);
        throw new AssertionError("Missing " + key + " in fixture observation");
    }
    private static void assertPerson(String notes) {
        String text = field(notes, "Subject").toLowerCase(Locale.ROOT);
        String people = "person|woman|man|human|individual|figure|girl|boy|child|adult|kid";
        assertTrue("Public source visibly depicts a person", text.matches("(?s).*\\b(" + people + ")\\b.*"));
        assertFalse("Absence must not be counted as person recognition", text.matches("(?s).*\\b(no|not|without)\\b(?:\\s+[a-z]+){0,3}\\s+\\b(" + people + ")\\b.*")
                || text.matches("(?s).*\\b(none|nothing|unidentifiable|unidentified)\\b.*") || text.contains("not visible") || text.contains("not identifiable"));
    }
    private void assertNoInternetPermission() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) for (String permission : info.requestedPermissions) assertNotEquals("No network inference or upload permission", "android.permission.INTERNET", permission);
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) { byte[] bytes = new byte[65536]; for (int n; (n = input.read(bytes)) != -1;) digest.update(bytes, 0, n); }
        StringBuilder text = new StringBuilder(); for (byte value : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", value & 255)); return text.toString();
    }
    private static String repeat(char value, int length) { char[] text = new char[length]; Arrays.fill(text, value); return new String(text); }
    private interface Checked { void run() throws Exception; }
    private static void assertRejected(Checked action) throws Exception {
        try { action.run(); fail("Invalid/unreviewed input must be rejected without silently changing text"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
}
