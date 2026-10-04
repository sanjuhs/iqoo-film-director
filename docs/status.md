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
The accounted conversion peak estimate was9.344GB, below10GBaim/15GBlimit,
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
