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
| [MediaPipe Pose Landmarker for Android](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android) | Image, video and live-stream body landmarks; an Android sample | First geometry baseline for body visibility/framing. It does not identify fashion details or camera clearance. Measure detection reliability and latency on Nothing Phone (3a) and the eventual iQOO. |
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
5. Evaluate checkpoint-based Kev fine-tuning on structured scene/intent decisions,
   then attempt an Android port only after a useful held-out result. Retain the
   tiny head as the baseline and fallback.

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
Nothing Phone (3a) results do not prove iQOO performance. Pilot approval, DJI state
validation and any future executor remain outside the model. No training or
benchmark result is claimed in this document.

## Android, Snapdragon NPU and checkpoint adaptation feasibility

The intended pipeline is phone/drone perception plus locally transcribed spoken
intent → bounded scene state → probabilistic shot choices. Kev receives the
text state; camera perception and speech recognition are separate models. Train
or adapt on the laptop, then deploy inference to Android. Begin from a compatible
released Kev checkpoint, preserving its base, adapter, pointer head and calibration;
this differs from random-weight training or converting generic Qwen to GGUF.
[Kev's documented fine-tuning path](https://github.com/jaredpalmer/kev#fine-tune-on-your-own-data)
does not establish Android success.

A faithful export needs tokenizer/delimiter semantics, independent question
branches, hidden-state readout, pointer scores, calibration and supported numerical
operators. [Kev's implementation](https://github.com/jaredpalmer/kev/blob/main/kev/model.py)
uses block-causal masks for compatible backbones and independent causal rows for
Qwen3.5 hybrid attention, rather than forcing that mask on unsupported layers.
An Android converter/runtime must preserve the selected execution form, positions
and recurrent/cache behavior. Compare complete probability vectors with the
laptop implementation before and after quantization. A successful generic language
model load is insufficient; custom-head export and integration remain research work.

There are documented Qualcomm deployment routes:

- [LiteRT CompiledModel with Qualcomm QNN](https://developers.google.com/edge/litert/next/qualcomm)
  offers ahead-of-time and on-device compilation for listed Snapdragon SoCs.
  The separate [QNN Interpreter delegate](https://developers.google.com/edge/litert/android/npu/qualcomm)
  is another documented route; choose one compatible versioned stack.
- [Qualcomm AI Hub compilation](https://dev.aihub.qualcomm.com/docs/hub/compile_examples.html)
  can target LiteRT, QNN DLC or device-specific QNN context binaries. Workbench
  jobs upload assets; do not submit private model/data assets without explicit
  authorization. Its device-farm profile is separate from our physical phone test.
- [Google's LiteRT-LM Qualcomm sample](https://github.com/google-ai-edge/litert-samples/blob/main/samples/litert/qualcomm/gemma3/npu/README.md)
  demonstrates a hardware-matched Gemma 3 text model, not a Kev or live-video port.

Pin exact phone/SoC, Android build, model hashes, compiler/exporter, LiteRT/QNN/QAIRT
versions, quantization and supported operators. The user confirms Nothing Phone
(3a); [Nothing's specifications](https://in.nothing.tech/products/phone-3a) identify
Snapdragon 7s Gen 3. Its model/backend compatibility and actual execution still
need verification; the eventual iQOO model/access are unknown. Run identical
inputs only on compatible tested paths: the current LiteRT CompiledModel
Qualcomm SoC list does not include Snapdragon 7s Gen 3. That is an unresolved
backend compatibility gate, not proof the phone lacks an NPU. Compare identical
inputs on CPU and supported GPU/NPU paths; retain sanitized backend/partition logs,
fallback details and output parity. [Qualcomm documents unsupported-operation fallback](https://dev.aihub.qualcomm.com/docs/hub/faq.html#how-do-i-make-sure-i-m-leveraging-the-npu).
NPU requested is not NPU verified. Measure concurrent decoding/perception/speech,
warmup, sustained latency, memory and thermal behavior. The tiny head may not justify
accelerator overhead; test that hypothesis. An NPU accelerates inference, not aircraft
stabilization or the safety executor.

Phone camera and microphone operate only in an explicitly started shoot session
with runtime `CAMERA`/`RECORD_AUDIO` permission, visible state and a stop control.
Bind camera use to [CameraX lifecycle](https://developer.android.com/media/camera/camerax/architecture).
Test phone capture, microphone and DJI preview together on real hardware; API
availability alone does not prove concurrent performance. If background continuation
is later required, [Android camera/microphone foreground-service rules](https://developer.android.com/develop/background-work/services/fgs/service-types)
require appropriate types/permissions and while-in-use eligibility; do not silently
start from the background. Stop on revoked permission or lost inputs. No recording
or inference is added to the first read-only DJI probe by this design.
