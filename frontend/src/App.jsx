import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Sprout, Plus, Search, CloudSun, Sun, Cloud, CloudRain, ImagePlus, Leaf, Wallet, Boxes, TrendingUp, CalendarDays, ChevronRight, X, Save, Trash2, MapPin, LoaderCircle, Mic, MicOff, FileDown, FileSpreadsheet, Activity, Bell, CheckCircle2, BookOpen, ShoppingBasket, HandHelping, ArrowUp, Shield, RefreshCw } from 'lucide-react';
import farmLogo from './image/WhatsApp Image 2026-10-20 at 2.00.01 PM.jpeg';
import AssistantPanel from './AssistantPanel.jsx';
import ReminderPanel from './ReminderPanel.jsx';
import InformationPanel from './InformationPanel.jsx';
import MarketplacePanel from './MarketplacePanel.jsx';
import ConsultantPanel from './ConsultantPanel.jsx';
import AuthPanel from './AuthPanel.jsx';
import { FollowUpReminderButton, InputLogFields, IssueTrackingFields, OfflineQueueStatus, PlotSeasonFields, QuickEntryTemplates, ReceiptGallery, ReceiptUploader, downloadEntriesCsv, uploadEntryPhoto } from './DailyEntryFeatures.jsx';
import { queueFarmEntry } from './offlineEntrySync.js';
import './brand.css';
import './auth.css';

const API = `${(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')}/api`;
const CROP_GROUPS = {
  'धान्य व तृणधान्ये':['भात / तांदूळ','गहू','ज्वारी','बाजरी','मका','नाचणी / रागी','बार्ली / जव','वरई / भगर','राळा','कोद्रा','कुटकी','सामा','राजगिरा'],
  'कडधान्ये':['तूर','हरभरा','मूग','उडीद','मसूर','मटकी','चवळी','वाटाणा','कुळीथ','वाल','पावटा','राजमा'],
  'तेलबिया':['भुईमूग','सोयाबीन','सूर्यफूल','तीळ','मोहरी','करडई','जवस','एरंडी','नायजर','नारळ','तेलपाम'],
  'भाजीपाला':['बटाटा','कांदा','लसूण','टोमॅटो','वांगी','मिरची','भेंडी','कोबी','फुलकोबी','गाजर','मुळा','बीट','काकडी','दोडका','दुधी भोपळा','कारले','पडवळ','भोपळा','कलिंगड','खरबूज','शेवगा','पालक','मेथी','कोथिंबीर','ढोबळी मिरची','फरसबी','गवार','वालपापडी','स्वीटकॉर्न','मशरूम','पालेभाज्या'],
  'फळपिके':['द्राक्ष','आंबा','डाळिंब','केळी','संत्रे','मोसंबी','लिंबू','पेरू','पपई','चिकू','सीताफळ','फणस','अननस','काजू','आवळा','स्ट्रॉबेरी','जांभूळ','अंजीर'],
  'मसाला व औषधी पिके':['हळद','आले','धणे','जिरे','मेथी','काळी मिरी','वेलची','लवंग','दालचिनी','तमालपत्र','बडीशेप','ओवा','अश्वगंधा'],
  'व्यावसायिक व इतर पिके':['ऊस','कापूस','तंबाखू','ताग','चहा','कॉफी','रबर','बांबू','चारा पीक','फुलशेती','गुलाब','झेंडू','जर्बेरा']
};
const requestId = () => globalThis.crypto?.randomUUID?.() || `entry-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const fresh = () => ({clientRequestId:requestId(),entryDate:new Date().toISOString().slice(0,10),weather:'',pincode:'',cropName:'',cropCustom:'',plotName:'',seasonName:'',plotAreaAcres:'',farmSection:'',workDone:'',materials:'',waterLitres:'',applicationKind:'',applicationName:'',applicationAmount:'',applicationUnit:'',pestDisease:'',issueSeverity:'',intervention:'',followUpDate:'',receiptPhotoUrls:'[]',workerCount:'',laborCost:'',materialsCost:'',otherCost:'',productionKg:'',soldKg:'',saleIncome:'',cropCondition:'',soilNotes:'',tomorrowPlan:'',specialNote:'',photoUrl:'',weatherDetails:''});
const money = n => `₹${Number(n||0).toLocaleString('en-IN')}`;
const day = s => s ? new Date(`${s}T00:00:00`).toLocaleDateString('mr-IN',{day:'numeric',month:'short',year:'numeric'}) : 'दिनांक नाही';
const entryCost = e => Number(e.laborCost||0)+Number(e.materialsCost||0)+Number(e.otherCost||0);
const weatherDetailText = raw => {if(!raw)return '';try{const d=typeof raw==='string'?JSON.parse(raw):raw;return [d.district,d.state,d.warning,d.note].filter(Boolean).join(' · ')}catch{return String(raw)}};
const ANALYTICS={cost:{label:'एकूण खर्च',value:entryCost,format:money},income:{label:'एकूण उत्पन्न',value:e=>Number(e.saleIncome||0),format:money},production:{label:'एकूण उत्पादन',value:e=>Number(e.productionKg||0),format:v=>`${Number(v).toLocaleString('mr-IN',{maximumFractionDigits:1})} किलो`}};
const NAV_ITEMS = {
  entries: { label: 'दैनिक नोंद', icon: CalendarDays },
  reminders: { label: 'स्मरणपत्रे', icon: Bell },
  assistant: { label: 'AI शेती सहाय्यक', icon: Sprout },
  information: { label: 'माहिती', icon: BookOpen },
  marketplace: { label: 'शेतकरी बाजार', icon: ShoppingBasket },
  consultants: { label: 'सेंद्रिय सल्लागार', icon: HandHelping },
  admin: { label: 'व्यवस्थापन', icon: Activity }
};
const ACCOUNT_TYPES = [
  { id: 'farmer', label: 'शेतकरी', initials: 'शे', homeTab: 'entries', tabs: ['entries', 'reminders', 'assistant', 'information', 'consultants'], description: 'दैनिक नोंद, शेतीसाठी AI मदत, स्मरणपत्रे आणि सेंद्रिय सल्लागार' },
  { id: 'seller', label: 'विक्रेता', initials: 'वि', homeTab: 'marketplace', tabs: ['marketplace', 'information'], description: 'शेतमालाची नोंद, उपलब्ध साठा आणि खरेदीदारांच्या विनंत्या सांभाळा' },
  { id: 'buyer', label: 'खरेदीदार', initials: 'ख्रे', homeTab: 'marketplace', tabs: ['marketplace', 'consultants', 'information'], description: 'शेतमाल शोधा, विक्रेत्यांशी संपर्क करा आणि सल्लागार पाहा' },
  { id: 'consultant', label: 'सेंद्रिय सल्लागार', initials: 'स', homeTab: 'consultants', tabs: ['consultants', 'information'], description: 'सल्लागार प्रोफाइल प्रकाशित करा आणि शेती माहिती पहा' },
  { id: 'admin', label: 'प्रशासक', initials: 'प्र', homeTab: 'admin', tabs: ['admin', 'marketplace', 'consultants', 'information'], description: 'सार्वजनिक बाजार व सल्लागार निर्देशिकेचे निरीक्षण' }
];
const ADMIN_ACCOUNT = ACCOUNT_TYPES.find(account => account.id === 'admin');
const PUBLIC_ACCOUNT_TYPES = ACCOUNT_TYPES.filter(account => account.id !== 'admin');
function readAccountType() {
  try {
    const saved = localStorage.getItem('swarnabhoomi-account-role');
    return ACCOUNT_TYPES.some(account => account.id === saved) ? saved : null;
  } catch { return null; }
}

function readSessionUser() {
  try {
    const token = sessionStorage.getItem('swarnabhoomi-user-token');
    const user = JSON.parse(sessionStorage.getItem('swarnabhoomi-user') || 'null');
    return token && ACCOUNT_TYPES.some(account => account.id === user?.role) ? user : null;
  } catch { return null; }
}

function App(){
  const [authUser,setAuthUser]=useState(readSessionUser);
  const [authToken,setAuthToken]=useState(()=>sessionStorage.getItem('swarnabhoomi-user-token')||'');
  const [selectedAccount,setSelectedAccount]=useState(null);
  const [accountType,setAccountType]=useState(()=>readSessionUser()?.role||null),[lastAccountType,setLastAccountType]=useState(readAccountType);
  const [activeTab,setActiveTab]=useState(()=>{const role=readSessionUser()?.role;const account=ACCOUNT_TYPES.find(item=>item.id===role);const requested=new URLSearchParams(window.location.search).get('tab');return account?.tabs.includes(requested)?requested:(account?.homeTab||'entries')});
  const [showBackToTop,setShowBackToTop]=useState(false);
  const mainRef=useRef(null);
  const [entries,setEntries]=useState([]),[form,setForm]=useState(fresh),[open,setOpen]=useState(false),[search,setSearch]=useState(''),[loading,setLoading]=useState(false),[weatherBusy,setWeatherBusy]=useState(false),[weatherError,setWeatherError]=useState(''),[preview,setPreview]=useState(''),[printing,setPrinting]=useState(false),[pdfBusy,setPdfBusy]=useState(false),[printEntries,setPrintEntries]=useState([]),[printPhotos,setPrintPhotos]=useState({}),[activeRecord,setActiveRecord]=useState(null),[analyticsMetric,setAnalyticsMetric]=useState(null),[assistantMessages,setAssistantMessages]=useState([]);
  const currentAccount=ACCOUNT_TYPES.find(account=>account.id===accountType)||ACCOUNT_TYPES[0];
  const logout=useCallback(()=>{
    const token=sessionStorage.getItem('swarnabhoomi-user-token');
    if(token)fetch(`${API}/auth/logout`,{method:'POST',headers:{Authorization:`Bearer ${token}`}}).catch(()=>{});
    sessionStorage.removeItem('swarnabhoomi-user-token');
    sessionStorage.removeItem('swarnabhoomi-user');
    sessionStorage.removeItem('swarnabhoomi-auth');
    sessionStorage.removeItem('swarnabhoomi-marketplace-auth');
    setAuthToken('');setAuthUser(null);setSelectedAccount(null);setAccountType(null);setEntries([]);setAssistantMessages([]);
  },[]);
  const apiFetch=useCallback((url,options={})=>{
    const headers={...(options.headers||{}),...(authToken?{Authorization:`Bearer ${authToken}`}:{})};
    const creatingEntry=String(url)===`${API}/entries`&&(options.method||'GET').toUpperCase()==='POST';
    if(creatingEntry&&!navigator.onLine)return queueFarmEntry(options.body,authUser?.id);
    const request=fetch(url,{...options,headers}).then(response=>{if(response.status===401&&authToken)logout();return response});
    return creatingEntry?request.then(response=>response.status>=500?queueFarmEntry(options.body,authUser?.id):response).catch(error=>{if(error instanceof TypeError)return queueFarmEntry(options.body,authUser?.id);throw error}):request;
  },[authToken,authUser,logout]);
  function chooseAccount(account){setSelectedAccount(account);setLastAccountType(account.id)}
  function enterAccount(result){
    if(!result?.token||!result?.user)return;
    const account=ACCOUNT_TYPES.find(item=>item.id===result.user.role);
    if(!account)return;
    sessionStorage.setItem('swarnabhoomi-user-token',result.token);
    sessionStorage.setItem('swarnabhoomi-user',JSON.stringify(result.user));
    setAuthToken(result.token);setAuthUser(result.user);setAccountType(account.id);setSelectedAccount(null);
    const requested=new URLSearchParams(window.location.search).get('tab');
    setActiveTab(account.tabs.includes(requested)?requested:account.homeTab);
    setAssistantMessages([]);
    try{localStorage.setItem('swarnabhoomi-account-role',account.id)}catch{}
  }
  const load=async()=>{try{const r=await apiFetch(`${API}/entries`);if(r.ok)setEntries(await r.json())}catch{}};
  useEffect(()=>{if(accountType==='farmer'&&authUser)load();else setEntries([])},[authToken,accountType,authUser?.id]);
  useEffect(()=>{const main=mainRef.current;if(!main)return;window.scrollTo({top:0,behavior:window.matchMedia?.('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});main.classList.remove('tab-entering');void main.offsetWidth;main.classList.add('tab-entering')},[activeTab,accountType]);
  useEffect(()=>{const updateVisibility=()=>setShowBackToTop(window.scrollY>480);updateVisibility();window.addEventListener('scroll',updateVisibility,{passive:true});return()=>window.removeEventListener('scroll',updateVisibility)},[]);
  useEffect(()=>{if(!printing)return;const originalTitle=document.title;document.title='Swarnabhoomi-Dainik-Sheti-Nondi';const finish=()=>{document.title=originalTitle;setPrinting(false);setPrintPhotos(current=>{Object.values(current).forEach(URL.revokeObjectURL);return {}})};const timer=window.setTimeout(()=>window.print(),350);window.addEventListener('afterprint',finish);return()=>{window.clearTimeout(timer);window.removeEventListener('afterprint',finish);document.title=originalTitle}},[printing]);
  const totals=useMemo(()=>entries.reduce((a,e)=>({cost:a.cost+Number(e.laborCost||0)+Number(e.materialsCost||0)+Number(e.otherCost||0),income:a.income+Number(e.saleIncome||0),production:a.production+Number(e.productionKg||0)}),{cost:0,income:0,production:0}),[entries]);
  const assistantFarmData=useMemo(()=>{
    const recentEntries=[...entries].sort((a,b)=>String(b.entryDate||'').localeCompare(String(a.entryDate||''))).slice(0,5).map(entry=>{
      const short=value=>String(value||'').trim().slice(0,100);
      return Object.fromEntries(Object.entries({
        date:entry.entryDate,
        cropOrPlot:short(entry.farmSection),
        weather:short(entry.weather),
        workDone:short(entry.workDone),
        materials:short(entry.materials),
        cropCondition:short(entry.cropCondition),
        soilNotes:short(entry.soilNotes),
        nextPlan:short(entry.tomorrowPlan),
        costRs:entryCost(entry),
        productionKg:entry.productionKg,
        soldKg:entry.soldKg,
        salesRs:entry.saleIncome
      }).filter(([,value])=>value!==null&&value!==undefined&&value!==''));
    });
    const summary={
      recordCount:entries.length,
      totalCostRs:Number(totals.cost.toFixed(2)),
      totalSalesRs:Number(totals.income.toFixed(2)),
      totalProductionKg:Number(totals.production.toFixed(2)),
      netRs:Number((totals.income-totals.cost).toFixed(2))
    };
    return {summary,recentEntries,context:JSON.stringify({summary,recentEntries})};
  },[entries,totals]);
  const filtered=entries.filter(e=>`${e.farmSection||''} ${e.workDone||''} ${e.entryDate||''}`.toLowerCase().includes(search.toLowerCase()));
  const filteredTotals=filtered.reduce((a,e)=>({cost:a.cost+entryCost(e),income:a.income+Number(e.saleIncome||0),production:a.production+Number(e.productionKg||0)}),{cost:0,income:0,production:0});
  const set=(k,v)=>setForm(f=>({...f,[k]:v}));
  async function downloadPdf(){if(!filtered.length)return;const exportEntries=[...filtered];setPdfBusy(true);setPrintEntries(exportEntries);try{const photos=await Promise.all(exportEntries.filter(e=>e.photoUrl).map(async e=>{const source=e.photoUrl.startsWith('http://localhost:8080/')?`/api/photos/${e.photoUrl.slice('http://localhost:8080/'.length)}`:e.photoUrl;try{const r=await apiFetch(source);if(!r.ok)return[e.id,null];return[e.id,URL.createObjectURL(await r.blob())]}catch{return[e.id,null]}}));setPrintPhotos(Object.fromEntries(photos.filter(([,url])=>url)))}finally{setPdfBusy(false);setPrinting(true)}}
  async function save(e){e.preventDefault();setLoading(true);try{const {cropName,cropCustom,plotName,...record}=form;const crop=cropName==='इतर'?(cropCustom||'').trim():cropName;record.farmSection=[crop,plotName].filter(Boolean).join(' — ')||null;const r=await apiFetch(`${API}/entries`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(Object.entries(record).map(([k,v])=>[k,v===''?null:v])))});if(!r.ok)throw Error();setForm(fresh());setOpen(false);setPreview('');await load()}catch{alert('नोंद जतन करता आली नाही. Java सर्व्हर सुरू आहे का ते तपासा.')}finally{setLoading(false)}}
  async function getWeather(){setWeatherBusy(true);setWeatherError('');try{const r=await apiFetch(`${API}/weather/${form.pincode}`);const contentType=r.headers.get('content-type')||'';if(!contentType.toLowerCase().includes('json'))throw Error('हवामान सर्व्हरकडून JSON प्रतिसाद मिळाला नाही. Java सर्व्हर सुरू आहे का ते तपासा.');let d;try{d=await r.json()}catch{throw Error('हवामान सर्व्हरकडून वाचता न येणारा प्रतिसाद मिळाला.')}if(!r.ok)throw Error(d.message||(r.status>=500?'IMD हवामान सेवा सध्या उपलब्ध नाही. हवामान हाताने निवडा.':'हवामान माहिती मिळाली नाही.'));set('weatherDetails',JSON.stringify(d));}catch(e){setWeatherError(e instanceof TypeError?'हवामान सर्व्हरशी संपर्क होऊ शकला नाही. Java सर्व्हर सुरू आहे का ते तपासा.':e.message)}finally{setWeatherBusy(false)}}
  async function upload(file){if(!file)return;setPreview(URL.createObjectURL(file));const data=new FormData();data.append('file',file);try{const r=await apiFetch(`${API}/photos`,{method:'POST',body:data});if(!r.ok)throw Error();const d=await r.json();set('photoUrl',d.url)}catch{setWeatherError('फोटो अपलोड झाला नाही. Java सर्व्हर तपासा.')}}
  async function remove(id){if(!confirm('ही नोंद हटवायची आहे का?'))return;await apiFetch(`${API}/entries/${id}`,{method:'DELETE'});load()}
  if(!authUser||!authToken){
    if(!selectedAccount)return <AccountSelection lastAccountType={lastAccountType} onSelect={chooseAccount}/>;
    return <AuthPanel account={selectedAccount} apiBase={API} onAuthenticated={enterAccount} onBack={()=>setSelectedAccount(null)}/>;
  }
  if(!accountType)return <AccountSelection lastAccountType={lastAccountType} onSelect={chooseAccount}/>;
  return <div className="app-shell">
    <aside className="sidebar"><div className="brand"><img className="brand-logo" src={farmLogo} alt="स्वर्णभूमी सेंद्रिय शेतीचे चिन्ह"/></div><div className="nav-label">मुख्य मेनू</div>{currentAccount.tabs.map(tab=>{const item=NAV_ITEMS[tab],Icon=item.icon;return <button type="button" key={tab} className={`nav ${activeTab===tab?'active':''}`} onClick={()=>setActiveTab(tab)} aria-label={item.label} title={item.label}><Icon size={18}/>{item.label}</button>})}<div className="sidebar-tip"><Leaf size={19}/><b>आजची छोटी नोंद,<br/>उद्याची मोठी भरभराट.</b><span>तुमच्या शेताचा प्रत्येक दिवस जपून ठेवा.</span><div className="tip-leaves">✳　✳</div></div><button type="button" className="profile account-profile" onClick={logout} aria-label="लॉगआउट"><div className="avatar">{currentAccount.initials}</div><span><b>{currentAccount.label} खाते</b><small>{authUser.mobileNumber} · लॉगआउट</small></span><ChevronRight size={16}/></button></aside>
    <main ref={mainRef} className="main"><header className="topbar"><div><span className="crumb">स्वर्णभूमी　/　</span><b>{NAV_ITEMS[activeTab]?.label||'स्वर्णभूमी'}</b></div><div className="today"><CalendarDays size={16}/>{new Date().toLocaleDateString('mr-IN',{weekday:'long',day:'numeric',month:'long'})}</div></header>
      {activeTab==='entries'?<>
      <section className="welcome"><div><span className="eyebrow"><span className="pulse"/> तुमच्या शेताची डायरी</span><h1>नमस्कार, शेतकरी मित्रा! <span>🌱</span></h1><p>आज शेतात काय घडलं? तुमची नोंद इथे जतन करा.</p></div><button className="primary" onClick={()=>{setForm(fresh());setOpen(true)}}><Plus size={19}/> आजची नोंद लिहा</button><div className="welcome-art"><span>☀️</span><div>〰️〰️</div></div></section>
      <section className="stats"><Stat icon={<Wallet/>} label="एकूण खर्च" value={<AnimatedMetric value={totals.cost} format="money"/>} tone="peach" analyticsKey="cost" onAnalytics={setAnalyticsMetric}/><Stat icon={<TrendingUp/>} label="एकूण उत्पन्न" value={<AnimatedMetric value={totals.income} format="money"/>} tone="green" analyticsKey="income" onAnalytics={setAnalyticsMetric}/><Stat icon={<Boxes/>} label="एकूण उत्पादन" value={<AnimatedMetric value={totals.production} format="quantity"/>} tone="blue" analyticsKey="production" onAnalytics={setAnalyticsMetric}/><div className="stat-card net"><div className="net-label">निव्वळ शिल्लक</div><strong><AnimatedMetric value={totals.income-totals.cost} format="money"/></strong><span>आतापर्यंतची कमाई</span><div className="net-icon">↗</div></div></section>
      <OfflineQueueStatus ownerId={authUser.id}/>
      <section className="entries-head"><div><h2>शेतातील नोंदी</h2><p>तुमच्या रोजच्या कामांचा लेखाजोखा</p></div><div className="tools"><div className="search"><Search size={17}/><input placeholder="नोंदी शोधा..." value={search} onChange={e=>setSearch(e.target.value)}/><VoiceButton value={search} onChange={setSearch}/></div><button className="outline pdf-export" onClick={downloadPdf} disabled={!filtered.length||pdfBusy} title="दिसणाऱ्या नोंदींची PDF तयार करा">{pdfBusy?<LoaderCircle className="spin" size={15}/>:<FileDown size={15}/>} PDF डाउनलोड</button><button type="button" className="outline csv-export" onClick={() => downloadEntriesCsv(filtered)} disabled={!filtered.length} title="दिसणाऱ्या नोंदी Excel किंवा CSV मध्ये डाउनलोड करा"><FileSpreadsheet size={15}/> CSV डाउनलोड</button><button className="filter">सर्व नोंदी⌄</button></div></section>
      {!filtered.length?<div className="empty"><div className="empty-icon">🌾</div><h3>{entries.length?'नोंद सापडली नाही':'तुमची पहिली नोंद लिहा'}</h3><p>शेतातल्या छोट्या-मोठ्या गोष्टी नोंदवा. त्या पुढच्या हंगामात उपयोगी पडतील.</p><button className="primary" onClick={()=>{setForm(fresh());setOpen(true)}}><Plus size={18}/> नवी नोंद</button></div>:<div className="entry-list">{filtered.map(e=><article className={`entry-card ${activeRecord===e.id?'details-open':''}`} key={e.id} tabIndex={0} aria-describedby={`record-details-${e.id}`} onClick={()=>{if(window.matchMedia?.('(hover: none)').matches)setActiveRecord(current=>current===e.id?null:e.id)}} onKeyDown={event=>{if(event.key==='Escape')setActiveRecord(null)}}><div className="entry-date"><div className="date-icon"><CalendarDays size={18}/></div><div><b>{day(e.entryDate)}</b><small>{e.weather||'हवामान नोंदले नाही'}</small></div></div><div className="entry-crop"><small>पीक / विभाग</small><b>{e.farmSection||'—'}</b></div><div className="entry-work"><small>आजची कामे</small><b>{e.workDone||'—'}</b></div><div className="entry-money"><small>खर्च　·　उत्पन्न</small><b>{money(entryCost(e))} <span>·</span> {money(e.saleIncome)}</b></div><div className="entry-actions">{e.photoUrl&&<ProtectedPhoto src={e.photoUrl} auth={authToken} alt="शेत फोटो"/>}<button className="delete" title="नोंद हटवा" onClick={event=>{event.stopPropagation();remove(e.id)}}><Trash2 size={16}/></button></div><div className="record-details-popup" id={`record-details-${e.id}`} role="tooltip" onClick={event=>event.stopPropagation()}><RecordDetails entry={e} apiFetch={apiFetch} userId={authUser.id}/></div></article>)}</div>}
      {printing&&<section id="print-report" lang="mr"><header className="print-header"><div className="print-brand"><Sprout size={24}/> स्वर्णभूमी</div><h1>दैनिक शेती नोंदवही</h1><p>तयार केल्याची वेळ: {new Date().toLocaleString('mr-IN',{dateStyle:'medium',timeStyle:'short'})}{search&&<>　·　शोध: “{search}”</>}</p></header><div className="print-summary"><div><span>एकूण नोंदी</span><b>{printEntries.length.toLocaleString('mr-IN')}</b></div><div><span>एकूण खर्च</span><b>{money(printEntries.reduce((sum,e)=>sum+entryCost(e),0))}</b></div><div><span>एकूण उत्पन्न</span><b>{money(printEntries.reduce((sum,e)=>sum+Number(e.saleIncome||0),0))}</b></div><div><span>निव्वळ उत्पन्न</span><b>{money(printEntries.reduce((sum,e)=>sum+Number(e.saleIncome||0)-entryCost(e),0))}</b></div></div>{printEntries.map((e,index)=>{const cost=entryCost(e);return <article className="print-entry" key={e.id}><div className="print-entry-heading"><h2>नोंद {String(index+1).toLocaleString('mr-IN')}</h2><b>{day(e.entryDate)}</b></div><div className="print-grid"><PrintField label="पीक / शेताचा विभाग" value={e.farmSection}/><PrintField label="हंगाम" value={e.seasonName}/><PrintField label="क्षेत्र (एकर)" value={e.plotAreaAcres}/><PrintField label="हवामान" value={e.weather}/>{e.pincode&&<PrintField label="पिनकोड" value={e.pincode}/ >}{weatherDetailText(e.weatherDetails)&&<PrintField label="हवामान माहिती" value={weatherDetailText(e.weatherDetails)}/>}<PrintField label="आज केलेली कामे" value={e.workDone}/><PrintField label="वापरलेले साहित्य / खते" value={e.materials}/><PrintField label="पाणी (लिटर)" value={e.waterLitres}/><PrintField label="निविष्ठेचा प्रकार" value={e.applicationKind}/><PrintField label="निविष्ठा" value={e.applicationName}/><PrintField label="प्रमाण" value={e.applicationAmount ? e.applicationAmount + " " + (e.applicationUnit || "") : ""}/><PrintField label="मजूर संख्या" value={e.workerCount}/><PrintField label="मजुरी खर्च" value={money(e.laborCost)}/><PrintField label="खते / साहित्य खर्च" value={money(e.materialsCost)}/><PrintField label="इतर खर्च" value={money(e.otherCost)}/><PrintField label="एकूण खर्च" value={money(cost)}/><PrintField label="उत्पादन" value={e.productionKg?`${Number(e.productionKg).toLocaleString('mr-IN')} किलो`:''}/><PrintField label="विक्री" value={e.soldKg?`${Number(e.soldKg).toLocaleString('mr-IN')} किलो`:''}/><PrintField label="विक्रीतून मिळालेले पैसे" value={money(e.saleIncome)}/><PrintField label="निव्वळ उत्पन्न" value={money(Number(e.saleIncome||0)-cost)}/><PrintField label="पिकाची सद्यस्थिती" value={e.cropCondition}/><PrintField label="माती व सेंद्रिय शेती निरीक्षण" value={e.soilNotes}/><PrintField label="कीड / रोग" value={e.pestDisease}/><PrintField label="तीव्रता" value={e.issueSeverity}/><PrintField label="केलेला उपाय" value={e.intervention}/><PrintField label="पुढील पाहणी" value={e.followUpDate ? day(e.followUpDate) : ""}/><PrintField label="उद्याची नियोजित कामे" value={e.tomorrowPlan}/><PrintField label="विशेष नोंद" value={e.specialNote}/></div>{e.photoUrl&&(printPhotos[e.id]?<figure className="print-photo"><img src={printPhotos[e.id]} alt="शेताचा फोटो"/><figcaption>शेताचा फोटो</figcaption></figure>:<p className="print-photo-missing">शेताचा फोटो जोडला होता; तो PDF मध्ये उपलब्ध झाला नाही.</p>)}</article>})}<footer className="print-footer">स्वर्णभूमी · तुमच्या कष्टांची नोंद</footer></section>}
      <footer>🌿　तुमच्या कष्टांना योग्य नोंद — स्वर्णभूमी</footer></>:activeTab==='reminders'?<ReminderPanel apiFetch={apiFetch} apiBase={API} entries={entries} userId={authUser.id} onUnauthorized={logout}/>:null}
      <div hidden={activeTab!=='assistant'}><AssistantPanel apiFetch={apiFetch} apiBase={API} messages={assistantMessages} setMessages={setAssistantMessages} farmData={assistantFarmData} onUnauthorized={logout}/></div>
      {activeTab==='information'&&<InformationPanel/>}
      {activeTab==='admin'&&<AdminOverview apiBase={API} apiFetch={apiFetch}/>}
      {activeTab==='marketplace'&&<MarketplacePanel accountType={accountType} apiBase={API} auth={authToken} userId={authUser.id} onUnauthorized={logout}/>}
      {activeTab==='consultants'&&<ConsultantPanel accountType={accountType} apiBase={API} authToken={authToken} onUnauthorized={logout}/>}
    </main>
    {showBackToTop&&!open&&!analyticsMetric&&<button type="button" className="back-to-top" onClick={()=>window.scrollTo({top:0,behavior:window.matchMedia?.('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'})} aria-label="पृष्ठाच्या वर जा"><ArrowUp size={17}/><span>वर जा</span></button>}
    {analyticsMetric&&<AnalyticsDialog metric={analyticsMetric} entries={entries} onClose={()=>setAnalyticsMetric(null)}/>}
    {open&&<div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget)setOpen(false)}}><form className="dialog" onSubmit={save}><div className="dialog-head"><div><span className="eyebrow">शेताची रोजची डायरी</span><h2>आजची नोंद लिहा <span>🌱</span></h2></div><button type="button" className="close" onClick={()=>setOpen(false)}><X/></button></div><div className="form-scroll">
      <div className="form-grid"><Field label="दिनांक" required><input type="date" value={form.entryDate} onChange={e=>set('entryDate',e.target.value)} required/></Field><Field label="पीक निवडा"><select value={form.cropName} onChange={e=>set('cropName',e.target.value)}><option value="">पीक निवडा...</option>{Object.entries(CROP_GROUPS).map(([group,crops])=><optgroup label={group} key={group}>{crops.map(crop=><option value={crop} key={crop}>{crop}</option>)}</optgroup>)}<option value="इतर">इतर पीक — नाव लिहा</option></select></Field></div>
      {form.cropName==='इतर'&&<Field label="पिकाचे नाव"><input placeholder="तुमच्या पिकाचे नाव लिहा" value={form.cropCustom} onChange={e=>set('cropCustom',e.target.value)}/></Field>}
      <Field label="शेताचा विभाग / प्लॉट"><input placeholder="उदा. पूर्वेकडचा प्लॉट / गट क्रमांक १२" value={form.plotName} onChange={e=>set('plotName',e.target.value)}/></Field><PlotSeasonFields form={form} set={set}/>
      <section className="form-section"><h3><CloudSun size={18}/> आजचे हवामान</h3><div className="weather-options">{[['ऊन',<Sun/>],['ढगाळ',<Cloud/>],['पाऊस',<CloudRain/>],['ऊन आणि ढगाळ',<CloudSun/>]].map(([w,icon])=><button type="button" key={w} className={`weather-option ${form.weather===w?'selected':''}`} onClick={()=>set('weather',w)}>{icon}{w}</button>)}</div><div className="pin-row"><div className="pin-input"><MapPin size={17}/><input inputMode="numeric" maxLength="6" placeholder="पिनकोड टाका (६ अंक)" value={form.pincode} onChange={e=>set('pincode',e.target.value.replace(/\D/g,''))}/></div><button type="button" className="outline" onClick={getWeather} disabled={weatherBusy||form.pincode.length!==6}>{weatherBusy?<LoaderCircle className="spin" size={16}/>:<CloudSun size={16}/>} हवामान मिळवा</button></div>{form.weatherDetails&&<div className="weather-result"><b><MapPin size={14}/> {JSON.parse(form.weatherDetails).district}, {JSON.parse(form.weatherDetails).state}</b><span>{JSON.parse(form.weatherDetails).warning}</span><small>{JSON.parse(form.weatherDetails).note}</small><small>स्रोत: <a href={JSON.parse(form.weatherDetails).sourceUrl} target="_blank" rel="noreferrer">IMD</a></small></div>}{weatherError&&<div className="error">{weatherError}</div>}</section>
      <QuickEntryTemplates onApply={template=>setForm(f=>({...f,...template}))}/>
      <Field label="आज केलेली कामे"><textarea rows="2" placeholder="उदा. तण काढले, पाणी दिले..." value={form.workDone} onChange={e=>set('workDone',e.target.value)}/></Field><Field label="वापरलेले साहित्य / खते"><textarea rows="2" placeholder="उदा. शेणखत २ पोती, ठिबक दुरुस्ती..." value={form.materials} onChange={e=>set('materials',e.target.value)}/></Field>
      <InputLogFields form={form} set={set}/>
      <section className="form-section"><h3><Wallet size={18}/> मजुरी व खर्च</h3><div className="form-grid three"><Field label="मजूर संख्या"><input type="number" min="0" value={form.workerCount} onChange={e=>set('workerCount',e.target.value)} placeholder="०"/></Field><Field label="मजुरी खर्च (₹)"><input type="number" min="0" value={form.laborCost} onChange={e=>set('laborCost',e.target.value)} placeholder="०"/></Field><Field label="खते / साहित्य खर्च (₹)"><input type="number" min="0" value={form.materialsCost} onChange={e=>set('materialsCost',e.target.value)} placeholder="०"/></Field><Field label="इतर खर्च (₹)"><input type="number" min="0" value={form.otherCost} onChange={e=>set('otherCost',e.target.value)} placeholder="०"/></Field></div></section>
      <section className="form-section"><h3><Boxes size={18}/> उत्पादन व विक्री</h3><div className="form-grid three"><Field label="उत्पादन (किलो)"><input type="number" min="0" value={form.productionKg} onChange={e=>set('productionKg',e.target.value)} placeholder="०"/></Field><Field label="विक्री (किलो)"><input type="number" min="0" value={form.soldKg} onChange={e=>set('soldKg',e.target.value)} placeholder="०"/></Field><Field label="विक्रीतून पैसे (₹)"><input type="number" min="0" value={form.saleIncome} onChange={e=>set('saleIncome',e.target.value)} placeholder="०"/></Field></div><div className="calculation">आजचा खर्च <b>{money(Number(form.laborCost||0)+Number(form.materialsCost||0)+Number(form.otherCost||0))}</b><span>•</span> निव्वळ उत्पन्न <b>{money(Number(form.saleIncome||0)-Number(form.laborCost||0)-Number(form.materialsCost||0)-Number(form.otherCost||0))}</b></div></section>
      <div className="form-grid"><Field label="पिकाची सद्यस्थिती"><textarea rows="2" value={form.cropCondition} onChange={e=>set('cropCondition',e.target.value)} placeholder="पिकाची वाढ, कीड, फुलोरा..."/></Field><Field label="माती व सेंद्रिय शेती निरीक्षण"><textarea rows="2" value={form.soilNotes} onChange={e=>set('soilNotes',e.target.value)} placeholder="मातीतील ओलावा, गांडूळखत..."/></Field></div><IssueTrackingFields form={form} set={set}/><Field label="उद्याची नियोजित कामे"><input value={form.tomorrowPlan} onChange={e=>set('tomorrowPlan',e.target.value)} placeholder="उद्या कोणते काम करायचे?"/></Field>
      <Field label="विशेष नोंद"><textarea rows="2" value={form.specialNote} onChange={e=>set('specialNote',e.target.value)} placeholder="काही विशेष सांगायचे असल्यास..."/></Field>
      <div className="photo-upload"><label><input type="file" accept="image/*" capture="environment" onChange={e=>upload(e.target.files?.[0])}/><ImagePlus size={22}/><b>शेताचा फोटो जोडा</b><small>एक फोटो · कमाल १० MB</small></label>{preview&&<img src={preview} alt="निवडलेला फोटो"/>}</div>
      <ReceiptUploader value={form.receiptPhotoUrls} onChange={value=>set('receiptPhotoUrls',value)} uploadFile={file=>uploadEntryPhoto(apiFetch,file)} apiFetch={apiFetch} onError={()=>setWeatherError('पावती फोटो अपलोड झाला नाही. इंटरनेट जोडून पुन्हा प्रयत्न करा.')}/>
    </div><div className="dialog-footer"><span><Leaf size={16}/> तुमची नोंद सुरक्षित जतन केली जाईल</span><button className="primary" disabled={loading}>{loading?<LoaderCircle className="spin" size={17}/>:<Save size={17}/>} नोंद जतन करा</button></div></form></div>}
  </div>
}
function AnalyticsDialog({metric,entries,onClose}){
  const config=ANALYTICS[metric]||ANALYTICS.cost;
  const ordered=[...entries].sort((a,b)=>(a.entryDate||'').localeCompare(b.entryDate||''));
  const chart=ordered.slice(-8).map(entry=>({entry,value:Math.max(0,Number(config.value(entry)||0))}));
  const total=entries.reduce((sum,entry)=>sum+Number(config.value(entry)||0),0);
  const average=entries.length?total/entries.length:0;
  const peak=entries.reduce((best,entry)=>!best||Number(config.value(entry)||0)>Number(config.value(best)||0)?entry:best,null);
  const peakValue=peak?Number(config.value(peak)||0):0;
  const averageText=metric==='production'?`${average.toLocaleString('mr-IN',{maximumFractionDigits:1})} किलो`:money(Math.round(average));
  const compact=value=>metric==='production'?Intl.NumberFormat('mr-IN',{notation:'compact',maximumFractionDigits:1}).format(value):`₹${Intl.NumberFormat('mr-IN',{notation:'compact',maximumFractionDigits:1}).format(value)}`;
  const plot={left:68,right:18,top:18,bottom:45,width:560,height:250};
  const plotWidth=plot.width-plot.left-plot.right,plotHeight=plot.height-plot.top-plot.bottom;
  const axisMax=Math.max(1,...chart.map(point=>point.value))*1.15;
  const graphPoints=chart.map(({entry,value},index)=>({entry,value,x:chart.length===1?plot.left+plotWidth/2:plot.left+index*plotWidth/(chart.length-1),y:plot.top+plotHeight-(value/axisMax)*plotHeight}));
  const linePath=graphPoints.map(({x,y},index)=>`${index?'L':'M'} ${x} ${y}`).join(' ');
  const areaPath=graphPoints.length>1?`M ${graphPoints[0].x} ${plot.top+plotHeight} ${graphPoints.map(({x,y})=>`L ${x} ${y}`).join(' ')} L ${graphPoints.at(-1).x} ${plot.top+plotHeight} Z`:'';
  const ticks=Array.from({length:5},(_,index)=>({value:axisMax*index/4,y:plot.top+plotHeight-plotHeight*index/4}));
  useEffect(()=>{const onKeyDown=event=>{if(event.key==='Escape')onClose()};window.addEventListener('keydown',onKeyDown);return()=>window.removeEventListener('keydown',onKeyDown)},[onClose]);
  return <div className="analytics-overlay" onMouseDown={event=>{if(event.target===event.currentTarget)onClose()}}><section className={`analytics-dialog analytics-${metric}`} role="dialog" aria-modal="true" aria-labelledby="analytics-title"><header className="analytics-dialog-head"><div className="analytics-heading-icon"><Activity size={19}/></div><div><span>नोंदींचे विश्लेषण</span><h2 id="analytics-title">{config.label}</h2></div><button className="close" onClick={onClose} aria-label="विश्लेषण बंद करा"><X size={19}/></button></header><div className="analytics-kpis"><div><span>एकूण</span><strong>{config.format(total)}</strong></div><div><span>प्रति नोंद सरासरी</span><strong>{averageText}</strong></div><div><span>सर्वाधिक नोंद</span><strong>{peak?config.format(peakValue):'—'}</strong><small>{peak?day(peak.entryDate):'नोंदी नाहीत'}</small></div></div><div className="analytics-chart-section"><div className="analytics-chart-title"><h3>अलीकडील नोंदी</h3><span>शेवटच्या {chart.length.toLocaleString('mr-IN')} नोंदी</span></div>{chart.length?<div className="analytics-chart-wrap"><svg className="analytics-chart" viewBox={`0 0 ${plot.width} ${plot.height}`} role="img" aria-label={`${config.label} — अलीकडील नोंदींचा आलेख`}><defs><linearGradient id={`analytics-fill-${metric}`} x1="0" x2="0" y1="0" y2="1"><stop offset="0%" className="analytics-gradient-top"/><stop offset="100%" className="analytics-gradient-bottom"/></linearGradient></defs>{ticks.map(({value,y},index)=><g key={index}><line className="analytics-grid-line" x1={plot.left} x2={plot.width-plot.right} y1={y} y2={y}/><text className="analytics-axis-label" x={plot.left-9} y={y+3} textAnchor="end">{compact(value)}</text></g>)}<line className="analytics-axis-line" x1={plot.left} x2={plot.left} y1={plot.top} y2={plot.top+plotHeight}/><line className="analytics-axis-line" x1={plot.left} x2={plot.width-plot.right} y1={plot.top+plotHeight} y2={plot.top+plotHeight}/>{areaPath&&<path className="analytics-area" d={areaPath} fill={`url(#analytics-fill-${metric})`}/ >}{graphPoints.length>1&&<path className="analytics-line" d={linePath}/ >}{graphPoints.map(({entry,value,x,y})=><g className="analytics-point-group" key={entry.id||entry.entryDate}><title>{`${day(entry.entryDate)}: ${config.format(value)}`}</title><circle className="analytics-point-halo" cx={x} cy={y} r="8"/><circle className="analytics-point" cx={x} cy={y} r="4"/><text className="analytics-x-label" x={x} y={plot.height-13} textAnchor="middle">{entry.entryDate?new Date(`${entry.entryDate}T00:00:00`).toLocaleDateString('mr-IN',{day:'numeric',month:'short'}):'—'}</text></g>)}</svg><div className="analytics-chart-legend"><span className="analytics-legend-line"/><b>{config.label}</b><small>रक्कम / किलो</small></div></div>:<div className="analytics-empty">विश्लेषणासाठी नोंदी उपलब्ध नाहीत.</div>}</div><p className="analytics-note">जतन केलेल्या {entries.length.toLocaleString('mr-IN')} शेती नोंदींवर आधारित</p></section></div>
}
function PrintField({label,value}){return <div className="print-field"><dt>{label}</dt><dd>{value===null||value===undefined||value===''?'नोंद नाही':value}</dd></div>}
function RecordDetails({entry:e,apiFetch,userId}){
  const fields=[
    ['दिनांक',day(e.entryDate)],['पीक / शेताचा विभाग',e.farmSection],['हंगाम',e.seasonName],
    ['प्लॉटचे क्षेत्र',e.plotAreaAcres?e.plotAreaAcres+' एकर':null],['हवामान',e.weather],['पिनकोड',e.pincode],
    ['हवामान माहिती',weatherDetailText(e.weatherDetails)],['आज केलेली कामे',e.workDone],['वापरलेले साहित्य / खते',e.materials],
    ['पाणी',e.waterLitres?e.waterLitres+' लिटर':null],['निविष्ठेचा प्रकार',e.applicationKind],['निविष्ठेचे नाव',e.applicationName],
    ['निविष्ठेचे प्रमाण',e.applicationAmount?e.applicationAmount+' '+(e.applicationUnit||''):null],
    ['मजूर संख्या',e.workerCount===null||e.workerCount===undefined||e.workerCount===''?'':Number(e.workerCount).toLocaleString('mr-IN')+' मजूर'],
    ['मजुरी खर्च',e.laborCost===null||e.laborCost===undefined||e.laborCost===''?'':money(e.laborCost)],
    ['खते / साहित्य खर्च',e.materialsCost===null||e.materialsCost===undefined||e.materialsCost===''?'':money(e.materialsCost)],
    ['इतर खर्च',e.otherCost===null||e.otherCost===undefined||e.otherCost===''?'':money(e.otherCost)],
    ['एकूण खर्च',money(entryCost(e))],['उत्पादन',e.productionKg===null||e.productionKg===undefined||e.productionKg===''?'':Number(e.productionKg).toLocaleString('mr-IN')+' किलो'],
    ['विक्री',e.soldKg===null||e.soldKg===undefined||e.soldKg===''?'':Number(e.soldKg).toLocaleString('mr-IN')+' किलो'],
    ['विक्रीतून मिळालेले पैसे',e.saleIncome===null||e.saleIncome===undefined||e.saleIncome===''?'':money(e.saleIncome)],
    ['निव्वळ उत्पन्न',money(Number(e.saleIncome||0)-entryCost(e))],['पिकाची सद्यस्थिती',e.cropCondition],
    ['माती व सेंद्रिय शेती निरीक्षण',e.soilNotes],['कीड / रोग',e.pestDisease],['तीव्रता',e.issueSeverity],
    ['केलेला उपाय',e.intervention],['पुढील पाहणी',day(e.followUpDate)],['उद्याची नियोजित कामे',e.tomorrowPlan],['विशेष नोंद',e.specialNote]
  ];
  return <><div className="record-popover-title"><strong>नोंदीचा संपूर्ण तपशील</strong><span>{day(e.entryDate)}</span></div><dl className="record-detail-list">{fields.filter(([,value])=>value!==null&&value!==undefined&&value!=='').map(([label,value])=><div className="record-detail" key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><ReceiptGallery value={e.receiptPhotoUrls} apiFetch={apiFetch}/><FollowUpReminderButton entry={e} apiFetch={apiFetch} userId={userId}/>{e.photoUrl&&<div className="record-photo-note">📷 शेताचा फोटो जोडलेला आहे</div>}</>;
}
function ProtectedPhoto({src,auth,alt}){
  const [image,setImage]=useState(''),[imageType,setImageType]=useState('image/jpeg'),[expanded,setExpanded]=useState(false);
  useEffect(()=>{let active=true,objectUrl='';const source=src.startsWith('http://localhost:8080/')?`/api/photos/${src.slice('http://localhost:8080/'.length)}`:src;const headers=auth?{Authorization:`Bearer ${auth}`}:{ };fetch(source,{headers}).then(r=>{if(!r.ok)throw Error();return r.blob()}).then(blob=>{if(active){objectUrl=URL.createObjectURL(blob);setImage(objectUrl);setImageType(blob.type||'image/jpeg')}}).catch(()=>{});return()=>{active=false;if(objectUrl)URL.revokeObjectURL(objectUrl)}},[src,auth]);
  useEffect(()=>{if(!expanded)return;const onKeyDown=event=>{if(event.key==='Escape')setExpanded(false)};window.addEventListener('keydown',onKeyDown);return()=>window.removeEventListener('keydown',onKeyDown)},[expanded]);
  const extension=imageType.includes('jpeg')||imageType.includes('jpg')?'jpg':imageType.split('/')[1]?.split('+')[0]||'jpg';
  if(!image)return null;
  return <><button type="button" className="photo-thumb-trigger" title="मोठा फोटो पाहण्यासाठी hover करा किंवा टॅप करा" aria-label="शेताचा फोटो मोठा पाहा" aria-haspopup="dialog" onMouseEnter={()=>setExpanded(true)} onClick={event=>{event.stopPropagation();setExpanded(true)}}><img src={image} alt={alt}/></button>{expanded&&<div className="image-lightbox" onMouseDown={event=>{if(event.target===event.currentTarget)setExpanded(false)}}><section className="image-lightbox-dialog" role="dialog" aria-modal="true" aria-label="शेताचा मोठा फोटो"><header className="image-lightbox-header"><strong>{alt||'शेताचा फोटो'}</strong><div><a className="image-download" href={image} download={`swarnabhoomi-photo.${extension}`}><FileDown size={15}/> फोटो डाउनलोड</a><button type="button" className="close" onClick={()=>setExpanded(false)} aria-label="फोटो बंद करा"><X size={19}/></button></div></header><img className="image-lightbox-full" src={image} alt={alt}/></section></div>}</>;
}
function AccountSelection({lastAccountType,onSelect}){
  return <main className="account-select-screen" lang="mr"><section className="account-select-content"><header className="account-select-header"><img src={farmLogo} alt="स्वर्णभूमी"/><div><span>स्वर्णभूमी · शेतकरी समुदाय</span><h1>कोणत्या खात्यातून पुढे जायचे?</h1><p>खाते निवडा. पुढे मोबाईल क्रमांक आणि पासवर्डने प्रवेश करा किंवा नवीन खाते नोंदवा.</p></div></header><div className="account-role-grid">{PUBLIC_ACCOUNT_TYPES.map(account=><button type="button" key={account.id} className={`account-role-card ${lastAccountType===account.id?'previous':''}`} onClick={()=>onSelect(account)}><span className={`account-role-avatar role-${account.id}`}>{account.initials}</span><span className="account-role-copy"><b>{account.label}</b>{lastAccountType===account.id&&<small className="account-last-used">मागील निवड</small>}<small>{account.description}</small><small><strong>प्रमुख टॅब:</strong> {account.tabs.map(tab=>NAV_ITEMS[tab].label).join(' · ')}</small></span></button>)}</div><p className="account-select-note">प्रत्येक खाते प्रकारासाठी मोबाईल क्रमांक आणि पासवर्ड स्वतंत्र आहेत. तुमच्या शेतीच्या नोंदी फक्त त्याच खात्यात लॉगिन केल्यावर दिसतील.</p></section><button type="button" className="admin-entry-symbol" onClick={()=>onSelect(ADMIN_ACCOUNT)} aria-label="प्रशासक लॉगिन" title="प्रशासक लॉगिन"><Shield size={17}/><span>प्रशासक</span></button></main>;
}

const ADMIN_SECTIONS = {
  buyers: { label: 'खरेदीदार', endpoint: '/users?role=buyer', columns: ['मोबाईल क्रमांक', 'नोंदणी दिनांक', 'खाते क्रमांक'] },
  sellers: { label: 'विक्रेते', endpoint: '/users?role=seller', columns: ['मोबाईल क्रमांक', 'नोंदणी दिनांक', 'खाते क्रमांक'] },
  entries: { label: 'शेतकऱ्यांच्या दैनिक नोंदी', endpoint: '/daily-entries', columns: ['दिनांक', 'शेतकरी मोबाईल', 'पीक / विभाग', 'आजची कामे', 'उत्पादन', 'तपशील'] },
  listings: { label: 'विक्रीच्या नोंदी', endpoint: '/listings', columns: ['माल', 'विक्रेता', 'प्रमाण', 'भाव', 'स्थिती', 'दिनांक'] },
  requests: { label: 'खरेदी विनंत्या', endpoint: '/purchase-requests', columns: ['खरेदीदार', 'माल / विक्रेता', 'प्रमाण', 'स्थिती', 'दिनांक'] }
};

const adminDate = value => {
  if(!value)return '—';
  const dateOnly=/^\d{4}-\d{2}-\d{2}$/.test(String(value));
  const date=new Date(dateOnly?`${value}T00:00:00`:value);
  return Number.isNaN(date.getTime())?String(value):date.toLocaleString('mr-IN',dateOnly?{day:'numeric',month:'short',year:'numeric'}:{day:'numeric',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'});
};
const adminMoney = value => value==null?'—':`₹${Number(value).toLocaleString('en-IN',{maximumFractionDigits:2})}`;

function downloadAdminCsv(section,rows){
  const columns={
    buyers:[['मोबाईल क्रमांक','mobileNumber'],['नोंदणी दिनांक','createdAt'],['खाते क्रमांक','id']],
    sellers:[['मोबाईल क्रमांक','mobileNumber'],['नोंदणी दिनांक','createdAt'],['खाते क्रमांक','id']],
    entries:[['शेतकरी मोबाईल','farmerMobileNumber'],['दिनांक','entryDate'],['पीक / विभाग','farmSection'],['हंगाम','seasonName'],['क्षेत्र एकर','plotAreaAcres'],['आजची कामे','workDone'],['साहित्य','materials'],['हवामान','weather'],['पिनकोड','pincode'],['मजूर','workerCount'],['मजुरी खर्च','laborCost'],['साहित्य खर्च','materialsCost'],['इतर खर्च','otherCost'],['पाणी लिटर','waterLitres'],['निविष्ठा प्रकार','applicationKind'],['निविष्ठा','applicationName'],['निविष्ठा प्रमाण','applicationAmount'],['निविष्ठा एकक','applicationUnit'],['उत्पादन किलो','productionKg'],['विक्री किलो','soldKg'],['विक्री उत्पन्न','saleIncome'],['पीक स्थिती','cropCondition'],['माती नोंदी','soilNotes'],['कीड / रोग','pestDisease'],['तीव्रता','issueSeverity'],['उपाय','intervention'],['पुढील पाहणी','followUpDate'],['उद्याची योजना','tomorrowPlan'],['विशेष नोंद','specialNote'],['हवामान तपशील','weatherDetails'],['फोटो','photoUrl'],['पावती फोटो','receiptPhotoUrls']],
    listings:[['माल','productName'],['विक्रेता','sellerName'],['विक्रेता मोबाईल','accountMobileNumber'],['प्रमाण','quantity'],['एकक','unit'],['भाव','pricePerUnit'],['उपलब्ध','available'],['नोंदणी दिनांक','createdAt']],
    requests:[['खरेदीदार','buyerName'],['खरेदीदार मोबाईल','buyerPhone'],['माल','productName'],['विक्रेता','sellerName'],['प्रमाण','quantity'],['एकक','unit'],['स्थिती','status'],['दिनांक','createdAt']]
  }[section]||[];
  const cell=value=>{let text=String(value??'').replace(/[\r\n]+/g,' ');if(/^[=+\-@]/.test(text))text=`'${text}`;return `"${text.replace(/"/g,'""')}"`};
  const content='\uFEFF'+[columns.map(([label])=>cell(label)).join(','),...rows.map(row=>columns.map(([,key])=>cell(row[key])).join(','))].join('\r\n');
  const url=URL.createObjectURL(new Blob([content],{type:'text/csv;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download=`swarnabhoomi-admin-${section}.csv`;link.click();URL.revokeObjectURL(url);
}

function AdminOverview({apiBase,apiFetch}){
  const [summary,setSummary]=useState(null),[section,setSection]=useState('buyers'),[page,setPage]=useState(null),[pageIndex,setPageIndex]=useState(0),[loading,setLoading]=useState(true),[summaryLoading,setSummaryLoading]=useState(true),[error,setError]=useState(''),[search,setSearch]=useState(''),[reload,setReload]=useState(0);
  useEffect(()=>{let active=true;setSummaryLoading(true);apiFetch(`${apiBase}/admin/overview`).then(async response=>{if(!response.ok)throw new Error('प्रशासक सारांश माहिती मिळाली नाही.');return response.json()}).then(value=>{if(active){setSummary(value);setError('')}}).catch(exception=>{if(active)setError(exception.message||'सारांश माहिती मिळाली नाही.')}).finally(()=>{if(active)setSummaryLoading(false)});return()=>{active=false}},[apiBase,apiFetch,reload]);
  useEffect(()=>{let active=true;setLoading(true);const config=ADMIN_SECTIONS[section];apiFetch(`${apiBase}/admin${config.endpoint}${config.endpoint.includes('?')?'&':'?'}page=${pageIndex}&size=50`).then(async response=>{if(!response.ok)throw new Error('नोंदी मिळाल्या नाहीत. पुन्हा प्रयत्न करा.');return response.json()}).then(value=>{if(active){setPage(value);setError('')}}).catch(exception=>{if(active)setError(exception.message||'नोंदी मिळाल्या नाहीत.')}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[apiBase,apiFetch,section,pageIndex,reload]);
  const rows=page?.content||[];
  const filteredRows=rows.filter(row=>Object.values(row).join(' ').toLocaleLowerCase('mr-IN').includes(search.toLocaleLowerCase('mr-IN')));
  function selectSection(next){setSection(next);setPageIndex(0);setSearch('')}
  function renderRows(){
    if(section==='buyers'||section==='sellers')return filteredRows.map(row=><tr key={row.id}><td><b>{row.mobileNumber}</b></td><td>{adminDate(row.createdAt)}</td><td>#{Number(row.id).toLocaleString('mr-IN')}</td></tr>);
    if(section==='entries')return filteredRows.map(row=><tr key={row.id}><td>{adminDate(row.entryDate)}</td><td><b>{row.farmerMobileNumber||'—'}</b></td><td>{row.farmSection||'—'}</td><td className="admin-work-cell">{row.workDone||'—'}</td><td>{row.productionKg==null?'—':`${Number(row.productionKg).toLocaleString('mr-IN')} किलो`}</td><td><details className="admin-row-details"><summary>संपूर्ण नोंद</summary><dl>{[['हंगाम',row.seasonName],['क्षेत्र (एकर)',row.plotAreaAcres],['हवामान',row.weather],['हवामान तपशील',row.weatherDetails],['पिनकोड',row.pincode],['साहित्य',row.materials],['पाणी (लिटर)',row.waterLitres],['निविष्ठेचा प्रकार',row.applicationKind],['निविष्ठा',row.applicationName],['निविष्ठेचे प्रमाण',row.applicationAmount==null?'':`${row.applicationAmount} ${row.applicationUnit||''}`],['मजूर',row.workerCount],['मजुरी खर्च',adminMoney(row.laborCost)],['साहित्य खर्च',adminMoney(row.materialsCost)],['इतर खर्च',adminMoney(row.otherCost)],['विक्री',row.soldKg==null?'—':`${row.soldKg} किलो`],['विक्री उत्पन्न',adminMoney(row.saleIncome)],['पीक स्थिती',row.cropCondition],['मातीची नोंद',row.soilNotes],['कीड / रोग',row.pestDisease],['तीव्रता',row.issueSeverity],['केलेला उपाय',row.intervention],['पुढील पाहणी',adminDate(row.followUpDate)],['उद्याची योजना',row.tomorrowPlan],['विशेष नोंद',row.specialNote],['शेताचा फोटो',row.photoUrl],['पावती फोटो',row.receiptPhotoUrls]].filter(([,value])=>value!=null&&value!=='').map(([label,value])=><React.Fragment key={label}><dt>{label}</dt><dd>{value}</dd></React.Fragment>)}</dl></details></td></tr>);
    if(section==='listings')return filteredRows.map(row=><tr key={row.id}><td><b>{row.productName}</b><small>{row.cropType} · {row.category}</small></td><td>{row.sellerName}<small>{row.accountMobileNumber||row.sellerPhone}</small></td><td>{Number(row.quantity).toLocaleString('mr-IN')} {row.unit}</td><td>{adminMoney(row.pricePerUnit)}</td><td><span className={`admin-status ${row.available?'active':'inactive'}`}>{row.available?'उपलब्ध':'विकले'}</span></td><td>{adminDate(row.createdAt)}</td></tr>);
    return filteredRows.map(row=><tr key={row.id}><td><b>{row.buyerName}</b><small>{row.buyerPhone}</small></td><td>{row.productName||'माल नोंद उपलब्ध नाही'}<small>{row.sellerName||'विक्रेता नोंद उपलब्ध नाही'}</small></td><td>{Number(row.quantity).toLocaleString('mr-IN')} {row.unit}</td><td><span className={`admin-status ${String(row.status||'').toLowerCase()}`}>{row.status}</span></td><td>{adminDate(row.createdAt)}</td></tr>);
  }
  const statCards=[['buyers','नोंदणीकृत खरेदीदार',summary?.buyers],['sellers','नोंदणीकृत विक्रेते',summary?.sellers],['entries','शेतकऱ्यांच्या दैनिक नोंदी',summary?.dailyEntries],['listings','एकूण विक्री नोंदी',summary?.listings],['requests','खरेदी विनंत्या',summary?.purchaseRequests],['requests','प्रलंबित खरेदी विनंत्या',summary?.pendingRequests],['entries','आजच्या दैनिक नोंदी',summary?.todayEntries]];
  return <section className="account-admin-page" lang="mr"><div className="account-admin-hero"><span><Shield size={15}/> स्वर्णभूमी · सुरक्षित प्रशासक विभाग</span><h1>व्यवस्थापन आढावा</h1><p>खाते नोंदणी, बाजार व्यवहार आणि शेतकऱ्यांच्या दैनिक नोंदी एका ठिकाणी पाहा.</p><button type="button" className="admin-refresh" onClick={()=>setReload(value=>value+1)} disabled={summaryLoading||loading}><RefreshCw size={15} className={summaryLoading||loading?'spin':''}/> माहिती रिफ्रेश करा</button></div>
    {error&&<p className="account-admin-error" role="status">{error}</p>}
    <div className="account-admin-stats">{statCards.map(([key,label,value],index)=><button type="button" key={`${key}-${index}`} className={`admin-stat-card ${section===key?'selected':''}`} onClick={()=>selectSection(key)}><span>{label}</span><strong>{summaryLoading?'…':Number(value||0).toLocaleString('mr-IN')}</strong><small>{index===6?'नोंदी आज':index===2?'सर्व शेतकरी खात्यांतून':index===5?'उत्तराच्या प्रतीक्षेत':''}</small></button>)}</div>
    <section className="admin-records"><div className="admin-records-head"><div><span className="eyebrow">संपूर्ण नोंदी</span><h2>{ADMIN_SECTIONS[section].label}</h2><p>{page?`${Number(page.totalElements||0).toLocaleString('mr-IN')} नोंदी · पान ${Number(page.number||0)+1} / ${Math.max(1,Number(page.totalPages||1)).toLocaleString('mr-IN')}`:'नोंदी लोड होत आहेत…'}</p></div><div className="admin-record-tools"><label className="admin-search"><Search size={16}/><input value={search} onChange={event=>setSearch(event.target.value)} placeholder="या पानावर शोधा"/></label><button type="button" className="outline" onClick={()=>downloadAdminCsv(section,filteredRows)} disabled={!filteredRows.length}><FileSpreadsheet size={15}/> CSV</button></div></div>
      <nav className="admin-record-tabs" aria-label="प्रशासक नोंदी">{Object.entries(ADMIN_SECTIONS).map(([key,value])=><button type="button" key={key} className={section===key?'active':''} onClick={()=>selectSection(key)}>{value.label}</button>)}</nav>
      <div className="admin-table-wrap"><table className="admin-table"><thead><tr>{ADMIN_SECTIONS[section].columns.map(column=><th key={column}>{column}</th>)}</tr></thead><tbody>{!loading&&renderRows()}</tbody></table>{loading&&<div className="admin-loading"><LoaderCircle size={20} className="spin"/> नोंदी लोड होत आहेत…</div>}{!loading&&!filteredRows.length&&<div className="admin-empty">{rows.length?'या शोधासाठी नोंद नाही.':'या विभागात अद्याप नोंद नाही.'}</div>}</div>
      <div className="admin-pagination"><span>{page?`${Number(page.totalElements||0).toLocaleString('mr-IN')} पैकी ${rows.length?((pageIndex*50)+1).toLocaleString('mr-IN'):'0'}–${Math.min((pageIndex+1)*50,Number(page.totalElements||0)).toLocaleString('mr-IN')}`:'—'}</span><div><button type="button" className="outline" disabled={loading||pageIndex<=0} onClick={()=>setPageIndex(value=>Math.max(0,value-1))}>मागील</button><button type="button" className="outline" disabled={loading||!page||pageIndex+1>=page.totalPages} onClick={()=>setPageIndex(value=>value+1)}>पुढील</button></div></div>
    </section></section>;
}

function Stat({icon,label,value,tone,analyticsKey,onAnalytics}){return <div className="stat-card"><div className={`stat-icon ${tone} ${analyticsKey?'stat-icon-analytics':''}`}>{icon}{analyticsKey&&<button type="button" className="analytics-symbol" title={`${label}चे विश्लेषण`} aria-label={`${label}चे विश्लेषण उघडा`} onClick={()=>onAnalytics(analyticsKey)}><Activity size={11}/></button>}</div><div><small>{label}</small><strong>{value}</strong></div></div>}
function AnimatedMetric({value,format}){const [display,setDisplay]=useState(0);const current=useRef(0);useEffect(()=>{const target=Number(value)||0,start=current.current,started=performance.now(),duration=850;let frame;const prefersReducedMotion=window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;if(prefersReducedMotion){current.current=target;setDisplay(target);return}const tick=now=>{const progress=Math.min((now-started)/duration,1),eased=1-Math.pow(1-progress,3),next=start+(target-start)*eased;current.current=next;setDisplay(next);if(progress<1)frame=requestAnimationFrame(tick);else current.current=target};frame=requestAnimationFrame(tick);return()=>cancelAnimationFrame(frame)},[value]);return format==='money'?money(Math.round(display)):`${display.toLocaleString('mr-IN')} किलो`}
function Field({label,required,children}){
  const id=React.useId();
  const voiceEnabled=React.isValidElement(children)&&(children.type==='textarea'||(children.type==='input'&&(!children.props.type||children.props.type==='text')));
  const input=React.isValidElement(children)?React.cloneElement(children,{id:children.props.id||id}):children;
  const onVoiceChange=transcript=>children.props.onChange?.({target:{value:transcript}});
  return <div className="field"><label className="field-label" htmlFor={id}>{label}{required&&<i> *</i>}</label>{voiceEnabled?<div className="voice-field">{input}<VoiceButton value={children.props.value||''} onChange={onVoiceChange}/></div>:input}</div>;
}
function VoiceButton({value,onChange}){
  const [listening,setListening]=useState(false),[notice,setNotice]=useState('');
  const recognitionRef=useRef(null),latestValue=useRef(value||'');
  useEffect(()=>{latestValue.current=value||''},[value]);
  useEffect(()=>()=>recognitionRef.current?.abort(),[]);
  function toggle(){
    if(listening){recognitionRef.current?.stop();return}
    const SpeechRecognition=window.SpeechRecognition||window.webkitSpeechRecognition;
    if(!SpeechRecognition){setNotice('आवाजातून मजकूर भरण्यासाठी Chrome वापरा.');return}
    const recognition=new SpeechRecognition();
    recognition.lang='mr-IN';recognition.continuous=true;recognition.interimResults=true;
    recognition.onstart=()=>{setListening(true);setNotice('')};
    recognition.onend=()=>setListening(false);
    recognition.onerror=event=>{setListening(false);if(event.error==='not-allowed')setNotice('मायक्रोफोनची परवानगी द्या.');else if(event.error!=='aborted')setNotice('आवाज ओळखता आला नाही; पुन्हा प्रयत्न करा.');};
    recognition.onresult=event=>{
      let transcript='';
      for(let i=event.resultIndex;i<event.results.length;i++)if(event.results[i].isFinal)transcript+=event.results[i][0].transcript;
      transcript=transcript.trim();if(!transcript)return;
      const current=String(latestValue.current||''),next=`${current}${current&&!/\s$/.test(current)?' ':''}${transcript}`;
      latestValue.current=next;onChange(next);
    };
    recognitionRef.current=recognition;
    try{recognition.start()}catch{setNotice('मायक्रोफोन सुरू करता आला नाही.');}
  }
  return <span className={`voice-control ${listening?'listening':''}`}><button type="button" className="voice-button" onClick={toggle} title={notice||(listening?'बोलणे थांबवा':'मराठीत बोलून मजकूर भरा')} aria-label={notice|| (listening?'बोलणे थांबवा':'मराठीत बोलून मजकूर भरा')}>{listening?<MicOff size={15}/>:<Mic size={15}/>}</button>{notice&&<span className="voice-message" role="status">{notice}</span>}</span>;
}
export default App;
