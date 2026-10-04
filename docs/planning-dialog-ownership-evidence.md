# Keep the reviewed shot plan when a planning prompt is dismissed

4 October 2026; pre-event research under `prototype/`.

Replace shot plan and the planner-error starter prompt used untracked default
AlertDialog listeners. Their Keep actions dismissed asynchronously, while a
retained positive action could still call runPlanner/replacePlan in the same
event. With no model installed, this immediately replaced manual shot edits
with a template despite Keep. This is a source/retained-action seam, not a claim
of observed private loss or an ordinary background touch.

One current attached planning dialog now owns both prompts. Keep clears that
ownership synchronously before dismissal. Replace/Use starter requires the
current showing window, resumed Brief, idle state and unchanged brief/style/
serialized plan/source plus composing reference URI/summary/reviewed-moments
presence/matching reviewed-speech facts. Render/background/destroy dismiss the
old prompt. Current explicit replacement still works and preserves old takes,
with distinct model-absent and AI-failed template labels. No native/model/plan-
parsing policy or controller schema changed.

LocalPlanner already suppresses queued callbacks when closed; cancellation/
destroy closes the old planner while retaining its lease until native resources
are freed. Backgrounding intentionally lets an explicitly requested plan finish
and save, without capture/speech dispatch. This contract is unchanged. An error
shows its starter prompt only in foreground. No new model bypass or worker
cancellation seam was added.

Four own-empty-emulator UI methods cover actual Build → Replace prompt, same-
event Keep + retained Replace, current model-absent replacement preserving takes/
subtitle/cut facts and returning to first shot, real production fallback dialog
with explicitly synthetic error text, distinct Keep/current starter semantics,
and changed brief/style/plan/reference summary/URI/busy/render plus actual
CREATED/background/resume refusal. These are metadata/lifecycle checks with
weights absent and camera/microphone denied. Directly calling the production
fallback presentation method does not establish a native failure or inference.
Full preferences restored, no selected source read or playback/audio/capture.

Offline build1s (11 executed/51 cached), normal app/test installs without grants.
Fresh own API36 UI18/33.135s passed: new planning-dialog4, revised new-reel4 and
workflow10. No runtime failures. Independent source review found no blocker.
Native/model/media implementation unchanged; prior physical export/budget6 checks
retain their23:39 scope and were not repeated for this dialog-only increment.
Fresh metadata-only gallery1/1.017s unchanged31owned/0pending/8,563,448B.

Built/saved/independently read installedAPK52838059B SHA256
28491e11e129b92aa0095bb1ac039e9a5616dfdb7c82b03104dbb00c4c4edac2; source-identical notices,
separate Qwen/Whisper weights excluded/bundledMLKit/noInternet. Normal Main launch
requested at23:43 behind physical securekeyguard/capturedenied. No unlock/grants,
new downloads, private capture/upload/playback or aircraft action. Own AVD/reg
removed after parent terminal; other untouched. Qualified peak10950785909B/
finaladjacent9366604661B at23:44 includes prior raw/labelled walkthrough and
external artwork once, with historic missing-archive/cache reserves and existing
full-inventory/runtime exclusions. Attended/AirPods/iQOO/NPU/OfficeKit/semantic
usefulness/event-code eligibility/accepted-entry gates remain open.
