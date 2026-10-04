# Fixed Assemble export action — 4 October 2026

Pre-event research under `prototype/`. Assemble now keeps one selection summary
and **Export my reel** above navigation, outside the take-list scroll area.
Portable ZIP, playback and sharing remain in the scrolling editor. Selected cut
duration updates as takes are checked; empty selection and active processing
keep the export action disabled. Existing cut-count/duration validation remains.

Dispatch checks the exact current footer button, visible window, resumed Assemble
screen and idle state. Render, background and destruction invalidate old controls.
Returning from a preview rebuilds the footer and restores the previous list
position; current-view identity protects the queued restoration. Pending review
refreshes retain their previous behavior. A review caught rebuilt editors becoming
enabled during surviving processing. Render now reapplies the existing editor
lock after constructing the complete page, while Cancel remains reachable.

This changes control access and lifecycle state, not capture, model, crop,
subtitle, media export or package algorithms. No model, camera or microphone
starts from viewing this screen.

## Verification

The initial four-method increment plus21 related regressions passed
**25/40.023s**. Review then found and repaired the busy-resume editor lock and
added its fifth method. A first final runner completed25 of26 methods; one
background transition timed out after root launched a second Main screen and
attempted UIAutomator inspection before instrumentation completed. UIAutomator
also failed because the runner already owned automation. This was orchestration
interference; no production/test assertions were loosened. Failed receipts remain.
The isolated fresh-emulator final runner passed **26/48.455s**: five footer,
four take-tools, ten workflows, three assembly and four shot-assignment methods.

New footer checks use six deliberately unreadable synthetic URI rows, absent
separate weights, denied camera/microphone and full preference restoration. They
measure actual overflowing-list geometry at top/bottom, one fully visible footer
outside ScrollView, selection count/duration and busy/empty enablement. Retained
buttons after render, tab change and true CREATED background remain inert before
and after resume; the list restores its exact nonzero scroll position. The fifth
method holds a controlled busy flag across background/resume, checks disabled
rebuilt title/look/checkboxes/export, enabled Cancel, inert framework checkbox
touch and reenabling after processing. It does not run a real background export.

After the final runner had terminated, root used the ordinary app's labelled
DemoAssets and clicked the fixed **Export my reel** at the top of Assemble.
The actual Clean output has three reviewed-order cuts, each500–2500ms, nominal
6000ms and encoded6.069660s. ffprobe reports H264720×1280 plus AAC,1,371,409B,
SHA256`ae9405dec0dffb1f89fdc6728a68ba2ccd6135961ca4740810032c87b63e85c6`.
The3,618B `minifilm.edit.v1` cut list retains the current five-shot plan, caption
words, source cuts and2000ms sequential offsets; SHA256
`e8547885bdaf11e57918def84b9682fe48a6cac085170dddfe86055705cb78d8`.
All three synthetic source sizes/hashes stayed identical. Three decoded midpoint
frames and top/bottom footer screenshots were visually reviewed locally. This
supports one synthetic layout/output, not real capture/audio or creator benefit.

Final build739ms; fifth-test build853ms. Normal app/test installs used no grants.
Built/saved/independently read physical installedAPK52,838,059B, SHA256
`380a2eb9dd0ce0c3a1e66fe8835f59a3eb8a3514df208b75b25fe731ee5e2495`.
Notices31,635B/source-identical, separate Qwen/Whisper weights absent fromGit/APK,
bundled MLKit assets remain. Internet permission absent. Read-only physical
gallery inventory passed1/0.470s, unchanged28 owned/0pending/8,319,196B.
Normal Main launch stays behind secure keyguard with capture permissions denied.
No unlock/grant, private upload or aircraft action occurred.

Reproduce on a fresh API36 arm64 emulator with cameras/audio disabled, airplane
mode verified and no separate models. Build app/test offline using the existing
toolchain, install normally, then run only the five classes named above through
AndroidJUnitRunner. Wait for runner termination before the ordinary-app DemoAssets
journey. Local receipts live under ignored `private/evidence/assembly-footer-*`.
Attended capture/AirPods, useful learned direction, iQOO/NPU, OfficeKit, event
eligibility and an accepted submission remain open.

Only owned AVDs and matching registrations were removed; the existing other AVD
stayed untouched. Qualified observed temporary peak **10,457,645,825B**,
above10GB aim/below15GB cap; final adjacent **9,326,506,753B**, belowboth.
External generated cover, all three physical APKs, unchanged owned gallery and
historic missing-archive/cache reserves are counted. Full-inventory limitations
persist. No new SDK/JDK/system-image/model/dependency downloads.
