import { useEffect, useRef, useState } from 'react';
import { BellPlus, Bot, Camera, CheckCircle2, ImagePlus, LoaderCircle, Mic, MicOff, Send, Sprout, Volume2, X } from 'lucide-react';
import './assistant.css';

const MAX_ORIGINAL_PHOTO_SIZE = 10_000_000;
const MAX_COMPRESSED_PHOTO_SIZE = 4_000_000;
const suggestions = [
  'टोमॅटोच्या पानांवर डाग पडले आहेत, काय करू?',
  'उन्हाळ्यात भाजीपाला किती वेळा पाणी द्यावे?',
  'मातीचा पोत सुधारण्यासाठी काय करता येईल?'
];

function indiaDateTimeInput(date) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  }).formatToParts(date).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

function reminderReasonFromAnswer(answer) {
  const firstLine = answer.split('\n').map(line => line.trim()).find(Boolean) || '';
  return (firstLine.replace(/^[\s*#\-•\d.)]+/, '').trim() || 'शेतीचे काम तपासणे').slice(0, 180);
}

function money(value) {
  return `₹${Number(value || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
}

async function preparePhoto(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    throw Error('JPG, PNG किंवा WEBP फोटो निवडा.');
  }
  if (file.size > MAX_ORIGINAL_PHOTO_SIZE) throw Error('मूळ फोटोचा आकार १० MB पेक्षा कमी असावा.');
  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d');
    if (!context) throw Error('फोटो प्रक्रिया करता आली नाही.');
    context.fillStyle = '#fff';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.82));
    if (!blob) throw Error('फोटो प्रक्रिया करता आली नाही.');
    if (blob.size > MAX_COMPRESSED_PHOTO_SIZE) throw Error('फोटो खूप मोठा आहे. कृपया कमी रिझोल्यूशनचा फोटो निवडा.');
    const dataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(Error('फोटो वाचता आला नाही.'));
      reader.readAsDataURL(blob);
    });
    return { dataUrl, previewUrl: URL.createObjectURL(blob), name: file.name };
  } finally {
    bitmap?.close();
  }
}

export default function AssistantPanel({ apiFetch, apiBase, messages, setMessages, farmData, onUnauthorized }) {
  const [question, setQuestion] = useState('');
  const [photo, setPhoto] = useState(null);
  const [sending, setSending] = useState(false);
  const [listening, setListening] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [useFarmContext, setUseFarmContext] = useState(true);
  const [reminderDraft, setReminderDraft] = useState(null);
  const [reminderSaving, setReminderSaving] = useState(false);
  const [reminderError, setReminderError] = useState('');
  const [savedReminderIndices, setSavedReminderIndices] = useState(() => new Set());
  const recognitionRef = useRef(null);
  const conversationRef = useRef(null);
  const cameraInputRef = useRef(null);
  const libraryInputRef = useRef(null);
  const previewUrlsRef = useRef(new Set());

  useEffect(() => {
    conversationRef.current?.scrollTo({ top: conversationRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, sending]);

  useEffect(() => () => {
    recognitionRef.current?.abort();
    window.speechSynthesis?.cancel();
    previewUrlsRef.current.forEach(url => URL.revokeObjectURL(url));
  }, []);

  async function selectPhoto(file) {
    if (!file) return;
    setError('');
    setNotice('फोटो तयार करत आहे…');
    try {
      const prepared = await preparePhoto(file);
      previewUrlsRef.current.add(prepared.previewUrl);
      setPhoto(prepared);
      setNotice('');
    } catch (exception) {
      setError(exception.message || 'फोटो जोडता आला नाही.');
      setNotice('');
    }
  }

  async function sendQuestion(value, speakAnswer = false, attachedPhoto = photo) {
    const content = value.trim();
    if ((!content && !attachedPhoto) || sending) return;
    const userMessage = {
      role: 'user',
      content: content || 'या फोटोमध्ये दिसणाऱ्या पिकाबद्दल मार्गदर्शन करा.',
      imageUrl: attachedPhoto?.previewUrl
    };
    const nextMessages = [...messages, userMessage].slice(-12);
    setMessages(nextMessages);
    setError('');
    setNotice('');
    setSending(true);
    try {
      const requestMessages = nextMessages.map((message, index) => ({
        role: message.role,
        content: message.content,
        ...(attachedPhoto && index === nextMessages.length - 1 ? { imageDataUrl: attachedPhoto.dataUrl } : {})
      }));
      const response = await apiFetch(`${apiBase}/assistant/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          messages: requestMessages,
          farmContext: useFarmContext && farmData?.summary?.recordCount ? farmData.context : null
        })
      });
      const result = await response.json().catch(() => null);
      if (response.status === 401) {
        onUnauthorized();
        throw Error('पुन्हा लॉगिन करा.');
      }
      if (!response.ok) {
        const message = result?.detail || result?.message || (response.status === 502
          ? 'Java सर्व्हर सुरू नाही. आधी backend सर्व्हर सुरू करा.'
          : response.status === 503
            ? 'Google Gemini उपलब्ध नाही. सर्व्हरवरील Gemini API key तपासा.'
            : 'AI कडून उत्तर मिळाले नाही.');
        throw Error(message);
      }
      if (!result?.answer) throw Error('AI कडून वाचता येणारे उत्तर मिळाले नाही.');
      setMessages(current => [...current, { role: 'assistant', content: result.answer }]);
      setQuestion('');
      setPhoto(null);
      if (speakAnswer) speak(result.answer);
    } catch (exception) {
      setMessages(current => current.filter(message => message !== userMessage));
      setError(exception instanceof TypeError
        ? 'सर्व्हरशी संपर्क होऊ शकला नाही. Java सर्व्हर सुरू आहे का ते तपासा.'
        : exception.message || 'प्रश्न पाठवता आला नाही.');
    } finally {
      setSending(false);
    }
  }

  function speak(text) {
    if (!window.speechSynthesis) {
      setNotice('या ब्राउझरमध्ये उत्तर मोठ्याने वाचण्याची सुविधा उपलब्ध नाही.');
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = /[\u0900-\u097f]/.test(text) ? 'mr-IN' : 'en-IN';
    window.speechSynthesis.speak(utterance);
  }

  function toggleMicrophone() {
    if (listening) {
      recognitionRef.current?.stop();
      return;
    }
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setNotice('आवाजातून प्रश्न विचारण्यासाठी Chrome वापरा; किंवा खाली प्रश्न लिहा.');
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = 'mr-IN';
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onstart = () => {
      setListening(true);
      setNotice('ऐकत आहे… मराठीत तुमचा प्रश्न विचारा.');
    };
    recognition.onresult = event => {
      const transcript = event.results?.[0]?.[0]?.transcript?.trim();
      if (transcript) void sendQuestion(transcript, true);
    };
    recognition.onerror = event => {
      if (event.error === 'not-allowed') setNotice('मायक्रोफोन वापरण्यासाठी ब्राउझरची परवानगी द्या.');
      else if (event.error !== 'aborted') setNotice('आवाज ओळखता आला नाही; पुन्हा प्रयत्न करा.');
    };
    recognition.onend = () => setListening(false);
    recognitionRef.current = recognition;
    try {
      recognition.start();
    } catch {
      setNotice('मायक्रोफोन सुरू करता आला नाही; पुन्हा प्रयत्न करा.');
    }
  }

  function submit(event) {
    event.preventDefault();
    void sendQuestion(question);
  }

  function analyzeFarmRecords() {
    if (!farmData?.summary?.recordCount || sending) return;
    void sendQuestion('माझ्या शेतीच्या नोंदींचा आढावा द्या. नोंदवलेला खर्च, उत्पन्न आणि उत्पादन समजावून सांगा; अलीकडच्या नोंदींमध्ये दिसणारे बदल किंवा वारंवार दिसणाऱ्या अडचणी नमूद करा. अपुरी माहिती असल्यास स्पष्ट सांगा.', false, null);
  }

  function openReminder(answer, index) {
    setReminderError('');
    setReminderDraft({
      sourceIndex: index,
      scheduledAt: indiaDateTimeInput(new Date(Date.now() + 24 * 60 * 60 * 1000)),
      reason: reminderReasonFromAnswer(answer)
    });
  }

  async function saveReminder(event) {
    event.preventDefault();
    if (!reminderDraft) return;
    setReminderSaving(true);
    setReminderError('');
    try {
      const response = await apiFetch(`${apiBase}/reminders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scheduledAt: reminderDraft.scheduledAt, reason: reminderDraft.reason })
      });
      if (response.status === 401) {
        onUnauthorized();
        throw Error('पुन्हा लॉगिन करा.');
      }
      const result = await response.json().catch(() => null);
      if (!response.ok) throw Error(result?.message || 'स्मरणपत्र जतन करता आले नाही.');
      setSavedReminderIndices(current => new Set([...current, reminderDraft.sourceIndex]));
      setReminderDraft(null);
      setNotice('सल्ल्यावर आधारित स्मरणपत्र जतन झाले. ते स्मरणपत्रे विभागात दिसेल.');
    } catch (exception) {
      setReminderError(exception.message || 'स्मरणपत्र जतन करता आले नाही.');
    } finally {
      setReminderSaving(false);
    }
  }

  return <section className="assistant-page" lang="mr">
    <div className="assistant-hero">
      <div className="assistant-hero-icon"><Sprout size={24}/></div>
      <div>
        <span className="eyebrow">तुमच्या शेतीसाठी AI मदतनीस</span>
        <h1>शेतीबद्दल काहीही विचारा</h1>
        <p>मराठीत बोला किंवा लिहा. प्रश्नाच्या भाषेत सोपे मार्गदर्शन मिळवा.</p>
      </div>
    </div>
    {farmData?.summary?.recordCount > 0 ? <section className="assistant-farm-card" aria-label="शेती नोंदींचा सारांश">
      <div className="assistant-farm-heading">
        <div><span>तुमच्या नोंदींवरून</span><h2>शेतीचा एकूण आढावा</h2></div>
        <label className="assistant-context-toggle"><input type="checkbox" checked={useFarmContext} onChange={event => setUseFarmContext(event.target.checked)}/> AI उत्तरात नोंदी वापरा</label>
      </div>
      <div className="assistant-farm-metrics">
        <div><span>नोंदी</span><b>{farmData.summary.recordCount.toLocaleString('mr-IN')}</b></div>
        <div><span>एकूण खर्च</span><b>{money(farmData.summary.totalCostRs)}</b></div>
        <div><span>विक्री उत्पन्न</span><b>{money(farmData.summary.totalSalesRs)}</b></div>
        <div><span>उत्पादन</span><b>{Number(farmData.summary.totalProductionKg || 0).toLocaleString('mr-IN', { maximumFractionDigits: 1 })} किलो</b></div>
        <div><span>निव्वळ शिल्लक</span><b>{money(farmData.summary.netRs)}</b></div>
      </div>
      <div className="assistant-farm-footer">
        <span>{useFarmContext ? 'सल्ला मागताना एकूण आकडे आणि अलीकडच्या ५ नोंदी Gemini कडे जातील. दैनिक नोंदीतील फोटो, पिनकोड आणि स्थान पाठवले जात नाही; प्रश्नासोबत जोडलेला फोटो पाठवला जातो.' : 'तुमच्या नोंदींचा सारांश फक्त या स्क्रीनवर दिसतो; AI कडे पाठवला जाणार नाही.'}</span>
        <button type="button" className="assistant-analysis-button" onClick={analyzeFarmRecords} disabled={!useFarmContext || sending}><Sprout size={15}/> नोंदींचे AI विश्लेषण</button>
      </div>
    </section> : <div className="assistant-farm-empty"><Sprout size={16}/> दैनिक नोंदी केल्यावर AI तुमच्या शेताच्या नोंदींवर आधारित उत्तर देऊ शकेल.</div>}
    <div className="assistant-card">
      <div className="assistant-chat" ref={conversationRef} aria-live="polite">
        {!messages.length && <div className="assistant-welcome">
          <div className="assistant-welcome-mark"><Bot size={23}/></div>
          <h2>नमस्कार शेतकरी मित्रा!</h2>
          <p>पीक, माती, पाणी किंवा शेतीकामाबद्दल प्रश्न विचारा.</p>
          <div className="assistant-suggestions">{suggestions.map(item =>
            <button type="button" key={item} disabled={sending} onClick={() => setQuestion(item)}>{item}</button>
          )}</div>
        </div>}
        {messages.map((message, index) =>
          <article className={`assistant-message ${message.role}`} key={`${index}-${message.role}`}>
            <div className="assistant-message-icon">{message.role === 'assistant' ? <Bot size={16}/> : 'शे'}</div>
            <div className="assistant-message-content">
              <span>{message.role === 'assistant' ? 'AI शेती सहाय्यक' : 'तुम्ही'}</span>
              <p>{message.content}</p>
              {message.imageUrl && <img className="assistant-attachment" src={message.imageUrl} alt="तुमच्या प्रश्नासोबतचा शेताचा फोटो"/>}
              {message.role === 'assistant' && <div className="assistant-message-actions">
                <button type="button" className="assistant-speak" onClick={() => speak(message.content)} aria-label="उत्तर मोठ्याने ऐका" title="उत्तर मोठ्याने ऐका"><Volume2 size={15}/><span>ऐका</span></button>
                <button type="button" className="assistant-reminder-button" disabled={savedReminderIndices.has(index)} onClick={() => openReminder(message.content, index)}>{savedReminderIndices.has(index) ? <CheckCircle2 size={14}/> : <BellPlus size={14}/>} {savedReminderIndices.has(index) ? 'स्मरणपत्र जतन झाले' : 'सल्ल्यावर स्मरणपत्र'}</button>
              </div>}
            </div>
          </article>
        )}
        {sending && <div className="assistant-thinking"><LoaderCircle className="spin" size={17}/> उत्तर तयार करत आहे…</div>}
      </div>
      {error && <div className="assistant-error" role="alert">{error}</div>}
      {notice && <div className="assistant-notice" role="status">{notice}</div>}
      <form className="assistant-composer" onSubmit={submit}>
        {photo && <div className="assistant-photo-preview">
          <img src={photo.previewUrl} alt={`प्रश्नासाठी जोडलेला फोटो: ${photo.name}`}/>
          <span>{photo.name}</span>
          <button type="button" onClick={() => setPhoto(null)} aria-label="फोटो काढून टाका" title="फोटो काढून टाका"><X size={15}/></button>
        </div>}
        <textarea
          aria-label="शेतीविषयक प्रश्न"
          value={question}
          onChange={event => setQuestion(event.target.value)}
          placeholder="तुमचा शेतीविषयक प्रश्न इथे लिहा…"
          maxLength={2000}
          rows={2}
          disabled={sending}
        />
        <div className="assistant-composer-actions">
          <span>प्रश्न व जोडलेला फोटो Google Gemini API कडे पाठवले जातात. नोंदींचा वापर सुरू असल्यास वर दिलेला सारांशही पाठवला जाईल. महत्त्वाच्या निर्णयांपूर्वी स्थानिक कृषी तज्ज्ञांचा सल्ला घ्या.</span>
          <div>
            <input ref={cameraInputRef} className="assistant-file-input" type="file" accept="image/jpeg,image/png,image/webp" capture="environment" onChange={event => { void selectPhoto(event.target.files?.[0]); event.target.value = ''; }}/>
            <input ref={libraryInputRef} className="assistant-file-input" type="file" accept="image/jpeg,image/png,image/webp" onChange={event => { void selectPhoto(event.target.files?.[0]); event.target.value = ''; }}/>
            <button type="button" className="assistant-photo-button" onClick={() => cameraInputRef.current?.click()} disabled={sending} aria-label="फोटो काढा" title="फोनच्या कॅमेऱ्याने फोटो काढा"><Camera size={17}/></button>
            <button type="button" className="assistant-photo-button" onClick={() => libraryInputRef.current?.click()} disabled={sending} aria-label="गॅलरीतून फोटो निवडा" title="फोन किंवा संगणकातून फोटो निवडा"><ImagePlus size={17}/></button>
            <button type="button" className={`assistant-mic ${listening ? 'listening' : ''}`} onClick={toggleMicrophone} disabled={sending} aria-label={listening ? 'ऐकणे थांबवा' : 'बोलून प्रश्न विचारा'} title={listening ? 'ऐकणे थांबवा' : 'बोलून प्रश्न विचारा'}>
              {listening ? <MicOff size={18}/> : <Mic size={18}/>}
            </button>
            <button type="submit" className="primary assistant-send" disabled={sending || (!question.trim() && !photo)}>
              {sending ? <LoaderCircle className="spin" size={17}/> : <Send size={16}/>} विचारा
            </button>
          </div>
        </div>
      </form>
    </div>
    {reminderDraft && <div className="assistant-reminder-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !reminderSaving) setReminderDraft(null); }}>
      <form className="assistant-reminder-modal" onSubmit={saveReminder}>
        <button type="button" className="assistant-reminder-close" onClick={() => setReminderDraft(null)} disabled={reminderSaving} aria-label="बंद करा"><X size={18}/></button>
        <div className="assistant-reminder-icon"><BellPlus size={20}/></div>
        <span>AI सल्ल्याची आठवण</span><h2>स्मरणपत्र तयार करा</h2>
        <label>काम किंवा तपासणी<input value={reminderDraft.reason} onChange={event => setReminderDraft(current => ({ ...current, reason: event.target.value }))} maxLength={300} required/></label>
        <label>तारीख आणि वेळ (भारतीय वेळ)<input type="datetime-local" min={indiaDateTimeInput(new Date())} value={reminderDraft.scheduledAt} onChange={event => setReminderDraft(current => ({ ...current, scheduledAt: event.target.value }))} required/></label>
        {reminderError && <div className="assistant-error" role="alert">{reminderError}</div>}
        <p>स्मरणपत्र जतन झाल्यावर ते स्मरणपत्रे विभागात दिसेल.</p>
        <button type="submit" className="primary assistant-reminder-save" disabled={reminderSaving}>{reminderSaving ? <LoaderCircle className="spin" size={16}/> : <BellPlus size={16}/>} स्मरणपत्र जतन करा</button>
      </form>
    </div>}
  </section>;
}
