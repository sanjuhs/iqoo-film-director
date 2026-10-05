"""Wrap inspected slide PNGs in an attachment PDF; PPTX remains the editable deck."""
from pathlib import Path
import json
import sys
from reportlab.pdfgen import canvas

root = Path(__file__).resolve().parents[2]
out = root / 'output/pitch'
suffix = sys.argv[1] if len(sys.argv) > 1 else 'v5'
target = out / 'final' / f'Mini-Film-Director-idea-deck-{suffix}.pdf'
links = json.loads((root / 'design/pitch/links.json').read_text())
pdf = canvas.Canvas(str(target), pagesize=(960, 540), pageCompression=1)
pdf.setTitle('Mini Film Director: iQOO Open Innovation idea')
pdf.setAuthor('Mini Film Director')
for i in range(1, 8):
    pdf.drawImage(str(out / 'previews' / f'slide-{i}.png'), 0, 0, 960, 540)
    if i == 7:
        for j, key in enumerate(['prototype', 'walkthrough', 'demo', 'repository']):
            if links.get(key):
                x, y, w, h = 816, 251 + j * 76, 396, 43
                pdf.linkURL(links[key], (x*.75, (720-y-h)*.75, (x+w)*.75, (720-y)*.75), relative=0)
    pdf.showPage()
pdf.save()
print(target)
