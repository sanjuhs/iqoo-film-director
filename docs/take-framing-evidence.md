# Local sampled cut-framing review — 4 October 2026

Pre-event research under `prototype/`. Assemble previously offered playback,
editing, transcription and creator shot assignments; local pixel-based frame
review was confined to reference clips. **Review cut framing** explicitly inspects
three nearby frames from the chosen take. It leaves the original, selection,
trims, captions, subtitles, mappings, shot plan and reference context unchanged.

## Behavior

The request captures exact take identity in Main plus local URI, duration and
trim bounds. Three requested source times fall at the cut's quarter, midpoint
and three-quarter positions. Inputs must be valid local file/content sources,
with cuts of at least 0.25 seconds and originals no longer than three minutes.
Actual decoder metadata validates the readable video and available bounds.

Decoded frames are bounded to 512 pixels, then approximately center-cropped to
9:16 before the existing bundled single-image ML Kit pose check (CPU preference).
No additional LLM/VLM, model weights or network permission are used. Displayed
thumbnails are 144×256 and observations use plain, qualified descriptions. Missing,
partial or unavailable landmark evidence stays uncertain; it never marks a shot
covered, good, complete or safe. This is not garment recognition, motion analysis,
learned taste or a calibrated framing classifier.

Review owns one worker request. Cancel/background/render/destruction suppress
stale results; source, take replacement or trim changes reject an old result.
Done/dismiss detaches and recycles temporary review images. No review images or
text are persisted or added to exports. The underlying platform decode can finish
before cleanup; ML Kit tasks retain their private image until completion after
cancellation/timeout. A worker drain is not proof of a hard native abort deadline.

Requested seek times are not exact decoded frame timestamps. Nearby frames may
fall outside the selected cut. The approximate crop precedes color and text
layers and cannot establish exact final export appearance on every format.
Play the actual output reel when checking the final crop, captions and motion.

## Official API basis

[Android MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
specifies that a nearby frame can be returned instead of the requested instant.
[Media3 Presentation](https://developer.android.com/reference/androidx/media3/effect/Presentation)
specifies cropping from the top/bottom or sides to achieve the chosen aspect.
[ML Kit pose detection](https://developers.google.com/ml-kit/vision/pose-detection/android)
describes the bundled pose detector and CPU preference. These APIs do not prove
useful real-creator guidance, complete clip coverage or actual NPU acceleration.

## Verification

Final app/test build passed in 1s and normal installs passed without permission
grants. Five core and three actual decoder/pose methods passed in the initial
**9-method/11.476s** physical runner. Its fourth native method failed only an
expected rotation value: ffprobe reports the display matrix counterclockwise90°,
while Android VIDEO_ROTATION reports the equivalent clockwise270°. The fixture
SHA stayed fixed. The test-only expectation/log correction preserved every
pixel/source assertion; the targeted method passed **1/7.412s** on both inputs.

The real flat synthetic source returned three **empty or unclear** samples at
requested 2000/3000/4000ms in 1841ms. The public static person source returned three
**full-body** landmark labels at 1000/1500/2000ms in 1824ms, with the exact visible
joint groups. Independently drawn crop-to-thumbnail whole-image mean RGB error
was 1.71825; squeezing the full landscape source produced a larger error. These
labels are limited evidence from an annotated static public image, not held-out
raw fashion footage, garment recognition or actual motion.

Actual Clean/no-title/no-caption Media3 outputs were 720×1280, each nominal 2000ms
and encoded 2067ms. Three time-mapped decoded output samples agreed with review
thumbnails: maximum whole/center mean RGB error **2.3097/3.1284** for upright and
**1.03865/2.2087** for Android270° rotation. Content variance and incorrect 180°
rotation comparison prevent an empty/flat image from passing parity. This proves
approximate crop agreement only on these two static fixtures. Callback-verified
completed synthetic MP4/JSON pairs remain saved; only their exact COMPLETE
journals were detached, with no gallery output deletion.

Five core checks cover input/cut bounds, identity snapshots, times/crop geometry,
owned bitmap disposal and cancellation/reuse/error publication. Native cancellation
suppressed stale and closed callbacks before independent reuse; source SHA/size/
mtime, preferences, model-file metadata and denied capture permissions stayed
intact. Worker drainage is not proof of pending ML Kit task completion or a hard
inference abort. Up to three tasks can remain after timeouts in one review;
repeated reviews have no demonstrated global outstanding-task bound.

The initial own fresh-emulator UI runner **27/140.605s** passed 26 methods:22 prior
regressions and four new methods. Its background method timed out while using
ActivityScenario.onActivity when stopped and then closing through EmptyActivity.
The test-only repair captures reviewer identity while RESUMED and polls its
thread-safe worker state while actually CREATED, retaining real lifecycle and
all cleanup/no-auto-resume assertions. The five-method rerun passed **5/8.451s**.
Production code and assertions were not weakened for either fixture repair.
Rotation test build 559ms; corrected UI test build 597ms. Both failures are retained
in ignored receipts. Fresh emulator host camera/audio and snapshots were off;
airplane mode verified, no separate planner/Whisper/projector weights installed.

Root also launched the actual app on this empty emulator, explicitly generated
its labelled demo clips and opened Review cut framing through real controls.
Actual local pose inference populated the modal. Top/bottom screenshots and
hierarchy inspect readable qualified observations, three requested times and
reachable Done; this is one synthetic layout, not attended phone usability.

Current built/saved/independently read installed APK 52,838,059B:
SHA256`168406ff0a88119d1511cdf205184ba43c3d4cff2c2b312c881a0e01d170ea4f`.
Notices 31,635B/source-identicalSHA256`02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68`.
Separate Qwen/Whisper weights excluded from Git/APK; bundled ML Kit assets remain.
No Internet permission. Read-only owned gallery inventory passed 1/0.172s:
28 owned/0 pending/8,319,196B. No private-media upload or capture permission grant.

Fixtures are flat synthetic video and a static video derived from the already
attributed Google public pose image. The source image remains unchanged. These
are not private footage or a real creator shoot. Physical camera/microphone/
AirPods, iQOO/NPU, Office Kit and event eligibility remain separate gates.

Public fixture source: [Google's annotated pose illustration](https://developers.google.com/static/ml-kit/images/vision/pose-detection/girl_pose_3d.png),
Google Developers ML Kit documentation, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Original 1,383,345B/1190×2048/SHA256`012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21`
unchanged; fit/padded static landscape video 640×360/24fps/72frames/3seconds/noaudio:
15,479B/SHA256`2f6415feb62b7d1bd51f377f6557de191b0e68262715514a7631663e4a173500`.
Metadata-rotated copy15,479B/SHA256`6189a8f814fa114170d29b33bbe28b0a585882f4bba774a1f9e452e821b51e1c`.
Two host fixture-encoding attempts and decoder warnings were retained: bytes after
PNG IEND confused automatic demuxing and a25→24fps conversion made71frames.
Explicit image demuxing produced independently decoded72-frame H264; its input
trailing-chunk warning remains disclosed. This was fixture preparation, not an
Android application failure; no private footage or new dependency download.

Normal Main launch requested on the physical phone behind secure keyguard;
final read-only verification confirmed camera/microphone denied and no Internet
permission. No unlock/grant/private upload occurred. Only the own test AVD and
matching registration were removed; existing other AVD untouched.
Qualified temporary peak **10,903,427,857B**, above 10GB aim/below 15GB cap; final
adjacent accounting **9,302,457,105B**, below both. Existing missing-archive/cache/
oat/provider and full-machine inventory qualifications persist. No new SDK/JDK/
system-image/model/dependency downloads occurred for this increment.
