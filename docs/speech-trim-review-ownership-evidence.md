# Own the suggested speech-cut review — 4 October 2026

Pre-event research under `prototype/`. The speech-cut dialog was untracked.
Retained Apply/Keep buttons could still apply a trim or discard a newer suggestion
after backgrounding/new work. Its analysis callback also released busy without
reader/generation ownership and could render while backgrounded. This is a
reproducible source/seam defect, not a claim of observed private data loss.

Main now owns one current attached speech-trim dialog in resumed Assemble.
Apply/Preview require its exact pending Take/candidate and unchanged URI/source
duration/range. Keep may discard only that exact current candidate, even when
its source/range has since changed; it does not modify the Take. Dismissed/hidden/
replaced actions abstain. Apply preserves source, titles, selection, mappings,
caption/provenance, subtitle list/rows and source timestamps; only in/out changes.
Numerical SpeechTrim heuristics, bounds, native runtime and export policy stay
unchanged.

Preview closes its current dialog and retains a finished valid suggestion for
explicit re-review after returning. Background/render/destroy dismiss old dialog
without applying or discarding a completed suggestion. Returning from preview
also restores the prior list position when that suggestion is pending. This is
in-memory, not a new persisted candidate/schema or automatic apply.

Unfinished analysis owns an exact reader, generation and busy gate. Cancel,
background/render/destroy close that reader; busy remains held until its resource-
only closeWhenIdle hook drains. Old terminal/resource callbacks cannot clear
newer work or publish suggestions. Shared-reader replacement uses exact identity.
Normal terminals already occur after native/resource release. Existing single-
take subtitle ownership remains distinct; no generic reader/model bypass added.

## Verification scope

Five new own-empty-unlocked-emulator methods use the actual production private
TrimListener with idle readers and explicitly synthetic terminals/candidates.
Candidates are calculated by the unchanged deterministic SpeechTrim.analyze from
synthetic PCM and timestamped test words, without ASR or source access. They cover
current Apply to a reordered exact Take/non-trim aliases, stale Apply/Keep/Preview
against a newer pending candidate, blocked current Preview dispatch plus actual
background/resume/nonzero scroll/explicit re-review, source/range/replacement/
render refusal and changed-range Keep, and held idle cleanup gates/old callbacks
versus newer busy work/background cancellation. Public Instrumentation activity
monitor intercepts the one current Preview intent before playback; stale Preview
has zero new dispatches. This is intent/dialog/lifecycle evidence, not actual
PreviewActivity media playback or native ASR concurrency. Fullprefs restore.

Offline build1s (11 executed/51 cached) and normal app/test installs without
permission grants. Physical focused **3/13.245s** passed: existing actual
SpeechTrimExport1 plus two cancellation API methods. On the known synthetic
padded fixture, actual CPU tiny.en analysis5,532ms and exported-reel transcription
4,312ms each returned3segments. Timing includes decoding/model loading/
transcription, not isolated neural inference. Actual source8759ms → explicit
reviewed range1220–7480ms → actual encoded720×1280 reel6263ms. Output JSON source/
timeline mapping, burned opening/ending captions and absent middle caption,
known jacket/green/outfit speech, original SHA/prefs, subtitle identity/times and
stale/repeated application refusal all passed. The synthetic result is retained
locally with only its owned recovery journal removed, preventing hydration into
the creator's last output. No attended Main→ASR→Apply path is established.

Fresh own API36 UI **39/74.806s** passed: new trim-review5, standalone-proposal5,
corrective-subtitle4, workflow10, new-reel4, fixed-controls4, stop-preference3 and
early-Finish4. No runtime failures; cameras/audio disabled, airplane1, capture
denied and separate weights absent. Current Preview intent was blocked before
media access. Physical gallery metadata1/0.284s recorded30owned/0pending/
8,534,454B, including the one freshly retained labelled synthetic reel.

Built/saved/independently read installedAPK52838059B SHA256
91c09281a3d3f8c16c12c36326c620d26b03ee3970c1c69ab82a02d9afd7abea;
source-identical notices, separate Qwen/Whisper weights excluded, bundledMLKit
remains/noInternet. Attended filming/full ASR-review flow, AirPods, iQOO/NPU,
OfficeKit, useful semantic direction and eligible accepted entry remain open.

Independent final source review found no blocker. Normal Main launch was requested
behind the physical secure keyguard at23:21; camera/microphone remained denied.
No unlock, grants, private capture/upload, playback or aircraft action. Only own
AVD/matching registration were removed after emulator parent completion; existing
other AVD untouched. The first cleanup helper invocation rejected a path-shaped
argument before mutation; correct basename invocation passed. Qualified peak
10,873,353,179B and final adjacent23:23 allocation9,336,525,787B include external
artwork once and historic missing-archive/cache reserves. Full inventory/runtime
exclusions persist. No model/dependency/SDK/JDK downloads this increment.
