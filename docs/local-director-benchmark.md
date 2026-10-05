# Local director latency — phone comparison and clean benchmark

5 October 2026 (IST). Pre-event research. This document separates existing
Android evidence, proposed models, and a new minimal benchmark. The website is
a simulation; it does not execute the local speech/vision stack.

## What can run in the middle?

There is no selected checkpoint named “GPT-0.5B” in this project. If that means a
small Qwen model, choose the exact model and input type before measuring it.

| Candidate | Inputs | Role and present evidence |
| --- | --- | --- |
| [Qwen2.5-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct) | Text | Official card reports 0.49B parameters. A candidate for a short cue from a reviewed transcript; no measurement of this checkpoint on either phone here. |
| [Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B) | Text | Candidate with a published non-thinking switch. It is not an image model; no measurement here. |
| [Qwen3.5-0.8B](https://huggingface.co/Qwen/Qwen3.5-0.8B) | Image/text with the required vision path | Existing pinned core and matching projector have narrow current-prototype Android CPU execution evidence below. A language GGUF alone does not process pixels. |
| [SmolVLM-500M-Instruct](https://huggingface.co/HuggingFaceTB/SmolVLM-500M-Instruct) | Image/text | A real approximately 0.5B multimodal alternative from Hugging Face. Neither its Android execution nor director quality is measured here. |

Qwen2.5 0.5B and Qwen3 0.6B can read textual pose measurements produced by a
separate vision component, but that does not give those checkpoints VLM
capabilities. SmolVLM's publisher memory example is not a measurement of this
Android runtime. A smaller parameter count alone does not establish latency or
pose/shot accuracy. The exact chat template and a bounded short output matter;
Qwen3's [official non-thinking configuration](https://huggingface.co/Qwen/Qwen3-0.6B)
is preferable to spending an interaction's budget on reasoning tokens.

For speech, the design proposes Kokoro-82M and an English Moonshine Tiny variant.
Their model/runtime/component licenses, the streaming Tiny published-count
discrepancy, and the distinction from currently running speech are recorded in
[the speech brief](../design/speech-models.md). Neither has been measured in
this Android app. The current native evidence uses Whisper tiny.en and Android
offline TTS instead.

## What the Nothing phone has actually done

These are **4 October current Mini Film Director research-prototype** observations
on the Nothing Phone (3a), Android 16. They are not the archived FocusPilot
app's results, and they are not measurements of the website or the new clean
benchmark. The generic language weight was initially reused from the historical
app's sandbox, then matched to published provenance; new product inference
tests established the current results. The full historical archive is currently
missing from its recorded location; do not claim a verified complete recovery.

| Component/workload | Observed time | What it establishes |
| --- | ---: | --- |
| Qwen3.5 0.8B Q4_0 model load in full-style comparison | 1.428 s | One recorded initialization; not a cold-storage distribution. |
| Same model, five-shot text plans | 24.512 / 27.503 / 35.616 s | Introduction/product/fashion fixture generation at 610 / 673 / 675 prompt tokens. Four CPU threads; output bounded by 580 tokens. This is a full plan, not a short conversational cue. |
| Same model plus exact-source vision projector, one frame | 10.495 / 9.308 / 11.869 s | Public full-body image / held-out crop / synthetic black pixels in the final narrow run. Subject execution passed; framing uses a separate landmark heuristic after real generative framing failures. |
| Whisper tiny.en, selected synthetic 8.759 s media | 4.516 s | Three timed English subtitle cues from a known file, without live microphone input. |
| Reviewed speech-context fixture, then five-shot Qwen plan | 42.400 s for planner | Actual STT → test-authored correction → CPU plan. Compute durations sum to 46.916 s; human review and TTS were not timed. |
| Android offline TTS, short public cue → WAV | 0.511 s | Local 203,546-byte waveform synthesis. No speaker/earbud playback or Kokoro benchmark. |

Sources: [full production-style comparison and raw results](planner-model-comparison-feasibility.md),
[raw comparison JSON](planner-model-comparison-full-style-evidence.json),
[exact vision source and fixture evidence](local-reference-feasibility.md),
[speech-context pipeline](reference-speech-evidence.md), and
[local AI/TTS evidence](local-ai-evidence.md).
These tests include quality failures, prompt changes and isolated fixtures; they
are not a calibrated sustained-performance benchmark. Output token counts were
not recorded for every historical plan, so tokens/second cannot be reconstructed
from elapsed time alone. No continuous every-frame VLM, camera concurrency,
Moonshine/Kokoro loop or iQOO result follows from them.

The measured existing full-plan path is **tens of seconds**. Adding these
independent numbers does not create a measured live conversational loop.
Putting a VLM in every spoken turn would also add image preparation/encoding and
memory contention; existing image timings are not negligible.

## Phone comparison without invented scores

The user selected the current India iQOO flagship for comparison. The official
India site lists [iQOO 15 specifications](https://www.iqoo.com/in/products/param/iqoo15):
Snapdragon 8 Elite Gen 5, 12/16 GB RAM and Android 16 at launch. Nothing's
[Phone (3a) specification](https://intl.nothing.tech/products/phone-3a)
identifies Snapdragon 7s Gen 3. Exact physical RAM, installed OS and thermal
conditions of each tested unit must be recorded independently; virtual RAM is
not physical model memory.

| Device | Current practical assessment | Missing evidence |
| --- | --- | --- |
| User's Nothing Phone (3a) | Suitable development/measurement phone: the current 0.8B model has genuinely run on CPU. Existing long plans and image requests are too slow to call instant direction. | Fresh short-cue distribution, proposed speech stack, sustained camera/audio coexistence. |
| iQOO 15 | Flagship comparison candidate with a newer high-end chip and more published RAM. It is reasonable to investigate higher throughput and memory headroom; this is an inference from specifications. | Actual unit, same artifact/runtime/workload, measured sustained results and actual selected backend. |

There is no defensible numeric handset rating, speed multiplier or tokens/second
comparison yet. General phone benchmark scores and advertised AI TOPS do not
measure this model's end-to-end loop. Access to a chip's NPU does not establish
that llama.cpp or the selected model uses it. CPU, GPU and Qualcomm NPU
conversion/backend experiments must have separate, attributed results.

## Define the loop before assigning a target

Measure **last user speech sample → first audible useful response** for the
interactive loop, and separately measure speech onset → complete reply.
Exclude neither endpointing nor audio output from the first metric. A file STT
test instead starts from an already available fixture; label that boundary.

```text
Explicit speech session
  → endpoint/final transcript
  → short reviewed state + optional fresh visual observation
  → one short model cue
  → validate complete usable phrase
  → TTS first audio chunk
  → output actually begins
```

Approximate timing accounting is:

```text
response onset = endpoint delay + remaining STT + queue/model load
              + optional frame decoding/vision encoding
              + prompt processing + useful cue generation
              + TTS first chunk + audio-output delay
```

Streaming can overlap some work. It cannot make stale visual inputs current or
make an unvalidated partial command ready. Record the actual overlap rather than
subtracting an assumed benefit. Full reply time additionally includes remaining
generation/synthesis/playback; the creator's utterance duration is a separate
part of start-to-finish elapsed time.

**Engineering target, not measured prediction:** attempt a warm text-based
voice interaction in **2–4 seconds to the first useful audible cue**, with a
preliminary 4-second allocation below. The current five-shot and frame results
do not meet this target; the new short-cue benchmark decides whether it is
plausible on Nothing before changing a product promise.

| Warm short-cue budget | Target ceiling |
| --- | ---: |
| End-of-speech detection | 0.6 s |
| Remaining transcription after streaming overlap | 0.5 s |
| Prompt processing plus complete short cue | 2.0 s |
| TTS first chunk | 0.6 s |
| Queue/validation/output overhead | 0.3 s |
| Total | 4.0 s |

This allocation deliberately excludes fresh VLM inference. Measure a vision
turn separately; neither phone has a demonstrated 4-second multimodal speech
loop. First-word audio is useful only when it conveys the actual cue, rather
than an immediate filler phrase masking a long wait. Cold starts and sustained
p95 results may exceed the target. The benchmark must report failures too.

Prepare the five-shot plan before filming, keep models resident only when
memory/concurrency measurements permit, cache approved recurring countdown
audio, and reuse fresh bounded pose measurements for repeated direction.
Deterministic countdown/Start/Stop/Cut timing must never wait for an LLM. This
architecture lets filming remain responsive even while creative planning is
slower. A complete shoot still includes the planned take durations, performance,
review and saving/export; model latency is not the entire filmmaking time.

## Clean CPU benchmark contract

Start with the already pinned Qwen3.5 0.8B language GGUF, avoiding an unneeded
download. The user confirmed this exact model family after initially mentioning
0.5B. The separate [minimal harness](../prototype/model-bench/README.md) has
passed its offline Android build and static APK/JNI checks. Its device run is
**pending reconnection**: the Nothing phone disconnected after backup and before
installation. No new timing, replacement or uninstall is established. It measures
text first, with no camera,
microphone, speech playback, drone connection or network inference. Installing
or launching a harness is not a benchmark result.

The creator chose to finish the demo/test package now and reconnect the phone
later. The old app's verified local backup and preserved source remain available;
actual removal/installation and measurements are explicitly deferred.

Baseline artifact/runtime:

- Language GGUF **563,036,064 bytes**, SHA-256
  `57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`.
- Runtime commit `11fe02151f79c41d0d4af7da708755d73b9c0da6`, four CPU threads,
  arm64, no GPU layers. Preserve/publish actual context and batch settings.
- Exact publisher/source and projector identities remain in
  [local-reference-feasibility.md](local-reference-feasibility.md). Do not load
  the projector during the initial text-only test.
- Pin the synthetic short prompt, chat adapter, disabled-thinking behavior,
  sampling settings, seed and maximum **32 generated tokens**. Count actual
  prompt/output tokens; a cap is not an observed token count. Preserve actual
  text locally to distinguish a usable cue from nonsense or repetition.

Run a fresh model-handle/process case, then several resident-model cases with
independent contexts. A fresh process can still benefit from the operating
system's file cache; call this **process cold**, not proven cold disk. Resetting
the context for warm runs prevents hidden prompt-cache reuse. A separate
conversation/cache experiment may intentionally retain context, with that label.

Record model initialization, prompt processing, first generated token, complete
short cue, generated-token count and decoding time independently using a
monotonic clock. Report precisely which operations are inside each interval.
First-token latency is not full-response time. Calculate decoding throughput
only from observed decoded tokens and its matching decode interval; do not
divide output characters by total turn duration and call it tokens/second.

An optional native microbenchmark, using the pinned runtime's own documented
flags after checking the actual binary's help, can characterize prompt/decode
work without the Android UI:

```sh
llama-bench -m /path/to/existing/pinned-model.gguf -ngl 0 -t 4 -p 128,512 -n 32 -r 5 -o json
```

This command is a reproduction example, not executed evidence. `llama-bench`
reports prompt/decode rates but excludes tokenization and sampling according to
its [pinned official documentation](https://raw.githubusercontent.com/ggml-org/llama.cpp/11fe02151f79c41d0d4af7da708755d73b9c0da6/tools/llama-bench/README.md).
The [official Android integration guide](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/docs/android.md)
provides a supported starting point; runtime source support is not handset
performance evidence.

After the initial mechanical check, collect at least 20 measured warm turns and
three process starts per device, with raw per-run values, median, range and
clearly defined empirical p95. A small sample p95 is preliminary. Repeat a
sustained five-minute sequence to expose throttling, then test concurrent
camera/audio only in an explicitly started attended session. Record ambient
conditions, charging state, thermal state, OS version, physical RAM and sampled
PSS/RSS, without exporting hardware identifiers. Compare identical artifacts,
configuration and prompts; verify CPU feature compatibility on each device.

Then add one speech fixture at a time: existing Whisper baseline first,
Moonshine only after artifact/runtime/license/size approval and local execution,
Android offline voice first, Kokoro after its independent conversion/runtime
check. Measure actual audio onset and cancellation separately from waveform
generation. Finally add a single authorized visual fixture with pinned
dimensions and vision-token settings. Publish every failed/unusable result,
not just successful timings. No private-media upload is needed.

## Proposed evidence record

Keep a sanitized per-run JSON record with model/runtime SHA, backend, threads,
context/batch settings, prompt fixture hash, input/output token counts, load,
prefill, first-token, cue-complete and total-turn times, stop reason, usability
review and measured memory/thermal observations. Speech/vision stages add their
own fixture hashes, media duration/dimensions, processing boundaries and audio
onset measurements. Do not include provider keys, private transcripts, hardware
IDs or model weights in Git.

No model or dependency was downloaded for this research document. Before any
later addition, count current project, phone copies, shared-cache growth and the
retained historical archive reserve against the below-10-GB aim/15-GB cap. The
missing archive and incomplete cache inventory remain qualifications; removing
an APK does not by itself reclaim model or source storage. Preserve the website,
source and existing model provenance while replacing only the user-confirmed
Android application.
