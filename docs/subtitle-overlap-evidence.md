# Subtitle overlap rejection — 4 October 2026

Shared validation now rejects overlapping nonempty subtitle cues in the review
dialog, MP4 exporter and portable ZIP packager. Adjacent endpoints are accepted.
The helper sorts temporary interval copies for checking; it does not reorder the
creator's subtitle list or rewrite words and timestamps.

The actual physical-phone suite passed **3 methods / 4.192 seconds** with the
phone locked and camera/microphone permissions denied. A separate fresh unlocked
emulator UI suite passed **10 methods / 14.428 seconds**, including one new
overlap-review method and the existing assembly editing checks. These are
synthetic research checks, not attended capture, sound review or an eligible
event submission.

## Supported defect and repair

The renderer selects the first subtitle whose source interval contains the
current frame and stops scanning. Before this change, independently valid cues
could overlap without being rejected. For example, in a source cut from 0 to
3,000 ms:

- Cue A: 500–2,000 ms, “Opening words”.
- Cue B: 1,000–1,500 ms, “Later words”.

Cue B would never be selected while A came first in the list. Both cues could
still be described as visible in editable JSON. Partial overlaps similarly
suppressed part of a later cue. This is a source-supported first-match defect;
there was no separate pre-repair encoded/OCR experiment for this example.

`SubtitleTimeline.requireNonOverlapping(List<SubtitleCue>)` now checks the
nonempty valid intervals. Start less than the previous sorted interval's end
rejects; equality accepts adjacency. Null, blank and invalid intervals remain
subject to each caller's existing validation or normalization policy. No
automatic cue splitting, timing alignment, merging or source-list sorting is
introduced.

The creator-facing error is:

> Subtitle times overlap. Adjust the start/end times so one cue ends no later
> than another begins. Your words and times were kept.

The review dialog checks the candidate list before assigning or saving it. The
exporter and packager check their validated snapshots before creating an export
journal, temporary media, gallery row, edit output or ZIP. The first-match
renderer remains unchanged; accepted nonoverlapping lists remain compatible
even when their list order is not chronological.

## Physical-phone evidence

`SubtitleOverlapTest` contains three bounded headless methods:

1. Nested, partial and same-start overlaps reject in either list order. Unsorted
   adjacent intervals accept without changing list references, text or times.
   Null/blank/zero-duration handling and extreme endpoint arithmetic also retain
   the helper's documented behavior.
2. **Six preflight cases** reject both export and packaging with the shared
   message. No export progress callback occurs; output snapshots show no new
   journal, gallery row, editable JSON, temporary MP4 or ZIP. An injected package
   source opener is never called. The original synthetic file's size, mtime and
   SHA-256 remain unchanged.
3. A real Media3 export uses the existing labelled
   `files/synthetic-demo/synthetic-1.mp4`, two reviewed source cuts of
   500–2,500 ms and deliberately reversed subtitle list order. “SECOND ADJACENT
   WORDS” occupies 1,100–1,600 ms; “ONE” occupies 600–1,100 ms. Their touching
   source boundary is accepted without reordering either list.

The exported video decoded at **720 × 1280** with an independently
measured duration of **4,064 ms**, versus the nominal 4,000 ms edit timeline.
Both cuts show short and distinct longer white-caption shapes at their expected
times, with no white subtitle text before or after those intervals. Editable
JSON keeps the exact full strings and reversed list order. Source-to-timeline
mapping is checked independently for each cut: 100–600 / 600–1,100 ms on the
first cut, and 2,100–2,600 / 2,600–3,100 ms on the second.

The same reviewed cuts produce a real portable ZIP. Its JSON retains those
words, order and mapped times; the single deduplicated source has the original
size and SHA-256, and the actual archived source bytes hash identically. The
test title is “Synthetic adjacent subtitle verification”; the source remains
the existing synthetic demo, not newly recorded or private footage.

White-pixel comparisons establish distinct encoded caption appearances and
their timing. They are **not OCR, exact decoded-word recognition or an audio
listening assessment**. Full-text preservation is separately asserted in the
editable and portable JSON.

## Real dialog evidence and retained outputs

`AssemblyEditUiTest.overlappingReviewKeepsAllWordsAndOriginalTimesThenAcceptsTouchingCues`
ran in the separate ten-method emulator suite. It uses the actual subtitle
dialog to attempt nested, partial and same-start edits. Every rejected save
keeps all entered words and candidate times in the open dialog, while the
stored original cues and preferences remain unchanged. The creator then
sets an adjacent boundary at 2.000 seconds; saving and Activity recreation
retain both exact cues and the assembly screen. The test preserves/restores
the complete earlier preferences and checks the synthetic source unchanged.
It opens no camera, microphone or audio player.

The physical test retains its completed synthetic gallery MP4 and editable
JSON. It removes only that callback's verified synthetic export UUID journal
so a later startup cannot adopt the test reel into saved creator state. Only
the returned test ZIP is deleted. Original fixture files, earlier outputs and
unrelated journals are preserved. No permissions are granted, shoot preferences
are changed, media is uploaded or models are downloaded by the physical suite.

Ignored evidence is retained in `private/evidence/subtitle-overlap-tests.log`,
`subtitle-overlap-metrics.log` and `subtitle-camera-ui-tests.log`. Device
identifiers, gallery row IDs and private paths are omitted here. Final artifact
hashes and storage accounting belong to the project status evidence.

This repair prevents one concrete subtitle-loss case. It does not establish
general subtitle recognition accuracy, arbitrary typography suitability,
soundtrack quality, attended phone recording, AirPods operation, iQOO/NPU
execution or hackathon submission eligibility.
