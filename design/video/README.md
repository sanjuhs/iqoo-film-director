# Guided Demo Idea: Mini Film Director

5 October 2026. Public synthetic design walkthrough, created for the user's
personal YouTube channel and idea submission. It is pre-event preparation,
not an eligible event-written implementation or proof of a complete on-phone
speech/vision workflow.

The approximately 103-second landscape video uses actual captures of the
scripted phone wireframe. Narration is rendered locally using macOS Samantha;
there is no external speech service, private footage, or captured phone screen.
The source screenshots are public illustrations, captured in an isolated browser
with virtual time and voice off. The simulated five-take sequence is condensed
into its important states; its framing and playback do not measure latency.

## What the viewer sees

| Beat | What the viewer learns | Visible action and resulting state |
| --- | --- | --- |
| Idea | The creator sets the objective | Camera objective opens the actual brief; the jacket brief is edited |
| Reference | Proposed observations need review | Five invented shots are reviewed; timing, angles and posing are selected |
| Apply | Replacing a plan is explicit | Actual confirmation sheet preserves the brief and existing takes |
| Plan | Applying is separate from capture | New editable plan appears, with 3 + 3 + 5 + 4 + 5 seconds |
| Direct | The next cue is easy to follow | Actual cue screen changes to 3, 2, 1, Action and Cut |
| Edit | Captured drafts need creator review | The simulated sequence produces five takes in the actual editor |
| Finish | Words and choices remain editable | Actual editable caption screen is shown with sample-content disclosure |
| Save | A film is a reusable project | Explicit browser save creates a named synthetic project |
| Projects | Saved work can be reopened or backed up | The actual library appears and its JSON download is exercised |
| Local vision | Intended local execution needs separate proof | Separate Android Qwen CPU evidence is distinguished from pending full loop |

Headings and progressive point reveals explain each screen. The countdown swaps
its real captured states; Shoot/Edit/Projects highlights show the current
destination. Every frame comes from a pure time function, including backwards
seeking. No AI-generated app screens substitute for the actual wireframe.

## Reproduction

Start the existing static design server on `127.0.0.1:4173`. Use the installed
Node, Playwright/Chromium, macOS `say`, and `/opt/homebrew/bin/ffmpeg`; these scripts
do not download dependencies. Paths reflect this preparation workstation.

1. Run `node design/video/capture.cjs` to capture only public synthetic screens.
2. Run `python3 design/video/prepare-audio.py` for the narration and measured timing.
3. Run `node design/video/render.cjs` to stream deterministic JPEG frames directly
   into H.264/AAC. No frame archive is retained.

The upload-ready result is the ignored
`output/demo/mini-film-director-guided-demo.mp4`. The public
[storyboard](storyboard.json) contains the exact narration and explanatory beats;
[captions](captions.srt) follow the measured speech scene timing. Narration is also
visible inside the video. [Verification](verification.json) records the final
file's hash, dimensions, codecs, duration, audio presence and reviewed frames.

The website uses optional browser speech and illustrated takes. The video does
not claim deployed Moonshine/Kokoro, fresh VLM analysis, Instagram access,
Bluetooth/drone control, NPU execution, iQOO performance or a web video export.
The generated MP4 is the walkthrough video itself, not an exported film from
the prototype. Future camera/speech/local-loop tests remain separate.

Publication and accepted dashboard submission are handled separately by the
parent task; file creation alone establishes neither.
