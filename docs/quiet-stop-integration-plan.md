# Experimental quiet-pause stop — minimal integration plan

Source review, 4 October 2026. This is a proposed opt-in behavior, **not an
implemented or tested feature**. It extends a planned talking take by at most
eight seconds, within the existing 60-second recording ceiling, and observes
CameraX's recording statistics without another microphone stream. Audio energy
cannot establish that a sentence finished. API and pinned-ABI evidence is in
[speech-stop feasibility](speech-stop-feasibility.md).

Implementation follow-up: the app now contains the off-by-default switch,
per-attempt controller IDs/status callback, pure bounded policy and immediate
manual/background overrides described here. The policy uses the experimental
parameters below; all **13 synthetic policy checks passed on the phone** in the
23-test / 92.759-second combined runner. Actual audio calibration and attended
recording remain unverified. The rest of this document records the original proposal.

## Existing seams and stop-path findings

Line numbers below describe the reviewed source snapshot and may move.

| File / seam | Existing behavior | Required integration |
| --- | --- | --- |
| [MainActivity.java](../prototype/phone-director/app/src/main/java/dev/minifilm/director/MainActivity.java), Direct switches, lines 81–84 | Direction/sequence/auto-stop are separate switches. | Add **Wait for a quiet pause · experimental**, off by default. Explain “up to 8 extra seconds; sound levels can mistake a pause for the end.” Enable it only for an explicitly selected spoken take and a timed/sequence stop. Do not infer that fashion movement or product detail contains speech. |
| MainActivity, `count(0)`, line 118 | Starts the reviewed `recordingShot` after countdown completion. | Allocate a unique take-attempt ID and immutable stop settings **before** invoking capture. Snapshot planned duration, timed-stop intent and quiet opt-in. |
| MainActivity, capture listener, line 91 | Start sets `recordStart`; Finalize saves the take and then advances/rebinds the sequence. | Initialize policy timing on this take's Start. Feed matching audio events into the policy. Invalidate policy on error/finalization; preserve the saved-take/finalization path. |
| MainActivity, `tick`, line 92 | Calls `capture.stopRecording()` exactly at planned duration when `autoStop || sequenceActive`. | Preserve that branch when quiet opt-in is off. When on, keep polling during the extension and stop on an eligible quiet tail or the independent hard deadline. Never return at planned time without scheduling the next deadline check. |
| MainActivity, `stopTake`, line 127; `onStop`, line 296 | Immediately cancel countdown/sequence, remove ticks, and stop recording or preview. | Invalidate the quiet policy before the existing direct stop. Neither path passes through a quiet-wait decision. |
| [CaptureController.java](../prototype/phone-director/app/src/main/java/dev/minifilm/director/CaptureController.java), `startRecording`, lines 203–237 | Foreground/permissions checked; file capped at 100 MiB and 60 seconds; audio enabled. | Retain these checks/caps. Capture a take ID in the recording event lambda. No AudioRecord, SpeechRecognizer or second stream is added. |
| CaptureController, `handleEvent`, lines 240–263 | Handles Start/Finalize; Status is ignored. | Forward scalar Status data only for the current, started, non-stopping recording. Include take ID, recording duration, audio state, has-audio/error flags and amplitude. |
| CaptureController, `stopRecording`/`stopPreview`, lines 287–309 | Idempotent stop request; `isRecording` remains true while flushing; preview stop unbinds. | Keep immediate semantics and suppress further policy Status after stop request. Finalize still validates actual container duration before publishing the take. |

**Actual current limitation:** the planned-time branch has no speech awareness,
so it can cut spoken content mid-sentence. Turning automatic stop off is not a
complete spoken-sequence fallback: `sequenceActive` still forces the planned
stop. The new option must be explicit for the sequence too.

**Current safeguards found:** navigation, lens changes and a new take are blocked
while recording/finalizing; manual Stop and background stop do not wait for a
timer; automatic sequence progression follows Finalize, not stop request. No
source-level blocker was found in those paths during this review. Physical stop
latency, finalization and audio routing remain untested.

**Integration risks to prevent:** the current `tick` reads mutable `autoStop` and
`sequenceActive`, and callbacks carry no recording ID. Snapshot the stop decision
for each attempt rather than letting switch changes unexpectedly extend it. An
old audio callback must not update shared counters or stop a rebound controller.
Calling the full `stopTake()` for an automatic quiet completion would cancel the
sequence: use the existing capture stop request for successful automatic ends.

## Minimal state and callback contract

Use one small pure-Java decision object per recording; no new model or service.
Suggested immutable inputs:

```text
takeId, ownerControllerGeneration, plannedMs, timedStopEnabled, quietEnabled
hardMs = quietEnabled ? min(plannedMs + 8000, 60000) : plannedMs
```

Allocate monotonically increasing `takeId` in Main before `count(0)` starts
capture. Increment the controller generation whenever constructing/replacing
CaptureController. A `startRecording(long takeId)` overload can retain the old
method for existing callers; its event lambda captures that ID. Add a default
listener method such as:

```java
onAudioStatus(long takeId, long recordedDurationNs, int audioState,
              boolean hasAudio, boolean hasError, double amplitude);
```

The listener's construction-time controller generation and event's take ID must
both match Main's current attempt. Capture also checks ID, started state,
`!closed`, `previewRequested` and `!stopRequested` before forwarding. Use the
existing main executor, so Start, Status, tick and Stop mutate state serially.
Do not expose internal audio-source timestamps or pass mutable CameraX objects
to another worker.

Keep the pending take identity needed for Finalize separate from **policy
active**. Manual Stop/background invalidates policy but must not drop the final
saved take. On Finalize clear the matching attempt, remove its tick and save
the validated container duration as today. Do not let an unrelated stale
Finalize clear a newer recording's field if future refactoring allows overlap.

Mutable policy state can be limited to: started time, last accepted stats
duration/receipt time, prior-energy observation counters, quiet-window start
duration/receipt/count, active flag and a terminal stop-request flag. No audio
samples, transcript or amplitude history need be retained.

## Concrete decision rule for the first experiment

These constants are **experimental test parameters**, not measured phone
thresholds: high energy `>=0.020`, quiet energy `<=0.008`, at least three
prior high observations spanning 200 ms, at least three quiet observations
spanning 1,000 ms, and a maximum 500 ms gap between accepted observations.
Require actual phone/input-route calibration before claiming useful behavior.

For every matching Status:

1. Accept only `AUDIO_STATE_ACTIVE`, `hasAudio`, no error, finite amplitude
   within `[0,1]`, nonnegative duration and strictly advancing recording
   duration. Local receipt uses `SystemClock.elapsedRealtime()`. Duplicate or
   regressing stats cannot count as another observation. Nonactive/invalid
   audio resets both quiet evidence and prior-energy eligibility.
2. A gap over 500 ms in receipt time **or** recording duration resets the
   tentative quiet window and prior-energy eligibility. Count spans using both
   clocks. An event backlog delivered in a burst cannot satisfy a one-second
   receipt span, even if stats durations advance. Lack of a public amplitude
   timestamp still prevents proof of fresh underlying samples.
3. Observe prior high energy from Start onward. Reset quiet evidence on energy
   above the quiet threshold; the intermediate band also resets it. Once
   enough high observations establish eligibility, begin a quiet window only
   on valid low observations **at or after planned duration**. Do not cut early
   because a pause occurred before the planned duration.
4. Request automatic stop once quiet count and both time spans qualify, only if
   the latest accepted observation is still recent. Set the terminal flag
   before calling capture stop to prevent duplicate requests. Keep the sequence
   active; all cues stay silent until Finalize and the normal next-shot path.

For every tick, independently of Status delivery:

- If not opted in, preserve the existing planned stop.
- If opted in and wall elapsed reaches `hardMs`, request stop once regardless
  of audio eligibility. CameraX's unchanged 60-second output limit is a second
  ceiling. Recording duration can also trigger this ceiling earlier; no pause
  support is introduced by this plan.
- After planned duration, show “Waiting briefly for a quiet pause · Stop ends
  the take.” At the hard ceiling use “Time limit reached; review your speech.”
  Never say “sentence finished.” No spoken message plays while recording.
- Manual Stop, app background, camera error and controller close override the
  policy immediately and disable its future actions. Returning never resumes it.

If timed stop is disabled and no sequence is active, the quiet option does
nothing; ordinary manual recording and the existing 60-second file ceiling
remain. A planned duration of 60 seconds has zero extension. Disable stop-option
switch changes during countdown/recording/finalization, or state clearly that
they affect only the next take; **Stop take** stays available throughout.

## Proposed checks and honest limits

Before production integration, exercise the pure decision object with monotonic
synthetic events: voice crossing planned time then a valid tail, hesitation
before planned time, long noise, zero-only input, audio disabled/muted/silenced/
error, invalid numbers, duplicate/regressing durations, stale gaps, backlog
bursts, old take/controller IDs, repeated Stop, missing Status and the 8-second/
60-second deadlines. Verify that automatic completion preserves the sequence,
while manual/background cancellation invalidates it and still saves Finalize.
These are proposed checks; none ran for this document.

Then use an explicitly consented attended phone recording and inspect the
original saved speech, including faint voice, sentence-internal pauses,
clothing noise and background music. Confirm immediate Stop/background and
full cue-to-next-shot behavior. Repeat for the actual chosen microphone route;
connected AirPods do not prove recorded AirPods input. Only call the option an
experimental pause heuristic: quiet can occur mid-sentence, background sound
can prevent stopping, and the hard limit can still cut speech. Offline trim
and subtitle tests do not establish any of these live results.

This plan changed only this document. No app, native runtime, permissions,
recording, device commands, build or test execution changed.
