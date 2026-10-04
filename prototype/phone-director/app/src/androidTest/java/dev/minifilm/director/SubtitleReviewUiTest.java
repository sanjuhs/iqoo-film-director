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

/** Synthetic metadata-only subtitle correction and modal ownership checks; no source is read. */
@RunWith(AndroidJUnit4.class)
public final class SubtitleReviewUiTest {
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

    @Test(timeout=60_000) public void malformedDraftOpensAndAllRowsValidateBeforeCorrectedSaveWithoutCutPruning() throws Exception{
        Take take=take("Review focus");take.subtitles.get(0).startMs=-10;take.subtitles.get(0).endMs=2000;
        take.subtitles.get(1).startMs=1500;Snapshot unchanged=new Snapshot(take);
        File pointer=File.createTempFile("subtitle-review-ui-pointer-",".zip",context().getCacheDir());owned.add(pointer);
        try(FileOutputStream out=new FileOutputStream(pointer)){out.write(new byte[]{7,5,3});}
        AtomicInteger y=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take);set(a,"pendingPack",pointer);set(a,"pendingPackSnapshot",invoke(a,"packSnapshot"));});idle();
            openReview(scenario,take,y);
            scenario.onActivity(a->{assertTrue(y.get()>0);assertEquals("-0.010",edits(a,"Start seconds").get(0).getText().toString());
                String saved=preferences.getString("state","");
                edits(a,"Start seconds").get(0).setText("0.100");edits(a,"Subtitle words").get(0).setText("Corrected first words");
                dialog(a).getButton(-1).performClick();assertTrue(dialog(a).isShowing());unchanged.check(take);assertEquals(saved,preferences.getString("state",""));assertSame(pointer,field(a,"pendingPack"));assertTrue(pointer.exists());
                // Empty words still require valid source timestamps; they cannot hide an invalid row.
                edits(a,"Subtitle words").get(1).setText("");edits(a,"Start seconds").get(1).setText("NaN");
                dialog(a).getButton(-1).performClick();assertTrue(dialog(a).isShowing());unchanged.check(take);assertEquals(saved,preferences.getString("state",""));assertSame(pointer,field(a,"pendingPack"));
                edits(a,"Start seconds").get(1).setText("2.000");edits(a,"End seconds").get(1).setText("5.746");edits(a,"Subtitle words").get(1).setText("Corrected later words");
                dialog(a).getButton(-1).performClick();assertNull(field(a,"subtitleReviewDialog"));
            });awaitScroll(scenario,y.get());
            scenario.onActivity(a->{unchanged.checkNonSubtitleFacts(take);assertNotSame(unchanged.cueList,take.subtitles);assertEquals(2,take.subtitles.size());
                assertEquals(100,take.subtitles.get(0).startMs);assertEquals(2000,take.subtitles.get(0).endMs);assertEquals("Corrected first words",take.subtitles.get(0).text);
                assertEquals(2000,take.subtitles.get(1).startMs);assertEquals(5746,take.subtitles.get(1).endMs);assertEquals("Corrected later words",take.subtitles.get(1).text);
                assertEquals("creator-reviewed-offline-asr",take.captionOrigin);assertEquals(1000,take.inMs);assertEquals(2000,take.outMs);
                assertNull(field(a,"pendingPack"));assertFalse(pointer.exists());assertNoMedia(a);
            });
            // A deliberately blank row is removed only after its valid times are parsed.
            openReview(scenario,take,y);
            scenario.onActivity(a->{edits(a,"Subtitle words").get(1).setText("   ");dialog(a).getButton(-1).performClick();});awaitScroll(scenario,y.get());
            scenario.onActivity(a->{assertEquals(1,take.subtitles.size());assertEquals(100,take.subtitles.get(0).startMs);assertEquals("Corrected first words",take.subtitles.get(0).text);
                unchanged.checkNonSubtitleFacts(take);assertEquals("creator-reviewed-offline-asr",take.captionOrigin);assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void laterSynchronouslyRejectsRetainedSaveAndRemoveThenExplicitRemovePreservesOtherFacts() throws Exception{
        Take take=take("Review focus");take.subtitles=Arrays.asList(take.subtitles.get(0),take.subtitles.get(1));
        Snapshot unchanged=new Snapshot(take);AtomicInteger y=new AtomicInteger();AtomicReference<Take> aliasedOther=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,take);aliasedOther.set(takes(a).get(0));aliasedOther.get().subtitles=take.subtitles;});idle();openReview(scenario,take,y);
            scenario.onActivity(a->{String saved=preferences.getString("state","");AlertDialog current=dialog(a);
                Button save=current.getButton(-1),remove=current.getButton(-3);edits(a,"Subtitle words").get(0).setText("Unapplied words");
                current.getButton(-2).performClick();assertNull("Later clears ownership in this event",field(a,"subtitleReviewDialog"));save.performClick();remove.performClick();
                unchanged.check(take);assertEquals(saved,preferences.getString("state",""));});idle();
            scenario.onActivity(a->assertEquals(y.get(),((ScrollView)field(a,"pageScroll")).getScrollY()));
            openReview(scenario,take,y);scenario.onActivity(a->dialog(a).getButton(-3).performClick());awaitScroll(scenario,y.get());
            scenario.onActivity(a->{assertNull(field(a,"subtitleReviewDialog"));unchanged.checkNonSubtitleFacts(take);assertTrue(take.subtitles.isEmpty());assertEquals("manual",take.captionOrigin);
                assertNotSame(unchanged.cueList,take.subtitles);assertSame(unchanged.cueList,aliasedOther.get().subtitles);assertEquals(2,aliasedOther.get().subtitles.size());
                assertEquals("First synthetic words",aliasedOther.get().subtitles.get(0).text);assertEquals("Later synthetic words",aliasedOther.get().subtitles.get(1).text);assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void retainedSaveAndRemoveRejectRenderedReplacedDeepCueAndChangedSourceFacts(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            for(int mode=0;mode<6;mode++){
                Take take=take("Review focus");scenario.onActivity(a->prepare(a,take));idle();openReview(scenario,take,null);final int change=mode;
                scenario.onActivity(a->{AlertDialog old=dialog(a);Button save=old.getButton(-1),remove=old.getButton(-3);edits(a,"Subtitle words").get(0).setText("Stale overwrite");
                    Take current=take;
                    if(change==0)invoke(a,"render");else if(change==1)take.subtitles.get(0).text="Newer creator words";
                    else if(change==2)take.uri=Uri.parse("content://synthetic.invalid/replaced-source");
                    else if(change==3){current=take("Replacement focus");takes(a).set(takes(a).indexOf(take),current);}
                    else if(change==4)take.reviewedShotIds.add("newer-reviewed-assignment");else take.durationMs=7000;
                    Snapshot now=new Snapshot(current);Snapshot originalNow=new Snapshot(take);String saved=preferences.getString("state","");
                    save.performClick();remove.performClick();now.check(current);originalNow.check(take);assertEquals(saved,preferences.getString("state",""));
                    invoke(a,"render");assertNull(field(a,"subtitleReviewDialog"));assertNoMedia(a);
                });idle();
            }
        }
    }

    @Test(timeout=60_000) public void realBackgroundDismissesReviewAndStaleActionsCannotApplyAfterResume(){
        Take take=take("Review focus");Snapshot unchanged=new Snapshot(take);AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<Button> save=new AtomicReference<>(),remove=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);prepare(a,take);});idle();openReview(scenario,take,null);
            scenario.onActivity(a->{save.set(dialog(a).getButton(-1));remove.set(dialog(a).getButton(-3));edits(a,"Subtitle words").get(0).setText("Stale overwrite");});
            scenario.moveToState(Lifecycle.State.CREATED);assertEquals(Lifecycle.State.CREATED,scenario.getState());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=activity.get();assertNull(field(a,"subtitleReviewDialog"));save.get().performClick();remove.get().performClick();unchanged.check(take);
                invokeReview(a,takes(a).indexOf(take));assertNull(field(a,"subtitleReviewDialog"));});
            scenario.moveToState(Lifecycle.State.RESUMED);scenario.onActivity(a->{String saved=preferences.getString("state","");save.get().performClick();remove.get().performClick();unchanged.check(take);assertEquals(saved,preferences.getString("state",""));
                set(a,"busy",true);invokeReview(a,takes(a).indexOf(take));assertNull(field(a,"subtitleReviewDialog"));set(a,"busy",false);
                set(a,"tab",0);invokeReview(a,takes(a).indexOf(take));assertNull(field(a,"subtitleReviewDialog"));set(a,"tab",2);
                invokeReview(a,-1);invokeReview(a,takes(a).size());assertNull(field(a,"subtitleReviewDialog"));assertNoMedia(a);});
        }
    }

    private static final class Snapshot{
        final Uri uri;final String id,title,caption,origin;final long duration,in,out;final boolean selected;final List<String> idsList,ids;final List<SubtitleCue> cueList;final List<String> facts=new ArrayList<>();
        Snapshot(Take t){uri=t.uri;id=t.shotId;title=t.title;caption=t.caption;origin=t.captionOrigin;duration=t.durationMs;in=t.inMs;out=t.outMs;selected=t.selected;idsList=t.reviewedShotIds;ids=new ArrayList<>(idsList);cueList=t.subtitles;for(SubtitleCue c:cueList)facts.add(c.startMs+"/"+c.endMs+"/"+c.text);}
        void checkNonSubtitleFacts(Take t){assertEquals(uri,t.uri);assertEquals(id,t.shotId);assertEquals(title,t.title);assertEquals(caption,t.caption);assertEquals(duration,t.durationMs);assertEquals(in,t.inMs);assertEquals(out,t.outMs);assertEquals(selected,t.selected);assertSame(idsList,t.reviewedShotIds);assertEquals(ids,t.reviewedShotIds);}
        void check(Take t){checkNonSubtitleFacts(t);assertEquals(origin,t.captionOrigin);assertSame(cueList,t.subtitles);List<String> now=new ArrayList<>();for(SubtitleCue c:t.subtitles)now.add(c.startMs+"/"+c.endMs+"/"+c.text);assertEquals(facts,now);}
    }
    private static Take take(String title){Take t=new Take(Uri.parse("content://synthetic.invalid/subtitle/source"),"synthetic-shot",title,"Fallback caption",6000);t.inMs=1000;t.outMs=2000;t.captionOrigin="whisper-tiny.en-draft";t.reviewedShotIds.add("synthetic-review");t.subtitles.add(new SubtitleCue(500,1500,"First synthetic words"));t.subtitles.add(new SubtitleCue(4500,5500,"Later synthetic words"));return t;}
    private static void prepare(MainActivity a,Take focus){takes(a).clear();takes(a).add(take("Before"));takes(a).add(focus);takes(a).add(take("After"));takes(a).add(take("Fourth"));takes(a).add(take("Fifth"));set(a,"tab",2);invoke(a,"render");}
    private static void openReview(ActivityScenario<MainActivity> scenario,Take take,AtomicInteger position){
        scenario.onActivity(a->{Button tools=toolsFor(a,take.title);assertNotNull(tools);ScrollView scroll=(ScrollView)field(a,"pageScroll");scrollToButton(scroll,tools);if(position!=null)position.set(scroll.getScrollY());});idle();
        scenario.onActivity(a->clickVisible(toolsFor(a,take.title)));idle();
        scenario.onActivity(a->{AlertDialog menu=(AlertDialog)field(a,"takeToolsDialog");assertNotNull(menu);Button tool=(Button)find(menu.getWindow().getDecorView(),Button.class,"Review subtitle words & timing");assertNotNull(tool);scrollToButton((ScrollView)find(menu.getWindow().getDecorView(),ScrollView.class,null),tool);});idle();
        scenario.onActivity(a->{AlertDialog menu=(AlertDialog)field(a,"takeToolsDialog");clickVisible((Button)find(menu.getWindow().getDecorView(),Button.class,"Review subtitle words & timing"));assertNull(field(a,"takeToolsDialog"));});idle();
        scenario.onActivity(a->assertTrue(dialog(a).isShowing()));
    }
    private static List<EditText> edits(MainActivity a,String hint){List<EditText> list=new ArrayList<>();collectEdits(dialog(a).getWindow().getDecorView(),hint,list);assertEquals(2,list.size());return list;}
    private static void collectEdits(View v,String hint,List<EditText> out){if(v instanceof EditText&&hint.contentEquals(((EditText)v).getHint()))out.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collectEdits(((ViewGroup)v).getChildAt(i),hint,out);}
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"subtitleReviewDialog");assertNotNull(d);assertTrue(d.isShowing());return d;}
    private static Button toolsFor(MainActivity a,String title){CheckBox heading=(CheckBox)find((View)field(a,"root"),CheckBox.class,title);return heading==null?null:(Button)find((View)heading.getParent(),Button.class,"Edit & review take");}
    private static void scrollToButton(ScrollView scroll,Button button){int[] at=new int[2],top=new int[2];button.getLocationOnScreen(at);scroll.getLocationOnScreen(top);scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));}
    private static void clickVisible(Button button){assertNotNull(button);Rect visible=new Rect();assertTrue(button.getGlobalVisibleRect(visible));assertTrue(visible.height()>=button.getHeight()-2);assertTrue(button.performClick());}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    @SuppressWarnings("unchecked") private static List<Take> takes(MainActivity a){return (List<Take>)field(a,"takes");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void invokeReview(MainActivity a,int index){try{Method m=MainActivity.class.getDeclaredMethod("reviewSubtitles",int.class);m.setAccessible(true);m.invoke(a,index);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    private static void awaitScroll(ActivityScenario<MainActivity> scenario,int expected)throws Exception{long until=android.os.SystemClock.elapsedRealtime()+5000;boolean[] restored={false};while(android.os.SystemClock.elapsedRealtime()<until){idle();scenario.onActivity(a->restored[0]=((ScrollView)field(a,"pageScroll")).getScrollY()==expected);if(restored[0])return;Thread.sleep(20);}fail("Assembly position was not restored");}
}
