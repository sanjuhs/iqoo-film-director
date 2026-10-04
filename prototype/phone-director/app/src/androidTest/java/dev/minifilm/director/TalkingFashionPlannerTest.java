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
import java.util.regex.Pattern;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Two real CPU attempts, retained even on semantic failure: one observed repair and one
 * genuinely fresh prospective brief. No recording, playback, retries, model downloads or fallback.
 * Spoken duration remains a draft; this does not demonstrate actual speech fit or creator benefit.
 */
@RunWith(AndroidJUnit4.class)
public final class TalkingFashionPlannerTest {
    private static final String TAG="MiniFilmTalkingFashion";
    private static final String SHA="57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf";
    private static final String[] ROLES={"Hero pose","Movement","Detail","Side pose","Closing"};
    private static final String[] BRIEFS={
        "Make a stationary talking-fashion reel while I wear my burgundy waistcoat. I want to describe my own layering choice. Stay in one marked spot: no walking or steps. Small body turns are fine. No new props. The phone is already mounted.",
        "Make a stationary spoken outfit reel while I wear my violet poncho. I want to explain my own styling choice. Stay planted: no walking or steps. Only small body turns; no new props or fasteners. The phone is mounted."
    };

    @Test(timeout=360_000) public void actualCpuRepairAndFreshPonchoDirectOwnSpeechWhileStayingPlanted()throws Exception{
        Context context=context();assertGates();assertFalse(LocalModelLease.isHeld());
        File model=new File(context.getFilesDir(),"director-model.gguf");assertEquals(563036064L,model.length());assertEquals(SHA,hash(model));long modified=model.lastModified();
        Map<String,?> preferences=new HashMap<>(context.getSharedPreferences("shoot",0).getAll());
        File dir=new File(context.getFilesDir(),"test-evidence");assertTrue(dir.isDirectory()||dir.mkdirs());
        File evidenceFile=File.createTempFile("talking-fashion-repair-",".json",dir);
        JSONArray records=new JSONArray();JSONObject evidence=new JSONObject().put("syntheticOnly",true).put("backend","CPU")
                .put("retries",0).put("fallback",false).put("actualSpeechFitClaim",false).put("userBenefitClaim",false)
                .put("manualSemanticReviewPending",true).put("cases",records);
        List<String> failures=new ArrayList<>();
        try{
            for(int i=0;i<BRIEFS.length;i++){
                String name=i==0?"observed-waistcoat-repair":"prospective-violet-poncho";
                JSONObject record=new JSONObject().put("id",name).put("brief",BRIEFS[i]).put("status","pending")
                        .put("expectedNativeDecodedInstructions",5).put("stationaryMovementClosedChoice",true)
                        .put("speechTopicNativeFreeText",true).put("creatorOverride",false);
                records.put(record);write(evidenceFile,evidence);List<String> findings=new ArrayList<>();
                // Pure-model topic-fidelity regression, intentionally no creator-request override.
                LocalPlanner planner=new LocalPlanner(context,false);CountDownLatch done=new CountDownLatch(1);
                AtomicReference<List<Shot>> plan=new AtomicReference<>();AtomicReference<String> label=new AtomicReference<>(),error=new AtomicReference<>();
                AtomicLong nativeMs=new AtomicLong(-1);AtomicInteger callbacks=new AtomicInteger();AtomicBoolean main=new AtomicBoolean(true);
                long began=SystemClock.elapsedRealtime();
                try{
                    if(!planner.isModelAvailable())findings.add("default local model/runtime unavailable");
                    else{
                        final int caseIndex=i;
                        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->planner.generate(BRIEFS[caseIndex],"Fashion",new LocalPlanner.Listener(){
                            public void onPlan(List<Shot> shots,long elapsed,String modelLabel){callbacks.incrementAndGet();main.set(Looper.myLooper()==Looper.getMainLooper());plan.set(shots);nativeMs.set(elapsed);label.set(modelLabel);done.countDown();}
                            public void onError(String message){callbacks.incrementAndGet();main.set(Looper.myLooper()==Looper.getMainLooper());error.set(message);done.countDown();}
                        }));
                        boolean complete=done.await(150,TimeUnit.SECONDS);
                        JSONArray output=encode(plan.get());record.put("shots",output).put("nativeElapsedMs",nativeMs.get()).put("wallElapsedMs",SystemClock.elapsedRealtime()-began)
                                .put("modelLabel",label.get()==null?JSONObject.NULL:label.get()).put("error",error.get()==null?JSONObject.NULL:error.get())
                                .put("status",error.get()==null?"draft_received":"production_error");
                        // These full drafts are synthetic and retained before targeted checks.
                        write(evidenceFile,evidence);log(record);
                        check(complete&&callbacks.get()==1&&main.get(),findings,"one main-thread production callback required");
                        check(error.get()==null,findings,"actual native generation failed: "+error.get());
                        if(plan.get()==null)findings.add("no actual native draft");else inspect(plan.get(),label.get(),i,findings);
                    }
                }finally{
                    planner.close();boolean drained=((ExecutorService)field(planner,"worker")).awaitTermination(15,TimeUnit.SECONDS);
                    boolean freed=(Long)field(planner,"handle")==0&&!LocalModelLease.isHeld();
                    check(drained&&freed,findings,"native handle and model lifetime must be released after close");
                    record.put("workerDrained",drained).put("nativeHandleAndLeaseReleased",freed).put("targetedFindings",new JSONArray(findings))
                            .put("status",findings.isEmpty()?"targeted_checks_passed_manual_review_pending":"targeted_findings");
                    for(String failure:findings)failures.add(name+": "+failure);write(evidenceFile,evidence);log(record);
                }
            }
        }finally{
            boolean prefs=preferences.equals(context.getSharedPreferences("shoot",0).getAll());
            boolean weights=model.length()==563036064L&&model.lastModified()==modified&&SHA.equals(hash(model));
            check(prefs,failures,"creator preferences changed");check(weights,failures,"default model changed");
            evidence.put("preferencesPreserved",prefs).put("modelPreserved",weights).put("findings",new JSONArray(failures));write(evidenceFile,evidence);assertGates();
        }
        Log.i(TAG,"synthetic_only=true complete=true attempts=2 retries=0 findings="+new JSONArray(failures)+" evidence_file="+evidenceFile.getName());
        assertTrue("Actual repair/prospective findings retained: "+failures,failures.isEmpty());
    }
    private static void inspect(List<Shot> plan,String label,int fixture,List<String> failures){
        check(plan.size()==5,failures,"five exact Fashion roles required");if(plan.size()!=5)return;
        check(label!=null&&label.startsWith(LocalPlanner.MODEL_LABEL)&&label.contains("local CPU")
                &&label.contains("stationary movement constraint")&&label.contains("talking-fashion speech constraint")
                &&label.contains("creator-choice detail constraint")&&!label.contains("creator-authored reviewed cues retained"),failures,"actual CPU and constraint disclosure required");
        StringBuilder text=new StringBuilder();
        for(int i=0;i<5;i++){
            Shot shot=plan.get(i);check(ROLES[i].equals(shot.title),failures,"role order "+i);
            check(shot.id.startsWith("ai-shot-")&&shot.instruction.length()<=90&&shot.caption.length()<=30&&!shot.caption.trim().isEmpty(),failures,"actual bounded model fields "+i);
            check(shot.targetDurationMs>=3000&&shot.targetDurationMs<=8000,failures,"bounded duration "+i);
            check(!word(shot.instruction+" "+shot.caption,"walk|walking|steps?|stepping|running|stroll|stride"),failures,"stationary output contains locomotion "+i);
            check(!word(shot.caption,"camera|phone|tripod|gimbal|drone|screen"),failures,"caption equipment "+i);
            check(!Pattern.compile("(?i)\\b(?:hold|move|adjust|lift|bring|take|turn|point|focus)\\s+(?:the\\s+|your\\s+)?(?:camera|phone)\\b").matcher(shot.instruction).find(),failures,"equipment operation "+i);
            text.append(shot.instruction).append(' ').append(shot.caption).append('\n');
        }
        String all=text.toString(),topic=fixture==0?"layering":"styling",garment=fixture==0?"waistcoat":"poncho",color=fixture==0?"burgundy":"violet";
        check(word(all,garment)&&word(all,color),failures,"supplied garment/color not retained");
        Shot speech=plan.get(4);check(speech.instruction.startsWith("Tell in your own words ")&&word(speech.instruction,topic),failures,"practical own-words cue must name supplied speech topic");
        check(speech.targetDurationMs>=6000&&speech.targetDurationMs<=8000,failures,"short sentence needs 6-8s editable draft");
        check(word(plan.get(2).instruction,"visible")&&word(plan.get(2).instruction,"garment")&&word(plan.get(2).instruction,"choose"),failures,"generic Detail remains creator choice");
        check(!word(all,"chair|stool|table|bag|hat|scarf|mirror|cup|jacket|raincoat|pencil|mug"),failures,"unsupplied prop or earlier example noun");
        // Specific negative fixture checks are not an exhaustive fact/benefit validator.
        if(fixture==1)check(!word(all,"zipper|zippers|buttons?|buckle|comfortable|warm|flattering|cotton|linen|silk|wool"),failures,"unsupplied fastener/material/benefit wording");
    }
    private static JSONArray encode(List<Shot> plan)throws Exception{JSONArray array=new JSONArray();if(plan!=null)for(Shot s:plan)array.put(new JSONObject().put("title",s.title).put("instruction",s.instruction).put("caption",s.caption).put("durationMs",s.targetDurationMs));return array;}
    private static void log(JSONObject record)throws Exception{Log.i(TAG,"synthetic_only=true id="+record.getString("id")+" status="+record.optString("status")+" native_ms="+record.optLong("nativeElapsedMs",-1)+" model="+record.optString("modelLabel")+" findings="+record.optJSONArray("targetedFindings")+" error="+record.optString("error"));JSONArray shots=record.optJSONArray("shots");if(shots!=null)for(int i=0;i<shots.length();i++)Log.i(TAG,"synthetic_only=true id="+record.getString("id")+" shot="+(i+1)+" actual="+shots.get(i));}
    private static void write(File file,JSONObject object)throws Exception{try(FileOutputStream out=new FileOutputStream(file,false)){out.write(object.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}}
    private static void check(boolean okay,List<String> failures,String message){if(!okay)failures.add(message);}
    private static boolean word(String text,String alternatives){return Pattern.compile("\\b(?:"+alternatives+")\\b",Pattern.CASE_INSENSITIVE).matcher(text).find();}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void assertGates()throws Exception{Context c=context();assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.RECORD_AUDIO));PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS);if(info.requestedPermissions!=null)for(String p:info.requestedPermissions)assertNotEquals(Manifest.permission.INTERNET,p);}
}
