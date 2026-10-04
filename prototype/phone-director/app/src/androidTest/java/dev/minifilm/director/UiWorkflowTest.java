package dev.minifilm.director;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

/** Exercises real screens using synthetic content, without camera/mic permission grants. */
@RunWith(AndroidJUnit4.class)
public final class UiWorkflowTest {
    private SharedPreferences preferences;
    private Map<String, ?> originalPreferences;

    @Before public void preserveShootState() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences("shoot", 0);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before UI checks; screen layout/lifecycle cannot be verified behind its lock.", keyguard != null && keyguard.isKeyguardLocked());
        assertDenied(context);
        originalPreferences = new HashMap<>(preferences.getAll());
        assertTrue(preferences.edit().clear().putString("state", "{}").commit());
    }
    @After public void restoreShootState() {
        if (originalPreferences != null) {
            SharedPreferences.Editor editor = preferences.edit().clear();
            for (Map.Entry<String, ?> entry : originalPreferences.entrySet()) {
                String key = entry.getKey(); Object value = entry.getValue();
                if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof Set) {
                    @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
                    editor.putStringSet(key, new HashSet<>(strings));
                } else throw new AssertionError("Unexpected preference value type");
            }
            assertTrue(editor.commit()); assertEquals(originalPreferences, preferences.getAll());
        }
        assertDenied(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static void assertDenied(Context context) {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
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
                assertEquals("Reopening Direct preserves the screen without restarting capture", 1, field(activity, "tab"));
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
                SpeechCoach speech = (SpeechCoach) field(activity, "speech");
                setField(speech, "listening", true); // Synthetic pending input: no recognizer/microphone.
                try { Method method = activity.getClass().getDeclaredMethod("setBusy", boolean.class);
                    method.setAccessible(true); method.invoke(activity, true);
                } catch (Exception failure) { throw new AssertionError(failure); }
                assertFalse("A late typed brief must not replace the snapshot being planned", brief.isEnabled());
                assertFalse("Scene style must remain fixed for a pending plan", style.isEnabled());
                assertFalse("Local processing must cancel pending spoken input", speech.isListening());
                click(activity, "Cancel local processing");
                assertTrue(find(activity.getWindow().getDecorView(), android.widget.EditText.class, null).isEnabled());
                assertTrue(find(activity.getWindow().getDecorView(), android.widget.Spinner.class, null).isEnabled());
                assertNoCapture(activity);
            });
        }
    }

    @Test(timeout = 30_000) public void typingAndExplicitVoiceStopPreserveTheBriefAgainstSyntheticLateResults() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SpeechCoach speech = (SpeechCoach) field(activity, "speech");
                android.widget.EditText brief = (android.widget.EditText) field(activity, "briefField");
                Button stop = find(activity.getWindow().getDecorView(), Button.class, "Stop voice input");
                assertNotNull("A dedicated cancellation control must exist", stop); assertFalse(stop.isEnabled());
                java.util.concurrent.atomic.AtomicInteger lateCalls = new java.util.concurrent.atomic.AtomicInteger();
                android.speech.RecognitionListener typedStale = syntheticRecognition(speech, lateCalls);
                invoke(activity, "updateVoiceInputControls"); assertTrue(stop.isEnabled());
                brief.setText("My manually reviewed synthetic jacket brief");
                assertFalse(speech.isListening()); assertFalse(stop.isEnabled());
                android.os.Bundle stale = new android.os.Bundle();
                stale.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,
                        new java.util.ArrayList<>(java.util.Arrays.asList("Unwanted late synthetic speech")));
                typedStale.onResults(stale); assertEquals(0, lateCalls.get());
                assertEquals("My manually reviewed synthetic jacket brief", brief.getText().toString());
                android.speech.RecognitionListener stoppedStale = syntheticRecognition(speech, lateCalls);
                invoke(activity, "updateVoiceInputControls"); assertTrue(stop.isEnabled());
                stop.performClick(); stoppedStale.onResults(stale); stoppedStale.onError(7);
                assertEquals(0, lateCalls.get()); assertFalse(speech.isListening()); assertFalse(stop.isEnabled());
                assertEquals("My manually reviewed synthetic jacket brief", field(activity, "brief"));
                assertNoCapture(activity);
            });
        }
    }

    private static android.speech.RecognitionListener syntheticRecognition(SpeechCoach speech,
            java.util.concurrent.atomic.AtomicInteger callbacks) {
        setField(speech, "listening", true);
        SpeechCoach.Listener creator = new SpeechCoach.Listener() {
            public void onText(String text) { callbacks.incrementAndGet(); }
            public void onError(String error) { callbacks.incrementAndGet(); }
        };
        try {
            Method factory = SpeechCoach.class.getDeclaredMethod("createRecognitionListener", SpeechCoach.Listener.class, int.class);
            factory.setAccessible(true);
            return (android.speech.RecognitionListener) factory.invoke(speech, creator, (Integer) field(speech, "recognitionSession"));
        } catch (Exception failure) { throw new AssertionError("Unable to install synthetic pending input", failure); }
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
        // Match the production ZIP MIME type; an untyped filter misses this intent.
        pickerFilter.addDataType("application/zip");
        Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(pickerFilter, null, true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> { click(activity, "03  Assemble"); setField(activity, "pendingPack", zip); setField(activity, "pendingPackSnapshot", invoke(activity, "packSnapshot")); invoke(activity, "save"); });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Ready cache ZIP must survive activity replacement", zip, field(activity, "pendingPack"));
                assertTrue(zip.isFile());
                assertEquals("A ready package reopens on Assemble without another navigation action", 2, field(activity, "tab"));
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

    @Test(timeout = 30_000) public void startingAnotherReelDeselectsOldTakesAndInvalidatesEarlierPreparedPackage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File original = File.createTempFile("selection-original-", ".bin", context.getCacheDir());
        File zip = File.createTempFile("minifilm-pack-", ".zip", context.getCacheDir());
        byte[] bytes = "Synthetic selection fixture; no camera or microphone".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(original)) { out.write(bytes); }
        try (FileOutputStream out = new FileOutputStream(zip)) { out.write(bytes); }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                @SuppressWarnings("unchecked") java.util.ArrayList<Take> takes = (java.util.ArrayList<Take>) field(activity, "takes");
                takes.clear();
                for (int i = 0; i < 10; i++) takes.add(new Take(android.net.Uri.fromFile(original), "shot-" + i,
                        (i < 5 ? "Earlier shoot " : "Next shoot ") + i, "Synthetic", 4000));
                setField(activity, "pendingPack", zip);
                setField(activity, "pendingPackSnapshot", invoke(activity, "packSnapshot"));
                invoke(activity, "save");
                click(activity, "03  Assemble");
                assertEquals(zip, field(activity, "pendingPack"));
                click(activity, "Select no takes");
                assertEquals(10, takes.size());
                for (Take take : takes) { assertFalse(take.selected); assertEquals(android.net.Uri.fromFile(original), take.uri); assertEquals(4000, take.durationMs); }
                assertNull("Earlier package must not be offered as the changed edit", field(activity, "pendingPack"));
                assertFalse("Only the reproducible generated package is invalidated", zip.exists());
                assertEquals("Whole source bytes remain intact", bytes.length, original.length());
                assertNotNull(find(activity.getWindow().getDecorView(), Button.class, "Save clips + edits for my laptop"));
                // The creator can choose only the next shoot after the reversible reset.
                for (int i = 5; i < 10; i++) {
                    CheckBox box = find(activity.getWindow().getDecorView(), CheckBox.class, "Next shoot " + i);
                    assertNotNull(box); box.setChecked(true);
                }
                for (int i = 0; i < 10; i++) assertEquals(i >= 5, takes.get(i).selected);
                assertNoCapture(activity);
            });
            assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(original.toPath()));
        } finally { original.delete(); zip.delete(); }
    }

    @Test(timeout = 30_000) public void audioInterruptionCancelsPreparationAndLeavesExplicitResumeRequired() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                click(activity, "02  Direct");
                setField(activity, "countdown", true);
                setField(activity, "sequenceActive", true);
                int oldGeneration = (Integer) field(activity, "countdownGeneration");
                // Synthetic preparation state: no capture controller, microphone or audible cue.
                invoke(activity, "pauseSpokenPreparation");
                assertTrue(booleanField(activity, "spokenPreparationPaused"));
                assertFalse(booleanField(activity, "sequenceActive"));
                assertFalse(booleanField(activity, "countdown"));
                assertTrue((Integer) field(activity, "countdownGeneration") > oldGeneration);
                assertTrue(((TextView) field(activity, "status")).getText().toString().contains("Check your earbuds"));
                invoke(activity, "render");
                assertTrue("Rendering must not resume speech", booleanField(activity, "spokenPreparationPaused"));
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
                assertNull("Secondary tools belong to the selected take's menu", find(decor, Button.class, "Trim & typography"));
                assertNull(find(decor, Button.class, "Generate offline subtitles"));
                assertNotNull(find(decor, Button.class, "Export my reel"));
                CheckBox synthetic = find(decor, CheckBox.class, "THE HOOK · synthetic demo");
                Button tools = find((View) synthetic.getParent(), Button.class, "Edit & review take");
                assertNotNull(tools); assertTrue(tools.performClick());
                AlertDialog menu = (AlertDialog) field(activity, "takeToolsDialog");
                assertNotNull(menu); assertTrue(menu.isShowing());
                assertNotNull(find(menu.getWindow().getDecorView(), Button.class, "Trim & typography"));
                assertNotNull(find(menu.getWindow().getDecorView(), Button.class, "Generate offline subtitles"));
                assertNoCapture(activity);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                AlertDialog menu = (AlertDialog) field(activity, "takeToolsDialog");
                assertNotNull(menu); assertTrue(menu.isShowing());
                assertNotNull(menu.getButton(AlertDialog.BUTTON_NEGATIVE));
                assertTrue(menu.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> { assertNull(field(activity, "takeToolsDialog")); assertNoCapture(activity); });
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
