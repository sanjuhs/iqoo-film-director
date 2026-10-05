import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { Presentation, PresentationFile, FileBlob } from '@oai/artifact-tool';
import sharp from 'sharp';

const root = process.env.MINIFILM_WORKSPACE || '/Users/sanju/Desktop/coding/hackathons/iqoo-hackathon';
const skill = '/Users/sanju/.codex/plugins/cache/openai-primary-runtime/presentations/26.915.20218/skills/presentations';
const out = path.join(root,'output/pitch');
const build = path.join(out,'.build');
const links = JSON.parse(await fs.readFile(path.join(root,'design/pitch/links.json'),'utf8'));
const {resolvePresentationFont, finalizePresentation} = await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const font = resolvePresentationFont({fontFamily:'Arial'});
const C = {navy:'#152C43', coral:'#F26543', grey:'#566573', light:'#DDE2E7', white:'#FFFFFF'};
const deck = Presentation.create({slideSize:{width:1280,height:720}});
await fs.mkdir(build,{recursive:true});
await fs.mkdir(path.join(out,'previews'),{recursive:true});
await fs.mkdir(path.join(out,'final'),{recursive:true});

function txt(s,text,x,y,w,h,size=27,bold=false,color=C.navy){
 const a=s.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
 a.text=text;
 a.text.style={typeface:font,fontSize:size,bold,color,autoFit:'none',insets:{left:0,right:0,top:0,bottom:0},verticalAlignment:'top'};
 return a;
}
function base(title){
 const s=deck.slides.add();s.background.fill=C.white;
 txt(s,title,64,48,1152,86,42,true);
 txt(s,'Pre-event research and design prototype',64,678,960,22,15,false,C.grey);
 txt(s,String(deck.slides.items.length).padStart(2,'0'),1170,678,46,22,15,false,C.grey);
 return s;
}
function note(s,text){s.speakerNotes.textFrame.setText(text);}
async function img(s,name,x,y,w,h,crop){
 let bytes=await fs.readFile(path.join(root,'design/assets',name));
 if(crop){const meta=await sharp(bytes).metadata();const left=Math.round(meta.width*crop.left),top=Math.round(meta.height*crop.top),right=Math.round(meta.width*(1-crop.right)),bottom=Math.round(meta.height*(1-crop.bottom));bytes=await sharp(bytes).extract({left,top,width:right-left,height:bottom-top}).png().toBuffer();await fs.writeFile(path.join(build,name.replace('.png','-crop.png')),bytes);}
 s.images.add({blob:new Uint8Array(bytes),contentType:'image/png',alt:'Actual design prototype screenshot: '+name,fit:'contain',position:{left:x,top:y,width:w,height:h}});
}
function step(s,label,body,x,y,w=196,h=88){
 const a=s.shapes.add({geometry:'rect',position:{left:x,top:y,width:w,height:h},fill:C.white,line:{fill:C.light,width:1}});
 a.text=label;a.text.style={typeface:font,fontSize:26,bold:true,color:C.navy,verticalAlignment:'middle',alignment:'center',insets:{left:10,right:10,top:8,bottom:8}};
 if(body)txt(s,body,x,y+h+18,w,86,22,false,C.grey);
 return a;
}
function join(s,a,b,from='right',to='left',dashed=false){s.shapes.connect(a,b,{kind:'straight',fromSide:from,toSide:to,line:{fill:C.coral,width:2,style:dashed?'dashed':'solid'},tail:{type:'arrow',width:'med',length:'med'}});}
const phoneCrop={left:852/1440,top:158/1067,right:(1440-1214)/1440,bottom:(1067-947)/1067};

// 1. The cover uses an actual prototype screen, with editable title copy.
{
 const s=base('');
 txt(s,'Mini Film\nDirector',64,151,760,182,70,true);
 txt(s,'A local director for solo video creators',68,377,690,88,34,false);
 txt(s,'iQOO Open Innovation\nPhase 1 idea submission',68,527,650,78,24,false,C.grey);
 await img(s,'wireframe-shoot-mobile-v5.png',880,84,274,561,{left:14/390,top:59/869,right:14/390,bottom:66/869});
 note(s,'The screenshot is the actual HTML/CSS/JavaScript design prototype. The preview is an illustrated sample. No camera, microphone or local model runs in this website. Intended product: local Android solo-filming direction. Source: design/README.md, docs/submission-draft.md.');
}
// 2. Problem statement uses flat editable text rather than interface panels.
{
 const s=base('Solo filming combines four jobs');
 const jobs=[['01','Set the camera','Choose a useful angle and framing.'],['02','Perform','Pose or speak while keeping the shot in mind.'],['03','Direct the story','Remember the hook, details and missing shots.'],['04','Edit the reel','Review takes, trim clips and correct captions.']];
 jobs.forEach((r,i)=>{const y=185+i*105;txt(s,r[0],64,y,68,44,29,true,C.coral);txt(s,r[1],155,y,460,44,29,true);txt(s,r[2],155,y+43,540,45,23,false,C.grey);});
 txt(s,'First workflow',823,191,373,50,29,true);
 txt(s,'A daily fashion reel\nOne outfit\nOne phone\nFive short takes',823,261,360,220,30,false);
 txt(s,'Hypothesis: concise direction and reviewed coverage can reduce avoidable retakes.',823,524,370,111,24,false,C.grey);
 note(s,'Problem and intended benefit are product hypotheses, not findings from a creator study. Daily solo fashion is the first workflow. Walking/talking, product reveals and introductions follow. Source: docs/submission-draft.md, docs/objectives.md.');
}
// 3. Screenshot sequence preserves the actual design while all labels remain native.
{
 const s=base('One jacket, five guided takes');
 const shots=[['Your shot plan','Editable instructions','reference-demo-plan-v6.png'],['Guided capture','Cue, countdown and Cut','reference-demo-countdown-v6.png'],['Your reel','Review and save','reference-demo-edit-v6.png']];
 for(let i=0;i<shots.length;i++){const x=87+i*391;txt(s,shots[i][0],x,156,330,42,29,true);await img(s,shots[i][2],x+49,209,234,423,phoneCrop);txt(s,shots[i][1],x,642,330,30,22,false,C.grey);}
 note(s,'All three images are actual design screenshots from design/assets/reference-demo-*-v6.png. The walkthrough uses an invented jacket reel and scripted timers. It does not analyze Instagram content or record real footage. Applying a reference plan preserves objective and opening line, asks for replacement confirmation, then opens an editable plan. Source: design/reference-demo.html, design/verification-demo.json.');
}
// 4. Explicitly requested workflow diagram stays editable.
{
 const s=base('Three tabs keep the core workflow simple');
 const names=['Shoot','Edit','Projects'];
 const bodies=['One cue\nVoice on or off\nStart and Stop','Review takes\nTrim and correct captions\nSave project','Open previous films\nDownload saved project\nContinue editing'];
 const nodes=names.map((n,i)=>step(s,n,bodies[i],92+i*402,235,292,106));join(s,nodes[0],nodes[1]);join(s,nodes[1],nodes[2]);
 txt(s,'Before the shoot',92,476,292,43,25,true);
 txt(s,'Edit idea or adapt a reference\nReview the shot plan',92,522,330,85,23,false,C.grey);
 txt(s,'During the shoot',494,476,292,43,25,true);
 txt(s,'One instruction at a time\nStop cancels future cues',494,522,330,85,23,false,C.grey);
 txt(s,'Extra cameras',896,476,292,43,25,true);
 txt(s,'Optional settings\nPhone alone remains useful',896,522,292,85,23,false,C.grey);
 note(s,'Native editable process diagram. These are the prototype’s three primary tabs, not claims of completed on-device capture or video export. Current design saves portable project JSON. A future Android implementation exports reviewed local media. Sources: design/user-flow.md, design/README.md.');
}
// 5. Architecture separates the proposed stack from measured research components.
{
 const s=base('Local speech and vision architecture');
 txt(s,'Proposed spoken interaction',64,154,1152,40,26,true,C.coral);
 const n=[step(s,'Moonshine Tiny','Speech to text\nCandidate',72,230,252,95),step(s,'Qwen3.5 0.8B',null,514,230,252,95),step(s,'Kokoro 82M','Cue to speech\nCandidate',956,230,252,95)];
 join(s,n[0],n[1]);join(s,n[1],n[2]);
 const vision=step(s,'Sparse frame\nor pose input',null,481,451,318,91);join(s,vision,n[1],'top','bottom');
 txt(s,'Prepared plan runs the countdown and Cut sequence.',64,594,1152,37,26,true);
 txt(s,'Complete STT, VLM and TTS latency remains unmeasured. CPU execution does not establish NPU use.',64,635,1152,31,21,false,C.grey);
 note(s,'Qwen3.5 0.8B is a multimodal official checkpoint: https://huggingface.co/Qwen/Qwen3.5-0.8B. Image execution requires the matching projector and runtime. Kokoro official model card: https://huggingface.co/hexgrad/Kokoro-82M. Moonshine Streaming Tiny: https://huggingface.co/moonshine-ai/moonshine-streaming-tiny. Its published ~34M count differs from the Hub tensor count; exact artifact verification is required. Moonshine and Kokoro remain proposed, not running in this Android app. Existing preparation speech uses Whisper tiny.en and Android offline TTS. Sources: design/speech-models.md, docs/local-director-benchmark.md.');
}
// 6. Real preparation observations, with boundaries visible on the slide.
{
 const s=base('Preparation evidence on Nothing Phone (3a)');
 const values=[['Workload','Observed time','What it establishes'],['Qwen five-shot plan','25–36 s','Earlier CPU fixture generation'],['Qwen single frame','9–12 s','Sparse image inference on CPU'],['English transcription','4.516 s','One synthetic 8.759 s clip'],['Offline cue synthesis','0.511 s','Audio bytes, no playback test']];
 const table=s.tables.add({rows:5,columns:3,left:64,top:194,width:1152,height:350,columnWidths:[390,223,539],values});
 table.borders.assign({style:'solid',fill:C.light,width:1});
 table.cells.block({row:0,column:0,rowCount:5,columnCount:3}).assign({fill:C.white,textStyle:{typeface:font,fontSize:24,color:C.navy},margins:{left:18,right:18,top:12,bottom:12},anchor:'center'});
 for(let c=0;c<3;c++){table.getCell(0,c).fill=C.navy;table.getCell(0,c).text.style={typeface:font,fontSize:24,bold:true,color:C.white};}
 txt(s,'An attended creator shoot remains a separate validation gate.',64,576,1152,42,26,true);
 txt(s,'Fresh short-cue benchmark prepared. Phone installation and matched iQOO tests are pending.',64,628,1152,38,22,false,C.grey);
 note(s,'Exact prior Qwen plan times: 24.512/27.503/35.616 seconds at 610/673/675 prompt tokens, 4 CPU threads, output cap 580 tokens. Frame times 10.495/9.308/11.869 seconds. Whisper tiny.en selected synthetic clip: 4.516 seconds. Offline Android TTS cue waveform synthesis: 0.511 seconds. The original full prototype has real local execution, but the new minimal benchmark package remains uninstalled because the user chose to reconnect later. No fresh measurement, earbud acceptance, camera concurrency or general accuracy follows. Sources: docs/local-director-benchmark.md, docs/status.md, docs/local-ai-evidence.md.');
}
// 7. Event plan, provenance and live proof links.
{
 const s=base('The event build proves one complete phone workflow');
 txt(s,'Acceptance on iQOO',64,174,624,52,30,true);
 txt(s,'Record five real takes with audible cues.\nReview captions and cuts. Export a playable reel.\nMeasure response time, failures and recovery.',64,245,656,154,28,false);
 txt(s,'Optional camera research',64,453,656,47,28,true);
 txt(s,'Action 4 and DJI remain secondary.\nNo working connection or flight claim.',64,510,656,78,25,false,C.grey);
 txt(s,'Explore the design',816,174,380,50,30,true);
 const items=[['Prototype','prototype'],['Guided walkthrough','walkthrough'],['Video demo','demo'],['GitHub repository','repository']];
 items.forEach(([label,key],i)=>{const a=txt(s,label,816,251+i*76,396,43,26,false,links[key]?C.coral:C.grey);if(links[key])a.text.get(label).link={uri:links[key],isExternal:true};else txt(s,'Link added after publication',816,290+i*76,396,24,16,false,C.grey);});
 txt(s,'Competition source will be created during the allowed event window. Preparation remains disclosed.',64,626,1152,37,22,true);
 note(s,'Current iQOO India flagship comparison: iQOO 15 with Snapdragon 8 Elite Gen 5, https://www.iqoo.com/in/products/param/iqoo15. No actual iQOO/NPU result. Official guide https://iqoo.reskilll.com/guide restricts competition code to the event window. This repository and deck disclose pre-event research; source reuse requires organizer approval. Phone-only delivery is the first gate. Optional DJI work cannot block it. Proof links: '+JSON.stringify(links));
}

const draft=path.join(build,'candidate.pptx');
await (await PresentationFile.exportPptx(deck)).save(draft);
const suffix=process.env.MINIFILM_DECK_SUFFIX || 'v1';
const finalPath=path.join(out,'final',`Mini-Film-Director-idea-deck-${suffix}.pptx`);
const result=await finalizePresentation({workspaceDir:root,candidatePath:draft,finalPath,pythonExecutable:'/Users/sanju/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3',integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-heading-fit','--require-native-table-slide','6'],explicitTotalSlideCount:7,requiredNativeTableOwnerSlides:[6],fontPolicy:{basis:'design',families:[font]},verifyArtifactToolImport:true,receiptPath:path.join(build,`validation-${suffix}.json`)});
const imported=await PresentationFile.importPptx(await FileBlob.load(finalPath));
for(let i=0;i<imported.slides.items.length;i++){const s=imported.slides.items[i];const png=await imported.export({slide:s,format:'png',scale:1});await fs.writeFile(path.join(out,'previews',`slide-${i+1}.png`),new Uint8Array(await png.arrayBuffer()));}
await fs.writeFile(path.join(build,'deck-proto.json'),JSON.stringify(imported.toProto()));
console.log(JSON.stringify({finalPath,result,font,slides:imported.slides.items.length}));
