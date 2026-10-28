import React, { useCallback, useEffect, useState } from "react";
import { Boxes, Plus, Trash2 } from "lucide-react";
import { TradeField, useTradeApi } from "./TradeFeatures.jsx";
import "./tradeFeatures.css";

function useMaterials(apiFetch, apiBase) {
  const request = useCallback(
    (path, options) => apiFetch(`${apiBase}${path}`, options),
    [apiFetch, apiBase],
  );
  return useTradeApi(request);
}
export function MaterialInventory({ apiFetch, apiBase }) {
  const api = useMaterials(apiFetch, apiBase),
    [items, setItems] = useState([]),
    [error, setError] = useState(""),
    [history, setHistory] = useState({}),
    [form, setForm] = useState({
      name: "",
      category: "बियाणे",
      unit: "kg",
      openingStock: "0",
      lowThreshold: "0",
      unitCost: "",
    }),
    [busy, setBusy] = useState(false);
  const load = useCallback(
    () =>
      api("/materials")
        .then(setItems)
        .catch((e) => setError(e.message)),
    [api],
  );
  useEffect(() => {
    load();
  }, [load]);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/materials", "POST", {
        ...form,
        openingStock: Number(form.openingStock),
        lowThreshold: Number(form.lowThreshold),
        unitCost: form.unitCost === "" ? null : Number(form.unitCost),
      });
      setForm((f) => ({ ...f, name: "", openingStock: "0", unitCost: "" }));
      await load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="trade-card trade-inventory">
      <summary>
        <Boxes size={18} /> शेती साहित्याचा साठा{" "}
        {items.some((i) => Number(i.quantity) <= Number(i.lowThreshold)) && (
          <span className="trade-low">साठा कमी</span>
        )}
      </summary>
      <p>
        बियाणे, खते आणि इतर साहित्य सांभाळा. दैनिक नोंदीत वापर निवडल्यावर साठा
        आपोआप कमी होतो.
      </p>
      {error && (
        <p className="trade-notice error" role="alert">
          {error}
        </p>
      )}
      <div className="trade-card-list">
        {items.map((i) => (
          <article key={i.id} className="trade-material">
            <header>
              <h3>{i.name}</h3>
              <b
                className={
                  Number(i.quantity) <= Number(i.lowThreshold)
                    ? "trade-low"
                    : ""
                }
              >
                {i.quantity} {i.unit}
              </b>
            </header>
            <small>
              {i.category} · कमी साठ्याची मर्यादा {i.lowThreshold} {i.unit}
              {i.unitCost != null ? ` · ₹${i.unitCost} / ${i.unit}` : ""}
            </small>
            <MaterialAdjustment item={i} api={api} onUpdated={load} />
            <button
              className="secondary"
              onClick={async () => {
                try {
                  setHistory((h) => ({ ...h, [i.id]: null }));
                  const rows = await api(`/materials/${i.id}/history`);
                  setHistory((h) => ({ ...h, [i.id]: rows }));
                } catch (e) {
                  setError(e.message);
                }
              }}
            >
              साठ्याच्या नोंदी पहा
            </button>
            {history[i.id] && (
              <div className="trade-history">
                {history[i.id].map((h) => (
                  <div key={h.id}>
                    <span>
                      {new Date(h.createdAt).toLocaleDateString("mr-IN")} ·{" "}
                      {h.reason === "DIARY"
                        ? `दैनिक नोंद #${h.entryId}`
                        : h.reason === "OPENING"
                          ? "सुरुवातीचा साठा"
                          : h.reason}
                    </span>
                    <b>
                      {Number(h.quantity) > 0 ? "+" : ""}
                      {h.quantity} {i.unit}
                    </b>
                  </div>
                ))}
              </div>
            )}
          </article>
        ))}
      </div>
      <details className="trade-tools">
        <summary>
          <Plus size={16} /> नवीन साहित्य जोडा
        </summary>
        <form className="trade-grid" onSubmit={submit}>
          <TradeField label="साहित्याचे नाव" voice>
            <input
              required
              maxLength={120}
              value={form.name}
              onChange={(e) => set("name", e.target.value)}
            />
          </TradeField>
          <TradeField label="प्रकार">
            <select
              value={form.category}
              onChange={(e) => set("category", e.target.value)}
            >
              {["बियाणे", "खत", "कीड नियंत्रण", "पशुखाद्य", "इतर"].map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </TradeField>
          <TradeField label="माप">
            <select
              value={form.unit}
              onChange={(e) => set("unit", e.target.value)}
            >
              {["kg", "g", "litre", "ml", "piece", "bag"].map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </TradeField>
          <TradeField label="सध्याचा साठा">
            <input
              required
              type="number"
              min="0"
              step="0.001"
              value={form.openingStock}
              onChange={(e) => set("openingStock", e.target.value)}
            />
          </TradeField>
          <TradeField label="कमी साठ्याची सूचना कधी द्यावी?">
            <input
              required
              type="number"
              min="0"
              step="0.001"
              value={form.lowThreshold}
              onChange={(e) => set("lowThreshold", e.target.value)}
            />
          </TradeField>
          <TradeField label="प्रति माप खर्च (₹, ऐच्छिक)">
            <input
              type="number"
              min="0"
              step="0.01"
              value={form.unitCost}
              onChange={(e) => set("unitCost", e.target.value)}
            />
          </TradeField>
          <button disabled={busy}>साहित्य जतन करा</button>
        </form>
      </details>
    </details>
  );
}
function MaterialAdjustment({ item, api, onUpdated }) {
  const [amount, setAmount] = useState(""),
    [kind, setKind] = useState("ADD"),
    [reason, setReason] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [requestKey, setRequestKey] = useState(() => crypto.randomUUID());
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api(`/materials/${item.id}/adjust`, "POST", {
        quantity: Number(amount) * (kind === "REMOVE" ? -1 : 1),
        reason,
        requestKey,
      });
      setAmount("");
      setReason("");
      setRequestKey(crypto.randomUUID());
      await onUpdated();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <details className="trade-tools">
      <summary>साठा वाढवा / दुरुस्त करा</summary>
      <form className="trade-grid" onSubmit={submit}>
        <TradeField label="बदल">
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value);
              setRequestKey(crypto.randomUUID());
            }}
          >
            <option value="ADD">साठा जोडा</option>
            <option value="REMOVE">साठा कमी करा</option>
          </select>
        </TradeField>
        <TradeField label={`प्रमाण (${item.unit})`}>
          <input
            required
            type="number"
            min="0.001"
            step="0.001"
            value={amount}
            onChange={(e) => {
              setAmount(e.target.value);
              setRequestKey(crypto.randomUUID());
            }}
          />
        </TradeField>
        <TradeField label="कारण / खरेदी नोंद" voice>
          <input
            required
            maxLength={120}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </TradeField>
        <button disabled={busy}>बदल जतन करा</button>
        {error && (
          <p className="trade-notice error" role="alert">
            {error}
          </p>
        )}
      </form>
    </details>
  );
}
export function EntryMaterialPicker({
  apiFetch,
  apiBase,
  value,
  onChange,
  onCost,
}) {
  const api = useMaterials(apiFetch, apiBase),
    [items, setItems] = useState([]),
    [error, setError] = useState("");
  useEffect(() => {
    api("/materials")
      .then(setItems)
      .catch((e) => setError(e.message));
  }, [api]);
  const usages = Array.isArray(value) ? value : [];
  const total = usages.reduce(
    (n, u) =>
      n +
      Number(u.quantity || 0) *
        Number(items.find((i) => i.id === Number(u.materialId))?.unitCost || 0),
    0,
  );
  function change(index, key, v) {
    onChange(usages.map((u, n) => (n === index ? { ...u, [key]: v } : u)));
  }
  return (
    <section className="form-section trade-entry-materials">
      <h3>
        <Boxes size={18} /> साहित्यातून वापर नोंदवा
      </h3>
      <p>
        निवडलेला वापर साठ्यातून वजा होईल. नोंद बदलल्यावर किंवा हटवल्यावर साठाही
        दुरुस्त होईल.
      </p>
      {error && <p role="alert">{error}</p>}
      {usages.map((u, index) => (
        <div className="trade-usage-row" key={index}>
          <TradeField label="साहित्य">
            <select
              required
              value={u.materialId || ""}
              onChange={(e) =>
                change(index, "materialId", Number(e.target.value))
              }
            >
              <option value="">निवडा</option>
              {items.map((i) => (
                <option value={i.id} key={i.id}>
                  {i.name} · {i.quantity} {i.unit} शिल्लक
                </option>
              ))}
            </select>
          </TradeField>
          <TradeField
            label={`वापर (${items.find((i) => i.id === Number(u.materialId))?.unit || "माप"})`}
          >
            <input
              required
              type="number"
              min="0.001"
              step="0.001"
              value={u.quantity ?? ""}
              onChange={(e) =>
                change(
                  index,
                  "quantity",
                  e.target.value === "" ? "" : Number(e.target.value),
                )
              }
            />
          </TradeField>
          <button
            type="button"
            className="secondary"
            aria-label="साहित्य काढा"
            onClick={() => onChange(usages.filter((_, n) => n !== index))}
          >
            <Trash2 size={16} />
          </button>
        </div>
      ))}
      <div className="trade-actions">
        <button
          type="button"
          className="secondary"
          onClick={() =>
            onChange([...usages, { materialId: "", quantity: "" }])
          }
          disabled={!items.length || usages.length >= 40}
        >
          <Plus size={16} /> वापर जोडा
        </button>
        {usages.length > 0 && (
          <button
            type="button"
            className="secondary"
            onClick={() => onCost(Number(total.toFixed(2)))}
          >
            साहित्य खर्च ₹{total.toFixed(2)} भरा
          </button>
        )}
      </div>
      {!items.length && (
        <small>
          आधी दैनिक नोंद विभागातील ‘शेती साहित्याचा साठा’ येथे साहित्य जोडा.
        </small>
      )}
    </section>
  );
}
