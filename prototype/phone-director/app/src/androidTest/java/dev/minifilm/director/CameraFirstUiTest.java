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
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Visible camera-first controls, explicit AI unavailability and inert stale gestures on an owned camera-disabled emulator. */
@RunWith(AndroidJUnit4.class)
public final class CameraFirstUiTest {
    private SharedPreferences prefs;private Map<String,?> original;
    @Before public void requireOwnedEmptyUnlockedEmulatorAndBackupPreferences()throws Exception{
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(Locale.US);
        assertTrue(fingerprint.contains("generic")||fingerprint.contains("emulator")||fingerprint.contains("sdk_gphone"));
        KeyguardManager keyguard=(KeyguardManager)context().getSystemService(Context.KEYGUARD_SERVICE);assertFalse(keyguard!=null&&keyguard.isKeyguardLocked());denied();
        empty(new File(context().getFilesDir(),"takes"));empty(new File(context().getFilesDir(),"export-journal"));
        assertFalse(new File(context().getFilesDir(),"director-model.gguf").exists());assertFalse(new File(context().getFilesDir(),"models/ggml-tiny.en.bin").exists());
        prefs=context().getSharedPreferences("shoot",0);original=new HashMap<>(prefs.getAll());
        assertTrue(prefs.edit().clear().putString("state",new JSONObject().put("voice",false).put("tab",1).toString()).commit());
    }
    @After public void restoreAllPreferences(){if(original!=null){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:original.entrySet()){
        Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set){@SuppressWarnings("unchecked")Set<String> values=(Set<String>)v;edit.putStringSet(k,new HashSet<>(values));}else throw new AssertionError("Unsupported preference type");
    }assertTrue(edit.commit());assertEquals(original,prefs.getAll());}denied();}

    @Test(timeout=60_000) public void firstScreenHasVisibleCameraAndAiControlsWithNoAutomaticCapture(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{assertEquals(1,field(a,"tab"));View root=(View)field(a,"root");
                for(String label:new String[]{"Generate AI","Start camera","Camera","Reel"}){View view=find(root,label);assertNotNull(label,view);Rect visible=new Rect();assertTrue(label,view.getGlobalVisibleRect(visible));assertTrue(label,visible.height()>=view.getHeight()-2);}
                assertFalse(find(root,"Switch front / back").isShown());quiet(a);
            });
        }
    }
    @Test(timeout=60_000) public void optionsExpansionPreservesTheCurrentPageAndCameraOffState(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{View root=(View)field(a,"root");Object preview=field(a,"preview");int generation=(Integer)field(a,"pageRenderGeneration");
                find(root,"Shoot options").performClick();assertTrue(find(root,"Switch front / back").isShown());assertSame(preview,field(a,"preview"));assertEquals(generation,field(a,"pageRenderGeneration"));
                find(root,"Shoot options").performClick();assertFalse(find(root,"Switch front / back").isShown());assertSame(preview,field(a,"preview"));quiet(a);
            });
        }
    }
    @Test(timeout=60_000) public void absentAiNeedsAnExplicitChoiceAndOldGenerateButtonCannotChangeAReelPage(){
        AtomicReference<Button> old=new AtomicReference<>();AtomicReference<String> before=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{before.set(plan(a));old.set((Button)field(a,"planGenerateButton"));assertTrue(old.get().performClick());assertEquals(before.get(),plan(a));});idle();
            scenario.onActivity(a->{assertTrue(dialog(a).isShowing());dialog(a).getButton(-2).performClick();assertEquals(before.get(),plan(a));assertNull(field(a,"planningDialog"));old.get().performClick();});idle();
            scenario.onActivity(a->{dialog(a).getButton(-1).performClick();assertEquals("Editable template · local AI unavailable",field(a,"planSource"));invoke(a,"switchTab",new Class<?>[]{int.class},2);String reelPlan=plan(a);old.get().performClick();assertEquals(2,field(a,"tab"));assertEquals(reelPlan,plan(a));assertNull(field(a,"planningDialog"));quiet(a);});
        }
    }
    @Test(timeout=60_000) public void countdownAdmissionDisablesGenerationAndRejectsDirectOrRetainedGestures(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            idle();scenario.onActivity(a->{Button generate=(Button)field(a,"planGenerateButton");String before=plan(a);set(a,"countdown",true);invoke(a,"updateFinishContinueButton");assertFalse(generate.isEnabled());generate.performClick();invoke(a,"generatePlan");assertEquals(before,plan(a));assertNull(field(a,"planningDialog"));set(a,"countdown",false);invoke(a,"updateFinishContinueButton");assertTrue(generate.isEnabled());quiet(a);});
        }
    }
    private static AlertDialog dialog(MainActivity a){AlertDialog d=(AlertDialog)field(a,"planningDialog");assertNotNull(d);assertTrue(d.isShowing());return d;}
    private static String plan(MainActivity a){return invoke(a,"serializeShots").toString();}
    private static void quiet(MainActivity a){assertNull(field(a,"capture"));assertNull(field(a,"pose"));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(((SpeechCoach)field(a,"speech")).isListening());denied();}
    private static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    @SuppressWarnings("unchecked")private static List<Take> takes(MainActivity a){return(List<Take>)field(a,"takes");}
    @SuppressWarnings("unchecked")private static List<Shot> shots(MainActivity a){return(List<Shot>)field(a,"shots");}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object invoke(Object o,String name){return invoke(o,name,new Class<?>[0]);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void empty(File dir){File[] entries=dir.listFiles();assertTrue(!dir.exists()||entries!=null&&entries.length==0);}
    private static void denied(){assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.CAMERA));assertEquals(PackageManager.PERMISSION_DENIED,context().checkSelfPermission(Manifest.permission.RECORD_AUDIO));}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void idle(){InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
