// Deterministic public-screen renderer. Frames stream to ffmpeg; no frame archive.
const { chromium }=require('/Users/sanju/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('fs'),path=require('path'),{spawn}=require('child_process'),{once}=require('events');
(async()=>{
  const out=path.resolve('output/demo'),scenes=JSON.parse(fs.readFileSync(path.join(out,'timing.json'),'utf8'));
  const duration=scenes.at(-1).start+scenes.at(-1).duration,fps=15;
  const browser=await chromium.launch({headless:true});
  const page=await browser.newPage({viewport:{width:1920,height:1080},deviceScaleFactor:1});
  await page.goto('file://'+path.join(__dirname,'render.html'));
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const encoder=spawn('/opt/homebrew/bin/ffmpeg',['-hide_banner','-loglevel','warning','-y','-f','image2pipe','-vcodec','mjpeg','-framerate',String(fps),'-i','pipe:0','-i',path.join(out,'narration.wav'),'-c:v','libx264','-preset','fast','-crf','19','-pix_fmt','yuv420p','-r','30','-c:a','aac','-b:a','160k','-ar','48000','-movflags','+faststart','-t',duration.toFixed(6),path.join(out,'mini-film-director-guided-demo.mp4')],{stdio:['pipe','ignore','pipe']});
  let logs='';encoder.stderr.on('data',b=>logs+=b.toString());
  for(let f=0;f<Math.ceil(duration*fps);f++){
    await page.evaluate(async({t,scenes})=>{window.renderFrame(t,scenes);await Promise.all([...document.images].map(i=>i.decode()));},{t:f/fps,scenes});
    const bytes=await page.screenshot({type:'jpeg',quality:88});
    if(!encoder.stdin.write(bytes))await once(encoder.stdin,'drain');
    if(f%300===0)console.log(`Rendered ${Math.round(f/fps)} / ${Math.round(duration)} seconds`);
  }
  encoder.stdin.end();const [code]=await once(encoder,'close');
  for(const i of [0,3,4,6,9,12,14]){const s=scenes[i];await page.evaluate(async({t,scenes})=>{window.renderFrame(t,scenes);await Promise.all([...document.images].map(i=>i.decode()));},{t:s.start+Math.min(2,s.duration/2),scenes});await page.screenshot({path:path.join(out,`proof-${s.id}.png`)});}
  // Seek backwards to prove pure frame evaluation independent of playback history.
  await page.evaluate(({scenes})=>window.renderFrame(scenes[4].start+1,scenes),{scenes});
  await page.screenshot({path:path.join(out,'proof-backwards-seek.png')});
  await browser.close();
  if(code!==0)throw Error(logs);
  fs.writeFileSync(path.join(out,'render-evidence.json'),JSON.stringify({duration_sec:duration,source_fps:fps,encoded_fps:30,size:[1920,1080],scenes:scenes.length,errors,encoder_warnings:logs,source:'Actual isolated public synthetic wireframe captures',narration:'Local macOS Samantha speech; no external service',frame_archive:false},null,2));
  console.log(JSON.stringify({duration_sec:duration,bytes:fs.statSync(path.join(out,'mini-film-director-guided-demo.mp4')).size,errors}));
})().catch(e=>{console.error(e);process.exit(1)});
