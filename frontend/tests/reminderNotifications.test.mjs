import test, { beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { disableBrowserPush, enableBrowserPush, getBrowserPushSubscriptionId, isBrowserPushDisabled, restoreBrowserPush } from '../src/reminderNotifications.js';

let storage, permissionCalls, subscribeCalls, posts, registration, currentSubscription;
const publicKey = Buffer.alloc(65, 4).toString('base64url');
const response = body => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } });
const apiFetch = async (url, options) => {
  if (url.endsWith('/public-key')) return response({ publicKey });
  posts.push(JSON.parse(options.body));
  return response({ id: 42 });
};

beforeEach(() => {
  storage = new Map(); permissionCalls = 0; subscribeCalls = 0; posts = [];
  currentSubscription = {
    toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/test', keys: { p256dh: publicKey, auth: Buffer.alloc(16, 1).toString('base64url') } }),
    unsubscribe: async () => true
  };
  const notification = { permission: 'granted', requestPermission: async () => { permissionCalls++; return 'granted'; } };
  registration = { pushManager: {
    getSubscription: async () => null,
    subscribe: async () => { subscribeCalls++; return currentSubscription; }
  } };
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { serviceWorker: {
    register: async () => registration, ready: Promise.resolve(registration), getRegistration: async () => registration
  } } });
  globalThis.Notification = notification;
  globalThis.window = {
    isSecureContext: true, PushManager: {}, Notification: notification,
    atob: value => Buffer.from(value, 'base64').toString('binary'), location: { search: '?androidApp=1' },
    btoa: value => Buffer.from(value, 'binary').toString('base64'),
    localStorage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) }
  };
});

test('subscription waits for an active worker and keeps the Android app launch context', async () => {
  let activate;
  navigator.serviceWorker.ready = new Promise(resolve => { activate = resolve; });
  const enabling = enableBrowserPush(apiFetch, '/api', null, 7);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(subscribeCalls, 0);
  activate(registration);
  assert.equal(await enabling, 42);
  assert.equal(subscribeCalls, 1);
  assert.equal(posts[0].androidApp, true);
  assert.equal(getBrowserPushSubscriptionId(7), 42);
  assert.equal(getBrowserPushSubscriptionId(8), null);
});

test('permission is requested from the user click before network access', async () => {
  Notification.permission = 'default';
  await enableBrowserPush(async (...args) => {
    assert.equal(permissionCalls, 1);
    return apiFetch(...args);
  }, '/api', null, 7);
});

test('denied permission clears stale enabled state and does not send subscription requests', async () => {
  storage.set('swarnabhoomi-push-subscription-id:7', '42');
  Notification.permission = 'denied';
  assert.equal(await restoreBrowserPush(apiFetch, '/api', null, 7), null);
  assert.equal(getBrowserPushSubscriptionId(7), null);
  await assert.rejects(enableBrowserPush(apiFetch, '/api', null, 7), /Notifications/);
  assert.equal(posts.length, 0);
});

test('changed application key replaces a stale subscription', async () => {
  let removed = false;
  registration.pushManager.getSubscription = async () => ({
    options: { applicationServerKey: new Uint8Array(65).buffer },
    unsubscribe: async () => { removed = true; }
  });
  await enableBrowserPush(apiFetch, '/api', null, 7);
  assert.equal(removed, true);
  assert.equal(subscribeCalls, 1);
});

test('restoring an enabled device repairs its server registration without prompting', async () => {
  registration.pushManager.getSubscription = async () => currentSubscription;
  assert.equal(await restoreBrowserPush(apiFetch, '/api', null, 7), 42);
  assert.equal(permissionCalls, 0);
  assert.equal(subscribeCalls, 0);
  assert.equal(posts.length, 1);
});

test('explicitly disabled notifications stay off until the user enables them again', async () => {
  await enableBrowserPush(apiFetch, '/api', null, 7);
  await disableBrowserPush(async () => new Response(null, { status: 204 }), '/api', null, 7);
  assert.equal(isBrowserPushDisabled(7), true);
  assert.equal(await restoreBrowserPush(apiFetch, '/api', null, 7), null);
  await enableBrowserPush(apiFetch, '/api', null, 7);
  assert.equal(isBrowserPushDisabled(7), false);
});

test('Android subscriptions use native encryption keys when JSON metadata is incomplete', async () => {
  currentSubscription.toJSON = () => ({ endpoint: 'https://fcm.googleapis.com/wp/android-device' });
  currentSubscription.getKey = name => Buffer.alloc(name === 'auth' ? 16 : 65, 4);
  registration.pushManager.getSubscription = async () => currentSubscription;
  await enableBrowserPush(apiFetch, '/api', null, 7);
  assert.equal(Buffer.from(posts[0].keys.p256dh, 'base64url').length, 65);
  assert.equal(Buffer.from(posts[0].keys.auth, 'base64url').length, 16);
  assert.equal(subscribeCalls, 0);
});

test('incomplete stored subscriptions are replaced before sending registration', async () => {
  let removed = 0;
  registration.pushManager.getSubscription = async () => ({ toJSON: () => ({ endpoint: 'https://fcm.googleapis.com/old' }), unsubscribe: async () => { removed++; } });
  await enableBrowserPush(apiFetch, '/api', null, 7);
  assert.equal(removed, 1);
  assert.equal(subscribeCalls, 1);
  assert.equal(posts.length, 1);
});

test('a rejected Android registration is refreshed once and saved', async () => {
  let attempts = 0, removed = 0;
  currentSubscription.unsubscribe = async () => { removed++; };
  registration.pushManager.getSubscription = async () => currentSubscription;
  const api = async (url, options) => {
    if (!url.endsWith('/public-key') && ++attempts === 1) return new Response(JSON.stringify({ code: 'PUSH_SUBSCRIPTION_INVALID', message: 'Old registration' }), { status: 400 });
    return apiFetch(url, options);
  };
  assert.equal(await enableBrowserPush(api, '/api', null, 7), 42);
  assert.equal(attempts, 2);
  assert.equal(removed, 1);
  assert.equal(subscribeCalls, 1);
});

test('repeated registration errors stop after one refresh and preserve the actual server message', async () => {
  let attempts = 0;
  const api = async (url, options) => {
    if (url.endsWith('/public-key')) return apiFetch(url, options);
    attempts++;
    return new Response(JSON.stringify({ code: 'PUSH_SUBSCRIPTION_INVALID', message: 'फोनवरील नोंदणी अवैध आहे.' }), { status: 400 });
  };
  await assert.rejects(enableBrowserPush(api, '/api', null, 7), error => error.status === 400 && error.message === 'फोनवरील नोंदणी अवैध आहे.');
  assert.equal(attempts, 2);
});

test('a different-account conflict is shown without replacing the existing subscription', async () => {
  let removed = 0;
  currentSubscription.unsubscribe = async () => { removed++; };
  registration.pushManager.getSubscription = async () => currentSubscription;
  const api = async (url, options) => url.endsWith('/public-key') ? apiFetch(url, options)
    : new Response(JSON.stringify({ message: 'हे डिव्हाइस दुसऱ्या खात्याशी जोडलेले आहे.' }), { status: 409 });
  await assert.rejects(enableBrowserPush(api, '/api', null, 7), error => error.status === 409 && error.message.includes('दुसऱ्या खात्याशी'));
  assert.equal(removed, 0);
  assert.equal(subscribeCalls, 0);
});

test('background push shows a tagged Android notification and opens the reminder app route', async () => {
  const events = {};
  let notification, destination, pending;
  const context = {
    URL, Date, Response, caches: {}, fetch: () => {},
    self: {
      location: { origin: 'https://example.test' },
      addEventListener: (name, handler) => { events[name] = handler; },
      registration: { showNotification: async (title, options) => { notification = { title, options }; } },
      clients: { matchAll: async () => [], openWindow: async url => { destination = url; } }
    }
  };
  vm.runInNewContext(readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8'), context);
  events.push({ data: { json: () => ({ title: 'Reminder', body: 'Water crops', tag: 'reminder-12', timestamp: 1, url: '/?tab=reminders&androidApp=1' }) }, waitUntil: promise => { pending = promise; } });
  await pending;
  assert.equal(notification.options.tag, 'reminder-12');
  assert.equal(notification.options.renotify, true);
  assert.equal(notification.options.badge, '/images/notification-badge.png');
  events.notificationclick({ notification: { close() {}, data: notification.options.data }, waitUntil: promise => { pending = promise; } });
  await pending;
  assert.equal(destination, 'https://example.test/?tab=reminders&androidApp=1');
});
