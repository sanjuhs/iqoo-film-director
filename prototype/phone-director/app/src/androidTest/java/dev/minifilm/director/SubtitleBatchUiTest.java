package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Future unlocked UI checks with controlled readers: no media, models, mic or playback. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleBatchUiTest {
    private static final String ACTION="Draft missing subtitles for selected takes";
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;

    @Before public void unlockedDeniedAndPreservePreferences() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the test device before UI checks",keyguard!=null&&keyguard.isKeyguardLocked());assertDenied(context);
        preferences=context.getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false).toString()).commit());
    }
    @After public void restorePreferencesAndDeniedPermissions(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
            else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)value;editor.putStringSet(key,new HashSet<>(strings));}
            else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test(timeout=30_000) public void actualButtonDraftsOnlySelectedMissingWordsKeepsEditsAndPersistsReviewableResult(){
        FakeFactory factory=new FakeFactory();Take missing=take("missing"),edited=take("edited"),unselected=take("unselected");
        List<SubtitleCue> reviewed=new ArrayList<>(Arrays.asList(new SubtitleCue(100,900,"Creator-reviewed existing words")));
        edited.subtitles=reviewed;edited.captionOrigin="manual-reviewed";unselected.selected=false;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                prepare(a,factory,missing,edited,unselected);Object oldPlanner=field(a,"planner");click(a,ACTION);
                assertEquals(true,field(a,"busy"));assertNotSame(oldPlanner,field(a,"planner"));assertEquals(true,field(oldPlanner,"closed"));
                assertNoCapture(a);
            });
            idle();scenario.onActivity(a->{
                assertEquals(1,factory.readers.size());assertEquals(missing.uri,factory.readers.get(0).source);
                factory.readers.get(0).success("Synthetic first draft words");assertTrue(missing.subtitles.isEmpty());assertEquals(true,field(a,"busy"));
            });
            idle();scenario.onActivity(a->factory.readers.get(0).release());idle();
            scenario.onActivity(a->{
                assertEquals(false,field(a,"busy"));assertNull(field(a,"activeSubtitleBatch"));
                assertEquals("Synthetic first draft words",missing.subtitles.get(0).text);assertEquals("whisper-tiny.en-draft",missing.captionOrigin);
                assertSame(reviewed,edited.subtitles);assertEquals("Creator-reviewed existing words",edited.subtitles.get(0).text);assertEquals("manual-reviewed",edited.captionOrigin);
                assertTrue(unselected.subtitles.isEmpty());assertEquals(1,factory.readers.size());assertTrue(status(a).contains("1 subtitle drafts saved"));
                assertTrue(status(a).contains("Review words and timing"));assertNotNull(button((View)field(a,"root"),"Review subtitle words & timing"));assertNoCapture(a);
            });
            scenario.recreate();scenario.onActivity(a->{
                List<Take> restored=takes(a);assertEquals(3,restored.size());assertEquals("Synthetic first draft words",restored.get(0).subtitles.get(0).text);
                assertEquals("Creator-reviewed existing words",restored.get(1).subtitles.get(0).text);assertFalse(restored.get(2).selected);
                assertNull(field(a,"subtitleBatch"));assertEquals(false,field(a,"busy"));assertEquals(1,factory.readers.size());assertNoCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void cancelHoldsBusyUntilIdleKeepsCompletedDraftAndRejectsLateNextResult(){
        FakeFactory factory=new FakeFactory();Take first=take("first"),second=take("second");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,factory,first,second);click(a,ACTION);});idle();
            scenario.onActivity(a->factory.readers.get(0).success("Completed synthetic words"));idle();
            scenario.onActivity(a->factory.readers.get(0).release());idle();
            scenario.onActivity(a->{
                assertEquals(2,factory.readers.size());assertFalse(first.subtitles.isEmpty());click(a,"Cancel local processing");
                assertEquals(true,field(a,"busy"));assertEquals(true,field(a,"subtitleBatchCancelRequested"));
                factory.readers.get(1).success("Late words must not be applied");assertTrue(second.subtitles.isEmpty());assertNoCapture(a);
            });idle();
            scenario.onActivity(a->{assertEquals(true,field(a,"busy"));factory.readers.get(1).release();});idle();
            scenario.onActivity(a->{
                assertEquals(false,field(a,"busy"));assertNull(field(a,"activeSubtitleBatch"));assertEquals("Completed synthetic words",first.subtitles.get(0).text);
                assertTrue(second.subtitles.isEmpty());assertTrue(status(a).contains("1 completed drafts kept"));
                String status=status(a);factory.readers.get(1).success("Duplicate stale words");assertEquals(status,status(a));assertTrue(second.subtitles.isEmpty());assertNoCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void backgroundCancelsReadingWithoutAutoResumeAndChangedWordsRemainUntouched(){
        FakeFactory factory=new FakeFactory();Take target=take("background");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,factory,target);click(a,ACTION);});idle();
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{
                assertEquals(true,field(a,"busy"));assertEquals(1,factory.readers.size());assertTrue(target.subtitles.isEmpty());
                target.subtitles=new ArrayList<>(Arrays.asList(new SubtitleCue(100,900,"New creator edit during cancellation")));target.captionOrigin="manual-reviewed";
                factory.readers.get(0).success("Old synthetic draft");
            });idle();scenario.onActivity(a->factory.readers.get(0).release());idle();
            scenario.onActivity(a->{
                assertEquals(false,field(a,"busy"));assertEquals(1,factory.readers.size());assertEquals("New creator edit during cancellation",target.subtitles.get(0).text);
                assertEquals("manual-reviewed",target.captionOrigin);assertNull(field(a,"activeSubtitleBatch"));assertNoCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void previousBatchTerminalCannotClearNewGenerationOrDifferentBusyOwner(){
        FakeFactory factory=new FakeFactory();Take target=take("generation");AtomicInteger oldGeneration=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,factory,target);click(a,ACTION);oldGeneration.set((Integer)field(a,"subtitleBatchGeneration"));});idle();
            scenario.onActivity(a->{click(a,"Cancel local processing");factory.readers.get(0).release();});idle();
            scenario.onActivity(a->{click(a,ACTION);});idle();
            scenario.onActivity(a->{
                SubtitleBatch current=(SubtitleBatch)field(a,"activeSubtitleBatch");assertNotNull(current);assertEquals(2,factory.readers.size());
                assertEquals(false,invoke(a,"finishSubtitleBatch",new Class<?>[]{SubtitleBatch.class,int.class},current,oldGeneration.get()));
                assertEquals(true,field(a,"busy"));assertSame(current,field(a,"activeSubtitleBatch"));
                // An idle acknowledgement for cleanup-only ownership must not release a
                // different operation's busy gate, even when identity/generation match.
                set(a,"subtitleBatchOwnsBusy",false);int currentGeneration=(Integer)field(a,"subtitleBatchGeneration");
                assertEquals(false,invoke(a,"finishSubtitleBatch",new Class<?>[]{SubtitleBatch.class,int.class},current,currentGeneration));
                assertEquals(true,field(a,"busy"));assertNull(field(a,"activeSubtitleBatch"));
                current.cancel();factory.readers.get(1).release();invoke(a,"setBusy",new Class<?>[]{boolean.class},false);assertNoCapture(a);
            });idle();scenario.onActivity(a->{assertEquals(false,field(a,"busy"));assertTrue(target.subtitles.isEmpty());assertNoCapture(a);});
        }
    }

    @Test(timeout=30_000) public void modelAvailabilityRefusalStartsNoReaderAndKeepsPlannerAndWords(){
        FakeFactory factory=new FakeFactory();factory.available=false;Take target=take("unavailable");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,factory,target);Object planner=field(a,"planner");click(a,ACTION);
                assertTrue(factory.readers.isEmpty());assertEquals(false,field(a,"busy"));assertSame(planner,field(a,"planner"));
                assertNull(field(a,"activeSubtitleBatch"));assertTrue(target.subtitles.isEmpty());assertNoCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void cancellingQueuedPreflightErrorDoesNotLeaveBusyWithoutAnyReader(){
        FakeFactory factory=new FakeFactory();Take invalid=take("too-long");invalid.durationMs=180_001;invalid.outMs=180_001;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                prepare(a,factory,invalid);click(a,ACTION);assertEquals(true,field(a,"busy"));
                SubtitleBatch batch=(SubtitleBatch)field(a,"activeSubtitleBatch");assertNotNull(batch);assertFalse(batch.isRunning());assertTrue(factory.readers.isEmpty());
                // Cancel before the core's main-posted preflight error can be delivered.
                click(a,"Cancel local processing");assertEquals(false,field(a,"busy"));assertNull(field(a,"activeSubtitleBatch"));
                assertTrue(invalid.subtitles.isEmpty());assertNoCapture(a);
            });idle();scenario.onActivity(a->{assertEquals(false,field(a,"busy"));assertNull(field(a,"activeSubtitleBatch"));assertTrue(factory.readers.isEmpty());assertNoCapture(a);});
        }
    }

    private static final class FakeFactory implements SubtitleBatch.ReaderFactory {
        boolean available=true;final List<FakeReader> readers=new ArrayList<>();
        public boolean isModelAvailable(){return available;}
        public SubtitleBatch.Reader create(){assertEquals(Looper.getMainLooper(),Looper.myLooper());FakeReader reader=new FakeReader();readers.add(reader);return reader;}
    }
    private static final class FakeReader implements SubtitleBatch.Reader {
        Uri source;ClipTranscriber.Listener listener;final List<Runnable> idleCallbacks=new ArrayList<>();
        public void transcribe(Uri source,ClipTranscriber.Listener listener){assertEquals(Looper.getMainLooper(),Looper.myLooper());this.source=source;this.listener=listener;}
        public void closeWhenIdle(Runnable idle){idleCallbacks.add(idle);}
        void success(String words){assertNotNull(listener);listener.onComplete(new ArrayList<>(Arrays.asList(new SubtitleCue(100,900,words))),25);}
        void release(){assertFalse("Cancellation/completion must first wait for reader idle",idleCallbacks.isEmpty());List<Runnable> pending=new ArrayList<>(idleCallbacks);idleCallbacks.clear();for(Runnable callback:pending)callback.run();}
    }
    private static Take take(String name){return new Take(Uri.parse("content://synthetic.subtitle/"+name),"synthetic",name,"Manual typography retained",2000);}
    private static void prepare(MainActivity a,FakeFactory factory,Take...values){takes(a).clear();takes(a).addAll(Arrays.asList(values));invoke(a,"render",new Class<?>[0]);set(a,"subtitleBatch",new SubtitleBatch(a,factory));assertNoCapture(a);}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private static void click(MainActivity a,String label){Button button=button((View)field(a,"root"),label);assertNotNull(label,button);assertTrue(button.isEnabled());button.performClick();}
    private static Button button(View view,String label){if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
    private static String status(MainActivity a){return ((TextView)field(a,"status")).getText().toString();}
    private static void assertNoCapture(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertDenied(a);}
    private static void assertDenied(Context context){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Object field(Object target,String name){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object target,String name,Object value){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object target,String name,Class<?>[] types,Object...args){try{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}catch(Exception e){throw new AssertionError(e);}}
}
