self.addEventListener('install', event => {
  event.waitUntil(caches.open('swarnabhoomi-shell-v1').then(cache => cache.add('/')));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(
      keys.filter(key => key.startsWith('swarnabhoomi-shell-') && key !== 'swarnabhoomi-shell-v1')
        .map(key => caches.delete(key))
    )).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  event.respondWith((async () => {
    try {
      const response = await fetch(request);
      if (response.ok && response.type === 'basic') {
        await caches.open('swarnabhoomi-shell-v1').then(cache => cache.put(request, response.clone()));
      }
      return response;
    } catch {
      const cache = await caches.open('swarnabhoomi-shell-v1');
      return await cache.match(request) || (request.mode === 'navigate' ? cache.match('/') : undefined)
        || new Response('Offline', { status: 503, statusText: 'Offline' });
    }
  })());
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
    return existing ? existing.focus() : self.clients.openWindow(target.href);
  }));
});
