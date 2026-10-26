import React, { useCallback, useEffect, useState } from 'react';
import { Ban, Check, ChevronDown, CircleAlert, LoaderCircle, MessageCircle, Send, ShieldCheck, Star, Undo2 } from 'lucide-react';
import SpeechTextInput from './SpeechTextInput.jsx';
import './marketplaceEngagement.css';
import './sellerReviews.css';

const stageNames = {
  REQUESTED: 'विनंती पाठवली', ACCEPTED: 'स्वीकारली', PREPARING: 'तयारी सुरू',
  READY_FOR_PICKUP: 'घेण्यासाठी तयार', OUT_FOR_DELIVERY: 'वितरणासाठी निघाली',
  COMPLETED: 'व्यवहार पूर्ण', DECLINED: 'नाकारली'
};
const stageFlow = fulfillment => ['REQUESTED', 'ACCEPTED', 'PREPARING', fulfillment === 'DELIVERY' ? 'OUT_FOR_DELIVERY' : 'READY_FOR_PICKUP', 'COMPLETED'];

export function OrderProgress({ order }) {
  const flow = stageFlow(order.fulfillment);
  const current = order.fulfillmentStage || ({ PENDING: 'REQUESTED', ACCEPTED: 'ACCEPTED', COMPLETED: 'COMPLETED', DECLINED: 'DECLINED' }[order.status] || 'REQUESTED');
  if (current === 'DECLINED' || order.status === 'DECLINED') return <div className="order-progress declined"><CircleAlert size={15}/><span>ऑर्डर नाकारली</span></div>;
  const currentIndex = flow.indexOf(current);
  return <div className="order-progress" aria-label={`ऑर्डरची स्थिती: ${stageNames[current] || current}`}><div className="order-progress-track">{flow.map((stage, index) => <div className={`order-progress-step ${index <= currentIndex ? 'done' : ''} ${index === currentIndex ? 'current' : ''}`} key={stage}><span>{index < currentIndex ? <Check size={11}/> : index + 1}</span><small>{stageNames[stage]}</small></div>)}</div></div>;
}

export function MarketplaceConversation({ orderId, userId, request, unreadCount = 0, onRead }) {
  const [open, setOpen] = useState(false);
  const [conversation, setConversation] = useState(null);
  const [body, setBody] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const load = useCallback(async () => {
    try {
      const response = await request(`/marketplace/orders/${orderId}/messages`);
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.message || 'संदेश मिळाले नाहीत.');
      setConversation(result); setError('');
    } catch (exception) { setError(exception.message || 'संदेश मिळाले नाहीत.'); }
    finally { setLoading(false); }
  }, [orderId, request]);
  useEffect(() => { if (open) { setLoading(true); void load().then(() => onRead?.()); const timer = window.setInterval(load, 10000); return () => window.clearInterval(timer); } return undefined; }, [open, load, onRead]);
  async function send(event) {
    event.preventDefault(); if (!body.trim()) return;
    setSending(true); setError(''); setNotice('');
    try {
      const response = await request(`/marketplace/orders/${orderId}/messages`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ body }) });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.message || 'संदेश पाठवता आला नाही.');
      setBody(''); await load();
    } catch (exception) { setError(exception.message || 'संदेश पाठवता आला नाही.'); }
    finally { setSending(false); }
  }
  async function setBlock(blocked) {
    setError(''); setNotice('');
    try {
      const response = await request(`/marketplace/orders/${orderId}/block`, { method: blocked ? 'POST' : 'DELETE' });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.message || 'चर्चेची सेटिंग बदलता आली नाही.');
      setConversation(current => ({ ...current, ...result })); setNotice(blocked ? 'या ऑर्डरवरील चर्चा थांबवली.' : 'चर्चा पुन्हा सुरू केली.');
    } catch (exception) { setError(exception.message || 'चर्चेची सेटिंग बदलता आली नाही.'); }
  }
  async function report(messageId) {
    const reason = window.prompt('या संदेशाची तक्रार करण्याचे कारण लिहा:');
    if (!reason?.trim()) return;
    try {
      const response = await request(`/marketplace/messages/${messageId}/report`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason }) });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.message || 'तक्रार पाठवता आली नाही.');
      setNotice(result.message || 'तक्रार प्रशासकाकडे पाठवली.'); setError('');
    } catch (exception) { setError(exception.message || 'तक्रार पाठवता आली नाही.'); }
  }
  return <section className="market-private-chat">
    <button type="button" className="market-chat-toggle" onClick={() => setOpen(value => !value)} aria-expanded={open}><MessageCircle size={15}/><span>खाजगी ऑर्डर चर्चा</span>{unreadCount > 0 && <b className="market-unread-badge market-chat-unread" aria-label={`${unreadCount.toLocaleString('mr-IN')} न वाचलेले संदेश`}>{unreadCount.toLocaleString('mr-IN')}</b>}<small>संपर्क तपशील शेअर करता येत नाहीत</small><ChevronDown size={15} className={open ? 'expanded' : ''}/></button>
    {open && <div className="market-chat-body"><div className="market-chat-top"><span><ShieldCheck size={14}/> फक्त या ऑर्डरमधील खरेदीदार आणि विक्रेता</span><div>{conversation?.blockedByMe ? <button type="button" onClick={() => setBlock(false)}><Undo2 size={13}/> चर्चा सुरू करा</button> : <button type="button" onClick={() => setBlock(true)}><Ban size={13}/> चर्चा थांबवा</button>}<button type="button" onClick={() => { setLoading(true); void load().then(() => onRead?.()); }} aria-label="संदेश रिफ्रेश करा"><LoaderCircle size={13} className={loading ? 'spin' : ''}/></button></div></div>
      {error && <p className="market-chat-error" role="alert">{error}</p>}{notice && <p className="market-chat-notice" role="status">{notice}</p>}
      <div className="market-chat-messages">{loading && !conversation ? <span>संदेश लोड होत आहेत…</span> : !conversation?.messages?.length ? <span>ऑर्डरची वेळ, मालाची तयारी किंवा वितरणाबद्दल येथे बोला.</span> : conversation.messages.map(message => <article className={`market-chat-message ${message.senderUserId === userId ? 'mine' : ''}`} key={message.id}><div><b>{message.senderUserId === userId ? 'तुम्ही' : message.senderRole === 'buyer' ? 'खरेदीदार' : 'विक्रेता'}</b><time>{new Date(message.createdAt).toLocaleString('mr-IN', { dateStyle: 'short', timeStyle: 'short' })}</time></div><p>{message.body}</p>{message.senderUserId !== userId && <button type="button" onClick={() => void report(message.id)}>तक्रार करा</button>}</article>)}</div>
      {conversation?.blockedByOther && <p className="market-chat-error">समोरच्या व्यक्तीने ही चर्चा थांबवली आहे.</p>}
      <form className="market-chat-form" onSubmit={send}><SpeechTextInput><textarea maxLength={1000} rows="2" value={body} onChange={event => setBody(event.target.value)} placeholder="ऑर्डरबद्दल संदेश लिहा…" disabled={!conversation?.canMessage || sending}/></SpeechTextInput><button type="submit" disabled={!conversation?.canMessage || !body.trim() || sending}><Send size={15}/>{sending ? 'पाठवत आहे…' : 'पाठवा'}</button></form><small className="market-chat-privacy">फोन नंबर, ईमेल, पत्ता किंवा बाहेरील दुवे पाठवता येणार नाहीत.</small>
    </div>}
  </section>;
}

export function SellerReviewForm({ order, request, onSubmitted }) {
  const [rating, setRating] = useState(5); const [comment, setComment] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [done, setDone] = useState(false);
  if (order.status !== 'COMPLETED') return null;
  if (order.reviewSubmitted || done) return <p className="market-review-pending"><ShieldCheck size={14}/> धन्यवाद! तुमचे रेटिंग प्रशासकाच्या तपासणीनंतर दिसेल.</p>;
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const response = await request(`/marketplace/orders/${order.id}/review`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ rating, comment }) });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw Error(result.message || 'रेटिंग जतन करता आले नाही.');
      setDone(true); onSubmitted?.(order.id);
    } catch (exception) { setError(exception.message || 'रेटिंग जतन करता आले नाही.'); }
    finally { setBusy(false); }
  }
  return <form className="market-review-form" onSubmit={submit}><b>व्यवहार कसा झाला?</b><div className="market-review-stars" role="radiogroup" aria-label="१ ते ५ तारे निवडा">{[1, 2, 3, 4, 5].map(value => <button type="button" key={value} aria-label={`${value} तारे`} aria-pressed={rating === value} onClick={() => setRating(value)}><Star size={19} fill={value <= rating ? 'currentColor' : 'none'}/></button>)}</div><SpeechTextInput><textarea rows="2" maxLength={500} value={comment} onChange={event => setComment(event.target.value)} placeholder="विक्रेत्याबद्दल छोटा अभिप्राय (ऐच्छिक)"/></SpeechTextInput>{error && <small className="market-chat-error">{error}</small>}<button className="market-review-submit" type="submit" disabled={busy}>{busy ? 'जतन होत आहे…' : 'रेटिंग पाठवा'}</button></form>;
}

export function SellerReviews({ listing, request }) {
  const [open, setOpen] = useState(false); const [reviews, setReviews] = useState([]); const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  useEffect(() => {
    if (!open || reviews.length) return undefined;
    let active = true; setLoading(true);
    request(`/marketplace/listings/${listing.id}/reviews`).then(async response => {
      const result = await response.json().catch(() => []);
      if (!response.ok) throw Error(result.message || 'रेटिंग मिळाली नाही.');
      if (active) { setReviews(result); setError(''); }
    }).catch(exception => { if (active) setError(exception.message || 'रेटिंग मिळाली नाही.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [listing.id, open, request, reviews.length]);
  if (Number(listing.sellerReviewCount) < 1) return null;
  return <div className="market-seller-reviews"><button type="button" onClick={() => setOpen(value => !value)} aria-expanded={open}><Star size={12} fill="currentColor"/>{Number(listing.sellerRating).toFixed(1)} · {Number(listing.sellerReviewCount).toLocaleString('mr-IN')} रेटिंग {open ? 'लपवा' : 'पहा'}</button>{open && <div>{loading ? <small>रेटिंग लोड होत आहेत…</small> : error ? <small>{error}</small> : reviews.map((review, index) => <article key={`${review.createdAt}-${index}`}><span>{'★'.repeat(Number(review.rating || 0))}{'☆'.repeat(Math.max(0, 5 - Number(review.rating || 0)))}</span><time>{review.createdAt ? new Date(review.createdAt).toLocaleDateString('mr-IN', { dateStyle: 'medium' }) : ''}</time><p>{review.comment || 'खरेदीदाराने रेटिंग दिले.'}</p></article>)}</div>}</div>;
}

export function nextSellerStage(order) {
  const current = order.fulfillmentStage || ({ PENDING: 'REQUESTED', ACCEPTED: 'ACCEPTED' }[order.status] || order.status);
  if (current === 'ACCEPTED') return 'PREPARING';
  if (current === 'PREPARING') return order.fulfillment === 'DELIVERY' ? 'OUT_FOR_DELIVERY' : 'READY_FOR_PICKUP';
  if (current === 'READY_FOR_PICKUP' || current === 'OUT_FOR_DELIVERY') return 'COMPLETED';
  return null;
}

export const stageLabel = stage => stageNames[stage] || stage;
