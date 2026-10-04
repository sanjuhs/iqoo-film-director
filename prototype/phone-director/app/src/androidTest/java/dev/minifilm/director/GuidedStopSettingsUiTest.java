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
import android.widget.Switch;
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

/** Visible timed-stop settings only; no camera, model, audio or selected source is started. */
@RunWith(AndroidJUnit4.class)
public final class GuidedStopSettingsUiTest {
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
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",1).put("voice",false).put("takes",new JSONArray()).toString()).commit());
    }
    @After public void restoreEntirePreferencesAndRemoveOnlyOwnPointers(){
        for(File f:owned)if(f.exists())assertTrue(f.delete());
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }



    @Test(timeout=60_000) public void defaultManualStopCanBeDisabledThenSequenceForcesCheckedAndRestoresFalse(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{assertEquals(1,field(a,"tab"));assertFalse((Boolean)field(a,"guideSequence"));assertTrue((Boolean)field(a,"autoStop"));assertTrue(planned(a).isChecked());assertTrue(planned(a).isEnabled());});
            clickSwitch(scenario,"Stop at planned shot length");
            scenario.onActivity(a->{assertFalse((Boolean)field(a,"autoStop"));assertFalse(planned(a).isChecked());assertTrue(planned(a).isEnabled());});
            clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertTrue((Boolean)field(a,"guideSequence"));assertFalse("Original manual preference stays false",(Boolean)field(a,"autoStop"));assertTrue(planned(a).isChecked());assertFalse(planned(a).isEnabled());
                assertTrue(hasText((View)field(a,"root"),"Guided sequences require a planned stop"));assertTrue(hasText((View)field(a,"root"),"turn both sequence guidance and planned stop off"));
                assertTrue(hasText((View)field(a,"root"),"60-second limit"));assertTrue(hasText((View)field(a,"root"),"Changes apply to the next take"));assertNoMedia(a);});
            clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertFalse((Boolean)field(a,"guideSequence"));assertFalse((Boolean)field(a,"autoStop"));assertFalse(planned(a).isChecked());assertTrue(planned(a).isEnabled());assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void trueManualPreferenceSurvivesSequenceAndBusyEditorReenableKeepsForcedControlDisabled(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertTrue((Boolean)field(a,"autoStop"));assertTrue(planned(a).isChecked());assertFalse(planned(a).isEnabled());
                invokeBusy(a,true);assertFalse(planned(a).isEnabled());assertFalse(sequence(a).isEnabled());invokeBusy(a,false);
                assertFalse("Generic editor re-enable must preserve the forced stop",planned(a).isEnabled());assertTrue(sequence(a).isEnabled());assertNoMedia(a);});
            clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertTrue((Boolean)field(a,"autoStop"));assertTrue(planned(a).isChecked());assertTrue(planned(a).isEnabled());assertFalse((Boolean)field(a,"waitQuietPause"));assertNoMedia(a);});
        }
    }

    @Test(timeout=60_000) public void directRerenderRetainsManualFalseUnderCheckedDisabledSequenceWithoutChangingTakeStopFlags(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            clickSwitch(scenario,"Stop at planned shot length");clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{Switch previous=planned(a);Object timed=field(a,"recordingTimedStop"),quiet=field(a,"recordingQuietStop");invoke(a,"render");
                assertNotSame(previous,planned(a));assertSame(planned(a),field(a,"plannedStopSwitch"));assertTrue(planned(a).isChecked());assertFalse(planned(a).isEnabled());assertFalse((Boolean)field(a,"autoStop"));
                assertEquals(timed,field(a,"recordingTimedStop"));assertEquals(quiet,field(a,"recordingQuietStop"));assertNoMedia(a);});idle();
            clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertFalse(planned(a).isChecked());assertTrue(planned(a).isEnabled());assertFalse((Boolean)field(a,"autoStop"));assertNoMedia(a);});
        }
    }

    private static Switch planned(MainActivity a){Switch s=(Switch)find((View)field(a,"root"),Switch.class,"Stop at planned shot length");assertNotNull(s);return s;}
    private static Switch sequence(MainActivity a){Switch s=(Switch)find((View)field(a,"root"),Switch.class,"Guide the full shot sequence");assertNotNull(s);return s;}
    private static void clickSwitch(ActivityScenario<MainActivity> scenario,String label){
        awaitVisibleSwitch(scenario,label);
        scenario.onActivity(a->{
            Switch s=(Switch)find((View)field(a,"root"),Switch.class,label);
            assertNotNull("Current switch exists: "+label,s);
            Rect visible=new Rect();
            assertTrue("Switch has a visible global rectangle: "+label,s.getGlobalVisibleRect(visible));
            assertTrue("Full switch height is reachable: "+label+" rect="+visible+" height="+s.getHeight(),visible.height()>=s.getHeight()-2);
            assertTrue("Full switch width is reachable: "+label+" rect="+visible+" width="+s.getWidth(),visible.width()>=s.getWidth()-2);
            assertTrue("Switch is enabled before creator click: "+label,s.isEnabled());
            boolean before=s.isChecked();
            // CompoundButton.performClick toggles checked state, but its return value only
            // reports an assigned OnClickListener. These controls use OnCheckedChangeListener.
            s.performClick();
            assertEquals("Creator click changes the actual checked state: "+label,!before,s.isChecked());
        });idle();
    }
    private static void awaitVisibleSwitch(ActivityScenario<MainActivity> scenario,String label){
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;boolean[] visible={false};String[] bounds={"layout not observed"};
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            idle();
            scenario.onActivity(a->{
                Switch s=(Switch)find((View)field(a,"root"),Switch.class,label);assertNotNull("Current switch exists: "+label,s);
                ScrollView scroll=(ScrollView)field(a,"pageScroll");
                if(!s.isLaidOut()||s.isLayoutRequested()||s.getHeight()<=0||s.getWidth()<=0||scroll.getHeight()<=0){visible[0]=false;return;}
                int[] at=new int[2],top=new int[2];s.getLocationOnScreen(at);scroll.getLocationOnScreen(top);
                scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+at[1]-top[1]-24));
                Rect rect=new Rect();boolean shown=s.getGlobalVisibleRect(rect);
                bounds[0]="rect="+rect+" switch="+s.getWidth()+"x"+s.getHeight()+" scrollY="+scroll.getScrollY();
                visible[0]=shown&&rect.height()>=s.getHeight()-2&&rect.width()>=s.getWidth()-2;
            });
            if(visible[0])return;
            try{Thread.sleep(16);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError("Interrupted while awaiting switch layout",interrupted);}
        }
        fail("Current switch did not become fully visible after scrolling: "+label+" "+bounds[0]);
    }
    private static void invokeBusy(MainActivity a,boolean value){try{Method m=MainActivity.class.getDeclaredMethod("setBusy",boolean.class);m.setAccessible(true);m.invoke(a,value);}catch(Exception e){throw new AssertionError(e);}}
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static boolean hasText(View v,String fragment){if(v instanceof TextView&&((TextView)v).getText().toString().contains(fragment))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasText(((ViewGroup)v).getChildAt(i),fragment))return true;return false;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
