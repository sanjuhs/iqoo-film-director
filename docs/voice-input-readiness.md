# Spoken-brief readiness — 4 October 2026

The availability diagnostic passed **1 method / 0.063 seconds** on the Nothing
Phone (3a), Android 16/API36. It observed an on-device recognition service,
no Bluetooth or wired output, and denied microphone permission unchanged before
and after the query. It created no recognizer, opened no microphone and played
no audio. The fixed boolean-only snapshot and runner report are retained in
ignored `private/evidence/speech-capability-metrics.log` and
`private/evidence/speech-capability-test.log`.

Service presence does not establish that the requested English model is ready,
that recognition succeeds, or that AirPods deliver instructions. A separate
metadata-only query asks about the app's exact freeform/en-US intent using the
dedicated on-device recognizer, installs its listener before commands, and
destroys it on main after a bounded wait. It never calls `startListening`,
requests a download, or falls back to a cloud recognizer.

Android distinguishes installed languages (ready), pending downloads, and
supported languages still needing a download. An error or timeout in this
query remains unknown readiness, rather than a failed live recognition test.
The app's real voice input still requires an explicit action, normal microphone
permission and an attended English recognition check. Typed briefs remain usable.

Primary contracts: [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
and [RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport).
These describe the API; the device observations above are separate evidence.

The subsequent combined runner passed **24 methods / 10.313 seconds**. Its
metadata response was `support_result`: **installedEnUS=false,
pendingEnUS=false, supportedEnUS=true**. The English model needs downloading;
service presence is insufficient for **Use phone dictation**. No model download
or live recognition ran. **Record a local voice brief** now uses the already
installed Whisper model through a separate explicitly started recording flow.

Six synthetic recognition lifecycle methods passed: terminal cleanup before
creator callbacks, duplicate/stale suppression, null/blank handling, independent
cancel/destroy exceptions, reentrant callbacks and close. They exercised the
actual listener logic with fake cleanup, rather than live recognition. Main now
offers **Stop voice input**, cancels pending speech on text edits/planning/
processing, and checks service availability before the normal microphone prompt.
The visible UI acceptance check is compiled, not run behind keyguard.

## Local brief flow and evidence — 16:46 IST

Main offers Record → **Stop & review words** → editable draft → **Use this brief**.
Normal permission approval requires another explicit Record tap. It blocks
other processing/navigation while recording/transcribing; Cancel/background
discard recording and invalidate drafts. Words are never applied automatically.
Overlong drafts stay visible in full; acceptance requires 1–500 characters.
Using a draft does not generate a plan or start filming. Phone dictation remains
a separate optional dedicated Android recognizer path.

LocalBriefRecorder requests AAC/MPEG4, mono 16 kHz/64 kbps, encoder 45,000 ms and a
main-thread deadline. Reaching either limit conservatively releases/discards and
asks for a shorter brief. This is configured behavior, not a certified microphone
wall-clock bound. Native release failure protects the file/quota, refuses handoff
and exposes unconfirmed release; retry/close attempts cleanup. Four process-wide
slots include handed-off/canceled-reader files. Only exact owned files can be
discarded; failed deletion stays counted. First reserve in a new process cleans
only canonical numeric helper .m4a orphan names/regular files without following
links; recreation never rescans live readers. This is recovery at the next brief
attempt, rather than immediately at process death.

Whisper's resource-only closeWhenIdle hook deletes a canceled owned source after
the old request releases its decoder/native handle. Normal completion/errors
already follow decoder release and can discard directly. Hooks run outside the
lifecycle lock, once per Runnable identity in attachment order; fixed-category
cleanup failures do not block remaining hooks. No URI/original deletion API was
introduced. Brief audio is never added to takes, Gallery, packages or logs.

The initial runner executed 24 methods in 3.720s: **23 passed, one fixture failed**
before audio-only transcription. Fourteen fake-recorder methods covered guarded
start, explicit transfer/cancel, failures including release, deadlines/stale
events, quota, safe discard and process recovery. Eight request methods included
two new cleanup-hook checks plus actual padded-video ASR: three cues in3,310ms,
expected words, unchanged source. One unchanged trim cancellation check passed.
The fake recorder emits labelled non-AAC bytes and never opens a microphone.

The fixture had valid negative AAC priming PTS; its incorrect EOF loop copied
zero packets. The corrected test preserves all **412 AAC packets/hash**, rebases
timestamps by 21,333µs, and measures the standalone container independently:
source 8759ms, audio-only 8789ms. Its focused rerun passed **1 method /3.823s**:
actual local tiny.en transcription returned **three cues /3,553ms**, jacket/
green/outfit, timestamps within the audio container, one main callback, unchanged
source and test-copy cleanup after worker termination. Both failed and repaired
runner/metrics reports remain ignored under private/evidence/voice-brief-*.
Thus 24 methods have passed across these runs; the first runner was not all-pass.

Three additional unlocked-only draft-review UI checks are compiled, unrun.
Actual MediaRecorder encoding, microphone/earbud routing, permission dialogs,
visible Stop/Cancel and attended speech accuracy remain gates. The audio-only
remux check establishes decoding/inference, not microphone recording acceptance.
Official [MediaRecorder](https://developer.android.com/media/platform/mediarecorder)
and [File implementation](https://android.googlesource.com/platform/libcore/+/master/ojluni/src/main/java/java/io/File.java)
contracts informed foreground/stop/release and exact orphan naming.
