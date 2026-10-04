# Compact take tools — 4 October 2026

Pre-event research under `prototype/`. Assemble take cards previously stacked
seven to nine editing/review buttons. Each card now keeps the selected title,
bounded cut/caption-or-subtitle summary and shot assignment, with **Preview take**
and **Edit & review take**. The latter opens a scrollable **Edit this take** menu.

## Behavior

The menu retains existing shot assignment, trim/typography, offline transcription,
speech-cut suggestion, framing and reorder actions. Subtitle review appears only
for existing timed words, and Move earlier only for a later take. Preview remains
direct paused playback. No model, speech, camera or microphone starts on menu open.

Dispatch validates the still-showing current dialog, foreground lifecycle, Assemble
page, idle state and the exact Take object still in the list. It resolves the fresh
index and closes the menu before opening the existing tool. Reordering while the
menu is open cannot redirect a trim to another take. Render, background and
destruction dismiss the menu; retained stale buttons cannot edit or reorder.
Cancel leaves take edits and saved preferences intact. This changes tool access,
not editing, capture, inference or export algorithms.

## Verification

The first fresh API36 emulator run passed **17 of19 methods /36.394s**: five framing
journeys, ten existing workflows and two new menu checks. Two new fixtures failed:
the subtitle negative control is **Later**, not Cancel; Android clears the menu’s
dismissal reference on a subsequent main-queue turn. Test-only corrections use
the real label and check reference clearing after idle, while clicking retained
stale buttons immediately after dismissal and retaining strict edit/order/no-tool
assertions. Production was unchanged; the failed receipt remains retained.

The corrected runner passed **22/79.937s**: four new take-menu methods, three
assembly edit methods, six subtitle-batch methods, four shot-assignment methods,
four plan-package methods and one late-take recovery method. Together the two
runners establish **37 distinct passing methods**, not one37-method suite.
Builds passed in1s and656ms; normal app/test installs used no permission grants.

The new checks open the actual card menu; conditional tools and real assignment/
subtitle dialogs preserve existing edits on cancellation. Actual trim fields save
exact millisecond edits to the same take after a live index change; Move earlier
resolves the fresh index and preserves selection/edits. Retained tools after
dismissal, render, true CREATED background or removal remain inert. Existing
framing/subtitle/reorder tests now follow the menu journey and assert visible
scrolled actions. Package/recovery checks retain synthetic-original integrity
and ready-package invalidation/restoration. No private media or physical capture
was used. Fixture/UI times are not product-latency measurements.

Root also ran the ordinary app on this empty emulator, generated its labelled
DemoAssets and inspected the compact cards plus all six visible tools for its
second take. Actual Cancel left the complete saved preference bytes unchanged.
Screenshots/hierarchy/proof are ignored local evidence. This is one synthetic
layout, not attended creator usability or AirPods acceptance. The emulator had
host camera/audio disabled, airplane mode verified and no separate model weights.

Built/saved/independently read physical installed APK: 52,838,059B,
SHA256`5fdbc089736728c316382256fdf419e9ea43f87a7d2861f315413793916021a0`. Packaged notices remain source-identical,31,635B; separate
Qwen/Whisper weights remain outside Git/APK, bundled ML Kit assets remain, and
no Internet permission is declared. Normal Main launch was requested behind secure
keyguard; camera/microphone remain denied. No unlock, private upload or aircraft
action occurred. No new SDK/JDK/system-image/model/dependency downloads.

Only the owned AVD and matching registration were removed; the existing other AVD
stayed untouched. Qualified observed temporary peak **10,431,892,565B**, above10GB
aim/below15GB cap. Final adjacent accounting **9,300,356,181B**, below
both, precedes documentation/Git commit. All three installed APKs and unchanged
28 owned/0pending/8,319,196B gallery metadata are included, alongside historic
missing-archive/cache reserves. Existing full-inventory qualifications persist.

Attended filming/AirPods, useful unseen-brief/real-footage direction, iQOO/NPU,
Office Kit, eligible event code and accepted idea submission remain open. Sampled
framing’s documented pending ML Kit task bound remains a separate limitation.
