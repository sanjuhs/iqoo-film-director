package dev.minifilm.director;

import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Synthetic worker/dialog ownership only. No actual source reads, camera, audio, models or quality claim. */
@RunWith(AndroidJUnit4.class)
public final class TakeFramingUiTest {
    private SharedPreferences preferences;
    private Map<String,?> original;
    private boolean changed;
    private final List<FakeInspector> inspectors=new ArrayList<>();

    @Before public void requireFreshUnlockedDeniedEmulatorAndPreservePreferences() throws Exception {
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT);
        assertTrue("Use a fresh synthetic emulator",fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("UI test requires normal unlock",keyguard!=null&&keyguard.isKeyguardLocked());assertDenied();
        assertEmpty(new File(context().getFilesDir(),"takes"));assertEmpty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());
        assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false)
                .put("takes",new JSONArray()).toString()).commit());
    }
    @After public void releaseFakeWorkersAndRestoreAllPreferences(){
        for(FakeInspector inspector:inspectors)inspector.release.countDown();
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
            else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)value;editor.putStringSet(key,new HashSet<>(strings));}
            else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }

    @Test(timeout=45_000) public void actualButtonShowsThreeEphemeralObservationsAndDoneRecyclesWithoutEditingTake() throws Exception {
        FakeInspector fake=fake(false);Take take=take();EditSnapshot originalEdit=new EditSnapshot(take);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take,fake);clickReview(a);});
            await(fake.started);awaitIdleReview(scenario);
            scenario.onActivity(a->{AlertDialog dialog=dialog(a);assertTrue(dialog.isShowing());
                assertTrue(hasText(dialog.getWindow().getDecorView(),"Three nearby frames with an approximate vertical crop"));
                assertTrue(hasText(dialog.getWindow().getDecorView(),"Local pose check · sample only"));
                assertTrue(hasText(dialog.getWindow().getDecorView(),"Framing needs your review"));
                assertFalse(hasText(dialog.getWindow().getDecorView(),"Landmark evidence:"));
                assertEquals(3,fake.result.moments.size());assertTrue(fake.selection.matches(take));
                for(TakeFramingReview.Moment moment:fake.result.moments)assertTrue(hasDescription(dialog.getWindow().getDecorView(),
                        "Approximate vertical frame near source time "+SubtitleTime.format(moment.requestedTimeMs)+" seconds"));
                originalEdit.assertUnchanged(take);assertNoCaptureOrSpeech(a);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });idle();scenario.onActivity(a->{assertNull(field(a,"takeFramingDialog"));assertRecycled(fake);originalEdit.assertUnchanged(take);});
        }
    }

    @Test(timeout=45_000) public void explicitCancelSuppressesLateFramesAndDoesNotClearNewerBusyOwner() throws Exception {
        FakeInspector fake=fake(true);Take take=take();EditSnapshot originalEdit=new EditSnapshot(take);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take,fake);clickReview(a);});await(fake.started);
            scenario.onActivity(a->{assertEquals(true,field(a,"busy"));Button cancel=(Button)find((View)field(a,"root"),Button.class,"Cancel local processing");
                assertNotNull(cancel);cancel.performClick();assertEquals(false,field(a,"busy"));assertNull(field(a,"activeTakeFramingReview"));
                // A later unrelated operation owns this gate; old review completion may not clear it.
                invoke(a,"setBusy",new Class<?>[]{boolean.class},true);
            });fake.release.countDown();awaitIdleReview(scenario);
            scenario.onActivity(a->{assertEquals(true,field(a,"busy"));assertNull(field(a,"takeFramingDialog"));assertRecycled(fake);
                originalEdit.assertUnchanged(take);assertNoCaptureOrSpeech(a);invoke(a,"setBusy",new Class<?>[]{boolean.class},false);});
        }finally{fake.release.countDown();}
    }

    @Test(timeout=45_000) public void backgroundCancelsWithoutAutoResumeAndDismissesOwnedReviewImages() throws Exception {
        FakeInspector fake=fake(true);Take take=take();EditSnapshot originalEdit=new EditSnapshot(take);
        AtomicReference<TakeFramingReview> backgroundReviewer=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take,fake);backgroundReviewer.set((TakeFramingReview)field(a,"takeFramingReview"));clickReview(a);});await(fake.started);
            scenario.moveToState(Lifecycle.State.CREATED);fake.release.countDown();
            assertEquals(Lifecycle.State.CREATED,scenario.getState());
            // Poll only the thread-safe worker owner while actually stopped. Do not route a
            // background wait through ActivityScenario's UI-action/facilitator machinery.
            awaitReviewerIdle(backgroundReviewer.get());scenario.moveToState(Lifecycle.State.RESUMED);idle();
            scenario.onActivity(a->{assertEquals(1,fake.calls.get());assertNull(field(a,"takeFramingDialog"));assertEquals(false,field(a,"busy"));
                assertRecycled(fake);originalEdit.assertUnchanged(take);assertNoCaptureOrSpeech(a);});
            FakeInspector immediate=fake(false);
            scenario.onActivity(a->{((TakeFramingReview)field(a,"takeFramingReview")).close();set(a,"takeFramingReview",new TakeFramingReview(a,immediate));clickReview(a);});await(immediate.started);awaitIdleReview(scenario);
            scenario.onActivity(a->assertTrue(dialog(a).isShowing()));scenario.moveToState(Lifecycle.State.CREATED);
            assertEquals(Lifecycle.State.CREATED,scenario.getState());
            assertRecycled(immediate);scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{assertNull(field(a,"takeFramingDialog"));assertEquals(1,immediate.calls.get());originalEdit.assertUnchanged(take);assertNoCaptureOrSpeech(a);});
        }finally{fake.release.countDown();}
    }

    @Test(timeout=45_000) public void changedTrimOrSourceIdentityRejectsResultAndClosesItsImages() throws Exception {
        for(int changedField=0;changedField<3;changedField++){
            final int change=changedField;
            FakeInspector fake=fake(true);Take take=take();EditSnapshot originalEdit=new EditSnapshot(take);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                scenario.onActivity(a->{prepare(a,take,fake);clickReview(a);});await(fake.started);
                scenario.onActivity(a->{if(change==1)take.uri=Uri.parse("content://synthetic/replaced-source");else if(change==0)take.outMs=3000;
                    else{takes(a).clear();takes(a).add(take());}});
                fake.release.countDown();awaitIdleReview(scenario);
                scenario.onActivity(a->{assertNull(field(a,"takeFramingDialog"));assertEquals(false,field(a,"busy"));assertRecycled(fake);
                    assertTrue(((TextView)field(a,"status")).getText().toString().contains("not applied"));
                    assertEquals(originalEdit.title,take.title);assertEquals(originalEdit.caption,take.caption);
                    assertSame(originalEdit.subtitles,take.subtitles);assertSame(originalEdit.mapping,take.reviewedShotIds);
                    assertEquals(originalEdit.selected,take.selected);assertEquals(originalEdit.duration,take.durationMs);assertNoCaptureOrSpeech(a);
                    if(change==1)assertEquals(Uri.parse("content://synthetic/replaced-source"),take.uri);else if(change==0)assertEquals(3000,take.outMs);
                    else{assertFalse(takes(a).contains(take));originalEdit.assertUnchanged(take);}
                });
            }finally{fake.release.countDown();}
        }
    }

    @Test(timeout=45_000) public void malformedRemoteAndTooLongSourceFailBeforeWorkerOrBusy() {
        FakeInspector fake=fake(false);Take take=take();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take,fake);
                take.uri=null;clickReview(a);assertEquals(false,field(a,"busy"));
                take.uri=Uri.parse("https://example.invalid/video.mp4");clickReview(a);assertEquals(false,field(a,"busy"));
                take.uri=Uri.parse("content://synthetic/selected-video");take.durationMs=180001;clickReview(a);assertEquals(false,field(a,"busy"));
                take.durationMs=4000;take.inMs=100;take.outMs=349;clickReview(a);assertEquals(false,field(a,"busy"));
                assertEquals(0,fake.calls.get());assertNull(field(a,"takeFramingDialog"));assertNoCaptureOrSpeech(a);
            });
        }
    }

    private FakeInspector fake(boolean blocked){FakeInspector inspector=new FakeInspector(blocked);inspectors.add(inspector);return inspector;}
    private static final class FakeInspector implements TakeFramingReview.Inspector {
        final CountDownLatch started=new CountDownLatch(1),release;final AtomicInteger calls=new AtomicInteger();
        volatile TakeFramingReview.Selection selection;volatile TakeFramingReview.Result result;
        FakeInspector(boolean blocked){release=new CountDownLatch(blocked?1:0);}
        public TakeFramingReview.Result inspect(TakeFramingReview.Selection selected,BooleanSupplier cancelled)throws Exception{
            calls.incrementAndGet();selection=selected;started.countDown();
            if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic inspector was not released");
            List<TakeFramingReview.Moment> moments=new ArrayList<>();long range=selected.outMs-selected.inMs;
            for(int i=1;i<=3;i++)moments.add(new TakeFramingReview.Moment(selected.inMs+range*i/4,
                    "review needed","Synthetic landmark evidence only",Bitmap.createBitmap(144,256,Bitmap.Config.ARGB_8888)));
            result=new TakeFramingReview.Result(selected,moments);return result;
        }
    }
    private static final class EditSnapshot {
        final Uri uri;final String id,title,caption,origin;final long duration,in,out;final boolean selected;
        final List<SubtitleCue> subtitles;final List<String> mapping;
        EditSnapshot(Take t){uri=t.uri;id=t.shotId;title=t.title;caption=t.caption;origin=t.captionOrigin;duration=t.durationMs;in=t.inMs;out=t.outMs;selected=t.selected;subtitles=t.subtitles;mapping=t.reviewedShotIds;}
        void assertUnchanged(Take t){assertEquals(uri,t.uri);assertEquals(id,t.shotId);assertEquals(title,t.title);assertEquals(caption,t.caption);assertEquals(origin,t.captionOrigin);
            assertEquals(duration,t.durationMs);assertEquals(in,t.inMs);assertEquals(out,t.outMs);assertEquals(selected,t.selected);assertSame(subtitles,t.subtitles);assertSame(mapping,t.reviewedShotIds);}
    }
    private static Take take(){File missing=new File(context().getCacheDir(),"take-framing-ui-source-not-created.mp4");assertFalse(missing.exists());
        Take t=new Take(Uri.fromFile(missing),"synthetic-shot","Synthetic take","Reviewed typography",4000);t.inMs=100;t.outMs=3500;
        t.subtitles.add(new SubtitleCue(200,700,"Synthetic reviewed words"));t.reviewedShotIds.add("creator-mapping");t.captionOrigin="creator-reviewed-offline-asr";return t;}
    private static void prepare(MainActivity a,Take take,FakeInspector fake){takes(a).clear();takes(a).add(take);invoke(a,"save");invoke(a,"render");TakeFramingReview previous=(TakeFramingReview)field(a,"takeFramingReview");if(previous!=null)previous.close();set(a,"takeFramingReview",new TakeFramingReview(a,fake));assertNoCaptureOrSpeech(a);}
    private static void clickReview(MainActivity a){Button button=(Button)find((View)field(a,"root"),Button.class,"Review cut framing");assertNotNull(button);button.performClick();}
    private static AlertDialog dialog(MainActivity a){AlertDialog dialog=(AlertDialog)field(a,"takeFramingDialog");assertNotNull(dialog);return dialog;}
    private static void assertRecycled(FakeInspector fake){assertNotNull(fake.result);for(TakeFramingReview.Moment moment:fake.result.moments)assertTrue(moment.thumbnail.isRecycled());}
    private static void await(CountDownLatch latch)throws Exception{assertTrue("Synthetic worker did not start",latch.await(10,TimeUnit.SECONDS));}
    private static void awaitIdleReview(ActivityScenario<MainActivity> scenario)throws Exception{
        long deadline=android.os.SystemClock.elapsedRealtime()+10_000;boolean[] idle={false};
        while(android.os.SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->idle[0]=!((TakeFramingReview)field(a,"takeFramingReview")).isRunning()&&field(a,"activeTakeFramingReview")==null);
            if(idle[0]){idle();return;}Thread.sleep(20);}fail("Synthetic reviewer did not become idle");
    }
    private static void awaitReviewerIdle(TakeFramingReview reviewer)throws Exception{
        assertNotNull(reviewer);long deadline=android.os.SystemClock.elapsedRealtime()+10_000;
        while(reviewer.isRunning()&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
        assertFalse("Stopped synthetic reviewer did not release ownership",reviewer.isRunning());idle();
    }
    private static View find(View view,Class<?> type,String text){if(type.isInstance(view)&&view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),type,text);if(found!=null)return found;}}return null;}
    private static boolean hasText(View view,String fragment){if(view instanceof TextView&&((TextView)view).getText().toString().contains(fragment))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),fragment))return true;}return false;}
    private static boolean hasDescription(View view,String description){if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasDescription(group.getChildAt(i),description))return true;}return false;}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object object,String name,Object value){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object object,String name){return invoke(object,name,new Class<?>[0]);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object...args){try{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoCaptureOrSpeech(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));
        assertEquals(false,field(a,"sequenceActive"));assertEquals(0L,field(field(a,"planner"),"handle"));assertEquals(0L,field(field(a,"transcriber"),"activeRequest"));
        SpeechCoach speech=(SpeechCoach)field(a,"speech");assertFalse(speech.isListening());assertFalse(speech.hasSpeechWork());assertDenied();}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void assertEmpty(File directory){File[] files=directory.listFiles();assertTrue("Synthetic recovery directories must be empty",!directory.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
