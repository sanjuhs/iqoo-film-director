package dev.minifilm.director;

/** Experimental recording-energy heuristic, not speech detection or sentence completion.
 * Construct only after explicit opt-in for a timed spoken take. No clocks, capture or audio streams.
 */
public final class QuietTailStopPolicy {
    public static final String LABEL = "Experimental quiet-pause energy heuristic; uncalibrated";
    public static final double ACTIVE_AMPLITUDE = .020, QUIET_AMPLITUDE = .008;
    public static final long MAX_EXTENSION_MS = 8000, MAX_TAKE_MS = 60000;
    public static final long MAX_GAP_MS = 500, PRIOR_ENERGY_MS = 200, QUIET_WINDOW_MS = 1000;
    public static final int MIN_PRIOR_SAMPLES = 3, MIN_QUIET_SAMPLES = 3;
    public enum Decision { CONTINUE, QUIET_TAIL, HARD_LIMIT }

    private final long generation, recordingId, plannedMs, startElapsedMs, hardMs;
    private long lastRecordedMs = -1, lastReceiptMs = -1;
    private long firstHighRecordedMs = -1, firstHighReceiptMs = -1;
    private long firstQuietRecordedMs = -1, firstQuietReceiptMs = -1;
    private int highSamples, quietSamples;
    private boolean priorEnergy, cancelled;
    private Decision decision = Decision.CONTINUE;

    public QuietTailStopPolicy(long generation, long recordingId, long plannedMs, long startElapsedMs) {
        if (generation <= 0 || recordingId <= 0 || plannedMs <= 0 || plannedMs > MAX_TAKE_MS
                || startElapsedMs < 0 || startElapsedMs > Long.MAX_VALUE - MAX_TAKE_MS)
            throw new IllegalArgumentException("Use one valid explicitly started take and planned duration.");
        this.generation = generation; this.recordingId = recordingId;
        this.plannedMs = plannedMs; this.startElapsedMs = startElapsedMs;
        this.hardMs = Math.min(MAX_TAKE_MS, plannedMs + MAX_EXTENSION_MS);
    }

    public long getHardDurationMs() { return hardMs; }

    /** Matching ACTIVE/error-free audio only. Increasing event duration and local receipt are required;
     * these cannot certify the underlying amplitude sample's age. Other take IDs never mutate this policy.
     */
    public void observe(long generation, long recordingId, long recordedMs, long receiptMs,
            double amplitude, boolean eligible) {
        if (cancelled || decision != Decision.CONTINUE || generation != this.generation || recordingId != this.recordingId) return;
        if (recordedMs < 0 || recordedMs > MAX_TAKE_MS || receiptMs < startElapsedMs
                || recordedMs <= lastRecordedMs || receiptMs <= lastReceiptMs) {
            clearEvidence(); return;
        }
        boolean gap = lastRecordedMs >= 0 && (recordedMs - lastRecordedMs > MAX_GAP_MS
                || receiptMs - lastReceiptMs > MAX_GAP_MS);
        lastRecordedMs = recordedMs; lastReceiptMs = receiptMs;
        if (gap) clearEvidence();
        if (!eligible || !Double.isFinite(amplitude) || amplitude < 0 || amplitude > 1) {
            clearEvidence(); return;
        }
        if (amplitude >= ACTIVE_AMPLITUDE) {
            clearQuiet();
            if (firstHighRecordedMs < 0) { firstHighRecordedMs = recordedMs; firstHighReceiptMs = receiptMs; }
            if (highSamples < MIN_PRIOR_SAMPLES) highSamples++;
            if (highSamples >= MIN_PRIOR_SAMPLES && recordedMs - firstHighRecordedMs >= PRIOR_ENERGY_MS
                    && receiptMs - firstHighReceiptMs >= PRIOR_ENERGY_MS) priorEnergy = true;
        } else if (amplitude <= QUIET_AMPLITUDE && priorEnergy && recordedMs >= plannedMs) {
            if (firstQuietRecordedMs < 0) { firstQuietRecordedMs = recordedMs; firstQuietReceiptMs = receiptMs; }
            if (quietSamples < MIN_QUIET_SAMPLES) quietSamples++;
        } else clearQuiet();
    }

    /** Call from the independent timer too: missing Status can never remove the bounded hard cap.
     * Decision is terminal until explicit cancellation; cancellation never requests a stop itself.
     */
    public Decision evaluate(long nowElapsedMs) {
        if (cancelled) return Decision.CONTINUE;
        if (decision != Decision.CONTINUE) return decision;
        if (nowElapsedMs < startElapsedMs) return Decision.CONTINUE;
        long elapsed = nowElapsedMs - startElapsedMs;
        if (elapsed >= hardMs) return decision = Decision.HARD_LIMIT;
        if (lastReceiptMs < 0 || nowElapsedMs < lastReceiptMs) return Decision.CONTINUE;
        if (nowElapsedMs - lastReceiptMs > MAX_GAP_MS) { clearEvidence(); return Decision.CONTINUE; }
        if (elapsed >= plannedMs && priorEnergy && quietSamples >= MIN_QUIET_SAMPLES
                && lastRecordedMs - firstQuietRecordedMs >= QUIET_WINDOW_MS
                && lastReceiptMs - firstQuietReceiptMs >= QUIET_WINDOW_MS)
            return decision = Decision.QUIET_TAIL;
        return Decision.CONTINUE;
    }

    /** Manual/background/error/finalize invalidation. Caller performs any immediate capture stop. */
    public void cancel() { cancelled = true; clearEvidence(); decision = Decision.CONTINUE; }

    private void clearQuiet() { firstQuietRecordedMs = -1; firstQuietReceiptMs = -1; quietSamples = 0; }
    private void clearEvidence() {
        clearQuiet(); firstHighRecordedMs = -1; firstHighReceiptMs = -1; highSamples = 0; priorEnergy = false;
    }
}
