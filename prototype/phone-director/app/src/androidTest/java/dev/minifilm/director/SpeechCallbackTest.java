package dev.minifilm.director;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Silent framework callback checks: synthesizeToFile never plays a cue or opens a microphone.
 * Reflection installs the completion gate without calling speakThen (which would play audio).
 * This verifies SpeechCoach's real listener/gate dispatch, not playback timing, audibility,
 * its speakThen enqueue path, timeout behavior, Bluetooth routing or camera coordination.
 */
@RunWith(AndroidJUnit4.class)
public final class SpeechCallbackTest {
    private static final String CUE = "Relax your shoulders. Look at the lens. Hold your pose.";

    @Test public void realOfflineSynthesisCompletesOnlyTheMatchingGateOnMainThread() throws Exception {
        SpeechCoach coach = openReadyCoach();
        File unrelated = output("speech-gate-unrelated.wav");
        File matched = output("speech-gate-matched.wav");
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicBoolean completedOnMain = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        try {
            TextToSpeech engine = engine(coach);
            assertOfflineEnglish(engine);
            main(() -> installGate(coach, "silent-gate-match", () -> {
                completedOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                completions.incrementAndGet(); done.countDown();
            }, () -> { failures.incrementAndGet(); done.countDown(); }));
            long began = SystemClock.elapsedRealtime();
            // Keep the matching synthesis unqueued while testing the unrelated ID.
            assertEquals(TextToSpeech.SUCCESS, engine.synthesizeToFile(CUE, new Bundle(), unrelated, "silent-gate-unrelated"));
            awaitFileAndDispatch(unrelated);
            assertEquals("Unrelated completion must leave the gate pending", 0, completions.get());
            assertEquals(0, failures.get());
            main(() -> assertEquals("silent-gate-match", field(coach, "activeUtterance")));
            assertEquals(TextToSpeech.SUCCESS, engine.synthesizeToFile(CUE, new Bundle(), matched, "silent-gate-match"));
            assertTrue("Matching framework completion must arrive", done.await(30, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals("Unrelated IDs must not fire the gate", 1, completions.get());
            assertEquals(0, failures.get());
            assertTrue("Completion must be marshalled to the UI thread", completedOnMain.get());
            assertTrue("Unrelated cue must actually synthesize", unrelated.length() > 44);
            assertTrue("Matched cue must actually synthesize", matched.length() > 44);
            assertCleared(coach);
            Log.i("MiniFilmSpeechCallbackTest", "silent_matching_gate pass=true main_thread=true unrelated_bytes=" + unrelated.length()
                    + " matched_bytes=" + matched.length() + " elapsed_ms=" + (SystemClock.elapsedRealtime() - began));
        } finally {
            main(coach::close);
            unrelated.delete(); matched.delete();
        }
    }

    @Test public void stopClearsGateAndRealStaleCompletionCannotFinishTheReplacement() throws Exception {
        SpeechCoach coach = openReadyCoach();
        File stale = output("speech-gate-stale.wav");
        File replacement = output("speech-gate-replacement.wav");
        AtomicInteger staleCallbacks = new AtomicInteger();
        AtomicInteger replacementCompletions = new AtomicInteger();
        AtomicInteger replacementFailures = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        try {
            TextToSpeech engine = engine(coach);
            assertOfflineEnglish(engine);
            main(() -> {
                installGate(coach, "silent-gate-stopped", staleCallbacks::incrementAndGet, staleCallbacks::incrementAndGet);
                coach.stop();
            });
            assertCleared(coach);
            // A late completion with the stopped ID is delivered by the real engine.
            // The following replacement is a queue barrier, so its completion follows it.
            main(() -> installGate(coach, "silent-gate-replacement", () -> {
                replacementCompletions.incrementAndGet(); done.countDown();
            }, () -> { replacementFailures.incrementAndGet(); done.countDown(); }));
            long began = SystemClock.elapsedRealtime();
            assertEquals(TextToSpeech.SUCCESS, engine.synthesizeToFile(CUE, new Bundle(), stale, "silent-gate-stopped"));
            awaitFileAndDispatch(stale);
            assertEquals(0, staleCallbacks.get());
            assertEquals("Stale completion must leave replacement pending", 0, replacementCompletions.get());
            assertEquals(0, replacementFailures.get());
            main(() -> assertEquals("silent-gate-replacement", field(coach, "activeUtterance")));
            assertEquals(TextToSpeech.SUCCESS, engine.synthesizeToFile(CUE, new Bundle(), replacement, "silent-gate-replacement"));
            assertTrue("Replacement framework completion must arrive", done.await(30, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals("Stopped callback must remain cancelled", 0, staleCallbacks.get());
            assertEquals("Stale completion must not finish replacement gate", 1, replacementCompletions.get());
            assertEquals(0, replacementFailures.get());
            assertTrue("Stopped-ID cue must actually synthesize", stale.length() > 44);
            assertTrue("Replacement cue must actually synthesize", replacement.length() > 44);
            assertCleared(coach);
            Log.i("MiniFilmSpeechCallbackTest", "silent_stopped_gate pass=true stale_callbacks=0 replacement_callbacks=1 stale_bytes="
                    + stale.length() + " replacement_bytes=" + replacement.length() + " elapsed_ms=" + (SystemClock.elapsedRealtime() - began));
        } finally {
            main(coach::close);
            stale.delete(); replacement.delete();
        }
    }

    private static SpeechCoach openReadyCoach() throws Exception {
        AtomicReference<SpeechCoach> created = new AtomicReference<>();
        main(() -> created.set(new SpeechCoach(context())));
        SpeechCoach coach = created.get();
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < deadline) {
            main(() -> ready.set(coach.isOfflineVoiceReady()));
            if (ready.get()) return coach;
            SystemClock.sleep(50);
        }
        main(coach::close);
        fail("An installed offline English voice must initialize within thirty seconds");
        return null;
    }

    private static void assertOfflineEnglish(TextToSpeech engine) {
        Voice voice = engine.getVoice();
        assertNotNull("SpeechCoach must select an installed voice", voice);
        assertFalse("Silent check must use a nonnetwork voice", voice.isNetworkConnectionRequired());
        assertEquals("en", voice.getLocale().getLanguage());
    }

    private static void installGate(SpeechCoach coach, String id, Runnable completed, Runnable failed) {
        assertEquals(Looper.getMainLooper(), Looper.myLooper());
        field(coach, "activeUtterance", id);
        field(coach, "afterSpeech", completed);
        field(coach, "speechFailed", failed);
    }

    private static void assertCleared(SpeechCoach coach) {
        main(() -> {
            assertNull(field(coach, "activeUtterance"));
            assertNull(field(coach, "afterSpeech"));
            assertNull(field(coach, "speechFailed"));
            assertNull(field(coach, "speechTimeout"));
        });
    }

    private static TextToSpeech engine(SpeechCoach coach) { return (TextToSpeech) field(coach, "tts"); }

    private static Object field(SpeechCoach coach, String name) {
        try {
            Field value = SpeechCoach.class.getDeclaredField(name); value.setAccessible(true);
            return value.get(coach);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void field(SpeechCoach coach, String name, Object value) {
        try {
            Field member = SpeechCoach.class.getDeclaredField(name); member.setAccessible(true); member.set(coach, value);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void awaitFileAndDispatch(File file) {
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        long lastSize = -1, stableSince = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() < deadline) {
            long size = file.length();
            if (size != lastSize) { lastSize = size; stableSince = SystemClock.elapsedRealtime(); }
            if (size > 44 && SystemClock.elapsedRealtime() - stableSince >= 1000) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
            SystemClock.sleep(50);
        }
        fail("Silent synthesis must produce a stable nonempty local file within thirty seconds");
    }

    private static File output(String name) {
        File file = new File(context().getCacheDir(), name);
        if (file.exists()) assertTrue("Remove any stale test output", file.delete());
        return file;
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void main(Runnable work) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { work.run(); } catch (Throwable caught) { failure.set(caught); }
        });
        if (failure.get() != null) throw new AssertionError("Main-thread test operation failed", failure.get());
    }
}
