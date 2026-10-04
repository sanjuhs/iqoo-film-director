# Creator-reviewed shot assignments — 4 October 2026

Pre-event research under `prototype/`. This addition links selected takes to the
editable shot plan through explicit creator review. It is a metadata checklist,
not a learned coverage evaluator, semantic garment check or quality judgment.

## Behavior

A take starts unassigned, including a newly captured, imported, recovered or
synthetic take. Its original capture `shotId` does not count as a reviewed
assignment. The creator may assign one take to several current plan shots and
several takes to one shot. Only selected local-source takes with valid trim
bounds and matching reviewed IDs count. Missing means no such selected
assignment; it does not mean that the footage visibly lacks the subject.

Replacing a plan gives the new shots a fresh namespace. Earlier assignments
remain preserved but do not satisfy the replacement plan. A restored legacy
plan is preserved; absent assignment metadata stays empty. The pickup control
chooses the next missing planned shot and opens Direct for explicit camera
start. It must not start a session, camera, microphone, countdown or recording.

Mappings belong in saved state and additive edit-document fields, with
`creator-reviewed` provenance for nonempty assignments and `unassigned` for
empty assignments. They do not change source bytes, captions, subtitle timing,
trims, cut order or selection. Portable packages retain their established
relative-path privacy behavior.

## Verification

Parent physical headless runner passed **17 tests / 5.915s**: five new
ShotCoverage metadata methods, two new ShotMappingExport methods, six existing
portable-package checks, three subtitle-overlap/export checks and one owned
media inventory. Capture permissions remained denied; no Activity, microphone,
playback, model inference or aircraft action was needed for this runner.

The new pure real edit-document serializer check retained original shot facts,
multiple explicit IDs, empty/unassigned mapping, independent lists/JSON and
unchanged captions, source-relative subtitles, cuts and timeline. The actual ZIP
check held its source opener after the production snapshot, changed caller
mappings, then verified the prior confirmed mappings, two selected cuts in order,
one deduplicated original with identical SHA/bytes, relative paths and omission
of original private shot IDs/device URIs. Only its returned test ZIP was removed.
The fixture was existing labelled synthetic `fixtures/jacket-speech.mp4`; no
private source or new model/media download was used.

The real encoder regression produced **4064ms**, two adjacent/unsorted subtitle
cuts, both visible caption shapes, preserved subtitle timeline mapping and
portable original hash. It exercises the extracted serializer through actual
publication with unassigned mappings; it is not a measured encoded creator reel
with visually validated shot coverage.

Fresh empty API36 ARM64 emulator initially ran **31 methods / 48.004s**, with one
failure in the new recreation fixture; the other 30, including 27 unchanged
assembly/pose/batch/general-lifecycle/recovery regressions, passed. The fixture
edited saved JSON while the old Activity was alive, then its legitimate onStop
save replaced the injected data. The retained assertion failure was corrected
by closing the old Activity before injecting that JSON, launching fresh and
then recreating. Production restore and the assertions were unchanged.
Corrected **all four new UI methods passed / 9.198s**. These use actual mapping
dialogs, selection and pickup controls, and verify many-to-many assignments,
cancel/clear behavior, stale dismissed/render/background Save/Clear suppression,
ready-cache snapshot invalidation, legacy/fresh plan identity, old unknown ID
retention, malformed/deduplicated restore and no implicit camera/session/audio.
The cache pointer for the invalidation test is explicitly a synthetic three-byte
fixture, not a created ZIP. Full preferences were restored. UI checks require a
fresh unlocked emulator with empty capture/export journals, absent separate
weights and denied camera/microphone before changing any preferences.

Parent also visually inspected the real fresh default Assemble screen: all five
assignment rows and next-missing control were readable ahead of Look & Feel at
default Pixel 9 font/layout. This is one emulator layout, not general usability
or physical creator acceptance. Host camera/audio/snapshots were disabled;
existing SDK/image reused. A lab airplane broadcast was rejected; supported
`cmd connectivity airplane-mode enable` then succeeded and setting 1 was verified.
Only this own temporary AVD/registration was removed after terminal shutdown.

App/test build passed **1s**; corrected test-only rebuild passed **743ms**. Normal
app/test installs succeeded, without permission grants. Built/saved/independently
read installed APK: **52,838,059 bytes**, SHA256
**608b8a58d5ec28ca27269bb95660e8df9e758a09ed84cbe640a268fe2623d071**.
Notices remained source-identical **31,635 bytes**, SHA256
02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68.
Separate Qwen/Whisper weights remain outside Git/APK; bundled MLKit SDK assets
remain. No Internet permission. Normal physical Main launch was requested behind
secure keyguard; attended visible use is not established.

Read-only owned gallery inventory: **23 rows / zero pending / 7,631,066 bytes**,
without reading media content. Qualified temporary accounting peak including the
one external AVD registration: **10,493,207,187 bytes**, above the 10GB aim and
below the 15GB cap. After removing only the own AVD, final adjacent accounting at
19:14 IST was **9,294,561,939 bytes**, below both. Missing-full-archive reserve,
shared-cache/oat/provider and adjacent sampling limitations remain as described
in [storage](storage.json). No new SDK/JDK/image/dependency/model downloads.

Learned semantic coverage, usefulness on real creator footage, live phone
capture, AirPods sound routing, iQOO/NPU, Office Kit, eligible competition code
and accepted submission remain separate unfinished gates.
