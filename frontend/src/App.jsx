import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Sprout, Plus, Search, CloudSun, Sun, Cloud, CloudRain, ImagePlus, Leaf, Wallet, Boxes, TrendingUp, CalendarDays, ChevronRight, X, Save, Trash2, MapPin, LoaderCircle, Mic, MicOff, FileDown, FileSpreadsheet, Activity, Bell, CheckCircle2, BookOpen, ShoppingBasket, HandHelping } from 'lucide-react';
import farmLogo from './image/WhatsApp Image 2026-10-20 at 2.00.01 PM.jpeg';
import AssistantPanel from './AssistantPanel.jsx';
import ReminderPanel from './ReminderPanel.jsx';
import InformationPanel from './InformationPanel.jsx';
import MarketplacePanel from './MarketplacePanel.jsx';
import ConsultantPanel from './ConsultantPanel.jsx';
import { FollowUpReminderButton, InputLogFields, IssueTrackingFields, OfflineQueueStatus, PlotSeasonFields, QuickEntryTemplates, ReceiptGallery, ReceiptUploader, downloadEntriesCsv, uploadEntryPhoto } from './DailyEntryFeatures.jsx';
import { queueFarmEntry } from './offlineEntrySync.js';
import './brand.css';

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
  { id: 'farmer', label: 'शेतकरी / विक्रेता', initials: 'शे', homeTab: 'entries', tabs: ['entries', 'reminders', 'assistant', 'information', 'marketplace', 'consultants'], description: 'दैनिक नोंद, बाजारात शेतमाल विक्री व खरेदी, स्मरणपत्रे आणि सल्लागार' },
  { id: 'buyer', label: 'खरेदीदार', initials: 'ख्रे', homeTab: 'marketplace', tabs: ['marketplace', 'consultants', 'information'], description: 'शेतमाल शोधा, विक्रेत्यांशी संपर्क करा आणि सल्लागार पाहा' },
  { id: 'consultant', label: 'सेंद्रिय सल्लागार', initials: 'स', homeTab: 'consultants', tabs: ['consultants', 'information'], description: 'सल्लागार प्रोफाइल प्रकाशित करा आणि शेती माहिती पहा' },
  { id: 'admin', label: 'प्रशासक', initials: 'प्र', homeTab: 'admin', tabs: ['admin', 'marketplace', 'consultants', 'information'], description: 'सार्वजनिक बाजार व सल्लागार निर्देशिकेचे निरीक्षण' }
];
function readAccountType() {
  try {
    const saved = localStorage.getItem('swarnabhoomi-account-role');
    if (saved === 'seller') { localStorage.setItem('swarnabhoomi-account-role', 'farmer'); return 'farmer'; }
    return ACCOUNT_TYPES.some(account => account.id === saved) ? saved : null;
  } catch { return null; }
}

function App(){
  const [accountType,setAccountType]=useState(null),[lastAccountType,setLastAccountType]=useState(readAccountType);
  const [activeTab,setActiveTab]=useState('entries');
  const [entries,setEntries]=useState([]),[form,setForm]=useState(fresh),[open,setOpen]=useState(false),[search,setSearch]=useState(''),[loading,setLoading]=useState(false),[weatherBusy,setWeatherBusy]=useState(false),[weatherError,setWeatherError]=useState(''),[preview,setPreview]=useState(''),[auth,setAuth]=useState(()=>sessionStorage.getItem('swarnabhoomi-auth')||''),[authRequired,setAuthRequired]=useState(false),[loginError,setLoginError]=useState(''),[printing,setPrinting]=useState(false),[pdfBusy,setPdfBusy]=useState(false),[printEntries,setPrintEntries]=useState([]),[printPhotos,setPrintPhotos]=useState({}),[activeRecord,setActiveRecord]=useState(null),[analyticsMetric,setAnalyticsMetric]=useState(null),[assistantMessages,setAssistantMessages]=useState([]);
  const currentAccount=ACCOUNT_TYPES.find(account=>account.id===accountType)||ACCOUNT_TYPES[0];
  const apiFetch=useCallback((url,options={})=>{
    const headers={...(options.headers||{}),...(auth?{Authorization:`Basic ${auth}`}:{})};
    const creatingEntry=String(url)===`${API}/entries`&&(options.method||'GET').toUpperCase()==='POST';
    if(creatingEntry&&!navigator.onLine)return queueFarmEntry(options.body);
    const request=fetch(url,{...options,headers});
    return creatingEntry?request.then(response=>response.status>=500?queueFarmEntry(options.body):response).catch(error=>{if(error instanceof TypeError)return queueFarmEntry(options.body);throw error}):request;
  },[auth]);
  const authenticateMarketplace=useCallback(token=>{sessionStorage.setItem('swarnabhoomi-auth',token);setAuth(token)},[]);
  function chooseAccount(account){const requested=new URLSearchParams(window.location.search).get('tab');setAccountType(account.id);setLastAccountType(account.id);setActiveTab(account.tabs.includes(requested)?requested:account.homeTab);setAuthRequired(false);try{localStorage.setItem('swarnabhoomi-account-role',account.id)}catch{}}
  const load=async()=>{try{const r=await apiFetch(`${API}/entries`);if(r.status===401){setAuthRequired(accountType==='farmer');return}if(r.ok){setEntries(await r.json());setAuthRequired(false)}}catch{}};
  useEffect(()=>{if(accountType)load()},[auth,accountType]);
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
  async function login(e){e.preventDefault();setLoginError('');const data=new FormData(e.currentTarget);const token=btoa(`${data.get('username')}:${data.get('password')}`);try{const r=await fetch(`${API}/entries`,{headers:{Authorization:`Basic ${token}`}});if(!r.ok)throw Error();sessionStorage.setItem('swarnabhoomi-auth',token);setEntries(await r.json());setAuth(token);setAuthRequired(false)}catch{setLoginError('वापरकर्तानाव किंवा पासवर्ड तपासा.')}}
  async function save(e){e.preventDefault();setLoading(true);try{const {cropName,cropCustom,plotName,...record}=form;const crop=cropName==='इतर'?(cropCustom||'').trim():cropName;record.farmSection=[crop,plotName].filter(Boolean).join(' — ')||null;const r=await apiFetch(`${API}/entries`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(Object.entries(record).map(([k,v])=>[k,v===''?null:v])))});if(r.status===401){setAuthRequired(true);throw Error()}if(!r.ok)throw Error();setForm(fresh());setOpen(false);setPreview('');await load()}catch{alert('नोंद जतन करता आली नाही. Java सर्व्हर सुरू आहे का ते तपासा.')}finally{setLoading(false)}}
  async function getWeather(){setWeatherBusy(true);setWeatherError('');try{const r=await apiFetch(`${API}/weather/${form.pincode}`);const contentType=r.headers.get('content-type')||'';if(!contentType.toLowerCase().includes('json'))throw Error('हवामान सर्व्हरकडून JSON प्रतिसाद मिळाला नाही. Java सर्व्हर सुरू आहे का ते तपासा.');let d;try{d=await r.json()}catch{throw Error('हवामान सर्व्हरकडून वाचता न येणारा प्रतिसाद मिळाला.')}if(!r.ok)throw Error(d.message||(r.status>=500?'IMD हवामान सेवा सध्या उपलब्ध नाही. हवामान हाताने निवडा.':'हवामान माहिती मिळाली नाही.'));set('weatherDetails',JSON.stringify(d));}catch(e){setWeatherError(e instanceof TypeError?'हवामान सर्व्हरशी संपर्क होऊ शकला नाही. Java सर्व्हर सुरू आहे का ते तपासा.':e.message)}finally{setWeatherBusy(false)}}
  async function upload(file){if(!file)return;setPreview(URL.createObjectURL(file));const data=new FormData();data.append('file',file);try{const r=await apiFetch(`${API}/photos`,{method:'POST',body:data});if(!r.ok)throw Error();const d=await r.json();set('photoUrl',d.url)}catch{setWeatherError('फोटो अपलोड झाला नाही. Java सर्व्हर तपासा.')}}
  async function remove(id){if(!confirm('ही नोंद हटवायची आहे का?'))return;await apiFetch(`${API}/entries/${id}`,{method:'DELETE'});load()}
  if(!accountType)return <AccountSelection lastAccountType={lastAccountType} onSelect={chooseAccount}/>;
  if(authRequired)return <div className="login-screen"><form className="login-card" onSubmit={login}><div className="login-mark"><img className="login-logo" src={farmLogo} alt="स्वर्णभूमी सेंद्रिय शेतीचे चिन्ह"/></div><span className="eyebrow">स्वर्णभूमी · दैनिक शेती नोंद</span><h1>स्वागत आहे</h1><p>शेतकरी नोंदींसाठी प्रवेश तपशील भरा. इतर खाते प्रकार credentials न मागता उघडतील.</p><div className="login-account-options" aria-label="इतर खाते प्रकार">{ACCOUNT_TYPES.filter(account=>account.id!=='farmer').map(account=><button type="button" key={account.id} onClick={()=>chooseAccount(account)}><span>{account.initials}</span><b>{account.label}</b></button>)}</div><label className="field"><span>वापरकर्तानाव</span><input name="username" autoComplete="username" required/></label><label className="field"><span>पासवर्ड</span><input type="password" name="password" autoComplete="current-password" required/></label>{loginError&&<div className="error">{loginError}</div>}<button className="primary">अॅप उघडा <ChevronRight size={17}/></button></form></div>;
  return <div className="app-shell">
    <aside className="sidebar"><div className="brand"><img className="brand-logo" src={farmLogo} alt="स्वर्णभूमी सेंद्रिय शेतीचे चिन्ह"/></div><div className="nav-label">मुख्य मेनू</div>{currentAccount.tabs.map(tab=>{const item=NAV_ITEMS[tab],Icon=item.icon;return <button type="button" key={tab} className={`nav ${activeTab===tab?'active':''}`} onClick={()=>setActiveTab(tab)} aria-label={item.label} title={item.label}><Icon size={18}/>{item.label}</button>})}<div className="sidebar-tip"><Leaf size={19}/><b>आजची छोटी नोंद,<br/>उद्याची मोठी भरभराट.</b><span>तुमच्या शेताचा प्रत्येक दिवस जपून ठेवा.</span><div className="tip-leaves">✳　✳</div></div><button type="button" className="profile account-profile" onClick={()=>{setAuthRequired(false);setAccountType(null)}} aria-label="खाते बदला"><div className="avatar">{currentAccount.initials}</div><span><b>{currentAccount.label} खाते</b><small>खाते बदला</small></span><ChevronRight size={16}/></button></aside>
    <main className="main"><header className="topbar"><div><span className="crumb">स्वर्णभूमी　/　</span><b>{NAV_ITEMS[activeTab]?.label||'स्वर्णभूमी'}</b></div><div className="today"><CalendarDays size={16}/>{new Date().toLocaleDateString('mr-IN',{weekday:'long',day:'numeric',month:'long'})}</div></header>
      {activeTab==='entries'?<>
      <section className="welcome"><div><span className="eyebrow"><span className="pulse"/> तुमच्या शेताची डायरी</span><h1>नमस्कार, शेतकरी मित्रा! <span>🌱</span></h1><p>आज शेतात काय घडलं? तुमची नोंद इथे जतन करा.</p></div><button className="primary" onClick={()=>{setForm(fresh());setOpen(true)}}><Plus size={19}/> आजची नोंद लिहा</button><div className="welcome-art"><span>☀️</span><div>〰️〰️</div></div></section>
      <section className="stats"><Stat icon={<Wallet/>} label="एकूण खर्च" value={money(totals.cost)} tone="peach" analyticsKey="cost" onAnalytics={setAnalyticsMetric}/><Stat icon={<TrendingUp/>} label="एकूण उत्पन्न" value={money(totals.income)} tone="green" analyticsKey="income" onAnalytics={setAnalyticsMetric}/><Stat icon={<Boxes/>} label="एकूण उत्पादन" value={`${totals.production.toLocaleString('mr-IN')} किलो`} tone="blue" analyticsKey="production" onAnalytics={setAnalyticsMetric}/><div className="stat-card net"><div className="net-label">निव्वळ शिल्लक</div><strong>{money(totals.income-totals.cost)}</strong><span>आतापर्यंतची कमाई</span><div className="net-icon">↗</div></div></section>
      <OfflineQueueStatus/>
      <section className="entries-head"><div><h2>शेतातील नोंदी</h2><p>तुमच्या रोजच्या कामांचा लेखाजोखा</p></div><div className="tools"><div className="search"><Search size={17}/><input placeholder="नोंदी शोधा..." value={search} onChange={e=>setSearch(e.target.value)}/><VoiceButton value={search} onChange={setSearch}/></div><button className="outline pdf-export" onClick={downloadPdf} disabled={!filtered.length||pdfBusy} title="दिसणाऱ्या नोंदींची PDF तयार करा">{pdfBusy?<LoaderCircle className="spin" size={15}/>:<FileDown size={15}/>} PDF डाउनलोड</button><button type="button" className="outline csv-export" onClick={() => downloadEntriesCsv(filtered)} disabled={!filtered.length} title="दिसणाऱ्या नोंदी Excel किंवा CSV मध्ये डाउनलोड करा"><FileSpreadsheet size={15}/> CSV डाउनलोड</button><button className="filter">सर्व नोंदी⌄</button></div></section>
      {!filtered.length?<div className="empty"><div className="empty-icon">🌾</div><h3>{entries.length?'नोंद सापडली नाही':'तुमची पहिली नोंद लिहा'}</h3><p>शेतातल्या छोट्या-मोठ्या गोष्टी नोंदवा. त्या पुढच्या हंगामात उपयोगी पडतील.</p><button className="primary" onClick={()=>{setForm(fresh());setOpen(true)}}><Plus size={18}/> नवी नोंद</button></div>:<div className="entry-list">{filtered.map(e=><article className={`entry-card ${activeRecord===e.id?'details-open':''}`} key={e.id} tabIndex={0} aria-describedby={`record-details-${e.id}`} onClick={()=>{if(window.matchMedia?.('(hover: none)').matches)setActiveRecord(current=>current===e.id?null:e.id)}} onKeyDown={event=>{if(event.key==='Escape')setActiveRecord(null)}}><div className="entry-date"><div className="date-icon"><CalendarDays size={18}/></div><div><b>{day(e.entryDate)}</b><small>{e.weather||'हवामान नोंदले नाही'}</small></div></div><div className="entry-crop"><small>पीक / विभाग</small><b>{e.farmSection||'—'}</b></div><div className="entry-work"><small>आजची कामे</small><b>{e.workDone||'—'}</b></div><div className="entry-money"><small>खर्च　·　उत्पन्न</small><b>{money(entryCost(e))} <span>·</span> {money(e.saleIncome)}</b></div><div className="entry-actions">{e.photoUrl&&<ProtectedPhoto src={e.photoUrl} auth={auth} alt="शेत फोटो"/>}<button className="delete" title="नोंद हटवा" onClick={event=>{event.stopPropagation();remove(e.id)}}><Trash2 size={16}/></button></div><div className="record-details-popup" id={`record-details-${e.id}`} role="tooltip" onClick={event=>event.stopPropagation()}><RecordDetails entry={e} apiFetch={apiFetch}/></div></article>)}</div>}
      {printing&&<section id="print-report" lang="mr"><header className="print-header"><div className="print-brand"><Sprout size={24}/> स्वर्णभूमी</div><h1>दैनिक शेती नोंदवही</h1><p>तयार केल्याची वेळ: {new Date().toLocaleString('mr-IN',{dateStyle:'medium',timeStyle:'short'})}{search&&<>　·　शोध: “{search}”</>}</p></header><div className="print-summary"><div><span>एकूण नोंदी</span><b>{printEntries.length.toLocaleString('mr-IN')}</b></div><div><span>एकूण खर्च</span><b>{money(printEntries.reduce((sum,e)=>sum+entryCost(e),0))}</b></div><div><span>एकूण उत्पन्न</span><b>{money(printEntries.reduce((sum,e)=>sum+Number(e.saleIncome||0),0))}</b></div><div><span>निव्वळ उत्पन्न</span><b>{money(printEntries.reduce((sum,e)=>sum+Number(e.saleIncome||0)-entryCost(e),0))}</b></div></div>{printEntries.map((e,index)=>{const cost=entryCost(e);return <article className="print-entry" key={e.id}><div className="print-entry-heading"><h2>नोंद {String(index+1).toLocaleString('mr-IN')}</h2><b>{day(e.entryDate)}</b></div><div className="print-grid"><PrintField label="पीक / शेताचा विभाग" value={e.farmSection}/><PrintField label="हंगाम" value={e.seasonName}/><PrintField label="क्षेत्र (एकर)" value={e.plotAreaAcres}/><PrintField label="हवामान" value={e.weather}/>{e.pincode&&<PrintField label="पिनकोड" value={e.pincode}/ >}{weatherDetailText(e.weatherDetails)&&<PrintField label="हवामान माहिती" value={weatherDetailText(e.weatherDetails)}/>}<PrintField label="आज केलेली कामे" value={e.workDone}/><PrintField label="वापरलेले साहित्य / खते" value={e.materials}/><PrintField label="पाणी (लिटर)" value={e.waterLitres}/><PrintField label="निविष्ठेचा प्रकार" value={e.applicationKind}/><PrintField label="निविष्ठा" value={e.applicationName}/><PrintField label="प्रमाण" value={e.applicationAmount ? e.applicationAmount + " " + (e.applicationUnit || "") : ""}/><PrintField label="मजूर संख्या" value={e.workerCount}/><PrintField label="मजुरी खर्च" value={money(e.laborCost)}/><PrintField label="खते / साहित्य खर्च" value={money(e.materialsCost)}/><PrintField label="इतर खर्च" value={money(e.otherCost)}/><PrintField label="एकूण खर्च" value={money(cost)}/><PrintField label="उत्पादन" value={e.productionKg?`${Number(e.productionKg).toLocaleString('mr-IN')} किलो`:''}/><PrintField label="विक्री" value={e.soldKg?`${Number(e.soldKg).toLocaleString('mr-IN')} किलो`:''}/><PrintField label="विक्रीतून मिळालेले पैसे" value={money(e.saleIncome)}/><PrintField label="निव्वळ उत्पन्न" value={money(Number(e.saleIncome||0)-cost)}/><PrintField label="पिकाची सद्यस्थिती" value={e.cropCondition}/><PrintField label="माती व सेंद्रिय शेती निरीक्षण" value={e.soilNotes}/><PrintField label="कीड / रोग" value={e.pestDisease}/><PrintField label="तीव्रता" value={e.issueSeverity}/><PrintField label="केलेला उपाय" value={e.intervention}/><PrintField label="पुढील पाहणी" value={e.followUpDate ? day(e.followUpDate) : ""}/><PrintField label="उद्याची नियोजित कामे" value={e.tomorrowPlan}/><PrintField label="विशेष नोंद" value={e.specialNote}/></div>{e.photoUrl&&(printPhotos[e.id]?<figure className="print-photo"><img src={printPhotos[e.id]} alt="शेताचा फोटो"/><figcaption>शेताचा फोटो</figcaption></figure>:<p className="print-photo-missing">शेताचा फोटो जोडला होता; तो PDF मध्ये उपलब्ध झाला नाही.</p>)}</article>})}<footer className="print-footer">स्वर्णभूमी · तुमच्या कष्टांची नोंद</footer></section>}
      <footer>🌿　तुमच्या कष्टांना योग्य नोंद — स्वर्णभूमी</footer></>:activeTab==='reminders'?<ReminderPanel apiFetch={apiFetch} apiBase={API} entries={entries} onUnauthorized={()=>setAuthRequired(true)}/>:null}
      <div hidden={activeTab!=='assistant'}><AssistantPanel apiFetch={apiFetch} apiBase={API} messages={assistantMessages} setMessages={setAssistantMessages} farmData={assistantFarmData} onUnauthorized={()=>setAuthRequired(true)}/></div>
      {activeTab==='information'&&<InformationPanel/>}
      {activeTab==='admin'&&<AdminOverview apiBase={API}/>}
      {activeTab==='marketplace'&&<MarketplacePanel accountType={accountType} apiBase={API} auth={auth} onAuthenticated={authenticateMarketplace} onUnauthorized={()=>setAuthRequired(true)}/>}
      {activeTab==='consultants'&&<ConsultantPanel accountType={accountType} apiBase={API}/>}
    </main>
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
function RecordDetails({entry:e,apiFetch}){
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
  return <><div className="record-popover-title"><strong>नोंदीचा संपूर्ण तपशील</strong><span>{day(e.entryDate)}</span></div><dl className="record-detail-list">{fields.filter(([,value])=>value!==null&&value!==undefined&&value!=='').map(([label,value])=><div className="record-detail" key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl><ReceiptGallery value={e.receiptPhotoUrls} apiFetch={apiFetch}/><FollowUpReminderButton entry={e} apiFetch={apiFetch}/>{e.photoUrl&&<div className="record-photo-note">📷 शेताचा फोटो जोडलेला आहे</div>}</>;
}
function ProtectedPhoto({src,auth,alt}){
  const [image,setImage]=useState(''),[imageType,setImageType]=useState('image/jpeg'),[expanded,setExpanded]=useState(false);
  useEffect(()=>{let active=true,objectUrl='';const source=src.startsWith('http://localhost:8080/')?`/api/photos/${src.slice('http://localhost:8080/'.length)}`:src;const headers=auth?{Authorization:`Basic ${auth}`}:{ };fetch(source,{headers}).then(r=>{if(!r.ok)throw Error();return r.blob()}).then(blob=>{if(active){objectUrl=URL.createObjectURL(blob);setImage(objectUrl);setImageType(blob.type||'image/jpeg')}}).catch(()=>{});return()=>{active=false;if(objectUrl)URL.revokeObjectURL(objectUrl)}},[src,auth]);
  useEffect(()=>{if(!expanded)return;const onKeyDown=event=>{if(event.key==='Escape')setExpanded(false)};window.addEventListener('keydown',onKeyDown);return()=>window.removeEventListener('keydown',onKeyDown)},[expanded]);
  const extension=imageType.includes('jpeg')||imageType.includes('jpg')?'jpg':imageType.split('/')[1]?.split('+')[0]||'jpg';
  if(!image)return null;
  return <><button type="button" className="photo-thumb-trigger" title="मोठा फोटो पाहण्यासाठी hover करा किंवा टॅप करा" aria-label="शेताचा फोटो मोठा पाहा" aria-haspopup="dialog" onMouseEnter={()=>setExpanded(true)} onClick={event=>{event.stopPropagation();setExpanded(true)}}><img src={image} alt={alt}/></button>{expanded&&<div className="image-lightbox" onMouseDown={event=>{if(event.target===event.currentTarget)setExpanded(false)}}><section className="image-lightbox-dialog" role="dialog" aria-modal="true" aria-label="शेताचा मोठा फोटो"><header className="image-lightbox-header"><strong>{alt||'शेताचा फोटो'}</strong><div><a className="image-download" href={image} download={`swarnabhoomi-photo.${extension}`}><FileDown size={15}/> फोटो डाउनलोड</a><button type="button" className="close" onClick={()=>setExpanded(false)} aria-label="फोटो बंद करा"><X size={19}/></button></div></header><img className="image-lightbox-full" src={image} alt={alt}/></section></div>}</>;
}
function AccountSelection({lastAccountType,onSelect}){
  return <main className="account-select-screen" lang="mr"><section className="account-select-content"><header className="account-select-header"><img src={farmLogo} alt="स्वर्णभूमी"/><div><span>स्वर्णभूमी · शेतकरी समुदाय</span><h1>कोणत्या खात्यातून पुढे जायचे?</h1><p>तुमच्या कामाला योग्य खाते निवडा. प्रत्येक खात्यासाठी आवश्यक टॅब इथे पाहा.</p></div></header><div className="account-role-grid">{ACCOUNT_TYPES.map(account=><button type="button" key={account.id} className={`account-role-card ${lastAccountType===account.id?'previous':''}`} onClick={()=>onSelect(account)}><span className={`account-role-avatar role-${account.id}`}>{account.initials}</span><span className="account-role-copy"><b>{account.label}</b>{lastAccountType===account.id&&<small className="account-last-used">मागील निवड</small>}<small>{account.description}</small><small><strong>प्रमुख टॅब:</strong> {account.tabs.map(tab=>NAV_ITEMS[tab].label).join(' · ')}</small></span></button>)}</div><p className="account-select-note">खाते निवडल्यानंतरच अॅप उघडेल. या खात्यांसाठी सध्या username किंवा password विचारले जाणार नाही. भूमिका या ब्राउझरमध्ये जतन होतात; स्वतंत्र सुरक्षित खाती आणि प्रशासकीय अधिकार नंतर जोडता येतील.</p></section></main>;
}

function AdminOverview({apiBase}){
  const [summary,setSummary]=useState({listings:null,consultants:null});
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  useEffect(()=>{let active=true;Promise.all([fetch(`${apiBase}/marketplace/listings`),fetch(`${apiBase}/consultants`)]).then(async([listingsResponse,consultantsResponse])=>{if(!listingsResponse.ok||!consultantsResponse.ok)throw new Error('सारांश माहिती मिळाली नाही.');const [listings,consultants]=await Promise.all([listingsResponse.json(),consultantsResponse.json()]);if(active)setSummary({listings:listings.length,consultants:consultants.length})}).catch(exception=>{if(active)setError(exception.message||'सारांश माहिती मिळाली नाही.')}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[apiBase]);
  return <section className="account-admin-page"><div className="account-admin-hero"><span>स्वर्णभूमी प्रशासक</span><h1>व्यवस्थापन आढावा</h1><p>बाजारातील सार्वजनिक शेतमाल नोंदी आणि सेंद्रिय सल्लागार निर्देशिका.</p></div>{error&&<p className="account-admin-error" role="status">{error}</p>}<div className="account-admin-stats"><article><span>शेतमाल नोंदी</span><strong>{loading?'…':Number(summary.listings||0).toLocaleString('mr-IN')}</strong><small>शेतकरी बाजारात उपलब्ध</small></article><article><span>सेंद्रिय सल्लागार</span><strong>{loading?'…':Number(summary.consultants||0).toLocaleString('mr-IN')}</strong><small>शेतकरी पाहू शकतात</small></article></div><div className="account-admin-notice"><Activity size={18}/><p><b>प्रशासक भूमिका पूर्वावलोकन</b><span>वापरकर्ता व्यवस्थापन, खाते मंजुरी किंवा डेटा बदलण्याचे अधिकार जोडलेले नाहीत. या भूमिका निवडीला सध्या सुरक्षित स्वतंत्र लॉगिन नाही.</span></p></div></section>;
}

function Stat({icon,label,value,tone,analyticsKey,onAnalytics}){return <div className="stat-card"><div className={`stat-icon ${tone} ${analyticsKey?'stat-icon-analytics':''}`}>{icon}{analyticsKey&&<button type="button" className="analytics-symbol" title={`${label}चे विश्लेषण`} aria-label={`${label}चे विश्लेषण उघडा`} onClick={()=>onAnalytics(analyticsKey)}><Activity size={11}/></button>}</div><div><small>{label}</small><strong>{value}</strong></div></div>}
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
