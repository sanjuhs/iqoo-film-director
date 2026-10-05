# Mini Film Director — design workshop

Dated **5 October 2026 (IST)**. This is a pre-event design research artifact and
scripted wireframe, not eligible event-written competition code. The user's
explicit request places this design artifact in `design/`; it overrides the older
`prototype/` location convention for these design files only. Existing Android
research and hardware acceptance gates remain separate.

## The experience

Open directly on the camera. A small **Today's objective** pill describes the objective
and opens the brief/chat view. The director offers one clear pose or spoken line
at a time, counts down, starts and stops each sample take, then moves to the next
shot automatically. Takes enter the draft without an intervening Keep prompt;
the creator reviews them afterward. Save the brief, camera choices and editable
reel together as a named project. Daily solo fashion is the first workflow.
The Reference reel view adds a way to study a reference's proposed structure and
adapt rhythm, angles and poses into the creator's own film.

The three destinations are **Shoot → Edit → Projects**. Shoot opens directly on
the camera; the brief, shot plan, reference study and shoot preferences unfold
inside it. Edit holds the reel, take review, subtitles, cover and current-project
save page. Projects gives saved work a dedicated home. Move between these areas
freely: planning, shooting and editing do not require a rigid wizard.

The workshop promise is **“Turn your idea into a reel.”** Its supporting line is
**“Get the shots. Shape the edit. Keep every project.”** The saved library in this
wireframe retains explicitly saved browser projects until the browser's actual
storage quota is reached; the line does not imply unlimited storage, an account
or a cloud history.

## Wireframe inventory

| Destination/state | What the creator sees | Main action |
| --- | --- | --- |
| Shoot / camera ready | Portrait preview placeholder, objective pill, one director cue, voice on/off and secondary Edit plan/Review takes actions | Start shoot |
| Shoot / direction | Current pose or line, scripted framing cue and clear explanation of next step | Continue automatically after explicit session start |
| Shoot / countdown | Large 3 → 2 → 1, current instruction and Stop | Continue automatically in the simulation; Stop cancels |
| Shoot / recording | Simulated recording indicator, elapsed time, timed cues, selected sample angles and Stop | Automatic timed end; Stop aborts the sequence |
| Shoot / What are we making? | Idea and target length first; opening line/mood under Advanced; starters, reference and scripted chat as collapsed extras | Revise the brief |
| Shoot / Reference reel | Instagram bookmark, local file metadata, unrelated synthetic shot study and review checklist | Review and adapt a sample into the own-film plan |
| Shoot / Your shot plan | Ordered shots and planned duration; camera choices secondary | Review/edit shots or return to Shoot |
| Shoot / shot detail sheet | Shot title, action/pose cue, duration and source assignment | Edit a shot before filming |
| Shoot / preferences | Simple Voice guidance and optional Camera gear sections; secondary quiet/sequence controls | Plan equipment and session behavior |
| Edit / take review | Simulated draft-take preview, duration and review choices | Review afterward or retry a shot |
| Edit / Edit your reel | Portrait preview, selected sample clip and obvious Review take action | Review the sequence |
| Edit / clip detail | Advanced collapsed trim, order and available angle choices | Trim or reorder when needed |
| Edit / Words & sound | Optional details for editable sample subtitle lines and music direction | Review changes |
| Edit / Choose a cover | Optional illustrated hook, detail and hero choices | Choose a cover |
| Edit / Save your project | Name, dirty/saved status, explicit save, current-draft JSON and View all projects | Save the whole current project |
| Projects / Your projects | Current draft, recent saved cards, name search, Open and Download per card, all-project JSON backup | Continue, reopen or download saved work |
| Workshop / overview | Side-by-side view summaries and the capture-to-edit flow | Compare the proposed screens |

Details can be inline panels or sheets; they do not need additional primary tabs.
The workshop exposes twelve named views: Camera & objective, Today's brief,
Reference reel, The shot plan, Live direction, Review a take, Build the reel, Words & sound, Choose a cover,
Save your project, Shoot preferences and Projects. The old `#export` link opens
`#project`, the current-draft save page; `#projects` is the dedicated library.
Countdown and recording are live-direction
states; shot editing and camera-source research use sheets. Settings stay compact:
optional commentary, quiet during takes, sequence mode and camera roster, with
reset in the workshop. Hardware planning remains outside the main camera action.
Avoid a landing page, dashboard or model picker on the shoot path.
Workshop labels explain the views; the phone uses concise headings such as
Shoot, What are we making?, Your shot plan, Edit your reel and Your projects.

## Desktop workshop and phone layout

The website is a design review workspace: key links and a separate Settings
button keep the left navigation compact, the overview retains all twelve views, a central phone frame displays
the proposed app, and a right
inspector explains the current state's purpose, actions and future component
roles. The inspector is outside the phone UI. A narrow browser prioritizes the
phone; supporting workshop panels stack or collapse.

Inside the phone, keep the portrait camera visually dominant. Overlay the
objective pill at the top and a concise director cue above a reachable lower
action area. Use one full-width Start shoot action, voice on/off and secondary
Edit plan/Review takes actions. Remove the progress strip, decorative 1× lens
control, redundant phone badges, shot dots and cue-history entry from the camera.
The brief starts with idea and target; opening line/mood remain under Advanced.
Starters, reference and chat remain collapsed extras. Edit starts with the
selected clip and review; trim/order/angle and words/cover unfold only when needed.
Settings separates Voice guidance from optional Camera gear. Use short
labels, strong contrast, generous touch targets, visible
focus, and text as well as color for status. Longer planning and editing live in
their own views. The generated overview image retains the original v1 concept
dated 5 October; it predates automatic sessions, camera planning and saved
projects. The v2 screenshots predate the Reference reel addition. The v3 screens
predate the simplified Shoot/Edit/Projects navigation and dedicated library.
The v4 screens precede this further reduction of controls. The current HTML
prototype is the source for current interactions; each saved image retains
its dated design provenance.

## User flow

**Shoot → idea/plan → Start shoot → draft takes → Edit/review → Save → Projects.**
The camera is the starting screen. Adjust the idea and review the plan when
needed, then start one explicit session. The director handles its scripted
preparation/countdown/takes. Stop cancels future capture/cues; edits and completed
drafts remain available. Review and optionally trim/change angles, words or cover,
save explicitly, then reopen or download from Projects. Reference study and
external-camera planning are optional branches. Opening a saved project never
restarts capture or audio.

- [User flow explanation](user-flow.md)
- [Interactive flowchart](flowchart.html)
- [Standalone flowchart](assets/user-flow.svg)

The flowchart distinguishes implemented design simulation from proposed model
processing. All camera imagery and takes remain samples.

## First sample film

Objective: **“20-second streetwear reel, editorial mood, emphasize the jacket.”**
Use one outfit, one location, a fixed phone and five four-second shots.

| Order | Shot | Direction | Story role |
| --- | --- | --- | --- |
| 1 | The hook | Look into the lens; let the jacket fall into frame | Hook: “One jacket. A whole different energy.” |
| 2 | The detail | Bring the cuff close to the lens; hold for a beat | Show the garment |
| 3 | The walk | Take two slow steps toward the camera | Add energy |
| 4 | The silhouette | Turn slightly sideways; look over your shoulder | Show the fit |
| 5 | The hero | Face the light; hold the final pose | Resolve the reveal |

Other objective starters reuse the same views:

- **Walking and talking:** hook → short story → supporting detail → conclusion;
  cues can pause during spoken dialogue.
- **Product reveal:** tease → reveal → detail → use → finish.
- **Introduction:** opening line → who I am → what I am building → invitation.

Choosing a starter creates an editable five-shot template and clears current
sample takes. Editing the brief form changes the objective, target length, mood
and hook while preserving existing shot edits and durations. Target length and
planned duration can therefore differ; edit individual timings to reconcile them.
Templates do not demonstrate language understanding or generated-shot accuracy.

## Interaction contract

1. Camera opens immediately with a synthetic scene and fashion objective.
2. Clicking the objective pill opens Brief and its expanded objective.
3. Starter presets create the shared plan used by Camera and Reel. Brief form
   edits preserve shot timing; shot edits change title, cue, duration and source. A typed
   message is appended to the objective and receives a labelled scripted reply.
   This is a chat-shaped simulation, with no conversational model.
4. Start shoot explicitly starts the simulated session. The director handles
   preparation, countdown, start, timed commentary and stop for each take. In
   full-sequence mode it advances automatically; one-at-a-time mode pauses after
   one shot. Stop aborts all pending timers and speech. Navigation, backgrounding
   and project reopening must cancel active work.
   An early Stop can retain the elapsed portion as a partial sample draft for
   review; it schedules no subsequent take or speech.
5. Completed takes enter the reel as **unreviewed drafts**, without waiting for
   Keep confirmation. Review, retry and trim afterward. Muting commentary alone
   does not stop the simulated capture. No physical camera or microphone is used.
6. Reel shows the proposed assembly. Move earlier, Remove, trimming, subtitle
   edits, music-direction selection and cover choice change metadata. Storyboard
   playback cycles the selected sample clip; it does not play video or music.
7. Project saves a named snapshot of the brief, shots, hardware selections, cuts,
   captions, music note, cover and reference study in this browser. A separate JSON download makes
   the metadata portable. Future video export is secondary, visibly disabled
   with an explanation. No MP4, real audio mix, synchronized render or Office Kit
   transfer occurs.
8. Projects shows the current draft and saved library. Opening the tab does not
   save the draft. A saved card's Download exports that saved snapshot without
   opening it or replacing current edits. Download all projects exports the
   retained saved library as one JSON backup; it excludes unsaved draft edits.
   Neither download starts speech, capture or another session.

## Reference study and future analysis

Brief opens **Reference reel** at `#reference`. Save a validated Instagram reel
link as a bookmark or select a local video's name, size and type. This design
does not fetch the link, read the video, upload it or analyze its content. A link
cannot establish download permission, and a selected file's metadata cannot
establish its shot structure.

**Explore sample breakdown** loads an unrelated, invented five-shot study. Edit
durations (1–30 whole seconds), framing, apparent angle, movement, pose and
transitions; split or merge sample shots to revise boundaries, up to twelve
shots. Review every shot and choose rhythm,
angles and/or poses before applying. Edits require review again. The new phone
shot plan preserves the objective, opening line and existing takes; fresh shot
IDs keep old takes distinct from the new plan. The target duration becomes the
sum of the new shot timings. Assign optional cameras afterward.

Reference edits/bookmarks/file metadata travel with the saved project and JSON;
file bytes never do. The wireframe runs no Instagram/trending connection or
reference analysis. [Reference research and model plan](reference-reels.md)
documents professional-account API limits, scoped hashtag discovery versus a
global trend feed, embed versus original video, shot-boundary detectors,
multimodal interpretation, pose, speech and evaluation gates.

[Qwen3.5-0.8B's official model](https://huggingface.co/Qwen/Qwen3.5-0.8B) is multimodal; the existing Android text-planning
path does not establish that its deployed artifact/runtime accepts images. A
future pipeline must separately verify the complete vision path. Use reviewed
observations for plan adaptation, keep timed capture outside model inference,
and compare optional larger VLM candidates before claiming any quality or phone
performance. No additional models or dependencies are installed by this design.

## Camera planning and sample angles

The phone is the first source. Settings can add a **sample DJI Action 4** through
a Bluetooth concept flow; its status remains mock-added, with real pairing,
control, preview and media transfer untested. Assign a shot to phone, Action 4
or a planned drone pickup. An optional simultaneous phone + Action 4 simulation
collects one draft object with an `angles` array containing phone and Action 4.
Choose one angle for each timeline slot; two perspectives do not double the
duration. This establishes no measured concurrent capture or synchronization.

Drone planning explicitly separates **Neo 2 / DJI Fly manual capture and import**
from **Mini 4 Pro / read-only SDK probe research**. Adding a drone assigns the last
shot as a pickup planned outside the automatic session. The director skips and
logs that pickup without generating a take or source file: the default five-shot
plan therefore produces four drafts plus one pending drone pickup. There is no Bluetooth drone
connection, simulated automatic flight/capture or aircraft command. The actual
SDK probe remains independently gated, propellers-off and read-only.

Official research, checked 5 October: [DJI's ESP32-C6 example](https://github.com/dji-sdk/Osmo-GPS-Controller-Demo)
lists Action 4 and demonstrates BLE recording start/stop; it is not Android
integration evidence. [DJI's SDK compatibility table](https://repair.dji.com/help/content?customId=01700000763&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17)
lists Neo 2 as unsupported and Mini 4 Pro as Mobile SDK supported. Neither source
establishes a tested hardware connection in this project.

## Saved projects and commentary

Unsaved working edits live in memory; the explicit **Save project** button stores
a snapshot in `localStorage`. Project status distinguishes dirty edits from the
saved version. Saved projects survive reloads on the same browser origin;
different hostnames/ports or browser storage clearing affect availability.
The dedicated **Projects** tab at `#projects` shows recent saved cards and name
search, with Open and direct Download actions. The current-draft card offers
Continue or Save; Save opens `#project`. That save page links back to the library
through View all projects rather than duplicating the saved list.

The library retains all valid explicitly saved projects in this browser origin
until actual browser storage limits are reached; there is no arbitrary project
count cap. A failed save preserves the existing library and offers JSON backup.
It is not every film previously made, nor a cloud/account history. There
is no autosave; opening Projects and downloading a saved card do not save current
edits. A direct download uses the stored snapshot, and Download all projects
backs up the retained library. These downloads contain project JSON, not photos,
source videos, audio or an MP4. The current-draft JSON download remains separate.

Opening a saved project restores design data with recording and commentary
inactive, and never replays camera actions or voice. Unsaved edits trigger a
confirmation before Open; cancelling preserves the current draft. Reset demo clears working
sample state while retaining saved projects. Download JSON for a portable copy;
it contains metadata and no media.

Voice is optional. Only an explicit click can enable or test browser cue
playback; it follows the installed browser's speech service and selected output,
and that service may be online. Browser speech is not evidence of offline
inference, earbud routing or Android TTS. No microphone
permission, speech recognition or private-media upload belongs to this prototype.
Visual commentary changes at timed points in a take. Optional browser speech
uses the same scripted cues; there is no live perception or conversational
inference. Quiet-during-takes permits preparation, countdown and Cut, but
suppresses spoken Action and midpoint cues during recording. Starting commentary
while a quiet take is recording is also silent. **Stop commentary** cancels speech
without stopping capture; **Stop shoot** cancels the session and all future cues. Keep the
full flow usable with cues displayed as text. The supplied default inputs and
all preview/take imagery are synthetic; no private inputs are needed.

## Future model and execution responsibilities

These are component responsibilities and research candidates, not implemented backends.
The current speech proposal selects candidates for evaluation: **Kokoro-82M** for
short director cue audio and **English Moonshine Tiny/Base or streaming Tiny**
for opt-in spoken briefs and later reviewed subtitle drafts. The requested 0.5B
is an upper size bound, not Moonshine's exact size. Compare with Whisper Tiny;
pin the exact checkpoint/code licenses and prove actual Android outputs before
claiming a working speech stack. [Speech research](speech-models.md) records
generation/size distinctions, licensing, timing and evaluation gates.

| Responsibility | Proposed output | Remains outside the model |
| --- | --- | --- |
| Brief language interpretation | Editable objective, hook, lines and draft shot list | Creator approval, valid duration and schema |
| Visual perception | Timestamped framing/pose observations with confidence | Permission, capture lifecycle, observation freshness |
| Speech input | Proposed Moonshine transcript for an explicit brief, then editable subtitle drafts | Listening permission, transcript review, timestamps and manual corrections |
| Speech output | Proposed Kokoro-82M audio for approved short cues | Session ownership, mute, output-route feedback and interruption |
| Bounded director | Next-shot/pose suggestions and reasons | Timers, cancellation, recording acknowledgements and action limits |
| Edit suggestions | Proposed clip selections, subtitles and cuts | Original media, manual trims, verified alignment and rendering |

A slower brief model may prepare the plan; short live cues must not wait on it.
Camera recording and video rendering are deterministic app/media operations.
Local execution, NPU acceleration, clip-analysis accuracy and benefit require
their own reproducible evidence. This wireframe runs no models, measures no
framing, analyzes no footage and performs no actual video post-production.

Phone capture is the primary future source. Optional imported views can expand
the assembly later. Action 4 mock addition and sample-angle collection establish
no real connection/control. Aircraft movement remains separately approved and bounded;
hardware support research does not establish a tested connection.

## Files and launch

| File | Purpose |
| --- | --- |
| `README.md` | Product intent, views, flow, roles and acceptance checklist |
| `reference-reels.md` | Official-source research, access limitations, proposed reference/model pipeline and evaluation gates |
| `speech-models.md` | Kokoro/Moonshine primary-source research, exact families/licenses and evaluation gates |
| `user-flow.md` | User-facing Shoot/Edit/Projects flow and optional branches |
| `flowchart.html` / `assets/user-flow.svg` | Explicit interactive/standalone user flowchart |
| `index.html` | Workshop shell and phone views |
| `styles.css` | Phone-first layout, states and responsive workshop |
| `app.js` | Scripted director, editable plan/reel/reference study, camera planning and local saved projects |
| `assets/app-overview.png` | Original v1 generated concept image; retained as dated design history |
| `assets/overview-prompt.md` | Prompt and provenance for the built-in image generation concept |
| `verification-reference.json` | Reference review, adaptation, persistence and responsive checks |
| `verification-revision.json` | Verification evidence for the revised project/session design |

No package installation, build, provider key, model download or dependency is
needed. From the repository root, run:

```sh
python3 -m http.server 4173 --bind 127.0.0.1 --directory design
```

Open [the local design workshop](http://127.0.0.1:4173/). Opening
`design/index.html` directly in a browser supports the core simulation; browser
voice and file-origin storage behavior may vary, so use the local server for
saved-project review. The server is bound to this computer's loopback address.

## Review walkthrough

- Open Shoot and verify that one cue and Start shoot are obvious, with no progress
  strip, fake lens control or redundant shot/camera badges.
- Open Brief through the pill; revise a fashion brief and inspect all five shots.
- Try a walking/talking, reveal or introduction starter, then return to fashion.
- Open Reference reel; save a valid Instagram bookmark and reject an unrelated
  URL. Select a video and confirm only metadata is noted. Load the unrelated
  sample, edit/split/merge its shots, review them and apply selected study aspects.
  Verify that the existing objective/takes remain and new shots have fresh IDs.
- Start shoot; watch countdown, timed commentary, automatic end and next
  shot. Check that the full sequence produces unreviewed draft takes without Keep.
- Try one-at-a-time mode and check that it pauses after one shot.
- Check that navigation and Stop cancel pending timing and spoken cues.
- Use optional clicked voice playback, then repeat with commentary muted/quiet.
  Confirm that mute preserves the running simulation while Stop aborts it.
- Add a sample Action 4, assign sources and try two-angle sample collection.
  Verify that drone assignments remain separately planned pickups with no commands.
- Open Edit; review a selected take, then expand advanced clip controls to move it
  earlier, change trims/angles and open optional words/cover details.
- Save a named project, edit it and inspect dirty status. Reload and reopen the
  saved version; check that no timer, voice or capture restarts.
- Open Projects without saving; verify current dirty edits remain. Search names,
  download a saved card without opening it, and check the downloaded JSON uses
  the saved snapshot while the current draft remains untouched.
- Download all projects; check that it includes the retained library and excludes
  unsaved changes. Cancel opening a saved project and verify draft preservation.
- Reset the demo and verify saved projects remain available. Download JSON and
  check the objective, hardware selections, cuts and finishing metadata.
- Check that future video export is disabled and explains the limitation.
- Inspect overview mode and a narrow viewport for readable labels and controls.
- Confirm that no camera/mic permission, real recording, model call, private
  upload, drone connection or rendered-video claim occurs.
- Inspect the flowchart and confirm proposed Kokoro/Moonshine paths are labelled;
  browser voice, typed input and scripted captions remain the current fallbacks.

This walkthrough validates the design simulation only. Parent project evidence
and its unresolved attended phone/hardware tests remain in
[`docs/status.md`](../docs/status.md).

## Saved review evidence

The original v1 browser run passed **84 checks**, covering all ten workshop views at
360, 390, 768, 1024 and 1440-pixel widths; objective/shot edits; countdown and
navigation cancellation; kept-take progression; clip trimming/reordering;
subtitle/music/cover changes; and downloaded JSON content. No runtime/console
errors or external requests occurred in that run. Those checks preceded the
automatic director, saved projects and camera-roster revision. Revised checks
are recorded separately in `verification-revision.json`. Optional speech playback
and actual phone/hardware behavior are distinct from simulation checks.

- [Verification results](verification.json)
- [Revised verification results](verification-revision.json)
- [Original v1 camera workshop screenshot](assets/wireframe-camera.png)
- [Original v1 all-views board screenshot](assets/wireframe-overview.png)
- [Original v1 phone-sized screenshot](assets/wireframe-mobile.png)
- [Original v1 generated concept overview](assets/app-overview.png)
- [Built-in image generation prompt and provenance](assets/overview-prompt.md)

The revised browser run passed **94 checks** covering all ten views plus overview
at five viewport widths; Action 4 sample setup and paired-angle editing; planned
drone pickups; automatic/single-shot sessions, Stop and mute; draft review; trims;
project JSON and explicit save/reload/reopen; and retention of the saved library
on reset. It reported no runtime errors, sensor use or external requests.
Timing checks used a virtual clock and mocked speech synthesis to verify cue
scheduling, not actual audibility. Real Bluetooth, camera, microphone, drone,
models and video rendering remain untested by this design verification.
Those v2 checks preceded the Reference reel addition; they do not establish
reference-flow correctness. The Reference revision passed **154 checks**, recorded separately in
[reference verification](verification-reference.json): bookmark validation and
tracking removal, metadata-only file selection, sample editing/split/merge,
review and adaptation gates, preserved takes/fresh IDs, custom target duration,
save/reload/JSON, capture cancellation and all eleven views plus overview at five
widths. It reported no runtime errors or external requests. The run used isolated
Chromium, reduced motion, a virtual clock, mocked speech and synthetic file bytes;
no Instagram access, video analysis or hardware result is claimed.
Those v3 checks and screenshots precede the v4 simplification and Projects
library. Revision 4 verification is recorded below; earlier counts do not
establish its tab grouping, direct saved-project downloads or bulk backup.

- [Revised settings screenshot](assets/wireframe-settings-v2.png)
- [Revised project screenshot](assets/wireframe-project-v2.png)
- [Revised all-views board screenshot](assets/wireframe-overview-v2.png)
- [Revised phone-sized screenshot](assets/wireframe-mobile-v2.png)

- [Reference shot-study screenshot](assets/wireframe-reference-v3.png)
- [Reference phone screenshot](assets/wireframe-reference-mobile-v3.png)
- [Eleven-view overview](assets/wireframe-overview-v3.png)


### Revision 4 evidence

[Library verification](verification-library.json) records **177 passed checks**.
The isolated existing Chromium run verifies Shoot/Edit/Projects navigation,
collapsed extras and visible chat results; exact saved-snapshot downloads and
all-project backup payloads; search/focus; preserving unsaved changes during
Downloads and cancelled Open; explicit reopen and browser persistence;
capture cancellation; optional camera/reference access; all twelve detailed
views plus overview at five widths; retaining a thirteenth project without
truncation/eviction; and preserving saved data when a simulated browser-quota
failure prevents a save. No runtime errors or external requests occurred.
Reduced motion, virtual time, mocked speech and synthetic project metadata were
used; this establishes the design flow, not real video export or hardware use.

- [Simplified Shoot screenshot](assets/wireframe-shoot-v4.png)
- [Projects workshop screenshot](assets/wireframe-projects-v4.png)
- [Projects phone screenshot](assets/wireframe-projects-mobile-v4.png)
- [Current twelve-view overview](assets/wireframe-overview-v4.png)

Revision 5 further reduces the camera/brief/edit controls and adds the explicit
flowchart and Kokoro/Moonshine research. Its completed verification is recorded
below; no browser check establishes execution of either proposed speech model.


### Revision 5 evidence

[Decluttered-flow verification](verification-simplified.json) records **178
passed checks**. It covers the minimal camera controls, edited idea visibility,
folded advanced brief/settings/edit/project controls, paired sample-camera
planning, explicit voice consent/mute, automatic sample takes, Stop retaining
only the elapsed partial take, caption/angle/trim/cover persistence and saved
project reopening/download. All twelve detailed views plus overview fit five
widths. The diagram defaults to the core path, reveals optional screens, links
to actual routes, fits a phone and downloads its standalone SVG. No runtime
errors or external requests; existing isolated Chromium, virtual time, reduced
motion, mocked browser speech and synthetic data were used. Kokoro/Moonshine
were neither downloaded nor executed; no sensor/aircraft action occurred.

- [Minimal Shoot phone screenshot](assets/wireframe-shoot-mobile-v5.png)
- [Simplified idea phone screenshot](assets/wireframe-idea-mobile-v5.png)
- [Simplified edit screenshot](assets/wireframe-edit-v5.png)
- [All-screen flowchart screenshot](assets/user-flow-v5.png)
- [Main-path flowchart screenshot](assets/user-flow-main-v5.png)

### Reference walkthrough — 5 October

[See what happens next](reference-demo.html) is a replayable six-step companion
to “Use as my shot plan.” It uses the actual prototype in a separate phone frame:
review the invented sample, confirm replacement, see the five editable shots,
start the cue/countdown/take/Cut sequence, review the resulting draft reel and
open Save project. It leaves existing saved projects alone and never saves the
walkthrough automatically. Voice is off; no real sensors or AI models execute.
The ordinary reference screen links to the walkthrough even while its review
gate is disabled. The normal creator review gate remains unchanged.

[Walkthrough verification](verification-demo.json) confirms all six stages,
restart, no automatic save, four responsive widths and no browser runtime errors
with isolated Chromium and virtual time. The first verification found that a
same-URL iframe restart did not reset the draft; a distinct local replay URL
now creates a fresh frame and the complete verification passes.

- [Applied shot plan](assets/reference-demo-plan-v6.png)
- [Countdown](assets/reference-demo-countdown-v6.png)
- [Draft reel](assets/reference-demo-edit-v6.png)
- [Save screen](assets/reference-demo-save-v6.png)

The separate [local model benchmark brief](../docs/local-director-benchmark.md)
distinguishes measured Android evidence from this scripted website demo.
