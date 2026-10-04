# Reviewed reference speech — 4 October 2026 research

This addition reads English speech from a selected local reference video using
the installed Whisper tiny.en CPU path. It does not open a microphone or play
the reference. It is dated pre-event research, not competition-window code.

## Creator workflow and boundaries

In Brief, select a reference video and choose **Read reference speech locally**.
The full timestamped draft remains visible for review, including words beyond
the planning budget. Write 1–210 corrected characters of useful spoken context,
then explicitly choose **Use speech context**. This preserves the typed brief,
current shots and visual notes; it does not generate a plan or start filming.

Both this review and **Edit reviewed speech context** show the concise-context
count and combined planning count. The combined brief, spoken context, visual
notes and structural labels must fit 500 characters. Overflow is rejected;
nothing is truncated or dropped, and earlier confirmed context remains intact.
The creator can shorten their inputs and explicitly build a new shot plan.
Editing saved corrected words requires no further source or model access.

Confirmed text is saved with its selected source URI and a versioned reviewed
marker. It restores only for that exact source. Audio, full transcript cues and
unconfirmed edits are not persisted by this feature. Changing the source clears
its speech context; cancellation or backgrounding keeps earlier confirmed words
for the same source and dismisses the new draft. Selected original media are
never deleted. Clearing visual reference notes is separate from clearing speech.

## Request ownership

The app borrows its existing ClipTranscriber only while other local work and
voice recording are idle. Its request captures the source, reader identity and
generation. On terminal completion or cancellation, closeWhenIdle waits for that
exact native request/decoder to release before releasing the reader barrier.
Replacement affects only the matching shared reader. It has no original-file
deletion hook.

Reader cleanup and ownership of the busy state are distinct. If a replacement
reference starts frame inspection while an old speech request drains, repeated
cancel/background cleanup cannot clear the newer inspector's busy state. Cancel
still reaches that newer operation. Source changes also invalidate reference
visual callbacks before they can change notes or enable controls. These are
implementation contracts; attended callback/dialog acceptance remains separate.

The speech section precedes the complete canonical reviewed-moments suffix in
the planner input. The existing named-shot parser therefore reads only explicit
reviewed visual shot cues. Role-like speech dialogue cannot become a retained
manual direction merely by containing a shot name.

## Device evidence — 17:37 IST

The Nothing Phone (3a)/Android 16 headless runner passed **seven methods in
47.335 seconds**: six data/parser checks and one actual speech-to-plan pipeline.
The labelled synthetic container measured **8,759 ms**. Local tiny.en returned
three source-bounded draft cues in **4,516 ms**, with the expected jacket/green/
outfit words. A test-authored correction and reviewed moments produced a
**216-character** combined input. Qwen3.5 0.8B CPU returned five bounded shots in
**42,400 ms**; callbacks arrived on the main loop and both workers/core ownership
released. Original SHA-256, size and modification time remained unchanged.

Hero and Closing retained the explicitly authored `face left` / `face forward`
cues with the creator-retention label. Detail retained the separately labelled
creator-choice constraint; Movement and Side pose were editable model drafts.
Four-second shot lengths and generic captions are not evidence of artistic
quality or learned speech grounding. The complete labelled synthetic output and
callback metrics were logged before assertions for failure provenance.

A related runner passed **six methods in 2.828 seconds**: three real frame-decoder
checks, two board cancellation/selection/bitmap checks without vision loading,
and read-only app-owned gallery inventory. The latter found 19 rows, zero pending
and 5,598,886 logical bytes, matching the previous snapshot. This does not test
MainActivity's visible dialogs or the actual timing of a replaced-source UI job.
Seven future synthetic UI methods are compiled and **unrun** behind keyguard.

Both APK builds/installations passed. Built/saved/independently read installed
app bytes match: **52,838,059 bytes**, SHA-256
**90ad51be1bedab35941cf084c97f1bcb418e96ad7c6976fafa51d60c4bd9708f**.
Publisher notices remain unchanged; separate Qwen/Whisper weights are absent
from Git/APK, with bundled ML Kit dependency assets still present. The installed
package has no INTERNET/ACCESS_NETWORK_STATE permission. CAMERA/RECORD_AUDIO
remained denied and keyguard showing before/after. A normal launch was requested
afterward; no attended visible launch is claimed.

Raw logs and APK checks are retained locally under ignored `private/evidence/`:
`reference-speech-build.log`, `reference-speech-install.log`,
`reference-speech-device-test.log`, `reference-speech-metrics.log`,
`reference-source-regression-test.log`, `reference-speech-gallery-metrics.log`,
`reference-speech-installed-apk.json` and `reference-speech-final-device.json`.
The initial gallery log collector used the wrong tag; its result was recovered
from the scoped MiniFilmStorageTest buffer rather than inferred from an empty log.
No runner failure, model/SDK download, private capture/playback/upload, permission
bypass, aircraft action or original-file deletion occurred in this increment.

## Reproduction

Use the existing labelled `files/fixtures/jacket-speech-padded.mp4` and installed
pinned tiny.en/default Qwen CPU models; their provenance is in
[transcription evidence](transcription-evidence.md) and
[local AI evidence](local-ai-evidence.md). Do not use private footage or download
another model for these checks. Build/install the app and test APK with the
existing tools, then select only the headless classes:

```sh
adb -d shell am instrument -w \
  -e class dev.minifilm.director.ReferenceSpeechContextTest,dev.minifilm.director.ReferenceSpeechDeviceTest \
  dev.minifilm.director.test/androidx.test.runner.AndroidJUnitRunner
```

ReferenceSpeechContextTest checks defensive cue copies, full draft text, explicit
review, URI/time limits, strict confirmed JSON/source matching, exact 500/501
boundaries and the real planner's named-cue parser. ReferenceSpeechDeviceTest
reads the synthetic video's actual audio, verifies known words and source-time
bounds, explicitly supplies a test-authored correction, composes the input with
reviewed moments and requests one actual local Fashion plan. Retained named cues
are creator-authored; they do not prove learned speech grounding.

ReferenceSpeechUiTest requires a normally unlocked phone and denied capture
permissions before altering/restoring its synthetic preferences. It uses no
microphone, ASR inference or audible playback. Those prepared checks must not be
run behind keyguard or reported as passed merely because they compile.

## Practical limits

English drafts can be wrong. Encoded local audio is required, up to three minutes;
silent videos, unsupported codecs, unavailable source access and absent models
retain manual text. Reading speech does not establish music beats, spoken facts,
whole-reference story understanding, creative quality or creator benefit. The
phone's live camera/microphone, AirPods, iQOO/NPU and Office Kit remain separate
acceptance gates in the [completion audit](completion-audit.md).

Source access follows the existing user-selected document workflow and normal
Android permissions. Lifecycle handling follows
[official Android guidance](https://developer.android.com/topic/libraries/architecture/lifecycle);
selected-media grant boundaries are described by
[Android's media-selection documentation](https://developer.android.com/training/data-storage/shared/photo-picker).
