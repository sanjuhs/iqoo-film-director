package dev.minifilm.director;

import android.net.Uri;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Review-only outer-edge trim from local PCM energy supported by English ASR timestamps. */
public final class SpeechTrim {
    public final Uri uri;
    public final long currentInMs, currentOutMs, sourceDurationMs, suggestedInMs, suggestedOutMs;
    public final boolean hasSuggestion, reviewRequired = true;
    /** Confidence is an uncalibrated rule score, not a probability of correct speech detection. */
    public final double confidence;
    public final double noiseFloorRms, peakRms, thresholdRms, clippedFraction;
    public final long energyStartMs, energyEndMs, asrStartMs, asrEndMs, activeEnergyMs;
    public final String reason;
    public final String backend = "local PCM energy + offline English ASR; deterministic edge heuristic";

    private SpeechTrim(Uri uri, long in, long out, long duration, long suggestedIn, long suggestedOut,
            boolean suggest, double confidence, String reason, double floor, double peak, double threshold,
            double clipped, long energyStart, long energyEnd, long asrStart, long asrEnd, long activeMs) {
        this.uri = uri; currentInMs = in; currentOutMs = out; sourceDurationMs = duration;
        suggestedInMs = suggestedIn; suggestedOutMs = suggestedOut; hasSuggestion = suggest;
        this.confidence = confidence; this.reason = reason; noiseFloorRms = floor; peakRms = peak;
        thresholdRms = threshold; clippedFraction = clipped; energyStartMs = energyStart;
        energyEndMs = energyEnd; asrStartMs = asrStart; asrEndMs = asrEnd; activeEnergyMs = activeMs;
    }

    public boolean matches(Take take) {
        return take != null && uri.equals(take.uri) && currentInMs == take.inMs && currentOutMs == take.outMs
                && sourceDurationMs == take.durationMs;
    }

    /** Explicit creator-confirmed application. Captions, selection and the source stay untouched. */
    public boolean applyTo(Take take) {
        if (!hasSuggestion || !matches(take) || suggestedInMs < currentInMs || suggestedOutMs > currentOutMs
                || suggestedInMs < 0 || suggestedOutMs > sourceDurationMs || suggestedOutMs - suggestedInMs < 1000
                || (suggestedInMs == currentInMs && suggestedOutMs == currentOutMs)) return false;
        take.inMs = suggestedInMs; take.outMs = suggestedOutMs;
        return true;
    }

    static SpeechTrim analyze(Uri uri, float[] pcm, long offsetMs, long durationMs, long inMs, long outMs,
            List<SubtitleCue> cues, BooleanSupplier cancelled) {
        if (uri == null || pcm == null || cues == null || cancelled == null || offsetMs < 0
                || durationMs <= 0 || durationMs > 180_000 || inMs < 0 || outMs > durationMs || outMs <= inMs)
            throw new IllegalArgumentException("Choose a valid reviewed clip range before suggesting a trim.");
        checkCancelled(cancelled);
        long asrStart = Long.MAX_VALUE, asrEnd = -1;
        for (SubtitleCue cue : cues) {
            if (cue != null && cue.text != null && !cue.text.trim().isEmpty() && cue.startMs >= 0
                    && cue.endMs > cue.startMs && cue.endMs <= durationMs && cue.endMs > inMs && cue.startMs < outMs) {
                asrStart = Math.min(asrStart, Math.max(inMs, cue.startMs));
                asrEnd = Math.max(asrEnd, Math.min(outMs, cue.endMs));
            }
        }
        if (asrEnd < 0) return unchanged(uri, inMs, outMs, durationMs, "No valid spoken-text timing supports a trim.",
                0, 0, 0, 0, -1, -1, -1, -1, 0);
        int firstSample = (int) Math.max(0, Math.min(pcm.length, (inMs - offsetMs) * 16));
        int lastSample = (int) Math.max(0, Math.min(pcm.length, (outMs - offsetMs) * 16));
        int windows = (lastSample - firstSample) / 320; // Complete 20 ms windows at 16 kHz.
        if (windows < 50) return unchanged(uri, inMs, outMs, durationMs, "Keep at least a second for reviewable speech.",
                0, 0, 0, 0, -1, -1, asrStart, asrEnd, 0);
        double[] rms = new double[windows]; long clipped = 0;
        for (int w = 0; w < windows; w++) {
            checkCancelled(cancelled); double squares = 0;
            for (int s = firstSample + w * 320; s < firstSample + (w + 1) * 320; s++) {
                float value = pcm[s];
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Audio contains invalid samples.");
                squares += value * value; if (Math.abs(value) >= .98f) clipped++;
            }
            rms[w] = Math.sqrt(squares / 320);
        }
        double[] sorted = rms.clone(); Arrays.sort(sorted);
        double floor = sorted[Math.min(windows - 1, windows / 5)];
        double peak = sorted[windows - 1]; double threshold = Math.max(.004, Math.max(floor * 3.5, peak * .08));
        double clippedFraction = clipped / (windows * 320.0);
        if (peak < .008) return unchanged(uri, inMs, outMs, durationMs, "Audio is silent or too faint for a confident trim.",
                floor, peak, threshold, clippedFraction, -1, -1, asrStart, asrEnd, 0);
        if (clippedFraction > .01 || peak < floor * 4)
            return unchanged(uri, inMs, outMs, durationMs, "Clipping or continuous background energy makes the boundaries uncertain.",
                    floor, peak, threshold, clippedFraction, -1, -1, asrStart, asrEnd, 0);
        long supportedStart = -1, supportedEnd = -1, energyStart = -1, energyEnd = -1;
        int supported = 0, allActive = 0, sustained = 0, run = 0, allRun = 0;
        for (int w = 0; w < windows; w++) {
            checkCancelled(cancelled);
            long start = offsetMs + (firstSample + w * 320) / 16;
            long end = Math.min(outMs, start + 20);
            boolean active = rms[w] >= threshold;
            if (active) {
                allActive++; allRun++;
                // An energetic word missed by ASR must stay. Ignore only isolated runs
                // shorter than 60 ms; every sustained run sets the outer keep boundaries.
                if (allRun >= 3) {
                    if (energyStart < 0) energyStart = start - (allRun - 1) * 20;
                    energyEnd = end;
                }
            } else allRun = 0;
            boolean overlaps = false;
            if (active) for (SubtitleCue cue : cues) {
                if (cue != null && cue.text != null && !cue.text.trim().isEmpty() && cue.startMs >= 0
                        && cue.endMs > cue.startMs && cue.endMs <= durationMs
                        && start < cue.endMs && end > cue.startMs) { overlaps = true; break; }
            }
            if (overlaps) {
                if (supportedStart < 0) supportedStart = start; supportedEnd = end; supported++; run++;
                if (run >= 3) sustained++;
            } else run = 0;
        }
        long activeMs = supported * 20L;
        if (supported < 12 || sustained < 4 || supportedEnd - supportedStart < 500 || supported < allActive * .65)
            return unchanged(uri, inMs, outMs, durationMs, "Too little sustained energy agrees with the spoken-text timing.",
                    floor, peak, threshold, clippedFraction, energyStart, energyEnd, asrStart, asrEnd, activeMs);
        long proposedIn = Math.max(inMs, energyStart - 300), proposedOut = Math.min(outMs, energyEnd + 300);
        // Avoid proposing tiny changes, and never move either edge beyond the current review range.
        if (proposedIn - inMs < 400) proposedIn = inMs;
        if (outMs - proposedOut < 400) proposedOut = outMs;
        if (proposedOut - proposedIn < 1000 || (proposedIn == inMs && proposedOut == outMs))
            return unchanged(uri, inMs, outMs, durationMs, "The current range already fits the measured speech with padding.",
                    floor, peak, threshold, clippedFraction, energyStart, energyEnd, asrStart, asrEnd, activeMs);
        checkCancelled(cancelled);
        double agreement = supported / (double) Math.max(1, allActive);
        double confidence = Math.min(.90, .60 + .20 * agreement + .10 * Math.min(1, activeMs / 2000.0));
        return new SpeechTrim(uri, inMs, outMs, durationMs, proposedIn, proposedOut, true, confidence,
                "Review the quieter outer edges; measured speech agrees with English draft timing. Includes 300 ms padding.",
                floor, peak, threshold, clippedFraction, energyStart, energyEnd, asrStart, asrEnd, activeMs);
    }

    private static SpeechTrim unchanged(Uri uri, long in, long out, long duration, String reason, double floor,
            double peak, double threshold, double clipped, long start, long end, long asrStart, long asrEnd, long activeMs) {
        return new SpeechTrim(uri, in, out, duration, in, out, false, 0, reason, floor, peak, threshold,
                clipped, start, end, asrStart, asrEnd, activeMs);
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("Cancelled.");
    }
}
