# Single-image task ownership — 4 October 2026

Pre-event research. Previously FramePoseFraming returned after caller cancellation
or timeout while ML Kit retained its private image/detector. New reviews could
start another task before the first completed. One private static shared gate
now admits only one single-image helper request before allocating its input or
detector. TakeFramingReview and VisionReference use this same helper.

The original five-second wait budget covers admission/preparation/waiting. Waiting
polls cancellation in bounded intervals. A submitted task retains its image,
client and slot until a non-Activity completion listener attempts both cleanup
steps exactly once. Caller timeout/cancel never frees pending task resources.
Setup failure before a submitted task cleans safely. Unconfirmed cleanup or
pending-task listener-registration failure retains the slot and refuses another
backend, rather than accumulating work. Existing classification thresholds,
labels and CPU preference remain unchanged.

This bounds this helper family, not live PoseCoach, ReferenceAnalyzer, all native
threads or memory. A stuck task can keep the slot occupied until process restart.
Synchronous SDK setup/process and external video decoding cannot be preempted by
the wait budget. isRunning on the take-review worker still does not prove native
abort or all model cleanup. No hard abort or live-camera concurrency claim.

## Primary API basis

[Task completion](https://developers.google.com/android/reference/com/google/android/gms/tasks/Task)
covers success, failure and cancellation, including already-complete tasks.
Activity-scoped listeners disappear onStop, so resource cleanup uses an executor
listener. [Tasks.await](https://developers.google.com/android/reference/com/google/android/gms/tasks/Tasks)
times out the wait; it does not prove backend cancellation.
[PoseDetector](https://developers.google.com/android/reference/com/google/mlkit/vision/pose/PoseDetector)
returns asynchronous Tasks and documents close as releasing detector resources.

## Reproducible checks

App/test build passed in1s; normal installs used no permission grants. A physical
runner passed **17/11.636s**: five new controlled Android-task ownership methods,
four FramePoseFraming methods, five take-review core methods and three actual
decoder/pose methods. The actual export-parity method was deliberately excluded
because export algorithms were unchanged; no new gallery outputs were created.

Controlled tests use per-call factories and isolated gates, never a mutable global
backend. They prove pending-task image/client retention across cancellation and
timeout, admission refusal without another detector, waiting cancellation, setup
failures, already-completed success/error/cancelled tasks, exact-once late cleanup
and independent reuse. A malformed null successful result tests ownership only;
real valid pose classification has separate native evidence. Intentional cleanup
failure poisons only its isolated test gate, while an independent gate works.

The actual cancellation test observes the unchanged production factory/client/
Task through a per-call delegating wrapper using the real shared production gate.
It cancels only after process returns an **incomplete actual Task**, not by counting
supplier polls. The Task remained pending after caller return in this run. A
second real request creates its client only after the first Task completes, its
bitmap recycles and real close returns. Two distinct Tasks/images, maximum one
owned client, exact-once close, released gate and full-body reuse all passed.
No guessed race passes as pending-task evidence. This establishes API-level
ownership and ordering, not TFLite internal thread termination or abort latency.

The public original remains SHA256
`012dff1fb8123ff9d29efb3b7aaef0a85a3cd637168b6051fd4b2c2e393dbe21`,
with source bytes/size/mtime, preferences and denied capture permissions preserved.
Full image33landmarks/full-body518ms, top32percentcrop12landmarks/reviewneeded398ms,
black0landmarks365ms. Actual take review again returned3full-body public samples
in2067ms and3empty/unclear flat samples in2440ms. These public/static fixtures
do not establish real creator guidance, fashion recognition or motion quality.
Attribution: [Google ML Kit public image](https://developers.google.com/static/ml-kit/images/vision/pose-detection/girl_pose_3d.png),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

Read-only owned-gallery check passed **1/0.783s**, unchanged28owned/0pending/
8,319,196B. Built/saved/independently read installed APK52838059B,
SHA256`ae9b04e68f9147fc4de5fe181c356d7ba82372f62a98e69b076bd2744cc7da2b`. Notices remain source-identical31,635B. Separate weights
stay outside Git/APK; bundled ML Kit assets remain. No Internet permission. Normal
Main launch requested behind secure keyguard, camera/microphone denied. No unlock,
private upload, audio playback or aircraft action. No new models/dependencies.

Adjacent qualified storage after this fix and concept deck: **9,320,832,945B**, below
10GB aim/15GB cap. Includes the external generated cover1,576,960B once and its
workspace copy once. Historical missing-archive/cache/inventory limitations stay
in force. Goal active: attended filming/AirPods, iQOO/NPU, Office Kit, useful unseen
direction and eligible/accepted competition delivery remain open.
