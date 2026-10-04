package dev.minifilm.director;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Native lifetime ordering on synthetic inputs; no camera, microphone, playback or network. */
@RunWith(AndroidJUnit4.class)
public final class ModelLeaseTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test(timeout = 20_000) public void cancelledWaiterDoesNotStealOrReleaseCurrentOwner() throws Exception {
        LocalModelLease.Token holder = LocalModelLease.acquire("test-owner", () -> false, 5000);
        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch waiting = new CountDownLatch(1), ended = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean entered = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            try (LocalModelLease.Token ignored = LocalModelLease.acquire("test-waiter", cancelled::get, 5000)) {
                entered.set(true);
            } catch (Throwable error) { failure.set(error); }
            finally { ended.countDown(); }
        });
        try {
            waiter.start();
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            cancelled.set(true);
            assertTrue("Cancelled lease wait must observe its flag within bounded polling", ended.await(2, TimeUnit.SECONDS));
            assertFalse(entered.get());
            assertTrue(failure.get() instanceof CancellationException);
            assertTrue("Cancelling a waiter cannot release another owner's native-lifetime permit", LocalModelLease.isHeld());
            assertEquals("test-owner", LocalModelLease.ownerForDiagnostics());
        } finally { cancelled.set(true); holder.close(); waiter.join(2000); }
        holder.close(); // Idempotent: never creates a second semaphore permit.
        try (LocalModelLease.Token successor = LocalModelLease.acquire("test-successor", () -> false, 1000)) {
            assertEquals("test-successor", LocalModelLease.ownerForDiagnostics());
            assertTrue(LocalModelLease.isHeld());
        }
        assertFalse(LocalModelLease.isHeld());
    }

    @Test(timeout = 60_000) public void nativeCancellationBeforeGenerationRemainsTerminalForThatHandle() throws Exception {
        LocalPlanner planner = new LocalPlanner(context()); // Initializes the JNI library, but loads no model.
        assertTrue(planner.isModelAvailable());
        planner.close();
        Method load = method("nativeLoad", String.class);
        Method cancel = method("nativeCancel", long.class);
        Method generate = method("nativeGenerate", long.class, byte[].class, byte[].class, int.class);
        Method free = method("nativeFree", long.class);
        long handle = 0;
        try (LocalModelLease.Token lease = LocalModelLease.acquire("test-pre-cancel", () -> false, 5000)) {
            try {
                handle = (Long) load.invoke(null, new File(context().getFilesDir(), "director-model.gguf").getAbsolutePath());
                assertNotEquals(0, handle);
                cancel.invoke(null, handle);
                byte[] prompt = "<|im_start|>user\nReturn ok.\n<|im_end|>\n<|im_start|>assistant\n".getBytes(StandardCharsets.UTF_8);
                byte[] grammar = "root ::= \"ok\" ws\nws ::= [ \\t\\n\\r]*\n".getBytes(StandardCharsets.UTF_8);
                long began = SystemClock.elapsedRealtime();
                try {
                    generate.invoke(null, handle, prompt, grammar, 8);
                    fail("Pre-cancelled native handle must not generate a successful response");
                } catch (InvocationTargetException expected) {
                    assertTrue(expected.getCause() instanceof IllegalStateException);
                }
                assertTrue("Pre-cancelled generation must abort promptly", SystemClock.elapsedRealtime() - began < 5000);
                assertEquals("test-pre-cancel", LocalModelLease.ownerForDiagnostics());
                Log.i("MiniFilmModelLeaseTest", "native_pre_cancel pass=true terminal=true synthetic_input=true");
            } finally { if (handle != 0) free.invoke(null, handle); }
        }
        assertFalse(LocalModelLease.isHeld());
    }

    @Test(timeout = 280_000) public void closedPlannerFreesItsActualCoreBeforeVisionAcquiresModelLifetime() throws Exception {
        LocalPlanner planner = new LocalPlanner(context());
        VisionReference vision = new VisionReference(context());
        assertTrue(planner.isModelAvailable());
        assertTrue(vision.isModelAvailable());
        AtomicInteger oldCallbacks = new AtomicInteger();
        CountDownLatch ended = new CountDownLatch(1);
        AtomicReference<String> observations = new AtomicReference<>(), error = new AtomicReference<>();
        Bitmap frame = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        frame.eraseColor(Color.BLACK);
        try {
            planner.generate("I am wearing a jacket. Direct five small performer actions.", "Fashion", new LocalPlanner.Listener() {
                @Override public void onPlan(List<Shot> shots, long elapsed, String label) { oldCallbacks.incrementAndGet(); }
                @Override public void onError(String message) { oldCallbacks.incrementAndGet(); }
            });
            long deadline = SystemClock.elapsedRealtime() + 30_000;
            while (plannerHandle(planner) == 0 && SystemClock.elapsedRealtime() < deadline) Thread.sleep(20);
            assertNotEquals("The test must observe a real loaded native text core before cancellation", 0, plannerHandle(planner));
            assertEquals("planner", LocalModelLease.ownerForDiagnostics());
            planner.close(); // Resource teardown is asynchronous on the text worker.
            vision.observe(frame, new VisionReference.Listener() {
                @Override public void onObservation(String text, long elapsed, String label) {
                    observations.set(text); ended.countDown();
                }
                @Override public void onError(String message) { error.set(message); ended.countDown(); }
            });
            frame.recycle();
            boolean sawVisionLease = false;
            deadline = SystemClock.elapsedRealtime() + 215_000;
            while (ended.getCount() != 0 && SystemClock.elapsedRealtime() < deadline) {
                if (LocalModelLease.ownerForDiagnostics().equals("vision")) {
                    sawVisionLease = true;
                    assertEquals("Vision cannot own/load a core until the old text handle has been freed", 0, plannerHandle(planner));
                }
                ended.await(20, TimeUnit.MILLISECONDS);
            }
            assertEquals("Actual local vision must complete after the native lifetime handoff", 0, ended.getCount());
            assertNull("Vision must not silently fall back after cancellation handoff: " + error.get(), error.get());
            assertNotNull(observations.get());
            assertTrue(sawVisionLease);
            assertEquals("Closed planner cannot deliver stale results", 0, oldCallbacks.get());
            assertFalse("Vision frees native state and releases the lifetime lease before callback", LocalModelLease.isHeld());
            Log.i("MiniFilmModelLeaseTest", "native_lifetime_handoff pass=true text_loaded_then_closed=true"
                    + " vision_after_text_free=true callbacks_after_text_close=0 synthetic_black=true");
        } finally {
            if (!frame.isRecycled()) frame.recycle();
            planner.close(); vision.close();
        }
    }
    private Method method(String name, Class<?>... types) throws Exception {
        Method method = LocalPlanner.class.getDeclaredMethod(name, types); method.setAccessible(true); return method;
    }
    private long plannerHandle(LocalPlanner planner) throws Exception {
        Field field = LocalPlanner.class.getDeclaredField("handle"); field.setAccessible(true); return field.getLong(planner);
    }
}
