import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { getAppEnvironment, observeIOSViewport, registerAppServiceWorker } from '../src/appEnvironment.js';

const browser = (search = '', installed = false) => ({ location: { search }, matchMedia: () => ({ matches: installed }) });
const iphone = { userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', platform: 'iPhone', maxTouchPoints: 5 };

test('iPhone home-screen launch automatically uses the app interface', () => {
  const result = getAppEnvironment(browser(), { ...iphone, standalone: true });
  assert.equal(result.isIOS, true);
  assert.equal(result.isApp, true);
  assert.equal(result.androidApp, false);
});

test('iPad desktop user agent is distinguished from a Mac', () => {
  const nav = { userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X)', platform: 'MacIntel', maxTouchPoints: 5 };
  assert.equal(getAppEnvironment(browser('', true), nav).isIOS, true);
  assert.equal(getAppEnvironment(browser(), { ...nav, maxTouchPoints: 0 }).isIOS, false);
});

test('normal desktop website stays in browser layout and the Android APK remains in app layout', () => {
  assert.equal(getAppEnvironment(browser(), { userAgent: 'Windows NT' }).isApp, false);
  assert.equal(getAppEnvironment(browser('?androidApp=1'), { userAgent: 'Android' }).androidApp, true);
});

test('manifest launch opens the app layout on Apple and Android phones', () => {
  const manifest = JSON.parse(readFileSync(new URL('../public/manifest.webmanifest', import.meta.url), 'utf8'));
  const search = new URL(manifest.start_url, 'https://example.test').search;
  assert.equal(getAppEnvironment(browser(search), iphone).isApp, true);
  assert.equal(getAppEnvironment(browser(search), { userAgent: 'Android' }).androidApp, true);
  assert.equal(manifest.id, '/');
});

test('Safari browser and Home Screen installation are detected separately', () => {
  const result = getAppEnvironment(browser('?app=1'), iphone);
  assert.equal(result.isIOS, true);
  assert.equal(result.isApp, true);
  assert.equal(result.standalone, false);
});

test('iPhone visual viewport follows the keyboard without relying on layout viewport resizing', () => {
  const viewport = Object.assign(new EventTarget(), { height: 844, offsetTop: 0 });
  const win = Object.assign(new EventTarget(), { visualViewport: viewport, innerHeight: 844 });
  const properties = new Map(), attributes = new Map();
  const root = { style: { setProperty: (key, value) => properties.set(key, value), removeProperty: key => properties.delete(key) },
    setAttribute: (key, value) => attributes.set(key, value), removeAttribute: key => attributes.delete(key) };
  const stop = observeIOSViewport(win, root);
  viewport.height = 320; viewport.offsetTop = 12;
  viewport.dispatchEvent(new Event('resize'));
  assert.equal(properties.get('--app-visible-height'), '320px');
  assert.equal(properties.get('--app-visible-top'), '12px');
  assert.equal(attributes.get('data-app-platform'), 'ios');
  stop();
  assert.equal(properties.size, 0);
  assert.equal(attributes.size, 0);
});

test('startup registers the offline worker securely without asking for notification permission', async () => {
  let registrations = 0;
  const nav = { serviceWorker: { register: async (url, options) => { registrations++; assert.equal(url, '/sw.js'); assert.equal(options.scope, '/'); return { active: true }; } } };
  assert.equal(await registerAppServiceWorker({ isSecureContext: false }, nav), null);
  assert.equal((await registerAppServiceWorker({ isSecureContext: true }, nav)).active, true);
  assert.equal(registrations, 1);
});
