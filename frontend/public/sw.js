const SHELL_CACHE = 'swarnabhoomi-shell-v2';

self.addEventListener('install', event => {
  event.waitUntil(caches.open(SHELL_CACHE).then(cache => cache.add('/')).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('swarnabhoomi-shell-') && key !== SHELL_CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then(response => {
      if (response.ok) caches.open(SHELL_CACHE).then(cache => cache.put('/', response.clone()));
      return response;
    }).catch(async () => (await caches.match('/')) || Response.error()));
    return;
  }

  event.respondWith(caches.match(request).then(cached => cached || fetch(request).then(response => {
    if (response.ok) caches.open(SHELL_CACHE).then(cache => cache.put(request, response.clone()));
    return response;
  })));
});

self.addEventListener('push', event => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = { body: event.data?.text() || '' };
  }

  event.waitUntil(self.registration.showNotification(payload.title || 'स्वर्णभूमी स्मरणपत्र', {
    body: payload.body || 'तुमच्यासाठी शेतीकामाचे स्मरणपत्र आहे.',
    icon: '/images/app-icon-192.png',
    badge: '/images/notification-badge.png',
    tag: payload.tag || undefined,
    timestamp: Number.isFinite(payload.timestamp) ? payload.timestamp : Date.now(),
    renotify: Boolean(payload.tag),
    vibrate: [300, 100, 300, 100, 500],
    requireInteraction: true,
    data: { url: payload.url || '/' }
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const target = new URL(event.notification.data?.url || '/', self.location.origin);
  if (target.origin !== self.location.origin) return;

  event.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clients => {
    const existing = clients.find(client => new URL(client.url).origin === self.location.origin);
    return existing
      ? existing.navigate(target.href).then(client => client?.focus())
      : self.clients.openWindow(target.href);
  }));
});
