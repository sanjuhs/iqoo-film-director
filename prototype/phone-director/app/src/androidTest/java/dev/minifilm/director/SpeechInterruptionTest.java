package dev.minifilm.director;

import static org.junit.Assert.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Inaudible state-transition checks. Gates are installed by reflection; focus is synthetic.
 * No granted-focus test calls speakThen, no test calls TTS.speak, and no microphone opens.
 * Directly invoking the protected noisy receiver tests its handler, not Android broadcast
 * delivery, actual playback interruption, earbud disconnection or Bluetooth routing.
 */
@RunWith(AndroidJUnit4.class)
public final class SpeechInterruptionTest {
    @Test public void noisyFailsCueOnceClearsPendingAndNotifiesPreparationAfterFailure() {
        FakeFocus focus = new FakeFocus();
        SpeechCoach coach = create(focus);
        List<String> callbacks = new ArrayList<>();
        try {
            main(() -> {
                assertEquals(true, field(coach, "noisyReceiverRegistered"));
                coach.setAudioInterruptionListener(() -> {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper()); callbacks.add("interrupted");
                });
                gate(coach, "silent-noisy", () -> callbacks.add("completed"), () -> {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper()); callbacks.add("failed");
                });
                assertTrue(request(coach, "silent-noisy"));
                field(coach, "pendingSpeech", "A synthetic pending cue that must never play.");
                receiver(coach).onReceive(context(), new Intent("dev.minifilm.director.UNRELATED"));
                assertEquals("silent-noisy", field(coach, "activeUtterance"));
                noisy(coach);
                assertEquals(List.of("failed", "interrupted"), callbacks);
                cleared(coach);
                assertNull(field(coach, "pendingSpeech"));
                assertEquals(1, focus.abandoned.size());
                assertSame(focus.requests.get(0), focus.abandoned.get(0));
                finish(coach, "silent-noisy", true); // Late TTS completion cannot advance preparation.
                focus.listeners.get(0).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
                assertEquals(List.of("failed", "interrupted"), callbacks);
            });
        } finally { main(coach::close); }
    }

    @Test public void noisyBetweenCuesStillPausesPreparationWithoutPlayingAudio() {
        FakeFocus focus = new FakeFocus();
        SpeechCoach coach = create(focus);
        AtomicInteger interrupted = new AtomicInteger();
        try {
            main(() -> {
                coach.setAudioInterruptionListener(interrupted::incrementAndGet);
                assertNull(field(coach, "activeUtterance"));
                noisy(coach);
                assertEquals(1, interrupted.get());
                assertTrue("Idle noisy handling must not acquire focus", focus.requests.isEmpty());
                cleared(coach);
                coach.close();
                assertEquals(false, field(coach, "noisyReceiverRegistered"));
                assertNull(field(coach, "audioInterruptionListener"));
                noisy(coach); // Even an already-dispatched receiver callback is harmless after close.
                assertEquals(1, interrupted.get());
                coach.close();
            });
        } finally { main(coach::close); }
    }

    @Test public void allFocusLossKindsFailOnMainAndGainNeverResumes() {
        FakeFocus focus = new FakeFocus();
        SpeechCoach coach = create(focus);
        AtomicInteger completed = new AtomicInteger(), failed = new AtomicInteger(), interruptions = new AtomicInteger();
        AtomicBoolean onMain = new AtomicBoolean(true);
        try {
            int[] losses = {AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK};
            for (int i = 0; i < losses.length; i++) {
                String id = "silent-loss-" + i;
                main(() -> {
                    coach.setAudioInterruptionListener(interruptions::incrementAndGet);
                    gate(coach, id, completed::incrementAndGet, () -> {
                        onMain.set(onMain.get() && Looper.myLooper() == Looper.getMainLooper()); failed.incrementAndGet();
                    });
                    assertTrue(request(coach, id));
                });
                // The instrumentation thread is not main: the real listener must marshal delivery.
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper());
                focus.listeners.get(i).onAudioFocusChange(losses[i]);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                int expected = i + 1;
                main(() -> {
                    assertEquals(expected, failed.get()); assertEquals(expected, interruptions.get());
                    assertEquals(expected, focus.abandoned.size()); cleared(coach);
                });
                focus.listeners.get(i).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            }
            assertEquals(0, completed.get()); assertEquals(3, failed.get()); assertTrue(onMain.get());
        } finally { main(coach::close); }
    }

    @Test public void delayedOldFocusLossCannotFailReplacementOrNotifyItsPreparation() {
        FakeFocus focus = new FakeFocus();
        SpeechCoach coach = create(focus);
        AtomicInteger oldCallbacks = new AtomicInteger(), newFailures = new AtomicInteger(), interruptions = new AtomicInteger();
        try {
            main(() -> {
                coach.setAudioInterruptionListener(interruptions::incrementAndGet);
                gate(coach, "silent-old", oldCallbacks::incrementAndGet, oldCallbacks::incrementAndGet);
                assertTrue(request(coach, "silent-old")); coach.stop();
                gate(coach, "silent-new", oldCallbacks::incrementAndGet, newFailures::incrementAndGet);
                assertTrue(request(coach, "silent-new"));
            });
            focus.listeners.get(0).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            main(() -> {
                assertEquals("silent-new", field(coach, "activeUtterance"));
                assertEquals(0, oldCallbacks.get()); assertEquals(0, newFailures.get()); assertEquals(0, interruptions.get());
                assertEquals(1, focus.abandoned.size());
                focus.listeners.get(1).onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
                assertEquals(1, newFailures.get()); assertEquals(1, interruptions.get());
                assertEquals(2, focus.abandoned.size()); cleared(coach);
            });
        } finally { main(coach::close); }
    }

    @Test public void completionAbandonsBeforeCallbackCanPrepareAnotherCueAndStopIsSilent() {
        FakeFocus focus = new FakeFocus();
        SpeechCoach coach = create(focus);
        AtomicInteger completed = new AtomicInteger(), failed = new AtomicInteger();
        try {
            main(() -> {
                gate(coach, "silent-completed", () -> {
                    assertEquals(1, focus.abandoned.size());
                    assertNull(field(coach, "activeFocus")); completed.incrementAndGet();
                    gate(coach, "silent-next", completed::incrementAndGet, failed::incrementAndGet);
                    assertTrue(request(coach, "silent-next"));
                }, failed::incrementAndGet);
                assertTrue(request(coach, "silent-completed"));
                finish(coach, "silent-completed", true);
                finish(coach, "silent-completed", false);
                assertEquals("silent-next", field(coach, "activeUtterance"));
                coach.stop();
                assertEquals(1, completed.get()); assertEquals(0, failed.get());
                assertEquals(2, focus.abandoned.size()); cleared(coach);
            });
        } finally { main(coach::close); }
    }

    @Test public void deniedOrDelayedFocusFailsSpeakThenBeforeAnyPlayback() {
        for (int result : new int[] {AudioManager.AUDIOFOCUS_REQUEST_FAILED, AudioManager.AUDIOFOCUS_REQUEST_DELAYED}) {
            FakeFocus focus = new FakeFocus(); focus.result = result;
            SpeechCoach coach = create(focus);
            AtomicInteger completed = new AtomicInteger(), failed = new AtomicInteger();
            try {
                main(() -> {
                    field(coach, "ready", true); // Focus refusal is tested independently of installed voice readiness.
                    assertFalse(coach.speakThen("Silent synthetic cue; focus must refuse it.", completed::incrementAndGet,
                            () -> { assertEquals(Looper.getMainLooper(), Looper.myLooper()); failed.incrementAndGet(); }));
                    assertNull("No playback timeout should be armed", field(coach, "speechTimeout"));
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                main(() -> {
                    assertEquals(0, completed.get()); assertEquals(1, failed.get()); cleared(coach);
                    assertEquals(1, focus.requests.size());
                    assertEquals(result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED ? 1 : 0, focus.abandoned.size());
                    focus.listeners.get(0).onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
                    assertEquals(1, failed.get()); cleared(coach);
                });
            } finally { main(coach::close); }
        }
    }

    @Test public void synchronousLossDuringFocusRequestCannotLeakJustGrantedFocus() {
        FakeFocus focus = new FakeFocus(); focus.loseDuringRequest = true;
        SpeechCoach coach = create(focus);
        AtomicInteger failed = new AtomicInteger();
        try {
            main(() -> {
                gate(coach, "silent-reentrant", () -> fail("Interrupted cue must not complete"), failed::incrementAndGet);
                assertFalse(request(coach, "silent-reentrant"));
                assertEquals(1, failed.get()); assertEquals(1, focus.abandoned.size()); cleared(coach);
                coach.stop(); coach.close();
                assertEquals(1, focus.abandoned.size()); assertEquals(1, failed.get());
            });
        } finally { main(coach::close); }
    }

    private static final class FakeFocus implements SpeechCoach.FocusControl {
        final List<AudioFocusRequest> requests = new ArrayList<>(), abandoned = new ArrayList<>();
        final List<AudioManager.OnAudioFocusChangeListener> listeners = new ArrayList<>();
        int result = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        boolean loseDuringRequest;
        @Override public int request(AudioFocusRequest request, AudioManager.OnAudioFocusChangeListener listener) {
            assertEquals(Looper.getMainLooper(), Looper.myLooper());
            assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, request.getFocusGain());
            assertTrue(request.willPauseWhenDucked()); assertFalse(request.acceptsDelayedFocusGain());
            assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, request.getAudioAttributes().getContentType());
            assertEquals(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, request.getAudioAttributes().getUsage());
            requests.add(request); listeners.add(listener);
            if (loseDuringRequest) listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
            return result;
        }
        @Override public void abandon(AudioFocusRequest request) { abandoned.add(request); }
    }

    private static SpeechCoach create(FakeFocus focus) {
        AtomicReference<SpeechCoach> value = new AtomicReference<>();
        main(() -> value.set(new SpeechCoach(context(), focus))); return value.get();
    }
    private static void gate(SpeechCoach coach, String id, Runnable completed, Runnable failed) {
        assertEquals(Looper.getMainLooper(), Looper.myLooper());
        field(coach, "activeUtterance", id); field(coach, "afterSpeech", completed); field(coach, "speechFailed", failed);
    }
    private static void cleared(SpeechCoach coach) {
        for (String name : new String[] {"activeUtterance", "afterSpeech", "speechFailed", "speechTimeout", "activeFocus"})
            assertNull(name + " must clear", field(coach, name));
    }
    private static boolean request(SpeechCoach coach, String id) { return (Boolean) invoke(coach, "requestSpeechFocus", new Class<?>[] {String.class}, id); }
    private static void finish(SpeechCoach coach, String id, boolean success) { invoke(coach, "finishSpeech", new Class<?>[] {String.class, boolean.class}, id, success); }
    private static BroadcastReceiver receiver(SpeechCoach coach) { return (BroadcastReceiver) field(coach, "noisyReceiver"); }
    private static void noisy(SpeechCoach coach) { receiver(coach).onReceive(context(), new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); }
    private static Object invoke(SpeechCoach coach, String name, Class<?>[] types, Object... args) {
        try { Method method = SpeechCoach.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(coach, args); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object field(SpeechCoach coach, String name) {
        try { Field field = SpeechCoach.class.getDeclaredField(name); field.setAccessible(true); return field.get(coach); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void field(SpeechCoach coach, String name, Object value) {
        try { Field field = SpeechCoach.class.getDeclaredField(name); field.setAccessible(true); field.set(coach, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void main(Runnable work) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { work.run(); } catch (Throwable caught) { failure.set(caught); }
        });
        if (failure.get() != null) throw new AssertionError("Main-thread operation failed", failure.get());
    }
}
