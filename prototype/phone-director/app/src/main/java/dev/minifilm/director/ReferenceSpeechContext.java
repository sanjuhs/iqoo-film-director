package dev.minifilm.director;

import android.net.Uri;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local speech drafts are temporary. Only explicitly corrected words become planning context.
 * This data helper neither transcribes audio nor infers beats, motion, or a reference's story. */
public final class ReferenceSpeechContext {
    public static final int MAX_CONTEXT = 210;
    public static final int MAX_PLAN = 500;
    private static final int MAX_CUES = 512;
    private static final int MAX_DRAFT_TEXT = 16_000;
    private static final String SPEECH_PREFIX = "\nCreator-reviewed reference speech: ";
    private static final String MOMENTS_PREFIX = "\nCreator-reviewed reference moments: ";
    private static final String VISUAL_PREFIX = "\nReference notes (reviewed when visual AI is used; approximate): ";
    private ReferenceSpeechContext() { }

    public static final class Cue {
        public final long startMs, endMs;
        public final String text;
        private Cue(SubtitleCue cue) {
            if (cue == null || cue.startMs < 0 || cue.endMs <= cue.startMs || cue.endMs > 180_000)
                throw new IllegalArgumentException("Reference speech times must be inside a three-minute clip.");
            startMs = cue.startMs; endMs = cue.endMs;
            text = plainText(cue.text, MAX_DRAFT_TEXT, "Speech draft");
        }
    }

    /** No JSON method: an unconfirmed transcript must never be restored as approved context. */
    public static final class Draft {
        public final Uri sourceUri;
        public final List<Cue> cues;
        public final long elapsedMs;
        public Draft(Uri sourceUri, List<SubtitleCue> cues, long elapsedMs) {
            ReferenceBoard.validateUri(sourceUri);
            if (elapsedMs < 0 || cues == null || cues.isEmpty() || cues.size() > MAX_CUES)
                throw new IllegalArgumentException("No bounded reference speech draft is available. Keep manual notes.");
            ArrayList<Cue> copies = new ArrayList<>();
            int total = 0; long previous = -1;
            for (SubtitleCue input : cues) {
                Cue cue = new Cue(input);
                if (cue.startMs < previous)
                    throw new IllegalArgumentException("Reference speech cues must remain in source-time order.");
                previous = cue.startMs; total += cue.text.length();
                if (total > MAX_DRAFT_TEXT)
                    throw new IllegalArgumentException("This reference speech draft is too large. Use a shorter reference.");
                copies.add(cue);
            }
            this.sourceUri = sourceUri; this.elapsedMs = elapsedMs;
            this.cues = Collections.unmodifiableList(copies);
        }
        /** Full text, including every source cue; no automatic summary or truncation. */
        public String formatTimedText() {
            StringBuilder result = new StringBuilder();
            for (Cue cue : cues) {
                if (result.length() > 0) result.append('\n');
                result.append(SubtitleTime.format(cue.startMs)).append("–")
                        .append(SubtitleTime.format(cue.endMs)).append("s: ").append(cue.text);
            }
            return result.toString();
        }
        /** Call only after the creator explicitly chooses/corrects the words. */
        public Reviewed reviewed(String correctedText) { return new Reviewed(sourceUri, correctedText); }
    }

    public static final class Reviewed {
        public final Uri sourceUri;
        public final String text;
        private Reviewed(Uri sourceUri, String correctedText) {
            ReferenceBoard.validateUri(sourceUri);
            text = plainText(correctedText, MAX_CONTEXT, "Reviewed speech context");
            // The planner strips chat tokens and treats the moments marker as a structural suffix.
            // Reject them explicitly rather than silently changing confirmed creator wording.
            if (text.contains("<|") || text.contains("|>")
                    || text.contains("Creator-reviewed reference moments:")
                    || text.contains("Creator-reviewed reference speech:"))
                throw new IllegalArgumentException("Use plain reference words without planner markers.");
            this.sourceUri = sourceUri;
        }
        public boolean matchesSource(Uri selectedUri) { return sourceUri.equals(selectedUri); }
        /** Explicit re-review of confirmed words, retaining their original source binding. */
        public Reviewed reviewed(String correctedText) { return new Reviewed(sourceUri, correctedText); }
        /** Confirmed text only; no audio, transcript cues or temporary frame pixels. */
        public String toJson() {
            try {
                return new JSONObject().put("version", 1).put("sourceUri", sourceUri.toString())
                        .put("reviewed", true).put("text", text).toString();
            } catch (Exception failure) { throw new IllegalStateException("Reviewed reference speech could not be saved."); }
        }
        public static Reviewed fromJson(String json, Uri selectedUri) {
            try {
                ReferenceBoard.validateUri(selectedUri);
                if (json == null || json.length() > 8000) throw new IllegalArgumentException();
                JSONObject object = new JSONObject(json);
                Object version = object.get("version");
                if (object.length() != 4 || !(version instanceof Integer || version instanceof Long)
                        || ((Number) version).longValue() != 1 || !Boolean.TRUE.equals(object.get("reviewed"))
                        || !(object.get("sourceUri") instanceof String) || !(object.get("text") instanceof String))
                    throw new IllegalArgumentException();
                Reviewed restored = new Reviewed(Uri.parse(object.getString("sourceUri")), object.getString("text"));
                if (!restored.matchesSource(selectedUri)) throw new IllegalArgumentException();
                return restored;
            } catch (Exception failure) {
                throw new IllegalArgumentException("Saved speech context does not match this reference or is invalid. Review it again.");
            }
        }
    }

    /** Main must additionally check reviewedSpeech.matchesSource(the selected URI).
     * Keep the entire canonical moments suffix last for LocalPlanner's named-cue parser. */
    public static String composePlanBrief(String brief, String visualSummary, boolean reviewedBoard,
                                          Reviewed reviewedSpeech) {
        if (composedLength(brief, visualSummary, reviewedBoard, reviewedSpeech == null ? null : reviewedSpeech.text) > MAX_PLAN)
            throw new IllegalArgumentException("Shorten your brief or reviewed reference words to fit 500 characters together, including labels. Nothing was truncated.");
        StringBuilder result = new StringBuilder(brief == null ? "" : brief);
        if (reviewedSpeech != null) result.append(SPEECH_PREFIX).append(reviewedSpeech.text);
        if (visualSummary != null && !visualSummary.isEmpty())
            result.append(reviewedBoard ? MOMENTS_PREFIX : VISUAL_PREFIX).append(visualSummary);
        return result.toString();
    }

    /** Live UI count, even for not-yet-valid speech words. Null omits the section; an empty but
     * nonnull candidate counts its label. This neither validates nor confirms the candidate. */
    public static int composedLength(String brief, String visualSummary, boolean reviewedBoard, String speechText) {
        long count = brief == null ? 0 : brief.length();
        if (speechText != null) count += (long) SPEECH_PREFIX.length() + speechText.length();
        if (visualSummary != null && !visualSummary.isEmpty())
            count += (long) (reviewedBoard ? MOMENTS_PREFIX : VISUAL_PREFIX).length() + visualSummary.length();
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("The combined reference text is outside the supported length.");
        return (int) count;
    }

    private static String plainText(String value, int limit, String name) {
        if (value == null || value.trim().isEmpty() || value.length() > limit)
            throw new IllegalArgumentException(name + " must contain 1–" + limit + " characters. Nothing was truncated.");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
                throw new IllegalArgumentException(name + " contains unsupported control characters.");
        }
        return value;
    }
}
