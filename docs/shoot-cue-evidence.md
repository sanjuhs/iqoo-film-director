# Shoot cue ownership and interruption evidence

Verified 4 October 2026 (IST), using the pre-event research app under
`prototype/phone-director/`. These checks cover synthetic UI and callback state;
they do not establish an attended Pose → Perform capture result.

## Source findings and repairs

An old pose result could already be queued on the main thread when its pose
coach was closed. The previous UI callback checked the current global session,
allowing that old result to change a newer shot's framing text or spoken cue.
`MainActivity` now captures a shoot generation and checks it in `applyPoseCue`
before any visual or speech mutation. Render, camera replacement, end-shoot,
background and destruction invalidate the generation before resource closure.

Automatic pose speech previously replaced an explicit “Hear the direction” cue
through the existing stop/flush behavior. `SpeechCoach.hasSpeechWork()` includes
both a cue pending voice initialization and an active utterance. Automatic pose
speech now waits while either exists; visual framing feedback can still update.
Explicit direction and full-sequence speech retain their existing behavior.

A live lens switch now creates a fresh controller and pose coach through the
existing preview path. A second switch during binding is refused using
`CaptureController.isPreviewPending()`. A stopped controller does not prevent
choosing the lens for the next explicit camera start. Recording/countdown gates
remain in place; a lens choice does not start recording.

## Executed synthetic checks

The parent ran these tests on a fresh, unlocked Android 36 ARM64 project emulator
with camera and microphone permissions denied. Both APK installations succeeded;
the build reported success in 1 second. No physical device, host camera/audio,
native model execution, camera preview, microphone recording or audible TTS was
used by these test suites. Test setup restores the complete prior `shoot`
preferences and asserts permissions remain denied.

`ShootPoseCueUiTest` (five new methods) and the seven existing
`SpeechInterruptionTest` methods passed together: **12 tests, 6.974 seconds**.

| New method | What the synthetic check establishes |
| --- | --- |
| `alreadyQueuedOldPoseCannotReplaceNewShotAfterRender` | A queued old result cannot replace current text after a real render invalidation. |
| `backgroundInvalidatesOldPoseEvenWhenCreatorResumesNewSession` | Real Activity stop/resume invalidates the old result even after a new session is simulated. |
| `pendingAndActiveDirectionKeepExactSpeechWhileVisualPoseUpdatesThenIdleCanRequestCue` | Pending words and active utterance/focus identity survive automatic pose updates; after completion an idle cue can request focus. |
| `stoppedSessionAndCountdownRejectPoseWithoutChangingDirectionOrFocus` | Ended-session and countdown gates prevent pose visual/speech mutations. |
| `rapidLensSwitchIsRefusedWhileBindingButStoppedControllerAllowsSavedSelection` | The actual switch button preserves pending lens state; a stopped controller permits a saved next-lens choice without opening preview. |

The controller is constructed but never started. Pending-binding fields and
speech state are injected by reflection. Speech uses a deny-only focus boundary:
an eligible idle cue's focus attempt is counted, and refusal prevents playback.
The tests do not reproduce camera binding or real spoken output.

The seven existing interruption methods exercise noisy-output failure and
between-cue notification, the three focus-loss kinds without automatic resume,
stale focus-loss isolation, completion/stop focus release, denied/delayed focus,
and reentrant focus-loss cleanup. They use synthetic callback/focus state rather
than a real earbud disconnect.

Three selected existing `UiWorkflowTest` methods also passed: **3 tests,
4.094 seconds**:

- `launchResumeAndEnabledSequenceNeverOpenCaptureAutomatically`
- `verticalViewfinderAndBackLensPersistWithoutOpeningCamera`
- `objectShotsKeepPersonFramingAdviceOffWithoutOpeningCapture`

Ignored local evidence filenames: `private/evidence/pose-publication-build.log`,
`private/evidence/pose-speech-ui-tests.log`, and
`private/evidence/pose-ui-workflow-regressions.log`. Final APK hashes and storage
accounting are maintained by the parent in the status ledger.

## Remaining evidence limits

Real front/back preview rebinding, sustained pose inference during a shoot,
spoken countdown timing, physical capture/finalization, AirPods routing and
disconnect behavior still need an attended test. This run does not prove pose
accuracy, useful creative advice, speech completion detection, iQOO/NPU execution,
Office Kit integration or creator benefit. Source remains pre-event preparation;
these passing tests do not establish event-code eligibility.
