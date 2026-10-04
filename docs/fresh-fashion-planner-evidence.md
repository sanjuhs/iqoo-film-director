# Three prospective local fashion plans — 4 October 2026

Pre-event research under `prototype/`. A fixed three-case evaluation called the
production default CPU planner sequentially, once per synthetic brief, without
retry, prompt changes, fallback or model downloads. Full briefs, actual final
shot fields, timings, provenance and cleanup facts are retained in
[baseline results](fresh-fashion-planner-baseline.json). These are bounded
prospective checks, not a general accuracy or creator-benefit estimate.

## Baseline findings

The five-shot jumpsuit draft retained the supplied garment and invented no
color, material, garment part or prop in the inspected directions/captions.
Its movement, slight side turn and lens-facing closing were feasible for a
mounted phone; durations were all4seconds. The unspecified Detail was a native
closed creator-choice request, explicitly disclosed rather than learned feature
selection. No actual creator performed these actions.

The navy pullover draft retained the garment/color and supplied moon patch.
The Detail instruction was the exact creator-authored reviewed cue merged after
generation; it is not evidence of native model understanding. The other four
instructions and all captions were actual native model outputs. They did not
introduce the excluded logos/stripes or new props. Two small steps were suggested;
the brief allowed small turns without explicitly banning steps. That is a weak
fit to its preferred movement, not proof that every preference was followed.

The stationary talking-fashion draft failed. Despite “no walking or steps”, it
instructed “Take two small steps while maintaining the waistcoat position.”
All five instructions were silent poses/detail gestures: none directed speaking
about the creator's own layering choice. The waistcoat/color were retained and
no new prop appeared, but that does not satisfy the requested talking workflow.
The failed test and full draft are preserved; role grammar and lexical checks
alone do not establish useful direction.

Actual native generation times were25,291/26,132/29,724ms (wall25,293/26,139/
29,732ms). The one aggregate instrumentation method ended with two targeted
findings after all three attempts, **1 failure/83.548s**. Preferences, model
size/mtime/hash and denied capture permissions were preserved; each planner
closed its worker/native handle and released the model lease. The pinned existing
563,036,064B Qwen3.5 0.8B model executed on CPU, not NPU. No camera/microphone,
source media, speech synthesis/playback, real AirPods or aircraft was used.

This baseline identifies an actual repair target. Subsequent same-brief checks
are regression checks; they cannot retrospectively turn this into a passing
held-out result. Any fresh post-repair case must be reported separately.

The evaluator now additionally accepts `Tell` as the stationary talking Closing's
opening verb, consistent with its original practical-speech criterion. Its
no-locomotion, layering-topic and full-output checks remain. The saved baseline
was collected before this compatibility adjustment and retains its actual failure.

## First constraint repair: improvement and retained failure

The first repair made explicit English performer intent separate from stationary
phone wording and appended reference speech/visual notes. Native decoding used
closed in-place Movement choices and a free-text own-words Closing prefix with
6–8seconds; labels disclosed both constraints. Reviewed conflicting cues were
rejected without rewriting, including a final check after merging creator edits.
Inactive Fashion/other styles retained the original prompt/schema paths.

Three new deterministic and eleven existing Fashion/Talking parser methods
passed. The aggregate native method then failed on both its observed waistcoat
regression and first prospective violet-poncho brief: **14 passing/1 failing
methods,67.807s**. Actual native times39,819/25,947ms. Both drafts stayed planted,
retained supplied garment/color, added an eight-second own-words cue and invented
no inspected prop/material/benefit. But both copied the active-intent example's
“one short sentence about your outfit choice” instead of the actual layering or
styling subject. This is improved speaking behavior with unresolved topic fidelity,
not a successful quality gate. [Full first repair outputs](talking-fashion-first-repair.json)
retain both attempts. No retry/fallback within the run. Model/preferences and
native worker/handle/lease were preserved/released; no actual speech was recorded.

The next causal change removes only that active-intent full-plan example, keeping
compact role purposes, actual-subject wording and identical decoding/validation.
The same two briefs become regression cases on rerun. Removing a copying attractor
is a hypothesis to test, not evidence that a small model understands arbitrary briefs.

## Example-removal regression result

Removing the active-intent JSON example did not solve topic fidelity. All three
new prompt/intent checks passed, but the unchanged two-case native method failed
again: **3 passing/1 failing methods,64.28s**. Native times29,967/32,449ms;
full [regression outputs](talking-fashion-topic-repair.json) remain. Both Closings
said “Tell in your own words about the brief's topic.”, with six-second drafts
and generic Topic/Brief topic captions. The plans retained garment/color, stayed
in place and introduced no inspected prop/property, but did not name the requested
layering/styling subject. No grammar, duration or topic criterion was weakened.
This rerun is a regression result, not a fresh unseen-quality success.

## One raw Closing-first diagnostic

A single default-CPU draw changed only the prompt's exact title order and grammar
root order to generate Closing first. Untouched native JSON was persisted before
assertions; canonicalization reordered objects solely for the existing production
parser, preserving every field. **1 failure/33.991s**: native generation31,162ms,
load+generation32,414ms. It still said “about the brief's actual speech topic.”
without layering. [Full raw diagnostic](talking-fashion-order-diagnostic.json)
records one call, no retry/fallback/rewrite, canonical validation and cleanup.

Independent review also found truncated native garment words in captions:
“waistc” and “waistco”. All directions stayed planted and no inspected new
prop/property appeared, but this supplied no publishable-caption or topic success.
The generation-order experiment is not promoted. No further prompt retry is
counted as a successful native-topic result. Faithful retention of an explicit
creator request is a separate, disclosed product path rather than model grounding.

## Disclosed creator-request retention

The default planner subsequently retained a single literal creator speech
request after genuine native generation. [Hybrid evidence](creator-speech-retention-evidence.md)
records one actual CPU attempt and separates creator Closing from the four native
directions. The raw-fidelity constructors now explicitly disable this merge;
all topic assertions above remain unchanged and all failed receipts remain.
This is a bounded author-preservation repair, not evidence of native topic learning.
