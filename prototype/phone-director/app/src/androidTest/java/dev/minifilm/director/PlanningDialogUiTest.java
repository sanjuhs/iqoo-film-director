package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
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

/** Actual planning dialogs with absent models and metadata-only takes; no inference or source reads. */
@RunWith(AndroidJUnit4.class)
public final class PlanningDialogUiTest {
    private SharedPreferences prefs;private Map<String,?> original;
    @Before public void requireOwnedEmptyUnlockedEmulatorAndBackupPreferences()throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.US);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());denied();
        empty(new File(context().getFilesDir(),"takes"));empty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        prefs=context().getSharedPreferences("shoot",0);original=new HashMap<>(prefs.getAll());
        assertTrue(prefs.edit().clear().putString("state",new JSONObject().put("voice",false).put("tab",0).toString()).commit());
    }
    @After public void restoreAllPreferences(){if(original!=null){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
        Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked")Set<String> values=(Set<String>)v;edit.putStringSet(k,new HashSet<>(values));}else throw new AssertionError("Unsupported preference type");
    }assertTrue(edit.commit());assertEquals(original,prefs.getAll());}denied();}

    @Test(timeout=60_000) public void keepPlanSynchronouslyRejectsRetainedReplaceAndPreservesReviewedEdits(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            prepare(scenario);openReplace(scenario);
            scenario.onActivity(a->{String plan=plan(a),state=prefs.getString("state","");Take take=takes(a).get(0);AlertDialog d=dialog(a);Button old=d.getButton(-1);
                d.getButton(-2).performClick();assertNull(field(a,"planningDialog"));old.performClick();assertEquals(plan,plan(a));assertEquals(state,prefs.getString("state",""));assertSame(take,takes(a).get(0));assertEquals(1,field(a,"shotIndex"));quiet(a);});
        }
    }
    @Test(timeout=60_000) public void currentReplaceUsesLabelledTemplateKeepsTakeEditsAndRestartsFirstShot(){
        AtomicReference<String> takeJson=new AtomicReference<>();AtomicReference<String> oldPlan=new AtomicReference<>();AtomicReference<Take> take=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            prepare(scenario);openReplace(scenario);scenario.onActivity(a->{take.set(takes(a).get(0));takeJson.set(invoke(a,"serializeTakes").toString());oldPlan.set(plan(a));assertTrue(dialog(a).getButton(-1).performClick());assertEquals(oldPlan.get(),plan(a));});idle();
            scenario.onActivity(a->{assertTrue(dialog(a).isShowing());assertTrue(dialog(a).getButton(-1).performClick());assertNull(field(a,"planningDialog"));assertNotEquals(oldPlan.get(),plan(a));assertEquals("Editable template · local AI unavailable",field(a,"planSource"));assertEquals(0,field(a,"shotIndex"));assertSame(take.get(),takes(a).get(0));assertEquals(takeJson.get(),invoke(a,"serializeTakes").toString());quiet(a);});
        }
    }
    @Test(timeout=60_000) public void fallbackKeepAndCurrentExplicitTemplateHaveSeparateOwnershipAndFailureLabel(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            prepare(scenario);scenario.onActivity(a->invoke(a,"showPlannerFallback",new Class<?>[]{String.class},"Synthetic local planner failure"));idle();
            scenario.onActivity(a->{String before=plan(a),state=prefs.getString("state","");Button old=dialog(a).getButton(-1);dialog(a).getButton(-2).performClick();old.performClick();assertEquals(before,plan(a));assertEquals(state,prefs.getString("state",""));invoke(a,"showPlannerFallback",new Class<?>[]{String.class},"Synthetic local planner failure");});idle();
            scenario.onActivity(a->{String facts=invoke(a,"serializeTakes").toString();dialog(a).getButton(-1).performClick();assertEquals("Editable template · AI failed",field(a,"planSource"));assertEquals(facts,invoke(a,"serializeTakes").toString());assertEquals(0,field(a,"shotIndex"));assertNull(field(a,"planningDialog"));quiet(a);});
        }
    }
    @Test(timeout=90_000) public void changedInputsNewWorkRenderAndActualBackgroundRejectRetainedReplacement(){
        AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<Button> old=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            for(int mode=0;mode<7;mode++){prepare(scenario);openReplace(scenario);final int change=mode;
                scenario.onActivity(a->{Button retained=dialog(a).getButton(-1);if(change==0)set(a,"brief","Newer creator brief");else if(change==1)set(a,"style","Introduction");else if(change==2)shots(a).get(0).instruction="Newer reviewed direction";else if(change==3)set(a,"referenceSummary","Newer reviewed visual note");else if(change==4)set(a,"referenceVideoUri",Uri.parse("content://synthetic.invalid/new-reference"));else if(change==5)set(a,"busy",true);else invoke(a,"render");
                    String facts=plan(a),state=prefs.getString("state","");retained.performClick();assertEquals(facts,plan(a));assertEquals(state,prefs.getString("state",""));assertEquals("Creator-reviewed synthetic plan",field(a,"planSource"));set(a,"busy",false);invoke(a,"render");assertNull(field(a,"planningDialog"));quiet(a);});idle();
            }
            prepare(scenario);openReplace(scenario);scenario.onActivity(a->{activity.set(a);old.set(dialog(a).getButton(-1));});scenario.moveToState(Lifecycle.State.CREATED);assertEquals(Lifecycle.State.CREATED,scenario.getState());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertNull(field(a,"planningDialog"));String facts=plan(a),state=prefs.getString("state","");old.get().performClick();assertEquals(facts,plan(a));assertEquals(state,prefs.getString("state",""));});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{String facts=plan(a);old.get().performClick();assertEquals(facts,plan(a));assertNull(field(a,"planningDialog"));quiet(a);});
        }
    }
    private void prepare(ActivityScenario<MainActivity> scenario){scenario.onActivity(a->{takes(a).clear();Take take=new Take(Uri.parse("content://synthetic.invalid/planning-dialog/source"),"reviewed-1","Kept take","Kept caption",6000);take.inMs=250;take.outMs=5746;take.captionOrigin="creator-reviewed-offline-asr";take.subtitles.add(new SubtitleCue(500,1000,"Kept reviewed words"));take.reviewedShotIds.add("reviewed-1");takes(a).add(take);
        shots(a).clear();shots(a).add(new Shot("reviewed-1","My edited hero","Pose exactly as I reviewed.","My reviewed title",5000));shots(a).add(new Shot("reviewed-2","My edited close","Wave exactly as I reviewed.","My reviewed ending",5000));
        set(a,"brief","A simple outfit reel");set(a,"style","Fashion");set(a,"planSource","Creator-reviewed synthetic plan");set(a,"shotIndex",1);set(a,"tab",0);set(a,"referenceVideoUri",null);set(a,"referenceSummary","");invoke(a,"save");invoke(a,"render");});idle();}
    private static void openReplace(ActivityScenario<MainActivity> scenario){scenario.onActivity(a->{Button b=(Button)find((View)field(a,"root"),"Build my shot plan");assertNotNull(b);ScrollView scroll=(ScrollView)field(a,"pageScroll");int[] at=new int[2],top=new int[2];b.getLocationOnScreen(at);scroll.getLocationOnScreen(top);scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));});idle();scenario.onActivity(a->{Button b=(Button)find((View)field(a,"root"),"Build my shot plan");Rect r=new Rect();assertTrue(b.getGlobalVisibleRect(r));assertTrue(r.height()>=b.getHeight()-2);assertTrue(b.performClick());});idle();scenario.onActivity(a->assertTrue(dialog(a).isShowing()));}
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"planningDialog");assertNotNull(d);assertTrue(d.isShowing());return d;}
    private static String plan(MainActivity a){return invoke(a,"serializeShots").toString();}
    private static void quiet(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());denied();}
    private static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    @SuppressWarnings("unchecked")private static List<Take> takes(MainActivity a){return(List<Take>)field(a,"takes");}
    @SuppressWarnings("unchecked")private static List<Shot> shots(MainActivity a){return(List<Shot>)field(a,"shots");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){return invoke(o,name,new Class<?>[0]);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void empty(File dir){File[] entries=dir.listFiles();assertTrue(!dir.exists()||entries!=null&&entries.length==0);}
    private static void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
