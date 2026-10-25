import React, { useState } from 'react';
import { ArrowLeft, KeyRound, LockKeyhole, ShieldCheck, Smartphone } from 'lucide-react';
import './auth.css';

async function sendRequest(url, payload) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || body.detail || 'विनंती पूर्ण करता आली नाही.');
  return body;
}

export default function AuthPanel({ account, apiBase, onAuthenticated, onBack }) {
  const [mode, setMode] = useState('login');
  const [mobileNumber, setMobileNumber] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const canRegister = account.id !== 'admin';

  function changeMode(nextMode) {
    setMode(nextMode);
    setError('');
    setNotice('');
    setPassword('');
    setConfirmPassword('');
    setCode('');
  }

  async function submitPassword(event) {
    event.preventDefault();
    setError('');
    setNotice('');
    if (mode === 'register' && password !== confirmPassword) {
      setError('दोन्ही पासवर्ड जुळत नाहीत.');
      return;
    }
    setBusy(true);
    try {
      const route = mode === 'register' ? '/register' : '/login';
      const result = await sendRequest(`${apiBase}/auth${route}`, { mobileNumber, password, role: account.id });
      onAuthenticated(result);
    } catch (requestError) {
      setError(requestError.message || 'प्रवेश करता आला नाही.');
    } finally {
      setBusy(false);
    }
  }

  async function requestCode(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await sendRequest(`${apiBase}/auth/otp/request`, { mobileNumber, role: account.id });
      setNotice(`${result.message} SMS आल्यास खालील OTP भरा.`);
      setMode('otp-verify');
    } catch (requestError) {
      setError(requestError.message || 'OTP पाठवता आला नाही.');
    } finally {
      setBusy(false);
    }
  }

  async function verifyCode(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const result = await sendRequest(`${apiBase}/auth/otp/verify`, { mobileNumber, code, role: account.id });
      onAuthenticated(result);
    } catch (requestError) {
      setError(requestError.message || 'OTP तपासता आला नाही.');
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-screen" lang="mr">
    <section className="auth-card" aria-labelledby="auth-title">
      <button className="auth-back" type="button" onClick={onBack}><ArrowLeft size={17}/> खाते निवडा</button>
      <div className="auth-heading">
        <span className={`account-role-avatar role-${account.id}`}>{account.initials}</span>
        <span className="auth-eyebrow">स्वर्णभूमी · {account.label} खाते</span>
        <h1 id="auth-title">{mode === 'register' ? 'नवीन खाते तयार करा' : mode.startsWith('otp') ? 'OTP ने प्रवेश करा' : 'तुमच्या खात्यात प्रवेश करा'}</h1>
        <p>{mode === 'register' ? 'मोबाईल क्रमांक आणि पासवर्ड वापरून खाते नोंदवा.' : 'तुमचा मोबाईल क्रमांक या खात्याच्या माहितीशी जुळला पाहिजे.'}</p>
      </div>

      {mode === 'otp-verify' ? <form className="auth-form" onSubmit={verifyCode}>
        <div className="auth-mobile-summary"><Smartphone size={17}/><span>{mobileNumber}</span><button type="button" onClick={() => changeMode('otp')}>बदला</button></div>
        <label><span>SMS मधील ६ अंकी OTP</span><input autoFocus inputMode="numeric" autoComplete="one-time-code" value={code} onChange={event => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} required minLength={6} maxLength={6} placeholder="००००००"/></label>
        {notice && <p className="auth-notice" role="status">{notice}</p>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="auth-submit" disabled={busy || code.length !== 6}>{busy ? 'तपासत आहे…' : 'OTP तपासा आणि प्रवेश करा'}</button>
        <button className="auth-text-button" type="button" onClick={() => changeMode('otp')}>नवीन OTP मागवा</button>
      </form> : mode === 'otp' ? <form className="auth-form" onSubmit={requestCode}>
        <label><span>मोबाईल क्रमांक</span><input type="tel" inputMode="tel" autoComplete="tel" value={mobileNumber} onChange={event => setMobileNumber(event.target.value)} maxLength={18} required placeholder="९८७६५४३२१०"/></label>
        {notice && <p className="auth-notice" role="status">{notice}</p>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="auth-submit" disabled={busy}>{busy ? 'OTP पाठवत आहे…' : <><KeyRound size={17}/> OTP पाठवा</>}</button>
        <button className="auth-text-button" type="button" onClick={() => changeMode('login')}>पासवर्ड वापरा</button>
      </form> : <form className="auth-form" onSubmit={submitPassword}>
        <label><span>मोबाईल क्रमांक</span><input type="tel" inputMode="tel" autoComplete="tel" value={mobileNumber} onChange={event => setMobileNumber(event.target.value)} maxLength={18} required placeholder="९८७६५४३२१०"/></label>
        <label><span>{mode === 'register' ? 'पासवर्ड तयार करा' : 'पासवर्ड'}</span><input type="password" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} minLength={8} maxLength={72} required placeholder="किमान ८ अक्षरे"/></label>
        {mode === 'register' && <label><span>पासवर्ड पुन्हा लिहा</span><input type="password" autoComplete="new-password" value={confirmPassword} onChange={event => setConfirmPassword(event.target.value)} minLength={8} maxLength={72} required/></label>}
        {mode === 'register' && account.id === 'admin' && <p className="auth-notice">प्रशासक खाते सार्वजनिक sign up मधून तयार करता येत नाही. प्रणाली प्रशासकाकडून खाते तयार करून घ्या.</p>}
        {mode === 'login' && account.id === 'admin' && <p className="auth-notice">प्रशासक खाते प्रणाली प्रशासकाकडून तयार केलेले असावे.</p>}
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="auth-submit" disabled={busy || (mode === 'register' && !canRegister)}>{busy ? 'कृपया थांबा…' : <><LockKeyhole size={17}/> {mode === 'register' ? 'खाते तयार करा' : 'लॉगिन करा'}</>}</button>
        {canRegister && <button className="auth-switch" type="button" onClick={() => changeMode(mode === 'register' ? 'login' : 'register')}>
          {mode === 'register' ? 'आधीच खाते आहे? लॉगिन करा' : 'खाते नाही? नवीन खाते तयार करा'}
        </button>}
      </form>}

      <div className="auth-privacy"><ShieldCheck size={16}/><span>तुमची शेतीची नोंद या खाते प्रकारातील तुमच्या लॉगिनपुरतीच दिसेल.</span></div>
    </section>
  </main>;
}
