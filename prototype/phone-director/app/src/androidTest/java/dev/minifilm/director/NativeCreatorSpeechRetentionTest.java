package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** One real default CPU generation, followed by disclosed literal-author Closing retention.
 * This does NOT test free native speech-topic understanding, actual spoken fit or creator benefit.
 * No recording, playback, retries, alternative models, downloads or hidden starter fallback.
 */
@RunWith(AndroidJUnit4.class)
public final class NativeCreatorSpeechRetentionTest {
    private static final String TAG="MiniFilmCreatorSpeech";
    private static final String SHA="57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final String BRIEF="Make a stationary talking-fashion reel while I wear my burgundy waistcoat. I want to describe my own layering choice. Stay in one marked spot: no walking or steps. Small body turns are fine. No new props. The phone is already mounted.";
    private static final String[] ROLES={"Hero pose","Movement","Detail","Side pose","Closing"};

    @Test(timeout=210_000) public void actualFiveShotCpuDraftRetainsOneCreatorSpeechRequestWithHonestClosingProvenance()throws Exception{
        Context context=context();assertGates();assertFalse(LocalModelLease.isHeld());
        File model=new File(context.getFilesDir(),"director-model.gguf");assertEquals(563036064L,model.length());assertEquals(SHA,hash(model));long modified=model.lastModified();
        Map<String,?> prefs=new HashMap<>(context.getSharedPreferences("shoot",0).getAll());
        File dir=new File(context.getFilesDir(),"test-evidence");assertTrue(dir.isDirectory()||dir.mkdirs());File file=File.createTempFile("creator-speech-retention-",".json",dir);
        JSONObject record=new JSONObject().put("syntheticOnly",true).put("brief",BRIEF).put("backend","CPU").put("attempts",1).put("retries",0)
                .put("fallback",false).put("nativeFiveShotDraftRequiredBeforeMerge",true).put("nativeTopicUnderstandingClaim",false)
                .put("actualSpeechFitClaim",false).put("userBenefitClaim",false).put("closingInstructionOrigin","literal creator-authored request")
                .put("closingCaptionOrigin","creator-owned disclosure caption").put("closingDurationOrigin","native bounded 6-8 second draft")
                .put("otherFourFieldsOrigin","actual native draft, including disclosed closed-choice Movement and Detail constraints");
        write(file,record);
        LocalPlanner planner=new LocalPlanner(context);CountDownLatch done=new CountDownLatch(1);
        AtomicReference<List<Shot>> plan=new AtomicReference<>();AtomicReference<String> label=new AtomicReference<>(),error=new AtomicReference<>();
        AtomicInteger callbacks=new AtomicInteger();AtomicLong elapsed=new AtomicLong(-1),loadedHandle=new AtomicLong();AtomicBoolean main=new AtomicBoolean();long began=SystemClock.elapsedRealtime();
        try{
            assertTrue(planner.isModelAvailable());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->planner.generate(BRIEF,"Fashion",new LocalPlanner.Listener(){
                public void onPlan(List<Shot> shots,long millis,String modelLabel){callbacks.incrementAndGet();main.set(Looper.myLooper()==Looper.getMainLooper());plan.set(shots);label.set(modelLabel);elapsed.set(millis);try{loadedHandle.set((Long)field(planner,"handle"));}catch(Exception e){error.set("Native handle evidence unavailable");}done.countDown();}
                public void onError(String message){callbacks.incrementAndGet();main.set(Looper.myLooper()==Looper.getMainLooper());error.set(message);done.countDown();}
            }));
            boolean complete=done.await(150,TimeUnit.SECONDS);
            JSONArray output=encode(plan.get());record.put("complete",complete).put("nativeElapsedMs",elapsed.get()).put("wallElapsedMs",SystemClock.elapsedRealtime()-began)
                    .put("modelLabel",label.get()==null?JSONObject.NULL:label.get()).put("error",error.get()==null?JSONObject.NULL:error.get())
                    .put("actualNativeHandlePresentAtCallback",loadedHandle.get()!=0).put("shots",output);
            // Full synthetic output/provenance is retained before assertions, including failures.
            write(file,record);log(record);
            assertTrue("One bounded production attempt must complete",complete);assertEquals(1,callbacks.get());assertTrue(main.get());assertNull(error.get());assertNotNull(plan.get());assertEquals(5,plan.get().size());assertTrue(loadedHandle.get()!=0);
            assertTrue(label.get().startsWith(LocalPlanner.MODEL_LABEL));assertTrue(label.get().contains("local CPU"));
            for(String disclosure:new String[]{"creator-choice detail constraint","stationary movement constraint","talking-fashion speech constraint","Closing: creator-authored speech request retained"})assertTrue(disclosure,label.get().contains(disclosure));
            assertFalse(label.get().contains("creator-authored reviewed cues retained"));
            for(int i=0;i<5;i++){Shot s=plan.get().get(i);assertEquals(ROLES[i],s.title);assertTrue(s.id.startsWith("ai-shot-"));assertTrue(s.instruction.length()<=90);assertTrue(s.caption.length()<=30);assertFalse(s.caption.trim().isEmpty());assertTrue(s.targetDurationMs>=3000&&s.targetDurationMs<=8000);}
            Shot closing=plan.get().get(4);assertEquals("Describe my own layering choice.",closing.instruction);assertEquals("My own words",closing.caption);assertTrue(closing.targetDurationMs>=6000&&closing.targetDurationMs<=8000);
            assertTrue(Arrays.asList("Turn your upper body slightly while staying in one spot.","Turn slightly in place, keeping your feet planted.").contains(plan.get().get(1).instruction));
            assertTrue(Arrays.asList("Show one visible garment detail you choose.","Point to one visible garment detail you choose.").contains(plan.get().get(2).instruction));
            record.put("targetedHybridChecksPassed",true);write(file,record);
        }finally{
            planner.close();boolean drained=((ExecutorService)field(planner,"worker")).awaitTermination(15,TimeUnit.SECONDS);
            boolean released=(Long)field(planner,"handle")==0&&!LocalModelLease.isHeld();
            boolean prefsPreserved=prefs.equals(context.getSharedPreferences("shoot",0).getAll());boolean modelPreserved=model.length()==563036064L&&model.lastModified()==modified&&SHA.equals(hash(model));
            record.put("workerDrained",drained).put("nativeHandleAndLeaseReleased",released).put("preferencesPreserved",prefsPreserved).put("modelPreserved",modelPreserved);write(file,record);
            Log.i(TAG,"synthetic_only=true attempts=1 native_ms="+elapsed.get()+" released="+released+" preferences_preserved="+prefsPreserved+" model_preserved="+modelPreserved+" evidence_file="+file.getName());
            assertTrue(drained);assertTrue(released);assertTrue(prefsPreserved);assertTrue(modelPreserved);assertGates();
        }
    }
    private static JSONArray encode(List<Shot> plan)throws Exception{JSONArray array=new JSONArray();if(plan!=null)for(int i=0;i<plan.size();i++){Shot s=plan.get(i);array.put(new JSONObject().put("title",s.title).put("instruction",s.instruction).put("caption",s.caption).put("durationMs",s.targetDurationMs).put("instructionOrigin",i==4?"creator-authored retained request":"native decoded draft").put("captionOrigin",i==4?"creator-owned disclosure caption":"native decoded draft").put("durationOrigin","native decoded draft"));}return array;}
    private static void log(JSONObject r)throws Exception{Log.i(TAG,"synthetic_only=true native_ms="+r.optLong("nativeElapsedMs")+" model="+r.optString("modelLabel")+" error="+r.optString("error")+" native_topic_understanding_claim=false");JSONArray output=r.optJSONArray("shots");if(output!=null)for(int i=0;i<output.length();i++)Log.i(TAG,"synthetic_only=true shot="+(i+1)+" hybrid="+output.get(i));}
    private static void write(File file,JSONObject data)throws Exception{try(FileOutputStream out=new FileOutputStream(file,false)){out.write(data.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void assertGates()throws Exception{Context c=context();assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.RECORD_AUDIO));PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS);if(info.requestedPermissions!=null)for(String p:info.requestedPermissions)assertNotEquals(Manifest.permission.INTERNET,p);}
}
