# Local director implementation and evidence

4 October 2026, pre-event research under `prototype/phone-director/`.
This source is not eligible event-written competition code. No archived app
implementation is copied into the new app.

## Components and honest labels

- `DirectorEngine`: editable, handwritten five-shot starters for fashion,
  talking stories, product reveals and introductions. This is a template,
  explicitly labelled without an LLM claim.
  Creator-edited `Shot` durations allow 2–60 seconds, matching the editor.
  Model-generated drafts retain their separate 3–8-second grammar bound;
  the model parser and generation configuration are unchanged by the editor fix.
- `LocalPlanner`: new Java/JNI integration of upstream llama.cpp, text-only
  five-shot generation from the brief. It accepts no images, never sends a
  request to a server, contains no provider key, and owns no camera or flight
  command. The merged app manifest must have Internet permission removed
  explicitly; ML Kit's transitive manifest adds it despite the app's initial
  source manifest omitting it. The instrumentation test checks the installed
  target's permissions. Drafts require creator
  review; a valid JSON schema does not establish creative quality or safe advice.
- `PoseCoach`: bundled ML Kit pose detection followed by framing rules, not
  an LLM or a trained taste/pose-quality model. CPU preference, 750 ms sampling,
  one outstanding image, confidence/coordinate checks, 2-second latency
  rejection and cue throttling. Coordinates are normalized in the visible CameraX crop after rotation. It cannot infer obstacle clearance or reliable distance.
- `SpeechCoach`: explicit call to Android's on-device recognizer only when
  its availability check succeeds. No cloud/default-recognizer fallback.
  An offline English TTS voice is chosen only when installed; unavailable
  offline voices leave written cues usable. Android chooses the playback route.
  Detecting Bluetooth output availability does not prove an AirPods listening
  test or that Bluetooth supplies the recording microphone.

ML Kit dependency: `com.google.mlkit:pose-detection:18.0.0-beta5`, documented
as a bundled model (approximately 10.1 MB APK impact). The SDK is beta and
detects a prominent person's landmarks. See [official Android pose guide](https://developers.google.com/ml-kit/vision/pose-detection/android).
No new framing accuracy, user-benefit or sustained-latency result is established
by compiling these classes. Normal Android camera/microphone permission remains
required and is handled by the screen initiating capture/listening.

## Pinned LLM runtime and model dependency

Runtime: [official llama.cpp](https://github.com/ggml-org/llama.cpp), MIT license,
commit `11fe02151f79c41d0d4af7da708755d73b9c0da6`. The locally cloned checkout
occupies approximately 214 MB before compilation and is ignored. CMake checks
the exact revision. Reproduce dependency preparation from the project root:

```sh
git clone --filter=blob:none https://github.com/ggml-org/llama.cpp.git prototype/phone-director/app/src/main/cpp/llama.cpp
git -C prototype/phone-director/app/src/main/cpp/llama.cpp checkout 11fe02151f79c41d0d4af7da708755d73b9c0da6
```

Native configuration uses the already installed NDK `28.2.13676358` and CMake
`3.22.1`, arm64-v8a, CPU only, four inference threads, 1,536-token context.
The optimized Phone (3a) build uses `armv8.2-a+dotprod+fp16`: read-only
`/proc/cpuinfo` reports `asimddp` and `asimdhp`, and JNI checks matching
`getauxval(AT_HWCAP)` flags before entering those kernels. An unsupported
phone gets an explicit local-planner error and retains templates. Set
`DIRECTOR_PHONE_ARM_ARCH` to an empty value for a portable baseline build;
re-test its performance separately. iQOO feature support must still be checked.
`-O3` is explicit even when Gradle uses a debug variant. The first successful
device inference was already a Release build with `-O3`; an older Debug build
directory initially led to an incorrect performance diagnosis, corrected
before attributing any gain. CPU dot-product instructions are not an NPU.
No NPU, GPU, training or Kev deployment is claimed. The grammar restricts output
to five shot objects with durations of 3–8 seconds and concise fields
(title 24, caption 30, instruction 90 characters); Java validates the schema.
Handwritten scene-beat scaffolding tells the language model what each take
must accomplish. The model adapts the wording to the brief; it has not learned
film direction from new training data.
Generation is bounded by 580 tokens and a 100-second decode deadline. Context
and model initialization can take additional time. Cancellation and release
are serialized through the worker.

A read-only inspection of the generic model already installed in the historical
`dev.focuspilot.prototype` sandbox found `files/qwen35.gguf`, **563,036,064
bytes**. Its GGUF3 metadata identifies:

```text
general.architecture = qwen35
general.name = Qwen3.5 0.8B
general.license = apache-2.0
general.file_type = 2 (mostly Q4_0)
general.base_model.0.repo_url = https://huggingface.co/Qwen/Qwen3.5-0.8B-Base
```

The [publisher's Qwen3.5 0.8B model card](https://huggingface.co/Qwen/Qwen3.5-0.8B)
states Apache 2.0 and research/prototyping applicability for this model scale.
Tensor headers confirm 186 Q4_0, 133 F32 and one Q8_0 tensor. The embedded
chat template's disabled-thinking generation prefix matches the prefix used
by `LocalPlanner`. An additional 4 October primary-source check resolved publisher provenance:
the exact 563,036,064-byte file/SHA matches ggml-org/Qwen3.5-0.8B-GGUF
at revision `8fea620810c4afa23dd6443f999a48574c1611a3`; its PRIMARY source
record pins Qwen/Qwen3.5-0.8B revision `2fc06364715b967f1860aea9cf38778875588b17`.
See [exact reference-source evidence](local-reference-feasibility.md).
The integrator measured SHA-256
`57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`
and verified the local transfer's byte count/hash matched the original.
The original transfer check alone established integrity; the later exact
publisher metadata match also establishes artifact/source identity.
The new app uses a private local file named `files/director-model.gguf` and
does not package weights in Git or the APK. Reusing this generic model as a
dependency does not reuse the previous app's implementation or establish
performance for the new film director. Local device-to-device-sandbox copying
must preserve the original model and must not include old private media/data.

Root integrator owns installation, local file transfer and actual device tests.
`MiniFilmLocalAI` logs only success/failure type, model/backend, shot count and
elapsed time; it does not log the user's brief or model text. A successful
`planner_complete` plus a reviewed adaptive five-shot result is required before
claiming this product has run its local LLM. iQOO execution is a separate gate.

## Speech and subtitle limits

[Android SpeechRecognizer documentation](https://developer.android.com/reference/android/speech/SpeechRecognizer)
distinguishes on-device creation/availability from a default recognition service.
[Voice documentation](https://developer.android.com/reference/android/speech/tts/Voice)
exposes whether synthesis needs network access. Neither API availability nor
voice metadata alone proves an airplane-mode speech/audio test on this phone.

Clip transcription now uses the separately pinned local whisper.cpp tiny.en runtime,
with MediaExtractor/MediaCodec file decoding and no microphone opening. It produces
editable timed English drafts; the synthetic speech-to-export check passed.
See [transcription evidence](transcription-evidence.md). No second microphone
recognizer starts during CameraX recording. Spoken brief recognition still uses
the explicit Android on-device recognizer and requires an attended check.

## Verification ledger

Completed: required project docs read; upstream sources checked; existing
model header/size inspected read-only; fresh template, bundled-pose,
offline-speech and native-planner source implemented. Integrator reports Java
and fresh native build passed, APK installed/launched and model transfer
hash/bytecount verified. `LocalAITest` now checks real ML Kit inference on a
synthetic empty bitmap and two genuinely generated brief-specific model plans;
the test source itself does not establish a passed run. A merged-manifest audit
found transitive Internet/network-state permissions; the integrator explicitly
removed them and reports the installed-target assertion passed. ML Kit's
synthetic empty-frame execution passed at 384 ms cold / 28 ms warm with zero
landmarks. Its attempted telemetry network access was denied. The first
actual CPU model draft returned five shots in 75,787 ms, but its fashion
steps described preparation/tabletop styling rather than a person performing.
That is an observed quality failure despite successful model execution.
The revised concise prompt mandates worn-outfit poses and five actual
recorded roles. A subsequent CPU run returned fashion in **52,553 ms** and
coffee-mug product in **46,473 ms**, each with five valid shots and brief-specific
terms; the installed app's no-Internet assertion passed. Fashion now describes
worn-garment poses and movement. However, the role order drifted, and the
product draft invented a "handle cap" and "ridged grip" not supplied in the
brief. This is an observed hallucination. Drafts therefore remain editable
proposals, with no accuracy/usefulness claim. The prompt and kernel settings
both changed, so these timings do not isolate a kernel optimization's effect.
Source/data provenance
remains distinct from event eligibility.

The public-fixture pose and offline-English synthesis tests subsequently
passed: **2 tests in 2.558 seconds**, recorded in ignored
`private/evidence/pose-voice-test.log`. Public-image inference returned all
**33 landmarks at confidence ≥0.65**, mean in-frame confidence **0.98735**,
in **388 ms**. A voice marked as not requiring network produced an actual
local **203,546-byte WAV in 511 ms**. Neither test opened a camera/microphone
or played audio.

Pending integrator evidence: live offline speech recognition, audible cue/earbuds playback, quality/latency under camera
load, memory/thermal use and iQOO testing. Clip transcription has separate passed
synthetic evidence; live spoken-brief recognition remains unverified. Storage
accounting must include the 214 MB upstream checkout, native build output,
563 MB model copy if retained locally, and shared dependency-cache growth.

## Attributed public positive fixture and synthesis check

One public image was downloaded to ignored
`private/fixtures/google-mlkit-girl-pose.png`: Google's
[annotated pose illustration](https://developers.google.com/static/ml-kit/images/vision/pose-detection/girl_pose_3d.png)
from the [ML Kit pose-detection overview](https://developers.google.com/ml-kit/vision/pose-detection).
Attribution: Google Developers, ML Kit pose-detection documentation;
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) per that page's
content-license notice. Image bytes were not modified. Test decoding samples
it at half resolution in memory. It is an annotated documentation photograph,
not a held-out raw photograph or the user's footage.

Downloaded size **1,383,345 bytes**, original **1190 × 2048** pixels, SHA-256
`012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21`.
The integrator copies it into target-private `files/public-pose-fixture.png`.
Instrumentation verifies that exact checksum, runs the bundled CPU model,
checks 33 returned landmarks / at least 12 confident in-frame points, and
logs measured confidence/latency. Passing this test establishes execution on
that image; it does not validate pose advice, styling quality, camera accuracy
or benefit to the creator.

A separate test selects an English voice marked as not requiring network
and synthesizes a public synthetic cue to an app-private WAV. It checks actual
audio bytes and completion. It never plays sound or opens a microphone. This
test passed with the measurements above; audible quality, AirPods playback
and an airplane-mode test remain distinct and unverified.

## Role-constrained solo-performer revision

An initial ordered-role regression ran two tests in **121.536 seconds** with one
failure: the known fashion performer-count assertion missed the valid verb
“Face”; actual output also instructed holding/focusing the camera, a real solo
shoot usability failure. The unknown-jacket fixture avoided named colors but
still included camera-operator language. These observations are retained rather
than counted as a passed director-quality run.

LocalPlanner now supplies exact per-scene titles to a bounded native grammar,
with role-specific opening verbs for fashion/product and parser checks for exact
order, imperative action and full field limits. The prompt assumes a mounted,
stationary phone and asks the creator to perform. A conservative fashion-only
wording guard rejects camera/phone/screen/tripod while allowing lens eyeline.
It also rejects benign camera wording and does not prove semantic understanding;
product scenes may legitimately feature a phone/camera and are exempt. The
unknown-jacket test now checks performer roles/garment detail and rejects device
wording. Revised actual generation is running; no general accuracy claim follows
from these constraints or fixtures.

Two silent SpeechCoach completion/cancellation tests passed as part of the
**9-test / 14.110-second** framing/speech/UI run. Real offline English synthesis
was used, but no audio playback/microphone/camera occurred. Matching completions
execute on the main thread once; unrelated/stopped IDs cannot complete the next
gate. Actual spoken sequence and AirPods listening still require attendance.

The second role/performer regression passed **2 methods / three actual plans in
95.745 seconds**: unspecified jacket **28.875 s**, yellow raincoat **33.463 s**,
blue ceramic mug **33.284 s**. Jacket cues obeyed ordered performer roles without
named-color inventions or filming-equipment language. Raincoat cues remained
awkward (“check your path with the lens”, “hold your pose against the lens”).
The mug still invented a blue-handle color, glossy finish/grip texture and a hand-to-bowl
use. The old targeted test prohibited cap/ridged errors only and therefore passed
while missing these real quality failures. Its new regression is being strengthened
and product scaffolding refined. This passing runner establishes execution and
its asserted constraints; it does not establish grounded product direction.


### Grounding regressions across story modes

The talking/introduction/product runner took **129.037 s**, three methods with
two failures: missing coffee, invented ceramic rim and repeated product finish/
component/benefit inventions. Talking passed the old assertions but produced
duration-unit captions and an unavailable empty train car. A concrete per-mode
five-shot example improved the next product fixture; that runner took
**140.584 s** with one overstrict introduction-detail assertion. “Feature of
your work” is now accepted only when the preceding Work shot still names the
supplied ceramic bowls; Name and coffee checks remain, and observed rim/rims
inventions are explicitly rejected. Review found new talking door/opened and
past-event drift plus fashion pattern/incomplete-caption errors, now added to
the fixture regressions rather than hidden behind the green portions.

Final sampling temperature is **0.25** (previously 0.65); top-k 20, top-p 0.9 and
seed 42 remain. Prompts request short complete fashion captions, creator-chosen
visible detail, and future lessons rather than invented past events. These are
handwritten prompt/decoding constraints, not new training or proof of grounding.
Five fresh actual plans are being checked; general creative accuracy, unseen
briefs, latency under camera load and usefulness remain unverified.


Final lower-temperature check passed **4 methods / five actual plans in
142.074 seconds**: talking story25.733s, introduction25.563s, unspecified
jacket31.666s, yellow raincoat31.580s and blue mug27.203s. Direct output review
found the targeted observed inventions/fragments absent. Talking retained the
missed-train/walk-home events and future early-departure decision, with an
available creator-chosen cutaway. Introduction retained Rae/ceramic bowls/coffee.
Fashion used concise complete captions and creator-chosen detail; product
retained actual-use/own-opinion prompts without the earlier finish/grip/component
inventions. All five plans kept exact role order and bounded performer cues.
The installed no-Internet assertion passed. These are narrow synthetic fixtures
with handwritten scaffolding; unseen brief quality and creator benefit remain
unverified, and all drafts still require review. Evidence: ignored
`private/evidence/final-planner-test.log` and `final-planner-metrics.log`.

## One-frame visual interpretation — 4 October, final check 14:00 IST

`VisionReference` retains the exact existing language core and uses the locally
converted **204,987,136-byte F16 projector** from official Qwen source revision
`2fc06364715b967f1860aea9cf38778875588b17`, with unchanged llama.cpp pin
`11fe02151f79c41d0d4af7da708755d73b9c0da6`. Projector SHA-256:
`91388cbe4ccde93acd902d7ce32776d14c32bd71a462d4affc1d2e226d81cada`.
Laptop/phone copies match; instrumentation checks core, projector and public
fixture hashes. Actual RGB enters mtmd encoding and M-RoPE, rather than a textual
image placeholder. CPU only: 1536-token context, 64–256 image tokens, at most
192 generated tokens, 180-second native deadline, maximum decoded frame side 512.
The subject/uncertainty fields are grammar-bounded to 80 characters each and
remain editable. No Internet permission, provider request, private-image upload,
camera/microphone capture or training was used for these checks.

The original three-field VLM passed public-person/black and immediate-cancellation
fixtures in **42.766 s**, then **24.206 s** with concise phrases. Public-person
subject recognition took **11.140 s**, black **12.189 s** in that second run.
Those subject checks missed a real error: generated framing called the known
full-body image close-up. A later category prompt called the held-out top-32%
head/shoulders crop full-body. Generative framing was therefore **removed**.
It was not validated by renaming the field or by the earlier green subject tests.

Current displayed framing comes from **FramePoseFraming**, bundled ML Kit
single-image inference with CPU preference, followed by handwritten likelihood
>=0.75 and in-bounds landmark rules. It is an uncalibrated heuristic, separate
from VLM subject inference. The public full-body fixture had **33** visible
landmarks and all required groups (**upper 3/3, hips 2/2, lower 4/4**), yielding
**full-body**. The held-out crop had **12** visible landmarks, **upper 2/3,
hips 0/2, lower 0/4**, yielding **review needed** because upper evidence was
incomplete. It does not claim head-and-shoulders recognition for this crop.
Black pixels returned zero landmarks and **empty or unclear**. Diagnostic
counts/reasons are preserved; extrapolated lower joints do not count as visible.

The **9-method / 45.669-second** checkpoint failed one strict black-subject
assertion: VLM returned **unknown**, not explicit absence/black wording. The
subsequent **9-method / 46.25-second** rerun passed, with a specifically logged
conservative-abstention outcome requiring **unknown subject**, **empty or unclear
heuristic framing**, and **unknown uncertainty** together. This accepts missing
information; it does not turn unknown into correct explicit subject recognition.
Final public/crop/black observations and callback times:

| Fixture | VLM subject | Heuristic framing | VLM uncertainty | Elapsed |
| --- | --- | --- | --- | ---: |
| Public full-body person | person | full-body | unknown | 10.495 s |
| Held-out top-32% crop | child | review needed | hair | 9.308 s |
| Synthetic black | unknown | empty or unclear | unknown | 11.869 s |

The cropped subject's age label is not validated ground truth, and **hair** is
vague uncertainty text rather than calibrated confidence. Results establish
actual local inference and the asserted fixture policies only. Evidence:
ignored `private/evidence/final-local-checkpoint-device-test.log` and
`final-local-checkpoint-metrics.log`; the earlier failed checkpoint remains in
`pose-abstention-checkpoint-device-test.log`.

All **three LocalModelLease methods passed** in that final runner. The shared
fair lease covers the full native language-core lifetime, including an idle
planner handle, and is released only after nativeFree. It waits at most five
seconds with cancellation checks every 50 ms. Tests verified a cancelled waiter
cannot release another owner's permit, real JNI pre-cancellation remains
terminal, and an actual loaded planner is freed before vision owns the lease
without stale planner callbacks. Immediate vision-request cancellation also
passed; this does not certify cancellation latency at every encoding stage.
Existing **18 trim/export/preview/decoder/capture tests passed in 18.56 s**
separately; media success does not establish semantic-reference quality.

An earlier concise-phrase run's read-only memory sampling over **28.184 s**
collected **25** usable samples and **3** missing: maximum sampled total PSS
**1,547,793 KiB**, RSS **1,652,760 KiB**, native-heap PSS **954,096 KiB**.
These are sampled maxima for that build, not certified latest-pipeline peaks.
No camera concurrency, sustained thermal/power or real-time benchmark ran.

The **1,746,942,600-byte source shard** was removed by root after verified
conversion; matching projector output remains on laptop and phone. Conversion
packages, native builds and both copies still count toward the storage budget.
See [exact provenance and conversion evidence](local-reference-feasibility.md).

Reference UI decodes one creator-requested seek in a selected local video of at
most three minutes; exact decoded presentation timestamp is not asserted.
A thumbnail and editable observations are reviewed before explicit **Use corrected notes**
informs planning. Longer combined briefs/notes require shortening, rather than
silently discarding notes. Visible attended UI remains pending normal unlock.
No temporal trend/story understanding, general reference accuracy, useful
creative direction, creator benefit, iQOO execution or NPU acceleration follows
from these public/synthetic tests. Further unseen/consented reference evaluation
and an attended creator workflow remain required.
