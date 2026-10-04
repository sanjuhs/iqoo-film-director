package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Synthetic terminals on the real owned proposal UI; no model, transcription or source read. */
@RunWith(AndroidJUnit4.class)
public final class StandaloneSubtitleProposalUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    @Before public void requireOwnEmptyUnlockedEmulatorAndBackupAllPreferences() throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.US);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());assertDenied();
        assertEmpty(new File(context().getFilesDir(),"takes"));assertEmpty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreEntirePreferences(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }

    @Test(timeout=60_000) public void proposalAndLaterKeepExistingAliasedWordsProvenanceAndPreferences() throws Exception{
        Take take=take("Primary"),alias=take("Alias");alias.subtitles=take.subtitles;List<SubtitleCue> original=take.subtitles;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            AtomicReference<ClipTranscriber.Listener> callback=new AtomicReference<>();AtomicReference<String> saved=new AtomicReference<>();
            scenario.onActivity(a->{prepare(a,take,alias);saved.set(preferences.getString("state",""));callback.set(job(a,take));callback.get().onComplete(proposed(),1000);
                assertSame(original,take.subtitles);assertSame(original,alias.subtitles);assertOriginal(take);assertEquals("manual-reviewed",take.captionOrigin);assertEquals(saved.get(),preferences.getString("state",""));});idle();
            scenario.onActivity(a->{assertTrue(dialog(a).isShowing());assertTrue(dialog(a).getWindow().getDecorView().isShown());});
            captureOwnedProposalScreenshot();
            scenario.onActivity(a->{AlertDialog dialog=dialog(a);assertTrue(dialog.isShowing());assertEquals("New proposed first words",edits(dialog,"Subtitle words").get(0).getText().toString());
                assertTrue(hasText(dialog.getWindow().getDecorView(),"Later keeps the existing subtitles unchanged"));Button save=dialog.getButton(-1);dialog.getButton(-2).performClick();save.performClick();
                assertNull(field(a,"subtitleReviewDialog"));assertSame(original,take.subtitles);assertSame(original,alias.subtitles);assertOriginal(take);assertEquals("manual-reviewed",take.captionOrigin);assertEquals(saved.get(),preferences.getString("state",""));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void explicitOwnedSaveAppliesToExactReorderedTakeAndPreservesAliasedOriginalWords() throws Exception{
        Take take=take("Target"),alias=take("Alias");alias.subtitles=take.subtitles;List<SubtitleCue> original=take.subtitles;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take,alias);ClipTranscriber.Listener callback=job(a,take);Collections.swap(takes(a),0,1);callback.onComplete(proposed(),1000);assertSame(original,take.subtitles);});idle();
            scenario.onActivity(a->{AlertDialog d=dialog(a);edits(d,"Subtitle words").get(0).setText("Creator corrected proposal");d.getButton(-1).performClick();
                assertNull(field(a,"subtitleReviewDialog"));assertNotSame(original,take.subtitles);assertSame(original,alias.subtitles);assertOriginal(alias);
                assertEquals("Creator corrected proposal",take.subtitles.get(0).text);assertEquals(100,take.subtitles.get(0).startMs);assertEquals(2400,take.subtitles.get(1).endMs);
                assertEquals("creator-reviewed-offline-asr",take.captionOrigin);assertEquals(1000,take.inMs);assertEquals(2000,take.outMs);assertEquals(take,takes(a).get(1));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void emptyMutatedAndReplacedTargetsKeepExistingWordsWithoutOpeningOldIndex() throws Exception{
        Take take=take("Old target"),replacement=take("Replacement");List<SubtitleCue> original=take.subtitles;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take);String saved=preferences.getString("state","");job(a,take).onComplete(Collections.emptyList(),1);
                assertSame(original,take.subtitles);assertOriginal(take);assertEquals(saved,preferences.getString("state",""));assertNull(field(a,"subtitleReviewDialog"));
                job(a,take).onComplete(Arrays.asList((SubtitleCue)null),1);assertSame(original,take.subtitles);assertOriginal(take);assertEquals(saved,preferences.getString("state",""));assertNull(field(a,"subtitleReviewDialog"));
                ClipTranscriber.Listener changed=job(a,take);take.subtitles.get(0).text="New manual words";changed.onComplete(proposed(),1);
                assertEquals("New manual words",take.subtitles.get(0).text);assertSame(original,take.subtitles);assertNull(field(a,"subtitleReviewDialog"));
                ClipTranscriber.Listener old=job(a,take);takes(a).set(0,replacement);old.onComplete(proposed(),1);assertOriginal(replacement);assertNull(field(a,"subtitleReviewDialog"));assertEquals(false,field(a,"busy"));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void cancelWaitsForOwnedCleanupAndOldTerminalsOrCleanupCannotReleaseNewBusyWork() throws Exception{
        Take take=take("Cancelable");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take);ClipTranscriber.Listener old=job(a,take);ClipTranscriber reader=(ClipTranscriber)field(a,"standaloneSubtitleReader");
                // Hold only the idle resource-cleanup gate, never a native request or model.
                set(reader,"cleanupRunning",true);Button cancel=(Button)find((View)field(a,"root"),Button.class,"Cancel local processing");assertNotNull(cancel);cancel.performClick();
                assertEquals(true,field(a,"busy"));assertEquals(true,field(a,"standaloneSubtitleCancelRequested"));assertTrue((Boolean)field(reader,"closed"));assertNotSame(reader,field(a,"transcriber"));
                old.onComplete(proposed(),1);old.onError("Synthetic old error");assertEquals(true,field(a,"busy"));assertOriginal(take);assertNull(field(a,"subtitleReviewDialog"));
                ClipTranscriber.Listener newer=job(a,take);ClipTranscriber newReader=(ClipTranscriber)field(a,"standaloneSubtitleReader");String status=((TextView)field(a,"status")).getText().toString();
                releaseCleanup(reader);old.onError("Late old error");assertSame(newReader,field(a,"standaloneSubtitleReader"));assertEquals(true,field(a,"busy"));assertEquals(status,((TextView)field(a,"status")).getText().toString());
                invoke(a,"cancelStandaloneSubtitles",new Class<?>[]{boolean.class},false);assertEquals(false,field(a,"busy"));newer.onComplete(proposed(),1);assertOriginal(take);assertNull(field(a,"subtitleReviewDialog"));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void actualBackgroundCancelsProposalAndRetainedReviewSaveCannotMutateAfterResume() throws Exception{
        Take take=take("Background");AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<ClipTranscriber.Listener> old=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);prepare(a,take);old.set(job(a,take));});scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();old.get().onComplete(proposed(),1);old.get().onError("Old background error");assertOriginal(take);assertNull(field(a,"subtitleReviewDialog"));assertEquals(false,field(a,"busy"));});
            scenario.moveToState(Lifecycle.State.RESUMED);AtomicReference<Button> save=new AtomicReference<>();
            scenario.onActivity(a->{job(a,take).onComplete(proposed(),1);});idle();scenario.onActivity(a->{save.set(dialog(a).getButton(-1));});
            scenario.moveToState(Lifecycle.State.CREATED);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{save.get().performClick();assertOriginal(take);assertNull(field(activity.get(),"subtitleReviewDialog"));});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{assertOriginal(take);assertNull(field(a,"subtitleReviewDialog"));assertNoMedia(a);});
        }
    }
    private static void captureOwnedProposalScreenshot() throws IOException{
        // Only this labelled synthetic dialog on the required empty emulator is captured.
        // Instrumentation runs under the target UID; retain only this exact synthetic
        // screenshot in its writable cache for the parent's bounded visual review.
        File cache=InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        assertTrue("Target cache directory is available",cache.isDirectory()||cache.mkdirs());
        assertTrue("Target cache is a directory",cache.isDirectory());
        File output=new File(cache,"standalone-subtitle-proposal-screen.png");
        assertTrue("Do not overwrite an earlier screenshot",output.createNewFile());
        Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Synthetic proposal screen is available",screenshot);
        try(FileOutputStream stream=new FileOutputStream(output)){
            assertTrue("Synthetic proposal PNG saved",screenshot.compress(Bitmap.CompressFormat.PNG,100,stream));
        }finally{screenshot.recycle();}
    }
    private static Take take(String title){Take t=new Take(Uri.parse("content://dev.minifilm.synthetic/"+title.replace(" ","-")),"synthetic-shot",title,"Manual fallback",5746);t.inMs=1000;t.outMs=2000;t.captionOrigin="manual-reviewed";t.reviewedShotIds.add("synthetic-assignment");t.subtitles=Arrays.asList(new SubtitleCue(0,500,"Original corrected first"),new SubtitleCue(2300,2800,"Original corrected second"));return t;}
    private static List<SubtitleCue> proposed(){return Arrays.asList(new SubtitleCue(100,700,"New proposed first words"),new SubtitleCue(2100,2400,"New proposed second words"));}
    private static void assertOriginal(Take t){assertEquals("Original corrected first",t.subtitles.get(0).text);assertEquals("Original corrected second",t.subtitles.get(1).text);assertEquals(0,t.subtitles.get(0).startMs);assertEquals(2800,t.subtitles.get(1).endMs);}
    private static void prepare(MainActivity a,Take... clips){takes(a).clear();takes(a).addAll(Arrays.asList(clips));invoke(a,"save",new Class<?>[0]);invoke(a,"render",new Class<?>[0]);}
    private static ClipTranscriber.Listener job(MainActivity a,Take take){
        ClipTranscriber reader=(ClipTranscriber)field(a,"transcriber");int generation=(Integer)field(a,"standaloneSubtitleGeneration")+1;
        set(a,"standaloneSubtitleGeneration",generation);set(a,"standaloneSubtitleReader",reader);set(a,"standaloneSubtitleOwnsBusy",true);set(a,"standaloneSubtitleCancelRequested",false);
        invoke(a,"setBusy",new Class<?>[]{boolean.class},true);
        return (ClipTranscriber.Listener)invoke(a,"standaloneSubtitleListener",new Class<?>[]{ClipTranscriber.class,int.class,Take.class,SubtitleReviewDraft.class},reader,generation,take,SubtitleReviewDraft.capture(take));
    }
    private static void releaseCleanup(ClipTranscriber reader){invoke(reader,"runResourceCleanups",new Class<?>[]{List.class},Collections.singletonList((Runnable)()->{}));}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"subtitleReviewDialog");assertNotNull(d);return d;}
    private static List<EditText> edits(AlertDialog d,String hint){ArrayList<EditText> result=new ArrayList<>();collectEdits(d.getWindow().getDecorView(),hint,result);return result;}
    private static void collectEdits(View view,String hint,List<EditText> result){if(view instanceof EditText&&hint.contentEquals(((EditText)view).getHint()))result.add((EditText)view);if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)collectEdits(g.getChildAt(i),hint,result);}}
    private static boolean hasText(View view,String part){if(view instanceof TextView&&((TextView)view).getText().toString().contains(part))return true;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(hasText(g.getChildAt(i),part))return true;}return false;}
    private static View find(View view,Class<?> type,String text){if(type.isInstance(view)&&view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),type,text);if(found!=null)return found;}}return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
