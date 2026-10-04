# Synthetic interface and Files evidence — 4 October 2026, 17:54 IST

This is pre-event research. A fresh, separate Android 36 / arm64-v8a emulator
ran the latest interface checks while the physical Nothing Phone remained locked.
Emulator execution does not establish a live creator shoot, AirPods, iQOO/NPU,
Office Kit, eligibility or accepted submission.

## Test environment and retained failures

Reused installed emulator 36.5.11.0 and the existing Android 36 Google APIs ARM64
system image. Created an empty AVD in ignored project storage; did not start,
copy or modify the other existing project's AVD. Camera front/back were disabled,
host audio disabled, snapshots disabled, RAM 2048 MB and two cores. Own data
partition was set to 1024 MB. Fresh keyguard was not showing; CAMERA and
RECORD_AUDIO remained denied. Airplane mode was enabled on this synthetic AVD.
No model weights were copied, inference requested, audible playback started,
private media read/uploaded or permission granted through debugging tools.

Boot completed in 27.587 seconds. The selected console/ADB port produced an
outside-recommended-range warning; the observed connection and all runners
completed. This warning is retained in emulator-boot.log, not treated as success
without the actual boot and runner reports. AVD management follows the existing
installed tool and [official AVD guidance](https://developer.android.com/tools/avdmanager).

Initial results are retained:

| Runner | Initial result | Cause and correction |
| --- | --- | --- |
| VoiceBriefUiTest | 6 methods / 2 failures / 14.228 s | Synthetic clicks occurred in the same main-loop event as dialog show, before queued custom OnShow listeners. Separate presentation, idle synchronization and button interaction. |
| ReferenceSpeechUiTest | 7 methods / 3 failures / 10.413 s | Same scheduling problem. Corrected successful and stale-dialog cases to exercise the installed custom listeners. |
| Nine non-media UiWorkflowTest methods | 9 methods / 1 failure / 11.360 s | The Files ActivityMonitor lacked the production application/zip MIME type, so it missed the intent. Match the actual MIME type. |
| Isolated synthetic demo | 1 passed / 3.845 s | Three AVC fixtures reached Assemble and opened paused preview, with capture off. |

[AOSP Dialog](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Dialog.java)
queues the show listener. [AlertController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/app/AlertController.java)
provides the initial button handler. Android's
[IntentFilter documentation](https://developer.android.com/reference/android/content/IntentFilter)
requires matching data/type as well as action/category. These were test setup
repairs; review limits, explicit confirmation, foreground/source/generation
checks and original assertions were not weakened. No production review bug is
claimed from those initial failures.

## Current verified outcome

| Latest sequential runner | Passed | Seconds |
| --- | ---: | ---: |
| VoiceBriefUiTest | 6 | 7.843 |
| ReferenceSpeechUiTest | 7 | 10.643 |
| AssemblyEditUiTest | 2 | 4.049 |
| Nine non-media UiWorkflowTest methods | 9 | 10.766 |
| Isolated synthetic demo | 1 | 2.246 |

**25 interface methods passed.** Voice/reference checks retain full text,
explicit corrections, 500-character/composed-input bounds, overflow rejection,
background/cancel/source invalidation and stale callback ownership. Fake recorder
checks do not establish microphone encoding or loaded-model release timing.

The new real trim/subtitle dialogs use app-scoped Accessibility actions on tiny
labelled non-media fixtures. They retain the exact 5.746-second end; reject
5.747, zero, negative and 249-ms trim bounds without applying edits; preserve
complete title/caption/cues; retain selected c/a/b order across recreation; and
invalidate a prepared ZIP after trim changes. Subtitle edits reject invalid
source times and keep the complete reviewed words. All preference types are
restored and equality checked. Only owned tiny fixtures are removed after
original byte/mtime checks and ActivityScenario closure.

Production usability changes: Trim & typography is scroll-wrapped; saved screen
is range-checked 0–2 and restored. Direct restoration keeps capture/countdown/
sequence inactive, and Assemble restoration keeps the ready-package action.
Import/reference-summary callbacks assign their destination screen before save.
No live session or pending review is restored. Independent source review found
no blocking issue.

Manual inspection on this fresh Pixel 9-sized emulator showed the caption field,
other trim fields, Cancel and Save above the open keyboard. Brief and Direct
screens were also inspected; Start camera and Stop take remain fixed controls.
This is one observed size/density, not an all-screen or physical-phone layout test.

## Actual Android Files save, beyond intent interception

Through the app's normal controls, generated three synthetic demo clips, selected
Save clips + edits for my laptop, and used the real DocumentsUI SAVE action in
the fresh empty Downloads folder. The app reported completion. Independently
pulled the resulting ZIP and verified CRC, manifest, timeline and each current
synthetic original byte-for-byte:

- ZIP: **614,367 bytes**, SHA-256
  **7f14ae3081083f01bfec89481804f7eb4e69fba58ca8cf0fc04115d6ac335823**.
- Three whole originals: **610,506 bytes total**; each packaged length/hash matches
  its manifest and actual app original. The save did not delete source media;
  later cleanup removed the own temporary AVD and generated fixtures.
- Three 0.500–2.500-second cuts, contiguous 0–2–4–6-second timeline;
  preEventResearch=true, manual captions, relative media paths.

This passes one real emulator DocumentsUI save. Physical Files/providers,
revoked access, desktop-editor compatibility and Office Kit remain separate.
It does not prove an MP4 export or audible review of those clips.

## Installed checkpoint, storage and reproduction

Both APK builds succeeded in 1 second. App/test installed on the emulator and
physical phone. Built, saved and independently read physical installed app match:
**52,838,059 bytes**, SHA-256
**b0d67a683ac1ed5c38e99dc81bb317237d589c2c4e1778572946164ed589ac86**.
Third-party notices are unchanged. Separate Qwen/Whisper weights remain outside
Git/APK; bundled ML Kit dependency assets remain. No INTERNET or
ACCESS_NETWORK_STATE permission. Physical CAMERA/RECORD_AUDIO stayed denied;
keyguard stayed showing. A normal launch was requested, not an attended launch.
The metadata-only physical gallery inventory passed one method / 0.499 s:
19 owned rows, zero pending, 5,598,886 logical bytes.

Qualified temporary accounted peak was **10,507,375,535 bytes**, above the 10 GB
aim and below the 15 GB cap. Stopped and removed only this newly created AVD and
its registration; retained evidence. After cleanup: **9,291,985,083 bytes** (snapshot before this evidence commit).
No new SDK/JDK/system-image/model downloads. Pre-existing installations are
excluded; historic archive/cache reserves and incomplete-inventory qualifications
in [storage.json](storage.json) remain. Other AVD, archive and model files were
untouched.

For reproduction, create a separate empty AVD with the existing image and flags
above; use an available recommended port. Install the built app/test APKs without
-g. Confirm unlocked synthetic keyguard and denied CAMERA/RECORD_AUDIO. Run
VoiceBriefUiTest, ReferenceSpeechUiTest and AssemblyEditUiTest sequentially with
AndroidJUnitRunner; select the nine non-media UiWorkflowTest methods separately
from UiWorkflowTest#syntheticDemoReachesEditableAssemblyWithoutActivatingShoot.
Use the explicit emulator serial throughout. Do not run behind physical keyguard,
run the entire test APK unattended, reuse someone else's AVD data, or copy models
for these checks. For Files, generate demo clips from Assemble and explicitly
save to an empty emulator folder, then verify ZIP/source bytes as above.

Ignored local evidence: emulator-{voice,reference,workflow}-ui.log retains initial
failures; corresponding -corrected.log files hold latest reports;
emulator-assembly-ui.log and emulator-synthetic-demo-ui-final.log hold the other
passes. emulator-files-save-proof.json, emulator-files-saved.zip, scoped UI XML/
screenshots, emulator-ui-{build,apk,installed-apk,final-storage} evidence,
emulator-ui-phone-gallery-test.log, emulator-boot.log and emulator-cleanup.log
retain the observed checks. See [completion audit](completion-audit.md) for open
attended/hardware/event gates. The goal remains active.
