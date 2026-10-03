# Verified status — Mini Film Director

Updated 4 October 2026 (IST). The user authorized archiving FocusPilot, removing
its files from this workspace and proceeding with the DJI Mini film director.

## Current scope

Solo fashion reel workflow: brief → editable shot list → guided phone/drone shoot
→ imported-clip coverage review → selected clips/edit notes for the laptop.
The full vision now includes phone/drone perspectives, opt-in spoken performance,
local learned shot direction, approved bounded SDK actions and aligned video
assembly. [Vision](vision.md) separates these proposed stages from completed work.
First milestone is a propellers-off, read-only Mini 4 Pro SDK connection probe.
Planning documents are prepared. No new product or connection probe is built yet.
Android is the required app/inference target. The user reports Nothing Phone (3a)
and an iPhone with DJI Fly; Phone (3a) is the development device and iQOO
validation remains pending.

The user explicitly confirmed **DJI Neo 2**, **RC-N3** and **Nothing Phone (3a)**
this turn. Mini 4 Pro and screen-remote ownership are previously reported.
Physical connection, firmware, remote pairing, transceiver presence, Android
version/RAM and exact iQOO model/access have not been checked.

## Archive and migration

Full local archive verification and final storage measurements are recorded in
[migration evidence](archive-migration.json). Every original regular file, symlink
and directory was matched to the archive, original file contents were rechecked
before cleanup, and the complete archive checksum is retained. Private local
configuration and original Git history are included in the owner-only archive.
The working `.env` is retained byte-for-byte and remains ignored/untracked.
See [recovery instructions](archive-and-recovery.md).

**Latest recovery check:** the recorded full-archive directory is now missing.
Focused Spotlight lookups did not locate it; read-only Finder Trash inspection
also found no matching archive. Its location/removal cause is unknown, and a
moved-path clarification remains unanswered. Complete private recovery cannot
currently be verified. Published GitHub code/releases and the active local `.env`
remain available. No backup deletion was performed during this review.
An additional filename search across Desktop, Documents, Downloads and mounted
volumes found no matching full archive/manifest; protected system directories
were inaccessible. This is a bounded search, not proof of deletion.

Old source/builds/models/training data/recordings/research checkouts and product
documents were removed from the active folder only after full verification.
Fresh film-director Git history is independent of the preserved FocusPilot Git
repository. FocusPilot's GitHub repository is marked archived/read-only and its
published code/releases remain intact. The new `origin` points to the private
[film-director preparation repository](https://github.com/sanjuhs/iqoo-film-director).
Existing shared toolchains/caches and the phone installation were
not removed; old application dashboard fields were not edited or submitted.
Global-cache accounting carries forward 218,929,328 bytes from the old research
record. It excludes pre-existing shared SDK/JDK/Gradle installations, so it is not
a complete machine-wide disk inventory.

All 35,385 original regular files, 43,106 source entries and 43,105 macOS extended
attributes were verified. The full compressed archive is 3,755,403,907 bytes,
versus 9,569,024,590 original logical bytes. Final measured research storage and
cleanup savings are recorded in [storage evidence](storage.json); credentials,
models and private media were not uploaded. This pivot only creates documentation
and an independent Git repository; it does not establish a runnable Android app.
Those byte counts are the historical migration snapshot, not current proof of an
accessible archive or a complete present-day storage total.

## Documentation evidence

Official DJI MSDK 5.18.0 docs list Mini 4 Pro with RC-N2/RC-N3 and provide
registration, product/battery keys and camera-stream APIs. The first sample
connection path uses Android USB accessory handling. See
[connection test and primary sources](mini4-connection-test.md) and
[architecture](architecture.md). Hardware support documentation does not prove
our app or the user's physical setup works.

Official [DJI compatibility table](https://repair.dji.com/help/content?customId=01700000763&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17)
reviewed this turn explicitly lists Neo and Neo 2 as SDK-unsupported and Mini 4
Pro as Mobile SDK supported. Neo 2 DJI Fly learning is a vendor-controlled path;
the custom-control probe remains Mini 4 Pro + RC-N3. The Neo 2 FAQ documents Fly
voice/audio features, which are not our model/app execution. No Fly plug-in or
shared-transport control bridge is established.

Nothing's official specification identifies Snapdragon 7s Gen 3 in Phone (3a).
The current LiteRT CompiledModel Qualcomm support list does not list this chip;
that route's compatibility remains unresolved and is not a claim about absent
silicon capabilities. Phone (3a) local inference and eventual iQOO NPU execution
need separate tests.
Qualcomm/LiteRT routes and Android camera/microphone/media/sensor APIs were
reviewed as design sources. Kev checkpoint adaptation, faithful Android readout,
quantization parity, NPU backend evidence, multi-view alignment and Media3 export
remain experiments. No hardware/runtime success follows from those API docs.

The [product specification](product.md) now consolidates Android targeting,
guided selected-clip import, coverage/framing review, creator decisions, stop/go
gates and export. [Vision research](vision-research.md) records official candidate
sources; [tiny model design](tiny-director-model.md) proposes a 24→32→16→6 dense
framing head (1,430 parameters). The user supplied Jared Palmer's Kev repository;
its text decision API is distinguished from image perception and Android inference.
These are research designs only: no new weights, training, accuracy, conversion,
phone inference or action-conditioned drone behavior has been demonstrated.

Documentation checks passed: local Markdown links resolve, the proposed head's
parameter count is 1,430, `.env` is ignored/untracked, and the new origin points
to the private film-director repository. GitHub confirms FocusPilot remains
archived/read-only. No runnable-code test applies to this documentation update.
The new vision and linked documents passed local-link and whitespace checks;
`.env` remains ignored/untracked. Documentation changes added no SDK/model
downloads, training runs, private-media uploads or phone/aircraft actions.

## Open gates

- Aircraft/remote firmware, data cable, pairing, screen-remote model, Neo 2
  Digital Transceiver presence and actual iQOO model/access remain unverified.
  User-confirmed Neo 2/RC-N3/Phone (3a) labels are inventory evidence only.
- No DJI app key is configured. The preserved `.env` has existing laptop-only
  provider configuration, which does not register DJI SDK.
- SDK dependencies, probe source/APK, permission UI, registration, aircraft
  identification, fresh telemetry, preview and detach/reconnect remain pending.
- No drone connection, flight, capture, recording, gimbal motion, firmware
  change, Android installation or phone-state operation occurred in this pivot.
- Useful AI planning/vision, live-feed analysis, evaluation, on-device backend,
  NPU acceleration and actual Office Kit transfer remain unverified. No old
  inference or accuracy result is transferred to the new product.
- Concurrent phone camera/audio and drone preview, local speech, Kev adaptation,
  Android probability parity, approved bounded control, synchronization/drift,
  reviewed automatic cut assembly and final Android video export remain unbuilt
  and untested. The first probe still permits no flight or capture actions.
- Venue drone-demo permission, new Phase 1 assets, exact cutoff/admission,
  eligible event-created implementation and accepted submission remain pending.

No SDK/model download or provider/media upload occurred. This is pre-event
research preparation; no competition eligibility or winning outcome is claimed.
