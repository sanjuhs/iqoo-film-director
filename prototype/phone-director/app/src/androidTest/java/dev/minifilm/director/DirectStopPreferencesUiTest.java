package dev.minifilm.director;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Real preference edits/recreation only; no camera, microphone, model or playback. */
@RunWith(AndroidJUnit4.class)
public final class DirectStopPreferencesUiTest {
    private SharedPreferences preferences;private Map<String,?> original;private boolean changed;
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
    @After public void restoreEntirePreferences(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
            Object v=e.getValue();String key=e.getKey();if(v instanceof String)editor.putString(key,(String)v);else if(v instanceof Boolean)editor.putBoolean(key,(Boolean)v);
            else if(v instanceof Integer)editor.putInt(key,(Integer)v);else if(v instanceof Long)editor.putLong(key,(Long)v);else if(v instanceof Float)editor.putFloat(key,(Float)v);
            else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(key,new HashSet<>(strings));}else throw new AssertionError("Unsupported preference type");
        }assertTrue(editor.commit());assertEquals(original,preferences.getAll());}assertDenied();
    }

    @Test(timeout=60_000) public void legacyDefaultsThenActualFreeTalkingAndQuietChoicesSurviveRecreation() throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{assertEquals(true,field(a,"autoStop"));assertEquals(false,field(a,"waitQuietPause"));assertTrue(planned(a).isChecked());assertFalse(quiet(a).isChecked());assertNoMedia(a);});
            clickSwitch(scenario,"Stop at planned shot length");clickSwitch(scenario,"Wait for a quiet pause · experimental");
            assertSaved(false,true,false);scenario.recreate();idle();
            scenario.onActivity(a->{assertEquals(false,field(a,"autoStop"));assertEquals(true,field(a,"waitQuietPause"));assertFalse(planned(a).isChecked());assertTrue(planned(a).isEnabled());assertTrue(quiet(a).isChecked());assertFalse(sequence(a).isChecked());assertNoMedia(a);});
            clickSwitch(scenario,"Stop at planned shot length");clickSwitch(scenario,"Wait for a quiet pause · experimental");
            assertSaved(true,false,false);scenario.recreate();idle();
            scenario.onActivity(a->{assertTrue(planned(a).isChecked());assertFalse(quiet(a).isChecked());assertEquals(true,field(a,"autoStop"));assertEquals(false,field(a,"waitQuietPause"));assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void guidedForcedDisplayDoesNotOverwriteManualFalseAcrossRecreationAndTurningGuideOff() throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            clickSwitch(scenario,"Stop at planned shot length");clickSwitch(scenario,"Wait for a quiet pause · experimental");clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertTrue(planned(a).isChecked());assertFalse(planned(a).isEnabled());assertEquals(false,field(a,"autoStop"));assertTrue(quiet(a).isChecked());assertNoMedia(a);});
            assertSaved(false,true,true);scenario.recreate();idle();
            scenario.onActivity(a->{assertTrue(sequence(a).isChecked());assertTrue(planned(a).isChecked());assertFalse(planned(a).isEnabled());assertEquals(false,field(a,"autoStop"));assertEquals(true,field(a,"waitQuietPause"));assertNoMedia(a);});
            clickSwitch(scenario,"Guide the full shot sequence");assertSaved(false,true,false);
            scenario.onActivity(a->{assertFalse(planned(a).isChecked());assertTrue(planned(a).isEnabled());assertTrue(quiet(a).isChecked());assertNoMedia(a);});
            scenario.recreate();idle();scenario.onActivity(a->{assertFalse(sequence(a).isChecked());assertFalse(planned(a).isChecked());assertTrue(planned(a).isEnabled());assertTrue(quiet(a).isChecked());assertNoMedia(a);});
        }
    }
    @Test(timeout=60_000) public void preferenceEditsNeverChangeOrPersistCurrentTakeStopSnapshots() throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{set(a,"recordingTimedStop",true);set(a,"recordingQuietStop",false);});
            clickSwitch(scenario,"Stop at planned shot length");clickSwitch(scenario,"Wait for a quiet pause · experimental");clickSwitch(scenario,"Guide the full shot sequence");
            scenario.onActivity(a->{assertEquals(true,field(a,"recordingTimedStop"));assertEquals(false,field(a,"recordingQuietStop"));assertEquals(false,field(a,"sequenceActive"));assertNoMedia(a);});
            JSONObject state=new JSONObject(preferences.getString("state","{}"));assertFalse(state.has("recordingTimedStop"));assertFalse(state.has("recordingQuietStop"));assertFalse(state.has("countdown"));assertFalse(state.has("session"));
            assertSaved(false,true,true);scenario.recreate();idle();
            scenario.onActivity(a->{assertEquals(false,field(a,"recordingTimedStop"));assertEquals(false,field(a,"recordingQuietStop"));assertEquals(false,field(a,"sequenceActive"));assertEquals(false,field(a,"session"));assertNoMedia(a);});
        }
    }
    private void assertSaved(boolean manual,boolean quiet,boolean guided) throws Exception{
        JSONObject state=new JSONObject(preferences.getString("state","{}"));assertEquals(manual,state.getBoolean("autoStop"));assertEquals(quiet,state.getBoolean("waitQuietPause"));assertEquals(guided,state.getBoolean("guideSequence"));
    }
    private static Switch quiet(MainActivity a){Switch s=(Switch)find((View)field(a,"root"),Switch.class,"Wait for a quiet pause · experimental");assertNotNull(s);return s;}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
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
    private static View find(View v,Class<?> type,String text){if(type.isInstance(v)&&(text==null||v instanceof TextView&&text.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type,text);if(found!=null)return found;}return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void assertNoMedia(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());assertDenied();}
    private static void assertEmpty(File dir){File[] files=dir.listFiles();assertTrue(!dir.exists()||files!=null&&files.length==0);}
    private static void assertDenied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
