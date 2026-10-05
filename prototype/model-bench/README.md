# Minimal local Qwen timing experiment

New pre-event research harness, 5 October 2026. This is separate from the
preserved Mini Film source and design website, and is not event-written code.

This Android app measures **text-only CPU inference** from a locally supplied
GGUF. It is not a director, VLM, speech recognizer, speech synthesizer, NPU test or
camera app. It requests no permissions and has no network dependency. There are
three controls: a short prompt, Run benchmark, and Stop. A normal launch waits for
the user. The first run loads the model; following runs reuse it and clear the
prompt cache. Leaving the activity cancels ongoing work. Loading can stop at the
next model-load callback; decoding stops at a safe backend boundary.

## Build with installed tools only

```sh
prototype/model-bench/tools/build.sh :app:assembleDebug
```

The script uses the installed Homebrew JDK 17 only if `JAVA_HOME` is unset and
always passes `--offline`. This project reuses cached Gradle 8.14, AGP 8.11.1,
Android SDK 35, NDK 28.2.13676358 and CMake 3.22.1. The earlier request mentioned
Gradle 8.13; 8.14 is the version actually present in the preserved project and
cache, so no replacement was downloaded. `local.properties` is machine-local
and ignored. There are no AndroidX, speech, DJI or MLKit dependencies.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Package:
`dev.minifilm.benchmark`. The APK contains no model, provider credential or key.

The native build references the existing ignored vendor checkout at
`../phone-director/app/src/main/cpp/llama.cpp`; it does not copy/download it. CMake
rejects a revision other than `11fe02151f79c41d0d4af7da708755d73b9c0da6`.
Do not remove this preserved checkout while the benchmark depends on it.

## Model and reproducible measurement

Supply the user's authorized, pinned existing Qwen GGUF as private app file
`files/director-model.gguf`. Verify the exact source revision, size and SHA-256
externally before use. Filename alone is not model identity. No download or
model selection is built into this harness. Parent-owned installation and
replacement of the previous app require its own backup verification; building
this project does not remove or change that app.

For an explicit public-fixture batch on a debug APK:

```sh
adb shell am start -n dev.minifilm.benchmark/.BenchmarkActivity \
  --ez benchmark_once true --ei benchmark_runs 3
```

This only runs the hardcoded public prompt “Suggest one pose for a jacket
reveal.” Runs are bounded to 1–5. The same loaded context is reused; three runs
produce one first inference and two warm inferences. The hook is disabled in
non-debug APKs. Stop/background/error cancels remaining batch runs. For repeated
cold-load tests, force-stop before each explicitly requested new batch. Android
page cache may remain warm; a process restart does not establish a cold disk.

Retrieve the latest result or all run history without logging prompts/replies:

```sh
adb exec-out run-as dev.minifilm.benchmark cat files/benchmark-result.json
adb exec-out run-as dev.minifilm.benchmark cat files/benchmark-history.jsonl
```

Results stay private until explicitly retrieved. Reports contain numeric timing,
token counts, file size, device model/Android version and a prompt SHA-256, but no
prompt text or generated response. A response is shown only in the app. Runtime
logs are suppressed; no raw prompt/model output is sent to public Android logs.

## Measurement boundaries

- CPU only: `n_gpu_layers=0`; no GPU, Vulkan, QNN, NPU or multimodal projector.
- ARM64 build uses `armv8.2-a+dotprod+fp16`, runtime-checked before loading.
- Exactly four inference/prefill threads, context 1,024, batch/microbatch 128.
- Greedy sampling; at most 32 emitted non-EOG tokens. No quality claim follows.
  Reports include `stop_reason` (`eog`, `token_limit`, or `cancel_or_deadline`)
  and `output_complete`, true only when the model reached EOG. Hitting the cap
  does not establish a complete cue; even EOG does not establish useful content.
- Chat format comes from `llama_model_chat_template` metadata and
  `llama_chat_apply_template`. This pinned simple API supports recognized
  templates, not full Jinja. If metadata declares both Qwen ChatML and `<think>`,
  append the closed-think prefix used by the existing Qwen research adapter.
  It is recorded source behavior, not proof of equivalent full Jinja execution;
  inspect actual output on the pinned model before interpreting speed as useful
  spoken guidance. Missing/unsupported templates fail explicitly.
- Native timing uses `std::chrono::steady_clock`; button timing uses Android
  `SystemClock.elapsedRealtimeNanos`, which is monotonic. No wall-clock
  subtraction is used for durations.
- Load includes GGUF loading plus context initialization, measured once. Warm
  reports distinguish that previous load from zero new model-load work.
- First token starts before formatting/tokenization, cache clear and prefill,
  and ends when the first non-EOG token piece is collected. It is **model first
  token**, not audible playback onset or completion of a useful sentence.
- Prefill is the input-token `llama_decode` calls; formatting/tokenization/cache
  clear are reported separately.
- Decode interval is first-to-last emitted token. Rate is `(output_tokens-1)`
  divided by that interval, excluding EOG. A subsequent EOG sampling/decode may
  extend total inference. `decoded_output_tokens` reports actual decode calls
  completed, which can differ from emitted tokens.
- Generation total includes formatting, cache clear, prefill, sampling and
  decode through completion/cancellation. Button-to-result additionally includes
  any load, and ends before JSON serialization/private-file writes.
- A three-minute native deadline bounds each inference. Cancellation/deadline
  reports are explicitly partial; load failures do not produce a valid timing
  report. A one-token/EOG-only result has no meaningful decode rate.
- Serial executor owns model work; cancellation uses atomic state. JNI registry
  uses shared ownership so cancellation/close cannot free an active context.
  Destroy queues close after work; repeated prompts cannot overlap.

Keep fixture and settings identical across phones; report medians/ranges,
charging/thermal state, memory pressure and background workload. These results
do not measure STT, Kokoro, audio endpointing, VLM image encoding, camera capture,
render/export or complete speech-loop latency.

## Attribution

- Official [llama.cpp](https://github.com/ggml-org/llama.cpp), pinned revision
  above: MIT, retained in [LICENSE.llama.cpp.txt](LICENSE.llama.cpp.txt). JNI is a
  fresh API harness informed by the existing pre-event CPU research path; no
  vendor source is relabelled event-written.
- Standard Gradle wrapper scripts/JAR copied from the preserved project:
  Gradle 8.14, Apache License 2.0; [Gradle license](https://github.com/gradle/gradle/blob/v8.14.0/LICENSE).
- Android platform SDK/NDK, Android Gradle Plugin and Java retain their original
  upstream licenses. No models or training data are distributed with this app.

An initial native compile found a const token pointer incompatible with the
pinned batch API; fixed before delivery. The offline lint attempt could not run
because AGP 8.11.1 lint jars are not in the cache. No lint dependencies were
downloaded, and no successful lint result is claimed.

Final offline debug build passed. Packaged-manifest inspection confirms no
requested permissions, backup disabled and the separate debug package. All
seven Java JNI entrypoints are exported in the stripped packaged library.
The APK is 12,049,394 bytes; build artifact digest and directory storage are in
[verification.json](verification.json). The new project including generated
native objects occupies 337,564 KiB; the existing vendor checkout is not copied.
No APK installation, old-app removal or on-phone benchmark was performed by this
build task. The phone disconnected before the parent hardware test; actual
measurements remain pending.

Build/static validation is not on-phone inference evidence. Hardware results and
replacement/backup evidence are recorded by the parent in `docs/status.md`.
