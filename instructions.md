# Instructions — Mini Film Director research

Scope approved by the user on 4 October 2026 (IST): preserve FocusPilot as a
recoverable research archive and use this workspace for an AI mini film director.
The archive's tests, installed APKs and model results are historical evidence;
they do not verify the new product. Read `plan.md`, `hackathon.md`,
`docs/status.md` and `docs/architecture.md` before changing scope.

## Product contract

The first user makes fashion reels daily. Build one useful workflow: brief →
editable shot list → user-guided phone/drone capture → imported-clip review →
selected clips and edit notes for the laptop. First demo: one outfit, one location,
five shots and a 20-second reel. The creator keeps creative and pilot control.

The immediate milestone is a **propellers-off Mini 4 Pro connection probe**:
SDK registration, aircraft identification, read-only telemetry and camera preview.
It excludes takeoff, motor commands, Virtual Stick, waypoint execution, gimbal
movement, recording and firmware changes. See `docs/mini4-connection-test.md`.

User reports owning a Mini 4 Pro, a Neo and both phone-holder and screen remotes.
Exact labels, firmware, cable, pairing and iQOO availability remain unknown.
Use the documented RC-N2/RC-N3 route once the physical label is checked; do not
assume the screen remote works with our Android SDK app.

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
debugging tools. Phone camera capture and push-to-talk permissions are later,
separate opt-in features. No passive audio, general screen observation, Accessibility
or usage-monitoring scope is inherited from the old product.

Do not claim on-device vision, useful AI shot planning, iQOO execution, NPU
acceleration, Office Kit transfer or working aircraft control before reproducible
evidence exists. The archived text inference path does not establish image/video
understanding. Pin any future model/runtime, license, backend and measured device.
Report failures and unknowns in `docs/status.md`.

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
