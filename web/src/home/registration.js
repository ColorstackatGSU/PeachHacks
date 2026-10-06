import { useSyncExternalStore } from 'react';
import { REGISTER_PANEL_HREF } from './site.js';

const DEFAULT_API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : 'https://api.peachhacks.com';
const API_BASE = (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE).replace(/\/+$/, '');
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
  fetch(`${API_BASE}/public/status`, { headers: { Accept: 'application/json' }, signal: controller.signal })
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

export function useRegistrationCta() {
  return useSyncExternalStore(subscribe, getOpen) ? REGISTER_CTA : PRE_REGISTER_CTA;
}

// loading | open | closed | error
export function useRegistrationStatus() {
  return useSyncExternalStore(subscribe, getStatus);
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
