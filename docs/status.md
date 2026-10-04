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

## Continuing quality work — 4 October 2026 afternoon IST

The user asked to keep working while away through the evening. Camera/microphone
remain inactive during unattended work; all media is public or synthetic.
Vertical shared preview/capture/analysis cropping, visible-crop pose normalization,
persisted selected lens, speech-completion-gated sequence countdown and selected
length display repairs are integrated. Fresh build/install passed. Three geometry,
two silent speech completion and four UI checks passed together: **9 tests in
14.110 seconds**. This proves geometry, callback cancellation and inactive UI
behavior; it does not establish live camera, earbud audio or quiet take recording.

Ordered local planner regression first failed (2 tests / 121.536 s / 1 failure),
with a test missing legitimate “Face” plus observed camera-operator cues. Grammar
and performer prompt were refined, with conservative fashion device-word rejection;
fresh generation is being checked. Long typography now fits fully or rejects
clearly, with new actual-output/rejection tests awaiting the next build.
Export restart recovery and portable selected-originals/edit ZIP are underway;
neither is claimed tested yet. See [completion audit](completion-audit.md).

Second planner run: 2 methods / three actual generations passed in **95.745 s**;
unspecified jacket 28.875 s, yellow raincoat 33.463 s, mug 33.284 s. Direct review
still found awkward fashion eyeline language and invented mug component colors/finish/use,
which the narrow prior assertion missed. These are observed remaining failures;
stronger mug regressions and concrete product/voice-scene prompt examples are
being added. No general director-quality claim follows from a green test result.

Full-caption tests (including actual 199-character rendered output), all five
portable-package fixtures and four UI tests passed in the combined 20-test /
21.065-second runner; one export-recovery test failed in cleanup after deleting
its expected unfinished row. Existence-checked fixture cleanup was corrected;
the recovery-only rerun passed **5 tests / 0.775 s**. Recovery uses scoped durable
journals and exact owned outputs; staged tests do not imply a killed-live-export
result. [Export](export-evidence.md) and [portable edit package](portable-edit-evidence.md)
record exact behavior and limits.

The new voice/product model runner took **129.037 s**, 3 methods with 2 failures:
introduction omitted coffee and named an unsupplied rim; product still invented
glossy/blue-handle/texture/benefit details. Talking-story assertions passed but
review found duration-unit captions and an unavailable empty-train-car cutaway.
Targeted assertions and prompts are being strengthened; story/model execution
is established, useful grounded direction across modes is not yet established.

Source audit also found product/object shots receiving face-framing advice,
background package completion attempting to open Files, activity recreation
losing a ready package, and blocked provider reads delaying a cancelled job.
Root has repaired object-shot pose gating, visible-only picker launch, ready-cache
pointer persistence and partial-document cancellation feedback; stream cancellation
repair and integration checks remain underway. Camera/audio remain inactive.


### Automatic balance, package cancellation and lock-screen boundary

Six portable-package tests passed on the phone, including actual stream-close
cancellation of a blocked provider read, suppression of a late-open read, real
local MP4 MIME detection, byte/hash preservation and portable timeline mapping.
The standard Files ZIP save and vendor Office Kit receipt are still pending.

Five AutoColorBalance tests and two actual AutoColorExport tests passed after
the test encoder fixture was corrected from unsupported 96×128 to the existing
360×640/24-fps profile, with explicit capability checks. The production algorithm
was unchanged by that fixture repair. Three measured samples per cut produced
bounded RGB gains; actual decoded exported video reduced a mild neutral color
cast spread from **12.1742 to 1.5267**, and lifted the dim neutral fixture by
**6.0603 luma units**. Intentional flat color remains unchanged; stale/mismatched
measurements are rejected before outputs, and original hashes stay unchanged.
This is an opt-in, conservative deterministic heuristic, not learned AI grading.
HDR, saturated/uncertain frames and unsuitable samples abstain.

The combined rerun had 14 tests / five UI failures: all seven color tests passed,
while screen-dependent UI cases ran behind the phone's secure keyguard. Read-only
power/keyguard checks confirmed a locked, initially dozing phone. Only screen
wake was requested; no unlock credential, permission bypass or lock dismissal
was attempted. New UI-test preconditions require an unlocked phone. Native
planning and media tests can continue; actual screen workflow awaits the user.

The next few-shot planner runner took **140.584 s**, three methods / one failure.
Product generation avoided the previously observed glossy/blue-handle/grip
inventions. Introduction used a legitimate “feature of your work” reference to
its preceding ceramic-bowls shot, which the detail assertion was too strict to
accept. Review still found invented door/opened/past-event instructions in the
talking story, and an unsupplied raincoat pattern plus incomplete captions in
fashion. Regressions now target those observed errors. The final refinement
lowers sampling temperature from 0.65 to 0.25 and clarifies short captions and
creator-chosen visible detail/future-decision wording; its fresh five-plan check
is underway. A lower temperature or a narrow passing test cannot establish
general scene grounding or creative usefulness.


Final lower-temperature check passed **4 methods / five actual plans in
142.074 seconds**: talking story25.733s, introduction25.563s, unspecified
jacket31.666s, yellow raincoat31.580s and blue mug27.203s. Direct output review
found the targeted observed inventions/fragments absent. Talking retained the
missed-train/walk-home events and future early-departure decision, with an
available creator-chosen cutaway. Introduction retained Rae/ceramic bowls/coffee.
Fashion used concise complete captions and creator-chosen detail; product
retained actual-use/own-opinion prompts without the earlier finish/grip/component
inventions. All five plans kept exact role order and bounded performer cues.
The installed no-Internet assertion passed. These are narrow synthetic fixtures
with handwritten scaffolding; unseen brief quality and creator benefit remain
unverified, and all drafts still require review. Evidence: ignored
`private/evidence/final-planner-test.log` and `final-planner-metrics.log`.


Integrated retained-demo check first failed before export because its helper
assumed six seconds of synthesized speech; the actual known source is 5.746 s.
The fixture now uses its full measured duration, making the timeline 11.746 s.
The next run exported a real 720×1280 H.264/AAC video and decoded speech/captions,
then failed portable packaging: AAC decoded PCM duration exceeded container
duration by about 12 ms, leaving an ASR endpoint outside the source clip. This
is a real integration defect; transcription is being bounded to the container,
with the existing media test tightened to reject any endpoint overrun.
Source review also found two-decimal time fields rounding 5.746 to 5.75 and
rejecting default review/trim saving. Exact millisecond-preserving decimal
format/parsing is being integrated. No package success is claimed for these
failed runs; only synthetic outputs were generated, all originals preserved.

The prepared six-page pitch was refreshed with the final 26–32 s planner fixture
range, actual automatic-balance/package checks and remaining hardware/event
limits. All six rendered pages were visually inspected, text/provenance checked,
and the 247,292-byte PDF remains under the portal 25 MB limit. It is a review draft
with no attestation or external submission.


### Retained synthetic delivery and timestamp fixes — completed checks

The final runner passed **six tests in 20.578 s**: four exact-time parsing/format
regressions, strict source-bounded speech-to-export captions, and integrated
retained reel plus portable package. ClipTranscriber now caps source-offset
ASR endpoints to actual container duration, so codec padding cannot create a
draft beyond the clip. Trim/subtitle fields preserve three-decimal seconds;
5.746s round-trips to 5746 ms and default review no longer rounds beyond source.
Invalid/nonfinite/overflow decimal input is rejected. Visible dialog behavior
still awaits normal unlock; these headless checks establish the exact conversion.

The actual retained reel is **11.795737 s / 720×1280 / H.264 + AAC**, versus the
reviewed 11746 ms timeline. Three visual cuts precede the full known 5.746 s
synthetic English clip. Actual decoded frames verified caption white-pixel counts
3955 inside first cue, 0 in the gap and 2864 inside second cue. CPU transcription
of source and completed output retained jacket/green in three segments each
(4.039s source and 4.707 s output in this run). No audio was played or recorded.

The 252,326-byte portable ZIP resolves four unchanged originals totaling 246,497
bytes, relative paths, reviewed order/trims and source-to-timeline subtitle
ranges. Copies under ignored `output/demo/` matched phone SHA-256 values on the
laptop; ffprobe independently confirmed codecs, dimensions and duration.
Reel SHA-256: 20ebb74737cc2e782f3afaf478475ff84e81f63eb6a447c732df0677e4bf2540.
ZIP SHA-256: 0668a39bf8f36542f5cd3f25f9eaee17165e4b40b8032d3d88b1b29e9f31ba78.
See `output/demo/README.txt` and `metrics.json` for synthetic provenance.

The failed delivery's exact owned synthetic output was cleaned only after
ID/title/source/MediaStore ownership proof; original fixtures and saved shoot
preferences stayed intact. Successful test-only journal markers are removed
after durable delivery copies so later startup does not replace the saved
project's output pointer. A separate media-test marker was locally archived
and removed after verifying its exact known synthetic source; its MP4/JSON stay
intact. Future media checks use callback-scoped fixture-marker cleanup.
Normal Files ZIP saving, Office Kit receipt, attended capture/AirPods, actual
iQOO and organizer-compliant event code/submission remain unverified.


Final fixture-marker cleanup rerun passed one strict speech/export test in
4.749s and left no test journals. Fresh APK and test APK are installed; the
normal MainActivity launch intent was accepted behind the existing secure lock.
Camera and microphone runtime permissions are still denied, with no unattended
capture. Standalone ignored APK SHA-256:
8222a70a206dafb82d36aca451498c2d46ce74685aca1c0160e6eb16682b8f4a.
The current accounted estimate is about 6.42 GB, including target phone files/cache
and historical archive/cache reserves; this remains a qualified estimate, not
a complete machine inventory. No further model/SDK/JDK download occurred.

### Speech-edge review, held-out briefs and actual cut preview — 4 October, 13:36 IST

Two new held-out native CPU planner briefs passed (Dev/comics/badminton introduction
40.002 s; unspecified kurta fashion 32.378 s), with actual supplied facts and no
observed earlier-example leakage or invented garment color/material/fastener.
These are two additional fixtures, not broad creative accuracy evidence. The
first combined held-out/trim/source-bounded ASR runner passed nine tests in 84.636 s.

ClipTranscriber now performs one local PCM decode and English ASR pass for a
review-only outer-edge SpeechTrim candidate. Sustained energy, ASR agreement,
noise/clipping abstention, 300 ms padding and a minimum useful removed edge
bound the heuristic. Review found that ASR-missed energetic prefix/tail could be
trimmed; the final rule preserves ALL sustained above-threshold energy runs,
not only ASR-supported runs. It never cuts internal pauses or selects the best
performance. The rule score is uncalibrated. Stale URI/range/duration candidates,
repeat application and cancellation are rejected. Apply changes only in/out;
reviewed caption text/timing and originals remain untouched.

The updated runner passed **18 tests in 18.560 s**: nine trim/cancellation/rule
cases, one actual analysis→confirmed application→MP4 export, three paused clipped
preview checks, three local reference-frame decoder checks and two finalized
container-duration checks. The known synthetic source is 8759 ms; final suggested
range **1220–7480 ms** exported as **6263 ms / 720×1280**. Reviewed first/last cues
were clipped/mapped correctly and present in decoded pixels, absent in the
middle. Re-transcription retained jacket/green/outfit; source SHA and saved shoot
preferences stayed unchanged. Only the test's exact verified journal marker was
removed; original, MP4 and JSON retained. No playback, camera, microphone or UI
Activity was used. Evidence: ignored `trim-preview-export-test.log` and
`vision-trim-preview-metrics.log`.

Take finalization reads actual video container duration and rejects missing,
empty, malformed or audio-only files instead of using CameraX statistics as a
source bound. This passed synthetic-file checks, not an attended camera result.
Main exposes candidate review and a paused suggested-range preview before Apply;
normal take preview now respects the current trim. Dialogs wait for foreground
review where implemented. The latest visible UI and real capture/AirPods remain
unverified behind the secure phone lock; no lock bypass or permission grant ran.

### Exact-source visual component preparation — 4 October, 13:36 IST

The existing 563,036,064-byte language GGUF matches the official runtime
maintainer's artifact at revision `8fea620810c4afa23dd6443f999a48574c1611a3`
by SHA-256, resolving the earlier unverified-publisher statement. Its declared
PRIMARY source is official Qwen revision `2fc06364715b967f1860aea9cf38778875588b17`.
See [reference feasibility](local-reference-feasibility.md) for primary sources.

Pinned conversion dependencies were installed in an ignored isolated Python3.12
venv (about578 MB allocated), preserving the bundled runtime and existing SDK/JDK.
Global Hugging Face CLI metadata requests stalled in TCP address selection;
a child-only IPv4 probe/dry-run succeeded. Official bounded curl retrieved only
the exact source shard/config/index/processor/license/card; the large transfer
hit its180s cap and resumed once, without a duplicate source cache. The final
1,746,942,600-byte official shard matched SHA-256
04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696.

`tools/convert_reference_projector.py` verifies source size/hash, architecture,
153 visual source tensors, runtime pin and ignored output paths, then runs
`--mmproj --outtype f16` only. Result **204,987,136 bytes /154 GGUF tensors**, SHA-256
91388cbe4ccde93acd902d7ce32776d14c32bd71a462d4affc1d2e226d81cada,
with no language tensors, core conversion or training. The existing language
weights were retained. The phone's separate projector copy matched the same SHA;
no weights entered APK or Git. A separate bounded CPU mtmd JNI image path builds
successfully with the unchanged runtime pin. It does not establish image
interpretation quality until actual inference tests finish.

### Visual execution and stricter framing failure —4October13:50IST

Actual RGB/mtmd CPU image execution passed first two tests in42.766s: public
person recognized at30.004s, black frame described as black/empty/no identifiable
subject at11.509s, immediate cancellation delivered no success. A concise-phrase
revision passed in24.206s with subject latencies11.140/12.189s. The public image
clearly depicts a full body, but the model initially called its framing close-up.
These subject checks therefore do not prove framing quality.

A strict enum refinement correctly called the full frame full-body(12.482s),
but called its held-out head/shoulders crop full-body too(12.912s). The7-case
runner failed1case in97.484s. Its actual assertion failed first because the
valid subject word'child' was absent from the test's person synonyms; that test
false-negative is repaired, while the separately logged wrong framing remains
an actual model defect. No creative/framing accuracy success is claimed for it.
The implementation now removes generative framing and is integrating a separate
visible-pose-landmark heuristic with strict full/crop/empty real-inference checks.

The other6cases passed: three shared-model lifetime/cancellation regressions,
vision cancellation and two fresh planner briefs. A fair shared permit spans
entire native core/context lifetimes, including idle text handles. Vision waits
for canceled planner resources to be freed; no released request steals another
owner's permit. Native cancel immediately before generation stays terminal
instead of being reset at JNI entry. The actual loaded-text→close→vision check
observed text handle0 throughout vision ownership and no stale text callback.
Main gates planning during a still-releasing frame job. Selecting a different
reference clears previous clip notes even if inspection fails; excessive combined
brief/notes are shortened by the creator rather than silently losing notes.

One prior read-only~1s memory sample run collected25usable/3missing samples over
28.184s: max sampled PSS1,547,793KiB, RSS1,652,760KiB, nativeheapPSS954,096KiB.
These are sampled maxima, not certified peaks; no camera concurrency benchmark.
The accounted conversion peak estimate was9.344GB, below10GBaim/15 GBlimit,
including both projector copies, isolated converter dependencies and historical
archive/cache reserves. After re-verifying both SHA values, only the newly
fetched1,746,942,600-byte conversion source shard was removed. Runtime weights,
small pinned configs/license/card, provenance record, converter environment and
historical archive reserve are preserved. No new SDK/JDK, training or upload.

The first separate-pose framing runner ran9tests in33.857s with2failures:
full-body and black framing passed, but the held-out portrait crop returned
'review needed' rather than the test's required head-and-shoulders label. This
is conservative abstention, not a wrong positive class. The stricter positive
head-classification requirement is NOT established. Acceptance is being made
explicit: cropped portraits may produce evidenced head-and-shoulders or a
zero-confidence review-needed result, and must never claim full-body/waist-up.
The landmark threshold is not lowered and no missing joints are invented.
Both earlier failed reports remain retained. Subject inference, immediate
cancellation and all three model-lifetime checks passed in that runner.


### Saved local reference/edit checkpoint — 4 October, 14:02 IST

The final framing/reference/model-lifetime runner passed **9 tests in 46.25 s**.
It ran actual CPU multimodal inference on the attributed public full-body image,
a held-out top-32% crop and synthetic black pixels, without camera/microphone.
The full frame returned person/full-body in 10.495 s. The crop returned child/
review-needed in 9.308 s; age is not validated, and its landmark evidence was
upper 2/3, hips 0/2, lower 0/4. The heuristic abstained rather than extrapolating
missing joints. Black returned unknown/empty-or-unclear/unknown in 11.869 s.

The preceding 45.669-second runner failed its black-subject assertion despite
that safe unknown result. Acceptance was explicitly expanded to permit **only**
that exact three-field uncertain outcome, separately logged as abstention;
explicit absence recognition is not claimed. The earlier wrong generative
framing and the initial positive-portrait failures remain recorded above.
No thresholds were lowered to force a framing label. Model uncertainty text
may be vague. All observations require creator correction before planning.

All three shared-model lifetime methods and immediate vision cancellation passed.
This is separate from the **18-test / 18.560-second** speech-trim/export/paused-
preview/reference-decoder/container run already recorded. Its actual synthetic
speech cut shortened 8759 ms to a 6263 ms encoded video, preserving reviewed
caption timing, known speech and original hashes. No real creator recording or
AirPods playback was attempted. Exact runner and metrics remain ignored under
private/evidence/final-local-checkpoint-*; the earlier failed reports are retained.

The final APK is copied to output/apk/MiniFilm-research.apk, **52,838,059 bytes**,
SHA-256 **725ba52792cc8075c9b1fc32a96400028006b92235a4caada3670f11768c966a**.
Build and test APK installs succeeded. A normal MainActivity launch was accepted;
the phone remains securely locked, so this does not establish visible usability.
Camera and microphone remain denied. Manifest inspection confirms no Internet/
network-state permission. Separate Qwen/Whisper weights are absent from the APK;
ML Kit's bundled SDK assets remain. Full publisher license bytes match its
packaged notices. No private upload, cloud request or aircraft action occurred.

The six-page phase-one pitch was refreshed with current evidence and all pages
rendered and visually checked. PDF SHA-256:
**f226fb9fbe82da5db302ee253455f64243de4df7d270cd48950124136cb95087**.
The [attended device checklist](device-demo-checklist.md) maps actual app controls
to unlock, normal permissions, audible-earbud checks, capture, correction,
speech-cut preview/apply and MP4/ZIP saving. The accounted storage estimate is
**7.557 GB**, including historical archive/cache reserves and both phone/laptop
projector copies; inventory qualifications in storage.json still apply.
The goal remains active: attended UI/capture/AirPods, iQOO/NPU/Office Kit and
organizer-compliant event implementation/submission have not been completed.


Three parallel read-only source reviews found no concrete blocking defect in
trim/application/source bounds, clipped preview/capture duration, model ownership/
cancellation/JNI bounds, or normal reference-thumbnail ownership. These reviews
are not additional hardware tests. Unapplied trim/reference review state is
intentionally ephemeral across recreation; saved cuts/confirmed notes persist.
Decoder close suppresses late callbacks but cannot interrupt every blocking
provider/MMR read. Both practical limitations are in the attended checklist.


### Reviewed reference board and experimental quiet stop — 4 October, 14:38 IST

The installed pre-event app adds serial review of three selected reference
moments, editable/excludable notes and confirmed text-only board persistence.
Actual local frame inference remains sparse CPU vision plus conservative pose
landmarks; thumbnails and unconfirmed edits are temporary across recreation.
Selected canonical notes have a strict 210-character limit with no silent
truncation. Named role instructions remain creator-authored edits retained
verbatim after native generation, with a distinct label. Unknown fashion Detail
uses a disclosed creator-choice constraint. Embedded source clocks are rejected
from model captions. Neither mechanism is learned reference grounding.

Prompt-only preservation repeatedly failed. The 11-test runner failed one case
in 141.547 s: its native plan kept a generic Side/Closing despite reviewed cues,
while its ordinary introduction/fashion and reference checks passed. An earlier
28.438-second single-method green runner still invented a lapel because that
installed test lacked the newly added part/choice assertions. These reports and
all earlier failed outputs remain retained; see reference-board-evidence.md.

The final combined runner passed **23 tests in 92.759 s**: eight board cases,
13 synthetic quiet-stop policy cases and two finalized-container methods.
Actual three-frame inspection completed in **32.779 s**, preserving original
hash and requested 1000/4500/7500 ms association. Two actual native CPU plans ran
in **27.781/29.638 s**; exact named manual instructions and disclosures passed,
and an explicit confirmed left-pocket Detail bypassed the unknown constraint.
Fifteen malformed/time/duplicate cases failed before native load with one
main-thread error, no plan or fallback, zero resident handle and no model lease.
The prior same-source sparse-reference and ordinary unseen-plan checks passed
in the preceding runner. These are targeted mechanics, not general accuracy.

The explicit-detail native draft also said **“Walk to the left pocket”**. That
nonsensical Movement met shape/lexical tests and demonstrates a remaining quality
limit. Model captions can stay generic after manual directions are retained.
The Brief page explicitly requires review/editing of directions and captions;
no good creative output, whole-trend understanding or creator benefit is proven.

Direct now offers **Wait for a quiet pause · experimental**, off by default and
not persisted across recreation. It observes only scalar CameraX statistics
from an explicitly started recording; no extra microphone stream/model is added.
It needs prior valid energy and a fresh advancing one-second quiet window, and
allows at most eight extra seconds within the existing 60-second limit. Stop
settings are snapshotted for each take. Matching controller/take IDs reject old
events; duplicates, invalid/muted/error states and gaps invalidate evidence.
Manual Stop/background/error/finalize cancel immediately, while automatic quiet
completion retains normal sequence progression after Finalize. The 13 policy
checks cover bounded synthetic sequences only. Sound energy is not sentence
completion; thresholds and real microphone/AirPods behavior are uncalibrated.
Source review found no blocker in this wiring, not a hardware result.

Both APK builds/installations succeeded; the latest app received a normal launch
request. Window policy still reports Keyguard unlocked=false; camera/microphone
remain denied. No secure unlock, grant, camera/mic, private upload, cloud or
aircraft action occurred. The final APK is output/apk/MiniFilm-research.apk,
**52,838,059 bytes**, SHA-256
**9f8febfb45688a9462247f953b2b9e45aecf43d092087c7c5e2aedf818a7b3eb**.
The packaged notices match source bytes, separate core/projector/Whisper weights
are absent, and the manifest has no Internet/network-state permission. The only
post-test source change was the Brief review-warning text, rebuilt/reinstalled;
no functional source changed afterward. No new SDK/JDK or model download.

Qualified storage accounting is **7.546 GB**, including historical missing-archive
and cache reserves plus phone files/cache; exhaustive inventory remains limited
as recorded in storage.json. The goal remains active. New board UI/persistence,
real filming, quiet-stop calibration, audible AirPods, iQOO/NPU/Office Kit and
organizer-compliant competition source/accepted submission remain open.


### Planner comparison, causal repairs and installed checkpoint — 4 October, 15:52 IST

The default remains the pinned **Qwen3.5 0.8B local CPU** model. A separately
pinned official Qwen2.5 1.5B Q4_K_M candidate was streamed directly into a unique
phone-only partial file, with length/SHA checked incrementally and independently
on the phone before publication. No host weight copy remains; no new SDK/JDK
was installed. Its1,117,320,736 bytes and SHA
6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
match the official pinned Apache2 artifact; source/license metadata is in
[the comparison ledger](planner-model-comparison-feasibility.md). It is **not
promoted**: it was slower and less faithful on the measured full-style fixtures.

Both compact-prompt model methods failed (**2 tests /279.718 s**) with incomplete
or invented outputs. The full production-style raw comparison passed only native
shape/lifetime mechanics (**2 tests /214.062 s**); manual review still found the
baseline “Take the left pocket” error. The candidate lost requested poses and
product-use details and gave weaker captions. All twelve comparison outputs,
failures, pins and prompt-token counts remain retained. Numeric memory samples
observed maximum mixed-model PSS1,970,816 /1,957,154 KiB in the two experiments;
these are sampled maxima, not certified peaks or live-camera concurrency.

Source now bounds JSON whitespace, rejects narrow observed garment-destination
Movement phrases and single-letter Fashion labels, and requests asynchronous
planner cleanup before camera/editing paths. The initial10-method runner passed
9 and failed an explicit-Detail native board; its4-method diagnostic also failed.
The exact raw probe identified benign “Look at the camera” rejection and F/S/J/S/C
captions, rather than a Movement failure. A subsequent9-method repair passed
106.568 s but a6-method plain-jacket checkpoint failed one native case in42.786 s.
The exact plain probe then failed **1 test /30.933 s**, revealing both a benign
camera-facing pose and an invented zipper. These failures remain in the ledger.

The final Fashion source allows body/eyeline mentions of a mounted camera, while
a bounded English operation/hand-placement/pronoun filter rejects tested device
handling. Seven benign cues and18 synthetic operator cases passed independently
in draft/reviewed paths. Every Fashion draft without an explicitly named
creator-reviewed Detail uses the separately disclosed creator-choice instruction/
caption grammar. Ordinary free-text parts are not extracted/verified; creators
can author a named reviewed Detail or edit the draft themselves. No native retry,
hidden starter result or after-generation cue rewrite was added.

The two final Fashion runners passed **9 tests /62.159 s** and **4 tests /118.576 s**.
Actual raw/public plain plans took **28.910/30.837 s**, kurta **42.055 s**, hybrid
unknown/explicit boards **27.243/23.770 s** and raw explicit-board **23.832 s**.
Prompts670/670/668/697/664/664 tokens remained below the unchanged900 cap.
Full repaired outputs are in [the Fashion evidence](plain-fashion-camera-detail-repair.json).
Independent review retains ambiguous jacket-on-chest phrasing, repeated garment-
still directions and generic one-word captions. Raw Closing still substitutes
lens eyeline for requested face-forward stance; exact hybrid directions are
creator-authored retention, separately labelled, not learned reference grounding.

A shared-grammar Talking regression failed **1 test /40.897 s**, inventing an
unsupplied door-opening Cutaway; the future assertion was not reached. Talking
now decodes and validates a separately disclosed generic creator-choice Cutaway,
with two possible instructions/three captions. It does not extract/verify ordinary
free-text props; other four roles remain native drafts. The final Talking runner
passed **4 tests /67.135 s**, including two parser methods and two actual stories.
Train/umbrella callbacks took **39.353/27.692 s** with675 tokens each; full plans
were logged before independent semantic checks. The stronger future check requires
actual future/next-time wording in the instruction. Both fixtures retained their
supplied events and future lesson, with no tested invented props/places/events.
Repeated final lessons and generic captions still need creator editing;4s
allocations are not observed performance/speech fitting.
[Talking failure](talking-cutaway-failure.json) and
[repair](talking-cutaway-repair.json) remain separate evidence.

**17 final focused methods passed across three runners; this is fixture evidence,
not general creative accuracy, user benefit or an attended full shoot.** Source
review also repaired stale countdown/pose-break labels. Stop/sequence-disable
returns a pre-recording canceled state, but excludes a take already starting/
recording/finalizing; disabling future sequence advancement does not claim that
current take was canceled. Planner cleanup is queued after its worker, not proof
of zero momentary allocation overlap or a lower camera memory peak. These UI and
camera paths remain source-reviewed rather than attended verification.

Both builds/installations succeeded. Final app:
output/apk/MiniFilm-research.apk, **52,838,059 bytes**, SHA-256
**8da4469e1fc11a231e86d666e0128426c6bc6b23c68082996dd25d38b0252fac**. Packaged publisher notices exactly match source:
31,635 bytes, SHA02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68.
Separate Qwen core/projector/Whisper weights are absent from APK/Git; ML Kit
bundled SDK assets remain. Manifest has no Internet/network-state permission.
The installed baseline hash was independently rechecked unchanged. A normal
MainActivity launch request succeeded; secure keyguard remains unlocked=false
and camera/microphone remain denied. No unlock/grant bypass, camera/mic, audible
AirPods, private upload, cloud inference, outgoing submission or aircraft action
occurred. No source change followed the final checks.

Qualified storage is **9.279 GB**,
now including measured historical-phone files/cache and all three known installed
project APKs in addition to workspace, target-phone data and historical archive/
cache reserves. Missing full archive and untracked/profile/oat cache limits
remain; this is not a certified complete inventory. Neither backup nor credentials
were deleted/changed. Prepared phase-one PDF remains unchanged and visually
verified at its earlier digest. The goal stays active: live filming/AirPods,
visible UI/pause calibration, required iQOO/NPU/Office Kit, eligible event-created
competition source and accepted submission remain open.

### Movement clause regression — 4 October, 16:04 IST

A new deterministic phone test reproduced six misses: three synthetic invalid
cues passed both draft and reviewed-cue parsing. Later steps text exempted
garment-taking, and steps toward a pocket were not checked. The failing method
ran in0.046s; its report remains in private/evidence/movement-clause-red-test.log.
The narrow guard now checks the first action without that global exemption,
stops scanning at recognized clause/action boundaries and includes a bounded
Take + step(s) destination prefix. Prompt, grammar, models and callbacks are
unchanged. The complete class passed **9 methods /0.153s**: three invalid and
four valid synthetic cues in both paths, and exact replay of four recorded
native Movement phrases. No new native generation ran. Unlisted paraphrases,
later clauses, semantic validity and walking safety are not established.

Both APK builds/installations succeeded. Saved app:
output/apk/MiniFilm-research.apk,52,838,059 bytes, SHA-256
**6f9b8465208ec2eaec8d84a91b388a13e8363263dc14efa10a8bc5df7cc586b2**.
Notices remain byte-identical; separate GGUF/Whisper weights are absent; the
manifest has no Internet/network-state permission. Exact planner snapshot SHA
8bfa269905488828f3742dd15dbc44ade03d45fe10171907eb4d2f34eb1bec26 is
retained privately. Normal launch was requested successfully; camera/microphone
remain denied. No capture, audible playback, upload or aircraft action ran.
Qualified storage is **9.280GB**, retaining the earlier inventory limits.
The goal remains active; attended filming/AirPods and event gates remain open.
Earlier native results are retained evidence, not freshly rerun.

### Save retry and audio interruption — 4 October, 16:14 IST

Source review found blocked Files saves were not actively canceled and failed
saves discarded their prepared ZIP. A bounded DocumentCopier now independently
requests signal/stream cleanup and suppresses stale callbacks. Success follows
flush and both closes; providers can still ignore cancellation and leave partial
destinations. Main keeps the ready ZIP for retry, invalidates it on metadata
changes, labels last exports and offers a reversible Select no takes control.
Spoken preparation handles token-matched focus loss/noisy output, including
between cues; future sequence/automatic pose speech pause until explicit resume,
while an already-starting/recording take remains running.

**16 methods /4.445s passed**: seven document checks, seven silent synthetic
interruption checks and two unchanged real-engine synthesis-to-file callback
checks. These used no Activity, camera, microphone or audible playback. Full
cases, official contracts and limitations are in
[document/audio evidence](document-audio-evidence.md). Main UI integration is
source-reviewed; two new visible acceptance checks are prepared, not run behind
keyguard. The post-test picker-message/snapshot checks were rebuilt; tested
helper/speech classes stayed unchanged. No new native model run occurred.

Both final APK builds/installations succeeded. Saved and independently hashed
installed app:52,838,059 bytes, SHA-256
**4ad9095bfbef9007961a2212b9138e52456578974dfd7197f5f5223be9954724**.
Notices remain identical; separate model weights are absent; no Internet/
network-state permission. Normal launch succeeded behind showing keyguard/
sleeping screen; camera/microphone remain denied. Qualified storage **9.280GB**
retains the missing-archive/inventory limits. No private filming/playback/upload,
aircraft action, permission bypass or outgoing submission. Attended Files,
AirPods, full creator reel and event gates remain open; goal remains active.

### Voice readiness and request cancellation — 4 October, 16:33 IST

The combined runner passed **24 methods / 10.313s**, including six new request
lifetime checks, six synthetic recognition lifecycle checks, seven interruption,
two silent TTS callbacks, two readiness queries and unchanged trim cancellation.
Actual padded synthetic clip transcription returned three bounded cues in
5,185ms with expected words and unchanged source. Per-request native cancellation
cannot be reset by another entry; queued/rejected Java work releases its handles.
No active abort-time guarantee follows. Details: [transcription evidence](transcription-evidence.md).

Android's dedicated service is present, but exact en-US metadata reports
installed=false, pending=false, supported=true. The English model requires a
download; none ran. No earbud outputs were available. Permission stayed denied
and no microphone/audio playback occurred. Main now has explicit Stop voice
input and stops stale speech when editing/planning; visible acceptance is only
compiled behind current keyguard. [Voice readiness](voice-input-readiness.md)
records the distinction. A separate opt-in Whisper brief path is now being
prepared using the existing verified model; it is not yet a tested feature.

Both APKs built and installed. Saved/installed app hashes match:
52,838,059 bytes, SHA256
**13bc15086c4c12b8d59427ecbc68f71d440493bd089ffe9099f1c8959f46ad75**.
Notices remain identical; separate weights are absent; no network permission.
Normal launch requested successfully. Last qualified storage was9.280GB before
these small source/test changes; no SDK/JDK/model download was added. Real capture,
AirPods, eligible event code and accepted submission remain open; goal stays active.

### Local spoken-brief alternative — 4 October, 16:46 IST

Because Android's English recognition model is absent, Brief now includes an
explicit local Record → Stop & review words → corrected draft → Use this brief
flow using the installed Whisper model. Normal permission approval requires a
fresh Record tap. No automatic application, planning or filming. Cancel/background
discard recording and suppress stale drafts. Recorder output is configured for
AAC/MPEG4 mono 16 kHz/64 kbps with encoder/main 45-second limits; limit paths discard.
Native release failure retains protection and refuses another recorder until
cleanup succeeds. Four owned slots, safe resource hooks and exact once-per-process
orphan reconciliation preserve active readers and unrelated files.

The combined runner executed 24 methods /3.720s: **23 passed, one audio-remux
fixture failed before inference**. Fourteen fake-recorder checks, eight native/
request/resource checks and unchanged trim cancellation passed. The remux EOF
helper incorrectly skipped valid negative priming PTS; corrected fixture-only
code then passed **one method /3.823s**. It preserved all 412 AAC packets/hash,
rebased 21,333µs, and measured actual audio-only 8789ms separately from source 8759ms.
Real CPU Whisper returned three bounded cues /3,553ms with expected synthetic
words, main callback, unchanged source and cleanup after worker termination.
Padded-video regression also returned three cues /3,310ms. Thus 24 methods passed
across these runs; the failed report is retained. [Voice evidence](voice-input-readiness.md)
records exact cases and limits. Three new review-dialog UI checks are compiled,
unrun behind keyguard. Fake recording/remux do not establish actual MediaRecorder
capture, permission UX, microphone/earbud routing or live speech accuracy.

Both builds and installations succeeded. Final saved/independently hashed
installed app:52,838,059 bytes, SHA256
**15eafa4b54a1e15c578971a304d7577303f49aedf6f8abec385109fafdf6c18c**.
Publisher notices remain 31,635 bytes/SHA02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68;
separate Qwen/Whisper weights remain absent from Git/APK and no network permission.
Normal launch succeeded behind showing keyguard/sleeping screen; camera and
microphone remain denied. No private capture/audio playback/upload, aircraft
action, permission bypass or outgoing submission ran. Qualified storage is
**9.282GB**, under 10 GB aim/15 GB limit with retained missing-archive/cache/oat
inventory limitations. No model/SDK/JDK download, backup removal or credential
change occurred. Full attended creator workflow, AirPods, iQOO/NPU/Office Kit,
eligible competition code and accepted submission remain open; goal stays active.

### Exact AAC proof and recorder handoffs — 4 October, 16:59 IST

The new focused VoiceBriefCodecTest passed **one method /6.498s**. Existing
labelled synthetic speech decoded to 140,279 PCM samples and was actually encoded
on the phone as AAC-LC/16kHz/mono; extractor/header checks confirmed that format,
with encoder-reported target64kbps. Its 139 AAC packets produced an independently
measured8896ms audio container. Actual local tiny.en returned three cues/3974ms,
expected synthetic words, bounded timing and one main callback. Source/input
hashes stayed unchanged, and its own test file was removed after worker exit.
No microphone, MediaRecorder or playback ran. [Voice evidence](voice-input-readiness.md)
records the exact requested-format result and separate actual-recording gate.

Main requests asynchronous planner cleanup before a voice brief, clears only
current-generation terminal busy state before foreground guards, and checks
active/retained recorder ownership before all microphone starts. This prevents
the reviewed paused-completion seam from leaving controls disabled and refuses
new microphone ownership while release remains unconfirmed. These are compiled
source repairs; no instant native-memory release or physical recovery is proven.
Six unlocked-only voice UI methods are compiled, unrun, including three new
helper/fake-backend checks. Actual callback race timing, loaded-planner memory
handoff, live mic/earbud route and visible permission/Stop/Cancel need attendance.

App and test builds/installations succeeded. Saved APK52,838,059 bytes; its
independently measured installed SHA256 matches
**1ab6cc3d804127113f21a9fd29fdce5360a0911351ccbd2df4ee3ec1a346231b**.
Publisher notices remain unchanged; separate Qwen/Whisper weights are absent
from the APK; no network permission. Normal launch was requested successfully
behind showing keyguard, with CAMERA/RECORD_AUDIO still denied after the runner.
Qualified storage **9,282,791,273 bytes (9.283GB)** remains below the10GB aim and
15GB limit; it now also includes11,264 bytes of test-package private allocation.
Missing-archive/shared-cache/oat inventory limits remain. No new model/SDK/JDK
download, private capture/playback/upload, permission bypass, aircraft action,
credential change or backup removal occurred.

The in-app deadline refresh redirected to sign-in, so it supplied no new deadline
evidence or form action. The earlier authenticated date/countdown observation
remains authoritative for this ledger:5October, approximately23:59IST, timezone
inferred. No attestation/submission/receipt. Full attended creator workflow,
AirPods, iQOO/NPU/Office Kit and eligible event-written delivery remain open.
This goal turn made implementation and real-codec progress; the goal stays active.

### Genuine export process-death recovery — 4 October, 17:17 IST

A real 12-cut/36-second Media3 export from the existing labelled synthetic speech
fixture was externally stopped while still encoding. The host observed actual
progress 4%, growing output 68,121→121,399 bytes, matching live process, showing
keyguard and denied capture permissions. After force-stop, the process was absent
and the same journal remained ENCODING with 121,399 bytes. The intentionally
interrupted runner reported Process crashed; exit 0 does not make it a green test.

Fresh-process recovery passed **one method/0.242s**: real ExportRecovery cleaned
exactly one job/no warning, removed its temp/journal, and preserved original
hash/size/mtime, an unrelated cache sentinel and the exact earlier published
video/edit pair. Recovery returned that prior pair. A normal app launch was
requested afterward; a read-only check retained only the original COMPLETE
journal and found the test marker absent. Details and external kill provenance:
[actual process-death evidence](export-process-death-evidence.md).

The first attempt missed the kill window and failed one method/13.274s when
encoding advanced to SAVING; its retained journal was later READY. No host kill
occurred. Explicit reviewed cleanup passed one method/0.322s before the corrected
attempt. This cleanup is not recovery evidence; both failed and interrupted
reports remain retained. No production repair was needed. Test builds/installations
passed; the saved/installed app remains 52,838,059 bytes, SHA256
**1ab6cc3d804127113f21a9fd29fdce5360a0911351ccbd2df4ee3ec1a346231b**.

Read-only project gallery accounting passed **one method/0.777s**: 19 app-owned
Movies/MiniFilm rows, zero pending, **5,598,886 logical bytes**, using the larger
provider-size/fd-stat observation per row without reading media contents. These
outputs are now included separately from private files/cache/APKs. The updated
qualified snapshot is **9,288,930,775 bytes (9.289GB)**, below the 10GB aim/15GB
limit. Missing-archive/global-cache/oat/provider-thumbnail inventory limitations
remain; this is not a certified full-machine inventory. No model/SDK/JDK download,
private capture/playback/upload, permission bypass, aircraft action, credential
change or backup removal occurred. Only newly created synthetic trial outputs
and markers were cleaned; existing originals/results stayed intact.

The next useful reference-reel addition is explicitly reviewed English speech
context, alongside the current frame notes, using installed Whisper. This is
design-only so far: source binding, timed editable draft, concise explicit
confirmation and a 500-character combined prompt budget without truncating visual
notes. It cannot be presented as music-beat or whole-trend understanding. Full
attended creator/AirPods workflow, iQOO/NPU/Office Kit, eligible event-written code
and accepted submission remain open. This turn produced genuine recovery and
storage evidence; the goal remains active.

### Reviewed reference speech and source ownership — 4 October, 17:37 IST

The reference workflow now reads English audio from an explicitly selected local
video with existing tiny.en. Its full source-timed draft remains visible; only
explicitly corrected 1–210-character context is saved for the matching source.
Saved words can be edited without reading the video again. Both dialogs show
the combined 500-character budget, including labels, typed brief and visual
notes; overflow preserves previous context and never truncates inputs. Speech
precedes the canonical reviewed-moments suffix. It never starts planning,
recording, playback or microphone access. [Detailed evidence](reference-speech-evidence.md).

Read-only review found and repaired a real ownership race: an old speech reader
could drain after source replacement and clear a newer frame inspector's busy
state; specialized cancel also skipped that inspector. Reader resource cleanup
now remains distinct from ownership of busy. A separate existing visual race
cleared busy before checking a replaced source or attached old frame notes to
it. Board/frame callbacks and retained review actions now check source/generation
first. Replacement closes old visual helpers and disposes owned pixels without
recycling an attached review image. These MainActivity repairs were reviewed;
their seven synthetic unlocked UI methods compile but are **unrun** behind lock.

The actual headless reference-speech runner passed **7 methods/47.335s**:
six boundary/parser methods plus one tiny.en → explicit test-authored correction
→ actual Qwen3.5 0.8B CPU plan. The known synthetic source measured 8,759ms;
three timed cues took 4,516ms. A 216-character composed input returned five shots
in 42,400ms. Source SHA/size/mtime, main-thread callback delivery and worker/core
release passed. Hero/Closing directions are labelled creator-authored retention;
Detail is a disclosed creator-choice constraint. The other cues/captions remain
editable drafts. This is not learned grounding or artistic/usefulness evidence.

A focused related runner passed **6 methods/2.828s**: three frame-decoder checks,
two board cancellation/selection/bitmap checks without vision loading and
read-only gallery inventory. Gallery remained 19 owned rows, zero pending and
5,598,886 logical bytes. An initial log collector used the wrong tag; the scoped
MiniFilmStorageTest buffer supplied the actual aggregate. No runner failure.
Both builds/installations passed using existing tools. Saved/built/independently
read installed app match **52,838,059 bytes**, SHA-256
**90ad51be1bedab35941cf084c97f1bcb418e96ad7c6976fafa51d60c4bd9708f**.
Publisher notices unchanged; separate Qwen/Whisper weights remain outside
Git/APK, bundled ML Kit assets remain. No INTERNET/ACCESS_NETWORK_STATE permission.
CAMERA/RECORD_AUDIO still denied, keyguard showing before/after; normal launch
requested afterward. No attended visible launch is claimed.

The six-page Phase 1 PDF was refreshed and all pages rendered/visually checked.
Its 72,704 bytes/SHA-256
**459d47b48291297596ac4db66afc924bf686b3252062c692aa9f0a0b68c6f23b**
include the opt-in voice design, actual AAC/recovery evidence, clear live gates
and event-code disclosure. The previous PDF is preserved privately. A first
text probe missed a wrapped phrase; whitespace-normalized disclosure checks
passed without changing content. A dated builder is now retained under
prototype/phase-one-document. The Codex PDF tab is queued for this chat.

Read-only signed-in Chrome dashboard refresh at approximately 17:24 IST still
showed **5 Oct 2026** with 1 day, 6 hours, 36 minutes remaining, consistent with about
**23:59 IST on 5 October**, timezone inferred. The form was empty/attestation
unchecked; no field, upload, team setting, submission or receipt changed.

Qualified storage is **9,290,146,735 bytes (9.290GB)**, below the 10GB aim/15GB
limit, including the three installed APKs/private directories and owned gallery
logical bytes. Missing-archive/cache/oat/provider-thumbnail inventory limits
remain. No model/SDK/JDK download, private capture/playback/upload, permission
bypass, aircraft action, credential change, archive or original-file deletion.
The goal made source/device/document progress and remains active. Full attended
creator/AirPods capture, iQOO/NPU/Office Kit, eligible event-written code and
accepted submission remain unfinished; tonight's work continues within those
existing gates.

### Fresh emulator interface and actual Files save — 4 October, 17:54 IST

Reused installed Android 36 ARM64 image/emulator in a newly created empty AVD.
Physical phone stayed locked. Host camera/audio and snapshots disabled; camera/
microphone permissions denied, emulator airplane mode on. No model/SDK/JDK/image
downloads or private media. Existing other-project AVD was not started/copied/
modified. Boot27.587s and selected-port range warning retained.

Latest sequential runners passed **25 interface methods**: VoiceBrief6/7.843s,
ReferenceSpeech7/10.643s, AssemblyEdit2/4.049s, non-media workflow9/10.766s and
isolated synthetic demo1/2.246s. Initial Voice6/2fail, Reference7/3fail,
workflow9/1fail reports retained. Dialog clicks preceded queued OnShow custom
listener setup; split main-loop presentation/idle/click without weakening any
review/overflow/stale assertions. Files ActivityMonitor now includes ZIP MIME
type. These were fixture repairs, not proven production review defects.

Production: scroll-wrapped Trim & typography and validated persisted screen0–2;
import/reference-summary assign destination before save. Recreated Direct stays
inactive; Assemble keeps edits/package controls. New real-dialog tests preserve
exact5.746s source end, reject5.747/zero/negative/249ms trim bounds, retain complete
caption/title/cues and selected order, and invalidate prepared ZIP after trim.
Preference maps restored/equality checked; only owned tiny fixtures removed
after original-byte/mtime checks. Independent review found no blocking issue.
Default emulator trim fields and Save visibly stayed above its open keyboard;
this is one screen/density, not all-screen or attended physical layout evidence.

Used normal app controls and actual emulator DocumentsUI SAVE for three synthetic
clips to empty Downloads. Saved ZIP **614,367 bytes**, SHA256
**7f14ae3081083f01bfec89481804f7eb4e69fba58ca8cf0fc04115d6ac335823**;
CRC valid, three whole originals610,506B exactly match current app originals/
manifest hashes, three500–2500ms cuts align contiguous0–6000ms. This is one real
emulator Files save, distinct from physical providers, desktop import/Office Kit
and MP4/audio review. Full [interface evidence](emulator-ui-evidence.md) retains
runner logs, original failures, scoped screen/XML and Files proof.

Build1s/app+test installs passed. Built/saved/independently read physical installed
app matches **52,838,059B**, SHA256
**b0d67a683ac1ed5c38e99dc81bb317237d589c2c4e1778572946164ed589ac86**.
Notices unchanged, separate Qwen/Whisper weights outside Git/APK, bundled ML Kit
assets remain; no network permission. Camera/mic denied and keyguard showing;
normal Main launch requested. Metadata-only phone inventory1PASS/0.499s:19owned
rows/0pending/5,598,886B. No attended launch/capture/audio, aircraft action, private
upload, permission bypass or submission occurred.

Qualified temporary accounted peak **10,507,375,535B**, above10GB aim/below15GB
cap. Stopped/removed only this newly created AVD/registration; evidence retained.
After cleanup **9,291,985,083B (9.292GB)**, including three installed APKs, phone
files/cache/test allocation and owned gallery; historic archive/cache and incomplete
inventory qualifications preserved in storage.json. Other AVD, archive/model
files and local credentials untouched. Goal remains active; attended creator/
AirPods, iQOO/NPU, Office Kit, event provenance and accepted submission still open.

### Shoot cue ownership, supplied product facts and READY recovery — 4 October, 18:14 IST

Source now invalidates already-queued pose results on render/camera replacement/
end/background/destruction. Automatic posing speech waits for pending or active
explicit direction. Lens replacement creates a fresh pose/controller generation;
a second lens tap waits while actual preview binding is pending, while a stopped
controller permits selecting the next lens. Five new injected-state UI methods
plus seven existing interruption methods passed **12/6.974s**, and three selected
lens/launch/object workflow methods passed **3/4.094s** on a fresh unlocked
Android36 ARM64 emulator. No preview, microphone, native inference or playback
was started by those checks. Complete preferences restored; camera/mic stayed
denied. Live rebinding/pose/audio remains unproved.
[Exact shoot checks](shoot-cue-evidence.md).

New storage-box Product CPU fixture initially failed **1/27.862s**: actual
27.812s draft lost the supplied removable lid and index-card use. A Product-only
prompt repair maps supplied parts to Reveal and supplied actions/objects to
In use, illustrated by an unrelated pencil example. No fixture-specific response,
output merge/retry or model/grammar change. Original assertions preserved; added
example-leakage checks. Rerun passed **1/26.771s**, actual draft26.693s retained
lid/card action and requested own honest opinion. Existing fashion/mug regression
passed **1/57.426s** with actual requests24.761/32.630s. Direct output review found
no targeted fact inventions; repetitive lid coverage/mug captions remain. A
fixture used to repair prompting is not independent post-repair generalization.
[Product failure and outputs](product-detail-evidence.md).

Actual Media3 completed twelve3s synthetic cuts; genuine publisher wrote/synced
JSON, copied/flushed/closed **1,340,552B** to its pending owned gallery row and
wrote durable READY. A **test-controlled main-queue barrier** held publication.
Prepared host controller checked full marker/PID, denied camera/mic, showing
keyguard, exact row/journal ownership, stream/stat/temp lengths, temp/row SHA and
independent temp/edit hashes. Effective deadline used the earlier queue90s and
marker+45s bounds. External own-app force-stop at12:42:20.202UTC left process
absent and same READY/temp; interrupted runner said Process crashed/shell0,
**not PASS**. Fresh recovery passed **1/0.387s**: exactly1 cleaned/no warning,
pending row/temp/edit/journal removed, originals/sentinel/earlier complete pair
unchanged and baseline returned. Initial **1/0.038s** root-alias fixture failure
preceded new job/marker/sentinel and caused no host stop. Fixed only trusted
app-root spellings; unknown/private/traversal/child-symlink sources still refuse.
No production export/recovery repair needed. This is actual READY/pending
recovery in a controlled queue window, not natural timing or all kill boundaries.
[Publication evidence](export-publication-death-evidence.md).

Both source builds1s; normal app/test installs succeeded. Built/saved/independently
read installed APK matches **52,838,059B**, SHA256
**1d214f10c478ec38aa2da176fdc52e3ce8d75d09cdb07c9236205cb6c3bdc33a**.
Packaged notices match source31,635B/SHA
02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68.
Separate Qwen/Whisper weights absent from APK/Git; bundled MLKit assets remain;
no network permission. Normal Main launch requested behind secure keyguard;
camera/mic denied. Gallery metadata inventory passed **1/0.135s**:
19owned/0pending/5,598,886B. No attended launch/recording/audio, aircraft action,
private upload or submission occurred.

Reused existing SDK/JDK/emulator/image; fresh own AVD boot27.990s, correct port
range, host cameras/audio/snapshots off and airplane mode on. Qualified temporary
peak **10,671,163,551B**, above10GB aim/below15GB cap. Stopped and deleted only
this new AVD/registration after tests, retaining logs. Final adjacent accounted
footprint **9,288,493,747B**, below aim/cap, including all three installed project
APKs, phone/test allocation and owned gallery. Missing-full-archive reserve and
incomplete cache/oat/provider inventory qualifications persist in storage.json.
No new models/SDK/JDK/images downloaded this probe. Goal remains active: real
creator/AirPods, iQOO/NPU/Office Kit, eligible event provenance and accepted
submission remain unfinished.

### Camera-error retry and subtitle visibility — 4 October, 18:27 IST

Supported source defects repaired: failed provider/bind left previewRequested
true and blocked lens choice; failure now calls normal stopPreview cleanup before
error delivery. Main camera error invalidates pose generation, ends session,
cancels countdown/stops speech and disables pose advice, preserving the error
against delayed frame results. Seven shoot-state plus three assembly-review UI
methods passed **10/14.428s** on a fresh unlocked API36 ARM64 emulator with
camera/mic denied and host camera/audio disabled. New tests exercise reflected
partial unbound analysis failure and actual denied Main camera start, without
frames/inference/playback/recording. Earlier passing history remains in
[shoot-cue evidence](shoot-cue-evidence.md). Full live sequence remains unproved.

Subtitle review/export/package previously accepted overlapping text; the renderer
uses the first matching cue, so a nested cue could never appear although JSON
marked it visible. Shared preflight now rejects overlap with correction advice
and preserves every word/time/list position. Only temporary interval copies
are sorted; adjacent endpoints and unsorted nonoverlap remain allowed. Actual
review rejected nested/partial/same-start edits without changing saved state or
removing dialog words; adjacent edits saved/restored. Three headless phone
checks passed **3/4.192s**, including six no-output/no-source-open rejections
and a real two-cut **4064ms**,720×1280 export. Short/long adjacent caption
appearances/gaps, per-cut source/timeline offsets and portable ZIP/original
hash passed; appearance measurements are not OCR/exact-word recognition or
audio review. Kept labelled completed MP4/JSON; removed only returned test ZIP
and callback-verified own COMPLETE journal so later Main launch cannot adopt it.
No original/pref modified by headless check.
[Subtitle visibility evidence](subtitle-overlap-evidence.md).

Fresh ordinary cream-cotton-overshirt/dark-jeans Fashion CPU fixture passed
**1/40.082s**, native callback **39.998s**, on its first run, with no Fashion
prompt/model/grammar/response change. It retained both garments and supplied
descriptors, small steps/side turn and held closing pose, with no targeted
unsupplied facts. Supplied chest pockets were NOT selected: ordinary Detail is
a disclosed creator-choice constraint. Other roles are native but mostly generic.
This is one targeted observation, not broad creative quality, learned pocket
selection or real-time coaching. [Full fresh draft](held-out-fashion-evidence.md).

Shot-edit form now scrolls. Normal own-emulator UI showed all four starter-shot
fields and Cancel/Save above the open keyboard at default Pixel9 density; scoped
XML/screens remain under private/evidence/shot-edit-*. No edited characters were
entered/saved in that manual check. This is one layout, not all-screen or physical
UI acceptance. Fresh AVD reused existing image/tools, boot-complete observed
17.637s after polling began (not total boot duration), airplane mode enabled;
no model/SDK/image/JDK download. Removed only own fresh AVD/registration after
tests/manual review; other AVD/archive/credentials untouched.

Build1s/app+test installs passed. Built/saved/independently read physical installed
APK matches **52,838,059B**, SHA256
**2245a6774e810b7a75496e1258a9089270718922efaee3e494a568f2f3777f03**.
Notices remain source-identical31,635B/SHA
02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68.
No Internet permission; separate Qwen/Whisper weights absent from Git/APK,
bundledMLKit assets remain. Gallery metadata-only inventory passed **1/0.138s**:
20owned/0pending/5,892,830B. Camera/mic denied/keyguard showing; normal launch
requested behind lock. No attended capture/audio, permission bypass, private
upload, aircraft action or submission.

Qualified own-emulator snapshot peak **10,441,104,459B**, above10GB aim/below15GB
cap. After cleanup adjacent accounted footprint **9,293,118,539B (9.293GB)**,
including all3 installed project APKs/phone+test allocation/owned gallery. Same
historical missing-archive/cache/oat/provider qualifications persist. Goal active;
creator/AirPods, iQOO/NPU/Office Kit, permitted event-code provenance and accepted
submission still required.

### Selected missing-subtitle batch — 4 October, 18:40 IST

Assemble now offers **Draft missing subtitles for selected takes**. It snapshots
up to twelve missing-subtitle takes (each source at most three minutes), reads
duplicate URIs once and delivers independent cue/list copies. Existing subtitle
words and unselected takes are skipped. Each real reader must acknowledge native
idle before draft delivery or the next read. Cancel/background suppress late
results, keep completed saved drafts and hold busy until drain; no automatic
resume. New words retain `whisper-tiny.en-draft` provenance and need review.
Whole-batch preflight prevents a partially started invalid selection.

Fresh empty API36 ARM64 emulator passed **15/13.077s**: six model-free coordinator,
six actual-button/persistence/cancellation/background/ownership UI and three
existing assembly-review methods. No fake weights, media, microphone, inference
or playback were used by those checks. Headless Nothing Phone test passed
**1/16.177s**, using two existing labelled synthetic encoded-AAC sources and real
Whisper tiny.en CPU reads: **4438/5284ms**, maximum one reader, **3 independent
drafts/0 failures/2 skips**, batch9735ms. Actual Media3 export5458ms produced a
**20,387ms**,720×1280 reel against a20,251ms nominal timeline, preserving all
source/timeline subtitle offsets and raw draft provenance in JSON. Caption
region pixels on each cut and original SHA/size/mtime preservation passed;
appearance is not OCR, sound review or general accuracy. No Activity/preferences
were changed by that physical check. Only its callback-verified COMPLETE journal
was detached; labelled MP4/JSON remain. [Full batch evidence](subtitle-batch-evidence.md).

Build1s and normal app/test installs succeeded. Built/saved/independently read
physical installed APK **52,838,059B**, SHA256
**6aa93419bc66b50f967efb2ccda99fa5bc8db72187367aa1fd71a5a5793eb6a8**.
Notices match source31,635B/SHA02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68;
no Internet permission or separate Qwen/Whisper weights in APK/Git. Bundled
MLKit assets remain. Metadata inventory passed **1/0.148s**:21owned/0pending/
6,614,976B. Normal launch requested behind secure keyguard; camera/mic denied.
No attended filming, AirPods playback, permission bypass, aircraft action,
private upload or submission occurred.

Existing tools/image reused, boot-complete observed8.579s after polling began,
host camera/audio/snapshots disabled, airplane mode enabled. Removed only own
AVD/registration after tests. Qualified temporary peak **10,425,629,653B** above
10GB aim/below15GB cap; final adjacent accounting **9,294,420,949B**, below both.
Missing archive/cache/oat/provider qualifications persist in storage.json; no
new model/SDK/JDK/image download. Goal active; real creator/AirPods, iQOO/NPU,
Office Kit, eligible event provenance and accepted submission remain unfinished.

### Late saved-take recovery and speech handoff — 4 October, 18:57 IST

A source-backed lifecycle race left a valid late-finalized take absent when the
replacement Activity scanned before the old recorder finished. Durable private
shot ID/title/caption metadata now precedes recording, and usable Finalize records
actual container facts before the closed-controller UI guard. Main recovers on
idle resume, startup/Assemble and explicit **Find saved phone takes**. New results
are unselected, preserve prior edits/order/selection, and deduplicate canonical
file aliases. Live pending sources are skipped. Unreadable media, unknown files
and malformed notes remain preserved; valid metadata survives a fresh process.
[Recovery evidence](capture-take-recovery-evidence.md).

Fresh empty, denied-camera/mic API36 ARM64 emulator passed **19/28.955s**: one new
recovery UI method (entry/manual/resume/recreation), six batch UI, three assembly,
seven shoot-pose state and two general lifecycle/ready-ZIP checks. New UI media
were locally generated synthetic DemoAssets copies in an exclusive injected
store, with full preferences/source preservation and no capture/model/audio.
Physical headless checks passed **25/42.327s**: two new recovery-store, two actual
container-finalization validators, two same-reader ASR completion, eight request/
cleanup cancellation, nine speech-trim, one real subtitle-batch/export and one
encoded voice-codec method. No native failure. Recovery retained exact5746ms
container/shot facts; existing alias edits, unreadable/unknown/symlink and bad-note
preservation passed. Batch regression retained a20,387ms720×1280 reel/JSON against
20,251ms nominal with two serial reads/three independent drafts and originals intact.

Separate synthetic pending-record stage passed **1/0.077s**. Runner exited
normally; root verified its old process already absent, pending journal unchanged,
known fixture/copy/metadata/sentinel size/SHA and denied camera/mic/keyguard.
No external force-stop occurred. Fresh-process recovery passed **1/0.062s**, with
changed PID, one unselected5746ms take, original shot facts, sentinel/original
preservation and idempotency; only its own copies/marker/sandbox were removed.
This proves persisted staged-file recovery, not killed CameraX finalization.

The single-clip speech callback previously could reach Main before native request
release/internal busy reset. Success/error delivery now follows native release,
busy clear and cleanup drain. Actual callback-immediate reentry passed: intentional
missing-source error→ASR3734ms; ASR3723ms→trim native4006ms/total4065ms. Every
callback verified released request/registry, main-thread ownership and unchanged
source SHA/size/mtime. Deliberate missing-source/failing-hook warnings are test
inputs, not regressions. No Main background-policy change or measured human tap
failure is claimed. [Speech handoff evidence](transcriber-completion-evidence.md).

Retained two pre-install compile failures: missing recovery validation parenthesis
and root's duplicate onResume. Corrected syntax/merged existing lifecycle handler;
no weakened validation. Final app build820ms/test build1s and normal installs passed.
Built/saved/independently read installed APK **52,838,059B**, SHA256
**5b43d6797073351921e34a1dcf761651bae336f72d338a538192d5306d7aaad3**.
Notices source-identical31,635B/SHA02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68;
separate Qwen/Whisper weights absent from Git/APK, bundledMLKit assets remain.
No Internet permission. Gallery metadata inventory passed **1/0.152s**:
22owned/0pending/7,337,122B. Normal Main launch requested behind secure keyguard;
camera/mic remain denied and staged test marker is absent. No permission grants, private upload, aircraft action,
attended filming/audio, accepted submission or eligible event-code claim.

Own empty AVD reused existing tools/image; boot-complete observed12.492s after
polling began, host camera/audio/snapshots off and airplane mode on. Removed only
own AVD/registration after tests. Qualified temporary peak **10,427,729,419B**,
above10GB aim/below15GB cap; final adjacent accounting **9,296,520,715B**, below
both. Missing archive/cache/oat/provider qualifications persist. No new models/
SDK/JDK/images downloaded. Goal active; real creator/AirPods, iQOO/NPU, Office Kit,
permitted event provenance and accepted submission remain unfinished.

## 4 October, 19:14 IST — explicit shot assignment and pickup review

Added creator-reviewed many-to-many take→plan assignments, selected-take counts
and explicit next-missing-shot direction in Assemble. Fresh plan namespaces
prevent old shot-N assignments from satisfying a replacement; historical IDs
persist but do not count. Captured/imported/recovered takes start unassigned.
Only selected local-source metadata with valid trim bounds and explicit current
IDs counts; no visual quality or semantic coverage is inferred. Pickup opens
Direct without camera/session/microphone/countdown/sequence. Save/Clear requires
current foreground dialog ownership; stale dismissed buttons cannot mutate.

Additive device/portable edit JSON carries mapping IDs with creator provenance;
empty mappings say unassigned. Both snapshots isolate lists. Portable privacy
behavior keeps original device URIs/private capture IDs out. Saved mappings
invalidate a changed ready cache package. [Full assignment evidence](shot-assignment-evidence.md).

Physical headless runner passed **17/5.915s**: five new core, two new serializer/
actual-ZIP, six package, three overlap/actual-export and one metadata-only media
inventory checks. ZIP retained two selected cuts, deduplicated original SHA and
confirmed mapping despite later caller edits. Actual unassigned encoder regression
retained4064ms/two cuts/both adjacent subtitle shapes/timeline/original hash.
Fresh emulator initial **31/48.004s**, one new fixture failure: editing prefs
while old Activity lived was overwritten by legitimate onStop save. Fixture
now closes old Activity before injecting restore data; production/assertions
unchanged. Corrected four new UI methods **passed4/9.198s**; initial27 unchanged
regressions passed. Failure retained. Real default screen visually inspected,
with readable five-row assignment/pickup panel above settings; this one layout
is not physical creator usability. Airplane broadcast rejection retained and
replaced by successful supported connectivity command/setting verification.

App/test build1s, corrected test-only build743ms; normal installs passed. Built/
saved/independently read installed APK **52,838,059B**, SHA256
**608b8a58d5ec28ca27269bb95660e8df9e758a09ed84cbe640a268fe2623d071**.
Notices31,635B/SHA02797d896355991c613f9f2081f2687b4328ea63362cc355f8baa9955f89bc68
unchanged; separate Qwen/Whisper weights remain excluded from Git/APK, MLKit
assets remain. No Internet permission. Phone camera/mic denied/keyguard showing;
normal Main launch requested behind lock. No unlock, recording, permission
grant, private upload, aircraft action, real AirPods sound or submission.
Gallery23 owned/0 pending/7,631,066B. Own temporary AVD/registration removed;
other AVD untouched. Qualified temporary peak **10,493,207,187B**, above10GB
aim/below15GB cap; final adjacent **9,294,561,939B**, below both. No new models,
SDK/JDK/images/dependencies downloaded. Existing archive/cache qualifications
persist. Goal active: attended creator capture/AirPods, learned semantic review,
iQOO/NPU, Office Kit, eligible event source and accepted submission unfinished.

## 4 October, 19:38 IST — editable plan context and readable shoot notes

Current plan IDs/order/names/directions/captions/target durations/source label now
freeze at export dispatch and accompany device/portable JSON. Nonempty portable
context adds human `shoot-notes.txt`, exact current-ID assignment names and an
unresolved label for unknown/earlier IDs. Per-cut original shotId/sourceUri fields
stay omitted; explicit mappings/current plan IDs remain intentionally included.
Copied original media retains embedded metadata. Plans are metadata, not inferred
visual quality, semantic coverage or creator approval.

Plan edits/order/source invalidate cached packages. Real completed ZIP is bound
to dispatch state; edits during worker execution remove the now-stale ZIP. Invalid
bounded context fails before work without rewriting edits. [Full measured evidence](shot-plan-export-evidence.md).

Physical headless checks passed26/7.049s, fresh own emulator interface checks22/
85.366s. Targeted verified synthetic ZIP retention passed1/0.165s. Actual encoder
published720×1280 H264/AAC nominal1000ms/encoded1043ms, independently decoded and
host duration1.043356s; frozen context/mappings/captions and original SHA verified.
Portable synthetic ZIP has4entries,2cuts/4000ms,2plan shots/one original with exact
hash/readable notes. Saved MP4/deviceJSON and ZIP are separate labelled fixtures.
No attended camera/microphone/AirPods or useful learned direction claim follows.

Final app/test build1s; retention test-only build793ms; normal installs passed.
APK52,838,059B/SHA904c9c01e60283c8c05826f5463900cb05c89c5fa0ec337615c65def1d75e869
built/saved/independently read installed match. Notices31,635B/source-identical;
separate Qwen/Whisper weights excluded, bundled MLKit assets remain. No Internet
permission. Gallery metadata25owned/0pending/7,954,004B. Only exact verified retained
synthetic test cache/marker removed after durable host copy; own AVD/registration
removed, other AVD untouched. No new model/SDK/JDK/system-image downloads.
Qualified temporary peak10,514,539,385B, above10GB aim/below15GB cap; final adjacent
9,295,612,529B, belowboth. Existing archive/cache/inventory qualifications remain.
Normal Main launch requested behind secure keyguard; final read-only verification
confirmed camera/mic denied and no Internet permission. No unlock or grant.
Goal active: attended creator capture/AirPods, useful learned semantic direction,
iQOO/NPU, Office Kit, eligible event code and accepted submission remain open.

## 4 October, 19:57 IST — opt-in review of captured cut framing

Assemble now offers **Review cut framing**: three requested quarter/midpoint/
three-quarter source frames, an approximate center 9:16 crop and bundled local
pose hints in a temporary, scrollable review. Plain uncertainty stays visible.
The review leaves selection, cuts, subtitles, typography, mappings, plan and
reference context unchanged; it supplies no quality or coverage score. Cancel,
background, source/trim/take changes and dismissal suppress stale publication
and recycle owned review images. No camera or microphone opens.
[Full checks, retained failures and limits](take-framing-evidence.md).

The initial physical runner passed eight of nine methods in 11.476s (five core
and three actual native). Crop parity failed only its Android rotation fixture
expectation: ffprobe's counterclockwise90° matrix corresponds to Android's
clockwise270°. The corrected test-only expectation/log kept every pixel assertion.
Its targeted rerun passed **1/7.412s**, including both upright and rotated inputs.
Actual Media3 Clean exports were720×1280, nominal2000ms/encoded2067ms. Maximum
whole/center mean RGB error was2.3097/3.1284 upright and1.03865/2.2087 rotated across
three samples. The annotated static public person fixture gave three full-body
landmark labels; the flat synthetic fixture gave three empty/unclear samples.
Decode/crop/pose took1824/1841ms respectively. Source SHA/size/mtime, preferences,
model metadata and denied capture permissions stayed intact. Native cancellation/
reuse passed, but isRunning tracks the helper's worker/results, not pending ML Kit
task completion. Timeouts can leave up to three tasks per review with no proven
global outstanding-task bound.

The initial fresh-emulator runner passed26 of27 methods in140.605s (22 regressions
and four new checks). One background fixture timed out after polling an Activity
through ActivityScenario.onActivity while CREATED. The test-only repair captures
reviewer identity while RESUMED and polls its thread-safe state while actually
CREATED, retaining real background transitions and all cleanup assertions. The
five-method rerun passed **5/8.451s**. Production stayed unchanged for both fixture
repairs; failures remain retained. The actual app's synthetic DemoAssets → local
framing modal was also visually inspected at the top, through all three samples
and at Done. This is one synthetic layout, not attended creator usability.

App/test build1s, rotation test build559ms and corrected UI test build597ms; normal
installs passed. Current built/saved/independently read installed APK52,838,059B:
SHA256168406ff0a88119d1511cdf205184ba43c3d4cff2c2b312c881a0e01d170ea4f.
Notices31,635B/source-identical; separate Qwen/Whisper weights remain excluded from
Git/APK and bundled ML Kit assets remain. No Internet permission. Read-only gallery
inventory passed1/0.172s:28 owned/0 pending/8,319,196B. Final normal Main launch was
requested behind secure keyguard; camera/microphone denied, no unlock/grant,
private upload or aircraft action.

Only the own AVD/registration was removed; the other AVD stayed untouched.
Qualified temporary peak **10,903,427,857B**, above10GB aim/below15GB cap; final
adjacent **9,302,457,105B**, below both. Existing archive/cache/inventory
qualifications persist. No new models/SDK/JDK/images/dependencies downloaded.
Goal active: attended capture/AirPods, useful learned direction/semantic coverage,
iQOO/NPU, Office Kit, eligible event code and accepted submission remain open.

### 20:10 IST — compact take tools checkpoint

Assemble now shows two actions per take: Preview and Edit & review. Existing trim,
subtitle, speech suggestion, mapping, framing and reorder actions live in a
scrollable menu. Exact current take/index/foreground ownership guards dispatch;
render/background/destruction closes the menu. Opening/canceling starts no work
and leaves edits intact. [Evidence](take-tools-evidence.md).

First emulator runner17/19 in36.394s; two test-only fixtures used an incorrect
subtitle button label and checked asynchronous dismissal before idle. Failed
receipts remain retained. Corrected22/79.937s runner passed all four new checks
and18 edit/subtitle/mapping/package/recovery regressions; together37 distinct
methods passed across two runners. Actual demo-card/menu screenshots and Cancel
preference-byte equality were reviewed. Physical filming/audio remain untested.

Build1s/corrected656ms; normal installs passed, built/saved/independently read
installed APK52838059B SHA2565fdbc089736728c316382256fdf419e9ea43f87a7d2861f315413793916021a0. Source-identical notices,
separate weights excluded, no Internet permission. Normal Main launch requested
behind secure keyguard; camera/microphone denied, no unlock/grant/private upload.
Only own AVD/registration removed, other AVD untouched. Qualified peak10431892565B
above10GB aim/below15GB cap; final adjacent9300356181B belowboth.
All historic storage qualifications and unchanged gallery28/0/8319196B remain.
No new dependencies/models. Goal active; remaining acceptance/competition gates
are unchanged.

### 20:21 IST — completion-held pose slot and idea deck

FramePoseFraming now bounds its single-image helper family to one admitted input/
client until task completion and exact-once cleanup. Cancel/timeout leaves pending
resources owned; failed cleanup closes admission. Separate live detectors,
synchronous SDK calls, provider reads and native abort deadlines remain outside
this bound. [Ownership evidence](framing-task-ownership-evidence.md).

Physical17/11.636s passed:5controlled ownership+4native framing+5take-core+3actual
decoder/pose methods. Real cancellation observed an incomplete actual Task after
process and after caller return; cleanup preceded real independent reuse, max
one owned client. Input/prefs/denied capture preserved. No new export/gallery
outputs; inventory1/0.783s unchanged28/0/8319196B. Build1s/normal installs; current
built/saved/independently read installedAPK52838059B SHA256ae9b04e68f9147fc4de5fe181c356d7ba82372f62a98e69b076bd2744cc7da2b.
No Internet permission/separate weights inAPK. Normal Main launch behind secure
keyguard; no unlock/grant/private upload/aircraft action.

Six-slide editable idea deck plus rendered PDF prepared and visually reviewed.
All pages label pre-event research, actual app captures use synthetic clips, and
cover illustration is labelled AI-generated. [Deck evidence](phase1-deck.md).
Draft remains unsubmitted; creator declarations and outgoing approval pending.
Qualified adjacent storage9320832945B below10GB/15GB, includes external generated
coveronce; prior reserve/inventory limits persist. No new models/SDK/dependencies.
Goal active, remaining attended/device/competition gates unchanged.
