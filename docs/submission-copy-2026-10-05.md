# Mini Film Director — Phase 1 form copy

Prepared **5 October 2026 (IST)** for creator review. No form submission, account
change, upload or publication was performed by this writing task. Track for this
revision: **Open Innovation**, superseding the older draft's Productivity
recommendation. These paragraphs are form-ready; the evidence notes below are
internal context and should not be pasted into unrelated form fields.

## Idea name

Mini Film Director

## Idea description

Mini Film Director helps a solo creator make a reel without juggling a director,
camera operator and editor. The first workflow is a daily fashion reel: one
outfit, one location, five short shots. The creator gives an idea, reviews an
editable shot plan, then receives one clear posing or speaking cue at a time.

The proposed experience is Pose → Perform → Assemble. Pose establishes the
story, framing and sequence. Perform offers optional spoken direction, a
countdown, explicit session start and a reachable Stop. Assemble lets the creator
review takes, change cuts and captions, save a project and export a vertical
reel. A reviewed reference can inspire timing, angles and movement while keeping
the creator's own objective and footage.

Android is the primary target. Preparation has separately demonstrated local
Qwen CPU planning, pose inference, English transcription and subtitle/video
assembly on a Nothing Phone (3a), using controlled test inputs. A simple
interactive wireframe makes the flow reviewable. Useful live creator direction,
the full attended shoot and matched iQOO performance remain validation goals.
Action 4 coordination and drone footage are optional later additions; autonomous
aircraft control is not part of the first demo.

All existing implementations are disclosed pre-event research. Competition code
will be created in the permitted event window; any preparation-code reuse needs
organizer approval.

## Prior builds and hackathons

My recent projects span creator tools, embodied AI, mobile games and agent
evaluation. Make My Reels turns a natural-language brief into a captioned video
using indexed footage, agent planning and FFmpeg. Tiny Toybox / Fire Boy began
as a Build Small hackathon project, combining a virtual character, physics and
an inspectable vision-language-action research route. HighFuzz Gym was submitted
and published for the Micro1 Frontier Engineering Challenge; it pairs bounded
AI security hypotheses with fuzzing and executable verification.

I have also built Bearfish, a Godot fishing game with Android development builds
and a signed iPhone installation, and explored compact speech models and
fixed-weight routing/RTL for local AI hardware. My published demo history
includes OpenAI Eng Day Bangalore, OpenEnv/Meta at Scaler, Build Small, Micro1,
and Gemini 3 hackathon project work. For Mini Film Director, I have run local
LLM components on Android and prepared a separate reproducible CPU timing app.

## What makes the team stand out

As a solo builder, I connect product design, model/runtime work and practical
mobile delivery. My projects range from video editing and virtual characters to
on-device inference and agent evaluation. That breadth helps me reduce an
ambitious idea to a clear interaction and a testable implementation.

For this project, I am focusing on one useful fashion shoot rather than making
every camera integration a prerequisite. I keep model-generated plans editable,
separate slower planning from immediate shot timing, and retain visible Start,
Stop and review controls. My preparation records timings, failures, preserved
originals and processing backends. I will bring that same discipline to the
event: fresh competition code, a phone-first demo and explicit evidence for what
actually works on iQOO.

## Confirmed form selections and link slots

These selections were supplied by the parent from the user's form context;
they are not proficiency ratings inferred from project titles.

- LLM proficiency: **Deployed local LLMs on-device**.
- Android proficiency: **Intermediate**.
- Track: **Open Innovation**.
- Deck: parent supplies the reviewed upload/link.
- Demo video: parent supplies the reviewed current film-director video/link.
- Interactive prototype: parent supplies the accessible hosted link. The local
  `127.0.0.1` wireframe URL is not accessible to judges on another computer.

## Evidence and copy boundaries

| Fact used | Source inspected | Boundary |
| --- | --- | --- |
| Phone-first fashion workflow and independent local Android component results | [Current status](status.md), [existing submission draft](submission-draft.md), [local AI evidence](local-ai-evidence.md), [design brief](../design/README.md) | Controlled preparation results do not establish a complete live creator shoot, NPU or iQOO result. |
| New separate Qwen benchmark | [Benchmark README](../prototype/model-bench/README.md), [static checks](../prototype/model-bench/verification.json), [replacement status](status.md) | Build passed; the phone disconnected before replacement/measurement. Do not call the new benchmark installed or measured. |
| HighFuzz submitted and Published | [Local submission evidence](/Users/sanju/Desktop/coding/hackathons/micro1-ai-fuzzing-software-hackathon/docs/SUBMISSION_LINKS.md), last verified31 August2026; [public project](https://github.com/sanjuhs/highfuzz-gym-public) | This is one documented submission, not a win. Narrow source-informed synthetic replay and one historical repair task do not prove general live-model superiority. |
| Fire Boy creator and Build Small project | [Project README](/Users/sanju/Desktop/coding/hackathons/tiny-toybox-fireboy-minicpm/README.md), [public project](https://github.com/sanjuhs/tiny-toybox-fireboy-minicpm), [demo](https://youtu.be/rwRIXLwaLmI) | README identifies Sanjay Prasad H S as creator and links a frozen hackathon snapshot. Current toy's needs-driven lane is separate from its model research lane. No award/placing claimed. |
| Make My Reels implementation and recorded reel result | [Project README](/Users/sanju/Desktop/coding/nextjs-stuff/make-my-reels/README.md) | README attributes the project to Sanjay and records a 30-second1080×1920 reel from an actual prompt-to-reel smoke test. Local/cloud processing is not on-phone local inference. No private footage details copied. |
| Bearfish mobile build/install work | [Game README](/Users/sanju/Desktop/coding/Aigen-code-video-and-more/bear-fish/README.md), [latest signed installation record](/Users/sanju/Desktop/coding/Aigen-code-video-and-more/bear-fish/docs/checkpoints/iphone-latest-install/README.md); Codex thread “Install latest Bearfish game” | 5 October record verifies current-source signed iPhone install, foreground launch and preserved save bytes. Human gameplay/frame rate remain separate. Do not call this a public-store release. |
| Compact speech/fixed-weight routing research | [Routing project README](/Users/sanju/Desktop/coding/llm-hardware/route-voice-llm-hardware-v1/routing-voice-chip-design/README.md), [fact sheet](</Users/sanju/Desktop/coding/llm-hardware/route-voice-llm-hardware-v1/Media Kit/Version 2/press/FACT-SHEET.md>); Codex thread “Design FPGA routing voice chip” | Native/synthesis research, not fabricated/taped-out silicon, measured sub-watt transcription or a complete proven ASIC. |
| Additional published hackathon demos | Parent directly inspected the creator's YouTube Studio on5 October2026: “Openai hackathon demo - open ai Eng day Bangalore2026 -Jan31”, “Openenv-meta-hackathon at Scaler school of Technology April26 2026” ([video](https://youtu.be/9IOhr8Np_fk)), “build small hackathon Hugging Face”, “afl plus micro1 hackathon”, and “Utho alarmclock cerebral valley - gemini3 hackathon” | Titles support published project demos. They do not independently establish attendance, official acceptance, team role, placing or a win. Wording deliberately says demo/project work. |

No hackathon wins were verified in the inspected material. Do not infer “no
wins” as a personal declaration; simply omit award claims. The older Anthropic
video title explicitly says “devpost late attempt”, so it is not presented as an
accepted submission. The Hermes Buildathon folder inspected is a **planning
workspace**; it is not evidence of attendance or submission.

FocusPilot is an earlier archived research project, distinct from Mini Film
Director. [Archive notes](archive-and-recovery.md) preserve that provenance, and
its full historical backup remains missing at its recorded path. Do not reuse
its results as verification of the new product. Its private contents were not
inspected for this copy.

Jhana-related Codex summaries show legal-AI engineering/audit work, but this
review did not verify the user's employment/title or permission to disclose
client-specific outcomes. It is omitted from the form paragraphs rather than
publishing private client information or implying sole authorship of a company.

The [existing six-slide deck](phase1-deck.md) remains a dated preparation
artifact. Its older Android component evidence is historical; the current
benchmark package has no speech/VLM path. Kokoro and Moonshine remain
[candidate research](../design/speech-models.md), not demonstrated integrations.
No performance multiplier, phone rating, continuous real-time VLM loop, working
Action 4/Neo2 control or final event eligibility is claimed by this copy.
