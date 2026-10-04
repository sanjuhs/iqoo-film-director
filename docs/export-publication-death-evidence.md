# Actual synthetic publication-window recovery — 4 October 2026

The production Media3 exporter completed a real synthetic video and copied it to
an owned pending MediaStore row. An externally recorded force-stop interrupted
the process while its durable journal remained **READY**, before gallery
publication. Fresh-process recovery passed **one method / 0.387 seconds**.

This is pre-event research on the development Phone (3a). The encoder, JSON
writer, gallery-copy worker and recovery code are real production paths. A
**test-controlled main-queue barrier** deliberately held the final publication
runnable. This does not measure a naturally occurring publication window, prove
every interruption boundary, or establish attended capture or general user
benefit. No production exporter/recovery change or production pause hook was
needed.

## Fixture and controlled window

`ExportPublicationDeathTest` reused the existing labelled synthetic padded
English jacket-video fixture. Twelve three-second cuts produced a 36-second
timeline. It opened no Activity, camera, microphone or player, loaded no AI
model, changed no shoot preferences and uploaded no media.

The test observed the actual public start callback at zero and the production
completion callback at 95. That callback posted a bounded test-only main-queue
task; the unchanged `Reel-save` worker then wrote and synced the actual editable
JSON, inserted the actual pending gallery row, copied/flushed/closed the complete
video and durably wrote READY. Its final publication task queued behind the
barrier. The test did not construct a staged journal or mock the encoder,
publisher or provider.

The durable marker at `files/publication-death-test/live-ready.json` recorded
the exact job, process, READY journal, pending row, temp/edit snapshots, original
source and independent sentinel. A PREPARING marker never authorizes a stop.
Baseline inspection allowed zero or one earlier COMPLETE pair and refused
unfinished, additional or unknown/private-source records. Every baseline cut
source had to match a fixed labelled synthetic fixture or demo path before any
gallery media bytes were read. Legitimate Android app-root aliases were
normalized separately from child paths; traversal and child symlink redirection
remained rejected.

## Observed external stop and recovery

The host controller was prepared before launch and retained the same running
instrumentation command throughout inspection. Before stopping, it verified:

- The fixture schema/window/source flags, 12 cuts, 36-second timeline, actual
  zero/95 observations and full unchanged READY marker.
- The exact marked PID was the live app process and the original runner had
  not ended; camera/microphone permissions remained denied and keyguard showed.
- The persisted journal matched the marker and its URI matched the fixture's
  exact app-owned, named, pending Movies/MiniFilm row.
- Expected length, complete stream length, independent row FD stat and temp
  length all equalled **1,340,552 bytes**. Pending-row and temp SHA-256 matched;
  the host independently checked temp size/hash and editable JSON hash.
- The effective barrier deadline still had **44,136 milliseconds** remaining.

The controller used the earlier of the main task's 90-second deadline and the
test's marker-arming time plus 45 seconds: normal Phase1 failure releases the
barrier at that earlier bound. It checked remaining time from device uptime
after final PID/marker checks and required at least two seconds before stopping.
This corrected an initially identified timing-contract gap; the 90-second value
alone would overstate the available window.

The host recorded its stop request at **2026-10-04 12:42:20.202 UTC**, targeted
only this synthetic app process, and confirmed that the process disappeared.
Afterward the same journal was still READY and the temp remained 1,340,552
bytes. The full host sequence took 15,509 milliseconds; this is controller
elapsed time, not a standalone encoding-latency measurement. Host and device
wall-clock log timestamps are not assumed synchronized.

The interrupted Phase1 runner reported **Process crashed** and shell exit code
0. This expected externally interrupted report is **not a passing test** or an
unexplained application crash. The recorded host action and post-stop state
establish its provenance.

Fresh-process Phase2 passed **one method / 0.387 seconds**. It required a new
process, the same READY journal and still-pending exact row, matching temp/edit/
row snapshots and no unrelated active export. Production `ExportRecovery`
cleaned **exactly one** interrupted job with an empty warning: its pending row,
temp, edit and journal were removed. Original source hash/size/mtime, the earlier
COMPLETE published video/edit pair and its synthetic sources stayed unchanged;
recovery returned that earlier pair. The untracked sentinel remained unchanged
through every recovery assertion. Only after success did the test remove its
own sentinel and marker.

## Retained initial failure

The first run failed **one method / 0.038 seconds** while validating the earlier
baseline. Android's legitimate `/data/user/0` and `/data/data` root spellings
resolved to the same app directory, but the original fixture compared the
uncanonicalized root directly with its canonical path. The host refused the
stop and recorded `forceStopPerformed=false`.

The failure preceded original-fixture inspection, sentinel creation, exporter
construction/start, new journal or marker creation, and baseline gallery-media
reads. The test-only path check was corrected to normalize the actual canonical
app-files root while retaining exact child-path and symlink restrictions. The
failed runner/controller/proof remain retained as `initial-publication-death-*`;
they are not relabelled as successful recovery.

## Reproduction and limits

Select `phase1ArmRealReadyPendingCopyAndAwaitParentForceStop` and
`phase2RecoverKilledReadyPendingPublication` separately. Phase1 never passes
naturally. Prepare the host controller first, inspect its exact live READY
marker, recheck process/permissions/keyguard/deadline, record the external stop,
then require the surviving READY/pending state in a fresh process. Do not run
the whole class as an ordinary green suite.

`phase3CleanupReviewedRacedSyntheticPublicationFixture` exists only for explicit
reviewed cleanup of a known synthetic missed/raced trial. It verifies exact
title/cut IDs/source URI/timeline/ownership and preserves the baseline/original;
it is not process-death recovery evidence. It was not needed for the corrected
successful stop/recovery sequence.

Ignored evidence includes `private/evidence/run-publication-death-probe.py`,
`publication-death-host-proof.json`, `publication-death-live-marker.json`, both
phase runner logs, the controller log and scoped `publication-death-metrics.log`.
No raw process IDs, row IDs or private media are included in this document.

The earlier [actual ENCODING interruption check](export-process-death-evidence.md)
and [staged publication checks](export-evidence.md) remain distinct evidence.
This result closes one real READY/pending recovery gate in a deliberately
controlled queue window. Natural publication timing, a live kill after
publication but before COMPLETE, arbitrary providers, real recording recovery,
visible creator workflow, AirPods, iQOO/NPU/Office Kit and accepted hackathon
submission remain separately unverified.

Primary contracts are Android's [pending shared-media
publication](https://developer.android.com/training/data-storage/shared/media),
[package-scoped activity-manager
force-stop](https://developer.android.com/tools/adb#am) and [Media3
transformations](https://developer.android.com/media/media3/transformer/transformations).
They describe API behavior; the source/host/runner evidence above establishes
this observed result.
