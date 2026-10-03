# Tiny director model — Android research design

Proposed on 4 October 2026 (IST). No model has been trained, downloaded or run
for this product. This is pre-event research; future experiments belong under
`prototype/`. Development targets the user's Nothing Phone (3a); iQOO is the
event target and needs its own measurements. The iPhone is not the deployment
target. Training on the laptop and local inference on Android are separate gates.

## What we should learn first

The user-confirmed reference is [Kev](https://github.com/jaredpalmer/kev), a Jev-like
decision-model family. Its documented API scores text states through yes/no,
choice and rating questions; the smallest published version uses a Qwen3.5-0.8B
base with an adapter and pointer head. Documented serving uses CUDA/ROCm or MLX,
not an established Android vision path. Its base-model lineage does not prove
Kev can process our camera feed. The useful analogy is **perceive a state, then
make a bounded decision**. Our purpose-specific head can be far smaller.

Train a small dense neural network to answer a narrow question: **given measured
framing and the intended shot type, which framing suggestion would this creator
prefer?** This can be trained from scratch without attempting to build a language
model from scratch. It is an MLP classifier, not an LLM, general film director or
demonstrated world model. Its outputs are suggestions rather than flight commands.

The complete director also needs an editable shot plan, coverage state, clip
import and creator decisions; six classes cannot infer a whole fashion story.
The [full vision](vision.md) adds phone/drone perspectives, opt-in speech,
separate Kev shot-choice research, approved control and aligned editing. This
small head remains a narrow framing baseline within that system.

## Smallest proposed learned head

Start with 24 normalized inputs, two hidden layers of 32 and 16 units, and six
output scores. Inputs are:

- Four subject bounding-box values: center x/y and width/height.
- Eight coordinates for four selected body landmarks, plus their four confidence
  values. Landmark choice and missing-value behavior must be fixed before training.
- Four image diagnostics: sharpness, exposure, estimated horizon tilt and frame
  age. These require their own implementation and validation.
- Four values encoding the requested framing: full body, upper body, detail or
  wide establishing shot.

Six classes: retain framing, reframe left, reframe right, frame wider, frame
tighter and request creator review. Advice specifies the image composition;
it does not assert which physical movement is safe or how far a drone should move.
Vertical guidance and semantic questions such as whether the jacket detail is
visible remain outside this first learned head.

Parameter count, including biases:
`(24 × 32 + 32) + (32 × 16 + 16) + (16 × 6 + 6) = 1,430`.
That is 5,720 bytes of raw float32 parameters. **This is not the app or perception
footprint:** subject/landmark detection, decoding, runtime, activations and UI can
cost much more. Measure the entire pipeline, including preprocessing and sustained
latency, RAM, temperature and battery behavior on each Android device. Reject
stale frames, absent subjects and low-confidence landmarks before classification;
unknown input must produce review/stop rather than an invented correction.

## Learning from everyday shoots

With explicit consent, use locally retained, user-selected frames or clips. Do
not upload footage. For each example the creator labels intended shot, acceptable
framing, preferred correction and reasons. These are personal creative preferences,
not universal “best camera position” labels. Record ambiguity and allow several
acceptable suggestions instead of forcing a false single answer.

Separate train, validation and locked test sets by entire shoot/episode/day;
adjacent frames and near-duplicates must stay together. Include different outfits,
lighting, locations and camera sources. Fit normalization only on training data.
Private data, weights and identifiers stay outside Git. Estimate retained archive,
model and shared-cache storage before downloads; no current total is established.

Predeclare comparisons against manual shot guidance, majority-class prediction,
simple framing rules and a linear classifier. Proposed go criteria: outperform
the strongest baseline on held-out shoots; reduce disagreement with the creator;
maintain a preselected high precision for displayed corrections; and meet the
measured device latency/resource budget. Set numerical thresholds before viewing
test results. Report class counts, uncertainty, abstentions and accepted/rejected
suggestions. A handful of clips is feasibility evidence, not demonstrated benefit.
If the head adds no useful improvement, keep the simpler baseline.

## Optional next-state predictor

Kev/Jev decision models differ from JEPA. [Meta's V-JEPA repository](https://github.com/facebookresearch/vjepa2)
describes video representations learned through latent prediction and a separate
robot-data-trained action-conditioned model. Neither establishes DJI capability.

Later, paired **before state → known action → after state** examples could train
a small action-conditioned predictor of framing-feature changes. Begin with
creator-guided phone movements. Test predicted outcomes against held-out actual
movements and a persistence/linear baseline. Phone data does not guarantee
aircraft transfer; drone experiments need separately approved, bounded actions and
accurate synchronization. This would not reproduce Meta's system.

A storyboard's target images or desired shot positions are **shoot goals**.
A saved neural-network checkpoint is **learned weights**. They are different
objects. Neither establishes obstacle clearance, depth, a safe orbit or flight
feasibility from a single camera image.

## Product and flight boundary

Possible pretrained vision candidates under investigation include Gemma 4 E2B
with LiteRT-LM, SmolVLM2-500M and Qwen3.5-0.8B. Their licenses, actual image support,
Android packaging, performance and useful accuracy need source review and testing;
they are not selected dependencies or demonstrated local capabilities.

First milestone remains the propellers-off read-only connection/telemetry/preview
probe: no motors, flight, recording, gimbal movement or waypoint upload. Later,
the model proposes a shot, the creator reviews it, and a deterministic executor
checks approved bounds and fresh state. Flight actions need explicit action-level
pilot confirmation and tested manual override. Unknown or stale perception stops
automation; app-generated shot suggestions never bypass DJI safety behavior.
