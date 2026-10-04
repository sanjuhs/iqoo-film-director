package dev.minifilm.director;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.view.PreviewView;
import androidx.camera.core.CameraSelector;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.util.*;

/** Pre-event research, 4 October 2026. All camera/microphone activity is explicit. */
public class MainActivity extends ComponentActivity {
    private final int BG=0xff101113, CARD=0xff1d1f22, FG=0xfff5f5ef, MUTED=0xffa8aaa4, LIME=0xffd9ff70;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,content,nav; private String reelTitle=""; private TextView status,cueView,timerView; private Button cancelProcessingButton, stopVoiceInputButton; private EditText briefField;
    private volatile int saveGeneration=0; private int demoGeneration=0; private int desiredLens=CameraSelector.LENS_FACING_FRONT; private TextView selectionSummary; private Button captureAction; private TextView cameraPlaceholder; private boolean guideSequence=false, sequenceActive=false; private PreviewView preview; private CaptureController capture; private PoseCoach pose; private SpeechCoach speech; private ReelExporter exporter;
    private AutoColorBalance autoColor; private ProjectPackager packager; private File pendingPack; private String pendingPackSnapshot=""; private Button packageSaveButton; private DocumentCopier documentCopier; private boolean savingDocument; private LocalPlanner planner; private ClipTranscriber transcriber; private ReferenceAnalyzer references; private String referenceSummary=""; private boolean busy=false, session=false, voice=true, autoStop=true, countdown=false, live=false;
    private long recordStart=0,lastCue=0; private int tab=0,shotIndex=0; private String brief="20-second streetwear reel. Show my jacket, a confident walk and the details.", style="Fashion", look="Clean", planSource="Editable starter plan";
    private final ArrayList<Shot> shots=new ArrayList<>(); private final ArrayList<Take> takes=new ArrayList<>(); private Uri lastVideo,lastEdit;
    private SpeechTrim pendingSpeechTrim; private Take pendingSpeechTrimTake;
    private VisionReference referenceVision; private ReferenceFrameDecoder referenceFrames; private Uri referenceVideoUri; private String pendingReferenceNotes=""; private long pendingReferenceTimeMs; private android.graphics.Bitmap pendingReferenceFrame;
    private ReferenceBoardInspection boardInspection; private ReferenceBoard pendingReferenceBoard, reviewedReferenceBoard; private long referenceDurationMs; private AlertDialog referenceBoardDialog,referenceNotesDialog;
    private boolean waitQuietPause=false, recordingTimedStop=false, recordingQuietStop=false; private QuietTailStopPolicy quietStopPolicy;
    private int shootPoseGeneration; private boolean spokenPreparationPaused; private Shot recordingShot; private int countdownGeneration=0; private boolean reviewAfterSave=false; private TextView framingView;
    private LocalBriefRecorder briefRecorder;
    private Button finishVoiceBriefButton;
    private File processingVoiceBrief;
    private ClipTranscriber voiceBriefReader;
    private int voiceBriefGeneration;
    private AlertDialog voiceBriefReview;
    private ReferenceSpeechContext.Reviewed referenceSpeechContext;
    private ReferenceSpeechContext.Draft referenceSpeechDraft;
    private ClipTranscriber referenceSpeechReader;
    private int referenceSpeechGeneration, referenceAnalysisGeneration;
    private boolean referenceSpeechOwnsBusy;
    private AlertDialog referenceSpeechDialog;
    private SubtitleBatch subtitleBatch, activeSubtitleBatch;
    private int subtitleBatchGeneration;
    private boolean subtitleBatchOwnsBusy, subtitleBatchCancelRequested;
    private TextView coverageSummary;
    private LinearLayout coverageRows;
    private Button nextMissingShotButton;
    private AlertDialog shotAssignmentDialog;
    private CaptureTakeStore captureTakeStore;
    private boolean destroying;
    @Override public void onCreate(Bundle state){super.onCreate(state);speech=new SpeechCoach(this);speech.setAudioInterruptionListener(this::pauseSpokenPreparation);exporter=new ReelExporter(this);packager=new ProjectPackager(this);documentCopier=new DocumentCopier(this);autoColor=new AutoColorBalance(this);planner=new LocalPlanner(this);transcriber=new ClipTranscriber(this);references=new ReferenceAnalyzer(this);referenceVision=new VisionReference(this);referenceFrames=new ReferenceFrameDecoder(this);boardInspection=new ReferenceBoardInspection(this);captureTakeStore=new CaptureTakeStore(this);restore();recoverSavedTakes(false);ExportRecovery.Result recovered=ExportRecovery.reconcile(this);if(recovered.videoUri!=null){lastVideo=recovered.videoUri;lastEdit=recovered.editListUri;save();}if(shots.isEmpty()){shots.addAll(ShotCoverage.freshPlan(DirectorEngine.plan(brief,style)));save();}render();if(!recovered.warning.isEmpty())status.setText(recovered.warning);else if(recovered.cleaned>0)status.setText("Interrupted export cleared. Original clips are safe.");}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(14),dp(18),dp(14));l.setBackground(bg(CARD));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);l.setLayoutParams(p);return l;}
    private Button button(String s,boolean primary,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?BG:FG);b.setBackground(bg(primary?LIME:0xff303338));b.setMinHeight(dp(50));b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.topMargin=dp(8);p.bottomMargin=dp(4);b.setLayoutParams(p);b.setOnClickListener(v->{if(referenceSpeechReader!=null&&!busy){toast("Reference speech is still stopping. Try again in a moment.");return;}if(busy){toast("Local processing is running. You can cancel it above.");return;}action.run();});return b;}
    private EditText input(String value,String hint){EditText e=new EditText(this);e.setText(value);e.setHint(hint);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(16);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(bg(0xff292c30));return e;}
    private void render(){
        ++shootPoseGeneration;
        dismissShotAssignments();coverageSummary=null;coverageRows=null;nextMissingShotButton=null;
        cancelReferenceSpeech();cancelVoiceBrief();speech.stopListening();stopVoiceInputButton=null;finishVoiceBriefButton=null;
        clearQuietStopPolicy();
        if(capture!=null){desiredLens=capture.getLensFacing();capture.stopPreview();capture.close();capture=null;}if(pose!=null){pose.close();pose=null;}live=false;
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(22),dp(22),dp(22),dp(10));androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(dp(22)+bars.left,dp(16)+bars.top,dp(22)+bars.right,dp(10)+bars.bottom);return insets;});
        TextView brand=text("mini film  /  your pocket director",15,LIME);brand.setTypeface(null,Typeface.BOLD);root.addView(brand);
        status=text(busy?"Working locally…":"Research preview · made locally on your phone",11,MUTED);root.addView(status);cancelProcessingButton=button("Cancel local processing",false,()->{});cancelProcessingButton.setOnClickListener(v->cancelProcessing());cancelProcessingButton.setVisibility(busy?View.VISIBLE:View.GONE);root.addView(cancelProcessingButton);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(0,dp(12),0,dp(18));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);String[] labels={"01  Brief","02  Direct","03  Assemble"};for(int i=0;i<3;i++){final int n=i;Button b=button(labels[i],tab==i,()->switchTab(n));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),0,dp(3),0);b.setLayoutParams(p);b.setTextSize(12);nav.addView(b);}root.addView(nav);setContentView(root);
        if(tab==0)briefPage();else if(tab==1){directPage();LinearLayout controls=new LinearLayout(this);Button record=button("Start camera",true,()->{if(live)startCountdown();else startShoot();}),stop=button("Stop take",false,()->stopTake());captureAction=record;stop.setTextColor(0xffff9e9e);for(Button b:new Button[]{record,stop}){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1);lp.setMargins(dp(3),dp(4),dp(3),dp(8));b.setLayoutParams(lp);controls.addView(b);}root.addView(controls,root.getChildCount()-1);}else editPage();
    }
    private void setBusy(boolean value){if(value)stopVoiceInput();busy=value;if(cancelProcessingButton!=null){cancelProcessingButton.setText(hasVoiceBriefWork()?"Cancel voice brief":"Cancel local processing");cancelProcessingButton.setVisibility(value?View.VISIBLE:View.GONE);}setEditorsEnabled(root,!value);}
    private void setEditorsEnabled(View view,boolean enabled){if(view==null)return;if(view instanceof EditText||view instanceof Spinner||view instanceof CheckBox||view instanceof Switch)view.setEnabled(enabled);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)setEditorsEnabled(group.getChildAt(i),enabled);}}
    private void cancelProcessing(){if(activeSubtitleBatch!=null&&subtitleBatchOwnsBusy){cancelSubtitleBatch();return;}if(referenceSpeechReader!=null&&referenceSpeechOwnsBusy){cancelReferenceSpeech();return;}if(hasVoiceBriefWork()){cancelVoiceBrief();return;}++referenceAnalysisGeneration;dismissReferenceNotesDialog();pendingSpeechTrim=null;pendingSpeechTrimTake=null;boolean interruptedDocument=savingDocument;savingDocument=false;++saveGeneration;++demoGeneration;documentCopier.cancel();exporter.cancel();packager.cancel();autoColor.close();autoColor=new AutoColorBalance(this);planner.close();transcriber.close();references.close();referenceVision.close();referenceFrames.close();boardInspection.close();closePendingReferenceBoard();pendingReferenceNotes="";clearPendingReferenceFrame();planner=new LocalPlanner(this);transcriber=new ClipTranscriber(this);references=new ReferenceAnalyzer(this);referenceVision=new VisionReference(this);referenceFrames=new ReferenceFrameDecoder(this);boardInspection=new ReferenceBoardInspection(this);setBusy(false);save();render();status.setText(interruptedDocument?"Save cancellation requested. The chosen document may be partial; use a new name when retrying. Original clips are safe.":"Processing stopped. Your clips and edits are safe.");}
    private void switchTab(int n){if(busy){toast("Wait for the current operation to finish.");return;}if(countdown || capture!=null&&capture.isRecording()){toast("Stop this take before changing screens.");return;}sequenceActive=false;cancelCountdown();speech.stopListening();speech.stop();session=false;if(n!=0)releasePlannerForMedia();if(n==2)recoverSavedTakes(false);tab=n;save();render();}
    // close() queues native cleanup after any work; it never blocks the UI or frees an active core directly.
    private void releasePlannerForMedia(){planner.close();planner=new LocalPlanner(this);}
    private void headline(String title,String sub){TextView h=text(title,32,FG);h.setTypeface(null,Typeface.BOLD);content.addView(h);content.addView(text(sub,15,MUTED));}
    private void briefPage(){
        headline("Make something\nworth watching.","One idea. A few good takes. Your reel, made here.");
        LinearLayout c=card();c.addView(text("WHAT ARE WE MAKING?",11,LIME));briefField=input(brief,"Describe your reel, outfit, story or reference trend");briefField.setMinLines(3);briefField.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(speech.isListening())stopVoiceInput();brief=s.toString();}public void afterTextChanged(android.text.Editable e){}});c.addView(briefField);c.addView(text("Describe a trend's beats or import your own reference clips. Automatic trend analysis is still research.",12,MUTED));
        Spinner modes=spinner(new String[]{"Fashion","Talking head","Product reveal","Introduction"},style,s->{style=s;save();});c.addView(modes);
        c.addView(button("Record a local voice brief",false,this::recordVoiceBrief));
        c.addView(text("Up to 45 seconds. Stop to review an English draft made on this phone. Audio is temporary; words need your review.",12,MUTED));
        finishVoiceBriefButton=button("Stop & review words",false,()->{});
        finishVoiceBriefButton.setOnClickListener(v->{if(briefRecorder!=null&&briefRecorder.isRecording())briefRecorder.finish();});
        c.addView(finishVoiceBriefButton);updateVoiceBriefControls();
        c.addView(button("Use phone dictation",false,()->listenBrief()));
        c.addView(text("Phone dictation needs Android's offline English speech model. If unavailable, use the local voice brief above or type.",12,MUTED));
        stopVoiceInputButton=button("Stop voice input",false,this::stopVoiceInput);c.addView(stopVoiceInputButton);updateVoiceInputControls();
        c.addView(button("Build my shot plan",true,()->generatePlan()));c.addView(button("Choose a reference reel",false,()->pickReference()));if(referenceVideoUri!=null){c.addView(button("Read reference speech locally",false,this::readReferenceSpeech));c.addView(text("English draft from this selected video. No microphone opens; review and shorten the context before planning.",12,MUTED));c.addView(button("Review three reference moments",false,()->chooseReferenceMoments()));c.addView(button("Describe a reference frame locally",false,()->chooseReferenceFrame()));}if(pendingReferenceBoard!=null)c.addView(button("Review reference moments draft",false,()->reviewReferenceBoard(pendingReferenceBoard)));else if(reviewedReferenceBoard!=null)c.addView(button("Edit reviewed reference moments",false,()->reviewReferenceBoard(reviewedReferenceBoard)));if(!pendingReferenceNotes.isEmpty())c.addView(button("Review reference frame notes",false,()->reviewReferenceNotes()));if(!referenceSummary.isEmpty()){c.addView(text("Reference notes: "+referenceSummary,12,MUTED));c.addView(button("Clear visual reference notes",false,()->{referenceSummary="";clearReferenceBoards();save();render();}));}if(referenceSpeechContext!=null&&referenceSpeechContext.matchesSource(referenceVideoUri)){c.addView(text("Reviewed reference speech: "+referenceSpeechContext.text,12,MUTED));c.addView(button("Edit reviewed speech context",false,this::editReferenceSpeechContext));c.addView(button("Clear reference speech context",false,()->{cancelReferenceSpeech();referenceSpeechContext=null;save();render();}));}content.addView(c);
        content.addView(text(planSource+" · "+shots.size()+" shots",12,LIME));
        content.addView(text("Review directions and captions before filming. AI can invent details or suggest an action that does not make sense. Edit each shot to make it yours.",13,MUTED));
        for(int i=0;i<shots.size();i++){final int index=i;Shot shot=shots.get(i);LinearLayout s=card();s.addView(text(String.format(Locale.US,"%02d  /  %s",i+1,shot.title),20,FG));s.addView(text(shot.instruction,14,MUTED));s.addView(text((shot.targetDurationMs/1000)+" sec · Phone camera",12,LIME));s.addView(button("Edit shot",false,()->editShot(index)));if(i>0)s.addView(button("Move earlier",false,()->{Collections.swap(shots,index,index-1);save();render();}));content.addView(s);}
        content.addView(button("Let's direct this reel →",true,()->{brief=briefField.getText().toString();switchTab(1);}));
        content.addView(button("Try a synthetic demo",false,()->loadDemo()));
    }
    private Spinner spinner(String[] values,String selected,java.util.function.Consumer<String> action){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values){@Override public View getView(int p,View c,android.view.ViewGroup g){TextView v=(TextView)super.getView(p,c,g);v.setTextColor(FG);v.setPadding(dp(12),dp(12),dp(12),dp(12));return v;}};s.setAdapter(a);s.setSelection(Math.max(0,Arrays.asList(values).indexOf(selected)));s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){action.accept(values[n]);}});return s;}
    private void generatePlan(){stopVoiceInput();brief=briefField.getText().toString().trim();if(brief.isEmpty()){toast("Add a brief first.");return;}if(takes.size()>0){new AlertDialog.Builder(this).setTitle("Replace shot plan?").setMessage("Your existing takes stay in Assemble. The new plan starts from shot one.").setPositiveButton("Replace plan",(d,w)->runPlanner()).setNegativeButton("Keep plan",null).show();}else runPlanner();}
    private void replacePlan(List<Shot> proposed,String source){
        List<Shot> fresh=ShotCoverage.freshPlan(proposed);
        shots.clear();shots.addAll(fresh);shotIndex=0;planSource=source;save();render();
    }
    private void runPlanner(){if(VisionReference.isRunning()){toast("The earlier frame review is still stopping. Try again in a moment.");return;}if(busy||referenceSpeechReader!=null)return;String planBrief;try{ReferenceSpeechContext.Reviewed context=referenceSpeechContext!=null&&referenceSpeechContext.matchesSource(referenceVideoUri)?referenceSpeechContext:null;planBrief=ReferenceSpeechContext.composePlanBrief(brief,referenceSummary,reviewedReferenceBoard!=null,context);}catch(IllegalArgumentException invalid){toast(invalid.getMessage());return;}speech.stopListening();speech.stop();save();if(!planner.isModelAvailable()){replacePlan(DirectorEngine.plan(brief,style),"Editable template · local LLM not installed");return;}setBusy(true);status.setText("Local model is creating your shot plan…");planner.generate(planBrief,style,new LocalPlanner.Listener(){public void onPlan(List<Shot> p,long ms,String model){setBusy(false);replacePlan(p,"Local AI · "+model+" · "+String.format(Locale.US,"%.1fs",ms/1000.0));}public void onError(String error){setBusy(false);toast(error);status.setText("Plan was not applied. Review your brief and confirmed reference notes.");if(!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;new AlertDialog.Builder(MainActivity.this).setTitle("Keep creating").setMessage(error).setPositiveButton("Use starter plan",(d,w)->{replacePlan(DirectorEngine.plan(brief,style),"Editable template · AI failed");}).setNegativeButton("Keep current",null).show();}});}
    private boolean hasVoiceBriefWork() {
        return processingVoiceBrief != null || briefRecorder != null && briefRecorder.isRecording();
    }
    private void updateVoiceBriefControls() {
        if (finishVoiceBriefButton != null) {
            boolean recording = briefRecorder != null && briefRecorder.isRecording();
            finishVoiceBriefButton.setEnabled(recording);
            finishVoiceBriefButton.setVisibility(recording ? View.VISIBLE : View.GONE);
        }
    }
    private boolean ensureVoiceRecorderReleased() {
        if (referenceSpeechReader != null) { status.setText("Reference speech is still stopping. Wait before starting another microphone session."); return false; }
        if (briefRecorder == null) return true;
        if (briefRecorder.isRecording()) { status.setText("Finish or cancel your voice brief before starting another microphone session."); return false; }
        briefRecorder.close();
        if (briefRecorder.hasUnreleasedResources()) { status.setText("Voice recorder release could not be confirmed. Close the app and check Android's microphone indicator before retrying."); return false; }
        return true;
    }
    private boolean completeVoiceBriefIfCurrent(int generation) {
        if (generation != voiceBriefGeneration || destroying) return false;
        setBusy(false); return true;
    }
    private void recordVoiceBrief() {
        if (busy || tab != 0 || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        if (!ensureVoiceRecorderReleased()) return;
        if (!transcriber.isModelAvailable()) { status.setText("The local English speech model is unavailable. Type your brief instead."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44); return;
        }
        releasePlannerForMedia(); stopVoiceInput(); speech.stop();
        final int generation = ++voiceBriefGeneration;
        briefRecorder = new LocalBriefRecorder(this, new LocalBriefRecorder.Listener() {
            public void onStarted() {
                if (generation != voiceBriefGeneration) return;
                setBusy(true); updateVoiceBriefControls();
                status.setText("● Voice brief recording · tap Stop & review words · 45-second limit");
            }
            public void onReady(File file) {
                updateVoiceBriefControls();
                if (!completeVoiceBriefIfCurrent(generation) || tab != 0 || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    LocalBriefRecorder.discard(file); return;
                }
                transcribeVoiceBrief(file, generation);
            }
            public void onError(String message) {
                if (generation != voiceBriefGeneration) return;
                setBusy(false); updateVoiceBriefControls(); status.setText(message);
            }
            public void onCanceled() {
                if (generation != voiceBriefGeneration) return;
                setBusy(false); updateVoiceBriefControls(); status.setText("Voice brief canceled. Your typed brief is kept.");
            }
        });
        briefRecorder.start();
    }
    private void transcribeVoiceBrief(final File file, final int generation) {
        processingVoiceBrief = file; voiceBriefReader = transcriber;
        setBusy(true); status.setText("Making an English draft locally… review it before using it");
        transcriber.transcribe(Uri.fromFile(file), new ClipTranscriber.Listener() {
            private boolean releaseAndCurrent() {
                // These callbacks follow decoder release. Cancellation uses the old worker's
                // resource hook instead, since close suppresses its creator callbacks.
                LocalBriefRecorder.discard(file);
                if (processingVoiceBrief == file) { processingVoiceBrief = null; voiceBriefReader = null; }
                return completeVoiceBriefIfCurrent(generation) && tab == 0
                        && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);
            }
            public void onComplete(List<SubtitleCue> cues, long elapsedMs) {
                if (!releaseAndCurrent()) return;
                StringBuilder words = new StringBuilder();
                for (SubtitleCue cue : cues) {
                    if (words.length() > 0) words.append(' ');
                    words.append(cue.text.trim());
                }
                String draft = words.toString().trim();
                if (draft.isEmpty()) { status.setText("No words were recognized. Record a clearer brief or type it."); return; }
                reviewVoiceBrief(draft, generation);
            }
            public void onError(String message) {
                if (!releaseAndCurrent()) return;
                status.setText(message);
            }
        });
    }
    private void reviewVoiceBrief(String draft, int generation) {
        EditText words = input(draft,"Correct the words before using this brief"); words.setMinLines(3);
        if (draft.length() > 500) words.setError("Shorten this draft to 500 characters before using it.");
        LinearLayout form = column(); form.setPadding(dp(20),dp(10),dp(20),dp(10));
        form.addView(text("English speech draft · check the words and keep it within 500 characters. Using it does not start planning or filming.",13,MUTED)); form.addView(words);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Review your voice brief").setView(form)
                .setPositiveButton("Use this brief",null).setNegativeButton("Keep typed brief",null).create();
        voiceBriefReview = dialog;
        dialog.setOnDismissListener(d -> { if (voiceBriefReview == dialog) voiceBriefReview = null; });
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String edited = words.getText().toString().trim();
            if (edited.isEmpty() || edited.length() > 500) { words.setError("Use 1–500 characters."); return; }
            if (generation != voiceBriefGeneration || destroying || tab != 0 || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) { dialog.dismiss(); return; }
            brief = edited; if (briefField != null) briefField.setText(edited); save();
            status.setText("Voice brief applied. Review it, then build your shot plan."); dialog.dismiss();
        }));
        status.setText("Review the English draft before applying it"); dialog.show();
    }
    private void cancelVoiceBrief() {
        boolean hadWork = hasVoiceBriefWork(); ++voiceBriefGeneration;
        if (voiceBriefReview != null) { voiceBriefReview.dismiss(); voiceBriefReview = null; }
        if (briefRecorder != null) briefRecorder.cancel();
        final File file = processingVoiceBrief;
        final ClipTranscriber reader = voiceBriefReader;
        processingVoiceBrief = null; voiceBriefReader = null;
        if (file != null && reader != null) {
            reader.closeWhenIdle(() -> LocalBriefRecorder.discard(file));
            if (transcriber == reader && !destroying) transcriber = new ClipTranscriber(this);
        }
        if (hadWork) setBusy(false);
        if (briefRecorder != null && briefRecorder.hasUnreleasedResources()) {
            if (status != null) status.setText("Voice recorder release could not be confirmed. Close the app and check Android's microphone indicator before retrying.");
        } else if (hadWork && status != null) status.setText("Voice brief canceled. Your typed brief is kept.");
        updateVoiceBriefControls();
    }
    private void updateVoiceInputControls(){if(stopVoiceInputButton!=null)stopVoiceInputButton.setEnabled(speech.isListening());}
    private void stopVoiceInput(){boolean wasListening=speech.isListening();speech.stopListening();updateVoiceInputControls();if(wasListening&&tab==0&&status!=null)status.setText("Voice input stopped. Your typed brief is kept.");}
    private void listenBrief(){
        if(busy||tab!=0||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        if(!ensureVoiceRecorderReleased())return;
        stopVoiceInput();
        if(!speech.isOfflineRecognitionAvailable()){status.setText("On-device speech recognition isn't available. Type your brief instead.");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42);return;}
        status.setText("Listening locally… tap Stop voice input to cancel");
        speech.listen(new SpeechCoach.Listener(){
            public void onText(String t){updateVoiceInputControls();if(busy||tab!=0||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;brief=t;if(briefField!=null)briefField.setText(t);status.setText("Voice brief ready for your review");save();}
            public void onError(String e){updateVoiceInputControls();if(busy||tab!=0||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;status.setText(e);toast(e);}
        });
        updateVoiceInputControls();
    }
    private void editShot(int i){Shot s=shots.get(i);LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(10));EditText name=input(s.title,"Title"),cue=input(s.instruction,"Direction"),caption=input(s.caption,"Manual caption"),duration=input(""+(s.targetDurationMs/1000),"Seconds");duration.setInputType(2);form.addView(name);form.addView(cue);form.addView(caption);form.addView(duration);ScrollView scroll=new ScrollView(this);scroll.addView(form);new AlertDialog.Builder(this).setTitle("Your shot").setView(scroll).setPositiveButton("Save",(d,w)->{long seconds=6;try{seconds=Long.parseLong(duration.getText().toString());}catch(Exception ignored){}shots.set(i,new Shot(s.id,name.getText().toString(),cue.getText().toString(),caption.getText().toString(),Math.max(2,Math.min(60,seconds))*1000));save();render();}).setNegativeButton("Cancel",null).show();}
    private void directPage(){
        shotIndex=Math.min(shotIndex,Math.max(0,shots.size()-1));Shot shot=shots.get(shotIndex);
        headline("Your next good shot.",String.format(Locale.US,"SHOT %02d OF %02d  ·  %s",shotIndex+1,shots.size(),shot.title));
        FrameLayout frame=new FrameLayout(this);frame.setBackground(bg(CARD));preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);int viewfinderHeight=Math.min(dp(390),(int)(getResources().getDisplayMetrics().heightPixels*.42f));FrameLayout.LayoutParams viewfinderBounds=new FrameLayout.LayoutParams((int)(viewfinderHeight*9f/16f),viewfinderHeight,Gravity.CENTER);frame.addView(preview,viewfinderBounds);
        TextView placeholder=text("Mount your phone vertically.\nLeave it steady for the shoot.\nTap Start camera to begin.",16,MUTED);cameraPlaceholder=placeholder;placeholder.setGravity(Gravity.CENTER);frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        content.addView(frame,new LinearLayout.LayoutParams(-1,viewfinderHeight));
        LinearLayout c=card();c.setPadding(dp(18),dp(16),dp(18),dp(16));cueView=text(shot.instruction,21,FG);c.addView(cueView);framingView=text(isPoseShot()?"Framing coach waits for your shoot to start":"Person-framing advice is off for this object shot. Follow your scene cue.",13,MUTED);c.addView(framingView);timerView=text("Ready when you are · "+shot.targetDurationMs/1000+" second take",13,LIME);c.addView(timerView);
        Switch v=new Switch(this);v.setText("Spoken direction / earbuds");v.setTextColor(FG);v.setChecked(voice);v.setOnCheckedChangeListener((b,on)->{voice=on;if(!on){if(countdown)stopTake();else speech.stop();}save();});c.addView(v);
        Switch sequence=new Switch(this);sequence.setText("Guide the full shot sequence");sequence.setTextColor(FG);sequence.setChecked(guideSequence);sequence.setOnCheckedChangeListener((b,on)->{guideSequence=on;if(!on){boolean preparing=countdown&&(capture==null||!capture.isRecording());sequenceActive=false;cancelCountdown();speech.stop();if(pose!=null)pose.setEnabled(session&&isPoseShot());if(preparing)showPreparationCanceled();}save();});c.addView(sequence);c.addView(text("When enabled, Record speaks each direction fully, gives you an 8-second pose break, then counts down before recording. Stop ends the sequence.",12,MUTED));
        Switch a=new Switch(this);a.setText("Stop at planned shot length");a.setTextColor(FG);a.setChecked(autoStop);a.setOnCheckedChangeListener((b,on)->autoStop=on);c.addView(a);
        Switch quiet=new Switch(this);quiet.setText("Wait for a quiet pause · experimental");quiet.setTextColor(FG);quiet.setChecked(waitQuietPause);quiet.setOnCheckedChangeListener((b,on)->waitQuietPause=on);c.addView(quiet);
        c.addView(text("For a talking take, this may add up to 8 seconds after its planned stop. Sound levels can mistake a pause for the end; the hard limit can still cut speech. Stop settings apply to the next take. Off by default.",12,MUTED));
        c.addView(text("Cues use your current phone audio output. Pair AirPods in Android settings. Cues stay quiet during recording.",12,MUTED));

        c.addView(button("Hear the direction",false,()->{if(!countdown&&(capture==null||!capture.isRecording())){spokenPreparationPaused=false;speech.speak(shot.instruction);}}));
        c.addView(button("Switch front / back",false,()->{if(countdown||capture!=null&&capture.isRecording()){toast("Stop this take first.");return;}if(capture!=null&&capture.isPreviewPending()){toast("Wait for the camera to finish opening before switching lenses.");return;}if(capture!=null&&live){desiredLens=capture.getLensFacing()==CameraSelector.LENS_FACING_FRONT?CameraSelector.LENS_FACING_BACK:CameraSelector.LENS_FACING_FRONT;live=false;startCamera();}else{desiredLens=desiredLens==CameraSelector.LENS_FACING_FRONT?CameraSelector.LENS_FACING_BACK:CameraSelector.LENS_FACING_FRONT;save();status.setText((desiredLens==CameraSelector.LENS_FACING_BACK?"Back":"Front")+" camera selected · tap Start camera");}}));c.addView(button("Next shot →",false,()->{if(countdown||capture!=null&&capture.isRecording()){toast("Stop this take first.");return;}shotIndex=(shotIndex+1)%shots.size();boolean reopen=live;session=false;save();render();if(reopen){cameraPlaceholder.setVisibility(View.GONE);startCamera();}}));c.addView(button("End shoot & review",false,()->{stopTake();++shootPoseGeneration;session=false;if(capture!=null)capture.stopPreview();if(capture==null||!capture.isRecording())switchTab(2);else{reviewAfterSave=true;status.setText("Saving your last take…");}}));content.addView(c);
        content.addView(text("Pose model observes only while this shoot is open. Camera and microphone stop when you leave the app. Drone clips can be imported; aircraft control is not connected.",12,MUTED));
    }
    private void startShoot(){if(!ensureVoiceRecorderReleased())return;if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},41);return;}cameraPlaceholder.setVisibility(View.GONE);startCamera();}
    private void startCamera(){if(live)return;if(capture!=null&&capture.isRecording()){toast("The previous take is still saving. Try again in a moment.");return;}if(!ensureVoiceRecorderReleased())return;releasePlannerForMedia();final int poseGeneration=++shootPoseGeneration;if(capture!=null){capture.close();capture=null;}if(pose!=null){pose.close();pose=null;}session=true;speech.stopListening();pose=new PoseCoach((cue,count,latency)->runOnUiThread(()->applyPoseCue(poseGeneration,cue,count,latency)));pose.setMode(style);pose.setEnabled(isPoseShot());capture=new CaptureController(this,preview,new CaptureController.Listener(){public void onReady(){live=true;desiredLens=capture.getLensFacing();save();if(captureAction!=null)captureAction.setText("Record · 3 sec");status.setText("Shoot active · "+speech.describeAudioRoute());if(!capture.isAnalysisAvailable())framingView.setText("Live pose analysis unavailable on this camera. Follow your shot cue and check the preview.");else if(!isPoseShot())framingView.setText("Person-framing advice is off for this object shot. Follow your scene cue.");if(sequenceActive)prepareSequenceShot();}public void onRecordingStarted(){countdown=false;speech.stop();recordStart=SystemClock.elapsedRealtime();quietStopPolicy=recordingQuietStop?new QuietTailStopPolicy(capture.getGeneration(),capture.getRecordingId(),recordingShot.targetDurationMs,recordStart):null;timerView.setText("● Recording — cues quiet");handler.post(tick);}public void onAudioStatus(CaptureController.AudioStatus audio){if(quietStopPolicy!=null)quietStopPolicy.observe(audio.generation,audio.recordingId,audio.recordedMs,audio.receivedElapsedMs,audio.amplitude,audio.eligible);}
        public void onRecordingFinished(Uri uri,long duration){clearQuietStopPolicy();handler.removeCallbacks(tick);if(pose!=null)pose.setEnabled(session&&isPoseShot());if(duration>300){Shot s=recordingShot!=null?recordingShot:shots.get(shotIndex);takes.add(new Take(uri,s.id,s.title,s.caption,duration));save();}timerView.setText("Take saved · "+String.format(Locale.US,"%.1f",duration/1000.0)+" sec");status.setText("Review your take in Assemble");if(reviewAfterSave){reviewAfterSave=false;switchTab(2);}else if(sequenceActive){if(shotIndex+1<shots.size()){shotIndex++;save();render();cameraPlaceholder.setVisibility(View.GONE);startCamera();}else{sequenceActive=false;session=false;switchTab(2);status.setText("Your shot sequence is ready to review.");}}}public void onError(String e){++shootPoseGeneration;session=false;clearQuietStopPolicy();sequenceActive=false;cancelCountdown();speech.stop();handler.removeCallbacks(tick);live=false;if(captureAction!=null)captureAction.setText("Start camera");toast(e);status.setText(e);if(pose!=null)pose.setEnabled(false);if(reviewAfterSave){reviewAfterSave=false;switchTab(2);}}});capture.setLensFacing(desiredLens);capture.setAnalyzer(pose);capture.startPreview();}
    private void applyPoseCue(int generation,String cue,int count,long latency){
        // A worker result can already be queued when its previous pose coach is closed.
        if(generation!=shootPoseGeneration||destroying||!session||tab!=1)return;
        if(capture==null||capture.isRecording()||countdown)return;
        if(!spokenPreparationPaused)status.setText("On-device pose · "+count+" visible points · "+latency+" ms");
        framingView.setText(cue);
        if(voice&&!spokenPreparationPaused&&!speech.hasSpeechWork()&&SystemClock.elapsedRealtime()-lastCue>12000){
            lastCue=SystemClock.elapsedRealtime();speech.speak(cue);
        }
    }
    private void clearQuietStopPolicy(){if(quietStopPolicy!=null){quietStopPolicy.cancel();quietStopPolicy=null;}}
    private final Runnable tick=new Runnable(){public void run(){
        if(capture==null||!capture.isRecording()||recordingShot==null)return;
        long now=SystemClock.elapsedRealtime(),elapsed=now-recordStart;
        boolean waiting=quietStopPolicy!=null&&elapsed>=recordingShot.targetDurationMs;
        String recordingStatus=waiting?"Waiting briefly for a quiet pause · Stop ends the take":String.format(Locale.US,"● %.1fs / %ds · quiet on set",elapsed/1000.0,recordingShot.targetDurationMs/1000);
        timerView.setText(recordingStatus);status.setText(recordingStatus);
        if(quietStopPolicy!=null){
            QuietTailStopPolicy.Decision decision=quietStopPolicy.evaluate(now);
            if(decision!=QuietTailStopPolicy.Decision.CONTINUE){status.setText(decision==QuietTailStopPolicy.Decision.QUIET_TAIL?"Quiet pause observed · review the saved take":"Time limit reached · review your speech");capture.stopRecording();return;}
        }else if(recordingTimedStop&&elapsed>=recordingShot.targetDurationMs){capture.stopRecording();return;}
        handler.postDelayed(this,100);
    }};
    private boolean isPoseShot(){if(style.toLowerCase(Locale.ROOT).contains("product"))return false;String title=shots.get(shotIndex).title.toLowerCase(Locale.ROOT);return !title.contains("cutaway")&&!title.contains("world")&&!title.contains("detail")&&!title.contains("fabric")&&!title.contains("product")&&!title.contains("texture")&&!title.contains("sleeve");}
    private void startCountdown(){if(busy){toast("Wait for local processing to finish.");return;}if(!live||capture==null){toast("Start the shoot camera first.");return;}if(countdown||capture.isRecording())return;spokenPreparationPaused=false;if(guideSequence&&!sequenceActive){sequenceActive=true;prepareSequenceShot();return;}speech.stop();speech.stopListening();pose.setEnabled(false);countdown=true;int generation=++countdownGeneration;recordingShot=shots.get(shotIndex);cueView.setText(recordingShot.instruction);count(3,generation);}
    private void prepareSequenceShot(){
        speech.stopListening();pose.setEnabled(false);countdown=true;
        int generation=++countdownGeneration;recordingShot=shots.get(shotIndex);
        cueView.setText(recordingShot.instruction);
        framingView.setText("Follow this direction. Your pose break starts after the cue.");
        status.setText("Direction first · Stop ends the sequence");timerView.setText("Hear your next scene");
        if(voice)speech.speakThen(recordingShot.instruction,()->beginPoseBreak(generation),()->spokenCueFailed(generation));
        else beginPoseBreak(generation);
    }
    private boolean countdownCurrent(int generation){return generation==countdownGeneration&&countdown&&session;}
    private void beginPoseBreak(int generation){
        if(!countdownCurrent(generation)||!sequenceActive)return;
        status.setText("Pose break · 8 seconds · Stop ends the sequence");timerView.setText("Get ready for your next take");
        handler.postDelayed(()->{if(countdownCurrent(generation)&&sequenceActive)count(3,generation);},8000);
    }
    private void pauseSpokenPreparation(){
        spokenPreparationPaused=true;sequenceActive=false;
        if(countdown&&(capture==null||!capture.isRecording()))stopTake();
        else speech.stop();
        save();
        if(status!=null)status.setText("Direction paused after an audio interruption. Check your earbuds, then tap Hear the direction or Record to resume.");
    }
    private void spokenCueFailed(int generation){
        if(!countdownCurrent(generation))return;
        spokenPreparationPaused=true;stopTake();
        timerView.setText("Paused before recording");
        status.setText("Spoken preparation paused. Check your offline voice and audio output, then tap Record again or turn spoken direction off.");
    }
    private void count(int n,int generation){
        if(!countdownCurrent(generation))return;
        if(n==0){speech.stop();clearQuietStopPolicy();recordingTimedStop=autoStop||sequenceActive;recordingQuietStop=recordingTimedStop&&waitQuietPause;capture.startRecording(recordingShot);return;}
        timerView.setText("Starting in "+n+"…");status.setText("Starting in "+n+"… tap Stop to cancel");
        long earliest=SystemClock.elapsedRealtime()+1000;
        if(voice)speech.speakThen(Integer.toString(n),()->{
            if(countdownCurrent(generation))handler.postDelayed(()->count(n-1,generation),Math.max(350,earliest-SystemClock.elapsedRealtime()));
        },()->spokenCueFailed(generation));
        else handler.postDelayed(()->count(n-1,generation),1000);
    }
    private void cancelCountdown(){countdown=false;++countdownGeneration;}
    private void showPreparationCanceled(){if(timerView!=null)timerView.setText("Ready when you are · "+shots.get(shotIndex).targetDurationMs/1000+" second take");if(status!=null)status.setText("Start canceled · tap Record when you're ready.");}
    private void stopTake(){boolean preparing=countdown&&(capture==null||!capture.isRecording());clearQuietStopPolicy();sequenceActive=false;cancelCountdown();handler.removeCallbacks(tick);if(capture!=null)capture.stopRecording();speech.stopListening();speech.stop();if(pose!=null)pose.setEnabled(session&&isPoseShot());if(preparing)showPreparationCanceled();}
    private void editPage(){
        headline("Put your story together.","Choose takes, tighten the cuts, add your words.");addCoveragePanel();LinearLayout c=card();c.addView(text("LOOK & FEEL",11,LIME));EditText title=input(reelTitle,"Optional reel title");title.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){reelTitle=s.toString();save();}public void afterTextChanged(android.text.Editable e){}});c.addView(title);c.addView(spinner(new String[]{"Clean","Warm","Cinematic","Auto balance"},look,s->{look=s;save();}));c.addView(text("Local presets or automatic neutral/exposure balance · vertical 720p · up to 12 cuts / 3 minutes. Originals stay intact. English speech can be transcribed offline.",12,MUTED));c.addView(button("Import clips from my phone",false,()->pickClips()));c.addView(button("Find saved phone takes",false,()->recoverSavedTakes(true)));c.addView(text("Finds readable phone takes saved after an interruption. Recovered takes stay unselected until you review them; incomplete files are kept.",12,MUTED));c.addView(button("Try synthetic demo clips",false,()->loadDemo()));c.addView(button("Draft missing subtitles for selected takes",false,this::draftMissingSubtitles));c.addView(text("Reads selected clips locally and keeps existing subtitle edits. New English drafts need word and timing review; no microphone opens.",12,MUTED));c.addView(button("Select no takes",false,()->{for(Take t:takes)t.selected=false;save();render();status.setText("All takes kept. Select the ones for your next reel.");}));content.addView(c);
        long total=0;for(int i=0;i<takes.size();i++){Take t=takes.get(i);final int index=i;if(t.selected)total+=t.outMs-t.inMs;LinearLayout row=card();CheckBox selected=new CheckBox(this);selected.setText(t.title);selected.setTextColor(FG);selected.setTextSize(19);selected.setChecked(t.selected);selected.setOnCheckedChangeListener((b,on)->{t.selected=on;save();updateSelectionSummary();updateCoverageSummary();});row.addView(selected);row.addView(text(String.format(Locale.US,"%.1f → %.1fs  ·  %s",t.inMs/1000.0,t.outMs/1000.0,t.caption.isEmpty()?"No caption":t.caption),13,MUTED));row.addView(text(shotAssignmentDescription(t),12,LIME));row.addView(button("Assign take to plan shots",false,()->reviewShotAssignments(t)));row.addView(button("Trim & typography",false,()->editTake(index)));row.addView(button("Generate offline subtitles",false,()->transcribeTake(index)));row.addView(button(pendingSpeechTrimTake==t&&pendingSpeechTrim!=null?"Review suggested speech cut":"Suggest a tighter talking cut",false,()->{if(pendingSpeechTrimTake==t&&pendingSpeechTrim!=null)reviewSpeechTrim(t,pendingSpeechTrim);else suggestSpeechTrim(t);}));if(!t.subtitles.isEmpty()){row.addView(text(t.subtitles.size()+" timed subtitle drafts · review before export",12,LIME));row.addView(button("Review subtitle words & timing",false,()->reviewSubtitles(index)));}row.addView(button("Preview take",false,()->previewVideo(t.uri,t.inMs,t.outMs)));if(i>0)row.addView(button("Move earlier",false,()->{Collections.swap(takes,index,index-1);save();render();}));content.addView(row);}
        selectionSummary=text("",13,LIME);updateSelectionSummary();content.addView(selectionSummary);content.addView(button("Export my reel",true,()->export()));packageSaveButton=button(pendingPack==null?"Save clips + edits for my laptop":"Save ready edit package",false,()->{if(pendingPack==null)packageProject();else requestPackDocument();});content.addView(packageSaveButton);if(pendingPack!=null)content.addView(button("Create a fresh edit package",false,()->packageProject()));content.addView(text("Portable ZIP includes the selected original clips and editable cut list. Choose a local folder; up to 512 MB of originals. Transfer through Office Kit separately.",12,MUTED));if(lastVideo!=null){content.addView(button("Play last exported reel",false,()->previewVideo(lastVideo)));content.addView(button("Share last exported reel",false,()->share(lastVideo,"video/mp4")));}if(lastEdit!=null){content.addView(button("Save last exported cut list to Files",false,()->saveEditDocument()));content.addView(button("Share last exported cut list",false,()->share(lastEdit,"application/json")));}
    }
    private void updateSelectionSummary(){if(selectionSummary==null)return;long total=0;int selected=0;for(Take take:takes)if(take.selected){total+=take.outMs-take.inMs;selected++;}selectionSummary.setText(selected+" selected / "+takes.size()+" takes · "+String.format(Locale.US,"%.1fs",total/1000.0)+" · up to 12 cuts / 3 min");}
    private void addCoveragePanel(){
        LinearLayout panel=card();panel.addView(text("SHOT ASSIGNMENTS · YOUR REVIEW",11,LIME));
        panel.addView(text("Review each take, then assign it to one or more plan shots. Assignments track your choices; review the footage yourself.",12,MUTED));
        coverageSummary=text("",14,FG);panel.addView(coverageSummary);
        coverageRows=column();panel.addView(coverageRows);
        nextMissingShotButton=button("Direct next missing shot",false,this::directNextMissingShot);
        panel.addView(nextMissingShotButton);content.addView(panel);updateCoverageSummary();
    }
    private void updateCoverageSummary(){
        if(coverageSummary==null||coverageRows==null)return;
        ShotCoverage.Result result=ShotCoverage.evaluate(shots,takes);
        coverageSummary.setText(result.coveredCount+" / "+result.totalCount+" shots have assigned selected takes · "
                +(result.totalCount-result.coveredCount)+" missing");
        coverageRows.removeAllViews();
        for(ShotCoverage.Entry entry:result.entries){
            int count=entry.reviewedSelectedTakeCount;
            coverageRows.addView(text(String.format(Locale.US,"%02d  %s · %d assigned selected take%s",
                    entry.shotIndex+1,shots.get(entry.shotIndex).title,count,count==1?"":"s"),12,MUTED));
        }
        if(nextMissingShotButton!=null)nextMissingShotButton.setEnabled(result.nextMissingIndex>=0);
    }
    private void directNextMissingShot(){
        if(busy||destroying||countdown||capture!=null&&capture.isRecording())return;
        int missing=ShotCoverage.evaluate(shots,takes).nextMissingIndex;
        if(missing<0){status.setText("Every plan shot has an assigned selected take. Review your footage before export.");return;}
        shotIndex=missing;switchTab(1);
        status.setText("Missing shot selected · review the direction, then tap Start camera when ready.");
    }
    private String shotAssignmentDescription(Take take){
        if(take.reviewedShotIds==null||take.reviewedShotIds.isEmpty())return "No creator-reviewed shot assignments yet";
        Set<String> current=new HashSet<>();for(Shot shot:shots)current.add(shot.id);
        for(String id:take.reviewedShotIds)if(!current.contains(id))return "Assignments include an earlier plan · review to update";
        return "Creator-reviewed shot assignments · "+take.reviewedShotIds.size();
    }
    private void reviewShotAssignments(Take take){
        if(busy||destroying||!takes.contains(take))return;
        dismissShotAssignments();
        ArrayList<String> ids=new ArrayList<>();String[] labels=new String[shots.size()];boolean[] checked=new boolean[shots.size()];
        for(int i=0;i<shots.size();i++){
            Shot shot=shots.get(i);ids.add(shot.id);labels[i]=String.format(Locale.US,"%02d  %s",i+1,shot.title);
            checked[i]=take.reviewedShotIds!=null&&take.reviewedShotIds.contains(shot.id);
        }
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Assign this take to plan shots")
                .setMultiChoiceItems(labels,checked,(d,index,on)->checked[index]=on)
                .setPositiveButton("Save shot assignments",null)
                .setNeutralButton("Clear assignments",null).setNegativeButton("Cancel",null).create();
        shotAssignmentDialog=dialog;
        dialog.setOnDismissListener(d->{if(shotAssignmentDialog==dialog)shotAssignmentDialog=null;});
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!ownsShotAssignments(dialog,take)){dialog.dismiss();return;}
            Set<String> current=new HashSet<>();for(Shot shot:shots)current.add(shot.id);
            if(!current.equals(new HashSet<>(ids))){dialog.dismiss();toast("The plan changed. Review this take's assignments again.");return;}
            ArrayList<String> reviewed=new ArrayList<>();for(int i=0;i<ids.size();i++)if(checked[i])reviewed.add(ids.get(i));
            take.reviewedShotIds=reviewed;dialog.dismiss();save();render();
        });dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(!ownsShotAssignments(dialog,take)){dialog.dismiss();return;}
            take.reviewedShotIds=new ArrayList<>();dialog.dismiss();save();render();
        });});
        dialog.show();
    }
    private boolean ownsShotAssignments(AlertDialog dialog,Take take){
        return !destroying&&shotAssignmentDialog==dialog&&dialog.isShowing()&&takes.contains(take)
                &&getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED);
    }
    private void dismissShotAssignments(){if(shotAssignmentDialog!=null){AlertDialog dialog=shotAssignmentDialog;shotAssignmentDialog=null;dialog.dismiss();}}
    private static List<String> restoredShotAssignments(JSONArray stored){
        ArrayList<String> ids=new ArrayList<>();
        if(stored==null||stored.length()>64)return ids;
        for(int i=0;i<stored.length();i++){
            Object value=stored.opt(i);if(!(value instanceof String))continue;
            String id=(String)value;if(id.trim().isEmpty()||id.length()>160)continue;
            boolean valid=true;for(int j=0;j<id.length();j++)if(Character.isISOControl(id.charAt(j))){valid=false;break;}
            // Unknown/old-plan IDs remain historical creator metadata; coverage ignores them.
            if(valid&&!ids.contains(id))ids.add(id);
        }
        return ids;
    }
    private void draftMissingSubtitles(){
        if(busy||activeSubtitleBatch!=null||tab!=2||destroying||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        if(hasVoiceBriefWork()||referenceSpeechReader!=null){toast("Wait for voice reading to stop before drafting subtitles.");return;}
        if(takes.stream().noneMatch(t->t.selected&&t.subtitles.isEmpty())){toast("Selected takes already have subtitle words, or no takes are selected.");return;}
        if(subtitleBatch==null)subtitleBatch=new SubtitleBatch(this);
        final SubtitleBatch batch=subtitleBatch;
        if(!batch.isModelAvailable()){toast("Offline English speech model is not installed. Use manual text.");return;}
        releasePlannerForMedia();stopVoiceInput();speech.stop();
        final int generation=++subtitleBatchGeneration;
        final IdentityHashMap<Take,Uri> sources=new IdentityHashMap<>();for(Take take:takes)sources.put(take,take.uri);
        activeSubtitleBatch=batch;subtitleBatchOwnsBusy=true;subtitleBatchCancelRequested=false;
        setBusy(true);status.setText("Drafting missing subtitles locally… review each take afterward");
        batch.start(new ArrayList<>(takes),new SubtitleBatch.Listener(){
            private int accepted;
            private boolean current(){return !destroying&&generation==subtitleBatchGeneration&&activeSubtitleBatch==batch;}
            public void onProgress(int completed,int total){if(current()&&subtitleBatchOwnsBusy&&!subtitleBatchCancelRequested)status.setText("Local subtitle reading · "+completed+" / "+total+" clips completed");}
            public void onDraft(Take take,List<SubtitleCue> cues,long elapsedMs){
                if(!current()||!subtitleBatchOwnsBusy||subtitleBatchCancelRequested||!takes.contains(take)||!take.selected||!take.subtitles.isEmpty()
                        ||!Objects.equals(sources.get(take),take.uri)||cues==null||cues.isEmpty())return;
                take.subtitles=new ArrayList<>(cues);take.captionOrigin="whisper-tiny.en-draft";accepted++;save();
            }
            public void onComplete(int drafted,int failed,int skipped){
                if(!finishSubtitleBatch(batch,generation))return;
                int kept=skipped+Math.max(0,drafted-accepted);render();
                status.setText(accepted+" subtitle drafts saved · "+failed+" failed · "+kept+" skipped. Review words and timing before export.");
            }
            public void onCancelled(int drafted){if(!finishSubtitleBatch(batch,generation))return;render();status.setText("Subtitle reading stopped · "+accepted+" completed drafts kept. Review words and timing before export.");}
            public void onError(String message){if(!finishSubtitleBatch(batch,generation))return;render();status.setText(message);toast(message);}
        });
    }
    private boolean finishSubtitleBatch(SubtitleBatch batch,int generation){
        if(destroying||generation!=subtitleBatchGeneration||activeSubtitleBatch!=batch)return false;
        boolean owned=subtitleBatchOwnsBusy;activeSubtitleBatch=null;subtitleBatchOwnsBusy=false;subtitleBatchCancelRequested=false;
        if(!owned)return false;
        setBusy(false);return true;
    }
    private void cancelSubtitleBatch(){
        SubtitleBatch batch=activeSubtitleBatch;if(batch==null)return;
        subtitleBatchCancelRequested=true;
        if(subtitleBatchOwnsBusy&&status!=null)status.setText("Stopping local subtitle reading… completed drafts are kept");
        // A queued preflight error has no reader to drain; cancel suppresses that error.
        boolean alreadyIdle=!batch.isRunning();int generation=subtitleBatchGeneration;
        batch.cancel();
        if(alreadyIdle&&finishSubtitleBatch(batch,generation)){render();status.setText("Subtitle reading canceled. Existing words and completed drafts are kept.");}
    }
    private void transcribeTake(int index){if(busy)return;if(!transcriber.isModelAvailable()){toast("Offline English speech model is not installed. Use manual text.");return;}Take take=takes.get(index);setBusy(true);status.setText("Listening to this clip locally… no microphone is opened");transcriber.transcribe(take.uri,new ClipTranscriber.Listener(){public void onComplete(List<SubtitleCue> cues,long ms){setBusy(false);take.subtitles=cues;take.captionOrigin="whisper-tiny.en-draft";save();render();status.setText(cues.isEmpty()?"No speech found. Add manual text if needed.":"Offline subtitle draft ready · "+String.format(Locale.US,"%.1fs",ms/1000.0)+" · review the words");if(!cues.isEmpty()&&getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))reviewSubtitles(index);}public void onError(String e){setBusy(false);status.setText(e);toast(e);}});}
    private void suggestSpeechTrim(Take take) {
        if(busy)return;
        if(!transcriber.isModelAvailable()){toast("The local English speech model is unavailable. Use Trim & typography.");return;}
        pendingSpeechTrim=null;pendingSpeechTrimTake=null;speech.stopListening();speech.stop();
        setBusy(true);status.setText("Finding a speech cut locally… keep the app open");
        transcriber.analyzeForTrim(take.uri,take.inMs,take.outMs,new ClipTranscriber.TrimListener(){
            public void onComplete(SpeechTrim candidate,long elapsedMs){
                setBusy(false);
                if(!takes.contains(take)||!candidate.matches(take)){status.setText("This cut changed. Request a new suggestion.");return;}
                if(!candidate.hasSuggestion){status.setText("Current cut kept. "+candidate.reason);return;}
                pendingSpeechTrim=candidate;pendingSpeechTrimTake=take;render();
                status.setText("A tighter talking cut is ready. Preview it before applying.");
                if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))reviewSpeechTrim(take,candidate);
            }
            public void onError(String message){setBusy(false);status.setText(message);toast(message);}
        });
    }
    private void reviewSpeechTrim(Take take,SpeechTrim candidate){
        if(!takes.contains(take)||!candidate.matches(take)){pendingSpeechTrim=null;pendingSpeechTrimTake=null;render();status.setText("This cut changed. Request a new speech suggestion.");return;}
        String message="Current: "+SubtitleTime.format(take.inMs)+" → "+SubtitleTime.format(take.outMs)+" seconds\n"
                +"Suggested: "+SubtitleTime.format(candidate.suggestedInMs)+" → "+SubtitleTime.format(candidate.suggestedOutMs)+" seconds\n\n"
                +"This trims quieter beginning/end pauses and keeps a short margin around speech. It is approximate; preview the clip before applying.";
        new AlertDialog.Builder(this).setTitle("A tighter talking cut").setMessage(message)
                .setPositiveButton("Apply trim",(dialog,which)->{
                    if(!takes.contains(take)||!candidate.applyTo(take)){toast("This cut changed. Request a new suggestion.");return;}
                    pendingSpeechTrim=null;pendingSpeechTrimTake=null;save();render();status.setText("Speech trim applied. Preview your cut before exporting.");
                })
                .setNeutralButton("Preview suggested cut",(dialog,which)->previewVideo(take.uri,candidate.suggestedInMs,candidate.suggestedOutMs))
                .setNegativeButton("Keep current cut",(dialog,which)->{pendingSpeechTrim=null;pendingSpeechTrimTake=null;render();status.setText("Your current cut is kept.");})
                .show();
    }
    private void reviewSubtitles(int index){Take take=takes.get(index);LinearLayout form=column();form.setPadding(dp(18),dp(8),dp(18),dp(8));ArrayList<EditText> words=new ArrayList<>(),starts=new ArrayList<>(),ends=new ArrayList<>();for(SubtitleCue cue:take.subtitles){form.addView(text("Source timestamps in seconds · short cues fit up to 4 lines",11,MUTED));EditText a=input(SubtitleTime.format(cue.startMs),"Start seconds"),b=input(SubtitleTime.format(cue.endMs),"End seconds"),w=input(cue.text,"Subtitle words");a.setInputType(8194);b.setInputType(8194);form.addView(a);form.addView(b);form.addView(w);starts.add(a);ends.add(b);words.add(w);}ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Review your subtitle draft").setView(scroll).setPositiveButton("Save reviewed subtitles",null).setNeutralButton("Remove subtitles",(d,w)->{take.subtitles.clear();take.captionOrigin="manual";save();render();}).setNegativeButton("Later",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{ArrayList<SubtitleCue> cues=new ArrayList<>();for(int i=0;i<words.size();i++){String w=words.get(i).getText().toString().trim();long start=SubtitleTime.parse(starts.get(i).getText().toString()),end=SubtitleTime.parse(ends.get(i).getText().toString());if(start<0||end<=start||end>take.durationMs)throw new IllegalArgumentException();if(!w.isEmpty())cues.add(new SubtitleCue(start,end,w));}try{SubtitleTimeline.requireNonOverlapping(cues);}catch(IllegalArgumentException overlap){toast(overlap.getMessage());return;}take.subtitles=cues;take.captionOrigin="creator-reviewed-offline-asr";save();dialog.dismiss();render();}catch(Exception e){toast("Use valid subtitle times inside the source clip.");}}));dialog.show();}
    private void closePendingReferenceBoard(){
        if(pendingReferenceBoard!=null){pendingReferenceBoard.close();pendingReferenceBoard=null;}
    }
    private void clearReferenceBoards(){
        closePendingReferenceBoard();
        if(reviewedReferenceBoard!=null){reviewedReferenceBoard.close();reviewedReferenceBoard=null;}
    }
    private void chooseReferenceMoments(){
        if(busy||referenceVideoUri==null)return;
        if(!referenceVision.isModelAvailable()){toast("The local vision component is not installed. Describe the reference in your brief.");return;}
        final Uri source=referenceVideoUri;final int generation=referenceAnalysisGeneration;
        LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));
        form.addView(text("Choose three times in your reference. You'll review each nearby frame and write the shot beats you want to try. Sparse moments cannot establish all motion or audio.",13,MUTED));
        EditText[] times=new EditText[3];
        for(int i=0;i<3;i++){long time=referenceDurationMs>0?Math.round((referenceDurationMs-1)*(.1+i*.4)):1000+i*3000;times[i]=input(SubtitleTime.format(time),"Moment "+(i+1)+" / seconds");times[i].setInputType(8194);form.addView(times[i]);}
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Three moments to review").setView(scroll).setPositiveButton("Read moments locally",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(!dialog.isShowing()||!isCurrentReference(source,generation)||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){dialog.dismiss();return;}try{long[] requested=new long[3];for(int i=0;i<3;i++){requested[i]=SubtitleTime.parse(times[i].getText().toString());if(requested[i]<0||requested[i]>=180000||(i>0&&requested[i]<=requested[i-1])||(referenceDurationMs>0&&requested[i]>=referenceDurationMs))throw new IllegalArgumentException();}dialog.dismiss();inspectReferenceMoments(requested);}catch(Exception invalid){toast("Use three increasing times inside your reference clip.");}}));dialog.show();
    }
    private void inspectReferenceMoments(long[] requested){
        if(busy||referenceVideoUri==null)return;
        speech.stopListening();speech.stop();planner.close();planner=new LocalPlanner(this);closePendingReferenceBoard();
        final int generation=++referenceAnalysisGeneration;
        setBusy(true);status.setText("Reading three moments locally… keep the app open");
        final Uri selectedReference=referenceVideoUri;
        boardInspection.inspect(selectedReference,requested,new ReferenceBoardInspection.Listener(){
            public void onProgress(int completed,int total){if(isCurrentReference(selectedReference,generation))status.setText("Reading reference moments locally · "+completed+" / "+total);}
            public void onBoard(ReferenceBoard board,long elapsedMs){
                if(!isCurrentReference(selectedReference,generation)||!board.sourceUri.equals(selectedReference)){board.close();return;}setBusy(false);
                pendingReferenceBoard=board;tab=0;render();status.setText("Three moment drafts ready · "+String.format(Locale.US,"%.1fs",elapsedMs/1000d)+" · correct them before planning");
                if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))reviewReferenceBoard(board);
            }
            public void onError(String message){if(!isCurrentReference(selectedReference,generation))return;setBusy(false);status.setText(message);toast(message);}
        });
    }
    private void reviewReferenceBoard(ReferenceBoard board){
        if(busy||board==null||!board.sourceUri.equals(referenceVideoUri)||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        final int generation=referenceAnalysisGeneration;final Uri source=board.sourceUri;
        referenceBoardDialog=ReferenceBoardReview.show(this,board,new ReferenceBoardReview.Listener(){
            public void onConfirmed(ReferenceBoard reviewed,String summary){
                if(!isCurrentReference(source,generation)||!reviewed.sourceUri.equals(source)||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){reviewed.close();return;}
                clearReferenceBoards();reviewedReferenceBoard=reviewed;referenceSummary=summary;save();render();status.setText("Reviewed moments will guide your next shot plan. Edit every proposed shot.");
            }
            public void onDiscarded(){if(!isCurrentReference(source,generation))return;if(board==pendingReferenceBoard)closePendingReferenceBoard();else if(board==reviewedReferenceBoard){reviewedReferenceBoard.close();reviewedReferenceBoard=null;referenceSummary="";save();}render();}
        });
    }
    private void chooseReferenceFrame(){
        if(busy||referenceVideoUri==null)return;
        if(!referenceVision.isModelAvailable()){toast("The local vision component is not installed. Keep manual reference notes.");return;}
        final Uri source=referenceVideoUri;final int generation=referenceAnalysisGeneration;
        LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));
        form.addView(text("Choose one time in your selected reference. The nearest decoded frame is described locally; review any guesses before using them.",13,MUTED));
        EditText time=input("1.000","Reference time / seconds");time.setInputType(8194);form.addView(time);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose a reference frame").setView(form).setPositiveButton("Describe frame",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(!dialog.isShowing()||!isCurrentReference(source,generation)||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){dialog.dismiss();return;}try{long requested=SubtitleTime.parse(time.getText().toString());if(requested<0)throw new IllegalArgumentException();dialog.dismiss();describeReferenceFrame(requested);}catch(Exception error){toast("Use a time inside your selected reference video.");}}));dialog.show();
    }
    private boolean isCurrentReference(Uri source,int generation){return !destroying&&source!=null&&source.equals(referenceVideoUri)&&generation==referenceAnalysisGeneration;}
    private void clearPendingReferenceFrame(){android.graphics.Bitmap old=pendingReferenceFrame;pendingReferenceFrame=null;if(old!=null&&!old.isRecycled())old.recycle();}
    private void dismissReferenceNotesDialog(){if(referenceNotesDialog!=null){referenceNotesDialog.dismiss();referenceNotesDialog=null;}}
    private void describeReferenceFrame(long requestedMs){
        if(busy||referenceVideoUri==null)return;
        speech.stopListening();speech.stop();dismissReferenceNotesDialog();pendingReferenceNotes="";clearPendingReferenceFrame();
        planner.close();planner=new LocalPlanner(this);
        final Uri source=referenceVideoUri;final int generation=++referenceAnalysisGeneration;
        final VisionReference observer=referenceVision;
        setBusy(true);status.setText("Reading one reference frame locally… keep the app open");
        referenceFrames.decode(source,requestedMs,new ReferenceFrameDecoder.Listener(){
            public void onFrame(android.graphics.Bitmap frame,long timeMs,long durationMs){
                try{
                    if(!isCurrentReference(source,generation))return;
                    float scale=Math.min(1f,256f/Math.max(frame.getWidth(),frame.getHeight()));
                    android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(frame,Math.max(1,Math.round(frame.getWidth()*scale)),Math.max(1,Math.round(frame.getHeight()*scale)),true);
                    final android.graphics.Bitmap thumbnail=scaled==frame?frame.copy(android.graphics.Bitmap.Config.ARGB_8888,false):scaled;
                    if(thumbnail==null)throw new IllegalStateException("Reference thumbnail unavailable");
                    clearPendingReferenceFrame();pendingReferenceFrame=thumbnail;
                    observer.observe(frame,new VisionReference.Listener(){
                        public void onObservation(String notes,long elapsedMs,String model){
                            if(!isCurrentReference(source,generation)){if(pendingReferenceFrame!=thumbnail&&!thumbnail.isRecycled())thumbnail.recycle();return;}
                            setBusy(false);pendingReferenceNotes=notes;pendingReferenceTimeMs=timeMs;tab=0;render();
                            status.setText("Local reference frame draft ready · "+String.format(Locale.US,"%.1fs",elapsedMs/1000d)+" · review guesses");
                            if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))reviewReferenceNotes();
                        }
                        public void onError(String message){
                            if(!isCurrentReference(source,generation)){if(pendingReferenceFrame!=thumbnail&&!thumbnail.isRecycled())thumbnail.recycle();return;}
                            clearPendingReferenceFrame();setBusy(false);status.setText(message);toast(message);
                        }
                    });
                }catch(RuntimeException failure){if(isCurrentReference(source,generation)){clearPendingReferenceFrame();setBusy(false);status.setText("This reference frame could not be prepared. Try another moment or use manual notes.");}}
                finally{frame.recycle();}
            }
            public void onError(String message){if(!isCurrentReference(source,generation))return;setBusy(false);status.setText(message);toast(message);}
        });
    }
    private void reviewReferenceNotes(){
        if(pendingReferenceNotes.isEmpty()||busy||referenceVideoUri==null||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        final Uri source=referenceVideoUri;final int generation=referenceAnalysisGeneration;final long requestedTime=pendingReferenceTimeMs;
        dismissReferenceNotesDialog();
        LinearLayout form=column();form.setPadding(dp(20),dp(12),dp(20),dp(12));
        final ImageView image=new ImageView(this);
        final android.graphics.Bitmap uiFrame=pendingReferenceFrame!=null&&!pendingReferenceFrame.isRecycled()?pendingReferenceFrame.copy(android.graphics.Bitmap.Config.ARGB_8888,false):null;
        if(uiFrame!=null){image.setImageBitmap(uiFrame);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Selected local reference frame");form.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));}
        form.addView(text("One frame near "+SubtitleTime.format(requestedTime)+"s. Body framing checks visible pose landmarks. Correct the model's subject notes and any framing mistakes; one frame does not establish the video's story.",13,MUTED));
        EditText notes=input(pendingReferenceNotes,"Correct the frame observations");notes.setMinLines(4);notes.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(300)});form.addView(notes);
        ScrollView review=new ScrollView(this);review.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Review reference notes").setView(review)
                .setPositiveButton("Use corrected notes",null).setNegativeButton("Discard",null).create();
        referenceNotesDialog=dialog;
        dialog.setOnDismissListener(d->{if(referenceNotesDialog==dialog)referenceNotesDialog=null;image.setImageDrawable(null);if(uiFrame!=null&&!uiFrame.isRecycled())uiFrame.recycle();});
        dialog.setOnShowListener(d->{
            dialog.getButton(-1).setOnClickListener(v->{
                if(!dialog.isShowing()||!isCurrentReference(source,generation)||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){dialog.dismiss();return;}
                String corrected=notes.getText().toString().trim();if(corrected.isEmpty()){notes.setError("Add corrected notes before using them.");return;}
                clearReferenceBoards();referenceSummary="Creator-reviewed frame near "+SubtitleTime.format(requestedTime)+"s: "+corrected;pendingReferenceNotes="";clearPendingReferenceFrame();save();dialog.dismiss();render();status.setText("Corrected reference notes will inform your next plan.");
            });
            dialog.getButton(-2).setOnClickListener(v->{if(isCurrentReference(source,generation)){pendingReferenceNotes="";clearPendingReferenceFrame();}dialog.dismiss();if(isCurrentReference(source,generation))render();});
        });dialog.show();
    }
    private void readReferenceSpeech() {
        if(busy||referenceSpeechReader!=null||hasVoiceBriefWork()||capture!=null&&capture.isRecording()||referenceVideoUri==null||tab!=0||destroying
                ||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        if(!ensureVoiceRecorderReleased())return;
        if(!transcriber.isModelAvailable()){status.setText("The local English speech model is unavailable. Write the reference context in your brief instead.");return;}
        cancelReferenceSpeech();releasePlannerForMedia();stopVoiceInput();speech.stop();
        final int generation=++referenceSpeechGeneration;
        final Uri source=referenceVideoUri;
        final ClipTranscriber reader=transcriber;
        referenceSpeechReader=reader;referenceSpeechOwnsBusy=true;setBusy(true);status.setText("Reading English reference speech locally… no microphone is opened");
        reader.transcribe(source,new ClipTranscriber.Listener(){
            public void onComplete(List<SubtitleCue> cues,long elapsedMs){
                if(cues==null||cues.isEmpty()){finishReferenceSpeech(reader,source,generation,null,"No English words were recognized. Add reference context yourself.");return;}
                ReferenceSpeechContext.Draft draft;
                try{draft=new ReferenceSpeechContext.Draft(source,cues,elapsedMs);}
                catch(IllegalArgumentException invalid){finishReferenceSpeech(reader,source,generation,null,"The speech draft could not be reviewed. Describe the reference in your own words.");return;}
                finishReferenceSpeech(reader,source,generation,draft,null);
            }
            public void onError(String message){finishReferenceSpeech(reader,source,generation,null,message);}
        });
    }
    private void finishReferenceSpeech(ClipTranscriber reader,Uri source,int generation,ReferenceSpeechContext.Draft draft,String error){
        if(generation!=referenceSpeechGeneration||referenceSpeechReader!=reader)return;
        // Completion can reach the main loop before the native finally block. Keep the busy
        // gate until that exact borrowed reader releases its request; never delete its source.
        reader.closeWhenIdle(()->handler.post(()->{
            if(referenceSpeechReader==reader){referenceSpeechReader=null;referenceSpeechOwnsBusy=false;}
            if(generation!=referenceSpeechGeneration||destroying||!source.equals(referenceVideoUri))return;
            setBusy(false);
            if(tab!=0||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
            if(error!=null){status.setText(error);return;}
            referenceSpeechDraft=draft;reviewReferenceSpeech(draft,generation);
        }));
        if(transcriber==reader&&!destroying)transcriber=new ClipTranscriber(this);
    }
    private void cancelReferenceSpeech(){
        referenceSpeechDraft=null;
        if(referenceSpeechDialog!=null){referenceSpeechDialog.dismiss();referenceSpeechDialog=null;}
        final int generation=++referenceSpeechGeneration;
        final boolean ownedBusy=referenceSpeechOwnsBusy;
        final ClipTranscriber reader=referenceSpeechReader;
        if(reader==null)return;
        reader.closeWhenIdle(()->handler.post(()->{
            if(referenceSpeechReader==reader){referenceSpeechReader=null;referenceSpeechOwnsBusy=false;}
            if(ownedBusy&&generation==referenceSpeechGeneration&&!destroying){setBusy(false);if(status!=null)status.setText("Reference speech canceled. Your confirmed context and original video are kept.");}
        }));
        if(transcriber==reader&&!destroying)transcriber=new ClipTranscriber(this);
    }
    private void reviewReferenceSpeech(ReferenceSpeechContext.Draft draft,int generation){
        showReferenceSpeechContext(draft.sourceUri,draft,null,generation);
    }
    private void editReferenceSpeechContext(){
        ReferenceSpeechContext.Reviewed saved=referenceSpeechContext;
        if(busy||referenceSpeechReader!=null||saved==null||!saved.matchesSource(referenceVideoUri))return;
        showReferenceSpeechContext(saved.sourceUri,null,saved,referenceSpeechGeneration);
    }
    private void showReferenceSpeechContext(Uri source,ReferenceSpeechContext.Draft draft,ReferenceSpeechContext.Reviewed saved,int generation){
        if(destroying||generation!=referenceSpeechGeneration||referenceVideoUri==null||!source.equals(referenceVideoUri)
                ||tab!=0||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        referenceSpeechDraft=draft;
        LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(10));
        if(draft!=null){
            form.addView(text("Source-timed English draft · check every word. This reads audio only; it does not establish the visual story or what is true.",13,MUTED));
            form.addView(text(draft.formatTimedText(),14,FG));
        }else form.addView(text("Saved corrected speech context. The original transcript and times were not saved. Edit these reviewed words without reading the video again.",13,MUTED));
        String initial=saved!=null?saved.text:referenceSpeechContext!=null&&referenceSpeechContext.matchesSource(source)?referenceSpeechContext.text:"";
        EditText context=input(initial,"Write 1–210 corrected characters of useful speech context");context.setMinLines(3);form.addView(context);
        TextView counts=text("",12,LIME);form.addView(counts);
        Runnable updateCounts=()->counts.setText(context.getText().length()+" / 210 context characters · "+ReferenceSpeechContext.composedLength(brief,referenceSummary,reviewedReferenceBoard!=null,context.getText().toString())+" / 500 combined plan characters, including labels");
        context.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updateCounts.run();}public void afterTextChanged(android.text.Editable e){}});updateCounts.run();
        form.addView(text("Only your confirmed concise context guides planning. Your typed brief and shot plan stay unchanged.",12,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(draft!=null?"Review reference speech":"Edit reviewed speech context").setView(scroll)
                .setPositiveButton("Use speech context",null).setNegativeButton("Keep previous context",null).create();
        referenceSpeechDialog=dialog;
        dialog.setOnDismissListener(d->{if(referenceSpeechDialog==dialog){referenceSpeechDialog=null;++referenceSpeechGeneration;}if(referenceSpeechDraft==draft)referenceSpeechDraft=null;});
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!dialog.isShowing()||generation!=referenceSpeechGeneration||destroying||tab!=0||!source.equals(referenceVideoUri)
                    ||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)){dialog.dismiss();return;}
            try{
                ReferenceSpeechContext.Reviewed reviewed=draft!=null?draft.reviewed(context.getText().toString()):saved.reviewed(context.getText().toString());
                ReferenceSpeechContext.composePlanBrief(brief,referenceSummary,reviewedReferenceBoard!=null,reviewed);
                referenceSpeechContext=reviewed;save();dialog.dismiss();render();status.setText("Reviewed speech context saved. Build a shot plan when you choose.");
            }catch(IllegalArgumentException invalid){context.setError(invalid.getMessage());}
        }));dialog.show();
    }
    private void pickReference(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,52);}
    private void changeReferenceSource(Uri uri){
        ++referenceAnalysisGeneration;
        referenceSpeechOwnsBusy=false;cancelReferenceSpeech();++referenceSpeechGeneration;
        dismissReferenceNotesDialog();if(referenceBoardDialog!=null){referenceBoardDialog.dismiss();referenceBoardDialog=null;}
        boardInspection.close();referenceFrames.close();referenceVision.close();
        boardInspection=new ReferenceBoardInspection(this);referenceFrames=new ReferenceFrameDecoder(this);referenceVision=new VisionReference(this);
        closePendingReferenceBoard();pendingReferenceNotes="";clearPendingReferenceFrame();
        if(!uri.equals(referenceVideoUri))referenceSpeechContext=null;referenceVideoUri=uri;
    }
    private void analyzeReference(Uri uri){changeReferenceSource(uri);referenceSummary="";clearReferenceBoards();referenceDurationMs=0;pendingReferenceNotes="";clearPendingReferenceFrame();references.close();references=new ReferenceAnalyzer(this);final int generation=++referenceAnalysisGeneration;save();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}setBusy(true);status.setText("Inspecting selected frames locally…");references.analyze(uri,new ReferenceAnalyzer.Listener(){private boolean current(){return generation==referenceAnalysisGeneration&&!destroying&&uri.equals(referenceVideoUri);}public void onResult(String summary,long elapsedMs,long durationMs){if(!current())return;referenceDurationMs=durationMs;onResult(summary,elapsedMs);}public void onResult(String summary,long elapsedMs){if(!current())return;setBusy(false);referenceSummary=summary;tab=0;save();render();status.setText("Reference samples ready. Choose a frame for local visual notes, or describe the story yourself.");}public void onError(String error){if(!current())return;setBusy(false);status.setText(error);toast(error);}});}
    private void packageProject(){
        if(busy)return;
        discardPendingPack();setBusy(true);status.setText("Packing your selected originals and edits locally…");
        int generation=++saveGeneration;
        packager.export(takes,reelTitle,look,new ProjectPackager.Listener(){
            public void onProgress(long bytes){if(generation==saveGeneration)status.setText(String.format(Locale.US,"Packing originals locally · %.1f MB",bytes/1048576.0));}
            public void onComplete(File file,long bytes){
                if(generation!=saveGeneration){file.delete();return;}
                setBusy(false);pendingPack=file;pendingPackSnapshot=packSnapshot();save();
                if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))requestPackDocument();
                else status.setText("Edit package ready. Return to Assemble to choose a save folder.");
            }
            public void onError(String message){if(generation!=saveGeneration)return;setBusy(false);status.setText(message);toast(message);}
        });
    }
    private void requestPackDocument(){
        if(pendingPack!=null&&!pendingPackSnapshot.equals(packSnapshot())){packageProject();return;}
        if(pendingPack==null||!pendingPack.isFile()){discardPendingPack();toast("Create the package again before saving.");return;}
        if(!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))return;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"MiniFilm-edit-package.zip");
        try{startActivityForResult(intent,54);}catch(Exception error){status.setText("Edit package is ready. Files could not open; try Save ready edit package again.");}
    }
    private void discardPendingPack(){if(pendingPack!=null){pendingPack.delete();pendingPack=null;pendingPackSnapshot="";save();}}
    private File restorePendingPack(String name){try{if(!name.matches("minifilm-pack-[0-9]+\\.zip"))return null;File file=new File(getCacheDir(),name);if(!file.getCanonicalFile().getParentFile().equals(getCacheDir().getCanonicalFile())||!file.isFile()||file.length()<=0||file.length()>ProjectPackager.MAX_ORIGINAL_BYTES+8388608L)return null;return file;}catch(Exception ignored){return null;}}
    @Override protected void onResume(){super.onResume();recoverSavedTakes(false);if(!busy&&tab==2&&(pendingPack!=null||pendingSpeechTrim!=null)){render();status.setText(pendingSpeechTrim!=null?"Your speech trim suggestion is ready to review.":"Your previously prepared edit package is ready to save.");}}

    private void copyProjectPack(Uri destination){
        if(pendingPack!=null&&!pendingPackSnapshot.equals(packSnapshot()))discardPendingPack();
        File source=pendingPack;
        if(source==null||!source.isFile()){toast("Create the package again before saving.");return;}
        int generation=++saveGeneration;savingDocument=true;setBusy(true);status.setText("Saving your portable edit package…");
        documentCopier.copy(Uri.fromFile(source),destination,ProjectPackager.MAX_ORIGINAL_BYTES+8388608L,new DocumentCopier.Listener(){
            public void onComplete(long bytes){
                if(generation!=saveGeneration)return;
                savingDocument=false;setBusy(false);
                if(source.equals(pendingPack))discardPendingPack();
                render();status.setText("Clips + edits saved to your chosen folder. Office Kit transfer is ready to test.");
            }
            public void onError(String message){
                if(generation!=saveGeneration)return;
                savingDocument=false;setBusy(false);save();render();
                status.setText("Package save did not finish. Your prepared package is ready to retry; choose a new name because the earlier document may be partial.");
            }
        });
    }
    private void saveEditDocument(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"MiniFilm-project.json");startActivityForResult(i,53);}
    private void copyEditDocument(Uri destination){
        Uri source=lastEdit;if(source==null)return;
        int generation=++saveGeneration;setBusy(true);savingDocument=true;status.setText("Saving your last exported cut list…");
        documentCopier.copy(source,destination,1048576L,new DocumentCopier.Listener(){
            public void onComplete(long bytes){if(generation!=saveGeneration)return;savingDocument=false;setBusy(false);status.setText("Last exported cut list saved to your chosen folder");}
            public void onError(String message){if(generation!=saveGeneration)return;savingDocument=false;setBusy(false);status.setText("Cut-list save did not finish. Choose a writable local folder and a new name; the earlier document may be partial.");}
        });
    }
    private void pickClips(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,51);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==54){if(result==RESULT_OK&&data!=null&&data.getData()!=null)copyProjectPack(data.getData());else{save();if(status!=null)status.setText(pendingPack!=null&&pendingPack.isFile()?"Edit package is ready. Choose Save ready edit package when you want to retry.":"Choose Save clips + edits to create a current package.");}return;}if(request==53&&result==RESULT_OK&&data!=null&&data.getData()!=null){copyEditDocument(data.getData());return;}if(request==52&&result==RESULT_OK&&data!=null&&data.getData()!=null){analyzeReference(data.getData());return;}if(request!=51||result!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());for(Uri u:uris){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(this,u);long duration=Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));if(duration<=0||m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)==null)throw new IllegalArgumentException();takes.add(new Take(u,"import","Imported take "+(takes.size()+1),"",duration));}catch(Exception e){toast("That clip could not be read. Try a local MP4.");}finally{try{m.release();}catch(Exception ignored){}}}releasePlannerForMedia();tab=2;save();render();}
    private void editTake(int i){Take t=takes.get(i);LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),dp(10));EditText start=input(SubtitleTime.format(t.inMs),"In point / seconds"),end=input(SubtitleTime.format(t.outMs),"Out point / seconds"),caption=input(t.caption,"Typography (used when no subtitles)"),title=input(t.title,"Take title");start.setInputType(8194);end.setInputType(8194);form.addView(text("In / out in seconds · total "+SubtitleTime.format(t.durationMs)+"s",12,MUTED));form.addView(start);form.addView(end);form.addView(title);form.addView(caption);form.addView(text("Keep typography to 4 short lines. Longer text must be shortened before export. Take headings fit one line; omit the reel title for caption-only output.",12,MUTED));ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Make this cut yours").setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{long in=SubtitleTime.parse(start.getText().toString()),out=SubtitleTime.parse(end.getText().toString());if(in<0||out<=in||out>t.durationMs||out-in<250)throw new IllegalArgumentException();t.inMs=in;t.outMs=out;t.title=title.getText().toString();t.caption=caption.getText().toString();save();dialog.dismiss();render();}catch(Exception e){toast("Use a valid cut at least 0.25 sec long within this take.");}}));dialog.show();}
    private void export(){if(busy)return;if(takes.stream().noneMatch(t->t.selected)){toast("Select at least one take.");return;}setBusy(true);if("Auto balance".equals(look)){status.setText("Inspecting local color samples… keep the app open");autoColor.analyze(takes,new AutoColorBalance.Listener(){public void onComplete(List<AutoColorBalance.Balance> balances,long elapsed){startReelExport(balances);}public void onError(String error){setBusy(false);status.setText(error);toast(error);}});}else startReelExport(Collections.emptyList());}
    private void startReelExport(List<AutoColorBalance.Balance> balances){status.setText("Exporting on your phone… keep the app open");exporter.export(takes,reelTitle,look,balances,new ReelExporter.Listener(){public void onProgress(int p){status.setText("Exporting locally · "+p+"%");}public void onComplete(Uri v,Uri e){setBusy(false);lastVideo=v;lastEdit=e;save();render();status.setText("Your reel is ready · saved to Movies / MiniFilm");}public void onError(String e){setBusy(false);status.setText(e);toast(e);}});}
    private void loadDemo(){if(busy)return;releasePlannerForMedia();int generation=++demoGeneration;setBusy(true);status.setText("Creating synthetic demo clips locally…");DemoAssets.create(this,new DemoAssets.Listener(){public void onReady(List<Take> t){if(generation!=demoGeneration)return;setBusy(false);takes.addAll(t);tab=2;save();render();status.setText("Synthetic demo · no camera or microphone was used");}public void onError(String e){if(generation!=demoGeneration)return;setBusy(false);toast(e);status.setText(e);}});}
    private void previewVideo(Uri u){previewVideo(u,null,null);}
    private void previewVideo(Uri u,Long startMs,Long endMs){Uri safe=u;if("file".equals(u.getScheme()))safe=FileProvider.getUriForFile(this,"dev.minifilm.director.files",new File(u.getPath()));Intent i=new Intent(this,PreviewActivity.class).setData(safe).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(startMs!=null&&endMs!=null)i.putExtra(PreviewActivity.EXTRA_START_MS,startMs.longValue()).putExtra(PreviewActivity.EXTRA_END_MS,endMs.longValue());try{startActivity(i);}catch(Exception e){toast("This video could not be opened.");}}
    private void share(Uri u,String type){Intent i=new Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share your film"));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] grants){super.onRequestPermissionsResult(r,p,grants);if(r==41){toast("Permissions updated. Tap Start camera when you're ready.");}else if(r==42)toast("Tap Use phone dictation when you're ready.");else if(r==44)toast("Tap Record a local voice brief when you're ready.");}
    @Override protected void onStop(){++shootPoseGeneration;super.onStop();dismissShotAssignments();cancelSubtitleBatch();dismissReferenceNotesDialog();cancelReferenceSpeech();cancelVoiceBrief();clearQuietStopPolicy();sequenceActive=false;session=false;cancelCountdown();handler.removeCallbacks(tick);if(capture!=null)capture.stopPreview();speech.stop();speech.stopListening();updateVoiceInputControls();if(pose!=null)pose.setEnabled(false);live=false;if(captureAction!=null)captureAction.setText("Start camera");save();}
    @Override protected void onDestroy(){++shootPoseGeneration;destroying=true;dismissShotAssignments();++subtitleBatchGeneration;subtitleBatchOwnsBusy=false;activeSubtitleBatch=null;if(subtitleBatch!=null)subtitleBatch.close();cancelReferenceSpeech();cancelVoiceBrief();if(briefRecorder!=null)briefRecorder.close();dismissReferenceNotesDialog();clearPendingReferenceFrame();++saveGeneration;++demoGeneration;documentCopier.close();if(capture!=null)capture.close();if(pose!=null)pose.close();speech.close();planner.close();transcriber.close();references.close();referenceVision.close();referenceFrames.close();boardInspection.close();if(referenceBoardDialog!=null)referenceBoardDialog.dismiss();clearReferenceBoards();exporter.cancel();packager.close();autoColor.close();super.onDestroy();}
    private JSONArray serializeTakes() throws JSONException{JSONArray tt=new JSONArray();for(Take t:takes){JSONArray subs=new JSONArray();for(SubtitleCue cue:t.subtitles)subs.put(new JSONObject().put("start",cue.startMs).put("end",cue.endMs).put("text",cue.text));tt.put(new JSONObject() .put("subtitles",subs).put("reviewedShotIds",new JSONArray(t.reviewedShotIds==null?Collections.emptyList():t.reviewedShotIds)).put("captionOrigin",t.captionOrigin).put("uri",t.uri.toString()).put("id",t.shotId).put("title",t.title).put("caption",t.caption).put("duration",t.durationMs).put("in",t.inMs).put("out",t.outMs).put("selected",t.selected));}return tt;}
    private String packSnapshot(JSONArray serialized) throws JSONException{return new JSONObject().put("reelTitle",reelTitle).put("look",look).put("takes",serialized).toString();}
    private String packSnapshot(){try{return packSnapshot(serializeTakes());}catch(JSONException impossible){return "";}}
    private void save(){try{JSONObject o=new JSONObject();if(referenceVideoUri!=null)o.put("referenceVideoUri",referenceVideoUri.toString());if(reviewedReferenceBoard!=null)o.put("referenceBoard",reviewedReferenceBoard.toJson());if(referenceSpeechContext!=null&&referenceSpeechContext.matchesSource(referenceVideoUri))o.put("referenceSpeech",referenceSpeechContext.toJson());o.put("referenceDurationMs",referenceDurationMs);o.put("tab",tab).put("lens",desiredLens).put("guideSequence",guideSequence).put("reelTitle",reelTitle).put("referenceSummary",referenceSummary).put("brief",brief).put("style",style).put("look",look).put("source",planSource).put("shot",shotIndex).put("voice",voice);JSONArray ss=new JSONArray();for(Shot s:shots)ss.put(new JSONObject().put("id",s.id).put("title",s.title).put("cue",s.instruction).put("caption",s.caption).put("duration",s.targetDurationMs));o.put("shots",ss);JSONArray tt=serializeTakes();if(pendingPack!=null&&!pendingPackSnapshot.equals(packSnapshot(tt))){pendingPack.delete();pendingPack=null;pendingPackSnapshot="";if(packageSaveButton!=null)packageSaveButton.setText("Save clips + edits for my laptop");}if(pendingPack!=null)o.put("pendingPack",pendingPack.getName()).put("pendingPackSnapshot",pendingPackSnapshot);o.put("takes",tt);if(lastVideo!=null)o.put("lastVideo",lastVideo.toString());if(lastEdit!=null)o.put("lastEdit",lastEdit.toString());getSharedPreferences("shoot",0).edit().putString("state",o.toString()).apply();}catch(Exception ignored){}}
    private void recoverSavedTakes(boolean announce){
        if(captureTakeStore==null||destroying||busy||session||countdown||capture!=null&&capture.isRecording())return;
        CaptureTakeStore.Result result=captureTakeStore.recover(new ArrayList<>(takes));
        if(!result.recovered.isEmpty()){takes.addAll(result.recovered);save();}
        if(root!=null&&(announce||!result.recovered.isEmpty()&&tab==2)){
            render();
            String message=result.recovered.isEmpty()?"No additional readable phone takes found.":result.recovered.size()+" saved phone takes found · review and select them for your reel.";
            if(result.unreadable>0)message+=" "+result.unreadable+" files or saved shot notes could not be read; originals were kept.";
            status.setText(message);
        }
    }
    private void restore(){try{JSONObject o=new JSONObject(getSharedPreferences("shoot",0).getString("state","{}"));int savedTab=o.optInt("tab",0);tab=savedTab>=0&&savedTab<=2?savedTab:0;pendingPack=restorePendingPack(o.optString("pendingPack",""));pendingPackSnapshot=o.optString("pendingPackSnapshot","");desiredLens=o.optInt("lens",CameraSelector.LENS_FACING_FRONT);if(desiredLens!=CameraSelector.LENS_FACING_BACK)desiredLens=CameraSelector.LENS_FACING_FRONT;guideSequence=o.optBoolean("guideSequence",false);reelTitle=o.optString("reelTitle","");referenceSummary=o.optString("referenceSummary","");String referenceUri=o.optString("referenceVideoUri","");if(!referenceUri.isEmpty()){Uri candidate=Uri.parse(referenceUri);if("file".equals(candidate.getScheme())||"content".equals(candidate.getScheme()))referenceVideoUri=candidate;}try{referenceSpeechContext=ReferenceSpeechContext.Reviewed.fromJson(o.optString("referenceSpeech",""),referenceVideoUri);}catch(Exception invalidSpeech){referenceSpeechContext=null;}referenceDurationMs=Math.max(0,Math.min(180000,o.optLong("referenceDurationMs",0)));try{String boardJson=o.optString("referenceBoard","");if(!boardJson.isEmpty()&&referenceVideoUri!=null){ReferenceBoard restored=ReferenceBoard.fromJson(boardJson);if(restored.sourceUri.equals(referenceVideoUri)){reviewedReferenceBoard=restored;referenceSummary=restored.planningSummary();}else restored.close();}}catch(Exception invalidBoard){reviewedReferenceBoard=null;}brief=o.optString("brief",brief);style=o.optString("style",style);look=o.optString("look",look);planSource=o.optString("source",planSource);shotIndex=o.optInt("shot",0);voice=o.optBoolean("voice",true);JSONArray ss=o.optJSONArray("shots");if(ss!=null)for(int i=0;i<ss.length();i++){JSONObject s=ss.getJSONObject(i);shots.add(new Shot(s.getString("id"),s.getString("title"),s.getString("cue"),s.getString("caption"),s.getLong("duration")));}JSONArray tt=o.optJSONArray("takes");if(tt!=null)for(int i=0;i<tt.length();i++){JSONObject t=tt.getJSONObject(i);Take a=new Take(Uri.parse(t.getString("uri")),t.getString("id"),t.getString("title"),t.getString("caption"),t.getLong("duration"));a.inMs=t.getLong("in");a.outMs=t.getLong("out");a.selected=t.getBoolean("selected");a.captionOrigin=t.optString("captionOrigin","manual");a.reviewedShotIds=restoredShotAssignments(t.optJSONArray("reviewedShotIds"));JSONArray cues=t.optJSONArray("subtitles");if(cues!=null)for(int j=0;j<cues.length();j++){JSONObject cue=cues.getJSONObject(j);a.subtitles.add(new SubtitleCue(cue.getLong("start"),cue.getLong("end"),cue.getString("text")));}takes.add(a);}if(o.has("lastVideo"))lastVideo=Uri.parse(o.getString("lastVideo"));if(o.has("lastEdit"))lastEdit=Uri.parse(o.getString("lastEdit"));}catch(Exception ignored){}}
}
