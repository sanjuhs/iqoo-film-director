# Offline clip transcription — pre-event prototype

Prepared 4 October 2026 (IST). These sources are research preparation under
`prototype/phone-director/`, not event-written competition code.

## Pinned runtime and model

- Official [whisper.cpp v1.7.6](https://github.com/ggml-org/whisper.cpp/tree/v1.7.6),
  commit `a8d002cfd879315632a579e73f0148d06959de36`, MIT license.
  The ignored shallow source checkout occupies approximately 33 MB on disk.
- English-only `ggml-tiny.en.bin` from the upstream maintainer's
  [Whisper model distribution](https://huggingface.co/ggerganov/whisper.cpp),
  pinned Hugging Face revision `5359861c739e955e79d9a303bcbc70fb988958b1`,
  MIT license. Downloaded exactly one model to ignored
  `models/ggml-tiny.en.bin`: **77,704,715 bytes**.
- Verified SHA-256:
  `921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f`.
- Android expects the verified model installed privately at
  `files/models/ggml-tiny.en.bin`. It is not packaged in Git or downloaded by
  the app, and no provider API key is used.

The Java/JNI wrapper runs this pretrained model on **CPU, four threads**.
No fine-tuning, NPU acceleration, multilingual recognition or transcription
accuracy has been established for this app. Model and source licenses are
reviewed before download; the upstream runtime license is preserved in
`app/src/main/cpp/whisper/LICENSE.whisper.txt`.

## Input and output

`ClipTranscriber` reads only the selected clip URI through MediaExtractor and
MediaCodec, chooses an encoded audio track such as AAC, decodes PCM16/PCM float,
mixes channels to mono and linearly resamples to 16 kHz. Clips longer than three
minutes, audio-less clips and unsupported decoders return an explicit error.
No SpeechRecognizer or microphone is invoked for clip transcription. PCM stays
in memory and there is no upload/network inference path.

Native Whisper creates draft text segments and source-relative millisecond
timestamps. `SubtitleCue` makes those text/time fields editable. Whisper's
token timestamp splitting is experimental; timings/text need creator review.
A low-energy whole-clip check returns an empty draft for effectively silent
audio; it is not a validated speech detector and cannot establish accuracy on
background music/noise. No subtitle sync claim follows from successful model
loading alone.

The Whisper build is isolated in its own native sub-build, with static-library
symbols hidden from the app's other native runtimes, because llama.cpp and
whisper.cpp both have version-specific GGML implementations. Existing Android
SDK/NDK/CMake tools are reused; no duplicate Android toolchain is installed.

## Checks and remaining evidence

The JNI wrapper passes an ARM64 Android API 29 C++ syntax check using the existing
NDK 28.2.13676358. Model SHA-256 matches the pinned upstream metadata.
The Android library built and ran on the connected Nothing Phone (3a). All four
`MediaWorkflowTest` cases passed in **13.743 seconds**. The labelled synthetic
Samantha speech fixture produced **three segments in 3,458 ms**, including the
expected words **jacket**, **green** and **outfit**. This is elapsed clip
decoding/model-loading/transcription time, not a separate neural-inference
benchmark. The test checked cue bounds against source duration, exported the
clip with its first second removed, and decoded an output frame to verify a
caption appeared at its adjusted timestamp. It also checked the editable JSON
retained source timestamps and the corresponding clipped timeline positions.

The synthetic input sentence was: “This jacket is my favorite. I love the green
color and the soft fabric. Here is my outfit for today.” No live microphone or
private/home recording was used. The local runner result is preserved in ignored
`private/evidence/media-test.log`; the root integration recorded the model's
elapsed time and segment count from the phone run.

This establishes actual on-phone CPU transcription of this one English fixture
and timed-caption rendering. It does not establish general recognition accuracy,
speaker/accent/noise robustness, word-level timestamp accuracy, usefulness on
real creator shoots, iQOO performance or NPU execution. Editing controls are
implemented, but a real creator review remains separate user validation.

Prior workspace measurement was approximately 558 MB before these additions.
Source and model add approximately 111 MB of disk allocation before native
build/phone copies. The historical archive (3,755,403,907 bytes) and prior cache
accounting (218,929,328 bytes) still count; missing archive location and complete
current cache accounting prevent certification of a complete storage total.
Root integration owns final current storage measurements and phone transfers.

## Primary references

- [Pinned whisper.cpp API](https://github.com/ggml-org/whisper.cpp/blob/v1.7.6/include/whisper.h)
- [OpenAI Whisper model license](https://github.com/openai/whisper/blob/main/LICENSE)
- [Android MediaExtractor](https://developer.android.com/reference/android/media/MediaExtractor)
- [Android MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)

These verify API/license contracts, separately from the device acceptance gates.
