"""Render public narration locally with macOS speech; no network/provider access."""
import json, pathlib, subprocess
root=pathlib.Path(__file__).resolve().parent
out=root.parents[1]/'output'/'demo'
out.mkdir(parents=True,exist_ok=True)
scenes=json.loads((root/'storyboard.json').read_text())
start=0
for i,s in enumerate(scenes):
    text=out/f'{i:02d}.txt';aiff=out/f'{i:02d}.aiff';wav=out/f'{i:02d}.wav'
    text.write_text(s['narration'])
    subprocess.run(['/usr/bin/say','-v','Samantha','-r','210','-f',str(text),'-o',str(aiff)],check=True)
    speech=float(subprocess.check_output(['/opt/homebrew/bin/ffprobe','-v','error','-show_entries','format=duration','-of','default=noprint_wrappers=1:nokey=1',str(aiff)]))
    duration=max(4.2,speech+.65)
    subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(aiff),'-af','apad','-t',str(duration),'-ar','48000','-ac','1',str(wav)],check=True)
    s.update(start=round(start,6),duration=round(duration,6),speech_duration=round(speech,6));start+=duration
(out/'timing.json').write_text(json.dumps(scenes,indent=2))
(out/'audio-list.txt').write_text(''.join(f"file '{out}/{i:02d}.wav'\n" for i in range(len(scenes))))
subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(out/'audio-list.txt'),'-c:a','pcm_s16le',str(out/'narration.wav')],check=True)
print(json.dumps({'duration_sec':round(start,3),'voice':'Samantha','rate_wpm':210,'scenes':len(scenes)}))
