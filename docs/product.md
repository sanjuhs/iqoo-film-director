# Mini Film Director — Android product specification

Research proposal, 4 October 2026 (IST). These are intended behaviors, not an
implemented app. Develop on the user's Nothing Android phone and validate the
complete demonstration on iQOO. The iPhone is optional reference equipment.

## The useful promise

Help a solo fashion creator leave a shoot with the footage needed for a reel.
The director turns a brief into an editable plan, guides the next take, reviews
selected clips and suggests pickups. The creator controls style and approves
decisions. A DJI Mini 4 Pro is one possible camera; the workflow also supports
phone footage and manually flown drone footage.

First story: “20-second editorial streetwear reel; emphasize this jacket.”
One outfit, one location and five shots are enough to test the product:

| Shot | Desired coverage | Suggested camera |
| --- | --- | --- |
| Outfit hero | Full outfit, including shoes, with deliberate composition | Phone |
| Jacket detail | Fabric, fastening or design feature readable in frame | Phone |
| Walking movement | A usable short movement take | Phone or manually piloted drone |
| Side silhouette | Outfit shape from another angle | Phone |
| Establishing view | Context for the location and subject | Phone or manually piloted drone |

Camera assignments and duration are editable suggestions. The plan never implies
that a flight is feasible. At a restricted venue, demonstrate with imported clips
and a clearly identified bench preview.

## Creator workflow

1. **Brief:** enter the subject, intended reel length, mood, garment emphasis and
   available cameras. Offer text first; push-to-talk is a later opt-in feature.
2. **Plan:** show a short ordered shot list, reference framing and the reason each
   shot helps. Let the creator revise, reorder, remove or add shots. Identify any
   manually authored/template plan accurately.
3. **Guide:** show one next shot, a framing reference and concise instructions.
   The creator starts capture through available supported controls and marks a
   take complete. Keep manual capture useful even if DJI is disconnected.
4. **Import:** invite the creator to select the new takes. Relate each clip to a
   planned shot and let the creator correct the mapping.
5. **Review:** show evidence for usable coverage, framing concerns and uncertain
   findings. Suggest one highest-value pickup instead of overwhelming the shoot.
6. **Decide:** accept the take, retake it, skip the suggestion or edit the plan.
   Creative disagreement does not force a retake.
7. **Export:** select the clips, order and notes to send to the laptop. Actual
   Office Kit transfer is a separate integration gate.

## Guided clip import

Use Android's [selected-media picker](https://developer.android.com/training/data-storage/shared/photo-picker).
Ask for chosen clips rather than access to the entire gallery. Provide an
instruction such as “Bring these clips onto this phone, then select the takes”
for footage still on a drone/card. Do not promise automatic aircraft-media import
until tested. Explain download progress when a picker item is cloud-backed.

Show import progress and errors, clip duration/orientation, a thumbnail and
suggested shot mapping. A clip can cover several shots, and several takes can
belong to one shot. Creator corrections remain authoritative. Avoid silently
marking every imported take as good or every missing mapping as missing coverage.

Retain selected URI permissions where supported, or make bounded private proxies.
Keep original footage intact. Track the sampling timestamps, original aspect and
any resolution reduction. Offer retry/remapping when media is unavailable.
Private clips and labels stay local; external analysis needs explicit consent.

## What AI review should say

Separate three tasks: measurable image diagnostics (blur/exposure), visible
composition (person, pose, crop) and semantic coverage (is the requested jacket
detail present?). Each requires its own evidence. A pose model alone cannot
recognize fabric details; one sampled frame cannot prove a smooth walking take.

Return a proposed shot mapping, a timestamped finding and a useful next step:
“The full-body take cuts off the shoes at this sampled moment. Review this frame
or try a wider composition.” A coverage suggestion can say “I have not identified
the jacket-detail shot in the selected clips”; it must allow remapping and review.
Do not claim unseen/unimported footage was analyzed. Clearly show uncertainty.

## Stop/go decisions

| Decision | Continue when | Pause or fallback when |
| --- | --- | --- |
| Connection probe | Real aircraft identity, fresh telemetry and changing preview | Registration/USB/preview fails; record failure and use imports |
| Display framing advice | Subject detected, frame fresh and held-out quality gate met | Subject absent, stale input or uncertainty; ask for creator review |
| Accept a take | Creator agrees with the evidence | Retake, skip or revise the plan |
| Learned model experiment | Beats simple baselines on separate shoots and fits the device budget | Keep the simpler measured method and disclose its limits |
| Aircraft action, later | Pilot confirms a bounded action and tested manual override is available | No confirmation, stale state or failed bounds; no execution |
| Export/Office Kit | Creator approves selection and real destination/transfer works | Preserve the shoot pack and report the transfer failure |

## Tiny learned director and camera positions

Reuse a pretrained visual detector, then train a small dense model to choose
framing suggestions from measured features and the requested shot type. See
[tiny model design](tiny-director-model.md) and [vision candidates](vision-research.md).
Train preferences from consented creator labels; evaluate unseen whole shoots.
Call this a learned framing model, not a language model or learned flight controller.

Desired front/side/detail views are **shoot goals**. Saved model weights are
**training checkpoints**. A storyboard can suggest viewpoints without learning
navigation. Turning those goals into drone paths requires a separate verified
DJI executor, actual geometry/state and action-level pilot approval. No vision
classifier establishes a safe orbit from a single image.

## First demonstration and implementation order

Deliberately omit the jacket-detail shot, import the other takes, show whether
the assistant identifies the gap, capture a pickup through supported controls
and export the creator-reviewed sequence. Compare its suggestions with creator
labels; report failures and review time rather than promising a perfect film.

First build the propellers-off [connection probe](mini4-connection-test.md), then
the editable plan/import/review workflow. No motors, flight, recording or gimbal
actions belong to the probe. A useful local model is the next measured experiment;
autonomous filming is later scope. The [delivery plan](../plan.md) preserves
event-code provenance and [status](status.md) lists what is actually verified.
