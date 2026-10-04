package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LiveData;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Authorized preview only: no recording, screenshots, microphone, inference or source-media reads. */
@RunWith(AndroidJUnit4.class)
public final class CameraPreviewDeviceTest {
    private SharedPreferences prefs;private Map<String,?> original;
    private Map<String,String> originalFiles;
    @Before public void requireUnlockedPhysicalPhoneWithExistingPermissionsAndBackup()throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.US);
        assertFalse("This check is for the authorized physical phone",fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse("Unlock the phone before this attended preview check",keyguard!=null&&keyguard.isKeyguardLocked());permissions();
        requireNoPendingExport();
        prefs=context().getSharedPreferences("shoot",0);original=new HashMap<>(prefs.getAll());originalFiles=fileMetadata();
        // List all existing phone takes so startup recovery cannot rewrite their journals.
        // Only metadata is read; original state is restored after the Activity is closed.
        JSONArray existing=new JSONArray();File[] files=new File(context().getFilesDir(),"takes").listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&f.getName().endsWith(".mp4")){
            existing.put(new JSONObject().put("uri",android.net.Uri.fromFile(f).toString()).put("id","preview-existing")
                    .put("title","Existing take").put("caption","").put("duration",1000).put("in",0).put("out",1000).put("selected",false));
        }
        JSONObject shot=new JSONObject().put("id","preview-only-check").put("title","Preview only")
                .put("cue","Check the visible camera preview.").put("caption","").put("duration",5000).put("framingTarget",FramingTarget.MANUAL);
        JSONObject state=new JSONObject().put("tab",1).put("voice",false).put("guideSequence",false).put("lens",CameraSelector.LENS_FACING_BACK)
                .put("brief","Preview-only device check").put("shots",new JSONArray().put(shot)).put("shot",0).put("takes",existing);
        assertTrue(prefs.edit().clear().putString("state",state.toString()).commit());
    }
    @After public void restoreAllPreferencesAndCheckNoSourceMetadataChanged(){
        if(original!=null){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> item:original.entrySet()){
            String k=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked")Set<String> values=(Set<String>)v;e.putStringSet(k,new HashSet<>(values));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(e.commit());assertTrue("Complete creator preferences restored",original.equals(prefs.getAll()));}
        if(originalFiles!=null){Map<String,String> after=fileMetadata();if(!originalFiles.equals(after))logSanitizedMetadataDiff(originalFiles,after);assertTrue("Preview must not add, delete or alter private file metadata",originalFiles.equals(after));}permissions();
    }
    @Test(timeout=65_000) public void actualStartCameraWaitsForOpenStreamingThenStopAndBackgroundRemainOffWithoutRecording()throws Exception{
        AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<CaptureController> opened=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{activity.set(a);assertEquals(1,field(a,"tab"));assertEquals(false,field(a,"voice"));assertEquals(false,field(a,"guideSequence"));
                Button start=(Button)field(a,"captureAction");assertNotNull(start);assertEquals("Start camera",start.getText().toString());assertTrue(start.isShown());assertNotNull(start.getWindowToken());assertTrue(start.performClick());});
            long began=SystemClock.elapsedRealtime();AtomicBoolean ready=new AtomicBoolean();
            while(SystemClock.elapsedRealtime()-began<20_000&&!ready.get()){
                scenario.onActivity(a->{CaptureController capture=(CaptureController)field(a,"capture");if(capture==null)return;
                    assertFalse("Preview must not start recording",capture.isRecording());assertEquals(0,capture.getRecordingId());
                    if(Boolean.TRUE.equals(field(a,"live"))){@SuppressWarnings("unchecked")LiveData<CameraState> states=(LiveData<CameraState>)field(capture,"cameraStates");assertNotNull(states);CameraState camera=states.getValue();assertNotNull(camera);assertNull(camera.getError());assertEquals(CameraState.Type.OPEN,camera.getType());assertEquals(PreviewView.StreamState.STREAMING,((PreviewView)field(a,"preview")).getPreviewStreamState().getValue());assertFalse(capture.isPreviewPending());opened.set(capture);ready.set(true);}
                });if(!ready.get())Thread.sleep(100);
            }
            assertTrue("Real camera did not reach OPEN and STREAMING before the bounded deadline",ready.get());
            Log.i("MiniFilmCameraPreview","REAL_PREVIEW_READY elapsedMs="+(SystemClock.elapsedRealtime()-began)+" recording=false");
            scenario.onActivity(a->{assertNoAudioOrRecording(a,opened.get());assertTrue("Preview must not create take files",originalFiles.equals(fileMetadata()));});
            scenario.onActivity(a->{Button stop=(Button)field(a,"stopTakeButton");assertNotNull(stop);assertEquals("Stop camera",stop.getText().toString());assertTrue(stop.isShown());assertTrue(stop.isEnabled());assertTrue(stop.performClick());assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"live"));assertEquals(false,field(a,"session"));assertFalse(opened.get().isPreviewPending());assertFalse((Boolean)field(opened.get(),"ready"));assertNoAudioOrRecording(a,opened.get());});
            scenario.moveToState(Lifecycle.State.CREATED);assertEquals(Lifecycle.State.CREATED,scenario.getState());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{CaptureController capture=opened.get();assertFalse(capture.isPreviewPending());assertFalse((Boolean)field(capture,"ready"));assertNull(field(capture,"cameraStateObserver"));assertNull(field(capture,"streamStateObserver"));assertNull(field(capture,"previewTimeout"));assertEquals(false,field(activity.get(),"live"));assertNoAudioOrRecording(activity.get(),capture);});
            scenario.moveToState(Lifecycle.State.RESUMED);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals(false,field(a,"live"));assertNoAudioOrRecording(a,opened.get());assertEquals("Start camera",((Button)field(a,"captureAction")).getText().toString());});
        }
    }
    private static void logSanitizedMetadataDiff(Map<String,String> before,Map<String,String> after){
        Map<String,int[]> counts=new TreeMap<>();Set<String> keys=new HashSet<>(before.keySet());keys.addAll(after.keySet());
        for(String key:keys){String old=before.get(key),now=after.get(key);if(Objects.equals(old,now))continue;
            String category=key.startsWith("takes/")?"take":key.startsWith("export-journal/")?"export-journal":key.startsWith("exports/")?"export-edit":
                    key.startsWith("models/")||key.endsWith(".gguf")||key.endsWith(".safetensors")?"model":"runtime-other";
            int[] tally=counts.computeIfAbsent(category,ignored->new int[4]);
            if(old==null)tally[0]++;else if(now==null)tally[1]++;else if(!old.substring(0,old.indexOf(':')).equals(now.substring(0,now.indexOf(':'))))tally[2]++;else tally[3]++;
        }
        for(Map.Entry<String,int[]> entry:counts.entrySet()){int[] n=entry.getValue();
            Log.w("MiniFilmCameraPreview","FILE_METADATA_DIFF category="+entry.getKey()+" added="+n[0]+" removed="+n[1]+" sizeChanged="+n[2]+" modifiedTimeOnly="+n[3]);
        }
    }
    private static void requireNoPendingExport()throws Exception{
        File[] files=new File(context().getFilesDir(),"export-journal").listFiles();if(files==null)return;
        int journals=0;for(File file:files){
            if(!file.getName().endsWith(".json")){assertFalse("Refuse an interrupted atomic export record",file.getName().endsWith(".bak")||file.getName().endsWith(".new"));continue;}
            journals++;assertTrue("Export metadata must be bounded",file.length()<=16384);
            String text=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("Finish pending export recovery before this preview-only check","COMPLETE",new JSONObject(text).getString("state"));
        }
        assertTrue("Refuse startup pruning of older completed journals",journals<=1);
    }
    private static void assertNoAudioOrRecording(MainActivity a,CaptureController c){assertFalse(c.isRecording());assertEquals(0,c.getRecordingId());assertEquals(false,field(a,"countdown"));assertEquals(false,field(a,"sequenceActive"));SpeechCoach speech=(SpeechCoach)field(a,"speech");assertFalse(speech.hasSpeechWork());assertFalse(speech.isListening());}
    private static Map<String,String> fileMetadata(){Map<String,String> result=new HashMap<>();collect(context().getFilesDir(),"",result);return result;}
    private static void collect(File dir,String prefix,Map<String,String> result){File[] files=dir.listFiles();assertNotNull("App file metadata must be readable",files);for(File f:files){String relative=prefix+f.getName();if(f.isDirectory())collect(f,relative+"/",result);else result.put(relative,f.length()+":"+f.lastModified());}}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError("Camera state could not be inspected",e);}}
    private static void permissions(){assertEquals(PackageManager.PERMISSION_GRANTED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_GRANTED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
}
