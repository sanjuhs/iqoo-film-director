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
service presence is insufficient for the app's current English brief. No model
download or live recognition ran. A separate explicit Whisper brief path is
being prepared using the already installed model; this is not yet device-tested.

Six synthetic recognition lifecycle methods passed: terminal cleanup before
creator callbacks, duplicate/stale suppression, null/blank handling, independent
cancel/destroy exceptions, reentrant callbacks and close. They exercised the
actual listener logic with fake cleanup, rather than live recognition. Main now
offers **Stop voice input**, cancels pending speech on text edits/planning/
processing, and checks service availability before the normal microphone prompt.
The visible UI acceptance check is compiled, not run behind keyguard.
