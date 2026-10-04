"""Build the dated pre-event idea document. Uses existing bundled reportlab only.

The finished PDF is an idea artifact, not an eligible event build or a receipt.
Measured statements must be reconciled with docs/status.md before each release.
"""
from pathlib import Path
from html import escape
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'output/pdf/mini-film-phase-one.pdf'
W, H = 960, 540
BG, CARD = HexColor('#101113'), HexColor('#1D1F22')
FG, MUTED, LIME = HexColor('#F5F5EF'), HexColor('#B8BBB4'), HexColor('#D9FF70')
FONT_ROOT = Path('/System/Library/Fonts/Supplemental')
pdfmetrics.registerFont(TTFont('Film', str(FONT_ROOT / 'Arial.ttf')))
pdfmetrics.registerFont(TTFont('FilmBold', str(FONT_ROOT / 'Arial Bold.ttf')))
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
c = canvas.Canvas(str(OUTPUT), pagesize=(W, H), pageCompression=1)
c.setTitle('Mini Film Director - Phase 1 idea / pre-event research')
c.setAuthor('Mini Film Director research')


def paragraph(text, x, top, width, size=16, color=FG, bold=False, max_height=None):
    style = ParagraphStyle('film', fontName='FilmBold' if bold else 'Film',
                           fontSize=size, leading=size * 1.35, textColor=color)
    block = Paragraph(escape(text).replace('\n', '<br/>'), style)
    _, height = block.wrap(width, 1000)
    if max_height is not None and height > max_height:
        raise ValueError(f'Overflow: {height} > {max_height}: {text[:50]}')
    if top + height > 490:
        raise ValueError(f'Footer overlap: {text[:50]}')
    block.drawOn(c, x, H - top - height)
    return height


def line(text, x, top, size=12, color=LIME, bold=False):
    c.setFillColor(color)
    c.setFont('FilmBold' if bold else 'Film', size)
    c.drawString(x, H - top - size, text)


def page(number, eyebrow, title):
    c.setFillColor(BG); c.rect(0, 0, W, H, fill=1, stroke=0)
    line('mini film / your pocket director', 46, 23, 14, bold=True)
    line(eyebrow, 46, 73, 11, MUTED, True)
    paragraph(title, 46, 103, 865, 36, FG, True, 105)
    line('PHASE 1 IDEA | PRE-EVENT RESEARCH | 4 OCT 2026', 46, 503, 10, MUTED)
    line(f'{number:02d} / 06', 865, 503, 10, MUTED)


def card(x, top, width, height):
    c.setFillColor(CARD)
    c.roundRect(x, H - top - height, width, height, 14, fill=1, stroke=0)


page(1, 'PRODUCTIVITY / SOLO CREATOR', 'A director for the person\nin front of the camera.')
paragraph('From an idea to a reel you can still change.', 46, 238, 780, 25, LIME, True)
paragraph('Mini Film is designed to help a solo creator plan shots, hear short performance cues, '
          'then assemble reviewed takes locally with subtitles and typography.',
          46, 298, 770, 20, max_height=90)
paragraph('First workflow: daily solo fashion reels.\nNext: talking stories, product reveals and introductions.',
          46, 397, 810, 17, MUTED, max_height=65)
c.showPage()

page(2, 'THE CREATOR PROBLEM', 'One phone. Too many jobs.')
for x, index, title, body in [
    (46, '01', 'Find the shot', 'Frame, pose, perform and check the footage while remembering the story.'),
    (341, '02', 'Keep the flow', 'Move through clear scenes without constantly switching between filming and editing.'),
    (636, '03', 'Finish the reel', 'Choose usable moments, trim each take, add words and export. Keep creative decisions editable.')]:
    card(x, 205, 278, 200)
    line(index, x + 22, 221, 20, LIME, True)
    paragraph(title, x + 22, 260, 234, 21, FG, True)
    paragraph(body, x + 22, 300, 232, 16, MUTED, max_height=100)
paragraph('Hypothesis: spoken scene-by-scene direction reduces friction for daily creators.\n'
          'Creator benefit and time savings still need an attended comparison.', 46, 431, 865, 14, MUTED, max_height=40)
c.showPage()

page(3, 'THE THREE-PHASE EXPERIENCE', 'Pose. Perform. Assemble.')
for x, title, body in [
    (46, '01 POSE', 'Type or record an opt-in brief, then review the words and five editable shots. '
     'Inspect selected reference moments. Start the camera explicitly and check framing.'),
    (341, '02 PERFORM', 'Hear one direction through Android audio routing. A pose break and countdown '
     'lead into a quiet take. The optional sequence moves to the next shot; Stop stays visible.'),
    (636, '03 ASSEMBLE', 'Choose and reorder takes. Edit cuts and subtitle drafts. Add typography and '
     'a color look. Save a vertical MP4 plus a portable ZIP of original clips and reviewed edits.')]:
    card(x, 201, 278, 229)
    line(title, x + 22, 218, 16, LIME, True)
    paragraph(body, x + 22, 260, 232, 16, max_height=160)
paragraph('This is the intended live experience. Microphone/camera capture, audible AirPods direction '
          'and the full sequence still need attended acceptance.', 46, 442, 865, 13, MUTED, max_height=36)
c.showPage()

page(4, 'WHAT IS LOCAL', 'AI where the footage lives.')
for top, label, title, body in [
    (197, 'PLAN + FRAME', 'Qwen3.5 0.8B + local vision projector', 'CPU shot drafts and selected-frame notes. Review guesses before planning.'),
    (259, 'FRAMING', 'Bundled ML Kit pose model', 'Person landmarks plus conservative rules. Live advice still needs evaluation.'),
    (321, 'WORDS', 'Offline TTS + whisper.cpp tiny.en', 'English cue synthesis, speech drafts and reviewed speech-edge cuts.'),
    (383, 'ASSEMBLY', 'CameraX + Media3 Transformer', 'Explicit capture design; real local trims, text, MP4 and portable edit ZIP.')]:
    line(label, 46, top, 11, LIME, True)
    paragraph(title, 225, top - 2, 680, 18, FG, True, 25)
    paragraph(body, 225, top + 25, 680, 14, MUTED, max_height=38)
paragraph('Installed app: no Internet permission or provider keys. Separate models stay on the phone.\n'
          'Nothing Phone (3a) CPU results do not establish iQOO, NPU or Office Kit execution.',
          46, 448, 865, 12, MUTED, max_height=35)
c.showPage()

page(5, 'REPRODUCIBLE PHONE EVIDENCE', 'Measured, with originals preserved.')
for x, metric, title, body in [
    (46, '25-53 s', 'Five-shot AI drafts', 'Recent local CPU fixture plans. Review every creative choice.'),
    (341, '11.80 s', 'Prepared sample reel', 'Synthetic 720 x 1280 H.264/AAC. Four unchanged originals in the ZIP.'),
    (636, '1 job', 'Real interrupted export', 'Stopped during actual encoding. Fresh-process recovery passed in 0.242 s.')]:
    card(x, 199, 278, 174)
    line(metric, x + 22, 213, 33, LIME, True)
    paragraph(title, x + 22, 263, 234, 18, FG, True)
    paragraph(body, x + 22, 299, 234, 14, MUTED, max_height=65)
paragraph('Real AAC encoding and local English transcription passed without opening a microphone. '
          'Export checks preserved source hashes, an unrelated file and the earlier completed reel.',
          46, 389, 865, 15, max_height=45)
paragraph('Inputs were labelled synthetic media and an attributed public image. Model errors and '
          'abstentions are retained; these checks do not establish real creator accuracy or AirPods playback.',
          46, 440, 865, 12, MUTED, max_height=35)
c.showPage()

page(6, 'DELIVERY / COMPETITION DISCLOSURE', 'A research base. Clear next gates.')
card(46, 193, 421, 212); card(488, 193, 426, 212)
line('LIVE DEMO GATES', 68, 210, 12, LIME, True)
paragraph('Validate actual phone capture, audible AirPods cues and quiet recorded speech. '
          'Then repeat on the required iQOO and demonstrate supported Office Kit transfer. '
          'NPU execution needs its own evidence.', 68, 244, 372, 16, max_height=132)
line('IDEA DEADLINE / PROVENANCE', 510, 210, 12, LIME, True)
paragraph('Dashboard: 5 October 2026. Countdown supports about 23:59 IST; timezone is inferred. '
          'Finale: 9-11 October. Competition code must be event-written unless reuse is approved. '
          'This dated prototype is pre-event research; no submission receipt is claimed.',
          510, 244, 378, 15, max_height=145)
paragraph('Drone control is secondary and unconnected. Creative quality and creator benefit remain '
          'unverified; every direction, cut and caption stays editable.', 46, 420, 865, 14, MUTED, max_height=39)
line('Sources: official iQOO guide/dashboard; Android/ML Kit docs; ggml-org runtimes. Evidence: docs/status.md.',
     46, 477, 10, MUTED)
c.linkURL('https://iqoo.reskilll.com/guide', (46, 49, 300, 65), relative=0)
c.showPage(); c.save()
print(f'Built {OUTPUT.name}: {OUTPUT.stat().st_size} bytes')
