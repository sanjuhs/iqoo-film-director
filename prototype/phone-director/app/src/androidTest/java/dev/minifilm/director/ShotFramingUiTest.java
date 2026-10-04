package dev.minifilm.director;

import static org.junit.Assert.*;
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
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Explicit synthetic creator settings and ownership, not live framing accuracy or creator benefit. */
@RunWith(AndroidJUnit4.class)
public final class ShotFramingUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    private final List<File> owned=new ArrayList<>();private final List<FakeInspector> inspectors=new ArrayList<>();
    @Before public void requireOwnUnlockedEmulatorAndPreservePreferences() throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.ROOT);
        assertTrue("Use a fresh synthetic emulator",fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());assertDenied();
        assertEmpty(new File(context().getFilesDir(),"takes"));assertEmpty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());
        assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        JSONArray plan=new JSONArray().put(new JSONObject().put("id","synthetic-framing-hero").put("title","Hero pose")
                .put("cue","Stand wearing your outfit.").put("caption","My outfit").put("duration",4000));
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",0).put("voice",false)
                .put("shots",plan).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void releaseOnlyOwnedFixturesAndRestoreAllPreferences() throws Exception{
        for(FakeInspector inspector:inspectors)inspector.release.countDown();
        for(FakeInspector inspector:inspectors)if(inspector.started.getCount()==0)assertTrue("Owned synthetic worker must finish",inspector.finished.await(5,TimeUnit.SECONDS));
        idle();
        for(File file:owned)if(file.exists())assertTrue(file.delete());
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();
            if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);
            else if(v instanceof Float)editor.putFloat(key,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}
            else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }

    @Test(timeout=45_000) public void actualEditorSavesExplicitChoiceCancelKeepsItAndOtherEditsRetainItAfterRecreation() throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{assertEquals(FramingTarget.SCENE_DEFAULT,shots(a).get(0).framingTarget);open(a);});idle();
            scenario.onActivity(a->{Spinner target=target(dialog(a));assertEquals(5,target.getCount());assertEquals("Scene default (suggested)",target.getSelectedItem());
                target.setSelection(2);edit(dialog(a),"Title").setText("My talking pose");dialog(a).getButton(-1).performClick();});idle();
            scenario.onActivity(a->{assertEquals(FramingTarget.FACE_SHOULDERS,shots(a).get(0).framingTarget);assertEquals("My talking pose",shots(a).get(0).title);
                assertTrue(hasText((View)field(a,"root"),"Face & shoulders"));open(a);});idle();
            scenario.onActivity(a->{assertEquals(2,target(dialog(a)).getSelectedItemPosition());target(dialog(a)).setSelection(1);dialog(a).getButton(-2).performClick();});idle();
            scenario.onActivity(a->{assertEquals(FramingTarget.FACE_SHOULDERS,shots(a).get(0).framingTarget);open(a);});idle();
            scenario.onActivity(a->{edit(dialog(a),"Direction").setText("Look toward the lens and smile.");dialog(a).getButton(-1).performClick();});idle();
            scenario.onActivity(a->{assertEquals(FramingTarget.FACE_SHOULDERS,shots(a).get(0).framingTarget);assertNoMediaWork(a);});
            scenario.recreate();scenario.onActivity(a->{assertEquals(FramingTarget.FACE_SHOULDERS,shots(a).get(0).framingTarget);
                assertEquals("Look toward the lens and smile.",shots(a).get(0).instruction);assertNoMediaWork(a);});
        }
        JSONObject saved=new JSONObject(preferences.getString("state","{}"));assertEquals(FramingTarget.FACE_SHOULDERS,saved.getJSONArray("shots").getJSONObject(0).getString("framingTarget"));
    }

    @Test(timeout=45_000) public void retainedSaveAfterRenderBackgroundAndRemovedShotCannotApplyTargetOrText(){
        AtomicReference<Button> stale=new AtomicReference<>();AtomicReference<Shot> originalShot=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{originalShot.set(shots(a).get(0));open(a);});idle();
            scenario.onActivity(a->{target(dialog(a)).setSelection(1);stale.set(dialog(a).getButton(-1));invoke(a,"render");stale.get().performClick();
                assertSame(originalShot.get(),shots(a).get(0));assertEquals(FramingTarget.SCENE_DEFAULT,originalShot.get().framingTarget);open(a);});idle();
            scenario.onActivity(a->{target(dialog(a)).setSelection(2);stale.set(dialog(a).getButton(-1));});
            scenario.moveToState(Lifecycle.State.CREATED);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->stale.get().performClick());
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{assertSame(originalShot.get(),shots(a).get(0));open(a);});idle();
            scenario.onActivity(a->{target(dialog(a)).setSelection(4);stale.set(dialog(a).getButton(-1));Shot replacement=new Shot("new-exact-identity","Replacement","Hold still.","",4000);
                shots(a).set(0,replacement);stale.get().performClick();assertSame(replacement,shots(a).get(0));assertEquals(FramingTarget.SCENE_DEFAULT,replacement.framingTarget);
                assertEquals(FramingTarget.SCENE_DEFAULT,originalShot.get().framingTarget);invoke(a,"render");assertNull(field(a,"shotEditDialog"));assertNoMediaWork(a);});
        }
    }

    @Test(timeout=45_000) public void explicitPersonTargetsOverrideProductSceneAndManualOrObjectTargetsDisableAdviceWithoutCapture(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{Shot shot=shots(a).get(0);set(a,"style","Product reveal");set(a,"tab",1);
                for(String value:new String[]{FramingTarget.FULL_OUTFIT,FramingTarget.FACE_SHOULDERS}){
                    shot.framingTarget=value;invoke(a,"render");assertEquals(true,invoke(a,"isPoseShot"));
                    assertTrue(hasText((View)field(a,"root"),"Framing target · "+FramingTarget.label(value)));
                }
                for(String value:new String[]{FramingTarget.OBJECT_DETAIL,FramingTarget.MANUAL,FramingTarget.SCENE_DEFAULT}){
                    shot.framingTarget=value;invoke(a,"render");assertEquals(false,invoke(a,"isPoseShot"));
                    if(FramingTarget.MANUAL.equals(value))assertTrue(hasText((View)field(a,"root"),"off for this target"));
                }
                shot.framingTarget=FramingTarget.FACE_SHOULDERS;invoke(a,"render");invoke(a,"startCamera");
                PoseCoach coach=(PoseCoach)field(a,"pose");assertNotNull(coach);assertEquals(FramingTarget.FACE_SHOULDERS,coach.configurationSnapshot().target);
                assertEquals("Product reveal".toLowerCase(Locale.ROOT),coach.configurationSnapshot().mode);
                assertEquals(false,field(coach,"enabled"));CaptureController capture=(CaptureController)field(a,"capture");
                assertNotNull(capture);assertFalse(capture.isRecording());assertFalse(capture.isPreviewPending());assertNull(field(capture,"provider"));
                assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertDenied();
            });
        }
    }

    @Test(timeout=45_000) public void confirmedTargetInvalidatesReadyPackageAndSavedReviewUsesOnlyExactCurrentAssociations() throws Exception{
        File pointer=File.createTempFile("shot-framing-ui-pointer-",".zip",context().getCacheDir());owned.add(pointer);
        try(FileOutputStream out=new FileOutputStream(pointer)){out.write(new byte[]{1,2,3});}
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));open(a);});idle();
            scenario.onActivity(a->{target(dialog(a)).setSelection(1);dialog(a).getButton(-1).performClick();});idle();
            scenario.onActivity(a->{assertNull(field(a,"pendingPack"));assertFalse(pointer.exists());Take take=take();
                Object resolved=resolve(a,take);assertEquals(FramingTarget.FULL_OUTFIT,field(resolved,"target"));assertTrue(((String)field(resolved,"disclosure")).contains("Current plan target"));
                assertTrue(((String)field(resolved,"disclosure")).contains("exact capture-ID match"));
                take.shotId="earlier-plan";take.reviewedShotIds.clear();resolved=resolve(a,take);assertEquals(FramingTarget.MANUAL,field(resolved,"target"));
                take.reviewedShotIds.add(shots(a).get(0).id);resolved=resolve(a,take);assertEquals(FramingTarget.FULL_OUTFIT,field(resolved,"target"));
                assertTrue(((String)field(resolved,"disclosure")).contains("creator-reviewed assignment"));
                Shot second=new Shot("second-synthetic-shot","Side pose","Turn.","",4000,FramingTarget.FACE_SHOULDERS);shots(a).add(second);
                take.reviewedShotIds.add(second.id);resolved=resolve(a,take);assertEquals(FramingTarget.MANUAL,field(resolved,"target"));
                assertTrue(((String)field(resolved,"disclosure")).contains("multiple shots"));
                take.reviewedShotIds.clear();take.shotId=shots(a).get(0).id;shots(a).get(0).framingTarget=FramingTarget.SCENE_DEFAULT;
                resolved=resolve(a,take);assertEquals(FramingTarget.MANUAL,field(resolved,"target"));assertTrue(((String)field(resolved,"disclosure")).contains("unspecified for saved footage"));
                take.reviewedShotIds.add("unknown\nreviewed:association");JSONObject binding;try{binding=new JSONObject((String)field(resolve(a,take),"binding"));}catch(Exception error){throw new AssertionError(error);}
                assertEquals("unknown\nreviewed:association",binding.optJSONArray("reviewedShotIds").optString(0));assertNoMediaWork(a);
            });
        }
    }

    @Test(timeout=45_000) public void changedReviewTargetRejectsLateFramesAndCurrentTargetAddsQualifiedAdviceWithoutEditingTake() throws Exception{
        FakeInspector blocked=new FakeInspector(true);inspectors.add(blocked);Take take=take();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{shots(a).get(0).framingTarget=FramingTarget.FULL_OUTFIT;prepareReview(a,take,blocked);});clickReview(scenario);await(blocked.started);
            scenario.onActivity(a->shots(a).get(0).framingTarget=FramingTarget.FACE_SHOULDERS);blocked.release.countDown();awaitIdle(scenario);
            scenario.onActivity(a->{assertNull(field(a,"takeFramingDialog"));assertRecycled(blocked);assertEquals(250L,take.inMs);assertEquals(3750L,take.outMs);
                assertEquals("Creator caption",take.caption);assertEquals(Arrays.asList("synthetic-framing-hero"),take.reviewedShotIds);});
            FakeInspector current=new FakeInspector(false);inspectors.add(current);
            scenario.onActivity(a->{((TakeFramingReview)field(a,"takeFramingReview")).close();set(a,"takeFramingReview",new TakeFramingReview(a,current));});clickReview(scenario);await(current.started);awaitIdle(scenario);
            scenario.onActivity(a->{AlertDialog review=(AlertDialog)field(a,"takeFramingDialog");assertNotNull(review);View decor=review.getWindow().getDecorView();
                assertTrue(hasText(decor,"Current plan target · Face & shoulders · exact capture-ID match"));
                assertTrue(hasText(decor,"Face & shoulders requested."));assertTrue(hasText(decor,"Framing needs your review"));
                assertTrue(hasText(decor,"Some body points could not be confirmed"));assertEquals("Creator caption",take.caption);
                assertEquals(250L,take.inMs);assertEquals(3750L,take.outMs);assertNoMediaWork(a);review.getButton(-1).performClick();});idle();assertRecycled(current);
        }finally{blocked.release.countDown();}
    }

    private static final class FakeInspector implements TakeFramingReview.Inspector{
        final CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1),release;volatile TakeFramingReview.Result result;
        FakeInspector(boolean blocked){release=new CountDownLatch(blocked?1:0);}
        public TakeFramingReview.Result inspect(TakeFramingReview.Selection selection,java.util.function.BooleanSupplier cancelled)throws Exception{
            started.countDown();try{while(!release.await(20,TimeUnit.MILLISECONDS)){if(cancelled.getAsBoolean())throw new InterruptedException("Synthetic canceled");}
            long length=selection.outMs-selection.inMs;List<TakeFramingReview.Moment> moments=new ArrayList<>();
            long[] times={selection.inMs+length/4,selection.inMs+length/2,selection.inMs+3*length/4};
            String[] labels={"head-and-shoulders","full-body","review needed"};
            for(int i=0;i<3;i++)moments.add(new TakeFramingReview.Moment(times[i],labels[i],"upper=2/3 hips=0/2 lower=0/4; synthetic partial evidence",Bitmap.createBitmap(16,24,Bitmap.Config.ARGB_8888)));
            return result=new TakeFramingReview.Result(selection,moments);
            }finally{finished.countDown();}
        }
    }
    private static void prepareReview(MainActivity a,Take take,FakeInspector fake){takes(a).clear();takes(a).add(take);set(a,"tab",2);set(a,"voice",false);invoke(a,"render");set(a,"takeFramingReview",new TakeFramingReview(a,fake));}
    private static Take take(){Take take=new Take(Uri.parse("content://synthetic.invalid/framing/clip"),"synthetic-framing-hero","Synthetic take","Creator caption",4000);take.inMs=250;take.outMs=3750;take.reviewedShotIds.add("synthetic-framing-hero");return take;}
    private static Object resolve(MainActivity a,Take take){return invoke(a,"resolveTakeFramingTarget",new Class<?>[]{Take.class},take);}
    private static void clickReview(ActivityScenario<MainActivity> scenario){scenario.onActivity(a->{Button button=(Button)find((View)field(a,"root"),Button.class,"Edit & review take");assertNotNull(button);button.performClick();});idle();
        scenario.onActivity(a->{AlertDialog menu=(AlertDialog)field(a,"takeToolsDialog");assertNotNull(menu);ScrollView scroll=(ScrollView)find(menu.getWindow().getDecorView(),ScrollView.class,null);assertNotNull(scroll);scroll.fullScroll(View.FOCUS_DOWN);});idle();
        scenario.onActivity(a->{AlertDialog menu=(AlertDialog)field(a,"takeToolsDialog");Button button=(Button)find(menu.getWindow().getDecorView(),Button.class,"Review cut framing");assertNotNull(button);button.performClick();assertNull(field(a,"takeToolsDialog"));});}
    private static void open(MainActivity a){Button button=(Button)find((View)field(a,"root"),Button.class,"Edit shot");assertNotNull(button);button.performClick();}
    private static AlertDialog dialog(MainActivity a){AlertDialog dialog=(AlertDialog)field(a,"shotEditDialog");assertNotNull(dialog);assertTrue(dialog.isShowing());return dialog;}
    private static Spinner target(AlertDialog d){Spinner spinner=(Spinner)find(d.getWindow().getDecorView(),Spinner.class,null);assertNotNull(spinner);return spinner;}
    private static EditText edit(AlertDialog d,String hint){return (EditText)findHint(d.getWindow().getDecorView(),hint);}
    private static View findHint(View v,String hint){if(v instanceof EditText&&hint.contentEquals(((EditText)v).getHint()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=findHint(((ViewGroup)v).getChildAt(i),hint);if(f!=null)return f;}return null;}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),type,text);if(f!=null)return f;}return null;}
    private static boolean hasText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return true;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),text))return true;return false;}
    @SuppressWarnings("unchecked") private static List<Shot> shots(MainActivity a){return (List<Shot>)field(a,"shots");}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){return invoke(o,name,new Class<?>[0]);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object...args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMediaWork(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));
        assertEquals(false,field(a,"sequenceActive"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertRecycled(FakeInspector fake){assertNotNull(fake.result);for(TakeFramingReview.Moment moment:fake.result.moments)assertTrue(moment.thumbnail.isRecycled());}
    private static void await(CountDownLatch latch)throws Exception{assertTrue(latch.await(10,TimeUnit.SECONDS));}
    private static void awaitIdle(ActivityScenario<MainActivity> scenario)throws Exception{long until=android.os.SystemClock.elapsedRealtime()+10_000;boolean[] idle={false};
        while(android.os.SystemClock.elapsedRealtime()<until){scenario.onActivity(a->idle[0]=field(a,"activeTakeFramingReview")==null&&!((TakeFramingReview)field(a,"takeFramingReview")).isRunning());if(idle[0]){idle();return;}Thread.sleep(20);}fail("Synthetic review did not finish");}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
