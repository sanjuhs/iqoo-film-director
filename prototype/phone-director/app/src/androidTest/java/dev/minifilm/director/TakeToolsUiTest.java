package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
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
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Fresh empty emulator: actual menu/dialog actions with synthetic unread URIs, no media/AI/capture. */
@RunWith(AndroidJUnit4.class)
public final class TakeToolsUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    @Before public void requireFreshEmulatorAndPreserveEveryPreference() throws Exception {
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.ROOT);
        assertTrue("Only a fresh synthetic emulator",fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));assertEquals("Approved synthetic UI API",36,android.os.Build.VERSION.SDK_INT);
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());denied();
        empty(new File(context().getFilesDir(),"takes"));empty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/director-mmproj.gguf").exists());
        assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreFullPreferencesAndDeniedCapture(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){
            String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)editor.putString(k,(String)v);else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(k,(Integer)v);else if(v instanceof Long)editor.putLong(k,(Long)v);else if(v instanceof Float)editor.putFloat(k,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> values=(Set<String>)v;editor.putStringSet(k,new HashSet<>(values));}else throw new AssertionError("Preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}denied();
    }

    @Test(timeout=45_000) public void compactCardsKeepPreviewAndMenuExposesToolsConditionallyWithoutCancelMutations(){
        Take first=take("First",false),second=take("Second",true);String savedFirst=snapshot(first),savedSecond=snapshot(second);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,first,second);View firstCard=card(a,first);assertNotNull(find(firstCard,Button.class,"Preview take"));
                assertNotNull(find(firstCard,Button.class,"Edit & review take"));assertEquals(2,buttons(firstCard));
                assertNull(find(firstCard,Button.class,"Trim & typography"));assertNull(find(firstCard,Button.class,"Assign take to plan shots"));openCard(a,first);});idle();
            scenario.onActivity(a->{AlertDialog menu=menu(a);assertNotNull(find(menu.getWindow().getDecorView(),android.widget.ScrollView.class,null));
                for(String label:new String[]{"Assign take to plan shots","Trim & typography","Generate offline subtitles","Suggest a tighter talking cut","Review cut framing"})assertNotNull(tool(a,label));
                assertNull(find(menu.getWindow().getDecorView(),Button.class,"Move earlier"));assertNull(find(menu.getWindow().getDecorView(),Button.class,"Review subtitle words & timing"));
                menu.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(savedFirst,snapshot(first));assertEquals(savedSecond,snapshot(second));openCard(a,second);});idle();
            scenario.onActivity(a->{assertNotNull(tool(a,"Move earlier"));assertNotNull(tool(a,"Review subtitle words & timing"));tool(a,"Assign take to plan shots").performClick();assertNull(field(a,"takeToolsDialog"));});idle();
            scenario.onActivity(a->{AlertDialog assignment=(AlertDialog)field(a,"shotAssignmentDialog");assertNotNull(assignment);assertTrue(assignment.isShowing());assignment.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();openCard(a,second);});idle();
            scenario.onActivity(a->{tool(a,"Review subtitle words & timing").performClick();assertNull(field(a,"takeToolsDialog"));});idle();
            assertNotNull(node("Review your subtitle draft",false));clickDialog("Later");
            scenario.onActivity(a->{assertEquals(Arrays.asList(first,second),takes(a));assertEquals(savedFirst,snapshot(first));assertEquals(savedSecond,snapshot(second));quiescent(a);});
        }
    }

    @Test(timeout=45_000) public void trimDispatchClosesMenuThenEditsSameTakeAfterItsIndexChanges(){
        Take first=take("First",false),target=take("Target",true);String firstOriginal=snapshot(first);Uri targetUri=target.uri;
        List<SubtitleCue> targetSubtitles=target.subtitles;List<String> targetMappings=target.reviewedShotIds;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,first,target);openCard(a,target);});idle();
            scenario.onActivity(a->{Collections.swap(takes(a),0,1);tool(a,"Trim & typography").performClick();assertNull(field(a,"takeToolsDialog"));});idle();
            assertNotNull(node("Make this cut yours",false));setText("In point / seconds","0.750");setText("Out point / seconds","3.500");
            setText("Take title","My explicitly edited target");setText("Typography (used when no subtitles)","My unchanged-source words");clickDialog("Save");
            scenario.onActivity(a->{assertSame(target,takes(a).get(0));assertSame(first,takes(a).get(1));assertEquals(firstOriginal,snapshot(first));
                assertEquals(targetUri,target.uri);assertEquals("Target-original",target.shotId);assertEquals(4000,target.durationMs);assertEquals(750,target.inMs);assertEquals(3500,target.outMs);
                assertEquals("My explicitly edited target",target.title);assertEquals("My unchanged-source words",target.caption);assertSame(targetSubtitles,target.subtitles);assertSame(targetMappings,target.reviewedShotIds);
                assertFalse(target.selected);assertEquals("creator-reviewed-synthetic",target.captionOrigin);quiescent(a);
            });
        }
    }

    @Test(timeout=45_000) public void moveEarlierUsesCurrentTakeIndexAndPreservesSelectionAndAllEdits(){
        Take first=take("First",false),target=take("Target",true),last=take("Last",false);
        String f=snapshot(first),t=snapshot(target),l=snapshot(last);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,first,target,last);openCard(a,target);});idle();
            scenario.onActivity(a->{Collections.swap(takes(a),1,2);tool(a,"Move earlier").performClick();assertNull(field(a,"takeToolsDialog"));
                assertEquals(Arrays.asList(first,target,last),takes(a));assertEquals(f,snapshot(first));assertEquals(t,snapshot(target));assertEquals(l,snapshot(last));
                CheckBox selected=find(card(a,target),CheckBox.class,target.title);assertNotNull(selected);assertFalse(selected.isChecked());selected.performClick();assertTrue(target.selected);assertTrue(first.selected);assertTrue(last.selected);quiescent(a);
            });
        }
    }

    @Test(timeout=45_000) public void retainedButtonsAfterDismissRenderBackgroundOrRemovalCannotDispatchOrReorder(){
        Take first=take("First",false),target=take("Target",true);String f=snapshot(first),t=snapshot(target);
        AtomicReference<Button> trim=new AtomicReference<>(),move=new AtomicReference<>();AtomicReference<MainActivity> retained=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{retained.set(a);prepare(a,first,target);openCard(a,target);});idle();
            scenario.onActivity(a->{retain(a,trim,move);menu(a).dismiss();trim.get().performClick();move.get().performClick();assertEquals(Arrays.asList(first,target),takes(a));assertEquals(f,snapshot(first));assertEquals(t,snapshot(target));});idle();
            scenario.onActivity(a->assertNull(field(a,"takeToolsDialog")));assertNoTrimDialog();
            scenario.onActivity(a->{openCard(a,target);});idle();scenario.onActivity(a->{retain(a,trim,move);call(a,"render");trim.get().performClick();move.get().performClick();assertEquals(Arrays.asList(first,target),takes(a));});idle();assertNoTrimDialog();
            scenario.onActivity(a->openCard(a,target));idle();scenario.onActivity(a->retain(a,trim,move));
            scenario.moveToState(Lifecycle.State.CREATED);
            // Retained identity is captured while RESUMED: do not call Scenario.onActivity while stopped.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertNull(field(retained.get(),"takeToolsDialog"));trim.get().performClick();move.get().performClick();assertEquals(Arrays.asList(first,target),takes(retained.get()));});
            scenario.moveToState(Lifecycle.State.RESUMED);idle();assertNoTrimDialog();
            scenario.onActivity(a->{openCard(a,target);});idle();scenario.onActivity(a->{retain(a,trim,move);takes(a).remove(target);trim.get().performClick();move.get().performClick();
                assertEquals(Collections.singletonList(first),takes(a));call(a,"dismissTakeTools");assertEquals(f,snapshot(first));assertEquals(t,snapshot(target));quiescent(a);});idle();assertNoTrimDialog();
        }
    }

    private static void retain(MainActivity a,AtomicReference<Button> trim,AtomicReference<Button> move){trim.set(tool(a,"Trim & typography"));move.set(tool(a,"Move earlier"));}
    private static void prepare(MainActivity a,Take...rows){takes(a).clear();takes(a).addAll(Arrays.asList(rows));call(a,"save");call(a,"render");quiescent(a);}
    private static void openCard(MainActivity a,Take take){Button button=find(card(a,take),Button.class,"Edit & review take");assertNotNull(button);assertTrue(button.performClick());}
    private static View card(MainActivity a,Take take){CheckBox checkbox=find((View)field(a,"root"),CheckBox.class,take.title);assertNotNull(checkbox);return (View)checkbox.getParent();}
    private static AlertDialog menu(MainActivity a){AlertDialog dialog=(AlertDialog)field(a,"takeToolsDialog");assertNotNull(dialog);assertTrue(dialog.isShowing());return dialog;}
    private static Button tool(MainActivity a,String label){Button button=find(menu(a).getWindow().getDecorView(),Button.class,label);assertNotNull("Tool reachable: "+label,button);return button;}
    private static int buttons(View view){int count=view instanceof Button&&!(view instanceof CheckBox)?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=buttons(group.getChildAt(i));}return count;}
    private static Take take(String name,boolean subtitles){Take t=new Take(Uri.fromFile(new File(context().getCacheDir(),"take-tools-"+name+"-never-created.mp4")),name+"-original",name,"Original caption",4000);assertFalse(new File(t.uri.getPath()).exists());
        t.inMs=500;t.outMs=3600;t.selected=!"Target".equals(name);t.captionOrigin="creator-reviewed-synthetic";t.reviewedShotIds.add("earlier-reviewed-id");if(subtitles)t.subtitles.add(new SubtitleCue(800,1400,"Reviewed existing words"));return t;}
    private static String snapshot(Take take){try{JSONArray subtitles=new JSONArray();for(SubtitleCue c:take.subtitles)subtitles.put(new JSONObject().put("s",c.startMs).put("e",c.endMs).put("t",c.text));return new JSONObject().put("uri",take.uri.toString()).put("id",take.shotId).put("title",take.title).put("caption",take.caption).put("origin",take.captionOrigin).put("duration",take.durationMs).put("in",take.inMs).put("out",take.outMs).put("selected",take.selected).put("mapping",new JSONArray(take.reviewedShotIds)).put("words",subtitles).toString();}catch(JSONException e){throw new AssertionError(e);}}
    private static void quiescent(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertNull(field(a,"briefRecorder"));assertNull(field(a,"referenceSpeechReader"));assertEquals(false,field(a,"busy"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"live"));assertEquals(false,field(a,"countdown"));assertEquals(false,field(a,"sequenceActive"));
        assertEquals(0L,field(field(a,"planner"),"handle"));assertEquals(0L,field(field(a,"transcriber"),"activeRequest"));SpeechCoach speech=(SpeechCoach)field(a,"speech");assertFalse(speech.isListening());assertFalse(speech.hasSpeechWork());denied();}
    private static void assertNoTrimDialog(){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&own(root))assertNull(findNode(root,"Make this cut yours",false));}
    private static void setText(String hint,String value){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(node(hint,true).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));idle();long until=SystemClock.elapsedRealtime()+5000;String actual;do{actual=String.valueOf(node(hint,true).getText());if(value.equals(actual))return;SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<until);assertEquals(value,actual);}
    private static void clickDialog(String label){AccessibilityNodeInfo node=node(label,false);assertTrue(node.isClickable());assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));idle();}
    private static AccessibilityNodeInfo node(String value,boolean hint){long until=SystemClock.elapsedRealtime()+5000;do{AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&own(root)){AccessibilityNodeInfo found=findNode(root,value,hint);if(found!=null)return found;}SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<until);throw new AssertionError("Missing own-app dialog field: "+value);}
    private static boolean own(AccessibilityNodeInfo root){return context().getPackageName().contentEquals(root.getPackageName()==null?"":root.getPackageName());}
    private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo node,String value,boolean hint){CharSequence text=hint?node.getHintText():node.getText();if(text!=null&&(hint?value.contentEquals(text):value.equalsIgnoreCase(text.toString())))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){AccessibilityNodeInfo found=findNode(child,value,hint);if(found!=null)return found;}}return null;}
    private static <T extends View>T find(View view,Class<T> type,String text){if(type.isInstance(view)&&(text==null||view instanceof TextView&&text.contentEquals(((TextView)view).getText())))return type.cast(view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type,text);if(found!=null)return found;}}return null;}
    @SuppressWarnings("unchecked")private static List<Take> takes(MainActivity a){return(List<Take>)field(a,"takes");}
    private static Object field(Object a,String name){try{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private static Object call(Object a,String name){try{Method m=a.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(a);}catch(Exception e){throw new AssertionError(e);}}
    private static void empty(File dir){File[] children=dir.listFiles();assertTrue("No creator recovery inputs",!dir.exists()||children!=null&&children.length==0);}
    private static void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
