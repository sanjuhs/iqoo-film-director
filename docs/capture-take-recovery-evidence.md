# Private phone-take recovery — 4 October 2026

This repair in the pre-event phone prototype passed synthetic store, UI and
fresh-process checks. No actual CameraX recording, microphone capture, audio
route or interruption of a live recording is claimed here.

## The observed source race

The old activity could be destroyed while CameraX was still finalizing its
private MP4. The replacement activity scanned for takes only during startup.
If that scan ran before the file became readable, it found nothing. A later
successful Finalize kept the file, but the closed controller correctly suppressed
the old activity's UI callback. The replacement had no further recovery action,
so the saved take remained absent until another recreation. Generic fallback
recovery also lost the shot/title/caption and selected recovered files by default.

Android documents that lifecycle source inactivity can finalize a recording with
output containing frames captured before the camera closed. That supports
handling a saved output independently of an activity callback; it does not prove
this phone's live behavior. See the official
[Finalize API](https://developer.android.com/reference/androidx/camera/video/VideoRecordEvent.Finalize).

## Implemented recovery contract

`CaptureController.startRecording(Shot)` snapshots the reviewed shot ID, title
and caption into a private per-file pending journal **before** recorder start.
The existing no-argument method remains available. If the initial metadata cannot
be saved, recording does not start.

On usable Finalize, `CaptureTakeStore.complete` validates the actual local video
container: video present, positive dimensions and duration longer than 300ms.
It commits ready state and that container duration before the controller checks
whether its activity is closed. Closed UI callbacks remain suppressed. A failed
metadata update does not cause valid media deletion: the duration is returned
and surviving pending facts can be read during later recovery.

Journals live beside exact `take-UUID.mp4` files in app-private `files/takes`.
They contain relative basenames and creator shot facts, not credentials or
hardware identifiers. Reads are bounded to 64KiB. Canonical direct-child checks
reject traversal and child/direct-parent symlinks while accepting legitimate
app-root spelling aliases. All store instances share a lock around AtomicFile
operations. Android's
[AtomicFile contract](https://developer.android.com/reference/android/util/AtomicFile)
specifies sync/commit at finish and requires callers to provide their own
threading protection.

An active pending file is skipped, so recovery does not index a recording still
flushing in this process. After a fresh process loses that active reservation,
an inactive pending file can be recovered if its actual container is valid,
retaining surviving shot facts. Readable legacy captures without metadata use
an explicit generic “Recovered take” label. Missing, malformed or unreadable
media/metadata and unrelated files remain intact. Bad metadata is not rewritten
with guessed shot facts; a valid source can receive a generic recovered label
and an unreadable-note count instead.

Main requests recovery after restoring startup state, on idle resume, and when
entering Assemble. **Find saved phone takes** in Assemble is the explicit
fallback if finalization occurs after the automatic checks. Recovery starts no
camera, microphone, playback, sequence or background service. It is deferred
while the activity is busy or a shoot is active.

New recovered takes are **unselected** until the creator reviews them. Existing
file URIs and canonical aliases are deduplicated; existing titles, captions,
source trims, subtitles, order and selection remain unchanged. Main saves the
merged state. The manual result reports additional readable takes and unreadable
files/notes without logging creator text or private paths. Failed-new-capture
cleanup may remove its owned journal, but the store never deletes a video.

## Completed checks

The unlocked, initially empty project emulator passed **19 tests in 28.955s**:
one new recovery UI method, six batch-subtitle UI methods, three assembly-edit
methods, seven shoot-pose methods and two existing workflow methods, including
ready ZIP resume. The recovery method copied only a generated three-second
fixture into a dedicated injected store. Actual entry to Assemble, **Find saved
phone takes** and resume each recovered one late file; recreation retained the
saved results. Recovered files stayed unselected, and existing edits, selection,
order, preferences and original bytes were preserved. No capture, model inference
or audio was used; camera and microphone remained denied.

The physical-phone headless runner passed **25 tests in 42.327s**, including two
new store methods, two existing finalized-container methods and 21 ASR, trim,
batch and voice checks. The durable-store case measured **5746ms** from the
synthetic MP4, skipped active pending output, retained the pre-record shot
snapshot, and preserved existing edits found through a canonical file alias.
The guard case recovered one valid legacy source and reported three unreadable
items; child symlinks, malformed metadata and unknown sources remained intact.
Original hashes were unchanged. These are synthetic media/store checks, not
physical recording or UI acceptance.

A separate two-phase check exercised genuinely fresh process state:

- Phase one passed **1 test in 0.077s**, writing a pending journal and a copied
  known synthetic MP4. The live process reservation correctly prevented recovery.
  The runner completed normally; its process was already absent before the next
  phase. No external force-stop or live recording interruption was performed.
- Before phase two, the parent checked the journal schema, original/copy/journal/
  sentinel hashes and sizes, unchanged pending state, denied capture permissions
  and secure keyguard. Phase two used a verified new process and passed **1 test
  in 0.062s**: one unselected 5746ms take with retained shot facts, idempotent
  recovery and unchanged originals/sentinel. Cleanup removed only its own
  synthetic fixtures.

This establishes durable synthetic pending-file recovery across process
boundaries. It does not establish CameraX or codec finalization after killing a
recording, nor an AirPods route. No private media upload or permission grant
occurred.

Two initial compile failures were retained: the store's missing outer-if closing
parenthesis and a duplicate MainActivity `onResume`. Both were corrected without
weakening validation. The final app build passed in **820ms** and test build in
about **1s**; both installs reported success.

Ignored evidence files: `private/evidence/capture-recovery-ui-tests.log`,
`capture-recovery-ui-metrics.log`, `capture-recovery-native-tests.log`,
`capture-recovery-native-metrics.log`, `capture-pending-stage.log`,
`capture-pending-fresh-process.log`, `capture-pending-fresh-metrics.log`,
`capture-pending-process-proof.json`, `capture-recovery-app-build.log`,
`capture-recovery-app-build-corrected.log`, `capture-recovery-app-build-final.log`
and `capture-recovery-test-build.log` (all under `private/evidence/`).

The parent owns APK hashes, storage accounting and status. Physical take
playback, full sequence, AirPods and iQOO acceptance remain separate.
