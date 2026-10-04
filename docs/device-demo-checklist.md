# Mini Film — attended phone demo checklist

Prepared 4 October 2026. Start with one outfit, one quiet location and a steady
portrait phone. This guide is for an attended test on the Nothing Phone (3a).
The app is a **pre-event research prototype**, not eligible event-written code.
See [current evidence](status.md) and the [presentation runbook](demo-runbook.md).

## Before filming

- Unlock the phone yourself using its normal secure unlock, then open **Mini
  Film**. Keep it visible. Opening the app does not start the camera or recording.
- Pair AirPods or other earbuds through Android settings. In **02 Direct**, turn
  on **Spoken direction / earbuds** and tap **Hear the direction**. Confirm you
  actually hear the complete cue in the earbuds. “Bluetooth audio available”
  only reports availability; Android chooses playback routing. Test the saved
  take's sound separately: the AirPods microphone is not guaranteed.
- **Start camera** requests camera and microphone permission through Android's
  normal prompts. Allow them only for the shoot you intend to make. If denied,
  the brief, manual plan and imported-clip editor remain the fallback; revisit
  Android app permissions yourself if you want to try capture again.
- Check that the portrait preview, chosen front/back lens and clear floor are
  suitable. Use **Switch front / back** before a take. Start with a stationary
  pose; landmarks do not establish safe walking distance or obstacle clearance.

The current local files live inside the app's private files directory:
`director-model.gguf` for the Qwen CPU planner,
`models/director-mmproj.gguf` for optional single-frame visual notes, and
`models/ggml-tiny.en.bin` for offline English clip transcription. They are
separate from the APK. No provider key is needed. If a model is unavailable,
use the visibly labelled editable starter or your own notes/text; do not call
that fallback a generated result. Setup/provenance is in the
[app README](../prototype/phone-director/README.md).

## 1. Pose: review a fashion plan

Optional voice brief: in Brief, tap **Record a local voice brief**. Allow the
normal microphone prompt, then tap Record again when ready. Speak a short English
brief and use **Stop & review words** before 45seconds. Correct the full draft,
keep it within 500characters, then explicitly choose **Use this brief**. Keep
typed brief leaves your words unchanged. Cancel or leaving the app discards
recording; it never starts planning/filming. **Use phone dictation** separately
requires Android's offline English model, currently not installed on this phone.
Local audio-only remux and AAC encoding/transcription checks passed, but actual
microphone capture and these visible controls still need attended acceptance.
Six synthetic voice-review checks and seven reference-speech checks now pass on
a fresh emulator, with denied capture permissions. They do not replace this
attended recording check. See [interface evidence](emulator-ui-evidence.md).
Check that leaving the app during a brief or transcription restores editable
controls after return and does not apply a draft. If recorder release cannot be
confirmed, the app refuses a new microphone session; follow its status before
retrying and inspect Android's microphone indicator.

1. In **01 Brief**, select **Fashion** and describe only the garment details you
   know: for example, “A short reel wearing my jacket; relaxed poses in one
   location.” Tap **Build my shot plan**. Allow about a minute for local planning;
   previous fixture plans took roughly 25–53 seconds, not a live speed guarantee.
2. Read all five shots. Use **Edit shot** to fix invented details, direction,
   duration or caption; **Move earlier** changes their order. Keep the plan
   useful for you as the performer. A saved AI plan or starter must be labelled
   honestly when presenting it.
3. Optional reference: **Choose a reference reel** opens Android's file picker.
   Choose a local clip you are allowed to inspect. Sparse observations are
   approximate. **Describe a reference frame locally** lets you choose a source
   time and inspect one frame; **Review reference notes** shows its thumbnail
   and editable notes. Correct guesses, then select **Use corrected notes**
   before building another plan. You can also type the desired beats in your
   brief. One frame is not an understanding of the whole trend.
   **Read reference speech locally** adds an optional English transcript from
   that selected video's audio. Check the complete timed draft, then write
   1–210 corrected characters of useful context and choose **Use speech context**.
   The dialog shows both the context count and the combined 500-character limit,
   including the typed brief, visual notes and labels. Shorten inputs yourself
   if they do not fit; the app does not truncate them. **Edit reviewed speech
   context** changes saved corrected words without reading the video again.
   This never starts filming or builds a plan automatically. Check that cancel,
   backgrounding and changing the selected video reject old draft actions;
   same-source confirmed context should remain after cancellation. Speech does
   not establish music beats, spoken facts or the complete trend's story. See
   [reference speech evidence](reference-speech-evidence.md) for measured checks
   and the remaining unlocked acceptance gate.
   **Review three reference moments** instead samples three increasing times;
   defaults use the selected clip's measured duration. Review the thumbnails,
   correct selected notes to short shot cues, or exclude an unclear moment.
   **Use reviewed moments** explicitly saves the selected cues. Saved boards
   retain text/times/selection rather than images; re-read to see frames again.
   Unconfirmed drafts and typed corrections are temporary across recreation.
   Fashion plans without an explicitly named creator-reviewed Detail use a
   visibly labelled creator-choice detail constraint, including ordinary briefs.
   This deliberately does not extract free-text garment features. Use a named
   reviewed Detail or edit the shot to specify one yourself. Other shots remain
   model drafts: inspect their pose, caption and facts before recording.
   Name one shot and an action in a note to retain your own direction, such as
   “Side pose: Pause in profile”. These are creator-authored instructions kept
   after generation, with a separate label; captions remain editable model drafts.
   A malformed or duplicate named cue asks for correction before planning.
   Observed garment-destination movement phrases and one-letter caption labels
   are rejected, but the filters are narrow. Body poses toward a mounted camera
   are allowed; bounded device-handling wording is rejected. Other roles can
   still invent facts, and even readable captions can be generic. Review them.
4. Tap **Let's direct this reel →**, then **Start camera**. Check the live preview
   and written pose cue. “Review needed” means insufficient landmark evidence;
   use your own framing judgment. Detail/object shots may disable person advice.

## 2. Perform: take one shot, then the sequence

1. First test one short take. With the sequence switch off, hear the direction,
   then tap **Record · 3 sec**. Confirm the spoken countdown finishes, recording
   starts, and directions stay quiet. Speak a short line if you want captions.
   **Stop take** cancels a countdown or stops the take; **Stop at planned shot
   length** enables its planned stop. Check that a canceled countdown/pose break
   returns to a ready label. A take that has already begun waits for saving;
   turning the sequence off does not cancel that current recording. Wait for
   the take to finish saving.
   An Android noisy-output or active-cue focus interruption pauses preparation
   and future sequence advancement. Automatic pose speech stays paused; check
   your earbuds, then explicitly use **Hear the direction** or **Record** to
   resume. A take already recording is kept. Test physical disconnection and
   audio-focus behavior yourself; synthetic events do not prove AirPods routing.
   For a talking take, optionally enable **Wait for a quiet pause · experimental**
   before recording. It is off by default and can extend a timed/sequence take
   by at most eight seconds, within the 60-second cap. It measures sound energy,
   not sentence completion; faint speech, music or a mid-sentence pause can fool
   it. Settings apply to the next take. Check the saved speech and immediate
   Stop/background behavior before relying on it in a demo.
2. Use **End shoot & review**, then **Preview take** in Assemble. Playback opens
   paused; press play yourself. Check picture, orientation, duration and sound
   before relying on a longer shoot.
3. For the five-shot rehearsal, return to Direct and enable **Guide the full shot
   sequence**. Start the camera, then tap Record. It speaks each reviewed cue,
   gives an eight-second pose break, speaks the countdown, records quietly and
   moves to the next shot. This complete live sequence is still an attended
   acceptance check. Keep **Stop take** reachable throughout.

**Stop take** ends the sequence and recording; it can leave the preview open.
**End shoot & review** ends the camera session. Leaving the app stops camera,
microphone and cues; returning requires a fresh action and does not resume the
sequence. Confirm these behaviors on the real phone before a presentation.

## 3. Assemble: review the cuts and save both outputs

1. In **03 Assemble**, select the takes you want; deselect failed takes. Use
   **Select no takes** clears selection for a new reel without deleting takes. Use
   **Move earlier**, **Trim & typography** and **Preview take** to review order,
   exact in/out times and manual text. Originals remain intact. Keep the result
   within 12 selected cuts / three minutes.
2. For spoken takes, try **Suggest a tighter talking cut**. Inspect the proposal,
   use **Preview suggested cut**, press play and listen. Return to **Review
   suggested speech cut** to choose **Apply trim** or **Keep current cut**.
   This is a proposed speech-boundary trim, not a judgment of the best performance.
3. **Generate offline subtitles** transcribes the existing clip locally; it does
   not open the microphone. Use **Review subtitle words & timing**, correct the
   English words and source timestamps, then **Save reviewed subtitles**.
   Remove a bad draft or use manual text. Short cues fit up to four lines.
4. Choose **Clean**, **Warm**, **Cinematic** or opt-in **Auto balance**. These are
   deterministic presets or sampled color/exposure correction, not learned taste.
   Tap **Export my reel**, keep the app open, then **Play last exported reel**. The
   vertical MP4 is saved under **Movies / MiniFilm**; inspect it before sharing.
5. Tap **Save clips + edits for my laptop** and choose a writable local folder in
   Android Files. The ZIP contains selected **whole originals**, hashes, relative
   media paths and reviewed edits/subtitles; up to 512 MB of originals. It is a
   portable edit package, not a proven desktop-editor importer. **Save ready edit
   package** retries a prepared package. Folder errors, cancellation and leaving
   the picker keep it available; choose a new name if a partial document exists.
   Changing cuts, selection, captions, title or look invalidates that cached
   package so the next save rebuilds your current edits. **Save last exported cut list to Files** saves
   the smaller JSON separately; that ordinary cut list refers to phone sources
   and the last export. Re-export after edits to update the reel/cut list.
   Actual emulator Files saving passed with generated clips and exact ZIP/source
   verification. Physical Files/providers and Office Kit still need an attended check.

## If something stalls or disagrees

- **Cancel local processing** stops a pending model, subtitle, color, export or
  package operation. Preserve originals and review the status before retrying.
  An interrupted Files save may leave a partial destination: save under a new
  name. It does not replace **Stop take** during filming.
- If a model is slow, let it finish while the app is visible, or cancel and use a
  reviewed saved plan/manual notes. A canceled frame job may need a moment to
  release shared model resources before a new plan starts. Unapplied trim/frame
  reviews are temporary: if Android recreates the app, request them again;
  saved cuts and confirmed notes stay saved. Cancel suppresses a late decode
  result but cannot guarantee an immediate stop of a blocking file provider.
- If pose advice abstains or is wrong, follow the preview and your scene cue.
  Do not lower confidence or treat missing joints as observed. The public cropped
  portrait check abstained; a separately encoded, letterboxed crop supported
  head-and-shoulders. These few fixtures do not establish general framing quality.
- If offline speech fails before a take, recording pauses. Turn spoken direction
  off for a visual countdown, or fix Android's offline English voice/output and
  rehear a cue before retrying.
- **Try a synthetic demo** in Brief or **Try synthetic demo clips** in Assemble
  exercises editing without camera/microphone use. Label the result synthetic.

## What the demo may truthfully establish

Fixture-based checks have exercised local CPU planning, public-image pose and
single-frame subject inference, offline English synthesis/transcription,
trimmed subtitle rendering, real vertical MP4 assembly, color correction and
portable ZIP integrity. Sparse reference measurements and pose abstention are
limited observations, not demonstrated trend or general framing accuracy.

Real creator capture, audible AirPods output/input, the complete live sequence
and sustained camera/model concurrency remain untested. iQOO execution, NPU
acceleration, Office Kit transfer, DJI/Action 4 control and a general creator
benefit are not established. Keep the phone demo complete without a drone.
Competition implementation must be created in the allowed event window unless
organizers approve reuse; this preparation app must not be relabelled as such.
