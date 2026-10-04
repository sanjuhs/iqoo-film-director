# Local reference-frame interpretation: implementation and evidence

Research and implementation update, 4 October 2026. This is pre-event preparation.
Initial feasibility work was read-only; root subsequently downloaded the exact
official source, converted its projector, built the app and verified the phone
copy. Actual CPU frame inference now has narrow public/synthetic fixture evidence;
the final combined runner passed nine narrow fixture/lifetime checks.
No provider inference or private-media uploads were used.

## Decision

**The existing language weights and pinned runtime are retained.** A separate
CPU reference-frame interpreter now uses
`libmtmd` and a projector derived from the exact official Qwen source revision.
The exact-source projector and separate image-input JNI path are now prepared.
The Android build and actual core/projector loading/inference ran. Public-person
subject recognition succeeded; framing required a separate pose heuristic after
the generative model mislabeled both full-body and head-crop fixtures. Unseen
reference quality and useful creator direction remain unverified.

Start with one locally selected frame and short editable observations. A few
independent frame descriptions do not establish temporal video understanding,
trend understanding, good shot recommendations or real-time performance.

## Exact model provenance

The ignored local `private/director-model.gguf` is **563,036,064 bytes**, SHA-256
`57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf`.
These match the runtime maintainer's published `Qwen3.5-0.8B-Q4_0.gguf` exactly
at repository revision **`8fea620810c4afa23dd6443f999a48574c1611a3`**; the file's
upload revision is `9447f74101aeb4e93621884dfa36ee8effb8831b`. The publisher's
`.src_sha`, present at the former repository revision, declares
`PRIMARY=2fc06364715b967f1860aea9cf38778875588b17`.
[Published artifact](https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/blob/8fea620810c4afa23dd6443f999a48574c1611a3/Qwen3.5-0.8B-Q4_0.gguf),
[source revision record](https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/raw/8fea620810c4afa23dd6443f999a48574c1611a3/.src_sha),
[publisher file metadata](https://huggingface.co/api/models/ggml-org/Qwen3.5-0.8B-GGUF/revision/8fea620810c4afa23dd6443f999a48574c1611a3?blobs=true).

The local GGUF's `base_model` entry points to Qwen3.5-0.8B-Base, but this is
ancestry metadata. It does not override the exact artifact match or establish
that this is a different Base checkpoint. Its architecture is `qwen35`, hidden
width 1024, 24 layers, M-RoPE sections `[11,11,10,0]`; the chat template includes
vision markers. **Markers alone do not process pixels.**

The declared official source is `Qwen/Qwen3.5-0.8B` at the exact revision above.
Its configuration specifies `Qwen3_5ForConditionalGeneration`, a 12-layer
768-wide vision encoder, 1024-wide output, patch size 16, spatial merge 2,
temporal patch size 2 and no deepstack layers. Its index contains **153
`model.visual.*` tensors**, alongside the language tensors. This provides a
specific matching source for an encoder/projector rather than merely a model
family name. The checkpoint is Apache-2.0.
[Pinned configuration](https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/2fc06364715b967f1860aea9cf38778875588b17/config.json),
[pinned tensor index](https://huggingface.co/Qwen/Qwen3.5-0.8B/raw/2fc06364715b967f1860aea9cf38778875588b17/model.safetensors.index.json),
[license](https://huggingface.co/Qwen/Qwen3.5-0.8B/blob/2fc06364715b967f1860aea9cf38778875588b17/LICENSE).

## Projector acquisition: exact source first

The maintainer's pinned GGUF repository contains language GGUFs, conversion log
and source record, **no projector**. A reproducible route is to convert only the
vision component of the declared official checkpoint using the existing pinned
`convert_hf_to_gguf.py --mmproj --outtype f16`. Its `Qwen3VLVisionModel` explicitly
registers `Qwen3_5ForConditionalGeneration`, selects `qwen3vl_merger`, and excludes
language/MTP tensors while retaining `model.visual.*`.
[Pinned converter](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/conversion/qwen3vl.py),
[projector conversion instructions](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/tools/mtmd/README.md).

The official source has one shard, `model.safetensors-00001-of-00001.safetensors`,
**1,746,942,600 bytes**, SHA-256
`04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696`.
Download only that exact shard and required small configuration/license files,
verify its digest, and export only the projector. Retain the current language
GGUF. Root completed this route: the F16 projector is **204,987,136 bytes**, SHA-256
**`91388cbe4ccde93acd902d7ce32776d14c32bd71a462d4affc1d2e226d81cada`**.
The pinned converter exported 153 source vision tensors as 154 GGUF tensors;
its temporal-convolution transform splits one tensor. Existing language weights
were retained; no training or fine-tuning occurred. The phone copy's SHA-256
matches. Local evidence: `private/evidence/vision-projector-provenance.json` and
`vision-projector-conversion.log`. These ignored files contain dependency
provenance, not a vision-quality result.
[Official pinned file metadata](https://huggingface.co/api/models/Qwen/Qwen3.5-0.8B/revision/2fc06364715b967f1860aea9cf38778875588b17?blobs=true).

For a smaller transfer, the pinned converter already has experimental remote
tensor-range loading. Its lazy filter can fetch only vision tensors. However,
it currently hardcodes `resolve/main`, downloads configuration without an
explicit revision, and does not require HTTP 206 or validate `Content-Range`.
Do not use that unchanged path for a provenance-pinned, bounded download.
A scoped conversion helper could use the exact revision for every URL and
configuration fetch, enforce offsets/lengths, timeouts and a cumulative transfer
ceiling, reject servers that ignore ranges, then fetch only the 153 vision
tensors. This smaller range route remains future work; root instead used the
complete verified source shard for the completed conversion.
[Pinned range reader](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/gguf-py/gguf/utility.py),
[converter configuration acquisition](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/convert_hf_to_gguf.py).

A third-party converter's own published artifact provides a useful size check:
`bartowski/Qwen_Qwen3.5-0.8B-GGUF`, revision
`f36b1ea49a332ede8fe5f389bbf5b3575ef71f48`,
`mmproj-Qwen_Qwen3.5-0.8B-f16.gguf`, **204,987,104 bytes**, SHA-256
`1dc1351c82e41b48edb55fd6ddfa7ca60fb5a16b3d5abf3ce7054880dd022847`.
Its producer identifies Qwen3.5-0.8B and llama.cpp b9222, and declares Apache-2.0,
but does not pin the source checkpoint revision. This is primary evidence for
that producer's artifact, **not an official Qwen projector or a proven exact
pair for our language weights**. Prefer exact-source conversion.
[Producer metadata](https://huggingface.co/api/models/bartowski/Qwen_Qwen3.5-0.8B-GGUF/revision/f36b1ea49a332ede8fe5f389bbf5b3575ef71f48?blobs=true),
[producer card](https://huggingface.co/bartowski/Qwen_Qwen3.5-0.8B-GGUF/blob/f36b1ea49a332ede8fe5f389bbf5b3575ef71f48/README.md).

## Android integration and actual fixture evidence

The original planner JNI remains text-only. The new separate `VisionReference`
and `reference_jni.cpp` implement the following route, with the existing CMake
now linking `mtmd` into `director_llm`. Root's
`private/evidence/vision-decoder-final-build.log` reports **BUILD SUCCESSFUL**
for the Android app/test build. Actual inference is evidenced separately below.
Main accepts a creator-selected decoded reference frame; observations
stay editable and enter notes only after explicit creator acceptance.

1. Keep runtime commit `11fe02151f79c41d0d4af7da708755d73b9c0da6`. Set
   `LLAMA_BUILD_MTMD=ON` before its existing CMake subdirectory, `MTMD_VIDEO=OFF`,
   and link `mtmd`. The pinned standalone target works with common/tools disabled;
   Android-specific compiler handling already exists. No FFmpeg or replacement
   runtime is needed for Android-decoded RGB frames. This path has now built.
2. Load the verified projector with `mtmd_init_from_file` and the existing
   language model; explicitly set `use_gpu=false`, four CPU threads. Check
   `mtmd_support_vision`. The loader checks projected embedding width against
   the language model; that check is necessary but does not prove source identity.
3. Supply RGB bytes through `mtmd_bitmap_init`. Build alternating text/image
   parts with `mtmd_tokenize_from_parts` using the pinned chat format; libmtmd
   supplies the model-specific vision boundary tokens. Count both token and
   position requirements using its helpers before evaluating the input. Start
   with one frame, a conservative image-token limit such as 256, and a short
   observation response. Java snapshots RGB synchronously with maximum side 512;
   native uses 64–256 image tokens, a 1536-token context and both token/position
   bounds. Dynamic-resolution performance beyond these fixtures remains unmeasured.
4. Use `mtmd_helper_eval_chunks` for image embeddings and M-RoPE positions, then
   bounded generation. Reusing the current text-only `llama_batch_get_one`
   ingestion for raw image tokens would omit encoding/position handling.
   Vision requests are serialized and native resources freed before callbacks.
   Cancellation covers loading, image graph computation and text decoding;
   native deadline is 180 seconds with at most 192 generated tokens. Two
   generative fields, subject and uncertainty, have at most 80 characters each;
   the misleading generated framing field was removed. `FramePoseFraming` supplies
   the displayed framing label from confidence/in-bounds landmark rules, with
   explicit abstention. Elapsed time includes loading/encoding; errors preserve
   manual notes. A shared `LocalModelLease` covers the entire native core lifetime,
   including idle text handles: acquire before load, free native resources before
   release, and cancel-aware 50 ms waits with a 5-second acquisition deadline.
5. Interpret timestamped locally selected frames as reviewable observations.
   Creator-confirmed observations can feed the existing planner; retain manual
   notes when inference fails. Preserve frame timestamps and distinguish
   observation from proposed shooting advice.

[Standalone build option](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/CMakeLists.txt),
[mtmd build](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/tools/mtmd/CMakeLists.txt),
[public API](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/tools/mtmd/mtmd.h),
[evaluation helpers](https://github.com/ggml-org/llama.cpp/blob/11fe02151f79c41d0d4af7da708755d73b9c0da6/tools/mtmd/mtmd-helper.h).

Initial public-person/black-frame and immediate-cancellation tests passed in
**42.766 seconds**, then a concise-phrase revision passed in **24.206 seconds**.
That latter public person took **11.140 s**, black **12.189 s**. These established
actual RGB-to-model subject responses, not useful framing: the full-body source
was initially called close-up. A subsequent category prompt called a held-out
top-32% head/shoulders crop full-body. Those errors led to removal of generative
framing, not a claim that prompt tuning fixed shot-size accuracy.

The **9-test / 45.669-second** checkpoint had **one
failure**: black pixels returned VLM subject **unknown** and uncertainty
**unknown**, whereas the test still required explicit absence/black wording.
This is safe abstention rather than an invented subject, but the runner is not
recorded as fully passing. The subsequent **9-test / 46.25-second** rerun passed
after explicitly accepting only the conservative combination **unknown subject +
empty or unclear heuristic framing + unknown uncertainty** as abstention, rather
than claiming explicit no-subject recognition. Final per-frame observations were public subject
**person** with heuristic **full-body**, held-out crop **child** with heuristic
**review needed**, and black **unknown** with heuristic **empty or unclear**.
The cropped subject's age word is a model guess, not validated ground truth.
Public/crop/black callback times were **10.495 / 9.308 / 11.869 s** respectively.
The uncertainty word **hair** for the crop is vague; this is not calibrated
uncertainty. Evidence is limited to these fixtures.

The full-body heuristic found **33** visible landmarks, all required groups
present (**upper 3/3, hips 2/2, lower 4/4**); the crop had **12** visible landmarks
and **upper 2/3, hips 0/2, lower 0/4**, so it abstained with “upper evidence
incomplete.” Black had zero landmarks. These are ML Kit CPU-preference inference
plus handwritten likelihood>=0.75/in-bounds rules, not a trained framing-quality
model or proof that every crop will be classified correctly. Accepting abstention
preserves missing evidence rather than treating extrapolated joints as visible.

All **three model-lifetime regression methods passed**: a cancelled waiter did
not release another owner's permit; real JNI cancellation before generation
remained terminal; an actual loaded planner was closed and freed before vision
acquired the lease, with no stale planner callback. Existing **18 trim/export/
preview/decoder/capture tests passed in 18.56 s**, a separate media result rather
than semantic-reference validation. Latest metrics are appended to ignored
`private/evidence/final-local-checkpoint-metrics.log`; the passing aggregate is
`final-local-checkpoint-device-test.log`. The earlier one-failure aggregate is
preserved in `pose-abstention-checkpoint-device-test.log`; earlier failing
category checks also remain in the saved runner logs.

**Final controlled reference rerun: 9 tests passed, 46.25 s.** This includes four
pose-framing methods, two VLM/cancellation methods and three model-lifetime methods.
Passing does not imply successful head-and-shoulders classification: that crop
explicitly requires review, and black VLM output remains an abstention.
No sustained/real-time,
camera-concurrent, thermal, temporal-video or learned-trend benchmark has run.

## Storage, dependencies and acceptance

After output verification, root removed the temporary **1,746,942,600-byte**
official source shard. The **204,987,136-byte** projector remains on laptop and
phone, with matching recorded checksum; existing core weights were retained.
This removes a conversion intermediate, not the preserved archive. Conversion
packages/native-build growth and both projector copies still count toward the
15 GB cap/10 GB target. Consult root's current storage accounting rather than
using the original 6.424 GB baseline as present measured usage.

Initial inspection found no conversion packages in the default Python 3.14 or
bundled Python 3.12. Root subsequently prepared conversion dependencies including
torch 2.11.0 and transformers 4.57.6; the actual conversion completed with the
pinned runtime converter. See ignored `vision-conversion-dependencies.log`.
Recount actual retained source, packages, builds and phone/laptop projector
copies rather than treating the earlier estimate as current measured usage.
Source conversion establishes no inference or training result.

An earlier concise-phrase run's read-only memory sampling collected **25** usable
samples over **28.184 s**, with **3** missing: maximum sampled total PSS
**1,547,793 KiB**, RSS **1,652,760 KiB**, native-heap PSS **954,096 KiB**. These
are sampled maxima for that build, not certified peaks of the latest combined
pipeline. Exact model/projector hashes are checked in instrumentation.
Evaluate unseen, consented reference clips against human labels for
subject, action, framing and uncertainty. CPU success would establish local
frame inference only. iQOO execution, NPU acceleration, continuous semantic video
analysis, learned directing skill and creator benefit each require separate
evidence. Upstream calls the multimodal API experimental; retain the runtime pin.
