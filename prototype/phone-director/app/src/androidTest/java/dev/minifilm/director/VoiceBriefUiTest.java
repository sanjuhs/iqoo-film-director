package dev.minifilm.director;

import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * Future unlocked-device UI acceptance using synthetic text and an injected fake recorder.
 * Never creates a microphone backend, runs ASR/native inference, plays cues, or grants permissions.
 */
@RunWith(AndroidJUnit4.class)
public final class VoiceBriefUiTest {
    private static final String TYPED = "My existing synthetic typed brief";
    private SharedPreferences preferences;
    private Map<String, ?> original;
    private boolean changedPreferences;

    @Before public void requireUnlockedDeniedCaptureAndPreserveShootPreferences() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before real dialog/lifecycle checks; do not run behind keyguard",
                keyguard != null && keyguard.isKeyguardLocked());
        assertDenied(context);
        preferences = context.getSharedPreferences("shoot", 0);
        original = new HashMap<>(preferences.getAll());
        changedPreferences = true;
        assertTrue(preferences.edit().clear().putString("state",
                "{\"brief\":\"" + TYPED + "\",\"voice\":false}").commit());
    }

    @After public void restoreShootPreferencesWithoutChangingPermissions() {
        if (changedPreferences) {
            SharedPreferences.Editor editor = preferences.edit().clear();
            for (Map.Entry<String, ?> entry : original.entrySet()) {
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
            assertTrue(editor.commit()); assertEquals(original, preferences.getAll());
        }
        assertDenied(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test(timeout = 30_000)
    public void fullFiveHundredCharacterDraftRequiresExplicitUseAndKeepsCorrectedTextExactly() {
        String fullDraft = textOfLength(500, "Original synthetic speech draft ", " final spoken words");
        String correction = textOfLength(500, "Creator-corrected synthetic brief ", " exact reviewed ending");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AlertDialog dialog = showReview(scenario, fullDraft);
            scenario.onActivity(activity -> {
                assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity);
                List<?> originalShots = new ArrayList<>((List<?>) field(activity, "shots"));
                Object originalSource = field(activity, "planSource");
                EditText words = words(dialog);
                assertEquals("Review must retain the complete draft", fullDraft, words.getText().toString());
                assertBrief(activity, TYPED); words.setText(correction); assertBrief(activity, TYPED);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertFalse(dialog.isShowing()); assertBrief(activity, correction);
                assertEquals("Applying reviewed words must preserve the current shots", originalShots, field(activity, "shots"));
                assertEquals(originalSource, field(activity, "planSource")); assertNoCaptureOrProcessing(activity);
            });
        }
    }

    @Test(timeout = 30_000)
    public void overlongAndEmptyDraftsAreRejectedWithoutTruncationAndKeepTypedPreservesExistingBrief() {
        String fullDraft = textOfLength(640, "Long synthetic draft ", " all final words remain visible");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AlertDialog dialog = showReview(scenario, fullDraft);
            scenario.onActivity(activity -> {
                EditText words = words(dialog);
                assertEquals(fullDraft, words.getText().toString()); assertNotNull(words.getError());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue(dialog.isShowing()); assertNotNull(words.getError());
                assertEquals("Overlong input must not silently become a shorter brief", fullDraft, words.getText().toString());
                assertBrief(activity, TYPED);
                words.setText("   "); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue(dialog.isShowing()); assertNotNull(words.getError()); assertBrief(activity, TYPED);
                words.setText("Corrected synthetic draft that I choose to discard");
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertFalse(dialog.isShowing()); assertNull(field(activity, "voiceBriefReview")); assertBrief(activity, TYPED);
            });
            AlertDialog corrected = showReview(scenario, fullDraft);
            scenario.onActivity(activity -> {
                String reviewed = "I corrected this long synthetic speech into a short fashion brief.";
                words(corrected).setText(reviewed); corrected.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertFalse(corrected.isShowing()); assertBrief(activity, reviewed); assertNoCaptureOrProcessing(activity);
            });
        }
    }

    @Test(timeout = 30_000)
    public void backgroundAndExplicitInvalidationDismissDraftAndPreventOldUseFromApplyingAfterResume() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AlertDialog old = showReview(scenario, "Synthetic draft pending when the app leaves foreground");
            AtomicReference<Button> oldUse = new AtomicReference<>();
            scenario.onActivity(activity -> { oldUse.set(old.getButton(AlertDialog.BUTTON_POSITIVE)); assertBrief(activity, TYPED); });
            scenario.moveToState(Lifecycle.State.CREATED); scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertFalse("Backgrounding must dismiss the review", old.isShowing()); assertNull(field(activity, "voiceBriefReview"));
                // The retained listener was installed before backgrounding, so this exercises
                // the real generation/foreground guard rather than AlertDialog's default click.
                oldUse.get().performClick(); assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity);
            });
            AlertDialog dialog = showReview(scenario, "Another synthetic pending voice draft");
            scenario.onActivity(activity -> {
                Button staleUse = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                invoke(activity, "cancelVoiceBrief", new Class<?>[0]);
                assertFalse(dialog.isShowing()); staleUse.performClick(); assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity);
            });
        }
    }

    @Test(timeout = 30_000)
    public void pausedCurrentCompletionReenablesEditingButStaleAndDestroyingCompletionsDoNot() {
        // This exercises the shared terminal helper, not encoder/ASR callback timing.
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.onActivity(activity -> {
                assertEquals(Lifecycle.State.STARTED, activity.getLifecycle().getCurrentState());
                int generation = (Integer) field(activity, "voiceBriefGeneration");
                invoke(activity, "setBusy", new Class<?>[] {boolean.class}, true);
                assertFalse(((EditText) field(activity, "briefField")).isEnabled());
                assertEquals(true, call(activity, "completeVoiceBriefIfCurrent", new Class<?>[] {int.class}, generation));
                assertEquals(false, field(activity, "busy"));
                assertTrue("Paused completion must not leave the editor disabled",
                        ((EditText) field(activity, "briefField")).isEnabled());
                assertNull("Paused terminal helper must not publish a draft dialog", field(activity, "voiceBriefReview"));
                assertBrief(activity, TYPED);

                setField(activity, "voiceBriefGeneration", generation + 1);
                invoke(activity, "setBusy", new Class<?>[] {boolean.class}, true);
                assertEquals(false, call(activity, "completeVoiceBriefIfCurrent", new Class<?>[] {int.class}, generation));
                assertEquals("Old completion must not clear the newer operation", true, field(activity, "busy"));
                assertFalse(((EditText) field(activity, "briefField")).isEnabled());
                assertEquals(true, call(activity, "completeVoiceBriefIfCurrent", new Class<?>[] {int.class}, generation + 1));
                assertEquals(false, field(activity, "busy"));

                invoke(activity, "setBusy", new Class<?>[] {boolean.class}, true);
                setField(activity, "destroying", true);
                try {
                    assertEquals(false, call(activity, "completeVoiceBriefIfCurrent", new Class<?>[] {int.class}, generation + 1));
                    assertEquals(true, field(activity, "busy"));
                } finally {
                    setField(activity, "destroying", false);
                    invoke(activity, "setBusy", new Class<?>[] {boolean.class}, false);
                }
                assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity);
            });
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> { assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity); });
        }
    }

    @Test(timeout = 30_000)
    public void activeAndRetainedFakeRecorderRefuseAllMicEntrypointsUntilReleaseRetrySucceeds() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FakeBriefBackend backend = new FakeBriefBackend();
                LocalBriefRecorder recorder = new LocalBriefRecorder(activity, silentRecorderListener(),
                        (file, error, limit) -> { backend.file = file; return backend; }, () -> true, 45_000);
                Object originalPlanner = field(activity, "planner");
                setField(activity, "briefRecorder", recorder);
                try {
                    assertTrue(recorder.start()); assertTrue(recorder.isRecording());
                    assertEquals(false, call(activity, "ensureVoiceRecorderReleased", new Class<?>[0]));
                    assertMicEntrypointsRefused(activity);
                    assertTrue("An active helper must be left for explicit finish/cancel", recorder.isRecording());
                    assertEquals(0, backend.stops); assertEquals(0, backend.releases);

                    backend.failRelease = true;
                    recorder.close();
                    assertFalse(recorder.isRecording()); assertTrue(recorder.hasUnreleasedResources());
                    assertTrue("Unreleased backend still owns its synthetic temp file", backend.file.isFile());
                    assertEquals(false, call(activity, "ensureVoiceRecorderReleased", new Class<?>[0]));
                    assertMicEntrypointsRefused(activity);
                    assertTrue(recorder.hasUnreleasedResources()); assertTrue(backend.file.isFile());
                    assertEquals("Refused retries must not construct another backend", 1, backend.starts);
                    assertSame("Refusal precedes planner release and permission request", originalPlanner, field(activity, "planner"));

                    backend.failRelease = false;
                    assertEquals("Already-closed retained helper can retry release", true,
                            call(activity, "ensureVoiceRecorderReleased", new Class<?>[0]));
                    assertFalse(recorder.hasUnreleasedResources()); assertFalse(backend.file.exists());
                    assertBrief(activity, TYPED);
                } finally {
                    backend.failRelease = false; recorder.close(); setField(activity, "briefRecorder", null);
                }
                assertNoCaptureOrProcessing(activity);
            });
        }
    }

    @Test(timeout = 30_000)
    public void plannerReleaseReplacesOnlyPlannerAndPreservesEditableShootWithoutLoadingModel() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Object oldPlanner = field(activity, "planner");
                List<?> shots = new ArrayList<>((List<?>) field(activity, "shots"));
                List<?> takes = new ArrayList<>((List<?>) field(activity, "takes"));
                Object source = field(activity, "planSource");
                assertFalse(LocalModelLease.isHeld());
                assertEquals(0L, ((Number) field(oldPlanner, "handle")).longValue());
                // The real record path is permission-gated; call its resource helper directly.
                invoke(activity, "releasePlannerForMedia", new Class<?>[0]);
                Object replacement = field(activity, "planner");
                assertNotSame(oldPlanner, replacement); assertEquals(true, field(oldPlanner, "closed"));
                assertEquals(false, field(replacement, "closed"));
                assertEquals(0L, ((Number) field(replacement, "handle")).longValue());
                assertFalse("Replacing an idle planner must not acquire native model ownership", LocalModelLease.isHeld());
                assertEquals(shots, field(activity, "shots")); assertEquals(takes, field(activity, "takes"));
                assertEquals(source, field(activity, "planSource")); assertBrief(activity, TYPED);
                assertNoCaptureOrProcessing(activity);
            });
        }
    }

    private static void assertMicEntrypointsRefused(MainActivity activity) {
        for (String action : new String[] {"recordVoiceBrief", "listenBrief", "startShoot", "startCamera"}) {
            invoke(activity, action, new Class<?>[0]);
            assertDenied(activity); assertNull(field(activity, "capture")); assertNull(field(activity, "pose"));
            assertNull(field(activity, "voiceBriefReview")); assertNull(field(activity, "processingVoiceBrief"));
            assertFalse(((SpeechCoach) field(activity, "speech")).isListening());
            assertEquals(false, field(activity, "session")); assertEquals(false, field(activity, "busy"));
        }
    }

    private static LocalBriefRecorder.Listener silentRecorderListener() {
        return new LocalBriefRecorder.Listener() {
            public void onStarted() { }
            public void onReady(File file) { LocalBriefRecorder.discard(file); }
            public void onError(String message) { }
            public void onCanceled() { }
        };
    }

    /** Plain synthetic bytes only: no MediaRecorder, audio decoding, or device microphone. */
    private static final class FakeBriefBackend implements LocalBriefRecorder.RecorderBackend {
        File file; int starts, stops, releases; boolean failRelease;
        public void start() throws Exception {
            starts++;
            try (FileOutputStream stream = new FileOutputStream(file)) { stream.write(new byte[] {1, 2, 3}); }
        }
        public void stop() { stops++; }
        public void release() { releases++; if (failRelease) throw new IllegalStateException("Synthetic release failure"); }
    }

    private static AlertDialog showReview(ActivityScenario<MainActivity> scenario, String draft) {
        AtomicReference<AlertDialog> shown = new AtomicReference<>();
        scenario.onActivity(activity -> {
            assertEquals(Lifecycle.State.RESUMED, activity.getLifecycle().getCurrentState());
            shown.set(review(activity, draft));
        });
        // Dialog.show queues OnShow on Android's main loop. A synthetic button click
        // in the presentation event would hit the default auto-dismiss listener.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            assertEquals(Lifecycle.State.RESUMED, activity.getLifecycle().getCurrentState());
            assertTrue(shown.get().isShowing());
        });
        return shown.get();
    }

    private static AlertDialog review(MainActivity activity, String draft) {
        invoke(activity, "reviewVoiceBrief", new Class<?>[] {String.class, int.class}, draft,
                (Integer) field(activity, "voiceBriefGeneration"));
        AlertDialog dialog = (AlertDialog) field(activity, "voiceBriefReview");
        assertNotNull(dialog); assertTrue(dialog.isShowing()); return dialog;
    }

    private static EditText words(AlertDialog dialog) {
        EditText words = findEditText(dialog.getWindow().getDecorView()); assertNotNull(words); return words;
    }

    private static EditText findEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText result = findEditText(group.getChildAt(i)); if (result != null) return result;
            }
        }
        return null;
    }

    private static String textOfLength(int length, String prefix, String suffix) {
        StringBuilder text = new StringBuilder(prefix);
        while (text.length() < length - suffix.length()) text.append('x');
        text.append(suffix); assertEquals(length, text.length()); return text.toString();
    }

    private static void assertBrief(MainActivity activity, String expected) {
        assertEquals(expected, field(activity, "brief"));
        assertEquals(expected, ((EditText) field(activity, "briefField")).getText().toString());
    }

    private static void assertNoCaptureOrProcessing(MainActivity activity) {
        assertDenied(activity);
        assertNull(field(activity, "capture")); assertNull(field(activity, "briefRecorder"));
        assertNull(field(activity, "processingVoiceBrief")); assertNull(field(activity, "voiceBriefReader"));
        for (String flag : new String[] {"live", "session", "countdown", "sequenceActive", "busy"}) {
            assertEquals("Synthetic review must not activate " + flag, false, field(activity, flag));
        }
        assertFalse(((SpeechCoach) field(activity, "speech")).isListening());
        assertEquals("Review must not load the planner", 0L, ((Number) field(field(activity, "planner"), "handle")).longValue());
    }

    private static void assertDenied(Context context) {
        assertEquals("UI test requires microphone permission to remain denied", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
        assertEquals("UI test requires camera permission to remain denied", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.CAMERA));
    }

    private static Object field(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception error) { throw new AssertionError("Missing UI test field: " + name, error); }
    }

    private static void invoke(Object target, String name, Class<?>[] parameters, Object... args) {
        call(target, name, parameters, args);
    }

    private static Object call(Object target, String name, Class<?>[] parameters, Object... args) {
        try { Method method = target.getClass().getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(target, args); }
        catch (Exception error) { throw new AssertionError("Synthetic UI action failed: " + name, error); }
    }

    private static void setField(Object target, String name, Object value) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (Exception error) { throw new AssertionError("Missing UI test field: " + name, error); }
    }
}
