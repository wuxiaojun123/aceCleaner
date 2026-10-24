/* Clickable design prototype. All scan results below are sample data. */
const toolScreen = document.getElementById('toolScreen');
const toolContent = document.getElementById('toolContent');
const toolFooter = document.getElementById('toolFooter');
const toolScroll = document.getElementById('toolScroll');
const featureState = {
  current: '', previous: 'home', selected: new Set(), tab: 'Screenshots',
  filterType: 'All', filterSize: 'All sizes', filterTime: 'Any time',
  sort: 'Name', sortDescending: false, keepBest: true, notificationsEnabled: false,
  language: 'English', testRunning: false, testComplete: false, networkStep: 0,
  interceptedCount: 18, allowedApps: new Set(['Messages','Calendar','Phone']), allowedQuery: ''
};
const sampleFiles = [
  {name:'Holiday_video.mp4',type:'Video',size:'1.42 GB',mb:1454,date:'This week',icon:'▶'},
  {name:'Camera_backup.zip',type:'Archive',size:'812 MB',mb:812,date:'This month',icon:'▣'},
  {name:'Project_export.mov',type:'Video',size:'648 MB',mb:648,date:'This month',icon:'▶'},
  {name:'Portrait_original.jpg',type:'Image',size:'38 MB',mb:38,date:'Older',icon:'▧'},
  {name:'Podcast_episode.mp3',type:'Audio',size:'91 MB',mb:91,date:'Older',icon:'♫'},
  {name:'Document_scan.pdf',type:'Document',size:'22 MB',mb:22,date:'This week',icon:'▤'}
];
const sampleApps = [
  {name:'Photos',size:'824 MB',mb:824,used:'Today',install:2,last:4,icon:'▧'},
  {name:'Maps',size:'612 MB',mb:612,used:'Yesterday',install:4,last:3,icon:'◈'},
  {name:'Music',size:'458 MB',mb:458,used:'3 days ago',install:1,last:2,icon:'♫'},
  {name:'Notes',size:'126 MB',mb:126,used:'Last week',install:3,last:1,icon:'▤'}
];
const samplePhotos = [
  {name:'IMG_2048',size:'4.2 MB',group:'Beach • 3 similar',best:true},
  {name:'IMG_2049',size:'4.1 MB',group:'Beach • 3 similar'},
  {name:'IMG_2050',size:'4.0 MB',group:'Beach • 3 similar'},
  {name:'IMG_3110',size:'3.8 MB',group:'City • 3 similar',best:true},
  {name:'IMG_3111',size:'3.7 MB',group:'City • 3 similar'},
  {name:'IMG_3112',size:'3.7 MB',group:'City • 3 similar'}
];
const sampleMedia = [
  {name:'Screenshot_1024',size:'2.4 MB',type:'Screenshots'},
  {name:'Screenshot_0912',size:'1.8 MB',type:'Screenshots'},
  {name:'Screenshot_0823',size:'2.1 MB',type:'Screenshots'},
  {name:'Download_image',size:'3.2 MB',type:'Others'},
  {name:'Shared_image',size:'1.6 MB',type:'Others'},
  {name:'Saved_receipt',size:'1.2 MB',type:'Others'}
];
const sampleVideos = [
  {name:'Weekend trip',size:'642 MB'}, {name:'Concert clip',size:'384 MB'},
  {name:'Screen recording',size:'231 MB'}, {name:'Family video',size:'148 MB'}
];
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const rows = (items, options={}) => `<div class="tool-card">${items.length ? items.map((item,index)=>{
  const key = item.name || item.label;
  const selectable = options.selectable;
  const pressed = selectable && featureState.selected.has(key);
  return `<button class="tool-row" type="button" data-row="${key}" ${selectable?`aria-pressed="${pressed}"`:''}>
    <span class="tool-row-icon ${item.tone||['','orange','green','violet','coral'][index%5]}">${item.icon||'▤'}</span>
    <span class="tool-row-main"><b>${key}</b><small>${item.sub||item.type||''}</small></span>
    <span class="tool-row-value">${item.size||item.value||''}</span>${selectable?'<span class="tool-check"></span>':'<span class="tool-row-value">›</span>'}</button>`;
}).join(''):'<div class="tool-empty">Nothing to show with these filters.</div>'}</div>`;
const hero = (label,value,description,tone='') => `<div class="tool-hero ${tone}"><small>${label}</small><strong>${value}</strong><span>${description}</span></div>`;
const title = (label,hint='') => `<h2 class="tool-section-title">${label}${hint?`<small>${hint}</small>`:''}</h2>`;
const chips = (values,active,kind) => `<div class="tool-chipbar">${values.map(v=>`<button class="tool-chip ${v===active?'active':''}" type="button" data-chip-kind="${kind}" data-chip="${v}">${v}</button>`).join('')}</div>`;
const stats = pairs => `<div class="tool-statgrid">${pairs.map(([value,label])=>`<div class="tool-stat"><strong>${value}</strong><small>${label}</small></div>`).join('')}</div>`;
const foot = (label,action,disabled=false,notice='') => { toolFooter.hidden=false;toolFooter.innerHTML=`${notice?`<p class="tool-result-note">${notice}</p>`:''}<button class="primary" type="button" data-action="${action}" ${disabled?'disabled':''}>${label}</button>`; };
const notify = (heading,body) => {document.getElementById('resultTitle').textContent=heading;document.getElementById('resultText').textContent=body;resultOverlay.hidden=false;document.getElementById('doneButton').focus();};
function openFeature(name, from) {
  clearTimeout(featureState.networkTimer);
  if(from==='tool') featureState.parent=featureState.current;
  featureState.previous = from || (cleanScreen.hidden ? 'home' : 'clean');
  featureState.current = name;
  featureState.selected.clear();
  if(name==='Clean Up') ['Cache files','Ad files','Temporary files','Obsolete APKs','Residual files','Empty files'].forEach(x=>featureState.selected.add(x));
  if(name==='Duplicate Photo Cleaner'&&featureState.keepBest)samplePhotos.filter(p=>!p.best).forEach(p=>featureState.selected.add(p.name));
  featureState.tab='Screenshots';
  menuOverlay.hidden=true;infoOverlay.hidden=true;settingsOverlay.hidden=true;
  homeScreen.hidden=true;cleanScreen.hidden=true;toolScreen.hidden=false;
  toolScroll.scrollTop=0;
  renderFeature();
  document.getElementById('toolBack').focus();
}
function closeFeature() {
  clearTimeout(featureState.networkTimer);
  if(featureState.previous==='tool'){const parent=featureState.parent;featureState.previous='home';featureState.current=parent;featureState.selected.clear();renderFeature();toolScroll.scrollTop=0;return;}
  toolScreen.hidden=true;
  if(featureState.previous==='clean'){cleanScreen.hidden=false;document.getElementById('backButton').focus();}
  else {homeScreen.hidden=false;document.getElementById('openCleaning').focus();}
}
function featureTo(name) {openFeature(name,'tool');}
function renderFeature() {
  const f=featureState.current;
  document.getElementById('toolTitle').textContent=({'APP Manager':'App Manager','Duplicate Photo Cleaner':'Duplicate Photos','Big File Cleaner':'Big Files','Video Cleaner':'Videos','Screenshot Cleaner':'Screenshots','Device Scan':'Device Scan','Clean Up':'Junk Cleaner'})[f]||f;
  toolFooter.hidden=true;
  const lead='<p class="tool-kicker">Sample data · interactive design preview</p>';
  let html=['APP Manager','Screenshot Cleaner','Big File Cleaner'].includes(f)?'':lead;
  if(f==='Clean Up') {
    const data=[{name:'Cache files',sub:'Temporary app cache',size:'241 MB',icon:'✦'},{name:'Ad files',sub:'Downloaded advertising files',size:'82 MB',icon:'◇'},{name:'Temporary files',sub:'Safe to review',size:'61 MB',icon:'◷'},{name:'Obsolete APKs',sub:'Old installation packages',size:'198 MB',icon:'▣'},{name:'Residual files',sub:'Left by removed apps',size:'73 MB',icon:'▤'},{name:'Empty files',sub:'Unused empty folders',size:'0 B',icon:'○'}];
    html+=hero('Junk found','655 MB','Choose what to remove from this scan');
    html+=title('Cleaning categories','6 found')+rows(data,{selectable:true});
    html+='<button class="tool-action-link" type="button" data-action="rescan">↻ Scan again</button><div class="tool-card"><button class="tool-row" type="button" data-action="open-big"><span class="tool-row-icon orange">▤</span><span class="tool-row-main"><b>Find large files</b><small>Review more storage</small></span><span>›</span></button><button class="tool-row" type="button" data-action="system-cache"><span class="tool-row-icon">⚙</span><span class="tool-row-main"><b>System app cache</b><small>Open Android storage settings</small></span><span>›</span></button></div>';
    foot(`Clean selected (${featureState.selected.size})`,'delete',!featureState.selected.size);
  } else if(f==='Network Traffic') {
    html+=hero('Total data this month','12.8 GB','Network usage across your installed apps');
    html+=stats([['4.2 GB','Mobile data'],['8.6 GB','Wi-Fi']]);
    html+=title('Last 7 days')+`<div class="tool-card tool-chart">${[34,63,46,84,56,100,72].map((h,i)=>`<div><i style="height:${h}%"></i><small>${['M','T','W','T','F','S','S'][i]}</small></div>`).join('')}</div>`;
    html+=title('App data ranking')+rows([{name:'Video',sub:'Wi-Fi 3.2 GB · Mobile 0.8 GB',size:'4.0 GB',icon:'▶'},{name:'Browser',sub:'Wi-Fi 1.8 GB · Mobile 0.6 GB',size:'2.4 GB',icon:'◎'},{name:'Social',sub:'Wi-Fi 1.1 GB · Mobile 0.9 GB',size:'2.0 GB',icon:'◈'},{name:'Music',sub:'Wi-Fi 0.9 GB · Mobile 0.3 GB',size:'1.2 GB',icon:'♫'}]);
  } else if(f==='Duplicate Photo Cleaner') {
    html+=hero('Similar photos','6 photos','2 groups · about 23.5 MB to review','orange');
    html+=`<div class="tool-card"><label class="tool-row"><span class="tool-row-icon orange">★</span><span class="tool-row-main"><b>Keep best photo</b><small>Suggest copies to remove</small></span><input class="tool-switch" type="checkbox" data-switch="keep-best" ${featureState.keepBest?'checked':''}></label></div>`;
    ['Beach • 3 similar','City • 3 similar'].forEach(group=>{html+=title(group,'Select copies')+photoGrid(samplePhotos.filter(p=>p.group===group));});
    foot(`Delete selected (${featureState.selected.size})`,'delete',!featureState.selected.size,selectionSize());
  } else if(f==='APP Manager') {
    html+=title('Sort by')+chips(['Name','Installation','Size','Last used'],featureState.sort,'sort');
    const sorted=[...sampleApps];if(featureState.sort==='Name')sorted.sort((a,b)=>a.name.localeCompare(b.name));if(featureState.sort==='Size')sorted.sort((a,b)=>b.mb-a.mb);if(featureState.sort==='Installation')sorted.sort((a,b)=>b.install-a.install);if(featureState.sort==='Last used')sorted.sort((a,b)=>b.last-a.last);if(featureState.sortDescending)sorted.reverse();
    html+=title('Installed apps')+rows(sorted.map(a=>({...a,sub:`Last used ${a.used}`})),{selectable:true});
    foot(`Uninstall selected (${featureState.selected.size})`,'uninstall',!featureState.selected.size);
  } else if(f==='Screenshot Cleaner') {
    html+=title('Category')+chips(['Screenshots','Others'],featureState.tab,'tab');
    const shown=sampleMedia.filter(p=>p.type===featureState.tab);
    html+=`<div class="tool-inline"><span>October 2026</span><label><input type="checkbox" data-select-all ${shown.every(p=>featureState.selected.has(p.name))?'checked':''}> Select all</label></div>`+photoGrid(shown);
    foot(`Clean selected (${featureState.selected.size})`,'delete',!featureState.selected.size,selectionSize());
  } else if(f==='Big File Cleaner') {
    const filtered=sampleFiles.filter(file=>
      (featureState.filterType==='All'||file.type===featureState.filterType)&&
      (featureState.filterSize==='All sizes'||(featureState.filterSize==='Over 100 MB'?file.mb>100:file.mb>500))&&
      (featureState.filterTime==='Any time'||file.date===featureState.filterTime));
    html+=title('Type')+chips(['All','Video','Image','Audio','Document','Archive'],featureState.filterType,'type');
    html+=title('Size')+chips(['All sizes','Over 100 MB','Over 500 MB'],featureState.filterSize,'size');
    html+=title('Time')+chips(['Any time','This week','This month','Older'],featureState.filterTime,'time');
    html+=title('Files')+rows(filtered.map(x=>({...x,sub:x.type+' · '+x.date})),{selectable:true});
    foot(`Clean selected (${featureState.selected.size})`,'delete',!featureState.selected.size);
  } else if(f==='Video Cleaner') {
    html+=hero('Videos','4 videos','Review large video files','violet');
    html+=`<div class="tool-inline"><span>October 2026</span><label><input type="checkbox" data-select-all ${sampleVideos.every(v=>featureState.selected.has(v.name))?'checked':''}> Select all</label></div>`;
    html+=photoGrid(sampleVideos);
    foot(`Clean selected (${featureState.selected.size})`,'delete',!featureState.selected.size,selectionSize());
  } else if(f==='Notification Cleaner') {
    html+=hero('Intercepted today',String(featureState.interceptedCount),'Keep distracting alerts under control','violet');
    html+=`<div class="tool-card"><label class="tool-row"><span class="tool-row-icon violet">♧</span><span class="tool-row-main"><b>Notification interception</b><small>Pause unwanted notifications</small></span><input class="tool-switch" type="checkbox" data-switch="notifications" ${featureState.notificationsEnabled?'checked':''}></label><button class="tool-row" type="button" data-action="whitelist"><span class="tool-row-icon green">✓</span><span class="tool-row-main"><b>Allowed apps</b><small>Important notifications still appear</small></span><span class="tool-row-value">3 apps ›</span></button></div>`;
    html+=title('Top apps')+rows(featureState.interceptedCount?[{name:'Shopping',sub:'Offers and promotions',size:'9 alerts',icon:'▣'},{name:'Games',sub:'Daily rewards',size:'6 alerts',icon:'◇'},{name:'News',sub:'Breaking stories',size:'3 alerts',icon:'▤'}]:[]);
    html+=title('Recent intercepted')+rows(featureState.interceptedCount?[{name:'Shopping',sub:'Your cart is waiting · 10:24',icon:'▣'},{name:'Games',sub:'Collect your reward · 09:42',icon:'◇'}]:[]);
    foot('Clear intercepted notifications','clear-notifications',!featureState.interceptedCount);
  } else if(f==='Allowed Apps') {
    html+=hero('Allowed apps',`${featureState.allowedApps.size} apps`,'Choose which apps can notify you','green');
    html+=`<input class="tool-search" id="allowedSearch" type="search" placeholder="Search apps" value="${escapeHtml(featureState.allowedQuery)}" aria-label="Search apps">`;
    const available=[['Messages','✉'],['Calendar','▦'],['Phone','●'],['Maps','◈'],['Music','♫'],['Shopping','▣'],['Games','◇']].filter(([name])=>name.toLowerCase().includes(featureState.allowedQuery.toLowerCase()));
    html+=`<div class="tool-card">${available.length?available.map(([name,icon],i)=>`<label class="tool-row"><span class="tool-row-icon ${['','orange','green','violet'][i%4]}">${icon}</span><span class="tool-row-main"><b>${name}</b><small>${featureState.allowedApps.has(name)?'Allowed':'Blocked'}</small></span><input class="tool-switch" type="checkbox" data-allowed="${name}" ${featureState.allowedApps.has(name)?'checked':''}></label>`).join(''):'<div class="tool-empty">No apps found.</div>'}</div>`;
  } else if(f==='RAM Status') {
    html+=hero('Available memory','42%','2.6 GB available of 6 GB total','green');
    html+=`<div class="tool-card" style="padding:16px 19px"><b>Memory usage</b><div class="tool-progress"><span style="width:58%"></span></div><small class="tool-muted">58% used · 42% free</small></div>`;
    html+=title('Recently used apps')+rows([{name:'Browser',sub:'Recently active',size:'Close ›',icon:'◎'},{name:'Music',sub:'Recently active',size:'Close ›',icon:'♫'},{name:'Maps',sub:'Recently active',size:'Close ›',icon:'◈'}]);
    foot('OK','back');
  } else if(f==='Device Scan') {
    html+=hero('Device report','Healthy','Hardware and system overview');
    html+=`<div class="tool-card" style="padding:18px"><small class="tool-muted">CPU model</small><h2 style="margin:7px 0 0;font-size:20px">8-core processor</h2></div>`;
    html+=stats([['128 GB','Storage'],['6 GB','Memory'],['42%','RAM available'],['Android 15','Android version'],['1080 × 2400','Resolution'],['3.2','OpenGL ES'],['1.3','Vulkan'],['192.168.1.8','IP address'],['433 Mbps','Link speed']]);
    foot('OK','back');
  } else if(f==='Battery Info') {
    html+=hero('Battery level','82%','Estimated time remaining · 8 h 25 min','green');
    html+=stats([['4,500 mAh','Estimated capacity'],['Good','Battery health'],['Li-ion','Battery type'],['Not charging','Charging status'],['31.2°C','Temperature'],['3.89 V','Voltage'],['3,690 mAh','Current capacity']]);
    foot('Got it','back');
  } else if(f==='Network Test') {
    const step=featureState.networkStep;
    const running=featureState.testRunning;
    const done=featureState.testComplete;
    const progress=done?100:running?[10,30,66,89][Math.min(step,3)]:0;
    const speed=done?'58.6':!running?'0.0':step===0?'0.0':step===1?'22':step===2?'58.6':'18.3';
    const unit=running&&step===1?'ms':'Mbps';
    const status=done?'Test complete':!running?'Ready to test':['Connecting to server','Measuring ping','Testing download','Testing upload'][Math.min(step,3)];
    const phase=done?'Your connection looks good':!running?'Check your connection in seconds':['Preparing the connection','Checking response time','Measuring incoming speed','Measuring outgoing speed'][Math.min(step,3)];
    html=`<div class="speed-intro"><span>NETWORK SPEED TEST</span><h2>${done?'Connection checked':'How fast is your connection?'}</h2><p>${phase}</p></div>
      <div class="speed-dial-wrap"><div class="speed-dial" aria-live="polite">
        <svg class="speed-ring" viewBox="0 0 300 300" aria-hidden="true"><defs><linearGradient id="speedGradient" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#62c8ff"/><stop offset=".55" stop-color="#1f9bff"/><stop offset="1" stop-color="#0874f8"/></linearGradient></defs><circle cx="150" cy="150" r="126" class="speed-track"/><circle cx="150" cy="150" r="126" class="speed-arc" pathLength="100" stroke-dasharray="${progress} 100"/></svg>
        <div class="speed-inner"><span class="speed-wifi"><svg viewBox="0 0 24 24"><path d="M3 9a14 14 0 0 1 18 0M6.5 12.5a9 9 0 0 1 11 0M10 16a4 4 0 0 1 4 0"/><circle cx="12" cy="19" r="1" fill="currentColor" stroke="none"/></svg></span><span class="speed-reading">${done?'✓':speed}</span><span class="speed-unit">${done?'COMPLETE':unit}</span><span class="speed-status">${status}</span></div>
      </div></div>
      <div class="speed-steps" aria-label="Test stages">${[['Ping','Latency'],['Download','Incoming'],['Upload','Outgoing']].map(([name,sub],index)=>`<div class="speed-step ${done||running&&step>index+1?'finished':running&&step===index+1?'current':''}"><span class="speed-step-dot">${done||running&&step>index+1?'✓':index+1}</span><b>${name}</b><small>${sub}</small></div>`).join('')}</div>
      <h2 class="speed-results-title">Results <small>${done?'Just now':'Measured as the test runs'}</small></h2>
      <div class="speed-results"><div class="speed-result ping"><span class="speed-result-icon">↔</span><small>Ping</small><strong>${done||running&&step>=1?'22':'—'}</strong><em>ms</em></div><div class="speed-result download"><span class="speed-result-icon">↓</span><small>Download</small><strong>${done||running&&step>=2?'58.6':'—'}</strong><em>Mbps</em></div><div class="speed-result upload"><span class="speed-result-icon">↑</span><small>Upload</small><strong>${done||running&&step>=3?'18.3':'—'}</strong><em>Mbps</em></div></div>
      <div class="speed-connection"><span class="speed-connection-dot"></span><span>Wi-Fi connection</span><strong>Connected</strong></div>`;
    foot(running?'Testing…':done?'Test again':'Start speed test','start-test',running);
  } else if(f==='Language') {
    html+=hero('App language',featureState.language,'Choose your preferred language');
    const languages=['English','日本語','हिन्दी','한국어','Español','Português','Deutsch','Français','Türkçe','Italiano','Afrikaans','isiXhosa','isiZulu','Русский','Українська','Tiếng Việt','Bahasa Indonesia','Bahasa Melayu','ไทย','Filipino'];
    html+=`<div class="tool-card">${languages.map((language,index)=>`<button class="tool-row" type="button" data-language="${language}"><span class="tool-row-icon ${['','orange','green','violet'][index%4]}">${language.slice(0,1)}</span><span class="tool-row-main"><b>${language}</b></span><span class="tool-check" style="${featureState.language===language?'background:#147dfa;border-color:#147dfa':''}">${featureState.language===language?'✓':''}</span></button>`).join('')}</div>`;
    foot('Save language','save-language');
  }
  toolContent.innerHTML=html;
}
function photoGrid(items){return `<div class="tool-photo-grid">${items.map(p=>`<button class="tool-photo" type="button" data-photo="${p.name}" aria-pressed="${featureState.selected.has(p.name)}"><span class="tool-check"></span><b>${p.name}${p.best?' · Best':''}<br>${p.size}</b></button>`).join('')}</div>`;}
function selectionSize(){return featureState.selected.size?`${featureState.selected.size} selected in this preview`:'';}
function updateSelectionUI(){
  toolContent.querySelectorAll('[data-row],[data-photo]').forEach(node=>{const key=node.dataset.row||node.dataset.photo;if(node.hasAttribute('aria-pressed'))node.setAttribute('aria-pressed',String(featureState.selected.has(key)));});
  const button=toolFooter.querySelector('[data-action="delete"],[data-action="uninstall"]');
  if(button){const verb=featureState.current==='APP Manager'?'Uninstall selected':featureState.current==='Duplicate Photo Cleaner'?'Delete selected':'Clean selected';button.textContent=`${verb} (${featureState.selected.size})`;button.disabled=!featureState.selected.size;}
  const note=toolFooter.querySelector('.tool-result-note');if(note)note.textContent=selectionSize();
}
function visibleSelectionKeys(){
  if(featureState.current==='Video Cleaner')return sampleVideos.map(x=>x.name);
  if(featureState.current==='Screenshot Cleaner')return sampleMedia.filter(x=>x.type===featureState.tab).map(x=>x.name);
  return [];
}
toolContent.addEventListener('click',event=>{
  const chip=event.target.closest('[data-chip]');
  if(chip){const k=chip.dataset.chipKind,v=chip.dataset.chip;
    if(k==='sort'){featureState.sortDescending=featureState.sort===v?!featureState.sortDescending:false;featureState.sort=v;}
    else {featureState[{tab:'tab',type:'filterType',size:'filterSize',time:'filterTime'}[k]]=v;featureState.selected.clear();}
    renderFeature();return;}
  const language=event.target.closest('[data-language]');if(language){featureState.language=language.dataset.language;renderFeature();return;}
  const node=event.target.closest('[data-row],[data-photo]');
  if(node){const key=node.dataset.row||node.dataset.photo;
    if(node.hasAttribute('aria-pressed')){featureState.selected.has(key)?featureState.selected.delete(key):featureState.selected.add(key);updateSelectionUI();return;}
    if(featureState.current==='RAM Status'){notify('App details','On Android, this opens the selected app settings so you can close it.');return;}
    if(featureState.current==='Network Traffic'){notify('Usage details',`${key} usage is shown here as sample data.`);return;}
  }
});
toolContent.addEventListener('change',event=>{
  const t=event.target;
  if(t.dataset.selectAll!==undefined){visibleSelectionKeys().forEach(key=>t.checked?featureState.selected.add(key):featureState.selected.delete(key));updateSelectionUI();}
  if(t.dataset.switch==='keep-best'){featureState.keepBest=t.checked;featureState.selected.clear();if(t.checked)samplePhotos.filter(p=>!p.best).forEach(p=>featureState.selected.add(p.name));renderFeature();}
  if(t.dataset.switch==='notifications'){featureState.notificationsEnabled=t.checked;document.getElementById('notificationToggle').checked=t.checked;notify(t.checked?'Interception enabled':'Interception paused','This switch is interactive in the design preview.');}
  if(t.dataset.allowed){t.checked?featureState.allowedApps.add(t.dataset.allowed):featureState.allowedApps.delete(t.dataset.allowed);const q=featureState.allowedQuery;renderFeature();featureState.allowedQuery=q;}
});
toolContent.addEventListener('input',event=>{if(event.target.id==='allowedSearch'){featureState.allowedQuery=event.target.value;const pos=event.target.selectionStart;renderFeature();const field=document.getElementById('allowedSearch');field.focus();field.setSelectionRange(pos,pos);}});
function runToolAction(action){
  if(action==='back'){closeFeature();return;}
  if(action==='open-big'){featureTo('Big File Cleaner');return;}
  if(action==='whitelist'){featureTo('Allowed Apps');return;}
  if(action==='rescan'){featureState.selected.clear();renderFeature();notify('Scan complete','Sample cleanup categories have been refreshed.');return;}
  if(action==='system-cache'){notify('System app cache','The Android app opens the device storage settings for this action.');return;}
  if(action==='save-language'){notify('Language selected',`${featureState.language} is selected in this design preview.`);return;}
  if(action==='clear-notifications'){const count=featureState.interceptedCount;featureState.interceptedCount=0;renderFeature();notify('Notifications cleared',`${count} intercepted notifications cleared in this preview.`);return;}
  if(action==='delete'||action==='uninstall'){
    const count=featureState.selected.size;if(!count)return;
    notify(action==='uninstall'?'Uninstall preview':'Cleaning preview',`${count} ${count===1?'item':'items'} selected. The Android app performs the actual ${action==='uninstall'?'uninstall':'deletion'} with system permission.`);
    featureState.selected.clear();updateSelectionUI();return;
  }
  if(action==='start-test'){
    if(featureState.testRunning)return;
    featureState.testRunning=true;featureState.testComplete=false;featureState.networkStep=0;renderFeature();
    let step=0;featureState.networkTimer=setInterval(()=>{step++;featureState.networkStep=step;if(step>=4){clearInterval(featureState.networkTimer);featureState.testRunning=false;featureState.testComplete=true;}renderFeature();},720);
  }
}
toolScreen.addEventListener('click',event=>{const a=event.target.closest('[data-action]');if(a)runToolAction(a.dataset.action);});
document.getElementById('toolBack').addEventListener('click',closeFeature);
document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!toolScreen.hidden&&!resultOverlay.hidden)return;if(event.key==='Escape'&&!toolScreen.hidden)closeFeature();});
// Make the native Clean Up result reachable while keeping the reference screen intact.
document.getElementById('menuClean').insertAdjacentHTML('afterend','<button class="menu-entry" id="menuJunk" type="button"><span>Junk Cleaner</span><span>→</span></button>');
document.getElementById('menuJunk').addEventListener('click',()=>openFeature('Clean Up'));
const previewPage = new URLSearchParams(location.search).get('page');
const previewPages = {apps:'APP Manager',screenshots:'Screenshot Cleaner',bigfiles:'Big File Cleaner',speed:'Network Test'};
if(previewPages[previewPage]) openFeature(previewPages[previewPage]);
