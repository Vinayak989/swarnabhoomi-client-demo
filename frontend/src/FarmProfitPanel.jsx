import React, { useEffect, useMemo, useState } from 'react';
import {
  ArrowDownRight, ArrowUpRight, BarChart3, CalendarDays, Download,
  RotateCcw, Sprout, TrendingDown, TrendingUp, Wallet
} from 'lucide-react';
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
  const link = document.createElement('a');
  link.href = url;
  link.download = 'swarnabhoomi-farm-profit.csv';
  link.click();
  URL.revokeObjectURL(url);
}

function totalsOf(rows) {
  return rows.reduce((result, entry) => ({
    cost: result.cost + costOf(entry),
    income: result.income + incomeOf(entry),
    production: result.production + Number(entry.productionKg || 0),
    sold: result.sold + Number(entry.soldKg || 0)
  }), { cost: 0, income: 0, production: 0, sold: 0 });
}

function Comparison({ value, previous, label, cost = false }) {
  const change = value - previous;
  const up = change >= 0;
  const favorable = cost ? !up : up;
  const percent = previous === 0 ? null : Math.abs(change / Math.abs(previous)) * 100;
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return <span className={`farm-profit-delta ${favorable ? 'favorable' : 'unfavorable'}`} title={`${label}: मागील वर्षापेक्षा ${money(Math.abs(change))}`}>
    <Icon size={13}/>{percent === null ? money(Math.abs(change)) : `${percent.toLocaleString('mr-IN', { maximumFractionDigits: 0 })}%`}
  </span>;
}

const modeLabels = { income: 'उत्पन्न', cost: 'खर्च', net: 'नफा' };

export default function FarmProfitPanel({ entries = [] }) {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState(String(currentYear));
  const [crop, setCrop] = useState('');
  const [chartMode, setChartMode] = useState('income');
  const [selectedMonth, setSelectedMonth] = useState(null);

  const years = useMemo(() => [...new Set(entries.map(yearOf).filter(value => value !== 'तारीख नाही'))].sort((a, b) => b.localeCompare(a)), [entries]);
  useEffect(() => {
    if (years.length && !years.includes(year)) setYear(years[0]);
    else if (!years.length && year) setYear('');
  }, [years, year]);
  const crops = useMemo(() => [...new Set(entries.map(cropOf))].sort((a, b) => a.localeCompare(b, 'mr-IN')), [entries]);
  const filtered = useMemo(() => entries.filter(entry => (!year || yearOf(entry) === year) && (!crop || cropOf(entry) === crop)), [entries, year, crop]);
  const totals = useMemo(() => totalsOf(filtered), [filtered]);
  const profit = totals.income - totals.cost;
  const previousYear = year ? String(Number(year) - 1) : '';
  const previousRows = useMemo(() => previousYear ? entries.filter(entry => yearOf(entry) === previousYear && (!crop || cropOf(entry) === crop)) : [], [entries, previousYear, crop]);
  const previousTotals = useMemo(() => totalsOf(previousRows), [previousRows]);

  const months = useMemo(() => Array.from({ length: 12 }, (_, month) => {
    const monthEntries = filtered.filter(entry => String(entry.entryDate || '').slice(5, 7) === String(month + 1).padStart(2, '0'));
    const income = monthEntries.reduce((sum, entry) => sum + incomeOf(entry), 0);
    const cost = monthEntries.reduce((sum, entry) => sum + costOf(entry), 0);
    return {
      index: month,
      label: new Date(2024, month, 1).toLocaleDateString('mr-IN', { month: 'short' }),
      income, cost, net: income - cost, count: monthEntries.length
    };
  }), [filtered]);
  const maxBar = Math.max(1, ...months.map(month => chartMode === 'net' ? Math.abs(month.net) : month[chartMode]));
  const selectedMonthData = selectedMonth === null ? null : months[selectedMonth];

  const cropResults = useMemo(() => {
    const grouped = new Map();
    filtered.forEach(entry => {
      const name = cropOf(entry);
      const current = grouped.get(name) || { name, cost: 0, income: 0, production: 0, count: 0 };
      current.cost += costOf(entry);
      current.income += incomeOf(entry);
      current.production += Number(entry.productionKg || 0);
      current.count += 1;
      grouped.set(name, current);
    });
    return [...grouped.values()].sort((a, b) => (b.income - b.cost) - (a.income - a.cost));
  }, [filtered]);
  const bestCrop = cropResults[0]?.name;

  return <section className="farm-profit-page" lang="mr">
    <header className="farm-profit-hero">
      <div className="farm-profit-hero-copy">
        <span className="farm-profit-eyebrow"><Sprout size={15}/> शेतीचा नफा</span>
        <h1>तुमच्या शेतीचा हिशेब, एका नजरेत</h1>
        <p>विक्री, खर्च आणि पिकांचा परतावा समजून घ्या. फिल्टर बदला, महिन्यावर टॅप करा आणि तुमच्या नोंदींची तुलना करा.</p>
        <div className="farm-profit-hero-points"><span><i/> नोंदीनुसार थेट हिशेब</span><span><i/> कधीही CSV डाउनलोड</span></div>
      </div>
      <div className="farm-profit-hero-art" aria-hidden="true"><div className="farm-profit-sun"/><div className="farm-profit-hill hill-back"/><div className="farm-profit-hill hill-front"/><div className="farm-profit-art-card"><span>निव्वळ नफा</span><strong>{money(profit)}</strong><small>{year || 'सर्व वर्षे'} चा हिशेब</small></div><Sprout className="farm-profit-art-sprout" size={48}/></div>
      <button type="button" className="farm-profit-export" onClick={() => downloadCsv(filtered)} disabled={!filtered.length}><Download size={16}/> CSV डाउनलोड</button>
    </header>

    <div className="farm-profit-filters">
      <div className="farm-profit-filter-title"><span className="farm-profit-filter-icon"><CalendarDays size={17}/></span><div><b>तुमचा हिशेब निवडा</b><small>वर्ष आणि पीक बदलून तपशील पाहा</small></div></div>
      <label>वर्ष<select value={year} onChange={event => { setYear(event.target.value); setSelectedMonth(null); }}><option value="">सर्व वर्षे</option>{years.map(value => <option key={value} value={value}>{value}</option>)}</select></label>
      <label>पीक / विभाग<select value={crop} onChange={event => { setCrop(event.target.value); setSelectedMonth(null); }}><option value="">सर्व पिके</option>{crops.map(value => <option key={value} value={value}>{value}</option>)}</select></label>
      {(crop || !years.includes(String(currentYear))) && <button type="button" className="farm-profit-reset" onClick={() => { setCrop(''); if (years.includes(String(currentYear))) setYear(String(currentYear)); else if (years[0]) setYear(years[0]); setSelectedMonth(null); }}><RotateCcw size={13}/> फिल्टर रीसेट</button>}
      <span className="farm-profit-record-count"><b>{filtered.length.toLocaleString('mr-IN')}</b> नोंदी</span>
    </div>

    <div className="farm-profit-metrics">
      <article className="income"><span><i className="farm-profit-metric-icon"><TrendingUp size={17}/></i> विक्री उत्पन्न</span><strong>{money(totals.income)}</strong><small>नोंदवलेल्या विक्रीतून</small>{previousRows.length > 0 && <div className="farm-profit-metric-compare"><span>{previousYear} पेक्षा</span><Comparison value={totals.income} previous={previousTotals.income} label="उत्पन्न"/></div>}</article>
      <article className="cost"><span><i className="farm-profit-metric-icon"><TrendingDown size={17}/></i> एकूण खर्च</span><strong>{money(totals.cost)}</strong><small>मजुरी, साहित्य आणि इतर</small>{previousRows.length > 0 && <div className="farm-profit-metric-compare"><span>{previousYear} पेक्षा</span><Comparison value={totals.cost} previous={previousTotals.cost} label="खर्च" cost/></div>}</article>
      <article className={profit >= 0 ? 'net-positive' : 'net-negative'}><span><i className="farm-profit-metric-icon"><Wallet size={17}/></i> अंदाजे निव्वळ नफा</span><strong>{money(profit)}</strong><small>उत्पन्न वजा नोंदवलेला खर्च</small>{previousRows.length > 0 && <div className="farm-profit-metric-compare"><span>{previousYear} पेक्षा</span><Comparison value={profit} previous={previousTotals.income - previousTotals.cost} label="नफा"/></div>}</article>
      <article className="production"><span><i className="farm-profit-metric-icon"><Sprout size={17}/></i> उत्पादन / विक्री</span><strong>{totals.production.toLocaleString('mr-IN', { maximumFractionDigits: 1 })} <small>किलो</small></strong><small>{totals.sold.toLocaleString('mr-IN', { maximumFractionDigits: 1 })} किलो विक्री</small><div className="farm-profit-production-track"><i style={{ width: `${totals.production > 0 ? Math.min(100, totals.sold / totals.production * 100) : 0}%` }}/></div></article>
    </div>

    {previousRows.length > 0 && <div className="farm-profit-comparison-note"><TrendingUp size={15}/><span><b>{year} विरुद्ध {previousYear}</b> — टक्केवारी बदल मागील वर्षाच्या नोंदींवर आधारित आहे.</span></div>}

    <div className="farm-profit-content">
      <section className="farm-profit-chart">
        <div className="farm-profit-section-head">
          <div><span className="farm-profit-section-kicker"><BarChart3 size={14}/> महिन्यानुसार विश्लेषण</span><h2>उत्पन्न, खर्च आणि नफा</h2><p>महिन्याच्या बारवर टॅप करून त्या महिन्याचा तपशील पाहा.</p></div>
          <div className="farm-profit-chart-modes" role="group" aria-label="चार्टमध्ये दाखवायचा हिशेब">
            {Object.entries(modeLabels).map(([mode, label]) => <button key={mode} type="button" className={chartMode === mode ? 'active' : ''} aria-pressed={chartMode === mode} onClick={() => setChartMode(mode)}>{label}</button>)}
          </div>
        </div>
        <div className={`farm-profit-legend ${chartMode === 'net' ? 'single' : ''}`}><span><i className={`${chartMode === 'net' ? 'net-dot' : chartMode === 'cost' ? 'cost-dot' : 'income-dot'}`}/>{modeLabels[chartMode]}</span>{chartMode === 'income' && <span><i className="cost-dot"/> खर्च</span>}</div>
        <div className="farm-profit-chart-scroll"><div className={`farm-profit-bars mode-${chartMode}`}>
          {months.map(month => {
            const value = month[chartMode];
            const height = value === 0 ? 0 : Math.max(4, Math.abs(value) / maxBar * 100);
            const selected = selectedMonth === month.index;
            return <button type="button" key={month.index} className={`farm-profit-month ${selected ? 'selected' : ''}`} aria-pressed={selected} aria-label={`${month.label}: ${modeLabels[chartMode]} ${money(value)}, ${month.count} नोंदी`} onClick={() => setSelectedMonth(selected ? null : month.index)}>
              <span className="farm-profit-bar-area">{chartMode === 'income' ? <><i className="income-bar" style={{ height: `${month.income === 0 ? 0 : Math.max(4, month.income / maxBar * 100)}%` }}/><i className="cost-bar" style={{ height: `${month.cost === 0 ? 0 : Math.max(4, month.cost / maxBar * 100)}%` }}/></> : <i className={`${chartMode}-bar ${chartMode === 'net' && value < 0 ? 'negative' : ''}`} style={{ height: `${height}%` }}/>}</span>
              <span className="farm-profit-month-label">{month.label}</span>
              {month.count > 0 && <i className="farm-profit-month-marker"/>}
            </button>;
          })}
        </div></div>
        {selectedMonthData && <div className="farm-profit-month-detail"><div className="farm-profit-month-detail-title"><span><CalendarDays size={15}/>{selectedMonthData.label} {year || 'सर्व वर्षे'}</span><button type="button" onClick={() => setSelectedMonth(null)}>बंद करा</button></div><div className="farm-profit-month-detail-values"><span><small>विक्री उत्पन्न</small><b>{money(selectedMonthData.income)}</b></span><span><small>एकूण खर्च</small><b>{money(selectedMonthData.cost)}</b></span><span><small>निव्वळ नफा</small><b className={selectedMonthData.net >= 0 ? 'positive' : 'negative'}>{money(selectedMonthData.net)}</b></span><span><small>नोंदी</small><b>{selectedMonthData.count.toLocaleString('mr-IN')}</b></span></div></div>}
        <p className="farm-profit-note">नफा = नोंदवलेले विक्री उत्पन्न − नोंदवलेला खर्च. रिकाम्या महिन्यांमध्ये नोंद नाही.</p>
      </section>

      <section className="farm-profit-crops">
        <div className="farm-profit-section-head"><div><span className="farm-profit-section-kicker"><Sprout size={14}/> पिकांची कामगिरी</span><h2>पिकानुसार हिशेब</h2><p>पिकावर टॅप करून त्याचा स्वतंत्र हिशेब पाहा.</p></div>{crop && <button type="button" className="farm-profit-clear-crop" onClick={() => setCrop('')}>सर्व पिके</button>}</div>
        {cropResults.length ? <div className="farm-profit-crop-list">{cropResults.map((result, index) => {
          const resultProfit = result.income - result.cost;
          const selected = crop === result.name;
          const ratio = result.income > 0 ? Math.max(0, Math.min(100, result.cost / result.income * 100)) : (result.cost > 0 ? 100 : 0);
          return <button type="button" className={`farm-profit-crop-card ${selected ? 'selected' : ''}`} key={result.name} aria-pressed={selected} onClick={() => { setCrop(selected ? '' : result.name); setSelectedMonth(null); }}>
            <div className="farm-profit-crop-top"><span className="farm-profit-crop-avatar"><Sprout size={17}/></span><span className="farm-profit-crop-name"><b>{result.name}</b><small>{result.count.toLocaleString('mr-IN')} नोंदी · {result.production.toLocaleString('mr-IN', { maximumFractionDigits: 1 })} किलो उत्पादन</small></span>{result.name === bestCrop && resultProfit > 0 && <span className="farm-profit-best">उत्तम</span>}</div>
            <div className="farm-profit-crop-profit"><span>निव्वळ नफा</span><b className={resultProfit >= 0 ? 'positive' : 'negative'}>{money(resultProfit)}</b></div>
            <div className="farm-profit-crop-track" aria-label={`खर्चाचे उत्पन्नाशी प्रमाण ${Math.round(ratio)} टक्के`}><i style={{ width: `${ratio}%` }}/></div>
            <div className="farm-profit-crop-breakdown"><span>उत्पन्न <b>{money(result.income)}</b></span><span>खर्च <b>{money(result.cost)}</b></span></div>
          </button>;
        })}</div> : <div className="farm-profit-empty"><Sprout size={25}/><b>या फिल्टरमध्ये नोंदी नाहीत</b><span>फिल्टर बदला किंवा दैनिक नोंद जोडा. नोंदी जोडल्यावर पिकांचा हिशेब इथे दिसेल.</span></div>}
      </section>
    </div>
  </section>;
}
