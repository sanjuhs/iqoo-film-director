# Single-model phone director — precedents and training

Reviewed 4 October 2026 (IST). Research prompted by the user's simpler iQOO +
DJI Osmo idea. Assume Osmo Action 4 from the preceding conversation until the
exact model is clarified. This proposes a smaller product experiment; it does
not implement it or silently replace the existing delivery plan. No private
data, model downloads or hardware actions were used in this review.

## Existing features

| Precedent | Documented feature | Model evidence and limits |
| --- | --- | --- |
| [Pixel Camera Coach](https://blog.google/products-and-platforms/devices/pixel/how-to-use-camera-coach/) | Composition, lighting, framing and mode guidance | Gemini confirmed; some cloud processing. [Support](https://support.google.com/pixelcamera/answer/17367411?hl=en) requires internet and documents rear-camera photo modes and a photo-taking procedure. Not an offline external-camera film director result. |
| [Samsung Auto framing](https://www.samsung.com/us/support/answer/ANS10003673/) and [Director's View](https://www.samsung.com/us/support/answer/ANS10003654/) | People-based video framing; user-controlled lens/front-rear views | Official descriptions do not identify an LLM. Capture assistance does not establish script understanding or choosing narrative shots. Availability depends on supported phone/software. |
| [DJI Mimo / Osmo Mobile ShotGuides](https://www.dji.com/osmo-mobile-7-series/faq) | Scene-based shooting guidance/templates and subject tracking on compatible hardware | AI terminology is not a disclosed LLM architecture. Osmo Mobile capability does not transfer automatically to Action 4 or our app. |
| [Huawei Pura 90 pose guidance](https://consumer.huawei.com/cn/support/content/zh-cn16098334/) | Scene-conditioned portrait pose outline | Architecture undisclosed here. [Product page](https://consumer.huawei.com/cn/phones/pura90/) advertises composition assistance. A photo/portrait precedent, not a measured local video-director result. |

[Vivo V17 Pose Master](https://www.vivo.com/in/product/productDetails?id=136)
is another historical posing precedent. Pose suggestions alone are not the
proposed distinguishing contribution. Stateful direction during a performance,
personal preferences, external capture and editable story coverage are useful
goals to evaluate.

The sources establish features and architecture where disclosed, not comparative
quality. No hands-on tests were performed. This bounded review did not establish
the complete local iQOO + Osmo director workflow; it cannot prove none exists.

## One model, separate execution

One local creative model can interpret the brief, suggest the next shot/short cue
and propose camera tools. Ordinary Android code validates and executes tested
operations. Speech recognition and scene/pose measurements may be separate helpers
without another creative LLM. Unknown state remains unknown. One LLM does not
mean one neural network performs all speech, vision, editing and Bluetooth work.

Proposed chain: opted-in speech/text + measured scene + shot history → one model
→ next-shot/cue/tool proposal → validated app adapter → camera. Model decisions
occur at meaningful shot/cue boundaries, not continuous low-level control timing.

[DJI's R SDK protocol](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol.md)
includes Action 4; its [data documentation](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol_data_segment.md)
describes recording/status operations. The example is ESP32, not our Android
adapter. Preview and media transfer remain separate. A fixed Action 4 has no
motorized pan/tilt gimbal: direction can ask the creator to move/reposition it,
rather than implying software moves it. Pocket/Mobile gimbal control would need
a different verified interface.

[Action 4 support](https://www.dji.com/support/product/osmo-action-4) already
documents a small English/Mandarin voice vocabulary for recording, photo and
shutdown. Implementation is not disclosed as an LLM. Show useful direction beyond
record/stop commands.

Establish which camera supplies observations. BLE record control does not expose
video by itself. Phone preview supports an initial phone-view coach but cannot
prove exact Osmo composition even if placed nearby. Mark phone proxies accurately.

## Model candidates

[Qwen3.5-0.8B](https://huggingface.co/Qwen/Qwen3.5-0.8B) is a small pretrained
text/image/video and tool-calling candidate, Apache-2.0 in the publisher card.
It is not a committed or verified Android dependency. A text-only export requires
separate perception and does not establish the original visual capability.

[FunctionGemma 270M Mobile Actions](https://ai.google.dev/gemma/docs/mobile-actions?hl=en)
has an official fine-tune, convert/quantize and Android deployment recipe for
offline function calling. Stronger deployment reference for narrow commands;
not demonstrated cinematic judgment or rich visual coaching. Do not download
multiple candidates by default.

Laptop training, faithful Android export, useful phone inference and NPU execution
are separate checks. Exact iQOO hardware is unknown. Measure offline behavior,
post-quantization parity, end-to-end latency, RAM, thermals, battery and concurrent
capture; record actual CPU/GPU/NPU/fallback. Existing storage limits still apply.

## Training examples

Fine-tune a pretrained checkpoint rather than train a foundation model from random
weights. Raw videos and generic captions do not label next-shot decisions.

| Data type | Inputs | Target |
| --- | --- | --- |
| Director decisions | Brief, style/length, measured scene, camera capabilities, accepted shots, recent cues | Next useful shot, short cue, reason, bounded tool proposal or clarification |
| Visual coaching | Selected frame/short clip, source camera, intended garment/pose/shot | Visible issue, correction, uncertainty; no invented depth/light/clearance |
| Sequence coverage | Shot history, performance beat, remaining coverage | Hold shot, request detail/side/ending, avoid repetition; human edits |
| Camera operations | Tool schema, actual connection/recording state, instruction, permissions | Valid proposal/arguments; no unsupported tool or duplicate start |
| Preferences | Two plausible cues/plans/cuts | Creator's preferred alternative and reason |
| Failures | Stale/missing input, occlusion, absent person, disconnect, conflicting intent | Wait, clarify, reconnect or request review |

Example: “15-second jacket reel”; full-body take accepted; sleeve detail missing;
creator speaking; Action 4 availability unknown. Target: hold the current shot,
queue a sleeve-detail cue after speech, and issue no Action 4 recording proposal.
Store whether the cue was accepted and whether the pickup supplied useful coverage.

Proposed budgets are engineering estimates, not sufficiency guarantees:

1. Reserve 100–200 reviewed situations for evaluation, including failures/unknowns.
   Compare prompted pretrained inference with rules before training.
2. Collect 1,000–3,000 reviewed examples for a narrow fashion adaptation if needed,
   combining consented real shoots with reviewed synthetic schema cases.
3. Expand only when held-out errors justify it; 5,000–10,000 examples and 500–1,000
   preference pairs are possible later budgets, not prerequisites.

Split by entire shoots/days/locations; keep near-duplicate frames, paraphrases and
synthetic descendants together. Synthetic data covers schemas/failures, not proof
of personal taste or visual benefit. Measure valid tools, unsupported/state
violations, accepted/incorrect cues, repetition/missing coverage, abstention and
completed-reel usefulness. Supervised adaptation first; preference training later.
Keep media, labels, weights and identities local/outside Git. No private uploads.

## Related research

[ShutterMuse](https://arxiv.org/html/2606.25763v1), a June 2026 photography
preprint, studies composition/pose choices with a multimodal model. Its
[authors' repository](https://github.com/lijayuTnT/ShutterMuse) uses Qwen3-VL-8B
and reports about 130K atomic examples plus joint/refinement material. Labels
include images, framing boxes, body keypoints/visibility and explanations.
Useful label-design reference, not a required dataset scale for our narrow MVP.
Six-person human evaluation and photography benchmarks do not prove live video
direction, Android execution or Osmo control. Repository license section remains
TODO; check reuse rights before adopting data/code/model.

## Smaller product experiments

1. Fashion director: jacket, pose/turn/detail cues, three approved takes and a
   reviewed 15-second sequence. Best match to the first creator workflow.
2. Talking-head director: intro/content/closing beats, quiet mode during speech,
   pickup suggestions and edit markers.
3. Product-reveal director: opening, feature detail and demonstration coverage,
   with concise prompts and recording through a tested adapter.

Evaluate fashion first, with phone observations and manually verified Osmo framing.
Add preview-aware external-camera advice after proving the feed. This is a proposed
experiment, not measured benefit or permission to record/move a device.
