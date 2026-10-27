import { useEffect, useMemo, useRef, useState } from 'react';
import { Bell, BellOff, CalendarDays, CheckCircle2, CloudSun, LoaderCircle, Mic, MicOff, Plus, Repeat2, RotateCcw, Search, Trash2, Volume2, X } from 'lucide-react';
import { disableBrowserPush, enableBrowserPush, isBrowserPushDisabled, restoreBrowserPush, sendBrowserPushTest } from './reminderNotifications.js';
import SpeechTextInput from './SpeechTextInput.jsx';
import { speechErrorNotice, speechRecognitionConstructor, speechUnavailableNotice } from './speechRecognition.js';
import './reminders.css';

function indiaDateTimeInput(date) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  }).formatToParts(date).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

function reminderDateTime(value, options = {}) {
  return new Date(`${value}+05:30`).toLocaleString('mr-IN', {
    timeZone: 'Asia/Kolkata', weekday: 'short', day: 'numeric', month: 'short',
    year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23', ...options
  });
}

function nextWeekDays() {
  const today = indiaDateTimeInput(new Date()).slice(0, 10);
  const base = new Date(`${today}T00:00:00Z`);
  return Array.from({ length: 7 }, (_, offset) => {
    const date = new Date(base);
    date.setUTCDate(date.getUTCDate() + offset);
    const key = date.toISOString().slice(0, 10);
    return { key, label: new Date(`${key}T00:00:00+05:30`).toLocaleDateString('mr-IN', { weekday: 'short' }), day: key.slice(-2) };
  });
}

function reminderEpoch(value) {
  return new Date(`${value}+05:30`).getTime();
}

function getBodyError(body, fallback) {
  return body?.detail || body?.message || fallback;
}

export default function ReminderPanel({ apiFetch, apiBase, onUnauthorized, entries = [], userId }) {
  const [reminders, setReminders] = useState([]);
  const [form, setForm] = useState(() => ({
    scheduledAt: indiaDateTimeInput(new Date(Date.now() + 30 * 60 * 1000)),
    reason: '', cropOrPlot: '', repeatFrequency: 'NONE', repeatUntil: ''
  }));
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [completingId, setCompletingId] = useState(null);
  const [pushBusy, setPushBusy] = useState(true);
  const [pushEnabled, setPushEnabled] = useState(false);
  const [pushWarning, setPushWarning] = useState('');
  const [listening, setListening] = useState(false);
  const [voiceNotice, setVoiceNotice] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedDay, setSelectedDay] = useState(null);
  const [soonOnly, setSoonOnly] = useState(false);
  const [weatherPincode, setWeatherPincode] = useState('');
  const [weatherLoading, setWeatherLoading] = useState(false);
  const [weatherError, setWeatherError] = useState('');
  const [weatherResult, setWeatherResult] = useState(null);
  const [rescheduleDraft, setRescheduleDraft] = useState(null);
  const [rescheduleSaving, setRescheduleSaving] = useState(false);
  const recognitionRef = useRef(null);
  const reasonInputRef = useRef(null);

  const cropOptions = useMemo(() => [...new Set(entries.map(entry => entry.farmSection?.trim()).filter(Boolean))].sort(), [entries]);
  const pincodeOptions = useMemo(() => [...new Set(entries.map(entry => entry.pincode).filter(pin => /^[1-9][0-9]{5}$/.test(pin || '')))], [entries]);
  const weekDays = useMemo(nextWeekDays, [reminders]);

  async function load(silent = false) {
    if (!silent) setLoading(true);
    try {
      const response = await apiFetch(`${apiBase}/reminders`);
      if (response.status === 401) { onUnauthorized(); return; }
      if (!response.ok) throw Error('स्मरणपत्रे लोड करता आली नाहीत.');
      setReminders(await response.json());
    } catch (exception) {
      if (!silent) setError(exception.message || 'स्मरणपत्रे लोड करता आली नाहीत.');
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => { void load(); }, [apiFetch]);
  useEffect(() => {
    let active = true;
    setPushBusy(true);
    restoreBrowserPush(apiFetch, apiBase, onUnauthorized, userId)
      .then(id => { if (active) setPushEnabled(Boolean(id)); })
      .catch(() => { if (active) setPushEnabled(false); })
      .finally(() => { if (active) setPushBusy(false); });
    return () => { active = false; };
  }, [apiFetch, apiBase, onUnauthorized, userId]);
  useEffect(() => {
    if (!reminders.some(item => item.status === 'PENDING' || item.status === 'SENDING')) return undefined;
    const timer = window.setTimeout(() => { void load(true); }, 15000);
    return () => window.clearTimeout(timer);
  }, [reminders, apiFetch]);
  useEffect(() => {
    if (!weatherPincode && pincodeOptions.length) setWeatherPincode(pincodeOptions[0]);
  }, [weatherPincode, pincodeOptions]);
  useEffect(() => () => recognitionRef.current?.abort(), []);

  const set = (key, value) => setForm(current => ({ ...current, [key]: value }));

  async function save(event) {
    event.preventDefault();
    setSaving(true); setError(''); setNotice(''); setPushWarning('');
    try {
      let subscriptionId = null;
      if (!isBrowserPushDisabled(userId)) {
        try {
          subscriptionId = await enableBrowserPush(apiFetch, apiBase, onUnauthorized, userId);
          setPushEnabled(true);
        } catch (notificationError) {
          setPushEnabled(false);
          setPushWarning(notificationError.message || 'फोनवर सूचना मिळवण्यासाठी डिव्हाइस सूचना सुरू करा.');
        }
      }
      const response = await apiFetch(`${apiBase}/reminders`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...form,
          cropOrPlot: form.cropOrPlot.trim(),
          repeatUntil: form.repeatUntil || null,
          browserSubscriptionId: subscriptionId
        })
      });
      if (response.status === 401) { onUnauthorized(); return; }
      const result = await response.json().catch(() => null);
      if (!response.ok) throw Error(getBodyError(result, 'स्मरणपत्र जतन करता आले नाही.'));
      setForm({ scheduledAt: indiaDateTimeInput(new Date(Date.now() + 30 * 60 * 1000)), reason: '', cropOrPlot: '', repeatFrequency: 'NONE', repeatUntil: '' });
      setNotice(subscriptionId ? 'स्मरणपत्र जतन झाले. ठरलेल्या वेळी फोनवर सूचना पाठवली जाईल.' : 'स्मरणपत्र जतन झाले. फोनवर सूचना मिळवण्यासाठी परवानगी सुरू करा.');
      await load();
    } catch (exception) {
      setError(exception.message || 'स्मरणपत्र जतन करता आले नाही.');
    } finally {
      setSaving(false);
    }
  }

  async function togglePush() {
    setPushBusy(true); setError(''); setNotice(''); setPushWarning('');
    try {
      if (pushEnabled) {
        await disableBrowserPush(apiFetch, apiBase, onUnauthorized, userId);
        setPushEnabled(false);
        setNotice('या डिव्हाइसवरील सूचना बंद केल्या.');
      } else {
        await enableBrowserPush(apiFetch, apiBase, onUnauthorized, userId);
        setPushEnabled(true);
        setNotice('या डिव्हाइसवर सूचना सुरू झाल्या. आधी जतन केलेली प्रलंबित स्मरणपत्रेही यात समाविष्ट आहेत.');
      }
    } catch (exception) {
      setError(exception.message || 'डिव्हाइस सूचना सेट करता आल्या नाहीत.');
    } finally {
      setPushBusy(false);
    }
  }

  async function testPush() {
    setPushBusy(true); setNotice(''); setPushWarning('');
    try {
      await sendBrowserPushTest(apiFetch, apiBase, onUnauthorized, userId);
      setNotice('चाचणी सूचना पाठवली. फोनवरील सूचना तपासा.');
    } catch (exception) {
      setPushWarning(exception.message || 'चाचणी सूचना पाठवता आली नाही.');
    } finally { setPushBusy(false); }
  }

  function startVoiceInput() {
    if (recognitionRef.current) { try { recognitionRef.current.stop(); } catch {} return; }
    const SpeechRecognition = speechRecognitionConstructor();
    if (!SpeechRecognition) { reasonInputRef.current?.focus(); setVoiceNotice(speechUnavailableNotice()); return; }
    let recognition;
    try { recognition = new SpeechRecognition(); }
    catch { setVoiceNotice(speechUnavailableNotice()); return; }
    let interim = '';
    let receivedResult = false;
    let hadError = false;
    recognition.lang = 'mr-IN'; recognition.continuous = false; recognition.interimResults = true;
    recognition.onstart = () => { setListening(true); setVoiceNotice(''); };
    recognition.onresult = event => {
      for (let index = event.resultIndex ?? 0; index < event.results.length; index += 1) {
        const result = event.results[index];
        const transcript = result?.[0]?.transcript?.trim() || '';
        if (result?.isFinal && transcript && !receivedResult) {
          receivedResult = true;
          setForm(current => ({ ...current, reason: `${current.reason ? `${current.reason} ` : ''}${transcript}`.slice(0, 300) }));
        } else if (!result?.isFinal) interim = transcript;
      }
    };
    recognition.onerror = event => {
      hadError = true;
      setListening(false);
      setVoiceNotice(speechErrorNotice(event.error));
    };
    recognition.onend = () => {
      if (!hadError && !receivedResult && interim) {
        setForm(current => ({ ...current, reason: `${current.reason ? `${current.reason} ` : ''}${interim}`.slice(0, 300) }));
      } else if (!hadError && !receivedResult) setVoiceNotice(speechErrorNotice('no-speech'));
      setListening(false);
      if (recognitionRef.current === recognition) recognitionRef.current = null;
    };
    recognitionRef.current = recognition;
    setListening(true);
    setVoiceNotice('मायक्रोफोन सुरू करत आहे…');
    try { recognition.start(); } catch { recognitionRef.current = null; setListening(false); setVoiceNotice('मायक्रोफोन सुरू झाला नाही. कीबोर्डवरील माइक वापरा.'); }
  }

  function speakReminder(item) {
    if (!('speechSynthesis' in window)) { setNotice('या ब्राउझरमध्ये आवाजात वाचण्याची सुविधा नाही.'); return; }
    window.speechSynthesis.cancel();
    const speech = new SpeechSynthesisUtterance(`${item.cropOrPlot ? `${item.cropOrPlot}. ` : ''}${item.reason}`);
    speech.lang = 'mr-IN'; window.speechSynthesis.speak(speech);
  }

  async function remove(id) {
    if (!window.confirm('हे स्मरणपत्र हटवायचे आहे का?')) return;
    setError('');
    try {
      const response = await apiFetch(`${apiBase}/reminders/${id}`, { method: 'DELETE' });
      if (response.status === 401) { onUnauthorized(); return; }
      if (!response.ok) throw Error('स्मरणपत्र हटवता आले नाही.');
      setReminders(current => current.filter(item => item.id !== id));
      setNotice('स्मरणपत्र हटवले.');
    } catch (exception) { setError(exception.message || 'स्मरणपत्र हटवता आले नाही.'); }
  }

  async function complete(id) {
    setCompletingId(id); setError(''); setNotice('');
    try {
      const response = await apiFetch(`${apiBase}/reminders/${id}/complete`, { method: 'PATCH' });
      if (response.status === 401) { onUnauthorized(); return; }
      const result = await response.json().catch(() => null);
      if (!response.ok) throw Error(getBodyError(result, 'स्मरणपत्र पूर्ण करता आले नाही.'));
      await load(true);
      setNotice(result?.repeatFrequency && result.repeatFrequency !== 'NONE'
        ? 'काम पूर्ण झाले. पुढील पुनरावृत्तीचे स्मरणपत्रही जतन झाले.'
        : 'स्मरणपत्र पूर्ण झाले.');
    } catch (exception) { setError(exception.message || 'स्मरणपत्र पूर्ण करता आले नाही.'); }
    finally { setCompletingId(null); }
  }

  async function reschedule(id, scheduledAt) {
    setRescheduleSaving(true); setError(''); setNotice('');
    try {
      const response = await apiFetch(`${apiBase}/reminders/${id}/reschedule`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scheduledAt })
      });
      if (response.status === 401) { onUnauthorized(); return; }
      const result = await response.json().catch(() => null);
      if (!response.ok) throw Error(getBodyError(result, 'वेळ बदलता आली नाही.'));
      setReminders(current => current.map(item => item.id === id ? result : item));
      setRescheduleDraft(null);
      setNotice('स्मरणपत्राची वेळ बदलली.');
    } catch (exception) { setError(exception.message || 'वेळ बदलता आली नाही.'); }
    finally { setRescheduleSaving(false); }
  }

  async function stopRepeat(item) {
    if (!window.confirm('या कामाची पुढील पुनरावृत्ती थांबवायची आहे का?')) return;
    setError('');
    try {
      const response = await apiFetch(`${apiBase}/reminders/${item.id}/stop-repeat`, { method: 'PATCH' });
      if (response.status === 401) { onUnauthorized(); return; }
      const result = await response.json().catch(() => null);
      if (!response.ok) throw Error(getBodyError(result, 'पुनरावृत्ती थांबवता आली नाही.'));
      setReminders(current => current.map(reminder => reminder.id === item.id ? result : reminder));
      setNotice('पुढील पुनरावृत्ती थांबवली.');
    } catch (exception) { setError(exception.message || 'पुनरावृत्ती थांबवता आली नाही.'); }
  }

  async function checkWeather() {
    if (!weatherPincode) return;
    setWeatherLoading(true); setWeatherError(''); setWeatherResult(null);
    try {
      const response = await apiFetch(`${apiBase}/weather/${weatherPincode}`);
      if (response.status === 401) { onUnauthorized(); return; }
      const result = await response.json().catch(() => null);
      if (!response.ok) throw Error(getBodyError(result, 'जिल्ह्याची हवामान माहिती मिळाली नाही.'));
      setWeatherResult(result);
    } catch (exception) { setWeatherError(exception.message || 'हवामान माहिती मिळाली नाही.'); }
    finally { setWeatherLoading(false); }
  }

  const now = Date.now();
  const soonCount = reminders.filter(item => item.status === 'PENDING' && reminderEpoch(item.scheduledAt) > now && reminderEpoch(item.scheduledAt) <= now + 24 * 60 * 60 * 1000).length;
  const filteredReminders = reminders.filter(item => {
    const due = reminderEpoch(item.scheduledAt);
    const overdue = item.status === 'PENDING' && due <= now;
    if (statusFilter === 'UPCOMING' && (item.status !== 'PENDING' || overdue)) return false;
    if (statusFilter === 'OVERDUE' && !overdue) return false;
    if (statusFilter === 'COMPLETED' && item.status !== 'COMPLETED') return false;
    if (selectedDay && item.scheduledAt.slice(0, 10) !== selectedDay) return false;
    if (soonOnly && !(item.status === 'PENDING' && due > now && due <= now + 24 * 60 * 60 * 1000)) return false;
    return `${item.reason} ${item.cropOrPlot || ''}`.toLocaleLowerCase('mr-IN').includes(search.trim().toLocaleLowerCase('mr-IN'));
  });
  const statusLabel = { PENDING: 'प्रलंबित', SENDING: 'सूचना पाठवत आहे', SENT: 'सूचना पाठवल्या', COMPLETED: 'पूर्ण', FAILED: 'सूचना अयशस्वी' };

  return <section className="reminder-page" lang="mr">
    <div className="reminder-hero">
      <div className="reminder-hero-icon"><Bell size={22}/></div>
      <div className="reminder-hero-copy"><span className="eyebrow">शेतीकामासाठी वेळेवर आठवण</span><h1>स्मरणपत्रे</h1><p>पीकनिहाय कामे ठरवा, सूचना मिळवा आणि पूर्ण झालेली कामे नोंदवा.</p></div>
      <button type="button" className={`reminder-push-toggle${pushEnabled ? ' enabled' : ''}`} onClick={togglePush} disabled={pushBusy || saving}>
        {pushBusy ? <LoaderCircle className="spin" size={16}/> : pushEnabled ? <BellOff size={16}/> : <Bell size={16}/>}
        {pushBusy ? 'सेट होत आहे' : pushEnabled ? 'सूचना बंद करा' : 'डिव्हाइस सूचना सुरू करा'}
      </button>
    </div>
    {error && <div className="reminder-error" role="alert">{error}</div>}
    {notice && <div className="reminder-success" role="status"><CheckCircle2 size={16}/>{notice}</div>}
    <section className="reminder-notification-status" aria-label="फोनवरील सूचना">
      <div><b><Bell size={16}/>{pushEnabled ? 'फोनवरील सूचना सुरू आहेत' : 'स्मरणपत्राची सूचना फोनवर मिळवा'}</b><p>{pushEnabled
        ? 'अॅप बंद असतानाही सूचना येऊ शकते. फोनला इंटरनेट असणे आणि Settings मध्ये स्वर्णभूमीच्या Notifications सुरू असणे आवश्यक आहे.'
        : '“डिव्हाइस सूचना सुरू करा” निवडा आणि फोनवर परवानगी द्या. स्मरणपत्र जतन करतानाही ही परवानगी देता येईल.'}</p>{pushWarning && <p className="reminder-notification-warning" role="alert">{pushWarning}</p>}</div>
      {pushEnabled && <button type="button" className="reminder-push-toggle" onClick={testPush} disabled={pushBusy || saving}>{pushBusy ? <LoaderCircle className="spin" size={15}/> : <Bell size={15}/>} चाचणी सूचना</button>}
    </section>

    <section className="reminder-weather-card" aria-label="हवामानानुसार शेतीकाम">
      <div className="reminder-weather-heading"><div className="reminder-weather-icon"><CloudSun size={18}/></div><div><span>हवामान तपासणी</span><h2>आज बाहेरची कामे ठरवण्यापूर्वी जिल्ह्याचा इशारा पाहा</h2></div></div>
      <div className="reminder-weather-controls">
        {pincodeOptions.length ? <select aria-label="शेताचा पिनकोड" value={weatherPincode} onChange={event => setWeatherPincode(event.target.value)}>{pincodeOptions.map(pin => <option key={pin} value={pin}>{pin}</option>)}</select> : <span>पिनकोड असलेली दैनिक नोंद जोडा.</span>}
        <button type="button" className="outline" onClick={checkWeather} disabled={!weatherPincode || weatherLoading}>{weatherLoading ? <LoaderCircle className="spin" size={15}/> : <CloudSun size={15}/>} IMD इशारा तपासा</button>
        {soonCount > 0 && <button type="button" className="reminder-weather-filter" onClick={() => { setSoonOnly(true); setSelectedDay(null); setStatusFilter('ALL'); }}>पुढील २४ तासांची {soonCount.toLocaleString('mr-IN')} कामे पहा</button>}
      </div>
      {weatherError && <div className="reminder-weather-error" role="alert">{weatherError}</div>}
      {weatherResult && <div className="reminder-weather-result"><b>{weatherResult.district}, {weatherResult.state}</b><p>{weatherResult.warning}</p><span>हा जिल्हास्तरीय इशारा आहे; शेतातील प्रत्यक्ष हवामान वेगळे असू शकते. बाहेरील कामाचा वेळ बदलण्यापूर्वी स्थानिक स्थिती तपासा.</span><a href={weatherResult.sourceUrl} target="_blank" rel="noreferrer">IMD स्रोत उघडा</a></div>}
    </section>

    <div className="reminder-layout">
      <form className="reminder-form" onSubmit={save}>
        <div className="reminder-section-title"><div><span>नवीन आठवण</span><h2>स्मरणपत्र तयार करा</h2></div><span className="reminder-step">IST</span></div>
        <label className="reminder-field"><span>तारीख आणि वेळ</span><input type="datetime-local" value={form.scheduledAt} min={indiaDateTimeInput(new Date(Date.now() + 60 * 1000))} onChange={event => set('scheduledAt', event.target.value)} required/><small>वेळ भारताच्या स्थानिक वेळेनुसार (IST) घेतली जाईल.</small></label>
        <label className="reminder-field"><span>काम / तपासणी <small>मराठीत लिहा</small></span><textarea ref={reasonInputRef} lang="mr" rows="3" maxLength={300} value={form.reason} onChange={event => set('reason', event.target.value)} placeholder="उदा. उद्या सकाळी कांद्याला पाणी द्यायचे आहे..." required/><small className="reminder-counter">{form.reason.length}/३००</small></label>
        <div className="reminder-voice-row"><button type="button" className={`reminder-voice-button${listening ? ' listening' : ''}`} onClick={startVoiceInput}>{listening ? <MicOff size={15}/> : <Mic size={15}/>} {listening ? 'ऐकत आहे…' : 'बोलून भरा'}</button>{voiceNotice && <span role="status">{voiceNotice}</span>}</div>
        <label className="reminder-field"><span>पीक / शेताचा विभाग <small>ऐच्छिक</small></span><SpeechTextInput><input list="reminder-crop-options" maxLength={120} value={form.cropOrPlot} onChange={event => set('cropOrPlot', event.target.value)} placeholder="उदा. कांदा — पूर्वेकडील शेत"/></SpeechTextInput><datalist id="reminder-crop-options">{cropOptions.map(option => <option key={option} value={option}/>)}</datalist></label>
        <label className="reminder-field"><span>पुनरावृत्ती</span><select value={form.repeatFrequency} onChange={event => setForm(current => ({ ...current, repeatFrequency: event.target.value, repeatUntil: event.target.value === 'NONE' ? '' : current.repeatUntil }))}><option value="NONE">एकदाच</option><option value="DAILY">दररोज</option><option value="WEEKLY">दर आठवड्याला</option></select></label>
        {form.repeatFrequency !== 'NONE' && <label className="reminder-field"><span>पुनरावृत्तीची शेवटची तारीख <small>ऐच्छिक</small></span><input type="date" min={form.scheduledAt.slice(0, 10)} value={form.repeatUntil} onChange={event => set('repeatUntil', event.target.value)}/><small>रिकामे ठेवल्यास, थांबवेपर्यंत पुनरावृत्ती सुरू राहील.</small></label>}
        <small className="reminder-notification-note"><Bell size={13}/>{pushEnabled ? 'ठरलेल्या वेळी या डिव्हाइसवर सूचना पाठवली जाईल.' : isBrowserPushDisabled(userId) ? 'फोनवरील सूचना बंद आहेत. आठवण मिळवण्यासाठी सूचना सुरू करा.' : 'जतन करताना फोनवरील सूचनांची परवानगी मागितली जाईल.'}</small>
        <button className="primary reminder-submit" disabled={saving || pushBusy}>{saving ? <LoaderCircle className="spin" size={17}/> : <Plus size={17}/>} स्मरणपत्र जतन करा</button>
      </form>

      <div className="reminder-list-section">
        <div className="reminder-section-title"><div><span>तुमची यादी</span><h2>जतन केलेली स्मरणपत्रे</h2></div><span className="reminder-count">{reminders.length.toLocaleString('mr-IN')}</span></div>
        <div className="reminder-week-block"><div className="reminder-week-heading"><b>पुढील ७ दिवस</b><button type="button" onClick={() => { setSelectedDay(null); setSoonOnly(false); }}>संपूर्ण यादी</button></div><div className="reminder-week-days">{weekDays.map(date => {
          const count = reminders.filter(item => item.status === 'PENDING' && item.scheduledAt.slice(0, 10) === date.key).length;
          return <button type="button" key={date.key} className={selectedDay === date.key ? 'selected' : ''} aria-pressed={selectedDay === date.key} onClick={() => { setSelectedDay(current => current === date.key ? null : date.key); setSoonOnly(false); }}><span>{date.label}</span><b>{Number(date.day).toLocaleString('mr-IN')}</b><small>{count ? `${count.toLocaleString('mr-IN')} कामे` : '—'}</small></button>;
        })}</div></div>
        <div className="reminder-list-tools"><label className="reminder-search"><Search size={15}/><SpeechTextInput><input value={search} onChange={event => setSearch(event.target.value)} placeholder="काम किंवा पीक शोधा"/></SpeechTextInput></label><div className="reminder-filters" role="group" aria-label="स्मरणपत्र फिल्टर"><button type="button" className={statusFilter === 'ALL' ? 'selected' : ''} onClick={() => setStatusFilter('ALL')}>सर्व</button><button type="button" className={statusFilter === 'UPCOMING' ? 'selected' : ''} onClick={() => setStatusFilter('UPCOMING')}>येणारी</button><button type="button" className={statusFilter === 'OVERDUE' ? 'selected' : ''} onClick={() => setStatusFilter('OVERDUE')}>मुदत संपली</button><button type="button" className={statusFilter === 'COMPLETED' ? 'selected' : ''} onClick={() => setStatusFilter('COMPLETED')}>पूर्ण</button></div></div>
        {soonOnly && <div className="reminder-filter-note">पुढील २४ तासांतील कामे <button type="button" onClick={() => setSoonOnly(false)}><X size={13}/> काढा</button></div>}
        {selectedDay && <div className="reminder-filter-note">{new Date(`${selectedDay}T00:00:00+05:30`).toLocaleDateString('mr-IN', { day: 'numeric', month: 'long' })}<button type="button" onClick={() => setSelectedDay(null)}><X size={13}/> काढा</button></div>}
        {loading ? <div className="reminder-empty"><LoaderCircle className="spin" size={22}/> स्मरणपत्रे लोड होत आहेत...</div> : filteredReminders.length ? <div className="reminder-list">{filteredReminders.map(item => {
          const overdue = item.status === 'PENDING' && reminderEpoch(item.scheduledAt) <= Date.now();
          return <article className={`reminder-card${overdue ? ' reminder-card-overdue' : ''}`} key={item.id}>
            <div className="reminder-card-top"><span className={`reminder-status status-${item.status.toLowerCase()}`}><span/>{overdue ? 'वेळ निघून गेली' : statusLabel[item.status] || item.status}</span><button type="button" className="reminder-delete" onClick={() => remove(item.id)} aria-label="स्मरणपत्र हटवा" title="हटवा"><Trash2 size={15}/></button></div>
            <div className="reminder-card-date"><CalendarDays size={16}/><b>{reminderDateTime(item.scheduledAt)}</b></div>
            <p>{item.reason}</p>
            {(item.cropOrPlot || item.repeatFrequency !== 'NONE') && <div className="reminder-card-tags">{item.cropOrPlot && <span>{item.cropOrPlot}</span>}{item.repeatFrequency !== 'NONE' && <span><Repeat2 size={12}/>{item.repeatFrequency === 'DAILY' ? 'दररोज' : 'दर आठवड्याला'}</span>}</div>}
            <div className="reminder-card-bottom"><span><Bell size={14}/>{item.deliveryNote || (pushEnabled ? 'या डिव्हाइसवर सूचना' : 'सूचना बंद')}</span></div>
            <div className="reminder-card-actions"><button type="button" onClick={() => speakReminder(item)} aria-label="स्मरणपत्र ऐका"><Volume2 size={14}/> ऐका</button>{item.status === 'PENDING' && <><button type="button" onClick={() => void reschedule(item.id, indiaDateTimeInput(new Date(Date.now() + 60 * 60 * 1000)))}><RotateCcw size={14}/> १ तास पुढे</button><button type="button" onClick={() => void reschedule(item.id, indiaDateTimeInput(new Date(Date.now() + 24 * 60 * 60 * 1000)))}><CalendarDays size={14}/> उद्या</button><button type="button" onClick={() => setRescheduleDraft({ id: item.id, scheduledAt: item.scheduledAt })}><CalendarDays size={14}/> वेळ बदला</button><button type="button" className="reminder-complete" disabled={completingId === item.id} onClick={() => complete(item.id)}>{completingId === item.id ? <LoaderCircle className="spin" size={14}/> : <CheckCircle2 size={14}/>} पूर्ण करा</button>{item.repeatFrequency && item.repeatFrequency !== 'NONE' && <button type="button" onClick={() => stopRepeat(item)}>पुनरावृत्ती थांबवा</button>}</>}</div>
            {item.status === 'FAILED' && <div className="reminder-failure-note" role="status"><b>सूचना पाठवता आली नाही</b><span>{item.deliveryNote || 'सूचना पाठवताना अडचण आली.'}</span></div>}
          </article>;
        })}</div> : <div className="reminder-empty"><div className="reminder-empty-icon"><Bell size={20}/></div><b>या फिल्टरमध्ये स्मरणपत्र नाही</b><span>दुसरा दिवस किंवा फिल्टर निवडा.</span></div>}
      </div>
    </div>

    {rescheduleDraft && <div className="reminder-modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !rescheduleSaving) setRescheduleDraft(null); }}>
      <form className="reminder-reschedule-modal" role="dialog" aria-modal="true" aria-labelledby="reminder-reschedule-title" onSubmit={event => { event.preventDefault(); void reschedule(rescheduleDraft.id, rescheduleDraft.scheduledAt); }}>
        <button type="button" className="reminder-modal-close" onClick={() => setRescheduleDraft(null)} disabled={rescheduleSaving} aria-label="बंद करा"><X size={18}/></button>
        <div className="reminder-empty-icon"><CalendarDays size={20}/></div><span>स्मरणपत्राची वेळ</span><h2 id="reminder-reschedule-title">नवीन वेळ निवडा</h2>
        <label className="reminder-field">तारीख आणि वेळ (IST)<input type="datetime-local" min={indiaDateTimeInput(new Date(Date.now() + 60 * 1000))} value={rescheduleDraft.scheduledAt} onChange={event => setRescheduleDraft(current => ({ ...current, scheduledAt: event.target.value }))} required/></label>
        <button type="submit" className="primary reminder-submit" disabled={rescheduleSaving}>{rescheduleSaving ? <LoaderCircle className="spin" size={16}/> : <CalendarDays size={16}/>} वेळ जतन करा</button>
      </form>
    </div>}
  </section>;
}
