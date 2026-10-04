package dev.minifilm.director;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static dev.minifilm.director.QuietTailStopPolicy.Decision.*;

/** Synthetic scalar events only: no camera, microphone, playback, model or speech-quality claim. */
@RunWith(AndroidJUnit4.class)
public final class QuietTailStopPolicyTest {
    private static final long START = 10_000, GENERATION = 41, TAKE = 7;

    @Test public void ongoingEnergyCrossesPlannedLengthThenAnObservedTailCanStop() {
        QuietTailStopPolicy p = policy(2000);
        talk(p, 100, 3000);
        assertEquals("Talking energy must not stop at the planned two seconds", CONTINUE, p.evaluate(START + 3000));
        quiet(p, 3250, 4000);
        assertEquals("A 750ms hesitation is insufficient", CONTINUE, p.evaluate(START + 4000));
        sample(p, 4250, .003, true);
        assertEquals(QUIET_TAIL, p.evaluate(START + 4250));
        assertEquals("A terminal decision is stable", QUIET_TAIL, p.evaluate(START + 4300));
    }

    @Test public void hesitationBeforePlannedLengthDoesNotArmAnEarlyEnd() {
        QuietTailStopPolicy p = policy(2000);
        talk(p, 100, 500); quiet(p, 750, 1750);
        assertEquals(CONTINUE, p.evaluate(START + 1750));
        sample(p, 2000, .06, true); quiet(p, 2250, 3000);
        assertEquals("Earlier quiet cannot be reused after resumed energy", CONTINUE, p.evaluate(START + 3000));
        sample(p, 3250, .003, true);
        assertEquals(QUIET_TAIL, p.evaluate(START + 3250));
    }

    @Test public void silentStartupNeverPretendsSpeechFinishedAndMissingStatusStillHasHardCap() {
        QuietTailStopPolicy silent = policy(2000);
        for (long t = 0; t <= 3500; t += 250) sample(silent, t, 0, true);
        assertEquals(CONTINUE, silent.evaluate(START + 3500));
        assertEquals(HARD_LIMIT, silent.evaluate(START + 10_000));
        QuietTailStopPolicy missing = policy(2000);
        assertEquals(CONTINUE, missing.evaluate(START + 9999));
        assertEquals("Independent timer must enforce the cap with no audio events", HARD_LIMIT,
                missing.evaluate(START + 10_000));
    }

    @Test public void ineligibleNoAudioMuteSilencingOrErrorCannotCountAsAQuietTail() {
        // CaptureController maps these audio states to the same eligible=false contract.
        // This tests that scalar contract, not real routing or state generation by CameraX.
        for (String reason : new String[] {"no audio", "muted", "system silenced", "encoder/source error"}) {
            QuietTailStopPolicy p = policy(2000); talk(p, 100, 1900);
            sample(p, 2000, 0, false); quiet(p, 2250, 3250);
            assertEquals(reason + " must discard prior-energy eligibility", CONTINUE, p.evaluate(START + 3250));
            talk(p, 3500, 4000); quiet(p, 4250, 5250);
            assertEquals("New eligible energy can re-arm after " + reason, QUIET_TAIL, p.evaluate(START + 5250));
        }
    }

    @Test public void invalidEnergyAndIntermediateEnergyCannotCompleteTheQuietWindow() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -.1, 1.1}) {
            QuietTailStopPolicy p = policy(2000); talk(p, 100, 1900);
            sample(p, 2000, bad, true); quiet(p, 2250, 3250);
            assertEquals("Invalid amplitude must not imply quiet", CONTINUE, p.evaluate(START + 3250));
        }
        QuietTailStopPolicy middle = policy(2000); talk(middle, 100, 1900);
        quiet(middle, 2000, 2500); sample(middle, 2750, .012, true); quiet(middle, 3000, 3750);
        assertEquals("Intermediate energy restarts the tail window", CONTINUE, middle.evaluate(START + 3750));
        sample(middle, 4000, .003, true); assertEquals(QUIET_TAIL, middle.evaluate(START + 4000));
    }

    @Test public void duplicateOrRegressingStatsCannotManufactureFreshQuietEvidence() {
        for (long staleDuration : new long[] {2250, 2000}) {
            QuietTailStopPolicy p = policy(2000); talk(p, 100, 1900); quiet(p, 2000, 2250);
            p.observe(GENERATION, TAKE, staleDuration, START + 2500, .003, true);
            quiet(p, 2750, 3750);
            assertEquals("Stale duration invalidates evidence instead of adding a sample", CONTINUE, p.evaluate(START + 3750));
        }
    }

    @Test public void ineligibleDuplicateOrNegativeDurationDisarmsAnOtherwiseCompleteTail() {
        for (long duration : new long[] {3000, -1}) {
            QuietTailStopPolicy p = policy(2000); talk(p, 100, 1900); quiet(p, 2000, 3000);
            // A muted/error status may arrive before the timer evaluates the completed tail,
            // without recorded-duration progress. It must still reach the policy and disarm it.
            p.observe(GENERATION, TAKE, duration, START + 3001, 0, false);
            assertEquals("Unavailable audio must invalidate earlier quiet evidence", CONTINUE, p.evaluate(START + 3001));
        }
    }

    @Test public void gapsAndStaleReceiptDiscardEvidenceWithoutRemovingTheDeadline() {
        QuietTailStopPolicy statsGap = policy(2000); talk(statsGap, 100, 1900); quiet(statsGap, 2000, 2250);
        statsGap.observe(GENERATION, TAKE, 3000, START + 2500, .003, true);
        statsGap.observe(GENERATION, TAKE, 3250, START + 2750, .003, true);
        statsGap.observe(GENERATION, TAKE, 3500, START + 3000, .003, true);
        assertEquals(CONTINUE, statsGap.evaluate(START + 3000));
        QuietTailStopPolicy receiptGap = policy(2000); talk(receiptGap, 100, 1900); quiet(receiptGap, 2000, 2250);
        receiptGap.observe(GENERATION, TAKE, 2500, START + 3000, .003, true);
        receiptGap.observe(GENERATION, TAKE, 2750, START + 3250, .003, true);
        receiptGap.observe(GENERATION, TAKE, 3000, START + 3500, .003, true);
        assertEquals(CONTINUE, receiptGap.evaluate(START + 3500));
        QuietTailStopPolicy stale = policy(2000); talk(stale, 100, 1900); quiet(stale, 2000, 3000);
        assertEquals("A completed-looking tail is unusable once delivery is stale", CONTINUE, stale.evaluate(START + 3501));
        assertEquals(HARD_LIMIT, stale.evaluate(START + 10_000));
    }

    @Test public void queuedBacklogNeedsBothStatsAndRealReceiptSpans() {
        QuietTailStopPolicy p = policy(2000); talk(p, 100, 1900);
        for (int i = 0; i < 5; i++) p.observe(GENERATION, TAKE, 2000 + i * 250, START + 2000 + i, .003, true);
        assertEquals("One second of queued stats delivered in four milliseconds is not a quiet second",
                CONTINUE, p.evaluate(START + 2010));
    }

    @Test public void priorEnergyRequiresMultipleObservationsWithElapsedSpan() {
        QuietTailStopPolicy p = policy(2000);
        sample(p, 1800, .06, true); sample(p, 1850, .06, true); sample(p, 1900, .06, true);
        quiet(p, 2000, 3000);
        assertEquals("Three samples spanning only 100ms cannot arm", CONTINUE, p.evaluate(START + 3000));
        talk(p, 3250, 3550); quiet(p, 3800, 4800);
        assertEquals(QUIET_TAIL, p.evaluate(START + 4800));
    }

    @Test public void otherTakeAndControllerEventsCannotArmOrResetCurrentPolicy() {
        QuietTailStopPolicy current = policy(2000); talk(current, 100, 1900); quiet(current, 2000, 2750);
        current.observe(GENERATION, TAKE - 1, 2900, START + 2900, .5, false);
        current.observe(GENERATION - 1, TAKE, 2900, START + 2900, .5, false);
        sample(current, 3000, .003, true); assertEquals(QUIET_TAIL, current.evaluate(START + 3000));
        QuietTailStopPolicy next = new QuietTailStopPolicy(GENERATION, TAKE + 1, 2000, START);
        for (long t = 100; t <= 3000; t += 100)
            next.observe(GENERATION, TAKE, t, START + t, t < 2000 ? .06 : .003, true);
        assertEquals("Old take cannot stop a new take", CONTINUE, next.evaluate(START + 3000));
        assertEquals(HARD_LIMIT, next.evaluate(START + 10_000));
    }

    @Test public void cancellationNeverWaitsForQuietOrRequestsAnotherStop() {
        QuietTailStopPolicy p = policy(2000); talk(p, 100, 1900); quiet(p, 2000, 3000);
        p.cancel(); assertEquals(CONTINUE, p.evaluate(START + 3000));
        assertEquals("Caller owns immediate Stop/background handling", CONTINUE, p.evaluate(START + 100_000));
        talk(p, 3500, 4000); quiet(p, 4250, 5250); assertEquals(CONTINUE, p.evaluate(START + 5250));
    }

    @Test public void extensionClipsToSixtySecondsAndInvalidConstructionIsRejected() {
        QuietTailStopPolicy nearLimit = policy(57_000); assertEquals(60_000, nearLimit.getHardDurationMs());
        assertEquals(CONTINUE, nearLimit.evaluate(START + 59_999));
        assertEquals(HARD_LIMIT, nearLimit.evaluate(START + 60_000));
        QuietTailStopPolicy atLimit = policy(60_000); assertEquals(60_000, atLimit.getHardDurationMs());
        assertEquals(HARD_LIMIT, atLimit.evaluate(START + 60_000));
        for (long planned : new long[] {-1, 0, 60_001, Long.MAX_VALUE}) {
            try { policy(planned); fail("Invalid planned duration accepted"); } catch (IllegalArgumentException expected) { }
        }
        try { new QuietTailStopPolicy(0, TAKE, 2000, START); fail("Invalid session accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static QuietTailStopPolicy policy(long planned) { return new QuietTailStopPolicy(GENERATION, TAKE, planned, START); }
    private static void sample(QuietTailStopPolicy p, long ms, double amplitude, boolean eligible) {
        p.observe(GENERATION, TAKE, ms, START + ms, amplitude, eligible);
    }
    private static void talk(QuietTailStopPolicy p, long start, long end) {
        for (long t = start; t <= end; t += 100) sample(p, t, .06, true);
    }
    private static void quiet(QuietTailStopPolicy p, long start, long end) {
        for (long t = start; t <= end; t += 250) sample(p, t, .003, true);
    }
}
