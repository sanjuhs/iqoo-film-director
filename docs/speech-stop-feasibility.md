# Pause-aware recording stop — feasibility only

Read-only review, 4 October 2026. A bounded, opt-in **wait for a quiet pause**
could reuse CameraX's existing recording audio statistics, without another
microphone stream. It would measure sound energy, not speech or sentence
completion. No production behavior changed and no hardware audio test ran.

## Current behavior and available API

The current MainActivity timer stops a take at its planned length when automatic
stop or the full sequence is enabled. CaptureController already starts CameraX
with audio, but handles Start/Finalize rather than forwarding Status. A spoken
take can therefore be cut while the creator is talking. Manual stop and later
speech-edge trim are available; neither reconstructs speech already cut off.

The installed dependency is `androidx.camera:camera-video:1.4.2`. Its cached AAR
SHA-256 is `f26d4a7a0cc63b40b71854b7c0d702ed50cf2bbf2a843f40bb3d7231f0360479`.
Read-only `javap` inspection of its embedded classes confirmed:

```java
event.getRecordingStats().getAudioStats().getAudioAmplitude(); // double
event.getRecordingStats().getAudioStats().getAudioState();     // int
event.getRecordingStats().getAudioStats().hasAudio();          // boolean
event.getRecordingStats().getAudioStats().hasError();          // boolean
event.getRecordingStats().getRecordedDurationNanos();         // long
```

The public amplitude method was added in 1.4.0. Android describes normalized
maximum absolute sampled amplitude, from 0 to 1; it is not a transcript or voice
activity result. `AUDIO_AMPLITUDE_NONE` equals zero when recording audio is
disabled. Audio states distinguish active, disabled, system-silenced, encoder
error, source error and explicit mute. Only active, error-free recording audio
would qualify for a quiet-tail heuristic.
[AudioStats reference](https://developer.android.com/reference/androidx/camera/video/AudioStats).

`VideoRecordEvent.Status` supplies a recording-in-progress report through the
existing event listener. Its associated recording duration is measured at stats
generation, excludes paused intervals and is not an amplitude capture timestamp.
No public amplitude age, audio packet timestamp or guaranteed status cadence was
found in the 1.4.2 ABI. Use recording duration plus local callback receipt time
to reject stale/nonadvancing events, while admitting that this cannot prove how
fresh the sampled amplitude is.
[Status reference](https://developer.android.com/reference/androidx/camera/video/VideoRecordEvent.Status),
[RecordingStats reference](https://developer.android.com/reference/androidx/camera/video/RecordingStats).

## What the pinned implementation additionally shows

Local bytecode inspection found `AudioStats.hasAudio()` is true only for
`AUDIO_STATE_ACTIVE`. `getAudioAmplitude()` returns zero specifically for
DISABLED; other states return the stored internal value. Muting, silencing or an
audio error must therefore reset quiet evidence rather than interpreting the
value as a valid pause.

The pinned internal `audio.AudioSource.postMaxAmplitude(ByteBuffer)` measures the
largest absolute signed PCM16 sample and divides by 32767. Its other-format
branch does not calculate that value. This is an implementation observation,
not an application API or proven phone input format. The internal update path
compares packet nanosecond timestamps against a literal 200; it does not justify
a promised 200 ms public sampling interval. The current public documentation's
sampling-time wording also does not establish a reliable application cadence.
Do not tune a quiet window from either statement without an actual recording
trace. No internal CameraX method needs to be called by the app.

## Small bounded experiment proposed

This is a proposed policy, not implemented or validated behavior:

1. Keep precise timed stops for fashion actions. Offer an explicit pause-aware
   option for spoken takes, explaining that it may extend the shot briefly and
   still has a hard cap.
2. After the planned duration, allow a small maximum extension, for example
   eight seconds, within the existing 60-second recording/file limits. Stop at
   an observed low-energy tail only after sufficient prior energy was observed.
   This prevents a silent take or missing audio from being mistaken for a
   successfully completed sentence.
3. Require several fresh, advancing Status observations over a conservative
   quiet interval, for example one second. Choose the energy threshold only
   after measuring phone-mic noise, quiet speech, clothing sounds and each
   intended input route. These example times are design parameters, not measured
   accuracy. Reset the candidate pause on renewed energy, event gaps, pause,
   nonfinite/out-of-range levels, mute, silencing, missing audio or errors.
4. If evidence is unavailable, continue only to the declared hard cap; explain
   the fallback rather than declaring speech finished. A hard cap can still cut
   a sentence. Loud background sound can prevent a quiet tail, and a pause in
   the middle of a sentence can trigger it. Manual stop remains the reliable
   creator decision.
5. **Stop take**, app backgrounding, camera failure and cancellation must retain
   their immediate existing stop path; they never wait for quiet. Scope all
   observations to the current recording/session so an older take cannot stop
   a new one. Keep spoken cues quiet throughout any extension and start the
   next sequence cue only after the take has finalized.

Store no continuous audio-level history or extra private data by default. A
future change would observe statistics from the explicitly started recording,
not create an always-listening microphone or run live transcription beside it.
Actual container metadata remains authoritative for the saved clip's duration.

## Evidence required before enabling it in a demo

First test the decision policy with synthetic event sequences: sustained voice,
short hesitation, valid quiet tail, never-quiet noise, silent/no-audio startup,
muted/error states, invalid levels, stale/gapped events, old-session delivery,
hard cap and immediate Stop/background override. Then conduct a consented
attended recording test using the phone microphone and the real intended
earbuds/input routing. Check saved audio/video and actual stop latency, including
quiet speech and pauses within a sentence. Available Bluetooth devices do not
prove which microphone recorded a take.

The existing offline English caption/speech-trim fixtures do not validate live
levels, speech completion, this policy, AirPods input or a reduction in retakes.
No API/dependency download, new audio permission, camera/microphone action or
app build was performed for this review. The helper UI repair is separate from
this future recording behavior.
