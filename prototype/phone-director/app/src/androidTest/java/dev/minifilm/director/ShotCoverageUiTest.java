package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unlocked synthetic metadata/dialog checks; not footage quality or hardware execution. */
@RunWith(AndroidJUnit4.class)
public final class ShotCoverageUiTest {
    private SharedPreferences preferences;
    private Map<String,?> original;
    private boolean changed;
    private final List<File> owned=new ArrayList<>();

    @Before public void preservePreferencesAndUseControlledLegacyPlan() throws Exception {
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT);
        assertTrue("Use only a fresh synthetic emulator",fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        Context context=context();KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("UI checks require an unlocked test device",keyguard!=null&&keyguard.isKeyguardLocked());assertDenied();
        assertEmpty(new File(context.getFilesDir(),"takes"));assertEmpty(new File(context.getFilesDir(),"export-journal"));
        assertFalse(new File(context.getFilesDir(),"director-model.gguf").exists());
        assertFalse(new File(context.getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context.getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        JSONArray shots=new JSONArray();
        String[] titles={"Hero pose","Movement","Detail"};
        for(int i=0;i<titles.length;i++)shots.put(new JSONObject().put("id","legacy-"+i).put("title",titles[i])
                .put("cue","Stand wearing your outfit.").put("caption","Outfit").put("duration",4000));
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false)
                .put("shots",shots).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreAllPreferencesAndRemoveOnlyOwnCacheFile(){
        for(File file:owned)if(file.exists())assertTrue(file.delete());
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
            else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)value;editor.putStringSet(key,new HashSet<>(strings));}
            else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }

    @Test(timeout=45_000) public void actualMultiChoiceMapsManyToManyAndSelectionRefreshesAssignedCountsWithoutChangingEdits() throws Exception {
        Take alpha=take("Alpha"),beta=take("Beta"),unselected=take("Unselected");unselected.selected=false;
        EditSnapshot preserved=new EditSnapshot(alpha);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,alpha,beta,unselected);assertEquals("legacy-0",shots(a).get(0).id);
                assertEquals("Original shotId alone never counts as review",0,ShotCoverage.evaluate(shots(a),takes(a)).coveredCount);open(a,alpha);});
            idle();scenario.onActivity(a->{choose(dialog(a),0);choose(dialog(a),1);dialog(a).getButton(AlertDialog.BUTTON_POSITIVE).performClick();});idle();
            scenario.onActivity(a->open(a,beta));idle();
            scenario.onActivity(a->{choose(dialog(a),0);choose(dialog(a),2);dialog(a).getButton(AlertDialog.BUTTON_POSITIVE).performClick();});idle();
            scenario.onActivity(a->{
                unselected.reviewedShotIds.add("legacy-1");invoke(a,"save");invoke(a,"render");
                ShotCoverage.Result result=ShotCoverage.evaluate(shots(a),takes(a));
                assertEquals(3,result.coveredCount);assertEquals(2,result.entries.get(0).reviewedSelectedTakeCount);
                assertEquals(1,result.entries.get(1).reviewedSelectedTakeCount);assertEquals(1,result.entries.get(2).reviewedSelectedTakeCount);
                assertTrue(((TextView)field(a,"coverageSummary")).getText().toString().contains("0 missing"));
                CheckBox selected=(CheckBox)find((View)field(a,"root"),CheckBox.class,"Alpha");assertNotNull(selected);selected.performClick();
                assertEquals(2,ShotCoverage.evaluate(shots(a),takes(a)).coveredCount);
                assertTrue(((TextView)field(a,"coverageSummary")).getText().toString().contains("1 missing"));
                assertEquals(Arrays.asList("legacy-0","legacy-1"),alpha.reviewedShotIds);
                assertEquals(Arrays.asList("legacy-0","legacy-2"),beta.reviewedShotIds);
                assertEquals(Arrays.asList(alpha,beta,unselected),takes(a));
                preserved.assertUnchangedExceptSelection(alpha);assertFalse(alpha.selected);assertFalse(unselected.selected);assertNoCapture(a);
            });
        }
    }

    @Test(timeout=45_000) public void cancelClearAndDismissedButtonsPreserveEditsAndOnlyConfirmedMappingInvalidatesReadySnapshot() throws Exception {
        Take alpha=take("Alpha");alpha.reviewedShotIds.add("legacy-0");List<String> originalMappings=alpha.reviewedShotIds;
        EditSnapshot preserved=new EditSnapshot(alpha);
        // A controlled cache pointer tests metadata snapshot invalidation, not actual ZIP creation.
        File pointer=File.createTempFile("coverage-ui-cache-pointer-",".zip",context().getCacheDir());owned.add(pointer);
        try(FileOutputStream out=new FileOutputStream(pointer)){out.write(new byte[]{1,2,3});}
        AtomicReference<Button> staleSave=new AtomicReference<>(),staleClear=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,alpha);set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));open(a,alpha);});idle();
            scenario.onActivity(a->{choose(dialog(a),2);dialog(a).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});idle();
            scenario.onActivity(a->{assertSame(originalMappings,alpha.reviewedShotIds);assertTrue(pointer.isFile());preserved.assertUnchanged(alpha);open(a,alpha);});idle();
            scenario.onActivity(a->{choose(dialog(a),1);staleSave.set(dialog(a).getButton(AlertDialog.BUTTON_POSITIVE));staleClear.set(dialog(a).getButton(AlertDialog.BUTTON_NEUTRAL));invoke(a,"render");
                staleSave.get().performClick();staleClear.get().performClick();assertSame(originalMappings,alpha.reviewedShotIds);assertTrue(pointer.isFile());open(a,alpha);});idle();
            scenario.onActivity(a->{staleSave.set(dialog(a).getButton(AlertDialog.BUTTON_POSITIVE));staleClear.set(dialog(a).getButton(AlertDialog.BUTTON_NEUTRAL));});
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{staleSave.get().performClick();staleClear.get().performClick();});
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{assertSame(originalMappings,alpha.reviewedShotIds);assertTrue(pointer.isFile());preserved.assertUnchanged(alpha);open(a,alpha);});idle();
            scenario.onActivity(a->{dialog(a).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();});idle();
            scenario.onActivity(a->{assertTrue(alpha.reviewedShotIds.isEmpty());assertNull(field(a,"pendingPack"));assertFalse(pointer.exists());
                preserved.assertUnchanged(alpha);assertNoCapture(a);});
        }
    }

    @Test(timeout=45_000) public void replacementNamespacesDoNotMatchHistoricalAssignmentsAndRecreationPreservesMetadata() throws Exception {
        Take alpha=take("Alpha");alpha.reviewedShotIds.add("legacy-0");EditSnapshot preserved=new EditSnapshot(alpha);
        List<String> newIds=new ArrayList<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,alpha);List<Shot> old=new ArrayList<>(shots(a));
                invoke(a,"replacePlan",new Class<?>[]{List.class,String.class},old,"Synthetic replacement test");
                for(int i=0;i<old.size();i++){Shot fresh=shots(a).get(i);assertNotEquals(old.get(i).id,fresh.id);assertEquals(old.get(i).title,fresh.title);newIds.add(fresh.id);}
                assertEquals(Arrays.asList("legacy-0"),alpha.reviewedShotIds);assertEquals(0,ShotCoverage.evaluate(shots(a),takes(a)).coveredCount);
                assertTrue(((String)invoke(a,"shotAssignmentDescription",new Class<?>[]{Take.class},alpha)).contains("earlier plan"));preserved.assertUnchanged(alpha);assertNoCapture(a);
            });
        }
        // Close first: a live Activity's onStop save must not overwrite this restore fixture.
        JSONObject stored=new JSONObject(preferences.getString("state","{}"));
        stored.getJSONArray("takes").getJSONObject(0).put("reviewedShotIds",new JSONArray()
                .put("legacy-0").put("legacy-0").put("unknown-previous-plan").put(17).put(JSONObject.NULL).put(" ").put("bad\ncontrol"));
        assertTrue(preferences.edit().putString("state",stored.toString()).commit());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                for(int i=0;i<newIds.size();i++)assertEquals(newIds.get(i),shots(a).get(i).id);
                assertEquals(Arrays.asList("legacy-0","unknown-previous-plan"),takes(a).get(0).reviewedShotIds);
                assertEquals(0,ShotCoverage.evaluate(shots(a),takes(a)).coveredCount);assertNoCapture(a);
            });
            scenario.recreate();scenario.onActivity(a->{
                for(int i=0;i<newIds.size();i++)assertEquals(newIds.get(i),shots(a).get(i).id);
                assertEquals(Arrays.asList("legacy-0","unknown-previous-plan"),takes(a).get(0).reviewedShotIds);
                assertEquals(0,ShotCoverage.evaluate(shots(a),takes(a)).coveredCount);assertNoCapture(a);
            });
        }
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false).toString()).commit());
        List<String> initial=new ArrayList<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{for(Shot shot:shots(a)){assertTrue(shot.id.startsWith("plan-"));initial.add(shot.id);}assertFalse(initial.isEmpty());assertNoCapture(a);});
            scenario.recreate();scenario.onActivity(a->{for(int i=0;i<initial.size();i++)assertEquals(initial.get(i),shots(a).get(i).id);assertNoCapture(a);});
        }
    }

    @Test(timeout=45_000) public void directNextMissingSelectsCurrentShotAndTabWithoutImplicitSessionOrCapture() {
        Take alpha=take("Alpha");alpha.reviewedShotIds.add("legacy-0");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,alpha);set(a,"guideSequence",true);
                Button action=(Button)find((View)field(a,"root"),Button.class,"Direct next missing shot");assertNotNull(action);assertTrue(action.isEnabled());action.performClick();
                assertEquals(1,field(a,"tab"));assertEquals(1,field(a,"shotIndex"));assertEquals("legacy-1",shots(a).get((Integer)field(a,"shotIndex")).id);
                assertEquals("Start camera",((Button)field(a,"captureAction")).getText().toString());
                assertTrue(((TextView)field(a,"status")).getText().toString().contains("tap Start camera"));
                assertEquals(Arrays.asList("legacy-0"),alpha.reviewedShotIds);assertNoCapture(a);
            });
        }
    }

    private static final class EditSnapshot {
        final Uri uri;final String id,title,caption,origin;final long duration,in,out;final List<SubtitleCue> subtitles;final boolean selected;
        EditSnapshot(Take take){uri=take.uri;id=take.shotId;title=take.title;caption=take.caption;origin=take.captionOrigin;duration=take.durationMs;in=take.inMs;out=take.outMs;subtitles=take.subtitles;selected=take.selected;}
        void assertUnchangedExceptSelection(Take take){assertEquals(uri,take.uri);assertEquals(id,take.shotId);assertEquals(title,take.title);assertEquals(caption,take.caption);assertEquals(origin,take.captionOrigin);
            assertEquals(duration,take.durationMs);assertEquals(in,take.inMs);assertEquals(out,take.outMs);assertSame(subtitles,take.subtitles);assertEquals("My reviewed words",take.subtitles.get(0).text);}
        void assertUnchanged(Take take){assertUnchangedExceptSelection(take);assertEquals(selected,take.selected);}
    }
    private static Take take(String title){File missing=new File(context().getCacheDir(),"coverage-ui-"+title+"-not-created.mp4");assertFalse(missing.exists());
        Take take=new Take(Uri.fromFile(missing),"legacy-0",title,"My typography",4000);take.inMs=100;take.outMs=3500;
        take.subtitles.add(new SubtitleCue(200,1200,"My reviewed words"));take.captionOrigin="creator-reviewed-offline-asr";return take;}
    private static void prepare(MainActivity a,Take...takes){takes(a).clear();takes(a).addAll(Arrays.asList(takes));invoke(a,"save");invoke(a,"render");assertNoCapture(a);}
    private static void open(MainActivity a,Take take){invoke(a,"reviewShotAssignments",new Class<?>[]{Take.class},take);}
    private static AlertDialog dialog(MainActivity a){AlertDialog dialog=(AlertDialog)field(a,"shotAssignmentDialog");assertNotNull(dialog);assertTrue(dialog.isShowing());return dialog;}
    private static void choose(AlertDialog dialog,int index){ListView list=dialog.getListView();assertNotNull(list);assertNotNull("Visible short plan row",list.getChildAt(index));list.performItemClick(list.getChildAt(index),index,list.getAdapter().getItemId(index));}
    private static View find(View view,Class<?> type,String text){if(type.isInstance(view)&&view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),type,text);if(found!=null)return found;}}return null;}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    @SuppressWarnings("unchecked") private static List<Shot> shots(MainActivity a){return (List<Shot>)field(a,"shots");}
    private static Object field(Object object,String name){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object object,String name,Object value){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object object,String name){return invoke(object,name,new Class<?>[0]);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object...args){try{Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(object,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoCapture(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));
        assertEquals(false,field(a,"sequenceActive"));assertEquals(0L,field(field(a,"planner"),"handle"));assertEquals(0L,field(field(a,"transcriber"),"activeRequest"));
        SpeechCoach speech=(SpeechCoach)field(a,"speech");assertFalse(speech.isListening());assertFalse(speech.hasSpeechWork());assertDenied();}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void assertEmpty(File directory){File[] files=directory.listFiles();assertTrue("Recovery inputs must be empty",!directory.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
