# Speech-reader completion ordering — 4 October 2026

This is a pre-event research repair to the existing local English tiny.en CPU
reader. It changes completion ordering, not recognized words, trim heuristics,
model, native inference or the single-take UI's background policy.

## Source-proven interval

Previously, `ClipTranscriber` posted success or decoder/inference error to main
before its worker's `finally` freed the native request, cleared `activeRequest`
and reset its internal busy flag. The single-take subtitle and speech-trim
callbacks in `MainActivity` release the UI busy gate as soon as the callback
arrives. A second public request could therefore be admitted by the UI during
that interval but rejected by the reader as “Another clip is being transcribed.”

The ordering interval is established by source review. No human tap timing,
measured occurrence or corrupt subtitle result was observed. Closing the old
reader already suppressed ordinary queued callbacks during cancellation and
Activity destruction; this repair does not claim a new cross-instance
cancellation or stale-UI defect.

## Bounded central repair

The private completion adapter now prepares a main-thread callback rather than
posting it immediately. Speech-trim computation still runs on the same worker,
with the same decoded PCM and cancellation check, before request cleanup.
The worker then frees its exact native request, clears active/busy state, drains
registered resource cleanup, and only afterward posts the guarded terminal
callback. A successful or failed request's callback may immediately use the
same reader again.

Close still suppresses normal queued results. Preflight generation guards and
the separate fixed error for an explicit new call on an already closed reader
remain unchanged. No Main UI or background-policy changes, native code/test
hooks, retries, output rewrites, model downloads or training were introduced.

## Actual completion and regression checks

Both new methods in `TranscriberCompletionTest` passed on the physical Nothing
Phone (3a):

- `actualTranscriptionCallbackIsReleasedBeforeImmediateSameReaderTrim`:
  genuine ASR of the existing labelled `jacket-speech.mp4`, then an immediate
  same-instance request for a reviewed speech trim of `jacket-speech-padded.mp4`
  inside the first callback. Both success callbacks verify main-thread delivery,
  zero active request, reset busy and native registry baseline.
- `decoderErrorCallbackIsReleasedBeforeImmediateSameReaderActualTranscription`:
  a deliberately never-created local source triggers a safe decoder error;
  its callback verifies released state and immediately starts genuine ASR of
  the existing labelled speech fixture on that same reader.

Together these executed three actual native speech reads plus one decoder-error
request. All callback checks passed: main-thread delivery, exact native request
released, zero active request, internal busy false and registry at baseline
before the immediate same-reader public call. Known synthetic words, measured
container cue bounds, reviewed trim bounds and unchanged source hashes, byte
lengths and modification times also passed.

| Actual callback sequence | Measured local time | Result |
| --- | ---: | --- |
| Deliberate missing-source error → immediate same-reader ASR | 3,734 ms ASR | 3 cues; safe error; released before both callbacks |
| Successful ASR → immediate same-reader trim | 3,723 ms initial ASR | 3 cues; released before starting trim |
| The chained padded-source trim | 4,006 ms ASR; 4,065 ms total trim | Reviewed bounded suggestion; released before callback |

ASR timings cover the existing reader request, including decode and inference;
they are not isolated kernel measurements. The source-proven old interval
remains distinct from an unmeasured human-facing failure.

The combined physical runner passed **25 methods / 42.327 seconds**: 2 new
completion methods, 8 `TranscriberCancellationTest`, 9 `SpeechTrimTest`, 2
`CaptureFinalizationTest`, 2 `CaptureTakeRecoveryTest`, 1
`SubtitleBatchDeviceTest` and 1 `VoiceBriefCodecTest`. Request isolation,
queued-close, rejection, cleanup failure isolation, closed-call behavior and
actual padded speech/trim regressions passed. Their actual ASR request timings
were 4,572 ms (cancellation-class padded fixture), 4,279 ms (trim regression)
and 4,073 ms (synthetic voice-brief codec fixture).

The serial batch regression made two actual reads (4,229 / 5,069 ms), with a
maximum of one reader in flight, 3 independent Take drafts, 0 failed and 2
skipped. It then exported a 720×1280 reel: 20,387 ms encoded versus 20,251 ms
nominal timeline, preserving source offsets and nonempty subtitle pixels.
Sources remained unchanged. These were unreviewed synthetic drafts, not a
creator-approved finished reel.

The logged `transcription_failed category=local_audio` and
`resource_cleanup_failed category=closed_resource` warnings came from the
intentional missing-source and throwing-cleanup-hook cases. The runner had no
native regression failures.

Ignored evidence:

- `private/evidence/capture-recovery-native-tests.log`: complete 25-method runner.
- `private/evidence/capture-recovery-native-metrics.log`: scoped
  `MiniFilmASRCompletion`, `MiniFilmASR` and batch metrics.
- `private/evidence/capture-recovery-ui-tests.log`: separate **19 methods /
  28.955 seconds** on the unlocked test emulator. Those injected batch/capture
  state checks are not additional native speech execution evidence.

The new checks used existing installed models and synthetic local files, with
camera/microphone permissions denied and no app Internet permission. They start
no Activity, camera, microphone, playback or preference edit. Logs report fixed
categories, elapsed time and cue counts rather than source URIs or transcripts.
They establish the tested reader lifecycle behavior, not general speech accuracy,
real-time coaching, NPU execution, AirPods routing or attended creator benefit.
