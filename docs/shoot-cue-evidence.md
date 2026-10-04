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

## Follow-up verified 4 October, 18:24 IST

A preview failure previously left `previewRequested=true` and `ready=false`,
so the lens selector continued treating a failed request as a pending binding.
`CaptureController.previewFailed()` now uses the existing stop-preview cleanup
before reporting its fixed error: pending/ready/analysis flags are cleared,
binding generation advances, and failed use cases are detached. A duplicate
failure or callback after controller closure is suppressed.

The Main camera-error path also now invalidates the shoot-pose generation,
ends the session, cancels countdown/sequence work, stops speech and disables the
pose coach. A queued old pose result cannot replace the camera error or restart
automatic pose speech. Retry remains an explicit creator action.

The parent executed the expanded **seven `ShootPoseCueUiTest` methods** together
with **three `AssemblyEditUiTest` methods**: **10 passed, 14.428 seconds**, on a
fresh, unlocked Android 36 ARM64 project emulator. Camera/microphone permissions
remained denied and host camera/audio were off; no framework speech playback was
used. The earlier 12-test and three-test results above remain separate history.

The two added camera-error methods provide these bounded checks:

- `failedPreviewClearsPendingAndPartialUseCasesBeforeErrorThenAllowsLensChoice`
  injects pending fields and an unbound partial `ImageAnalysis`, with the provider
  null. It calls the terminal failure helper and verifies cleanup is visible
  inside the single error callback, repeat/closed failures are silent, and the
  actual switch button accepts a saved opposite lens without opening preview.
  This does not induce a real CameraX binding failure.
- `deniedCameraStartEndsPoseSessionAndQueuedFailedAttemptCannotReplaceError`
  calls the actual `MainActivity.startCamera` path with camera permission denied.
  It constructs the bundled pose client, but returns before requesting a camera
  provider or receiving any frame. It verifies the actual error path ends the
  session and disables posing, then rejects an injected queued result from the
  failed attempt while preserving error/framing text and idle speech. There is
  no pose inference, microphone recording, model generation or audible output.

Ignored local evidence filename: `private/evidence/subtitle-camera-ui-tests.log`.
Real binding, live capture, full-sequence timing and AirPods routing remain
unproved; this follow-up adds no physical hardware or creator-benefit claim.
