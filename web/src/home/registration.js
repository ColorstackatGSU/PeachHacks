import { useSyncExternalStore } from 'react';

const DEFAULT_API_BASE = import.meta.env.DEV ? 'http://localhost:8080' : 'https://api.peachhacks.com';
const API_BASE = (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE).replace(/\/+$/, '');
const STATUS_TIMEOUT_MS = 5000;
const CACHE_KEY = 'peachhacks:registration-open';

const PRE_REGISTER_CTA = { open: false, label: 'Pre-register', href: '/pre-register' };
const REGISTER_CTA = { open: true, label: 'Register', href: '/register' };

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
let registrationOpen = readCache();
let requested = false;
const listeners = new Set();

function setRegistrationOpen(value) {
  writeCache(value);
  if (value === registrationOpen) return;
  registrationOpen = value;
  listeners.forEach((listener) => listener());
}

function requestStatus() {
  if (requested) return;
  requested = true;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), STATUS_TIMEOUT_MS);
  fetch(`${API_BASE}/public/status`, { headers: { Accept: 'application/json' }, signal: controller.signal })
    .then((response) => (response.ok ? response.json() : null))
    .then((data) => setRegistrationOpen(data?.registrationOpen === true))
    .catch(() => setRegistrationOpen(false))
    .finally(() => clearTimeout(timer));
}

function subscribe(listener) {
  listeners.add(listener);
  requestStatus();
  return () => listeners.delete(listener);
}

const getSnapshot = () => registrationOpen;

export function useRegistrationCta() {
  return useSyncExternalStore(subscribe, getSnapshot) ? REGISTER_CTA : PRE_REGISTER_CTA;
}
