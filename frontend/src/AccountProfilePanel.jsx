import React, { useEffect, useState } from 'react';
import { CheckCircle2, ExternalLink, Leaf, LoaderCircle, Save, ShieldCheck, UserRound } from 'lucide-react';
import './accountProfile.css';
import SpeechTextInput from './SpeechTextInput.jsx';

const ORGANIC_SOURCES = [
  { label: 'PGS-India UID पडताळणी', href: 'https://pgsindia-ncof.gov.in/consumer_ver.aspx' },
  { label: 'APEDA · NPOP ऑपरेटर', href: 'https://npop.apeda.gov.in/Certification-Body-Operator-Details' },
  { label: 'Jaivik Bharat · FSSAI', href: 'https://jaivikbharat.fssai.gov.in/' }
];

const blankForm = profile => ({
  fullName: profile?.fullName || '',
  businessName: profile?.businessName || '',
  village: profile?.village || '',
  taluka: profile?.taluka || '',
  district: profile?.district || '',
  state: profile?.state || '',
  pincode: profile?.pincode || '',
  address: profile?.address || '',
  about: profile?.about || '',
  organicCertificationSystem: profile?.organicCertificationSystem || 'NONE',
  organicCertificationNumber: profile?.organicCertificationNumber || '',
  organicCertificateValidUntil: profile?.organicCertificateValidUntil || ''
});

const STATUS_LABELS = {
  NOT_CLAIMED: 'प्रमाणपत्र जोडलेले नाही',
  PENDING: 'प्रशासक तपासणी बाकी',
  VERIFIED: 'प्रशासकाने तपासले',
  EXPIRED: 'प्रमाणपत्र कालबाह्य',
  NOT_VERIFIED: 'प्रमाणपत्र तपासता आले नाही'
};

function AccountProfilePanel({ accountType, mobileNumber, profile, profileLoading, onSaved, apiFetch }) {
  const [form, setForm] = useState(() => blankForm(profile));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const isSeller = accountType === 'seller' || accountType === 'farmer';
  const accountLabel = accountType === 'farmer' ? 'शेतकरी' : accountType === 'seller' ? 'विक्रेता' : 'खरेदीदार';

  useEffect(() => {
    setForm(blankForm(profile));
  }, [profile?.profileId, profile?.updatedAt, profile?.complete]);

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value }));
    setNotice('');
  }

  async function saveProfile(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const response = await apiFetch('/api/profile', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...form,
          pincode: form.pincode.trim() || null,
          organicCertificationSystem: isSeller ? form.organicCertificationSystem : 'NONE',
          organicCertificationNumber: isSeller ? form.organicCertificationNumber : null,
          organicCertificateValidUntil: isSeller && form.organicCertificationSystem !== 'NONE'
            ? form.organicCertificateValidUntil || null : null
        })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.message || 'प्रोफाइल जतन करता आली नाही.');
      setForm(blankForm(result));
      onSaved(result);
      setNotice('प्रोफाइल जतन झाले.');
    } catch (saveError) {
      setError(saveError.message || 'प्रोफाइल जतन करता आली नाही.');
    } finally {
      setSaving(false);
    }
  }

  const verificationStatus = profile?.organicVerificationStatus || 'NOT_CLAIMED';

  return <section className="account-profile-page" lang="mr">
    <header className="account-profile-hero">
      <div className="account-profile-icon"><UserRound size={22}/></div>
      <div><span>तुमचे खाते</span><h1>{accountLabel} प्रोफाइल</h1><p>तुमची संपर्क आणि ठिकाणाची माहिती जतन करा. ती प्रशासकाला व्यवस्थापन विभागात दिसेल.</p></div>
      <div className={`account-profile-completion ${profile?.complete ? 'complete' : ''}`}>
        {profile?.complete ? <CheckCircle2 size={16}/> : <ShieldCheck size={16}/>}
        {profile?.complete ? 'पूर्ण' : 'अपूर्ण'}
      </div>
    </header>

    {error && <div className="account-profile-message error" role="alert">{error}</div>}
    {notice && <div className="account-profile-message success" role="status">{notice}</div>}

    {profileLoading ? <div className="account-profile-loading"><LoaderCircle className="spin" size={19}/> प्रोफाइल उघडत आहे…</div> : <form className="account-profile-form" onSubmit={saveProfile}>
      <section className="account-profile-section">
        <div className="account-profile-section-heading"><span><UserRound size={17}/></span><div><h2>मूलभूत माहिती</h2><p>तुमची ओळख आणि परिसर</p></div></div>
        <div className="account-profile-grid">
          <label><span>पूर्ण नाव *</span><SpeechTextInput><input name="fullName" value={form.fullName} onChange={updateField} maxLength={120} required placeholder="तुमचे नाव"/></SpeechTextInput></label>
          <label><span>मोबाईल क्रमांक</span><input value={mobileNumber || ''} readOnly/><small>लॉगिन क्रमांक बदलण्यासाठी खाते सहाय्याशी संपर्क करा.</small></label>
          <label><span>व्यवसाय / शेताचे नाव</span><SpeechTextInput><input name="businessName" value={form.businessName} onChange={updateField} maxLength={160} placeholder="ऐच्छिक"/></SpeechTextInput></label>
          <label><span>गाव *</span><SpeechTextInput><input name="village" value={form.village} onChange={updateField} maxLength={120} required placeholder="गावाचे नाव"/></SpeechTextInput></label>
          <label><span>तालुका</span><SpeechTextInput><input name="taluka" value={form.taluka} onChange={updateField} maxLength={120} placeholder="तालुका"/></SpeechTextInput></label>
          <label><span>जिल्हा *</span><SpeechTextInput><input name="district" value={form.district} onChange={updateField} maxLength={120} required placeholder="जिल्हा"/></SpeechTextInput></label>
          <label><span>राज्य *</span><SpeechTextInput><input name="state" value={form.state} onChange={updateField} maxLength={120} required placeholder="राज्य"/></SpeechTextInput></label>
          <label><span>पिनकोड</span><input name="pincode" value={form.pincode} onChange={event => setForm(current => ({ ...current, pincode: event.target.value.replace(/\D/g, '').slice(0, 6) }))} inputMode="numeric" maxLength={6} placeholder="६ अंक"/></label>
          <label className="wide"><span>पत्ता</span><SpeechTextInput><textarea name="address" value={form.address} onChange={updateField} maxLength={500} rows="2" placeholder="ऐच्छिक"/></SpeechTextInput></label>
          <label className="wide"><span>थोडक्यात परिचय</span><SpeechTextInput><textarea name="about" value={form.about} onChange={updateField} maxLength={800} rows="3" placeholder="तुमच्याबद्दल किंवा व्यवसायाबद्दल लिहा"/></SpeechTextInput></label>
        </div>
      </section>

      {isSeller && <section className="account-profile-section organic-profile-section">
        <div className="account-profile-section-heading"><span><Leaf size={17}/></span><div><h2>सेंद्रिय प्रमाणपत्र</h2><p>प्रमाणपत्र क्रमांक दिल्यास प्रशासक सरकारी नोंदी तपासू शकतो.</p></div></div>
        <div className="account-profile-grid">
          <label><span>प्रमाणपत्र प्रणाली</span><select name="organicCertificationSystem" value={form.organicCertificationSystem} onChange={event => {
            const value = event.target.value;
            setForm(current => ({ ...current, organicCertificationSystem: value, organicCertificationNumber: value === 'NONE' ? '' : current.organicCertificationNumber, organicCertificateValidUntil: value === 'NONE' ? '' : current.organicCertificateValidUntil }));
            setNotice('');
          }}>
            <option value="NONE">प्रमाणपत्र नाही / सेंद्रिय दावा नाही</option>
            <option value="PGS_INDIA">PGS-India</option>
            <option value="NPOP_APEDA">NPOP / APEDA</option>
          </select></label>
          {form.organicCertificationSystem !== 'NONE' && <>
            <label><span>{form.organicCertificationSystem === 'PGS_INDIA' ? 'PGS-India UID *' : 'NPOP प्रमाणपत्र क्रमांक *'}</span><SpeechTextInput><input name="organicCertificationNumber" value={form.organicCertificationNumber} onChange={updateField} maxLength={120} required placeholder="प्रमाणपत्रावरील क्रमांक"/></SpeechTextInput></label>
            <label><span>प्रमाणपत्राची वैधता *</span><input name="organicCertificateValidUntil" type="date" value={form.organicCertificateValidUntil} onChange={updateField} required/></label>
          </>}
        </div>
        {form.organicCertificationSystem !== 'NONE' && <>
          <div className={`account-organic-status status-${String(verificationStatus).toLowerCase()}`}><ShieldCheck size={16}/><span><b>{STATUS_LABELS[verificationStatus] || STATUS_LABELS.PENDING}</b><small>प्रोफाइलमधील क्रमांक प्रशासक तपासेपर्यंत हा सरकारी पडताळणीचा शिक्का नाही.</small></span></div>
          <div className="account-organic-sources"><b>सरकारी पडताळणी संकेतस्थळे</b><div>{ORGANIC_SOURCES.map(source => <a key={source.href} href={source.href} target="_blank" rel="noreferrer"><ExternalLink size={13}/>{source.label}</a>)}</div><small>PGS-India, APEDA/NPOP आणि Jaivik Bharat यांची माहिती वेगळी असते. निवडलेल्या प्रमाणपत्र प्रणालीला लागू होणारी नोंद तपासा.</small></div>
        </>}
      </section>}

      <div className="account-profile-footer"><p>{profile?.complete ? 'प्रोफाइल पूर्ण आहे. माहिती बदलल्यास पुन्हा जतन करा.' : 'तुमचा प्रोफाइल निर्देशक पूर्ण करण्यासाठी नाव, गाव, जिल्हा आणि राज्य भरा.'}</p><button type="submit" disabled={saving}>{saving ? <LoaderCircle className="spin" size={16}/> : <Save size={16}/>} {saving ? 'जतन होत आहे…' : 'प्रोफाइल जतन करा'}</button></div>
    </form>}
  </section>;
}

export default AccountProfilePanel;
