# Mini Film Director — research preparation

An Android multicamera film director for solo creators: understand an opt-in
spoken brief, suggest poses and framing, coordinate phone/drone and optional
Action 4 shots, review coverage and propose an editable video. Daily fashion reels
are the first workflow; walking/talking stories and product reveals use the same
director. See the [expanded project objectives](docs/objectives.md). The [phone research app](prototype/phone-director/README.md) now runs local planning, pose inference, English transcription and MP4 assembly on the development phone. Attended capture, earbud playback and iQOO validation remain open checks.

The user makes fashion reels daily and reports a Mini 4 Pro, **DJI Neo 2**,
**RC-N3**, a screen-equipped remote and **Nothing Phone (3a)**. Firmware,
screen-remote model, pairing and iQOO access remain unverified.

The product and local inference target **Android**. Develop on the user's Nothing
Phone (3a), then verify the complete workflow on iQOO. The iPhone is reference
equipment, not the app target.

Start with the [full phone-and-drone vision](docs/vision.md): equipment, sensor
roles, Kev training, NPU gates and video assembly. DJI currently lists Neo/Neo 2
as SDK-unsupported; explore Neo 2 through DJI Fly and use Mini 4 Pro for the
custom SDK route. A shoot-specific Neo 2 Accessibility bridge and Action 4 BLE
adapter are research candidates, with [source checks and limitations](docs/multicamera-research.md).
No app-to-app control or multicamera hardware result is established.

## Phone delivery and secondary drone milestone

The phone app is installed and launched through ADB. Its three screens are Brief, Direct and Assemble. See [measured evidence](docs/status.md), the [demo runbook](docs/demo-runbook.md) and [submission draft](docs/submission-draft.md). Local CPU inference is verified; NPU acceleration is not.

Assemble now lets the creator explicitly assign a take to one or more plan shots,
see selected assignment counts and open the next missing direction. This is a
[reviewed metadata checklist](docs/shot-assignment-evidence.md); visual coverage
and shot quality still require creator review and learned evaluation evidence.

Assemble take cards now keep **Preview take** and **Edit & review take** together;
the latter opens the take’s scrollable tools menu for trims, subtitles, shot
assignments, framing and order. See [take-tools evidence](docs/take-tools-evidence.md).

The selection duration and **Export my reel** now stay above navigation while
reviewing takes. Returning from a preview preserves the list position; busy
processing keeps edits locked. See [footer evidence](docs/assembly-footer-evidence.md).

Guided preparation now gives person-framing advice during the pose break and
waits for a pending spoken hint before counting down. Take/trim previews show
the centered9:16 reel crop with a framing-only disclosure. [Verified checks](docs/pose-break-crop-evidence.md)
use synthetic inputs; attended filming and earbud sound remain open.

**Review cut framing** adds an opt-in look at three nearby frames from a saved
take’s current cut. Local pose checks show qualified body-visibility hints on an
approximate center 9:16 crop. Results are temporary and do not edit cuts, assign
shots or score quality. See [cut-framing evidence](docs/take-framing-evidence.md);
real footage, motion and attended usefulness still need review.

Device edit documents and portable ZIPs now include an immutable current shot
plan. A nonempty portable plan also has readable `shoot-notes.txt`; exact reviewed
assignments resolve against current IDs, while earlier IDs remain unresolved.
Changing the plan invalidates a prepared ZIP, including a package that finishes
after those edits. See [shot-plan export evidence](docs/shot-plan-export-evidence.md)
and [portable package evidence](docs/portable-edit-evidence.md).

For the separate aircraft research path, prove a **propellers-off Mini 4 Pro connection** from an Android phone through a
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
The new phone app contains no provider keys and no Internet permission. There is no installed DJI app from this repository and no proven drone, iQOO NPU or Office Kit execution yet.

Portable packages omit per-cut original `shotId` and device `sourceUri` fields,
but intentionally include explicit reviewed mapping IDs and current plan IDs
(which can equal an original ID). Copied clips retain embedded metadata and
footage outside trims. Saving or sharing a package is the creator’s choice;
separate Qwen/Whisper weight files are excluded from these edit packages.

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

A six-slide [Phase1 idea deck and PDF](docs/phase1-deck.md) are ready for creator
review. They remain unsubmitted and disclose pre-event research provenance.

Each shot now has an explicit creator-selected framing target. Face framing
omits outfit shoe advice, and saved-cut hints use a qualified current target or
manual review. Choices persist into editable exports. [Target evidence](docs/shot-framing-target-evidence.md)
records actual editor and original-byte ZIP checks; live usefulness remains open.

**Use another moment** now keeps independent ranges from one original, with the
new cut unselected until review. Shot/cut saves preserve your list position and
invalid shot lengths ask for correction. [Editor evidence](docs/another-moment-editor-evidence.md)
includes actual repeated-source reel and original-deduplicating ZIP checks.
