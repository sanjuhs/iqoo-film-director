# Local planner comparison feasibility

Research updated 4 October 2026. Root completed the pinned phone-only acquisition
and a CPU comparison on the Nothing Phone (3a). Both compact-prompt model tests
failed. The full-style follow-up passed native/schema mechanics but did not
establish acceptable all-shot quality; the larger candidate is **not promoted**. No default-model change
has occurred. The initial research agent performed no weight download or device
commands; measured results below come from root's subsequent experiment.

**First candidate evaluated: official Qwen2.5 1.5B Instruct Q4_K_M, text only.**
Its exact publisher artifact was acquired phone-only within the qualified
storage aim. Initial acquisition feasibility did not establish better direction;
actual CPU load/inference measurements and quality failures are recorded below.
Retain the current Qwen3.5 0.8B vision core/projector unchanged.

## Exact primary artifacts

| Candidate | Publisher revision | Filename | Bytes | Published SHA-256 |
| --- | --- | --- | ---: | --- |
| Qwen2.5 1.5B Instruct Q4_K_M | `91cad51170dc346986eccefdc2dd33a9da36ead9` | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | 1,117,320,736 | `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e` |
| Qwen3 1.7B Q8_0 | `90862c4b9d2787eaed51d12237eafdfe7c5f6077` | `Qwen3-1.7B-Q8_0.gguf` | 1,834,426,016 | `061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a` |

The 1.5B identity/size are verified from its **pinned Git-LFS pointer**, not an
estimated parameter-size calculation:
[publisher pointer](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/raw/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf).
The [official model card](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF)
documents this quantization with llama.cpp. Its
[full license](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/LICENSE)
is Apache 2.0, including Copyright 2024 Alibaba Cloud. The current published
[repository revision](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/commit/91cad51170dc346986eccefdc2dd33a9da36ead9)
is pinned above. Historical browser attempts to read the revision-qualified
license failed, but root subsequently acquired that **same pinned license** with
bounded HTTPS-only curl. The retained ignored file
`private/planner-candidate/LICENSE.qwen25.txt` is 11,343 bytes, SHA-256
`832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e`,
and verifies Apache 2.0 / Copyright 2024 Alibaba Cloud. Root also retained the
pinned README declaring `apache-2.0`. This resolves the license access limit
without changing upstream or revision. The phone file independently matches the pinned size/hash. Actual CPU loading
and synthetic inference are measured below; useful direction remains unproved.

For the heavier alternative, the
[revision-qualified artifact page](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/90862c4b9d2787eaed51d12237eafdfe7c5f6077/Qwen3-1.7B-Q8_0.gguf)
verifies the SHA, and its
[pointer](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/raw/main/Qwen3-1.7B-Q8_0.gguf)
gives exact bytes. Its
[pinned license](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/blob/90862c4b9d2787eaed51d12237eafdfe7c5f6077/LICENSE)
is Apache 2.0 with Copyright 2025 Alibaba Cloud. The inspected official tree
publishes Q8_0 only; this review does not establish an official smaller Q4 artifact.

Qwen3.5 2B is an official model, but the inspected
[Qwen source tree](https://huggingface.co/Qwen/Qwen3.5-2B/tree/main) provides a
roughly 4.55 GB safetensors shard, rather than a ready GGUF. Its source revision is
[`15852e8c16360a2fea060d615a32b45270f8a8fc`](https://huggingface.co/Qwen/Qwen3.5-2B/commit/15852e8c16360a2fea060d615a32b45270f8a8fc).
Neither attempted `Qwen/Qwen3.5-2B-GGUF` nor
`ggml-org/Qwen3.5-2B-GGUF` lookup supplied a readable exact primary GGUF
revision/file/hash in this session. This is an access/verification limit, not a
claim that no such repository exists. Community conversions were not accepted
as an official-publisher substitute. A local conversion would need the source,
an intermediate text GGUF, quantizer output and tool availability accounted
together; this research did not establish a sufficiently bounded conversion
peak or a finished quantized hash. It is therefore not today's minimal route.

## Storage and memory

Baseline is the existing qualified `docs/storage.json` accounting:
**7,546,289,200 bytes**, including phone copies and historical archive/cache
reserves. The missing archive and incomplete global-cache inventory still limit
that accounting. Values below add candidate bytes; they are projections, not new
disk measurements.

| Candidate | Phone only, streamed acquisition | Laptop + phone | Accidental third copy |
| --- | ---: | ---: | ---: |
| 1.5B Q4_K_M | 8,663,609,936 | 9,780,930,672 | 10,898,251,408 |
| 1.7B Q8_0 | 9,380,715,216 | 11,215,141,232 | 13,049,567,248 |

Two 1.5B copies leave only 219 MB beneath the 10 GB aim before further build,
cache or media growth. Prefer streaming the pinned public file into a **new**
ignored phone path, incrementally counting bytes and computing host SHA, then
independently checking phone SHA/size before publishing that path. A same-directory
rename of the verified temporary file avoids a duplicate copy. Avoid an extra
Hugging Face cache, host staging weight or model in the APK. Remeasure totals and
free phone space before acquisition; the 15 GB hard cap still applies.

Parent's read-only phone snapshot reports total 7,607,308 KiB and available
2,526,952 KiB: approximately **7.255 GiB total / 2.410 GiB available**. This is
momentary system memory, not a process allowance. Q8_0's 1.834 GB weight file
alone leaves considerably less space for context, graph buffers, app and OS.
Q4_K_M's 1.117 GB file is the more conservative first load, though neither
file size predicts PSS/RSS or guarantees survival under Android memory pressure.

## Runtime and minimal experiment

The inspected local llama.cpp remains pinned at
`11fe02151f79c41d0d4af7da708755d73b9c0da6`. Its
[Qwen2 loader](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/src/models/qwen2.cpp)
explicitly identifies 28-layer/1536-hidden 1.5B, and its
[Qwen3 loader](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/src/models/qwen3.cpp)
supports 1.7B. Existing JNI loads a caller-supplied GGUF path, CPU only, mmap,
1536-token context, four threads; it accepts at most 900 prompt tokens and 580
generated tokens with a 100-second generation deadline. Source support is
promising before testing; the 1.5B candidate subsequently loaded on Android CPU
in 1.986 seconds in the compact comparison below.

First use a headless comparison test with existing private `nativeLoad(path)` /
`nativeGenerate` / `nativeFree` entry points and the shared full-lifetime model
lease. Close the resident planner first. Use a new path such as
`files/models/planner-qwen25-1_5b-q4km.gguf`; verify exact hash/size before native
load. Keep production selection and the existing vision paths untouched.

For a later selectable profile, add fixed model identity, path, hash, size,
label, license attribution and prompt format together. A verified explicit
profile selection must close/free the old planner before loading the new one;
missing/corrupt candidates report an error and never silently substitute weights.
Display `Qwen2.5 1.5B Instruct Q4_K_M · local CPU · editable AI draft`, retaining
the existing creator-authored/unknown-detail constraint disclosures. Preserve
the exact 0.8B default and vision/core projector pairing.

The 1.5B profile needs its own chat suffix: Qwen2.5 Instruct's
[published template](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct/blob/main/tokenizer_config.json)
uses ChatML assistant start without Qwen3.5's synthetic empty `<think>` block.
Keep task instructions and JSON grammar semantically identical when comparing;
measure both token counts. The Qwen3
[official card](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF) separately documents
nonthinking control and sampling guidance; it must not inherit an undocumented
Qwen3.5 profile by filename alone.

The initial feasibility review had no candidate phone timing measurement.
Subsequent measurements are recorded below. More weights/layers may
increase prefill, generation and loading cost; do not infer latency linearly from
parameter count or promise an improvement. Use the current deadline initially,
record timeout as failure, and measure load/prefill/generation/cancellation plus
sampled PSS/RSS and available memory. CPU support is separate from Qualcomm NPU
execution. This experiment would not extend or replace the vision model.

## Honest quality decision

Freeze a small held-out set before any candidate prompt tuning: two fresh solo
fashion briefs, two different products, a new walking/talking event with a future
decision, and a new introduction with explicit work/personal fact. Keep creator
facts, grammar, seed and review constraints the same. Retain every raw native
output, timings, errors and the exact model/runtime identities.

Evaluate raw native instructions for meaningful performer actions, small safe
movement, actual subject use, no invented parts/colors/materials/benefits/events,
and readable captions. Specifically catch errors such as walking toward a
garment part or treating an opinion as a product property. Do not score
deterministically retained creator cues or closed-choice Detail as learned
grounding; report those separately. Compare creator-review corrections and
preferences independently of schema success. A few successful fixtures can
justify continuing the comparison, not a general accuracy or creator-benefit
claim. If quality or latency does not improve, keep the existing default and
document the candidate failure instead of silently promoting it.

## Completed phone-only acquisition and first comparison

The pinned 1.5B artifact was streamed directly into the app's private
`files/models/planner-qwen25-1.5b-q4km.gguf`, with no host weight copy. The
109.526-second transfer counted 1,117,320,736 bytes and verified SHA-256
`6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`
in transit and independently on the phone before publishing the isolated path.
No partial candidate remains. Its full pinned license was retained locally.
The default model path and paired vision core/projector were not changed.
The actual transfer completed before a future-run write watchdog was added to
the ignored helper; the initial run is not claimed to have that watchdog.
Qualified accounting after acquisition is **8,653,126,704 bytes** including phone
files and historical reserves; missing archive/global inventory limitations remain.

The first comparison called the actual native loader/generator/free under the
shared full-lifetime model lease, with the same three synthetic briefs and a
compact comparison prompt. It bypassed creator-cue retention and used no private
media. It is **not a full production prompt comparison**. Both model files were
size/hash checked before and after inference. The baseline used its empty-think
ChatML adapter, while Qwen2.5 used its published plain ChatML adapter. Runtime was
the pinned llama.cpp, four CPU threads, 1,536-token context, 580 output tokens and
100-second generation deadline. The 2-test runner failed both methods in
279.718 seconds. The old compact test source was not snapshotted before the prompt refactor;
that limits exact prompt reproduction from this checkpoint. The log retains
the original briefs and prompt character counts. Every raw result and targeted
lexical flag is retained in
[the compact comparison evidence](planner-model-comparison-compact-evidence.json).

| Model | Load | Reviewed jacket | Nila introduction | Notebook reveal |
| --- | ---: | ---: | ---: | ---: |
| Qwen3.5 0.8B Q4_0 | 1.302 s | 83.405 s, incomplete JSON | 35.730 s, parsed with weak work/caption wording | 31.965 s, parsed with third-person verdict |
| Qwen2.5 1.5B Q4_K_M | 1.986 s | 35.701 s, rejected by production Fashion device-word filter | 46.943 s, parsed with repeated facts/invented prop and malformed wording | 37.740 s, parsed with invented positive verdict |

The baseline fashion result spent its output budget on excessive whitespace
after a partial first object. The candidate fashion directions lost left/profile/
forward cues, and repeated the caption “Hero”. The introduction repeated the same
work/hobby sentence across roles, invented a chess set and produced a corrupted
caption. Its notebook verdict “Say this is useful” invented praise. Passing JSON
or keyword checks would not make these acceptable directions. The conservative
Fashion parser also rejects benign camera wording; its failure does not mean
the candidate failed native loading. Neither model established raw grounding of
creator reference cues or general creative quality.

A numeric-only memory sampler obtained 205 snapshots over 251.463 seconds,
starting after the first model had loaded and covering both models. Observed
maximum PSS/RSS/native-heap PSS are in the evidence JSON. These mixed-model
snapshots are not certified peaks, per-model allowances, camera concurrency
measurements or evidence of iQOO/NPU execution. Both native cores were freed
before their model leases were released. No camera, microphone, earbud playback,
private upload or aircraft action was performed.

The follow-up bounds each JSON whitespace run to eight characters and reuses
the exact production style prompt for both native models, changing only the
model-specific assistant adapter. These are format/prompt experiment changes,
not evidence that the candidate is useful. Completed results follow.

## Full production-style comparison — 15:04 IST

The next installed test reused the exact production style-prompt builder;
Qwen2.5 omitted only the Qwen3.5 empty-think adapter. Briefs were unchanged.
Fashion used the reviewed-moments style but no manual cue retention or unknown
Detail constraint, since the synthetic brief specified a left pocket. This raw
comparison does not call the public cue preflight parser: its four named moments
are a prompt stress fixture, not a valid three-moment saved board. Whitespace
runs were bounded to eight characters. Both methods passed in **214.062 seconds**,
meaning six native/schema results and correct release, **not six useful plans**.
The exact pre-movement-repair production source was retained locally at
`private/evidence/LocalPlanner.full-style-before-motion.java`, SHA-256
`ccf0bf81f102971c6b384b3dbfe4110917a617231f2b01e555c572a2bb8c087e`;
the original prompt literals are also in commit7548ca0. See [all raw results and flags](planner-model-comparison-full-style-evidence.json).

| Model | Load | Fashion | Introduction | Product | Prompt tokens in that order |
| --- | ---: | ---: | ---: | ---: | --- |
| Qwen3.5 0.8B Q4_0 | 1.428 s | 35.616 s | 24.512 s | 27.503 s | 675 / 610 / 673 |
| Qwen2.5 1.5B Q4_K_M | 2.146 s | 40.720 s | 30.225 s | 46.291 s | 669 / 605 / 666 |

Independent manual review found the baseline introduction and notebook directions
faithful and feasible on these fixtures, with generic but readable captions.
Its fashion draft preserved left/profile/forward and pocket facts, but the
Movement **“Take the left pocket”** remained incoherent even though every targeted
keyword flag was true. The profile instruction omitted the explicit pause.

The candidate improved that one Movement to a small step but weakened the
requested profile/face-forward stance and produced verb-only captions
“Take/Show/Turn/Look”. Its introduction caption “Bicycle wheel” selected an
unconfirmed detail, and its personal-fact instruction became generic despite
a chess caption. Its product plan dropped explicit elastic closure and list
use, while retaining an honest opinion request. The candidate was slower on all
three inputs and is not promoted. Existing model paths and vision pairing remain
unchanged. Formatting completion improved in this combined prompt/grammar
experiment; that does not isolate a causal whitespace effect or prove grounding.

The 170 numeric memory snapshots over 208.407 seconds observed maximum PSS
1,957,154 KiB, RSS2,022,112 KiB and native-heap PSS1,133,778 KiB, mixing both
models and beginning after baseline load. These are sampled observations, not
certified peaks or camera-concurrency evidence. No capture, mic, audible playback,
private upload or aircraft action occurred. A narrow lexical guard and explicit
body-movement prompt repair are being checked separately; they are not a learned
semantic validator.

## Movement regression check — 15:09 IST

A ten-method targeted runner took **145.281 seconds**, passing nine methods
and failing the valid explicit-Detail native board case. Three lexical/preflight
methods passed: two bad phrases came from actual synthetic native outputs
(taking/walking to the left pocket), and collar/sleeve destinations were two
new synthetic analogs. The initial installed test log mislabelled all four
as observed; test-source provenance was corrected and the unchanged assertions
are being rerun. Two ordinary held-out native plans passed in 40.438/30.819 s,
and the unknown-Detail hybrid board passed in32.552 s with an actual small-step
Movement. All three model-lifetime methods passed, including actual text-to-vision
free-before-lease handoff and stale callback suppression.

The explicit-Detail error was initially generic. A fixed private exception type
and privacy-safe category now distinguish a rejected Fashion Movement without
logging a private brief, raw output or arbitrary exception text. A specific user
message keeps the existing plan available and offers the labelled starter.
The exact cause is being confirmed in a separate diagnostic rerun; no creative
quality or all-board success is claimed from the nine passes.

The corrected-provenance diagnostic runner passed the three unchanged guard
methods, but the explicit-Detail native method failed again: **4 tests, one
failure,45.263 s**. Its log was generic `IllegalArgumentException`, not the new
`fashion_movement` category. That rules out the typed Movement filter as the
immediate cause of this rerun; it does not identify the remaining policy or
shape failure. An exact-brief raw native diagnostic is being prepared using
synthetic creator notes only. No Movement repair is implemented without that
causal evidence.

## Exact-brief raw diagnostic — 15:15 IST

The raw diagnostic acquired the pinned baseline, called the same production
prompt/grammar/adapter on the exact three reviewed notes, logged its complete
synthetic output in numbered chunks before parsing, and freed native state
before releasing its lease. The 731-token prompt produced five native rows in
44.740 s including load. The one-test runner failed its parser check, with
**“Fashion cue refers to a filming device”**. The actual Closing was benign
**“Look at the camera”**; Movement was already two small steps. This was an
observed conservative-filter false positive, rather than an invalid Movement.
Captions were also unhelpful labels **F/S/J/S/C**, which the existing shape/
keyword checks had allowed. Raw data is retained in
[the diagnostic evidence](reviewed-board-eyeline-caption-failure.json).

Source work now permits only the whole observed eyeline phrase as an exception,
retaining rejection of instructions that manipulate the filming device. It
rejects single-letter Fashion caption labels and adds a compact five-shot
caption/body-action example. No automatic cue rewrite, hidden template output
or native retry is added. New source/tests remain to be built and measured.

## Eyeline/caption repair check — 15:21 IST

The compact five-shot Fashion example kept native prompts below the unchanged
900-token cap (actual634/696/664/664 tokens). The nine-method runner passed in
**106.568 seconds**: five deterministic policy/preflight cases, one ordinary
kurta generation, two actual hybrid board plans and the exact raw diagnostic.
The same raw explicit-Detail input now produced readable **Hero/Small steps/
Pocket/Side view/Final pose** captions and a practical small-step Movement in
**24.000 seconds** including load. Its raw Closing still used lens eyeline rather
than the requested face-forward stance; exact creator retention remains necessary.
No learned grounding or isolated performance-speedup claim is made.
[All raw repaired results](reviewed-board-eyeline-caption-repair.json) accompany
the earlier failure.

The hybrid board plans took **28.205/23.842 seconds** and kept their exact three
creator instructions with the proper constraint/retention labels. The ordinary
kurta case took28.726 s including callback and used the actual garment noun,
small steps and readable captions. Manual review found that its Detail chose
“fabric texture” despite the brief asking the creator to choose a detail. That
remaining fidelity limit passed the targeted color/material/fastener tests, and
is explicitly retained; the narrow caption guard only requires two letters,
not two words, meaningful phrasing or general creative quality. Hero/Pocket are
readable but generic one-word captions.

Only the complete observed camera-eyeline phrase is allowed. Mixed or unrelated
filming-device instructions still reject in native/manual paths. Source tests
were aligned to this deliberate exception while preserving hardware-caption,
unknown-fact and mixed-operator assertions. A final six-method guard/plain-jacket
checkpoint is in progress. No native repair/retry, output rewrite, default-model
change, camera, microphone, audible AirPods or aircraft action was added.

The additional six-method checkpoint passed all five deterministic guard tests
but failed the ordinary unknown-color jacket generation with a generic
`IllegalArgumentException` in **42.786 seconds**. The nine-method board/kurta/raw passes therefore
do not establish all ordinary fashion briefs work. An exact plain-jacket raw
diagnostic is being added rather than changing acceptance to hide the failure.
The prior successful fixture results remain intact and the app leaves invalid
drafts unapplied, offering the labelled starter/current plan.

## Plain-jacket causal diagnostic — 15:30 IST

The exact public plain-jacket brief was run through the same installed production
prompt/grammar/adapter with no manual merge. The one-method runner failed in
**30.933 s**; actual native load/generation took **29.168 s** at636 prompt tokens.
Its Hero “Stand facing the camera with the jacket on your chest.” was a benign
performer cue rejected by the narrow whole-eyeline exception. Independently, its
Detail invented **“Show the zipper pull on the jacket side.” / “Zippers”** despite
the brief explicitly withholding fasteners. The model freed before its lease
released and its pinned weight hash remained unchanged.
[Complete synthetic raw evidence](plain-fashion-camera-detail-failure.json) is
retained, along with an ignored exact source snapshot digest.

A bounded camera-operation filter and a disclosed creator-choice Detail grammar
for fashion drafts without a named creator-reviewed Detail are now being prepared.
The latter deliberately does not extract ordinary free-text garment features;
creators can author a reviewed named Detail or edit the draft themselves. Merely
allowing body poses toward a camera would have exposed the invented zipper; both
issues need separate checks. No output rewrite, hidden starter, native retry or
default-model promotion is intended. This source work is not yet device evidence.

## Camera/unknown-Detail repair — 15:38 IST

The frozen source now tests bounded English camera-operation wording rather
than rejecting the camera noun itself. Seven benign body/eyeline fixtures
(one observed native phrase and six synthetic analogs) remained exact in draft
and reviewed paths; 18 synthetic direct-handling/hand-placement/pronoun fixtures
were rejected. Other phone/screen/tripod/drone/gimbal wording remains conservative.
This is an English lexical policy with unlisted-wording and pronoun-ambiguity
limits, not semantic or aircraft-safety validation.

All Fashion plans without an explicitly named creator-reviewed Detail now use
the existing closed creator-choice instruction/caption grammar with its visible
label. Ordinary free-text garment parts are not extracted or verified; specify
a named reviewed Detail or edit the resulting shot yourself. There is no
after-generation rewrite, hidden starter output, automatic retry or model switch.

The nine-method runner passed in **62.159 s** (seven deterministic methods plus
raw/plain public native paths). Both 670-token requests produced the same
synthetic five-shot jacket plan: raw generation **28.910 s** including load,
public generation/callback **30.837 s**. Hero kept the formerly rejected camera-
facing phrase; Detail requested the creator's choice with “Chosen detail”,
without the invented zipper. Steps/body turns were feasible on this fixture.
Hero's “Jacket” caption remains generic and one word. The two measured paths
do not establish general repeatability or accuracy.
[Complete repaired raw evidence](plain-fashion-camera-detail-repair.json) retains
source digest, all measured policy events, tokens, label and full public plan.

The separately frozen MainActivity source also resets ready/canceled labels
when Stop or sequence-disable cancels a pre-recording countdown/pose break.
The guard excludes a take already starting/recording/finalizing, which is not
claimed canceled by disabling future sequence advancement. Review caught and
corrected that Start-callback race before build. This label repair and planner
cleanup requests are source-reviewed only; attended UI/camera/mic checks remain
pending behind secure keyguard. Secondary kurta/reviewed-board regressions are
being measured separately.

The four-method secondary runner passed in **118.576 s**. Kurta took
**42.055 s** and now retained a genuine creator-choice Detail, without any tested
unsupplied color/material/fastener/part. Hybrid unknown/explicit boards took
**27.243/23.770 s**, preserving exact named creator directions and correct separate
labels. Raw explicit-board native generation took **23.832 s**, including load.
Prompt counts668/697/664/664 stayed below the unchanged900 cap. Source did not
change between the two runners; timings are observations, not a speed benchmark.

Independent manual review still found ambiguous jacket “on your chest” wording,
repetitive keep/hold-garment-still phrasing, generic one-word Hero/Pocket captions,
and a face-focused “My face” caption on an outfit story. The raw explicit-board
Closing still substitutes lens eyeline for face-forward stance; hybrid creator
retention remains necessary. All4s take lengths are draft allocations, not
measured performance or speech fitting. These limits and every full plan are
in the repaired evidence JSON. No general creative fidelity or user benefit
is established by the13 focused passes.

## Shared-grammar Talking regression — 15:42 IST

Because the shared whitespace grammar changed, a previously passing public
Talking-story fixture was rerun rather than assuming Fashion passes covered it.
It failed **one test in40.897 s**, with actual675-token CPU generation/callback
**40.878 s**. Train/walk-home facts and roles passed their earlier assertions;
Cutaway invented **“Show the door you opened.” / “The door”**, absent from the
brief. Key idea said “Explain you chose to leave early.” / “Leave early”, losing
an explicit next-time marker; the door assertion failed before the later future
check, so that later assertion has no measured outcome in this run.
[Complete available synthetic callback evidence](talking-cutaway-failure.json)
retains its full flattened plan and the exact frozen production source digest.
Raw native JSON/durations were not logged by that existing test, so they are not
reconstructed as observed fields.

A disclosed creator-choice generic Cutaway grammar/parser is being prepared
for Talking stories, analogous to the Fashion Detail constraint. It will not
extract a specific prop from ordinary free text or rewrite a generated output;
the creator can edit the shot. The future-decision assertion will remain intact.
The13 Fashion checks remain valid, but do not establish Talking-story fidelity.

## Talking creator-choice repair — 15:48 IST

The source adds a separately disclosed native Cutaway instruction/caption
constraint for all Talking stories: choose an object already available for the
story, with generic text. Grammar and parser require one of two instructions
and three captions; no generated output is rewritten. Ordinary free-text props
are not extracted/verified and can be added by creator editing. Other four
roles, Fashion/Product/Intro branches and future-story prompting stayed unchanged.

The four-method runner passed in **67.135 s**: two deterministic parser methods
plus actual train and held-out umbrella stories, each675 prompt tokens. The
original story took **39.353 s** including callback (native39.342 s), umbrella
**27.692 s** including callback (native27.597 s). The future check was made more
explicit: its instruction must contain an actual future/next-time marker, rather
than allowing a decision word alone. All earlier no-door/unsupplied-event checks
remain, and every structured public plan was logged before semantic assertions.
[Complete repaired plans and provenance](talking-cutaway-repair.json) accompany
the retained prior failure.

Manual review found both drafts faithful to the supplied events on these two
fixtures, with explicit future lessons and no invented props/places/transport/
benefits. Generic Cutaway is honest creator-choice policy, not learned visual
availability. The last two takes repeat the future lesson with generic captions,
and all4s lengths still need performance review. Two successful stories do not
establish general accuracy, useful live filming, speech timing or user benefit.
No further source change followed these checks.
