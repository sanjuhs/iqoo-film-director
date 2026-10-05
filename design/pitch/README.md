# Mini Film Director idea deck

## Current native deck — revision 7

The [published Google Slides deck](https://docs.google.com/presentation/d/13kmYRemUZdNRtpRoBeaoFUkAgn4maZBF2vsHzhPQGgA/edit)
was updated in place on 5 October 2026 and now contains nine slides. The existing
submission link and visual style are preserved. Added reference-shot detection
with Python/PySceneDetect, Qwen scene descriptions, Whisper speech/caption drafts,
and a proposed post-capture media workspace. Optional Dreamlite image-to-image
cover/still editing is a candidate; the exact model remains unconfirmed.

[Revision plan](revision-7-plan.md), [native edit record](native-update-v7.json)
and [verification](verification-v7.json) record this update. The edit record has
non-idempotent duplication requests and must not be replayed against the updated
deck. Native readback found nine slides and zero structural checker issues; all
nine pages of the native Google Slides PDF were rendered and visually inspected.
The pipeline is proposed, with no new model execution or phone timing claimed.

The local builder and story below describe the earlier seven-slide revision 6
baseline; they do not regenerate the current nine-slide native deck. Native
revision 7 PDF and review renders remain under ignored `output/pitch/revision-7/`.

## Earlier local baseline — revision 6

5 October 2026. Seven-slide, self-paced iQOO Open Innovation concept deck in a
simple white/navy/coral style. It accompanies the phone-shaped design prototype.
The preparation code remains explicitly pre-event research. Competition source
must be created during the permitted window unless the organizers approve reuse.

## Story

1. Mini Film Director: a local director for solo creators.
2. The solo creator's four filming jobs and first fashion workflow.
3. Actual plan, countdown and editor screenshots from the reference walkthrough.
4. Editable Shoot/Edit/Projects workflow.
5. Editable proposed Moonshine, Qwen3.5 0.8B and Kokoro architecture.
6. Earlier Nothing Phone (3a) CPU fixture results with visible limitations.
7. iQOO acceptance plan, optional camera boundaries and publication links.

Sources live in the relevant slide's speaker notes. The speech candidates are
distinct from the existing Whisper tiny.en and offline Android TTS preparation
evidence. No matched iQOO, NPU, live creator benefit or DJI connection is claimed.
The full speech/VLM/TTS loop remains unmeasured. The new minimal benchmark APK
awaits the creator's phone reconnection.

## Outputs

The editable PPTX and attachment PDF live under ignored `output/pitch/final/`.
The PDF contains rendered slide images, with clickable closing links where
available. Editable text, process diagrams, a native evidence table and citations
remain in the PPTX. Google Slides publication and conversion checks are separate.

`build-deck.mjs` uses the existing bundled Artifact Tool runtime and font Arial.
Copy it into `output/pitch/.build/`, link that directory's `node_modules` to the
bundled packages, and run with the bundled Node and the `RUNTIME_NODE`,
`RUNTIME_NODE_MODULES` and `RUNTIME_PYTHON` paths from workspace dependencies.
No new packages or fonts are needed. Set `MINIFILM_DECK_SUFFIX` to a new version
so final exports remain separate. `MINIFILM_WORKSPACE` optionally selects a checkout.

`links.json` holds the public prototype, walkthrough, YouTube and GitHub URLs.
Revision 6 includes all four public URLs supplied by the publishing agent. The
Google Slides conversion and external hosting acceptance remain separate checks.
`render-pdf.py VERSION` wraps the latest inspected slide
previews in a PDF. Generated crops, previews, validation reports and draft exports
stay ignored, separate from final outputs.

## Inspection record

The local seven-slide deck passed structural, layout, seven-slide, native-table,
font policy and first-party reimport checks with zero findings and zero warnings
in revisions 5 and 6. All slides were rendered and visually inspected. Earlier draft
issues with runtime settings, screenshot crops, connector direction and caption
spacing were repaired. The PDF's seven pages were rendered separately for review.
Revision 6 retains four exact hyperlinks in both the PPTX and PDF, verified from
their final package relationships and PDF link annotations. The changed closing
slide and its PDF page were individually inspected. No native PowerPoint or
Google Slides acceptance is claimed by these local checks.
