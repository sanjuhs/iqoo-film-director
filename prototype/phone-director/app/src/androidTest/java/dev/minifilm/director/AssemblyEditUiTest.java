package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
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
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

/** Real trim/subtitle dialogs on an explicitly unlocked synthetic test device. The source is
 * labelled non-media bytes: nothing is decoded, recorded, played, inferred or uploaded.
 * Accessibility actions target this app's active dialog only; no private framework reflection
 * or additional test dependency is needed. This does not establish small-screen/IME layout. */
@RunWith(AndroidJUnit4.class)
public final class AssemblyEditUiTest {
    private static final long DURATION = 5746;
    private static final String ORIGINAL_TITLE = "Synthetic exact source A";
    private static final String EDITED_TITLE = "My reviewed synthetic opening";
    private static final String CAPTION = "My reviewed synthetic outfit\nEvery chosen word remains intact\n"
            + "This third line is intentionally complete\nThe final line and punctuation stay here.";
    private static final byte[] SOURCE_BYTES = "Synthetic assembly UI fixture; deliberately not playable media."
            .getBytes(StandardCharsets.UTF_8);
    private SharedPreferences preferences;
    private Map<String, ?> originalPreferences;
    private boolean changedPreferences;
    private File source, zip;
    private long sourceModified;

    @Before public void requireUnlockedDeniedCaptureAndPreserveEntireShootState() throws Exception {
        Context context = context();
        KeyguardManager lock = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Use a fresh unlocked synthetic device for real dialog checks", lock != null && lock.isKeyguardLocked());
        assertDenied(context);
        preferences = context.getSharedPreferences("shoot", 0);
        originalPreferences = new HashMap<>(preferences.getAll());
        source = File.createTempFile("synthetic-assembly-ui-", ".bin", context.getFilesDir());
        try (FileOutputStream output = new FileOutputStream(source)) { output.write(SOURCE_BYTES); }
        sourceModified = source.lastModified();
        JSONArray takes = new JSONArray();
        takes.put(takeJson("a", ORIGINAL_TITLE, true));
        takes.put(takeJson("b", "Synthetic excluded B", false));
        takes.put(takeJson("c", "Synthetic closing C", true));
        JSONObject state = new JSONObject().put("brief", "Synthetic editable assembly")
                .put("voice", false).put("look", "Clean").put("takes", takes);
        changedPreferences = true;
        assertTrue(preferences.edit().clear().putString("state", state.toString()).commit());
    }

    @After public void restoreEntirePreferencesAndRemoveOnlyOwnedTinyFixtures() throws Exception {
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
                    else if (value instanceof Set) {
                        @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
                        editor.putStringSet(key, new HashSet<>(strings));
                    } else throw new AssertionError("Unexpected preference type");
                }
                assertTrue(editor.commit()); assertEquals(originalPreferences, preferences.getAll());
            }
            if (source != null) assertSourceUnchanged();
            assertDenied(context());
        } finally {
            if (zip != null && zip.exists()) assertTrue("Remove only this test's generated ZIP", zip.delete());
            if (source != null && source.exists()) assertTrue("Remove only this test's original fixture", source.delete());
        }
    }

    @Test(timeout = 45_000) public void trimDialogRejectsInvalidBoundsThenPersistsExactEditsOrderAndInvalidatesPreparedZip() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assemble(activity); assertQuiescent(activity);
                invoke(activity, "editTake", new Class<?>[]{int.class}, 0);
            });
            dialog("Make this cut yours");
            assertEquals("0.000", editText("In point / seconds"));
            assertEquals("5.746", editText("Out point / seconds"));
            setText("Take title", EDITED_TITLE); setText("Typography (used when no subtitles)", CAPTION);
            String[][] invalid = {{"0.000", "5.747"}, {"1.001", "1.001"}, {"1.001", "1.250"}, {"-0.001", "5.746"}};
            for (String[] bounds : invalid) {
                setText("In point / seconds", bounds[0]); setText("Out point / seconds", bounds[1]); clickDialog("Save");
                dialog("Make this cut yours");
                assertEquals(bounds[0], editText("In point / seconds"));
                assertEquals(bounds[1], editText("Out point / seconds"));
                assertEquals(CAPTION, editText("Typography (used when no subtitles)"));
                scenario.onActivity(activity -> {
                    Take take = takes(activity).get(0);
                    assertEquals(0, take.inMs); assertEquals(DURATION, take.outMs);
                    assertEquals(ORIGINAL_TITLE, take.title); assertEquals("Original synthetic caption", take.caption);
                    assertQuiescent(activity);
                });
            }
            // First prove exact unchanged source-end roundtrip through the real Save handler.
            setText("In point / seconds", "0.000"); setText("Out point / seconds", "5.746");
            clickDialog("Save");
            scenario.onActivity(activity -> {
                Take take = takes(activity).get(0); assertEquals(0, take.inMs); assertEquals(DURATION, take.outMs);
                assertEquals(EDITED_TITLE, take.title); assertEquals(CAPTION, take.caption); assertQuiescent(activity);
            });
            createTinyZip();
            scenario.onActivity(activity -> {
                set(activity, "pendingPack", zip); set(activity, "pendingPackSnapshot", invoke(activity, "packSnapshot", new Class<?>[0]));
                invoke(activity, "save", new Class<?>[0]); assertTrue(zip.isFile());
                invoke(activity, "editTake", new Class<?>[]{int.class}, 0);
            });
            dialog("Make this cut yours"); setText("In point / seconds", "1.001"); setText("Out point / seconds", "5.746"); clickDialog("Save");
            scenario.onActivity(activity -> {
                assertNull("Changed trim invalidates an earlier generated package", field(activity, "pendingPack"));
                assertFalse(zip.exists()); assertEquals(1001, takes(activity).get(0).inMs);
                moveClosingEarlier(activity); moveClosingEarlier(activity);
                assertEditsAndOrder(activity); assertQuiescent(activity);
            });
            scenario.recreate();
            scenario.onActivity(activity -> { assertAssemblyRestored(activity); assertEditsAndOrder(activity); assertQuiescent(activity); });
            JSONObject saved = new JSONObject(preferences.getString("state", "{}"));
            JSONArray rows = saved.getJSONArray("takes");
            assertEquals("c", rows.getJSONObject(0).getString("id")); assertEquals("a", rows.getJSONObject(1).getString("id"));
            assertEquals(CAPTION, rows.getJSONObject(1).getString("caption"));
            assertEquals(1001, rows.getJSONObject(1).getLong("in")); assertEquals(DURATION, rows.getJSONObject(1).getLong("out"));
            assertTrue(rows.getJSONObject(0).getBoolean("selected")); assertTrue(rows.getJSONObject(1).getBoolean("selected")); assertFalse(rows.getJSONObject(2).getBoolean("selected"));
            assertSourceUnchanged();
        }
    }

    @Test(timeout = 45_000) public void subtitleDialogPreservesFullReviewedWordsAndExactSourceEndAcrossRecreation() throws Exception {
        String reviewed = "Creator-reviewed synthetic words " + repeat('x', 210) + " with every final word retained.";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> { assemble(activity); invoke(activity, "reviewSubtitles", new Class<?>[]{int.class}, 0); });
            dialog("Review your subtitle draft");
            assertEquals("1.001", editText("Start seconds")); assertEquals("5.746", editText("End seconds"));
            setText("Subtitle words", reviewed);
            String[][] invalid = {{"1.001", "5.747"}, {"1.001", "1.001"}, {"-0.001", "5.746"}};
            for (String[] bounds : invalid) {
                setText("Start seconds", bounds[0]); setText("End seconds", bounds[1]); clickDialog("Save reviewed subtitles");
                dialog("Review your subtitle draft"); assertEquals(reviewed, editText("Subtitle words"));
                scenario.onActivity(activity -> {
                    SubtitleCue cue = takes(activity).get(0).subtitles.get(0);
                    assertEquals(1001, cue.startMs); assertEquals(DURATION, cue.endMs); assertEquals("Original synthetic subtitle", cue.text);
                    assertEquals("creator-reviewed-synthetic-manual", takes(activity).get(0).captionOrigin); assertQuiescent(activity);
                });
            }
            setText("Start seconds", "1.001"); setText("End seconds", "5.746"); clickDialog("Save reviewed subtitles");
            scenario.onActivity(activity -> assertReviewedCue(activity, reviewed));
            scenario.recreate(); scenario.onActivity(activity -> { assertAssemblyRestored(activity); assertReviewedCue(activity, reviewed); assertQuiescent(activity); });
            JSONObject cue = new JSONObject(preferences.getString("state", "{}")).getJSONArray("takes").getJSONObject(0).getJSONArray("subtitles").getJSONObject(0);
            assertEquals(1001, cue.getLong("start")); assertEquals(DURATION, cue.getLong("end")); assertEquals(reviewed, cue.getString("text"));
            assertSourceUnchanged();
        }
    }

    @Test(timeout = 45_000) public void overlappingReviewKeepsAllWordsAndOriginalTimesThenAcceptsTouchingCues() throws Exception {
        final String first="Reviewed opening words",second="Reviewed later words";
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity->{
                assemble(activity);Take take=takes(activity).get(0);
                take.subtitles=Arrays.asList(new SubtitleCue(500,2000,first),new SubtitleCue(3000,4500,second));
                invoke(activity,"save",new Class<?>[0]);
                invoke(activity,"reviewSubtitles",new Class<?>[]{int.class},0);
            });
            dialog("Review your subtitle draft");
            String saved=preferences.getString("state","");
            String[][] rejected={{"1.000","1.500"},{"1.500","2.500"},{"0.500","2.000"}};
            for(String[] range:rejected) {
                setTextAt("Start seconds",1,range[0]);setTextAt("End seconds",1,range[1]);
                clickDialog("Save reviewed subtitles");dialog("Review your subtitle draft");
                assertEquals(first,textAt("Subtitle words",0));assertEquals(second,textAt("Subtitle words",1));
                assertEquals(range[0],textAt("Start seconds",1));assertEquals(range[1],textAt("End seconds",1));
                assertEquals("Rejected overlap must not mutate saved edits",saved,preferences.getString("state",""));
                scenario.onActivity(activity->{
                    List<SubtitleCue> cues=takes(activity).get(0).subtitles;
                    assertEquals(2,cues.size());assertEquals(500,cues.get(0).startMs);assertEquals(2000,cues.get(0).endMs);
                    assertEquals(3000,cues.get(1).startMs);assertEquals(4500,cues.get(1).endMs);
                    assertEquals(first,cues.get(0).text);assertEquals(second,cues.get(1).text);assertQuiescent(activity);
                });
            }
            setTextAt("Start seconds",1,"2.000");setTextAt("End seconds",1,"3.500");clickDialog("Save reviewed subtitles");
            scenario.recreate();scenario.onActivity(activity->{
                assertAssemblyRestored(activity);List<SubtitleCue> cues=takes(activity).get(0).subtitles;
                assertEquals(2,cues.size());assertEquals(500,cues.get(0).startMs);assertEquals(2000,cues.get(0).endMs);
                assertEquals(2000,cues.get(1).startMs);assertEquals(3500,cues.get(1).endMs);
                assertEquals(first,cues.get(0).text);assertEquals(second,cues.get(1).text);assertQuiescent(activity);
            });assertSourceUnchanged();
        }
    }

    private JSONObject takeJson(String id, String title, boolean selected) throws Exception {
        return new JSONObject().put("uri", Uri.fromFile(source).toString()).put("id", id).put("title", title)
                .put("caption", "Original synthetic caption").put("duration", DURATION).put("in", 0).put("out", DURATION)
                .put("selected", selected).put("captionOrigin", "creator-reviewed-synthetic-manual")
                .put("subtitles", new JSONArray().put(new JSONObject().put("start", 1001).put("end", DURATION).put("text", "Original synthetic subtitle")));
    }
    private void createTinyZip() throws Exception {
        zip = File.createTempFile("minifilm-pack-", ".zip", context().getCacheDir());
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("README.txt")); output.write("Synthetic package invalidation fixture only".getBytes(StandardCharsets.UTF_8)); output.closeEntry();
        }
    }
    private void assertSourceUnchanged() throws Exception { assertEquals(SOURCE_BYTES.length, source.length()); assertEquals(sourceModified, source.lastModified()); assertArrayEquals(SOURCE_BYTES, Files.readAllBytes(source.toPath())); }
    private static void assertReviewedCue(MainActivity activity, String text) {
        Take take = takes(activity).get(0); assertEquals(1, take.subtitles.size()); SubtitleCue cue = take.subtitles.get(0);
        assertEquals(1001, cue.startMs); assertEquals(DURATION, cue.endMs); assertEquals(text, cue.text);
        assertEquals("creator-reviewed-offline-asr", take.captionOrigin); assertEquals(0, take.inMs); assertEquals(DURATION, take.outMs);
        assertEquals(ORIGINAL_TITLE, take.title); assertEquals("Original synthetic caption", take.caption); assertQuiescent(activity);
    }
    private static void assertEditsAndOrder(MainActivity activity) {
        List<Take> rows = takes(activity); assertEquals(3, rows.size());
        assertEquals(Arrays.asList("c", "a", "b"), Arrays.asList(rows.get(0).shotId, rows.get(1).shotId, rows.get(2).shotId));
        assertTrue(rows.get(0).selected); assertTrue(rows.get(1).selected); assertFalse(rows.get(2).selected);
        Take edited = rows.get(1); assertEquals(EDITED_TITLE, edited.title); assertEquals(CAPTION, edited.caption);
        assertEquals(1001, edited.inMs); assertEquals(DURATION, edited.outMs); assertEquals(DURATION, edited.durationMs);
        assertEquals(1001, edited.subtitles.get(0).startMs); assertEquals(DURATION, edited.subtitles.get(0).endMs);
        assertEquals("Original synthetic subtitle", edited.subtitles.get(0).text); assertEquals("creator-reviewed-synthetic-manual", edited.captionOrigin);
    }
    private static void moveClosingEarlier(MainActivity activity) {
        CheckBox row = find(activity.getWindow().getDecorView(), CheckBox.class, "Synthetic closing C"); assertNotNull(row);
        Button move = find((View) row.getParent(), Button.class, "Move earlier"); assertNotNull(move); assertTrue(move.performClick());
    }
    private static void assemble(MainActivity activity) { Button button = find(activity.getWindow().getDecorView(), Button.class, "03  Assemble"); assertNotNull(button); assertTrue(button.performClick()); }
    private static void assertAssemblyRestored(MainActivity activity) {
        assertEquals("Recreation must retain the creator's assembly screen", 2, ((Number) field(activity, "tab")).intValue());
        assertNotNull(find(activity.getWindow().getDecorView(), Button.class, "Export my reel"));
    }
    private static void assertQuiescent(MainActivity activity) {
        assertDenied(activity); assertNull(field(activity, "capture")); assertNull(field(activity, "briefRecorder"));
        assertNull(field(activity, "processingVoiceBrief")); assertNull(field(activity, "referenceSpeechReader"));
        assertEquals(false, field(activity, "busy")); assertEquals(false, field(activity, "session")); assertEquals(false, field(activity, "live"));
        assertEquals(false, field(activity, "countdown")); assertEquals(false, field(activity, "sequenceActive"));
        assertEquals(0L, ((Number) field(field(activity, "planner"), "handle")).longValue()); assertFalse(((SpeechCoach) field(activity, "speech")).isListening());
    }
    private static void assertDenied(Context context) { assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA)); assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)); }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void dialog(String title) { assertNotNull("Expected current app dialog: " + title, node(title, false)); }
    private static String editText(String hint) { CharSequence text = node(hint, true).getText(); return text == null ? "" : text.toString(); }
    private static void setText(String hint, String text) {
        Bundle arguments = new Bundle(); arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        assertTrue("Real dialog field must accept exact synthetic text: " + hint, node(hint, true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        long deadline = SystemClock.elapsedRealtime() + 5000;
        String actual;
        do { actual = editText(hint); if (text.equals(actual)) return; SystemClock.sleep(25); }
        while (SystemClock.elapsedRealtime() < deadline);
        assertEquals(text, actual);
    }
    private static String textAt(String hint,int index) {
        CharSequence value=nodeAt(hint,index).getText();return value==null?"":value.toString();
    }
    private static void setTextAt(String hint,int index,String value) {
        Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        assertTrue(nodeAt(hint,index).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        long deadline=SystemClock.elapsedRealtime()+5000;
        do {if(value.equals(textAt(hint,index)))return;SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<deadline);
        assertEquals(value,textAt(hint,index));
    }
    private static AccessibilityNodeInfo nodeAt(String hint,int index) {
        long deadline=SystemClock.elapsedRealtime()+5000;
        do {
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null&&context().getPackageName().contentEquals(root.getPackageName()==null?"":root.getPackageName())) {
                java.util.ArrayList<AccessibilityNodeInfo> matches=new java.util.ArrayList<>();collectHint(root,hint,matches);
                if(matches.size()>index)return matches.get(index);
            }SystemClock.sleep(25);
        }while(SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("Missing own-app subtitle field "+hint+" at "+index);
    }
    private static void collectHint(AccessibilityNodeInfo node,String hint,List<AccessibilityNodeInfo> matches) {
        if(node.getHintText()!=null&&hint.contentEquals(node.getHintText()))matches.add(node);
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)collectHint(child,hint,matches);}
    }
    private static void clickDialog(String label) {
        AccessibilityNodeInfo button = node(label, false); assertTrue("Expected dialog button", button.isClickable());
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)); InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static AccessibilityNodeInfo node(String value, boolean hint) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null && context().getPackageName().contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                AccessibilityNodeInfo found = findNode(root, value, hint); if (found != null) return found;
            }
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Missing own-app active-dialog " + (hint ? "hint: " : "text: ") + value);
    }
    private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String value, boolean hint) {
        CharSequence text = hint ? node.getHintText() : node.getText();
        // System dialog button themes may expose their all-caps transformation. Field hints
        // and every entered/stored creator character retain exact, case-sensitive assertions.
        if (text != null && (hint ? value.contentEquals(text) : value.equalsIgnoreCase(text.toString()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) { AccessibilityNodeInfo found = findNode(child, value, hint); if (found != null) return found; } }
        return null;
    }
    private static <T extends View> T find(View root, Class<T> type, String text) {
        if (type.isInstance(root) && root instanceof TextView && text.contentEquals(((TextView) root).getText())) return type.cast(root);
        if (root instanceof ViewGroup) { ViewGroup group = (ViewGroup) root; for (int i = 0; i < group.getChildCount(); i++) { T found = find(group.getChildAt(i), type, text); if (found != null) return found; } } return null;
    }
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity activity) { return (List<Take>) field(activity, "takes"); }
    private static Object field(Object target, String name) { try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); } catch (Exception failure) { throw new AssertionError(name, failure); } }
    private static void set(Object target, String name, Object value) { try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); } catch (Exception failure) { throw new AssertionError(name, failure); } }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) { try { Method method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, arguments); } catch (Exception failure) { throw new AssertionError(name, failure); } }
    private static String repeat(char character, int count) { char[] text = new char[count]; Arrays.fill(text, character); return new String(text); }
}
