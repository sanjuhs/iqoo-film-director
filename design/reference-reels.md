# Reference reels — access, shot breakdown and model plan

**5 October 2026 (IST), pre-event research.** This describes a proposed analysis
pipeline and the current design simulation. No reference video has been fetched,
decoded, uploaded or analyzed by this wireframe. No new dependency, model or
training data was downloaded for this research.

## What the Reference reel view does now

Reference reel is nested under Brief, alongside the editable objective and plan.
It accepts a validated Instagram link as a bookmark. The URL is not fetched;
valid syntax does not prove that a post exists, is public or can be downloaded.
The local-video picker records file metadata only. It does not read video bytes,
decode frames, play the selected file, upload it or run analysis.

An explicit sample action opens an **unrelated synthetic five-shot breakdown**.
Its timing (1–30 whole seconds), framing, angle, movement, pose and transition
fields are editable. Split/merge samples to correct boundaries, up to twelve
shots; editing clears the affected review checkbox. Review every sample
checklist and choose rhythm, angles and/or poses to adapt into your own plan and
director cues. This is a design demonstration, not an inferred description of
the pasted reel or selected file. Camera equipment remains a separate choice.

Applying the sample preserves existing draft takes and creates fresh shot IDs;
old takes remain associated with the earlier plan. New planned sources start on
the phone, and target duration becomes the sum of the adapted shot timings.
Project save/JSON includes reference metadata, reviewed observations and
adaptation choices, but never the local file or private video pixels.
An application that eventually reads private media must require a separate
explicit selection and processing disclosure; this simulation performs neither
local visual analysis nor external processing.

## Instagram access and “trending”

The user-facing flow should distinguish bookmarking, embedding, importing an
authorized source file and analyzing that source. A pasted public URL supplies a
reference identifier; it does not authorize raw-video download or establish an
API-accessible media ID.

| Route | Intended use | Boundary |
| --- | --- | --- |
| Paste an Instagram link | Save a reference and notes | No fetch, authentication or raw download in this design |
| Official oEmbed | Display an eligible post using an embed | Embed markup is not raw video or an analysis input |
| Connected professional account | Discover permitted account/media data | Login, granted scopes, app access and endpoint rules must be established |
| Hashtag discovery | Candidate references for a selected topic | Top/recent hashtag results are not a global trending-reels feed |
| Creator-supplied original file | Later local analysis of an authorized video | Explicit processing consent, rights and retention controls |

Meta's [official Instagram API with Facebook Login collection](https://www.postman.com/meta/instagram/folder/u4g5a2a/instagram-api-with-facebook-login)
describes Business/Creator professional accounts, a linked Facebook Page and
access-token permissions; it excludes consumer-account access through that
route. It mentions hashtagged media and business/creator metadata. These are
route-specific constraints: do not silently apply Facebook Login requirements
to a different Instagram Login flow.

The official [top-media](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-hashtag/top_media/)
and [recent-media](https://developers.facebook.com/documentation/instagram-platform/instagram-graph-api/reference/ig-hashtag/recent_media/)
references are discovery candidates, subject to verification. Our research did
not establish a supported global trending-reels endpoint. Calling hashtag
results “trending everywhere” would overstate their scope. A future product may
call a user-selected collection “reference picks” and disclose its query,
collection time, source, coverage and ranking method.

[Instagram oEmbed](https://developers.facebook.com/docs/instagram-platform/oembed/)
is the candidate display route, not the download/analysis route. The
[oEmbed specification](https://oembed.com/) describes video responses as player
HTML. Neither an embed nor a public post proves permission to extract the
underlying recording. Use user-owned/licensed originals or separately verified
authorized media access for analysis.

**Source-access limitation:** direct Meta developer pages were inaccessible in
this research session; the parent investigation reported HTTP 429. The official
Postman collection's indexed text was available, while its rendered documentation
was sparse. Current hashtag availability, exact permissions, API version, quotas,
oEmbed authentication and permitted processing terms remain unresolved. Recheck
the live official documentation and a separately authorized API probe before
implementing these routes. No token, Meta app or API result is established here.

## Proposed analysis pipeline

The following is an engineering proposal, not a measured implementation. Start
with a short authorized local file and a manually reviewed shot list; keep the
whole reference-to-plan path useful without a model.

| Stage | Candidate component | Output and review boundary |
| --- | --- | --- |
| 1. Intake | Deterministic file/import layer | Source provenance, permission, duration, aspect ratio, timestamps and processing consent |
| 2. Shot boundaries | PySceneDetect baseline; optional TransNetV2 comparison | Candidate cut times; creator corrects missed cuts and false boundaries |
| 3. Visual sampling | Decoder and bounded sampling schedule | Timestamped start/middle/end frames plus denser samples around motion/transition uncertainty |
| 4. Shot observations | Vision-language model; optional pose landmarks and motion measurements | Framing, apparent view/angle, subject action and uncertain movement labels with evidence timestamps |
| 5. Speech | Optional Whisper-family transcription | Reviewed words and timing; silence, music and transcript errors remain explicit |
| 6. Adaptation | Small language planner reading the reviewed structured breakdown | Own objective, proposed rhythm/angles/poses and executable short cues |
| 7. Session | Deterministic director state machine | Preparation, countdown, recording acknowledgement, bounded cue timing, Stop and interruption ownership |
| 8. Assembly | Reviewed cut list and media pipeline | Creator-selected takes, trims, subtitles and eventually verified video export |

Cut detection, pose, semantic interpretation, transcription and cue scheduling
are different tasks. A single “analyze reel” button may coordinate them, but
must expose progress, provenance and reviewable failures. Unknown observations
stay unknown. Sampled frames cannot establish continuous motion or exact camera
geometry; perspective, cropping, body movement and editing can imitate camera
movement. Do not infer physical distance, focal length or drone flight from
appearance alone.

## Candidate components and the 0.8B question

**Shot segmentation:** [PySceneDetect's official detectors](https://www.scenedetect.com/docs/latest/api/detectors.html)
offer a content-change baseline and an adaptive threshold that can reduce false
boundaries caused by camera motion. It does not describe poses or shot meaning.
[TransNetV2](https://github.com/soCzech/TransNetV2) is a learned shot-boundary
detector with separate inference code/weights. Compare its transition handling
against the baseline before adding another model. Published dataset scores are
not measurements on our short fashion references.

**Semantic description:** the official [Qwen3.5-0.8B model card](https://huggingface.co/Qwen/Qwen3.5-0.8B)
describes a causal language model with a vision encoder and image-text examples.
The family is not inherently text-only. However, the project's existing Android
CPU text-planning evidence establishes only its exercised text path. A text-only
export/runtime invocation cannot acquire visual ability from the model's name.
Confirm the actual checkpoint, vision encoder/projector, image processor,
quantization, multimodal runtime and reproducible image-input output before
claiming reference-video understanding. The card positions this small scale for
prototyping and task-specific research; it does not establish this task's accuracy.

Use 0.8B first as a **candidate for concise structured-plan adaptation**, after
the creator reviews visual observations. Separately compare its complete visual
path with optional [Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B) or
[Qwen3.5-4B](https://huggingface.co/Qwen/Qwen3.5-4B) candidates on the same held-out
shots. More parameters do not guarantee correct angles or acceptable phone
latency. Select only after quality, memory, storage and actual device execution
are measured; no model download or size commitment follows from this plan.

**Pose and words:** [MediaPipe Pose Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker)
provides pose landmarks for images/video. Landmarks can support visible joint
orientation and coarse pose observations; they do not prove artistic intent or
camera motion. [Whisper](https://github.com/openai/whisper) provides speech
recognition models. A reference hook/transcript still needs review; choosing a
speech model does not prove synchronization, music rights or Android performance.
Use manual annotations when people or speech are absent or observations fail.

The phone's live director should consume reviewed plans and fresh bounded
observations. It must not decode/analyze an entire reference or wait for a slow
VLM during a countdown or capture callback. Preparation models propose; the
app owns timing, recording state, permissions, cancellation and rendering.

## Review schema and adaptation

Each future observed shot should retain source/in/out timestamps, boundary
method, sampling points, framing, apparent angle, movement, pose/action,
speech/overlay notes, confidence/unknowns and creator corrections. Keep
observations separate from proposed instructions. The current synthetic editor
exposes the main creative fields, not this completed analysis pipeline.

Adapt **rhythm** as durations/order, **angles** as framing/view suggestions and
**poses** as actionable cues for the creator's own objective. Adjust instructions
to the available phone framing, location and optional equipment. Do not copy
audio, promise identical output or silently assign drone execution. A drone-like
wide shot can become a fixed phone wide view or separately planned pickup.

## Evaluation and deployment gates

Use authorized, creator-labelled whole reels, split by reel/creator rather than
frames from the same source. Begin with diverse short fashion clips and retain
walking/talking, products, occlusion, fast pans, flash cuts, dissolves, mirrored
views and no-person footage as challenging examples.

- Measure boundary precision/recall at a declared timestamp tolerance and errors
  for rapid cuts/transitions; compare PySceneDetect, TransNetV2 and manual labels.
- Score framing/angle/movement/pose labels against creator review, report unknowns,
  unsupported details and correction effort. Do not equate fluent text with truth.
- Test transcript and hook errors separately from visual findings.
- Compare manual template, reviewed-observation → 0.8B planner, complete 0.8B
  visual path and any 2B/4B alternative on the same held-out references.
- Measure end-to-end and per-stage latency, peak memory, storage and sustained
  capture interference on the exact Android phone/backend. Android conversion,
  output parity and NPU execution require separate evidence.
- Verify cancellation, stale results, manual fallback, malformed model output,
  private-media retention and no upload without explicit authorization.

No task accuracy, latency, NPU acceleration or creator benefit is established by
this research. Pin versions/licenses before implementation. Reuse existing
installations, account for shared-cache/model growth and the retained archive,
aim below 10 GB and remain within the 15 GB project budget. Reference analysis
adds no dependency downloads or aircraft actions to the current design task.
