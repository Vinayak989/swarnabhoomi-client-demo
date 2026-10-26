import React, { useEffect, useState } from 'react';
import { Bug, ChevronDown, ImagePlus, Sprout, Trash2 } from 'lucide-react';
import { Bell, LoaderCircle } from 'lucide-react';
import { getBrowserPushSubscriptionId } from './reminderNotifications.js';
import SpeechTextInput from './SpeechTextInput.jsx';
import './dailyEntryFeatures.css';

const templates = [
  { label: 'पाणी दिले', workDone: 'पाणी दिले', applicationKind: '', waterLitres: '' },
  { label: 'फवारणी', workDone: 'फवारणी केली', applicationKind: 'कीड/रोग नियंत्रण' },
  { label: 'सेंद्रिय खत', workDone: 'सेंद्रिय खत दिले', applicationKind: 'सेंद्रिय खत' },
  { label: 'काढणी', workDone: 'काढणी केली', applicationKind: '' }
];
const followUpStorageKey = (userId, date, cropOrPlot, pestDisease) =>
  `swarnabhoomi-followup-reminder-${userId}-${[date, cropOrPlot || '', pestDisease || ''].map(encodeURIComponent).join('|')}`;

export function QuickEntryTemplates({ onApply, expanded, onToggle }) {
  return <section className="daily-feature-card quick-entry-card">
    <button type="button" className="daily-feature-toggle" onClick={onToggle} aria-expanded={expanded} aria-controls="quick-entry-options">
      <span className="daily-feature-title"><Sprout size={17}/><span><b>झटपट नोंद नमुने</b><small>सामान्य कामासाठी एक टॅप करा</small></span></span>
      <ChevronDown className={expanded ? 'daily-feature-chevron expanded' : 'daily-feature-chevron'} size={18}/>
    </button>
    {expanded && <div className="quick-entry-buttons" id="quick-entry-options">{templates.map(template => <button type="button" key={template.label} onClick={() => onApply(template)}>{template.label}</button>)}</div>}
  </section>;
}

export function PlotSeasonFields({ form, set }) {
  return <div className="form-grid three plot-season-fields">
    <label className="field"><span className="field-label">हंगाम</span><select value={form.seasonName} onChange={e => set('seasonName', e.target.value)}><option value="">हंगाम निवडा...</option><option>खरीप</option><option>रब्बी</option><option>उन्हाळी</option><option>बारमाही</option><option>इतर</option></select></label>
    <label className="field"><span className="field-label">प्लॉटचे क्षेत्र (एकर)</span><input type="number" min="0" step="any" value={form.plotAreaAcres} onChange={e => set('plotAreaAcres', e.target.value)} placeholder="उदा. १.५"/></label>
  </div>;
}

function reminderDateTime(date) {
  const formatIndia = value => {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
    }).formatToParts(value).map(part => [part.type, part.value]));
    return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
  };
  const selected = new Date(`${date}T09:00:00+05:30`);
  const now = new Date();
  if (selected <= now) {
    const laterToday = new Date(now.getTime() + 30 * 60 * 1000);
    if (formatIndia(selected).slice(0, 10) !== formatIndia(laterToday).slice(0, 10)) {
      throw new Error('आजची पाहणीची वेळ निघून गेली आहे. उद्याची किंवा पुढील तारीख निवडा.');
    }
    return formatIndia(laterToday);
  }
  return `${date}T09:00`;
}

export function IssueTrackingFields({ form, set, apiFetch, apiBase, userId }) {
  const reminderRef = React.useRef({ requestId: '', reminderId: null, date: '' });
  const [reminderBusy, setReminderBusy] = useState(false);
  const [reminderMessage, setReminderMessage] = useState('');
  const [reminderError, setReminderError] = useState('');

  async function syncReminder(date) {
    if (reminderRef.current.requestId !== form.clientRequestId) {
      reminderRef.current = { requestId: form.clientRequestId, reminderId: null, date: '' };
    }
    const current = reminderRef.current;
    if (date === current.date) return;
    setReminderBusy(true);
    setReminderMessage('');
    setReminderError('');
    try {
      if (!date) {
        if (current.reminderId) {
          const response = await apiFetch(`${apiBase}/reminders/${current.reminderId}`, { method: 'DELETE' });
          if (!response.ok) throw new Error('स्मरणपत्र हटवता आले नाही.');
          if (current.storageKey) {
            try { localStorage.removeItem(current.storageKey); } catch { /* The server reminder is deleted. */ }
          }
          reminderRef.current = { ...current, reminderId: null, date: '', storageKey: '' };
          setReminderMessage('निवडलेले स्मरणपत्र हटवले.');
        }
        return;
      }
      const scheduledAt = reminderDateTime(date);
      const reason = `पुढील पाहणी: ${form.pestDisease || form.intervention || 'पीक तपासा'}`;
      const cropOrPlot = [form.cropName === 'इतर' ? form.cropCustom : form.cropName, form.plotName].filter(Boolean).join(' — ').slice(0, 120);
      const storageKey = followUpStorageKey(userId, date, cropOrPlot, form.pestDisease);
      const body = {
        scheduledAt,
        reason,
        cropOrPlot,
        browserSubscriptionId: getBrowserPushSubscriptionId(userId)
      };
      const response = current.reminderId
        ? await apiFetch(`${apiBase}/reminders/${current.reminderId}/reschedule`, {
          method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scheduledAt })
        })
        : await apiFetch(`${apiBase}/reminders`, {
          method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
        });
      const result = await response.json().catch(() => null);
      if (!response.ok) throw new Error(result?.detail || result?.message || 'स्मरणपत्र तयार करता आले नाही.');
      if (current.storageKey && current.storageKey !== storageKey) {
        try { localStorage.removeItem(current.storageKey); } catch { /* The server reminder remains saved. */ }
      }
      try { localStorage.setItem(storageKey, String(result.id)); } catch { /* The server reminder remains saved. */ }
      reminderRef.current = { requestId: form.clientRequestId, reminderId: result.id, date, storageKey };
      setReminderMessage(current.reminderId ? 'स्मरणपत्राची तारीख बदलली.' : 'पुढील पाहणी स्मरणपत्रांमध्ये जोडली.');
    } catch (exception) {
      setReminderError(exception.message || 'स्मरणपत्र तयार करता आले नाही. पुन्हा प्रयत्न करा.');
    } finally {
      setReminderBusy(false);
    }
  }

  return <section className="form-section issue-tracking-fields"><h3><Bug size={18}/> कीड / रोग व पुढील पाहणी</h3><div className="form-grid three">
    <label className="field"><span className="field-label">कीड किंवा रोग</span><SpeechTextInput><input value={form.pestDisease} onChange={e => set('pestDisease', e.target.value)} placeholder="लक्षण किंवा कीडीचे नाव"/></SpeechTextInput></label>
    <label className="field"><span className="field-label">तीव्रता</span><select value={form.issueSeverity} onChange={e => set('issueSeverity', e.target.value)}><option value="">निवडा...</option><option>कमी</option><option>मध्यम</option><option>जास्त</option></select></label>
    <label className="field"><span className="field-label">पुढील पाहणीची तारीख</span><input type="date" value={form.followUpDate} disabled={reminderBusy} onChange={event => { set('followUpDate', event.target.value); void syncReminder(event.target.value); }}/></label>
  </div><label className="field"><span className="field-label">केलेला उपाय / निरीक्षण</span><SpeechTextInput><textarea rows="2" value={form.intervention} onChange={e => set('intervention', e.target.value)} placeholder="उपाय, प्रमाण आणि पुढे काय तपासायचे ते लिहा"/></SpeechTextInput></label>
    {reminderBusy && <div className="follow-up-reminder" role="status"><LoaderCircle className="spin" size={14}/> स्मरणपत्र जतन करत आहे...</div>}
    {reminderMessage && <div className="follow-up-reminder" role="status"><span>{reminderMessage}</span></div>}
    {reminderError && <div className="follow-up-reminder" role="alert"><small>{reminderError}</small></div>}
  </section>;
}

export function FollowUpReminderButton({ entry, apiFetch, userId }) {
  const storageKey = followUpStorageKey(userId, entry.followUpDate, entry.farmSection, entry.pestDisease);
  const legacyStorageKey = `swarnabhoomi-entry-followup-${userId}-${entry.id}`;
  const [createdId, setCreatedId] = useState(() => { try { return localStorage.getItem(storageKey) || localStorage.getItem(legacyStorageKey) || ''; } catch { return ''; } });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (!entry.followUpDate) return null;
  async function createReminder() {
    setBusy(true); setError('');
    try {
      const now = new Date();
      const selected = new Date(`${entry.followUpDate}T09:00:00`);
      const scheduled = selected <= now ? new Date(now.getTime() + 60 * 60 * 1000) : selected;
      const localDateTime = `${scheduled.getFullYear()}-${String(scheduled.getMonth()+1).padStart(2,'0')}-${String(scheduled.getDate()).padStart(2,'0')}T${String(scheduled.getHours()).padStart(2,'0')}:${String(scheduled.getMinutes()).padStart(2,'0')}:00`;
      const subscriptionId = getBrowserPushSubscriptionId(userId);
      const response = await apiFetch(`${(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')}/api/reminders`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scheduledAt: localDateTime, reason: `पुढील पाहणी: ${entry.pestDisease || entry.intervention || 'पीक तपासा'}`, browserSubscriptionId: subscriptionId, cropOrPlot: entry.farmSection || '' })
      });
      if (!response.ok) throw new Error('स्मरणपत्र तयार झाले नाही.');
      const reminder = await response.json();
      setCreatedId(String(reminder.id));
      try {
        localStorage.setItem(storageKey, String(reminder.id));
        localStorage.setItem(legacyStorageKey, String(reminder.id));
      } catch { /* Reminder remains saved on the server. */ }
    } catch { setError('स्मरणपत्र तयार झाले नाही. पुन्हा प्रयत्न करा.'); }
    finally { setBusy(false); }
  }
  return <div className="follow-up-reminder">{createdId ? <span>स्मरणपत्र तयार झाले</span> : <button type="button" onClick={createReminder} disabled={busy}>{busy?<LoaderCircle className="spin" size={14}/>:<Bell size={14}/>} पाहणीची आठवण तयार करा</button>}{error&&<small role="status">{error}</small>}</div>;
}

function readReceiptUrls(value) {
  try { const parsed = typeof value === 'string' ? JSON.parse(value || '[]') : value; return Array.isArray(parsed) ? parsed.filter(url => typeof url === 'string' && url) : []; }
  catch { return []; }
}

function ReceiptPhoto({ url, apiFetch, onRemove }) {
  const [image, setImage] = useState('');
  useEffect(() => {
    let active = true, objectUrl = '';
    apiFetch(url).then(response => { if (!response.ok) throw Error(); return response.blob(); }).then(blob => {
      if (active) { objectUrl = URL.createObjectURL(blob); setImage(objectUrl); }
    }).catch(() => {});
    return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [url, apiFetch]);
  return <div className="receipt-thumb">{image ? <a href={image} target="_blank" rel="noreferrer" title="पावती मोठी पाहा"><img src={image} alt="खर्चाची पावती"/></a> : <span>पावती</span>}{onRemove&&<button type="button" onClick={onRemove} aria-label="पावती काढा"><Trash2 size={14}/></button>}</div>;
}

export function ReceiptUploader({ value, onChange, uploadFile, apiFetch, onError }) {
  const [uploading, setUploading] = useState(false);
  const urls = readReceiptUrls(value);
  async function addFiles(files) {
    setUploading(true);
    try {
      const next = [...urls];
      for (const file of files) {
        const url = await uploadFile(file);
        if (url) next.push(url);
      }
      onChange(JSON.stringify(next));
    } catch { onError?.(); }
    finally { setUploading(false); }
  }
  return <section className="daily-feature-card receipt-card"><div className="daily-feature-title"><ImagePlus size={17}/><div><b>खर्चाच्या पावत्या</b><small>खत, बियाणे किंवा मजुरीची पावती जोडा</small></div></div><label className="receipt-picker"><input type="file" accept="image/*" multiple onChange={e => { const files = [...(e.target.files || [])]; if (files.length) addFiles(files); e.target.value = ''; }}/><ImagePlus size={17}/>{uploading ? 'फोटो अपलोड होत आहेत…' : 'पावतीचे फोटो निवडा'}</label>{urls.length > 0 && <div className="receipt-thumbs">{urls.map((url, index) => <ReceiptPhoto key={`${url}-${index}`} url={url} apiFetch={apiFetch} onRemove={() => onChange(JSON.stringify(urls.filter((_, itemIndex) => itemIndex !== index)))}/>)}</div>}</section>;
}

export function ReceiptGallery({ value, apiFetch }) {
  const urls = readReceiptUrls(value);
  if (!urls.length) return null;
  return <div className="saved-receipts"><strong>जोडलेल्या पावत्या</strong><div className="receipt-thumbs">{urls.map((url, index) => <ReceiptPhoto key={`${url}-${index}`} url={url} apiFetch={apiFetch}/>)}</div></div>;
}

export async function uploadEntryPhoto(apiFetch, file) {
  const data = new FormData();
  data.append('file', file);
  const response = await apiFetch(`${(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')}/api/photos`, { method: 'POST', body: data });
  if (!response.ok) throw new Error('Photo upload failed.');
  return (await response.json()).url;
}

export function OfflineQueueStatus({ ownerId }) {
  const [count, setCount] = useState(0);
  useEffect(() => {
    const update = () => {
      try {
        const queue = JSON.parse(localStorage.getItem(`swarnabhoomi-offline-entry-queue-v1:${ownerId}`) || '[]');
        setCount(Array.isArray(queue) ? queue.length : 0);
      } catch { setCount(0); }
    };
    update();
    window.addEventListener('swarnabhoomi-entry-queued', update);
    window.addEventListener('swarnabhoomi-entries-synced', update);
    window.addEventListener('storage', update);
    return () => {
      window.removeEventListener('swarnabhoomi-entry-queued', update);
      window.removeEventListener('swarnabhoomi-entries-synced', update);
      window.removeEventListener('storage', update);
    };
  }, [ownerId]);
  if (!count) return null;
  return <div className="offline-status" role="status"><strong>{count.toLocaleString('mr-IN')} नोंदी समक्रमणाच्या प्रतीक्षेत</strong><button type="button" disabled={!navigator.onLine} onClick={() => window.dispatchEvent(new Event('swarnabhoomi-entry-queued'))}>आता समक्रमित करा</button></div>;
}

export function downloadEntriesCsv(entries) {
  const columns = [
    ['दिनांक', 'entryDate'], ['पीक / प्लॉट', 'farmSection'], ['हंगाम', 'seasonName'], ['क्षेत्र (एकर)', 'plotAreaAcres'],
    ['हवामान', 'weather'], ['पिनकोड', 'pincode'], ['हवामान माहिती', 'weatherDetails'], ['काम', 'workDone'], ['साहित्य / खते', 'materials'],
    ['पाणी (लिटर)', 'waterLitres'], ['निविष्ठेचा प्रकार', 'applicationKind'], ['निविष्ठा', 'applicationName'], ['प्रमाण', 'applicationAmount'], ['एकक', 'applicationUnit'],
    ['कीड / रोग', 'pestDisease'], ['तीव्रता', 'issueSeverity'], ['केलेला उपाय', 'intervention'], ['पुढील पाहणी', 'followUpDate'],
    ['मजूर संख्या', 'workerCount'], ['मजुरी खर्च (₹)', 'laborCost'], ['साहित्य खर्च (₹)', 'materialsCost'], ['इतर खर्च (₹)', 'otherCost'],
    ['एकूण खर्च (₹)', entry => Number(entry.laborCost || 0) + Number(entry.materialsCost || 0) + Number(entry.otherCost || 0)],
    ['उत्पादन (कि.)', 'productionKg'], ['विक्री (कि.)', 'soldKg'], ['विक्री उत्पन्न (₹)', 'saleIncome'],
    ['निव्वळ उत्पन्न (₹)', entry => Number(entry.saleIncome || 0) - Number(entry.laborCost || 0) - Number(entry.materialsCost || 0) - Number(entry.otherCost || 0)],
    ['पिकाची स्थिती', 'cropCondition'], ['माती निरीक्षण', 'soilNotes'], ['उद्याची कामे', 'tomorrowPlan'], ['विशेष नोंद', 'specialNote'],
    ['शेत फोटो URL', 'photoUrl'], ['पावती फोटो URL', 'receiptPhotoUrls']
  ];
  const quote = value => {
    let text = String(value ?? '');
    if (/^[\s]*[=+\-@]/.test(text)) text = `'${text}`;
    return `"${text.replaceAll('"', '""')}"`;
  };
  const rows = [columns.map(([label]) => quote(label)).join(','), ...entries.map(entry => columns.map(([, value]) => quote(typeof value === 'function' ? value(entry) : entry[value])).join(','))];
  const blob = new Blob([`\uFEFF${rows.join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `swarnabhoomi-dainik-nondi-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1500);
}
