import { useSyncExternalStore } from 'react';
import { prefersReducedMotion } from './reducedMotion.js';
import { REGISTER_PANEL_HASH } from './site.js';

const SCROLL_TIMEOUT_MS = 1500;
const BACK_TIMEOUT_MS = 400;

// The URL decides whether the sign-up panel is wanted: it is open exactly while
// the hash is #register, so Back closes it and a link to /#register opens it.
// `shown` lags behind the URL only while the page scrolls back up to the hero.
let shown = false;
let opener = null;
let steppingBack = false;
const listeners = new Set();

const isRequested = () => window.location.hash === REGISTER_PANEL_HASH;
const pushedEntry = () => window.history.state?.registerPanel === true;
const pathWithoutHash = () => window.location.pathname + window.location.search;

function setShown(value) {
  if (value === shown) return;
  shown = value;
  listeners.forEach((listener) => listener());
}

function heroInView() {
  if (window.scrollY < 2) return Promise.resolve();
  if (prefersReducedMotion()) {
    window.scrollTo({ top: 0, behavior: 'instant' });
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const deadline = performance.now() + SCROLL_TIMEOUT_MS;
    const check = () => {
      if (window.scrollY < 2 || performance.now() > deadline || !isRequested()) resolve();
      else requestAnimationFrame(check);
    };
    window.scrollTo({ top: 0, behavior: 'smooth' });
    requestAnimationFrame(check);
  });
}

function sync() {
  if (!isRequested()) {
    setShown(false);
    return;
  }
  if (shown) return;
  heroInView().then(() => setShown(isRequested()));
}

// Closing steps back only when the entry behind this one is known to be this
// same page without the panel: an entry pushed by openRegisterPanel, or, where
// the Navigation API can tell, one reached by a link on the page. Otherwise
// Back could leave the site, so the hash is dropped in place instead.
function pageIsBehind() {
  if (pushedEntry()) return true;
  const navigation = window.navigation;
  const previous = navigation?.entries()[navigation.currentEntry.index - 1];
  return Boolean(previous?.sameDocument) && new URL(previous.url).hash !== REGISTER_PANEL_HASH;
}

function onUrlChange() {
  steppingBack = false;
  if (isRequested() && !shown) opener = document.activeElement;
  sync();
}

// The homepage is also rendered at build time, where there is no URL to read.
if (typeof window !== 'undefined') {
  // /?register=1 is what the redirects from the old form pages land on.
  const params = new URLSearchParams(window.location.search);
  if (params.get('register') === '1') {
    params.delete('register');
    const query = params.toString();
    window.history.replaceState(null, '', `${window.location.pathname}${query ? `?${query}` : ''}${REGISTER_PANEL_HASH}`);
  }
  shown = isRequested();
  window.addEventListener('popstate', onUrlChange);
  window.addEventListener('hashchange', onUrlChange);
}
export const openedOnLoad = shown;

export function openRegisterPanel(trigger) {
  if (isRequested()) return;
  opener = trigger ?? document.activeElement;
  window.history.pushState({ registerPanel: true }, '', REGISTER_PANEL_HASH);
  sync();
}

export function closeRegisterPanel() {
  if (!isRequested() || steppingBack) return;
  const strip = () => {
    steppingBack = false;
    if (!isRequested()) return;
    window.history.replaceState(null, '', pathWithoutHash());
    sync();
  };
  if (!pageIsBehind()) {
    strip();
    return;
  }
  steppingBack = true;
  window.history.back();
  // A duplicated tab can carry the pushed marker with nothing behind it.
  setTimeout(strip, BACK_TIMEOUT_MS);
}

export const isRegisterPanelShown = () => shown;

// The element that had focus when the panel was asked for, to hand focus back to.
export const registerPanelOpener = () => opener;

function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const closedOnServer = () => false;

export function useRegisterPanelShown() {
  return useSyncExternalStore(subscribe, isRegisterPanelShown, closedOnServer);
}
