package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
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

/** Fresh API36 emulator geometry/state checks; no clip read, export, model, capture or playback. */
@RunWith(AndroidJUnit4.class)
public final class AssemblyFooterUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
    @Before public void requireEmptySyntheticEmulatorAndPreservePreferences() throws Exception {
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.ROOT);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));assertEquals(36,android.os.Build.VERSION.SDK_INT);
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());denied();
        empty(new File(context().getFilesDir(),"takes"));empty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/director-mmproj.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        preferences=context().getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",2).put("voice",false).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreEveryPreferenceAndCaptureDenial(){
        if(changed){SharedPreferences.Editor e=preferences.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){
            String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);
            else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> values=(Set<String>)v;e.putStringSet(k,new HashSet<>(values));}else throw new AssertionError("Preference type");
        }assertTrue(e.commit());assertEquals(original,preferences.getAll());}denied();
    }

    @Test(timeout=45_000) public void singleFixedSummaryAndExportRemainFullyVisibleAboveNavigationAtBothScrollExtremes(){
        List<Take> rows=rows();AtomicReference<MainActivity> activity=new AtomicReference<>();AtomicReference<Rect> exportAtTop=new AtomicReference<>(),summaryAtTop=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{activity.set(a);prepare(a,rows);});awaitLayout(activity.get());
            scenario.onActivity(a->{ScrollView scroll=scroll(a);assertTrue("Six cards must actually overflow",scroll.getChildAt(0).getHeight()>scroll.getHeight());scroll.scrollTo(0,0);});idle();
            scenario.onActivity(a->{assertFooterGeometry(a);assertEquals(0,scroll(a).getScrollY());exportAtTop.set(rect(exportButton(a)));summaryAtTop.set(rect(summary(a)));
                assertTrue(exportButton(a).isEnabled());assertTrue(summary(a).getText().toString().startsWith("6 selected / 6 takes · 30.0s"));scroll(a).fullScroll(View.FOCUS_DOWN);});idle();awaitLayout(activity.get());awaitScrollBottom(activity.get());
            scenario.onActivity(a->{assertTrue("Actual full bottom scroll",scroll(a).getScrollY()>0);assertEquals(scroll(a).getChildAt(0).getHeight()-scroll(a).getHeight(),scroll(a).getScrollY());
                assertFooterGeometry(a);assertEquals(exportAtTop.get(),rect(exportButton(a)));assertEquals(summaryAtTop.get(),rect(summary(a)));quiescent(a);
            });
        }
    }

    @Test(timeout=45_000) public void selectionUpdatesOneSummaryAndExportEnablementAndBusyRestoresWithoutChangingEdits(){
        List<Take> rows=rows();List<String> originalFields=new ArrayList<>();for(Take t:rows)originalFields.add(editFields(t));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{prepare(a,rows);for(Take t:rows){CheckBox box=find((View)field(a,"root"),CheckBox.class,t.title);assertNotNull(box);box.performClick();}
                assertEquals("0 selected / 6 takes · 0.0s · up to 12 cuts / 3 min",summary(a).getText().toString());assertFalse(exportButton(a).isEnabled());
                find((View)field(a,"root"),CheckBox.class,rows.get(0).title).performClick();assertTrue(exportButton(a).isEnabled());assertEquals("1 selected / 6 takes · 2.5s · up to 12 cuts / 3 min",summary(a).getText().toString());
                call(a,"setBusy",new Class<?>[]{boolean.class},true);assertFalse(exportButton(a).isEnabled());String status=status(a);exportButton(a).performClick();assertTrue((Boolean)field(a,"busy"));assertEquals(status,status(a));assertExporterIdle(a);
                call(a,"setBusy",new Class<?>[]{boolean.class},false);assertTrue(exportButton(a).isEnabled());
                for(int i=0;i<rows.size();i++){assertEquals(originalFields.get(i),editFields(rows.get(i)));assertEquals(i==0,rows.get(i).selected);}assertEquals(rows,takes(a));quiescent(a);
            });
        }
    }

    @Test(timeout=30_000) public void emptyFooterCannotStartWorkOrProduceOutputs(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{assertTrue(takes(a).isEmpty());assertFalse(exportButton(a).isEnabled());String state=preferences.getString("state","");String before=status(a);
                exportButton(a).performClick();assertEquals(before,status(a));assertEquals(state,preferences.getString("state",""));assertNull(field(a,"lastVideo"));assertNull(field(a,"lastEdit"));
                assertTrue(summary(a).getText().toString().startsWith("0 selected / 0 takes · 0.0s"));quiescent(a);empty(new File(a.getFilesDir(),"export-journal"));
            });
        }
    }

    @Test(timeout=45_000) public void busyBackgroundReturnPreservesScrollAndDisablesRebuiltEditorsUntilProcessingEnds(){
        List<Take> rows=rows();List<String> before=new ArrayList<>();for(Take take:rows)before.add(editFields(take));
        AtomicReference<MainActivity> identity=new AtomicReference<>();AtomicInteger savedScroll=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{identity.set(a);prepare(a,rows);});awaitLayout(identity.get());
            scenario.onActivity(a->{scroll(a).scrollTo(0,300);call(a,"setBusy",new Class<?>[]{boolean.class},true);});idle();
            scenario.onActivity(a->{savedScroll.set(scroll(a).getScrollY());assertTrue(savedScroll.get()>0);assertTrue((Boolean)field(a,"busy"));assertFalse(exportButton(a).isEnabled());});
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);
            awaitLayout(identity.get());awaitScrollPosition(identity.get(),savedScroll.get());
            scenario.onActivity(a->{
                assertTrue((Boolean)field(a,"busy"));assertFalse(exportButton(a).isEnabled());assertEquals("6 selected / 6 takes · 30.0s · up to 12 cuts / 3 min",summary(a).getText().toString());
                android.widget.EditText title=find((View)field(a,"root"),android.widget.EditText.class,null);android.widget.Spinner look=find((View)field(a,"root"),android.widget.Spinner.class,null);
                assertNotNull(title);assertNotNull(look);assertFalse("Rebuilt title remains locked",title.isEnabled());assertFalse("Rebuilt look remains locked",look.isEnabled());
                for(Take take:rows){CheckBox box=find((View)field(a,"root"),CheckBox.class,take.title);assertNotNull(box);assertFalse("Rebuilt selection remains locked",box.isEnabled());assertTrue(box.isChecked());}
                Button cancel=(Button)field(a,"cancelProcessingButton");assertEquals(View.VISIBLE,cancel.getVisibility());assertTrue(cancel.isEnabled());assertExporterIdle(a);
                // Dispatch framework touch events to a disabled checkbox; performClick alone bypasses enabled state.
                CheckBox box=find((View)field(a,"root"),CheckBox.class,rows.get(0).title);long time=SystemClock.uptimeMillis();
                android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,box.getWidth()/2f,box.getHeight()/2f,0);
                android.view.MotionEvent up=android.view.MotionEvent.obtain(time,time+40,android.view.MotionEvent.ACTION_UP,box.getWidth()/2f,box.getHeight()/2f,0);
                try{box.dispatchTouchEvent(down);box.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}
            });idle();
            scenario.onActivity(a->{
                for(int i=0;i<rows.size();i++){assertTrue(rows.get(i).selected);assertEquals(before.get(i),editFields(rows.get(i)));}
                assertEquals(savedScroll.get(),scroll(a).getScrollY());assertExporterIdle(a);call(a,"setBusy",new Class<?>[]{boolean.class},false);
                assertTrue(exportButton(a).isEnabled());assertTrue(find((View)field(a,"root"),android.widget.EditText.class,null).isEnabled());assertTrue(find((View)field(a,"root"),android.widget.Spinner.class,null).isEnabled());
                for(Take take:rows)assertTrue(find((View)field(a,"root"),CheckBox.class,take.title).isEnabled());assertFooterGeometry(a);quiescent(a);
            });
        }
    }

    @Test(timeout=45_000) public void retainedFooterAfterRenderTabSwitchAndBackgroundCannotDispatchEvenAfterResume(){
        AtomicReference<MainActivity> identity=new AtomicReference<>();AtomicReference<Button> old=new AtomicReference<>();AtomicInteger savedScroll=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{identity.set(a);prepare(a,rows());
                // A controlled oversized direction makes a broken stale guard fail before any clip read.
                // Status would change at real snapshot preflight; no fake export/backend is injected.
                shots(a).get(0).instruction=new String(new char[2001]).replace('\0','x');call(a,"save");old.set(exportButton(a));call(a,"render");
                assertNotSame(old.get(),exportButton(a));String before=status(a);old.get().performClick();assertEquals(before,status(a));quiescent(a);
                old.set(exportButton(a));call(a,"switchTab",new Class<?>[]{int.class},0);assertNull(field(a,"assemblyExportButton"));before=status(a);old.get().performClick();assertEquals(before,status(a));quiescent(a);
                call(a,"switchTab",new Class<?>[]{int.class},2);assertNotSame(old.get(),exportButton(a));before=status(a);old.get().performClick();assertEquals(before,status(a));old.set(exportButton(a));
            });
            awaitLayout(identity.get());scenario.onActivity(a->scroll(a).scrollTo(0,300));idle();
            scenario.onActivity(a->{savedScroll.set(scroll(a).getScrollY());assertTrue("Actual nonzero scroll before leaving",savedScroll.get()>0);});
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{MainActivity a=identity.get();assertNull(field(a,"assemblyExportButton"));String before=status(a);old.get().performClick();assertEquals(before,status(a));quiescent(a);});
            scenario.moveToState(Lifecycle.State.RESUMED);awaitLayout(identity.get());awaitScrollPosition(identity.get(),savedScroll.get());
            scenario.onActivity(a->{assertNotSame(old.get(),exportButton(a));assertTrue(exportButton(a).isEnabled());String before=status(a);old.get().performClick();assertEquals(before,status(a));assertEquals(savedScroll.get(),scroll(a).getScrollY());assertFooterGeometry(a);quiescent(a);});
        }
    }

    private static void assertFooterGeometry(MainActivity a){View root=(View)field(a,"root");Button button=exportButton(a);TextView summary=summary(a);ScrollView scroll=scroll(a);View nav=(View)field(a,"nav");
        assertEquals(1,countText(root,"Export my reel"));assertEquals(1,countText(root,summary.getText().toString()));assertFalse(hasScrollAncestor(button));assertFalse(hasScrollAncestor(summary));assertSame(button.getParent(),summary.getParent());
        View footer=(View)button.getParent();assertSame(root,footer.getParent());assertEquals(((ViewGroup)root).indexOfChild(nav)-1,((ViewGroup)root).indexOfChild(footer));
        Rect b=rect(button),s=rect(summary),f=rect(footer),n=rect(nav),sc=rect(scroll);assertTrue(button.getGlobalVisibleRect(new Rect()));assertTrue(summary.getGlobalVisibleRect(new Rect()));
        assertEquals(button.getWidth(),b.width());assertEquals(button.getHeight(),b.height());assertEquals(summary.getWidth(),s.width());assertEquals(summary.getHeight(),s.height());assertTrue(b.width()>0&&b.height()>0&&s.height()>0);
        assertTrue("Footer below scroll",sc.bottom<=f.top);assertTrue("Footer before navigation",f.bottom<=n.top);assertTrue(f.contains(b));assertTrue(f.contains(s));
    }
    private static void awaitLayout(MainActivity a){long until=SystemClock.elapsedRealtime()+5000;AtomicBoolean ready=new AtomicBoolean();do{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Button button=exportButton(a);ready.set(button.getWidth()>0&&button.getHeight()>0&&scroll(a).getHeight()>0);});if(ready.get())return;SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<until);fail("Footer layout did not settle");}
    private static void awaitScrollPosition(MainActivity a,int expected){long until=SystemClock.elapsedRealtime()+5000;AtomicInteger actual=new AtomicInteger();do{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->actual.set(scroll(a).getScrollY()));if(actual.get()==expected)return;SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<until);assertEquals("Return preserves the cut-list scroll position",expected,actual.get());}
    private static void awaitScrollBottom(MainActivity a){long until=SystemClock.elapsedRealtime()+5000;AtomicBoolean bottom=new AtomicBoolean();do{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{ScrollView scroll=scroll(a);bottom.set(scroll.getScrollY()==scroll.getChildAt(0).getHeight()-scroll.getHeight());});if(bottom.get())return;SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<until);fail("Full bottom scroll did not settle");}
    private static Rect rect(View view){Rect rect=new Rect();assertTrue(view.getGlobalVisibleRect(rect));return rect;}
    private static boolean hasScrollAncestor(View view){for(ViewParent parent=view.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollView)return true;return false;}
    private static int countText(View view,String text){int n=view instanceof TextView&&text.contentEquals(((TextView)view).getText())?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)n+=countText(group.getChildAt(i),text);}return n;}
    private static void prepare(MainActivity a,List<Take> rows){takes(a).clear();takes(a).addAll(rows);call(a,"save");call(a,"render");quiescent(a);}
    private static List<Take> rows(){List<Take> rows=new ArrayList<>();for(int i=0;i<6;i++){File absent=new File(context().getCacheDir(),"footer-ui-"+i+"-never-created.mp4");assertFalse(absent.exists());Take t=new Take(Uri.fromFile(absent),"recorded-"+i,"Synthetic take "+i,"Retained words "+i,4000+i*1000);t.inMs=500;t.outMs=3000+i*1000;t.reviewedShotIds.add("previous-reviewed-"+i);t.subtitles.add(new SubtitleCue(600,1200,"Existing subtitle "+i));t.captionOrigin="creator-reviewed-synthetic";rows.add(t);}return rows;}
    private static String editFields(Take t){try{JSONArray cues=new JSONArray();for(SubtitleCue c:t.subtitles)cues.put(new JSONObject().put("s",c.startMs).put("e",c.endMs).put("text",c.text));return new JSONObject().put("uri",t.uri.toString()).put("id",t.shotId).put("title",t.title).put("caption",t.caption).put("origin",t.captionOrigin).put("duration",t.durationMs).put("in",t.inMs).put("out",t.outMs).put("mapping",new JSONArray(t.reviewedShotIds)).put("cues",cues).toString();}catch(JSONException e){throw new AssertionError(e);}}
    private static void assertExporterIdle(MainActivity a){Object exporter=field(a,"exporter");assertEquals(false,field(exporter,"busy"));assertNull(field(exporter,"transformer"));assertNull(field(exporter,"activeFile"));assertNull(field(exporter,"activeJournal"));}
    private static void quiescent(MainActivity a){assertEquals(false,field(a,"busy"));assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertNull(field(a,"briefRecorder"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertEquals(false,field(a,"live"));assertEquals(false,field(a,"sequenceActive"));assertEquals(0L,field(field(a,"planner"),"handle"));assertEquals(0L,field(field(a,"transcriber"),"activeRequest"));assertExporterIdle(a);SpeechCoach speech=(SpeechCoach)field(a,"speech");assertFalse(speech.isListening());assertFalse(speech.hasSpeechWork());denied();}
    private static Button exportButton(MainActivity a){Button button=(Button)field(a,"assemblyExportButton");assertNotNull(button);return button;}
    private static TextView summary(MainActivity a){TextView summary=(TextView)field(a,"selectionSummary");assertNotNull(summary);return summary;}
    private static ScrollView scroll(MainActivity a){ScrollView view=find((View)field(a,"root"),ScrollView.class,null);assertNotNull(view);return view;}
    private static String status(MainActivity a){return ((TextView)field(a,"status")).getText().toString();}
    private static <T extends View>T find(View view,Class<T> type,String text){if(type.isInstance(view)&&(text==null||view instanceof TextView&&text.contentEquals(((TextView)view).getText())))return type.cast(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){T found=find(group.getChildAt(i),type,text);if(found!=null)return found;}}return null;}
    @SuppressWarnings("unchecked")private static List<Take> takes(MainActivity a){return(List<Take>)field(a,"takes");}
    @SuppressWarnings("unchecked")private static List<Shot> shots(MainActivity a){return(List<Shot>)field(a,"shots");}
    private static Object field(Object a,String name){try{Field f=a.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private static Object call(Object a,String name,Class<?>[] types,Object...args){try{Method m=a.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(a,args);}catch(Exception e){throw new AssertionError(e);}}
    private static Object call(Object a,String name){return call(a,name,new Class<?>[0]);}
    private static void empty(File dir){File[] contents=dir.listFiles();assertTrue(!dir.exists()||contents!=null&&contents.length==0);}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
