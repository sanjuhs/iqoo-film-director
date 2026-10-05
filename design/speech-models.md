# Speech models — Kokoro cues and Moonshine briefs

**5 October 2026 (IST), pre-event design research.** “CocoRo” is interpreted as
**Kokoro**. The proposed speech stack is Kokoro-82M for short director cues and a
small English Moonshine checkpoint for an explicitly started spoken brief and,
later, editable subtitle drafts. The user's **0.5B** is an upper size bound,
not an exact Moonshine model size or a phone performance result.

This website installs and runs neither model. Optional audio currently uses
browser speech synthesis following an explicit click; brief input is typed and
subtitle text is scripted/editable. There is no microphone access, STT, private
recording, speech upload or demonstrated offline model inference here.

## Candidates verified from primary sources

| Role | Exact candidate/reference | Published size or scope | Proposal |
| --- | --- | --- | --- |
| Spoken direction | [hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) | 82M parameters; open-weight TTS | Synthesize brief preparation, countdown and action/pose cues |
| Short English brief | [moonshine-ai/moonshine-tiny](https://huggingface.co/moonshine-ai/moonshine-tiny) | Original Tiny family | First non-streaming comparison candidate |
| Short English brief | [moonshine-ai/moonshine-base](https://huggingface.co/moonshine-ai/moonshine-base) | Original Base family | Alternative if creator-labelled accuracy warrants its cost |
| Incremental English speech | [moonshine-ai/moonshine-streaming-tiny](https://huggingface.co/moonshine-ai/moonshine-streaming-tiny) | Streaming card reports Tiny 34M | Separate streaming/runtime experiment |
| STT baseline | [OpenAI Whisper](https://github.com/openai/whisper) `tiny.en`/`tiny` | Repository reports Tiny 39M | Compare the same brief/caption audio and review effort |

The [original Moonshine paper](https://arxiv.org/html/2410.15608v1) reports Tiny
**27.1M** and Base **61.5M** parameters. The newer streaming card lists Tiny
**34M**, Small **123M** and Medium **245M**. These are different generations;
“Base streaming” is not established by this research. The requested small-model
ceiling accommodates these published counts, but memory, runtime and total
storage are separate measurements.

**Artifact-count discrepancy:** the streaming Tiny card's table says 34M while
the Hub's automatic tensor summary displayed 44.1M in this session. Treat 34M
as the card's published family description; verify the exact revision/config,
tensor count, exported components and file bytes before deployment. Do not turn
an approximate parameter label into a claimed download size.

The streaming card also warns that its Transformers path does not yet implement
fully efficient streaming. [Moonshine's official toolkit](https://github.com/moonshine-ai/moonshine)
and [platform quickstart](https://moonshine-voice.readthedocs.io/en/latest/quickstart/)
are candidate integration references. Their platform support and published
benchmarks establish neither Android execution nor latency in this app.

## Code, checkpoints and dependency licensing

The [Kokoro checkpoint card](https://huggingface.co/hexgrad/Kokoro-82M) labels the
weights Apache-2.0. The [official inference library license](https://github.com/hexgrad/kokoro/blob/main/LICENSE)
is also Apache-2.0. Its [usage instructions](https://github.com/hexgrad/kokoro)
include the Misaki phoneme pipeline and eSpeak-related fallback; those components,
voice assets and any conversion/runtime need their own pinned license inventory.
Do not assume every dependency inherits the checkpoint license.

The current [Moonshine repository license](https://github.com/moonshine-ai/moonshine/blob/main/LICENSE)
states MIT for repository code except `core/third-party`, and MIT for all English
and streaming STT models. It separately enumerates legacy non-English,
non-streaming models under the Moonshine Community License; third-party source,
TTS/G2P models and data have separate terms. This is a checked current distinction,
not an Apache-2.0 claim for the entire Moonshine family. Pin the exact selected
English checkpoint, code revision and dependency notices before redistribution.

Whisper's official repository describes its code and weights as MIT. Keep its
runtime/conversion dependencies separate too. This document records source
labels; it establishes no completed packaging or license-compliance audit.

## Brief → cues → subtitles

1. **Explicit brief action:** a future Speak idea control starts a visible,
   cancellable microphone session. Moonshine proposes a transcript; the creator
   corrects it before it updates the objective. Stop ends listening. Typed input
   stays available, and no passive/background listening is inherited.
2. **Plan preparation:** language interpretation reads the reviewed objective and
   proposes editable shots/cues. Speech recognition produces text; it does not
   choose shot boundaries, explain camera angles or execute hardware commands.
3. **Cue preparation:** future Kokoro synthesis produces short audio from approved
   text. Prepare/cache repeated countdown cues where useful, with explicit
   version/voice/provenance and bounded storage. Late synthesis must not delay or
   restart capture actions. Text remains usable when audio is unavailable.
4. **Shoot ownership:** the deterministic director owns preparation, countdown,
   actual recording acknowledgement, take timing, interruption and Stop. TTS
   plays guidance through a tested selected output; it never owns camera timing.
   Stop commentary cancels audio only. Stop shoot cancels future cue work and
   the session. Reopening a project never replays audio or recording.
5. **Dialogue policy:** quiet-during-takes suppresses spoken in-take Action and
   midpoint guidance while preparation/countdown/Cut remain allowed. Future
   real capture must assess whether those audible boundary cues enter the
   recorded track; headset routing and echo/speech feedback need separate tests.
6. **Subtitle preparation:** analyze explicitly selected original audio locally
   only after implementation and consent. Preserve segment timestamps and source
   offsets, review transcript text and allow manual timing correction. Partial
   streaming updates can revise earlier text; they are not final subtitle cues.
   Word-accurate alignment needs a demonstrated runtime/alignment path.

No spoken intent directly starts drone movement or changes global Bluetooth
routing. AirPods/earbud delivery, camera/mic/TTS coexistence and imported-camera
alignment remain independent acceptance gates. Browser voice may use an online
service and is not a demonstration of Kokoro, Moonshine or offline Android TTS.

## Evaluation before selecting a backend

Compare typed/manual and browser/system-voice fallbacks with the proposed model
paths on short authorized fixtures first. Then, with attended creator consent,
test short briefs, ordinary fashion language, very short utterances, pauses,
wind/music/noise, silence and supported accents. English checkpoints do not
establish Hindi or code-switch recognition.

- Compare Moonshine Tiny/Base, streaming Tiny and Whisper Tiny on identical
  held-out audio: word errors, objective correction effort, hallucinations on
  silence/noise and timestamp/alignment error.
- Measure first partial/final transcript delay and full end-to-end brief update;
  partial output is not final accuracy. Streaming API callbacks alone do not
  prove a streaming model/runtime is effective.
- For Kokoro, check understandable pose words/countdown, onset delay, interrupted
  playback, stale-result rejection and audible selected-output delivery. Test
  synthesized waveforms separately from actual hearing and camera coexistence.
- On the exact Android device, record backend, cold/warm behavior, sustained
  memory/thermal load and capture interference. Verify converted output parity,
  offline behavior and CPU/GPU/NPU execution independently.
- Pin the chosen model/voice/license/runtime and measure actual retained files
  before download. Count project cache growth and the retained archive; aim
  below 10 GB and never exceed the 15 GB budget.

No new model/dependency download, TTS/STT execution, task accuracy, phone latency,
NPU acceleration or benefit is claimed by this research. Selecting names in a
design specification is not evidence that the models work in the app.
