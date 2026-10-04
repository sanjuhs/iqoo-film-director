# Using Mini Film — camera-first repair

Updated 5 October 2026. The new research build is installed on the authorized
Nothing Phone (3a). Camera and microphone permissions were already granted by
the creator; no debugging permission grant was used. Your saved plan, clips and
edits are preserved. The app opens on Camera with preview off. **Talk during takes** now enables
live spoken Action/shot/framing/Cut cues; leave it off for quiet dialogue.

[See the current screen](../output/demo/MiniFilm-camera-first-screen.png).
This is the real camera-disabled emulator screen, with no private footage.
The older one-minute walkthrough shows the previous three-tab interface.

1. On **Camera**, type your idea beside **Generate AI**, then tap it. If you have
   existing takes, confirm **Replace plan**; those takes stay in Reel. Qwen runs
   on the phone CPU with visible loading stages. The measured request took about
   43 seconds. Review the five directions; generation turns guided shooting on.
2. Tap **Start camera**. Only after the preview streams does it become
   **Record · 3 sec**. Tap Record when you are ready to film. Guidance gives you
   time to pose and then counts down. With Talk during takes on, it says Action
   and can speak fresh framing advice while recording; it says Cut after saving.
   The next guided shot waits for that cue.
3. Enable **Spoken direction / earbuds** and pair AirPods in Android settings.
   Tap the visible **Test voice** first and check media volume. **Shoot options**
   contains full-sequence guidance, **Hear the direction**, lens selection, planned
   stopping and the detailed shot/reference editor. Confirm you hear the cue in
   the earbuds; actual AirPods playback/microphone use is still unverified.
4. **Stop take** ends a recording/preparation. When previewing, **Stop camera**
   turns the camera off. **Reel** has your takes: preview, select, trim and review
   caption/subtitle words, then **Export my reel**. MP4 export stays local;
   originals are kept. Save clips + edits when you want a portable edit package.

For clean dialogue, turn **Talk during takes** off. Speaker cues can be captured
by the phone microphone; headset microphone recording is not established.
Turning Talk during takes off during preparation cancels that pending start;
tap Record again when ready. Re-enabling it during a take waits for the next take.

For free talking, turn **Guide the full shot sequence** off and, in Shoot options,
turn planned stopping off. Stop the recording yourself; the 60-second hard limit
still applies. The main prompt makes a shot plan; continuous conversational
camera-chat direction is not implemented.

Qwen3.5 0.8B is a pretrained CPU language model, not a custom-trained Kev model.
Pose detection uses bundled MLKit with framing rules; English subtitle drafts
use Whisper tiny.en. Color looks are deterministic presets/neutral balance.
Model drafts still need creative/word/timing review; these checks do not prove
useful coaching on a real creator shoot.

The repaired real phone preview/stop/background check passed, as did one real
camera-page CPU AI request and updated interface/synthetic export checks. The
actual Test voice button passed on the phone: real offline TTS start/done and
Main completion message, about0.2s start delay, unchanged media volume and
creator data. [Live direction evidence](live-direction-evidence.json) distinguishes
that playback proof from human hearing and simultaneous camera recording.
Your short recorded take, sound, earbuds and final creator reel remain attended
acceptance gates. iQOO/NPU/Office Kit and optional aircraft work remain open.

This is dated pre-event research, not eligible event-created competition source.
The last-confirmed public Phase 1 deadline is **5 October 2026, 11:59:59 PM IST**;
[exact evidence](phase1-deadline-evidence.json). The prepared idea deck remains
unsubmitted; organizer reuse approval and personal declarations are separate.
