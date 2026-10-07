import { useSyncExternalStore } from 'react';
import { API_BASE_URL } from '../forms/api.js';
import { previewHeaders } from '../forms/preview.js';
import { REGISTER_PANEL_HREF } from './site.js';

const STATUS_TIMEOUT_MS = 5000;
const CACHE_KEY = 'peachhacks:registration-open';

const PRE_REGISTER_CTA = { open: false, label: 'Pre-register', href: REGISTER_PANEL_HREF };
const REGISTER_CTA = { open: true, label: 'Register', href: REGISTER_PANEL_HREF };

const readCache = () => {
  try {
    return window.sessionStorage.getItem(CACHE_KEY) === 'true';
  } catch {
    return false;
  }
};

const writeCache = (value) => {
  try {
    window.sessionStorage.setItem(CACHE_KEY, String(value));
  } catch {
    // Storage can be unavailable (private mode); the page works without it.
  }
};

// Pre-registration is the default. The last answer seen in this tab is reused
// while the request is in flight so the label does not flip on every page view;
// a failed, slow or malformed response always falls back to pre-registration.
// `status` is stricter: it only reports what the API said during this page view,
// so a form is never shown on a cached or assumed answer.
let registrationOpen = readCache();
let status = 'loading';
let requested = false;
const listeners = new Set();

function settle(next) {
  status = next;
  if (next !== 'loading') {
    registrationOpen = next === 'open';
    writeCache(registrationOpen);
  }
  listeners.forEach((listener) => listener());
}

function requestStatus() {
  if (requested) return;
  requested = true;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), STATUS_TIMEOUT_MS);
  fetch(`${API_BASE_URL}/public/status`, {
    headers: { Accept: 'application/json', ...previewHeaders() },
    signal: controller.signal,
  })
    .then((response) => (response.ok ? response.json() : null))
    .then((data) => {
      if (typeof data?.registrationOpen !== 'boolean') settle('error');
      else settle(data.registrationOpen ? 'open' : 'closed');
    })
    .catch(() => settle('error'))
    .finally(() => clearTimeout(timer));
}

function subscribe(listener) {
  listeners.add(listener);
  requestStatus();
  return () => listeners.delete(listener);
}

const getOpen = () => registrationOpen;
const getStatus = () => status;
// The pre-rendered homepage is built before anyone knows whether registration
// is open, so the first render always carries the pre-registration wording.
const closedOnServer = () => false;
const loadingOnServer = () => 'loading';

export function useRegistrationCta() {
  return useSyncExternalStore(subscribe, getOpen, closedOnServer) ? REGISTER_CTA : PRE_REGISTER_CTA;
}

// loading | open | closed | error
export function useRegistrationStatus() {
  return useSyncExternalStore(subscribe, getStatus, loadingOnServer);
}

export function retryRegistrationStatus() {
  if (status === 'loading') return;
  requested = false;
  settle('loading');
  requestStatus();
}

// The API can refuse a registration because the gate shut after the status was read.
export function markRegistrationClosed() {
  settle('closed');
}
