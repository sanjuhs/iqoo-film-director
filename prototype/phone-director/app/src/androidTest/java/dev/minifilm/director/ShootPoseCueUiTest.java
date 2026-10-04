package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.camera.view.PreviewView;
import androidx.camera.core.CameraSelector;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Synthetic session delivery only: no preview, ML inference, microphone or TTS playback. */
@RunWith(AndroidJUnit4.class)
public final class ShootPoseCueUiTest {
    private SharedPreferences preferences;
    private Map<String,?> original;
    private boolean changed;

    @Before public void unlockedDeniedAndPreservePreferences() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager lock=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the test device before running UI tests",lock!=null&&lock.isKeyguardLocked());
        assertDenied(context);
        preferences=context.getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",1)
                .put("brief","Synthetic outfit cue test").put("voice",false).toString()).commit());
    }
    @After public void restorePreferencesAndDeniedPermissions(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()){
                String key=entry.getKey();Object value=entry.getValue();
                if(value instanceof String)editor.putString(key,(String)value);
                else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof Long)editor.putLong(key,(Long)value);
                else if(value instanceof Float)editor.putFloat(key,(Float)value);
                else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)value;editor.putStringSet(key,new HashSet<>(strings));}
                else throw new AssertionError("Unsupported preference type");
            }assertTrue(editor.commit());assertEquals(original,preferences.getAll());
        }assertDenied(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test(timeout=30_000) public void alreadyQueuedOldPoseCannotReplaceNewShotAfterRender(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);int old=(Integer)field(a,"shootPoseGeneration");
                // Same main-loop delivery as a worker result already queued before replacement.
                new Handler(Looper.getMainLooper()).post(()->apply(a,old,"Old shot cue",5,20));
                invoke(a,"render",new Class<?>[0]);assertTrue((Integer)field(a,"shootPoseGeneration")>old);
                installIdleCapture(a);apply(a,(Integer)field(a,"shootPoseGeneration"),"Current shot cue",9,25);
                assertEquals("Current shot cue",framing(a));assertIdleCapture(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals("Current shot cue",framing(a));assertTrue(status(a).contains("9 visible points"));assertIdleCapture(a);});
        }
    }

    @Test(timeout=30_000) public void backgroundInvalidatesOldPoseEvenWhenCreatorResumesNewSession(){
        AtomicInteger old=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{installIdleCapture(a);old.set((Integer)field(a,"shootPoseGeneration"));});
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{
                assertTrue((Integer)field(a,"shootPoseGeneration")>old.get());
                assertEquals(false,field(a,"session"));installIdleCapture(a);
                int current=(Integer)field(a,"shootPoseGeneration");apply(a,current,"Resumed shot cue",11,30);
                String status=status(a);apply(a,old.get(),"Old background cue",2,900);
                assertEquals("Resumed shot cue",framing(a));assertEquals(status,status(a));assertIdleCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void pendingAndActiveDirectionKeepExactSpeechWhileVisualPoseUpdatesThenIdleCanRequestCue(){
        DenyFocus focus=new DenyFocus();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);SpeechCoach previous=(SpeechCoach)field(a,"speech");previous.close();
                SpeechCoach coach=new SpeechCoach(a,focus);set(a,"speech",coach);set(a,"voice",true);
                int generation=(Integer)field(a,"shootPoseGeneration");set(a,"lastCue",-12_001L);
                String direction="Stand wearing your outfit, then hold your pose.";
                set(coach,"pendingSpeech",direction);set(coach,"ready",false);
                assertTrue(coach.hasSpeechWork());apply(a,generation,"Pending visual cue",12,40);
                assertEquals("Pending visual cue",framing(a));assertEquals(direction,field(coach,"pendingSpeech"));
                assertEquals(0,focus.requests);assertEquals(-12_001L,field(a,"lastCue"));
                set(coach,"pendingSpeech",null);set(coach,"ready",true);set(coach,"activeUtterance","synthetic-direction");
                AudioFocusRequest lease=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build()).build();
                set(coach,"activeFocus",lease);
                assertTrue(coach.hasSpeechWork());apply(a,generation,"Active visual cue",13,45);
                assertEquals("Active visual cue",framing(a));assertEquals("synthetic-direction",field(coach,"activeUtterance"));
                assertSame(lease,field(coach,"activeFocus"));assertEquals(0,focus.requests);assertEquals(0,focus.abandons);
                invoke(coach,"finishSpeech",new Class<?>[]{String.class,boolean.class},"synthetic-direction",true);
                assertFalse(coach.hasSpeechWork());assertEquals(1,focus.abandons);
                apply(a,generation,"Idle visual cue",14,50);
                assertEquals("Idle visual cue",framing(a));assertEquals("Eligible idle cue must request focus",1,focus.requests);
                assertNull("Denied focus must never arm playback",field(coach,"speechTimeout"));assertIdleCapture(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{SpeechCoach coach=(SpeechCoach)field(a,"speech");assertFalse(coach.hasSpeechWork());assertEquals(1,focus.requests);assertIdleCapture(a);});
        }
    }

    @Test(timeout=30_000) public void stoppedSessionAndCountdownRejectPoseWithoutChangingDirectionOrFocus(){
        DenyFocus focus=new DenyFocus();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);((SpeechCoach)field(a,"speech")).close();SpeechCoach coach=new SpeechCoach(a,focus);
                set(a,"speech",coach);set(a,"voice",true);set(coach,"ready",true);set(a,"lastCue",-12_001L);
                int generation=(Integer)field(a,"shootPoseGeneration");String before=framing(a),status=status(a);
                set(a,"countdown",true);apply(a,generation,"Countdown must stay quiet",6,20);
                assertEquals(before,framing(a));assertEquals(status,status(a));assertEquals(0,focus.requests);
                set(a,"countdown",false);set(a,"session",false);apply(a,generation,"Ended session cue",7,20);
                assertEquals(before,framing(a));assertEquals(status,status(a));assertFalse(coach.hasSpeechWork());assertEquals(0,focus.requests);assertIdleCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void rapidLensSwitchIsRefusedWhileBindingButStoppedControllerAllowsSavedSelection(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);CaptureController capture=(CaptureController)field(a,"capture");
                int desired=(Integer)field(a,"desiredLens"),controllerLens=capture.getLensFacing();
                int generation=(Integer)field(a,"shootPoseGeneration");
                set(capture,"previewRequested",true);set(capture,"ready",false);
                assertTrue(capture.isPreviewPending());
                Button toggle=button((View)field(a,"root"),"Switch front / back");assertNotNull(toggle);toggle.performClick();
                assertEquals(desired,field(a,"desiredLens"));assertEquals(controllerLens,capture.getLensFacing());
                assertSame(capture,field(a,"capture"));assertTrue(capture.isPreviewPending());
                assertEquals(generation,field(a,"shootPoseGeneration"));assertNull(field(capture,"provider"));assertDenied(a);
                // Exercise the real stop state transition without ever requesting a preview.
                capture.stopPreview();set(a,"session",false);assertFalse(capture.isPreviewPending());toggle.performClick();
                int opposite=desired==CameraSelector.LENS_FACING_FRONT?CameraSelector.LENS_FACING_BACK:CameraSelector.LENS_FACING_FRONT;
                assertEquals(opposite,field(a,"desiredLens"));assertEquals(controllerLens,capture.getLensFacing());
                assertSame(capture,field(a,"capture"));assertEquals(false,field(a,"live"));assertEquals(false,field(a,"session"));
                assertEquals(generation,field(a,"shootPoseGeneration"));assertIdleCapture(a);
                capture.close();assertFalse(capture.isPreviewPending());
            });
        }
    }

    private static final class DenyFocus implements SpeechCoach.FocusControl {
        int requests,abandons;
        public int request(AudioFocusRequest request,AudioManager.OnAudioFocusChangeListener listener){
            assertEquals(Looper.getMainLooper(),Looper.myLooper());requests++;return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
        public void abandon(AudioFocusRequest request){abandons++;}
    }
    private static void installIdleCapture(MainActivity a){
        CaptureController old=(CaptureController)field(a,"capture");if(old!=null)old.close();
        CaptureController idle=new CaptureController(a,(PreviewView)field(a,"preview"),new CaptureController.Listener(){
            public void onReady(){fail("Synthetic test must never start preview");}
            public void onRecordingStarted(){fail("Synthetic test must never record");}
            public void onRecordingFinished(Uri uri,long duration){fail("Synthetic test must never create media");}
            public void onError(String message){fail("Synthetic test must never start camera work");}
        });
        set(a,"capture",idle);set(a,"session",true);set(a,"live",false);set(a,"countdown",false);
        assertIdleCapture(a);
    }
    private static void assertIdleCapture(MainActivity a){
        CaptureController capture=(CaptureController)field(a,"capture");assertNotNull(capture);assertFalse(capture.isRecording());
        assertEquals(false,field(capture,"previewRequested"));assertEquals(false,field(capture,"ready"));assertNull(field(capture,"provider"));assertNull(field(capture,"recording"));assertDenied(a);
    }
    private static void assertDenied(Context context){
        assertEquals("Camera must remain denied",PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals("Microphone must remain denied",PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
    }
    private static String framing(MainActivity a){return ((TextView)field(a,"framingView")).getText().toString();}
    private static String status(MainActivity a){return ((TextView)field(a,"status")).getText().toString();}
    private static Button button(View view,String label){
        if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    private static void apply(MainActivity a,int generation,String cue,int count,long latency){invoke(a,"applyPoseCue",new Class<?>[]{int.class,String.class,int.class,long.class},generation,cue,count,latency);}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object target,String name,Class<?>[] types,Object...args){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}catch(Exception e){throw new AssertionError(e);}}
}
