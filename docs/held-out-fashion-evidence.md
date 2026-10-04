# Fresh ordinary fashion brief — 4 October 2026

Pre-event research on the Nothing Phone (3a), using the existing Qwen3.5 0.8B
local **CPU** planner. One fresh synthetic ordinary brief exercised the daily
solo-fashion workflow after the Product-only prompt repair. No Fashion prompt,
grammar, model or output was changed to make this fixture pass. It used no
reviewed reference cues or deterministic creator-instruction merge.

## First actual result

`ScenePlanningTest.heldOutOvershirtAndJeansRetainsSuppliedOutfitWithCreatorChosenDetail`
passed **1 method / 40.082 seconds** on its first execution. The actual CPU
planning request took **39.998 seconds**, measured through the production
callback rather than as an isolated native-kernel benchmark. The brief supplied
a cream cotton overshirt, dark jeans and two chest pockets, and requested small
body turns or one or two steps with the filming phone already mounted.

The full synthetic output at **18:25:07** was:

| Role | Generated performer direction | Caption | Planned length |
| --- | --- | --- | ---: |
| Hero pose | Stand wearing the cream cotton overshirt with dark jeans. | Outfit | 4 s |
| Movement | Take two small steps. | Small steps | 4 s |
| Detail | Show one visible garment detail you choose. | Chosen detail | 4 s |
| Side pose | Turn slightly sideways. | Side view | 4 s |
| Closing | Look at the lens and hold your pose. | Final pose | 4 s |

Model label: **Qwen3.5 0.8B · local CPU · editable AI draft · creator-choice
detail constraint**.

Independent direct review confirms that Hero retains both garments and their
supplied descriptors. Movement and Side pose give small performer actions;
Closing gives an eyeline and held pose. No invented zipper, logo, pattern,
additional fabric/color, part-specific color/material, prompt-example object
or filming-device operation appeared in this output. Captions are bounded,
readable pose/outfit labels. Five planned four-second takes total 20 seconds;
this is not an observed shoot or exported reel duration.

## Supplied feature and creative limits

**The draft does not select the supplied two chest pockets.** Production's
existing ordinary-Fashion policy constrains Detail to a closed creator-choice
instruction/caption unless there is an explicit creator-reviewed named Detail
cue. The native model decodes that closed choice; it is disclosed rather than
presented as learned feature selection. Ordinary free-text feature facts do not
bypass this rule. The creator can edit the shot or author a reviewed Detail
cue; retaining such a manual instruction would be creator authority, not AI
grounding evidence.

The other four roles are native bounded fields. Their small-step, side-turn
and closing wording is terse and largely generic, similar to the previous
raincoat draft. This result verifies selected fact retention and policy behavior
for one fresh input, not varied creative coverage, useful pocket framing,
broader fashion accuracy or confidence in the separately repaired Product
prompt. The [Product evidence](product-detail-evidence.md) retains its initial
omitted-facts failure and subsequent repair.

## Checks and retained evidence

The test checked five exact Fashion roles, appropriate action prefixes,
90-character directions, 30-character captions, whole-second 3–8-second
durations, actual AI IDs/local-CPU disclosure and both garments retained. It
checked targeted unsupplied facts, tabletop/example leakage, equipment captions
and a bounded English direct device-operation pattern. These lexical checks
are not complete semantic, physical-feasibility or safety validation.

Ignored evidence:

- `private/evidence/held-out-fashion-test.log`: successful runner.
- `private/evidence/held-out-fashion-plans.log`: complete synthetic brief,
  five-shot output, model label and timing before semantic assertions.

Camera/microphone permissions remained denied; the target has no Internet
permission. No camera, microphone, playback, cloud call, model download or
training was performed. Approximately 40-second planning is not real-time
coaching. This check provides no NPU, live posing, AirPods, iQOO, aircraft,
Office Kit, attended usability or eligible event-submission evidence.
