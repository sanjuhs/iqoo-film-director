package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Rect;
import android.widget.ScrollView;
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

/** Actual fixed-view geometry plus synthetic pending presentation; no Recording, preview or playback. */
@RunWith(AndroidJUnit4.class)
public final class FixedDirectControlsUiTest {
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



    @Test(timeout=45_000) public void idlePrimaryAndStopStayFullyVisibleOutsideScrollWithUnchangedVerticalPreview(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitControls(scenario);AtomicReference<Rect> original=new AtomicReference<>();
            scenario.onActivity(a->{Button record=(Button)field(a,"captureAction"),finish=(Button)field(a,"finishContinueButton");
                assertEquals("Start camera",record.getText().toString());assertTrue(record.isEnabled());assertEquals(View.GONE,finish.getVisibility());
                assertEquals(View.VISIBLE,record.getVisibility());assertFalse(finish.isEnabled());assertFixed(a,record);
                PreviewView preview=(PreviewView)field(a,"preview");assertEquals(9f/16f,(float)preview.getWidth()/preview.getHeight(),0.01f);
                original.set(bounds(record));ScrollView scroll=(ScrollView)field(a,"pageScroll");scroll.fullScroll(View.FOCUS_DOWN);
                assertNull(field(a,"capture"));assertNoSpeech(a);});
            idle();scenario.onActivity(a->{assertFixed(a,(Button)field(a,"captureAction"));assertEquals(original.get(),bounds((Button)field(a,"captureAction")));assertDenied();});
        }
    }
    @Test(timeout=45_000) public void pendingExplicitRequestUsesSameFixedSlotDisabledSavingAndStopRestoresIdle(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitControls(scenario);scenario.onActivity(a->{CaptureController c=install(a);Object timed=field(a,"recordingTimedStop"),quiet=field(a,"recordingQuietStop");
                MainActivity.FinishContinueRequest request=new MainActivity.FinishContinueRequest(c,c.getGeneration(),(Integer)field(a,"shootPoseGeneration"),7);
                request.requestStop(()->{});set(a,"finishContinueRequest",request);invoke(a,"updateFinishContinueButton");
                Button finish=(Button)field(a,"finishContinueButton"),record=(Button)field(a,"captureAction");
                assertEquals(View.VISIBLE,finish.getVisibility());assertEquals("Saving…",finish.getText().toString());assertFalse(finish.isEnabled());assertEquals(0.45f,finish.getAlpha(),0f);
                assertEquals(View.GONE,record.getVisibility());assertSame(record.getParent(),finish.getParent());
                assertFalse("Idle controller never becomes actual started recording",c.canFinishRecording());assertNull(field(c,"recording"));assertNull(field(c,"provider"));
                assertEquals(timed,field(a,"recordingTimedStop"));assertEquals(quiet,field(a,"recordingQuietStop"));});
            idle();scenario.onActivity(a->{Button finish=(Button)field(a,"finishContinueButton");assertFixed(a,finish);
                // Measure the full command on this actual fixed view while it remains
                // unavailable. This does not fabricate a started CameraX Recording.
                finish.setText("Finish & continue");});
            idle();scenario.onActivity(a->{Button finish=(Button)field(a,"finishContinueButton");assertFixed(a,finish);
                android.text.Layout label=finish.getLayout();assertNotNull("Full label has a layout",label);assertEquals("Finish & continue",label.getText().toString());
                int textWidth=finish.getWidth()-finish.getCompoundPaddingLeft()-finish.getCompoundPaddingRight();
                for(int line=0;line<label.getLineCount();line++){assertEquals("Full command is not ellipsized",0,label.getEllipsisCount(line));assertTrue("Full label fits slot width",label.getLineWidth(line)<=textWidth+1);}
                assertTrue("Full command fits slot height",label.getHeight()<=finish.getHeight()-finish.getCompoundPaddingTop()-finish.getCompoundPaddingBottom()+1);
                assertFalse(finish.isEnabled());finish.setText("Saving…");
                // A retained callback cannot repeat the request even if synthetic code reenables it.
                finish.setEnabled(true);finish.performClick();assertEquals(0,((List<?>)field(a,"takes")).size());
                Button stop=stop(a);assertTrue(stop.isEnabled());stop.performClick();assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));
                assertEquals(View.GONE,finish.getVisibility());Button record=(Button)field(a,"captureAction");assertEquals(View.VISIBLE,record.getVisibility());assertTrue(record.isEnabled());
                assertEquals("Start camera",record.getText().toString());assertNoSpeech(a);assertDenied();});
        }
    }
    @Test(timeout=45_000) public void preparationAndBusyDisablePrimaryAndOldRenderedControlCannotDispatch(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitControls(scenario);scenario.onActivity(a->{Button record=(Button)field(a,"captureAction");set(a,"countdown",true);invoke(a,"updateFinishContinueButton");
                assertFalse(record.isEnabled());assertEquals("Preparing…",record.getText().toString());assertEquals(View.GONE,((Button)field(a,"finishContinueButton")).getVisibility());
                stop(a).performClick();assertTrue(record.isEnabled());assertEquals(false,field(a,"countdown"));assertNoSpeech(a);
                Button oldStop=stop(a);invokeBusy(a,true);assertFalse(record.isEnabled());assertTrue(oldStop.isEnabled());
                CaptureController busyOwner=install(a);MainActivity.FinishContinueRequest busyRequest=new MainActivity.FinishContinueRequest(busyOwner,busyOwner.getGeneration(),(Integer)field(a,"shootPoseGeneration"),9);
                busyRequest.requestStop(()->{});set(a,"finishContinueRequest",busyRequest);invoke(a,"updateFinishContinueButton");
                ClipTranscriber stopDrain=new ClipTranscriber(a);set(a,"referenceSpeechReader",stopDrain);oldStop.performClick();
                assertNull("Current Stop must cancel the take while other local work is busy",field(a,"finishContinueRequest"));assertEquals(false,field(a,"sequenceActive"));assertEquals(true,field(a,"busy"));
                assertSame("Stop must not cancel or replace unrelated reference work",stopDrain,field(a,"referenceSpeechReader"));assertFalse(busyRequest.acceptSaved(busyOwner,busyOwner.getGeneration(),(Integer)field(a,"shootPoseGeneration"),9,1000));
                set(a,"referenceSpeechReader",null);stopDrain.close();busyOwner.close();set(a,"capture",null);set(a,"session",false);invokeBusy(a,false);assertTrue(record.isEnabled());
                ClipTranscriber draining=new ClipTranscriber(a);set(a,"referenceSpeechReader",draining);record.performClick();
                assertNull("Pending reference reader preserves existing Start drain barrier",field(a,"capture"));assertEquals(false,field(a,"session"));set(a,"referenceSpeechReader",null);draining.close();
                invoke(a,"render");record.setEnabled(true);record.performClick();assertNull(field(a,"capture"));assertEquals(false,field(a,"session"));assertNull(field(a,"finishContinueRequest"));
                CaptureController newerOwner=install(a);MainActivity.FinishContinueRequest newer=new MainActivity.FinishContinueRequest(newerOwner,newerOwner.getGeneration(),(Integer)field(a,"shootPoseGeneration"),10);
                newer.requestStop(()->{});set(a,"finishContinueRequest",newer);set(a,"countdown",true);invoke(a,"updateFinishContinueButton");
                oldStop.performClick();assertSame("Detached Stop cannot clear newer authorization",newer,field(a,"finishContinueRequest"));assertEquals(true,field(a,"countdown"));assertEquals(true,field(a,"sequenceActive"));
                stop(a).performClick();assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"countdown"));assertDenied();});
            awaitControls(scenario);scenario.onActivity(a->{assertTrue(((Button)field(a,"captureAction")).isEnabled());assertFixed(a,(Button)field(a,"captureAction"));});
        }
    }
    @Test(timeout=60_000) public void actualBackgroundCancelsSavingPresentationAndResumeRestoresAccessibleIdleControls(){
        AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<Button> retained=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitControls(scenario);scenario.onActivity(a->{activity.set(a);CaptureController c=install(a);
                MainActivity.FinishContinueRequest request=new MainActivity.FinishContinueRequest(c,c.getGeneration(),(Integer)field(a,"shootPoseGeneration"),8);request.requestStop(()->{});
                set(a,"finishContinueRequest",request);invoke(a,"updateFinishContinueButton");retained.set((Button)field(a,"finishContinueButton"));});
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertNull(field(a,"finishContinueRequest"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"sequenceActive"));
                retained.get().setEnabled(true);retained.get().performClick();assertNull(field(a,"finishContinueRequest"));assertFalse(((Button)field(a,"captureAction")).isEnabled());});
            scenario.moveToState(Lifecycle.State.RESUMED);awaitControls(scenario);
            scenario.onActivity(a->{Button record=(Button)field(a,"captureAction");assertEquals("Start camera",record.getText().toString());assertTrue(record.isEnabled());assertFixed(a,record);
                assertEquals(View.GONE,((Button)field(a,"finishContinueButton")).getVisibility());assertEquals(0,((List<?>)field(a,"takes")).size());
                CaptureController stopped=(CaptureController)field(a,"capture");assertFalse(stopped.isRecording());String status=((TextView)field(a,"status")).getText().toString();
                String timer=((TextView)field(a,"timerView")).getText().toString();String framing=((TextView)field(a,"framingView")).getText().toString();
                record.setEnabled(false);syntheticOldFinalize(a,stopped,(Integer)field(a,"shootPoseGeneration")-1);
                assertTrue("Current stopped source restores idle availability after its late acknowledgement",record.isEnabled());
                assertEquals(status,((TextView)field(a,"status")).getText().toString());assertEquals(timer,((TextView)field(a,"timerView")).getText().toString());assertEquals(framing,((TextView)field(a,"framingView")).getText().toString());assertEquals(0,((List<?>)field(a,"takes")).size());
                CaptureController replacement=install(a);set(a,"session",false);set(a,"sequenceActive",false);record.setEnabled(false);
                syntheticOldFinalize(a,stopped,(Integer)field(a,"shootPoseGeneration")-1);assertFalse("Replaced source cannot refresh current controls",record.isEnabled());
                assertNull(field(a,"finishContinueRequest"));assertNull(field(replacement,"provider"));invoke(a,"updateFinishContinueButton");assertNoSpeech(a);assertDenied();});
        }
    }
    private static void syntheticOldFinalize(MainActivity a,CaptureController source,int oldGeneration){
        try{Method m=MainActivity.class.getDeclaredMethod("onTakeFinalized",CaptureController.class,int.class,long.class,Shot.class,Uri.class,long.class);m.setAccessible(true);
            @SuppressWarnings("unchecked") List<Shot> shots=(List<Shot>)field(a,"shots");m.invoke(a,source,oldGeneration,8L,shots.get(0),Uri.parse("file:///synthetic-late-finalize.mp4"),1000L);
        }catch(Exception e){throw new AssertionError(e);}
    }
    private static void assertFixed(MainActivity a,Button primary){
        Button stop=stop(a);ScrollView scroll=(ScrollView)field(a,"pageScroll");View nav=(View)field(a,"nav");
        assertSame(primary.getParent(),stop.getParent());assertFalse(isDescendant(primary,scroll));assertFalse(isDescendant(stop,scroll));
        Rect first=bounds(primary),second=bounds(stop),navigation=bounds(nav),viewport=bounds(scroll);
        assertTrue("Full primary height reachable: "+first,first.height()>=primary.getHeight()-2);assertTrue(first.width()>=primary.getWidth()-2);
        assertTrue("Full Stop height reachable: "+second,second.height()>=stop.getHeight()-2);assertTrue(second.width()>=stop.getWidth()-2);
        assertEquals(first.top,second.top);assertEquals(first.bottom,second.bottom);assertTrue(first.left<second.left);
        assertTrue(first.top>=viewport.bottom-2);assertTrue(first.bottom<=navigation.top+2);assertTrue(stop.isEnabled());
        ViewGroup row=(ViewGroup)primary.getParent();int visible=0;for(int i=0;i<row.getChildCount();i++)if(row.getChildAt(i).getVisibility()==View.VISIBLE)visible++;
        assertEquals("Exactly one primary action plus Stop",2,visible);
    }
    private static Rect bounds(View view){Rect rect=new Rect();assertTrue("View has visible bounds",view.getGlobalVisibleRect(rect));return rect;}
    private static boolean isDescendant(View child,View ancestor){for(android.view.ViewParent p=child.getParent();p!=null;p=p.getParent())if(p==ancestor)return true;return false;}
    private static Button stop(MainActivity a){Button b=(Button)find((View)field(a,"root"),Button.class,"Stop take");assertNotNull(b);return b;}
    private static void awaitControls(ActivityScenario<MainActivity> scenario){
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;boolean[] ready={false};
        while(android.os.SystemClock.elapsedRealtime()<deadline){idle();scenario.onActivity(a->{Button primary=(Button)field(a,"captureAction");ready[0]=primary!=null&&primary.isLaidOut()&&!primary.isLayoutRequested()&&primary.getHeight()>0&&primary.isEnabled();});
            if(ready[0])return;try{Thread.sleep(16);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
        fail("Idle fixed controls did not finish layout and become available");
    }
    private static void invokeBusy(MainActivity a,boolean value){try{Method m=MainActivity.class.getDeclaredMethod("setBusy",boolean.class);m.setAccessible(true);m.invoke(a,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoSpeech(MainActivity a){assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static CaptureController install(MainActivity a){
        CaptureController old=(CaptureController)field(a,"capture");if(old!=null)old.close();
        CaptureController c=new CaptureController(a,new PreviewView(a),new CaptureController.Listener(){
            public void onReady(){fail("No preview requested");}public void onRecordingStarted(){fail("No recording requested");}
            public void onRecordingFinished(Uri uri,long duration){fail("No recording requested");}public void onError(String message){fail(message);}
        });set(a,"capture",c);set(a,"session",true);set(a,"guideSequence",true);set(a,"sequenceActive",true);set(a,"voice",false);return c;
    }
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}

    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
