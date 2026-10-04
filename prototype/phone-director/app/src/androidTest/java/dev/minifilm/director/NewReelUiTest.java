package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Metadata-only daily-reel reset checks. No selected source or previous output is opened. */
@RunWith(AndroidJUnit4.class)
public final class NewReelUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    private final List<File> owned=new ArrayList<>();
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
    @After public void restoreEntirePreferencesAndRemoveOnlyOwnPointers(){
        for(File f:owned)if(f.exists())assertTrue(f.delete());
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }


    @Test(timeout=60_000) public void keepCurrentReelPreservesPreferencesReadyCacheAndInstantRetainedConfirm() throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->prepare(a,true));idle();File pointer=pointer();
            scenario.onActivity(a->{set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));invoke(a,"save");});openNewReel(scenario);
            scenario.onActivity(a->{String state=preferences.getString("state","");String snapshot=(String)invoke(a,"packSnapshot");AlertDialog current=dialog(a);Button retained=current.getButton(-1);
                assertTrue(hasText(current.getWindow().getDecorView(),"original clips"));assertTrue(hasText(current.getWindow().getDecorView(),"Review the brief and plan"));
                current.getButton(-2).performClick();assertNull("Keep relinquishes ownership in this event",field(a,"newReelDialog"));retained.performClick();
                assertEquals(snapshot,invoke(a,"packSnapshot"));assertEquals(state,preferences.getString("state",""));assertSame(pointer,field(a,"pendingPack"));assertTrue(pointer.exists());assertEquals(2,field(a,"tab"));assertEquals("Cancel keeps the previous last-shot position",1,field(a,"shotIndex"));assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void confirmedNewReelDeselectsOnlyClearsTitlePreservesEditsAndOutputsAcrossRecreation() throws Exception{
        AtomicReference<List<TakeFacts>> facts=new AtomicReference<>();AtomicReference<List<Shot>> plan=new AtomicReference<>();AtomicReference<String> shotsJson=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,true);
                // Starting another reel does not validate or rewrite an older repairable timing draft.
                takes(a).get(0).subtitles.get(0).startMs=-25;
                List<TakeFacts> snapshots=new ArrayList<>();for(Take t:takes(a))snapshots.add(new TakeFacts(t));facts.set(snapshots);plan.set(new ArrayList<>(shots(a)));shotsJson.set(invoke(a,"serializeShots").toString());});idle();File pointer=pointer();
            scenario.onActivity(a->{set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));invoke(a,"save");});openNewReel(scenario);
            scenario.onActivity(a->dialog(a).getButton(-1).performClick());idle();
            scenario.onActivity(a->{assertNull(field(a,"newReelDialog"));assertEquals(1,field(a,"tab"));assertEquals("The next reel begins at the first retained plan shot",0,field(a,"shotIndex"));assertEquals("",field(a,"reelTitle"));assertEquals(3,takes(a).size());
                for(int i=0;i<facts.get().size();i++){assertSame(facts.get().get(i).identity,takes(a).get(i));facts.get().get(i).check(takes(a).get(i),true);}
                for(int i=0;i<plan.get().size();i++)assertSame(plan.get().get(i),shots(a).get(i));assertEquals(shotsJson.get(),invoke(a,"serializeShots").toString());
                assertKeptContext(a);assertNull(field(a,"pendingPack"));assertFalse(pointer.exists());assertTrue(((TextView)field(a,"status")).getText().toString().contains("Review the existing brief and shot plan"));assertNoMedia(a);
                try{JSONObject saved=new JSONObject(preferences.getString("state",""));assertEquals(1,saved.getInt("tab"));assertEquals(0,saved.getInt("shot"));assertEquals("",saved.getString("reelTitle"));for(int i=0;i<saved.getJSONArray("takes").length();i++)assertFalse(saved.getJSONArray("takes").getJSONObject(i).getBoolean("selected"));}catch(Exception e){throw new AssertionError(e);}
            });scenario.recreate();idle();
            scenario.onActivity(a->{assertEquals(1,field(a,"tab"));assertEquals("The persisted first-shot position survives recreation",0,field(a,"shotIndex"));assertEquals("",field(a,"reelTitle"));assertEquals(3,takes(a).size());for(int i=0;i<facts.get().size();i++)facts.get().get(i).check(takes(a).get(i),false);
                assertEquals(shotsJson.get(),invoke(a,"serializeShots").toString());assertKeptContext(a);assertNoMedia(a);});
            scenario.onActivity(a->clickVisible((Button)find((View)field(a,"nav"),Button.class,"Camera")));idle();
            scenario.onActivity(a->{assertEquals(1,field(a,"tab"));assertEquals(0,field(a,"shotIndex"));assertTrue("Direct displays the first retained plan title",hasText((View)field(a,"root"),"Hero pose  ·  1 / 2"));assertEquals("Pose in your own outfit.",((TextView)field(a,"cueView")).getText().toString());assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void changedSnapshotRenderAndRealBackgroundMakeRetainedConfirmInert(){
        AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<Button> retained=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            for(int mode=0;mode<5;mode++){
                scenario.onActivity(a->prepare(a,true));idle();openNewReel(scenario);final int change=mode;
                scenario.onActivity(a->{Button old=dialog(a).getButton(-1);if(change==0)takes(a).get(0).subtitles.get(0).text="Newer reviewed words";
                    else if(change==1)set(a,"reelTitle","Newer title");else if(change==2)shots(a).get(0).instruction="Pose with your own new choice.";
                    else if(change==3)invoke(a,"render");else set(a,"busy",true);
                    String snapshot=(String)invoke(a,"packSnapshot"),state=preferences.getString("state","");old.performClick();assertEquals(snapshot,invoke(a,"packSnapshot"));assertEquals(state,preferences.getString("state",""));assertEquals(2,field(a,"tab"));assertTrue(takes(a).get(0).selected);
                    set(a,"busy",false);invoke(a,"render");assertNull(field(a,"newReelDialog"));assertNoMedia(a);});idle();
            }
            scenario.onActivity(a->{activity.set(a);prepare(a,true);});idle();openNewReel(scenario);scenario.onActivity(a->retained.set(dialog(a).getButton(-1)));
            scenario.moveToState(Lifecycle.State.CREATED);assertEquals(Lifecycle.State.CREATED,scenario.getState());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertNull(field(a,"newReelDialog"));String snapshot=(String)invoke(a,"packSnapshot"),state=preferences.getString("state","");retained.get().performClick();assertEquals(snapshot,invoke(a,"packSnapshot"));assertEquals(state,preferences.getString("state",""));});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{String snapshot=(String)invoke(a,"packSnapshot");retained.get().performClick();assertEquals(snapshot,invoke(a,"packSnapshot"));assertEquals(2,field(a,"tab"));assertTrue(takes(a).get(0).selected);assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void zeroTakesCanClearOnlyTitleAndUnchangedEmptyReelKeepsMatchingReadyCache() throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->prepare(a,false));idle();openNewReel(scenario);scenario.onActivity(a->dialog(a).getButton(-1).performClick());idle();
            scenario.onActivity(a->{assertEquals(1,field(a,"tab"));assertTrue(takes(a).isEmpty());assertEquals("",field(a,"reelTitle"));assertKeptContext(a);set(a,"tab",2);invoke(a,"save");invoke(a,"render");});idle();File pointer=pointer();
            scenario.onActivity(a->{set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));invoke(a,"save");});openNewReel(scenario);
            scenario.onActivity(a->dialog(a).getButton(-1).performClick());idle();scenario.onActivity(a->{assertEquals(1,field(a,"tab"));assertTrue(takes(a).isEmpty());assertEquals("",field(a,"reelTitle"));assertSame(pointer,field(a,"pendingPack"));assertTrue(pointer.exists());assertKeptContext(a);assertNoMedia(a);});
        }
    }

    private File pointer()throws Exception{File f=File.createTempFile("new-reel-ui-pointer-",".zip",context().getCacheDir());owned.add(f);try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{6,2,4});}return f;}
    private static final class TakeFacts{
        final Take identity;final Uri uri;final String id,title,caption,origin;final long duration,in,out;final List<String> idsIdentity,ids;final List<SubtitleCue> cueIdentity;final List<String> cues=new ArrayList<>();
        TakeFacts(Take t){identity=t;uri=t.uri;id=t.shotId;title=t.title;caption=t.caption;origin=t.captionOrigin;duration=t.durationMs;in=t.inMs;out=t.outMs;idsIdentity=t.reviewedShotIds;ids=new ArrayList<>(idsIdentity);cueIdentity=t.subtitles;for(SubtitleCue cue:t.subtitles)cues.add(cue.startMs+"/"+cue.endMs+"/"+cue.text);}
        void check(Take t,boolean identities){assertFalse(t.selected);assertEquals(uri,t.uri);assertEquals(id,t.shotId);assertEquals(title,t.title);assertEquals(caption,t.caption);assertEquals(origin,t.captionOrigin);assertEquals(duration,t.durationMs);assertEquals(in,t.inMs);assertEquals(out,t.outMs);assertEquals(ids,t.reviewedShotIds);
            List<String> actual=new ArrayList<>();for(SubtitleCue cue:t.subtitles)actual.add(cue.startMs+"/"+cue.endMs+"/"+cue.text);assertEquals(cues,actual);if(identities){assertSame(idsIdentity,t.reviewedShotIds);assertSame(cueIdentity,t.subtitles);}}
    }
    private static void prepare(MainActivity a,boolean withTakes){takes(a).clear();if(withTakes){Take t=take("Old selected take");takes(a).add(t);Take second=take("Old unselected take");second.selected=false;takes(a).add(second);Take alias=take("Another original cut");alias.uri=t.uri;alias.subtitles=t.subtitles;alias.reviewedShotIds=t.reviewedShotIds;takes(a).add(alias);}
        shots(a).clear();shots(a).add(new Shot("new-reel-ui-plan-1","Hero pose","Pose in your own outfit.","My outfit",4000,FramingTarget.FACE_SHOULDERS));shots(a).add(new Shot("new-reel-ui-plan-2","Movement","Turn slightly in place.","My turn",5000,FramingTarget.FULL_OUTFIT));
        set(a,"shotIndex",shots(a).size()-1);set(a,"tab",2);set(a,"reelTitle","Yesterday's reel");set(a,"brief","My next daily outfit idea");set(a,"look","Warm");set(a,"planSource","Synthetic creator-reviewed plan");set(a,"lastVideo",Uri.parse("content://synthetic.invalid/previous-reel"));set(a,"lastEdit",Uri.parse("content://synthetic.invalid/previous-edit"));invoke(a,"save");invoke(a,"render");}
    private static Take take(String title){Take t=new Take(Uri.parse("content://synthetic.invalid/new-reel/source"),"new-reel-ui-plan-1",title,"Kept manual fallback",6000);t.inMs=500;t.outMs=5746;t.captionOrigin="creator-reviewed-offline-asr";t.reviewedShotIds.add("new-reel-ui-plan-1");t.subtitles.add(new SubtitleCue(1000,3000,"Kept reviewed words"));return t;}
    private static void assertKeptContext(MainActivity a){assertEquals("My next daily outfit idea",field(a,"brief"));assertEquals("Warm",field(a,"look"));assertEquals("Synthetic creator-reviewed plan",field(a,"planSource"));assertEquals(Uri.parse("content://synthetic.invalid/previous-reel"),field(a,"lastVideo"));assertEquals(Uri.parse("content://synthetic.invalid/previous-edit"),field(a,"lastEdit"));}
    private static void openNewReel(ActivityScenario<MainActivity> scenario){scenario.onActivity(a->{Button b=(Button)find((View)field(a,"root"),Button.class,"Start a new reel");assertNotNull(b);scrollToButton((ScrollView)field(a,"pageScroll"),b);});idle();scenario.onActivity(a->clickVisible((Button)find((View)field(a,"root"),Button.class,"Start a new reel")));idle();scenario.onActivity(a->assertTrue(dialog(a).isShowing()));}
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"newReelDialog");assertNotNull(d);assertTrue(d.isShowing());return d;}
    private static void scrollToButton(ScrollView scroll,Button button){int[] at=new int[2],top=new int[2];button.getLocationOnScreen(at);scroll.getLocationOnScreen(top);scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));}
    private static void clickVisible(Button button){assertNotNull(button);Rect visible=new Rect();assertTrue(button.getGlobalVisibleRect(visible));assertTrue(visible.height()>=button.getHeight()-2);assertTrue(button.performClick());}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static boolean hasText(View v,String fragment){if(v instanceof TextView&&((TextView)v).getText().toString().contains(fragment))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),fragment))return true;return false;}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    @SuppressWarnings("unchecked") private static List<Shot> shots(MainActivity a){return (List<Shot>)field(a,"shots");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
