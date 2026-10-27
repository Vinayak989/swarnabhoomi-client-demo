import { getAppEnvironment } from './appEnvironment.js';

const PUSH_SUBSCRIPTION_KEY = 'swarnabhoomi-push-subscription-id';
const subscriptionKey = userId => `${PUSH_SUBSCRIPTION_KEY}:${userId}`;
const disabledKey = userId => `swarnabhoomi-push-disabled:${userId}`;

export function isBrowserPushDisabled(userId) {
  try { return window.localStorage.getItem(disabledKey(userId)) === 'true'; } catch { return false; }
}

export function getBrowserPushSubscriptionId(userId) {
  if (!userId) return null;
  try {
    const id = Number(window.localStorage.getItem(subscriptionKey(userId)));
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  } catch { return null; }
}

function forgetSubscription(userId) {
  try { window.localStorage.removeItem(subscriptionKey(userId)); } catch {}
}

function pushSupported() {
  return window.isSecureContext && 'serviceWorker' in navigator
    && 'PushManager' in window && 'Notification' in window;
}

function encodePushKey(value) {
  return window.btoa(Array.from(new Uint8Array(value), byte => String.fromCharCode(byte)).join(''))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function subscriptionDetails(subscription) {
  const serialized = subscription.toJSON?.() || {};
  const key = name => {
    try {
      const raw = subscription.getKey?.(name);
      if (raw) return encodePushKey(raw);
      const value = serialized.keys?.[name];
      return value ? encodePushKey(decodeApplicationServerKey(value)) : '';
    } catch { return ''; }
  };
  return { endpoint: subscription.endpoint || serialized.endpoint, keys: { p256dh: key('p256dh'), auth: key('auth') } };
}

function completeSubscription(subscription) {
  if (!subscription) return false;
  const details = subscriptionDetails(subscription);
  try {
    return Boolean(details.endpoint) && decodeApplicationServerKey(details.keys.p256dh).length === 65
      && decodeApplicationServerKey(details.keys.auth).length === 16;
  } catch { return false; }
}

async function saveSubscription(subscription, apiFetch, apiBase, onUnauthorized, userId) {
  const saved = await responseBody(await apiFetch(`${apiBase}/push/subscriptions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...subscriptionDetails(subscription), androidApp: getAppEnvironment().androidApp })
  }), onUnauthorized);
  if (!Number.isSafeInteger(Number(saved?.id)) || Number(saved.id) < 1) throw Error('सूचना सदस्यता जतन झाली नाही.');
  try { window.localStorage.setItem(subscriptionKey(userId), String(saved.id)); } catch {}
  return Number(saved.id);
}

export async function restoreBrowserPush(apiFetch, apiBase, onUnauthorized, userId) {
  if (isBrowserPushDisabled(userId)) return null;
  if (!pushSupported() || Notification.permission !== 'granted') { forgetSubscription(userId); return null; }
  const registration = await navigator.serviceWorker.getRegistration('/');
  const subscription = await registration?.pushManager?.getSubscription();
  if (!completeSubscription(subscription) || (subscription.expirationTime && subscription.expirationTime <= Date.now())) {
    forgetSubscription(userId);
    return null;
  }
  return saveSubscription(subscription, apiFetch, apiBase, onUnauthorized, userId);
}

function decodeApplicationServerKey(value) {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  return Uint8Array.from(window.atob(padded), character => character.charCodeAt(0));
}

async function responseBody(response, onUnauthorized) {
  if (response.status === 401) {
    onUnauthorized?.();
    throw Error('पुन्हा लॉगिन करा.');
  }
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(body?.detail || body?.message || `डिव्हाइस सूचना सेट करता आल्या नाहीत (HTTP ${response.status}). पुन्हा प्रयत्न करा.`);
    error.status = response.status;
    error.code = body?.code;
    throw error;
  }
  return body;
}

export async function enableBrowserPush(apiFetch, apiBase, onUnauthorized, userId) {
  const environment = getAppEnvironment();
  if (environment.isIOS && !environment.standalone) {
    throw Error('iPhone वर Safari → Share → Add to Home Screen मधून अॅप जोडा. नंतर होम स्क्रीनवरील स्वर्णभूमी उघडून सूचना सुरू करा. यासाठी iOS 16.4 किंवा पुढील आवृत्ती आवश्यक आहे.');
  }
  if (!pushSupported()) {
    throw Error(environment.isIOS
      ? 'या iPhone वर सूचना उपलब्ध नाहीत. iOS 16.4 किंवा पुढील आवृत्ती वापरा आणि होम स्क्रीनवरील स्वर्णभूमी अॅप उघडा.'
      : 'या ब्राउझरमध्ये डिव्हाइस सूचना उपलब्ध नाहीत. HTTPS व अद्ययावत ब्राउझर वापरून पुन्हा प्रयत्न करा.');
  }

  // Ask directly from the button click, before any network request consumes user activation.
  const permission = Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission;
  if (permission !== 'granted') {
    forgetSubscription(userId);
    throw Error('फोनच्या Settings मध्ये स्वर्णभूमी → Notifications सुरू करा आणि पुन्हा “डिव्हाइस सूचना सुरू करा” निवडा.');
  }
  const keyResponse = await apiFetch(`${apiBase}/push/subscriptions/public-key`);
  const { publicKey } = await responseBody(keyResponse, onUnauthorized);
  if (!publicKey) throw Error('वेब सूचना सर्व्हरवर कॉन्फिगर नाहीत.');

  const applicationServerKey = decodeApplicationServerKey(publicKey);
  await navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' });
  const registration = await navigator.serviceWorker.ready;
  let subscription = await registration.pushManager.getSubscription();
  const oldKey = subscription?.options?.applicationServerKey;
  const keyChanged = oldKey && (new Uint8Array(oldKey).length !== applicationServerKey.length
    || new Uint8Array(oldKey).some((value, index) => value !== applicationServerKey[index]));
  if (subscription && (!completeSubscription(subscription) || keyChanged || (subscription.expirationTime && subscription.expirationTime <= Date.now()))) {
    await subscription.unsubscribe();
    subscription = null;
  }
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey
    });
  }

  let id;
  try {
    id = await saveSubscription(subscription, apiFetch, apiBase, onUnauthorized, userId);
  } catch (error) {
    // A stored browser subscription may predate the current encryption or provider setup.
    if (error.status !== 400) throw error;
    await subscription.unsubscribe();
    subscription = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey });
    id = await saveSubscription(subscription, apiFetch, apiBase, onUnauthorized, userId);
  }
  try { window.localStorage.removeItem(disabledKey(userId)); } catch {}
  return id;
}

export async function sendBrowserPushTest(apiFetch, apiBase, onUnauthorized, userId) {
  const id = getBrowserPushSubscriptionId(userId);
  if (!id) throw Error('या डिव्हाइसवरील सूचना आधी सुरू करा.');
  await responseBody(await apiFetch(`${apiBase}/push/subscriptions/${id}/test`, { method: 'POST' }), onUnauthorized);
}

export async function disableBrowserPush(apiFetch, apiBase, onUnauthorized, userId) {
  const subscriptionId = getBrowserPushSubscriptionId(userId);
  if (subscriptionId) {
    const response = await apiFetch(`${apiBase}/push/subscriptions/${subscriptionId}`, { method: 'DELETE' });
    if (response.status === 401) {
      onUnauthorized?.();
      throw Error('पुन्हा लॉगिन करा.');
    }
    if (!response.ok && response.status !== 404) {
      const body = await response.json().catch(() => null);
      throw Error(body?.detail || body?.message || 'सूचना बंद करता आल्या नाहीत.');
    }
  }

  const registration = await navigator.serviceWorker?.getRegistration('/');
  const subscription = await registration?.pushManager?.getSubscription();
  if (subscription) await subscription.unsubscribe();
  if (userId) forgetSubscription(userId);
  try { window.localStorage.setItem(disabledKey(userId), 'true'); } catch {}
}
