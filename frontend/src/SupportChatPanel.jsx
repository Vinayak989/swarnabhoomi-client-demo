import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, Headphones, LoaderCircle, MessageCircle, RefreshCw, Send, X } from 'lucide-react';
import SpeechTextInput from './SpeechTextInput.jsx';
import './supportChat.css';

const POLL_MS = 15000;

async function responseJson(response, fallback) {
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || fallback);
  return data;
}

function messageTime(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('mr-IN', {
    day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit'
  });
}

function roleName(role) {
  return role === 'seller' ? 'विक्रेता' : 'शेतकरी';
}

function Conversation({ admin, title, subtitle, messages, draft, setDraft, onSend, sending, loading, error }) {
  const bottomRef = useRef(null);
  const lastMessageId = messages.at(-1)?.id;

  useEffect(() => {
    const container = bottomRef.current?.parentElement;
    container?.scrollTo({ top: container.scrollHeight, behavior: 'smooth' });
  }, [lastMessageId]);

  return <section className="support-conversation" aria-label={title}>
    <header className="support-conversation-head">
      <span className="support-avatar"><Headphones size={21}/></span>
      <div><h2>{title}</h2><p>{subtitle}</p></div>
    </header>
    <div className="support-messages" role="log" aria-live="polite" aria-label="मदत संभाषण">
      {loading && !messages.length && <div className="support-loading"><LoaderCircle size={18} className="spin"/> संदेश मिळवत आहे…</div>}
      {!loading && !messages.length && <div className="support-empty-chat">
        <MessageCircle size={32}/>
        <h3>{admin ? 'संभाषण सुरू झालेले नाही' : 'अडचण सांगा, आम्ही मदतीसाठी आहोत'}</h3>
        <p>{admin ? 'शेतकरी किंवा विक्रेता संदेश पाठवल्यावर तो इथे दिसेल.' : 'अॅप वापरताना आलेली अडचण लिहा. प्रशासक तुमच्या संभाषणात उत्तर देतील.'}</p>
        {!admin && <div className="support-suggestions">
          {['फोटो जोडताना अडचण', 'नोंद जतन होत नाही', 'शेतमाल विक्रीबाबत मदत'].map(text =>
            <button type="button" key={text} onClick={() => setDraft(text)}>{text}</button>)}
        </div>}
      </div>}
      {messages.map(message => {
        const mine = admin ? message.senderRole === 'admin' : message.senderRole !== 'admin';
        return <div className={`support-message ${mine ? 'mine' : 'theirs'}`} key={message.id}>
          <span className="support-message-sender">{message.senderRole === 'admin' ? 'प्रशासक' : roleName(message.senderRole)}</span>
          <p>{message.body}</p>
          <small>{messageTime(message.createdAt)}</small>
        </div>;
      })}
      <div ref={bottomRef}/>
    </div>
    <form className="support-compose" onSubmit={onSend}>
      {error && <p className="support-error" role="alert">{error}</p>}
      <label htmlFor={admin ? 'support-admin-message' : 'support-user-message'}>तुमचा संदेश</label>
      <SpeechTextInput><textarea id={admin ? 'support-admin-message' : 'support-user-message'}
        value={draft} onChange={event => setDraft(event.target.value)} maxLength={2000} rows={3}
        placeholder={admin ? 'उत्तर लिहा…' : 'अॅप वापरताना काय अडचण येत आहे?'} /></SpeechTextInput>
      <div className="support-compose-actions">
        <small>{draft.length.toLocaleString('mr-IN')} / २०००</small>
        <button type="submit" disabled={sending || !draft.trim()}>
          {sending ? <LoaderCircle size={16} className="spin"/> : <Send size={16}/>}
          {sending ? 'पाठवत आहे…' : 'संदेश पाठवा'}
        </button>
      </div>
    </form>
  </section>;
}

function ParticipantSupport({ apiFetch, apiBase, onUnreadChange }) {
  const [chat, setChat] = useState({ messages: [] });
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const data = await responseJson(await apiFetch(`${apiBase}/support/my`, { cache: 'no-store' }), 'संदेश मिळाले नाहीत.');
      setChat(data);
      onUnreadChange(0);
      setError('');
    } catch (exception) {
      if (!silent) setError(exception.message);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [apiFetch, apiBase, onUnreadChange]);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => { void refresh(true); }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  async function send(event) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || sending) return;
    setSending(true);
    setError('');
    try {
      await responseJson(await apiFetch(`${apiBase}/support/my/messages`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ body })
      }), 'संदेश पाठवता आला नाही.');
      setDraft('');
      await refresh(true);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setSending(false);
    }
  }

  return <main className="support-page" lang="mr">
    <header className="support-page-hero">
      <span><Headphones size={17}/> स्वर्णभूमी मदत केंद्र</span>
      <h1>प्रशासकाशी संवाद</h1>
      <p>अॅप वापरताना काही अडचण आली तर इथे संदेश पाठवा. उत्तर याच संभाषणात दिसेल.</p>
      <button type="button" onClick={() => void refresh()} disabled={loading}><RefreshCw size={15}/> संदेश तपासा</button>
    </header>
    <Conversation admin={false} title="स्वर्णभूमी मदत" subtitle="तुमचे संदेश फक्त तुम्हाला आणि प्रशासकांना दिसतात"
      messages={chat.messages || []} draft={draft} setDraft={setDraft} onSend={send}
      sending={sending} loading={loading} error={error}/>
  </main>;
}

function AdminSupport({ apiFetch, apiBase, onUnreadChange }) {
  const [threads, setThreads] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [selectedId, setSelectedId] = useState(null);
  const [chat, setChat] = useState(null);
  const [draft, setDraft] = useState('');
  const [loading, setLoading] = useState(true);
  const [chatLoading, setChatLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const selectedThread = threads.find(thread => thread.threadId === selectedId);

  const refreshInbox = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const data = await responseJson(await apiFetch(`${apiBase}/support/admin/threads?page=${page}&size=25`, { cache: 'no-store' }),
        'मदत संभाषणे मिळाली नाहीत.');
      const rows = data.content || [];
      setThreads(rows);
      setTotalPages(Math.max(1, data.totalPages || 1));
      setSelectedId(current => rows.some(row => row.threadId === current) ? current : rows[0]?.threadId ?? null);
      const unread = await responseJson(await apiFetch(`${apiBase}/support/unread`, { cache: 'no-store' }), 'नवीन संदेश तपासता आले नाहीत.');
      onUnreadChange(Number(unread.count || 0));
      setError('');
    } catch (exception) {
      if (!silent) setError(exception.message);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [apiFetch, apiBase, page, onUnreadChange]);

  useEffect(() => {
    void refreshInbox();
    const timer = window.setInterval(() => { void refreshInbox(true); }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [refreshInbox, refreshKey]);

  useEffect(() => {
    if (selectedId == null) { setChat(null); return; }
    let active = true;
    setChatLoading(true);
    const refreshChat = async () => {
      try {
        const data = await responseJson(await apiFetch(`${apiBase}/support/admin/threads/${selectedId}`, { cache: 'no-store' }),
          'संभाषण मिळाले नाही.');
        if (active) {
          setChat(data);
          setThreads(current => current.map(thread =>
            thread.threadId === selectedId ? { ...thread, unreadCount: 0 } : thread));
          setError('');
          const unread = await responseJson(await apiFetch(`${apiBase}/support/unread`, { cache: 'no-store' }),
            'नवीन संदेश तपासता आले नाहीत.');
          if (active) onUnreadChange(Number(unread.count || 0));
        }
      } catch (exception) {
        if (active) setError(exception.message);
      } finally {
        if (active) setChatLoading(false);
      }
    };
    void refreshChat();
    const timer = window.setInterval(() => { void refreshChat(); }, POLL_MS);
    return () => { active = false; window.clearInterval(timer); };
  }, [apiFetch, apiBase, selectedId, onUnreadChange, refreshKey]);

  async function send(event) {
    event.preventDefault();
    const body = draft.trim();
    if (!body || sending || selectedId == null) return;
    setSending(true);
    setError('');
    try {
      await responseJson(await apiFetch(`${apiBase}/support/admin/threads/${selectedId}/messages`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ body })
      }), 'उत्तर पाठवता आले नाही.');
      setDraft('');
      setRefreshKey(current => current + 1);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setSending(false);
    }
  }

  return <main className="support-page support-admin-page" lang="mr">
    <header className="support-page-hero">
      <span><Headphones size={17}/> प्रशासक मदत कक्ष</span>
      <h1>शेतकरी आणि विक्रेता संदेश</h1>
      <p>वापरकर्त्यांच्या अडचणी वाचा आणि त्यांना थेट उत्तर द्या.</p>
      <button type="button" onClick={() => setRefreshKey(current => current + 1)} disabled={loading}>
        <RefreshCw size={15}/> रिफ्रेश करा
      </button>
    </header>
    {error && selectedId == null && <p className="support-error" role="alert">{error}</p>}
    <div className="support-admin-layout">
      <aside className="support-inbox" aria-label="मदत संभाषणे">
        <div className="support-inbox-head"><h2>संभाषणे</h2><span>{threads.length.toLocaleString('mr-IN')}</span></div>
        {loading && !threads.length && <div className="support-loading"><LoaderCircle size={18} className="spin"/> संभाषणे मिळवत आहे…</div>}
        {!loading && !threads.length && <p className="support-inbox-empty">अद्याप कोणताही मदत संदेश नाही.</p>}
        <div className="support-thread-items">{threads.map(thread =>
          <button type="button" key={thread.threadId}
            className={`support-thread-item ${selectedId === thread.threadId ? 'active' : ''}`}
            onClick={() => { setSelectedId(thread.threadId); setDraft(''); }}>
            <span className="support-thread-icon">{thread.participantName?.slice(0, 1) || 'शे'}</span>
            <span className="support-thread-copy">
              <strong>{thread.participantName}</strong>
              <small>{roleName(thread.participantRole)} · {thread.participantMobileNumber || 'मोबाईल उपलब्ध नाही'}</small>
              <span>{thread.lastMessage || 'संदेश नाही'}</span>
            </span>
            <span className="support-thread-meta"><small>{messageTime(thread.lastMessageAt)}</small>
              {thread.unreadCount > 0 && <b aria-label={`${thread.unreadCount} न वाचलेले संदेश`}>{thread.unreadCount > 99 ? '99+' : thread.unreadCount}</b>}
            </span>
          </button>)}</div>
        <div className="support-inbox-pages">
          <button type="button" onClick={() => setPage(current => Math.max(0, current - 1))} disabled={page === 0}><ChevronLeft size={16}/> मागील</button>
          <span>{(page + 1).toLocaleString('mr-IN')} / {totalPages.toLocaleString('mr-IN')}</span>
          <button type="button" onClick={() => setPage(current => current + 1)} disabled={page + 1 >= totalPages}>पुढील <ChevronRight size={16}/></button>
        </div>
      </aside>
      {selectedId == null ? <div className="support-no-selection"><MessageCircle size={34}/><h2>संभाषण निवडा</h2><p>नवीन मदत विनंती आल्यावर इथे उत्तर देता येईल.</p></div>
        : <Conversation admin title={selectedThread?.participantName || 'वापरकर्ता'}
          subtitle={`${roleName(selectedThread?.participantRole)} · ${selectedThread?.participantMobileNumber || 'मोबाईल उपलब्ध नाही'}`}
          messages={chat?.threadId === selectedId ? chat.messages : []}
          draft={draft} setDraft={setDraft} onSend={send} sending={sending}
          loading={chatLoading} error={error}/>}
    </div>
  </main>;
}

export default function SupportChatPanel({ role, apiFetch, apiBase, onUnreadChange }) {
  return role === 'admin'
    ? <AdminSupport apiFetch={apiFetch} apiBase={apiBase} onUnreadChange={onUnreadChange}/>
    : <ParticipantSupport apiFetch={apiFetch} apiBase={apiBase} onUnreadChange={onUnreadChange}/>;
}

export function SupportChatDialog({ role, apiFetch, apiBase, onUnreadChange, onClose }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.focus();
    const onKeyDown = event => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose]);

  return createPortal(<div className="support-modal-backdrop" onMouseDown={event => {
    if (event.target === event.currentTarget) onClose();
  }}>
    <section ref={dialogRef} className={`support-modal ${role === 'admin' ? 'support-modal-admin' : ''}`}
      role="dialog" aria-modal="true" aria-labelledby="support-modal-title" tabIndex={-1}>
      <div className="support-modal-toolbar">
        <span id="support-modal-title"><MessageCircle size={18}/> मदत व संवाद</span>
        <button type="button" onClick={onClose} aria-label="मदत संवादातून बाहेर पडा"><X size={20}/> बाहेर पडा</button>
      </div>
      <SupportChatPanel role={role} apiFetch={apiFetch} apiBase={apiBase} onUnreadChange={onUnreadChange}/>
    </section>
  </div>, document.body);
}
