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
   In **Edit shot**, set **Framing target** for each planned shot: Full outfit,
   Face & shoulders, Object / detail or Manual preview only. Scene default is
   only a suggestion. Save the choice and keep direction/caption consistent;
   target changes do not rewrite them. Cancel preserves the shot. Save keeps your
   previous position. Use whole lengths from2 to60 seconds; invalid entries
   stay open for correction without applying any fields.
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
   paused in the centered9:16 reel crop; the label explains that export text/color
   are absent and source audio is unchanged. Press play yourself. Check picture,
   orientation, duration and sound
   before relying on a longer shoot.
3. For the five-shot rehearsal, return to Direct and enable **Guide the full shot
   sequence**. Start the camera, then tap Record. It speaks each reviewed cue,
   gives at least eight seconds for posing with current person-framing advice.
   With spoken direction enabled, one pending framing hint finishes before the
   countdown. Speech failure pauses preparation. It then records quietly and
   moves to the next shot. This complete live sequence is still an attended
   acceptance check. Keep **Stop take** reachable throughout.

**Stop take** ends the sequence and recording; it can leave the preview open.
**End shoot & review** ends the camera session. Leaving the app stops camera,
microphone and cues; returning requires a fresh action and does not resume the
sequence. Confirm these behaviors on the real phone before a presentation.

## 3. Assemble: review the cuts and save both outputs

If a take is missing after an interruption, choose **Find saved phone takes**.
Readable saved phone videos are added unselected with surviving shot details;
review and select them yourself. The app also checks on idle resume and entry
here. Incomplete files or unreadable notes stay preserved. A successful synthetic
recovery check does not establish that a real interrupted recording is playable;
check it with **Preview take**. [Recovery evidence](capture-take-recovery-evidence.md).

1. In **03 Assemble**, select the takes you want; deselect failed takes. Use
   **Select no takes** clears selection for a new reel without deleting takes. Use
   Saved-cut framing observations now show a qualified **Current plan target**
   only for one exact current association. Multiple/unmatched/default associations
   ask for manual review; the target does not prove captured intent or quality.
   **Preview take** opens paused playback directly. **Edit & review take** opens
   that take’s scrollable tools menu: **Move earlier**, **Trim & typography**,
   shot assignment, subtitle tools and framing review. Cancel keeps the edits;
   leaving the screen closes this menu. Use the tools to review order, exact
   in/out times and manual text. Originals remain intact. Keep the result
   within 12 selected cuts / three minutes.
   Selection duration and **Export my reel** stay above navigation while scrolling.
   Preview return keeps your list position. Export is disabled for an empty
   selection and during processing; review your chosen cuts before exporting.
   For a saved take, optionally tap **Review cut framing**. It reads three nearby
   frames at the current cut’s quarter, midpoint and three-quarter source times;
   no camera, microphone or playback starts. Sources must be at most three minutes
   and the cut at least 0.25 seconds. Inspect each approximate centered vertical
   thumbnail and its plain local pose hint. “Framing needs your review” or no clear
   person is insufficient evidence, not a failed take.
   These are requested seeks, not exact decoded timestamps; a nearby frame can
   lie outside the cut. Three samples cannot judge motion, garment details or
   shot quality. The crop precedes final text/color effects: preview the actual
   exported reel too. **Done** dismisses the temporary observations; trims,
   subtitles, selection and shot assignments stay unchanged. Cancel or leaving
   the app discards the review; returning does not resume it. Request a new review
   after changing a cut. See [cut-framing checks and limits](take-framing-evidence.md).
2. For spoken takes, try **Suggest a tighter talking cut**. Inspect the proposal,
   use **Preview suggested cut**, press play and listen. Return to **Review
   suggested speech cut** to choose **Apply trim** or **Keep current cut**.
   This is a proposed speech-boundary trim, not a judgment of the best performance.
3. **Generate offline subtitles** transcribes the existing clip locally; it does
   not open the microphone. Use **Review subtitle words & timing**, correct the
   English words and source timestamps, then **Save reviewed subtitles**.
   For several takes, choose **Draft missing subtitles for selected takes**.
   It skips existing subtitle edits and unselected takes. Choose at most 12 takes
   needing words, each source no longer than three minutes; the reel still has
   its separate three-minute total limit. Identical source URIs are read once,
   and progress counts completed unique clips. New drafts have
   `whisper-tiny.en-draft` provenance and still require per-take word/timing
   review. No microphone or playback starts. **Cancel local processing** or
   leaving the app keeps completed drafts and waits for the current reader to
   stop; returning does not resume the batch. Start it again yourself if needed.
   Fifteen core/UI/assembly checks passed on a fresh capture-permission-denied
   emulator (13.077s). A synthetic headless phone batch passed (16.177s): two
   serial CPU reads supplied three independent drafts, kept manual/unselected
   takes and originals intact, then exported a three-cut 720×1280 reel plus JSON
   (20.387s output from 20.251s nominal cuts). At most one reader was observed.
   These results do not replace your own recorded-speech or physical UI check;
   AirPods and creator capture remain unverified. See
   [batch subtitle evidence](subtitle-batch-evidence.md).
   Remove a bad draft or use manual text. Short cues fit up to four lines.
4. Choose **Clean**, **Warm**, **Cinematic** or opt-in **Auto balance**. These are
   deterministic presets or sampled color/exposure correction, not learned taste.
   Tap **Export my reel**, keep the app open, then **Play last exported reel**. The
   vertical MP4 is saved under **Movies / MiniFilm**; inspect it before sharing.
5. Tap **Save clips + edits for my laptop** and choose a writable local folder in
   Android Files. The ZIP contains selected **whole originals**, hashes, relative
   media paths and reviewed edits/subtitles; up to 512 MB of originals. Current
   plan IDs, order, names, directions, captions, durations and source label are
   copied at dispatch into the edit documents. With a nonempty plan, the ZIP
   includes readable `shoot-notes.txt` showing directions and selected-cut
   assignments with source-relative times. Exact current IDs resolve to names;
   unknown/earlier-plan IDs stay unresolved. This is saved context, not approval
   of AI directions or a judgment of the footage. It is a
   portable edit package, not a proven desktop-editor importer. **Save ready edit
   package** retries a prepared package. Folder errors, cancellation and leaving
   the picker keep it available; choose a new name if a partial document exists.
   Changing cuts, selection, captions, title, look or the plan's text/order/source
   invalidates that cached package so the next save rebuilds your current edits.
   A package that finishes after its dispatch state changes is also rejected;
   follow **Create a fresh edit package** when asked. **Save last exported cut list to Files** saves
   the smaller JSON separately; that ordinary cut list refers to phone sources
   and the last export. Re-export after edits to update the reel/cut list.
   Actual emulator Files saving passed with generated clips and exact ZIP/source
   verification. Physical Files/providers and Office Kit still need an attended check.
   The plan-context increment passed 26 physical-phone checks (7.049s) and
   22 fresh-emulator checks (85.366s), including four new lifecycle methods.
   A real synthetic 720×1280 export with frozen plan/mappings measured 1043ms
   from a 1000ms nominal cut and yielded an independently decoded frame. These
   checks do not prove a creator recording, audible AirPods or desktop-editor
   compatibility. See [plan export evidence](shot-plan-export-evidence.md).

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
- A canceled cut-framing review may need a moment before a fresh review can
  start. A slow pose check can still finish later after timeout/cancellation;
  cancel does not guarantee an instant backend stop. If it repeatedly times out,
  inspect the thumbnails yourself instead of repeatedly restarting the check.
  The full pending-task limitation is recorded in the evidence link above.
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

Saved-cut review has separate public/flat-frame and real normal/rotated Media3
crop checks, plus five fresh-emulator lifecycle checks. The two initial failures
were test-only rotation-convention and stopped-Activity polling errors; corrected
checks passed without production changes. These results establish sampled local
execution and ownership, not useful judgment of your performance.
[Measured results and retained failures](take-framing-evidence.md).

Real creator capture, audible AirPods output/input, the complete live sequence
and sustained camera/model concurrency remain untested. iQOO execution, NPU
acceleration, Office Kit transfer, DJI/Action 4 control and a general creator
benefit are not established. Keep the phone demo complete without a drone.
Competition implementation must be created in the allowed event window unless
organizers approve reuse; this preparation app must not be relabelled as such.

### Returning-user lens and direction check

While preview is active, switch front/back once and wait for the camera to
finish opening. A second tap during binding should ask you to wait. Hear the
full direction and confirm automatic framing advice does not interrupt it;
after a shot change/background return, advice should belong to the current
shot. Source and synthetic callback checks passed in
[shoot-cue evidence](shoot-cue-evidence.md); actual capture, audible output and
Bluetooth buffering still require this attended check.

### Check visible subtitle words and camera retry

If camera startup fails, choose the other lens and explicitly Start camera
again. The pending state and old advice should end before the error is shown;
live hardware retry remains an attended check.

When reviewing subtitles, one cue must end no later than the next begins.
Overlap correction preserves the draft fields and current saved edits; adjust
the times yourself, then Save. Touching endpoints are accepted. Export and
laptop packaging also stop before creating output when overlaps remain.
See [subtitle evidence](subtitle-overlap-evidence.md) for the synthetic cases;
verify real spoken words/timing and sound before sharing your reel.

## Review the missing-shot pickup

In Assemble, preview each take, then use **Assign take to plan shots** to confirm
which current shots it serves. A clip can serve several shots. Leave uncertain
clips unassigned; unselected clips do not count. The summary tracks your choices,
not an AI judgment of framing, garment visibility or image quality.

To try the first fashion workflow, deliberately leave the detail shot without an
assigned selected take. **Direct next missing shot** chooses the next missing
item in plan order and opens its direction. It leaves the camera stopped until
you tap **Start camera**. After filming, preview and assign the new take yourself.
Replacing the plan keeps old takes/assignments but they do not satisfy the new
plan; review and remap them if wanted. Save/Clear is explicit, Cancel keeps the
previous assignment, and the portable edit document retains confirmed mappings.
[Verified research checks and limits](shot-assignment-evidence.md).

After editing or reordering the plan, rebuild the reel/package before transferring
it. The laptop copy keeps the plan and source label captured for that export;
the phone's later edits cannot change an already saved copy. Read
`shoot-notes.txt` alongside `project.json`, then check the actual clips yourself.
This portable handoff remains separate from Office Kit transfer or a native
desktop-editor project. [Plan context and tested limits](shot-plan-export-evidence.md).

## Keep another moment from a take

In **Edit & review take**, choose **Use another moment**, set Start/End seconds
within the original video and give the cut a useful title. Save adds it directly
beside the original, unselected. Preview/review the words, then check it if it
belongs in the reel and reorder as wanted. You can keep an introduction and a
closing line while leaving the middle out. The source video stays intact; both
cuts can be edited independently and the laptop ZIP copies that original once.
Timed words retain source times, so review which words are visible in each cut.
If existing subtitle timestamps overlap, correct them before opening the cut
editor. Cancel applies nothing. [Tested cases and limits](another-moment-editor-evidence.md).
