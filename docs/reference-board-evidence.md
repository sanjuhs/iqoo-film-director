# Reference moments → reviewed shot cues

Pre-event phone research, 4 October 2026. This adds a sparse, creator-reviewed
reference board to Brief. It does not claim to understand an entire trend,
recognize motion/audio from still images or recreate a reel automatically.

## Source and review contract

The creator chooses a local reference, then three increasing source times.
Defaults use approximately 10%, 50% and 90% of its measured duration. The decoder
returns nearby frames, not verified exact presentation timestamps. Each frame
runs through the existing local CPU VLM and separate pose-landmark framing
heuristic, serially. Every native model lifetime ends before the next frame.
No camera, microphone, network permission or new model is involved.

The board shows small independent thumbnails, requested times and editable
notes. The creator selects useful moments and explicitly confirms their notes.
Unclear moments can be excluded without rewriting them. Selected notes are
limited to 70 characters each and the canonical summary to 210 including times;
validation never silently truncates. Later/Stop dismiss without applying. The
confirmed board saves text, selection, times, provenance and its local URI, not
pixels. Reopening explains missing thumbnails and requires explicit reinspection
to see frames again. Excluded observations remain marked excluded and never
enter planning. Unconfirmed drafts are temporary across recreation.

Cancellation invalidates the whole batch, suppresses stale progress/results and
closes that batch's engines. Decode waits are bounded, though the underlying
provider/MMR call cannot be forcibly interrupted. Late frames are recycled even
after an abandoned decode waiter. Thumbnails are independently copied for board,
review and caller ownership; review copies detach before recycling.

## Actual local fixture and first run

The 9-second, 360×640 H.264 fixture contains the attributed public Google pose
image, its top-32% crop letterboxed into portrait, then black pixels. It has no
audio, no private media and was synthesized locally. Bytes: **49,836**;
SHA-256 **eede24ef7294525a6779a5873ccc011fe976a44d3a665c8f22cd484a5841168f**.
The original public image/hash and attribution are in the local AI evidence.
No native runtime model or private source was uploaded.

The first combined runner ran **9 tests in 76.701 s with 1 failure**. Five board
methods and three sparse-reference methods passed. Actual timed inspection at
1000/4500/7500 ms completed in **32.840 s**, with progress at 10.919/21.735/32.840 s.
Full-body framing was supported; this letterboxed crop yielded head-and-shoulders
rather than the earlier unletterboxed crop's abstention. Black remained uncertain
unknown/empty-or-unclear/unknown. This is one encoded fixture, not general portrait
accuracy. Caller-array mutation did not change requested times, prior canceled
batch callbacks were zero, thumbnail pixels were distinct, and original SHA was
unchanged. Review/selection/JSON bounds and independent ownership checks passed.

The actual planner method failed: Hero followed a confirmed left direction, but
Side/Closing kept generic default poses, and captions copied source time labels
0:01–0:05. A first grammar/prompt repair produced word captions but still missed
the profile cue (**1 test, 30.967 s, failed**). Removing default reference-mode
pose examples preserved the forward cue but still missed named profile and
invented an unspecified jacket lapel (**1 test, 26.627 s, failed**). These
outputs are retained; no grounding or arbitrary garment-fact accuracy is claimed.
A subsequent named-cue-word repair passed the older single-method assertions
in **28.438 s**, preserving left/profile/forward and word captions, but still
invented a lapel. That installed test lacked the subsequently added garment-part
and creator-choice assertions; its green report is not complete grounding proof.

The current repair constrains only an unspecified Detail instruction/caption in
Fashion plans with the reviewed-moments marker and no explicit `Detail:` cue.
Native decoding chooses among two creator-choice instructions and three generic
captions, with independent parser validation. The model label discloses
**creator-choice detail constraint**. The other four instructions/captions remain
generated within their normal bounded grammar; an explicit Detail cue leaves
the free role grammar active. This is deterministic unknown-feature handling,
not learned garment-feature selection.

The next combined runner ran **11 tests in 141.547 s with 1 failure**. Its
generic Detail was correctly constrained, but the actual 41.280-second native
plan again replaced profile/forward with generic poses. The three-frame batch
passed in **32.330 s**, and the ordinary introduction/fashion checks passed at
23.785/32.580 s. This repeat demonstrates that cue prompting alone is unreliable.
The next change retains validated, explicitly named creator-authored cues after
native generation and discloses that retention; it cannot be presented as learned
grounding. The final runner passed **23 tests in 92.759 s**: eight reference-board cases,
13 synthetic quiet-stop policy cases and two finalized-container cases. The
actual serial board completed in **32.779 s**, with progress at
10.829/21.840/32.778 s. Original bytes/hash stayed unchanged, the letterboxed
crop supported head-and-shoulders, and black remained explicitly uncertain.

Two actual CPU plans completed in **27.781/29.638 s**. The first retained exact
creator instructions `face left`, `pause in profile`, `face forward` and disclosed
both retention and the unknown-Detail constraint. The second retained a confirmed
left-pocket Detail, omitted that unknown-feature constraint and succeeded after
an invalid preflight on the same planner. Fifteen malformed/order/duplicate-role
cases produced one main-thread error, no plan/fallback, resident handle zero and
no held model lease.

These checks establish the disclosed hybrid mechanics, not artistic quality.
The second native Movement was **“Walk to the left pocket”**, an unusable action
that still met lexical/shape checks. Captions may also stay generic when creator
instructions change. The app explicitly asks for directions/captions to be
reviewed and edited before filming; no creative-quality success is claimed.
Canonical notes containing a semicolon followed by a clock-shaped phrase can
be ambiguous; ordinary body semicolons are preserved. No UI, private reference
or attended filming was tested.

Evidence is retained, ignored, in private/evidence/reference-board-*. The newest
visible review/persistence workflow still needs an unlocked attended UI check.
The existing phone/camera/AirPods/iQOO/NPU/Office Kit and event-code gates remain
in [the completion audit](completion-audit.md).
