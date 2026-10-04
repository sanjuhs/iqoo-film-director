package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** One actual camera-page Generate AI click with a synthetic brief and installed CPU model.
 * Does not start a camera, microphone, take, pose inference, spoken cue or playback.
 * Main's normal startup recovery remains real; pending export journals prohibit this test.
 */
@RunWith(AndroidJUnit4.class)
public final class CameraPlannerProgressDeviceTest {
    private static final String TAG="MiniFilmCameraAI";
    private static final String SHA="57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final String BRIEF="I wear a blue linen shirt and black trousers. Make a silent solo fashion reel.";
    private static final String[] ROLES={"Hero pose","Movement","Detail","Side pose","Closing"};
    private static final String[] PHASES={"Waiting for earlier local model work to finish…","Loading the AI model on this phone…",
            "Writing five shot directions on this phone. This can take a minute…","Checking the editable AI draft…"};

    @Test(timeout=210_000) public void actualCameraGenerateShowsCpuStagesAndFiveEditableShotsThenDrains() throws Exception {
        Context context=context();KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before this explicit interface check",keyguard!=null&&keyguard.isKeyguardLocked());
        int camera=context.checkSelfPermission(Manifest.permission.CAMERA),mic=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        assertNoInternet();assertFalse(LocalModelLease.isHeld());requireNoPendingExport(context);
        File model=new File(context.getFilesDir(),"director-model.gguf");long size=model.length(),modified=model.lastModified();
        assertEquals(563036064L,size);assertEquals(SHA,hash(model));
        SharedPreferences prefs=context.getSharedPreferences("shoot",0);Map<String,Object> original=backup(prefs);
        Map<String,String> sourceFiles=sourceMetadata(new File(context.getFilesDir(),"takes"));
        File evidenceDir=new File(context.getFilesDir(),"test-evidence");assertTrue(evidenceDir.isDirectory()||evidenceDir.mkdirs());
        File evidence=File.createTempFile("camera-planner-progress-",".json",evidenceDir);
        JSONObject record=new JSONObject().put("syntheticOnly",true).put("brief",BRIEF).put("attempts",1).put("retries",0)
                .put("entryPoint","actual Main Camera Generate AI button").put("backend","CPU").put("creativeQualityClaim",false);
        write(evidence,record);
        ActivityScenario<MainActivity> scenario=null;AtomicReference<LocalPlanner> planner=new AtomicReference<>();
        List<String> phases=new ArrayList<>();AtomicReference<Throwable> publicationFailure=new AtomicReference<>();
        AtomicBoolean published=new AtomicBoolean(),publishedBusy=new AtomicBoolean(true);AtomicLong publishedHandle=new AtomicLong();
        long began=SystemClock.elapsedRealtime();
        SharedPreferences.OnSharedPreferenceChangeListener publication=(preferences,key)->{
            if(!"state".equals(key)||planner.get()==null)return;
            try {
                JSONObject state=new JSONObject(preferences.getString("state","{}"));
                if(!state.optString("source").startsWith("Local AI"))return;
                assertSame(Looper.getMainLooper(),Looper.myLooper());
                publishedBusy.set(((AtomicBoolean)field(planner.get(),"busy")).get());
                publishedHandle.set((Long)field(planner.get(),"handle"));published.set(true);
            } catch(Throwable failure){publicationFailure.compareAndSet(null,failure);}
        };
        try {
            // Retain saved take metadata during normal startup recovery; none is sent to the model.
            // Clear only the test Activity's in-memory takes immediately before the synthetic click.
            JSONArray storedTakes=new JSONObject(prefs.getString("state","{}")).optJSONArray("takes");
            JSONObject controlled=new JSONObject().put("tab",1).put("voice",false).put("guideSequence",false)
                    .put("style","Fashion").put("brief",BRIEF).put("takes",storedTakes==null?new JSONArray():storedTakes);
            assertTrue(prefs.edit().clear().putString("state",controlled.toString()).commit());
            scenario=ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra("open_screen","camera"));
            prefs.registerOnSharedPreferenceChangeListener(publication);
            scenario.onActivity(activity->{
                assertEquals(1,field(activity,"tab"));assertNoCapture(activity);
                @SuppressWarnings("unchecked") List<Take> takes=(List<Take>)field(activity,"takes");takes.clear();
                planner.set((LocalPlanner)field(activity,"planner"));assertTrue(planner.get().isModelAvailable());
                ((EditText)field(activity,"briefField")).setText(BRIEF);
                TextView status=(TextView)field(activity,"status");status.addTextChangedListener(new TextWatcher(){
                    public void beforeTextChanged(CharSequence text,int start,int count,int after) { }
                    public void afterTextChanged(Editable text) { }
                    public void onTextChanged(CharSequence text,int start,int before,int count){
                        assertSame(Looper.getMainLooper(),Looper.myLooper());String value=text.toString();
                        if(Arrays.asList(PHASES).contains(value))phases.add(value);
                    }
                });
                Button generate=find((View)field(activity,"root"),"Generate AI");assertNotNull(generate);
                assertTrue(generate.isShown());assertNotNull(generate.getWindowToken());assertTrue(generate.performClick());
                assertTrue((Boolean)field(activity,"busy"));assertNoCapture(activity);
            });
            AtomicBoolean finished=new AtomicBoolean();String[] outcome=new String[2];JSONArray[] shots={new JSONArray()};
            long deadline=SystemClock.elapsedRealtime()+150_000;
            while(!finished.get()&&SystemClock.elapsedRealtime()<deadline){
                scenario.onActivity(activity->{
                    assertNoCapture(activity);
                    if(!(Boolean)field(activity,"busy")){
                        outcome[0]=(String)field(activity,"planSource");outcome[1]=((TextView)field(activity,"status")).getText().toString();
                        @SuppressWarnings("unchecked") List<Shot> current=(List<Shot>)field(activity,"shots");shots[0]=encode(current);finished.set(true);
                    }
                });
                if(!finished.get())SystemClock.sleep(100);
            }
            record.put("completed",finished.get()).put("wallElapsedMs",SystemClock.elapsedRealtime()-began)
                    .put("phases",new JSONArray(phases)).put("planSource",outcome[0]==null?JSONObject.NULL:outcome[0])
                    .put("status",outcome[1]==null?JSONObject.NULL:outcome[1]).put("shots",shots[0])
                    .put("nativeHandleAtPublication",publishedHandle.get()!=0).put("workerBusyAtPublication",publishedBusy.get());
            write(evidence,record);Log.i(TAG,"synthetic_only=true actual_main_generate=true record="+record.toString());
            assertTrue("One actual CPU attempt must finish",finished.get());assertNull(publicationFailure.get());
            assertTrue("Actual Main saved the real AI result",published.get());assertFalse("Callback admission released before publication",publishedBusy.get());assertNotEquals(0L,publishedHandle.get());
            assertEquals(Arrays.asList(PHASES),phases);assertTrue(outcome[0].contains(LocalPlanner.MODEL_LABEL));
            assertTrue(outcome[1].startsWith("AI directions ready"));assertEquals(5,shots[0].length());
            boolean shirt=false;
            for(int i=0;i<5;i++){
                JSONObject shot=shots[0].getJSONObject(i);assertEquals(ROLES[i],shot.getString("title"));
                String instruction=shot.getString("instruction"),caption=shot.getString("caption");
                assertTrue(instruction.length()>0&&instruction.length()<=90);assertTrue(caption.length()>0&&caption.length()<=30);
                long duration=shot.getLong("durationMs");assertTrue(duration>=3000&&duration<=8000);
                shirt|=(instruction+" "+caption).toLowerCase(Locale.ROOT).contains("shirt");
            }
            assertTrue("Draft should visibly refer to the supplied garment",shirt);
            record.put("targetedUiAndCpuChecksPassed",true);write(evidence,record);
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(publication);
            if(scenario!=null)scenario.close();
            boolean drained=planner.get()==null||((ExecutorService)field(planner.get(),"worker")).awaitTermination(15,TimeUnit.SECONDS);
            boolean released=planner.get()==null||(Long)field(planner.get(),"handle")==0&&!LocalModelLease.isHeld();
            restore(prefs,original);
            boolean preserved=model.length()==size&&model.lastModified()==modified&&SHA.equals(hash(model));
            boolean sourcesPreserved=sourceFiles.equals(sourceMetadata(new File(context.getFilesDir(),"takes")));
            record.put("workerDrained",drained).put("handleAndLeaseReleased",released).put("modelPreserved",preserved)
                    .put("preferencesRestored",original.equals(prefs.getAll())).put("sourceVideoMetadataPreserved",sourcesPreserved);
            write(evidence,record);Log.i(TAG,"synthetic_only=true drained="+drained+" released="+released+" model_preserved="+preserved+" evidence_file="+evidence.getName());
            assertTrue(drained);assertTrue(released);assertTrue(preserved);assertTrue(sourcesPreserved);
            assertTrue("All original preference keys and values restored",original.equals(prefs.getAll()));
            assertEquals(camera,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(mic,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));assertNoInternet();
        }
    }

    private static void requireNoPendingExport(Context context)throws Exception{
        File[] files=new File(context.getFilesDir(),"export-journal").listFiles();if(files==null)return;
        int journals=0;for(File file:files){
            if(!file.getName().endsWith(".json")){assertFalse("Refuse an interrupted atomic export record",file.getName().endsWith(".bak")||file.getName().endsWith(".new"));continue;}
            journals++;assertTrue(file.length()<=16384);String text=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
            assertEquals("Refuse pending export reconciliation during a planner check","COMPLETE",new JSONObject(text).getString("state"));
        }
        assertTrue("Refuse startup pruning of older completed journals",journals<=1);
    }
    private static Map<String,String> sourceMetadata(File dir){Map<String,String> values=new HashMap<>();File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.getName().endsWith(".mp4"))values.put(file.getName(),file.length()+":"+file.lastModified());return values;}
    private static void assertNoCapture(MainActivity activity){assertNull(field(activity,"capture"));assertNull(field(activity,"pose"));assertNull(field(activity,"briefRecorder"));assertFalse((Boolean)field(activity,"countdown"));assertFalse((Boolean)field(activity,"session"));}
    private static JSONArray encode(List<Shot> shots){try{JSONArray array=new JSONArray();for(Shot shot:shots)array.put(new JSONObject().put("title",shot.title).put("instruction",shot.instruction).put("caption",shot.caption).put("durationMs",shot.targetDurationMs));return array;}catch(Exception failure){throw new AssertionError(failure);}}
    private static Button find(View view,String text){if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return(Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button match=find(group.getChildAt(i),text);if(match!=null)return match;}}return null;}
    private static Object field(Object target,String name){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception failure){throw new AssertionError(failure);}}
    private static Map<String,Object> backup(SharedPreferences preferences){Map<String,Object> result=new HashMap<>();for(Map.Entry<String,?> entry:preferences.getAll().entrySet()){Object value=entry.getValue();if(value instanceof Set)value=new HashSet<>((Set<?>)value);result.put(entry.getKey(),value);}return result;}
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences preferences,Map<String,Object> values){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,Object> entry:values.entrySet()){String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,new HashSet<>((Set<String>)value));else throw new AssertionError("Unsupported preference type");}assertTrue(editor.commit());}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder text=new StringBuilder();for(byte value:digest.digest())text.append(String.format(Locale.ROOT,"%02x",value&255));return text.toString();}
    private static void write(File file,JSONObject record)throws Exception{try(OutputStream output=new FileOutputStream(file)){output.write(record.toString(2).getBytes(StandardCharsets.UTF_8));output.flush();}}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void assertNoInternet()throws Exception{PackageInfo info=context().getPackageManager().getPackageInfo(context().getPackageName(),PackageManager.GET_PERMISSIONS);if(info.requestedPermissions!=null)for(String permission:info.requestedPermissions)assertNotEquals(Manifest.permission.INTERNET,permission);}
}
