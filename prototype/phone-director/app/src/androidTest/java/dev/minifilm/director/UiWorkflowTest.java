package dev.minifilm.director;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.KeyguardManager;
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
import androidx.camera.view.PreviewView;
import androidx.camera.core.CameraSelector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before UI checks; screen layout/lifecycle cannot be verified behind its lock.", keyguard != null && keyguard.isKeyguardLocked());
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

    @Test(timeout = 30_000) public void verticalViewfinderAndBackLensPersistWithoutOpeningCamera() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> click(activity, "02  Direct"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                PreviewView preview = find(activity.getWindow().getDecorView(), PreviewView.class, null);
                assertNotNull(preview);
                assertEquals("Visible preview must share the vertical reel aspect", 9f / 16f,
                        preview.getWidth() / (float) preview.getHeight(), .005f);
                click(activity, "Switch front / back");
                assertEquals(CameraSelector.LENS_FACING_BACK, ((Integer) field(activity, "desiredLens")).intValue());
                assertNoCapture(activity);
                click(activity, "Next shot →");
                assertEquals(CameraSelector.LENS_FACING_BACK, ((Integer) field(activity, "desiredLens")).intValue());
                assertNoCapture(activity);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Chosen lens must survive screen/controller recreation", CameraSelector.LENS_FACING_BACK,
                        ((Integer) field(activity, "desiredLens")).intValue());
                assertNoCapture(activity);
            });
        }
    }

    @Test(timeout = 30_000) public void processingDisablesEditableInputsAndReenablesThemAfterCancellation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                android.widget.EditText brief = find(activity.getWindow().getDecorView(), android.widget.EditText.class, null);
                android.widget.Spinner style = find(activity.getWindow().getDecorView(), android.widget.Spinner.class, null);
                assertNotNull(brief); assertNotNull(style); assertTrue(brief.isEnabled()); assertTrue(style.isEnabled());
                try { Method method = activity.getClass().getDeclaredMethod("setBusy", boolean.class);
                    method.setAccessible(true); method.invoke(activity, true);
                } catch (Exception failure) { throw new AssertionError(failure); }
                assertFalse("A late typed brief must not replace the snapshot being planned", brief.isEnabled());
                assertFalse("Scene style must remain fixed for a pending plan", style.isEnabled());
                click(activity, "Cancel local processing");
                assertTrue(find(activity.getWindow().getDecorView(), android.widget.EditText.class, null).isEnabled());
                assertTrue(find(activity.getWindow().getDecorView(), android.widget.Spinner.class, null).isEnabled());
                assertNoCapture(activity);
            });
        }
    }

    @Test(timeout = 30_000) public void objectShotsKeepPersonFramingAdviceOffWithoutOpeningCapture() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                setField(activity, "style", "Product reveal");
                click(activity, "02  Direct");
                assertEquals("Person-framing advice is off for this object shot. Follow your scene cue.",
                        ((TextView) field(activity, "framingView")).getText().toString());
                assertEquals(false, invoke(activity, "isPoseShot"));
                assertNoCapture(activity);
                setField(activity, "style", "Talking head");
                @SuppressWarnings("unchecked") java.util.ArrayList<Shot> shots =
                        (java.util.ArrayList<Shot>) field(activity, "shots");
                shots.clear(); shots.add(new Shot("synthetic-cutaway", "Cutaway", "Show a story detail", "", 4000));
                setField(activity, "shotIndex", 0);
                invoke(activity, "render");
                assertEquals(false, invoke(activity, "isPoseShot"));
                assertNoCapture(activity);
            });
        }
    }

    @Test(timeout = 30_000) public void readyPackageSurvivesRecreationAndBackgroundSaveDefersPicker() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File zip = File.createTempFile("minifilm-pack-", ".zip", context.getCacheDir());
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("README.txt"));
            output.write("Synthetic ready package lifecycle fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        IntentFilter pickerFilter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
        pickerFilter.addCategory(Intent.CATEGORY_OPENABLE);
        Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(pickerFilter, null, true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> { setField(activity, "pendingPack", zip); invoke(activity, "save"); });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Ready cache ZIP must survive activity replacement", zip, field(activity, "pendingPack"));
                assertTrue(zip.isFile());
                click(activity, "03  Assemble");
                assertNotNull(find(activity.getWindow().getDecorView(), Button.class, "Save ready edit package"));
                assertNoCapture(activity);
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(activity -> invoke(activity, "requestPackDocument"));
            instrumentation.waitForIdleSync();
            assertEquals("Background completion must not open a document picker", 0, picker.getHits());
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                click(activity, "Save ready edit package");
                assertNoCapture(activity);
            });
            instrumentation.waitForIdleSync();
            assertEquals("Explicit resumed save should dispatch the normal Files intent", 1, picker.getHits());
        } finally { instrumentation.removeMonitor(picker); zip.delete(); }
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
    private static void setField(Object owner, String name, Object value) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
        catch (Exception failure) { throw new AssertionError("Unable to set synthetic UI state", failure); }
    }
    private static Object invoke(Object owner, String name) {
        try { Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
        catch (Exception failure) { throw new AssertionError("Unable to exercise UI action: " + name, failure); }
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
