package dev.minifilm.director;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Switch;
import androidx.camera.view.PreviewView;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Synthetic session delivery only: no preview, ML inference, microphone or TTS playback. */
@RunWith(AndroidJUnit4.class)
public final class ShootPoseCueUiTest {
    private SharedPreferences preferences;
    private Map<String,?> original;
    private boolean changed;

    @Before public void unlockedDeniedAndPreservePreferences() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager lock=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the test device before running UI tests",lock!=null&&lock.isKeyguardLocked());
        assertDenied(context);
        preferences=context.getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("tab",1)
                .put("brief","Synthetic outfit cue test").put("voice",false).toString()).commit());
    }
    @After public void restorePreferencesAndDeniedPermissions(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()){
                String key=entry.getKey();Object value=entry.getValue();
                if(value instanceof String)editor.putString(key,(String)value);
                else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)editor.putInt(key,(Integer)value);
                else if(value instanceof Long)editor.putLong(key,(Long)value);
                else if(value instanceof Float)editor.putFloat(key,(Float)value);
                else if(value instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)value;editor.putStringSet(key,new HashSet<>(strings));}
                else throw new AssertionError("Unsupported preference type");
            }assertTrue(editor.commit());assertEquals(original,preferences.getAll());
        }assertDenied(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test(timeout=30_000) public void alreadyQueuedOldPoseCannotReplaceNewShotAfterRender(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);int old=(Integer)field(a,"shootPoseGeneration");
                // Same main-loop delivery as a worker result already queued before replacement.
                new Handler(Looper.getMainLooper()).post(()->apply(a,old,"Old shot cue",5,20));
                invoke(a,"render",new Class<?>[0]);assertTrue((Integer)field(a,"shootPoseGeneration")>old);
                installIdleCapture(a);apply(a,(Integer)field(a,"shootPoseGeneration"),"Current shot cue",9,25);
                assertEquals("Current shot cue",framing(a));assertIdleCapture(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals("Current shot cue",framing(a));assertTrue(status(a).contains("9 visible points"));assertIdleCapture(a);});
        }
    }

    @Test(timeout=30_000) public void backgroundInvalidatesOldPoseEvenWhenCreatorResumesNewSession(){
        AtomicInteger old=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{installIdleCapture(a);old.set((Integer)field(a,"shootPoseGeneration"));});
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{
                assertTrue((Integer)field(a,"shootPoseGeneration")>old.get());
                assertEquals(false,field(a,"session"));installIdleCapture(a);
                int current=(Integer)field(a,"shootPoseGeneration");apply(a,current,"Resumed shot cue",11,30);
                String status=status(a);apply(a,old.get(),"Old background cue",2,900);
                assertEquals("Resumed shot cue",framing(a));assertEquals(status,status(a));assertIdleCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void pendingAndActiveDirectionKeepExactSpeechWhileVisualPoseUpdatesThenIdleCanRequestCue(){
        DenyFocus focus=new DenyFocus();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);SpeechCoach previous=(SpeechCoach)field(a,"speech");previous.close();
                SpeechCoach coach=new SpeechCoach(a,focus);set(a,"speech",coach);set(a,"voice",true);
                int generation=(Integer)field(a,"shootPoseGeneration");set(a,"lastCue",-12_001L);
                String direction="Stand wearing your outfit, then hold your pose.";
                set(coach,"pendingSpeech",direction);set(coach,"ready",false);
                assertTrue(coach.hasSpeechWork());apply(a,generation,"Pending visual cue",12,40);
                assertEquals("Pending visual cue",framing(a));assertEquals(direction,field(coach,"pendingSpeech"));
                assertEquals(0,focus.requests);assertEquals(-12_001L,field(a,"lastCue"));
                set(coach,"pendingSpeech",null);set(coach,"ready",true);set(coach,"activeUtterance","synthetic-direction");
                AudioFocusRequest lease=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build()).build();
                set(coach,"activeFocus",lease);
                assertTrue(coach.hasSpeechWork());apply(a,generation,"Active visual cue",13,45);
                assertEquals("Active visual cue",framing(a));assertEquals("synthetic-direction",field(coach,"activeUtterance"));
                assertSame(lease,field(coach,"activeFocus"));assertEquals(0,focus.requests);assertEquals(0,focus.abandons);
                invoke(coach,"finishSpeech",new Class<?>[]{String.class,boolean.class},"synthetic-direction",true);
                assertFalse(coach.hasSpeechWork());assertEquals(1,focus.abandons);
                apply(a,generation,"Idle visual cue",14,50);
                assertEquals("Idle visual cue",framing(a));assertEquals("Eligible idle cue must request focus",1,focus.requests);
                assertNull("Denied focus must never arm playback",field(coach,"speechTimeout"));assertIdleCapture(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{SpeechCoach coach=(SpeechCoach)field(a,"speech");assertFalse(coach.hasSpeechWork());assertEquals(1,focus.requests);assertIdleCapture(a);});
        }
    }

    @Test(timeout=30_000) public void stoppedSessionAndCountdownRejectPoseWithoutChangingDirectionOrFocus(){
        DenyFocus focus=new DenyFocus();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);((SpeechCoach)field(a,"speech")).close();SpeechCoach coach=new SpeechCoach(a,focus);
                set(a,"speech",coach);set(a,"voice",true);set(coach,"ready",true);set(a,"lastCue",-12_001L);
                int generation=(Integer)field(a,"shootPoseGeneration");String before=framing(a),status=status(a);
                set(a,"countdown",true);apply(a,generation,"Countdown must stay quiet",6,20);
                assertEquals(before,framing(a));assertEquals(status,status(a));assertEquals(0,focus.requests);
                set(a,"countdown",false);set(a,"session",false);apply(a,generation,"Ended session cue",7,20);
                assertEquals(before,framing(a));assertEquals(status,status(a));assertFalse(coach.hasSpeechWork());assertEquals(0,focus.requests);assertIdleCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void rapidLensSwitchIsRefusedWhileBindingButStoppedControllerAllowsSavedSelection(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                installIdleCapture(a);CaptureController capture=(CaptureController)field(a,"capture");
                int desired=(Integer)field(a,"desiredLens"),controllerLens=capture.getLensFacing();
                int generation=(Integer)field(a,"shootPoseGeneration");
                set(capture,"previewRequested",true);set(capture,"ready",false);
                assertTrue(capture.isPreviewPending());
                Button toggle=button((View)field(a,"root"),"Switch front / back");assertNotNull(toggle);toggle.performClick();
                assertEquals(desired,field(a,"desiredLens"));assertEquals(controllerLens,capture.getLensFacing());
                assertSame(capture,field(a,"capture"));assertTrue(capture.isPreviewPending());
                assertEquals(generation,field(a,"shootPoseGeneration"));assertNull(field(capture,"provider"));assertDenied(a);
                // Exercise the real stop state transition without ever requesting a preview.
                capture.stopPreview();set(a,"session",false);assertFalse(capture.isPreviewPending());toggle.performClick();
                int opposite=desired==CameraSelector.LENS_FACING_FRONT?CameraSelector.LENS_FACING_BACK:CameraSelector.LENS_FACING_FRONT;
                assertEquals(opposite,field(a,"desiredLens"));assertEquals(controllerLens,capture.getLensFacing());
                assertSame(capture,field(a,"capture"));assertEquals(false,field(a,"live"));assertEquals(false,field(a,"session"));
                assertEquals(generation,field(a,"shootPoseGeneration"));assertIdleCapture(a);
                capture.close();assertFalse(capture.isPreviewPending());
            });
        }
    }

    @Test(timeout=30_000) public void failedPreviewClearsPendingAndPartialUseCasesBeforeErrorThenAllowsLensChoice(){
        AtomicInteger errors=new AtomicInteger();AtomicReference<CaptureController> owned=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                CaptureController controller=new CaptureController(a,(PreviewView)field(a,"preview"),new CaptureController.Listener(){
                    public void onReady(){fail("No preview may start in this synthetic test");}
                    public void onRecordingStarted(){fail("No recording may start in this synthetic test");}
                    public void onRecordingFinished(Uri uri,long duration){fail("No media may be created in this synthetic test");}
                    public void onError(String message){
                        CaptureController failed=owned.get();assertNotNull(failed);
                        assertFalse("Error callback must observe terminal, not pending, preview",failed.isPreviewPending());
                        assertEquals(false,field(failed,"ready"));assertEquals(false,field(failed,"analysisAvailable"));
                        assertNull(field(failed,"analysis"));assertNull(field(failed,"videoCapture"));assertNull(field(failed,"provider"));
                        assertFalse(failed.isRecording());assertEquals("Camera could not open. Close other camera apps and try again.",message);errors.incrementAndGet();
                    }
                });
                owned.set(controller);set(a,"capture",controller);set(a,"live",false);set(a,"session",true);
                // Construct an unbound partial analysis use case; never request a provider,
                // attach frames, call startPreview, or grant a capture permission.
                set(controller,"analysis",new ImageAnalysis.Builder().build());set(controller,"analysisAvailable",true);
                set(controller,"previewRequested",true);set(controller,"ready",false);
                int binding=(Integer)field(controller,"bindingGeneration"),desired=(Integer)field(a,"desiredLens");
                assertTrue(controller.isPreviewPending());invoke(controller,"previewFailed",new Class<?>[0]);
                assertEquals(1,errors.get());assertEquals(binding+1,field(controller,"bindingGeneration"));
                invoke(controller,"previewFailed",new Class<?>[0]);assertEquals("Duplicate terminal failure must be silent",1,errors.get());
                Button toggle=button((View)field(a,"root"),"Switch front / back");assertNotNull(toggle);toggle.performClick();
                int opposite=desired==CameraSelector.LENS_FACING_FRONT?CameraSelector.LENS_FACING_BACK:CameraSelector.LENS_FACING_FRONT;
                assertEquals(opposite,field(a,"desiredLens"));assertSame(controller,field(a,"capture"));assertEquals(false,field(a,"live"));assertIdleCapture(a);
                controller.close();invoke(controller,"previewFailed",new Class<?>[0]);assertEquals("Closed controller cannot deliver a late error",1,errors.get());
            });
        }
    }

    @Test(timeout=30_000) public void deniedCameraStartEndsPoseSessionAndQueuedFailedAttemptCannotReplaceError(){
        AtomicReference<String> error=new AtomicReference<>(),frame=new AtomicReference<>();
        AtomicInteger attempt=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                assertDenied(a);assertEquals(false,field(a,"voice"));
                // The actual start path constructs its bundled pose client, but denied
                // camera permission returns before requesting a provider or any frame.
                attempt.set((Integer)field(a,"shootPoseGeneration")+1);
                new Handler(Looper.getMainLooper()).post(()->apply(a,attempt.get(),"Synthetic queued failed-attempt pose",8,20));
                invoke(a,"startCamera",new Class<?>[0]);
                assertTrue("Terminal error must invalidate the attempted pose generation",(Integer)field(a,"shootPoseGeneration")>attempt.get());
                assertEquals(false,field(a,"session"));assertEquals(false,field(a,"live"));
                PoseCoach pose=(PoseCoach)field(a,"pose");assertNotNull(pose);assertEquals(false,field(pose,"enabled"));
                assertEquals("Allow camera access to start your shoot preview.",status(a));
                error.set(status(a));frame.set(framing(a));assertIdleCapture(a);
                assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{
                assertEquals(error.get(),status(a));assertEquals(frame.get(),framing(a));
                assertEquals(false,field(a,"session"));assertEquals(false,field((PoseCoach)field(a,"pose"),"enabled"));
                assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertIdleCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void sequencePoseBreakEnablesPersonVisualAdviceButRejectsOldFramesAndObjectAdvice(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                prepareIdlePoseBreak(a);int generation=(Integer)field(a,"countdownGeneration");
                assertEquals(true,field(field(a,"pose"),"enabled"));assertEquals(true,field(a,"poseBreakActive"));
                apply(a,(Integer)field(a,"shootPoseGeneration"),"Fresh synthetic framing observation",15,40);
                assertEquals("Fresh synthetic framing observation",framing(a));
                String current=framing(a);apply(a,(Integer)field(a,"shootPoseGeneration"),"Stale observation",4,2001);
                assertEquals(current,framing(a));
                DenyFocus focus=installDenyFocus(a);String held=holdActualFramingCompletion(a,"One early synthetic framing cue");
                invoke(field(a,"speech"),"finishSpeech",new Class<?>[]{String.class,boolean.class},held,true);
                assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertEquals(1,focus.requests);
                apply(a,(Integer)field(a,"shootPoseGeneration"),"Visual update after own speech completes",16,30);
                assertEquals("Visual update after own speech completes",framing(a));assertEquals("The same break cannot speak a second framing cue",1,focus.requests);
                invoke(a,"finishPoseBreak",new Class<?>[]{int.class},generation);
                assertEquals("An early finish cannot shorten the eight-second break",true,field(a,"poseBreakActive"));
                assertEquals(true,field(field(a,"pose"),"enabled"));assertFalse(timer(a).startsWith("Starting in"));
                invoke(a,"stopTake",new Class<?>[0]);set(a,"style","Product reveal");set(a,"voice",false);
                set(a,"sequenceActive",true);invoke(a,"prepareSequenceShot",new Class<?>[0]);
                assertEquals(false,field(field(a,"pose"),"enabled"));current=framing(a);String before=status(a);
                apply(a,(Integer)field(a,"shootPoseGeneration"),"Object shot must not accept a person cue",17,40);
                assertEquals(current,framing(a));assertEquals(before,status(a));
                invoke(a,"stopTake",new Class<?>[0]);assertIdleCapture(a);
            });
        }
    }

    @Test(timeout=30_000) public void realEightSecondPoseBreakWaitsForOneOwnedFramingCueBeforeCountdownThree(){
        AtomicReference<MainActivity> identity=new AtomicReference<>();AtomicReference<String> heldId=new AtomicReference<>();
        AtomicReference<DenyFocus> focus=new AtomicReference<>();AtomicReference<Long> deadline=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                identity.set(a);prepareIdlePoseBreak(a);deadline.set((Long)field(a,"poseBreakUntilMs"));
                focus.set(installDenyFocus(a));heldId.set(holdActualFramingCompletion(a,"One synthetic framing cue"));
                assertEquals(1,focus.get().requests);assertEquals(true,field(a,"poseBreakSpeechPending"));
                apply(a,(Integer)field(a,"shootPoseGeneration"),"New visual observation while speech is pending",18,30);
                assertEquals("New visual observation while speech is pending",framing(a));assertEquals(1,focus.get().requests);
                assertEquals(heldId.get(),field(field(a,"speech"),"activeUtterance"));assertIdleCapture(a);
            });
            // This method retains a real clock-driven minimum; other cancellation checks do not wait eight seconds.
            while(SystemClock.elapsedRealtime()<deadline.get()-500)SystemClock.sleep(Math.min(50,Math.max(1,deadline.get()-500-SystemClock.elapsedRealtime())));
            scenario.onActivity(a->{assertEquals(true,field(a,"poseBreakActive"));assertEquals(true,field(field(a,"pose"),"enabled"));assertFalse(timer(a).startsWith("Starting in"));assertEquals(1,focus.get().requests);});
            awaitMain(identity.get(),()->Boolean.TRUE.equals(field(identity.get(),"poseBreakWaitingForSpeech")),5000);
            scenario.onActivity(a->{
                assertTrue(SystemClock.elapsedRealtime()>=deadline.get());assertEquals(false,field(a,"poseBreakActive"));
                assertEquals(false,field(field(a,"pose"),"enabled"));assertEquals(true,field(a,"poseBreakSpeechPending"));
                assertFalse(timer(a).startsWith("Starting in"));String before=framing(a);
                apply(a,(Integer)field(a,"shootPoseGeneration"),"Late observation after break",19,20);assertEquals(before,framing(a));
                SpeechCoach coach=(SpeechCoach)field(a,"speech");invoke(coach,"finishSpeech",new Class<?>[]{String.class,boolean.class},heldId.get(),true);
                assertEquals("Starting in 3…",timer(a));assertEquals(false,field(field(a,"pose"),"enabled"));
                assertEquals("One framing cue and then countdown three",2,focus.get().requests);
                assertNull("Denied focus never arms playback",field(coach,"speechTimeout"));invoke(a,"stopTake",new Class<?>[0]);assertIdleCapture(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals(false,field(a,"countdown"));assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertIdleCapture(a);});
        }
    }

    @Test(timeout=30_000) public void stoppedDisabledReplacedAndBackgroundPoseBreaksCannotAdvanceFromRetainedSpeechCompletion(){
        AtomicReference<MainActivity> identity=new AtomicReference<>();AtomicReference<Runnable> backgroundCompletion=new AtomicReference<>();AtomicInteger backgroundGeneration=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                identity.set(a);
                for(int mode=0;mode<3;mode++){
                    prepareIdlePoseBreak(a);installDenyFocus(a);holdActualFramingCompletion(a,"Synthetic cancellable cue");
                    Runnable completion=(Runnable)field(field(a,"speech"),"afterSpeech");assertNotNull(completion);int old=(Integer)field(a,"countdownGeneration");
                    if(mode==0)invoke(a,"stopTake",new Class<?>[0]);
                    else if(mode==1){Switch sequence=switchView((View)field(a,"root"),"Guide the full shot sequence");assertNotNull(sequence);assertTrue(sequence.isChecked());sequence.setChecked(false);}
                    else{invoke(a,"cancelCountdown",new Class<?>[0]);set(a,"voice",false);set(a,"sequenceActive",true);invoke(a,"prepareSequenceShot",new Class<?>[0]);}
                    String before=timer(a);completion.run();invoke(a,"finishPoseBreak",new Class<?>[]{int.class},old);
                    assertEquals(before,timer(a));assertFalse(timer(a).startsWith("Starting in"));
                    if(mode==2){assertEquals(true,field(a,"poseBreakActive"));assertEquals(true,field(field(a,"pose"),"enabled"));}
                    else assertEquals(false,field(a,"countdown"));
                    invoke(a,"stopTake",new Class<?>[0]);assertIdleCapture(a);
                }
                prepareIdlePoseBreak(a);installDenyFocus(a);holdActualFramingCompletion(a,"Synthetic background cue");
                backgroundCompletion.set((Runnable)field(field(a,"speech"),"afterSpeech"));backgroundGeneration.set((Integer)field(a,"countdownGeneration"));
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                MainActivity a=identity.get();String before=timer(a);backgroundCompletion.get().run();invoke(a,"finishPoseBreak",new Class<?>[]{int.class},backgroundGeneration.get());
                assertEquals(before,timer(a));assertEquals(false,field(a,"session"));assertEquals(false,field(a,"countdown"));assertEquals(false,field(field(a,"pose"),"enabled"));assertIdleCapture(a);
            });
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{assertEquals(false,field(a,"countdown"));assertFalse(timer(a).startsWith("Starting in"));assertIdleCapture(a);});
        }
    }

    @Test(timeout=30_000) public void failedOwnedFramingSpeechPausesBeforeCountdownAndStaleDeadlineStaysInert(){
        AtomicInteger generation=new AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                prepareIdlePoseBreak(a);DenyFocus focus=installDenyFocus(a);generation.set((Integer)field(a,"countdownGeneration"));
                apply(a,(Integer)field(a,"shootPoseGeneration"),"Synthetic denied framing cue",14,30);
                assertEquals(1,focus.requests);assertNull(field(field(a,"speech"),"speechTimeout"));assertIdleCapture(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{
                assertEquals(false,field(a,"sequenceActive"));assertEquals(false,field(a,"countdown"));assertEquals(true,field(a,"spokenPreparationPaused"));
                assertFalse(((SpeechCoach)field(a,"speech")).hasSpeechWork());assertFalse(timer(a).startsWith("Starting in"));
                String before=timer(a);invoke(a,"finishPoseBreak",new Class<?>[]{int.class},generation.get());assertEquals(before,timer(a));assertIdleCapture(a);
            });
        }
    }

    private static void prepareIdlePoseBreak(MainActivity a){
        installIdleCapture(a);PoseCoach old=(PoseCoach)field(a,"pose");if(old!=null)old.close();
        PoseCoach pose=new PoseCoach((cue,count,latency)->fail("No frame may be inferred in this synthetic test"));pose.setEnabled(false);set(a,"pose",pose);
        set(a,"style","Fashion");set(a,"voice",false);
        Switch sequence=switchView((View)field(a,"root"),"Guide the full shot sequence");assertNotNull(sequence);sequence.setChecked(true);
        set(a,"sequenceActive",true);invoke(a,"prepareSequenceShot",new Class<?>[0]);assertEquals(true,field(a,"countdown"));assertEquals(true,field(a,"poseBreakActive"));
    }
    private static DenyFocus installDenyFocus(MainActivity a){
        ((SpeechCoach)field(a,"speech")).close();DenyFocus focus=new DenyFocus();SpeechCoach coach=new SpeechCoach(a,focus);set(a,"speech",coach);set(coach,"ready",true);set(a,"voice",true);set(a,"lastCue",-12_001L);return focus;
    }
    private static String holdActualFramingCompletion(MainActivity a,String cue){
        apply(a,(Integer)field(a,"shootPoseGeneration"),cue,16,30);SpeechCoach coach=(SpeechCoach)field(a,"speech");
        assertNotNull(field(coach,"activeUtterance"));assertNotNull(field(coach,"afterSpeech"));assertNotNull(field(coach,"speechFailed"));
        // Retain the production completion gate while its queued denied-focus ID becomes stale.
        // Only utterance ownership is controlled; no successful audio backend is fabricated.
        String held="synthetic-held-framing-"+field(a,"countdownGeneration");set(coach,"activeUtterance",held);assertNull(field(coach,"speechTimeout"));return held;
    }
    private static void awaitMain(MainActivity a,java.util.function.BooleanSupplier condition,long timeoutMs){
        long until=SystemClock.elapsedRealtime()+timeoutMs;AtomicBoolean ready=new AtomicBoolean();
        do{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ready.set(condition.getAsBoolean()));if(ready.get())return;SystemClock.sleep(25);}while(SystemClock.elapsedRealtime()<until);
        fail("Synthetic pose-break transition did not settle");
    }
    private static String timer(MainActivity a){return ((TextView)field(a,"timerView")).getText().toString();}
    private static Switch switchView(View view,String label){
        if(view instanceof Switch&&label.contentEquals(((Switch)view).getText()))return (Switch)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Switch found=switchView(group.getChildAt(i),label);if(found!=null)return found;}}return null;
    }

    private static final class DenyFocus implements SpeechCoach.FocusControl {
        int requests,abandons;
        public int request(AudioFocusRequest request,AudioManager.OnAudioFocusChangeListener listener){
            assertEquals(Looper.getMainLooper(),Looper.myLooper());requests++;return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
        public void abandon(AudioFocusRequest request){abandons++;}
    }
    private static void installIdleCapture(MainActivity a){
        CaptureController old=(CaptureController)field(a,"capture");if(old!=null)old.close();
        CaptureController idle=new CaptureController(a,(PreviewView)field(a,"preview"),new CaptureController.Listener(){
            public void onReady(){fail("Synthetic test must never start preview");}
            public void onRecordingStarted(){fail("Synthetic test must never record");}
            public void onRecordingFinished(Uri uri,long duration){fail("Synthetic test must never create media");}
            public void onError(String message){fail("Synthetic test must never start camera work");}
        });
        set(a,"capture",idle);set(a,"session",true);set(a,"live",false);set(a,"countdown",false);
        assertIdleCapture(a);
    }
    private static void assertIdleCapture(MainActivity a){
        CaptureController capture=(CaptureController)field(a,"capture");assertNotNull(capture);assertFalse(capture.isRecording());
        assertEquals(false,field(capture,"previewRequested"));assertEquals(false,field(capture,"ready"));assertNull(field(capture,"provider"));assertNull(field(capture,"recording"));assertDenied(a);
    }
    private static void assertDenied(Context context){
        assertEquals("Camera must remain denied",PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals("Microphone must remain denied",PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
    }
    private static String framing(MainActivity a){return ((TextView)field(a,"framingView")).getText().toString();}
    private static String status(MainActivity a){return ((TextView)field(a,"status")).getText().toString();}
    private static Button button(View view,String label){
        if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    private static void apply(MainActivity a,int generation,String cue,int count,long latency){invoke(a,"applyPoseCue",new Class<?>[]{int.class,String.class,int.class,long.class},generation,cue,count,latency);}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void invoke(Object target,String name,Class<?>[] types,Object...args){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}catch(Exception e){throw new AssertionError(e);}}
}
