# Selected-clip subtitle batching — pre-event research

Verified 4 October 2026. The actual physical-phone speech-to-export method passed
**1 method / 16.177 seconds**. A separate emulator suite passed **15 methods /
13.077 seconds**: six deterministic batch methods, six batch UI methods and
three assembly regression methods. No new failures occurred in these runs.

This work belongs to the research prototype under `prototype/phone-director/`.
It is not event-written competition code or an accepted hackathon submission.

## Explicit creator workflow and ownership

The assembly screen offers a creator-triggered batch for selected takes that
have no subtitle words. Existing subtitle words and unselected takes are
skipped. Drafts are labelled `whisper-tiny.en-draft`; completion asks the creator
to review words and timing before export. The batch does not mark an ASR result
as creator-reviewed or automatically start export.

`SubtitleBatch` snapshots the candidate takes and groups missing drafts by
local source URI. It permits up to twelve eligible takes with source durations
of at most three minutes each. Each unique URI receives a fresh real
`ClipTranscriber`. Progress counts unique URI reads, while drafted/failed/skipped
counts count takes. Multiple takes sharing a URI receive independent mutable
lists and independently copied `SubtitleCue` objects.

The helper does not mutate a take. Before delivering a result, it checks source
URI, source duration, selected state, original subtitle-list identity and the
continued absence of subtitle words. Changed entries are skipped. The UI adds
its own active-batch/generation, busy ownership and source/list guards before
applying and saving a draft.

The real reader's `closeWhenIdle` acknowledgement precedes draft delivery and
the next URI read. This is a native/resource drain barrier, not merely an ASR
callback or cancellation request. Cancellation retains completed drafts and
waits for the active reader's idle acknowledgement before reporting completion
or releasing its busy operation. Closing suppresses callbacks while resources
still drain. Controlled-reader tests exercise that ordering and stale-result
suppression; the physical native run verifies serial idle-before-delivery on
normal completion. Actual in-flight native cancellation latency is not measured.

The underlying [offline speech pipeline](transcription-evidence.md) reads the
selected encoded audio with Android MediaExtractor/MediaCodec, converts it to
16 kHz mono PCM and uses installed Whisper tiny.en on CPU. It opens no
microphone. Source-relative cues are capped to actual container duration.
No new model, microphone service or network inference backend is introduced.

## Actual device acceptance

The exact selector is:

```text
dev.minifilm.director.SubtitleBatchDeviceTest#actualSerialUniqueSpeechReadsDeliverIndependentDraftsAndExportThreeSourceTimelines
```

It requires the two existing labelled synthetic speech videos at
`files/fixtures/jacket-speech.mp4` and
`files/fixtures/jacket-speech-padded.mp4`, plus the already installed verified
tiny.en runtime/model. These fixtures contain synthetic encoded AAC narration,
not a microphone recording. Known container durations are approximately
5,746 and 8,759 ms; the test reads actual metadata and gates each against its
known duration rather than fabricating padding or duplicating source packets.

Five input takes exercise three selected missing drafts, including a duplicate
short-video URI, one selected existing manual subtitle and one unselected
missing subtitle. The instrumented factory delegates every read and idle hook
to the **real** `ClipTranscriber`; it supplies no fake transcript. It asserts:

- Exactly two real transcribe calls, two unique URIs, two real idle releases and
  maximum one reader in flight; progress finishes at two of two.
- Three independently delivered draft lists/cue objects, zero failures and two
  skips. Existing manual words/times/provenance and the unselected list remain
  unchanged.
- Main-thread callbacks, jacket/green/outfit words in each synthetic draft,
  positive cue lengths inside actual source duration and nonoverlapping cues.
- Both originals retain their complete SHA-256, size and mtime throughout the
  batch and export; all real reader workers finish during test cleanup.

The headless listener applies draft text/times and unreviewed provenance to its
own in-memory takes. It does not execute MainActivity or save preferences.
Actual UI persistence and cancellation belong to the separate UI checks.

The test then performs a real three-cut Media3 export using only those drafted
takes, in short/padded/short order. Its nominal timeline is derived from measured
source durations, measured as **20,251 ms**. Independent encoded duration must
be within 300 ms; decoded dimensions must be 720 × 1280. Editable JSON must
retain every delivered word, original source timestamp, list order, draft
provenance and the exact cumulative source-to-timeline offsets.

Decoded frames at cue midpoints must contain white subtitle pixels in the
caption region on all three cuts. This establishes caption appearance at
checked times; it is **not OCR, exact decoded-word recognition, sound review or
general ASR/subtitle accuracy**. JSON text preservation is asserted separately.

The labelled output title is “Synthetic batch subtitle verification”. The test
retains the successful synthetic MP4/edit JSON and detaches only that callback's
verified COMPLETE journal UUID after checking its title, schema, cut IDs,
synthetic source URIs and gallery URI. This prevents a future startup from
adopting the synthetic result into saved creator state. It deletes no original
or unrelated output and does not scan/delete other journals.

The device method opens no Activity, camera, microphone or audio player; changes
no preferences or permission grants; downloads no weights; and uploads no media.
It requires camera and microphone permissions to remain denied before and after
the work. Its numeric `MiniFilmSubtitleBatchTest` report records actual native
read counts, serialism, ASR/batch/export elapsed times, source/encoded durations,
caption-region counts and preservation flags without source URIs, transcripts,
hardware identifiers or gallery IDs. Separate scoped `ASR_OK` logs corroborate
the two actual native completions.

## Verified results and limits

The physical method passed with camera/microphone permissions denied. Its
`pipeline_pass` metrics show **two actual unique native reads**, maximum **one**
reader in flight, **three drafts / zero failures / two skips**, independent
duplicate lists/cues, main-thread callbacks and unchanged originals. Raw draft
provenance stayed unreviewed (`draftsReviewed=false`), and no microphone was
opened. All real reader workers finished before the method returned.

| Observation | Measured result |
| --- | --- |
| Short / padded source duration | 5,746 / 8,759 ms |
| Short / padded ASR elapsed | 4,438 / 5,284 ms |
| Complete batch elapsed | 9,735 ms |
| Export elapsed | 5,458 ms |
| Nominal / actual encoded duration | 20,251 / 20,387 ms |
| Decoded output dimensions | 720 × 1280 |
| Three checked caption-region white-pixel counts | 5,479 / 5,041 / 5,487 |

ASR elapsed includes source decoding, model loading and transcription; it is not
an isolated inference benchmark. The scoped native logs report **CPU, four
threads, tiny.en, three segments** for each of the two real reads. The duplicate
short take receives its own copied result without a third native request.
Every delivered word/source time and cumulative timeline mapping passed the
editable JSON assertions. Both fixture hashes, sizes and mtimes stayed unchanged.

The separate emulator suite used injected controlled readers and real UI
paths; it loaded no models, decoded/encoded no media and opened no audio. Its
six core methods passed grouping/deep-copy/idle ordering, error continuation,
changed-entry rejection, malformed/silent result handling, whole-batch
preflight rejection, cancellation/reuse, close suppression and reentrant
cancellation checks. Its six batch UI methods passed the actual button,
saved draft provenance/persistence, model-unavailable refusal, busy-until-idle
cancellation, retained completed drafts, background cancellation without
automatic restart, changed-word protection, stale-terminal busy ownership and
queued-preflight cancellation. The three assembly regressions passed exact
trim/subtitle bounds, overlap rejection/adjacency, ordering and recreation.
UI preference preservation/restoration is distinct from the native headless
method, which never opens MainActivity or saves preferences.

Ignored evidence is retained in `private/evidence/subtitle-batch-device-tests.log`,
`subtitle-batch-native-metrics.log` and `subtitle-batch-emulator-tests.log`.
No hardware or gallery identifiers are reproduced here. The successful
synthetic MP4/edit pair remains retained; only its verified own COMPLETE marker
was detached as described above.

These checks verify the bounded synthetic pipeline and controlled UI/lifecycle
behavior. Model loading and blocked provider setup can still delay cancellation;
no immediate abort guarantee is made. The two
synthetic English fixtures cannot establish general clip-analysis accuracy,
benefit on real creator footage, multilingual recognition, actual microphone
recording or physical UI usability. AirPods, iQOO/NPU, Office Kit and submission
eligibility remain separate gates.
