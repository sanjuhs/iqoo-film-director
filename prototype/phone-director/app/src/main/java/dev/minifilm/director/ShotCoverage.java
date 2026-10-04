package dev.minifilm.director;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creator-reviewed metadata coverage only. This does not inspect media or judge shot quality. */
public final class ShotCoverage {
    private ShotCoverage() {}

    public static final class Entry {
        public final int shotIndex;
        public final String shotId;
        public final int reviewedSelectedTakeCount;
        public final boolean covered;

        private Entry(int index, String id, int count) {
            shotIndex = index;
            shotId = id;
            reviewedSelectedTakeCount = count;
            covered = count > 0;
        }
    }

    public static final class Result {
        public final List<Entry> entries;
        public final int coveredCount, totalCount, nextMissingIndex;

        private Result(List<Entry> entries, int covered, int nextMissing) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            coveredCount = covered;
            totalCount = entries.size();
            nextMissingIndex = nextMissing;
        }
    }

    /** Returns a snapshot without altering mappings, selections, trims or either input list. */
    public static Result evaluate(List<Shot> plan, List<Take> takes) {
        List<Shot> shots = plan == null ? Collections.emptyList() : plan;
        Set<String> currentIds = new HashSet<>();
        for (Shot shot : shots) if (shot != null && shot.id != null && !shot.id.isEmpty())
            currentIds.add(shot.id);
        Map<String, Integer> counts = new HashMap<>();
        if (takes != null) for (Take take : takes) {
            if (take == null || !take.selected || take.uri == null
                    || !("file".equals(take.uri.getScheme()) || "content".equals(take.uri.getScheme()))
                    || take.inMs < 0 || take.outMs <= take.inMs
                    || take.outMs > take.durationMs || take.reviewedShotIds == null) continue;
            Set<String> counted = new HashSet<>();
            for (String id : take.reviewedShotIds) if (currentIds.contains(id) && counted.add(id))
                counts.put(id, counts.getOrDefault(id, 0) + 1);
        }
        List<Entry> entries = new ArrayList<>();
        int covered = 0, nextMissing = -1;
        for (int i = 0; i < shots.size(); i++) {
            Shot shot = shots.get(i);
            String id = shot == null ? null : shot.id;
            Entry entry = new Entry(i, id, counts.getOrDefault(id, 0));
            entries.add(entry);
            if (entry.covered) covered++;
            else if (nextMissing == -1) nextMissing = i;
        }
        return new Result(entries, covered, nextMissing);
    }

    /** New plan identity prevents older reviewed shot-N mappings from matching a replacement. */
    public static List<Shot> freshPlan(List<Shot> plan) {
        if (plan == null) throw new IllegalArgumentException("Plan is required");
        String namespace = "plan-" + UUID.randomUUID() + "-shot-";
        List<Shot> copies = new ArrayList<>(plan.size());
        for (int i = 0; i < plan.size(); i++) {
            Shot source = plan.get(i);
            if (source == null) throw new IllegalArgumentException("Plan contains a missing shot");
            Shot copy = new Shot(namespace + (i + 1), source.title, source.instruction,
                    source.caption, source.targetDurationMs);
            // Preserve reviewed metadata exactly, including a duration edited after construction.
            copy.targetDurationMs = source.targetDurationMs;
            copies.add(copy);
        }
        return copies;
    }
}
