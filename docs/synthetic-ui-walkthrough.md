# One-minute actual-app research walkthrough

4 October 2026. [Open the video](../output/demo/MiniFilm-synthetic-walkthrough.mp4).
This local60.000-second silent540×1380H.264 recording shows the installed research
app on our own empty API36 emulator, with an editable template and labelled
synthetic clips. It contains actual UI, not generated/recreated screens.

Brief reviews the existing template/shot editor; Direct shows its first scene
with the camera closed; Assemble reviews existing cut/typography controls,
exports and opens its real output. The final excerpt is the actual synthetic
export. A sparse variable-frame-rate raw capture initially lost held dialog
frames at trim boundaries during editing; review detected this. Normalizing to
24fps before cutting preserved those held frames. Initial/raw assets retained
as private evidence. Final contact sheet and full-size review frame visually
checked; headers/labels do not cover app controls. Pauses are explicitly removed,
not an export speed benchmark. Final video1668131B.

The ordinary app path generated three synthetic source clips, preserved their
500–2500ms cuts and published3851B edit JSON with6000ms nominal timeline. Only
that exact own-emulator output was copied after verifying its UUID/published
owner and three known synthetic cuts. Independent probe:720×1280H.264/AAC,
6.069660s,1371401B SHA256
d99dd0e50d406ff55f77aacf3e3a4efaf849a3c3aaed39b3ccbf3b13e34c4406.
[Original generated output](../output/demo/synthetic-ui-export-20261004.mp4).
It is distinct from physical-phone fixture results in the other ledgers.

No model weights existed on this emulator, no camera/microphone opened, audio
devices disabled/airplane1. Plan generation used the visibly labelled fallback;
no local LLM/ASR/live pose run is implied by these screens. Opening the result
was paused, then explicit synthetic playback was requested after screen recording
ended. This proves no audible output or AirPods result. Android's first-use
fullscreen hint is excluded from the edited walkthrough.

Observed-label helper first rejected mixed-case Replace Plan; uppercase observed
button retry passed. A scroll attempt while that modal was open rejected its
small viewport; no action occurred. Playback UI dump did not reach idle; its
output was excluded and the actual known synthetic screen was inspected directly.
AVD creation helper first rejected a missing basename before mutation, then
correct invocation passed. These are artifact/navigation-helper failures, not
app/test failures. Own AVD/reg removed after emulator parent completion; other
AVD untouched. No new downloads, private upload or aircraft action.

At the recording checkpoint, the app independently matched52838059B/SHA256
45b96786e2871a4b65af8b4c3a56094fc92bda345d4acb684dbb34d91e09d76f.
[Current audit](completion-audit.md) retains attended filming, AirPods, iQOO/NPU,
OfficeKit, semantic usefulness, event-source eligibility and accepted-entry gates.
The walkthrough is pre-event preparation, not a submitted competition demo.
