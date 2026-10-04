package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Actual recovery buttons/lifecycle on a fresh unlocked emulator, with a dedicated synthetic
 * store. Media comes from DemoAssets; no camera, microphone, model, audio player or flight.
 * Late readable file publication is staged; this is not a CameraX recording interruption. */
@RunWith(AndroidJUnit4.class)
public final class CaptureTakeRecoveryUiTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SharedPreferences preferences; private Map<String, ?> originalPreferences;
    private boolean changedPreferences; private File directory; private CaptureTakeStore store;
    private long demoDuration;
    private final List<File> owned = new ArrayList<>();
    private final List<FileState> sources = new ArrayList<>();
    private List<Take> demo;

    @Before public void freshUnlockedEmulatorOnlyAndPreserveEntirePreferences() throws Exception {
        String fingerprint = Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT);
        assertTrue("Use only the project's fresh synthetic emulator", fingerprint.contains("generic") || fingerprint.contains("emulator") || fingerprint.contains("sdk_gphone"));
        KeyguardManager lock = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Do not unlock a user device for this test", lock != null && lock.isKeyguardLocked()); assertDenied();
        assertEmpty(new File(context.getFilesDir(), "takes")); assertEmpty(new File(context.getFilesDir(), "export-journal"));
        assertFalse(new File(context.getFilesDir(), "director-model.gguf").exists());
        assertFalse(new File(context.getFilesDir(), "models/ggml-tiny.en.bin").exists());
        preferences = context.getSharedPreferences("shoot", 0); originalPreferences = new HashMap<>(preferences.getAll());
        CountDownLatch done = new CountDownLatch(1); AtomicReference<List<Take>> generated = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        DemoAssets.create(context, new DemoAssets.Listener() {
            public void onReady(List<Take> takes) { generated.set(takes); done.countDown(); }
            public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue("Local synthetic demo encoding timed out", done.await(60, TimeUnit.SECONDS)); assertNull(error.get(), error.get());
        demo = generated.get(); assertNotNull(demo); assertEquals(3, demo.size());
        for (Take take : demo) sources.add(new FileState(new File(take.uri.getPath())));
        demoDuration = CaptureController.finalizedVideoDuration(new File(demo.get(0).uri.getPath()));
        assertTrue("Known three-second synthetic container", Math.abs(demoDuration - 3000) <= 100);
        directory = new File(context.getCacheDir(), "synthetic-capture-recovery-ui-" + UUID.randomUUID()).getCanonicalFile();
        assertFalse(directory.exists()); assertTrue(directory.mkdir()); store = new CaptureTakeStore(directory);
        JSONArray takes = new JSONArray().put(savedTake(demo.get(1), "synthetic-existing-selected", true))
                .put(savedTake(demo.get(2), "synthetic-existing-excluded", false));
        changedPreferences = true;
        assertTrue(preferences.edit().clear().putString("state", new JSONObject().put("tab", 0).put("voice", false)
                .put("brief", "Synthetic recovery UI").put("reelTitle", "Creator title kept").put("takes", takes).toString()).commit());
    }

    @After public void restorePreferencesAndCleanOnlyOwnedCopiesAndMarkers() throws Exception {
        try {
            if (changedPreferences) {
                SharedPreferences.Editor editor = preferences.edit().clear();
                for (Map.Entry<String, ?> entry : originalPreferences.entrySet()) {
                    String key = entry.getKey(); Object value = entry.getValue();
                    if (value instanceof String) editor.putString(key, (String) value);
                    else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                    else if (value instanceof Long) editor.putLong(key, (Long) value);
                    else if (value instanceof Float) editor.putFloat(key, (Float) value);
                    else if (value instanceof Set) { @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value; editor.putStringSet(key, new HashSet<>(set)); }
                    else throw new AssertionError("Unexpected preference type");
                }
                assertTrue(editor.commit()); assertEquals(originalPreferences, preferences.getAll());
            }
            for (FileState source : sources) source.assertSame(); assertDenied();
        } finally {
            for (File file : owned) {
                store.abort(file); new android.util.AtomicFile(new File(file.getPath() + ".json")).delete();
                if (file.exists()) assertTrue("Delete only this test's copied synthetic take", file.delete());
            }
            if (directory != null) assertTrue("Own sandbox contains no unknown files", directory.delete());
        }
    }

    @Test(timeout = 120_000)
    public void enteringAssemblyManualFindAndResumeRecoverLateFilesUnselectedWithoutChangingCreatorEdits() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> { set(activity, "captureTakeStore", store); assertEquals(2, takes(activity).size()); assertExisting(activity); assertQuiescent(activity); });
            File first = readyCopy(0);
            scenario.onActivity(activity -> { click(activity, "Reel"); assertRecovered(activity, 1); assertEquals(Uri.fromFile(first), takes(activity).get(2).uri); });
            File second = readyCopy(1);
            scenario.onActivity(activity -> { click(activity, "Find saved phone takes"); assertRecovered(activity, 2); assertEquals(Uri.fromFile(second), takes(activity).get(3).uri); });
            // Repeating the real control must keep order, selections and edits without duplicates.
            scenario.onActivity(activity -> { click(activity, "Find saved phone takes"); assertRecovered(activity, 2); });
            scenario.moveToState(Lifecycle.State.CREATED);
            File late = readyCopy(2);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> { assertRecovered(activity, 3); assertEquals(Uri.fromFile(late), takes(activity).get(4).uri); });
            scenario.recreate();
            scenario.onActivity(activity -> { assertRecovered(activity, 3); assertEquals(2, ((Number) field(activity, "tab")).intValue()); assertQuiescent(activity); });
            JSONArray saved = new JSONObject(preferences.getString("state", "{}")).getJSONArray("takes"); assertEquals(5, saved.length());
            for (int i = 2; i < 5; i++) { assertFalse(saved.getJSONObject(i).getBoolean("selected")); assertEquals("synthetic-recovery-" + (i - 2), saved.getJSONObject(i).getString("id")); }
            for (FileState source : sources) source.assertSame(); assertDenied();
            android.util.Log.i("MiniFilmCaptureRecoveryUiTest", "UI_PASS synthetic=true entryRecovery=1 manualRecovery=1 resumeRecovery=1 recoveredUnselected=true existingEditsPreserved=true noCapture=true noModel=true");
        }
    }

    private File readyCopy(int index) throws Exception {
        File target = new File(directory, "take-" + UUID.randomUUID() + ".mp4"); owned.add(target);
        store.prepare(target, new Shot("synthetic-recovery-" + index, "Synthetic recovered " + index, "Hold still", "Synthetic retained caption " + index, 6000));
        try (InputStream input = new FileInputStream(new File(demo.get(0).uri.getPath())); FileOutputStream output = new FileOutputStream(target)) {
            byte[] block = new byte[8192]; int count; while ((count = input.read(block)) != -1) output.write(block, 0, count); output.getFD().sync();
        }
        assertEquals(demoDuration, store.complete(target)); return target;
    }
    private static JSONObject savedTake(Take source, String id, boolean selected) throws Exception {
        return new JSONObject().put("uri", source.uri.toString()).put("id", id).put("title", "Creator existing " + id)
                .put("caption", "Creator caption kept").put("duration", 3000).put("in", 501).put("out", 2499).put("selected", selected)
                .put("captionOrigin", "creator-reviewed-manual").put("subtitles", new JSONArray().put(new JSONObject().put("start", 600).put("end", 1100).put("text", "Creator reviewed words")));
    }
    private void assertRecovered(MainActivity activity, int count) {
        assertEquals(2 + count, takes(activity).size()); assertExisting(activity); assertQuiescent(activity);
        for (int i = 0; i < count; i++) {
            Take take = takes(activity).get(i + 2); assertFalse(take.selected); assertEquals("synthetic-recovery-" + i, take.shotId);
            assertEquals("Synthetic recovered " + i, take.title); assertEquals("Synthetic retained caption " + i, take.caption);
            assertEquals(demoDuration, take.durationMs); assertEquals(0, take.inMs); assertEquals(demoDuration, take.outMs); assertTrue(take.subtitles.isEmpty());
        }
    }
    private static void assertExisting(MainActivity activity) {
        assertEquals("Creator title kept", field(activity, "reelTitle"));
        for (int i = 0; i < 2; i++) {
            Take take = takes(activity).get(i); String id = i == 0 ? "synthetic-existing-selected" : "synthetic-existing-excluded";
            assertEquals(id, take.shotId); assertEquals("Creator existing " + id, take.title); assertEquals(i == 0, take.selected);
            assertEquals("Creator caption kept", take.caption); assertEquals(501, take.inMs); assertEquals(2499, take.outMs);
            assertEquals("creator-reviewed-manual", take.captionOrigin); assertEquals(1, take.subtitles.size());
            assertEquals(600, take.subtitles.get(0).startMs); assertEquals(1100, take.subtitles.get(0).endMs); assertEquals("Creator reviewed words", take.subtitles.get(0).text);
        }
    }
    private static void assertQuiescent(MainActivity activity) {
        assertNull(field(activity, "capture")); assertNull(field(activity, "pose")); assertNull(field(activity, "briefRecorder"));
        assertEquals(false, field(activity, "busy")); assertEquals(false, field(activity, "session")); assertEquals(false, field(activity, "countdown"));
        assertEquals(0L, ((Number) field(field(activity, "planner"), "handle")).longValue());
        assertFalse(((SpeechCoach) field(activity, "speech")).hasSpeechWork());
    }
    private static void click(MainActivity activity, String label) { Button button = find((View) field(activity, "root"), label); assertNotNull(label, button); assertTrue(button.isEnabled()); assertTrue(button.performClick()); }
    private static Button find(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { Button button = find(((ViewGroup) view).getChildAt(i), label); if (button != null) return button; }
        return null;
    }
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity activity) { return (List<Take>) field(activity, "takes"); }
    private static Object field(Object object, String name) { try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); } catch (Exception error) { throw new AssertionError(error); } }
    private static void set(Object object, String name, Object value) { try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); } catch (Exception error) { throw new AssertionError(error); } }
    private static void assertEmpty(File directory) { File[] files = directory.listFiles(); assertTrue("Require fresh emulator capture/recovery state", files == null || files.length == 0); }
    private void assertDenied() { assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA)); assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)); }
    private static final class FileState {
        final File file; final long bytes, mtime; final String hash;
        FileState(File file) throws Exception { this.file = file; bytes = file.length(); mtime = file.lastModified(); hash = sha(file); }
        void assertSame() throws Exception { assertTrue(file.isFile()); assertEquals(bytes, file.length()); assertEquals(mtime, file.lastModified()); assertEquals(hash, sha(file)); }
    }
    private static String sha(File file) throws Exception { try (InputStream input = new FileInputStream(file)) { MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] block = new byte[8192]; int count; while ((count = input.read(block)) != -1) hash.update(block, 0, count); return ProjectPackager.hex(hash.digest()); } }
}
