# Actual synthetic export process-death check — 4 October 2026

This tests recovery of a genuine on-phone Media3 export after externally stopping
its process. It uses the existing labelled synthetic padded jacket speech clip,
12 three-second cuts (36 seconds), and the production ReelExporter. It does not
mock or pause the encoder, call stageInterruption, capture private surroundings,
load an AI model, change shoot preferences or grant permissions.

## Observed runs

The first attempt missed the kill window. Its live marker showed positive
progress 3% and 51,564 bytes; the runner then failed after 13.274s because the journal
had advanced from ENCODING to SAVING. Read-only inspection found READY afterward.
The host performed **no force-stop**. This is a retained controller-race failure,
not an app-recovery pass. An explicit reviewed cleanup method passed **one
method/0.322s**, verifying the exact synthetic title, 12 cut IDs/source URI,
36-second timeline, ownership, source hash/size/mtime and prior completed pair
before removing only that trial's outputs/marker/guard. This cleanup is not
process-death recovery evidence.

The corrected host controller was written before launch and started the runner,
polled its durable marker and checked liveness within one running script. The
marker followed actual zero-start and advancing public export progress, growing
output and a non-null Transformer. It reported **progress 4%**, **68,121 bytes**
at arming. Before stopping, the host confirmed the same live process, showing
keyguard, denied camera/microphone, an ENCODING journal without a gallery URI,
and **121,399 bytes** of output. It then force-stopped only dev.minifilm.director.
Afterward the process was absent, that same journal was still ENCODING, and its
temporary file remained 121,399 bytes. The host completed this sequence in 2826ms.

The deliberately interrupted Phase1 runner reported **Process crashed**; its
shell exit code was 0. This expected interrupted report is **not a passing test**
and is not an unexplained application crash. Both the host proof and runner
report are needed to establish how the process ended.

Fresh-process Phase2 passed **one method/0.242s**. It verified the interrupted
UUID, original live-progress marker, actual temp, unchanged synthetic source and
exact baseline, then called production ExportRecovery.reconcile. It cleaned
**exactly one** interrupted job with no warning: its temp/journal were removed,
no edit or gallery row existed for that job, and the original hash/size/mtime,
untracked cache sentinel and earlier completed published video/edit pair stayed
unchanged. Recovery returned the exact earlier completed pair. Only the test's
sentinel and marker were removed after all assertions passed.

Normal app launch was subsequently requested behind keyguard. A final read-only
check found only the original COMPLETE journal, byte-for-byte fields matching
the pre-test baseline, with the fixture marker absent and capture permissions
still denied. The installed production APK is unchanged from 9239099; no
production-source repair was necessary for this gate.

## Reproduction and limits

Select ExportProcessDeathTest methods separately. Phase1 must be externally
interrupted while its exact job remains ENCODING; it never passes normally.
Phase2 requires a fresh process and the surviving marker. Running the entire
class as an ordinary green suite is invalid. Phase3 is restricted cleanup of a
reviewed, known synthetic trial after a missed window; it is not recovery proof.

The Android test accepts zero or one strictly verified prior COMPLETE pair and
refuses unknown, unfinished or additional journal records. The parent refuses
the unattended stop if keyguard or capture-permission state changes. No baseline
media or original source is deleted. Private proof/manifests, three runner reports,
corrected metrics and host controller remain ignored under private/evidence/
process-death-* and run-process-death-probe.py.

Primary contracts: Android documents package-scoped
[activity-manager force-stop](https://developer.android.com/tools/adb#am)
and [Media3 transformations](https://developer.android.com/media/media3/transformer/transformations).
The test pins Media3 1.5.1; its normal abort waits for the actual internal worker
to end before fixture cleanup. These API contracts are separate from the device
observations above.

This closes the genuine **ENCODING process-death** check for one synthetic input
on Phone (3a). It does not prove all kill/publication windows, arbitrary providers,
revoked inputs, live-camera interruption, visible UI recovery or AirPods. Earlier
staged publication-window checks remain staged. Full attended creator capture,
iQOO/NPU/Office Kit and hackathon acceptance remain open.
