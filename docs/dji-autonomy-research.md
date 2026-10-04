# DJI autonomy repository review

Reviewed 4 October 2026 (IST). Pre-event research only. Public documentation and
source inspection; no dependency installation, hardware connection or flight test.

## Decision

Programmatic DJI flight already exists. For the user's Mini 4 Pro, Android MSDK
V5 is the relevant official interface. Repeatable missions and continuously
updated Virtual Stick control are distinct options. An AI camera director would
still require validated perception, a bounded executor and pilot takeover; the
repositories reviewed do not establish a working unattended fashion-filming
system on our hardware.

Start with the existing [read-only bench gate](mini4-connection-test.md). No flight,
gimbal movement, recording or waypoint upload is added to that gate by this review.

## Official hardware and interface evidence

[DJI release notes](https://developer.dji.com/doc/mobile-sdk-tutorial/en/) list
Android MSDK 5.18.0 (22 May 2026). The Mini 4 Pro reference row is aircraft
01.00.1100, RC-N2 01.01.0300 and RC-N3 01.01.0300. These are published versions,
not measured user firmware. RC 2 is not in this SDK row. Do not assume a built-in
screen controller, iPhone DJI Fly installation, Neo or arbitrary firmware works
with the Android adapter; confirm exact physical labels and versions first.

[Virtual Stick API](https://developer.dji.com/api-reference-v5/android-api/Components/IVirtualStickManager/IVirtualStickManager.html)
provides software stick commands and advanced control; DJI recommends a 5–25 Hz
send frequency. A deterministic controller must handle timing and stale input,
separately from any slower model. The same API explicitly lists supported
Virtual Stick obstacle avoidance for enterprise models and does not include
Mini 4 Pro. Do not infer collision avoidance from the aircraft's normal DJI Fly
features. Authority-change events include pilot pause/mode switch, RC loss and
battery-driven return/landing; actual takeover and loss behavior require tests.

[Waypoint manager](https://developer.dji.com/api-reference-v5/android-api/Components/IWaypointMissionManager/IWaypointMissionManager.html)
provides mission upload/execution APIs. Availability of a generic method is not
an airframe-specific result. As independent product evidence,
[Dronelink's own support page](https://support.dronelink.com/hc/en-us/articles/39718021407379-Mini-4-Pro-Support-Overview)
documents Mini 4 Pro Virtual Stick missions and onboard waypoints with RC-N2/N3
and 64-bit Android; it excludes RC 2 and iOS. This is vendor-reported support,
not a test performed here. Its reported startup preview delay and false warnings
also show why telemetry/preview behavior needs bench verification.

## Repository shortlist

| Source | Inspected evidence and possible use | Limits |
| --- | --- | --- |
| [DJI Mobile-SDK-Android-V5](https://github.com/dji-sdk/Mobile-SDK-Android-V5) | Official supported-product list, registration/preview samples and actual Virtual Stick API calls. Best adapter reference. | Sample source is MIT; SDK binaries have separate terms. Full sample includes flight controls, so use selected interfaces in an isolated read-only probe. |
| [fcsonline/droneroute](https://github.com/fcsonline/droneroute) | TypeScript planner for waypoint/orbit shots. Source creates KMZ archives containing template.kml and waylines.wpml; CLI copies a mission to controller storage. | MIT. README lists Mini 4 Pro, but no test performed here. File generation/copying is not live visual tracking or official RC 2 SDK support. Do not run its uploader in the first test. |
| [SDU-UAS-Center/lyrebird](https://github.com/SDU-UAS-Center/lyrebird) | Android MSDK5 bridge with HTTP/MAVLink/ROS2. VirtualStickVM calls actual DJI control APIs and observes RC input for manual override. Useful later architecture reference. | Source available under BSL 1.1, scheduled MIT conversion on 31 August 2028. README lists Mini 4 Pro, but cited field demonstrations use Mini 3 and enterprise aircraft. No verified Mini 4 Pro filming result here. |
| [WUR-ABE/MavDrone](https://github.com/WUR-ABE/MavDrone) | MSDK5-to-MAVLink bridge with Python/ROS2 clients. Mission code calls DJI's native KMZ upload API. | AGPL-3.0-or-later. Authors mark only M300/M350 tested; Mini 4 Pro is an SDK-compatibility claim. Authors do not plan active development. |
| [WildDrone/WildBridge](https://github.com/WildDrone/WildBridge) | Earlier MIT bridge with HTTP telemetry/control, PID loops and explicit Mini 4 Pro control profile. | Lyrebird continuation is separately licensed. Hardware-specific detection and payload features cannot be transferred to Mini 4 Pro. Unknown-aircraft fallback to an enterprise profile needs review before any reuse. |
| [AndreasLabs/format-wpmz](https://github.com/AndreasLabs/format-wpmz) | TypeScript KML/WPML serialization examples. Potential mission-format reference. | Writer, not transport/control. No Mini 4 Pro hardware validation or license clearance established in this review. |
| [siokas/tellots](https://github.com/siokas/tellots) | TypeScript takeoff/control wrapper for Tello. | MIT, but a different aircraft/protocol; not a Mini 4 Pro control solution. |

The supplied [TypeScript topic](https://github.com/topics/dji?l=typescript&o=desc&s=updated)
also contains logs, panorama viewers and color tools. Language/topic membership
does not establish aircraft-control capability. Expand beyond TypeScript for the
Android hardware layer. A TypeScript shoot-planning UI is possible; live control
would pass through the Android SDK adapter or a separately validated bridge.

## Reproducible source anchors

Official sample inspected at commit `a48aa4e7811d824c27abfa973f5655579bfb8a77`:
[VirtualStickVM.kt](https://github.com/dji-sdk/Mobile-SDK-Android-V5/blob/a48aa4e7811d824c27abfa973f5655579bfb8a77/SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/models/VirtualStickVM.kt)
contains enable/disable and advanced parameter calls.

DroneRoute inspected at commit `2b27ac2d9ff9647bdae3fad9da807bd59d3ef646`:
[KMZ generator](https://github.com/fcsonline/droneroute/blob/2b27ac2d9ff9647bdae3fad9da807bd59d3ef646/packages/backend/src/services/kmzGenerator.ts)
and [uploader](https://github.com/fcsonline/droneroute/blob/2b27ac2d9ff9647bdae3fad9da807bd59d3ef646/packages/cli/src/upload.ts).

Bridge source paths reviewed:
[Lyrebird VirtualStickVM](https://github.com/SDU-UAS-Center/lyrebird/blob/main/LyrebirdApp/lyrebird-app/src/v5/java/com/lyrebird/rc/models/VirtualStickVM.kt)
and [MavDrone waypoint implementation](https://github.com/WUR-ABE/MavDrone/blob/main/app/src/main/java/nl/wur/mavdrone/communication/components/impl/mission/WaypointMission.kt).

## Product implications and remaining checks

After the bench gate, a separately approved supervised shot executor could
support fixed routes/orbits or bounded position/yaw/gimbal adjustments. A live
framing loop is a research candidate: preview → validated subject/framing estimate
→ bounded requested adjustment → pilot-authorized deterministic executor.
No visual accuracy, latency, Android inference or flight reliability is measured.
Generic SDK support does not establish programmable ActiveTrack on Mini 4 Pro.

Before a later flight experiment, validate action bounds, pilot takeover,
disconnect/stale-frame behavior and the exact aircraft/controller/SDK combination.
Retain vendor safety behavior. Do not adopt bridges wholesale: they expose much
broader control and network services than the initial product requires.

No repositories were cloned and no SDKs, model weights or private media were
downloaded/uploaded. Public source was read in memory. Active workspace measured
171,549 logical bytes before adding this note (including Git and ignored files).
Retained archive and shared-cache figures remain the historical status records;
the archive location is unresolved, so a current complete storage total cannot
be certified. This review adds small Markdown files and no dependency cache growth.
