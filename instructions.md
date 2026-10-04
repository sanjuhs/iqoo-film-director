# Instructions — Mini Film Director research

Scope approved by the user on 4 October 2026 (IST): preserve FocusPilot as a
recoverable research archive and use this workspace for an AI mini film director.
The archive's tests, installed APKs and model results are historical evidence;
they do not verify the new product. Read `plan.md`, `hackathon.md`,
`docs/status.md` and `docs/architecture.md` before changing scope.

## Product contract

The first user makes fashion reels daily. The full vision combines a phone
camera/microphone and drone view, local learned direction, pilot-approved bounded
capture/control and an aligned editable video. The expanded objectives add opt-in
voice briefs, posing/framing cues, story-aware shot choices, optional Action 4
coordination and a separately gated Neo 2/Fly app bridge. Daily fashion remains
the first workflow; walking/talking stories, product reveals and introductions
reuse that director. See `docs/objectives.md` and `docs/vision.md`.
Build one useful first workflow: brief →
editable shot list → user-guided phone/drone capture → imported-clip review →
selected clips and edit notes for the laptop. First demo: one outfit, one location,
five shots and a 20-second reel. The creator keeps creative and pilot control.

Android is the app and local inference target: develop on the user's Nothing
Phone (3a), then validate on iQOO. The user also reports an iPhone with DJI Fly;
that does not change the Android target. See `docs/product.md` for the workflow
and `docs/tiny-director-model.md` for the proposed task-specific model.

The latest user delivery instruction prioritizes a phone-first **Pose → Perform →
Assemble** prototype now: local planning/posing, earbud spoken direction, explicit
phone capture and editable local subtitle/reel export. ADB installation/launch
and parallel agents are authorized. Phone work does not wait for drone access.

The independent drone milestone remains a **propellers-off Mini 4 Pro connection probe**:
SDK registration, aircraft identification, read-only telemetry and camera preview.
It excludes takeoff, motor commands, Virtual Stick, waypoint execution, gimbal
movement, recording and firmware changes. See `docs/mini4-connection-test.md`.

User confirms DJI Neo 2 and RC-N3, and reports a Mini 4 Pro and screen remote.
Firmware, cable, pairing, screen-remote label and iQOO availability remain unknown.
DJI's current compatibility table lists Neo/Neo 2 as SDK-unsupported. Learn vendor
filming on Neo 2 through DJI Fly; use Mini 4 Pro + RC-N3 for the custom probe.
Do not assume a DJI Fly plug-in, shared connection or screen-remote app support.
A shoot-specific Accessibility bridge is now a research candidate, distinct from
the SDK probe and from a demonstrated Neo 2 transport. Optional Action 4 control
needs exact camera/firmware/interface validation and Android evidence.

## Evidence and permissions

Keep prototypes under `prototype/` and explicitly label them pre-event research.
Competition code must be separately created during the permitted event window
unless organizers explicitly authorize reuse. Preserve reused code provenance.

The model proposes guidance; deterministic validation and user review remain
outside it. New DJI flight or capture actions need explicit action-level approval
and tested manual override before they enter scope. Never turn a spoken brief
directly into flight commands. Real purchases, payments, outgoing messages and
destructive actions require action-level confirmation.

Use official Android permission/lifecycle documentation. The connection probe
runs while visible, uses DJI's accessory connection integration and requests only
justified permissions through normal Android UI. No permission grants through
debugging tools. Phone camera/microphone capture and opt-in spoken intent belong
to a later, explicitly started shoot session, separate from the connection probe.
No passive audio outside that session, general screen observation or
usage-monitoring scope is inherited from the old product. New Accessibility scope
is limited to an explicitly enabled shoot experiment with allowlisted mock/Fly
surfaces. First test gestures against our own mock UI with no aircraft connected.
It is not part of the read-only connection probe; hardware control remains behind
the existing action-level approval and override gates.

Do not claim on-device vision, useful AI shot planning, iQOO execution, NPU
acceleration, Office Kit transfer or working aircraft control before reproducible
evidence exists. The archived text inference path does not establish image/video
understanding. Pin any future model/runtime, license, backend and measured device.
Report failures and unknowns in `docs/status.md`.

The NPU accelerates compatible inference; Android/DJI owns command dispatch and
the drone retains onboard stabilization. Kev adaptation, faithful Android export
and Qualcomm execution are separate gates. A generic Qwen build is not a Kev
port, and CPU inference is not NPU evidence. Model proposals are not SDK actions.

## Secrets, private media and storage

Keep `.env`, API credentials, DJI app keys, private phone/drone clips, captures,
weights, training data and credential-bearing APKs outside Git. Preserve local
credentials without printing their values. DJI requires a separately registered
Android application key; an existing AI API credential cannot substitute for it.
Inject keys from ignored local configuration. Model/media uploads are opt-in.

Track source, configuration placeholders, attributed dependency versions and
sanitized evidence only. Attribute copied vendor sample code and retain licenses.
Do not download SDK/model bundles until their size and license are checked.

Aim below 10 GB and never exceed 15 GB across the active project, retained local
archive, project dependency/cache growth, models and temporary outputs. Account
for the archive explicitly; moving bytes to a sibling directory is not freeing
disk space. Reuse installed Android/Gradle tools instead of duplicating them.

Parallel agents are authorized only with non-overlapping file ownership. Do not
rewrite, push or remove the preserved archive while working on the new product.
