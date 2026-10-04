# Subtitle review ownership — 4 October 2026

Pre-event research under `prototype/`. **Review subtitle words & timing** now
owns one current dialog. Save and Remove require that exact visible dialog,
resumed idle Assemble screen, original Take identity and unchanged source/editing
facts. Later invalidates ownership synchronously. Render, stop and destruction
dismiss the editor, so retained actions cannot replace or clear newer words.
Save/Remove restore the previous Assemble scroll position. Cue number and
Start/End/Words labels remain visible when fields are filled.

`SubtitleReviewDraft` freezes all take scalars, ordered mappings and immutable
source-time cue facts without reading media. Unlike a validated cut draft, it
deliberately accepts existing negative, reversed, zero, out-of-source, overlapping
or blank cue data for correction. Unreadable structure, missing duration and
more than500 segments are rejected without mutation. Save parses every row and
checks full-source bounds and nonoverlap before replacing the list; blank words
still need valid times before being omitted. Existing provenance behavior remains.
Remove assigns a new empty list and manual provenance, preserving fixed-size or
aliased original lists. Cuts, selection, titles, fallback captions and mappings
stay unchanged. Words outside the current trim remain in source time.

## Verification

Physical headless `SubtitleReviewDraftTest` passed **4/0.019s**, checking immutable
independent facts, correction of malformed existing data, changed source/deep
facts/object identity and bounded count/structure. Sources are nonexistent
synthetic URIs; no file/provider/model work occurs.

Fresh own API36 emulator passed **36/68.802s**: four new
`SubtitleReviewUiTest`, three `AssemblyEditUiTest`, six `SubtitleBatchUiTest`, four
`TakeToolsUiTest`, four `AnotherCutUiTest`, five `AssemblyFooterUiTest` and ten
`UiWorkflowTest` methods. No failures in this increment. New checks use actual
visible take-menu actions with controlled synthetic cue metadata, absent separate
weights and denied capture. They establish all-or-nothing negative/overlap/NaN
correction, blank-word omission, intact ready-cache pointers until valid Save,
nonzero scroll restoration, immediate Later→retained Save/Remove rejection,
stale render/replacement/URI/deep-word/mapping/duration rejection and real
CREATED→RESUMED background transitions. Legitimate Remove from an aliased
`Arrays.asList` leaves the other take's two words intact. All preferences are
restored and only owned temporary pointers removed. No ASR, source reads, live
capture, inference or audio playback occur in these new methods.

After runner termination, root generated the ordinary app's labelled DemoAssets,
stopped only that emulator app before injecting two explicitly authored synthetic
cues, then reopened the real menu/form. Both cues and all three stacked actions
were readable; the form and saved-card screenshots were visually reviewed.
An ordinary Save changed only the first cue's words and review provenance,
retaining250–850ms and1250–1800ms source times, the500–2500ms trim, all other take
facts and both other takes. The first cue intentionally starts before the cut.
The card returned to the same visible position. Saved preferences independently
confirmed this. Fixture review provenance is not new ASR execution or creator
speech accuracy; this is one synthetic layout, not attended usability.

This repair governs review actions. Standalone per-take transcription retains
its existing completion behavior: read-only audit found onStop does not cancel
that request, unlike selected batching. Ordinary busy controls prevent editing;
no wrong-take overwrite was demonstrated. This increment supplies no new ASR
cancellation/background or hardware concurrency evidence.

## Build and accounting

Offline app/test build succeeded in1s with the existing toolchain. Normal installs
used no permission grants. Built/saved/independently read installed physicalAPK
52,838,059B/SHA256
`43614cd2a4e84deb88d9e58d81b15f43cb95d2543d02a342d21f5e8dfb23ca62`.
Packaged notices remain source-identical31,635B; separate Qwen/Whisper weights
stay excluded from Git/APK, bundled ML Kit assets remain and Internet permission
is absent. Gallery metadata passed **1/0.252s**, unchanged29 owned/0 pending/
8,373,323B. No new physical media output. Normal Main launch was requested behind
secure keyguard; camera/microphone remain denied. No unlock, private upload,
recording, audible playback or aircraft action occurred.

Own fresh emulator reused the existing API36 image, with camera/audio/snapshots
disabled and airplane mode verified. Its process reached terminal before only
its exact AVD and matching registration were removed; the other AVD stayed
untouched. Qualified observed temporary peak **10,460,685,716B**, above10GB aim/
below15GB cap; final adjacent **9,329,161,620B**, below both. External cover,
historical missing-archive/cache reserves and latest owned-gallery metadata remain
counted with existing inventory/runtime exclusions. No new models, SDK/JDK,
system images or dependencies were downloaded.

Reproduce by building/installing normally offline, running the physical class
and seven emulator classes above through AndroidJUnitRunner, then waiting for
termination before ordinary UI inspection. Ignored receipts are under
`private/evidence/subtitle-editor-*`. Real creator filming/AirPods, useful learned
direction, iQOO/NPU, Office Kit, eligible event source and an accepted submission
remain unfinished; the goal stays active.
