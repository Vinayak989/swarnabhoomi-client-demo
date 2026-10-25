const PUSH_SUBSCRIPTION_KEY = 'swarnabhoomi-push-subscription-id';
const subscriptionKey = userId => `${PUSH_SUBSCRIPTION_KEY}:${userId}`;

export function getBrowserPushSubscriptionId(userId) {
  if (!userId) return null;
  const id = Number(window.localStorage.getItem(subscriptionKey(userId)));
  return Number.isSafeInteger(id) && id > 0 ? id : null;
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
  if (!response.ok) throw Error(body?.detail || body?.message || 'वेब सूचना सेट करता आली नाही.');
  return body;
}

export async function enableBrowserPush(apiFetch, apiBase, onUnauthorized, userId) {
  if (!window.isSecureContext || !('serviceWorker' in navigator)
      || !('PushManager' in window) || !('Notification' in window)) {
    throw Error('या ब्राउझरमध्ये डिव्हाइस सूचना उपलब्ध नाहीत. HTTPS व Chrome वापरून पुन्हा प्रयत्न करा.');
  }

  const keyResponse = await apiFetch(`${apiBase}/push/subscriptions/public-key`);
  const { publicKey } = await responseBody(keyResponse, onUnauthorized);
  if (!publicKey) throw Error('वेब सूचना सर्व्हरवर कॉन्फिगर नाहीत.');

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw Error('डिव्हाइस सूचनांची परवानगी द्या.');

  const registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: decodeApplicationServerKey(publicKey)
    });
  }

  const saved = await responseBody(await apiFetch(`${apiBase}/push/subscriptions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(subscription.toJSON())
  }), onUnauthorized);
  if (!Number.isSafeInteger(Number(saved?.id)) || Number(saved.id) < 1) throw Error('सूचना सदस्यता जतन झाली नाही.');
  window.localStorage.setItem(subscriptionKey(userId), String(saved.id));
  return Number(saved.id);
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
  if (userId) window.localStorage.removeItem(subscriptionKey(userId));
}
