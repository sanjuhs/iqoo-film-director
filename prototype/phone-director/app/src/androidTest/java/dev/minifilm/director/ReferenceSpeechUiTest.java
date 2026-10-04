package dev.minifilm.director;

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
import android.widget.EditText;
import android.widget.TextView;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Future unlocked UI acceptance with synthetic cues only. No ASR, native model, audio or mic. */
@RunWith(AndroidJUnit4.class)
public final class ReferenceSpeechUiTest {
    private static final Uri SOURCE=Uri.parse("content://synthetic.reference/first");
    private static final Uri OTHER=Uri.parse("content://synthetic.reference/second");
    private static final String BRIEF="My synthetic fashion idea";
    private SharedPreferences preferences;
    private Map<String,?> original;
    private boolean changed;

    @Before public void requireUnlockedAndPreservePreferences() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        KeyguardManager lock=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        assertFalse("Unlock the phone before running dialog tests",lock!=null&&lock.isKeyguardLocked());
        assertDenied(context);
        preferences=context.getSharedPreferences("shoot",0);original=new HashMap<>(preferences.getAll());changed=true;
        assertTrue(preferences.edit().clear().putString("state",new JSONObject().put("brief",BRIEF)
                .put("referenceVideoUri",SOURCE.toString()).put("voice",false).toString()).commit());
    }
    @After public void restorePreferencesAndKeepPermissionsDenied(){
        if(changed){SharedPreferences.Editor editor=preferences.edit().clear();
            for(Map.Entry<String,?> entry:original.entrySet()){
                String k=entry.getKey();Object v=entry.getValue();
                if(v instanceof String)editor.putString(k,(String)v);
                else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
                else if(v instanceof Integer)editor.putInt(k,(Integer)v);
                else if(v instanceof Long)editor.putLong(k,(Long)v);
                else if(v instanceof Float)editor.putFloat(k,(Float)v);
                else if(v instanceof Set){@SuppressWarnings("unchecked") Set<String> strings=(Set<String>)v;editor.putStringSet(k,new HashSet<>(strings));}
                else throw new AssertionError("Unsupported preference type");
            }assertTrue(editor.commit());assertEquals(original,preferences.getAll());
        }assertDenied(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test(timeout=30_000) public void fullTimedDraftNeedsExplicitConfirmationAndDoesNotOverwriteBriefOrPlan(){
        ReferenceSpeechContext.Draft draft=draft();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            AlertDialog dialog=showReview(scenario,draft);
            scenario.onActivity(a->{
                Object shots=new ArrayList<>((java.util.List<?>)field(a,"shots"));Object source=field(a,"planSource");
                assertTrue("All source-timed words must be visible",containsText(dialog.getWindow().getDecorView(),draft.formatTimedText()));
                assertNull(field(a,"referenceSpeechContext"));words(dialog).setText("I explain how I chose the outfit, then show the jacket.");
                assertNull(field(a,"referenceSpeechContext"));assertEquals(BRIEF,field(a,"brief"));dialog.getButton(-1).performClick();assertFalse(dialog.isShowing());
                ReferenceSpeechContext.Reviewed reviewed=(ReferenceSpeechContext.Reviewed)field(a,"referenceSpeechContext");
                assertEquals("I explain how I chose the outfit, then show the jacket.",reviewed.text);
                assertEquals(BRIEF,field(a,"brief"));assertEquals(shots,field(a,"shots"));assertEquals(source,field(a,"planSource"));assertIdle(a);
            });
            scenario.recreate();scenario.onActivity(a->{assertEquals("I explain how I chose the outfit, then show the jacket.",((ReferenceSpeechContext.Reviewed)field(a,"referenceSpeechContext")).text);assertIdle(a);});
        }
    }

    @Test(timeout=30_000) public void oversizedContextAndCombinedPlanAreRejectedWithoutTruncatingOrReplacingPreviousContext(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            ReferenceSpeechContext.Reviewed previous=draft().reviewed("Previously confirmed synthetic context");
            scenario.onActivity(a->set(a,"referenceSpeechContext",previous));AlertDialog dialog=showReview(scenario,draft());
            scenario.onActivity(a->{
                EditText edit=words(dialog);String longText=repeat('x',211);edit.setText(longText);dialog.getButton(-1).performClick();
                assertTrue(dialog.isShowing());assertEquals(longText,edit.getText().toString());assertNotNull(edit.getError());assertSame(previous,field(a,"referenceSpeechContext"));
                edit.setText(" ");dialog.getButton(-1).performClick();assertTrue(dialog.isShowing());assertNotNull(edit.getError());
                set(a,"brief",repeat('b',470));edit.setText("Reviewed words that push the exact labelled composition beyond five hundred characters.");
                String corrected=edit.getText().toString();dialog.getButton(-1).performClick();
                assertTrue(dialog.isShowing());assertEquals(corrected,edit.getText().toString());assertNotNull(edit.getError());assertSame(previous,field(a,"referenceSpeechContext"));
                dialog.getButton(-2).performClick();assertSame(previous,field(a,"referenceSpeechContext"));set(a,"brief",BRIEF);assertIdle(a);
            });
        }
    }

    @Test(timeout=30_000) public void cancelBackgroundAndChangedReferenceInvalidateOldUseWhileSameReferenceKeepsConfirmation(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            ReferenceSpeechContext.Reviewed previous=draft().reviewed("Confirmed words to preserve");scenario.onActivity(a->set(a,"referenceSpeechContext",previous));
            AlertDialog canceled=showReview(scenario,draft());
            scenario.onActivity(a->{Button use=canceled.getButton(-1);words(canceled).setText("An unconfirmed replacement");invoke(a,"cancelReferenceSpeech",new Class<?>[0]);use.performClick();assertFalse(canceled.isShowing());assertSame(previous,field(a,"referenceSpeechContext"));});
            AlertDialog old=showReview(scenario,draft());AtomicReference<Button> stale=new AtomicReference<>();
            scenario.onActivity(a->{stale.set(old.getButton(-1));words(old).setText("Another unconfirmed replacement");});
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a->{assertFalse(old.isShowing());stale.get().performClick();assertEquals("Confirmed words to preserve",((ReferenceSpeechContext.Reviewed)field(a,"referenceSpeechContext")).text);});
            AlertDialog replaced=showReview(scenario,draft());
            scenario.onActivity(a->{
                Button oldUse=replaced.getButton(-1);words(replaced).setText("Words from the previous source");invoke(a,"changeReferenceSource",new Class<?>[]{Uri.class},OTHER);oldUse.performClick();
                assertNull(field(a,"referenceSpeechContext"));assertFalse(replaced.isShowing());assertEquals(OTHER,field(a,"referenceVideoUri"));assertIdle(a);invoke(a,"save",new Class<?>[0]);
            });
            scenario.recreate();scenario.onActivity(a->{assertNull(field(a,"referenceSpeechContext"));assertEquals(OTHER,field(a,"referenceVideoUri"));assertIdle(a);});
        }
    }

    @Test(timeout=30_000) public void staleTerminalCannotClearNewerBusyAndCancellationClosesOnlyBorrowedReader(){
        AtomicReference<ClipTranscriber> newer=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                ClipTranscriber old=(ClipTranscriber)field(a,"transcriber");
                ClipTranscriber replacement=new ClipTranscriber(a);newer.set(replacement);set(a,"transcriber",replacement);
                set(a,"referenceSpeechReader",old);int generation=(Integer)field(a,"referenceSpeechGeneration");
                invoke(a,"setBusy",new Class<?>[]{boolean.class},true);
                set(a,"referenceSpeechGeneration",generation+1);
                invoke(a,"finishReferenceSpeech",new Class<?>[]{ClipTranscriber.class,Uri.class,int.class,ReferenceSpeechContext.Draft.class,String.class},old,SOURCE,generation,draft(),null);
                assertEquals(true,field(a,"busy"));assertNull(field(a,"referenceSpeechDialog"));assertEquals(false,field(old,"closed"));
                invoke(a,"cancelReferenceSpeech",new Class<?>[0]);assertEquals(true,field(old,"closed"));assertSame(replacement,field(a,"transcriber"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals("Cleanup of a non-owning old reader must not clear newer busy",true,field(a,"busy"));assertNull(field(a,"referenceSpeechReader"));assertSame(newer.get(),field(a,"transcriber"));assertEquals(false,field(newer.get(),"closed"));invoke(a,"setBusy",new Class<?>[]{boolean.class},false);assertIdle(a);});
        }
    }

    @Test(timeout=30_000) public void savedContextEditsWithoutReadingSourceAndLiveCountsIncludeLabelsWithOverflowPreserved(){
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            ReferenceSpeechContext.Reviewed saved=draft().reviewed("Previously saved corrected context");
            AtomicReference<Object> reader=new AtomicReference<>(),planner=new AtomicReference<>(),shots=new AtomicReference<>();
            scenario.onActivity(a->{set(a,"referenceSpeechContext",saved);reader.set(field(a,"transcriber"));planner.set(field(a,"planner"));shots.set(new ArrayList<>((java.util.List<?>)field(a,"shots")));});
            AlertDialog dialog=showDialog(scenario,"editReferenceSpeechContext",new Class<?>[0]);
            scenario.onActivity(a->{
                assertNull("Editing saved words must not invent a persisted transcript",field(a,"referenceSpeechDraft"));
                assertTrue(containsText(dialog.getWindow().getDecorView(),"Saved corrected speech context. The original transcript and times were not saved. Edit these reviewed words without reading the video again."));
                EditText words=words(dialog);assertEquals(saved.text,words.getText().toString());String edited="Corrected saved words about choosing my outfit.";words.setText(edited);
                int count=ReferenceSpeechContext.composedLength(BRIEF,"",false,edited);assertTrue(containsText(dialog.getWindow().getDecorView(),edited.length()+" / 210 context characters · "+count+" / 500 combined plan characters, including labels"));
                set(a,"brief",repeat('b',470));words.setText(edited+" More corrected words.");String retained=words.getText().toString();
                dialog.getButton(-1).performClick();assertTrue(dialog.isShowing());assertNotNull(words.getError());assertEquals(retained,words.getText().toString());assertSame(saved,field(a,"referenceSpeechContext"));
                set(a,"brief",BRIEF);words.setText(edited);dialog.getButton(-1).performClick();assertFalse(dialog.isShowing());
                assertEquals(edited,((ReferenceSpeechContext.Reviewed)field(a,"referenceSpeechContext")).text);assertSame(reader.get(),field(a,"transcriber"));assertSame(planner.get(),field(a,"planner"));assertEquals(shots.get(),field(a,"shots"));assertEquals(BRIEF,field(a,"brief"));assertIdle(a);
            });
        }
    }

    @Test(timeout=30_000) public void replacedSourceRelinquishesOldReaderBusyOwnershipAndCancelReachesNewInspector(){
        AtomicReference<Object> inspector=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                ClipTranscriber old=(ClipTranscriber)field(a,"transcriber");set(a,"referenceSpeechReader",old);set(a,"referenceSpeechOwnsBusy",true);
                invoke(a,"setBusy",new Class<?>[]{boolean.class},true);
                invoke(a,"changeReferenceSource",new Class<?>[]{Uri.class},OTHER);
                assertEquals(false,field(a,"referenceSpeechOwnsBusy"));
                // Synthetic new inspector owns busy; neither another speech cancel nor the
                // old reader's queued cleanup may clear that unrelated operation.
                invoke(a,"setBusy",new Class<?>[]{boolean.class},true);inspector.set(field(a,"references"));
                invoke(a,"cancelReferenceSpeech",new Class<?>[0]);assertEquals(true,field(a,"busy"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{
                assertEquals(true,field(a,"busy"));assertNull(field(a,"referenceSpeechReader"));
                // Restore a draining resource-only old reader to exercise cancel routing;
                // all objects are idle seams, no source/provider/native work was started.
                ClipTranscriber resource=new ClipTranscriber(a);set(a,"referenceSpeechReader",resource);set(a,"referenceSpeechOwnsBusy",false);
                invoke(a,"cancelProcessing",new Class<?>[0]);assertEquals(true,field(inspector.get(),"closed"));assertEquals(true,field(resource,"closed"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();scenario.onActivity(ReferenceSpeechUiTest::assertIdle);
        }
    }

    @Test(timeout=30_000) public void replacedVisualSourceClosesOldEnginesRecyclesOwnedFrameAndRejectsOldReviewedUse(){
        AtomicReference<android.graphics.Bitmap> oldFrame=new AtomicReference<>();
        AtomicReference<Object> oldDecoder=new AtomicReference<>(),oldVision=new AtomicReference<>(),oldBoard=new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger generation=new java.util.concurrent.atomic.AtomicInteger();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{
                oldDecoder.set(field(a,"referenceFrames"));oldVision.set(field(a,"referenceVision"));oldBoard.set(field(a,"boardInspection"));generation.set((Integer)field(a,"referenceAnalysisGeneration"));
                android.graphics.Bitmap frame=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);oldFrame.set(frame);frame.eraseColor(android.graphics.Color.RED);
                set(a,"pendingReferenceFrame",frame);set(a,"pendingReferenceTimeMs",1234L);set(a,"pendingReferenceNotes","Synthetic old-source frame observations");
            });
            AlertDialog dialog=showDialog(scenario,"reviewReferenceNotes",new Class<?>[0]);
            scenario.onActivity(a->{
                Button oldUse=dialog.getButton(-1);words(dialog).setText("Unconfirmed old-source correction");invoke(a,"changeReferenceSource",new Class<?>[]{Uri.class},OTHER);
                assertFalse(dialog.isShowing());assertTrue(oldFrame.get().isRecycled());assertNull(field(a,"pendingReferenceFrame"));assertEquals("",field(a,"pendingReferenceNotes"));
                assertEquals(true,field(oldDecoder.get(),"closed"));assertEquals(true,field(oldVision.get(),"closed"));assertEquals(true,field(oldBoard.get(),"closed"));
                assertNotSame(oldDecoder.get(),field(a,"referenceFrames"));assertNotSame(oldVision.get(),field(a,"referenceVision"));assertNotSame(oldBoard.get(),field(a,"boardInspection"));assertEquals(false,call(a,"isCurrentReference",new Class<?>[]{Uri.class,int.class},SOURCE,generation.get()));
                set(a,"referenceSummary","New selected-source observations must survive");invoke(a,"setBusy",new Class<?>[]{boolean.class},true);oldUse.performClick();
                assertEquals("New selected-source observations must survive",field(a,"referenceSummary"));assertEquals("Retained old review must not clear the new operation",true,field(a,"busy"));invoke(a,"setBusy",new Class<?>[]{boolean.class},false);assertIdle(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();scenario.onActivity(a->{assertNull(field(a,"referenceNotesDialog"));assertTrue(oldFrame.get().isRecycled());assertEquals("New selected-source observations must survive",field(a,"referenceSummary"));assertIdle(a);});
        }
    }

    private static ReferenceSpeechContext.Draft draft(){return new ReferenceSpeechContext.Draft(SOURCE,Arrays.asList(
            new SubtitleCue(1001,5746,"Complete synthetic first sentence "+repeat('x',550)+" with its final words."),
            new SubtitleCue(6200,11000,"A second complete synthetic sentence that remains in the full draft.")),42);}
    private static AlertDialog showReview(ActivityScenario<MainActivity> scenario,ReferenceSpeechContext.Draft draft){
        AtomicReference<AlertDialog> shown=new AtomicReference<>();
        scenario.onActivity(a->{assertEquals(Lifecycle.State.RESUMED,a.getLifecycle().getCurrentState());shown.set(review(a,draft));});
        return awaitShown(scenario,shown);
    }
    private static AlertDialog showDialog(ActivityScenario<MainActivity> scenario,String method,Class<?>[] parameters){
        AtomicReference<AlertDialog> shown=new AtomicReference<>();
        scenario.onActivity(a->{assertEquals(Lifecycle.State.RESUMED,a.getLifecycle().getCurrentState());invoke(a,method,parameters);shown.set((AlertDialog)field(a,method.equals("reviewReferenceNotes")?"referenceNotesDialog":"referenceSpeechDialog"));assertNotNull(shown.get());});
        return awaitShown(scenario,shown);
    }
    private static AlertDialog awaitShown(ActivityScenario<MainActivity> scenario,AtomicReference<AlertDialog> shown){
        // OnShow installs the custom validation listener in a queued main-loop message.
        // Let that event complete before synthetic button actions, as real input does.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(a->{assertEquals(Lifecycle.State.RESUMED,a.getLifecycle().getCurrentState());assertTrue(shown.get().isShowing());});
        return shown.get();
    }
    private static AlertDialog review(MainActivity a,ReferenceSpeechContext.Draft draft){
        invoke(a,"reviewReferenceSpeech",new Class<?>[]{ReferenceSpeechContext.Draft.class,int.class},draft,(Integer)field(a,"referenceSpeechGeneration"));
        AlertDialog dialog=(AlertDialog)field(a,"referenceSpeechDialog");assertNotNull(dialog);assertTrue(dialog.isShowing());return dialog;
    }
    private static EditText words(AlertDialog dialog){EditText result=findEdit(dialog.getWindow().getDecorView());assertNotNull(result);return result;}
    private static EditText findEdit(View view){if(view instanceof EditText)return(EditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText result=findEdit(group.getChildAt(i));if(result!=null)return result;}}return null;}
    private static boolean containsText(View view,String text){if(view instanceof TextView&&text.equals(((TextView)view).getText().toString()))return true;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(containsText(g.getChildAt(i),text))return true;}return false;}
    private static String repeat(char c,int count){char[] chars=new char[count];Arrays.fill(chars,c);return new String(chars);}
    private static void assertIdle(MainActivity a){assertDenied(a);assertNull(field(a,"capture"));assertNull(field(a,"briefRecorder"));assertNull(field(a,"processingVoiceBrief"));assertNull(field(a,"referenceSpeechReader"));assertEquals(false,field(a,"busy"));assertEquals(0L,((Number)field(field(a,"planner"),"handle")).longValue());assertFalse(((SpeechCoach)field(a,"speech")).isListening());}
    private static void assertDenied(Context c){assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.RECORD_AUDIO));assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(Manifest.permission.CAMERA));}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(name,e);}}
    private static void set(Object target,String name,Object value){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(name,e);}}
    private static void invoke(Object target,String name,Class<?>[] types,Object...args){call(target,name,types,args);}
    private static Object call(Object target,String name,Class<?>[] types,Object...args){try{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(Exception e){throw new AssertionError(name,e);}}
}
