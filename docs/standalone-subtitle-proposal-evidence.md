# Review fresh subtitles before replacing saved words — 4 October 2026

Pre-event research under `prototype/`. Standalone **Generate offline subtitles**
previously assigned/saved returned cues before opening the editor. That erased
corrected words even if the creator pressed Later; an empty ASR result silently
cleared them. The earlier corrective editor ownership work did not repair this
upstream assignment. This is a concrete source defect, not an observed private
creator data-loss claim.

Generate now captures the exact current Take and immutable original facts,
reads the chosen local source and proposes deep-copied display rows. Completion
makes no subtitle/origin assignment or persistence change. **Save reviewed
subtitles** validates all corrected rows and replaces only the still-current
original Take. Later, unreadable/empty output and cancellation keep saved words.
Explicit Remove retains its existing semantics; it creates a new empty list,
preserving an aliased take's original list. Reordering does not redirect the
proposal to an old numeric index. Changed source/cut/selection/mapping/provenance/
word facts or removed/replaced Take invalidate the proposal.

`SubtitleReviewDraft.withProposedCues` retains the original immutable matching
baseline separately from proposed display rows, including after chained proposals.
Numeric errors, overlaps and blank words remain editable; null structure and
more than500 rows reject. Proposal creation never recaptures a changed Take.

A standalone job owns an exact reader, generation and busy gate. Explicit Cancel,
background exit, render or destroy close that reader; a shared replacement occurs
only when its identity matches. Cancellation retains busy until the exact
reader's resource-only closeWhenIdle hook drains. Old success/error/resource
callbacks cannot release newer work or apply words. Normal ClipTranscriber
terminals already follow native/resource release; its runtime/API is unchanged.
The visible action asks the creator to keep the app open. Background exit
cancels this single-take proposal instead of applying unreviewed words.

## Verification scope

Three new headless proposal tests exercise deep immutable copy, original fixed-
size cue aliases/provenance, changed facts before/after proposals, chaining/empty
views and malformed/count bounds. Five new own-empty-unlocked-emulator methods
use the actual production private listener factory with idle readers and explicitly
synthetic terminals. They cover proposal+Later without saved-state mutation,
explicit Save to a reordered target, empty/malformed/changed/replaced Take,
cancellation with a held idle resource-cleanup gate and stale terminals/hooks,
and true ActivityScenario background/resume plus a retained old Save button.
No native request is fabricated. The held cleanup gate is controlled metadata,
not evidence of actual ASR concurrency. All original preferences restore.

Offline build1s (11 executed/51 cached) followed by normal app/test installs.
Physical **15/5.254s** passed: new proposal3, existing original snapshot4 and
transcriber cancellation/resource8. The existing labelled padded English speech
fixture actually ran CPU tiny.en: **3 segments /4,982ms**, known jacket/green/outfit
words, original source unchanged and native registry released. Timing includes
decoding/model-loading/transcription. That runtime test
is separate from synthetic proposal UI; it does not show actual attended UI→ASR→
Save, general recognition quality, iQOO/NPU or creator benefit. No microphone,
playback, private source, preferences or model download was used by that test.

Fresh own API36 UI **34/65.073s** passed: new proposal5, corrective subtitle4,
workflow10, new-reel4, fixed-controls4, stop-preference3 and early-Finish4.
No runtime failures. Cameras/audio disabled, airplane1, capture denied and
separate weights absent. Independent final source review found no blocker.
Existing batch missing-subtitle behavior is unchanged; this repair concerns the
single-take Generate action.

Built/saved/independently read installedAPK52838059B SHA256
3dd0f7dff38dff36c1666d292feef800e5da3cc3b7da1168a2634718e3887455;
source-identical notices, separate Qwen/Whisper weights excluded, bundledMLKit
remains/noInternet. Physical gallery metadata1/0.829s unchanged29owned/
0pending/8,373,323B. Normal Main launch requested behind secure keyguard,
camera and microphone denied. Attended full workflow, AirPods, iQOO/NPU,
OfficeKit, trained/director benefit and event acceptance remain open.

A screenshot-only test increment followed the34-method functional run. Its first
focused invocation failed before capture because the instrumentation TEST cache
path was unavailable to the target process UID (ENOENT/createNewFile). Existing
proposal assertions before capture passed; this is a test-artifact location
failure, not an app/model failure. Parent retains the failed receipt.

Screenshot helper-only repair uses the target process's writable cache and
refuses overwrite. Final test build540ms (6 executed/56 cached); focused
proposal+Later **1/1.689s** passed, including the unchanged original-word/prefs
assertions. Parent independently inspected the actual rendered synthetic proposal:
full Save/Later explanation readable, source times/words visible and all three
Save/Later/Remove actions reachable. This is controlled synthetic-callback UI
presentation, not ordinary actual ASR output. Only that owned PNG was read and
removed from emulator cache. Production app APK remained exactly the same hash.

Only own AVD/registration removed after parent terminal; other AVD untouched.
Qualified observed peak10536879296B and final adjacent
9338096076B. Historic missing-archive/cache reserves
and full-inventory/runtime exclusions remain. No new downloads this increment.
