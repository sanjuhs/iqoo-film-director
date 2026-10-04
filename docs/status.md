# Verified status — Mini Film Director

Updated 4 October 2026 (IST). The user authorized archiving FocusPilot, removing
its files from this workspace and proceeding with the DJI Mini film director.

## Current delivery

The fresh phone app **Mini Film** (`dev.minifilm.director`) is installed and
launched on the authorized Nothing Phone (3a), Android 16. The three screens
are Brief → Direct → Assemble. Local CPU shot generation, bundled pose inference,
offline English TTS synthesis, English clip transcription, reference-frame
inspection and actual vertical reel export have passed fixture-based phone checks.
See [app/build instructions](../prototype/phone-director/README.md),
[demo runbook](demo-runbook.md) and [submission draft](submission-draft.md).

Phone delivery is the primary task; daily solo fashion is the first workflow.
Talking head, introductions and product reveal starters are included. The
optional full-shot sequence is implemented but needs an attended capture test.
AirPods playback, real creator capture, sustained live-camera/model concurrency,
iQOO/NPU and Office Kit remain unverified. Color changes are deterministic local
presets. Reference inspection uses sparse heuristics, not semantic trend analysis.
No trained director/benefit/accuracy claim is made.

Drone and optional Action 4 work remain secondary. No aircraft SDK connection,
recording or control has occurred. The separate Mini 4 Pro probe remains
propellers-off/read-only. User-reported Neo 2 and RC-N3 do not establish a custom
SDK route; official documentation lists Neo 2 as SDK-unsupported.

This 4 October source remains explicitly **pre-event research**. The signed-in
Phase 1 dashboard shows **5 October 2026**; its countdown implies about
**23:59 IST**, without an explicit timezone label. Finale is 9–11 October and
competition code must be created in the allowed event window unless organizers
approve reuse. No form attestation, external message or submission was made.
The hourly continuation remains active through this evening.

The sections below retain earlier research/migration history. Current measured
phone evidence is in the final delivery ledger; older “not built” statements
describe their dated checks and are superseded for the phone app.

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

## DJI autonomy research — 4 October 2026

Reviewed the supplied GitHub TypeScript topic, official MSDK V5 source/APIs and
public mission/control bridges; findings and source anchors are in
[DJI autonomy research](dji-autonomy-research.md). DJI 5.18.0 documents Mini 4 Pro
with RC-N2/N3. DroneRoute generates/copies mission files; Lyrebird/WildBridge and
MavDrone contain real SDK control/mission calls. Their listed Mini 4 Pro support
does not establish a hardware test here. MavDrone explicitly marks M300/M350
tested; Lyrebird's cited field examples use Mini 3 and enterprise aircraft.
Dronelink documents a deployed Mini 4 Pro mission path on Android RC-N2/N3.

Material limitation: DJI's Virtual Stick API does not include Mini 4 Pro in its
supported obstacle-avoidance model list. Fixed supervised missions and unattended
vision-guided filming remain different feasibility gates. No new flight scope,
connection, model execution or capture is established by this research.

Source reads completed through web/GitHub APIs. Python's default HTTPS request
failed certificate verification; normal certificate-verified curl/GitHub API
reads worked. Some raw-source reads delayed; no dependency build/run was needed.
Active workspace was 171,549 logical bytes before this small documentation
addition, including Git and ignored files. No repository clone, SDK/model install,
dependency-cache growth or private upload occurred. Archive/shared-cache totals
remain historical and the missing archive prevents complete current accounting.
Documentation whitespace and local-link checks passed. A subsequent active
workspace measurement was 221,876 logical bytes including Git and ignored files;
this is a workspace snapshot, not the complete retained-storage total.

## Research incorporated into objectives — 4 October 2026

Read the complete user-supplied ChatGPT conversation and updated the objectives,
vision, product, architecture, delivery plan, project instructions and hackathon
positioning. The [objectives](objectives.md) define voice-led shoots, posing cues,
optional multicamera coordination, bounded model choices and editable assembly.
Daily fashion stays the first workflow. This is a documentation/scope update,
not authorization or evidence of aircraft actions or private uploads.

[Primary-source checks](multicamera-research.md) found an official DJI R SDK BLE
protocol/demo listing Action 4. Its sample is ESP32; Android integration and
Action 4 ownership/access remain unverified. The pasted Android control example
primarily documents RTMP livestreaming, while the three-camera example is ESP32.
Neither proves our local recording or simultaneous Android multicamera capture.
Bluetooth channel occupancy, authentication, preview/import and sync are separate
gates. No camera, SDK or model has been installed or tested for this update.

Neo 2/Fly Accessibility is now an explicit optional experiment, beginning with
our own mock UI with no aircraft connected. Official Android gesture dispatch
exists but can cancel user gestures; cancellation/STOP is not proof of neutral
sticks or aircraft takeover. DJI describes Neo 2 mobile sticks as fine-tuning/
return controls. Public parameter/media work and the pasted UI-telemetry claim
do not establish reproducible live custom piloting. No complete independent
Neo 2 piloting implementation was verified in this bounded source review.

The first milestone remains the propellers-off read-only Mini 4 Pro probe, with
no recording, gimbal, motor or aircraft-control actions. Model probability alone
never clears a flight action. Hardware versions, firmware, layout, Android
concurrency, useful local decisions and required iQOO execution still need tests.

Validation for this objectives update: all 62 checked local Markdown links
resolve, whitespace checks pass, and `.env` remains ignored/untracked. No runnable
code changed, so no application test result is claimed. Active workspace measured
265,035 logical bytes before this validation note, including Git/ignored files.
Adding the historical archive and known incremental cache figures gives an
estimated 3,996,359,547 bytes (about 4.0 GB); the unresolved archive location and
historical cache accounting prevent certification of a complete current total.
This update added small Markdown files only, with no dependency/model downloads,
cache growth, private uploads, Android permission changes or aircraft commands.
Existing status/autonomy research edits were preserved.

## Single-model phone director research — 4 October 2026

The user asked to explore a simpler iQOO + Osmo director with one creative model.
[Research findings](phone-director-research.md) compare Pixel Gemini Camera Coach,
Samsung video framing/views, DJI ShotGuides and phone pose guidance; distinguish
LLM disclosure from generic AI claims; and propose a bounded training recipe.
Osmo Action 4 is an assumption from prior context pending exact-model clarification.
This is an exploratory recommendation, not an implemented scope/roadmap reset.

Official DJI R SDK recording/status and Google FunctionGemma Android adaptation
sources were checked. Camera control, preview access, creative quality, useful
training data, faithful Android export and actual iQOO/NPU execution remain
separate unknowns. ShutterMuse is a relevant photography research reference,
not phone/Osmo evidence; reuse rights need review. No hands-on product comparison,
training, model download, deployment, recording, phone action or private upload
occurred. No performance or user-benefit result is claimed.

This review adds Markdown only, with no dependency/model cache growth. Active
workspace measured 324,292 logical bytes before this status addition. Archive
availability and current complete cache accounting remain unresolved; the existing
historical retained-byte estimates are not a certified current total. Existing
scope and first read-only drone-probe bounds are preserved.

Documentation validation passed: 63 local links resolve and Markdown
whitespace checks pass. No runnable source changed.

## ADB connection check — 4 October 2026

The existing Android SDK platform-tools ADB (1.0.41, platform-tools
37.0.0-14910828) runs successfully. One USB-connected Android device reports
`device` (authorized), model `A059`, product/device family `Asteroids`.
`adb -d get-state` returned `device`; a read-only shell printf returned
`ADB shell OK` with exit status 0. This verifies current ADB communication,
not DJI connectivity or application/model execution. Hardware serial omitted.
No installation, permission grant, recording, phone-setting change or aircraft
action was performed. No dependency/model download or private upload occurred;
this check adds only a small status note. Complete retained-storage accounting
remains unresolved as documented above.

## Earlier preparation gates (historical)

- Aircraft/remote firmware, data cable, pairing, screen-remote model, Neo 2
  Digital Transceiver presence and actual iQOO model/access remain unverified.
  User-confirmed Neo 2/RC-N3/Phone (3a) labels are inventory evidence only.
- No DJI app key is configured. The preserved `.env` has existing laptop-only
  provider configuration, which does not register DJI SDK.
- SDK dependencies, probe source/APK, permission UI, registration, aircraft
  identification, fresh telemetry, preview and detach/reconnect remain pending.
- No drone connection, flight, capture, recording, gimbal motion, firmware
  change, Android installation or phone-state operation occurred in this pivot.
- Action 4 access/model/firmware, Android BLE authentication/status/recording,
  Neo 2/Fly mock bridge mechanics, human-input interference, lifecycle/network
  concurrency and any subsequent hardware bridge test remain pending.
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

## Phone delivery ledger — 4 October 2026

The user requested autonomous phone-first work while away for dental care.
Three agents owned separate capture/reference, local AI and export/transcription
files. The fresh app stays under `prototype/phone-director/`; the original local
`.env` is preserved and ignored. Native/Java debug and test APK builds passed,
and both APKs installed through existing ADB. Target app has CAMERA/RECORD_AUDIO
normal permission prompts and **no INTERNET or ACCESS_NETWORK_STATE permission**.
Transitive ML Kit manifest permissions were found and explicitly removed.
No permission was bypass-granted and no private camera/microphone input was taken.

| Actual development-phone check | Result |
| --- | --- |
| Local CPU planner, revised prompt | Fashion five shots 52,553 ms; product five shots 46,473 ms; target no-Internet assertion passed |
| Empty image pose execution | 480×640 black image: no landmarks, 384 ms cold / 28 ms warm |
| Public positive pose fixture | 33 landmarks ≥0.65 confidence, mean 0.98735, 388 ms; public annotated documentation image only |
| Offline English TTS synthesis | 203,546-byte synthetic WAV, 511 ms; two pose/voice tests passed in 2.558 s; no audible playback claim |
| English clip transcription | Local whisper tiny.en CPU: synthetic speech, three segments in 3,458 ms; jacket/green/outfit recognized |
| Local reel assembly | Three 0.5–2.5 s synthetic cuts → actual 6.060 s MP4, 720×1280 H.264 + AAC; decoded scene order/captions verified |
| Timed subtitles | Source 600–1100 ms → timeline 100–600 ms first trimmed cut and 2100–2600 ms second; actual frames present inside / absent outside |
| Cancellation | Twelve-cut synthetic export cancels, preserves sources and leaves zero new temporary MP4s |
| Latest media runner | All four tests passed again in 10.190 s after typography update; initial full run 13.743 s |
| Local reference inspection | Three tests passed in 4.883 s: expected two synthetic scene changes, flat-video zero changes/persons, rejected web URI |
| Navigation/internal preview | Three tests passed in 6.603 s: no capture on launch/resume/recreate, persistent Start/Stop, synthetic clip actually prepares in internal player paused |

Ignored evidence logs, model hashes, public/synthetic fixtures and UI snapshots
are retained locally under `private/`. No footage or credentials were uploaded.
An additional real UI local-model generation saved five default streetwear shots in 50,224 ms. That draft invents a black jacket and a dark background absent from the brief; it is saved as an editable AI draft, not validated scene truth. The six-page Phase 1 PDF is prepared under ignored `output/pdf/`; it includes
verified measurements, remaining gates and event provenance disclosure. It was
rendered and visually checked. [Local AI](local-ai-evidence.md),
[export](export-evidence.md), [transcription](transcription-evidence.md),
[capture](capture-evidence.md) and [reference](reference-evidence.md) evidence
describe the implementation and reproducibility limits.

### Observed failures and limits

Initial native build used an obsolete llama parameter; repaired to the pinned
API and rebuilt successfully. Initial LLM fashion output described tabletop
styling rather than worn-outfit performance. The revised prompt improves that
fixture, but scene order can drift and the product draft invented details.
Creative drafts remain editable and reviewed; valid JSON is not accuracy.
CPU planning is around 47–53 seconds, not real-time vision language generation.
ML Kit attempted telemetry was denied by the removed network permission; pose
inference still passed. A preview UI hierarchy dump could not reach idle while
the player loaded; actual instrumentation verified local prepared playback.

An early synthetic export heading collided with source text. Optional headings
and a dark card backing correct that presentation. Bottom recording controls
stay visible independently of the scrolling cue/settings card. Start camera
opens preview separately from recording; no automatic camera on launch/resume.
Optional hands-free sequence speaks between takes, provides an eight-second pose
break plus three-second countdown and stops on Stop/background. Its full live
sequence, audible earbud route, real recorded speech preservation, front/back
capture and source-inactive flushing need attended validation.

No NPU/GPU, Kev training, unseen-creator pose accuracy, learned color correction,
semantic trend understanding, multicamera synchronization, HDR footage, Office
Kit or iQOO hardware result is established. No DJI app key/SDK probe or aircraft
action is included. Current Android source is preparation, not an eligible
finale submission or proof of an accepted idea submission.

### Storage/toolchain accounting

Existing Android SDK/JDK/NDK/CMake/Gradle were reused. Fresh pinned official
llama.cpp and whisper.cpp checkouts are ignored. The generic 563,036,064-byte
Qwen GGUF was locally copied from an existing phone model, hash-verified; no new
Qwen download or archived app source reuse occurred. One new 77,704,715-byte
tiny.en model was downloaded and hash-verified. Weights remain separate from
Git/APK. Storage estimates, historical archive reserve and measured incremental
cache growth are in [storage evidence](storage.json). The measured conservative accounted estimate is 6.41 GB, including current workspace, new target phone files and historical archive/cache reserves, below the 10 GB aim. Existing SDK/JDK and historical phone models are excluded; this is not a complete machine inventory. The unresolved archive
location prevents certification of a complete current retained-storage total.

Office Kit primary-source research found a vendor phone/laptop bridge requirement
and 10% usage score, but no verified public Android Office Kit SDK contract. The
app now offers standard user-chosen JSON document saving alongside MediaStore
MP4 export to prepare for native Files/Albums transfer. The installed app saved its synthetic cut list to local Downloads through the normal Android Files picker. The 1,512-byte saved JSON SHA-256 matched the source export. Vendor bridge, received-file playback/hash and HackTracker checks remain separate gates. See [Office Kit research](office-kit-research.md).
Third-party notices (MIT, Apache, libyuv BSD/authors, ML Kit terms and model
provenance) are packaged and verified in the APK; exact CameraX libyuv binary
revision remains unknown. See [notices](third-party-notices.md).

Final Android document-save check: explicit app button → local Downloads picker
→ new JSON document, successful UI receipt and exact source/destination hash
match. Only our synthetic edit list was copied. No existing file was overwritten.
No runtime camera/audio permission was granted; app remains ready for an attended
shoot. Local fixture exports and cut lists are available for preview in Assemble.

## Preserved delivery checkpoint

Fresh phone source, evidence documents and provenance are preserved in local
Git commits `3da9e90` and `443c431` on `codex/film-director-research`. No remote
push or external submission occurred. The standalone research APK is retained
under ignored `output/apk/MiniFilm-research.apk` (approximately 50 MB); models
remain separate. Latest accounted storage is approximately 6.41 GB after this
retained APK. Windows wrapper CRLF normalization and source whitespace checks
passed across the full new-source diff.

The phone is left on Brief with a saved editable local-AI draft and three clearly
synthetic takes/finished reel in Assemble. Camera and microphone are inactive.
Goal/quiet hourly continuation remains active through the requested evening;
no attended live capture, AirPods or iQOO result is inferred from this checkpoint.
