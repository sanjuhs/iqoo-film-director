package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Owned trim UI with labelled synthetic PCM/ASR timing; no source read, model or playback. */
@RunWith(AndroidJUnit4.class)
public final class SpeechTrimReviewUiTest {
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

    @Test(timeout=60_000) public void currentApplyUsesExactReorderedTakeAndPreservesWordsTitleMappingsAndSource() throws Exception{
        Take target=take("Target"),other=take("Other");List<SubtitleCue> words=target.subtitles;List<String> mappings=target.reviewedShotIds;
        SpeechTrim candidate=candidate(target);assertTrue(candidate.hasSuggestion);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,target,other);ClipTranscriber.TrimListener callback=job(a,target);Collections.swap(takes(a),0,1);callback.onComplete(candidate,1);assertEquals(0,target.inMs);assertEquals(6000,target.outMs);});idle();
            scenario.onActivity(a->{dialog(a).getButton(-1).performClick();assertEquals(candidate.suggestedInMs,target.inMs);assertEquals(candidate.suggestedOutMs,target.outMs);
                assertSame(words,target.subtitles);assertSame(mappings,target.reviewedShotIds);assertNonTrim(target,"Target");assertEquals(target,takes(a).get(1));assertEquals(0,other.inMs);assertEquals(6000,other.outMs);
                assertNull(field(a,"speechTrimDialog"));assertNull(field(a,"pendingSpeechTrim"));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void keepInvalidatesSynchronouslyAndAllRetainedActionsLeaveNewerSuggestionUntouched() throws Exception{
        Take take=take("Keep ownership");SpeechTrim first=candidate(take),second=candidate(take);
        Instrumentation.ActivityMonitor monitor=monitor();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take);publish(a,take,first);});idle();
            scenario.onActivity(a->{AlertDialog old=dialog(a);Button apply=old.getButton(-1),preview=old.getButton(-3),keep=old.getButton(-2);
                keep.performClick();assertNull(field(a,"speechTrimDialog"));assertNull(field(a,"pendingSpeechTrim"));publish(a,take,second);
                keep.performClick();apply.performClick();preview.performClick();assertSame(second,field(a,"pendingSpeechTrim"));assertSame(take,field(a,"pendingSpeechTrimTake"));assertEquals(0,take.inMs);assertEquals(6000,take.outMs);assertNotSame(old,dialog(a));assertNoMedia(a);});
            assertEquals("Old Preview dispatches no activity",0,monitor.getHits());
        }finally{InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
    }
    @Test(timeout=60_000) public void currentPreviewKeepsPendingSuggestionAndBackgroundDismissesOldModalUntilExplicitReview() throws Exception{
        Take take=take("Preview review");SpeechTrim candidate=candidate(take);AtomicReference<Button> oldApply=new AtomicReference<>(),oldKeep=new AtomicReference<>(),oldPreview=new AtomicReference<>();AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicInteger scrollY=new AtomicInteger();
        Instrumentation.ActivityMonitor monitor=monitor();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);prepare(a,take,take("OtherA"),take("OtherB"));publish(a,take,candidate);});idle();
            scenario.onActivity(a->{AlertDialog d=dialog(a);d.getButton(-3).performClick();assertNull(field(a,"speechTrimDialog"));assertSame(candidate,field(a,"pendingSpeechTrim"));assertSame(take,field(a,"pendingSpeechTrimTake"));assertEquals(0,take.inMs);assertEquals(6000,take.outMs);});
            assertEquals("Current Preview explicitly dispatches once, intercepted before playback",1,monitor.getHits());
            scenario.onActivity(a->{((ScrollView)field(a,"pageScroll")).scrollTo(0,150);});idle();
            scenario.onActivity(a->{scrollY.set(((ScrollView)field(a,"pageScroll")).getScrollY());assertTrue("Actual list has a nonzero review position",scrollY.get()>0);invoke(a,"reviewSpeechTrim",new Class<?>[]{Take.class,SpeechTrim.class},take,candidate);});idle();
            scenario.onActivity(a->{AlertDialog d=dialog(a);oldApply.set(d.getButton(-1));oldKeep.set(d.getButton(-2));oldPreview.set(d.getButton(-3));});
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertNull(field(a,"speechTrimDialog"));oldApply.get().performClick();oldKeep.get().performClick();oldPreview.get().performClick();assertSame(candidate,field(a,"pendingSpeechTrim"));assertEquals(0,take.inMs);assertEquals(6000,take.outMs);});
            scenario.moveToState(Lifecycle.State.RESUMED);awaitScroll(scenario,scrollY.get());scenario.onActivity(a->{assertNull("No automatic modal reopen",field(a,"speechTrimDialog"));assertSame(candidate,field(a,"pendingSpeechTrim"));
                invoke(a,"reviewSpeechTrim",new Class<?>[]{Take.class,SpeechTrim.class},take,candidate);});idle();scenario.onActivity(a->{assertTrue(dialog(a).isShowing());assertNonTrim(take,"Preview review");assertNoMedia(a);});
            assertEquals("Retained old Preview never dispatches again",1,monitor.getHits());
        }finally{InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);}
    }
    @Test(timeout=60_000) public void changedRangeSourceReplacedTakeAndRenderedDialogCannotApply(){
        Take take=take("Stale facts"),replacement=take("Replacement");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take);publish(a,take,candidate(take));});idle();
            scenario.onActivity(a->{Button old=dialog(a).getButton(-1);take.inMs=20;old.performClick();assertEquals(20,take.inMs);assertEquals(6000,take.outMs);dialog(a).getButton(-2).performClick();assertNull(field(a,"speechTrimDialog"));assertNull(field(a,"pendingSpeechTrim"));assertEquals(20,take.inMs);assertEquals(6000,take.outMs);
                take.inMs=0;SpeechTrim fresh=candidate(take);publish(a,take,fresh);});idle();
            scenario.onActivity(a->{Button old=dialog(a).getButton(-1);Uri original=take.uri;take.uri=Uri.parse("content://dev.minifilm.synthetic/changed");old.performClick();assertEquals(0,take.inMs);assertEquals(6000,take.outMs);take.uri=original;
                takes(a).set(0,replacement);old.performClick();assertEquals(0,replacement.inMs);assertEquals(6000,replacement.outMs);invoke(a,"render",new Class<?>[0]);old.performClick();assertEquals(0,take.inMs);assertEquals(6000,take.outMs);assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void cancelWaitsForCleanupAndOldCallbacksCannotClearNewBusyOrBackgroundPublish() throws Exception{
        Take take=take("Worker ownership");SpeechTrim candidate=candidate(take);AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<ClipTranscriber.TrimListener> retained=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);prepare(a,take);ClipTranscriber.TrimListener old=job(a,take);ClipTranscriber reader=(ClipTranscriber)field(a,"speechTrimReader");
                set(reader,"cleanupRunning",true);Button cancel=(Button)find((View)field(a,"root"),Button.class,"Cancel local processing");cancel.performClick();assertEquals(true,field(a,"busy"));assertEquals(true,field(a,"speechTrimCancelRequested"));assertTrue((Boolean)field(reader,"closed"));
                old.onComplete(candidate,1);old.onError("Old synthetic error");assertNull(field(a,"pendingSpeechTrim"));assertEquals(true,field(a,"busy"));
                ClipTranscriber.TrimListener newer=job(a,take);ClipTranscriber current=(ClipTranscriber)field(a,"speechTrimReader");String status=((TextView)field(a,"status")).getText().toString();
                releaseCleanup(reader);old.onError("Old synthetic error");assertSame(current,field(a,"speechTrimReader"));assertEquals(true,field(a,"busy"));assertEquals(status,((TextView)field(a,"status")).getText().toString());
                invoke(a,"cancelSpeechTrim",new Class<?>[]{boolean.class},false);newer.onComplete(candidate,1);assertNull(field(a,"pendingSpeechTrim"));assertEquals(false,field(a,"busy"));retained.set(job(a,take));});
            scenario.moveToState(Lifecycle.State.CREATED);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();retained.get().onComplete(candidate,1);retained.get().onError("Old background error");assertNull(field(a,"pendingSpeechTrim"));assertNull(field(a,"speechTrimDialog"));assertEquals(false,field(a,"busy"));assertEquals(0,take.inMs);assertEquals(6000,take.outMs);});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{assertNull(field(a,"speechTrimDialog"));assertNonTrim(take,"Worker ownership");assertNoMedia(a);});
        }
    }
    private static void awaitScroll(ActivityScenario<MainActivity> scenario,int expected){long deadline=android.os.SystemClock.elapsedRealtime()+5000;boolean[] restored={false};
        while(android.os.SystemClock.elapsedRealtime()<deadline){idle();scenario.onActivity(a->{restored[0]=((ScrollView)field(a,"pageScroll")).getScrollY()==expected;});if(restored[0])return;try{Thread.sleep(16);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
        fail("Reviewed pending suggestion did not preserve the actual list position after resume");
    }
    private static SpeechTrim candidate(Take take){float[] samples=new float[96_000];for(int i=19_200;i<70_400;i++)samples[i]=(float)(.12*Math.sin(2*Math.PI*220*i/16_000));
        SpeechTrim result=SpeechTrim.analyze(take.uri,samples,0,6000,0,6000,Collections.singletonList(new SubtitleCue(1000,4700,"Synthetic timing only")),()->false);assertTrue(result.hasSuggestion);return result;}
    private static Take take(String title){Take t=new Take(Uri.parse("content://dev.minifilm.synthetic/"+title.replace(" ","-")),"synthetic-shot",title,"Manual caption",6000);t.captionOrigin="creator-reviewed";t.reviewedShotIds.add("creator-shot");t.subtitles=Arrays.asList(new SubtitleCue(100,500,"Original words"));return t;}
    private static void assertNonTrim(Take t,String title){assertEquals(title,t.title);assertEquals("Manual caption",t.caption);assertEquals("creator-reviewed",t.captionOrigin);assertTrue(t.selected);assertEquals(Collections.singletonList("creator-shot"),t.reviewedShotIds);assertEquals("Original words",t.subtitles.get(0).text);assertEquals(100,t.subtitles.get(0).startMs);assertEquals(500,t.subtitles.get(0).endMs);assertEquals(6000,t.durationMs);assertEquals("synthetic-shot",t.shotId);assertEquals(Uri.parse("content://dev.minifilm.synthetic/"+title.replace(" ","-")),t.uri);}
    private static void publish(MainActivity a,Take take,SpeechTrim candidate){set(a,"pendingSpeechTrim",candidate);set(a,"pendingSpeechTrimTake",take);invoke(a,"reviewSpeechTrim",new Class<?>[]{Take.class,SpeechTrim.class},take,candidate);}
    private static ClipTranscriber.TrimListener job(MainActivity a,Take take){ClipTranscriber reader=(ClipTranscriber)field(a,"transcriber");int generation=(Integer)field(a,"speechTrimGeneration")+1;
        set(a,"speechTrimGeneration",generation);set(a,"speechTrimReader",reader);set(a,"speechTrimOwnsBusy",true);set(a,"speechTrimCancelRequested",false);invoke(a,"setBusy",new Class<?>[]{boolean.class},true);
        return (ClipTranscriber.TrimListener)invoke(a,"speechTrimListener",new Class<?>[]{ClipTranscriber.class,int.class,Take.class},reader,generation,take);}
    private static Instrumentation.ActivityMonitor monitor(){return InstrumentationRegistry.getInstrumentation().addMonitor(PreviewActivity.class.getName(),new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);}
    private static void releaseCleanup(ClipTranscriber reader){invoke(reader,"runResourceCleanups",new Class<?>[]{List.class},Collections.singletonList((Runnable)()->{}));}
    private static void prepare(MainActivity a,Take... clips){takes(a).clear();takes(a).addAll(Arrays.asList(clips));invoke(a,"save",new Class<?>[0]);invoke(a,"render",new Class<?>[0]);}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"speechTrimDialog");assertNotNull(d);return d;}
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
