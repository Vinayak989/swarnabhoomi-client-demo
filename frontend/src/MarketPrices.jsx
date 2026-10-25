import { useEffect, useMemo, useState } from 'react';
import { ArrowDownWideNarrow, CalendarDays, ChartNoAxesCombined, CircleAlert, MapPin, RefreshCw, Search, Wheat } from 'lucide-react';
import './marketPrices.css';

const SOURCE_URL = 'https://www.data.gov.in/catalog/current-daily-price-various-commodities-various-markets-mandi';
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

export default function MarketPricesPanel({ apiFetch, apiBase }) {
  const [feed, setFeed] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [district, setDistrict] = useState('');
  const [sort, setSort] = useState('recent');
  const [visibleCount, setVisibleCount] = useState(40);

  async function load(force = false) {
    if (force) setRefreshing(true); else setLoading(true);
    try {
      const response = await apiFetch(`${apiBase}/market-prices${force ? '?refresh=true' : ''}`);
      const result = await response.json();
      if (!response.ok) throw new Error(result.message || 'बाजारभाव सध्या मिळत नाहीत.');
      setFeed(result);
      setError(result.error || '');
    } catch (exception) {
      setError(exception.message || 'बाजारभाव आणता आले नाहीत. इंटरनेट जोडून पुन्हा प्रयत्न करा.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(true), 15 * 60 * 1000);
    return () => window.clearInterval(timer);
  }, [apiFetch, apiBase]);

  const prices = feed?.prices || [];
  const districts = useMemo(() => [...new Set(prices.map(item => item.district).filter(Boolean))].sort((a, b) => a.localeCompare(b)), [prices]);
  const commodityCount = useMemo(() => new Set(prices.map(item => item.commodity).filter(Boolean)).size, [prices]);
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return prices.filter(item => (!district || item.district === district)
      && (!query || [item.commodity, item.variety, item.grade, item.market, item.district].some(value => String(value || '').toLocaleLowerCase().includes(query))))
      .sort((a, b) => {
        if (sort === 'low') return Number(a.modalPrice ?? Infinity) - Number(b.modalPrice ?? Infinity);
        if (sort === 'high') return Number(b.modalPrice ?? -Infinity) - Number(a.modalPrice ?? -Infinity);
        return dateValue(b.arrivalDate) - dateValue(a.arrivalDate) || String(a.commodity).localeCompare(String(b.commodity));
      });
  }, [prices, search, district, sort]);

  useEffect(() => { setVisibleCount(40); }, [search, district, sort]);

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
      <label className="market-prices-select"><MapPin size={16}/><select value={district} onChange={event => setDistrict(event.target.value)} aria-label="जिल्ह्यानुसार फिल्टर करा"><option value="">सर्व जिल्हे</option>{districts.map(name => <option key={name} value={name}>{name}</option>)}</select></label>
      <label className="market-prices-select"><ArrowDownWideNarrow size={16}/><select value={sort} onChange={event => setSort(event.target.value)} aria-label="दरांची मांडणी"><option value="recent">नवीन नोंदी आधी</option><option value="low">कमी मोडल दर आधी</option><option value="high">जास्त मोडल दर आधी</option></select></label>
    </div>

    {loading && !feed ? <div className="market-prices-loading"><RefreshCw className="market-prices-spin" size={20}/> अधिकृत बाजारभाव आणत आहोत…</div>
      : filtered.length ? <>
        <div className="market-prices-grid">
          {filtered.slice(0, visibleCount).map((item, index) => <article className="market-price-card" key={`${item.market}-${item.commodity}-${item.variety}-${item.arrivalDate}-${index}`}>
            <div className="market-price-card-heading"><div><span className="market-price-label">शेतमाल</span><h2>{item.commodity}</h2></div><span className="market-price-date"><CalendarDays size={14}/>{stamp(item.arrivalDate)}</span></div>
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
