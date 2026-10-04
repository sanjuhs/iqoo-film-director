package dev.minifilm.director;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared subtitle visibility check. Does not rewrite text, timings or list order. */
public final class SubtitleTimeline {
    public static final String OVERLAP_MESSAGE = "Subtitle times overlap. Adjust the start/end times so one cue ends no later than another begins. Your words and times were kept.";

    private SubtitleTimeline() { }

    /**
     * Rejects overlapping nonempty, positive-length cue intervals; touching endpoints
     * are valid. Null/blank/invalid cues are left to each caller's existing validation
     * or normalization policy. Only temporary interval copies are sorted, so an
     * unsorted but nonoverlapping creator list remains unchanged.
     */
    public static void requireNonOverlapping(List<SubtitleCue> cues) {
        if (cues == null) return;
        List<long[]> intervals = new ArrayList<>();
        for (SubtitleCue cue : cues) {
            if (cue == null || cue.text == null || cue.text.trim().isEmpty()
                    || cue.endMs <= cue.startMs) continue;
            intervals.add(new long[] { cue.startMs, cue.endMs });
        }
        intervals.sort(Comparator.comparingLong(interval -> interval[0]));
        boolean first = true;
        long previousEnd = 0;
        for (long[] interval : intervals) {
            if (!first && interval[0] < previousEnd)
                throw new IllegalArgumentException(OVERLAP_MESSAGE);
            previousEnd = interval[1];
            first = false;
        }
    }
}
