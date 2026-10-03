# Verified status — Mini Film Director

Updated 4 October 2026 (IST). The user authorized archiving FocusPilot, removing
its files from this workspace and proceeding with the DJI Mini film director.

## Current scope

Solo fashion reel workflow: brief → editable shot list → guided phone/drone shoot
→ imported-clip coverage review → selected clips/edit notes for the laptop.
First milestone is a propellers-off, read-only Mini 4 Pro SDK connection probe.
Planning documents are prepared. No new product or connection probe is built yet.

## Archive and migration

Full local archive verification and final storage measurements are recorded in
[migration evidence](archive-migration.json). Every original regular file, symlink
and directory was matched to the archive, original file contents were rechecked
before cleanup, and the complete archive checksum is retained. Private local
configuration and original Git history are included in the owner-only archive.
The working `.env` is retained byte-for-byte and remains ignored/untracked.
See [recovery instructions](archive-and-recovery.md).

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

## Documentation evidence

Official DJI MSDK 5.18.0 docs list Mini 4 Pro with RC-N2/RC-N3 and provide
registration, product/battery keys and camera-stream APIs. The first sample
connection path uses Android USB accessory handling. See
[connection test and primary sources](mini4-connection-test.md) and
[architecture](architecture.md). Hardware support documentation does not prove
our app or the user's physical setup works.

## Open gates

- Exact phone-holder remote label, aircraft/remote firmware, data cable and
  actual iQOO access remain unverified. User reports a Mini 4 Pro, Neo and both
  remote types; Neo generation is unknown and is not the first target.
- No DJI app key is configured. The preserved `.env` has existing laptop-only
  provider configuration, which does not register DJI SDK.
- SDK dependencies, probe source/APK, permission UI, registration, aircraft
  identification, fresh telemetry, preview and detach/reconnect remain pending.
- No drone connection, flight, capture, recording, gimbal motion, firmware
  change, Android installation or phone-state operation occurred in this pivot.
- Useful AI planning/vision, live-feed analysis, evaluation, on-device backend,
  NPU acceleration and actual Office Kit transfer remain unverified. No old
  inference or accuracy result is transferred to the new product.
- Venue drone-demo permission, new Phase 1 assets, exact cutoff/admission,
  eligible event-created implementation and accepted submission remain pending.

No SDK/model download or provider/media upload occurred. This is pre-event
research preparation; no competition eligibility or winning outcome is claimed.
