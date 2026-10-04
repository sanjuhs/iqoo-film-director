# Keep creator stop choices after reopening — 4 October 2026

Pre-event research under `prototype/`. Planned-stop and experimental quiet-pause
choices previously existed only in memory. A creator could disable planned stop
for free talking, then reopen/recreate Direct and silently get planned stop ON
again. The next explicit take would snapshot timed stopping and could end at its
short planned duration. This is a deterministic continuity defect, not evidence
of an observed interrupted private recording.

Main now saves manual `autoStop` and `waitQuietPause` settings after their real
switch edits and restores them with legacy defaults true/false. Guidance still
forces the planned-stop display checked and disabled without overwriting the
manual choice; turning guidance off restores that retained choice. Current-take
`recordingTimedStop`/`recordingQuietStop` snapshots and planned/quiet/hard limits
are unchanged. Transient capture/session/countdown facts are not persisted.

Three new own-empty-unlocked-emulator methods exercise actual switches and real
ActivityScenario recreation: legacy defaults and both preference directions;
guided display across recreation and guide-off preserving manual false; and
synthetic current-take snapshots staying unchanged by edits and omitted from
saved/restored state. Entire original preferences are restored. No actual
Recording, source, camera/mic grant, preview, model, speech or audio is used.

Independent final source review found no blocker. Offline build1s (11 executed/
51 cached), then normal app/test installs without grants. Final own API36 UI
**39/63.629s** passed: preference3, fixed-controls4, early-Finish4, guided-stop3,
pose-cue11, workflow10 and new-reel4. No runtime failure. This repeats companions
because new saved/restored fields affect recreation and new-reel state checks;
it does not add another live-camera or native-model result.

Final built/saved/independently read installedAPK52838059B SHA256
f416d78b500ad0d241b08ab37deac3bd1ed6b53a5004fb1eba3a6e2547e27819.
Source-identical notices, separate Qwen/Whisper weights excluded, bundledMLKit
remains/noInternet. Adjacent physical gallery metadata inventory1/0.862s was
unchanged29/0/8,373,323B; the preference change adds no media path. Normal Main
launch requested behind secure keyguard, camera and microphone denied. Live
recording/early Finish, longer spoken fit and actual AirPods routing remain
attended acceptance gates. No download, private upload/capture or aircraft action.

Own AVD/registration removed after parent terminal; existing other AVD untouched.
Qualified observed peak11140303840B and final adjacent
9338014688B. Historic missing-archive/cache reserves
and full-inventory/runtime exclusions remain. No new downloads this increment.
