# Mini Film Director delivery plan

Updated 4 October 2026 (IST). This is pre-event research, not an eligible build.

**Promise:** an Android phone acts as a multicamera film director while the
creator talks or performs: interpret a spoken brief, suggest poses/framing,
coordinate approved phone/drone and optional Action 4 shots, then propose an
aligned editable reel. Daily fashion is the first scene; walking/talking stories,
product reveals and introductions use the same workflow. See the
[expanded objectives](docs/objectives.md) and [interface research](docs/multicamera-research.md). Read the
[full vision and equipment decisions](docs/vision.md). Guided coverage review is
the first useful delivery toward that vision.

Android is required for the app and local inference. Use the user's Nothing
Phone (3a) for development and iQOO for final validation. The
[product specification](docs/product.md) consolidates the creator workflow.

## Immediate delivery revision — 4 October 2026

The latest user instruction makes the phone director the primary delivery and
drone integration secondary. Build and test a dated research prototype now:
**Pose → Perform → Assemble**, with editable local model plans, optional local
reference observations, short earbud-friendly direction, explicit CameraX takes,
offline English subtitle drafts and reviewed local Media3 export. The user has
authorized ADB installation/launch and parallel agents while away. Normal
Android permission UI still applies; unattended verification uses generated
fixtures rather than recording private surroundings.

Phone work no longer waits for the drone probe. The independent Mini 4 Pro
probe retains its propellers-off, read-only bounds. Optional drone clips can be
imported; no DJI connection/control is implied. Nothing Phone development, actual
iQOO execution, Qualcomm NPU, AirPods routing, Office Kit and accepted submission
remain separately evidenced gates. Today's implementation remains pre-event
research; event-created competition source and any organizer reuse approval
must be handled separately. See [current build evidence](docs/status.md).

## 5 October revision — live spoken shot direction

The returned creator requests live audible Action/framing/shot/Cut cues during
recording, through speaker or selected Bluetooth media output, plus a quiet
dialogue option. This is now a required phone-first acceptance item. See the
[updated objectives](docs/objectives.md). Validate cancellation/ownership and
real offline TTS playback separately from human audibility, Bluetooth identity
and attended camera/audio coexistence. The existing slow Qwen CPU planner
prepares the plan; bounded local pose/timer cues must not wait for that model.

## Milestones

| Order | Deliverable | Acceptance gate |
| --- | --- | --- |
| 0 | Recoverable FocusPilot archive and fresh scope | Backup inventory/digests verified, local credentials preserved, retained storage measured; documented by migration owner |
| 1 | Mini 4 Pro connection probe | Exact remote label recorded, app registered, Mini 4 Pro identified, fresh battery/connection telemetry, changing live preview and unplug/reconnect behavior; no flight/capture commands |
| 2 | Editable shoot plan | One actual fashion brief becomes five bounded shots with phone/drone assignment; creator can revise/reorder; template/manual fallback visibly identified |
| 3 | Guided shoot and clip import | Next-shot guidance, manual completion and user-selected clips work without depending on autonomous flight |
| 4 | Coverage and framing review | Evaluate unseen consented clips against creator labels; report useful findings, false alarms, unknowns and review time; sampled frames do not prove every-frame temporal analysis |
| 5 | Laptop shoot pack | Export selected media, shot order and notes; prove actual Office Kit transfer separately from file creation or a generic share action |
| 6 | iQOO demo and event delivery | Repeatable complete workflow on required hardware, truthful model/backend evidence, permitted drone demo, event-created competition implementation and accepted submission |

Milestone 0 records migration-time verification. The full archive is now missing
from its saved path; current complete recovery remains unresolved in
[status](docs/status.md). No new documentation work establishes its availability.

The user confirms Neo 2 and RC-N3. DJI currently lists Neo/Neo 2 as SDK-unsupported;
Neo 2 DJI Fly experiments/imports are a vendor-controlled path. Mini 4 Pro + RC-N3
remains the official SDK integration target. A Neo 2/Fly Accessibility bridge is
a separate experimental route; its first test uses mock controls with no aircraft
connected. Action 4 is optional equipment with ownership/access unconfirmed.
The research conversation does not establish working control on either device.

## Full-vision technical gates

After the independent probe, add explicitly started phone camera/audio capture
and timestamped drone preview, then local visual/speech perception in advice mode.
Evaluate the tiny framing head and a compatible Kev checkpoint fine-tune on
separate whole shoots. Verify Android outputs before NPU compilation; measure
the actual backend and fallback on each phone.

With action-level pilot approval and tested bounds/takeover/failure behavior,
add SDK capture and one bounded control action at a time. The NPU accelerates
inference; it does not dispatch SDK calls or stabilize flight. Align original
phone/drone recordings to phone master audio, produce a reviewed cut list and
export a short reel. Manual Fly capture, model loading and template storyboards
do not pass these gates. See the detailed order in [the vision](docs/vision.md).

First technical work is milestone 1, described in
[`docs/mini4-connection-test.md`](docs/mini4-connection-test.md). No SDK or model
installation is established by this plan. Keep the probe independent of any AI
runtime so model failures cannot hide connection failures.

## Expanded director milestones

These follow the independent read-only probe and useful fashion plan/review path;
they do not enlarge milestone 1 or require every camera for the first demo.

| Order | Addition | Acceptance gate |
| --- | --- | --- |
| A | Voice brief and posing/framing advice | Explicit session start/stop, offline speech evidence or typed fallback, creator edits, measured useful cues and quiet mode |
| B | Shoot-specific app bridge bench prototype | Allowlisted mock UI, user-enabled Accessibility, continued/multitouch gestures, cancellation and layout/focus failures measured; no aircraft connected |
| C | Optional Action 4 adapter | Confirm access/model/firmware; pin protocol/implementation/license; separately test status, approved record/stop and resulting original clip; preview/import are independent |
| D | Multicamera feasibility | Phone capture/audio plus BLE and chosen drone route measured together; no assumed dual-Wi-Fi or shared Fly/SDK connection; sequential/manual fallback |
| E | Learned next-shot decisions | Perception/speech → structured state → bounded shot choices; held-out quality/calibration, Android parity and actual backend evidence |
| F | Approved capture/control | Each aircraft action confirmed with bounds and tested takeover/failure behavior; Neo 2 bridge feasibility separate from supported Mini 4 Pro SDK evidence |
| G | Editable multicamera reel | Original recordings aligned to phone audio, reviewed cuts/offsets/drift, real Android export/playback and separate Office Kit transfer |

The same-phone target needs concurrency/lifecycle testing while DJI Fly is in the
foreground. The laptop may train models but should not be required at shoot time.
Unknown observations remain unknown; model confidence is not aircraft safety
clearance. DJI Fly joystick/UI success would not establish reliable autonomous
following, a safe orbit or all other integration gates. Reverse-engineered Neo 2
protocol work is long-term research, outside the first delivery.

## First complete story

Brief: “20-second streetwear reel, editorial mood, emphasize the jacket.”
Plan: outfit hero, jacket/fabric detail, walking movement, side silhouette and
wide establishing shot. The creator approves the plan and chooses equipment.
Deliberately omit the detail shot, import consented clips, ask what is missing,
capture the recommended pickup and export a reviewed sequence and edit notes.
This is a proposed test, not a measured success.

Use a small manually labelled sample before selecting a vision model. Assess
whether the full outfit/detail is visible, framing is usable and requested
coverage exists. Keep blur/exposure heuristics distinct from semantic vision.
No old command-model accuracy or speed is carried over to these tasks.

Proposed model work: pretrained visual measurements → small learned framing
suggestion head, trained on the laptop and evaluated locally on Android. See
[model design](docs/tiny-director-model.md) and
[source-backed candidates](docs/vision-research.md). No model has been trained
for this product; any Kev comparison must distinguish text-state decisions from
visual perception and an unverified Android port.

## Stop/go decisions

If DJI registration or hardware access fails, record the exact failure and
continue the useful phone/manual-drone shoot workflow with imported clips.
Do not present imported footage as live SDK capture. If a small model fails
quality/latency gates, retain editable manual planning and disclose the gap;
do not present a template as generated AI. Broader autonomous flight and a
full video editor are later scope decisions, after the first useful workflow.

## Storage and event order

Archive bytes remain in the budget. Measure active files, archive, project cache
growth and temporary copies before each large SDK/model/media addition; aim
below 10 GB, hard ceiling 15 GB. Prefer one pinned runtime/model and short proxy
clips over duplicated originals. Never reclaim the only verified backup.

Before the event: research interfaces, bench feasibility, evaluate small consented
examples and prepare the concept. During the allowed window: create fresh
competition source, prove one workflow, validate on iQOO, demonstrate actual
Office Kit use, rehearse and submit with receipt. Respect the organizer's
Red/Green Light schedule and confirmed cutoff. Prior draft application assets
must be reviewed for the new concept before any submission.
