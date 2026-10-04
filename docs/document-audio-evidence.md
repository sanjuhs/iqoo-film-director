# Document saving and spoken interruption — 4 October 2026, 16:14 IST

Pre-event research on Nothing Phone (3a). Source review found that the old Files
save loops kept streams inside their worker, so cancellation only advanced a
generation flag. A blocked provider could keep writing. Failed ZIP saves also
deleted the prepared cache copy. These were source findings, not reproduced
private-provider failures.

`DocumentCopier` owns each copy's cancellation signal and streams. Cancel
invalidates publication immediately and schedules signal/input/output cleanup
independently off the main thread. Late opens close their returned resource;
a stale source open never opens the destination. Four outstanding operation
slots include cleanup, with bounded workers/queues. A provider that ignores
cancel/close may occupy a slot indefinitely; saturation reports an error and
partial destination documents may remain. Success requires flush and both
successful closes. Sources are never deleted by the helper.

The default adapter uses cancellation-aware `openFileDescriptor` in `r`/`wt`
mode and AutoClose streams. See Android's [ContentResolver contract](https://developer.android.com/reference/android/content/ContentResolver),
[CancellationSignal](https://developer.android.com/reference/android/os/CancellationSignal)
and [document access guidance](https://developer.android.com/training/data-storage/shared/documents-files).
Provider-specific behavior still needs an attended Files test.

MainActivity keeps its prepared ZIP on failure, cancellation or picker exit;
successful saving deletes only that reproducible cache ZIP after resource close.
Saved metadata snapshots invalidate it when take order/selection, words/timing,
title/look or other serialized take metadata changes. Legacy packages lacking a
matching snapshot rebuild rather than saving stale edits. Last-export controls
are labelled explicitly; the reversible Select no takes action keeps originals.
These integration/UI behaviors are source-reviewed. Two new visible tests and
an updated ready-package fixture are prepared, with no unlocked rerun yet.

SpeechCoach requests transient per-cue focus, rejects denied/delayed grants
before playback, abandons focus on completion/stop, and handles system noisy
output plus token-matched focus loss. Current failure callbacks run before an
optional preparation hook; stale focus callbacks cannot fail a replacement.
MainActivity pauses the countdown/pose break and future sequence advancement,
leaves an already-starting/recording take running, and suppresses automatic
pose speech until explicit Hear the direction or Record. No forced Bluetooth
route, pairing, automatic resume or microphone selection is added.

Official sources: [audio focus](https://developer.android.com/media/optimize/audio-focus),
[noisy output](https://developer.android.com/reference/android/media/AudioManager#ACTION_AUDIO_BECOMING_NOISY),
[broadcast receiver flags](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
and [system-server dispatch](https://android.googlesource.com/platform/frameworks/base/+/HEAD/services/core/java/com/android/server/audio/SystemServerAdapter.java).
System-source/flag review is not evidence of a real AirPods disconnect event.

## Actual focused runner

**16 methods passed in4.445s**. No Activity, camera, microphone or audible
playback was used by this runner:

- Seven document methods: exact local file→app FileProvider copies; short-read,
  flush/close-before-success; byte budget/source preservation; write blocked
  solely until independently closed; next-copy progress; cancellation-ignoring
  late source open; revoked/failed destination; flush/input/output-close faults;
  closed helper and queued-callback suppression.
- Seven silent synthetic interruption methods: active/idle noisy events, all
  three focus-loss kinds, main dispatch, stale request isolation, abandon-before-
  completion, explicit Stop, denied/delayed requests and reentrant loss/grant.
  Granted fake-focus paths never call audible `speakThen`; refusal checks stop
  before TTS playback. Close clears the hook/receiver registration state.
- Two unchanged real-engine synthesize-to-file callback methods: matching
  completion and stopped/stale-ID suppression. These never play the audio.

Logs are ignored private/evidence/document-audio-repair-{build,test}.log.
Post-test MainActivity changes only corrected the canceled-picker message and
checked its snapshot again before copy; tested helper/speech classes are
unchanged. Both APKs were rebuilt/installed, with no fresh native model run.
The nine-method visible UI class was compiled, not executed behind keyguard.

Saved/installed app:52,838,059 bytes, SHA-256
**4ad9095bfbef9007961a2212b9138e52456578974dfd7197f5f5223be9954724**;
installed bytes were independently hashed. Notices remain identical at SHA
02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68;
separate Qwen/Whisper weights are absent from APK. Manifest has no Internet/
network-state permission. Normal launch succeeded behind showing keyguard with
screen asleep; camera/microphone remain denied. Qualified storage9.280GB.

Physical AirPods/focus delivery, audible buffering/leakage, Bluetooth microphone,
real Files save/retry, visible package invalidation, live filming and required
iQOO/NPU/Office Kit/event submission gates remain unverified. No private upload,
aircraft action, permission bypass or outgoing submission occurred.
