package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.ZipFile;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Fresh-emulator synthetic persistence and real local ZIP callback checks; no capture or Files save. */
@RunWith(AndroidJUnit4.class)
public final class ShotPlanPackageUiTest {
    private SharedPreferences prefs;
    private Map<String,?> prior;
    private boolean changed;
    private final List<File> owned=new ArrayList<>();
    private Set<String> cacheBefore,demoBefore;

    @Before public void requireEmptySyntheticEmulatorAndPreservePreferences() throws Exception {
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.ROOT);
        assertTrue("Fresh synthetic emulator only",fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());denied();
        empty(new File(context().getFilesDir(),"takes"));empty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());
        assertFalse(new File(context().getFilesDir(),"models/director-mmproj.gguf").exists());
        assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        cacheBefore=names(context().getCacheDir());demoBefore=names(new File(context().getFilesDir(),"synthetic-demo"));
        prefs=context().getSharedPreferences("shoot",0);prior=new HashMap<>(prefs.getAll());changed=true;
        JSONArray shots=new JSONArray();for(int i=0;i<2;i++)shots.put(new JSONObject().put("id","package-plan-"+i)
                .put("title",i==0?"Hero pose":"Closing").put("cue",i==0?"Stand in your outfit.":"Smile at the lens.")
                .put("caption",i==0?"My outfit":"See you soon").put("duration",4000));
        assertTrue(prefs.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false)
                .put("source","Synthetic reviewed plan").put("shots",shots).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreAllPreferencesAndOnlyOwnFiles(){
        for(File file:owned)if(file.exists())assertTrue(file.delete());
        if(cacheBefore!=null)for(File file:children(context().getCacheDir()))if(!cacheBefore.contains(file.getName())&&file.getName().matches("minifilm-pack-[0-9]+\\.(?:zip|partial)"))assertTrue(file.delete());
        File demo=new File(context().getFilesDir(),"synthetic-demo");
        if(demoBefore!=null){for(File file:children(demo))if(!demoBefore.contains(file.getName())&&file.getName().matches("synthetic-[123]\\.mp4"))assertTrue(file.delete());if(demoBefore.isEmpty()&&demo.exists()&&children(demo).length==0)assertTrue(demo.delete());}
        if(changed){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:prior.entrySet()){
            Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> s=(Set<String>)v;e.putStringSet(k,new HashSet<>(s));}else throw new AssertionError("Preference type");
        }assertTrue(e.commit());assertEquals(prior,prefs.getAll());}denied();
    }

    @Test(timeout=45_000) public void everyPlanContentSourceOrderAndNamespaceChangeInvalidatesOnlyReadyPointer() throws Exception {
        Take take=metadataTake();String originalTake=takeJson(take);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{takes(a).add(take);call(a,"save");});
            for(int mutation=0;mutation<7;mutation++){
                File pointer=new File(context().getCacheDir(),"minifilm-pack-"+System.nanoTime()+".zip");assertTrue(pointer.createNewFile());owned.add(pointer);
                try(FileOutputStream out=new FileOutputStream(pointer)){out.write(new byte[]{1,2,3});}
                final int change=mutation;
                scenario.onActivity(a->{set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",call(a,"packSnapshot"));call(a,"save");assertTrue(pointer.exists());
                    Shot shot=shots(a).get(0);
                    switch(change){case 0:shot.title="Edited hero";break;case 1:shot.instruction="Turn slightly.";break;case 2:shot.caption="New caption";break;
                        case 3:shot.targetDurationMs=6000;break;case 4:Collections.reverse(shots(a));break;case 5:set(a,"planSource","Creator changed source label");break;
                        default:call(a,"replacePlan",new Class<?>[]{List.class,String.class},new ArrayList<>(shots(a)),"New plan with same wording");}
                    call(a,"save");assertNull(field(a,"pendingPack"));assertFalse(pointer.exists());assertEquals(originalTake,takeJson(take));noCapture(a);
                });
            }
        }
    }

    @Test(timeout=45_000) public void invalidPlanRefusesBothDispatchesWithoutChangingEditsOrOpeningSources() {
        Take take=metadataTake();String original=takeJson(take);AtomicInteger opens=new AtomicInteger();
        ProjectPackager packager=new ProjectPackager(context(),(uri,signal)->{opens.incrementAndGet();throw new IOException("must not open");},1024);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{((ProjectPackager)field(a,"packager")).close();set(a,"packager",packager);takes(a).add(take);
                shots(a).get(0).instruction=new String(new char[2001]).replace('\0','x');
                call(a,"packageProject");assertFalse((Boolean)field(a,"busy"));assertNull(field(a,"pendingPack"));
                call(a,"startReelExport",new Class<?>[]{List.class},Collections.emptyList());
                assertFalse((Boolean)field(a,"busy"));assertNull(field(a,"lastVideo"));assertEquals(0,opens.get());
                assertTrue(((TextView)field(a,"status")).getText().toString().contains("Plan direction is too long. Use at most 2000 characters"));assertEquals(original,takeJson(take));noCapture(a);
            });
        }finally{packager.close();}
    }

    @Test(timeout=120_000) public void realMainPackageDiscardsStaleDispatchPlanWhilePausedAndPreservesSyntheticOriginal() throws Exception {
        runRealPackage(true);
    }
    @Test(timeout=120_000) public void realMainPackageKeepsFrozenPlanAndCutsReadyAcrossRecreationWhenUnchanged() throws Exception {
        runRealPackage(false);
    }

    private void runRealPackage(boolean mutate) throws Exception {
        List<Take> demos=demo();Take source=demos.get(0);source.reviewedShotIds.add("package-plan-0");
        source.subtitles.add(new SubtitleCue(700,1200,"Synthetic words"));String originalEdits=takeJson(source);
        File sourceFile=new File(source.uri.getPath());byte[] hash=sha(sourceFile);long size=sourceFile.length(),mtime=sourceFile.lastModified();
        CountDownLatch opened=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger reads=new AtomicInteger();
        ProjectPackager packager=new ProjectPackager(context(),(uri,signal)->{
            reads.incrementAndGet();opened.countDown();if(!release.await(30,TimeUnit.SECONDS))throw new IOException("synthetic gate timeout");
            signal.throwIfCanceled();File file=new File(uri.getPath());return new ProjectPackager.Source(new FileInputStream(file),file.length(),"video/mp4");
        },4*1024*1024);
        AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<JSONObject> dispatchedPlan=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);((ProjectPackager)field(a,"packager")).close();set(a,"packager",packager);takes(a).clear();takes(a).add(source);
                call(a,"save");try{dispatchedPlan.set(ShotPlanSnapshot.capture(shots(a),(String)field(a,"planSource")).toJson());}catch(JSONException e){throw new AssertionError(e);}
                call(a,"packageProject");assertTrue((Boolean)field(a,"busy"));noCapture(a);
            });
            assertTrue("Actual Main dispatch must open real synthetic source",opened.await(20,TimeUnit.SECONDS));
            scenario.moveToState(Lifecycle.State.CREATED);
            if(mutate)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();shots(a).get(0).instruction="Face left in your outfit.";set(a,"planSource","Updated while synthetic packaging paused");call(a,"save");});
            release.countDown();awaitIdleCompletion(activity.get());assertEquals(1,reads.get());
            AtomicReference<File> ready=new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertEquals(originalEdits,takeJson(source));noCapture(a);
                if(mutate){assertNull(field(a,"pendingPack"));assertEquals("",field(a,"pendingPackSnapshot"));assertTrue(((TextView)field(a,"status")).getText().toString().contains("Create a fresh edit package"));}
                else{ready.set((File)field(a,"pendingPack"));assertNotNull(ready.get());assertEquals(call(a,"packSnapshot"),field(a,"pendingPackSnapshot"));}
            });
            if(mutate){for(File file:children(context().getCacheDir()))assertTrue("Stale completed ZIP must be removed",cacheBefore.contains(file.getName())||!file.getName().matches("minifilm-pack-[0-9]+\\.zip"));}
            else{
                owned.add(ready.get());JSONObject project=projectJson(ready.get());assertEquals(dispatchedPlan.get().toString(),project.getJSONObject("shotPlan").toString());
                // Context names only the current plan; original cut edits and explicit mappings remain separately editable.
                JSONArray cuts=project.getJSONArray("cuts");assertEquals(1,cuts.length());assertEquals(source.inMs,cuts.getJSONObject(0).getLong("inMs"));assertEquals(source.outMs,cuts.getJSONObject(0).getLong("outMs"));
                assertEquals("package-plan-0",cuts.getJSONObject(0).getJSONArray("reviewedShotIds").getString(0));
                scenario.moveToState(Lifecycle.State.RESUMED);scenario.recreate();
                scenario.onActivity(a->{assertEquals(ready.get(),field(a,"pendingPack"));assertEquals(call(a,"packSnapshot"),field(a,"pendingPackSnapshot"));
                    assertEquals(originalEdits,takeJson(takes(a).get(0)));noCapture(a);});
            }
            assertArrayEquals(hash,sha(sourceFile));assertEquals(size,sourceFile.length());assertEquals(mtime,sourceFile.lastModified());
        }finally{release.countDown();packager.close();}
    }

    private static JSONObject projectJson(File file) throws Exception {try(ZipFile zip=new ZipFile(file);InputStream input=zip.getInputStream(zip.getEntry("project.json"))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;while((n=input.read(bytes))!=-1)out.write(bytes,0,n);return new JSONObject(out.toString("UTF-8"));}}
    private static List<Take> demo() throws Exception {CountDownLatch done=new CountDownLatch(1);AtomicReference<List<Take>> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        DemoAssets.create(context(),new DemoAssets.Listener(){public void onReady(List<Take> takes){result.set(takes);done.countDown();}public void onError(String message){error.set(message);done.countDown();}});
        assertTrue("Synthetic fixture encoder completed",done.await(40,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(result.get());return result.get();}
    private static void awaitIdleCompletion(MainActivity a) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);AtomicBoolean busy=new AtomicBoolean(true);while(System.nanoTime()<until){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->busy.set((Boolean)field(a,"busy")));if(!busy.get())return;Thread.sleep(25);}fail("Main package did not finish");}
    private static Take metadataTake(){Take t=new Take(Uri.fromFile(new File(context().getCacheDir(),"shot-plan-ui-not-created.mp4")),"original-recording-id","Synthetic take","Creator typography",4000);t.inMs=200;t.outMs=3000;t.reviewedShotIds.add("package-plan-0");t.subtitles.add(new SubtitleCue(400,1000,"Reviewed words"));return t;}
    private static String takeJson(Take t){try{return new JSONObject().put("uri",t.uri.toString()).put("id",t.shotId).put("title",t.title).put("caption",t.caption).put("origin",t.captionOrigin).put("duration",t.durationMs).put("in",t.inMs).put("out",t.outMs).put("selected",t.selected).put("mapping",new JSONArray(t.reviewedShotIds)).put("subtitles",subtitleJson(t.subtitles)).toString();}catch(JSONException e){throw new AssertionError(e);}}
    private static JSONArray subtitleJson(List<SubtitleCue> cues) throws JSONException{JSONArray a=new JSONArray();for(SubtitleCue c:cues)a.put(new JSONObject().put("s",c.startMs).put("e",c.endMs).put("text",c.text));return a;}
    private static byte[] sha(File file) throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return digest.digest();}
    private static Set<String> names(File folder){Set<String> names=new HashSet<>();for(File f:children(folder))names.add(f.getName());return names;}
    private static File[] children(File folder){File[] files=folder.listFiles();return files==null?new File[0]:files;}
    private static void empty(File dir){assertEquals("No creator recovery inputs",0,children(dir).length);}
    private static void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @SuppressWarnings("unchecked") private static List<Shot> shots(MainActivity a){return (List<Shot>)field(a,"shots");}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static Object field(Object a,String name){try{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object a,String name,Object value){try{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object call(Object a,String name){return call(a,name,new Class<?>[0]);}
    private static Object call(Object a,String name,Class<?>[] types,Object...args){try{Method m=a.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(a,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void noCapture(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertEquals(false,field(a,"sequenceActive"));
        assertEquals(0L,field(field(a,"planner"),"handle"));assertEquals(0L,field(field(a,"transcriber"),"activeRequest"));SpeechCoach speech=(SpeechCoach)field(a,"speech");assertFalse(speech.isListening());assertFalse(speech.hasSpeechWork());denied();}
}
