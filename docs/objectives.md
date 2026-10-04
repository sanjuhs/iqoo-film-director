# Mini Film Director — project objectives

Current implementation evidence is maintained in [status](status.md) and the
[completion audit](completion-audit.md). The design/proposal statements below
retain their original planning context: a phone research prototype and synthetic
local model/media checks now exist, while the full attended multicamera, trained
director and event-hardware vision remains unverified.


Updated 4 October 2026 (IST) from the user's supplied ChatGPT research and request
to broaden the project. These are product objectives and research gates, not
completed capabilities. [Source checks](multicamera-research.md) distinguish
documented APIs, third-party reports and untested integration ideas.

## The product we want

An Android phone acts as a local film director for a solo creator: understand a
shoot brief, suggest framing and poses, coordinate available cameras, recognize
useful moments during a performance, and propose an editable reel. Daily fashion
reels remain the first workflow. Walking-and-talking stories, product reveals
and a short hackathon introduction are additional scenes using the same system.

Example brief: “Make a 30-second jacket reveal. Start close on the phone, suggest
a pose, get a side angle if an Action 4 is available, and suggest a wide drone
shot when I start walking. Let me finish speaking before giving another cue.”

The creator edits the brief, shot list and final cuts. A creative request does
not authorize aircraft movement. A phone-only shoot must remain useful when an
external camera, local model or connection is unavailable.

## Objectives and evidence

| Objective | Intended experience | Acceptance evidence |
| --- | --- | --- |
| Voice-led shoot | Explicitly start a session; speak a brief, request another angle, pause or end the session | Local speech availability/offline test, editable interpreted intent, visible microphone state and working stop; typed fallback |
| Framing and posing coach | Concise cues such as turn toward the light, hold a pose or adjust composition, with a quiet mode while speaking | Creator-labelled unseen shoots, measured useful/incorrect cues and suppression during speech; no invented distance or light estimate |
| Multicamera coordination | Assign close, side and wide shots to the phone, optional Action 4 and drone; coordinate approved recording where tested | Per-device capability/state evidence, recording acknowledgements and originals; manual capture/import when control is unavailable |
| Story-aware direction | Use speech cues, current framing, shot duration and existing coverage to suggest the next shot | Held-out comparison with an editable checklist/rules; clear reasons, creator overrides and abstention |
| Bounded learned decisions | Compare a tiny framing head and Kev-style shot selection; separate occasional language interpretation from repeated decisions | Useful laptop evaluation, faithful Android output parity and actual measured backend; no fixed model count/size promised |
| Optional Neo 2 app bridge | Investigate a shoot-specific Android service communicating with DJI Fly through accessibility | Mock UI first; exact Fly/phone/firmware validation and separate pilot-approved hardware gates; no official SDK or working-flight claim |
| Editable assembly | Align original recordings to phone master audio and propose deliberate camera cuts | Reviewed source/in/out list, measured offset/drift, correct Android export and playback; editable manual alignment |
| Phone-first demonstration | Run the useful workflow on iQOO without requiring a laptop at shoot time | Offline model execution and concurrent capture measured on the actual iQOO; real Office Kit transfer tested separately |

Training may happen on the laptop. “Everything runs locally on the phone” is a
future inference/capture claim requiring evidence for each component, including
speech. Connection/activation requirements remain distinct from offline AI.

## Camera integration routes

| Route | Role | Current status |
| --- | --- | --- |
| Phone camera | First capture source and master audio | Android development on confirmed Nothing Phone (3a); product unbuilt |
| Mini 4 Pro + RC-N3 / MSDK | Official custom preview/telemetry, then separately approved capture/control | First technical milestone remains the propellers-off read-only probe |
| Neo 2 / DJI Fly | Vendor filming and selected original clip import | Aircraft/remote labels confirmed; physical connection and firmware unverified |
| Neo 2 / accessibility to Fly | Optional unofficial app-to-app experiment | Android gesture APIs are an enabling interface, not proof of Neo 2 control |
| Action 4 / BLE | Optional third camera; investigate direct recording/status control without app switching | DJI R SDK protocol/demo lists Action 4; ownership/access and Android integration unconfirmed |
| Undocumented Neo 2 protocol | Track relevant public transport/media research | Long-term investigation, outside the first delivery; no parameter writes, firmware changes or vendor-safety bypass |

Prefer the [official DJI R SDK BLE protocol/demo](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo)
as the Action 4 research starting point; its ESP32 example is not an Android
result. Action 4 recording control, preview, media transfer and synchronization
are separate capabilities. BLE channel occupancy/authentication must be tested;
do not assume coexistence with another Bluetooth camera controller. Bluetooth control does not establish a live video feed.
Do not assume the phone can join two camera Wi-Fi networks at once, or that
DJI Fly, phone capture, local inference and BLE all run concurrently. Test each
combination and preserve a sequential/manual workflow.

## Director state and bounded choices

Track intent/style/length; subject framing, pose and speech state; available
cameras and their verified capabilities; current shot length and coverage;
input source, confidence and monotonic timestamp; and creator/pilot approvals.
Unknown depth, clearance, recording status or flight state stays unknown. Screen
OCR is an estimate with provenance, not authoritative aircraft telemetry.

For example, an illustrative advice-only state could be:

```json
{
  "session": {"active": true, "mode": "advice_only"},
  "goal": {"scene": "talking_jacket_reveal", "duration_sec": 30},
  "subject": {"speaking": true, "framing": "medium", "depth_m": null},
  "cameras": {
    "phone": {"recording": true, "source": "camera_callback"},
    "action4": {"available": null, "recording": null},
    "neo2": {"connected": null, "airborne": null}
  },
  "coverage": {"accepted": ["phone_medium"], "missing": ["detail", "wide"]},
  "permissions": {"aircraft_action_approved": false},
  "candidate_choices": ["KEEP_SHOT", "SUGGEST_DETAIL", "SUGGEST_WIDE", "REQUEST_REVIEW"]
}
```

This is synthetic design data, not captured device evidence. In implementation,
every observation also needs its own source, confidence and freshness metadata.

Begin with KEEP_SHOT, SUGGEST_DETAIL, SUGGEST_SIDE, SUGGEST_WIDE, HOLD_POSE,
REQUEST_REVIEW and PROPOSE_EDIT_CUT. Add tested camera record/stop operations to
an executor separately. Follow, orbit, reveal and pull-back are desired shot
descriptions; they enter aircraft execution only through approved bounded actions.
An edit cut selects recorded media; it does not reposition a camera.

Kev receives a structured text state after separate perception/speech processing.
It is a Jev-like decision framework, not a demonstrated Android port or a model
built on Jev. Compare rules and the tiny head before committing storage to a
checkpoint. Probability thresholds require task calibration; a score above 0.7
does not prove safe flight. Low confidence requests review or suspends proposals;
hover/landing behavior requires a separately tested aircraft-specific contract.

## Delivery boundaries

First prove the Mini 4 Pro read-only connection, independently of AI. Then deliver
the editable fashion plan/import/review workflow. Add opted-in speech and posing
advice, multicamera capture feasibility, measured local shot decisions, and aligned
assembly in that order. See [delivery plan](../plan.md) for acceptance gates.

Accessibility scope is limited to an explicitly enabled shoot experiment and
allowlisted test/Fly surfaces. Start gestures on our own mock controls with no
aircraft connected. Gesture cancellation or an on-screen STOP does not prove
neutral sticks, hover or pilot takeover. Hardware input, recording, gimbal and
flight tests are separate from the first probe; flight requires explicit
action-level pilot confirmation, validated bounds and tested manual override.

This scope update authorizes planning/research, not aircraft actions or private
media uploads. Keep pre-event code under `prototype/`, preserve event provenance,
credentials and the archive, and stay below the existing storage limits. Do not
carry forward the archived assistant/accountability features.


## 5 October creator revision — spoken direction during actual takes

The creator explicitly requests real-time audible shot direction through either
the normal phone speaker or an already paired Bluetooth headset, including
Action, one action/pose at a time, framing updates and Cut. This updates the
phone-first delivery goal beyond preparation-only speech. Keep the simple
Camera/Reel interface and an explicit quiet-during-takes choice for dialogue.
Use Android's selected media output, expose voice readiness/test feedback,
and immediately cancel future cues on Stop, background, audio focus/noisy
interruption or replaced capture. Do not force Bluetooth pairing/global routes.

The slower pretrained Qwen CPU request prepares editable shot directions;
bounded shot timing and fresh bundled pose observations drive the live cue loop.
Call that distinction out: fixed shot cues are plan execution, framing rules
interpret MLKit output, and no streaming frame-LLM/free-form conversational
understanding or trained director accuracy is implied. Real audible speaker
playback, actual Bluetooth delivery and camera/audio coexistence each need
separate evidence. Speaker cues can enter recorded audio; quiet mode preserves
the existing dialogue workflow. Never initiate private filming to test this.
