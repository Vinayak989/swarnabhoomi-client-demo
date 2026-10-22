import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, LoaderCircle, MapPin, MessageCircle, MessageSquareText, Phone, Plus, Search, Sprout, UserRound, X } from 'lucide-react';
import './consultant.css';

const EMPTY_FORM = {
  name: '',
  phone: '',
  serviceArea: '',
  expertise: '',
  experienceYears: '',
  introduction: ''
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

function ConsultantPanel({ apiBase }) {
  const [consultants, setConsultants] = useState([]);
  const [search, setSearch] = useState('');
  const [form, setForm] = useState(EMPTY_FORM);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  async function loadConsultants() {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(`${apiBase}/consultants`);
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
  }, [apiBase]);

  const filteredConsultants = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    if (!query) return consultants;
    return consultants.filter(consultant =>
      `${consultant.name} ${consultant.serviceArea} ${consultant.expertise} ${consultant.introduction || ''}`
        .toLocaleLowerCase()
        .includes(query));
  }, [consultants, search]);

  async function register(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const response = await fetch(`${apiBase}/consultants`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...form,
          experienceYears: Number(form.experienceYears)
        })
      });
      const result = await response.json().catch(() => null);
      if (!response.ok) throw new Error(result?.message || 'नोंदणी जतन करता आली नाही.');
      setConsultants(current => [result, ...current.filter(item => item.id !== result.id)].slice(0, 200));
      setForm(EMPTY_FORM);
      setShowForm(false);
      setNotice('तुमची सल्लागार नोंदणी प्रकाशित झाली.');
    } catch (exception) {
      setError(exception.message || 'नोंदणी जतन करता आली नाही.');
    } finally {
      setSaving(false);
    }
  }

  return <section className="consultant-page" lang="mr">
    <div className="consultant-hero">
      <div>
        <span className="consultant-eyebrow"><Sprout size={14}/> सेंद्रिय शेतीसाठी शेतकरी ते शेतकरी मार्गदर्शन</span>
        <h1>सेंद्रिय शेती सल्लागार</h1>
        <p>अनुभवी सल्लागार शोधा किंवा तुमचे ज्ञान इतर शेतकऱ्यांपर्यंत पोहोचवण्यासाठी नोंदणी करा.</p>
      </div>
      <button type="button" className="consultant-primary" onClick={() => { setShowForm(value => !value); setNotice(''); }}>
        {showForm ? <X size={16}/> : <Plus size={16}/>}
        {showForm ? 'फॉर्म बंद करा' : 'सल्लागार म्हणून नोंदणी'}
      </button>
    </div>

    {error && <div className="consultant-message consultant-error" role="alert">{error}</div>}
    {notice && <div className="consultant-message consultant-success" role="status"><CheckCircle2 size={16}/>{notice}</div>}

    {showForm && <form className="consultant-form" onSubmit={register}>
      <div className="consultant-section-heading">
        <div><span>तुमची माहिती</span><h2>सल्लागार नोंदणी</h2></div>
        <UserRound size={20}/>
      </div>
      <div className="consultant-form-grid">
        <label className="consultant-field"><span>पूर्ण नाव <i>*</i></span><input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} maxLength={100} required placeholder="तुमचे नाव"/></label>
        <label className="consultant-field"><span>मोबाइल नंबर <i>*</i></span><input type="tel" inputMode="tel" autoComplete="tel" value={form.phone} onChange={event => setForm({ ...form, phone: event.target.value })} pattern="(?:\+91[\s-]?)?[6-9][0-9]{9}" maxLength={16} required placeholder="+91 98765 43210"/><small>हा नंबर कॉल, SMS आणि WhatsApp संपर्कासाठी सार्वजनिक दिसेल.</small></label>
        <label className="consultant-field"><span>सेवा क्षेत्र / जिल्हा <i>*</i></span><input value={form.serviceArea} onChange={event => setForm({ ...form, serviceArea: event.target.value })} maxLength={120} required placeholder="उदा. सांगली जिल्हा"/></label>
        <label className="consultant-field"><span>अनुभव (वर्षे) <i>*</i></span><input type="number" min="0" max="80" value={form.experienceYears} onChange={event => setForm({ ...form, experienceYears: event.target.value })} required placeholder="उदा. ५"/></label>
        <label className="consultant-field consultant-field-wide"><span>तज्ज्ञता <i>*</i></span><input value={form.expertise} onChange={event => setForm({ ...form, expertise: event.target.value })} maxLength={300} required placeholder="उदा. सेंद्रिय खते, कीड व्यवस्थापन, माती परीक्षण"/></label>
        <label className="consultant-field consultant-field-wide"><span>परिचय</span><textarea value={form.introduction} onChange={event => setForm({ ...form, introduction: event.target.value })} maxLength={800} rows="3" placeholder="तुमचे मार्गदर्शन कसे उपयुक्त ठरू शकते ते सांगा."/></label>
      </div>
      <div className="consultant-form-footer"><span>* आवश्यक माहिती</span><button className="consultant-primary" type="submit" disabled={saving}>{saving ? <LoaderCircle className="consultant-spin" size={16}/> : <CheckCircle2 size={16}/>} नोंदणी प्रकाशित करा</button></div>
    </form>}

    <div className="consultant-directory-head">
      <div><span>समुदायातील मार्गदर्शक</span><h2>सल्लागार शोधा</h2></div>
      <label className="consultant-search"><Search size={16}/><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="नाव, जिल्हा किंवा तज्ज्ञता शोधा" aria-label="सल्लागार शोधा"/></label>
    </div>

    {loading ? <div className="consultant-empty"><LoaderCircle className="consultant-spin" size={22}/> सल्लागारांची यादी लोड होत आहे...</div>
      : filteredConsultants.length ? <div className="consultant-grid">{filteredConsultants.map(consultant => {
        const links = phoneLinks(consultant.phone);
        return <article className="consultant-card" key={consultant.id}>
          <div className="consultant-card-top"><div className="consultant-avatar"><Sprout size={21}/></div><span className="consultant-experience">{consultant.experienceYears.toLocaleString('mr-IN')} वर्षांचा अनुभव</span></div>
          <h3>{consultant.name}</h3>
          <p className="consultant-expertise">{consultant.expertise}</p>
          <div className="consultant-area"><MapPin size={15}/><span>{consultant.serviceArea}</span></div>
          {consultant.introduction && <p className="consultant-introduction">{consultant.introduction}</p>}
          <div className="consultant-contact-actions" aria-label={`${consultant.name} यांच्याशी संपर्क`}>
            <a href={links.call} aria-label={`${consultant.name} यांना कॉल करा`}><Phone size={15}/> कॉल</a>
            <a href={links.sms} aria-label={`${consultant.name} यांना SMS करा`}><MessageSquareText size={15}/> SMS</a>
            <a href={links.whatsapp} target="_blank" rel="noopener noreferrer" aria-label={`${consultant.name} यांना WhatsApp करा`}><MessageCircle size={15}/> WhatsApp</a>
          </div>
        </article>;
      })}</div>
      : <div className="consultant-empty"><div className="consultant-empty-icon"><Sprout size={22}/></div><b>{search ? 'या शोधासाठी सल्लागार सापडले नाहीत' : 'अजून सल्लागार नोंदणीकृत नाहीत'}</b><span>{search ? 'शोध वेगळ्या नावाने, जिल्ह्याने किंवा तज्ज्ञतेने करून पाहा.' : 'पहिले सल्लागार म्हणून नोंदणी करून शेतकरी समुदायाला मार्गदर्शन करा.'}</span></div>}
    <p className="consultant-privacy-note">सल्लागारांची संपर्क माहिती सार्वजनिक आहे. संपर्क करण्यापूर्वी माहितीची खात्री करा.</p>
  </section>;
}

export default ConsultantPanel;
