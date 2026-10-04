package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.camera.core.CameraState;
import androidx.camera.view.PreviewView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

/** Synthetic binding events on idle controllers. No provider, camera, microphone or recording starts. */
@RunWith(AndroidJUnit4.class)
public final class CaptureReadinessUiTest {
    private SharedPreferences prefs;private Map<String,?> original;
    @Before public void requireUnlockedOwnedEmulatorAndBackupPrefs()throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.US);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());denied();
        prefs=context().getSharedPreferences("shoot",0);original=new HashMap<>(prefs.getAll());
        assertTrue(prefs.edit().clear().putString("state",new JSONObject().put("voice",false).put("tab",0).toString()).commit());
    }
    @After public void restoreCompletePrefs(){if(original!=null){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:original.entrySet()){
        String k=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked")Set<String> values=(Set<String>)v;e.putStringSet(k,new HashSet<>(values));}else throw new AssertionError("Unsupported pref type");
    }assertTrue(e.commit());assertEquals(original,prefs.getAll());}denied();}

    @Test(timeout=45_000) public void bindingNeedsOpenAndFreshStreamingAndPublishesReadyOnlyOnce(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{Events events=new Events();CaptureController c=idle(a,events);
            try{stage(c,7);camera(c,7,CameraState.create(CameraState.Type.OPENING));stream(c,7,PreviewView.StreamState.STREAMING);assertEquals(0,events.ready);assertTrue(c.isPreviewPending());
                camera(c,7,CameraState.create(CameraState.Type.OPEN));assertEquals(1,events.ready);assertFalse(c.isPreviewPending());camera(c,7,CameraState.create(CameraState.Type.OPEN));stream(c,7,PreviewView.StreamState.STREAMING);assertEquals(1,events.ready);assertEquals(0,events.errors);neverStarted(c);
            }finally{c.close();}});}
    }
    @Test(timeout=45_000) public void cachedStreamingAndOldBindingEventsCannotReadyOrFailReplacement(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{Events events=new Events();CaptureController c=idle(a,events);
            try{stage(c,8);set(c,"streamSawIdle",false);camera(c,8,CameraState.create(CameraState.Type.OPEN));stream(c,8,PreviewView.StreamState.STREAMING);assertEquals(0,events.ready);
                camera(c,7,error(CameraState.ERROR_CAMERA_IN_USE));stream(c,7,PreviewView.StreamState.IDLE);assertEquals(0,events.errors);assertTrue(c.isPreviewPending());
                stream(c,8,PreviewView.StreamState.IDLE);stream(c,8,PreviewView.StreamState.STREAMING);assertEquals(1,events.ready);neverStarted(c);
            }finally{c.close();}});}
    }
    @Test(timeout=45_000) public void asyncErrorClearsPendingBeforeListenerAndCannotRepeatAfterStop(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{Events events=new Events();CaptureController c=idle(a,events);events.controller=c;
            try{stage(c,12);camera(c,12,error(CameraState.ERROR_CAMERA_IN_USE));assertEquals(1,events.errors);assertTrue(events.message.contains("Another app"));assertTrue(events.errorSawCleared);assertFalse(c.isPreviewPending());assertFalse((Boolean)get(c,"ready"));
                camera(c,12,CameraState.create(CameraState.Type.OPEN));stream(c,12,PreviewView.StreamState.STREAMING);camera(c,12,error(CameraState.ERROR_CAMERA_DISABLED));assertEquals(1,events.errors);assertEquals(0,events.ready);assertNull(get(c,"previewTimeout"));assertNull(get(c,"cameraStateObserver"));assertNull(get(c,"streamStateObserver"));neverStarted(c);
            }finally{c.close();}});}
    }
    @Test(timeout=45_000) public void deadlineFailsUnstartedPreviewAndStoppedDeadlineCannotFailNewBinding(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){scenario.onActivity(a->{Events events=new Events();CaptureController c=idle(a,events);events.controller=c;
            try{stage(c,14);Runnable deadline=()->call(c,"onPreviewTimeout",new Class<?>[]{int.class},14);set(c,"previewTimeout",deadline);
                deadline.run();assertEquals(1,events.errors);assertTrue(events.errorSawCleared);stage(c,15);deadline.run();assertEquals(1,events.errors);assertTrue(c.isPreviewPending());c.stopPreview();camera(c,15,CameraState.create(CameraState.Type.OPEN));stream(c,15,PreviewView.StreamState.STREAMING);assertEquals(0,events.ready);neverStarted(c);
            }finally{c.close();}});}
    }
    private static CaptureController idle(MainActivity activity,Events events){return new CaptureController(activity,new PreviewView(activity),events);}
    private static void stage(CaptureController c,int binding){set(c,"bindingGeneration",binding);set(c,"previewRequested",true);set(c,"ready",false);set(c,"cameraOpen",false);set(c,"previewStreaming",false);set(c,"streamSawIdle",true);}
    private static void neverStarted(CaptureController c){assertNull(get(c,"provider"));assertNull(get(c,"recording"));assertEquals(0L,c.getRecordingId());denied();}
    private static CameraState error(int code){return CameraState.create(CameraState.Type.PENDING_OPEN,CameraState.StateError.create(code));}
    private static void camera(CaptureController c,int binding,CameraState state){call(c,"onCameraState",new Class<?>[]{int.class,CameraState.class},binding,state);}
    private static void stream(CaptureController c,int binding,PreviewView.StreamState state){call(c,"onPreviewStream",new Class<?>[]{int.class,PreviewView.StreamState.class},binding,state);}
    private static final class Events implements CaptureController.Listener{
        int ready,errors;String message;boolean errorSawCleared;CaptureController controller;
        public void onReady(){ready++;}public void onRecordingStarted(){fail("No recording may start");}public void onRecordingFinished(Uri u,long d){fail("No media may finalize");}
        public void onError(String text){errors++;message=text;if(controller!=null)errorSawCleared=!controller.isPreviewPending()&&!(Boolean)get(controller,"ready");}
    }
    private static Object get(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object call(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
}
