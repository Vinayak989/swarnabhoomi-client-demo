import { AlertTriangle, ArrowUpRight, Bug, Flower2, Leaf, Sprout } from 'lucide-react';

const weeds = [
  {
    name: 'घोळ / कुलफा',
    scientific: 'Portulaca oleracea',
    image: '/images/farm-info/purslane.jpg',
    alt: 'घोळ किंवा कुलफा वनस्पतीचे छायाचित्र',
    detail: 'जमिनीवर पसरणारे हे तण मातीला थेट उन्हापासून काही प्रमाणात आच्छादन देते. त्याची फुले परागीभवन करणाऱ्या कीटकांना उपयोगी ठरू शकतात.',
    care: 'बियांनी वेगाने पसरू शकते; फुलोरा आणि बी तयार होण्यापूर्वी वाढ नियंत्रित करा.',
    credit: 'Didier Descouens',
    license: 'CC BY-SA 4.0',
    photoUrl: 'https://commons.wikimedia.org/wiki/File:Portulaca_oleracea_MHNT.jpg',
    licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/'
  },
  {
    name: 'एकदंडी / ट्रिडॅक्स',
    scientific: 'Tridax procumbens',
    image: '/images/farm-info/tridax.jpg',
    alt: 'ट्रिडॅक्सची पांढरी फुले',
    detail: 'लहान फुले मधमाश्या व इतर परागीभवन करणाऱ्या कीटकांना संसाधने देऊ शकतात. बांधावर किंवा स्वतंत्र जैवविविधता पट्ट्यात मर्यादित ठेवता येते.',
    care: 'बिया वाऱ्याने पसरू शकतात; फुलोरा आणि बीधारणा नियंत्रित ठेवा.',
    credit: 'Gowrivemban',
    license: 'CC0',
    photoUrl: 'https://commons.wikimedia.org/wiki/File:Tridax_procumbens_flower_in_ground.jpg',
    licenseUrl: 'https://creativecommons.org/publicdomain/zero/1.0/'
  },
  {
    name: 'कुप्पी / कुंप्पी',
    scientific: 'Acalypha indica',
    image: '/images/farm-info/acalypha.jpg',
    alt: 'कुप्पी किंवा कुंप्पी वनस्पती',
    detail: 'हिरव्या वनस्पतीचे अवशेष जमिनीवर आच्छादन म्हणून वापरता येतात. कुजल्यावर त्यातून सेंद्रिय पदार्थ पुन्हा जमिनीत जातात.',
    care: 'कोवळ्या अवस्थेत कापून वापरा; बिया तयार झालेली तणे थेट शेतात पसरवू नका.',
    credit: 'Marisankar Mk',
    license: 'CC BY-SA 4.0',
    photoUrl: 'https://commons.wikimedia.org/wiki/File:Acalypha_indica_plant.jpg',
    licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/'
  },
  {
    name: 'पिवळ्या फुलांची रानभाजी',
    scientific: 'Cleome viscosa',
    image: '/images/farm-info/cleome.jpg',
    alt: 'क्लिओम व्हिस्कोसा वनस्पतीची फुले',
    detail: 'फुलांमुळे काही उपयुक्त कीटकांना अन्न मिळू शकते. शेताच्या कडेवर मर्यादित ठेवण्याचा विचार करता येतो.',
    care: 'पिकांशी पाणी, अन्नद्रव्ये आणि जागेसाठी स्पर्धा होणार नाही याची काळजी घ्या.',
    credit: 'Kevin Thiele',
    license: 'CC BY 2.0',
    photoUrl: 'https://commons.wikimedia.org/wiki/File:Cleome_viscosa_(7596847412).jpg',
    licenseUrl: 'https://creativecommons.org/licenses/by/2.0/'
  }
];

function PhotoCredit({ credit, license, photoUrl, licenseUrl }) {
  return <span className="info-photo-credit">
    छायाचित्र: <a href={photoUrl} target="_blank" rel="noreferrer">{credit}</a> · <a href={licenseUrl} target="_blank" rel="noreferrer">{license}</a>
  </span>;
}

function InformationPanel() {
  return <div className="info-page" lang="mr">
    <section className="info-hero">
      <div>
        <span className="info-eyebrow"><Sprout size={15}/> सेंद्रिय शेती · मैदानी मार्गदर्शक</span>
        <h1>शेतातील उपयुक्त जैवविविधता</h1>
        <p>सांगली परिसरातील हळद, लसूण, तूर, मूग, उडीद आणि शेवगा पिकांसाठी मित्र कीटकांचे निरीक्षण व तणांचे समतोल व्यवस्थापन.</p>
      </div>
      <div className="info-hero-mark"><Leaf size={32}/><span>निरीक्षण<br/>·<br/>समतोल</span></div>
    </section>

    <section className="info-section" aria-labelledby="info-insects-title">
      <div className="info-section-heading">
        <span className="info-section-icon"><Bug size={18}/></span>
        <div><span>निरीक्षण आणि संवर्धन</span><h2 id="info-insects-title">मित्र कीटकांसाठी शेताची छोटी योजना</h2></div>
      </div>
      <div className="info-insect-card">
        <figure className="info-photo info-insect-photo">
          <img src="/images/farm-info/ladybird.jpg" alt="हिरव्या पानावर बसलेला लेडीबर्ड भुंगेरा" loading="lazy"/>
          <figcaption><span>लेडीबर्ड भुंगेरा — मावा खाणाऱ्या उपयुक्त कीटकांपैकी एक</span><PhotoCredit credit="Luc Viatour" license="CC BY-SA 3.0" photoUrl="https://commons.wikimedia.org/wiki/File:Coccinella_septempunctata_Luc_Viatour.JPG" licenseUrl="https://creativecommons.org/licenses/by-sa/3.0/"/></figcaption>
        </figure>
        <div className="info-insect-copy">
          <h3>फवारणीपूर्वी शेताचे निरीक्षण करा</h3>
          <p>मित्र कीटक, अंडी आणि किडींची संख्या नोंदवा. मित्र कीटक दिसणे म्हणजे सर्व हानिकारक किडी नष्ट झाल्या असे नाही; नियंत्रणाचा निर्णय पीक अवस्था, किडीचे नुकसान आणि नैसर्गिक शत्रू यांचा विचार करून घ्या.</p>
          <div className="info-tip"><Flower2 size={17}/><span>शक्य असल्यास स्थानिक फुलझाडांचा छोटा पट्टा ठेवा; तो परागीभवन करणाऱ्या कीटकांना अन्न व निवारा देऊ शकतो.</span></div>
        </div>
      </div>
      <div className="info-table-wrap">
        <table className="info-plan-table">
          <thead><tr><th>शेताचा भाग</th><th>काय करावे?</th><th>उद्देश</th></tr></thead>
          <tbody>
            <tr><td>बांध</td><td>स्थानिक फुलझाडे व योग्य झुडपे ठेवा</td><td>अन्न आणि निवारा</td></tr>
            <tr><td>पिकांच्या कडा</td><td>धणे, शेपू किंवा हंगामानुसार फुलझाडांची छोटी पट्टी</td><td>परागकण व मधुरस उपलब्ध करणे</td></tr>
            <tr><td>हळदीचे क्षेत्र</td><td>पानांचे नियमित निरीक्षण करा</td><td>नैसर्गिक शत्रूंचे संरक्षण</td></tr>
            <tr><td>लसूण व कडधान्ये</td><td>मावा, अंडी आणि मित्र कीटकांची नोंद ठेवा</td><td>किडीचे संतुलन समजून घेणे</td></tr>
            <tr><td>शेवगा व फळझाडे</td><td>फुलोऱ्याच्या काळात मधमाश्यांचे निरीक्षण</td><td>परागीभवनाला मदत</td></tr>
          </tbody>
        </table>
      </div>
      <p className="info-source-note">संदर्भ: ICAR–National Bureau of Agricultural Insect Resources (NBAIR) आणि Tamil Nadu Agricultural University (TNAU).</p>
    </section>

    <section className="info-section" aria-labelledby="info-weeds-title">
      <div className="info-section-heading">
        <span className="info-section-icon"><Flower2 size={18}/></span>
        <div><span>ओळखा · निवडा · मर्यादित ठेवा</span><h2 id="info-weeds-title">शेतीसाठी उपयुक्त ठरू शकणारी तणे</h2></div>
      </div>
      <p className="info-section-intro">प्रत्येक तण हानिकारकच असेल असे नाही; पण तण पिकांशी पाणी, अन्नद्रव्ये, सूर्यप्रकाश आणि जागेसाठी स्पर्धा करतात. उपयोगी तण निवडून मुख्य पिकात स्पर्धा होणार नाही अशा ठिकाणीच ठेवा.</p>
      <div className="info-weed-grid">
        {weeds.map(weed=><article className="info-weed-card" key={weed.scientific}>
          <figure className="info-photo">
            <img src={weed.image} alt={weed.alt} loading="lazy"/>
            <figcaption><PhotoCredit credit={weed.credit} license={weed.license} photoUrl={weed.photoUrl} licenseUrl={weed.licenseUrl}/></figcaption>
          </figure>
          <div className="info-weed-copy">
            <h3>{weed.name}</h3><i>{weed.scientific}</i>
            <p>{weed.detail}</p>
            <div className="info-care"><Leaf size={15}/><span>{weed.care}</span></div>
          </div>
        </article>)}
      </div>
      <div className="info-species-note">
        <strong>इतर ओळखता येणाऱ्या वनस्पती</strong>
        <span>केना / केवना (<i>Commelina benghalensis</i>), माठासारखे रानतण (<i>Amaranthus</i> प्रजाती), रानातील शिंबावर्गीय वनस्पती (<i>Desmodium</i> प्रजाती) आणि दुधी / दुधिका (<i>Euphorbia hirta</i>) यांना मर्यादित भागातच ठेवा. दुधिकेचा चीक त्वचा किंवा डोळ्यांना त्रास देऊ शकतो; औषधी प्रयोग स्वतःहून करू नका.</span>
      </div>
    </section>

    <section className="info-management" aria-label="तण व्यवस्थापनातील काळजी">
      <div className="info-management-icon"><AlertTriangle size={19}/></div>
      <div><span>व्यवस्थापनातील काळजी</span><h2>तण उपयोगी असले तरी पसरण्यापूर्वी नियंत्रण ठेवा</h2>
        <ul>
          <li>बिया तयार होण्यापूर्वी तण कापा; बिया असलेले तण थेट शेतात पसरवू नका.</li>
          <li>जमिनीखालील खोडे किंवा मुळांनी पसरणाऱ्या तणांसाठी मुळेही काढा.</li>
          <li>गाजर गवत (<i>Parthenium hysterophorus</i>) आक्रमक आहे आणि त्वचेची अॅलर्जी करू शकते; हाताळताना काळजी घ्या.</li>
          <li>अमरवेल (<i>Cuscuta</i> spp.) परजीवी आहे; लव्हाळा / नागरमोथा (<i>Cyperus rotundus</i>) कंदांमुळे पसरू शकतो. दोन्हींचे प्रसारापूर्वी नियंत्रण करा.</li>
          <li>एका छोट्या पट्ट्यात दोन पद्धतींची तुलना करा; दर १५ दिवसांनी ओलावा, तणांचे प्रमाण, कीटकांची विविधता आणि पिकाची वाढ नोंदवा.</li>
        </ul>
      </div>
    </section>

    <section className="info-resources">
      <div><span>पुढील वाचन</span><h2>ICAR–NBAIR व TNAU कृषी संदर्भ</h2></div>
      <a href="https://agritech.tnau.ac.in/agriculture/agri_index.html" target="_blank" rel="noreferrer">TNAU AgriTech <ArrowUpRight size={15}/></a>
    </section>
    <footer className="info-footer">🌿 शेतात निर्णय घेण्यापूर्वी तणांची आणि कीटकांची अचूक ओळख स्थानिक कृषी तज्ज्ञांकडून करून घ्या.</footer>
  </div>;
}

export default InformationPanel;
