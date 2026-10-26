import React, { useEffect, useMemo, useState } from 'react';
import { Download, Sprout, TrendingDown, TrendingUp, Wallet } from 'lucide-react';
import './farmProfits.css';

const costOf = entry => Number(entry.laborCost || 0) + Number(entry.materialsCost || 0) + Number(entry.otherCost || 0);
const incomeOf = entry => Number(entry.saleIncome || 0);
const cropOf = entry => String(entry.farmSection || 'इतर').split('—')[0].trim() || 'इतर';
const money = value => `₹${Number(value || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
const yearOf = entry => String(entry.entryDate || '').slice(0, 4) || 'तारीख नाही';

function downloadCsv(rows) {
  const headers = ['दिनांक', 'पीक / विभाग', 'हंगाम', 'मजुरी खर्च', 'साहित्य खर्च', 'इतर खर्च', 'एकूण खर्च', 'विक्री उत्पन्न', 'निव्वळ नफा'];
  const fields = ['entryDate', 'farmSection', 'seasonName', 'laborCost', 'materialsCost', 'otherCost'];
  const cell = value => {
    let text = String(value ?? '').replace(/[\r\n]+/g, ' ');
    if (/^[=+\-@]/.test(text)) text = `'${text}`;
    return `"${text.replace(/"/g, '""')}"`;
  };
  const content = '\uFEFF' + [headers.map(cell).join(','), ...rows.map(entry => [
    ...fields.map(field => entry[field]), costOf(entry), incomeOf(entry), incomeOf(entry) - costOf(entry)
  ].map(cell).join(','))].join('\r\n');
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = 'swarnabhoomi-farm-profit.csv'; link.click(); URL.revokeObjectURL(url);
}

export default function FarmProfitPanel({ entries = [] }) {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(String(currentYear));
  const [crop, setCrop] = useState('');
  const years = useMemo(() => [...new Set(entries.map(yearOf).filter(value => value !== 'तारीख नाही'))].sort((a, b) => b.localeCompare(a)), [entries]);
  useEffect(() => { if (years.length && !years.includes(year)) setYear(years[0]); }, [years, year]);
  const crops = useMemo(() => [...new Set(entries.map(cropOf))].sort((a, b) => a.localeCompare(b, 'mr-IN')), [entries]);
  const filtered = useMemo(() => entries.filter(entry => (!year || yearOf(entry) === year) && (!crop || cropOf(entry) === crop)), [entries, year, crop]);
  const totals = useMemo(() => filtered.reduce((result, entry) => ({
    cost: result.cost + costOf(entry), income: result.income + incomeOf(entry),
    production: result.production + Number(entry.productionKg || 0), sold: result.sold + Number(entry.soldKg || 0)
  }), { cost: 0, income: 0, production: 0, sold: 0 }), [filtered]);
  const months = useMemo(() => Array.from({ length: 12 }, (_, month) => {
    const monthEntries = filtered.filter(entry => String(entry.entryDate || '').slice(5, 7) === String(month + 1).padStart(2, '0'));
    const income = monthEntries.reduce((sum, entry) => sum + incomeOf(entry), 0);
    const cost = monthEntries.reduce((sum, entry) => sum + costOf(entry), 0);
    return { label: new Date(2024, month, 1).toLocaleDateString('mr-IN', { month: 'short' }), income, cost, net: income - cost };
  }), [filtered]);
  const maxBar = Math.max(1, ...months.flatMap(month => [month.income, month.cost]));
  const cropResults = useMemo(() => {
    const grouped = new Map();
    filtered.forEach(entry => {
      const name = cropOf(entry); const current = grouped.get(name) || { name, cost: 0, income: 0, production: 0, count: 0 };
      current.cost += costOf(entry); current.income += incomeOf(entry); current.production += Number(entry.productionKg || 0); current.count += 1;
      grouped.set(name, current);
    });
    return [...grouped.values()].sort((a, b) => b.income - a.income);
  }, [filtered]);

  return <section className="farm-profit-page" lang="mr">
    <header className="farm-profit-hero"><div><span><Sprout size={15}/> शेतीचा हिशेब</span><h1>नफा आणि खर्चाचा सारांश</h1><p>दैनिक नोंदींमधील खर्च, विक्री उत्पन्न आणि उत्पादन एकाच ठिकाणी पहा.</p></div><button type="button" className="farm-profit-export" onClick={() => downloadCsv(filtered)} disabled={!filtered.length}><Download size={16}/> CSV डाउनलोड</button></header>
    <div className="farm-profit-filters"><label>वर्ष<select value={year} onChange={event => setYear(event.target.value)}><option value="">सर्व वर्षे</option>{years.map(value => <option key={value}>{value}</option>)}</select></label><label>पीक / विभाग<select value={crop} onChange={event => setCrop(event.target.value)}><option value="">सर्व पिके</option>{crops.map(value => <option key={value}>{value}</option>)}</select></label><span>{filtered.length.toLocaleString('mr-IN')} नोंदी</span></div>
    <div className="farm-profit-metrics"><article className="income"><span><TrendingUp size={17}/> विक्री उत्पन्न</span><strong>{money(totals.income)}</strong><small>नोंदवलेल्या विक्रीतून</small></article><article className="cost"><span><TrendingDown size={17}/> एकूण खर्च</span><strong>{money(totals.cost)}</strong><small>मजुरी, साहित्य आणि इतर</small></article><article className={totals.income - totals.cost >= 0 ? 'net-positive' : 'net-negative'}><span><Wallet size={17}/> अंदाजे निव्वळ नफा</span><strong>{money(totals.income - totals.cost)}</strong><small>उत्पन्न − नोंदवलेला खर्च</small></article><article className="production"><span><Sprout size={17}/> उत्पादन / विक्री</span><strong>{totals.production.toLocaleString('mr-IN', { maximumFractionDigits: 1 })} <small>किलो</small></strong><small>{totals.sold.toLocaleString('mr-IN', { maximumFractionDigits: 1 })} किलो विक्री</small></article></div>
    <div className="farm-profit-content"><section className="farm-profit-chart"><div className="farm-profit-section-head"><div><span>महिन्यानुसार</span><h2>खर्च आणि उत्पन्न</h2></div><div className="farm-profit-legend"><span><i className="income-dot"/> उत्पन्न</span><span><i className="cost-dot"/> खर्च</span></div></div><div className="farm-profit-bars">{months.map(month => <div className="farm-profit-month" key={month.label} title={`${month.label}: उत्पन्न ${money(month.income)}, खर्च ${money(month.cost)}`}><div className="farm-profit-bar-pair"><i className="income-bar" style={{ height: `${month.income / maxBar * 100}%` }}/><i className="cost-bar" style={{ height: `${month.cost / maxBar * 100}%` }}/></div><span>{month.label}</span></div>)}</div><p className="farm-profit-note">नोंदी नसलेल्या महिन्यांचे बार रिकामे दिसतील. हिशेब फक्त नोंदवलेल्या खर्च आणि विक्रीवर आधारित आहे.</p></section>
      <section className="farm-profit-crops"><div className="farm-profit-section-head"><div><span>तुलना</span><h2>पिकानुसार हिशेब</h2></div></div>{cropResults.length ? <div className="farm-profit-crop-list">{cropResults.map(result => <article key={result.name}><div><b>{result.name}</b><small>{result.count.toLocaleString('mr-IN')} नोंदी · {result.production.toLocaleString('mr-IN', { maximumFractionDigits: 1 })} किलो उत्पादन</small></div><div><span>नफा</span><strong className={result.income - result.cost >= 0 ? 'positive' : 'negative'}>{money(result.income - result.cost)}</strong></div><div><span>उत्पन्न / खर्च</span><small>{money(result.income)} / {money(result.cost)}</small></div></article>)}</div> : <div className="farm-profit-empty"><Sprout size={24}/><b>या फिल्टरमध्ये नोंदी नाहीत</b><span>दैनिक नोंदी जोडल्यानंतर हिशेब दिसेल.</span></div>}</section></div>
  </section>;
}
