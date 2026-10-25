import React from 'react';
import { ArrowUpRight, BadgeIndianRupee, BellRing, CalendarDays, Clock3, ExternalLink, Landmark, RefreshCw, Sprout } from 'lucide-react';
import './governmentSchemes.css';

const formatDate = value => value
  ? new Date(`${value}T00:00:00`).toLocaleDateString('mr-IN', { day: 'numeric', month: 'long', year: 'numeric' })
  : '';

const formatCheckedAt = value => value
  ? new Date(value).toLocaleString('mr-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
  : 'अद्यतन तपासत आहे';

export function GovernmentSchemeBanner({ announcements = [], onOpen }) {
  const notice = announcements.find(item => item.isNewToday && item.schemeRelated);
  if (!notice) return null;

  return (
    <button type="button" className="scheme-live-banner" onClick={onOpen} aria-live="polite">
      <span className="scheme-live-icon"><BellRing size={19}/></span>
      <span className="scheme-live-copy">
        <strong><i className="scheme-live-dot"/> आजची नवीन सरकारी योजना / घोषणा</strong>
        <span>{notice.title}</span>
        <small>{notice.sourceName} · {formatDate(notice.publishedOn)} · तपशील पाहा</small>
      </span>
      <ArrowUpRight className="scheme-live-arrow" size={20}/>
    </button>
  );
}

export default function GovernmentSchemesPanel({ feed, loading, refreshing, error, onRefresh }) {
  const schemes = feed?.schemes || [];
  const announcements = feed?.announcements || [];
  const sourceReady = feed?.pibAvailable || feed?.maharashtraAvailable;

  return (
    <section className="government-schemes" lang="mr">
      <header className="schemes-hero">
        <div className="schemes-hero-copy">
          <span className="schemes-eyebrow"><Sprout size={15}/> शेतकऱ्यांसाठी अधिकृत माहिती</span>
          <h1>सेंद्रिय शेतीसाठी सरकारी योजना</h1>
          <p>केंद्र व महाराष्ट्र शासनाच्या अधिकृत स्रोतांवरील योजना आणि ताज्या घोषणांची माहिती.</p>
        </div>
        <div className="schemes-hero-icon"><Landmark size={30}/></div>
      </header>

      {error && <div className="schemes-fetch-error" role="status">{error} {feed ? 'शेवटची मिळालेली माहिती खाली दिसेल.' : 'कृपया काही वेळाने पुन्हा तपासा.'}</div>}

      <div className="schemes-section-heading">
        <div><span>योजना समजून घ्या</span><h2>तुमच्यासाठी महत्त्वाच्या योजना</h2></div>
        <small><Clock3 size={14}/> शेवटची तपासणी: {formatCheckedAt(feed?.checkedAt)}</small>
      </div>

      {loading && !feed ? (
        <div className="schemes-loading"><RefreshCw className="scheme-spin" size={18}/> सरकारी स्रोतांवरील माहिती आणत आहोत…</div>
      ) : schemes.length ? (
        <div className="scheme-card-grid">
          {schemes.map((scheme, index) => (
            <article className={`scheme-card ${index === 0 ? 'scheme-card-primary' : ''}`} key={scheme.id}>
              <div className="scheme-card-topline">
                <span className="scheme-kind"><Sprout size={14}/>{scheme.category}</span>
                <BadgeIndianRupee className="scheme-card-mark" size={20}/>
              </div>
              <h3>{scheme.name}</h3>
              <p>{scheme.description}</p>
              <div className="scheme-benefit"><strong>मिळू शकणारे सहाय्य</strong><span>{scheme.benefit}</span></div>
              <div className="scheme-eligibility"><strong>अर्ज करण्यापूर्वी</strong><span>{scheme.eligibilityNote}</span></div>
              <div className="scheme-card-links">
                <a href={scheme.sourceUrl} target="_blank" rel="noreferrer">योजनेचा सरकारी स्रोत <ExternalLink size={14}/></a>
                <a href={scheme.stateGuidelinesUrl} target="_blank" rel="noreferrer">महाराष्ट्र मार्गदर्शक सूचना <ExternalLink size={14}/></a>
              </div>
              <small className="scheme-source-caption">माहिती स्रोत: {scheme.sourceName}</small>
            </article>
          ))}
        </div>
      ) : <div className="scheme-notices-empty"><Landmark size={20}/><span>योजना माहिती सध्या लोड झालेली नाही. खालील ताज्या सरकारी घोषणांच्या विभागातून पुन्हा तपासा.</span></div>}

      <section className="scheme-notices">
        <div className="scheme-notices-heading">
          <div><span>PIB आणि महाराष्ट्र कृषी विभाग</span><h2><CalendarDays size={19}/> ताज्या सरकारी घोषणा व शासन निर्णय</h2></div>
          <button type="button" className="scheme-refresh" onClick={onRefresh} disabled={loading || refreshing}>
            <RefreshCw className={refreshing ? 'scheme-spin' : ''} size={15}/>{refreshing ? 'तपासत आहे…' : 'आता तपासा'}
          </button>
        </div>
        {!sourceReady && !loading && <p className="scheme-source-warning">सरकारी संकेतस्थळांशी संपर्क झाला नाही. शेवटची जतन केलेली माहिती दाखवत आहोत.</p>}
        {announcements.length ? (
          <div className="scheme-notice-list">
            {announcements.map((notice, index) => (
              <article className={`scheme-notice ${notice.isNewToday ? 'scheme-notice-today' : ''}`} key={`${notice.sourceUrl}-${index}`}>
                <span className="scheme-notice-date">{notice.isNewToday && <b>आजची घोषणा</b>}{formatDate(notice.publishedOn)}</span>
                <div className="scheme-notice-copy"><h3>{notice.title}</h3><small>{notice.sourceName}</small></div>
                <a href={notice.sourceUrl} target="_blank" rel="noreferrer" aria-label={`${notice.title} — सरकारी संकेतस्थळावर उघडा`}><ExternalLink size={16}/></a>
              </article>
            ))}
          </div>
        ) : (
          <div className="scheme-notices-empty"><Landmark size={20}/><span>सेंद्रिय किंवा नैसर्गिक शेतीशी संबंधित नवीन सरकारी नोंदी सापडल्या नाहीत.</span></div>
        )}
        <p className="scheme-disclaimer"><ArrowUpRight size={14}/> योजनांची पात्रता, अर्जाची मुदत आणि उपलब्ध निधी बदलू शकतो. अर्ज करण्यापूर्वी अधिकृत सरकारी दुव्यावर किंवा स्थानिक कृषी कार्यालयात खात्री करा.</p>
      </section>
    </section>
  );
}
