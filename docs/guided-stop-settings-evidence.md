# Explain guided stopping — 4 October 2026

Pre-event research under `prototype/`. The planned-stop switch previously showed
the manual `autoStop` preference even while guided sequence execution used
`autoStop || sequenceActive`. Switching it off could falsely suggest a talking
take would keep going, although sequence mode still stopped at the planned length.

Direct now shows planned stopping checked and disabled while **Guide the full
shot sequence** is enabled. Turning guidance off restores the underlying manual
preference unchanged. The explanation says free talking requires both guidance
and planned stopping off, and that every take still has the existing60-second
limit. Changes apply to the next take. Generic busy→idle editor enabling respects
the forced control; render discards its old pointer.

This changes presentation only. Recording's timed/quiet-stop snapshots, countdown,
quiet-tail policy, sequence transitions, capture hard limit and persistence stay
unchanged. It adds no audible completion, voice command, capture or recording.
Actual microphone performance, speech fit and AirPods routing still need a creator.

## Verification

Three new visible-switch tests cover manual false→forced guidance→restored false,
manual true retention and busy re-enabling, and rerendered forced control without
changing existing recording-stop flags. They assert the explanation and denied
capture, with no camera/audio/model/source media execution and full preferences
restored. The first runner passed25 companion methods (four new-reel, eleven
pose-cue and ten general workflows) but all three new switch methods failed,
**25/28 passing,47.347s**. The shared helper wrongly asserted the return of
`CompoundButton.performClick()`: that boolean reports whether an OnClickListener
was invoked, rather than whether the checked-state listener toggled the control.
The [official Android API contract](https://developer.android.com/reference/android/widget/CompoundButton#performClick())
was verified. Production stayed unchanged. The repaired helper retains full
visibility, adds full-width/current-layout checks and asserts the actual checked
state changes. All original policy assertions remain. Focused rerun passed
**3/5.404s**; the initial three failures stay recorded. No recording timing or
hardware speech/capture check was added by this fixture correction.

After all runners terminated, the ordinary synthetic app showed manual planned
stop off/enabled, then guidance on with planned stop checked/disabled. The full
explanation was visually reviewed. Turning guidance off returned planned stop
to off/enabled, verified from current view state. No Hear/Start camera/recording
action was pressed. This confirms visible setting honesty on one synthetic layout.

Final offline build1s/normal install. Built/saved/independently read physical APK
52838059B SHA256b1d40c5947c0e4b62cbcd0c3a4f13b4255070f160fce185829a18b92406c8e49; source-identical notices,
separate Qwen/Whisper weights excluded, bundled ML Kit remains, no Internet.
Gallery metadata1/0.761s:29 owned/0 pending/8,373,323B unchanged. Normal Main
launch requested behind secure keyguard with camera/microphone denied. Only own
AVD/registration removed after parent process terminal; other AVD untouched.
Qualified observed peak11137596624B and final adjacent
9332161744B; historic inventory/reserve exclusions remain.
No new dependencies/models/SDK/JDK, private upload/capture or aircraft action.
