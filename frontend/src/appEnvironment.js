export function getAppEnvironment(browserWindow = globalThis.window, browserNavigator = globalThis.navigator) {
  const nav = browserNavigator || {};
  const params = new URLSearchParams(browserWindow?.location?.search || '');
  const isIOS = /iPad|iPhone|iPod/i.test(nav.userAgent || '')
    || (nav.platform === 'MacIntel' && Number(nav.maxTouchPoints) > 1);
  const isAndroid = /Android/i.test(nav.userAgent || '');
  const standalone = Boolean(nav.standalone)
    || Boolean(browserWindow?.matchMedia?.('(display-mode: standalone)').matches);
  const isApp = standalone || ['app', 'iosApp', 'androidApp'].some(key => params.get(key) === '1');
  return { isIOS, isAndroid, standalone, isApp, androidApp: !isIOS && (params.get('androidApp') === '1' || (isAndroid && isApp)) };
}

export function observeIOSViewport(browserWindow = window, root = document.documentElement) {
  const viewport = browserWindow.visualViewport;
  const update = () => {
    root.style.setProperty('--app-visible-height', `${viewport?.height || browserWindow.innerHeight}px`);
    root.style.setProperty('--app-visible-top', `${viewport?.offsetTop || 0}px`);
  };
  root.setAttribute('data-app-platform', 'ios');
  update();
  viewport?.addEventListener('resize', update);
  viewport?.addEventListener('scroll', update);
  browserWindow.addEventListener('resize', update);
  return () => {
    viewport?.removeEventListener('resize', update);
    viewport?.removeEventListener('scroll', update);
    browserWindow.removeEventListener('resize', update);
    root.style.removeProperty('--app-visible-height');
    root.style.removeProperty('--app-visible-top');
    root.removeAttribute('data-app-platform');
  };
}

export async function registerAppServiceWorker(browserWindow = globalThis.window, browserNavigator = globalThis.navigator) {
  if (!browserWindow?.isSecureContext || !browserNavigator?.serviceWorker) return null;
  try { return await browserNavigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' }); }
  catch { return null; }
}
