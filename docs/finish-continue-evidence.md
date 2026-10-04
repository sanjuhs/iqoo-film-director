# Finish a guided take early — 4 October 2026

Pre-event research under `prototype/`. Direct adds **Finish & continue**, enabled
only for a genuinely started current guided foreground recording. It asks the
same controller to stop once, shows Saving and leaves continuation pending.
It does not advance the shot or speak the next direction at the tap. Existing
planned-stop snapshots, quiet-tail policy and hard60-second/100MiB limits remain.
This finishes a scene EARLY; it does not enable longer manual-timed sequences.
For longer speech, edit planned duration or use free talking with guidance and
planned stop off. Stop still ends the sequence.

The production seam uses the current controller's real Recording-start identity
and validated Finalize callback. The existing private take store validates actual
container duration before callback; successful short/invalid/stale acknowledgements
cannot advance. Request ownership covers controller/generation/started attempt,
current shoot generation and foreground Direct. Other terminal paths now protect
newer work from a stale recording callback too. Camera error/Stop/guide-off/audio
interruption/render/background/destroy cancel pending continuation. A later valid
closed/background file remains recoverable, without starting another cue/take.
End shoot's current foreground final save may open Assemble; its pending review
intent is cleared on background or a genuinely new camera session.

The [official CameraX Recording API](https://developer.android.com/reference/androidx/camera/video/Recording)
describes stop/close as idempotent. App continuation deliberately waits for its
validated finalized-file seam instead of treating a stop request as saved media.
No new recording, microphone bypass, provider, dependency or model path was added.

## Verification scope

Three new one-shot bookkeeping tests use identity/counters to cover one stop,
exact saved acknowledgement, cancellation/replacement and throwing stop. Four
new own-unlocked-emulator methods use idle controllers and explicitly synthetic
callback URIs: no-real-Recording refusal/stale control, final-shot acknowledgement
once, stale/invalid/newer-request isolation, and Stop/guide-off/render/true-background
cancellation including stale End-review intent. They do not fake CameraX Recording,
start preview/recording, grant capture permission or validate a nonexistent source.
The existing finalized-container fixture is tested separately. Positive early
Finish during actual recording, audible next direction, next-camera reopening and
recorded speech fit remain attended acceptance gates.

Independent source review found no remaining blocker after adding universal
terminal guards and clearing stale End-review intent. First offline build failed
because a new lambda parameter shadowed the existing Switch variable; parent
renamed only the parameter and retained the failure. Repaired build succeeded
in1s (11 executed/51 cached), followed by normal phone and fresh-emulator installs.

Physical **18/0.148s** passed: new latch3, existing actual finalized-container2
and quiet-stop policy13. The known synthetic spoken fixture retained its actual
approximately5.75-second container bound and original bytes; no new source was
recorded. Fresh own API36 emulator **32/59.648s** passed: new early-Finish4,
guided-stop3, pose-cue11, general workflow10 and new-reel4. No runtime failures
in this increment. Cameras/audio disabled, airplane mode1, capture denied and
separate weights absent. The actual positive Recording/Finish/next-scene capture
remains outside these tests.

After all runners ended, ordinary Direct showed the full early-finish explanation
and the idle control disabled. Initial screenshot showed weak visual contrast
against active siblings. Parent added disabled opacity only, keeping the exact
eligibility predicate. Final build733ms/7 executed55cached and normal installs;
ordinary current-state XML and final screenshot visually confirmed the dimmed
idle control and readable explanation. No Hear/Start/Finish action was pressed.
The32-method runtime receipt precedes only this presentation change; no recording
policy changed and no positive live eligibility was inferred.

Built/saved/independently read installedAPK52838059B SHA256
71c36ef10bc91a4578e58fb9d26285dfc05af7be63f11968881c67efd19e196e; source-identical notices, separate Qwen/Whisper weights
excluded, bundled ML Kit remains/no Internet. Gallery1/0.885s unchanged29owned/
0pending/8,373,323B. Normal Main launch behind secure keyguard with capture
denied. Own AVD/registration removed only after parent terminal; other untouched.
Qualified observed peak10987724112B and final adjacent
9336683856B with previous inventory/reserve qualifications.
No new downloads, private upload/capture/audio playback or aircraft action.
