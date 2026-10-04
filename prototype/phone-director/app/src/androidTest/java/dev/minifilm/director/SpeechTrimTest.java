package dev.minifilm.director;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** PCM fixtures test only the deterministic rule; known padded TTS exercises real decode + ASR. */
@RunWith(AndroidJUnit4.class)
public final class SpeechTrimTest {
    private static final Uri FIXTURE = Uri.parse("file:///synthetic/pcm-test.mp4");
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void supportedEdgesIncludePaddingAndPreserveInputs() {
        float[] pcm = pcm(6000, 1200, 4400); int hash = Arrays.hashCode(pcm);
        List<SubtitleCue> cues = Collections.singletonList(new SubtitleCue(1000, 4700, "Synthetic timing fixture."));
        SpeechTrim trim = analyze(pcm, 0, 6000, 0, 6000, cues);
        assertTrue(trim.hasSuggestion); assertEquals(900, trim.suggestedInMs); assertEquals(4700, trim.suggestedOutMs);
        assertEquals(1200, trim.energyStartMs); assertEquals(4400, trim.energyEndMs);
        assertTrue(trim.confidence >= .6 && trim.confidence <= .9); assertTrue(trim.reviewRequired);
        assertTrue(trim.thresholdRms >= .004); assertEquals(hash, Arrays.hashCode(pcm));
        assertEquals(1000, cues.get(0).startMs); assertEquals(4700, cues.get(0).endMs);
        Take take = new Take(FIXTURE, "test", "Fixture", "", 6000);
        assertTrue(trim.matches(take)); take.inMs = 10; assertFalse(trim.matches(take));
    }

    @Test public void silenceAbsentTextStationaryNoiseAndClippingAbstain() {
        List<SubtitleCue> cues = Collections.singletonList(new SubtitleCue(0, 6000, "A test cue is not proof of speech."));
        unchanged(analyze(new float[96_000], 0, 6000, 0, 6000, cues), 0, 6000);
        unchanged(analyze(pcm(6000, 1200, 4400), 0, 6000, 0, 6000, Collections.emptyList()), 0, 6000);
        // Stationary energy with no quieter reference must not become a silence detector.
        unchanged(analyze(pcm(6000, 0, 6000), 0, 6000, 0, 6000, cues), 0, 6000);
        float[] noise = new float[96_000]; java.util.Random random = new java.util.Random(71);
        for (int s = 0; s < noise.length; s++) noise[s] = (random.nextFloat() * 2 - 1) * .04f;
        unchanged(analyze(noise, 0, 6000, 0, 6000, cues), 0, 6000);
        float[] clipped = new float[96_000]; Arrays.fill(clipped, 16_000, 80_000, 1f);
        unchanged(analyze(clipped, 0, 6000, 0, 6000, cues), 0, 6000);
    }

    @Test public void weakOrUnsupportedEnergyAndMinimumDurationAbstain() {
        unchanged(analyze(pcm(6000, 2000, 2180), 0, 6000, 0, 6000,
                Collections.singletonList(new SubtitleCue(1800, 2500, "Short impulse."))), 0, 6000);
        unchanged(analyze(pcm(6000, 1000, 4500), 0, 6000, 0, 6000,
                Collections.singletonList(new SubtitleCue(3000, 3400, "Only a small part agrees."))), 0, 6000);
        unchanged(analyze(pcm(800, 100, 700), 0, 800, 0, 800,
                Collections.singletonList(new SubtitleCue(100, 700, "Too short."))), 0, 800);
        unchanged(analyze(pcm(6000, 200, 5800), 0, 6000, 0, 6000,
                Collections.singletonList(new SubtitleCue(0, 6000, "Already fits."))), 0, 6000);
    }

    @Test public void decodedOffsetAndCurrentRangeArePreserved() {
        List<SubtitleCue> cues = Collections.singletonList(new SubtitleCue(1300, 4700, "Offset fixture."));
        SpeechTrim trim = analyze(pcm(6000, 1000, 4000), 500, 6500, 500, 5500, cues);
        assertTrue(trim.hasSuggestion); assertEquals(1500, trim.energyStartMs); assertEquals(4500, trim.energyEndMs);
        assertEquals(1200, trim.suggestedInMs); assertEquals(4800, trim.suggestedOutMs);
        assertTrue(trim.suggestedInMs >= trim.currentInMs); assertTrue(trim.suggestedOutMs <= trim.currentOutMs);
        unchanged(analyze(pcm(6000, 1200, 4400), 0, 6000, 0, 6000,
                Collections.singletonList(new SubtitleCue(1000, 6001, "Invalid container timestamp."))), 0, 6000);
        try {
            analyze(new float[16_000], 0, 1000, 0, 1001, cues); fail("Invalid range must be rejected");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("range")); }
    }

    @Test public void energeticPrefixMissingFromAsrIsRetained() {
        float[] samples = pcm(8000, 1000, 2000); addEnergy(samples, 3000, 6000);
        SpeechTrim trim = analyze(samples, 0, 8000, 0, 8000,
                Collections.singletonList(new SubtitleCue(3000, 6000, "Recognized middle only.")));
        assertTrue(trim.hasSuggestion); assertEquals(700, trim.suggestedInMs); assertEquals(6300, trim.suggestedOutMs);
        assertEquals(1000, trim.energyStartMs); assertEquals(6000, trim.energyEndMs);
        assertTrue("Missed energetic opening must survive", trim.suggestedInMs <= 1000);
        // If sustained energy reaches both outer edges, there is no quiet-edge suggestion.
        samples = pcm(8000, 0, 1000); addEnergy(samples, 3000, 7800);
        unchanged(analyze(samples, 0, 8000, 0, 8000,
                Collections.singletonList(new SubtitleCue(3000, 7800, "Recognized later only."))), 0, 8000);
    }

    @Test public void energeticTailMissingFromAsrIsRetained() {
        float[] samples = pcm(8000, 1000, 4000); addEnergy(samples, 5000, 6000);
        SpeechTrim trim = analyze(samples, 0, 8000, 0, 8000,
                Collections.singletonList(new SubtitleCue(1000, 4000, "Recognized opening only.")));
        assertTrue(trim.hasSuggestion); assertEquals(700, trim.suggestedInMs); assertEquals(6300, trim.suggestedOutMs);
        assertEquals(1000, trim.energyStartMs); assertEquals(6000, trim.energyEndMs);
        assertTrue("Missed energetic ending must survive", trim.suggestedOutMs >= 6000);
    }

    @Test public void queuedPreflightErrorIsSuppressedWhenRequestIsCancelled() throws Exception {
        ClipTranscriber transcriber = new ClipTranscriber(context); CountDownLatch barrier = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger(); Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            transcriber.analyzeForTrim(FIXTURE, -1, 1000, new ClipTranscriber.TrimListener() {
                @Override public void onComplete(SpeechTrim trim, long ms) { callbacks.incrementAndGet(); }
                @Override public void onError(String message) { callbacks.incrementAndGet(); }
            });
            transcriber.close(); main.post(barrier::countDown);
        });
        assertTrue(barrier.await(5, TimeUnit.SECONDS)); assertEquals("Cancelled error must not update a new operation", 0, callbacks.get());
    }

    @Test public void cancellationStopsPureAnalysisAndClosedApiDoesNotStartWork() throws Exception {
        AtomicInteger polls = new AtomicInteger();
        try {
            SpeechTrim.analyze(FIXTURE, pcm(6000, 1200, 4400), 0, 6000, 0, 6000,
                    Collections.singletonList(new SubtitleCue(1000, 4700, "Fixture.")), () -> polls.incrementAndGet() >= 12);
            fail("Cancelled analysis must not return a suggestion");
        } catch (CancellationException expected) { assertEquals(12, polls.get()); }
        ClipTranscriber transcriber = new ClipTranscriber(context); transcriber.close();
        CountDownLatch done = new CountDownLatch(1); AtomicReference<String> error = new AtomicReference<>();
        transcriber.analyzeForTrim(FIXTURE, 0, 1000, new ClipTranscriber.TrimListener() {
            @Override public void onComplete(SpeechTrim trim, long elapsedMs) { error.set("Unexpected completion"); done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertEquals("Transcriber is closed.", error.get());
    }

    @Test(timeout = 90_000) public void actualPaddedSyntheticSpeechSuggestsReviewedOuterEdgesOnly() throws Exception {
        File source = new File(context.getFilesDir(), "fixtures/jacket-speech-padded.mp4");
        assertTrue("Known offline TTS fixture with 1.5 second silent edges is required", source.isFile());
        String original = sha(source); long duration;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, Uri.fromFile(source));
            duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally { metadata.release(); }
        assertTrue("Known padded fixture duration " + duration, duration >= 8546 && duration <= 8946);
        ClipTranscriber transcriber = new ClipTranscriber(context);
        try {
            assertTrue(transcriber.isModelAvailable()); CountDownLatch done = new CountDownLatch(1);
            AtomicReference<SpeechTrim> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
            AtomicReference<Long> elapsed = new AtomicReference<>();
            new Handler(Looper.getMainLooper()).post(() -> transcriber.analyzeForTrim(Uri.fromFile(source), 0, duration,
                    new ClipTranscriber.TrimListener() {
                        @Override public void onComplete(SpeechTrim trim, long ms) { result.set(trim); elapsed.set(ms); done.countDown(); }
                        @Override public void onError(String message) { error.set(message); done.countDown(); }
                    }));
            assertTrue("Trim request timed out", done.await(75, TimeUnit.SECONDS)); assertNull(error.get());
            SpeechTrim trim = result.get(); assertNotNull(trim); assertTrue(trim.reason, trim.hasSuggestion);
            assertTrue("Leave padding before known speech", trim.suggestedInMs >= 900 && trim.suggestedInMs <= 1700);
            assertTrue("Leave padding after known speech", trim.suggestedOutMs >= 6900 && trim.suggestedOutMs <= 8000);
            assertTrue(trim.suggestedOutMs <= duration); assertTrue(trim.energyStartMs < trim.asrEndMs);
            assertTrue(trim.energyEndMs > trim.asrStartMs); assertTrue(trim.reviewRequired);
            assertEquals(original, sha(source));
            Log.i("MiniFilmSpeechTrimTest", "SPEECH_TRIM_OK backend=localCPU sourceDurationMs=" + duration
                    + " suggestedInMs=" + trim.suggestedInMs + " suggestedOutMs=" + trim.suggestedOutMs
                    + " energyStartMs=" + trim.energyStartMs + " energyEndMs=" + trim.energyEndMs
                    + " activeEnergyMs=" + trim.activeEnergyMs + " heuristicConfidence=" + trim.confidence
                    + " elapsedMs=" + elapsed.get() + " originalUnchanged=true reviewRequired=true");
        } finally { transcriber.close(); }
    }

    private static SpeechTrim analyze(float[] samples, long offset, long duration, long in, long out, List<SubtitleCue> cues) {
        return SpeechTrim.analyze(FIXTURE, samples, offset, duration, in, out, cues, () -> false);
    }
    private static void unchanged(SpeechTrim trim, long in, long out) {
        assertFalse(trim.hasSuggestion); assertEquals(in, trim.suggestedInMs); assertEquals(out, trim.suggestedOutMs);
        assertEquals(0, trim.confidence, 0); assertTrue(trim.reviewRequired); assertFalse(trim.reason.isEmpty());
    }
    private static float[] pcm(int durationMs, int fromMs, int toMs) {
        float[] samples = new float[durationMs * 16];
        addEnergy(samples, fromMs, toMs);
        return samples;
    }
    private static void addEnergy(float[] samples, int fromMs, int toMs) {
        for (int s = fromMs * 16; s < toMs * 16; s++) samples[s] = (float) (.10 * Math.sin(2 * Math.PI * 400 * s / 16_000));
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[8192]; for (int n; (n = input.read(bytes)) >= 0;) if (n > 0) digest.update(bytes, 0, n);
        }
        StringBuilder text = new StringBuilder(); for (byte value : digest.digest()) text.append(String.format(java.util.Locale.US, "%02x", value & 255));
        return text.toString();
    }
}
