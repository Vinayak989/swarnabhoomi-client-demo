import React, { useEffect, useState } from 'react';
import { UserRound } from 'lucide-react';
import './accountAvatar.css';

export default function AccountAvatar({ photoUrl, apiFetch, className = '', label = 'प्रोफाइल फोटो' }) {
  const [source, setSource] = useState('');
  useEffect(() => {
    setSource('');
    if (!photoUrl || !apiFetch) return undefined;
    let active = true;
    let objectUrl;
    const controller = new AbortController();
    apiFetch(photoUrl, { signal: controller.signal, cache: 'no-store' })
      .then(response => { if (!response.ok) throw new Error('Photo unavailable'); return response.blob(); })
      .then(blob => { if (active) { objectUrl = URL.createObjectURL(blob); setSource(objectUrl); } })
      .catch(() => { if (active) setSource(''); });
    return () => { active = false; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [photoUrl, apiFetch]);
  return <span className={`account-avatar ${className}`}>
    {source ? <img src={source} alt={label} onError={() => setSource('')}/> : <UserRound size={24} aria-label={label}/>}
  </span>;
}
