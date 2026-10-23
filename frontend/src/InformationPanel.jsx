import { AlertTriangle, ArrowUpRight, Bug, ClipboardCheck, Droplets, Flower2, Leaf, RefreshCw, Search, Sprout } from 'lucide-react';

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
        <h1>शाश्वत शेतीसाठी उपयोगी माहिती</h1>
        <p>मित्र कीटक आणि तण ओळखण्यासोबत कीड निरीक्षण, माती-पाणी जपणे, पीक फेरपालट आणि रोजच्या शेती नोंदींसाठी सोपे मार्गदर्शन.</p>
      </div>
      <div className="info-hero-mark"><Leaf size={32}/><span>निरीक्षण<br/>·<br/>समतोल</span></div>
    </section>

    <nav className="info-jump-links" aria-label="माहितीतील विभाग">
      <a href="#info-field-check">शेत निरीक्षण</a>
      <a href="#info-soil-water">माती आणि पाणी</a>
      <a href="#info-rotation">पीक फेरपालट</a>
      <a href="#info-records">दैनिक नोंदी</a>
      <a href="#info-insects-title">मित्र कीटक</a>
      <a href="#info-weeds-title">तण व्यवस्थापन</a>
    </nav>

    <section className="info-section" id="info-field-check" aria-labelledby="info-field-title">
      <div className="info-section-heading">
        <span className="info-section-icon"><Search size={18}/></span>
        <div><span>नियमित पाहणी · विचारपूर्वक निर्णय</span><h2 id="info-field-title">शेत निरीक्षणाची चार पावले</h2></div>
      </div>
      <div className="info-steps-grid">
        <article className="info-step-card"><span>१</span><h3>ठिकाण ठरवा</h3><p>शेताच्या कडा, मधला भाग आणि कमी-जास्त वाढ असलेला पट्टा पाहा. प्रत्येक वेळी साधारण त्याच भागांची पाहणी केल्यास बदल लक्षात येतात.</p></article>
        <article className="info-step-card"><span>२</span><h3>लक्षणे नीट पाहा</h3><p>पानांच्या वर-खाली, खोड आणि जमिनीवरील भागात अंडी, किडी, डाग, वाळलेली पाने आणि उपयुक्त कीटक शोधा.</p></article>
        <article className="info-step-card"><span>३</span><h3>ओळख खात्री करा</h3><p>फक्त एक कीड दिसली म्हणून फवारणी ठरवू नका. पीक अवस्था, नुकसान किती पसरले आहे आणि नैसर्गिक शत्रू दिसतात का हेही पाहा.</p></article>
        <article className="info-step-card"><span>४</span><h3>उपाय आणि परिणाम नोंदवा</h3><p>आधी प्रतिबंधक, यांत्रिक किंवा जैविक पर्यायांचा विचार करा. केलेला उपाय आणि पुढील पाहणीतील बदल नोंदवा.</p></article>
      </div>
      <p className="info-source-note">ही एक सामान्य पाहणी पद्धत आहे; प्रत्येक पिकासाठी कीड ओळख आणि आर्थिक नुकसान मर्यादा वेगळी असते. गरज असल्यास कृषी अधिकारी किंवा KVK कडून खात्री करा.</p>
    </section>

    <section className="info-section" id="info-soil-water" aria-labelledby="info-soil-title">
      <div className="info-section-heading">
        <span className="info-section-icon"><Droplets size={18}/></span>
        <div><span>जमिनीची ओल आणि सुपीकता</span><h2 id="info-soil-title">माती आणि पाणी जपण्यासाठी छोटे उपाय</h2></div>
      </div>
      <div className="info-guide-grid">
        <article className="info-guide-card">
          <h3><Droplets size={16}/> ओलावा जपून सिंचन करा</h3>
          <ul>
            <li>सिंचन ठरवताना पिकाची अवस्था, मातीतील ओलावा आणि पुढील हवामानाचा अंदाज पाहा.</li>
            <li>उघडी जमीन कमी ठेवण्यासाठी स्वच्छ पिकांचे अवशेष किंवा योग्य सेंद्रिय आच्छादन वापरण्याचा विचार करा.</li>
            <li>आच्छादन खोडाला चिकटवू नका आणि निचरा कमी असलेल्या जमिनीत जाड थर टाळा; जास्त ओलाव्यामुळे रोग किंवा मुळांना त्रास होऊ शकतो.</li>
          </ul>
        </article>
        <article className="info-guide-card">
          <h3><Leaf size={16}/> तपासणीवर आधारित पोषण</h3>
          <ul>
            <li>खत किंवा माती सुधारक वापरण्यापूर्वी माती परीक्षणाचा अहवाल आणि स्थानिक शिफारस पाहा.</li>
            <li>कुजलेले सेंद्रिय पदार्थ आणि पीक अवशेषांचा योग्य वापर करा; रोगट किंवा बियांनी भरलेले अवशेष शेतात पसरवू नका.</li>
            <li>सेंद्रिय प्रमाणपत्र घेतले असल्यास वापरायच्या निविष्ठा लागू मानकांनुसार मान्य आहेत का ते आधी तपासा.</li>
          </ul>
        </article>
      </div>
    </section>

    <section className="info-section" id="info-rotation" aria-labelledby="info-rotation-title">
      <div className="info-section-heading">
        <span className="info-section-icon"><RefreshCw size={18}/></span>
        <div><span>हंगामांमधील नियोजन</span><h2 id="info-rotation-title">पीक फेरपालट कसे ठरवावे?</h2></div>
      </div>
      <div className="info-rotation-card">
        <div><h3>एकाच पिकाची पुनरावृत्ती कमी करा</h3><p>शक्य असेल तेथे वेगवेगळ्या पीक-कुळांतील पिके आलटून घ्या. फेरपालटीत मूग, उडीद, तूर किंवा इतर कडधान्यांचा समावेश करणे हा एक पर्याय आहे; पिकाची निवड माती, पाण्याची उपलब्धता, पेरणीचा काळ आणि बाजार पाहून करा.</p></div>
        <div className="info-rotation-checks"><b>हंगाम ठरवण्यापूर्वी तपासा</b><span>मागील पीक आणि त्याचे रोग-किडी</span><span>जमिनीचा ओलावा आणि पाण्याचा स्रोत</span><span>बियाणे, मजूर आणि अपेक्षित बाजार</span></div>
      </div>
    </section>

    <section className="info-section" id="info-records" aria-labelledby="info-records-title">
      <div className="info-section-heading">
        <span className="info-section-icon"><ClipboardCheck size={18}/></span>
        <div><span>स्वर्णभूमी दैनिक नोंद</span><h2 id="info-records-title">नोंदींमधून पुढच्या हंगामाचे नियोजन करा</h2></div>
      </div>
      <div className="info-record-card">
        <p>काम झाल्यावर थोडक्यात नोंद करा. खर्च, उत्पादन आणि शेतातील बदल एकत्र पाहिल्यास कोणत्या कामाचा उपयोग झाला आणि कुठे खर्च वाढला हे समजण्यास मदत होते.</p>
        <div className="info-record-tags"><span>पीक आणि शेताचा भाग</span><span>हवामान आणि पाऊस</span><span>सिंचन व शेतकाम</span><span>खते आणि इतर निविष्ठा</span><span>मजुरी व खर्च</span><span>कीड व पीकस्थिती</span><span>काढणी, उत्पादन व विक्री</span></div>
        <p className="info-record-tip">नोंदीत तारीख, प्रमाण आणि कोणत्या भागात काम केले हे लिहा. दोन हंगामांची तुलना करण्यासाठी समान एकके वापरा.</p>
      </div>
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
      <div><span>पुढील वाचन · अधिकृत कृषी संदर्भ</span><h2>शिफारशी आणि पद्धती तपासा</h2></div>
      <a href="https://www.icar.gov.in/en/integrated-pest-management-sustainable-approach-responsible-pest-management" target="_blank" rel="noreferrer">ICAR · एकात्मिक कीड व्यवस्थापन <ArrowUpRight size={15}/></a>
      <a href="https://agritech.tnau.ac.in/org_farm/orgfarm_crop_rotation.html" target="_blank" rel="noreferrer">TNAU · पीक फेरपालट <ArrowUpRight size={15}/></a>
      <a href="https://agritech.tnau.ac.in/agriculture/agri_majorareas_dryland_agromeasures_mulching.html" target="_blank" rel="noreferrer">TNAU · आच्छादन <ArrowUpRight size={15}/></a>
      <a href="https://agritech.tnau.ac.in/agriculture/agri_index.html" target="_blank" rel="noreferrer">TNAU AgriTech <ArrowUpRight size={15}/></a>
    </section>
    <footer className="info-footer">🌿 पीक आणि शेतानुसार शिफारशी बदलतात. कीड, रोग किंवा निविष्ठेबद्दल कृती करण्यापूर्वी स्थानिक कृषी तज्ज्ञांकडून खात्री करा.</footer>
  </div>;
}

export default InformationPanel;
