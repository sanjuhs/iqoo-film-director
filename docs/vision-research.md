# Local vision and decision models — research notes

Official sources reviewed on 4 October 2026 (IST). Nothing has been downloaded,
trained, packaged or run on either Android device for this product. Published
support is a candidate-selection fact, not our measured inference result.

## Kev/Jev is the decision-model reference

The user supplied [jaredpalmer/kev](https://github.com/jaredpalmer/kev).
Its documented interface evaluates text states and returns yes/no, choice or
ordered-score probabilities. The smallest current release uses a Qwen3.5-0.8B
base; serving documentation covers CUDA/ROCm and Apple MLX. Android camera input
and an Android deployment are not established by those docs. The
[model implementation](https://github.com/jaredpalmer/kev/blob/main/kev/model.py)
uses a language backbone with specialized branch masking and pointer readout.
Converting a generic Qwen text model is not a faithful Kev port.

Kev could be a research comparator on a laptop using a structured text scene
description and labelled choices. It would depend on a separate perception step;
do not describe it as seeing a live feed. Its existing task benchmarks do not
measure cinematography. Our proposed [small dense head](tiny-director-model.md)
uses numerical visual features and learns a narrower creator-preference task.

## Existing perception candidates

| Candidate and primary source | Relevant published capability | Proposed role and unresolved test |
| --- | --- | --- |
| [MediaPipe Pose Landmarker for Android](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android) | Image, video and live-stream body landmarks; an Android sample | First geometry baseline for body visibility/framing. It does not identify fashion details or camera clearance. Measure detection reliability and latency on Nothing and iQOO. |
| [SmolVLM2-500M-Video-Instruct](https://huggingface.co/HuggingFaceTB/SmolVLM2-500M-Video-Instruct) | Image/video/text inputs and text output; Apache-2.0 card | Small multimodal candidate for selected-clip critique. Android conversion, complete model size, useful accuracy and sustained performance remain untested. |
| [Gemma 4 E2B](https://ai.google.dev/gemma/docs/core/model_card_4) with [LiteRT-LM Android](https://developers.google.com/edge/litert-lm/android) | Multimodal edge-oriented model; Kotlin runtime documentation includes image messages | Candidate for semantic review and explanations. Pin a compatible model bundle and runtime before testing; E2B is an effective-size designation, not a complete memory budget. |
| [Qwen3.5-0.8B](https://huggingface.co/Qwen/Qwen3.5-0.8B) | Publisher card provides image/video use and Apache-2.0 license | Alternative multimodal checkpoint. Its Android vision runtime and quality need validation. A text-only build does not prove image understanding. |

The [llama.cpp multimodal guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md)
lists SmolVLM2 and Gemma 4 support and describes a separate multimodal projector.
If that route is used, preserve image preprocessing, language weights and
projector compatibility. A generic text-only GGUF test is insufficient.

Model weights are only one cost. Budget vision encoders/projectors, decoding,
temporary frames, runtime/dependencies, activations and any language-model cache.
Published GPU memory examples or NPU backend support are not phone measurements.
Prefer one candidate at a time; check the 10 GB aim/15 GB ceiling first. The old
storage snapshot is historical while the retained archive's location is unresolved.

## Recommended experiment order

1. Prove the read-only DJI connection independently of AI.
2. Test local subject/pose extraction on chosen images and imported short clips.
   Use simple, labelled framing rules as the comparison baseline.
3. Train the tiny framing head on the laptop using creator-labelled examples.
   Deploy inference to Android only after held-out whole-shoot evaluation.
4. Add one multimodal checkpoint if geometric features cannot answer the useful
   semantic questions, such as whether a specific garment detail was captured.
5. Evaluate Kev separately only if text-state decision tasks justify its larger
   model and specialized Android-porting work.

Building a general language/vision model from random weights is not the preferred
hackathon path. A custom small classifier trained from scratch, or an adapted
pretrained checkpoint, can be an honest technical contribution if it produces a
measured improvement. Synthetic labels can help prototype a pipeline but cannot
establish the creator's preferences or useful real-world accuracy.

## Evidence required before claiming a local director

Record exact model/runtime revisions, licenses and hashes; input preprocessing;
device model/OS; CPU/GPU/NPU backend; model/disk footprint; initialization time;
end-to-end median/p95 latency; peak RAM; sustained temperature/battery behavior;
and task quality on withheld shoots. Run an offline inference check after setup
to demonstrate that media analysis itself does not require a remote model.

Keep consented data and weights out of Git. Compare with rules and a simple
classifier; report per-class errors and abstention rather than only accuracy.
Nothing-phone results do not prove iQOO performance. Pilot approval, DJI state
validation and any future executor remain outside the model. No training or
benchmark result is claimed in this document.
