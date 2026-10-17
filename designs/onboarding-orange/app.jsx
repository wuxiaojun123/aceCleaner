const prototypeScreens=[
    ['splash','启动页','沿用原启动页的 Logo、标题与进度布局。'],
    ['deviceScan','设备扫描','设备轮廓内的橙色扫描线往返移动，读数与进度同步。'],
    ['deviceDetail','设备信息','Taste 橙色立体版：原品牌橙 #FF6B2C、大型号、立体手机与悬叠数据面板。保留全部参数及 Skip、清理流程。'],
    ['junkScan','垃圾扫描','文件列表扫描动效，扫描数量与进度递增。'],
    ['junkResult','垃圾结果','展开分类、勾选子项，顶部总量跟随实际选择变化。'],
    ['junkCleaning','清理中','扫帚小幅往返，碎屑向清理区域收拢。'],
    ['complete','完成页','完成标记绘制后停留，沿用原有三个推荐入口。'],
    ['batteryScan','电池扫描','未授予存储权限时进入，电池图标呼吸表示读取状态。'],
    ['batteryInfo','电池信息','电量、充电估计、状态及四项指标，保持原有检查入口。'],
    ['batteryCheck','电池检查','电池轮廓中绘制对勾，表达检查完成。'],
    ['overlay','悬浮窗引导 · 备用','当前 App 已暂时关闭首次入口；这里只提供备用设计预览。'],
    ['home','首页 · 固定参照','按现有 fragment_home.xml 及其资源复现。点击 Network Test 体验新增测速设计。'],
    ['networkTest','网络测速','暖白底、轻圆环、大读数；保留自动测速、延迟/下载/上传、结果与重测。数据为本地模拟。']
];
const newGroups=()=>[
    {title:'System cache',icon:'ram',expanded:false,items:[{name:'App cache',bytes:60*1048576,selected:true},{name:'Ad cache',bytes:16*1048576,selected:true}]},
    {title:'Thumbnails',icon:'thumbnail',expanded:false,items:[{name:'DCIM',bytes:18*1048576,selected:true}]},
    {title:'Empty folders',icon:'folder',expanded:false,items:[{name:'Empty folders',bytes:0,selected:true}]},
    {title:'Downloads',icon:'download',expanded:false,items:[{name:'Downloads',bytes:128*1048576,selected:false}]},
    {title:'Apks',icon:'android',expanded:false,items:[{name:'Apks',bytes:54*1048576,selected:false}]}
];
const scannerPages=['splash','deviceScan','junkScan','junkCleaning','batteryScan','batteryCheck'];
function App(){
    const initial=location.hash.slice(1);const frozen=new URLSearchParams(location.search).get('auto')==='0';
    const [screen,setScreen]=React.useState(prototypeScreens.some(s=>s[0]===initial)?initial:'splash');
    const [continuous,setContinuous]=React.useState(!frozen);const [playing,setPlaying]=React.useState(!frozen);
    const [ads,setAds]=React.useState(true);const [ad,setAd]=React.useState(null);const [sheet,setSheet]=React.useState(null);
    const [system,setSystem]=React.useState(null);const [granted,setGranted]=React.useState(false);const [mobileMenu,setMobileMenu]=React.useState(false);
    const [groups,setGroups]=React.useState(newGroups);const [progress,setProgress]=React.useState(frozen?45:0);
    const [cleaned,setCleaned]=React.useState(94*1048576);const [fromBattery,setFromBattery]=React.useState(false);
    const [destination,setDestination]=React.useState('');const [autoPrompts,setAutoPrompts]=React.useState(true);
    const notificationShown=React.useRef(false);
    const networkFrame=React.useRef(null);
    React.useEffect(()=>{function receive(event){if(event.origin===location.origin&&event.source===networkFrame.current?.contentWindow&&event.data?.type==='network-test-back')go('home');}window.addEventListener('message',receive);return()=>window.removeEventListener('message',receive);},[]);
    React.useEffect(()=>{
        function syncHash(){const next=location.hash.slice(1);if(prototypeScreens.some(s=>s[0]===next)){
            setScreen(next);setSheet(null);setSystem(null);setAd(null);
        }}
        window.addEventListener('hashchange',syncHash);return()=>window.removeEventListener('hashchange',syncHash);
    },[]);
    const total=groups.reduce((sum,g)=>sum+g.items.filter(x=>x.selected).reduce((n,x)=>n+x.bytes,0),0);
    const current=prototypeScreens.find(s=>s[0]===screen);const index=prototypeScreens.findIndex(s=>s[0]===screen);
    function go(next){setSheet(null);setSystem(null);setAd(null);setMobileMenu(false);setScreen(next);history.replaceState(null,'','#'+next)}
    function withAd(slot,next){if(ads)setAd({slot,next});else go(next)}
    function advance(){
        if(screen==='splash')withAd('3007 · FIRST_START','deviceScan');
        else if(screen==='deviceScan')withAd('3003 · DEVICE_SCAN','deviceDetail');
        else if(screen==='deviceDetail')setSheet('storage');
        else if(screen==='junkScan')withAd('3004 · ANALYSIS','junkResult');
        else if(screen==='junkResult')startClean();
        else if(screen==='junkCleaning')withAd('3005 · FINISH','complete');
        else if(screen==='batteryScan'){setFromBattery(true);setScreen('batteryInfo');history.replaceState(null,'','#batteryInfo');withAd('3004 · ANALYSIS','batteryInfo')}
        else if(screen==='batteryInfo')go('batteryCheck');
        else if(screen==='batteryCheck'){setFromBattery(true);withAd('3005 · FINISH','complete')}
        else if(screen==='complete')leaveComplete();
        else if(screen==='overlay')go('splash');
        else if(screen==='home')go('networkTest');
    }
    const advanceRef=React.useRef(advance);advanceRef.current=advance;
    React.useEffect(()=>{
        if(!scannerPages.includes(screen))return;
        setProgress(continuous?0:45);if(!continuous)return;
        let ended=false;const start=performance.now();const duration=screen==='splash'?2000:screen==='junkCleaning'?4800:4400;
        const timer=setInterval(()=>{const p=Math.min(100,(performance.now()-start)/duration*100);setProgress(p);
            if(p>=100&&!ended){ended=true;clearInterval(timer);advanceRef.current()}
        },50);return()=>clearInterval(timer);
    },[screen,continuous]);
    React.useEffect(()=>{
        if(screen!=='complete'||notificationShown.current||!autoPrompts)return;
        const timer=setTimeout(()=>{notificationShown.current=true;setSheet('notification')},1600);return()=>clearTimeout(timer);
    },[screen,autoPrompts]);
    function startClean(){setCleaned(total);setFromBattery(false);go(groups.some(g=>g.items.some(x=>x.selected))?'junkCleaning':'complete')}
    function reset(){setGroups(newGroups());setFromBattery(false);setContinuous(true);setPlaying(true);notificationShown.current=false;setDestination('');go('splash')}
    function leaveComplete(){setSheet('overlay')}
    function feature(name){setDestination('推荐入口：'+name+'。原型预览范围止于新手流程，终点展示固定首页。');go('home')}
    function closePermission(){const previous=sheet;setSheet(null);if(previous==='storage'){setFromBattery(true);go('batteryScan')}else if(previous==='overlay')go('home')}
    function allow(){setGranted(false);setSystem(sheet);setSheet(null)}
    function finishSystem(){const kind=system;setSystem(null);
        if(kind==='storage'){setFromBattery(!granted);go(granted?'junkScan':'batteryScan')}
        else if(kind==='notification'){if(granted)feature('通知清理');else go('complete')}
        else if(screen==='overlay')go('splash');else go('home');
    }
    React.useEffect(()=>{
        if(!sheet&&!system&&!ad)return;
        const dialog=document.querySelector('.sheet,.ad-preview');
        const controls=()=>Array.from(dialog?.querySelectorAll('button,input,a')||[]).filter(x=>!x.disabled);
        const timer=setTimeout(()=>controls()[0]?.focus(),50);
        function trap(e){if(e.key!=='Tab')return;const list=controls();if(!list.length)return;
            if(e.shiftKey&&document.activeElement===list[0]){e.preventDefault();list[list.length-1].focus()}
            else if(!e.shiftKey&&document.activeElement===list[list.length-1]){e.preventDefault();list[0].focus()}
        }
        document.addEventListener('keydown',trap);return()=>{clearTimeout(timer);document.removeEventListener('keydown',trap)};
    },[sheet,system,ad]);
    const scanning={deviceScan:{title:'Device status',message:'Device information is being scanned...',motion:'device-scan'},
        junkScan:{title:'Junk Clean',message:'Scanning junk files...',motion:'junk-scan',items:progress*4.2},
        junkCleaning:{title:'Junk Clean',message:'Cleaning junk files...',motion:'junk-clean',cleaned:cleaned*progress/100},
        batteryScan:{title:'Battery Info',message:'Checking the battery info...',motion:'battery-scan'},
        batteryCheck:{title:'Battery Check',message:'Checking the battery info...',motion:'battery-check'}};
    let content;
    if(scanning[screen])content=<Scan {...scanning[screen]} progress={progress} playing={playing}/>;
    if(screen==='splash')content=<div className="splash"><img src="assets/splash_logo.webp" alt=""/><h1>Smart AI Cleaner</h1><div className="track"><div style={{width:progress+'%'}}></div></div><p>Loading… {Math.floor(progress)}%</p></div>;
    if(screen==='deviceDetail')content=<DeviceDetail onContinue={()=>setSheet('storage')} onSkip={()=>withAd('3006 · SKIP','home')}/>;
    if(screen==='junkResult')content=<JunkResults {...{groups,setGroups,total}} onClean={startClean}/>;
    if(screen==='batteryInfo')content=<BatteryInfo onCheck={()=>go('batteryCheck')} playing={playing}/>;
    if(screen==='complete')content=<Complete {...{cleaned,fromBattery,playing}} onLeave={leaveComplete} onFeature={feature}/>;
    if(screen==='overlay')content=<OverlayGuide onContinue={()=>setSheet('overlay')} onSkip={()=>go('splash')} playing={playing}/>;
    if(screen==='home')content=<Home onClean={()=>go('junkScan')} onNetworkTest={()=>go('networkTest')}/>;
    if(screen==='networkTest')content=<iframe ref={networkFrame} className="network-test-frame" src="network-test.html" title="Network Test interactive prototype"/>;
    const steps=<nav className="steps" aria-label="原型页面">{prototypeScreens.map((s,i)=><button key={s[0]} className={'step '+(screen===s[0]?'active':'')} onClick={()=>go(s[0])}><span>{String(i+1).padStart(2,'0')}</span>{s[1]}</button>)}</nav>;
    return <div className="workspace"><aside className="sidebar"><div className="wordmark"><img src="icon.png" alt=""/><div><strong>Smart AI Cleaner</strong><small>ONBOARDING / ORANGE</small></div></div><p className="eyebrow">页面预览</p>{steps}<div className="subtle-rule"><button className="review-button" onClick={reset}>↻ 从头体验新手流程</button><a className="review-button" href="animations.html">查看全部 Lottie 动画 ↗</a></div></aside>
        <main className="stage"><div className="stage-top"><span>PIXEL 8 / 412 × 915</span><b>{String(index+1).padStart(2,'0')} · {current[1]}</b></div><div className="device"><div className={"phone-app"+(screen==='networkTest'?' network-test-shell':'')} id="app"><div className="phone-content" key={screen} data-screen-label={current[1]}>{content}</div>
            {sheet&&<PermissionSheet type={sheet} onClose={closePermission} onAllow={allow} onDeny={()=>go('home')}/>}
            {system&&<SystemSheet kind={system} granted={granted} setGranted={setGranted} onDone={finishSystem}/>}
            {ad&&<section className="ad-preview" role="dialog" aria-modal="true" aria-label="Interstitial ad preview"><small>Sponsored</small><img src="icon.png" alt=""/><h2>Interstitial preview</h2><p>Smart AI Cleaner</p><button className="primary" onClick={()=>go(ad.next)}>Continue</button></section>}
        </div></div><div className="review-nav"><button disabled={index===0} onClick={()=>go(prototypeScreens[index-1][0])}>← 上一页</button><span>{scannerPages.includes(screen)?'扫描将自动推进':'点击手机内按钮体验'}</span><button disabled={screen==='networkTest'} onClick={advance}>下一步 →</button></div></main>
        <aside className="review-notes"><p className="note-label">设计说明</p><h2>{current[1]}</h2><p>{current[2]}</p><div className="swatches"><i style={{background:'#ff6b2c'}} title="#FF6B2C"></i><i style={{background:'#fff2ea'}} title="#FFF2EA"></i><i style={{background:'#faf9f7'}} title="#FAF9F7"></i><i style={{background:'#171d27'}} title="#171D27"></i></div>{screen==='networkTest'&&<div className="note-card"><p className="note-label">测速状态预览</p><select className="network-state-select" aria-label="测速状态" defaultValue="auto" onChange={e=>networkFrame.current?.contentWindow.postMessage({type:'network-test-state',state:e.target.value},location.origin)}><option value="auto">自动演示完整流程</option><option value="ready">待测速</option><option value="download">下载测速中</option><option value="upload">上传测速中</option><option value="complete">测试完成</option><option value="failed">测试失败</option><option value="offline">网络不可用</option></select><p className="quiet">进入后自动测速。点击手机内返回键回到首页。</p></div>}<div className="note-card"><p className="note-label">Pixel 8 尺寸基准</p><p className="pixel-canvas-note">内容画布 412 × 915，边框不占内容宽度。桌面按 100% 比例显示，窗口不足时滚动查看。顶部 28、底部 24 为系统栏参考留白。</p><p className="quiet">以默认显示大小和字号为参考；系统栏、导航方式及字体渲染仍需在设备连接后确认。</p></div><div className="note-card"><p className="note-label">预览控制</p><label className="motion-control"><input type="checkbox" checked={continuous} onChange={e=>setContinuous(e.target.checked)}/>自动推进扫描</label><label className="motion-control"><input type="checkbox" checked={playing} onChange={e=>setPlaying(e.target.checked)}/>播放 Lottie</label><label className="motion-control"><input type="checkbox" checked={ads} onChange={e=>setAds(e.target.checked)}/>展示插屏节点</label><label className="motion-control"><input type="checkbox" checked={autoPrompts} onChange={e=>setAutoPrompts(e.target.checked)}/>完成后展示权限引导</label></div>
        {ad&&<div className="note-card"><p className="note-label">当前广告场景</p><p>{ad.slot}<br/>关闭后进入：{prototypeScreens.find(x=>x[0]===ad.next)?.[1]}</p></div>}
        <div className="note-card"><p className="note-label">两条体验路径</p><p>允许存储访问 → 垃圾扫描与清理。<br/>关闭弹窗或不授权 → 电池检查。</p><p className="quiet">设备数据、扫描结果、权限设置与广告均为本地演示。页面沿用现有内容，未接入广告或系统授权。</p></div>{destination&&<div className="note-card">{destination}</div>}<a href="README.md">查看交付与资源说明 ↗</a></aside>
        <div className="mobile-tools"><button onClick={()=>setMobileMenu(!mobileMenu)} aria-expanded={mobileMenu}>预览导航</button>{mobileMenu&&<div className="mobile-menu">{steps}<label className="motion-control"><input type="checkbox" checked={continuous} onChange={e=>setContinuous(e.target.checked)}/>自动推进扫描</label><button className="review-button" onClick={advance}>下一步 →</button><a href="animations.html">Lottie 动画</a></div>}</div>
    </div>
}
ReactDOM.createRoot(document.getElementById('root')).render(<App/>);
