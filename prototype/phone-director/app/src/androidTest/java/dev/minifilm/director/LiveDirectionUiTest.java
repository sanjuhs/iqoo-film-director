package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Button;
import android.net.Uri;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import java.util.concurrent.atomic.AtomicReference;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Actual live-direction settings and idle-controller cancellation only; no recording or speech playback. */
@RunWith(AndroidJUnit4.class)
public final class LiveDirectionUiTest {
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

    @Test(timeout=60_000) public void quietDialogueChoicePersistsAndReenablingWaitsForAnActualNextTake()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{assertEquals(true,field(a,"liveDuringTakes"));assertTrue(liveSwitch(a).isChecked());assertNotNull(find((View)field(a,"root"),Button.class,"Test voice"));assertNoMedia(a);});
            clickSwitch(scenario,"Talk during takes");assertFalse(new JSONObject(preferences.getString("state","{}")).getBoolean("liveDuringTakes"));scenario.recreate();idle();
            scenario.onActivity(a->{assertFalse(liveSwitch(a).isChecked());assertEquals(false,field(a,"liveDuringTakes"));assertEquals(false,field(a,"liveDirectorActive"));assertNoMedia(a);});
            clickSwitch(scenario,"Talk during takes");assertTrue(new JSONObject(preferences.getString("state","{}")).getBoolean("liveDuringTakes"));
            scenario.onActivity(a->{assertEquals(false,field(a,"liveDirectorActive"));assertNull(field(a,"liveDirectorOwner"));assertEquals(0L,field(a,"liveDirectorRecordingId"));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void quietToggleInvalidatesOwnedCandidateAndTurningBackOnDoesNotResume(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();AtomicReference<Staged> staged=new AtomicReference<>();scenario.onActivity(a->staged.set(stage(a)));
            clickSwitch(scenario,"Talk during takes");scenario.onActivity(a->{assertEnded(a,staged.get());assertIdleCapture(a);});
            clickSwitch(scenario,"Talk during takes");scenario.onActivity(a->{assertEquals(true,field(a,"liveDuringTakes"));assertEnded(a,staged.get());assertIdleCapture(a);});
        }
    }
    @Test(timeout=60_000) public void voiceOffStopRenderAndRealBackgroundClearOnlySyntheticOwnedDirection(){
        AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<Staged> staged=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            clickSwitch(scenario,"Spoken direction / earbuds");scenario.onActivity(a->staged.set(stage(a)));clickSwitch(scenario,"Spoken direction / earbuds");
            scenario.onActivity(a->{assertEnded(a,staged.get());assertIdleCapture(a);staged.set(stage(a));invoke(a,"updateFinishContinueButton");Button stop=(Button)field(a,"stopTakeButton");assertTrue(stop.isShown());assertTrue(stop.isEnabled());assertTrue(stop.performClick());assertEnded(a,staged.get());assertNoMedia(a);
                staged.set(stage(a));invoke(a,"render");assertEnded(a,staged.get());assertNoMedia(a);});idle();
            scenario.onActivity(a->{activity.set(a);staged.set(stage(a));});scenario.moveToState(Lifecycle.State.CREATED);assertEquals(Lifecycle.State.CREATED,scenario.getState());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertEnded(activity.get(),staged.get());assertIdleCapture(activity.get());});
            scenario.moveToState(Lifecycle.State.RESUMED);idle();scenario.onActivity(a->{assertEnded(a,staged.get());assertEquals(false,field(a,"liveDirectorActive"));assertIdleCapture(a);});
        }
    }
    @Test(timeout=60_000) public void choosingQuietDuringPreparationCancelsThePendingStartWithoutRecording(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{stage(a);set(a,"countdown",true);});
            clickSwitch(scenario,"Talk during takes");scenario.onActivity(a->{assertEquals(false,field(a,"countdown"));assertEquals(false,field(a,"sequenceActive"));assertEquals(false,field(a,"liveDirectorActive"));assertIdleCapture(a);assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());});
        }
    }
    @Test(timeout=60_000) public void idleControllerCannotAdmitLearnedPoseOrStartLiveDirection(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){idle();scenario.onActivity(a->{
            Staged staged=stage(a);set(a,"voice",true);set(a,"session",true);String before=((TextView)field(a,"framingView")).getText().toString();
            invoke(a,"offerLivePose",new Class<?>[]{String.class,int.class,long.class},"Synthetic learned framing cue",1,100L);
            assertEquals(before,((TextView)field(a,"framingView")).getText().toString());assertIdleCapture(a);
            int generation=(Integer)field(a,"shootPoseGeneration");invoke(a,"beginLiveDirection",new Class<?>[]{CaptureController.class,int.class},staged.capture,generation);
            assertEnded(a,staged);assertIdleCapture(a);set(a,"voice",false);set(a,"session",false);
        });}
    }
    private static final class Staged{final CaptureController capture;final LiveDirectionPolicy policy;final LiveDirectionPolicy.Candidate candidate;final int epoch;Staged(CaptureController c,LiveDirectionPolicy p,LiveDirectionPolicy.Candidate candidate,int epoch){capture=c;policy=p;this.candidate=candidate;this.epoch=epoch;}}
    private static Staged stage(MainActivity a){CaptureController old=(CaptureController)field(a,"capture");if(old!=null)old.close();
        CaptureController c=new CaptureController(a,new PreviewView(a),new CaptureController.Listener(){public void onReady(){fail("No preview starts");}public void onRecordingStarted(){fail("No recording starts");}public void onRecordingFinished(Uri u,long duration){fail("No media finalizes");}public void onError(String message){fail("No camera operation requested");}});
        set(a,"capture",c);int epoch=(Integer)field(a,"liveDirectorGeneration")+1;set(a,"liveDirectorGeneration",epoch);set(a,"liveDirectorActive",true);set(a,"liveDirectorOwner",c);set(a,"liveDirectorRecordingId",71L);
        LiveDirectionPolicy p=(LiveDirectionPolicy)field(a,"liveDirections");p.reset(epoch,1000,8000,"Synthetic reviewed action");LiveDirectionPolicy.Candidate candidate=p.next(epoch,1000,true,false);assertNotNull(candidate);return new Staged(c,p,candidate,epoch);
    }
    private static void assertEnded(MainActivity a,Staged s){assertEquals(false,field(a,"liveDirectorActive"));assertNull(field(a,"liveDirectorOwner"));assertEquals(0L,field(a,"liveDirectorRecordingId"));assertTrue((Integer)field(a,"liveDirectorGeneration")>s.epoch);assertFalse("Retained policy proposal cannot commit after ending",s.policy.commit(s.candidate,1000));assertNull(s.policy.next(s.epoch,1000,true,false));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());}
    private static void assertIdleCapture(MainActivity a){CaptureController c=(CaptureController)field(a,"capture");if(c!=null){assertFalse(c.isRecording());assertEquals(0,c.getRecordingId());assertNull(field(c,"provider"));}assertNull(field(a,"pose"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static Switch liveSwitch(MainActivity a){Switch s=(Switch)find((View)field(a,"root"),Switch.class,"Talk during takes");assertNotNull(s);return s;}
    private static Object invoke(Object o,String name){return invoke(o,name,new Class<?>[0]);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}

    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void clickSwitch(ActivityScenario<MainActivity> scenario,String label){
        awaitVisibleSwitch(scenario,label);
        scenario.onActivity(a->{
            Switch s=(Switch)find((View)field(a,"root"),Switch.class,label);
            assertNotNull("Current switch exists: "+label,s);
            Rect visible=new Rect();
            assertTrue("Switch has a visible global rectangle: "+label,s.getGlobalVisibleRect(visible));
            assertTrue("Full switch height is reachable: "+label+" rect="+visible+" height="+s.getHeight(),visible.height()>=s.getHeight()-2);
            assertTrue("Full switch width is reachable: "+label+" rect="+visible+" width="+s.getWidth(),visible.width()>=s.getWidth()-2);
            assertTrue("Switch is enabled before creator click: "+label,s.isEnabled());
            boolean before=s.isChecked();
            // CompoundButton.performClick toggles checked state, but its return value only
            // reports an assigned OnClickListener. These controls use OnCheckedChangeListener.
            s.performClick();
            assertEquals("Creator click changes the actual checked state: "+label,!before,s.isChecked());
        });idle();
    }
    private static void awaitVisibleSwitch(ActivityScenario<MainActivity> scenario,String label){
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;boolean[] visible={false};String[] bounds={"layout not observed"};
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            idle();
            scenario.onActivity(a->{
                Switch s=(Switch)find((View)field(a,"root"),Switch.class,label);assertNotNull("Current switch exists: "+label,s);
                ScrollView scroll=(ScrollView)field(a,"pageScroll");
                if(!s.isLaidOut()||s.isLayoutRequested()||s.getHeight()<=0||s.getWidth()<=0||scroll.getHeight()<=0){visible[0]=false;return;}
                int[] at=new int[2],top=new int[2];s.getLocationOnScreen(at);scroll.getLocationOnScreen(top);
                scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));
                Rect rect=new Rect();boolean shown=s.getGlobalVisibleRect(rect);
                bounds[0]="rect="+rect+" switch="+s.getWidth()+"x"+s.getHeight()+" scrollY="+scroll.getScrollY();
                visible[0]=shown&&rect.height()>=s.getHeight()-2&&rect.width()>=s.getWidth()-2;
            });
            if(visible[0])return;
            try{Thread.sleep(16);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError("Interrupted while awaiting switch layout",interrupted);}
        }
        fail("Current switch did not become fully visible after scrolling: "+label+" "+bounds[0]);
    }
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
