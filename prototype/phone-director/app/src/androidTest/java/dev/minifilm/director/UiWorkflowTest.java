package dev.minifilm.director;

import android.content.Context;
import android.content.SharedPreferences;
import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Exercises real screens using synthetic content, without camera/mic permission grants. */
@RunWith(AndroidJUnit4.class)
public final class UiWorkflowTest {
    private SharedPreferences preferences;
    private String originalState;

    @Before public void preserveShootState() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("shoot", 0);
        originalState = preferences.getString("state", null);
        assertTrue(preferences.edit().putString("state", "{}").commit());
    }
    @After public void restoreShootState() {
        SharedPreferences.Editor editor = preferences.edit();
        if (originalState == null) editor.remove("state"); else editor.putString("state", originalState);
        assertTrue(editor.commit());
    }

    @Test(timeout = 30_000) public void launchResumeAndEnabledSequenceNeverOpenCaptureAutomatically() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNoCapture(activity);
                click(activity, "02  Direct");
                Switch sequence = find(activity.getWindow().getDecorView(), Switch.class,
                        "Guide the full shot sequence");
                assertNotNull(sequence); sequence.setChecked(true);
                assertFalse(booleanField(activity, "sequenceActive"));
                assertNoCapture(activity);
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(UiWorkflowTest::assertNoCapture);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNoCapture(activity);
                click(activity, "02  Direct");
                Switch sequence = find(activity.getWindow().getDecorView(), Switch.class,
                        "Guide the full shot sequence");
                assertTrue("The option can persist without restarting recording", sequence.isChecked());
                assertNoCapture(activity);
            });
        }
    }

    @Test(timeout = 30_000) public void cameraStartAndStopRemainVisibleWhileNavigationDoesNotActivateCapture() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> click(activity, "02  Direct"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                ScrollView scroll = find(activity.getWindow().getDecorView(), ScrollView.class, null);
                assertNotNull(scroll); scroll.fullScroll(View.FOCUS_DOWN);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                Button record = find(activity.getWindow().getDecorView(), Button.class, "Start camera");
                Button stop = find(activity.getWindow().getDecorView(), Button.class, "Stop take");
                assertNotNull(record); assertNotNull(stop);
                assertVisible(record); assertVisible(stop);
                // Leave Start camera untouched: no permission dialog, preview or microphone.
                assertNoCapture(activity);
                stop.performClick();
                assertNoCapture(activity);
                click(activity, "03  Assemble");
                assertNotNull(find(activity.getWindow().getDecorView(), Button.class, "Export my reel"));
                assertNoCapture(activity);
            });
        }
    }

    @Test(timeout = 90_000) public void syntheticDemoReachesEditableAssemblyWithoutActivatingShoot() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> click(activity, "Try a synthetic demo"));
            AtomicBoolean ready = new AtomicBoolean(false);
            long deadline = SystemClock.elapsedRealtime() + 60_000;
            while (!ready.get() && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> ready.set(
                        find(activity.getWindow().getDecorView(), CheckBox.class,
                                "THE HOOK · synthetic demo") != null));
                if (!ready.get()) SystemClock.sleep(100);
            }
            assertTrue("Synthetic demo should reach Assemble", ready.get());
            scenario.onActivity(activity -> {
                View decor = activity.getWindow().getDecorView();
                assertNotNull(find(decor, CheckBox.class, "THE HOOK · synthetic demo"));
                assertNotNull(find(decor, CheckBox.class, "THE DETAIL · synthetic demo"));
                assertNotNull(find(decor, CheckBox.class, "THE REVEAL · synthetic demo"));
                assertNotNull(find(decor, Button.class, "Trim & typography"));
                assertNotNull(find(decor, Button.class, "Generate offline subtitles"));
                assertNotNull(find(decor, Button.class, "Export my reel"));
                assertNoCapture(activity);
            });
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(PreviewActivity.class.getName(), null, false);
            try {
                scenario.onActivity(activity -> {
                    CheckBox synthetic = find(activity.getWindow().getDecorView(), CheckBox.class,
                            "THE HOOK · synthetic demo");
                    Button preview = find((View) synthetic.getParent(), Button.class, "Preview take");
                    assertNotNull(preview); preview.performClick();
                });
                Activity playback = instrumentation.waitForMonitorWithTimeout(monitor, 10_000);
                assertTrue("Synthetic clip must open the app's internal player", playback instanceof PreviewActivity);
                AtomicBoolean prepared = new AtomicBoolean(false);
                long playerDeadline = SystemClock.elapsedRealtime() + 10_000;
                while (!prepared.get() && SystemClock.elapsedRealtime() < playerDeadline) {
                    instrumentation.runOnMainSync(() -> {
                        PlayerView view = find(playback.getWindow().getDecorView(), PlayerView.class, null);
                        assertNotNull(view); assertNotNull(view.getPlayer());
                        assertFalse("Preview must not autoplay sound", view.getPlayer().getPlayWhenReady());
                        prepared.set(view.getPlayer().getPlaybackState() == Player.STATE_READY);
                    });
                    if (!prepared.get()) SystemClock.sleep(100);
                }
                assertTrue("Synthetic local clip must actually prepare for playback", prepared.get());
                instrumentation.runOnMainSync(playback::finish);
                instrumentation.waitForIdleSync();
                scenario.onActivity(UiWorkflowTest::assertNoCapture);
            } finally { instrumentation.removeMonitor(monitor); }
        }
    }

    private static void click(MainActivity activity, String text) {
        Button button = find(activity.getWindow().getDecorView(), Button.class, text);
        assertNotNull("Missing control: " + text, button); assertTrue(button.performClick());
    }
    private static void assertNoCapture(MainActivity activity) {
        assertEquals("These checks must not grant camera permission", PackageManager.PERMISSION_DENIED,
                activity.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals("These checks must not grant microphone permission", PackageManager.PERMISSION_DENIED,
                activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        assertNull("No camera controller should be created by these actions", field(activity, "capture"));
        assertFalse("Shoot session must remain off", booleanField(activity, "session"));
        assertFalse("Capture must not be marked live", booleanField(activity, "live"));
        assertFalse("No recording countdown should be running", booleanField(activity, "countdown"));
        assertFalse("No automatic shot sequence should be running", booleanField(activity, "sequenceActive"));
    }
    private static boolean booleanField(Object owner, String name) { return (Boolean) field(owner, name); }
    private static Object field(Object owner, String name) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
        catch (Exception failure) { throw new AssertionError("Unable to inspect capture state", failure); }
    }
    private static void assertVisible(View view) {
        Rect visible = new Rect(); assertTrue("Persistent control is outside the visible screen", view.getGlobalVisibleRect(visible));
        assertTrue(visible.width() > 0 && visible.height() >= view.getHeight() - 2);
    }
    private static <T extends View> T find(View root, Class<T> type, String text) {
        if (type.isInstance(root) && (text == null || root instanceof TextView
                && ((TextView) root).getText().toString().equals(text))) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type, text); if (found != null) return found;
            }
        }
        return null;
    }
}
