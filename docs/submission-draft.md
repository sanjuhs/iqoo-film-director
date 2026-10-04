# Mini Film Director — Phase 1 idea submission draft

Prepared 4 October 2026 (IST). Ready for creator review; this document has not
been submitted. Use the portal's actual field limits, keeping the evidence and
preparation disclosure intact. Team context: solo; proposed track: Productivity.
The signed-in dashboard was reviewed by the parent agent and shows a Phase 1
date of 5 October 2026. Its observed countdown supports approximately 23:59 IST on 5 October; the visible
date label does not separately state timezone. No submission receipt
exists in this draft.

## Title

Mini Film Director

## Tagline

Pose. Perform. Publish. Your phone directs the reel with you.

## Short description

Mini Film Director is designed to help solo creators pose, perform and assemble
a reel on their phone. Local shot planning, visual measurements, editable
English subtitle drafts and reviewed cuts support the workflow. Earbud direction
is the intended shoot experience. Daily fashion comes first; talking stories,
product reveals and introductions follow.

## Main idea description

A solo creator often has to be the performer, camera operator, director and
editor at once. They set up a phone, guess their framing, repeat takes and later
discover that the story needs a missing detail or transition. Mini Film Director
brings those decisions into one phone-first shoot.

The experience has three phases. **Pose:** enter a brief and review a short shot
plan, then position the phone and get concise framing guidance. **Perform:** hear
one instruction through paired earbuds, start an explicit countdown, record the
take and move through the scenes. **Assemble:** choose the takes, adjust their
in/out points and captions, select a look and export a vertical reel with an
editable cut list.

The first scene is a 20-second jacket reveal: outfit hero, fabric detail, a short
movement, side silhouette and a finish. The preparation prototype has run local pose inference and generated
brief-specific shot plans on Nothing Phone (3a). Its media tests have produced
a playable vertical sequence with timed subtitles. Live capture and earbud
playback still need their own tests. Capture and editing use Android's media stack. The creator can override
suggestions, and private footage stays on the device unless they choose to share
it. Phone filming is the core demo. A drone is an optional source of manually
captured/imported footage until separate connection and safety tests pass.

## Problem and intended users

The first user makes solo fashion reels every day. Their immediate need is a
useful second pair of eyes and a sequence of clear instructions while they are
in front of the camera. A disconnected pose app, camera app and editor leave
coverage and continuity decisions to memory. Our intended benefit is fewer
avoidable setup mistakes and a simpler path from idea to reviewed reel. These
are hypotheses to test with creator-labelled takes and timed shoots; no measured
retake reduction, creator study or accuracy result is claimed yet.

## What makes the idea different

[Vantage's project page](https://devpost.com/software/vantage-qk687p) and
[repository](https://github.com/ParthPatel00/Vantage) describe an Android AI camera
that interprets style and tunes photos; its roadmap includes live coaching and
multi-shot stories. [Superpose's official site](https://www.superposelabs.ai/)
describes photographer guidance, pose inspiration and variations of a photo.
These are their authors' descriptions, not independent performance validation.

Our proposed distinction is the end-to-end **solo video performance**: shot
coverage across time, quiet earbud direction between takes, explicit recording,
and creator-reviewed reel assembly. Advice belongs to the current scene, and
its resulting footage remains editable. We do not claim exclusive invention of
AI camera coaching. We will validate the practical advantage of combining these
steps rather than relying on an absolute novelty claim.

## Technical approach

The Android app keeps a visible shoot state: brief, editable shots, current cue,
recording acknowledgements, selected takes and reviewed cuts. CameraX capture is
implemented with explicit foreground start/stop; a live capture result remains
unverified. Bundled ML Kit supplies person landmarks, followed by deterministic
framing rules. Qwen3.5 0.8B through a pinned llama.cpp runtime runs on the phone's
CPU to adapt a five-shot scaffold to the brief. Output is bounded, parsed and
creator-reviewed; an editable template is available when planning fails. Fashion
Detail and Talking Cutaway use visibly disclosed creator-choice decoding
constraints rather than inventing an unspecified garment part or available prop.
Named reviewed fashion directions are retained as creator-authored edits; this
does not establish learned reference grounding. Explicit short first-person
speaking requests now retain a separately labelled creator-authored Closing
instruction/caption after actual native five-shot generation; its duration remains
native. Earlier stationary/talking outputs missed the speech topic, including
the Closing-first diagnostic. [Hybrid evidence](creator-speech-retention-evidence.md)
preserves those failures; this is literal request retention, not native topic learning.

Selected reference clips receive sparse local inspection: at most 24 sampled
frames, approximate color-change candidates and at most six single-person pose
samples. These factual observations can accompany the user's brief to the text
planner. They do not identify a trend, narrative or creative style. An optional pinned
Qwen image projector has also run actual sparse single-frame subject inference
on this phone CPU; creators review/correct those notes before planning. Full-body,
cropped and black public/synthetic fixtures demonstrated conservative unknown
results as well as model mistakes. Single-frame execution does not establish
accurate clothing, age, framing or whole-trend understanding.

The reference speech addition reads that selected video's English audio locally,
keeps its full timed draft for review and uses only explicitly corrected concise
context alongside the visual notes. A synthetic tiny.en → test-authored corrected
context → Qwen CPU plan passed, preserving original bytes. The combined input
rejects overflow rather than truncating either source. This does not establish
learned speech/story grounding; the visible review still needs an unlocked check.

Whisper tiny.en through whisper.cpp runs locally on CPU to create editable
English subtitle drafts from selected clip audio. Media3 assembles reviewed
trims with timed text, typography and deterministic color presets, preserving
original takes and exporting an editable cut list with the MP4. An opt-in
conservative neutral/exposure heuristic has passed actual synthetic-video tests;
learned creative grading and word-level karaoke timing remain future work. A
portable ZIP includes selected originals, relative media paths, hashes and the
reviewed timeline; actual Office Kit transfer is a separate gate.

An opt-in local voice-brief design records at most 45 seconds, then requires
Stop and explicit review before words can replace the typed brief. Whisper reads
the temporary encoded audio locally; cancel/background discards the draft.
Actual AAC-LC encoding at the requested 16 kHz/mono/64 kbps and subsequent local
transcription passed with synthetic PCM, without opening a microphone. That
does not establish MediaRecorder or live spoken-brief acceptance.

Android's dedicated on-device dictation remains a separate option when its
offline English model is installed; it was absent on the development phone at
the last readiness check. The creator can always type. A non-network Android TTS
voice synthesizes cues. Actual earbud playback and live microphone use remain
separate attended tests.
The installed target was checked to have no INTERNET permission. No provider
key is packaged in the app, and no private-media upload is part of this workflow.

## Measured preparation results and remaining gates

These are **Nothing Phone (3a) preparation results**, not final iQOO validation
or evidence of competition eligibility. Test inputs were synthetic media and an
attributed public documentation image; no private shoot was captured. See
[local AI evidence](local-ai-evidence.md), [export evidence](export-evidence.md),
[transcription evidence](transcription-evidence.md),
[reference evidence](reference-evidence.md) and [current status](status.md).

| Component | Verified preparation result | Remaining limit |
| --- | --- | --- |
| Shot planning | Latest disclosed creator-speech hybrid: **30.381s native**, one attempt;7 actual methods passed, two invocation class-load errors retained, then11 correct companion methods passed. Earlier focused Fashion/Talking17methods and23.770–42.055s drafts retain their dated evidence. | Four native directions plus creator-owned Closing wording/caption; narrow deterministic/native checks are not general native topic fidelity, creator benefit or spoken-fit evidence. |
| Pose inference | **33 landmarks in 388 ms** on Google's annotated public pose image; synthetic black input returned zero landmarks | This is image-model execution, not useful live-camera coaching or held-out evaluation |
| Cue synthesis | Offline-voice synthetic cue produced audio bytes in **511 ms** | Synthesis only: no audible playback, AirPods route or Bluetooth microphone result |
| English subtitles | CPU tiny.en produced **three draft segments in 3.458 s** from one labelled synthetic spoken clip | End-to-end decoding/load/transcription time; expected words checked, not general ASR accuracy |
| Reel assembly | Actual **720×1280 H.264/AAC MP4**, **6.060 s** long, correct three-scene order and decoded subtitle visibility | Synthetic inputs; real camera audio, crop quality, HDR and multicamera synchronization remain untested |
| Media correctness | **Six media tests passed**, including actual 199-character full captions and overflow rejection; staged recovery five tests passed. One real externally stopped ENCODING export also passed fresh-process recovery in **0.242 s**, preserving original bytes and the earlier completed reel | Fixture media, not an attended shoot; other process-death publication windows remain unverified; test-suite time is not an export benchmark |
| Auto balance / portable ZIP | Seven analyzer/export checks reduced mild neutral RGB cast spread 12.1742→1.5267 and dim-luma +6.0603; six package checks preserved original bytes/hashes and portable timing | Deterministic heuristic, synthetic inputs; real grading, normal Files ZIP save and Office Kit remain unverified |
| Reference inspection | **Three device tests passed in 4.883 s**: flat-color negative, two synthetic change boundaries and web-URI rejection | Sparse heuristics only; no semantic trend recognition or general cut-detection accuracy |
| Phone capture | Explicit foreground implementation is present | Real preview/recorded audio/video, background stop and creator review |
| Required hardware/integrations | Phone development evidence is available; installed app received a normal launch request | Phone remains locked with capture/audio permissions denied; live creator filming, iQOO, NPU, AirPods, Office Kit and DJI connection/control remain unverified |

The measured local LLM, pose, transcription and media pipeline are real device
execution. Rules are not a trained taste model, and CPU results are not NPU
results. A generated shot instruction remains a proposal. Earlier drafts invented details;
latest creator-choice constraints and targeted refinements passed tested fixtures
without establishing general accuracy. Native reference directions still lost
requested stance, which is why exact creator-authored retention is labelled
separately. Full failed outputs remain in the comparison evidence ledger. Caption timing/text remain
editable. No creator-benefit or end-to-end solo-shoot result is claimed yet.

## Event build and feasibility

We will build one complete phone-only story before adding optional camera routes:
one outfit, one location, five short takes and a reviewed reel. Keep model
planning occasional, analysis bounded and advice quiet during recorded speech.
Test creator override, denied permissions, unavailable models, low storage and
app backgrounding. Compare cues against creator labels and record failures,
latency, storage and the actual processing backend. Reproduce the complete
workflow on the required iQOO hardware; Nothing Phone (3a) development results
do not substitute for that gate.

The public [hackathon guide](https://iqoo.reskilll.com/guide), read in its rendered
page on 4 October, states: “Original work only: code written during the event
window. No shipping a pre-built product.” It also permits attributed libraries
and frameworks while excluding carrying in a completed app. This repository's
pre-event Android source is a dated **research prototype**, not the eligible
competition implementation. We will create fresh competition source during the
permitted window, preserving preparation provenance. Any reuse requires explicit
organizer approval; a new repository name does not change when code was written.

## Judging fit and planned proof

Prior guide research in [hackathon.md](../hackathon.md) records these weights;
confirm them against the current guide at check-in.

| Criterion | Prior weight | Concrete proof to bring |
| --- | --- | --- |
| End product quality | 30% | One complete brief-to-playable-reel workflow and working recovery |
| Novelty and impact | 20% | Scene-specific direction and a measured creator comparison |
| Creative phone use | 15% | Actual iQOO capture, local guidance and local assembly |
| Technical depth | 15% | Model/backend measurements, concurrency and export correctness |
| Office Kit | 10% | Actual supported transfer and required event tracking evidence |
| Presentation | 10% | Three-minute story, clear labels and one visible creator correction |

## Deck outline — six slides

1. **Your phone becomes your film director:** tagline and three-phase journey.
2. **One person, four jobs:** the creator's setup, performance, coverage and edit
   problem; clearly label intended benefit as a hypothesis.
3. **One jacket, five shots:** editable shot cards with posing, cue and reviewed
   take at each step.
4. **Local by design:** measured CPU shot planning and pose inference, sampled
   reference observations, English subtitle drafts and reviewed media assembly.
5. **Evidence before claims:** show verified device results separately from
   pending iQOO/NPU, real-shot subtitle quality, semantic trend understanding and
   optional drone.
6. **A realistic event build:** fresh event-written implementation, phone-only
   acceptance gate, supported Office Kit transfer and three-minute demo.

A six-slide [editable deck and PDF](phase1-deck.md) now accompany this draft.
They retain pre-event preparation disclosure and pending hardware/creator checks.

## Submission checklist

- Confirm the signed-in Phase 1 cutoff and timezone and leave time for upload.
- Review title, description and track against the actual portal field limits.
- Attach a reviewed PDF/PPT deck under the portal's 25 MB limit, or a working
  deck link, as required by the signed-in form. The reviewed local deck/PDF are ready; this Markdown is not that upload.
- Creator supplies truthful proficiency/self-reports and reviews the original-work
  checkbox. Do not infer answers, attest on their behalf or silently reuse the
  archived project's application fields.
- Keep the preparation and event-source disclosure visible; seek organizer
  clarification if the eligibility wording is ambiguous for the proposed entry.
- Remove private media, hardware identifiers and secrets from deck/demo captures.
- Inspect the final form and attachment once more, then obtain action-level
  authorization before an outgoing submission.
- Save the actual confirmation/receipt and final submitted copy. Until then,
  report the application as a draft.
