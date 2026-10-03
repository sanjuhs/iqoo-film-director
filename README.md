# Mini Film Director — research preparation

An AI shoot director for solo fashion creators: turn a reel brief into an editable
shot list, guide phone and drone capture, review imported footage for missing
coverage, and prepare clips and edit notes for a laptop.

The user makes fashion reels every day and reports owning a DJI Mini 4 Pro,
a DJI Neo, a phone-holder remote and a screen-equipped remote. Exact remote
models, firmware and Neo generation remain unverified.

The product and local inference target **Android**. Develop on the user's Nothing
phone, then verify the complete workflow on iQOO. The iPhone is reference
equipment, not the app target.

## First milestone

Prove a **propellers-off Mini 4 Pro connection** from an Android phone through a
supported RC-N2/RC-N3 remote: SDK registration, aircraft identification,
read-only telemetry and camera preview. No motor start, takeoff, autonomous flight,
waypoint upload or camera recording belongs in this first test.

See [connection protocol](docs/mini4-connection-test.md), [delivery plan](plan.md),
[architecture](docs/architecture.md) and [verified status](docs/status.md).
The complete [product specification](docs/product.md) covers guided import,
AI review and stop/go decisions; the [tiny model design](docs/tiny-director-model.md)
and [vision research](docs/vision-research.md) explain the proposed learned director.

## Repository scope

This is pre-event research and preparation, not an eligible hackathon submission.
Research implementations belong in `prototype/`. Competition code must be created
during the permitted October 9–11, 2026 event window unless organizers explicitly
approve reuse. Provenance is retained; earlier implementation is never relabelled.

The earlier FocusPilot workspace was preserved in a private local full archive,
including Git history, uncommitted work, environment configuration, models,
dependencies and recordings. The film director has independent Git history.
See [archive and recovery](docs/archive-and-recovery.md).
**Current recovery limitation:** the verified full backup is no longer at its
recorded path and has not been located. Published FocusPilot code/releases remain
available, but they cannot replace the complete private archive.

## Local configuration and privacy

`.env` stays local and ignored. The existing provider configuration is preserved;
it is not an Android credential store. Never embed provider API keys in an APK.
The new `.env.example` contains placeholders only. A DJI SDK app key must be
registered for the probe's actual package before hardware testing can pass.

Private footage, captures, hardware identifiers, evidence, models, downloads and
build caches stay outside Git. Shared Android/JDK installations remain available.
There is no installed DJI app from this repository and no proven drone, model,
iQOO NPU or Office Kit execution yet.

## First product demonstration

One outfit, one location, five shots, one 20-second fashion reel. Intentionally
omit a garment-detail shot, then evaluate whether the assistant finds the gap and
guides a useful pickup. A model-based coverage/quality evaluator is still research;
an editable checklist must be labelled as such. Flight controls are a separate
later milestone with explicit pilot approval and manual override.

## Storage

Keep the complete local research footprint within 15 GB and aim below 10 GB,
including the retained archive and incremental dependency/model growth. Do not
restore or download old model bundles by default.
