import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDownRight, ArrowDownWideNarrow, ArrowRight, ArrowUpRight, Check, ClipboardList, FileDown, Heart, LocateFixed, MapPin, Minus, PackageCheck, PackagePlus, Plus, RefreshCw, Search, ShieldCheck, ShoppingBag, ShoppingCart, Star, Store, Trash2, Upload, UserRound, Wheat } from 'lucide-react';
import { fetchOfficialPrices, findLatestCommodityPrice, MARKET_CROP_OPTIONS } from './MarketPrices.jsx';
import SpeechTextInput from './SpeechTextInput.jsx';
import { MarketplaceConversation, OrderProgress, SellerReviewForm, SellerReviews, nextSellerStage, stageLabel } from './MarketplaceEngagement.jsx';
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
  pricePerUnit: '',
  discountPercent: '',
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
const PHOTO_LIMIT = 10 * 1024 * 1024;

function readCart(userId) {
  try {
    const stored = JSON.parse(window.localStorage.getItem(`swarnabhoomi-market-cart:${userId}`) || '[]');
    return Array.isArray(stored) ? stored.filter(item => Number.isInteger(Number(item.listingId))
      && Number(item.listingId) > 0 && Number(item.quantity) > 0)
      .map(item => ({ listingId: Number(item.listingId), quantity: Number(item.quantity) })) : [];
  } catch {
    return [];
  }
}

function wrappedLines(context, value, maxWidth) {
  const words = String(value || '—').split(/\s+/);
  const lines = [];
  let line = '';
  words.forEach(word => {
    const next = line ? `${line} ${word}` : word;
    if (line && context.measureText(next).width > maxWidth) {
      lines.push(line);
      line = word;
    } else line = next;
  });
  if (line) lines.push(line);
  return lines;
}

function receiptPdfBytes(canvas) {
  const encoder = new TextEncoder();
  const encodedJpeg = canvas.toDataURL('image/jpeg', 0.94).split(',')[1];
  const binaryJpeg = window.atob(encodedJpeg);
  const jpeg = new Uint8Array(binaryJpeg.length);
  for (let index = 0; index < binaryJpeg.length; index++) jpeg[index] = binaryJpeg.charCodeAt(index);
  const pageWidth = 595.28;
  const pageHeight = 841.89;
  const content = encoder.encode(`q\n${pageWidth} 0 0 ${pageHeight} 0 0 cm\n/Im0 Do\nQ\n`);
  const objects = [
    encoder.encode('1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n'),
    encoder.encode('2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n'),
    encoder.encode(`3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n`),
    null,
    null
  ];
  const imageHeader = encoder.encode(`4 0 obj\n<< /Type /XObject /Subtype /Image /Width ${canvas.width} /Height ${canvas.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.length} >>\nstream\n`);
  const imageFooter = encoder.encode('\nendstream\nendobj\n');
  objects[3] = new Uint8Array(imageHeader.length + jpeg.length + imageFooter.length);
  objects[3].set(imageHeader, 0);
  objects[3].set(jpeg, imageHeader.length);
  objects[3].set(imageFooter, imageHeader.length + jpeg.length);
  const contentHeader = encoder.encode(`5 0 obj\n<< /Length ${content.length} >>\nstream\n`);
  const contentFooter = encoder.encode('endstream\nendobj\n');
  objects[4] = new Uint8Array(contentHeader.length + content.length + contentFooter.length);
  objects[4].set(contentHeader, 0);
  objects[4].set(content, contentHeader.length);
  objects[4].set(contentFooter, contentHeader.length + content.length);

  const header = encoder.encode('%PDF-1.4\n');
  const offsets = [];
  let byteOffset = header.length;
  objects.forEach((object, index) => {
    offsets.push(byteOffset);
    byteOffset += object.length;
  });
  const xrefOffset = byteOffset;
  const xref = encoder.encode(`xref\n0 6\n0000000000 65535 f \n${offsets.map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`);
  const output = new Uint8Array(header.length + objects.reduce((sum, object) => sum + object.length, 0) + xref.length);
  let cursor = 0;
  [header, ...objects, xref].forEach(part => {
    output.set(part, cursor);
    cursor += part.length;
  });
  return output;
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

function ProductPhoto({ apiBase, photoUrl, authorization, productName }) {
  const [source, setSource] = useState('');
  useEffect(() => {
    let active = true;
    let objectUrl = '';
    if (!photoUrl) {
      setSource('');
      return undefined;
    }
    const headers = authorization ? { Authorization: `Bearer ${authorization}` } : {};
    const imagePath = photoUrl.startsWith('/api/') ? photoUrl : `${apiBase}${photoUrl}`;
    fetch(imagePath, { headers })
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

function MarketplacePanel({ accountType = 'farmer', apiBase, auth, userId, buyerPhone = '', onUnauthorized }) {
  const [view, setView] = useState('browse');
  const [listings, setListings] = useState([]);
  const [marketPriceFeed, setMarketPriceFeed] = useState(null);
  const [marketPriceLoading, setMarketPriceLoading] = useState(false);
  const [marketPriceError, setMarketPriceError] = useState('');
  const [form, setForm] = useState(EMPTY_FORM);
  const [customProductName, setCustomProductName] = useState(false);
  const [photo, setPhoto] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [authorization, setAuthorization] = useState(auth);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [sortBy, setSortBy] = useState('recommended');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState(() => {
    try {
      const stored = JSON.parse(window.localStorage.getItem(`swarnabhoomi-market-favorites:${userId}`) || '[]');
      return new Set(Array.isArray(stored) ? stored.map(String) : []);
    } catch {
      return new Set();
    }
  });
  const [locationMessage, setLocationMessage] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [adminListingNotice, setAdminListingNotice] = useState('');
  const [deletingListingId, setDeletingListingId] = useState(null);
  const [cart, setCart] = useState(() => readCart(userId));
  const [cartNotice, setCartNotice] = useState('');
  const [buyerName, setBuyerName] = useState('');
  const [buyerFulfillment, setBuyerFulfillment] = useState('PICKUP');
  const [buyerNote, setBuyerNote] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [paymentOptions, setPaymentOptions] = useState({
    cashEnabled: true,
    upiEnabled: false,
    upiMessage: 'UPI सुरू करण्यासाठी पेमेंट गेटवे आणि विक्रेता सेटलमेंट खाते सक्रिय करणे आवश्यक आहे.'
  });
  const [buyerOrders, setBuyerOrders] = useState([]);
  const [buyerOrdersLoading, setBuyerOrdersLoading] = useState(false);
  const [buyerOrdersError, setBuyerOrdersError] = useState('');
  const [buyerOrdersNotice, setBuyerOrdersNotice] = useState('');
  const [receiptBusyId, setReceiptBusyId] = useState(null);
  const [receiptError, setReceiptError] = useState('');
  const [sellerListingTokens, setSellerListingTokens] = useState(() => {
    try {
      const stored = JSON.parse(window.localStorage.getItem(`swarnabhoomi-market-seller-tokens:${userId}`) || '{}');
      return stored && typeof stored === 'object' && !Array.isArray(stored) ? stored : {};
    } catch {
      return {};
    }
  });
  const [sellerListings, setSellerListings] = useState([]);
  const [sellerLoading, setSellerLoading] = useState(false);
  const [sellerSavingId, setSellerSavingId] = useState(null);
  const [sellerStockAdds, setSellerStockAdds] = useState({});
  const [sellerError, setSellerError] = useState('');
  const [sellerNotice, setSellerNotice] = useState('');
  const fileInputRef = useRef(null);
  const formRef = useRef(form);
  const photoRef = useRef(photo);
  const onUnauthorizedRef = useRef(onUnauthorized);
  formRef.current = form;
  photoRef.current = photo;
  onUnauthorizedRef.current = onUnauthorized;

  useEffect(() => setAuthorization(auth), [auth]);
  useEffect(() => setView(accountType === 'seller' ? 'seller' : 'browse'), [accountType]);
  useEffect(() => {
    try {
      window.localStorage.setItem(`swarnabhoomi-market-cart:${userId}`, JSON.stringify(cart));
    } catch {
      // Cart operations remain available for the current page if browser storage is disabled.
    }
  }, [cart, userId]);
  useEffect(() => {
    try {
      window.localStorage.setItem(`swarnabhoomi-market-favorites:${userId}`, JSON.stringify([...favoriteIds]));
    } catch {
      // Favorites remain usable for this session if browser storage is disabled.
    }
  }, [favoriteIds, userId]);
  useEffect(() => {
    try {
      window.localStorage.setItem(`swarnabhoomi-market-seller-tokens:${userId}`, JSON.stringify(sellerListingTokens));
    } catch {
      // Seller listings remain manageable during this session if browser storage is disabled.
    }
  }, [sellerListingTokens, userId]);
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
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${apiBase}${path}`, { ...options, headers });
    return response;
  }, [apiBase, authorization]);

  const loadPaymentOptions = useCallback(async () => {
    try {
      const response = await request('/marketplace/payment-options');
      if (response.ok) setPaymentOptions(await response.json());
    } catch {
      // Keep cash available when payment configuration cannot be fetched.
    }
  }, [request]);

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

  const loadSellerListings = useCallback(async () => {
    const entries = Object.entries(sellerListingTokens);
    setSellerLoading(true);
    setSellerError('');
    setSellerNotice('');
    try {
      const rows = await Promise.all(entries.map(async ([id, sellerToken]) => {
        const headers = { 'X-Seller-Token': sellerToken };
        const [listingResponse, requestsResponse] = await Promise.all([
          request(`/marketplace/seller/listings/${id}`, { headers }),
          request(`/marketplace/seller/listings/${id}/purchase-requests`, { headers })
        ]);
        if (listingResponse.status === 404 || listingResponse.status === 403) return null;
        if (!listingResponse.ok) throw new Error('तुमची विक्रीची माहिती मिळाली नाही.');
        if (!requestsResponse.ok) throw new Error('खरेदीच्या विनंत्या मिळाल्या नाहीत.');
        return { listing: await listingResponse.json(), requests: await requestsResponse.json(), sellerToken };
      }));
      const validRows = rows.filter(Boolean);
      setSellerListings(validRows);
      if (!validRows.length && entries.length) {
        setSellerError('या ब्राउझरमध्ये जतन केलेली विक्रेता प्रवेश माहिती कालबाह्य किंवा अमान्य आहे.');
      }
    } catch (loadError) {
      setSellerError(loadError.message || 'विक्रेता माहिती मिळाली नाही.');
    } finally {
      setSellerLoading(false);
    }
  }, [request, sellerListingTokens]);

  const loadBuyerOrders = useCallback(async () => {
    if (accountType !== 'buyer') return;
    setBuyerOrdersLoading(true);
    setBuyerOrdersError('');
    try {
      const response = await request('/marketplace/buyer/orders');
      if (response.status === 401) {
        onUnauthorizedRef.current?.();
        return;
      }
      if (!response.ok) throw new Error('तुमच्या ऑर्डर मिळाल्या नाहीत.');
      setBuyerOrders(await response.json());
    } catch (loadError) {
      setBuyerOrdersError(loadError.message || 'तुमच्या ऑर्डर मिळाल्या नाहीत.');
    } finally {
      setBuyerOrdersLoading(false);
    }
  }, [accountType, request]);

  useEffect(() => {
    loadListings();
  }, [loadListings]);

  useEffect(() => {
    if (accountType !== 'farmer') return undefined;
    let active = true;
    setMarketPriceLoading(true);
    fetchOfficialPrices().then(feed => {
      if (active) {
        setMarketPriceFeed(feed);
        setMarketPriceError('');
      }
    }).catch(priceError => {
      if (active) setMarketPriceError(priceError.message || 'बाजारभाव सध्या उपलब्ध नाहीत.');
    }).finally(() => {
      if (active) setMarketPriceLoading(false);
    });
    return () => { active = false; };
  }, [accountType]);

  useEffect(() => {
    loadPaymentOptions();
  }, [loadPaymentOptions]);

  useEffect(() => {
    if (view === 'seller') loadSellerListings();
  }, [view, loadSellerListings]);

  useEffect(() => {
    if (view === 'orders' && accountType === 'buyer') loadBuyerOrders();
  }, [view, accountType, loadBuyerOrders]);

  const availableCategories = useMemo(() => Array.from(new Set(
    listings.map(listing => listing.category).filter(Boolean)
  )).sort((a, b) => a.localeCompare(b, 'mr-IN')), [listings]);

  const favoriteCount = useMemo(() => listings.filter(listing => favoriteIds.has(String(listing.id))).length,
    [listings, favoriteIds]);
  const availableListingCount = useMemo(() => listings.filter(listing => listing.available !== false && Number(listing.quantity) > 0).length,
    [listings]);
  const sellerCount = useMemo(() => new Set(listings.map(listing => listing.sellerName).filter(Boolean)).size,
    [listings]);
  const pendingSellerRequests = useMemo(() => sellerListings.reduce((sum, row) =>
    sum + row.requests.filter(purchaseRequest => purchaseRequest.status === 'PENDING').length, 0), [sellerListings]);
  const activeSellerListings = useMemo(() => sellerListings.filter(row => row.listing.available && Number(row.listing.quantity) > 0).length,
    [sellerListings]);
  const farmerMarketReferences = useMemo(() => new Map(listings.map(listing => [
    listing.id,
    findLatestCommodityPrice(listing, marketPriceFeed?.prices || [])
  ])), [listings, marketPriceFeed]);

  const cartRows = useMemo(() => cart.map(item => ({
    ...item,
    listing: listings.find(listing => listing.id === item.listingId)
  })).filter(item => item.listing), [cart, listings]);
  const cartTotal = useMemo(() => cartRows.reduce((sum, item) =>
    sum + effectivePrice(item.listing) * Number(item.quantity), 0), [cartRows]);

  const filteredListings = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('mr-IN');
    return listings
      .filter(listing => !query || [listing.productName, listing.cropType, listing.category]
        .join(' ').toLocaleLowerCase('mr-IN').includes(query))
      .filter(listing => categoryFilter === 'all' || listing.category === categoryFilter)
      .filter(listing => !favoritesOnly || favoriteIds.has(String(listing.id)))
      .sort((a, b) => {
        if (sortBy === 'name') return a.productName.localeCompare(b.productName, 'mr-IN');
        return b.id - a.id;
      });
  }, [listings, search, categoryFilter, favoritesOnly, favoriteIds, sortBy]);

  function toggleFavorite(id, event) {
    event.stopPropagation();
    setFavoriteIds(current => {
      const next = new Set(current);
      const key = String(id);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value, ...(name === 'productName' ? { cropType: value } : {}) }));
  }

  function updateChoice(event, setCustom) {
    const { name, value } = event.target;
    const isCustom = value === '__other__';
    setCustom(isCustom);
    setForm(current => ({
      ...current,
      [name]: isCustom ? '' : value,
      ...(name === 'productName' ? { cropType: isCustom ? '' : value } : {})
    }));
  }

  function askForLocation() {
    setLocationMessage('');
    locateUser(location => {
      setForm(current => ({
        ...current,
        latitude: location.latitude.toFixed(6),
        longitude: location.longitude.toFixed(6)
      }));
    }, setLocationMessage);
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
        const photoResponse = await request('/marketplace/photos', { method: 'POST', body: photoData }, token);
        if (!photoResponse.ok) throw new Error('फोटो अपलोड झाला नाही. १० MB पेक्षा कमी आकाराचा फोटो निवडा.');
        photoUrl = (await photoResponse.json()).url;
      }
      const payload = {
        ...current,
        latitude: Number(current.latitude),
        longitude: Number(current.longitude),
        quantity: Number(current.quantity),
        pricePerUnit: Number(current.pricePerUnit),
        discountPercent: current.discountPercent === '' ? null : Number(current.discountPercent),
        photoUrl: photoUrl || null
      };
      const response = await request('/marketplace/listings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }, token);
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || 'विक्रीची नोंद जतन करता आली नाही. भरलेली माहिती तपासा.');
      }
      const created = await response.json();
      const savedListing = created.listing;
      setSellerListingTokens(currentTokens => ({
        ...currentTokens,
        [String(savedListing.id)]: created.sellerManagementToken
      }));
      setForm(EMPTY_FORM);
      formRef.current = EMPTY_FORM;
      setCustomProductName(false);
      setPhoto(null);
      photoRef.current = null;
      setView('seller');
      await loadListings(token);
      setSellerNotice('विक्रीची नोंद जतन झाली. या ब्राउझरमधील “माझे व्यवहार” विभागातून साठा आणि खरेदीच्या विनंत्या व्यवस्थापित करा.');
    } catch (saveError) {
      setError(saveError.message || 'विक्रीची नोंद जतन करता आली नाही.');
    } finally {
      setSaving(false);
    }
  }

  async function submitListing(event) {
    event.preventDefault();
    await saveListing();
  }

  function addToCart(listing, event) {
    event.stopPropagation();
    if (!listing.available || Number(listing.quantity) <= 0) return;
    const existing = cart.find(item => item.listingId === listing.id);
    const currentQuantity = existing ? Number(existing.quantity) : 0;
    const nextQuantity = Math.min(Number(listing.quantity), currentQuantity + 1);
    if (nextQuantity <= currentQuantity) {
      setCartNotice('या शेतमालाचा उपलब्ध साठा कार्टमध्ये आधीच जोडला आहे.');
      return;
    }
    setCart(existing
      ? cart.map(item => item.listingId === listing.id ? { ...item, quantity: nextQuantity } : item)
      : [...cart, { listingId: listing.id, quantity: nextQuantity }]);
    setCartNotice(`${listing.productName} कार्टमध्ये जोडला.`);
  }

  function updateCartQuantity(listing, value) {
    if (value === '') return;
    const quantity = Number(value);
    if (!Number.isFinite(quantity) || quantity <= 0) return;
    setCart(current => current.map(item => item.listingId === listing.id
      ? { ...item, quantity: Math.min(quantity, Number(listing.quantity)) }
      : item));
  }

  function removeCartItem(listingId) {
    setCart(current => current.filter(item => item.listingId !== listingId));
  }

  async function submitCheckout(event) {
    event.preventDefault();
    if (!cartRows.length) return;
    setBuyerOrdersError('');
    setBuyerOrdersNotice('');
    setBuyerOrdersLoading(true);
    try {
      const response = await request('/marketplace/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          buyerName,
          fulfillment: buyerFulfillment,
          note: buyerNote,
          paymentMethod,
          items: cartRows.map(item => ({ listingId: item.listingId, quantity: Number(item.quantity) }))
        })
      });
      const body = await response.json().catch(() => ({}));
      if (response.status === 401) {
        onUnauthorizedRef.current?.();
        return;
      }
      if (!response.ok) throw new Error(body.message || 'ऑर्डर पाठवता आला नाही. उपलब्ध साठा तपासा.');
      setCart([]);
      setBuyerOrders(body.orders || []);
      setBuyerOrdersNotice(body.message || 'ऑर्डर विक्रेत्यांकडे पाठवला आहे.');
      setBuyerName('');
      setBuyerNote('');
      setView('orders');
      await loadListings();
    } catch (checkoutError) {
      setBuyerOrdersError(checkoutError.message || 'ऑर्डर पाठवता आला नाही.');
      await loadListings();
    } finally {
      setBuyerOrdersLoading(false);
    }
  }

  function updateSellerListing(id, field, value) {
    setSellerListings(current => current.map(row => row.listing.id === id
      ? { ...row, listing: { ...row.listing, [field]: value } }
      : row));
  }

  async function saveSellerListing(row) {
    const { listing, sellerToken } = row;
    setSellerSavingId(listing.id);
    setSellerError('');
    setSellerNotice('');
    try {
      const response = await request(`/marketplace/seller/listings/${listing.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Seller-Token': sellerToken },
        body: JSON.stringify({
          pricePerUnit: Number(listing.pricePerUnit),
          discountPercent: listing.discountPercent === '' || listing.discountPercent == null
            ? null : Number(listing.discountPercent),
          quantity: Number(listing.quantity),
          available: Boolean(listing.available)
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || 'विक्रीची माहिती जतन करता आली नाही.');
      setSellerListings(current => current.map(item => item.listing.id === listing.id ? { ...item, listing: body } : item));
      await loadListings();
      setSellerNotice('किंमत, साठा आणि उपलब्धतेची माहिती जतन झाली.');
      return true;
    } catch (saveError) {
      setSellerError(saveError.message || 'विक्रीची माहिती जतन करता आली नाही.');
      return false;
    } finally {
      setSellerSavingId(null);
    }
  }

  async function addSellerStock(row) {
    const addition = Number(sellerStockAdds[row.listing.id]);
    if (!Number.isFinite(addition) || addition <= 0) {
      setSellerError('साठ्यात जोडण्यासाठी योग्य प्रमाण भरा.');
      return;
    }
    const updatedRow = {
      ...row,
      listing: { ...row.listing, quantity: Number(row.listing.quantity) + addition, available: true }
    };
    if (await saveSellerListing(updatedRow)) {
      setSellerStockAdds(current => ({ ...current, [row.listing.id]: '' }));
      setSellerNotice(`${addition.toLocaleString('mr-IN')} ${UNITS.find(([value]) => value === row.listing.unit)?.[1] || row.listing.unit} साठ्यात जोडले.`);
    }
  }

  async function updatePurchaseStatus(row, purchaseRequest, status) {
    setSellerError('');
    setSellerNotice('');
    try {
      const response = await request(`/marketplace/seller/listings/${row.listing.id}/purchase-requests/${purchaseRequest.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Seller-Token': row.sellerToken },
        body: JSON.stringify({ status })
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || 'विनंतीची स्थिती बदलता आली नाही.');
      }
      const updatedRequest = await response.json();
      setSellerListings(current => current.map(item => item.listing.id === row.listing.id
        ? { ...item, requests: item.requests.map(requestItem => requestItem.id === updatedRequest.id ? updatedRequest : requestItem) }
        : item));
      await loadListings();
      await loadSellerListings();
    } catch (statusError) {
      setSellerError(statusError.message || 'विनंतीची स्थिती बदलता आली नाही.');
    }
  }

  async function updateFulfillmentStage(row, purchaseRequest, fulfillmentStage) {
    setSellerError(''); setSellerNotice('');
    try {
      const response = await request(`/marketplace/seller/listings/${row.listing.id}/purchase-requests/${purchaseRequest.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Seller-Token': row.sellerToken },
        body: JSON.stringify({ fulfillmentStage })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || 'ऑर्डरची प्रगती जतन करता आली नाही.');
      setSellerListings(current => current.map(item => item.listing.id === row.listing.id
        ? { ...item, requests: item.requests.map(requestItem => requestItem.id === body.id ? body : requestItem) }
        : item));
      setSellerNotice(`ऑर्डरची स्थिती: ${stageLabel(fulfillmentStage)}.`);
      await loadSellerListings();
    } catch (exception) { setSellerError(exception.message || 'ऑर्डरची प्रगती जतन करता आली नाही.'); }
  }

  async function updatePaymentStatus(row, purchaseRequest, paymentStatus) {
    setSellerError('');
    setSellerNotice('');
    try {
      const response = await request(`/marketplace/seller/listings/${row.listing.id}/purchase-requests/${purchaseRequest.id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-Seller-Token': row.sellerToken },
        body: JSON.stringify({ paymentStatus })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || 'पेमेंटची स्थिती जतन करता आली नाही.');
      setSellerListings(current => current.map(item => item.listing.id === row.listing.id
        ? { ...item, requests: item.requests.map(requestItem => requestItem.id === body.id ? body : requestItem) }
        : item));
      await loadSellerListings();
      setSellerNotice('रोख पेमेंटची नोंद खरेदीदार आणि विक्रेत्यासाठी जतन झाली.');
    } catch (paymentError) {
      setSellerError(paymentError.message || 'पेमेंटची स्थिती जतन करता आली नाही.');
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

  function formatPrice(price, unit) {
    const unitLabel = UNITS.find(([value]) => value === unit)?.[1] || unit;
    return `₹${Number(price).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} / ${unitLabel}`;
  }

  async function deleteMarketplaceListing(listing, event) {
    event.stopPropagation();
    if (accountType !== 'admin' || !window.confirm(`“${listing.productName}” ही विक्रीची नोंद कायमची हटवायची आहे का?`)) return;

    setDeletingListingId(listing.id);
    setError('');
    setAdminListingNotice('');
    try {
      const response = await request(`/admin/listings/${listing.id}`, { method: 'DELETE' });
      if (response.status === 401) {
        onUnauthorizedRef.current?.();
        return;
      }
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || 'विक्रीची नोंद हटवता आली नाही.');
      }

      setListings(current => current.filter(item => item.id !== listing.id));
      setFavoriteIds(current => {
        const next = new Set(current);
        next.delete(String(listing.id));
        return next;
      });
      setCart(current => current.filter(item => item.listingId !== listing.id));
      setAdminListingNotice(`${listing.productName} ची विक्रीची नोंद हटवली.`);
    } catch (deleteError) {
      setError(deleteError.message || 'विक्रीची नोंद हटवता आली नाही.');
    } finally {
      setDeletingListingId(null);
    }
  }

  function effectivePrice(listing) {
    return Number(listing.discountedPricePerUnit ?? listing.pricePerUnit ?? 0);
  }

  function hasOffer(listing) {
    return Number(listing.discountPercent || 0) > 0
      && effectivePrice(listing) < Number(listing.pricePerUnit || 0);
  }

  function priceDisplay(listing) {
    if (listing.pricePerUnit == null) return <p className="market-price">किंमत उपलब्ध नाही</p>;
    if (!hasOffer(listing)) return <p className="market-price">{formatPrice(listing.pricePerUnit, listing.unit)}</p>;
    return <div className="market-offer-price">
      <strong>{formatPrice(effectivePrice(listing), listing.unit)}</strong>
      <del>{formatPrice(listing.pricePerUnit, listing.unit)}</del>
      <span>{Number(listing.discountPercent).toLocaleString('mr-IN', { maximumFractionDigits: 2 })}% सूट</span>
    </div>;
  }

  function pricePerKg(listing) {
    if (listing.pricePerUnit == null) return null;
    const price = effectivePrice(listing);
    if (listing.unit === 'kg') return price;
    if (listing.unit === 'quintal') return price / 100;
    if (listing.unit === 'ton') return price / 1000;
    return null;
  }

  function farmerPriceComparison(listing) {
    const mandiPrice = farmerMarketReferences.get(listing.id);
    const sellerPricePerKg = pricePerKg(listing);
    const mandiPricePerKg = mandiPrice ? Number(mandiPrice.modalPrice) / 100 : null;
    const percentage = sellerPricePerKg != null && mandiPricePerKg > 0
      ? ((sellerPricePerKg - mandiPricePerKg) / mandiPricePerKg) * 100 : null;
    const comparison = percentage == null ? '' : percentage > 0.5 ? 'higher' : percentage < -0.5 ? 'lower' : 'similar';

    return <div className="market-farmer-price-comparison">
      <div className="market-farmer-price-pair">
        <div className="market-farmer-price-value">
          <span>{sellerPricePerKg == null ? 'शेतकरी विक्री दर' : 'शेतकरी विक्री दर · १ किलो'}</span>
          {listing.pricePerUnit == null
            ? <strong>किंमत उपलब्ध नाही</strong>
            : sellerPricePerKg == null
              ? <strong>{formatPrice(effectivePrice(listing), listing.unit)}</strong>
              : <strong>₹{sellerPricePerKg.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>}
          {listing.pricePerUnit != null && (sellerPricePerKg == null
            ? <small>या एककासाठी किलोतील दर मोजता येत नाही</small>
            : <small>{formatPrice(effectivePrice(listing), listing.unit)} प्रमाणे</small>)}
        </div>
        <span className="market-farmer-price-vs">तुलना</span>
        <div className="market-farmer-price-value mandi">
          <span>बाजारभाव · १ किलो</span>
          {mandiPricePerKg != null
            ? <strong>₹{mandiPricePerKg.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
            : <strong className="market-farmer-price-pending">{marketPriceLoading ? '…' : '—'}</strong>}
          {mandiPrice
            ? <small>{mandiPrice.market}{mandiPrice.district ? ` · ${mandiPrice.district}` : ''}{mandiPrice.arrivalDate ? ` · ${mandiPrice.arrivalDate}` : ''}</small>
            : <small>{marketPriceLoading ? 'मंडई भाव आणत आहोत' : marketPriceError ? 'बाजारभाव उपलब्ध नाहीत' : 'या पिकाचा मंडई भाव सापडला नाही'}</small>}
        </div>
      </div>
      {comparison && <div className={`market-price-comparison-indicator ${comparison}`} role="status">
        {comparison === 'higher' ? <ArrowUpRight size={15}/> : comparison === 'lower' ? <ArrowDownRight size={15}/> : <Minus size={15}/>}
        {comparison === 'similar' ? 'मंडई भावाच्या जवळपास' : `मंडई भावापेक्षा ${Math.abs(percentage).toLocaleString('mr-IN', { maximumFractionDigits: 1 })}% ${comparison === 'higher' ? 'जास्त' : 'कमी'}`}
      </div>}
      {mandiPrice && <small className="market-price-comparison-note">AGMARKNET मोडल दर / क्विंटल ÷ १०० · {mandiPrice.arrivalDate || 'तारीख उपलब्ध नाही'}</small>}
    </div>;
  }

  function requestStatusLabel(status) {
    return ({ PENDING: 'प्रलंबित', ACCEPTED: 'स्वीकारली', DECLINED: 'नाकारली', COMPLETED: 'पूर्ण' })[status] || status;
  }

  function paymentMethodLabel(method) {
    return ({ CASH: 'रोख', UPI: 'UPI' })[method] || 'नोंद नाही';
  }

  function paymentStatusLabel(status) {
    return ({ PENDING: 'पेमेंट बाकी', PAID: 'पेमेंट मिळाले', FAILED: 'पेमेंट अयशस्वी' })[status] || 'नोंद नाही';
  }

  async function downloadOrderReceipt(order) {
    setReceiptBusyId(order.id);
    setReceiptError('');
    try {
      await document.fonts?.ready;
      const canvas = document.createElement('canvas');
      canvas.width = 1240;
      canvas.height = 1754;
      const context = canvas.getContext('2d');
      if (!context) throw new Error('PDF तयार करता आला नाही.');
      const margin = 96;
      const contentWidth = canvas.width - margin * 2;
      const fontFamily = '"Noto Sans Devanagari", "Noto Sans", sans-serif';
      const money = value => `₹${Number(value || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
      const unitLabel = UNITS.find(([value]) => value === order.unit)?.[1] || order.unit;
      const status = requestStatusLabel(order.status);
      const paymentMethod = paymentMethodLabel(order.paymentMethod);
      const paymentStatus = paymentStatusLabel(order.paymentStatus);
      const createdAt = new Date(order.createdAt).toLocaleString('mr-IN', { dateStyle: 'medium', timeStyle: 'short' });
      const amount = order.totalAmount ?? (Number(order.pricePerUnit || 0) * Number(order.quantity || 0));
      const fulfillment = order.fulfillment === 'DELIVERY' ? 'पोहोचवून हवे' : 'स्वतः घेऊन जाणार';
      const wrap = (value, x, y, maxWidth, lineHeight, color, size = 29, weight = 400, maxLines = 3) => {
        context.fillStyle = color;
        context.font = `${weight} ${size}px ${fontFamily}`;
        const lines = wrappedLines(context, value, maxWidth).slice(0, maxLines);
        const allLines = wrappedLines(context, value, maxWidth);
        if (allLines.length > maxLines) lines[maxLines - 1] = `${lines[maxLines - 1]}…`;
        lines.forEach((line, index) => {
          if (context.measureText(line).width > maxWidth) {
            let fitted = `${line}…`;
            while (context.measureText(fitted).width > maxWidth && fitted.length > 2) {
              fitted = `${fitted.slice(0, -2)}…`;
            }
            lines[index] = fitted;
          }
        });
        lines.forEach((line, index) => context.fillText(line, x, y + index * lineHeight));
        return lines.length;
      };
      const rule = y => { context.strokeStyle = '#e2eadf'; context.lineWidth = 2; context.beginPath(); context.moveTo(margin, y); context.lineTo(canvas.width - margin, y); context.stroke(); };
      context.fillStyle = '#f0f7ec';
      context.fillRect(0, 0, canvas.width, 420);
      context.fillStyle = '#4c8054';
      context.beginPath();
      context.arc(margin + 34, 106, 33, 0, Math.PI * 2);
      context.fill();
      context.fillStyle = '#fff';
      context.font = `700 35px ${fontFamily}`;
      context.textAlign = 'center';
      context.fillText('श्री', margin + 34, 117);
      context.textAlign = 'left';
      context.fillStyle = '#294732';
      context.font = `700 46px ${fontFamily}`;
      context.fillText('स्वर्णभूमी', margin + 86, 92);
      context.fillStyle = '#6f8371';
      context.font = `400 25px ${fontFamily}`;
      context.fillText('शेतकरी बाजार · खरेदी ऑर्डर पावती', margin + 86, 135);
      context.fillStyle = '#315f38';
      context.font = `700 42px ${fontFamily}`;
      context.fillText('खरेदीची पावती', margin, 235);
      context.fillStyle = '#78877a';
      context.font = `400 23px ${fontFamily}`;
      context.fillText('ORDER RECEIPT · PAYMENT RECORD', margin, 282);
      context.fillStyle = '#fff';
      context.fillRect(margin, 330, contentWidth, 190);
      context.strokeStyle = '#dce8d8';
      context.strokeRect(margin, 330, contentWidth, 190);
      wrap('ऑर्डर क्रमांक', margin + 28, 376, 380, 34, '#849185', 22, 400);
      wrap(`#${Number(order.id).toLocaleString('mr-IN')}`, margin + 28, 423, 430, 44, '#304736', 32, 700);
      wrap('ऑर्डरची वेळ', margin + 540, 376, 470, 34, '#849185', 22, 400);
      wrap(createdAt, margin + 540, 423, 490, 44, '#304736', 28, 700);
      wrap(`स्थिती: ${status}`, margin + 28, 482, contentWidth - 56, 38, '#47764b', 25, 700);
      wrap(`पेमेंट: ${paymentMethod} · ${paymentStatus}`, margin + 28, 525, contentWidth - 56, 36, '#47764b', 24, 700);

      let y = 620;
      wrap('खरेदी केलेला शेतमाल', margin, y, contentWidth, 45, '#879486', 23, 700);
      y += 58;
      const productLineCount = wrap(order.productName || `शेतमाल #${order.listingId}`, margin, y, contentWidth, 55, '#304736', 39, 700, 2);
      y += productLineCount * 55 + 22;
      rule(y);
      y += 50;
      wrap('प्रमाण', margin, y, 480, 35, '#879486', 22, 400);
      wrap(`${Number(order.quantity).toLocaleString('mr-IN')} ${unitLabel}`, margin, y + 44, 480, 40, '#304736', 29, 700);
      wrap('प्रति एकक किंमत', margin + 540, y, 480, 35, '#879486', 22, 400);
      wrap(order.pricePerUnit == null ? '—' : money(order.pricePerUnit), margin + 540, y + 44, 480, 40, '#304736', 29, 700);
      y += 126;
      rule(y);
      y += 58;
      wrap('ऑर्डरची एकूण रक्कम', margin, y, 600, 42, '#6f8371', 25, 400);
      wrap(money(amount), margin, y + 65, contentWidth, 64, '#315f38', 49, 700);
      y += 160;
      rule(y);
      y += 52;
      wrap('विक्रेता', margin, y, 480, 34, '#879486', 22, 400);
      wrap(order.sellerName || 'शेतकरी विक्रेता', margin, y + 43, 490, 42, '#304736', 29, 700, 2);
      wrap('खरेदीदार', margin + 540, y, 480, 34, '#879486', 22, 400);
      wrap(order.buyerName || 'खरेदीदार', margin + 540, y + 43, 490, 42, '#304736', 29, 700, 2);
      y += 43 + 2 * 42 + 24;
      wrap('वितरण पद्धत', margin, y, contentWidth, 34, '#879486', 22, 400);
      wrap(fulfillment, margin, y + 44, contentWidth, 40, '#304736', 28, 700);
      y += 115;
      context.fillStyle = '#f7f9f5';
      context.fillRect(margin, y, contentWidth, 143);
      wrap('टीप', margin + 24, y + 36, contentWidth - 48, 30, '#708072', 22, 700);
      wrap(order.paymentStatus === 'PAID' ? 'पेमेंट मिळाल्याची नोंद विक्रेत्याने केली आहे. ही ऑर्डर पावती आहे.' : 'पेमेंट स्थिती विक्रेत्याशी तपासा. ही ऑर्डर पावती पेमेंट गेटवेची पावती नाही.', margin + 24, y + 77, contentWidth - 48, 34, '#6f7d70', 22, 400);
      context.fillStyle = '#8c988d';
      context.font = `400 20px ${fontFamily}`;
      context.textAlign = 'center';
      context.fillText('स्वर्णभूमी · शेतकऱ्यांकडून थेट शेतमाल', canvas.width / 2, 1695);
      context.textAlign = 'left';

      const pdf = receiptPdfBytes(canvas);
      const url = URL.createObjectURL(new Blob([pdf], { type: 'application/pdf' }));
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `Swarnabhoomi-Order-${order.id}.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1500);
    } catch (downloadError) {
      setReceiptError(downloadError.message || 'पावती PDF तयार करता आली नाही.');
    } finally {
      setReceiptBusyId(null);
    }
  }

  return <div className="market-page" lang="mr">
    <section className={`market-hero market-hero-${accountType}`}>
      <div className="market-hero-copy">
        <span className="market-eyebrow"><Wheat size={15}/> शेतमाल थेट शेतकऱ्याकडून</span>
        <h1>{accountType === 'buyer' ? 'ताज्या शेतमालाची खरेदी' : accountType === 'seller' || accountType === 'farmer' ? 'तुमचा शेतमाल, थेट बाजारात' : 'शेतकरी बाजार'}</h1>
        <p>{accountType === 'buyer' ? 'उपलब्ध ताजा शेतमाल शोधा, भाव पहा आणि थेट ऑर्डर द्या.' : accountType === 'seller' || accountType === 'farmer' ? 'तुमचा माल नोंदवा, साठा सांभाळा आणि खरेदीदारांच्या विनंत्या एका ठिकाणी हाताळा.' : 'उपलब्ध शेतमाल शोधा किंवा विक्रीची नोंद आणि व्यवहार व्यवस्थापित करा.'}</p>
        <div className="market-hero-actions">
          {accountType === 'buyer' ? <>
            <button type="button" className="market-hero-cta primary" onClick={() => setView('browse')}><Search size={16}/> शेतमाल शोधा <ArrowRight size={15}/></button>
            <button type="button" className="market-hero-cta" onClick={() => setView('cart')}><ShoppingCart size={16}/> कार्ट पहा</button>
          </> : accountType === 'seller' || accountType === 'farmer' ? <>
            <button type="button" className="market-hero-cta primary" onClick={() => { setError(''); setView('register'); }}><PackagePlus size={16}/> विक्री नोंदवा <ArrowRight size={15}/></button>
            <button type="button" className="market-hero-cta" onClick={() => { setError(''); setView('seller'); }}><ClipboardList size={16}/> माझे व्यवहार</button>
          </> : null}
        </div>
      </div>
      <div className="market-hero-stats" aria-label="बाजाराची माहिती">
        {accountType === 'seller' || accountType === 'farmer' ? <>
          <div className="market-hero-stat"><span><PackageCheck size={17}/> माझा शेतमाल</span><strong>{sellerListings.length.toLocaleString('mr-IN')}</strong><small>एकूण नोंदी</small></div>
          <div className="market-hero-stat"><span><Store size={17}/> विक्री सुरू</span><strong>{activeSellerListings.toLocaleString('mr-IN')}</strong><small>उपलब्ध नोंदी</small></div>
          <div className="market-hero-stat"><span><ClipboardList size={17}/> नवीन विनंत्या</span><strong>{pendingSellerRequests.toLocaleString('mr-IN')}</strong><small>उत्तराची प्रतीक्षा</small></div>
        </> : <>
          <div className="market-hero-stat"><span><Wheat size={17}/> उपलब्ध शेतमाल</span><strong>{availableListingCount.toLocaleString('mr-IN')}</strong><small>ताज्या विक्री नोंदी</small></div>
          <div className="market-hero-stat"><span><UserRound size={17}/> स्थानिक विक्रेते</span><strong>{sellerCount.toLocaleString('mr-IN')}</strong><small>थेट शेतकऱ्यांकडून</small></div>
          <div className="market-hero-stat"><span><ShoppingBag size={17}/> तुमच्या कार्टमध्ये</span><strong>{cart.length.toLocaleString('mr-IN')}</strong><small>निवडलेले प्रकार</small></div>
        </>}
      </div>
    </section>

    <div className="market-actions" role="tablist" aria-label="शेतकरी बाजार विभाग">
      <button type="button" role="tab" aria-selected={view === 'browse'} className={view === 'browse' ? 'active' : ''} onClick={() => setView('browse')}><Search size={16}/> शेतमाल शोधा</button>
      {accountType === 'buyer' && <>
        <button type="button" role="tab" aria-selected={view === 'cart'} className={view === 'cart' ? 'active' : ''} onClick={() => { setCartNotice(''); setView('cart'); }}><ShoppingCart size={16}/> कार्ट <span className="market-tab-count">{cart.length.toLocaleString('mr-IN')}</span></button>
        <button type="button" role="tab" aria-selected={view === 'orders'} className={view === 'orders' ? 'active' : ''} onClick={() => { setBuyerOrdersNotice(''); setView('orders'); }}><ClipboardList size={16}/> माझ्या ऑर्डर</button>
      </>}
      {accountType !== 'buyer' && accountType !== 'admin' && <>
        <button type="button" role="tab" aria-selected={view === 'register'} className={view === 'register' ? 'active' : ''} onClick={() => { setError(''); setView('register'); }}><PackagePlus size={16}/> शेतमाल विक्रीसाठी नोंदवा</button>
        <button type="button" role="tab" aria-selected={view === 'seller'} className={view === 'seller' ? 'active' : ''} onClick={() => { setError(''); setView('seller'); }}><ClipboardList size={16}/> माझे व्यवहार</button>
      </>}
    </div>

    {error && <div className="market-message market-error" role="alert">{error}</div>}
    {cartNotice && view === 'browse' && <div className="market-message market-success" role="status"><Check size={15}/>{cartNotice}</div>}
    {adminListingNotice && view === 'browse' && <div className="market-message market-success" role="status"><Check size={15}/>{adminListingNotice}</div>}

    {view === 'browse' ? <>
      <section className="market-category-shortcuts" aria-label="शेतमालाच्या प्रकारानुसार शोधा">
        <span className="market-category-label">जलद शोध</span>
        <button type="button" className={categoryFilter === 'all' ? 'selected' : ''} aria-pressed={categoryFilter === 'all'} onClick={() => setCategoryFilter('all')}>सर्व शेतमाल</button>
        {availableCategories.map(category => <button type="button" key={category} className={categoryFilter === category ? 'selected' : ''} aria-pressed={categoryFilter === category} onClick={() => setCategoryFilter(category)}>{category}</button>)}
      </section>
      <section className="market-search-panel">
        <label className="market-search-field"><Search size={17}/><SpeechTextInput><input value={search} onChange={event => setSearch(event.target.value)} placeholder="पीक किंवा शेतमाल शोधा"/></SpeechTextInput></label>
      </section>
      <section className="market-filter-row" aria-label="शेतमाल शोध फिल्टर">
        <label className="market-filter-field"><span>प्रकार</span>
          <select value={categoryFilter} onChange={event => setCategoryFilter(event.target.value)}>
            <option value="all">सर्व प्रकार</option>
            {availableCategories.map(category => <option key={category} value={category}>{category}</option>)}
          </select>
        </label>
        <label className="market-filter-field"><span>क्रम</span>
          <select value={sortBy} onChange={event => setSortBy(event.target.value)}>
            <option value="recommended">नवीन नोंदी आधी</option>
            <option value="name">नावानुसार</option>
          </select>
        </label>
        {accountType !== 'farmer' && <button type="button" className={`market-favorites-toggle ${favoritesOnly ? 'selected' : ''}`} aria-pressed={favoritesOnly} onClick={() => setFavoritesOnly(value => !value)}>
          <Heart size={14} fill={favoritesOnly ? 'currentColor' : 'none'}/>{favoritesOnly ? 'सर्व दाखवा' : 'आवडती'} <span>{favoriteCount.toLocaleString('mr-IN')}</span>
        </button>}
      </section>
      {accountType === 'farmer' && marketPriceError && <div className="market-message market-price-comparison-error" role="status">बाजारभाव तुलना सध्या उपलब्ध नाही. {marketPriceError}</div>}
      <section className={`market-listings ${accountType === 'farmer' ? 'market-farmer-listings' : ''}`}>
          <div className="market-card-heading"><div><span>उपलब्ध शेतमाल</span><h2>{accountType === 'farmer' ? 'शेतमाल आणि विक्री दर' : 'शेतमालाची यादी'}</h2></div><span className="market-result-count">{filteredListings.length.toLocaleString('mr-IN')} उत्पादने</span></div>
          {loading ? <div className="market-empty">विक्रीची माहिती लोड होत आहे…</div>
            : !filteredListings.length ? <div className="market-empty"><Wheat size={28}/><b>{favoritesOnly ? 'आवडत्या नोंदी सापडल्या नाहीत' : 'शेतमाल सापडला नाही'}</b><span>{favoritesOnly ? 'यादीत हृदयाचे चिन्ह दाबून आवडते शेतमाल जतन करा.' : 'शोध किंवा फिल्टर बदलून पाहा किंवा नवी विक्रीची नोंद जोडा.'}</span></div>
              : <div className="market-listing-scroll">{filteredListings.map(listing=><article className={`market-listing-card ${accountType === 'farmer' ? 'market-farmer-listing-card' : ''}`} key={listing.id}>
                <ProductPhoto apiBase={apiBase} photoUrl={listing.photoUrl} authorization={authorization} productName={listing.productName}/>
                <div className="market-listing-content">
                  <div className="market-listing-heading"><span>{listing.category}</span>{accountType !== 'farmer' && <button type="button" className={`market-favorite-button ${favoriteIds.has(String(listing.id)) ? 'saved' : ''}`} aria-label={favoriteIds.has(String(listing.id)) ? 'आवडत्यामधून काढा' : 'आवडत्यात जतन करा'} aria-pressed={favoriteIds.has(String(listing.id))} onClick={event => toggleFavorite(listing.id, event)}><Heart size={14} fill={favoriteIds.has(String(listing.id)) ? 'currentColor' : 'none'}/></button>}</div>
                  <h3>{listing.productName}</h3><p className="market-crop-type">{listing.cropType}</p>
                  <p className={`market-stock ${listing.available === false ? 'unavailable' : ''}`}>{listing.available === false ? 'सध्या उपलब्ध नाही' : `${formatQuantity(listing)} उपलब्ध`}</p>
                  {accountType === 'farmer' ? farmerPriceComparison(listing) : priceDisplay(listing)}
                  {accountType !== 'farmer' && <>
                    <div className="market-seller"><UserRound size={13}/><b>{listing.sellerName}</b>{listing.sellerVerified&&<span className="market-seller-verification" title={`सेंद्रिय प्रमाणपत्र ${listing.sellerCertificateValidUntil ? `वैध: ${listing.sellerCertificateValidUntil}` : 'पडताळलेले'}`}><ShieldCheck size={11}/> पडताळलेले</span>}{Number(listing.sellerReviewCount)>0&&<span className="market-seller-rating"><Star size={11} fill="currentColor"/>{Number(listing.sellerRating).toFixed(1)} ({Number(listing.sellerReviewCount).toLocaleString('mr-IN')})</span>}</div>
                    <SellerReviews listing={listing} request={request}/>
                    <div className="market-listing-actions">
                      {accountType === 'admin'
                        ? <button type="button" className="market-admin-delete-button" onClick={event => deleteMarketplaceListing(listing, event)} disabled={deletingListingId !== null}><Trash2 size={14}/>{deletingListingId === listing.id ? 'हटवत आहे…' : 'नोंद हटवा'}</button>
                        : <>
                          {accountType === 'buyer' && listing.pricePerUnit != null && listing.available !== false && Number(listing.quantity) > 0
                            ? <button type="button" className="market-buy-button" onClick={event => addToCart(listing, event)}><ShoppingCart size={14}/>{cart.some(item => item.listingId === listing.id) ? 'आणखी जोडा' : 'कार्टमध्ये जोडा'}</button>
                            : <button type="button" className="market-buy-button" disabled><ShoppingBag size={14}/>{listing.available === false || Number(listing.quantity) <= 0 ? 'साठा संपला' : 'खरेदीदारांसाठी'}</button>}
                        </>}
                    </div>
                  </>}
                </div>
              </article>)}</div>}
      </section>
    </> : view === 'cart' && accountType === 'buyer' ? <section className="market-cart-page">
      <div className="market-form-card market-cart-intro">
        <div className="market-card-heading"><div><span>खरेदीची तयारी</span><h2>तुमचे कार्ट</h2></div><span className="market-result-count">{cartRows.length.toLocaleString('mr-IN')} वस्तू</span></div>
        <p>प्रत्येक वस्तूचे प्रमाण उपलब्ध साठ्यापेक्षा जास्त ठेवता येणार नाही. ऑर्डर दिल्यानंतर साठा विक्रेत्याच्या यादीतून राखीव केला जाईल.</p>
      </div>
      {cartNotice && <div className="market-message market-success" role="status"><Check size={15}/>{cartNotice}</div>}
      {!cartRows.length ? <div className="market-empty market-cart-empty"><ShoppingCart size={30}/><b>कार्ट रिकामे आहे</b><span>शेतमाल शोधा आणि खरेदीसाठी कार्टमध्ये जोडा.</span><button type="button" className="market-location-button" onClick={() => setView('browse')}><Search size={15}/> शेतमाल शोधा</button></div>
        : <div className="market-cart-layout">
          <div className="market-cart-items">{cartRows.map(item => {
            const listing = item.listing;
            const available = listing.available !== false && Number(listing.quantity) > 0;
            const overStock = Number(item.quantity) > Number(listing.quantity);
            return <article className={`market-cart-item ${!available || overStock ? 'unavailable' : ''}`} key={listing.id}>
              <ProductPhoto apiBase={apiBase} photoUrl={listing.photoUrl} authorization={authorization} productName={listing.productName}/>
              <div className="market-cart-item-main"><div><b>{listing.productName}</b><span>{listing.sellerName}</span>{priceDisplay(listing)}</div>
                <small>{formatQuantity(listing)} उपलब्ध{overStock ? ' · साठा बदलला, प्रमाण कमी करा' : ''}{!available ? ' · सध्या उपलब्ध नाही' : ''}</small>
                <div className="market-cart-item-actions"><div className="market-quantity-stepper">
                  <button type="button" aria-label="प्रमाण कमी करा" onClick={() => updateCartQuantity(listing, Math.max(0.001, Number(item.quantity) - 1))} disabled={Number(item.quantity) <= 0.001}><Minus size={13}/></button>
                  <input aria-label={`${listing.productName} चे प्रमाण`} type="number" min="0.001" max={listing.quantity} step="0.001" value={item.quantity} onChange={event => updateCartQuantity(listing, event.target.value)} disabled={!available}/>
                  <button type="button" aria-label="प्रमाण वाढवा" onClick={() => updateCartQuantity(listing, Math.min(Number(listing.quantity), Number(item.quantity) + 1))} disabled={!available || Number(item.quantity) >= Number(listing.quantity)}><Plus size={13}/></button>
                </div><span className="market-cart-line-total">₹{(effectivePrice(listing) * Number(item.quantity)).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                  <button type="button" className="market-cart-remove" onClick={() => removeCartItem(listing.id)} aria-label="कार्टमधून काढा"><Trash2 size={15}/></button></div>
              </div>
            </article>;
          })}</div>
          <form className="market-checkout-card" onSubmit={submitCheckout}>
            <div className="market-card-heading"><div><span>ऑर्डर</span><h2>खरेदी ऑर्डर</h2></div><ShoppingBag size={19}/></div>
            <label className="market-field"><span>तुमचे नाव *</span><SpeechTextInput><input value={buyerName} onChange={event => setBuyerName(event.target.value)} maxLength={100} required autoComplete="name"/></SpeechTextInput></label>
            <label className="market-field"><span>नोंदणीकृत मोबाईल</span><input value={buyerPhone} readOnly/><small>तुमचा संपर्क क्रमांक फक्त प्रशासक पाहू शकतो.</small></label>
            <label className="market-field"><span>माल कसा हवा?</span><select value={buyerFulfillment} onChange={event => setBuyerFulfillment(event.target.value)}><option value="PICKUP">मी घेऊन जाईन</option><option value="DELIVERY">पोहोचवून हवा</option></select></label>
            <label className="market-field"><span>विक्रेत्यांसाठी संदेश <small>ऐच्छिक</small></span><SpeechTextInput><textarea maxLength={500} value={buyerNote} onChange={event => setBuyerNote(event.target.value)} placeholder="सोयीची वेळ किंवा इतर माहिती"/></SpeechTextInput></label>
            <fieldset className="market-payment-options">
              <legend>पेमेंट पद्धत</legend>
              <label className={`market-payment-choice ${paymentMethod === 'CASH' ? 'selected' : ''}`}>
                <input type="radio" name="paymentMethod" value="CASH" checked={paymentMethod === 'CASH'} onChange={() => setPaymentMethod('CASH')} disabled={!paymentOptions.cashEnabled}/>
                <span><b>रोख पेमेंट</b><small>विक्रेत्याला रोख द्या; तो मिळाल्याची नोंद करेल.</small></span>
              </label>
              <label className={`market-payment-choice unavailable ${paymentMethod === 'UPI' ? 'selected' : ''}`}>
                <input type="radio" name="paymentMethod" value="UPI" checked={paymentMethod === 'UPI'} onChange={() => setPaymentMethod('UPI')} disabled={!paymentOptions.upiEnabled}/>
                <span><b>UPI पेमेंट</b><small>{paymentOptions.upiEnabled ? 'सुरक्षित UPI चेकआउट उघडा.' : paymentOptions.upiMessage}</small></span>
              </label>
            </fieldset>
            <div className="market-checkout-total"><span>अंदाजे एकूण</span><b>₹{cartTotal.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</b></div>
            {buyerOrdersError && <div className="market-message market-error" role="alert">{buyerOrdersError}</div>}
            <p className="market-purchase-privacy"><ShieldCheck size={14}/> ऑर्डरची स्थिती दोघांना दिसेल. खरेदीदार आणि विक्रेत्यांचे संपर्क तपशील फक्त प्रशासकाला दिसतात.</p>
            <button type="submit" className="market-submit-button" disabled={buyerOrdersLoading || cartRows.some(item => item.listing.available === false || Number(item.quantity) > Number(item.listing.quantity))}><ShoppingBag size={16}/>{buyerOrdersLoading ? 'ऑर्डर पाठवत आहे…' : 'ऑर्डर निश्चित करा'}</button>
          </form>
        </div>}
    </section> : view === 'orders' && accountType === 'buyer' ? <section className="market-buyer-orders">
      <div className="market-form-card market-buyer-orders-intro">
        <div className="market-card-heading"><div><span>खरेदीचा मागोवा</span><h2>माझ्या ऑर्डर</h2></div><button type="button" className="market-location-button" onClick={loadBuyerOrders} disabled={buyerOrdersLoading}><RefreshCw size={14}/> ताजे करा</button></div>
        <p>तुमच्या ऑर्डरची स्थिती आणि पेमेंटची माहिती येथे पाहा.</p>
      </div>
      {buyerOrdersNotice && <div className="market-message market-success" role="status"><Check size={15}/>{buyerOrdersNotice}</div>}
      {buyerOrdersError && <div className="market-message market-error" role="alert">{buyerOrdersError}</div>}
      {receiptError && <div className="market-message market-error" role="alert">{receiptError}</div>}
      {buyerOrdersLoading ? <div className="market-empty">ऑर्डर लोड होत आहेत…</div>
        : !buyerOrders.length ? <div className="market-empty market-cart-empty"><ClipboardList size={30}/><b>अजून ऑर्डर नाही</b><span>कार्टमधून ऑर्डर दिल्यावर त्याची स्थिती येथे दिसेल.</span><button type="button" className="market-location-button" onClick={() => setView('browse')}><Search size={15}/> शेतमाल शोधा</button></div>
          : <div className="market-buyer-order-list">{buyerOrders.map(order => <article className="market-buyer-order-card" key={order.id}>
            <div className="market-buyer-order-main"><div><span>ऑर्डर #{order.id.toLocaleString('mr-IN')}</span><h3>{order.productName || `शेतमाल #${order.listingId}`}</h3></div><span className={`market-request-status status-${order.status.toLowerCase()}`}>{requestStatusLabel(order.status)}</span></div>
            <p>{order.sellerName || 'शेतकरी'} · {Number(order.quantity).toLocaleString('mr-IN')} {UNITS.find(([value]) => value === order.unit)?.[1] || order.unit}</p>
            <OrderProgress order={order}/>
            <div className="market-payment-summary"><span>{paymentMethodLabel(order.paymentMethod)}</span><b className={`payment-${String(order.paymentStatus || '').toLowerCase()}`}>{paymentStatusLabel(order.paymentStatus)}</b></div>
            {order.totalAmount != null && <b className="market-buyer-order-total">₹{Number(order.totalAmount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</b>}
            <time dateTime={order.createdAt}>{new Date(order.createdAt).toLocaleString('mr-IN', { dateStyle: 'medium', timeStyle: 'short' })}</time>
            <button type="button" className="market-receipt-button" onClick={() => downloadOrderReceipt(order)} disabled={receiptBusyId === order.id}><FileDown size={15}/>{receiptBusyId === order.id ? 'पावती तयार होत आहे…' : 'PDF पावती डाउनलोड करा'}</button>
            <MarketplaceConversation orderId={order.id} userId={userId} request={request}/>
            <SellerReviewForm order={order} request={request} onSubmitted={() => setBuyerOrders(current => current.map(item => item.id === order.id ? { ...item, reviewSubmitted: true } : item))}/>
          </article>)}</div>}
    </section> : view === 'register' ? <section className="market-form-card">
      <div className="market-card-heading"><div><span>विक्रेत्याची माहिती</span><h2>तुमचा उपलब्ध शेतमाल जोडा</h2></div><div className="market-private-badge"><ShieldCheck size={14}/> संपर्क माहिती सुरक्षित</div></div>
      <p className="market-form-intro">तुमची विक्रीची माहिती आणि अचूक पत्ता नोंदवा. नकाशावरील स्थान तुमच्या ब्राउझरमधून घ्या किंवा निर्देशांक भरा.</p>
      <form className="market-form" onSubmit={submitListing}>
        <div className="market-form-section-title"><UserRound size={16}/> विक्रेता आणि संपर्क</div>
        <div className="market-form-grid">
          <label className="market-field"><span>शेतकरी / विक्रेत्याचे नाव *</span><SpeechTextInput><input name="sellerName" value={form.sellerName} onChange={updateField} maxLength={100} required placeholder="पूर्ण नाव"/></SpeechTextInput></label>
          <label className="market-field"><span>मोबाइल नंबर * <small>भारतीय नंबर</small></span><SpeechTextInput><input name="phone" value={form.phone} onChange={updateField} maxLength={18} inputMode="tel" autoComplete="tel" required placeholder="+919876543210"/></SpeechTextInput></label>
          <label className="market-field market-field-wide"><span>संपूर्ण पत्ता *</span><SpeechTextInput><textarea name="address" value={form.address} onChange={updateField} maxLength={500} required placeholder="गाव, तालुका, जिल्हा आणि शेताचा पत्ता"/></SpeechTextInput></label>
        </div>
        <div className="market-location-fields">
          <div className="market-form-section-title"><MapPin size={16}/> नकाशावरील अचूक शेताचे स्थान *</div>
          <button type="button" className="market-location-button" onClick={askForLocation}><LocateFixed size={15}/> माझे सध्याचे स्थान घ्या</button>
          {locationMessage && <p className="market-location-note" role="status">{locationMessage}</p>}
          <div className="market-form-grid market-coordinate-grid">
            <label className="market-field"><span>अक्षांश (Latitude)</span><input name="latitude" type="number" min="-90" max="90" step="any" value={form.latitude} onChange={updateField} required placeholder="उदा. 16.8524"/></label>
            <label className="market-field"><span>रेखांश (Longitude)</span><input name="longitude" type="number" min="-180" max="180" step="any" value={form.longitude} onChange={updateField} required placeholder="उदा. 74.5815"/></label>
          </div>
          <small className="market-precision-note">अचूक पत्ता आणि स्थान फक्त प्रशासकाला दिसेल.</small>
        </div>
        <div className="market-form-section-title"><Wheat size={16}/> विक्रीसाठी शेतमाल</div>
        <div className="market-form-grid">
          <label className="market-field"><span>शेतमाल / खाद्यपदार्थाचे नाव *</span>
            <select name="productName" value={customProductName ? '__other__' : form.productName} onChange={event => updateChoice(event, setCustomProductName)} required>
              <option value="" disabled>शेतमाल निवडा</option>
              {MARKET_CROP_OPTIONS.map(name => <option value={name} key={name}>{name}</option>)}
              <option value="__other__">इतर शेतमाल / स्वतः लिहा</option>
            </select>
            {customProductName && <SpeechTextInput><input name="productName" value={form.productName} onChange={updateField} maxLength={120} required placeholder="शेतमालाचे नाव लिहा"/></SpeechTextInput>}
          </label>
          <label className="market-field"><span>शेतमालाचा प्रकार *</span><select name="category" value={form.category} onChange={updateField}>{CATEGORIES.map(category=><option key={category}>{category}</option>)}</select></label>
          <div className="market-quantity-field">
            <label className="market-field"><span>उपलब्ध प्रमाण *</span><input name="quantity" type="number" min="0.01" max="1000000000" step="any" value={form.quantity} onChange={updateField} required placeholder="उदा. ५०"/></label>
            <label className="market-field"><span>एकक</span><select name="unit" value={form.unit} onChange={updateField}>{UNITS.map(([unit,label])=><option value={unit} key={unit}>{label}</option>)}</select></label>
          </div>
          <label className="market-field"><span>प्रति एकक किंमत (₹) *</span><input name="pricePerUnit" type="number" min="0.01" max="9999999999.99" step="0.01" value={form.pricePerUnit} onChange={updateField} required placeholder="उदा. ८०.००"/><small>खरेदीदारांना ही किंमत दिसेल.</small></label>
          <label className="market-field"><span>ऑफर सवलत (%) <small>ऐच्छिक</small></span><input name="discountPercent" type="number" min="0" max="99.99" step="0.01" value={form.discountPercent} onChange={updateField} placeholder="उदा. १०"/><small>सवलतीनंतरची किंमत खरेदीदारांना दिसेल.</small></label>
          <label className="market-field market-field-wide"><span>शेतमालाची माहिती <small>ऐच्छिक</small></span><SpeechTextInput><textarea name="description" value={form.description} onChange={updateField} maxLength={1000} placeholder="काढणीची तारीख, उपलब्धता किंवा इतर तपशील"/></SpeechTextInput></label>
        </div>
        <div className="market-photo-picker">
          <input ref={fileInputRef} type="file" accept="image/*" onChange={selectPhoto} hidden/>
          {previewUrl ? <div className="market-photo-preview"><img src={previewUrl} alt="शेतमालाचा निवडलेला फोटो"/><button type="button" onClick={() => setPhoto(null)} aria-label="फोटो काढा"><X size={16}/></button></div>
            : <button type="button" className="market-upload-button" onClick={() => fileInputRef.current?.click()}><Upload size={17}/> शेतमालाचा फोटो जोडा <small>१० MB पर्यंत · ऐच्छिक</small></button>}
        </div>
        <div className="market-auth-note"><ShieldCheck size={15}/> तुमचा फोन नंबर फक्त अॅप प्रवेश केलेल्या खरेदीदारालाच दिसेल. विक्रेता नियंत्रणाची सुरक्षित माहिती याच ब्राउझरमध्ये जतन होईल.</div>
        <button className="market-submit-button" type="submit" disabled={saving}>{saving?'नोंद जतन होत आहे…':<><PackagePlus size={17}/> विक्रीची नोंद जतन करा</>}</button>
      </form>
    </section> : <section className="market-seller-dashboard">
      <div className="market-form-card market-seller-intro">
        <div className="market-card-heading"><div><span>विक्रेता विभाग</span><h2>माझे व्यवहार</h2></div><button type="button" className="market-location-button" onClick={loadSellerListings}><ArrowDownWideNarrow size={15}/> ताजे करा</button></div>
        <p>दर आणि साठा पाहा. बदल किंवा खरेदीच्या विनंत्यांसाठी नोंद उघडा.</p>
      </div>
      {sellerError && <div className="market-message market-error" role="alert">{sellerError}</div>}
      {sellerNotice && <div className="market-message market-success" role="status"><Check size={15}/>{sellerNotice}</div>}
      {sellerLoading ? <div className="market-empty">विक्रेता माहिती लोड होत आहे…</div>
        : sellerListings.length === 0 ? <div className="market-empty market-seller-empty"><ClipboardList size={30}/><b>या ब्राउझरमध्ये विक्रीची नोंद नाही</b><span>नोंदणी केल्यानंतर तुमचा शेतमाल आणि खरेदीच्या विनंत्या येथे दिसतील. जुनी नोंद असल्यास तिचे नियंत्रण तपशील या ब्राउझरमध्ये उपलब्ध नसू शकतात.</span><button type="button" className="market-submit-button" onClick={() => setView('register')}><PackagePlus size={16}/> शेतमाल नोंदवा</button></div>
          : <div className="market-seller-list">{sellerListings.map(row => <article className="market-seller-card" key={row.listing.id}>
            <div className="market-card-heading"><div><span>{row.listing.category}</span><h2>{row.listing.productName}</h2></div><span className={`market-availability ${row.listing.available ? 'available' : ''}`}>{row.listing.available ? 'विक्री सुरू' : 'बंद'}</span></div>
            <div className="market-seller-quick-facts">
              <span><small>विक्री दर</small><b>{formatPrice(row.listing.pricePerUnit, row.listing.unit)}</b></span>
              <span><small>उपलब्ध साठा</small><b>{formatQuantity(row.listing)}</b></span>
              <span><small>नवीन विनंत्या</small><b>{row.requests.filter(item => item.status === 'PENDING').length.toLocaleString('mr-IN')}</b></span>
            </div>
            <details className="market-seller-details">
              <summary>किंमत, साठा आणि उपलब्धता बदला</summary>
              <div className="market-seller-controls">
                <label className="market-field"><span>किंमत (₹ / {UNITS.find(([value]) => value === row.listing.unit)?.[1] || row.listing.unit})</span><input type="number" min="0.01" max="9999999999.99" step="0.01" value={row.listing.pricePerUnit ?? ''} onChange={event => updateSellerListing(row.listing.id, 'pricePerUnit', event.target.value)}/></label>
                <label className="market-field"><span>ऑफर सवलत (%)</span><input type="number" min="0" max="99.99" step="0.01" value={row.listing.discountPercent ?? ''} onChange={event => updateSellerListing(row.listing.id, 'discountPercent', event.target.value)} placeholder="ऐच्छिक"/><small>रिकामे ठेवल्यास ऑफर काढली जाईल.</small></label>
                <label className="market-field"><span>उपलब्ध प्रमाण ({UNITS.find(([value]) => value === row.listing.unit)?.[1] || row.listing.unit})</span><input type="number" min="0" max="1000000000" step="any" value={row.listing.quantity} onChange={event => updateSellerListing(row.listing.id, 'quantity', event.target.value)}/></label>
                <div className="market-stock-add"><label className="market-field"><span>साठ्यात वाढवा</span><input type="number" min="0.001" max="1000000000" step="any" value={sellerStockAdds[row.listing.id] ?? ''} onChange={event => setSellerStockAdds(current => ({ ...current, [row.listing.id]: event.target.value }))} placeholder="प्रमाण"/></label><button type="button" className="market-stock-add-button" onClick={() => addSellerStock(row)} disabled={sellerSavingId === row.listing.id || !sellerStockAdds[row.listing.id]}><Plus size={13}/> साठ्यात जोडा</button></div>
                <label className="market-availability-toggle"><input type="checkbox" checked={Boolean(row.listing.available)} onChange={event => updateSellerListing(row.listing.id, 'available', event.target.checked)}/> खरेदीसाठी उपलब्ध</label>
                <button type="button" className="market-submit-button" disabled={sellerSavingId === row.listing.id} onClick={() => saveSellerListing(row)}>{sellerSavingId === row.listing.id ? 'जतन होत आहे…' : 'बदल जतन करा'}</button>
              </div>
            </details>
            <details className="market-seller-details market-seller-requests">
              <summary>खरेदीच्या विनंत्या ({row.requests.length.toLocaleString('mr-IN')})</summary>
              {!row.requests.length ? <p className="market-no-requests">अजून खरेदीची विनंती आलेली नाही.</p> : <div className="market-request-list">{row.requests.map(purchaseRequest => <div className="market-request-card" key={purchaseRequest.id}>
                <div className="market-request-top"><b>{purchaseRequest.buyerName}</b><span className={`market-request-status status-${purchaseRequest.status.toLowerCase()}`}>{requestStatusLabel(purchaseRequest.status)}</span></div>
                <p>{Number(purchaseRequest.quantity).toLocaleString('mr-IN')} {UNITS.find(([value]) => value === purchaseRequest.unit)?.[1] || purchaseRequest.unit} · {purchaseRequest.fulfillment === 'DELIVERY' ? 'पोहोचवणे हवे' : 'स्वतः घेऊन जाणार'}{purchaseRequest.totalAmount != null ? ` · ₹${Number(purchaseRequest.totalAmount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : ''}</p>
                <div className="market-payment-summary"><span>{paymentMethodLabel(purchaseRequest.paymentMethod)}</span><b className={`payment-${String(purchaseRequest.paymentStatus || '').toLowerCase()}`}>{paymentStatusLabel(purchaseRequest.paymentStatus)}</b></div>
                <OrderProgress order={purchaseRequest}/>
                {purchaseRequest.note && <p className="market-request-note">“{purchaseRequest.note}”</p>}
                <div className="market-request-actions">
                  {purchaseRequest.paymentMethod === 'CASH' && purchaseRequest.paymentStatus === 'PENDING' && purchaseRequest.status !== 'DECLINED' && <button type="button" onClick={() => updatePaymentStatus(row, purchaseRequest, 'PAID')}>रोख पेमेंट मिळाले</button>}
                  {purchaseRequest.status === 'PENDING' && <><button type="button" onClick={() => updatePurchaseStatus(row, purchaseRequest, 'ACCEPTED')}>स्वीकारा</button><button type="button" className="decline" onClick={() => updatePurchaseStatus(row, purchaseRequest, 'DECLINED')}>नाकारा</button></>}
                  {purchaseRequest.status === 'ACCEPTED' && nextSellerStage(purchaseRequest) && <div className="market-request-stage-action"><button type="button" disabled={purchaseRequest.paymentMethod === 'CASH' && purchaseRequest.paymentStatus !== 'PAID' && nextSellerStage(purchaseRequest) === 'COMPLETED'} onClick={() => updateFulfillmentStage(row, purchaseRequest, nextSellerStage(purchaseRequest))}><PackageCheck size={13}/>{stageLabel(nextSellerStage(purchaseRequest))} म्हणून नोंदवा</button>{purchaseRequest.paymentMethod === 'CASH' && purchaseRequest.paymentStatus !== 'PAID' && nextSellerStage(purchaseRequest) === 'COMPLETED'&&<small>पूर्ण करण्यापूर्वी रोख पेमेंट नोंदवा.</small>}</div>}
                </div>
                <MarketplaceConversation orderId={purchaseRequest.id} userId={userId} request={request}/>
              </div>)}</div>}
            </details>
          </article>)}</div>}
    </section>}

  </div>;
}

export default MarketplacePanel;
