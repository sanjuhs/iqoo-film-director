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
 * Future unlocked-device UI acceptance using synthetic text only, not recorded voice evidence.
 * Never clicks Record/Speak, creates a recorder, runs ASR/native inference, or grants permissions.
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
            scenario.onActivity(activity -> {
                assertBrief(activity, TYPED); assertNoCaptureOrProcessing(activity);
                List<?> originalShots = new ArrayList<>((List<?>) field(activity, "shots"));
                Object originalSource = field(activity, "planSource");
                AlertDialog dialog = review(activity, fullDraft);
                EditText words = words(dialog);
                assertEquals("Review must retain the complete draft", fullDraft, words.getText().toString());
                assertBrief(activity, TYPED);
                words.setText(correction);
                assertBrief(activity, TYPED);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertFalse(dialog.isShowing()); assertBrief(activity, correction);
                assertEquals("Applying reviewed words must preserve the current shots", originalShots, field(activity, "shots"));
                assertEquals(originalSource, field(activity, "planSource"));
                assertNoCaptureOrProcessing(activity);
            });
        }
    }

    @Test(timeout = 30_000)
    public void overlongAndEmptyDraftsAreRejectedWithoutTruncationAndKeepTypedPreservesExistingBrief() {
        String fullDraft = textOfLength(640, "Long synthetic draft ", " all final words remain visible");
        AtomicReference<AlertDialog> kept = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                AlertDialog dialog = review(activity, fullDraft); kept.set(dialog);
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
                assertFalse(kept.get().isShowing()); assertNull(field(activity, "voiceBriefReview"));
                assertBrief(activity, TYPED);
                AlertDialog corrected = review(activity, fullDraft);
                String reviewed = "I corrected this long synthetic speech into a short fashion brief.";
                words(corrected).setText(reviewed);
                corrected.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertBrief(activity, reviewed); assertNoCaptureOrProcessing(activity);
            });
        }
    }

    @Test(timeout = 30_000)
    public void backgroundAndExplicitInvalidationDismissDraftAndPreventOldUseFromApplyingAfterResume() {
        AtomicReference<AlertDialog> old = new AtomicReference<>();
        AtomicReference<Button> oldUse = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                AlertDialog dialog = review(activity, "Synthetic draft pending when the app leaves foreground");
                old.set(dialog); oldUse.set(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
                assertBrief(activity, TYPED);
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertFalse("Backgrounding must dismiss the review", old.get().isShowing());
                assertNull(field(activity, "voiceBriefReview"));
                // Exercise the retained old listener, not an inaccessible dismissed-window click.
                oldUse.get().performClick(); assertBrief(activity, TYPED);
                AlertDialog dialog = review(activity, "Another synthetic pending voice draft");
                Button staleUse = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                invoke(activity, "cancelVoiceBrief", new Class<?>[0]);
                assertFalse(dialog.isShowing()); staleUse.performClick(); assertBrief(activity, TYPED);
                assertNoCaptureOrProcessing(activity);
            });
        }
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
        try { Method method = target.getClass().getDeclaredMethod(name, parameters); method.setAccessible(true); method.invoke(target, args); }
        catch (Exception error) { throw new AssertionError("Synthetic UI action failed: " + name, error); }
    }
}
