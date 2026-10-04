# Multicamera and Neo 2 bridge research

Reviewed 4 October 2026 (IST) against the user's pasted ChatGPT conversation.
This is source review for pre-event preparation, not a working integration or
hardware test. Sources below are live pages; pin exact revisions and review
licenses before adopting code. No SDK/model downloads, installations, private
uploads, firmware changes or hardware commands were performed for this review.

## What the research adds

The expanded objective is credible as a staged **phone-led multicamera film
director**: understand an explicitly started talking/performing shoot, suggest
framing and poses, propose camera coverage, coordinate separately approved
capture, and assemble reviewed cuts around continuous phone audio. A fixed
Action 4 side view is a useful optional third perspective. It is proposed
equipment in the pasted conversation, **not confirmed owned**. Neo 2, RC-N3 and
Nothing Phone (3a) are user-confirmed; iQOO access and exact model remain unknown.

Camera selection for an eventual edit, camera recording and aircraft movement
are different operations. Selecting `DRONE_WIDE` does not establish that the
aircraft can reach that viewpoint, that recording started, or that simultaneous
footage exists. Track requested, observed, approved and completed states
separately. The model proposes; permissions, freshness, bounds and human review
remain deterministic gates.

## DJI interfaces: evidence and limits

| Route | Primary evidence | Implication for this project |
| --- | --- | --- |
| Mini 4 Pro Mobile SDK | [DJI V5 repository](https://github.com/dji-sdk/Mobile-SDK-Android-V5) lists Mini 4 Pro; the project already records [DJI's compatibility table](https://repair.dji.com/help/content?customId=01700000763&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17) distinguishing it from SDK-unsupported Neo/Neo 2 | Preserve the independent propellers-off, read-only Mini 4 Pro + RC-N3 connection/telemetry/preview milestone. Support documentation is not our hardware result. |
| Neo 2 through DJI Fly | [DJI Neo 2 FAQ](https://www.dji.com/neo-2/faq) documents Wi-Fi mobile control, live view and vendor shooting modes | Vendor filming and selected-clip import are viable objectives. DJI describes mobile virtual sticks as position fine-tuning/return controls, with a different experience from a traditional remote. Do not assume full RC-equivalent control or SDK access. |
| Action 4 official BLE candidate | [DJI Osmo-GPS-Controller-Demo](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo) and its [R SDK protocol documentation](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/protocol.md) explicitly include Action 4 and document third-party camera control/status | Prefer investigating this official protocol before reverse-engineered commands. The sample runs on ESP32-C6; platform-independent protocol code is not a tested Android library. Recording, mode, version and status are candidates; preview/media transport are separate. |
| Action 4 unofficial Android control | [dimadesu/dji-remote](https://github.com/dimadesu/dji-remote) reports Action 4 tested by its author and ports Moblin BLE control | Its documented workflow is RTMP configuration and start/stop **livestreaming**. This alone does not verify local SD-card recording, our phone integration, multicamera operation or offline-only execution. |
| Multiple Action cameras | [rhoenschrat/DJI-Remote](https://github.com/rhoenschrat/DJI-Remote) lists Action 4 and up to three cameras, including record control/status | This is an ESP32/ESP32-S3 remote project. It supports multicamera feasibility as an independent author report, not three simultaneous Android connections or synchronized frames. |
| Media offload | [KonradIT/Osmosis](https://github.com/KonradIT/osmosis) marks Action 4 hardware-verified and Neo 2 QuickTransfer support as started | Relevant to media import research. Neo 2 QuickTransfer development is not flight control, and Action 4 media offload does not prove coordinated capture. |

The [official R SDK getting-started guide](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo/blob/main/docs/getting_started_guide.md)
describes BLE pairing/authentication and warns that an established BLE connection
occupies the camera channel so other BLE devices cannot connect. Do not assume
our adapter can share that channel with another remote or microphone. The
[DJI GPS remote FAQ](https://repair.dji.com/help/content?customId=01700008289&lang=en&paperDocType=ARTICLE&re=US&spaceId=17)
does document a BLE remote alongside a phone's Wi-Fi Mimo connection; that does
not demonstrate two BLE clients or two camera Wi-Fi feeds on one phone.

## Neo 2 Accessibility hypothesis

Android exposes [AccessibilityService gesture dispatch](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler))
with declared gesture capability and user enablement. This proves an Android
input mechanism exists. It does **not** prove DJI Fly accepts it reliably,
exposes usable telemetry nodes, tolerates overlays, or retains neutral stick
state on cancellation, layout changes or process/link failure. Android documents
that dispatching a gesture cancels gestures already in progress, including the
user's: successful synthetic touch is not evidence of safe manual takeover.
Android's [service guide](https://developer.android.com/guide/topics/ui/accessibility/service)
also frames this API as an assistive tool for users with disabilities, not a
general app-integration API; distribution suitability requires separate review.

Keep this as a separate, **mock-only first** research track. An own-app fake
two-stick screen can test simultaneous strokes, cancellation, coordinate changes,
state expiry, service disablement and a user stop/takeover control without a
drone. Restrict any service package scope to that mock during this stage. A later
Fly observation or action trial needs its own reviewed scope, exact app/firmware
versions and action-level authorization. Do not enable Accessibility, inject
Fly gestures or issue drone commands as part of an objectives update. Stopping
the bridge means stop dispatching; calling an unverified `HOVER` or `LAND` route
is itself an aircraft action.

The original [dji-neo2-tools](https://github.com/linnin233/dji-neo2-tools)
repository advertises USB flight-controller parameter reads/writes, not an
independent live-piloting application. [FreeFCC](https://github.com/doesthings/FreeFCC)
advertises Neo 2/RC2 regulatory radio changes; [DJI-FCC-Unlock-iOS](https://github.com/andreapianidev/DJI-FCC-Unlock-iOS)
reports original Neo + RC-N3 testing, not Neo 2 Android piloting. These projects
do not close the flight-control gap. Their protection/radio/firmware alterations
are outside this project's scope; preserve vendor safety behavior. The pasted
claim of a Neo 2 researcher extracting live UI telemetry could not be tied to an
original reproducible project in this bounded search. No complete independent
Neo 2 flight-control implementation was verified here; that is a search result,
not proof none exists.

## Android and test order

[Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
depend on target/OS version; Android 12+ scanning/connection permissions require
normal runtime approval. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
define connected-device, camera and microphone requirements. Camera/microphone
services have while-in-use start restrictions. A user-started visible shoot may
support later background continuation, but this needs actual device/lifecycle
testing; adding a `DirectorService` does not grant blanket background access.

Neo Wi-Fi control plus Action Wi-Fi preview/offload can compete for networking.
[WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager#isStaConcurrencyForLocalOnlyConnectionsSupported())
provides a device-specific local-only concurrency check; availability does not
guarantee this exact two-camera topology. Prefer testing small BLE camera control
independently from Wi-Fi media transfer, then measure phone camera/audio + BLE +
drone link + inference/thermal contention. Queue offload until after a take if
concurrency fails. Live camera switching and later edit cuts remain distinct.

1. Preserve the zero-action Mini 4 Pro probe, independent of models and Fly.
2. Test mock director choices and own-app Accessibility mechanics without hardware.
3. If Action 4 becomes available, inventory exact firmware and existing pairings;
   assess licensing and implement/read back identity/status before capture trials.
4. Separately authorize and validate short camera recording, state acknowledgement,
   disconnect behavior and imported original clips; test one camera before multiple.
5. Measure visible Android session concurrency and local model/backend performance.
6. Align consented originals with a visible sync cue, allow manual offsets/drift
   correction, and validate editable cuts/export. BLE start acknowledgements do not
   prove frame synchronization.
7. Consider bounded aircraft actions only after pilot approval, validated limits
   and tested independent takeover/failure behavior; no initial flight action.

Exact Neo/Mini/remote/camera firmware, DJI Fly version, Android version, Action 4
availability, BLE pairing behavior and iQOO model are still unknown. No official
R SDK firmware minimum was established from the reviewed pages. Record the
actual versions without silently upgrading/rebinding equipment. Model training,
Android output parity and NPU execution each remain separate evidence gates.
Keep all prototypes under `prototype/`, private media/keys/weights out of Git and
uploads opt-in. Count archive and dependency/cache growth within the existing
below-10-GB aim/15-GB ceiling; this review introduced only a Markdown document.
