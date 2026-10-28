import React, { useCallback, useEffect, useState } from "react";
import {
  CalendarDays,
  CheckCircle2,
  Download,
  PackageCheck,
  QrCode,
  RefreshCw,
  Repeat2,
  ShieldCheck,
  Tag,
  Truck,
} from "lucide-react";
import QRCode from "qrcode";
import SpeechTextInput from "./SpeechTextInput.jsx";
import "./tradeFeatures.css";

export const tradeUnits = [
  ["kg", "किलो"],
  ["litre", "लिटर"],
  ["quintal", "क्विंटल"],
  ["ton", "टन"],
  ["dozen", "डझन"],
  ["crate", "क्रेट"],
  ["piece", "नग"],
];
const labels = {
  ACTIVE: "सुरू",
  PAUSED: "विराम",
  CANCELLED: "बंद",
  OPEN: "दर मागवत आहे",
  AWARDED: "दर स्वीकारला",
  CLOSED: "बंद",
  REQUESTED: "तपासणी बाकी",
  APPROVED: "मंजूर",
  REJECTED: "नामंजूर",
  REFUNDED: "परतावा नोंदवला",
  REPLACED: "बदल पूर्ण",
  ORDERED: "ऑर्डर तयार",
  FAILED: "ऑर्डर झाली नाही",
  SKIPPED: "वगळले",
  ACCEPTED: "स्वीकारले",
};
const money = (n) =>
  `₹${Number(n || 0).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
const date = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
const localTime = (value) =>
  value
    ? new Date(
        new Date(value).getTime() - new Date(value).getTimezoneOffset() * 60000,
      )
        .toISOString()
        .slice(0, 16)
    : "";
const iso = (value) => (value ? new Date(value).toISOString() : null);
export function useTradeApi(request) {
  return useCallback(
    async (path, method = "GET", body) => {
      const response = await request(path, {
        method,
        ...(body instanceof FormData
          ? { body }
          : body === undefined
            ? {}
            : {
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body),
              }),
      });
      const data =
        response.status === 204
          ? null
          : await response.json().catch(() => null);
      if (!response.ok)
        throw new Error(
          data?.message ||
            data?.detail ||
            "माहिती जतन करता आली नाही. पुन्हा प्रयत्न करा.",
        );
      return data;
    },
    [request],
  );
}
export function TradeField({ label, children, voice = false }) {
  return (
    <label className="trade-field">
      <span>{label}</span>
      {voice ? <SpeechTextInput>{children}</SpeechTextInput> : children}
    </label>
  );
}
function Notice({ error, message }) {
  return error ? (
    <p className="trade-notice error" role="alert">
      {error}
    </p>
  ) : message ? (
    <p className="trade-notice" role="status">
      {message}
    </p>
  ) : null;
}
function Status({ value }) {
  return (
    <span
      className={`trade-status trade-status-${String(value).toLowerCase()}`}
    >
      {labels[value] || value}
    </span>
  );
}
function Units({ value, onChange }) {
  return (
    <select value={value} onChange={onChange}>
      {tradeUnits.map(([v, l]) => (
        <option value={v} key={v}>
          {l}
        </option>
      ))}
    </select>
  );
}

export function DeliveryActions({ order, buyer, request, onUpdated }) {
  const api = useTradeApi(request),
    [code, setCode] = useState(null),
    [entered, setEntered] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const ready =
    order.status === "ACCEPTED" &&
    ["READY_FOR_PICKUP", "OUT_FOR_DELIVERY"].includes(order.fulfillmentStage);
  if (!ready) return null;
  async function act(reset = false) {
    setBusy(true);
    setError("");
    try {
      if (buyer)
        setCode(
          await api(`/trade/orders/${order.id}/delivery-code`, "POST", {
            reset,
          }),
        );
      else {
        const r = await api(
          `/trade/orders/${order.id}/confirm-delivery`,
          "POST",
          { code: entered },
        );
        if (!r.verified) throw new Error(r.message);
        setEntered("");
        await onUpdated?.();
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="trade-delivery">
      <div className="trade-inline-title">
        <ShieldCheck size={18} />
        <b>सुरक्षित वितरण</b>
      </div>
      {buyer ? (
        <>
          <p>माल मिळाल्यावरच हा कोड विक्रेत्याला सांगा.</p>
          {code && (
            <div className="trade-code">
              <strong>{code.code}</strong>
              <small>
                वैधता: {new Date(code.expiresAt).toLocaleString("mr-IN")}
              </small>
            </div>
          )}
          <div className="trade-actions">
            <button type="button" onClick={() => act(false)} disabled={busy}>
              {code ? "कोड पुन्हा पहा" : "वितरण कोड पहा"}
            </button>
            {code && (
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={() => act(true)}
              >
                नवीन कोड घ्या
              </button>
            )}
          </div>
        </>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            act();
          }}
        >
          <p>पेमेंट नोंदवा, नंतर खरेदीदाराकडून मिळालेला सहा अंकी कोड तपासा.</p>
          <TradeField label="वितरण कोड">
            <input
              aria-label="वितरण कोड"
              value={entered}
              onChange={(e) =>
                setEntered(e.target.value.replace(/\D/g, "").slice(0, 6))
              }
              inputMode="numeric"
              autoComplete="off"
              pattern="[0-9]{6}"
              required
            />
          </TradeField>
          <button disabled={busy || order.paymentStatus !== "PAID"}>
            <PackageCheck size={15} /> वितरण पूर्ण करा
          </button>
        </form>
      )}
      <Notice error={error} />
    </div>
  );
}

export function ListingTools({ listing, request, onUpdated }) {
  const api = useTradeApi(request),
    [form, setForm] = useState(null),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    api(`/trade/listings/${listing.id}/settings`)
      .then((r) => {
        if (active)
          setForm({
            ...r,
            startsAt: localTime(r.offerStartsAt),
            endsAt: localTime(r.offerEndsAt),
          });
      })
      .catch((e) => active && setError(e.message));
    return () => {
      active = false;
    };
  }, [api, listing.id]);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  async function save(kind, e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api(
        `/trade/listings/${listing.id}/${kind}`,
        "PUT",
        kind === "offer"
          ? { startsAt: iso(form.startsAt), endsAt: iso(form.endsAt) }
          : {
              harvestDate: form.harvestDate || null,
              batchNumber: form.batchNumber,
              notes: form.notes,
              published: form.published,
            },
      );
      const fresh = await api(`/trade/listings/${listing.id}/settings`);
      setForm({
        ...fresh,
        startsAt: localTime(fresh.offerStartsAt),
        endsAt: localTime(fresh.offerEndsAt),
      });
      setMessage("तपशील जतन झाले.");
      await onUpdated?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function downloadQr() {
    setError("");
    try {
      const url = new URL("/", window.location.origin);
      url.searchParams.set("batch", form.token);
      const data = await QRCode.toDataURL(url.href, {
        width: 800,
        margin: 3,
        errorCorrectionLevel: "M",
      });
      const a = document.createElement("a");
      a.href = data;
      a.download = `Swarnabhoomi-batch-${listing.id}.png`;
      a.click();
    } catch {
      setError("QR तयार झाला नाही. पुन्हा प्रयत्न करा.");
    }
  }
  return (
    <details className="trade-tools">
      <summary>
        <CalendarDays size={17} /> ऑफर वेळापत्रक व बॅच QR
      </summary>
      <Notice error={error} message={message} />
      {form && (
        <div className="trade-tools-grid">
          <form onSubmit={(e) => save("offer", e)}>
            <h3>
              <Tag size={17} /> ऑफर कधी द्यायची?
            </h3>
            <p>
              वरील सवलत (%) जतन करा. या वेळेनुसार ऑफर आपोआप सुरू व बंद होईल. वेळ
              रिकामी असल्यास मर्यादा नाही.
            </p>
            <TradeField label="सुरुवातीची वेळ">
              <input
                type="datetime-local"
                value={form.startsAt}
                onChange={(e) => set("startsAt", e.target.value)}
              />
            </TradeField>
            <TradeField label="शेवटची वेळ">
              <input
                type="datetime-local"
                value={form.endsAt}
                min={form.startsAt || undefined}
                onChange={(e) => set("endsAt", e.target.value)}
              />
            </TradeField>
            <button disabled={busy}>ऑफर वेळ जतन करा</button>
          </form>
          <form onSubmit={(e) => save("batch", e)}>
            <h3>
              <QrCode size={17} /> काढणीपासून खरेदीपर्यंत
            </h3>
            <TradeField label="काढणी दिनांक">
              <input
                type="date"
                max={date()}
                value={form.harvestDate || ""}
                onChange={(e) => set("harvestDate", e.target.value)}
                required={form.published}
              />
            </TradeField>
            <TradeField label="बॅच क्रमांक" voice>
              <input
                maxLength={80}
                value={form.batchNumber || ""}
                onChange={(e) => set("batchNumber", e.target.value)}
                required={form.published}
              />
            </TradeField>
            <TradeField label="बॅचची माहिती (संपर्क तपशील टाळा)" voice>
              <textarea
                maxLength={1000}
                value={form.notes || ""}
                onChange={(e) => set("notes", e.target.value)}
              />
            </TradeField>
            <label className="trade-check">
              <input
                type="checkbox"
                checked={form.published}
                onChange={(e) => set("published", e.target.checked)}
              />{" "}
              ही बॅच माहिती सार्वजनिक QR पानावर दाखवा
            </label>
            <small>
              उत्पादन, नाव, काढणी आणि प्रमाणपत्र स्थिती दिसेल. शेताचा पत्ता
              किंवा संपर्क दिसणार नाही.
            </small>
            <div className="trade-actions">
              <button disabled={busy}>बॅच जतन करा</button>
              {form.token && form.published && (
                <button
                  className="secondary"
                  type="button"
                  onClick={downloadQr}
                >
                  <Download size={15} /> QR डाउनलोड
                </button>
              )}
            </div>
          </form>
        </div>
      )}
    </details>
  );
}

export function RecurringPurchases({ request, listings, profile }) {
  const api = useTradeApi(request),
    [items, setItems] = useState([]),
    [history, setHistory] = useState({}),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    listingId: "",
    quantity: "1",
    intervalDays: "7",
    nextDate: date(),
    maxPrice: "",
    buyerName: profile?.fullName || "",
    deliveryAddress: [
      profile?.address,
      profile?.village,
      profile?.taluka,
      profile?.district,
      profile?.pincode,
    ]
      .filter(Boolean)
      .join(", "),
  });
  const load = useCallback(
    () =>
      api("/trade/subscriptions")
        .then(setItems)
        .catch((e) => setError(e.message)),
    [api],
  );
  useEffect(() => {
    load();
  }, [load]);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  async function act(id, action) {
    setBusy(true);
    setError("");
    try {
      await api(`/trade/subscriptions/${id}/action`, "POST", { action });
      await load();
      setHistory((h) => {
        const next = { ...h };
        delete next[id];
        return next;
      });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function create(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api("/trade/subscriptions", "POST", {
        ...form,
        listingId: Number(form.listingId),
        quantity: Number(form.quantity),
        intervalDays: Number(form.intervalDays),
        maxPrice: Number(form.maxPrice),
      });
      setMessage(
        "वेळापत्रक सुरू झाले. पुढची खरेदी सकाळी ७ नंतर आपोआप नोंदवली जाईल.",
      );
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="trade-page">
      <div className="trade-hero">
        <Repeat2 />
        <div>
          <span>नियमित गरज, सोपी खरेदी</span>
          <h2>नियमित खरेदी</h2>
          <p>
            दररोज किंवा दर आठवड्याला शेतमाल मागवा. साठा व तुमची दरमर्यादा
            तपासूनच ऑर्डर तयार होईल.
          </p>
        </div>
      </div>
      <Notice error={error} message={message} />
      <details className="trade-card" open={!items.length}>
        <summary>नवीन वेळापत्रक तयार करा</summary>
        <form className="trade-grid" onSubmit={create}>
          <TradeField label="शेतमाल">
            <select
              required
              value={form.listingId}
              onChange={(e) => {
                const l = listings.find((l) => l.id === Number(e.target.value));
                setForm((f) => ({
                  ...f,
                  listingId: e.target.value,
                  maxPrice: String(
                    l?.discountedPricePerUnit ?? l?.pricePerUnit ?? "",
                  ),
                }));
              }}
            >
              <option value="">निवडा</option>
              {listings
                .filter((l) => l.available && l.quantity > 0)
                .map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.productName} ·{" "}
                    {money(l.discountedPricePerUnit ?? l.pricePerUnit)} /{" "}
                    {l.unit} · {l.sellerName}
                  </option>
                ))}
            </select>
          </TradeField>
          <TradeField label="प्रमाण (वरील मापात)">
            <input
              required
              type="number"
              min="0.001"
              step="0.001"
              value={form.quantity}
              onChange={(e) => set("quantity", e.target.value)}
            />
          </TradeField>
          <TradeField label="किती वेळा?">
            <select
              value={form.intervalDays}
              onChange={(e) => set("intervalDays", e.target.value)}
            >
              <option value="1">दररोज</option>
              <option value="7">दर आठवड्याला</option>
            </select>
          </TradeField>
          <TradeField label="पहिली खरेदी">
            <input
              required
              type="date"
              min={date()}
              value={form.nextDate}
              onChange={(e) => set("nextDate", e.target.value)}
            />
          </TradeField>
          <TradeField label="मला मान्य असलेला कमाल दर (₹ / माप)">
            <input
              required
              type="number"
              min="0.01"
              step="0.01"
              value={form.maxPrice}
              onChange={(e) => set("maxPrice", e.target.value)}
            />
          </TradeField>
          <TradeField label="तुमचे नाव" voice>
            <input
              required
              maxLength={100}
              value={form.buyerName}
              onChange={(e) => set("buyerName", e.target.value)}
            />
          </TradeField>
          <TradeField label="वितरणाचा पूर्ण पत्ता" voice>
            <textarea
              required
              maxLength={500}
              value={form.deliveryAddress}
              onChange={(e) => set("deliveryAddress", e.target.value)}
            />
          </TradeField>
          <div className="trade-form-end">
            <p>
              सकाळी ७ नंतर ऑर्डर नोंदवली जाते; वितरणाची वेळ विक्रेता ठरवेल.
              प्रत्येक ऑर्डरचे रोख पेमेंट स्वतंत्र आहे. उपलब्ध नसल्यास त्या
              दिवसाची खरेदी वगळली जाईल.
            </p>
            <button disabled={busy}>
              <CheckCircle2 size={16} /> वेळापत्रक सुरू करा
            </button>
          </div>
        </form>
      </details>
      <div className="trade-card-list">
        {items.map((s) => (
          <article key={s.id} className="trade-card">
            <header>
              <h3>{s.productName || `शेतमाल #${s.listingId}`}</h3>
              <Status value={s.status} />
            </header>
            <p>
              {s.quantity} {s.unit} ·{" "}
              {s.intervalDays === 1 ? "दररोज" : "दर आठवड्याला"} · दरमर्यादा{" "}
              {money(s.maxPrice)}
            </p>
            <div className="trade-next">
              <CalendarDays size={18} /> पुढील खरेदी: <b>{s.nextDate}</b>
            </div>
            <p className="trade-muted">वितरण: {s.deliveryAddress}</p>
            <div className="trade-actions">
              {s.status !== "CANCELLED" && (
                <>
                  <button
                    disabled={busy}
                    onClick={() =>
                      act(s.id, s.status === "ACTIVE" ? "PAUSE" : "RESUME")
                    }
                  >
                    {s.status === "ACTIVE" ? "विराम द्या" : "पुन्हा सुरू करा"}
                  </button>
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => act(s.id, "SKIP")}
                  >
                    पुढची खरेदी वगळा
                  </button>
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => act(s.id, "CANCEL")}
                  >
                    वेळापत्रक बंद करा
                  </button>
                </>
              )}
              <button
                className="secondary"
                onClick={async () => {
                  try {
                    setHistory((h) => ({ ...h, [s.id]: null }));
                    const r = await api(`/trade/subscriptions/${s.id}/history`);
                    setHistory((h) => ({ ...h, [s.id]: r }));
                  } catch (e) {
                    setError(e.message);
                  }
                }}
              >
                मागील खरेदी पहा
              </button>
            </div>
            {history[s.id] && (
              <div className="trade-history">
                {history[s.id].length ? (
                  history[s.id].map((h) => (
                    <div key={h.id}>
                      <b>{h.deliveryDate}</b>
                      <Status value={h.outcome} />
                      <span>
                        {h.orderId
                          ? `ऑर्डर #${h.orderId}`
                          : {
                              "Current price exceeds your limit":
                                "दरमर्यादेपेक्षा किंमत जास्त",
                              "Insufficient stock or unavailable product":
                                "साठा उपलब्ध नाही",
                              "Missed while app unavailable; no late order created":
                                "त्या दिवसाची खरेदी झाली नाही",
                              "Product unit changed": "माप बदलले आहे",
                            }[h.detail] ||
                            h.detail ||
                            ""}
                      </span>
                    </div>
                  ))
                ) : (
                  <p>अजून खरेदी नोंद नाही.</p>
                )}
              </div>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}

export function BulkPurchases({ request, buyer, profile }) {
  const api = useTradeApi(request),
    [items, setItems] = useState([]),
    [sellerListings, setSellerListings] = useState([]),
    [error, setError] = useState(""),
    [message, setMessage] = useState("");
  const [form, setForm] = useState({
    productName: "",
    quantity: "",
    unit: "kg",
    budget: "",
    requiredDate: date(),
    district: profile?.district || "",
    note: "",
  });
  const load = useCallback(
    () =>
      api("/trade/bulk")
        .then(setItems)
        .catch((e) => setError(e.message)),
    [api],
  );
  useEffect(() => {
    load();
    if (!buyer)
      api("/trade/seller-settings")
        .then(setSellerListings)
        .catch((e) => setError(e.message));
  }, [load, api, buyer]);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  async function submit(e) {
    e.preventDefault();
    setError("");
    try {
      await api("/trade/bulk", "POST", {
        ...form,
        quantity: Number(form.quantity),
        budget: Number(form.budget),
      });
      setMessage("तुमची मागणी विक्रेत्यांना दिसेल. मिळालेले दर खाली पहा.");
      setForm((f) => ({
        ...f,
        productName: "",
        quantity: "",
        budget: "",
        note: "",
      }));
      await load();
    } catch (e) {
      setError(e.message);
    }
  }
  return (
    <section className="trade-page">
      <div className="trade-hero">
        <Truck />
        <div>
          <span>एकाच वेळी मोठी खरेदी</span>
          <h2>
            {buyer
              ? "घाऊक खरेदीसाठी दर मागवा"
              : "खरेदीदारांच्या मोठ्या मागण्या"}
          </h2>
          <p>
            {buyer
              ? "आवश्यक माल नोंदवा, दरांची तुलना करा आणि योग्य दर स्वीकारा."
              : "तुमच्या उपलब्ध मालासाठी दर पाठवा. दर स्वीकारल्यावरच साठा राखीव होतो."}
          </p>
        </div>
      </div>
      <Notice error={error} message={message} />
      {buyer && (
        <details className="trade-card" open={!items.length}>
          <summary>नवीन खरेदी मागणी</summary>
          <form className="trade-grid" onSubmit={submit}>
            <TradeField label="शेतमालाचे नाव" voice>
              <input
                required
                maxLength={120}
                value={form.productName}
                onChange={(e) => set("productName", e.target.value)}
              />
            </TradeField>
            <TradeField label="प्रमाण">
              <input
                required
                type="number"
                min="0.001"
                step="0.001"
                value={form.quantity}
                onChange={(e) => set("quantity", e.target.value)}
              />
            </TradeField>
            <TradeField label="माप">
              <Units
                value={form.unit}
                onChange={(e) => set("unit", e.target.value)}
              />
            </TradeField>
            <TradeField label="अपेक्षित दर (₹ / माप)">
              <input
                required
                type="number"
                min="0.01"
                step="0.01"
                value={form.budget}
                onChange={(e) => set("budget", e.target.value)}
              />
            </TradeField>
            <TradeField label="माल कधी हवा?">
              <input
                required
                type="date"
                min={date()}
                value={form.requiredDate}
                onChange={(e) => set("requiredDate", e.target.value)}
              />
            </TradeField>
            <TradeField label="जिल्हा" voice>
              <input
                required
                maxLength={100}
                value={form.district}
                onChange={(e) => set("district", e.target.value)}
              />
            </TradeField>
            <TradeField label="मालाबद्दल अपेक्षा (ऐच्छिक)" voice>
              <textarea
                maxLength={500}
                value={form.note}
                onChange={(e) => set("note", e.target.value)}
              />
            </TradeField>
            <div className="trade-form-end">
              <p>
                तुमचे संपर्क तपशील किंवा संपूर्ण पत्ता सार्वजनिक दिसणार नाहीत.
              </p>
              <button>दर मागवा</button>
            </div>
          </form>
        </details>
      )}
      {!items.length && <p className="trade-empty">अजून मागण्या नाहीत.</p>}
      {items.map((b) => (
        <BulkCard
          key={b.id}
          bulk={b}
          api={api}
          buyer={buyer}
          profile={profile}
          listings={sellerListings}
          onUpdated={load}
        />
      ))}
    </section>
  );
}
function BulkCard({ bulk: b, api, buyer, profile, listings, onUpdated }) {
  const [quotes, setQuotes] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [selected, setSelected] = useState(null);
  const [form, setForm] = useState({
      listingId: "",
      unitPrice: "",
      validUntil: b.requiredDate,
      note: "",
    }),
    [name, setName] = useState(profile?.fullName || ""),
    [address, setAddress] = useState(
      [profile?.address, profile?.village, profile?.district, profile?.pincode]
        .filter(Boolean)
        .join(", "),
    );
  async function load() {
    try {
      setQuotes(await api(`/trade/bulk/${b.id}/quotes`));
    } catch (e) {
      setError(e.message);
    }
  }
  async function quote(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api(`/trade/bulk/${b.id}/quotes`, "POST", {
        ...form,
        listingId: Number(form.listingId),
        unitPrice: Number(form.unitPrice),
      });
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function accept(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api(`/trade/bulk/${b.id}/quotes/${selected.id}/accept`, "POST", {
        buyerName: name,
        deliveryAddress: address,
      });
      setSelected(null);
      await load();
      await onUpdated();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <article className="trade-card">
      <header>
        <h3>{b.productName}</h3>
        <Status value={b.status} />
      </header>
      <p>
        <b>
          {b.quantity} {b.unit}
        </b>{" "}
        · अपेक्षित {money(b.budget)} / {b.unit}
      </p>
      <p>
        📍 {b.district} · आवश्यक दिनांक: {b.requiredDate}
      </p>
      {b.note && <p>{b.note}</p>}
      <Notice error={error} />
      <div className="trade-actions">
        <button className="secondary" onClick={load}>
          मिळालेले दर पहा
        </button>
        {buyer && b.status === "OPEN" && (
          <button
            className="secondary"
            onClick={async () => {
              setBusy(true);
              try {
                await api(`/trade/bulk/${b.id}`, "DELETE");
                await onUpdated();
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
            disabled={busy}
          >
            मागणी बंद करा
          </button>
        )}
      </div>
      {quotes && (
        <div className="trade-quotes">
          {!quotes.length && <p>अजून दर आलेले नाहीत.</p>}
          {quotes.map((q) => (
            <div key={q.id}>
              <div>
                <b>{q.productName || `शेतमाल #${q.listingId}`}</b>
                <p>
                  {q.sellerName} · {money(q.unitPrice)} / {b.unit}
                </p>
                <small>
                  एकूण {money(Number(q.unitPrice) * Number(b.quantity))} · वैधता{" "}
                  {q.validUntil}
                </small>
                {q.note && <p>{q.note}</p>}
                <Status value={q.status} />
                {q.orderId && (
                  <small>ऑर्डर #{q.orderId} · माझ्या ऑर्डरमध्ये पहा</small>
                )}
              </div>
              {buyer &&
                b.status === "OPEN" &&
                q.status === "OPEN" &&
                q.validUntil >= date() && (
                  <button onClick={() => setSelected(q)}>हा दर निवडा</button>
                )}
            </div>
          ))}
        </div>
      )}
      {selected && (
        <form className="trade-grid" onSubmit={accept}>
          <p className="trade-form-end">
            निवडलेला एकूण दर:{" "}
            <b>{money(Number(selected.unitPrice) * Number(b.quantity))}</b>
          </p>
          <TradeField label="खरेदीदाराचे नाव" voice>
            <input
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </TradeField>
          <TradeField label="वितरणाचा पत्ता" voice>
            <textarea
              required
              maxLength={500}
              value={address}
              onChange={(e) => setAddress(e.target.value)}
            />
          </TradeField>
          <div className="trade-actions">
            <button disabled={busy}>दर स्वीकारा व ऑर्डर द्या</button>
            <button
              className="secondary"
              type="button"
              onClick={() => setSelected(null)}
            >
              रद्द करा
            </button>
          </div>
        </form>
      )}
      {!buyer && b.status === "OPEN" && (
        <details className="trade-quote-form">
          <summary>माझा दर पाठवा</summary>
          <form className="trade-grid" onSubmit={quote}>
            <TradeField label="तुमचा उपलब्ध माल">
              <select
                required
                value={form.listingId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, listingId: e.target.value }))
                }
              >
                <option value="">निवडा</option>
                {listings
                  .filter((l) => l.unit === b.unit)
                  .map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.productName} · #{l.id}
                    </option>
                  ))}
              </select>
            </TradeField>
            <TradeField label="दर (₹ / माप)">
              <input
                required
                type="number"
                min="0.01"
                step="0.01"
                value={form.unitPrice}
                onChange={(e) =>
                  setForm((f) => ({ ...f, unitPrice: e.target.value }))
                }
              />
            </TradeField>
            <TradeField label="दर किती दिवस वैध?">
              <input
                required
                type="date"
                min={date()}
                max={b.requiredDate}
                value={form.validUntil}
                onChange={(e) =>
                  setForm((f) => ({ ...f, validUntil: e.target.value }))
                }
              />
            </TradeField>
            <TradeField label="मालाचे तपशील (ऐच्छिक)" voice>
              <textarea
                maxLength={500}
                value={form.note}
                onChange={(e) =>
                  setForm((f) => ({ ...f, note: e.target.value }))
                }
              />
            </TradeField>
            <button disabled={busy}>दर पाठवा</button>
          </form>
        </details>
      )}
    </article>
  );
}

async function prepareEvidence(file) {
  if (
    !/^image\/(jpeg|png|webp)$/.test(file.type) ||
    file.size > 10 * 1024 * 1024
  )
    throw new Error("१० MB पेक्षा लहान फोटो निवडा.");
  const bitmap = await createImageBitmap(file);
  try {
    const canvas = document.createElement("canvas");
    const scale = Math.min(1, 1400 / Math.max(bitmap.width, bitmap.height));
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas
      .getContext("2d")
      .drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return await new Promise((resolve, reject) =>
      canvas.toBlob(
        (blob) =>
          blob ? resolve(blob) : reject(new Error("फोटो वाचता आला नाही.")),
        "image/jpeg",
        0.85,
      ),
    );
  } finally {
    bitmap.close();
  }
}
export function ReturnRequest({ order, request }) {
  const api = useTradeApi(request),
    [form, setForm] = useState({ remedy: "REFUND", reason: "" }),
    [photos, setPhotos] = useState([]),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  if (order.status !== "COMPLETED") return null;
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const evidence = [];
      for (const photo of photos) {
        const data = new FormData();
        data.append("file", await prepareEvidence(photo), "return.jpg");
        evidence.push((await api("/trade/evidence", "POST", data)).id);
      }
      await api(`/trade/orders/${order.id}/returns`, "POST", {
        ...form,
        evidence,
      });
      setMessage("विनंती नोंदवली आहे. परतावा व बदल विभागात स्थिती पहा.");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="trade-tools">
      <summary>मालाबद्दल समस्या? परतावा / बदल मागवा</summary>
      <Notice error={error} message={message} />
      {!message && (
        <form className="trade-grid" onSubmit={submit}>
          <TradeField label="काय मदत हवी?">
            <select
              value={form.remedy}
              onChange={(e) =>
                setForm((f) => ({ ...f, remedy: e.target.value }))
              }
            >
              <option value="REFUND">रकमेचा परतावा</option>
              <option value="REPLACEMENT">माल बदलून हवा</option>
            </select>
          </TradeField>
          <TradeField label="समस्या सांगा" voice>
            <textarea
              required
              maxLength={1000}
              value={form.reason}
              onChange={(e) =>
                setForm((f) => ({ ...f, reason: e.target.value }))
              }
            />
          </TradeField>
          <TradeField label="मालाचे फोटो (जास्तीत जास्त ३)">
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              onChange={(e) => {
                if (e.target.files.length > 3) {
                  setError("जास्तीत जास्त ३ फोटो निवडा.");
                  e.target.value = "";
                  setPhotos([]);
                } else {
                  setError("");
                  setPhotos([...e.target.files]);
                }
              }}
            />
          </TradeField>
          <div className="trade-form-end">
            <p>
              प्रशासक तपासून निर्णय देईल. रोख परतावा मिळाल्याची नोंद प्रशासक
              करेल.
            </p>
            <button disabled={busy}>
              {busy ? "पाठवत आहे…" : "विनंती पाठवा"}
            </button>
          </div>
        </form>
      )}
    </details>
  );
}
function EvidencePhoto({ id, request }) {
  const [src, setSrc] = useState("");
  useEffect(() => {
    let active = true,
      url;
    request(`/trade/evidence/${id}`)
      .then((r) => {
        if (!r.ok) throw Error();
        return r.blob();
      })
      .then((b) => {
        url = URL.createObjectURL(b);
        if (active) setSrc(url);
      })
      .catch(() => {});
    return () => {
      active = false;
      if (url) URL.revokeObjectURL(url);
    };
  }, [id, request]);
  return src ? (
    <a href={src} target="_blank" rel="noreferrer">
      <img src={src} alt="तक्रारीसाठी जोडलेला मालाचा फोटो" />
    </a>
  ) : null;
}
export function ReturnsPanel({ request, admin = false, buyer = false }) {
  const api = useTradeApi(request),
    [items, setItems] = useState([]),
    [error, setError] = useState("");
  const load = useCallback(
    () =>
      api("/trade/returns")
        .then(setItems)
        .catch((e) => setError(e.message)),
    [api],
  );
  useEffect(() => {
    load();
  }, [load]);
  return (
    <section className="trade-page">
      <div className="trade-hero">
        <ShieldCheck />
        <div>
          <span>तुमच्या खरेदीसाठी मदत</span>
          <h2>परतावा व बदल</h2>
          <p>
            {admin
              ? "विनंत्या तपासा आणि केलेल्या परताव्याची किंवा माल बदलाची नोंद करा."
              : "वितरण पूर्ण झालेल्या ऑर्डरमधून विनंती नोंदवा. येथे प्रशासकाचा निर्णय पाहता येईल."}
          </p>
        </div>
        <button onClick={load} className="secondary">
          <RefreshCw size={16} /> ताजे करा
        </button>
      </div>
      <Notice error={error} />
      {!items.length && <p className="trade-empty">अजून विनंत्या नाहीत.</p>}
      {items.map((r) => (
        <article className="trade-card" key={r.id}>
          <header>
            <h3>
              {r.productName} · ऑर्डर #{r.orderId}
            </h3>
            <Status value={r.status} />
          </header>
          <p>
            {r.remedy === "REFUND" ? "परतावा मागितला" : "बदल मागितला"} ·{" "}
            {r.buyerName} → {r.sellerName}
          </p>
          {admin && (
            <p>
              खरेदीदार: {r.buyerMobile || "—"} · विक्रेता:{" "}
              {r.sellerMobile || "—"}
            </p>
          )}
          <p>{r.reason}</p>
          {(admin || buyer) && r.evidence && (
            <div className="trade-evidence">
              {r.evidence
                .split(",")
                .filter(Boolean)
                .map((id) => (
                  <EvidencePhoto key={id} id={id} request={request} />
                ))}
            </div>
          )}
          {r.adminNote && (
            <p className="trade-resolution">
              <b>प्रशासक:</b> {r.adminNote}
            </p>
          )}
          {r.refundAmount != null && (
            <p>
              नोंदवलेला परतावा: <b>{money(r.refundAmount)}</b> · संदर्भ{" "}
              {r.resolutionReference}
            </p>
          )}
          {admin && ["REQUESTED", "APPROVED"].includes(r.status) && (
            <ResolutionForm item={r} api={api} onUpdated={load} />
          )}
        </article>
      ))}
    </section>
  );
}
function ResolutionForm({ item, api, onUpdated }) {
  const options =
    item.status === "REQUESTED"
      ? ["APPROVED", "REJECTED"]
      : [item.remedy === "REFUND" ? "REFUNDED" : "REPLACED"];
  const [form, setForm] = useState({
      status: options[0],
      note: "",
      refundAmount: "",
      reference: "",
    }),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(
    () => setForm((f) => ({ ...f, status: options[0] })),
    [item.status],
  );
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api(`/trade/returns/${item.id}`, "PATCH", {
        ...form,
        refundAmount: form.refundAmount ? Number(form.refundAmount) : null,
      });
      await onUpdated();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="trade-grid" onSubmit={submit}>
      <Notice error={error} />
      <TradeField label="निर्णय">
        <select
          value={form.status}
          onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}
        >
          {options.map((o) => (
            <option value={o} key={o}>
              {labels[o]}
            </option>
          ))}
        </select>
      </TradeField>
      <TradeField label="निर्णयाचे कारण / नोंद" voice>
        <textarea
          required
          maxLength={1000}
          value={form.note}
          onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
        />
      </TradeField>
      {form.status === "REFUNDED" && (
        <>
          <TradeField label="प्रत्यक्ष दिलेला परतावा (₹)">
            <input
              required
              type="number"
              min="0.01"
              step="0.01"
              value={form.refundAmount}
              onChange={(e) =>
                setForm((f) => ({ ...f, refundAmount: e.target.value }))
              }
            />
          </TradeField>
          <TradeField label="पेमेंट / पावती संदर्भ" voice>
            <input
              required
              maxLength={120}
              value={form.reference}
              onChange={(e) =>
                setForm((f) => ({ ...f, reference: e.target.value }))
              }
            />
          </TradeField>
        </>
      )}
      {["REFUNDED", "REPLACED"].includes(form.status) && (
        <label className="trade-check">
          <input type="checkbox" required /> प्रत्यक्ष परतावा / माल बदल पूर्ण
          झाला आहे
        </label>
      )}
      <div>
        <button disabled={busy}>निर्णय जतन करा</button>
        <small>
          हे प्रशासकीय नोंद आहे. अॅपमधून स्वयंचलित पैसे पाठवले जात नाहीत.
        </small>
      </div>
    </form>
  );
}

export function PublicBatchPage({ token, apiBase = "/api" }) {
  const [data, setData] = useState(null),
    [error, setError] = useState("");
  useEffect(() => {
    const abort = new AbortController();
    fetch(`${apiBase}/marketplace/batches/${encodeURIComponent(token)}`, {
      signal: abort.signal,
    })
      .then((r) => {
        if (!r.ok)
          throw Error(
            "ही बॅच माहिती उपलब्ध नाही किंवा विक्रेत्याने सार्वजनिक केलेली नाही.",
          );
        return r.json();
      })
      .then(setData)
      .catch((e) => {
        if (e.name !== "AbortError") setError(e.message);
      });
    return () => abort.abort();
  }, [token, apiBase]);
  return (
    <main className="trade-public">
      <div className="trade-public-brand">🌾 स्वर्णभूमी</div>
      <section className="trade-card">
        <QrCode size={34} />
        <p className="trade-muted">तुमच्या शेतमालाची ओळख</p>
        <Notice error={error} />
        {data ? (
          <>
            <h1>{data.productName}</h1>
            <dl>
              {[
                ["शेतकरी", data.sellerName],
                ["पीक / प्रकार", data.cropType],
                ["बॅच क्रमांक", data.batchNumber],
                ["काढणी दिनांक", data.harvestDate],
              ].map(([k, v]) => (
                <div key={k}>
                  <dt>{k}</dt>
                  <dd>{v || "—"}</dd>
                </div>
              ))}
            </dl>
            {data.notes && <p>{data.notes}</p>}
            <p className="trade-resolution">
              {data.organicVerified
                ? "✓ सेंद्रिय प्रमाणपत्र प्रशासकाने तपासले आहे."
                : "सेंद्रिय प्रमाणपत्राची पुष्टी उपलब्ध नाही."}
            </p>
            <small>
              {data.source}. ही माहिती उत्पादनाच्या स्वतंत्र गुणवत्ता चाचणीचे
              प्रमाणपत्र नाही.
            </small>
          </>
        ) : (
          !error && <p>बॅच माहिती लोड होत आहे…</p>
        )}
        <a className="trade-back-link" href="/">
          स्वर्णभूमी उघडा →
        </a>
      </section>
    </main>
  );
}

export function AdminBackups({ request }) {
  const api = useTradeApi(request),
    [data, setData] = useState(null),
    [error, setError] = useState("");
  const load = useCallback(
    () =>
      api("/platform-admin/backups")
        .then(setData)
        .catch((e) => setError(e.message)),
    [api],
  );
  useEffect(() => {
    load();
    const timer = setInterval(load, 15000);
    return () => clearInterval(timer);
  }, [load]);
  async function download(name) {
    setError("");
    try {
      const r = await request(
        `/platform-admin/backups/${encodeURIComponent(name)}`,
      );
      if (!r.ok) throw Error("बॅकअप डाउनलोड करता आला नाही.");
      const blob = await r.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 30000);
    } catch (e) {
      setError(e.message);
    }
  }
  return (
    <details className="trade-card trade-backups">
      <summary>डेटाबेस बॅकअप</summary>
      <Notice error={error || data?.error} />
      {data && (
        <>
          <p>
            {data.schedule} · नवीनतम {data.retention} बॅकअप ठेवले जातात.
          </p>
          <p>
            {data.offsiteConfigured
              ? "S3 प्रत जोडली आहे."
              : "बॅकअप Render वरील अॅपच्या कायमस्वरूपी डिस्कवर आहेत. स्वतंत्र प्रत ठेवण्यासाठी डाउनलोड करा."}
          </p>
          <button
            disabled={!data.enabled || data.running}
            onClick={async () => {
              try {
                await api("/platform-admin/backups", "POST", {});
                await load();
              } catch (e) {
                setError(e.message);
              }
            }}
          >
            {data.running ? "बॅकअप सुरू आहे…" : "आत्ताच बॅकअप घ्या"}
          </button>
          <div className="trade-history">
            {data.backups?.map((b) => (
              <div key={b.name}>
                <span>
                  {new Date(b.createdAt).toLocaleString("mr-IN")}
                  <small>
                    {(b.bytes / 1024 / 1024).toFixed(2)} MB
                    {b.offsite ? " · S3 प्रत" : ""}
                  </small>
                </span>
                <button className="secondary" onClick={() => download(b.name)}>
                  <Download size={15} /> डाउनलोड
                </button>
              </div>
            ))}
          </div>
        </>
      )}
    </details>
  );
}
