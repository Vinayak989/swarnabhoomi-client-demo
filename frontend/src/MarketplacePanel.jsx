import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import { ArrowDownWideNarrow, LocateFixed, MapPin, PackagePlus, Phone, Search, ShieldCheck, Upload, UserRound, Wheat, X } from 'lucide-react';
import 'leaflet/dist/leaflet.css';
import './marketplace.css';

const EMPTY_FORM = {
  sellerName: '',
  phone: '',
  address: '',
  latitude: '',
  longitude: '',
  productName: '',
  cropType: '',
  category: 'भाजीपाला',
  quantity: '',
  unit: 'kg',
  description: ''
};

const CATEGORIES = ['भाजीपाला', 'फळे', 'धान्य', 'कडधान्ये', 'मसाले', 'दुग्धजन्य', 'मध व इतर', 'इतर'];
const UNITS = [
  ['kg', 'किलो'],
  ['quintal', 'क्विंटल'],
  ['ton', 'टन'],
  ['dozen', 'डझन'],
  ['crate', 'क्रेट'],
  ['piece', 'नग']
];
const DEFAULT_CENTER = [16.8524, 74.5815];
const PHOTO_LIMIT = 10 * 1024 * 1024;

function distanceKm(a, b) {
  const radians = value => value * Math.PI / 180;
  const latitude = radians(b.latitude - a.latitude);
  const longitude = radians(b.longitude - a.longitude);
  const value = Math.sin(latitude / 2) ** 2
    + Math.cos(radians(a.latitude)) * Math.cos(radians(b.latitude)) * Math.sin(longitude / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}

function locateUser(onSuccess, onError) {
  if (!navigator.geolocation) {
    onError('या ब्राउझरमध्ये स्थान सुविधा उपलब्ध नाही.');
    return;
  }
  navigator.geolocation.getCurrentPosition(
    position => onSuccess({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
    error => onError(error.code === error.PERMISSION_DENIED
      ? 'जवळचे विक्रेते दाखवण्यासाठी ब्राउझरमध्ये स्थान परवानगी द्या.'
      : 'तुमचे स्थान मिळाले नाही. पत्ता व नकाशावरील जागा तपासा.'),
    { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 }
  );
}

function SellerMap({ listings, buyerLocation, onSelect }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markerLayerRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return undefined;
    const map = L.map(containerRef.current, { scrollWheelZoom: false }).setView(DEFAULT_CENTER, 9);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
    }).addTo(map);
    const markers = L.layerGroup().addTo(map);
    mapRef.current = map;
    markerLayerRef.current = markers;
    const resize = window.setTimeout(() => map.invalidateSize(), 120);
    return () => {
      window.clearTimeout(resize);
      map.remove();
      mapRef.current = null;
      markerLayerRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const markerLayer = markerLayerRef.current;
    if (!map || !markerLayer) return;
    markerLayer.clearLayers();
    const points = [];

    listings.forEach(listing => {
      const point = [listing.latitude, listing.longitude];
      points.push(point);
      const marker = L.marker(point, {
        icon: L.divIcon({
          className: 'market-map-pin',
          html: '<span>🌾</span>',
          iconSize: [38, 42],
          iconAnchor: [19, 39]
        })
      }).addTo(markerLayer);
      const popup = document.createElement('div');
      const name = document.createElement('strong');
      name.textContent = listing.productName;
      const seller = document.createElement('span');
      seller.textContent = listing.sellerName;
      popup.append(name, seller);
      marker.bindPopup(popup);
      marker.on('click', () => onSelect(listing.id));
    });

    if (buyerLocation) {
      const point = [buyerLocation.latitude, buyerLocation.longitude];
      points.push(point);
      L.circleMarker(point, {
        radius: 8,
        color: '#fff',
        weight: 2,
        fillColor: '#3180b5',
        fillOpacity: 1
      }).bindTooltip('तुमचे स्थान').addTo(markerLayer);
    }

    if (points.length > 1) {
      map.fitBounds(L.latLngBounds(points), { padding: [28, 28], maxZoom: 13 });
    } else if (points.length === 1) {
      map.setView(points[0], buyerLocation ? 12 : 14);
    } else {
      map.setView(buyerLocation ? [buyerLocation.latitude, buyerLocation.longitude] : DEFAULT_CENTER, 10);
    }
    const resize = window.setTimeout(() => map.invalidateSize(), 80);
    return () => window.clearTimeout(resize);
  }, [listings, buyerLocation, onSelect]);

  return <div className="market-map" ref={containerRef} aria-label="जवळील शेतमाल विक्रेत्यांचा नकाशा"/>;
}

function ProductPhoto({ apiBase, photoUrl, authorization, productName }) {
  const [source, setSource] = useState('');
  useEffect(() => {
    let active = true;
    let objectUrl = '';
    if (!photoUrl) {
      setSource('');
      return undefined;
    }
    const headers = authorization ? { Authorization: `Basic ${authorization}` } : {};
    fetch(`${apiBase}${photoUrl}`, { headers })
      .then(response => {
        if (!response.ok) throw new Error('फोटो उपलब्ध नाही.');
        return response.blob();
      })
      .then(blob => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setSource(objectUrl);
      })
      .catch(() => {
        if (active) setSource('');
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [apiBase, photoUrl, authorization]);
  return source
    ? <img className="market-product-photo" src={source} alt={`${productName} विक्रीचा फोटो`}/>
    : <div className="market-product-photo market-photo-placeholder"><Wheat size={30}/><span>फोटो नाही</span></div>;
}

function MarketplacePanel({ apiBase, auth, onAuthenticated, onUnauthorized }) {
  const [view, setView] = useState('browse');
  const [listings, setListings] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [photo, setPhoto] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [authorization, setAuthorization] = useState(auth);
  const [search, setSearch] = useState('');
  const [buyerLocation, setBuyerLocation] = useState(null);
  const [radius, setRadius] = useState(25);
  const [nearbyOnly, setNearbyOnly] = useState(false);
  const [locationMessage, setLocationMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [loginOpen, setLoginOpen] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [pendingAction, setPendingAction] = useState(null);
  const [credentials, setCredentials] = useState({ username: '', password: '' });
  const [contact, setContact] = useState(null);
  const [activeListing, setActiveListing] = useState(null);
  const fileInputRef = useRef(null);
  const formRef = useRef(form);
  const photoRef = useRef(photo);
  const onAuthenticatedRef = useRef(onAuthenticated);
  const onUnauthorizedRef = useRef(onUnauthorized);
  formRef.current = form;
  photoRef.current = photo;
  onAuthenticatedRef.current = onAuthenticated;
  onUnauthorizedRef.current = onUnauthorized;

  useEffect(() => setAuthorization(auth), [auth]);
  useEffect(() => {
    if (!photo) {
      setPreviewUrl('');
      return undefined;
    }
    const url = URL.createObjectURL(photo);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  const request = useCallback(async (path, options = {}, token = authorization) => {
    const headers = { ...(options.headers || {}) };
    if (token) headers.Authorization = `Basic ${token}`;
    const response = await fetch(`${apiBase}${path}`, { ...options, headers });
    return response;
  }, [apiBase, authorization]);

  const loadListings = useCallback(async (token = authorization) => {
    setLoading(true);
    setError('');
    try {
      const response = await request('/marketplace/listings', {}, token);
      if (response.status === 401) {
        onUnauthorizedRef.current?.();
        return;
      }
      if (!response.ok) throw new Error('विक्रीची माहिती मिळाली नाही. सर्व्हरची स्थिती तपासा.');
      setListings(await response.json());
    } catch (loadError) {
      setError(loadError.message || 'विक्रीची माहिती मिळाली नाही.');
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    loadListings();
  }, [loadListings]);

  const filteredListings = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('mr-IN');
    return listings
      .map(listing => ({
        ...listing,
        distance: buyerLocation ? distanceKm(buyerLocation, listing) : null
      }))
      .filter(listing => !query || [
        listing.sellerName, listing.productName, listing.cropType, listing.category, listing.address
      ].join(' ').toLocaleLowerCase('mr-IN').includes(query))
      .filter(listing => !nearbyOnly || buyerLocation && listing.distance <= radius)
      .sort((a, b) => buyerLocation
        ? a.distance - b.distance
        : b.id - a.id);
  }, [listings, search, buyerLocation, nearbyOnly, radius]);

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value }));
  }

  function askForLocation(useForListing = false) {
    setLocationMessage('');
    locateUser(location => {
      if (useForListing) {
        setForm(current => ({
          ...current,
          latitude: location.latitude.toFixed(6),
          longitude: location.longitude.toFixed(6)
        }));
      } else {
        setBuyerLocation(location);
        setNearbyOnly(true);
      }
    }, setLocationMessage);
  }

  function beginLogin(action) {
    setPendingAction(action);
    setLoginError('');
    setLoginOpen(true);
  }

  async function fetchContact(id, token = authorization) {
    setError('');
    try {
      const response = await request(`/marketplace/listings/${id}/contact`, {}, token);
      if (response.status === 401) {
        beginLogin({ type: 'contact', id });
        return;
      }
      if (response.status === 503) {
        const body = await response.json();
        throw new Error(body.message || 'सर्व्हरवर अॅप प्रवेश कॉन्फिगर केलेला नाही.');
      }
      if (!response.ok) throw new Error('विक्रेत्याचा संपर्क मिळाला नाही.');
      setContact({ id, phone: (await response.json()).phone });
    } catch (contactError) {
      setError(contactError.message || 'विक्रेत्याचा संपर्क मिळाला नाही.');
    }
  }

  async function saveListing(token = authorization) {
    const current = formRef.current;
    const currentPhoto = photoRef.current;
    setSaving(true);
    setError('');
    try {
      let photoUrl = '';
      if (currentPhoto) {
        const photoData = new FormData();
        photoData.append('file', currentPhoto);
        const photoResponse = await request('/photos', { method: 'POST', body: photoData }, token);
        if (photoResponse.status === 401) {
          beginLogin({ type: 'save' });
          return;
        }
        if (!photoResponse.ok) throw new Error('फोटो अपलोड झाला नाही. १० MB पेक्षा कमी आकाराचा फोटो निवडा.');
        photoUrl = (await photoResponse.json()).url;
      }
      const payload = {
        ...current,
        latitude: Number(current.latitude),
        longitude: Number(current.longitude),
        quantity: Number(current.quantity),
        photoUrl: photoUrl || null
      };
      const response = await request('/marketplace/listings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }, token);
      if (response.status === 401) {
        beginLogin({ type: 'save' });
        return;
      }
      if (response.status === 503) {
        const body = await response.json();
        throw new Error(body.message || 'विक्रेता नोंदणीसाठी अॅप प्रवेश तपशील आवश्यक आहेत.');
      }
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || 'विक्रीची नोंद जतन करता आली नाही. भरलेली माहिती तपासा.');
      }
      setForm(EMPTY_FORM);
      formRef.current = EMPTY_FORM;
      setPhoto(null);
      photoRef.current = null;
      setView('browse');
      await loadListings(token);
      setActiveListing((await response.json()).id);
    } catch (saveError) {
      setError(saveError.message || 'विक्रीची नोंद जतन करता आली नाही.');
    } finally {
      setSaving(false);
    }
  }

  async function submitListing(event) {
    event.preventDefault();
    if (!authorization) {
      beginLogin({ type: 'save' });
      return;
    }
    await saveListing();
  }

  async function signIn(event) {
    event.preventDefault();
    setLoginError('');
    const token = btoa(`${credentials.username}:${credentials.password}`);
    try {
      const response = await fetch(`${apiBase}/marketplace/access`, {
        method: 'POST',
        headers: { Authorization: `Basic ${token}` }
      });
      if (response.status === 503) {
        const body = await response.json();
        throw new Error(body.message || 'अॅप प्रवेश तपशील सर्व्हरवर कॉन्फिगर केलेले नाहीत.');
      }
      if (!response.ok) throw new Error('वापरकर्तानाव किंवा पासवर्ड तपासा.');
      sessionStorage.setItem('swarnabhoomi-auth', token);
      setAuthorization(token);
      onAuthenticatedRef.current?.(token);
      const action = pendingAction;
      setPendingAction(null);
      setLoginOpen(false);
      setCredentials(current => ({ ...current, password: '' }));
      if (action?.type === 'save') await saveListing(token);
      if (action?.type === 'contact') await fetchContact(action.id, token);
    } catch (signInError) {
      setLoginError(signInError.message || 'प्रवेश करता आला नाही.');
    }
  }

  function selectPhoto(event) {
    const file = event.target.files?.[0] || null;
    event.target.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      setError('कृपया फोटो फाइल निवडा.');
      return;
    }
    if (file.size > PHOTO_LIMIT) {
      setError('फोटोचा आकार १० MB पेक्षा कमी असावा.');
      return;
    }
    setError('');
    setPhoto(file);
    photoRef.current = file;
  }

  function formatQuantity(listing) {
    const unit = UNITS.find(([value]) => value === listing.unit)?.[1] || listing.unit;
    return `${Number(listing.quantity).toLocaleString('mr-IN', { maximumFractionDigits: 2 })} ${unit}`;
  }

  const phoneDigits = contact?.phone.replace(/\D/g, '') || '';

  return <div className="market-page" lang="mr">
    <section className="market-hero">
      <div>
        <span className="market-eyebrow"><Wheat size={15}/> शेतमाल थेट शेतकऱ्याकडून</span>
        <h1>शेतकरी बाजार</h1>
        <p>उपलब्ध शेतमाल शोधा, जवळचे विक्रेते नकाशावर पाहा किंवा तुमची विक्रीची नोंद जोडा.</p>
      </div>
      <div className="market-hero-icon"><MapPin size={26}/><span>{listings.length.toLocaleString('mr-IN')} नोंदी</span></div>
    </section>

    <div className="market-actions" role="tablist" aria-label="शेतकरी बाजार विभाग">
      <button type="button" role="tab" aria-selected={view === 'browse'} className={view === 'browse' ? 'active' : ''} onClick={() => setView('browse')}><Search size={16}/> शेतमाल शोधा</button>
      <button type="button" role="tab" aria-selected={view === 'register'} className={view === 'register' ? 'active' : ''} onClick={() => { setError(''); setView('register'); }}><PackagePlus size={16}/> माझा शेतमाल नोंदवा</button>
    </div>

    {error && <div className="market-message market-error" role="alert">{error}</div>}

    {view === 'browse' ? <>
      <section className="market-search-panel">
        <label className="market-search-field"><Search size={17}/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="पीक, शेतमाल, विक्रेता किंवा पत्ता शोधा"/></label>
        <button className="market-location-button" type="button" onClick={() => askForLocation(false)}><LocateFixed size={16}/> जवळचे विक्रेते</button>
        <label className="market-radius">त्रिज्या
          <select value={radius} onChange={event => setRadius(Number(event.target.value))}>
            <option value={5}>५ किमी</option><option value={25}>२५ किमी</option><option value={50}>५० किमी</option><option value={100}>१०० किमी</option>
          </select>
        </label>
        <button className={`market-nearby-toggle ${nearbyOnly ? 'selected' : ''}`} type="button" onClick={() => buyerLocation ? setNearbyOnly(value => !value) : askForLocation(false)}><ArrowDownWideNarrow size={15}/>{nearbyOnly ? 'सर्व विक्रेते दाखवा' : 'फक्त जवळचे दाखवा'}</button>
      </section>
      {locationMessage && <p className="market-location-note" role="status">{locationMessage}</p>}
      <div className="market-map-layout">
        <section className="market-map-card">
          <div className="market-card-heading"><div><span>OpenStreetMap</span><h2>नकाशावरील विक्रेते</h2></div><span className="market-result-count">{filteredListings.length.toLocaleString('mr-IN')} विक्रेते</span></div>
          <SellerMap listings={filteredListings} buyerLocation={buyerLocation} onSelect={setActiveListing}/>
          <p className="market-map-note">निळा ठिपका तुमचे स्थान; हिरवे चिन्ह शेतमाल विक्रेता दाखवते.</p>
        </section>
        <section className="market-listings">
          <div className="market-card-heading"><div><span>उपलब्ध शेतमाल</span><h2>विक्रेते</h2></div>{buyerLocation&&<span className="market-result-count"><LocateFixed size={12}/> जवळचे आधी</span>}</div>
          {loading ? <div className="market-empty">विक्रीची माहिती लोड होत आहे…</div>
            : !filteredListings.length ? <div className="market-empty"><Wheat size={28}/><b>शेतमाल सापडला नाही</b><span>शोध बदलून पाहा किंवा नवी विक्रीची नोंद जोडा.</span></div>
              : <div className="market-listing-scroll">{filteredListings.map(listing=><article className={`market-listing-card ${activeListing === listing.id ? 'focused' : ''}`} key={listing.id} onClick={() => setActiveListing(listing.id)}>
                <ProductPhoto apiBase={apiBase} photoUrl={listing.photoUrl} authorization={authorization} productName={listing.productName}/>
                <div className="market-listing-content">
                  <div className="market-listing-heading"><span>{listing.category}</span>{listing.distance !== null&&<small>{listing.distance < 1 ? `${Math.round(listing.distance*1000).toLocaleString('mr-IN')} मी` : `${listing.distance.toLocaleString('mr-IN',{maximumFractionDigits:1})} किमी`}</small>}</div>
                  <h3>{listing.productName}</h3><p className="market-crop-type">{listing.cropType}</p>
                  <p className="market-stock">{formatQuantity(listing)} उपलब्ध</p>
                  <div className="market-seller"><UserRound size={13}/><b>{listing.sellerName}</b></div>
                  <p className="market-address"><MapPin size={13}/>{listing.address}</p>
                  <button type="button" className="market-contact-button" onClick={event => { event.stopPropagation(); fetchContact(listing.id); }}><Phone size={14}/> संपर्क मिळवा</button>
                </div>
              </article>)}</div>}
        </section>
      </div>
    </> : <section className="market-form-card">
      <div className="market-card-heading"><div><span>विक्रेता नोंदणी</span><h2>तुमचा उपलब्ध शेतमाल जोडा</h2></div><div className="market-private-badge"><ShieldCheck size={14}/> संपर्क फक्त प्रवेशानंतर</div></div>
      <p className="market-form-intro">तुमची विक्रीची माहिती आणि अचूक पत्ता नोंदवा. नकाशावरील स्थान तुमच्या ब्राउझरमधून घ्या किंवा निर्देशांक भरा.</p>
      <form className="market-form" onSubmit={submitListing}>
        <div className="market-form-section-title"><UserRound size={16}/> विक्रेता आणि संपर्क</div>
        <div className="market-form-grid">
          <label className="market-field"><span>शेतकरी / विक्रेत्याचे नाव *</span><input name="sellerName" value={form.sellerName} onChange={updateField} maxLength={100} required placeholder="पूर्ण नाव"/></label>
          <label className="market-field"><span>मोबाइल नंबर * <small>भारतीय नंबर</small></span><input name="phone" value={form.phone} onChange={updateField} maxLength={18} inputMode="tel" autoComplete="tel" pattern="(?:\+91[\s-]?)?[6-9][0-9\s-]{9,14}" required placeholder="+91 98765 43210"/></label>
          <label className="market-field market-field-wide"><span>संपूर्ण पत्ता *</span><textarea name="address" value={form.address} onChange={updateField} maxLength={500} required placeholder="गाव, तालुका, जिल्हा आणि शेताचा पत्ता"/></label>
        </div>
        <div className="market-location-fields">
          <div className="market-form-section-title"><MapPin size={16}/> नकाशावरील अचूक शेताचे स्थान *</div>
          <button type="button" className="market-location-button" onClick={() => askForLocation(true)}><LocateFixed size={15}/> माझे सध्याचे स्थान घ्या</button>
          <div className="market-form-grid market-coordinate-grid">
            <label className="market-field"><span>अक्षांश (Latitude)</span><input name="latitude" type="number" min="-90" max="90" step="any" value={form.latitude} onChange={updateField} required placeholder="उदा. 16.8524"/></label>
            <label className="market-field"><span>रेखांश (Longitude)</span><input name="longitude" type="number" min="-180" max="180" step="any" value={form.longitude} onChange={updateField} required placeholder="उदा. 74.5815"/></label>
          </div>
          <small className="market-precision-note">तुम्ही निवडलेल्या अचूक निर्देशांकांवर विक्रेता नकाशावर दिसेल.</small>
        </div>
        <div className="market-form-section-title"><Wheat size={16}/> विक्रीसाठी शेतमाल</div>
        <div className="market-form-grid">
          <label className="market-field"><span>शेतमाल / खाद्यपदार्थाचे नाव *</span><input name="productName" value={form.productName} onChange={updateField} maxLength={120} required placeholder="उदा. ताजी हळद"/></label>
          <label className="market-field"><span>पीक / प्रकार *</span><input name="cropType" value={form.cropType} onChange={updateField} maxLength={80} required placeholder="उदा. सेंद्रिय हळद, आंबा"/></label>
          <label className="market-field"><span>शेतमालाचा प्रकार *</span><select name="category" value={form.category} onChange={updateField}>{CATEGORIES.map(category=><option key={category}>{category}</option>)}</select></label>
          <div className="market-quantity-field">
            <label className="market-field"><span>उपलब्ध प्रमाण *</span><input name="quantity" type="number" min="0.01" max="1000000000" step="any" value={form.quantity} onChange={updateField} required placeholder="उदा. ५०"/></label>
            <label className="market-field"><span>एकक</span><select name="unit" value={form.unit} onChange={updateField}>{UNITS.map(([unit,label])=><option value={unit} key={unit}>{label}</option>)}</select></label>
          </div>
          <label className="market-field market-field-wide"><span>शेतमालाची माहिती <small>ऐच्छिक</small></span><textarea name="description" value={form.description} onChange={updateField} maxLength={1000} placeholder="काढणीची तारीख, उपलब्धता किंवा इतर तपशील"/></label>
        </div>
        <div className="market-photo-picker">
          <input ref={fileInputRef} type="file" accept="image/*" onChange={selectPhoto} hidden/>
          {previewUrl ? <div className="market-photo-preview"><img src={previewUrl} alt="शेतमालाचा निवडलेला फोटो"/><button type="button" onClick={() => setPhoto(null)} aria-label="फोटो काढा"><X size={16}/></button></div>
            : <button type="button" className="market-upload-button" onClick={() => fileInputRef.current?.click()}><Upload size={17}/> शेतमालाचा फोटो जोडा <small>१० MB पर्यंत · ऐच्छिक</small></button>}
        </div>
        <div className="market-auth-note"><ShieldCheck size={15}/> नोंदणी आणि विक्रेत्याचा संपर्क सुरक्षित ठेवण्यासाठी विद्यमान अॅप प्रवेश तपशील विचारले जाऊ शकतात. वेगळे खाते तयार करण्याची गरज नाही.</div>
        <button className="market-submit-button" type="submit" disabled={saving}>{saving?'नोंद जतन होत आहे…':<><PackagePlus size={17}/> विक्रीची नोंद जतन करा</>}</button>
      </form>
    </section>}

    {loginOpen&&<div className="market-modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setLoginOpen(false)}}>
      <form className="market-login-modal" onSubmit={signIn}>
        <button type="button" className="market-modal-close" onClick={() => setLoginOpen(false)} aria-label="बंद करा"><X size={18}/></button>
        <div className="market-login-icon"><ShieldCheck size={22}/></div>
        <span>स्वर्णभूमी सुरक्षित प्रवेश</span><h2>अॅपचे विद्यमान लॉगिन वापरा</h2>
        <p>विक्रेत्याची नोंद जतन करण्यासाठी किंवा संपर्क पाहण्यासाठी तुमचे शेअर्ड अॅप प्रवेश तपशील भरा. नवीन खाते लागत नाही.</p>
        <label className="market-field"><span>वापरकर्तानाव</span><input autoComplete="username" value={credentials.username} onChange={event => setCredentials(current => ({ ...current, username: event.target.value }))} required/></label>
        <label className="market-field"><span>पासवर्ड</span><input type="password" autoComplete="current-password" value={credentials.password} onChange={event => setCredentials(current => ({ ...current, password: event.target.value }))} required/></label>
        {loginError&&<div className="market-message market-error" role="alert">{loginError}</div>}
        <button className="market-submit-button" type="submit">प्रवेश करा</button>
      </form>
    </div>}

    {contact&&<div className="market-modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setContact(null)}}>
      <section className="market-contact-modal" role="dialog" aria-modal="true" aria-labelledby="market-contact-title">
        <button type="button" className="market-modal-close" onClick={() => setContact(null)} aria-label="बंद करा"><X size={18}/></button>
        <div className="market-login-icon"><Phone size={21}/></div><span>विक्रेता संपर्क</span>
        <h2 id="market-contact-title">{listings.find(listing=>listing.id===contact.id)?.sellerName||'विक्रेता'}</h2>
        <a className="market-phone-number" href={`tel:${contact.phone}`}>{contact.phone}</a>
        <div className="market-contact-actions"><a href={`tel:${contact.phone}`}><Phone size={16}/> फोन करा</a><a href={`https://wa.me/${phoneDigits}`} target="_blank" rel="noreferrer">WhatsApp</a></div>
        <p>संपर्क माहिती फक्त अॅप प्रवेशानंतर दाखवली जाते.</p>
      </section>
    </div>}
  </div>;
}

export default MarketplacePanel;
