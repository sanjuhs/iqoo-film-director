# Pose-break feedback and reel crop playback — 4 October 2026

Pre-event research under `prototype/`. Two gaps affected the three-phase workflow:
sequence preparation disabled person analysis through the whole pose break, and
ordinary take playback fitted the complete source while export center-cropped it.

## Posing before a sequence take

The explicit guided sequence now enables person-framing advice during its
existing minimum eight-second pose break. Matching current-session cues can
update the written advice; with spoken direction enabled, at most one idle
framing cue uses the existing offline speech completion path. At the deadline,
analysis stops before the countdown. If that cue is still speaking, preparation
waits for its completion before “3”; a cue completed early cannot shorten the
break. The existing45-second speech watchdog or failure pauses preparation.

Object/detail shots retain their person-analysis exclusion. Instruction playback,
countdown and recording remain quiet from pose advice. Stop, disabling the
sequence or spoken direction, errors, replacement and backgrounding invalidate
pending preparation. Deadline and completion callbacks check current generation,
sequence, session, screen and foreground ownership. Landmark scores do not
certify readiness or decide when to record. The break is a main-loop scheduled
minimum, not an exact real-time cutoff. The2s inference-latency guard is not proof
of complete source-age freshness or useful live advice.

## Preview the intended crop

Take and suggested-trim playback now explicitly request a centered, clipped9:16
viewport. Media3 ZOOM fills that viewport, rather than fitting to the phone's
own aspect ratio. Back, the framing-only label and playback/scrub controls remain
reachable. The label explains that source audio remains and final export text/
color effects are absent. Last-exported-reel playback retains FIT mode.
Both modes still open paused and release the player on background; selected
ranges keep source-relative bounds and restore paused timeline position.

Official references: [AspectRatioFrameLayout](https://developer.android.com/reference/androidx/media3/ui/AspectRatioFrameLayout),
[PlayerView](https://developer.android.com/reference/androidx/media3/ui/PlayerView)
and [layout-change callbacks](https://developer.android.com/reference/android/view/View.OnLayoutChangeListener).
The pinned runtime remains Media3 1.5.1; no new dependency or model is used.

## Verification and retained failures

The first fresh API36 emulator runner passed **28 of 29 methods in 48.990s**:
all11 shoot-pose,10 workflow and5 footer checks plus two crop methods passed.
The remaining crop lifecycle method sampled controls during Media3's opening
animation: visible height108px versus expected126px. Production was unchanged.
The test now shows controls once, waits at most2s for two consecutive complete
visible rectangles, then applies the same exact bounds assertions. Its complete
three-method rerun passed **3/7.027s**. Together the runners establish29 distinct
passing methods; this is not a single29-pass run. Builds passed offline (1s and
671ms) using existing dependencies. Original failed receipts remain private.

The11-method pose suite includes four new preparation checks: person/object and
stale-cue gates; the actual monotonic minimum8s deadline; one owned cue finishing
before countdown3; Stop/sequence-disable/replacement/background invalidation;
and denied speech failure pausing preparation. Speech uses a controlled denied-
focus seam with the production completion callbacks retained. No actual TTS,
landmark inference, microphone, camera capture or AirPods playback is proved by
these synthetic state checks. The countdown's existing completion behavior is
preserved, with no landmark-triggered recording.

Crop checks use two generated silent3s sources with strict size/hash binding:

| Source | Coded size | Native rotation | Bytes | SHA256 |
| --- | --- | --- | ---: | --- |
| Normal landscape | 640×360 | 0° | 307173 | a093ebb0015492d558bbb8c44b8c63e4f6d6067bfe34260596cd42bb53f87bde |
| Metadata-rotated | 360×640 | 270° clockwise | 264444 | 85aaebc2959775f8b6d0db637c9af517b5bef7def320b7d7698e75bbd1bf5c15 |

ffprobe reports the latter90° counterclockwise; Android reports270° clockwise.
An initial metadata-writing command did not produce rotation metadata; that
failed owned fixture remains private and was excluded from passing tests. Final
fixtures have no audio. Actual native READY players open paused on500–2500ms
cuts. Exact viewport/surface/clipping/center geometry, full visible Back/label/
play/scrub bounds, default FIT behavior and release/rebuild/recreation at paused
700ms cut position passed. Preferences and source bytes/mtime stayed unchanged.
These checks establish geometry and lifecycle on these fixtures, not universal
encoded-pixel parity or real footage quality.

After instrumentation terminated, ordinary Main take buttons opened both silent
sources. Settled screenshots were visually inspected: central9:16 framing,
clear disclosure and complete controls. Playback stayed paused. A UI dump helper
initially accepted Android's exit-zero “could not get idle state” and retained an
old XML; it now removes only its own remote XML and rejects reported errors.
Fresh Preview hierarchy still could not become idle. Current Main hierarchy
provided the actual button locations; settled screenshots and actual resumed
PreviewActivity confirmed the result. No successful fresh Preview hierarchy is
claimed from that helper. No private media or hardware identifiers enter Git.

Physical headless ClipPreviewTest passed **3/0.915s** on an existing labelled
synthetic source, verifying native range/paused behavior without an Activity or
sound playback. Metadata-only gallery verification passed **1/0.975s**, unchanged
28 owned rows/0 pending/8,319,196B. Normal installs used no permission grants.

## Installed checkpoint and storage

Built, saved and independently read installed APK match: **52,838,059B**, SHA256
**c8534229daa61d538a608bce76e3a278fb04fc8585ca8d66bdb6a9de2b966ccd**.
Packaged31,635B notices match source. Separate Qwen/Whisper weights stay outside
Git/APK; bundled ML Kit dependency assets remain. No Internet permission.
Normal Main launch was requested behind secure keyguard; camera/microphone remain
denied. No unlock, grant, private recording/upload or aircraft action occurred.

Own temporary AVD and exact matching registration were removed after its process
terminated; the other AVD was untouched. Qualified observed peak including the
external4,096B registration was **10,844,277,013B**, above the10GB aim/below15GB
cap. Final adjacent20:57 sample is **9,327,196,437B**. The external generated cover
is counted once; historical missing-archive/cache reserves and existing inventory/
preinstalled-runtime exclusions remain. This is qualified project accounting,
not a full-machine inventory. No new models, SDK, JDK or dependencies downloaded.
See [storage ledger](storage.json).

Goal remains active. Attended Pose → Perform → Assemble and AirPods sound,
useful learned direction, actual iQOO/NPU/Office Kit, eligible event source and
accepted submission remain unproved. Per-shot creator-selected framing intent
is a next improvement; current person advice still follows the broad style.
