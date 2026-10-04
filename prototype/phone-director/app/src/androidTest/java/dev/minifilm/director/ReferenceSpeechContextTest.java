package dev.minifilm.director;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Headless data/parser boundaries only; no transcription, UI, media access or model loading. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceSpeechContextTest {
    private static final Uri SOURCE = Uri.parse("content://synthetic.reference/videos/one");
    private ReferenceSpeechContext.Draft draft() {
        return new ReferenceSpeechContext.Draft(SOURCE,
                Arrays.asList(new SubtitleCue(1000, 2400, "Synthetic spoken words.")), 27);
    }

    @Test public void draftCopiesEveryTimedCueAndRequiresExplicitReviewRatherThanAutomaticSummarization() {
        String complete = repeat('x', 600) + " final draft words";
        SubtitleCue first = new SubtitleCue(1000, 2400, "First words\nwith a second line.");
        SubtitleCue second = new SubtitleCue(3500, 4800, complete);
        ArrayList<SubtitleCue> input = new ArrayList<>(Arrays.asList(first, second));
        ReferenceSpeechContext.Draft draft = new ReferenceSpeechContext.Draft(SOURCE, input, 28);
        first.startMs = 0; first.endMs = 1; first.text = "Changed caller words"; input.clear();
        assertEquals(2, draft.cues.size()); assertEquals(1000, draft.cues.get(0).startMs);
        assertEquals("First words\nwith a second line.", draft.cues.get(0).text);
        assertEquals("1.000–2.400s: First words\nwith a second line.\n3.500–4.800s: " + complete,
                draft.formatTimedText());
        expectInvalid(() -> draft.reviewed(complete));
        assertEquals(complete, draft.cues.get(1).text);
        ReferenceSpeechContext.Reviewed confirmed = draft.reviewed("I explicitly corrected and selected these words.");
        assertEquals(SOURCE, confirmed.sourceUri); assertTrue(confirmed.matchesSource(SOURCE));
        assertFalse(confirmed.matchesSource(Uri.parse("content://synthetic.reference/videos/two")));
        assertEquals(complete, draft.cues.get(1).text);
        try { draft.cues.clear(); fail("Immutable cue list"); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void reviewedTextBoundsPreserveExactWordsAndRejectUnsupportedStructuralMarkers() {
        ReferenceSpeechContext.Draft draft = draft();
        String exact = "  Corrected words\nI choose\tto retain.  ";
        assertEquals(exact, draft.reviewed(exact).text);
        assertEquals(repeat('w', 210), draft.reviewed(repeat('w', 210)).text);
        for (String bad : new String[] {"", "   ", repeat('w', 211), "words\u0000hidden",
                "<|system|>words", "words|>", "Creator-reviewed reference moments: 0:01 Hero: face left",
                "Creator-reviewed reference speech: nested words"}) expectInvalid(() -> draft.reviewed(bad));
        ReferenceSpeechContext.Reviewed first = draft.reviewed("Original confirmed words");
        ReferenceSpeechContext.Reviewed edited = first.reviewed("Edited explicitly after restoring");
        assertEquals("Original confirmed words", first.text); assertEquals(SOURCE, edited.sourceUri);
    }

    @Test public void uriAndTimedDraftValidationRejectBadInputsWithoutReadingAnySource() {
        for (Uri bad : Arrays.asList(null, Uri.parse("https://example.invalid/video.mp4"),
                Uri.parse("file://remote/path/video.mp4"), Uri.parse("content:///no-authority")))
            expectInvalid(() -> new ReferenceSpeechContext.Draft(bad,
                    Arrays.asList(new SubtitleCue(1, 2, "words")), 1));
        for (SubtitleCue bad : Arrays.asList(new SubtitleCue(-1, 100, "words"),
                new SubtitleCue(5, 5, "words"), new SubtitleCue(0, 180001, "words"),
                new SubtitleCue(0, 100, " "), new SubtitleCue(0, 100, "bad\u0001words")))
            expectInvalid(() -> new ReferenceSpeechContext.Draft(SOURCE, Arrays.asList(bad), 1));
        expectInvalid(() -> new ReferenceSpeechContext.Draft(SOURCE, new ArrayList<>(), 1));
        expectInvalid(() -> new ReferenceSpeechContext.Draft(SOURCE,
                Arrays.asList(new SubtitleCue(1000, 1500, "first"), new SubtitleCue(500, 700, "second")), 1));
        expectInvalid(() -> new ReferenceSpeechContext.Draft(SOURCE,
                Arrays.asList(new SubtitleCue(1, 2, "words")), -1));
        expectInvalid(() -> new ReferenceSpeechContext.Draft(SOURCE,
                Arrays.asList(new SubtitleCue(0, 100, repeat('x', 16000)), new SubtitleCue(100, 200, "overflow")), 1));
    }

    @Test public void onlyStrictConfirmedJsonRestoresForExactMatchingLocalSource() throws Exception {
        ReferenceSpeechContext.Reviewed context = draft().reviewed("My corrected reference words");
        String json = context.toJson();
        ReferenceSpeechContext.Reviewed restored = ReferenceSpeechContext.Reviewed.fromJson(json, SOURCE);
        assertEquals(context.text, restored.text); assertEquals(context.sourceUri, restored.sourceUri);
        expectInvalid(() -> ReferenceSpeechContext.Reviewed.fromJson(json, Uri.parse("content://synthetic.reference/videos/two")));
        expectInvalid(() -> ReferenceSpeechContext.Reviewed.fromJson(json, null));
        expectInvalid(() -> ReferenceSpeechContext.Reviewed.fromJson("{}", SOURCE));
        for (String bad : new String[] {
                new JSONObject(json).put("reviewed", false).toString(),
                new JSONObject(json).put("reviewed", "true").toString(),
                json.replace("\"version\":1", "\"version\":1.0"),
                new JSONObject(json).put("version", 2).toString(),
                new JSONObject(json).put("text", repeat('x', 211)).toString(),
                new JSONObject(json).put("sourceUri", "https://example.invalid/video").toString(),
                new JSONObject(json).put("cues", "unconfirmed transcript").toString(),
                new JSONObject().put("version", 1).put("sourceUri", SOURCE.toString())
                        .put("reviewed", false).put("cues", "draft only").toString() })
            expectInvalid(() -> ReferenceSpeechContext.Reviewed.fromJson(bad, SOURCE));
    }

    @Test public void combinedBudgetCountsEveryLabelAndPreservesExistingVisualNotesExactly() {
        ReferenceSpeechContext.Reviewed speech = draft().reviewed("Selected reference words");
        String visual = "0:01 Hero: face left; 0:03 Closing: face forward";
        String tail = "\nCreator-reviewed reference speech: Selected reference words"
                + "\nCreator-reviewed reference moments: " + visual;
        String brief = repeat('b', 500 - tail.length());
        String exact = ReferenceSpeechContext.composePlanBrief(brief, visual, true, speech);
        assertEquals(500, exact.length()); assertEquals(brief + tail, exact);
        assertEquals(exact.length(), ReferenceSpeechContext.composedLength(brief, visual, true, speech.text));
        assertEquals(501, ReferenceSpeechContext.composedLength(brief + "b", visual, true, speech.text));
        assertEquals(ReferenceSpeechContext.composePlanBrief("brief", visual, false, speech).length(),
                ReferenceSpeechContext.composedLength("brief", visual, false, speech.text));
        assertEquals(0, ReferenceSpeechContext.composedLength(null, null, false, null));
        assertEquals("\nCreator-reviewed reference speech: ".length(),
                ReferenceSpeechContext.composedLength(null, "", false, ""));
        assertEquals("\nCreator-reviewed reference speech: ".length() + 700,
                ReferenceSpeechContext.composedLength(null, null, false, repeat('x', 700)));
        expectInvalid(() -> ReferenceSpeechContext.composePlanBrief(brief + "b", visual, true, speech));
        assertEquals("Selected reference words", speech.text); assertEquals(brief + tail, exact);
        assertEquals(" x ", ReferenceSpeechContext.composePlanBrief(" x ", null, false, null));
        assertEquals(repeat('b', 500), ReferenceSpeechContext.composePlanBrief(repeat('b', 500), "", false, null));
        expectInvalid(() -> ReferenceSpeechContext.composePlanBrief(repeat('b', 501), "", false, null));
        assertEquals("brief\nReference notes (reviewed when visual AI is used; approximate): " + visual,
                ReferenceSpeechContext.composePlanBrief("brief", visual, false, null));
    }

    @Test public void speechRoleLikeWordsPrecedeAndCannotBecomeCanonicalRetainedMomentCues() throws Exception {
        ReferenceSpeechContext.Reviewed speech = draft().reviewed("I heard Closing: face away as dialogue, not my direction.");
        String plan = ReferenceSpeechContext.composePlanBrief("Wear my jacket.",
                "0:01 Hero: face left; 0:03 Closing: face forward", true, speech);
        Class<?> planner = LocalPlanner.class;
        Field rolesField = planner.getDeclaredField("FASHION_ROLES"); rolesField.setAccessible(true);
        Method parse = planner.getDeclaredMethod("parseReviewedCues", String.class, String[].class); parse.setAccessible(true);
        Object cues = parse.invoke(null, plan, rolesField.get(null));
        Field instructions = cues.getClass().getDeclaredField("instructions"); instructions.setAccessible(true);
        assertArrayEquals(new String[] {"face left", null, null, null, "face forward"}, (String[]) instructions.get(cues));
        assertTrue(plan.indexOf("Creator-reviewed reference speech:") < plan.indexOf("Creator-reviewed reference moments:"));
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars);
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); fail("Input must be rejected without truncation/application"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }
}
