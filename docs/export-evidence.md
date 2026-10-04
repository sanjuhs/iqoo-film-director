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
