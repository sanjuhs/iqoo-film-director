# Independent cuts and editor position — 4 October 2026

This is pre-event research under `prototype/`. **Use another moment** in the
take tools adds an independent cut directly beside its original. It reuses the
original local URI without copying or rewriting video, starts unselected and
can choose a valid range anywhere in the full source. Trims, title, fallback
caption, source-time subtitle objects and reviewed mapping lists are independent.
Retained timed words keep their transcription provenance; changing a fallback
caption alone marks it manual only when no timed subtitles exist. Review words
for the new range before selecting it. This is manual editing, not automatic
selection of the best performance.

`TakeCutDraft` freezes exact take identity, URI, source facts and all editing
metadata. Save rejects changed facts, replaced/removed takes, background state,
busy processing and old dialogs. Cancel clears ownership immediately; render,
stop and destruction dismiss the owned cut editor. Existing **Trim & typography**
uses the same guards and preserves original cue/mapping list identity. Bounds
remain at least250ms inside the full source. Invalid source metadata or overlapping
subtitle cues must be reviewed before this editor opens.

Shot forms now label Title, Direction, On-screen caption and Length (seconds).
Blank, noninteger, overflow and values outside2–60 seconds remain unapplied with
an inline error instead of silently falling back or clamping. Successful shot
and cut saves restore the page's previous scroll position. A queued restoration
checks exact page identity, render generation, tab and resumed state; callbacks
retained across replacement/background cannot scroll a later page.

## Measured checks

Physical headless runner passed **7/2.034s**: four new immutable-draft methods and
three serializer, real ZIP and native encoding methods. The strict existing
synthetic `jacket-speech.mp4` is59,313B/5746ms, SHA256
`fb95e4027757071bc33f02562945f02510756a41dbfd31badc16eced398143f0`.
Source hash/size/mtime, preferences and denied capture permissions were preserved.
No ASR or model ran in these checks. Authored test cues carry a synthetic
creator-reviewed transcription label; that label is not new ASR evidence.

Two cuts500–1500ms and4000–5000ms retain their original source-time cues and map
visible words to200–600ms and1300–1700ms on a2000ms nominal reel. Off-range cues
stay retained but invisible. The actual three-entry empty-plan ZIP copies one
original for both ranges, with identical original hash and separate edits.
Native Media3 output is H264720×1280/AAC,2136ms measured on Android and2.136236s
by host ffprobe,54,127B/SHA256
`3e416157aaec838bc9ee3ba42e14ef277016c4b8f6d668acf0ad22a4f7032281`.
Caption-region white-pixel counts were2684/3208 on the two visible cues versus0
outside; both decoded caption frames were also visually reviewed. This checks
appearance on one synthetic clip, not transcription accuracy or creative quality.
The2492B edit JSON has SHA256
`7296f54d642ccb8b4d1b1cd30dcd210205c066e0c379b9829b498859d1ad82c8`.
The verified completed synthetic pair remains saved; only its exact COMPLETE
test journal was detached to prevent normal app recovery adopting test output.

Initial fresh-emulator runner passed33 of34 methods. One new immediate
Cancel→retained Save check exposed queued framework dismissal allowing a canceled
edit to apply in the same UI event. Production now synchronously clears ownership
before dismissal; the exact regression remains and adds an immediate null-ownership
assertion. Final isolated runner passed **34/61.489s**: eight shot-framing/editor,
four new cut, five footer, four tools, three assembly and ten workflow methods.
Tests preserve preference bytes and absent separate weights/denied capture; the
new cut UI fixture is an unread six-byte sentinel, not a playable video. Controlled
stale buttons/cache pointers are distinct from native media evidence.

After runner termination, the ordinary app's default second-shot editor labels
and unchanged nonzero position after Save were inspected. DemoAssets take tools
opened the new form; saving an authored2600–3000ms cut placed it beside the
unchanged500–2500ms original, unchecked. Preferences independently confirmed the
same URI, adjacency and3 selected/4 takes. Three form/card screenshots were viewed.
This is synthetic layout evidence, not attended creator usability. An initial
host preference read used the wrong filename and failed parsing; it was corrected
to the observed `shoot.xml`, and the failed read supplied no evidence.

## Build, permissions and accounting

Initial/final offline builds each succeeded in1s using existing installations.
Normal app/test installs used no grants. Final built/saved/independently read
physical installedAPK52,838,059B/SHA256
`27698963957b026d93beb86f9bc27f2687d48a57b73fd529ae1c895d67b097d5`.
Notices31,635B/source-identical; separate Qwen/Whisper weights remain excluded
from Git/APK and bundled ML Kit assets remain. Internet permission is absent.
Final normal Main launch was requested behind secure keyguard; camera/microphone
remain denied. No unlock, capture, audible playback, private upload or aircraft
action occurred. Gallery metadata passed **1/0.331s**:29 owned/0 pending/8,373,323B,
including the new54,127B synthetic export.

Own API36 emulator used the existing image, disabled camera/audio/snapshots and
verified airplane mode. Its process reached terminal before only its exact AVD
and matching registration were removed; the other AVD was untouched. Qualified
observed temporary peak **11,130,327,920B**, above10GB aim/below15GB cap; final
adjacent **9,328,042,864B**, below both. External cover and historic missing-archive/
cache reserves remain counted. Existing full-inventory exclusions persist; no
new models, SDK/JDK, system images or dependencies were downloaded.

Reproduce by building app/test offline and installing normally. Run the two
physical classes and the six emulator classes listed above through
AndroidJUnitRunner, waiting for termination before ordinary UI inspection.
Ignored receipts are under `private/evidence/another-moment-*`. Real creator
capture/AirPods, useful learned direction, iQOO/NPU, Office Kit, eligible event
source and an accepted submission remain unfinished; the goal stays active.
