# Local reel export — preparation prototype

Created 4 October 2026 (IST), before the event. Sources are in
`prototype/phone-director/`; this is not event-written competition code.

## Implemented contract

`Take` retains the source URI, shot identifier, editable title/manual caption,
source duration, selected state, timed subtitle drafts/origin and in/out times. `ReelExporter` snapshots those
reviewed cuts and rejects invalid ranges, an empty selection, more than 12 takes
or more than three minutes. It preserves original media.

The pinned **AndroidX Media3 1.5.1** Transformer composition concatenates selected
trimmed sources in reviewed order, crops to central 9:16, exports H.264 at
720×1280 with AAC audio, and adds optional title/caption typography. An empty
reel title leaves the entire top heading/shot label off. A supplied title uses
a dark rounded backing so source text cannot collide with the heading. Clean is
neutral; Warm adjusts red/blue gains slightly; Cinematic adds mild contrast and
small channel adjustments. These are deterministic local presets, not AI color
grading. Caption provenance/review state appears in the editing UI and JSON.
The published reel contains the creator's words and title, without technical
caption labels or runtime watermarks. Offline English transcription produces
editable timed drafts; its separate evidence is in [transcription evidence](transcription-evidence.md).
When timed cues exist they take priority and appear only during their reviewed
source ranges; otherwise the manual caption spans the cut. Word-level karaoke timing, face-aware crop,
multicamera synchronization and generated music are not implemented here.

The original audio remains attached to its sequential source cut; silent sources
are padded through Media3's forced-audio-track option. Phone master-audio
alignment across simultaneous camera views is a separate unpassed gate. HDR
input requests OpenGL tone mapping to SDR; actual HDR footage needs its own test.

Successful output is copied into scoped MediaStore `Movies/MiniFilm` using a
pending row and made visible only on completion. A JSON cut list, under the
app's `files/exports/`, contains source URI, shot/title/caption, source in/out,
timeline position, subtitle source/timeline bounds and export notes, shared through FileProvider. It is an
editable project description; third-party editor interchange compatibility and
Office Kit transfer are unverified. No uploads or provider keys are used.

Cancellation stops Transformer and invalidates pending save callbacks. Errors
and cancellation clean up temporary MP4s/pending gallery rows/partial JSON,
without deleting source takes. The UI needs to remain open during export; no
background-export service is claimed.

## Synthetic verification fixture

`DemoAssets.create` uses Android MediaCodec and MediaMuxer to generate three
360×640, 24 fps H.264 sources locally, with explicit **SYNTHETIC DEMO · NO CAMERA**
labels, distinct numbered/color scenes and advancing frame markers. Each source
is three seconds; default reviewed cuts use 0.5–2.5 seconds and should yield a
six-second sequence. No camera, microphone, home footage, aircraft or network
input is used. This fixture checks real encode/trim/order/overlay/export behavior
without capturing the absent user's surroundings. Its silent audio does not
verify captured-speech preservation.

Pinned Media3 source adds the cumulative cut duration before invoking item
effects, while decoded frames have their clipped start subtracted. The overlay
therefore looks up the subtitle at `composition timestamp - cut timeline start
+ cut in point`. This handles trimming and later takes in a sequence. The
device test checks actual output-frame caption visibility both inside and
outside a cue on the first and second trimmed takes, rather than relying on
the JSON timing calculation alone.

`MediaWorkflowTest` additionally checks output duration/resolution, distinct
synthetic scene order, actual white caption-word visibility, original-source preservation,
and three known spoken words from a labelled synthetic speech fixture. The
speech test exports the audio clip after trimming its first second and verifies
that an ASR caption is burned into a corresponding decoded output frame.

## Device verification — 4 October 2026

On the connected Nothing Phone (3a), **all four MediaWorkflowTest cases passed
in 13.743 seconds**, and the final heading-enabled rerun passed **all four in
10.190 seconds**. Runner results are retained in ignored
`private/evidence/media-test.log` and `private/evidence/final-media-test.log`.

| Check | Measured result |
| --- | --- |
| Sequential output | Actual H.264 + AAC MP4, **720×1280**, **6.060 seconds**; three reviewed 0.5–2.5-second cuts |
| Scene order and originals | Decoded output frames match synthetic scenes 01 → 02 → 03; original fixture files remain present |
| Manual captions | Actual white caption words visible in decoded output frames; no technical caption watermark |
| Trimmed/timeline cue behavior | Source cue 600–1100 ms appears at 100–600 ms on the first 500-ms-trimmed take, and 2100–2600 ms on the second; text is present inside each cue and absent outside |
| Speech-caption export | Local tiny.en returns three segments/expected words; after trimming one second, a decoded output frame shows the adjusted caption and JSON retains source/timeline bounds |
| Cancellation | Cancelling a 12-cut synthetic export preserves every source and leaves zero new temporary MP4 files |

The exported synthetic frame was also visually inspected. Its original “MINI
FILM” text collided with the always-on output heading; headings are now optional
and supplied headings have a rounded dark card backing. The heading-enabled
rerun passed all four media tests, and `final-heading-frame.png` showed readable
title/subtitle placement. That inspection also exposed faint source text through
the translucent heading backing, which has now been made fully opaque. Root
integration will rebuild for the final blank-title UI export. Subtitle placement
and timing logic remain the same. Source whitespace checks pass.

The separate **UiWorkflowTest passed all three tests in 6.603 seconds**. Its
internal-player check opened a private synthetic local video and verified the
ExoPlayer became ready while remaining paused (`playWhenReady=false`). Preview
supports play/pause/scrubbing and releases the player on leaving, with position
retained for recreation; the passing test establishes preparation without
autoplay, rather than a measured full lifecycle/audio-route matrix. Its local
result is in ignored `private/evidence/ui-test.log`.

These checks prove decoding and actual caption/cut output, rather than merely
file creation. They do not establish captured speech preservation, HDR footage,
real-world crop quality, automatic word-level speech alignment, multicamera sync,
third-party editor interchange, Office Kit or iQOO execution. The cancellation
test runs before publication; interrupted pending-gallery-save recovery has not
been exercised separately.

## Primary references

- [Media3 multi-asset editing](https://developer.android.com/media/media3/transformer/multi-asset)
- [Media3 1.5.1 Transformer source](https://github.com/androidx/media/blob/1.5.1/libraries/transformer/src/main/java/androidx/media3/transformer/Transformer.java)
- [Media3 1.5.1 Composition source](https://github.com/androidx/media/blob/1.5.1/libraries/transformer/src/main/java/androidx/media3/transformer/Composition.java)
- [Presentation crop/size API, pinned source](https://github.com/androidx/media/blob/1.5.1/libraries/effect/src/main/java/androidx/media3/effect/Presentation.java)
- [Bitmap overlay API, pinned source](https://github.com/androidx/media/blob/1.5.1/libraries/effect/src/main/java/androidx/media3/effect/BitmapOverlay.java)
- [Android MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
- [Android shared-media storage](https://developer.android.com/training/data-storage/shared/media)

These establish API contracts; hardware success requires the run evidence above.

## Final UI export check

After the fully opaque header update, the installed app's real Assemble screen
exported the three selected synthetic cuts with a blank optional title. Actual
output is 6.060408 seconds, H.264 720×1280 plus AAC. A decoded frame was visually
checked: no added top heading, source fixture labels remain unobscured, and
creator caption text appears at the bottom. The MP4/JSON pair and state receipt
remain ignored locally. This was an actual UI export, not a test-only API call.

The subsequent standard ACTION_CREATE_DOCUMENT path saved the synthetic edit
list to the user-selected local Downloads folder. Actual output is 1,512 bytes
and its SHA-256 matches the source JSON; UI displayed success. No broad storage
permission, overwrite, cloud provider or Office Kit pairing was used. Source
URIs remain phone references, not a portable self-contained desktop project.

## Full typography and export restart checks

The renderer now preserves complete caption text: it reduces font size within
35–24 pixels for up to four full lines, or rejects before Transformer/temp/gallery
outputs with an instruction to shorten/edit text. One-line heading bounds are
120/140 characters with minimum font sizes 14/12; no silent truncation remains.
Two additional media tests passed: six manual/ASR/title rejection cases create
no outputs, and an actual encoded 199-character four-line caption retains the
full layout/JSON. All six media tests, five portable-package tests and four UI
tests passed within a 20-test runner (21.065 s); that runner had one separate
recovery fixture-cleanup error, recorded below.

ReelExporter now journals each UUID-scoped temporary output/private cut list/
owned MediaStore row before creating it. READY is durable before publication;
startup checks exact package/name/path, matching JSON exportId/schema and video
byte count. A verified published pair is recovered; only verified unfinished
outputs are cleaned. Sources are never part of journal cleanup. Malformed,
ambiguous, oversized or unverifiable records preserve media with a warning.
At most 128 small journals are inspected. This is foreground export plus restart
reconciliation, not an Android background-service implementation.

Five staged interruption tests passed in **0.775 s**, including encoding cleanup,
insert-before-URI/pending-copy cleanup, published-before-complete commit recovery,
repeated startup, ambiguous published preservation and forged/oversized record
handling. The first combined runner failed when fixture cleanup deleted an
already-deleted row (Android raised SecurityException); the helper now queries
for existence and does not suppress permission errors on a remaining row. The
recovery-class-only rerun passed. These tests stage journal/publication states;
they do not prove killing an actively encoding Android process at every boundary.
Logs: ignored `private/evidence/recovery-package-media-ui-test.log` and
`private/evidence/recovery-repeat-test.log`.

[Official shared-media documentation](https://developer.android.com/training/data-storage/shared/media)
was checked for pending publication and owned-media access. API documentation
supports the implementation contract, rather than establishing hardware behavior.


## Opt-in automatic balance — actual exported-video check

**Auto balance** is a conservative deterministic option, separate from learned
LLM grading. It inspects three local ≤160-pixel samples inside each selected
trim and applies only small RGB gains (0.94–1.06) and linear brightness (±0.04).
Neutral detail must be distributed across at least three quadrants; uniform,
clipped/extreme, HDR, insufficient or changing-color samples retain Clean with
an explicit reason. Source URI, shot identity and in/out range must match the
immutable measurement. Per-cut JSON records measurements, sample positions,
reason and parameters, with learned=false/review-required provenance.

All five analyzer and two actual exporter instrumentation tests passed. The
first fixture attempted an unsupported 96×128 encoder shape; only its encoder
helper was corrected to the already supported 360×640/24-fps/900-kbps profile
with explicit capability checks. In-memory measurement fixtures and production
logic stayed unchanged. Measured cast-neutral RGB was
122.55609/116.71964/110.93282, giving gains 0.94/0.9990174/1.06. The dim neutral
fixture had mean luma65.71014 and brightness0.025491094.

Actual Media3 encoded **Auto versus Clean** output reduced mild cast RGB spread
from **12.174217 to 1.5266876**, lifted dim luma by **6.0603104**, and preserved
intentional flat color. Reversed measurement order still paired each source
correctly; missing/stale source/shot/in/out measurements failed before output
creation. Original SHA-256 values remained unchanged. Retained ignored logs:
`private/evidence/balance-ui-repeat-test.log` and `balance-repeat-metrics.log`.
The combined runner's five UI failures occurred behind secure keyguard; they
do not invalidate its seven headless color checks or pass UI behavior.

These synthetic positives/abstentions establish bounded execution, not grading
quality on skin tones, outfits, real lighting, moving cameras or unseen clips.
The creator must compare/review the result and can choose Clean.


### Retained synthetic delivery and timestamp fixes — completed checks

The final runner passed **six tests in 20.578 s**: four exact-time parsing/format
regressions, strict source-bounded speech-to-export captions, and integrated
retained reel plus portable package. ClipTranscriber now caps source-offset
ASR endpoints to actual container duration, so codec padding cannot create a
draft beyond the clip. Trim/subtitle fields preserve three-decimal seconds;
5.746s round-trips to 5746 ms and default review no longer rounds beyond source.
Invalid/nonfinite/overflow decimal input is rejected. Visible dialog behavior
still awaits normal unlock; these headless checks establish the exact conversion.

The actual retained reel is **11.795737 s / 720×1280 / H.264 + AAC**, versus the
reviewed 11746 ms timeline. Three visual cuts precede the full known 5.746 s
synthetic English clip. Actual decoded frames verified caption white-pixel counts
3955 inside first cue, 0 in the gap and 2864 inside second cue. CPU transcription
of source and completed output retained jacket/green in three segments each
(4.039s source and 4.707 s output in this run). No audio was played or recorded.

The 252,326-byte portable ZIP resolves four unchanged originals totaling 246,497
bytes, relative paths, reviewed order/trims and source-to-timeline subtitle
ranges. Copies under ignored `output/demo/` matched phone SHA-256 values on the
laptop; ffprobe independently confirmed codecs, dimensions and duration.
Reel SHA-256: 20ebb74737cc2e782f3afaf478475ff84e81f63eb6a447c732df0677e4bf2540.
ZIP SHA-256: 0668a39bf8f36542f5cd3f25f9eaee17165e4b40b8032d3d88b1b29e9f31ba78.
See `output/demo/README.txt` and `metrics.json` for synthetic provenance.

The failed delivery's exact owned synthetic output was cleaned only after
ID/title/source/MediaStore ownership proof; original fixtures and saved shoot
preferences stayed intact. Successful test-only journal markers are removed
after durable delivery copies so later startup does not replace the saved
project's output pointer. A separate media-test marker was locally archived
and removed after verifying its exact known synthetic source; its MP4/JSON stay
intact. Future media checks use callback-scoped fixture-marker cleanup.
Normal Files ZIP saving, Office Kit receipt, attended capture/AirPods, actual
iQOO and organizer-compliant event code/submission remain unverified.
