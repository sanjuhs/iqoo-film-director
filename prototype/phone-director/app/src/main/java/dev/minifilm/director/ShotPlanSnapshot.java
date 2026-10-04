package dev.minifilm.director;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable current-plan metadata for an explicit export. No media inspection or approval claim. */
public final class ShotPlanSnapshot {
    public static final int MAX_SHOTS = 12;
    public final List<Entry> entries;
    public final String sourceLabel;

    public static final class Entry {
        public final int shotIndex;
        public final String id, title, instruction, caption;
        public final long targetDurationMs;

        private Entry(int index, Shot shot) {
            shotIndex = index;
            id = shot.id;
            title = shot.title;
            instruction = shot.instruction;
            caption = shot.caption;
            targetDurationMs = shot.targetDurationMs;
        }
    }

    private ShotPlanSnapshot(List<Entry> entries, String sourceLabel) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.sourceLabel = sourceLabel;
    }

    public static ShotPlanSnapshot empty() {
        return new ShotPlanSnapshot(Collections.emptyList(), "");
    }

    /** Preserves existing IDs and exact text; invalid/oversized fields are rejected, never truncated. */
    public static ShotPlanSnapshot capture(List<Shot> plan, String sourceLabel) {
        if (plan == null) throw new IllegalArgumentException("The current shot plan is missing.");
        if (plan.size() > MAX_SHOTS) throw new IllegalArgumentException("Use at most 12 plan shots before exporting.");
        checkText(sourceLabel, 200, "Plan source label");
        List<Entry> entries = new ArrayList<>(plan.size());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < plan.size(); i++) {
            Shot shot = plan.get(i);
            if (shot == null) throw new IllegalArgumentException("The current plan contains a missing shot.");
            checkText(shot.id, 160, "Plan shot ID");
            if (shot.id.trim().isEmpty() || !ids.add(shot.id))
                throw new IllegalArgumentException("Plan shot IDs must be nonempty and distinct before exporting.");
            checkText(shot.title, 140, "Plan shot title");
            checkText(shot.instruction, 2000, "Plan direction");
            checkText(shot.caption, 1000, "Plan caption");
            if (shot.targetDurationMs < 2000 || shot.targetDurationMs > 60000)
                throw new IllegalArgumentException("Plan shot durations must be between 2 and 60 seconds.");
            entries.add(new Entry(i, shot));
        }
        return new ShotPlanSnapshot(entries, sourceLabel);
    }

    private static void checkText(String text, int max, String field) {
        if (text == null) throw new IllegalArgumentException(field + " is missing. Review the plan before exporting.");
        if (text.length() > max)
            throw new IllegalArgumentException(field + " is too long. Use at most " + max + " characters before exporting.");
    }

    public boolean isEmpty() { return entries.isEmpty(); }

    /** Exact identity only: never resolve an old association by its title, role or list position. */
    public Entry find(String id) {
        if (id == null) return null;
        for (Entry entry : entries) if (entry.id.equals(id)) return entry;
        return null;
    }

    /** Every call returns independently mutable JSON with no caller-owned objects. */
    public JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject().put("schema", "minifilm.shot-plan.v1")
                .put("sourceLabel", sourceLabel);
        JSONArray shots = new JSONArray();
        for (Entry entry : entries) shots.put(new JSONObject().put("order", entry.shotIndex + 1)
                .put("id", entry.id).put("title", entry.title).put("instruction", entry.instruction)
                .put("caption", entry.caption).put("targetDurationMs", entry.targetDurationMs));
        return result.put("shots", shots);
    }

    /** Portable notes omit source URIs, raw provenance IDs and unresolved association IDs. */
    public String readableNotes(List<Take> takes) {
        StringBuilder notes = new StringBuilder("CURRENT SHOT PLAN\nMetadata only; review your footage yourself.\n");
        if (!sourceLabel.isEmpty()) notes.append("Plan source: ").append(sourceLabel).append('\n');
        if (entries.isEmpty()) notes.append("No current plan was included.\n");
        for (Entry entry : entries) {
            notes.append('\n').append(entry.shotIndex + 1).append(". ").append(entry.title)
                    .append(" (target ").append(seconds(entry.targetDurationMs)).append("s)\n")
                    .append("Direction: ").append(entry.instruction).append('\n')
                    .append("Caption: ").append(entry.caption).append('\n');
        }
        notes.append("\nSELECTED CUT ASSIGNMENTS\nTimes below are relative to each original source.\n");
        int cutIndex = 0;
        if (takes != null) for (Take take : takes) {
            if (take == null || !take.selected || take.uri == null
                    || !("file".equals(take.uri.getScheme()) || "content".equals(take.uri.getScheme()))
                    || take.inMs < 0 || take.outMs <= take.inMs || take.outMs > take.durationMs) continue;
            if (++cutIndex > 12) throw new IllegalArgumentException("Use at most 12 selected cuts before exporting.");
            notes.append("\nCut ").append(cutIndex).append(": ").append(seconds(take.inMs)).append("–")
                    .append(seconds(take.outMs)).append("s\n");
            Set<String> seen = new HashSet<>();
            boolean assigned = false, unknown = false;
            if (take.reviewedShotIds != null) for (String id : take.reviewedShotIds) {
                if (!seen.add(id)) continue;
                Entry entry = find(id);
                if (entry == null) { unknown = true; continue; }
                notes.append("Creator assignment: ").append(entry.shotIndex + 1).append(". ")
                        .append(entry.title).append('\n');
                assigned = true;
            }
            if (unknown) notes.append("Unknown or earlier-plan assignment; review on the phone.\n");
            if (!assigned && !unknown) notes.append("No creator-reviewed shot assignment.\n");
        }
        if (cutIndex == 0) notes.append("No selected cuts with valid local-source trim metadata.\n");
        return notes.toString();
    }

    private static String seconds(long ms) { return BigDecimal.valueOf(ms, 3).toPlainString(); }
}
