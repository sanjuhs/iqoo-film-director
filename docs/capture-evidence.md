# Phone capture implementation — 4 October 2026

Pre-event research prototype; event-code eligibility is not established.

`prototype/phone-director/app/src/main/java/dev/minifilm/director/CaptureController.java`
implements explicit foreground phone preview, front/rear switching, CameraX
video recording with microphone, asynchronous finalization, and an optional
ImageAnalysis analyzer. It contains no drone APIs, uploads, provider credentials,
remote inference or permission bypass. Camera permission is checked before
preview; camera plus microphone grants and a resumed activity are required
before recording. The activity owns normal Android permission prompts.

The public integration contract is:

- Construct with `ComponentActivity`, `PreviewView`, and `Listener`.
- Register `setAnalyzer(ImageAnalysis.Analyzer)` before preview. The analyzer
  must close every ImageProxy, including asynchronous failure paths.
- Call `startPreview()` only from an explicit creator action.
- `onReady()` means camera use cases bound; check `isAnalysisAvailable()` before
  displaying live pose guidance. Hardware that rejects simultaneous video,
  preview and analysis uses real preview/recording without analysis.
- `startRecording(Shot)` snapshots reviewed shot facts into private durable
  metadata before capture; the no-argument overload remains available.
  `stopRecording()` is an explicit UI action. CameraX
  `onRecordingStarted()` confirms actual start; `isRecording()` remains true
  while a take is finalizing. A switch cannot interrupt an active take.
- `onRecordingFinished(Uri,long)` returns a private local file URI and actual
  validated container duration in milliseconds. Share through the activity's FileProvider, never a
  raw file URI across apps. Preserve original takes when editing.
- Call `stopPreview()` in `onStop()` before ending speech/pose session state.
  Call `close()` once in `onDestroy()`. Returning does not resume capture.
- Invoke public mutations on the Android UI thread.

Recorder preference is HD (720p) with CameraX lower-then-higher fallback. Each
take is capped at 60 seconds/100 MiB and recording requires at least 256 MiB
available phone storage. Files live in the app-private `files/takes` directory.
Known size/duration/source-inactive finalization results can retain valid media;
invalid recordings are discarded. Background interruption still needs a real
playback test. A usable take finalized after activity destruction now commits
private ready metadata before the closed controller suppresses UI callbacks.
Main recovers missing readable sources on startup, idle resume and entry to
Assemble; **Find saved phone takes** handles output arriving after those checks.
Recovered takes start unselected and existing edits/selection remain intact.
Active pending files are skipped; unreadable media/notes are preserved and
reported. Synthetic store, denied-camera UI and genuinely fresh-process pending
file recovery checks passed; see
[take recovery evidence](capture-take-recovery-evidence.md). They did not perform
CameraX recording or interrupt a live recording.

## Official references checked

- [CameraX video capture](https://developer.android.com/media/camera/camerax/video-capture):
  Recorder, HD quality/fallback, file output, start/stop and finalize events.
- [CameraX analysis](https://developer.android.com/media/camera/camerax/analyze):
  latest-frame backpressure, binding and ImageProxy lifecycle.
- [Runtime permission requests](https://developer.android.com/training/permissions/requesting):
  normal camera/microphone permission UI and handling denial.
- [Finalize API](https://developer.android.com/reference/androidx/camera/video/VideoRecordEvent.Finalize):
  distinguish terminal errors from saved output at limits or lifecycle stop.
- [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile):
  private metadata finish/sync/commit and caller-provided threading protection.

## Checks and limits

Source-level lifecycle and permission review completed. Parent integration owns
Gradle/build and device tests. Required dependencies are matching CameraX 1.4.2
`camera-camera2`, `camera-lifecycle`, `camera-video` and `camera-view`, plus
AndroidX Activity/Core. No separate model download, SDK installation or media
copy was performed by this capture work. Actual camera preview, recording,
audio route, analysis concurrency, saved playback and iQOO execution are not
yet evidenced by this file. Bluetooth AirPods recording route is not asserted;
CameraX uses the Android recording audio source, requiring actual route testing.

## Vertical framing and selected-lens correction

The preview now has a centered 9:16 visible viewfinder. CaptureController binds
Preview, VideoCapture and ImageAnalysis through a shared 9:16 CameraX ViewPort
(including the preview/video-only fallback). PoseCoach uses the image cropRect
mapped into rotated ML Kit coordinates, excludes hidden landmarks and normalizes
head/shoulder positions against the visible crop. These are source repairs;
actual camera crop alignment and framing usefulness still need an attended shoot.

Three Android geometry tests passed for asymmetric crops at all four rotations,
portrait crop exclusion, normalized positions and invalid inputs. Four real UI
tests passed, including 9:16 preview layout and back-lens persistence across Next
and activity recreation without opening capture. Combined with two silent speech
callback tests, the runner passed **9 tests in 14.110 seconds**. Camera/microphone
permissions remained denied. This establishes geometry/inactive selection, not
front/back hardware capture or live lens rebinding. Log:
`private/evidence/framing-speech-ui-test.log` (ignored).

Spoken directions now wait for matching utterance completion before the
8-second pose break. Each audible countdown number also waits for completion;
errors/timeouts pause before recording. Stop cancels callbacks and generation
guards prevent stale completion from starting a take. Real offline TTS engine
synthesis-to-file checks passed matching/unrelated/stopped ID behavior on the UI
thread; they never played sound, invoked live camera or proved Bluetooth buffering
or quiet recorded speech. Full attended AirPods sequencing remains pending.

Official contracts rechecked: [CameraX configuration/crop rectangles](https://developer.android.com/media/camera/camerax/configuration)
describes shared ViewPort crop areas, and [UtteranceProgressListener](https://developer.android.com/reference/android/speech/tts/UtteranceProgressListener)
describes completion/error/stop callbacks. Those API contracts support the design;
they do not replace the pending attended hardware tests.


Object/detail shots now suppress full-person pose coaching: all Product reveal
shots and named detail/cutaway/texture/fabric shots use the creator's scene cue.
Planning stops active speech/listening before generation and freezes editable
inputs while busy. These source fixes prevent mismatched brief/results and
object scenes receiving shoulder/face advice. The latest expanded seven-case
UI rerun encountered secure keyguard and its five screen-dependent failures
cannot verify these changes. An explicit unlocked-phone test precondition was
added; repeat those UI checks and the attended sequence after normal unlock.

### 13:36 IST: finalized duration and actual cut preview

Finalized takes now use positive MMR video container duration with valid video
width/height, rather than recording statistics. Two headless synthetic cases
passed for measured MP4 duration/original SHA and null/missing/empty/malformed/
audio-only inputs. No CameraX recording was performed; hardware finalization
latency and callbacks remain pending.

Paused PreviewActivity accepts optional paired source-relative start/end values.
It validates the local source and actual container bound, configures Media3
clipping, restores positions relative to that cut only for the same URI/range,
and never auto-plays. Three headless checks passed, including a real ExoPlayer
prepared 1500 ms clipped timeline while paused. Suggested speech-trim review and
normal take preview use these selected bounds. No Activity, camera, microphone
or audible playback was used in this verification. Evidence is part of the
18-case `private/evidence/trim-preview-export-test.log` runner (18.560 s).

### Durable late-take recovery verification

The new recovery UI method passed within a **19-test / 28.955s** unlocked empty
emulator runner. Generated fixture copies exercised actual Assemble entry,
manual **Find saved phone takes**, resume and recreation; recovered takes stayed
unselected and existing edits, selection, order, preferences and originals were
preserved. Camera/microphone were denied, with no capture, model or audio work.

Two new headless store methods passed within the physical-phone **25-test /
42.327s** runner alongside the two finalized-container tests and 21 ASR/trim/
batch/voice checks. The actual synthetic container measured **5746ms**; active
pending output was skipped, durable shot facts and canonical-alias edits were
preserved, and unreadable media/metadata and unknown files remained intact.

Separate staging (**1 test / 0.077s**) and fresh-process recovery (**1 test /
0.062s**) verified an actual new process recovering one unselected pending
synthetic take with its facts and duration. The first runner ended normally and
was already absent; no external force-stop or live recording kill occurred.
Hashes/sizes, pending schema, original bytes and a sentinel were checked before
and after recovery. This verifies private disk recovery, while actual CameraX
finalization, physical UI, AirPods and attended recording remain unproved.
Full logs, compile failures and scope are recorded in the linked recovery evidence.
