package dev.minifilm.director;

import static org.junit.Assert.*;

import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Synthetic terminal callbacks and cleanup only: never creates/starts a recognizer or plays TTS. */
@RunWith(AndroidJUnit4.class)
public final class SpeechRecognitionLifecycleTest {
    @Test public void resultReleasesSessionBeforeCreatorCallbackAndIgnoresDuplicateTerminalEvents() {
        SpeechCoach coach = create(); FakeCleanup cleanup = new FakeCleanup();
        AtomicInteger texts = new AtomicInteger(), errors = new AtomicInteger();
        try { main(() -> {
            RecognitionListener callback = install(coach, cleanup, new SpeechCoach.Listener() {
                public void onText(String text) {
                    assertEquals("Synthetic jacket brief", text); detached(coach, cleanup);
                    assertEquals(Looper.getMainLooper(), Looper.myLooper()); texts.incrementAndGet();
                }
                public void onError(String error) { errors.incrementAndGet(); }
            });
            int session = (Integer) field(coach, "recognitionSession");
            callback.onResults(words("Synthetic jacket brief"));
            assertTrue((Integer) field(coach, "recognitionSession") > session);
            callback.onResults(words("Must not overwrite the reviewed brief")); callback.onError(7);
            assertEquals(1, texts.get()); assertEquals(0, errors.get()); detached(coach, cleanup);
        }); } finally { main(coach::close); }
    }

    @Test public void terminalErrorStillDestroysWhenCancellationThrowsAndLateResultCannotApply() {
        SpeechCoach coach = create(); FakeCleanup cleanup = new FakeCleanup(); cleanup.throwCancel = true;
        AtomicInteger texts = new AtomicInteger(), errors = new AtomicInteger();
        try { main(() -> {
            RecognitionListener callback = install(coach, cleanup, new SpeechCoach.Listener() {
                public void onText(String text) { texts.incrementAndGet(); }
                public void onError(String error) { detached(coach, cleanup); errors.incrementAndGet(); }
            });
            callback.onError(SpeechRecognizer.ERROR_NO_MATCH);
            callback.onResults(words("Stale result after terminal error"));
            assertEquals(0, texts.get()); assertEquals(1, errors.get()); detached(coach, cleanup);
        }); } finally { main(coach::close); }
    }

    @Test public void emptyNullOrBlankResultsFailOnceAfterCleanup() {
        SpeechCoach coach = create(); AtomicInteger errors = new AtomicInteger();
        try { main(() -> {
            Bundle[] invalid = {null, new Bundle(), words(""), words("   "), words((String) null)};
            for (Bundle result : invalid) {
                FakeCleanup cleanup = new FakeCleanup();
                RecognitionListener callback = install(coach, cleanup, new SpeechCoach.Listener() {
                    public void onText(String text) { fail("Invalid result must not replace the brief"); }
                    public void onError(String error) { detached(coach, cleanup); errors.incrementAndGet(); }
                });
                callback.onResults(result); callback.onError(SpeechRecognizer.ERROR_NO_MATCH);
            }
            assertEquals(invalid.length, errors.get());
        }); } finally { main(coach::close); }
    }

    @Test public void stopAndThrowingDestroyInvalidateOldSessionWithoutAffectingReplacement() {
        SpeechCoach coach = create(); FakeCleanup old = new FakeCleanup(); old.throwDestroy = true;
        FakeCleanup next = new FakeCleanup(); AtomicInteger oldCalls = new AtomicInteger(), newCalls = new AtomicInteger();
        try { main(() -> {
            RecognitionListener stale = install(coach, old, counter(oldCalls));
            coach.stopListening(); detached(coach, old);
            RecognitionListener current = install(coach, next, counter(newCalls));
            stale.onResults(words("Old speech after manual editing")); stale.onError(7);
            assertEquals(0, oldCalls.get()); assertTrue(coach.isListening()); assertSame(next, field(coach, "recognitionCleanup"));
            current.onResults(words("Current synthetic brief"));
            assertEquals(1, newCalls.get()); detached(coach, next);
        }); } finally { main(coach::close); }
    }

    @Test public void reentrantCleanupAndCreatorReplacementCannotReviveOldSession() {
        SpeechCoach coach = create(); FakeCleanup old = new FakeCleanup(), next = new FakeCleanup();
        AtomicInteger results = new AtomicInteger(), errors = new AtomicInteger(), nextCalls = new AtomicInteger();
        AtomicReference<RecognitionListener> current = new AtomicReference<>();
        try { main(() -> {
            RecognitionListener callback = install(coach, old, new SpeechCoach.Listener() {
                public void onText(String text) {
                    detached(coach, old); results.incrementAndGet(); current.set(install(coach, next, counter(nextCalls)));
                }
                public void onError(String error) { errors.incrementAndGet(); }
            });
            old.duringCancel = () -> callback.onError(SpeechRecognizer.ERROR_CLIENT);
            callback.onResults(words("First synthetic brief"));
            callback.onResults(words("Late old result after replacement")); callback.onError(7);
            assertEquals(1, results.get()); assertEquals(0, errors.get()); assertTrue(coach.isListening());
            assertSame(next, field(coach, "recognitionCleanup"));
            current.get().onResults(words("Replacement synthetic brief")); assertEquals(1, nextCalls.get());
            detached(coach, next);
        }); } finally { main(coach::close); }
    }

    @Test public void closeReleasesOwnedCleanupAndSuppressesAlreadyQueuedTerminalCallback() {
        SpeechCoach coach = create(); FakeCleanup cleanup = new FakeCleanup(); AtomicInteger calls = new AtomicInteger();
        try { main(() -> {
            RecognitionListener callback = install(coach, cleanup, counter(calls));
            coach.close(); callback.onResults(words("Closed activity result")); callback.onError(7);
            assertEquals(0, calls.get()); detached(coach, cleanup); coach.close();
            assertEquals(1, cleanup.destroys);
        }); } finally { main(coach::close); }
    }

    private static final class FakeCleanup implements SpeechCoach.RecognitionCleanup {
        int cancels, destroys; boolean throwCancel, throwDestroy; Runnable duringCancel;
        public void cancel() { cancels++; if (duringCancel != null) duringCancel.run(); if (throwCancel) throw new IllegalStateException("Synthetic cancel failure"); }
        public void destroy() { destroys++; if (throwDestroy) throw new IllegalStateException("Synthetic destroy failure"); }
    }
    private static SpeechCoach.Listener counter(AtomicInteger count) {
        return new SpeechCoach.Listener() { public void onText(String text) { count.incrementAndGet(); } public void onError(String error) { count.incrementAndGet(); } };
    }
    private static RecognitionListener install(SpeechCoach coach, FakeCleanup cleanup, SpeechCoach.Listener listener) {
        field(coach, "listening", true); field(coach, "recognitionCleanup", cleanup);
        try { Method factory = SpeechCoach.class.getDeclaredMethod("createRecognitionListener", SpeechCoach.Listener.class, int.class);
            factory.setAccessible(true); return (RecognitionListener) factory.invoke(coach, listener, (Integer) field(coach, "recognitionSession"));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void detached(SpeechCoach coach, FakeCleanup cleanup) {
        assertFalse(coach.isListening()); assertNull(field(coach, "recognizer")); assertNull(field(coach, "recognitionCleanup"));
        assertEquals(1, cleanup.cancels); assertEquals(1, cleanup.destroys);
    }
    private static Bundle words(String text) { Bundle bundle = new Bundle(); bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, new ArrayList<>(Arrays.asList(text))); return bundle; }
    private static SpeechCoach create() {
        AtomicReference<SpeechCoach> value = new AtomicReference<>(); main(() -> value.set(new SpeechCoach(InstrumentationRegistry.getInstrumentation().getTargetContext()))); return value.get();
    }
    private static Object field(SpeechCoach coach, String name) {
        try { Field field = SpeechCoach.class.getDeclaredField(name); field.setAccessible(true); return field.get(coach); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void field(SpeechCoach coach, String name, Object value) {
        try { Field field = SpeechCoach.class.getDeclaredField(name); field.setAccessible(true); field.set(coach, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void main(Runnable work) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try { work.run(); } catch (Throwable caught) { error.set(caught); } });
        if (error.get() != null) throw new AssertionError("Main-thread operation failed", error.get());
    }
}
