import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowDownWideNarrow, CalendarDays, ChartNoAxesCombined, CircleAlert, MapPin, RefreshCw, Search, Wheat } from 'lucide-react';
import './marketPrices.css';

const SOURCE_URL = 'https://www.data.gov.in/catalog/current-daily-price-various-commodities-various-markets-mandi';
const DATA_API_URL = 'https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070';
const DATA_API_KEY = '579b464db66ec23bdd000001cdc3b564546246a772a26393094f5645';
const CROP_NAMES_MR = {
  'amla(nelli kai)': 'आवळा',
  apple: 'सफरचंद',
  'bajra(pearl millet/cumbu)': 'बाजरी',
  banana: 'केळी',
  beans: 'फरसबी',
  beetroot: 'बीट',
  'bengal gram(gram)(whole)': 'हरभरा',
  'bhindi(ladies finger)': 'भेंडी',
  'bitter gourd': 'कारले',
  'black gram(urd beans)(whole)': 'उडीद',
  'bottle gourd': 'दुधी भोपळा',
  brinjal: 'वांगी',
  cabbage: 'कोबी',
  carrot: 'गाजर',
  cauliflower: 'फुलकोबी',
  'chikoos(sapota)': 'चिकू',
  'chilly capsicum': 'ढोबळी मिरची',
  'coriander(leaves)': 'कोथिंबीर',
  cotton: 'कापूस',
  'cucumbar(kheera)': 'काकडी',
  drumstick: 'शेवगा',
  'elephant yam(suran)/amorphophallus': 'सुरण',
  garlic: 'लसूण',
  'ginger(green)': 'आले',
  grapes: 'द्राक्षे',
  'green chilli': 'हिरवी मिरची',
  'green gram(moong)(whole)': 'मूग',
  'green peas': 'हिरवे वाटाणे',
  guar: 'गवार',
  guava: 'पेरू',
  'jowar(sorghum)': 'ज्वारी',
  lime: 'लिंबू',
  'little gourd(kundru)': 'तोंडली',
  maize: 'मका',
  'mango(raw-ripe)': 'आंबा',
  'methi(leaves)': 'मेथी',
  'mint(pudina)': 'पुदिना',
  'mousambi(sweet lime)': 'मोसंबी',
  onion: 'कांदा',
  'onion green': 'पात कांदा',
  orange: 'संत्रे',
  'paddy(common)': 'भात (धान)',
  papaya: 'पपई',
  'pear(marasebu)': 'नाशपाती',
  'peas wet': 'ओले वाटाणे',
  pineapple: 'अननस',
  plum: 'आलुबुखारा',
  'pointed gourd(parval)': 'परवल',
  pomegranate: 'डाळिंब',
  potato: 'बटाटा',
  pumpkin: 'भोपळा',
  raddish: 'मुळा',
  rajgir: 'राजगिरा',
  'red gram/arhar/tur(whole)': 'तूर',
  rice: 'तांदूळ',
  'ridgeguard(tori)': 'दोडका',
  seetapal: 'सीताफळ',
  'sesamum(sesame,gingelly,til)': 'तीळ',
  snakeguard: 'पडवळ',
  soyabean: 'सोयाबीन',
  spinach: 'पालक',
  'sweet potato': 'रताळे',
  'sweet pumpkin': 'गोड भोपळा',
  'tender coconut': 'शहाळे',
  tomato: 'टोमॅटो',
  'water melon': 'कलिंगड',
  wheat: 'गहू'
};
const cropNameInMarathi = name => CROP_NAMES_MR[String(name || '').trim().toLocaleLowerCase()] || name || 'शेतमाल';
const money = value => value == null || value === '' ? '—' : `₹${Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
const stamp = value => {
  const match = String(value || '').match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (match) return new Date(Number(match[3]), Number(match[2]) - 1, Number(match[1])).toLocaleDateString('mr-IN', { day: 'numeric', month: 'short', year: 'numeric' });
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.valueOf()) ? date.toLocaleDateString('mr-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : 'तारीख उपलब्ध नाही';
};
const dateValue = value => {
  const match = String(value || '').match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  return match ? new Date(Number(match[3]), Number(match[2]) - 1, Number(match[1])).valueOf() : 0;
};
async function fetchOfficialPrices() {
  const prices = [];
  let offset = 0;
  let total = Number.MAX_SAFE_INTEGER;
  let dataUpdatedAt = '';
  while (offset < total && offset < 50000) {
    const url = new URL(DATA_API_URL);
    url.searchParams.set('api-key', DATA_API_KEY);
    url.searchParams.set('format', 'json');
    url.searchParams.set('offset', String(offset));
    url.searchParams.set('limit', '1000');
    url.searchParams.set('filters[state]', 'Maharashtra');
    const response = await fetch(url, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(20000) });
    if (!response.ok) throw new Error('सरकारी बाजारभाव सेवा सध्या उपलब्ध नाही. थोड्या वेळाने पुन्हा प्रयत्न करा.');
    const page = await response.json();
    if (page.status !== 'ok') throw new Error('सरकारी बाजारभाव सेवा सध्या उपलब्ध नाही. थोड्या वेळाने पुन्हा प्रयत्न करा.');
    if (offset === 0) {
      total = Number(page.total || 0);
      dataUpdatedAt = page.updated_date || '';
    }
    const records = Array.isArray(page.records) ? page.records : [];
    if (!records.length) break;
    prices.push(...records.map(item => ({
      state: item.state || '', district: item.district || '', market: item.market || '',
      commodity: item.commodity || '', variety: item.variety || '', grade: item.grade || '',
      arrivalDate: item.arrival_date || '', minPrice: item.min_price ?? null,
      maxPrice: item.max_price ?? null, modalPrice: item.modal_price ?? null
    })));
    offset += records.length;
  }
  if (!prices.length) throw new Error('सरकारी बाजारभाव सेवा सध्या कोणतेही दर देत नाही.');
  return { prices, checkedAt: new Date().toISOString(), dataUpdatedAt, available: true, state: 'Maharashtra', source: 'Data.gov.in / AGMARKNET', sourceUrl: SOURCE_URL, error: null };
}

export default function MarketPricesPanel() {
  const [feed, setFeed] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [crop, setCrop] = useState('');
  const [district, setDistrict] = useState('');
  const [sort, setSort] = useState('recent');
  const [visibleCount, setVisibleCount] = useState(40);

  const load = useCallback(async (force = false) => {
    if (force) setRefreshing(true); else setLoading(true);
    try {
      setFeed(await fetchOfficialPrices());
      setError('');
    } catch (exception) {
      setError(exception.message || 'बाजारभाव आणता आले नाहीत. इंटरनेट जोडून पुन्हा प्रयत्न करा.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(true), 15 * 60 * 1000);
    return () => window.clearInterval(timer);
  }, [load]);

  const prices = feed?.prices || [];
  const crops = useMemo(() => [...new Set(prices.map(item => item.commodity).filter(Boolean))].sort((a, b) => cropNameInMarathi(a).localeCompare(cropNameInMarathi(b), 'mr')), [prices]);
  const districts = useMemo(() => [...new Set(prices.map(item => item.district).filter(Boolean))].sort((a, b) => a.localeCompare(b)), [prices]);
  const commodityCount = useMemo(() => new Set(prices.map(item => item.commodity).filter(Boolean)).size, [prices]);
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return prices.filter(item => (!crop || item.commodity === crop)
      && (!district || item.district === district)
      && (!query || [item.commodity, cropNameInMarathi(item.commodity), item.variety, item.grade, item.market, item.district].some(value => String(value || '').toLocaleLowerCase().includes(query))))
      .sort((a, b) => {
        if (sort === 'low') return Number(a.modalPrice ?? Infinity) - Number(b.modalPrice ?? Infinity);
        if (sort === 'high') return Number(b.modalPrice ?? -Infinity) - Number(a.modalPrice ?? -Infinity);
        return dateValue(b.arrivalDate) - dateValue(a.arrivalDate) || String(a.commodity).localeCompare(String(b.commodity));
      });
  }, [prices, search, crop, district, sort]);

  useEffect(() => { setVisibleCount(40); }, [search, crop, district, sort]);

  return <section className="market-prices" lang="mr">
    <header className="market-prices-hero">
      <div className="market-prices-hero-copy">
        <span className="market-prices-eyebrow"><ChartNoAxesCombined size={15}/> अधिकृत मंडई भाव</span>
        <h1>महाराष्ट्रातील शेतमालाचे बाजारभाव</h1>
        <p>वेगवेगळ्या बाजार समित्यांमधील पिकांचे किमान, कमाल आणि मोडल दर पहा. प्रत्येक दरासोबत बाजार आणि नोंद दिनांक दिला आहे.</p>
      </div>
      <button type="button" className="market-prices-refresh" onClick={() => void load(true)} disabled={refreshing || loading}>
        <RefreshCw size={16} className={refreshing ? 'market-prices-spin' : ''}/>{refreshing ? 'तपासत आहोत…' : 'भाव तपासा'}
      </button>
      <div className="market-prices-hero-mark"><Wheat size={40}/></div>
    </header>

    <div className="market-prices-stats">
      <div><span>बाजार नोंदी</span><strong>{loading && !feed ? '…' : prices.length.toLocaleString('mr-IN')}</strong></div>
      <div><span>वेगवेगळी उत्पादने</span><strong>{loading && !feed ? '…' : commodityCount.toLocaleString('mr-IN')}</strong></div>
      <div><span>जिल्हे</span><strong>{loading && !feed ? '…' : districts.length.toLocaleString('mr-IN')}</strong></div>
      <div className="market-prices-updated"><span>सरकारी डेटासेट अद्ययावत</span><strong>{feed?.dataUpdatedAt ? stamp(feed.dataUpdatedAt) : '—'}</strong></div>
    </div>

    {error && <div className="market-prices-error" role="status"><CircleAlert size={17}/><span>{error}{feed?.prices?.length ? ' शेवटची मिळालेली माहिती खाली दाखवली आहे.' : ''}</span></div>}

    <div className="market-prices-controls">
      <label className="market-prices-search"><Search size={17}/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="पीक, बाजार किंवा जिल्हा शोधा" aria-label="पीक, बाजार किंवा जिल्हा शोधा"/><span>{filtered.length.toLocaleString('mr-IN')} नोंदी</span></label>
      <label className="market-prices-select"><Wheat size={16}/><select value={crop} onChange={event => setCrop(event.target.value)} aria-label="पिकानुसार फिल्टर करा"><option value="">सर्व पिके</option>{crops.map(name => <option key={name} value={name}>{cropNameInMarathi(name)}</option>)}</select></label>
      <label className="market-prices-select"><MapPin size={16}/><select value={district} onChange={event => setDistrict(event.target.value)} aria-label="जिल्ह्यानुसार फिल्टर करा"><option value="">सर्व जिल्हे</option>{districts.map(name => <option key={name} value={name}>{name}</option>)}</select></label>
      <label className="market-prices-select"><ArrowDownWideNarrow size={16}/><select value={sort} onChange={event => setSort(event.target.value)} aria-label="दरांची मांडणी"><option value="recent">नवीन नोंदी आधी</option><option value="low">कमी मोडल दर आधी</option><option value="high">जास्त मोडल दर आधी</option></select></label>
    </div>

    {loading && !feed ? <div className="market-prices-loading"><RefreshCw className="market-prices-spin" size={20}/> अधिकृत बाजारभाव आणत आहोत…</div>
      : filtered.length ? <>
        <div className="market-prices-grid">
          {filtered.slice(0, visibleCount).map((item, index) => <article className="market-price-card" key={`${item.market}-${item.commodity}-${item.variety}-${item.arrivalDate}-${index}`}>
            <div className="market-price-card-heading"><div><span className="market-price-label">शेतमाल</span><h2>{cropNameInMarathi(item.commodity)}</h2></div><span className="market-price-date"><CalendarDays size={14}/>{stamp(item.arrivalDate)}</span></div>
            <div className="market-price-modal"><strong>{money(item.modalPrice)}</strong><span>मोडल दर / क्विंटल</span></div>
            <div className="market-price-range"><span>किमान <b>{money(item.minPrice)}</b></span><span>कमाल <b>{money(item.maxPrice)}</b></span></div>
            <div className="market-price-market"><MapPin size={15}/><div><b>{item.market || 'बाजार उपलब्ध नाही'}</b><span>{item.district}{item.variety ? ` · ${item.variety}` : ''}</span></div></div>
            {item.grade && <span className="market-price-grade">{item.grade}</span>}
          </article>)}
        </div>
        {visibleCount < filtered.length && <div className="market-prices-more"><button type="button" onClick={() => setVisibleCount(count => count + 40)}>आणखी बाजारभाव दाखवा ({(filtered.length - visibleCount).toLocaleString('mr-IN')} उरले)</button></div>}
      </> : <div className="market-prices-empty"><Search size={24}/><strong>{prices.length ? 'या शोधाशी जुळणारा बाजारभाव नाही' : 'बाजारभाव उपलब्ध नाहीत'}</strong><span>{prices.length ? 'शोध किंवा जिल्हा फिल्टर बदलून पहा.' : 'थोड्या वेळाने पुन्हा प्रयत्न करा.'}</span></div>}

    <footer className="market-prices-source"><span>किंमती घाऊक दर आहेत; स्थानिक बाजारात दर आणि उपलब्धता बदलू शकते.</span><a href={feed?.sourceUrl || SOURCE_URL} target="_blank" rel="noreferrer">स्रोत: Data.gov.in · AGMARKNET</a></footer>
  </section>;
}
