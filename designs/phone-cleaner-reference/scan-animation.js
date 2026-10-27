/* Uses the project's vendored Skia Skottie runtime to play the native-ready JSON. */
const scanScreen = document.getElementById('scanScreen');
const scanCanvas = document.getElementById('scanCanvas');
const scanArt = document.querySelector('.scan-art');
const scanConfigs = {
  'Duplicate Photo Cleaner': {file:'scan-photos.json',title:'Duplicate Photos',heading:'Searching similar photos',description:'Comparing pictures on your device'},
  'Screenshot Cleaner': {file:'scan-screenshots.json',title:'Screenshots',heading:'Finding screenshots',description:'Reviewing saved captures'},
  'Big File Cleaner': {file:'scan-big-files.json',title:'Big Files',heading:'Finding large files',description:'Checking what takes up space'},
  cleaning: {file:'scan-cleaning.json',title:'Cleaning',heading:'Cleaning selected files',description:'Freeing up space on your device',kicker:'SMART CLEANING',progress:'Cleaning your files'}
};
let scanKitPromise;
let scanToken=0;
let scanFrame=0;
let scanSurface;
let scanMotion;
let scanOrigin='home';
const scanDuration=3000;
const scanParams=new URLSearchParams(location.search);
const scanLoopPreview=scanParams.get('loop')==='1';
const scanPinnedFrame=scanParams.has('frame')?Math.max(0,Math.min(95,Number(scanParams.get('frame'))||0)):null;

function releaseScan(){
  cancelAnimationFrame(scanFrame);
  scanFrame=0;
  scanMotion?.delete();scanMotion=undefined;
  scanSurface?.delete();scanSurface=undefined;
  scanArt.classList.remove('ready');
}
function restoreAfterScan(){
  scanScreen.hidden=true;
  if(scanOrigin==='tool')toolScreen.hidden=false;
  else if(scanOrigin==='clean')cleanScreen.hidden=false;
  else homeScreen.hidden=false;
}
function cancelScan(){scanToken++;releaseScan();restoreAfterScan();}
document.getElementById('scanBack').addEventListener('click',cancelScan);

async function beginScan(name,from,preview=false,onFinish){
  const config=scanConfigs[name];if(!config)return openFeature(name,from,true);
  const hold=preview&&scanLoopPreview;
  const pinned=preview?scanPinnedFrame:null;
  scanToken++;const token=scanToken;
  releaseScan();
  scanOrigin=from||(cleanScreen.hidden?'home':'clean');
  homeScreen.hidden=true;cleanScreen.hidden=true;toolScreen.hidden=true;
  menuOverlay.hidden=true;scanScreen.hidden=false;
  scanScreen.dataset.screenLabel=config.title;
  document.getElementById('scanTitle').textContent=config.title;
  document.getElementById('scanHeading').textContent=config.heading;
  document.getElementById('scanDescription').textContent=config.description;
  document.getElementById('scanKicker').textContent=config.kicker||'SMART SCAN';
  document.getElementById('scanProgressLabel').textContent=config.progress||'Scanning your files';
  document.getElementById('scanBack').setAttribute('aria-label',name==='cleaning'?'Cancel cleaning':'Cancel scan');
  scanCanvas.setAttribute('aria-label',`${config.title} animation`);
  document.getElementById('scanPercent').textContent='0%';
  document.getElementById('scanProgress').style.width='0%';
  const finish=()=>{if(token!==scanToken)return;scanToken++;releaseScan();scanScreen.hidden=true;if(onFinish)onFinish();else if(name==='cleaning')cleanScreen.hidden=false;else openFeature(name,scanOrigin,true);};
  try{
    scanKitPromise ||= CanvasKitInit({locateFile:()=> 'animations/vendor/canvaskit.wasm'});
    const [kit,raw]=await Promise.all([scanKitPromise,fetch(`animations/${config.file}?v=3`).then(response=>{
      if(!response.ok)throw Error('Scan animation missing');return response.text();
    })]);
    if(token!==scanToken)return;
    const composition=JSON.parse(raw);
    const assetName=composition.assets?.[0]?.p;
    const namedAssets={};
    if(assetName){
      const assetResponse=await fetch(`animations/${assetName}?v=3`);
      if(!assetResponse.ok)throw Error('Scan orb image missing');
      namedAssets[assetName]=await assetResponse.arrayBuffer();
    }
    if(token!==scanToken)return;
    scanSurface=kit.MakeCanvasSurface(scanCanvas);
    scanMotion=kit.MakeManagedAnimation(raw,namedAssets);
    if(!scanSurface||!scanMotion)throw Error('Skottie could not open scan animation');
    scanArt.classList.add('ready');
    const start=performance.now();
    const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
    const draw=now=>{
      if(token!==scanToken)return;
      const elapsed=now-start;
      const progress=pinned!==null?Math.round(Math.min(pinned,composition.op-1)/(composition.op-1)*100):hold?Math.floor(elapsed%scanDuration/scanDuration*100):Math.min(100,Math.floor(elapsed/scanDuration*100));
      document.getElementById('scanPercent').textContent=`${progress}%`;
      document.getElementById('scanProgress').style.width=`${progress}%`;
      const frame=pinned!==null?Math.min(pinned,composition.op-1):reduced?Math.floor(composition.op/2):(elapsed/1000*composition.fr)%composition.op;
      const canvas=scanSurface.getCanvas();canvas.clear(kit.TRANSPARENT);
      scanMotion.seekFrame(frame);scanMotion.render(canvas,kit.LTRBRect(-120,-120,480,480));
      scanSurface.flush();
      if(pinned!==null)return;
      if(!hold&&elapsed>=scanDuration){finish();return;}
      scanFrame=requestAnimationFrame(draw);
    };
    scanFrame=requestAnimationFrame(draw);
  }catch(error){
    if(token!==scanToken)return;
    (window.prototypeErrors||=[]).push(String(error));
    document.getElementById('scanDescription').textContent='Opening scan results…';
    setTimeout(finish,1400);
  }
}

const scanPreviewPage=new URLSearchParams(location.search).get('page');
if(scanPreviewPage==='scan-photos')beginScan('Duplicate Photo Cleaner',undefined,true);
if(scanPreviewPage==='scan-screenshots')beginScan('Screenshot Cleaner',undefined,true);
if(scanPreviewPage==='scan-bigfiles')beginScan('Big File Cleaner',undefined,true);
if(scanPreviewPage==='cleaning')beginScan('cleaning','clean',true);
