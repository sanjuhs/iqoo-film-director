package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Switch;
import androidx.lifecycle.Lifecycle;
import androidx.camera.view.PreviewView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

/** Synthetic finalization callbacks and idle controllers only; no CameraX recording or media validation. */
@RunWith(AndroidJUnit4.class)
public final class FinishContinueUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    @Before public void requireOwnEmptyUnlockedEmulatorAndBackupAllPreferences() throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.US);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());assertDenied();
        assertEmpty(new File(context().getFilesDir(),"takes"));assertEmpty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",1).put("voice",false).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreEntirePreferences(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }



    @Test(timeout=45_000) public void idleCameraCannotFinishAndRetainedOldControlCannotStartWork(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{Button old=(Button)field(a,"finishContinueButton");assertNotNull(old);assertFalse(old.isEnabled());
                CaptureController controller=install(a);assertFalse(controller.canFinishRecording());set(controller,"recordingStarted",true);
                assertFalse("Start flag alone is not a real Recording",controller.canFinishRecording());
                invoke(a,"updateFinishContinueButton");assertFalse(old.isEnabled());assertNull(field(controller,"provider"));
                invoke(a,"render");old.setEnabled(true);old.performClick();assertNull(field(a,"finishContinueRequest"));assertEquals(0,takes(a).size());
                assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertDenied();});
        }
    }
    @Test(timeout=45_000) public void syntheticSavedAcknowledgementFinishesLastShotOnceAndKeepsStopSnapshots(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{CaptureController controller=install(a);int generation=(Integer)field(a,"shootPoseGeneration");
                List<Shot> shots=shots(a);set(a,"shotIndex",shots.size()-1);Shot shot=shots.get(shots.size()-1);
                Object timed=field(a,"recordingTimedStop"),quiet=field(a,"recordingQuietStop");
                MainActivity.FinishContinueRequest request=stage(a,controller,generation,41);assertEquals(0,takes(a).size());
                Uri synthetic=Uri.parse("file:///synthetic-finish-ack.mp4");ack(a,controller,generation,41,shot,synthetic,5746);
                assertEquals(1,takes(a).size());assertEquals(shot.id,takes(a).get(0).shotId);assertEquals(synthetic,takes(a).get(0).uri);
                assertEquals(2,field(a,"tab"));assertEquals(false,field(a,"sequenceActive"));assertNull(field(a,"finishContinueRequest"));
                assertEquals(timed,field(a,"recordingTimedStop"));assertEquals(quiet,field(a,"recordingQuietStop"));
                ack(a,controller,generation,41,shot,synthetic,5746);assertEquals(1,takes(a).size());
                assertFalse(request.acceptSaved(controller,controller.getGeneration(),generation,41,5746));
                assertNull(field(a,"capture"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertDenied();
            });
        }
    }
    @Test(timeout=45_000) public void staleAndInvalidAcknowledgementsCannotTouchNewerSequenceOrAuthorization(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{CaptureController old=install(a);int oldGeneration=(Integer)field(a,"shootPoseGeneration");invoke(a,"render");
                CaptureController current=install(a);int generation=(Integer)field(a,"shootPoseGeneration");Shot shot=shots(a).get(0);
                set(a,"sequenceActive",true);String status=((TextView)field(a,"status")).getText().toString();
                ack(a,old,oldGeneration,1,shot,Uri.parse("file:///old.mp4"),1000);
                assertEquals(true,field(a,"sequenceActive"));assertEquals(status,((TextView)field(a,"status")).getText().toString());assertEquals(0,takes(a).size());
                MainActivity.FinishContinueRequest newer=stage(a,current,generation,2);
                ack(a,old,oldGeneration,1,shot,Uri.parse("file:///old.mp4"),1000);assertSame(newer,field(a,"finishContinueRequest"));
                ack(a,current,generation,1,shot,Uri.parse("file:///wrong-id.mp4"),1000);assertSame(newer,field(a,"finishContinueRequest"));
                assertEquals(0,takes(a).size());assertEquals(0,field(a,"shotIndex"));
                ack(a,current,generation,2,shot,Uri.parse("file:///short.mp4"),300);
                assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));assertEquals(0,takes(a).size());assertEquals(0,field(a,"shotIndex"));
                assertNull(field(current,"provider"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertDenied();
            });
        }
    }
    @Test(timeout=60_000) public void stopGuideOffRenderAndActualBackgroundCancelPendingAdvance(){
        AtomicReference<MainActivity> activity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);CaptureController c=install(a);int g=(Integer)field(a,"shootPoseGeneration");
                MainActivity.FinishContinueRequest stopped=stage(a,c,g,1);Button stop=(Button)find((View)field(a,"root"),Button.class,"Stop take");assertNotNull(stop);stop.performClick();
                assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));assertFalse(stopped.acceptSaved(c,c.getGeneration(),g,1,1000));
                stage(a,c,g,2);Switch guide=(Switch)find((View)field(a,"root"),Switch.class,"Guide the full shot sequence");guide.setChecked(false);
                assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));
                stage(a,c,g,3);invoke(a,"render");assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));
                c=install(a);stage(a,c,(Integer)field(a,"shootPoseGeneration"),4);set(a,"reviewAfterSave",true);
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"reviewAfterSave"));});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));assertEquals(0,takes(a).size());assertDenied();});
        }
    }
    private static CaptureController install(MainActivity a){
        CaptureController old=(CaptureController)field(a,"capture");if(old!=null)old.close();
        CaptureController c=new CaptureController(a,new PreviewView(a),new CaptureController.Listener(){
            public void onReady(){fail("No preview requested");}public void onRecordingStarted(){fail("No recording requested");}
            public void onRecordingFinished(Uri uri,long duration){fail("No recording requested");}public void onError(String message){fail(message);}
        });set(a,"capture",c);set(a,"session",true);set(a,"guideSequence",true);set(a,"sequenceActive",true);set(a,"voice",false);return c;
    }
    private static MainActivity.FinishContinueRequest stage(MainActivity a,CaptureController c,int generation,long id){
        set(a,"session",true);set(a,"sequenceActive",true);set(a,"guideSequence",true);
        Switch guide=(Switch)find((View)field(a,"root"),Switch.class,"Guide the full shot sequence");if(guide!=null)guide.setChecked(true);
        MainActivity.FinishContinueRequest r=new MainActivity.FinishContinueRequest(c,c.getGeneration(),generation,id);
        r.requestStop(()->{});set(a,"finishContinueRequest",r);return r;
    }
    private static void ack(MainActivity a,CaptureController c,int generation,long id,Shot shot,Uri uri,long duration){
        try{Method m=MainActivity.class.getDeclaredMethod("onTakeFinalized",CaptureController.class,int.class,long.class,Shot.class,Uri.class,long.class);m.setAccessible(true);m.invoke(a,c,generation,id,shot,uri,duration);}catch(Exception e){throw new AssertionError(e);}
    }
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    @SuppressWarnings("unchecked") private static List<Shot> shots(MainActivity a){return (List<Shot>)field(a,"shots");}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}

    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
