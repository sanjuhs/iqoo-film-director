# User-goal completion audit — 4 October 2026

The **pre-event phone prototype is installed and its local media pipeline has
real fixture evidence**, including reviewed speech-edge application and local image input. The attended Pose → Perform → Assemble workflow and
competition delivery remain incomplete. A fresh synthetic emulator now passed
25 interface methods and one actual DocumentsUI ZIP save; physical capture/audio
still needs attended acceptance. This audit separates current source,
passed checks and observed failures. Latest focused Fashion and Talking repairs passed17 methods across three
runners. Earlier invented-shot failures remain retained; creative accuracy
across unseen briefs and the attended workflow remain unverified.

Evidence: [status](status.md), [capture](capture-evidence.md),
[local AI](local-ai-evidence.md), [export](export-evidence.md),
[transcription](transcription-evidence.md), [portable edits](portable-edit-evidence.md)
and [Office Kit research](office-kit-research.md). Older statements in those
ledgers retain their dated context; the results below include the latest parent
runner reports and direct synthetic-output review.

## Acceptance against the user's request

| Requirement | Established now | Remaining gate |
| --- | --- | --- |
| Activate the app through ADB | Fresh Mini Film APK built, installed and launched on Nothing Phone (3a), Android 16. Synthetic takes/reel and editable plans have been saved. | Required iQOO hardware validation and attended visible launch. Latest source checkpoint is installed. |
| Clean three-phase experience | Brief, Direct and Assemble, editable plans/cuts, paused local preview, visible Start/Stop design and explicit camera start. Latest fresh-emulator run passed 25 interface methods; screen/edits restore without capture, and trim form scrolls. | An unlocked attended shoot and first-time usability check; the older locked-phone failures below retain their dated context. |
| Substantial local AI | Actual local CPU Qwen generation, bundled ML Kit pose inference, offline English TTS and whisper tiny.en clip transcription. App has no network permission. | Useful grounding across unseen briefs, sustained camera/inference concurrency and iQOO execution. No NPU/GPU, Kev adaptation or trained-director result. |
| Pose and direct each take | Live analysis integration, shared vertical crop, persisted lens and optional speech-completion-gated sequence in source. Public pose fixture yielded 33 confident landmarks; empty-frame inference yielded none. | Live framing usefulness, front/rear recording, full sequence, interruption and quiet captured speech. Synthetic geometry is not live capture. |
| AirPods direction | Offline voice synthesis and two real-engine silent completion/cancellation tests passed. | Paired AirPods playback, microphone routing and physical cue leakage. Synthesis-to-file never played audio. |
| Understand reels/trends | Sparse-reference and single-frame CPU vision/pose checks passed. Local reference speech → explicit test correction → CPU plan passed; confirmed words and visual notes share a bounded input. | Full trend/story/style understanding and real reference accuracy remain unverified. Source-binding/review checks passed on a fresh unlocked synthetic emulator; physical reference accuracy, observed model errors and conservative abstentions remain. |
| Start, stop and trim | Explicit CameraX start/stop, actual-container source bounds, editable cuts and reviewed local speech-edge suggestions; actual synthetic cuts exported in order. | Attended capture/finalization and semantic selection of the best performance moments. |
| Color correction | Local presets plus heuristic Auto balance have actual analyzer/export evidence below. | Real footage, creative suitability and shot matching. This is not learned AI grading. |
| Subtitles and typography | Local English drafts, reviewed words/times, real burned-in captions and fit-or-reject handling passed. One-action selected missing-subtitle batching now passed six lifecycle/six UI methods and a real serial two-source CPU → three-draft → 20.387-second reel/JSON test, preserving existing words/originals. [Batch evidence](subtitle-batch-evidence.md). | Attended speech accuracy and longer real text. No karaoke, generated music or semantic caption polishing. |
| Local finished reel and editable laptop pack | H.264/AAC 720×1280 synthetic output, reviewed timeline/JSON and ZIP with selected originals, relative paths and hashes. | Real creator reel, attended sound review, physical Files/provider saving, desktop-editor compatibility and actual Office Kit transfer. Actual emulator DocumentsUI ZIP/source validation passed. |
| Recovery | Five staged tests, genuine ENCODING interruption and real READY/pending interruption under a test-controlled main-queue barrier passed fresh-process recovery on synthetic media; originals and prior completed pair stayed unchanged. | Natural publication timing, kill after publication before COMPLETE, revoked/missing real inputs and live recording. Ready-package UI lifecycle passed under valid fresh-emulator visible conditions. |
| Drone demo | Imported clips can join the edit; no aircraft action or connection occurred. | Separate supported Mini 4 Pro propellers-off read-only probe, then documented pilot-approved capture/control gates. Neo 2/Fly and Action 4 remain research. |
| Hackathon completion | Signed-in idea deadline date is 5 October; countdown implies about 23:59 IST. Idea/pitch assets prepared. | Human attestation/self-report and accepted receipt. Finale is 9–11 October; competition code must be event-written unless organizers approve reuse. |

## Current source repairs

Latest local voice-brief source provides explicit record/stop/cancel, English
Whisper draft review and explicit 1–500-character application. Android en-US
metadata reported not installed, so optional phone dictation is not assumed
ready. Fourteen fake-recorder checks and eight request/cleanup checks passed;
the audio-only AAC fixture initially failed its EOF helper, then its corrected
focused run passed with three real Whisper cues. The additional exact-format
AAC-LC/16kHz/mono encoder-to-Whisper check passed one method/6.498s, with three
bounded cues/3974ms. Main now requests planner cleanup before voice recording,
clears current-generation busy before foreground guards, and refuses overlapping
microphone entry when recorder release is unconfirmed. Six visible/helper review
checks passed on the fresh unlocked synthetic emulator; physical recording remains untested. See [voice readiness](voice-input-readiness.md)
for complete counts/failure and the microphone/MediaRecorder/AirPods limits.

Read-only inspection of MainActivity confirms these repairs. Passing hardware/UI
checks are stated separately rather than assumed from the code.

- **Planning input consistency:** runPlanner stops listening and speech before
  generation. SpeechCoach invalidates recognition sessions, preventing stale
  results from replacing the brief. Busy processing recursively disables text
  inputs, spinners, checkboxes and switches; Cancel remains available.
- **Selected cut length:** checkbox changes save selection and immediately call
  updateSelectionSummary, showing selected count/duration and 12-cut/three-minute
  limits. Export reads the selected cuts independently.
- **Object-shot framing:** style/title rules disable person-pose advice for
  product, detail and cutaway shots. This is an explicit heuristic, not semantic
  object recognition. The inactive UI gating check passed; live object capture
  remains pending.
- **Spoken sequence:** matching utterance completion precedes the eight-second
  pose break; each spoken countdown number completes before advancing. Stop,
  error, timeout and background exit invalidate callbacks before capture. Two
  silent framework tests establish listener dispatch/stale-ID cancellation,
  not audible timing or Bluetooth buffering.
- **Ready edit package:** the cache ZIP pointer is persisted and validated on
  restore; a completed package launches Files only while RESUMED. Otherwise it
  stays ready for an explicit save after returning. Cancelled saves disclose
  possible partial destination documents. The ready-package visible/recreation
  path still needs a valid unlocked UI rerun.
- **Lens/crop:** selected lens persists and shared 9:16 ViewPort geometry is
  applied to preview/video/analysis. Three rotation/crop geometry tests and an
  earlier inactive lens-selection check passed; live lens rebinding is pending.

## Latest measured checks and failures

**Media and recovery:** all six MediaWorkflowTest cases passed in the combined
20-test / 21.065-second run, including actual 199-character four-line caption
output and overflow rejection with no new outputs. One recovery fixture failed
in its own already-deleted-row cleanup; corrected cleanup then passed all five
recovery tests in 0.775 seconds. These stage interruption/commit gaps and preserve
ambiguous outputs; they do not kill a live export. Earlier real Assemble UI
export produced a 6.060408-second vertical reel; normal Files saving copied a
1,512-byte JSON with matching source/destination hash.

**Portable package:** all six package tests passed, including original byte/hash
integrity, deduplication, relative paths, cut/subtitle offsets, bounds, revoked
sources, blocked-stream cancellation/late-open suppression and MIME handling.
The standard ZIP destination picker and persisted-ready UI are source-integrated,
but have not yet passed a valid attended Files-save check. Packaging files is
not Office Kit transfer or a demonstrated third-party editor import.

**Auto balance:** five analyzer and two exporter tests passed on rerun. Decoded
synthetic output reduced RGB cast spread from **12.1742 to 1.5267** and raised dim
fixture luma by **6.0603**; intentional flat color stayed neutral, exact source/range
pairing was enforced and original hashes stayed unchanged. These are bounded
neutral/exposure heuristics, not learned correction or demonstrated benefit on
real fashion footage. Evidence: ignored balance-repeat-metrics.log and
balance-ui-repeat-test.log under private/evidence. The earlier combined run
had MediaCodec fixture-start failures; the corrected fixture rerun is the color
execution evidence.

**UI:** the earlier combined framing/speech runner passed nine tests in 14.110
seconds. The latest seven UI-method attempt on the locked phone had five
visual/lifecycle failures; preview size was NaN and RESUMED/recreation waits timed
out. Only busy-editor gating and object-shot pose gating passed in that attempt.
Those invalid visible-state conditions neither establish a usable layout nor
justify claiming five application regressions were resolved. An unlocked rerun
and attended capture are pending. Camera/microphone remained inactive.

**Planner, previous failed run:** the prior few-shot runner took **140.584 seconds**:
three methods/four actual generations, one introduction assertion failure.
Introduction retained Rae, ceramic bowls and coffee; its Detail said “your work”,
a clear antecedent to the previous ceramic-bowls take. The test now accepts that
explicit detail/feature reference and still rejects observed invented rims.
Product met the strengthened mug fixture in 34.693 seconds and requested an
honest opinion; this is a targeted example, not general product quality.

Direct review found additional failures that the then-current green assertions
missed: talking invented an opened door and changed a future early-departure
plan into a past event; fashion invented a yellow pattern and repeated incomplete
caption phrases. These remain observed defects in that build. Temperature is
now 0.25, with short fashion caption examples and tighter next-time/cutaway
instructions, plus targeted independent regressions. The fresh four-method /
five-plan rerun passed in **142.074 s**, with actual plans25.563–31.666s. Direct
review confirmed the targeted story-event/intro/fashion/product errors absent.
These synthetic checks prove model execution and selected constraints, not
creative accuracy on unseen briefs.

## Completion boundary

The user can review a real development-phone local pipeline; a finished useful
creator shoot has not yet been demonstrated. Required next checks are an unlocked
visible UI/package save, attended phone/AirPods sequence and
sound review, then iQOO/Office Kit and organizer-compliant submission evidence.
Automatic semantic editing, multicamera alignment and aircraft control remain
unimplemented extensions. There was no unattended private capture, upload,
aircraft action or submission in this audit. This update changes only this
Markdown document; root owns builds/device work and other evidence ledgers.


Integrated delivery now passed: six tests / 20.578 s, actual 11.795737 s vertical
H.264/AAC reel, preserved synthesized jacket/green speech, decoded caption
timing and portable ZIP with four hash-verified originals. Container-bound ASR
and exact review/trim time conversion are repaired and checked headlessly.
Desktop copies matched hashes. Normal visible Files saving and all attended/
event gates above remain open.

### 13:39 IST: additional local editing/reference progress

Eighteen trim/actual-export/paused-cut-preview/reference-decoder/container tests
passed in18.560 s, preserving known synthetic speech, reviewed subtitles and
source SHA while shortening the padded source 8759 ms to an encoded6263 ms.
Two additional held-out native planner briefs passed at32.378 and40.002 s; this
adds fixture coverage, not broad grounding evidence.

An exact-source F16 vision projector was derived from the publisher-declared
official checkpoint using the unchanged pinned runtime and verified on the
phone. First actual CPU image run passed two tests in42.766 s: public person
recognized (~30.004 s), black frame reported black/empty/no identifiable subject
(~11.509 s), and immediate cancellation delivered no success. A long framing
phrase hit its grammar bound; a concise-phrase refinement is being rechecked.
This establishes image execution on these two fixtures, not temporal trend
understanding, learned direction, image accuracy on real footage, NPU, iQOO or
user benefit. Manual corrected notes are required before a visual draft enters
the next plan. Current UI/attended gate remains the secure device lock.


### 14:02 IST: installed checkpoint and explicit reference abstention

The final 9-test framing/reference/model-lifetime runner passed in 46.25 s.
Public full-body framing was supported, the portrait crop abstained with only
2/3 required upper joints visible, and black pixels yielded an exact unknown/
empty-or-unclear/unknown outcome. This accepts conservative missing information,
not successful head-and-shoulders classification or explicit empty-scene
recognition. Prior generative framing errors, portrait-positive failures and the
stricter black assertion failure remain in the evidence ledgers. Subject age and
vague uncertainty text are unvalidated; editable creator review is mandatory.

The latest APK installed and its normal launch intent succeeded behind secure
keyguard. Camera/microphone remain denied; visible UI and attended workflow
claims are still pending. APK and refreshed six-page pitch hashes are in
[status](status.md), exact visual/runtime evidence in
[local AI evidence](local-ai-evidence.md), and the practical returning-user flow
in [the attended checklist](device-demo-checklist.md). No new submission or
external publishing occurred. The development goal remains active.


### 14:38 IST: reviewed reference board and bounded pause experiment

The new 23-test / 92.759-second runner passed board ownership/persistence,
actual serial public/synthetic reference inspection, disclosed creator-cue
retention and quiet-stop policy/container checks. Source prompting repeatedly
lost named poses; deterministic retention now preserves explicitly authored
instructions and is labelled separately. Two native drafts still generate
other instructions/captions/lengths; one said “Walk to the left pocket”. Green
mechanics tests do not establish creative quality, and the app requires review.
See [reference-board evidence](reference-board-evidence.md).

The optional off-by-default quiet-pause switch is integrated, with a maximum
eight-second extension and immediate manual/background stop. Thirteen synthetic
policy checks passed; no live recording statistics, calibrated thresholds,
sentence completion or AirPods route was verified. The installed app remains
behind secure keyguard with camera/microphone denied. Latest SHA/storage and
retained failures are in [status](status.md). Attended workflow and competition
acceptance gates remain open; this is still pre-event research.

### 15:04 IST: larger-model comparison and source repairs

Both compact-prompt model methods failed (2 tests /279.718 s); the full
production-style comparison then passed native/schema/lifetime mechanics
(2 tests /214.062 s), but manual review still found an unusable baseline
“Take the left pocket” Movement. The larger Qwen2.5 1.5B candidate was slower
on all three briefs, lost specific pose/product-use details and gave weaker
captions. It is not promoted. Exact raw failures, outputs, prompt-token counts
and sampled numeric memory remain in
[the comparison ledger](planner-model-comparison-feasibility.md).

Source now bounds whitespace, adds a narrow observed-error Movement filter,
and requests idle planner cleanup before filming/editing paths. The filter
can miss other phrasing, including step-containing or different destination
forms. Cleanup is asynchronous, not proof of no allocation overlap. The initial ten-method guard/native/lease run passed nine and failed the
explicit-Detail native case; a four-method diagnostic also failed that case.
The exact raw probe identified a benign eyeline-filter rejection and single-
letter captions. The revised nine-method runner then passed in106.568 s,
including actual fresh fashion, hybrid boards and raw diagnostics. Directions
remain drafts: the raw Closing still loses requested face-forward stance, and
the kurta Detail chose texture despite a request for creator choice. Attended UI/camera/mic,
AirPods, real creator footage, iQOO/NPU/Office Kit and accepted event delivery
remain open.

### 15:42 IST: Fashion causal repairs and Talking regression

The larger comparison model was not promoted. Narrow Movement/caption checks,
a bounded camera-operation policy and a disclosed creator-choice Fashion Detail
constraint now have13 focused passes: nine methods in62.159 s and four in118.576 s.
Actual plain-jacket raw/public paths took28.910/30.837 s; kurta42.055 s and hybrid
unknown/explicit boards27.243/23.770 s. Native raw explicit-board generation
took23.832 s. Full plans, policies, earlier failures and qualified memory/storage
remain in [the comparison ledger](planner-model-comparison-feasibility.md).

Independent review found ambiguous jacket-on-chest wording, repeated garment-
still phrasing and generic one-word captions. Native Closing still loses the
explicit face-forward request; creator-authored retention preserves it in hybrid
plans with a separate label. Four-second allocations are drafts, not fitted
performance lengths. Source-only planner cleanup and pre-recording cancellation
label repairs still need attended UI/camera verification.

A shared-whitespace Talking regression failed one test in40.897 s: the model
returned an unsupplied “Show the door you opened” Cutaway. Key idea also omitted
an explicit next-time marker; its assertion was not reached after the door
failure. A disclosed native creator-choice Cutaway grammar/parser is being
built with the future/story checks retained. Fashion passes do not cover this
failure; no general story fidelity or complete attended workflow is claimed.

### 15:52 IST: Talking repair and saved checkpoint

The disclosed creator-choice Cutaway decoder/parser passed two deterministic
methods and two actual train/umbrella story fixtures (**4 tests /67.135 s**).
Callbacks39.353/27.692 s preserved supplied events and explicit future lessons.
The future assertion now requires a time/future marker in the instruction; no
facts/door check was weakened. Full structured outputs and source provenance
are in [Talking repair evidence](talking-cutaway-repair.json). Generic cutaways
are honest creator choices, not learned availability; repeated final lessons and
4s allocations still need performance/creative review.

Seventeen latest focused methods passed. The saved/installed APK, expanded9.279 GB
qualified storage and normal locked-phone launch are recorded in [status](status.md).
New source UI/capture cleanup still needs an unlocked attended check. This remains
pre-event research with no accepted submission, iQOO/NPU/Office Kit, live creator
reel or audible AirPods result. The goal remains active.

### 16:14 IST: save recovery and spoken preparation

Nine deterministic Movement methods passed0.153s after a reproduced step-clause
failure; four recorded native phrases remain exact, with no new generation.
Sixteen additional file/speech methods passed4.445s, covering bounded document
copy/cancellation and inaudible interruption/callback fixtures. Prepared ZIPs
now survive save failure/cancellation, invalidate when edits change, and last-
export controls are labelled. Select no takes preserves originals. Android
audio interruptions pause spoken preparation/future sequence until explicit
resume; actual recorded takes are left running. See
[exact evidence](document-audio-evidence.md). These UI integrations are reviewed
source with prepared visible checks, not an unlocked or physical AirPods result.
The saved installed APK and9.280GB qualified storage are in [status](status.md).
All attended and competition acceptance gates remain open.

### 17:54 IST: fresh emulator interface and real Files save

[Interface evidence](emulator-ui-evidence.md) records 25 passed methods, corrected
queued-dialog/MIME-monitor test setup, scrollable trims and preserved screen
without restarting capture. Initial six setup failures remain retained; no
production review bug is claimed from them. Real DocumentsUI saved a synthetic
614,367-byte ZIP; CRC, three current originals and six-second timeline checked.
Default emulator trim fields and Save were visible above its keyboard; other
sizes and physical layout remain unverified. Updated APK is independently
verified installed. Temporary AVD removed, qualified storage 9.292 GB.
Attended Pose → Perform → Assemble/AirPods, iQOO/NPU/Office Kit and accepted
eligible submission remain open. The goal is active.

### 18:14 IST: shoot ownership, product facts and controlled publication recovery

Fifteen selected synthetic shoot/speech/workflow checks passed after queued-pose
generation, speech-interruption and pending-lens guards. Two native planner
regressions passed after a retained Product omission failure and a Product-only
prompt repair; exact outputs and repetitive-caption limits are in
[product evidence](product-detail-evidence.md). Neither outcome proves an
attended shoot or general creative accuracy.

A real completed/copy-to-gallery export was externally stopped while READY and
pending, with a deliberately held test-only main queue. Fresh recovery passed
1/0.387s and removed exactly the owned interrupted pair while preserving
originals/baseline. This supplements actual ENCODING interruption; natural
publication timing and the published-before-COMPLETE kill window remain open.
See [shoot checks](shoot-cue-evidence.md) and
[publication checks](export-publication-death-evidence.md). Latest APK is installed,
normal launch requested behind keyguard, camera/mic denied; qualified storage
9.288GB after own temporary AVD cleanup. Full attended/eligibility/submission
acceptance remains required.

### 18:27 IST: camera-error recovery and subtitle visibility

Camera startup failure now releases pending state before reporting an error;
Main ends its pose/countdown/speech session so late advice cannot replace the
error. Latest seven synthetic shoot-state and three editor-review methods
passed10/14.428s; [shoot evidence](shoot-cue-evidence.md) preserves scope/limits.

Overlapping subtitle drafts now require correction before review Save, export
or packaging, preserving all words/times/order. Three phone checks passed
3/4.192s with an actual4064ms adjacent-caption export and matching portable
timing/original hashes. See [subtitle evidence](subtitle-overlap-evidence.md).
A first-run ordinary fashion draft retained both garments in39.998s but did not
select supplied chest pockets under the disclosed generic Detail policy;
[full fashion output](held-out-fashion-evidence.md) records that limit.

Latest verified APK is installed, normal launch requested behind keyguard,
camera/mic denied. Own temporary emulator removed; qualified storage9.293GB.
These repairs improve the existing prototype without establishing attended
recording/AirPods, general direction quality, event eligibility or accepted submission.
