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

/** Synthetic metadata/modal checks. The owned source is a byte sentinel, not decoded video. */
@RunWith(AndroidJUnit4.class)
public final class AnotherCutUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    private final List<File> owned=new ArrayList<>();private File source;private String sourceHash;private long sourceSize,sourceModified;
    @Before public void requireOwnUnlockedDeniedEmulatorAndPreservePreferences() throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.ROOT);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());assertDenied();
        assertEmpty(new File(context().getFilesDir(),"takes"));assertEmpty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false).put("takes",new JSONArray()).toString()).commit());
        source=File.createTempFile("another-cut-ui-source-",".fixture",context().getCacheDir());owned.add(source);
        try(FileOutputStream stream=new FileOutputStream(source)){stream.write(new byte[]{1,3,5,7,9,11});}
        sourceHash=hash(source);sourceSize=source.length();sourceModified=source.lastModified();
    }
    @After public void preserveSourceAndRestoreAllPreferences() throws Exception{
        if(source!=null)assertSourceUnchanged();for(File file:owned)if(file.exists())assertTrue(file.delete());
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }

    @Test(timeout=45_000) public void actualAnotherMomentAddsAdjacentUnselectedIndependentCutAnywhereInTheOriginal() throws Exception{
        Take originalTake=take("Source A");Snapshot untouched=new Snapshot(originalTake);AtomicInteger position=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->prepare(a,originalTake));idle();openTool(scenario,originalTake,"Use another moment",position);
            scenario.onActivity(a->{assertTrue(hasText(dialog(a).getWindow().getDecorView(),"new cut stays unselected"));
                edit(a,"In point / seconds").setText("4.100");edit(a,"Out point / seconds").setText("5.750");edit(a,"Take title").setText("Second moment");
                edit(a,"Typography (used when no subtitles)").setText("Independent fallback caption");
                // A reorder changes its index while the frozen source facts still match.
                Collections.swap(takes(a),takes(a).indexOf(originalTake),takes(a).indexOf(originalTake)+1);dialog(a).getButton(-1).performClick();});awaitScroll(scenario,position.get());
            scenario.onActivity(a->{int at=takes(a).indexOf(originalTake);Take next=takes(a).get(at+1);assertEquals(6,takes(a).size());assertNotSame(originalTake,next);
                assertEquals(originalTake.uri,next.uri);assertEquals(originalTake.shotId,next.shotId);assertEquals(6000,next.durationMs);assertEquals(4100,next.inMs);assertEquals(5750,next.outMs);
                assertEquals("Second moment",next.title);assertEquals("Independent fallback caption",next.caption);assertFalse(next.selected);assertEquals("whisper-tiny.en-draft",next.captionOrigin);
                assertNotSame(originalTake.reviewedShotIds,next.reviewedShotIds);assertEquals(originalTake.reviewedShotIds,next.reviewedShotIds);
                assertNotSame(originalTake.subtitles,next.subtitles);assertNotSame(originalTake.subtitles.get(0),next.subtitles.get(0));
                assertEquals(500,next.subtitles.get(0).startMs);assertEquals(5500,next.subtitles.get(1).endMs);
                CheckBox check=(CheckBox)find((View)field(a,"root"),CheckBox.class,"Second moment");assertNotNull(check);assertFalse(check.isChecked());untouched.check(originalTake);
                next.subtitles.get(0).text="Edited only in the new cut";next.reviewedShotIds.add("new-cut-only-assignment");untouched.check(originalTake);assertNoMedia(a);
            });assertSourceUnchanged();
        }
    }

    @Test(timeout=45_000) public void cancelAndInvalidTimingPreservePreferencesCacheAndOriginalThenExistingTrimEditsOnlyItsTake() throws Exception{
        Take originalTake=take("Source A");Snapshot untouched=new Snapshot(originalTake);File cache=File.createTempFile("another-cut-ui-pointer-",".zip",context().getCacheDir());owned.add(cache);
        try(FileOutputStream stream=new FileOutputStream(cache)){stream.write(new byte[]{8,6,4});}
        AtomicInteger position=new AtomicInteger();AtomicReference<String> state=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->prepare(a,originalTake));idle();scenario.onActivity(a->{set(a,"pendingPack",cache);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));});
            openTool(scenario,originalTake,"Use another moment",position);
            scenario.onActivity(a->{state.set(preferences.getString("state",""));edit(a,"Take title").setText("Unapplied title");edit(a,"Typography (used when no subtitles)").setText("Unapplied caption");
                for(String[] range:new String[][]{{"1.000","1.100"},{"-1","2"},{"NaN","2"},{"1","Infinity"},{"1","6.001"},{"3","2"}}){
                    edit(a,"In point / seconds").setText(range[0]);edit(a,"Out point / seconds").setText(range[1]);dialog(a).getButton(-1).performClick();
                    assertTrue(dialog(a).isShowing());assertNotNull(edit(a,"Out point / seconds").getError());assertEquals(5,takes(a).size());untouched.check(originalTake);
                    assertEquals(state.get(),preferences.getString("state",""));assertSame(cache,field(a,"pendingPack"));assertTrue(cache.exists());
                }dialog(a).getButton(-2).performClick();});idle();
            scenario.onActivity(a->{assertNull(field(a,"takeCutDialog"));untouched.check(originalTake);assertEquals(position.get(),((ScrollView)field(a,"pageScroll")).getScrollY());});
            openTool(scenario,originalTake,"Trim & typography",position);
            scenario.onActivity(a->{edit(a,"In point / seconds").setText("0.250");edit(a,"Out point / seconds").setText("5.746");edit(a,"Take title").setText("Edited original");
                edit(a,"Typography (used when no subtitles)").setText("Edited fallback");dialog(a).getButton(-1).performClick();});awaitScroll(scenario,position.get());
            scenario.onActivity(a->{assertEquals(5,takes(a).size());assertEquals(250,originalTake.inMs);assertEquals(5746,originalTake.outMs);assertEquals("Edited original",originalTake.title);
                assertEquals("Edited fallback",originalTake.caption);assertEquals("whisper-tiny.en-draft",originalTake.captionOrigin);assertSame(untouched.cueList,originalTake.subtitles);
                assertSame(untouched.idsList,originalTake.reviewedShotIds);assertTrue(originalTake.selected);assertNull(field(a,"pendingPack"));assertFalse(cache.exists());assertNoMedia(a);});
        }
    }

    @Test(timeout=45_000) public void staleAnotherMomentSaveAfterRenderSourceChangeReplacementAndRealBackgroundAddsNothing(){
        AtomicReference<Take> originalTake=new AtomicReference<>();AtomicReference<Button> retained=new AtomicReference<>();AtomicReference<MainActivity> identity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            for(int mode=0;mode<3;mode++){
                Take take=take("Source A");originalTake.set(take);scenario.onActivity(a->prepare(a,take));idle();openTool(scenario,take,"Use another moment",null);
                final int mutation=mode;scenario.onActivity(a->{retained.set(dialog(a).getButton(-1));edit(a,"Take title").setText("Unapplied clone");edit(a,"In point / seconds").setText("4");edit(a,"Out point / seconds").setText("5");
                    if(mutation==0)invoke(a,"render");else if(mutation==1)take.uri=Uri.parse("content://synthetic.invalid/replaced-source");
                    else{Take replacement=take("Source A");takes(a).set(takes(a).indexOf(take),replacement);}
                    retained.get().performClick();assertEquals(5,takes(a).size());assertFalse(hasTakeTitle(a,"Unapplied clone"));invoke(a,"render");assertNoMedia(a);});
            }
            Take take=take("Source A");scenario.onActivity(a->{identity.set(a);prepare(a,take);});idle();openTool(scenario,take,"Use another moment",null);
            scenario.onActivity(a->{edit(a,"Take title").setText("Unapplied clone");retained.set(dialog(a).getButton(-1));});
            scenario.moveToState(Lifecycle.State.CREATED);assertEquals(Lifecycle.State.CREATED,scenario.getState());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{retained.get().performClick();assertNull(field(identity.get(),"takeCutDialog"));assertEquals(5,takes(identity.get()).size());});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{retained.get().performClick();assertEquals(5,takes(a).size());assertFalse(hasTakeTitle(a,"Unapplied clone"));assertNoMedia(a);});
        }
    }

    @Test(timeout=45_000) public void existingTrimSaveAfterDismissDeepFactChangeAndActualBackgroundCannotOverwriteCurrentEdits(){
        AtomicReference<Button> retained=new AtomicReference<>();Take originalTake=take("Source A");AtomicReference<MainActivity> identity=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{identity.set(a);prepare(a,originalTake);});idle();openTool(scenario,originalTake,"Trim & typography",null);
            scenario.onActivity(a->{edit(a,"Take title").setText("Stale overwrite");retained.set(dialog(a).getButton(-1));dialog(a).getButton(-2).performClick();assertNull("Cancel relinquishes ownership synchronously",field(a,"takeCutDialog"));retained.get().performClick();assertEquals("Source A",originalTake.title);});idle();
            openTool(scenario,originalTake,"Trim & typography",null);
            scenario.onActivity(a->{edit(a,"Take title").setText("Stale overwrite");retained.set(dialog(a).getButton(-1));originalTake.subtitles.get(0).text="Current reviewed words";
                retained.get().performClick();assertEquals("Source A",originalTake.title);assertEquals("Current reviewed words",originalTake.subtitles.get(0).text);invoke(a,"render");});idle();
            openTool(scenario,originalTake,"Trim & typography",null);scenario.onActivity(a->{edit(a,"In point / seconds").setText("0");retained.set(dialog(a).getButton(-1));});
            scenario.moveToState(Lifecycle.State.CREATED);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{retained.get().performClick();assertEquals(1000,originalTake.inMs);assertNull(field(identity.get(),"takeCutDialog"));});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{retained.get().performClick();assertEquals(1000,originalTake.inMs);assertEquals(2000,originalTake.outMs);assertEquals("Current reviewed words",originalTake.subtitles.get(0).text);assertNoMedia(a);});
        }
    }

    private static final class Snapshot{
        final Uri uri;final String id,title,caption,origin;final long duration,in,out;final boolean selected;final List<String> idsList,ids;
        final List<SubtitleCue> cueList;final List<String> cueFacts=new ArrayList<>();
        Snapshot(Take take){uri=take.uri;id=take.shotId;title=take.title;caption=take.caption;origin=take.captionOrigin;duration=take.durationMs;in=take.inMs;out=take.outMs;selected=take.selected;
            idsList=take.reviewedShotIds;ids=new ArrayList<>(idsList);cueList=take.subtitles;for(SubtitleCue cue:cueList)cueFacts.add(cue.startMs+"/"+cue.endMs+"/"+cue.text);}
        void check(Take take){assertEquals(uri,take.uri);assertEquals(id,take.shotId);assertEquals(title,take.title);assertEquals(caption,take.caption);assertEquals(origin,take.captionOrigin);
            assertEquals(duration,take.durationMs);assertEquals(in,take.inMs);assertEquals(out,take.outMs);assertEquals(selected,take.selected);assertSame(idsList,take.reviewedShotIds);assertEquals(ids,take.reviewedShotIds);
            assertSame(cueList,take.subtitles);List<String> now=new ArrayList<>();for(SubtitleCue cue:take.subtitles)now.add(cue.startMs+"/"+cue.endMs+"/"+cue.text);assertEquals(cueFacts,now);}
    }
    private Take take(String title){Take take=new Take(Uri.fromFile(source),"synthetic-shot",title,"Original caption",6000);take.inMs=1000;take.outMs=2000;take.captionOrigin="whisper-tiny.en-draft";
        take.reviewedShotIds.add("synthetic-reviewed-shot");take.subtitles.add(new SubtitleCue(500,1500,"First synthetic words"));take.subtitles.add(new SubtitleCue(4500,5500,"Later synthetic words"));return take;}
    private void prepare(MainActivity a,Take focus){takes(a).clear();takes(a).add(take("Before"));takes(a).add(focus);takes(a).add(take("After"));takes(a).add(take("Fourth"));takes(a).add(take("Fifth"));set(a,"tab",2);invoke(a,"render");}
    private static void openTool(ActivityScenario<MainActivity> scenario,Take take,String label,AtomicInteger position){
        scenario.onActivity(a->{Button tools=toolsFor(a,take.title);assertNotNull(tools);ScrollView scroll=(ScrollView)field(a,"pageScroll");scrollToButton(scroll,tools);if(position!=null)position.set(scroll.getScrollY());});idle();
        scenario.onActivity(a->{Button tools=toolsFor(a,take.title);clickVisible(tools);});idle();
        scenario.onActivity(a->{AlertDialog menu=(AlertDialog)field(a,"takeToolsDialog");assertNotNull(menu);Button action=(Button)find(menu.getWindow().getDecorView(),Button.class,label);assertNotNull(action);
            ScrollView scroll=(ScrollView)find(menu.getWindow().getDecorView(),ScrollView.class,null);scrollToButton(scroll,action);});idle();
        scenario.onActivity(a->{AlertDialog menu=(AlertDialog)field(a,"takeToolsDialog");Button action=(Button)find(menu.getWindow().getDecorView(),Button.class,label);clickVisible(action);assertNull(field(a,"takeToolsDialog"));});idle();
    }
    private static Button toolsFor(MainActivity a,String title){CheckBox heading=(CheckBox)find((View)field(a,"root"),CheckBox.class,title);return heading==null?null:(Button)find((View)heading.getParent(),Button.class,"Edit & review take");}
    private static void scrollToButton(ScrollView scroll,Button button){int[] at=new int[2],top=new int[2];button.getLocationOnScreen(at);scroll.getLocationOnScreen(top);scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));}
    private static void clickVisible(Button button){assertNotNull(button);Rect visible=new Rect();assertTrue(button.getGlobalVisibleRect(visible));assertTrue(visible.height()>=button.getHeight()-2);assertTrue(button.performClick());}
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"takeCutDialog");assertNotNull(d);assertTrue(d.isShowing());return d;}
    private static EditText edit(MainActivity a,String hint){EditText edit=(EditText)findHint(dialog(a).getWindow().getDecorView(),hint);assertNotNull(edit);return edit;}
    private static View findHint(View v,String hint){if(v instanceof EditText&&hint.contentEquals(((EditText)v).getHint()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findHint(((ViewGroup)v).getChildAt(i),hint);if(found!=null)return found;}return null;}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static boolean hasText(View v,String fragment){if(v instanceof TextView&&((TextView)v).getText().toString().contains(fragment))return true;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),fragment))return true;return false;}
    private static boolean hasTakeTitle(MainActivity a,String title){for(Take take:takes(a))if(title.equals(take.title))return true;return false;}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private void assertSourceUnchanged()throws Exception{assertTrue(source.isFile());assertEquals(sourceSize,source.length());assertEquals(sourceModified,source.lastModified());assertEquals(sourceHash,hash(source));}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return Base64.getEncoder().encodeToString(digest.digest());}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private static void awaitScroll(ActivityScenario<MainActivity> scenario,int expected)throws Exception{long until=android.os.SystemClock.elapsedRealtime()+5000;boolean[] restored={false};while(android.os.SystemClock.elapsedRealtime()<until){idle();scenario.onActivity(a->restored[0]=((ScrollView)field(a,"pageScroll")).getScrollY()==expected);if(restored[0])return;Thread.sleep(20);}fail("Assembly position was not restored");}
}
