package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.security.MessageDigest;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit real sound check of one fixed synthetic sentence with the production coach.
 * Engine start/completion proves the playback path, not human audibility, a selected
 * Bluetooth route or AirPods identity. No capture, microphone, model or volume changes. */
@RunWith(AndroidJUnit4.class)
public final class LiveSpeechPlaybackDeviceTest {
    private static final String FINISHED_STATUS="Voice playback finished. Did you hear it on your selected speaker or headset?";
    private static final String FAILED_STATUS="Voice could not play. Check media volume, selected output and offline Android voice.";

    @Test(timeout=100_000)
    public void actualForegroundOfflineCueStartsAndCompletesWithMediaFocusWithoutCapture() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before this explicitly authorized sound check",keyguard!=null&&keyguard.isKeyguardLocked());
        int camera=context.checkSelfPermission(Manifest.permission.CAMERA),mic=context.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        requireNoPendingExport(context);
        Map<String,String> originalFiles=metadata(context.getFilesDir());
        Map<String,String> runtimeHashes=runtimeHashes(context.getFilesDir(),originalFiles);
        boolean originalFrameworkMarker=originalFiles.containsKey("profileInstalled");
        if(originalFrameworkMarker)validateProfileVerifierMarker(context,false);
        AtomicBoolean frameworkMarkerChanged=new AtomicBoolean();
        SharedPreferences prefs=context.getSharedPreferences("shoot",0);Map<String,Object> original=backup(prefs);
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);assertNotNull(audio);
        int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);boolean muted=audio.isStreamMute(AudioManager.STREAM_MUSIC);
        ActivityScenario<MainActivity> scenario=null;AtomicReference<SpeechCoach> owned=new AtomicReference<>();
        AtomicInteger starts=new AtomicInteger(),finished=new AtomicInteger(),completed=new AtomicInteger(),failed=new AtomicInteger();
        AtomicBoolean callbacksOnMain=new AtomicBoolean(true),engineSuccess=new AtomicBoolean(),focusGranted=new AtomicBoolean();
        AtomicLong startedAt=new AtomicLong(),finishedAt=new AtomicLong();CountDownLatch engineDone=new CountDownLatch(1);
        long requestedAt=0;JSONObject metrics=null;
        try {
            JSONObject controlled=new JSONObject(prefs.getString("state","{}"));controlled.put("tab",1).put("voice",false).put("guideSequence",false);
            assertTrue(prefs.edit().putString("state",controlled.toString()).commit());
            scenario=ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra("open_screen","camera"));
            scenario.onActivity(a->{assertNoCapture(a);assertEquals(false,field(a,"voice"));owned.set((SpeechCoach)field(a,"speech"));});
            SpeechCoach coach=owned.get();long readyDeadline=SystemClock.elapsedRealtime()+30_000;AtomicBoolean ready=new AtomicBoolean();
            while(SystemClock.elapsedRealtime()<readyDeadline){scenario.onActivity(a->{assertNoCapture(a);ready.set(coach.isOfflineVoiceReady());});if(ready.get())break;SystemClock.sleep(50);}
            assertTrue("An already installed offline English voice must initialize",ready.get());
            scenario.onActivity(a->{
                assertNoCapture(a);assertTrue(a.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED));
                TextToSpeech engine=(TextToSpeech)field(coach,"tts");Voice voice=engine.getVoice();assertNotNull(voice);assertFalse(voice.isNetworkConnectionRequired());assertEquals("en",voice.getLocale().getLanguage());
                assertFalse(coach.hasSpeechWork());assertFalse(coach.isListening());
                coach.setPlaybackObserver(new SpeechCoach.PlaybackObserver(){
                    public void onStarted(){callbacksOnMain.compareAndSet(true,Looper.myLooper()==Looper.getMainLooper());starts.incrementAndGet();startedAt.set(SystemClock.elapsedRealtime());}
                    public void onFinished(boolean success){callbacksOnMain.compareAndSet(true,Looper.myLooper()==Looper.getMainLooper());finished.incrementAndGet();engineSuccess.set(success);finishedAt.set(SystemClock.elapsedRealtime());engineDone.countDown();}
                });
            });
            awaitVisibleVoiceButton(scenario);
            requestedAt=SystemClock.elapsedRealtime();
            scenario.onActivity(a->{
                assertNoCapture(a);Button button=findButton((View)field(a,"root"),"Test voice");assertNotNull(button);
                Rect bounds=new Rect();assertTrue(button.getGlobalVisibleRect(bounds));assertEquals(button.getWidth(),bounds.width());assertEquals(button.getHeight(),bounds.height());assertTrue(button.isEnabled());
                assertTrue("Click the actual current app sound-check control",button.performClick());
                AudioFocusRequest focus=(AudioFocusRequest)field(coach,"activeFocus");assertNotNull("Real AudioManager must grant focus before enqueue",focus);
                assertEquals(AudioAttributes.USAGE_MEDIA,focus.getAudioAttributes().getUsage());assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH,focus.getAudioAttributes().getContentType());focusGranted.set(true);
            });
            assertTrue("Actual engine terminal observation required",engineDone.await(50,TimeUnit.SECONDS));
            long statusDeadline=SystemClock.elapsedRealtime()+5000;AtomicBoolean statusObserved=new AtomicBoolean();
            while(SystemClock.elapsedRealtime()<statusDeadline&&!statusObserved.get()){scenario.onActivity(a->{
                String status=((TextView)field(a,"status")).getText().toString();
                if(FINISHED_STATUS.equals(status)){completed.set(1);statusObserved.set(true);}
                else if(FAILED_STATUS.equals(status)){failed.set(1);statusObserved.set(true);}
            });if(!statusObserved.get())SystemClock.sleep(25);}
            assertTrue("Main must publish the actual Test voice terminal status",statusObserved.get());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(1,starts.get());assertEquals(1,finished.get());assertEquals(1,completed.get());assertEquals(0,failed.get());assertTrue(engineSuccess.get());assertTrue(callbacksOnMain.get());assertTrue(focusGranted.get());
            assertTrue(startedAt.get()>=requestedAt);assertTrue(finishedAt.get()>=startedAt.get());
            scenario.onActivity(a->{assertNoCapture(a);assertFalse(coach.hasSpeechWork());assertFalse(coach.isListening());assertNull(field(coach,"activeFocus"));assertNull(field(coach,"activeUtterance"));coach.setPlaybackObserver(null);});
            metrics=new JSONObject().put("syntheticOnly",true).put("offlineEnglish",true).put("usage","MEDIA").put("content","SPEECH")
                    .put("startCallbacks",starts.get()).put("engineDoneCallbacks",finished.get()).put("completedCallbacks",completed.get()).put("failureCallbacks",failed.get())
                    .put("mainThreadCallbacks",callbacksOnMain.get()).put("realFocusGranted",focusGranted.get()).put("focusAbandoned",true)
                    .put("startDelayMs",startedAt.get()-requestedAt).put("enginePlaybackIntervalMs",finishedAt.get()-startedAt.get())
                    .put("humanAudibilityVerified",false).put("headsetIdentityVerified",false).put("selectedRouteVerified",false)
                    .put("cameraOpened",false).put("microphoneOpened",false).put("actualTestVoiceButton",true).put("mainCompletionStatusObserved",true);
            // Keep actual engine evidence even if a later preservation check fails.
            // These metrics deliberately do not claim the still-pending file/prefs checks.
            Log.i("MiniFilmLiveSpeech",metrics.put("stage","engine_checks_passed").put("preservationChecksPending",true).toString());
        } finally {
            if(scenario!=null){scenario.onActivity(a->{SpeechCoach coach=owned.get();if(coach!=null){coach.setPlaybackObserver(null);coach.stop();}});scenario.close();}
            restore(prefs,original);
            assertTrue("Full original preferences restored without printing creator text",original.equals(prefs.getAll()));
            Map<String,String> afterFiles=metadata(context.getFilesDir());
            if(!originalFiles.equals(afterFiles))logMetadataDifferences(context.getFilesDir(),originalFiles,afterFiles,runtimeHashes);
            Map<String,String> preservedBefore=new TreeMap<>(originalFiles),preservedAfter=new TreeMap<>(afterFiles);
            // AndroidX 1.3.1 rewrites its installation/profile cache on startup.
            // Exempt exactly this existing validated framework file, never creator files.
            if(originalFrameworkMarker&&afterFiles.containsKey("profileInstalled")){
                validateProfileVerifierMarker(context,true);
                frameworkMarkerChanged.set(!java.util.Objects.equals(originalFiles.get("profileInstalled"),afterFiles.get("profileInstalled"))||!java.util.Objects.equals(runtimeHashes.get("profileInstalled"),boundedRuntimeHash(context.getFilesDir(),"profileInstalled")));
                preservedBefore.remove("profileInstalled");preservedAfter.remove("profileInstalled");
            }
            assertTrue("All original model/source/take/export and other runtime files must stay unchanged",preservedBefore.equals(preservedAfter));
            assertEquals(camera,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(mic,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
            assertEquals("Media volume must not be changed by this test",volume,audio.getStreamVolume(AudioManager.STREAM_MUSIC));assertEquals(muted,audio.isStreamMute(AudioManager.STREAM_MUSIC));
        }
        Log.i("MiniFilmLiveSpeech",metrics.put("stage","pipeline_pass").put("preservationChecksPending",false).put("volumeChanged",false).put("preferencesRestored",true).put("sourceFileMetadataUnchanged",true).put("permissionsUnchanged",true).put("knownFrameworkMarkerChanged",frameworkMarkerChanged.get()).put("otherPrivateFileMetadataUnchanged",true).toString());
    }

    private static void assertNoCapture(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertEquals(false,field(a,"live"));assertEquals(false,field(a,"busy"));assertEquals(false,field(a,"sequenceActive"));}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception error){throw new AssertionError("Expected production playback state unavailable",error);}}
    private static void requireNoPendingExport(Context context) throws Exception {File[] files=new File(context.getFilesDir(),"export-journal").listFiles();if(files==null)return;assertTrue("At most one settled export may be present",files.length<=1);for(File file:files){assertTrue(file.getName().endsWith(".json"));assertTrue(file.length()>0&&file.length()<=16384);String text=new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);assertEquals("COMPLETE",new JSONObject(text).getString("state"));}}
    private static Map<String,Object> backup(SharedPreferences preferences){Map<String,Object> original=new HashMap<>();for(Map.Entry<String,?> e:preferences.getAll().entrySet()){Object value=e.getValue();if(value instanceof Set)value=new HashSet<>((Set<?>)value);original.put(e.getKey(),value);}return original;}
    private static void restore(SharedPreferences preferences,Map<String,Object> original){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,Object> e:original.entrySet()){String key=e.getKey();Object v=e.getValue();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");}assertTrue(editor.commit());}
    private static Map<String,String> metadata(File directory){Map<String,String> values=new TreeMap<>();snapshot(directory,directory,values);return values;}
    private static void snapshot(File root,File directory,Map<String,String> values){File[] files=directory.listFiles();assertNotNull("Private file inventory must be readable",files);for(File file:files){assertTrue("Bound private metadata inventory",values.size()<2000);if(file.isDirectory())snapshot(root,file,values);else values.put(root.toPath().relativize(file.toPath()).toString(),file.length()+":"+file.lastModified());}}
    private static void logMetadataDifferences(File root,Map<String,String> before,Map<String,String> after,Map<String,String> runtimeHashes) throws Exception {
        String[] categories={"model","source","take","export-journal","export-edit","runtime-other"};
        Map<String,int[]> counts=new TreeMap<>();for(String category:categories)counts.put(category,new int[3]);
        Set<String> keys=new HashSet<>(before.keySet());keys.addAll(after.keySet());
        for(String key:keys){String old=before.get(key),current=after.get(key);if(java.util.Objects.equals(old,current))continue;
            int[] count=counts.get(category(key));count[old==null?0:current==null?1:2]++;
            if("runtime-other".equals(category(key)))logRuntimeDifference(root,key,old,current,runtimeHashes.get(key));
        }
        JSONObject difference=new JSONObject().put("stage","file_metadata_mismatch").put("beforeFiles",before.size()).put("afterFiles",after.size());
        for(String category:categories){int[] count=counts.get(category);difference.put(category,new JSONObject().put("added",count[0]).put("removed",count[1]).put("changed",count[2]));}
        Log.i("MiniFilmLiveSpeech",difference.toString()); // Never emit paths, IDs, model names or creator text.
    }
    private static void awaitVisibleVoiceButton(ActivityScenario<MainActivity> scenario){
        long deadline=SystemClock.elapsedRealtime()+5000;AtomicBoolean visible=new AtomicBoolean();
        while(SystemClock.elapsedRealtime()<deadline){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{Button button=findButton((View)field(a,"root"),"Test voice");assertNotNull(button);ScrollView scroll=(ScrollView)field(a,"pageScroll");
                if(!button.isLaidOut()||button.isLayoutRequested()||button.getHeight()<=0||scroll.getHeight()<=0)return;
                int[] at=new int[2],top=new int[2];button.getLocationOnScreen(at);scroll.getLocationOnScreen(top);scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));
                Rect rect=new Rect();visible.set(button.getGlobalVisibleRect(rect)&&rect.width()==button.getWidth()&&rect.height()==button.getHeight());
            });if(visible.get())return;SystemClock.sleep(16);
        }fail("The actual Test voice button must be fully visible after scrolling");
    }
    private static Button findButton(View view,String text){if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return(Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=findButton(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static Map<String,String> runtimeHashes(File root,Map<String,String> metadata)throws Exception{
        Map<String,String> hashes=new HashMap<>();for(String path:metadata.keySet())if("profileInstalled".equals(path)){String hash=boundedRuntimeHash(root,path);if(hash!=null)hashes.put(path,hash);}return hashes;
    }
    private static String boundedRuntimeHash(File root,String path)throws Exception{
        File file=new File(root,path);if(!file.isFile()||file.length()>1_048_576||!file.getCanonicalFile().equals(new File(root.getCanonicalFile(),path)))return null;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];long read=0;
        try(FileInputStream input=new FileInputStream(file)){int n;while((n=input.read(buffer))!=-1){read+=n;if(read>1_048_576)return null;digest.update(buffer,0,n);}}
        return hex(digest.digest());
    }
    private static void logRuntimeDifference(File root,String path,String before,String after,String oldHash)throws Exception{
        String role=path.equals("profileInstalled")?"androidx-profile-verifier":path.equals("profileinstaller_profileWrittenFor_lastUpdateTime.dat")?"androidx-profile-installer":path.contains("/")?"other-nested-runtime":"other-root-runtime";
        JSONObject detail=new JSONObject().put("stage","runtime_file_detail").put("role",role).put("added",before==null).put("removed",after==null);
        if(role.startsWith("other-"))detail.put("relativePathSha256",hex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        if(before!=null&&after!=null){String[] old=before.split(":"),now=after.split(":");detail.put("beforeBytes",Long.parseLong(old[0])).put("afterBytes",Long.parseLong(now[0])).put("sizeChanged",!old[0].equals(now[0])).put("mtimeChanged",!old[1].equals(now[1]));}
        String newHash=boundedRuntimeHash(root,path);boolean known=oldHash!=null&&newHash!=null;detail.put("byteEqualityKnown",known);if(known)detail.put("bytesHashSame",oldHash.equals(newHash));
        Log.i("MiniFilmLiveSpeech",detail.toString()); // No paths, file contents, hardware IDs or creator text.
    }
    private static void validateProfileVerifierMarker(Context context,boolean currentInstall)throws Exception{
        File file=new File(context.getFilesDir(),"profileInstalled");
        assertTrue("Only the exact direct-child AndroidX cache may change",file.getCanonicalFile().equals(new File(context.getFilesDir().getCanonicalFile(),"profileInstalled")));
        assertEquals("Resolved AndroidX ProfileVerifier cache layout is 24 bytes",24,file.length());
        // Verified against ProfileVerifier$Cache.readFromFile/writeOnFile in the
        // resolved androidx.profileinstaller:profileinstaller:1.3.1 runtime.
        // This records installation/profile bookkeeping, not app media or preferences.
        try(DataInputStream input=new DataInputStream(new FileInputStream(file))){
            assertEquals("AndroidX profile cache schema",1,input.readInt());int result=input.readInt();assertTrue("Recognized normal profile-state code",result>=0&&result<=3);
            long updated=input.readLong(),profileBytes=input.readLong();long actual=context.getPackageManager().getPackageInfo(context.getPackageName(),0).lastUpdateTime;
            assertTrue("Profile metadata references an actual prior/current installation",updated>0&&updated<=actual);
            if(currentInstall)assertEquals("Startup cache must reference the current installed application",actual,updated);
            assertTrue("Profile size must be nonnegative",profileBytes>=0);assertEquals(-1,input.read());
        }
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format(java.util.Locale.US,"%02x",value&255));return out.toString();}
    private static String category(String path){
        if(path.startsWith("takes/"))return "take";
        if(path.startsWith("export-journal/"))return "export-journal";
        if(path.startsWith("exports/"))return "export-edit";
        if(path.startsWith("models/")||path.endsWith(".gguf")||path.endsWith(".bin"))return "model";
        if(path.startsWith("fixtures/")||path.startsWith("synthetic-demo/")||path.endsWith(".mp4")||path.endsWith(".m4a")||path.endsWith(".png")||path.endsWith(".jpg"))return "source";
        return "runtime-other";
    }
}
