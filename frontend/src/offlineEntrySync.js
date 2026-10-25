const QUEUE_KEY = 'swarnabhoomi-offline-entry-queue-v1';
const API = `${(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')}/api`;
let syncing = false;

function readQueue(ownerId) {
  if (!ownerId) return [];
  try { const value = JSON.parse(localStorage.getItem(`${QUEUE_KEY}:${ownerId}`) || '[]'); return Array.isArray(value) ? value : []; }
  catch { return []; }
}

function toast(message) {
  const previous = document.querySelector('.offline-save-toast');
  previous?.remove();
  const element = document.createElement('div');
  element.className = 'offline-save-toast';
  element.setAttribute('role', 'status');
  element.textContent = message;
  document.body.append(element);
  window.setTimeout(() => element.remove(), 6500);
}

export function queueFarmEntry(body, ownerId) {
  if (!ownerId) throw new Error('Sign in before saving an offline farm entry.');
  const record = typeof body === 'string' ? JSON.parse(body) : body;
  if (!record || typeof record !== 'object' || !record.clientRequestId) throw new Error('Offline entry is missing its request ID.');
  const key = `${QUEUE_KEY}:${ownerId}`;
  const queue = readQueue(ownerId);
  if (!queue.some(item => item.clientRequestId === record.clientRequestId)) {
    queue.push(record);
    localStorage.setItem(key, JSON.stringify(queue));
  }
  toast('नोंद या उपकरणावर जतन झाली. इंटरनेट मिळाल्यावर ती आपोआप सर्व्हरवर पाठवली जाईल. फोटो जोडण्यासाठी इंटरनेट लागेल.');
  window.dispatchEvent(new Event('swarnabhoomi-entry-queued'));
  return new Response(JSON.stringify(record), { status: 202, headers: { 'Content-Type': 'application/json' } });
}

async function syncQueue() {
  if (syncing || !navigator.onLine) return;
  const token = sessionStorage.getItem('swarnabhoomi-user-token') || '';
  let user;
  try { user = JSON.parse(sessionStorage.getItem('swarnabhoomi-user') || 'null'); } catch { return; }
  if (!token || !user?.id) return;
  const key = `${QUEUE_KEY}:${user.id}`;
  syncing = true;
  let changed = false;
  try {
    let queue = readQueue(user.id);
    while (queue.length && navigator.onLine) {
      const record = queue[0];
      const response = await fetch(`${API}/entries`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify(record)
      });
      if (response.status === 401 || !response.ok) break;
      queue = queue.filter(item => item.clientRequestId !== record.clientRequestId);
      localStorage.setItem(key, JSON.stringify(queue));
      changed = true;
    }
    if (changed && queue.length === 0) toast('ऑफलाइन नोंदी सर्व्हरवर समक्रमित झाल्या.');
    if (changed) window.dispatchEvent(new Event('swarnabhoomi-entries-synced'));
  } catch { /* Keep queued records for the next online attempt. */ }
  finally { syncing = false; }
}

if (typeof window !== 'undefined') {
  window.addEventListener('online', syncQueue);
  window.addEventListener('focus', syncQueue);
  window.addEventListener('swarnabhoomi-entry-queued', () => { if (navigator.onLine) window.setTimeout(syncQueue, 1200); });
  window.setTimeout(syncQueue, 1200);
}
