# Third-party notices and provenance

4 October 2026 (IST), pre-event prototype. The app includes
[`THIRD_PARTY_NOTICES.txt`](../prototype/phone-director/app/src/main/assets/THIRD_PARTY_NOTICES.txt)
as a readable asset. It contains the exact full MIT licence texts from the two
pinned native checkouts, plus the complete Apache 2.0 terms distributed in the
locally cached CameraX AAR. The full official libyuv BSD notice and AUTHORS were
subsequently retrieved as small attribution documents from Google's primary
source. No SDK/model download or AI-provider request was made for this update.

| Component | Actual source/version | Licence/provenance evidence |
| --- | --- | --- |
| llama.cpp / GGML | Official checkout `11fe02151f79c41d0d4af7da708755d73b9c0da6` | Full top-level MIT text retained verbatim, including ggml authors copyright 2023–2026 |
| whisper.cpp / GGML | Official v1.7.6, `a8d002cfd879315632a579e73f0148d06959de36` | Full top-level MIT text retained verbatim, including ggml authors copyright 2023–2024 |
| AndroidX | Activity 1.9.2; Core 1.13.1; CameraX 1.4.2; Media3 1.5.1 | Direct coordinates recorded; cached resolved POMs declare Apache 2.0; full cached CameraX terms retained |
| CameraX native libyuv | `camera-core:1.4.2` cached POM; official notice source `aa6cedb39c87910b4c28e5c71c2121fc45fd234b` | Full official BSD notice and AUTHORS now retained; exact CameraX binary's bundled libyuv revision remains unverified |
| Google ML Kit pose detector | `pose-detection:18.0.0-beta5` | Cached POM declares [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms), not Apache 2.0 |
| Existing Qwen GGUF | Metadata says Qwen3.5 0.8B, Q4_0, Apache 2.0 and Qwen3.5-0.8B-Base | Weights are not packaged; exact quantizer/revision/original download remain unverified; the measured SHA-256 is transfer integrity, not publisher provenance |
| Whisper tiny.en | OpenAI model; ggml distributor `ggerganov/whisper.cpp`, revision `5359861c739e955e79d9a303bcbc70fb988958b1` | Distributor metadata MIT; exact 77,704,715-byte file/hash verified; weights are a separate private local install |

The AndroidX Apache terms were copied from the existing Gradle transform of
`camera-core-1.4.2.aar`, path within that artifact:
`META-INF/androidx/camera/camera-core/LICENSE.txt`. The cached artifact's POM
lists both Apache 2.0 and the libyuv BSD source reference. Its cached AAR lacked
a separate BSD notice. That attribution gap is now addressed by retaining the
full 1,506-byte official libyuv LICENSE, including **Copyright 2011 The LibYuv
Project Authors**, and its 212-byte AUTHORS file. Both came from official
commit `aa6cedb39c87910b4c28e5c71c2121fc45fd234b` and were retained verbatim.
A bounded read of AndroidX's camera release branch confirms that its native
image-processing module links libyuv, with source taken from the external
libyuv project; it did not establish the exact revision in the 1.4.2 AAR.
The pinned licence source therefore does **not** assert a CameraX binary/source
version match or a complete transitive/native distribution audit.
ML Kit/Google Play services and Android platform services retain their
own terms; the AndroidX licence does not cover all app dependencies.

The model hashes, identities and limitations match
[local AI evidence](local-ai-evidence.md) and
[transcription evidence](transcription-evidence.md). This file adds no weights,
keys, private footage, captures, training data or hardware identifiers to Git.
No third-party source is relabelled as original event-written implementation.

Validation: the notice contains both exact upstream MIT text strings and the
entire cached Apache licence text, plus full official libyuv BSD and AUTHORS,
verified byte-for-byte as substrings. The
root integration owns the next APK build and packaged-asset verification.

Primary references:

- [Pinned llama.cpp](https://github.com/ggml-org/llama.cpp/tree/11fe02151f79c41d0d4af7da708755d73b9c0da6)
- [Pinned whisper.cpp](https://github.com/ggml-org/whisper.cpp/tree/a8d002cfd879315632a579e73f0148d06959de36)
- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Media3 1.5.1](https://github.com/androidx/media/tree/1.5.1)
- [libyuv's declared third-party licence reference](https://chromium.googlesource.com/libyuv/libyuv/+/refs/heads/main/README.chromium)
- [Pinned official libyuv BSD licence](https://chromium.googlesource.com/libyuv/libyuv/+/aa6cedb39c87910b4c28e5c71c2121fc45fd234b/LICENSE)
- [Pinned official libyuv AUTHORS](https://chromium.googlesource.com/libyuv/libyuv/+/aa6cedb39c87910b4c28e5c71c2121fc45fd234b/AUTHORS)
- [Publisher's Qwen model card](https://huggingface.co/Qwen/Qwen3.5-0.8B)
- [Whisper GGML model distributor](https://huggingface.co/ggerganov/whisper.cpp)
