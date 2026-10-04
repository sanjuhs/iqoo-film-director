package dev.minifilm.director;

import static org.junit.Assert.*;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Error selection and posted-status lifetime only. No model load or claimed inference. */
@RunWith(AndroidJUnit4.class)
public final class PlannerFeedbackTest {
    @Test public void knownFailuresExplainModelTokenMemoryAndBusyConditionsWithoutPrivateText() throws Exception {
        assertTrue(error(new TimeoutException("private-uri-secret")).contains("still stopping"));
        assertTrue(error(new UnsatisfiedLinkError("private-path-secret")).contains("runtime"));
        String[][] known = {
                {"Brief is too long for the local planner.", "token budget"},
                {"This model build requires CPU dot-product and FP16 support. Use the editable template.", "CPU"},
                {"Local model could not load. Use the editable template.", "load the local model"},
                {"Local model context could not initialize. Use the editable template.", "allocate"},
                {"Local planning timed out or was cancelled. Use the editable template.", "before finishing"}};
        for (String[] pair : known) assertTrue(error(new IllegalStateException(pair[0])).contains(pair[1]));
        for (Throwable unknown : new Throwable[]{new IllegalStateException("file:///private/transcript-secret"),
                new IllegalArgumentException("private-brief-secret"), new RuntimeException("private-model-secret")}) {
            String message = error(unknown);
            assertTrue(message.contains("current plan is unchanged"));
            assertFalse(message.contains("secret"));assertFalse(message.contains("file:"));
        }
    }

    @Test public void queuedProgressUsesMainThreadAndRejectsOldGenerationAndClosedOwner() throws Exception {
        LocalPlanner planner = new LocalPlanner(InstrumentationRegistry.getInstrumentation().getTargetContext());
        AtomicLong generation = (AtomicLong) field(planner, "requestGeneration");
        List<String> delivered = new ArrayList<>();
        LocalPlanner.Listener listener = new LocalPlanner.Listener() {
            public void onProgress(String stage) { assertSame(Looper.getMainLooper(), Looper.myLooper());delivered.add(stage); }
            public void onPlan(List<Shot> shots,long ms,String label) { fail("No generation requested"); }
            public void onError(String error) { fail("No generation requested"); }
        };
        Method progress = LocalPlanner.class.getDeclaredMethod("postProgress",long.class,LocalPlanner.Listener.class,String.class);
        progress.setAccessible(true);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try {
                    generation.set(1);progress.invoke(planner,1L,listener,"old status");
                    generation.set(2);progress.invoke(planner,2L,listener,"current status");
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(java.util.Collections.singletonList("current status"),delivered);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                try { progress.invoke(planner,2L,listener,"closed status");planner.close(); }
                catch (Exception failure) { throw new AssertionError(failure); }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(java.util.Collections.singletonList("current status"),delivered);
            assertTrue(((ExecutorService)field(planner,"worker")).awaitTermination(2,TimeUnit.SECONDS));
            assertEquals(0L,field(planner,"handle"));
        } finally { planner.close(); }
    }

    @Test public void optionalProgressKeepsExistingListenersCompatibleAndClosedStateActionable() throws Exception {
        LocalPlanner planner = new LocalPlanner(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            new LocalPlanner.Listener() {
                public void onPlan(List<Shot> shots,long elapsed,String label) { }
                public void onError(String message) { }
            }.onProgress("optional stage");
            planner.close();
            assertTrue(planner.unavailableReason().contains("closed"));
            assertTrue(((ExecutorService)field(planner,"worker")).awaitTermination(2,TimeUnit.SECONDS));
            assertEquals(0L,field(planner,"handle"));
        } finally { planner.close(); }
    }

    private static String error(Throwable failure) throws Exception {
        Method method=LocalPlanner.class.getDeclaredMethod("errorMessage",Throwable.class);method.setAccessible(true);
        return (String)method.invoke(null,failure);
    }
    private static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
}
