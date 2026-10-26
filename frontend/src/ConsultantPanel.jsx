import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, LoaderCircle, MapPin, MessageCircle, MessageSquareText, Phone, Search, Sprout, X } from 'lucide-react';
import SpeechTextInput from './SpeechTextInput.jsx';
import './consultant.css';

const EMPTY_CONSULTATION = {
  farmerName: '',
  phone: '',
  crop: '',
  problem: '',
  preferredTime: ''
};

function phoneLinks(phone) {
  const digits = phone.replace(/\D/g, '');
  const internationalNumber = digits.startsWith('91') && digits.length === 12 ? digits : `91${digits}`;
  const dialNumber = phone.startsWith('+') ? phone.replace(/[^\d+]/g, '') : `+${internationalNumber}`;
  return {
    call: `tel:${dialNumber}`,
    sms: `sms:${dialNumber}`,
    whatsapp: `https://wa.me/${internationalNumber}`
  };
}

function ConsultantPanel({ apiBase, apiFetch, mobileNumber }) {
  const [consultants, setConsultants] = useState([]);
  const [search, setSearch] = useState('');
  const [areaFilter, setAreaFilter] = useState('');
  const [specialtyFilter, setSpecialtyFilter] = useState('');
  const [minimumExperience, setMinimumExperience] = useState('');
  const [requestingConsultant, setRequestingConsultant] = useState(null);
  const [consultation, setConsultation] = useState(EMPTY_CONSULTATION);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [payingConsultantId, setPayingConsultantId] = useState(null);

  async function loadConsultants() {
    setLoading(true);
    setError('');
    try {
      const response = await apiFetch(`${apiBase}/consultants`);
      const result = await response.json().catch(() => null);
      if (!response.ok) throw new Error(result?.message || 'सल्लागारांची यादी मिळाली नाही.');
      setConsultants(result);
    } catch (exception) {
      setError(exception.message || 'सल्लागारांची यादी मिळाली नाही.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadConsultants();
  }, [apiBase, apiFetch]);

  const filteredConsultants = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    const specialty = specialtyFilter.trim().toLocaleLowerCase();
    const years = Number(minimumExperience || 0);
    return consultants.filter(consultant => {
      const matchesSearch = !query ||
        `${consultant.name} ${consultant.serviceArea} ${consultant.expertise} ${consultant.introduction || ''}`
          .toLocaleLowerCase()
          .includes(query);
      const matchesArea = !areaFilter || consultant.serviceArea === areaFilter;
      const matchesSpecialty = !specialty || consultant.expertise.toLocaleLowerCase().includes(specialty);
      return matchesSearch && matchesArea && matchesSpecialty && Number(consultant.experienceYears) >= years;
    });
  }, [consultants, search, areaFilter, specialtyFilter, minimumExperience]);

  const serviceAreas = useMemo(() => Array.from(new Set(
    consultants.map(consultant => consultant.serviceArea).filter(Boolean)
  )).sort((a, b) => a.localeCompare(b, 'mr-IN')), [consultants]);

  const hasConsultantFilters = Boolean(search.trim() || areaFilter || specialtyFilter.trim() || minimumExperience);

  function openConsultationRequest(consultant) {
    setConsultation(EMPTY_CONSULTATION);
    setRequestingConsultant(consultant);
    setError('');
    setNotice('');
  }

  async function unlockConsultantContact(consultant) {
    if (payingConsultantId || !consultant.contactPaymentEnabled) return;
    setPayingConsultantId(consultant.id);
    setError('');
    setNotice('');
    try {
      const orderResponse = await apiFetch(`${apiBase}/consultants/${consultant.id}/contact-payment/order`, {
        method: 'POST'
      });
      const order = await orderResponse.json().catch(() => null);
      if (!orderResponse.ok) throw new Error(order?.message || 'पेमेंट सुरू करता आले नाही.');

      if (!window.Razorpay) {
        await new Promise((resolve, reject) => {
          const script = document.createElement('script');
          script.src = 'https://checkout.razorpay.com/v1/checkout.js';
          script.async = true;
          script.onload = resolve;
          script.onerror = () => reject(new Error('पेमेंट विंडो लोड झाली नाही. इंटरनेट तपासून पुन्हा प्रयत्न करा.'));
          document.body.appendChild(script);
        });
      }
      if (!window.Razorpay) throw new Error('पेमेंट विंडो उपलब्ध नाही.');

      const checkout = new window.Razorpay({
        key: order.keyId,
        amount: order.amount,
        currency: order.currency,
        name: 'स्वर्णभूमी',
        description: `${consultant.name} यांच्याशी संपर्क · ₹९९`,
        order_id: order.orderId,
        prefill: { contact: mobileNumber || '' },
        theme: { color: '#39784a' },
        handler: async payment => {
          try {
            const verifyResponse = await apiFetch(`${apiBase}/consultants/${consultant.id}/contact-payment/verify`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                orderId: payment.razorpay_order_id,
                paymentId: payment.razorpay_payment_id,
                signature: payment.razorpay_signature
              })
            });
            const verification = await verifyResponse.json().catch(() => null);
            if (!verifyResponse.ok || !verification?.unlocked) {
              throw new Error(verification?.message || 'पेमेंटची खात्री झाली नाही. संपर्क उघडलेला नाही.');
            }
            await loadConsultants();
            setNotice('₹९९ पेमेंट यशस्वी. आता कॉल, SMS किंवा WhatsApp मधून संपर्क करू शकता.');
          } catch (exception) {
            setError(exception.message || 'पेमेंटची खात्री करता आली नाही.');
          } finally {
            setPayingConsultantId(null);
          }
        },
        modal: { ondismiss: () => setPayingConsultantId(null) }
      });
      checkout.on('payment.failed', event => {
        setError(event?.error?.description || 'पेमेंट पूर्ण झाले नाही. पुन्हा प्रयत्न करा.');
        setPayingConsultantId(null);
      });
      checkout.open();
    } catch (exception) {
      setError(exception.message || 'पेमेंट सुरू करता आले नाही.');
      setPayingConsultantId(null);
    }
  }

  function sendConsultationRequest(event) {
    event.preventDefault();
    if (!requestingConsultant) return;
    const { whatsapp } = phoneLinks(requestingConsultant.phone);
    const message = [
      `नमस्कार ${requestingConsultant.name}, मला सेंद्रिय शेतीसाठी सल्ला हवा आहे.`,
      `माझे नाव: ${consultation.farmerName.trim()}`,
      `मोबाइल: ${consultation.phone.trim()}`,
      `पीक / प्रकार: ${consultation.crop.trim()}`,
      `समस्या: ${consultation.problem.trim()}`,
      consultation.preferredTime.trim() && `सोयीची वेळ: ${consultation.preferredTime.trim()}`
    ].filter(Boolean).join('\n');
    const link = document.createElement('a');
    link.href = `${whatsapp}?text=${encodeURIComponent(message)}`;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setRequestingConsultant(null);
    setNotice('WhatsAppमध्ये विनंतीचा संदेश तयार केला आहे. पाठवण्यासाठी Send दाबा.');
  }

  return <section className="consultant-page" lang="mr">
    <div className="consultant-hero">
      <div>
        <span className="consultant-eyebrow"><Sprout size={14}/> सेंद्रिय शेतीसाठी शेतकरी ते शेतकरी मार्गदर्शन</span>
        <h1>सेंद्रिय शेती सल्लागार</h1>
        <p>तुमच्या भागातील सल्लागारांचे कौशल्य, अनुभव आणि संपर्क तपशील पाहा.</p>
      </div>
    </div>

    {error && <div className="consultant-message consultant-error" role="alert">{error}</div>}
    {notice && <div className="consultant-message consultant-success" role="status"><CheckCircle2 size={16}/>{notice}</div>}

    <div className="consultant-directory-head">
      <div><span>समुदायातील मार्गदर्शक · {filteredConsultants.length.toLocaleString('mr-IN')} सल्लागार</span><h2>सल्लागार शोधा</h2></div>
      <label className="consultant-search"><Search size={16}/><SpeechTextInput><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="नाव, जिल्हा किंवा तज्ज्ञता शोधा" aria-label="सल्लागार शोधा"/></SpeechTextInput></label>
    </div>

    <div className="consultant-filters" aria-label="सल्लागार फिल्टर">
      <label><span>सेवा क्षेत्र</span><select value={areaFilter} onChange={event => setAreaFilter(event.target.value)}><option value="">सर्व क्षेत्रे</option>{serviceAreas.map(area => <option key={area} value={area}>{area}</option>)}</select></label>
      <label><span>तज्ज्ञता</span><SpeechTextInput><input value={specialtyFilter} onChange={event => setSpecialtyFilter(event.target.value)} placeholder="उदा. कीड व्यवस्थापन"/></SpeechTextInput></label>
      <label><span>किमान अनुभव</span><select value={minimumExperience} onChange={event => setMinimumExperience(event.target.value)}><option value="">काहीही</option><option value="5">५+ वर्षे</option><option value="10">१०+ वर्षे</option><option value="15">१५+ वर्षे</option></select></label>
      {hasConsultantFilters && <button type="button" className="consultant-clear-filters" onClick={() => { setSearch(''); setAreaFilter(''); setSpecialtyFilter(''); setMinimumExperience(''); }}>फिल्टर साफ करा</button>}
    </div>

    {loading ? <div className="consultant-empty"><LoaderCircle className="consultant-spin" size={22}/> सल्लागारांची यादी लोड होत आहे...</div>
      : filteredConsultants.length ? <div className="consultant-grid">{filteredConsultants.map(consultant => {
        const links = consultant.phone ? phoneLinks(consultant.phone) : null;
        return <article className="consultant-card" key={consultant.id}>
          <div className="consultant-card-top"><div className="consultant-avatar"><Sprout size={21}/></div><span className="consultant-experience">{consultant.experienceYears.toLocaleString('mr-IN')} वर्षांचा अनुभव</span></div>
          <h3>{consultant.name}</h3>
          <p className="consultant-expertise">{consultant.expertise}</p>
          <div className="consultant-area"><MapPin size={15}/><span>{consultant.serviceArea}</span></div>
          {consultant.introduction && <p className="consultant-introduction">{consultant.introduction}</p>}
          {links ? <>
            <div className="consultant-contact-actions" aria-label={`${consultant.name} यांच्याशी संपर्क`}>
              <a href={links.call} aria-label={`${consultant.name} यांना कॉल करा`}><Phone size={15}/> कॉल</a>
              <a href={links.sms} aria-label={`${consultant.name} यांना SMS करा`}><MessageSquareText size={15}/> SMS</a>
              <a href={links.whatsapp} target="_blank" rel="noopener noreferrer" aria-label={`${consultant.name} यांना WhatsApp करा`}><MessageCircle size={15}/> WhatsApp</a>
            </div>
            <button type="button" className="consultant-request-button" onClick={() => openConsultationRequest(consultant)}><MessageSquareText size={14}/> सल्ला विनंती पाठवा</button>
          </> : <div className="consultant-paywall">
            <p>कॉल, SMS आणि WhatsApp संपर्क उघडण्यासाठी एकदाच ₹९९ भरा.</p>
            <button type="button" className="consultant-request-button consultant-unlock-button"
              onClick={() => unlockConsultantContact(consultant)}
              disabled={!consultant.contactPaymentEnabled || payingConsultantId !== null}>
              {payingConsultantId === consultant.id ? <><LoaderCircle className="consultant-spin" size={14}/> पेमेंट सुरू होत आहे...</> : <>₹९९ भरून संपर्क उघडा</>}
            </button>
            {!consultant.contactPaymentEnabled && <small>ऑनलाइन पेमेंट सध्या उपलब्ध नाही.</small>}
          </div>}
        </article>;
      })}</div>
      : <div className="consultant-empty"><div className="consultant-empty-icon"><Sprout size={22}/></div><b>{hasConsultantFilters ? 'या फिल्टरसाठी सल्लागार सापडले नाहीत' : 'अजून सल्लागार नोंदणीकृत नाहीत'}</b><span>{hasConsultantFilters ? 'शोध, सेवा क्षेत्र किंवा अनुभवाची अट बदलून पाहा.' : 'प्रशासक सल्लागार प्रोफाइल जोडताच ती येथे दिसेल.'}</span></div>}
    <p className="consultant-privacy-note">विक्रेत्यांसाठी ₹९९ भरल्यानंतर निवडलेल्या सल्लागाराचे सर्व संपर्क पर्याय उघडतात. पेमेंट Razorpay द्वारे तपासल्यानंतरच संपर्क माहिती दाखवली जाते.</p>

    {requestingConsultant && <div className="consultant-request-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setRequestingConsultant(null); }}>
      <form className="consultant-form consultant-request-modal" onSubmit={sendConsultationRequest}>
        <button type="button" className="consultant-request-close" onClick={() => setRequestingConsultant(null)} aria-label="बंद करा"><X size={18}/></button>
        <div className="consultant-section-heading"><div><span>{requestingConsultant.name}</span><h2>सल्ला विनंती</h2></div><MessageCircle size={20}/></div>
        <p className="consultant-request-intro">तुमची माहिती WhatsApp संदेशात या सल्लागाराला पाठवली जाईल. अॅप ही विनंती साठवत नाही.</p>
        <div className="consultant-form-grid">
          <label className="consultant-field"><span>तुमचे नाव <i>*</i></span><SpeechTextInput><input autoComplete="name" maxLength={100} required value={consultation.farmerName} onChange={event => setConsultation(current => ({ ...current, farmerName: event.target.value }))} placeholder="पूर्ण नाव"/></SpeechTextInput></label>
          <label className="consultant-field"><span>मोबाइल नंबर <i>*</i></span><SpeechTextInput><input type="tel" inputMode="tel" autoComplete="tel" pattern="(?:\+91[\s-]?)?[6-9](?:[\s-]?[0-9]){9}" maxLength={16} required value={consultation.phone} onChange={event => setConsultation(current => ({ ...current, phone: event.target.value }))} placeholder="+91 98765 43210"/></SpeechTextInput></label>
          <label className="consultant-field consultant-field-wide"><span>पीक / प्रकार <i>*</i></span><SpeechTextInput><input maxLength={100} required value={consultation.crop} onChange={event => setConsultation(current => ({ ...current, crop: event.target.value }))} placeholder="उदा. ऊस, डाळिंब"/></SpeechTextInput></label>
          <label className="consultant-field consultant-field-wide"><span>शेतीची समस्या <i>*</i></span><SpeechTextInput><textarea maxLength={800} rows="4" required value={consultation.problem} onChange={event => setConsultation(current => ({ ...current, problem: event.target.value }))} placeholder="लक्षणे आणि ती कधीपासून दिसत आहेत ते सांगा"/></SpeechTextInput></label>
          <label className="consultant-field consultant-field-wide"><span>संपर्कासाठी सोयीची वेळ</span><SpeechTextInput><input maxLength={100} value={consultation.preferredTime} onChange={event => setConsultation(current => ({ ...current, preferredTime: event.target.value }))} placeholder="उदा. आज संध्याकाळी ६ नंतर"/></SpeechTextInput></label>
        </div>
        <div className="consultant-form-footer"><span>WhatsAppमध्ये संदेश तपासून Send दाबा.</span><button className="consultant-primary" type="submit"><MessageCircle size={15}/> WhatsAppवर पाठवा</button></div>
      </form>
    </div>}
  </section>;
}

export default ConsultantPanel;
