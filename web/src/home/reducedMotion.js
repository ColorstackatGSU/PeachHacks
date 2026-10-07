import { useSyncExternalStore } from 'react';

const STORAGE_KEY = 'peachhacks:motion';
const PAUSED = 'paused';
const QUERY = '(prefers-reduced-motion: reduce)';
const onServer = typeof window === 'undefined';
const listeners = new Set();

const readChoice = () => {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === PAUSED;
  } catch {
    return false;
  }
};

// styles.css applies its reduced-motion rules to html[data-motion="paused"] too.
const apply = (value) => {
  if (value) document.documentElement.dataset.motion = PAUSED;
  else delete document.documentElement.dataset.motion;
};

let paused = !onServer && readChoice();
if (paused) apply(true);

export const isMotionPaused = () => paused;

export const prefersReducedMotion = () => paused || window.matchMedia(QUERY).matches;

export function setMotionPaused(value) {
  if (value === paused) return;
  paused = value;
  apply(value);
  try {
    if (value) window.localStorage.setItem(STORAGE_KEY, PAUSED);
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage can be unavailable (private mode); the choice then lasts for this page view.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener) {
  const query = window.matchMedia(QUERY);
  listeners.add(listener);
  query.addEventListener('change', listener);
  return () => {
    listeners.delete(listener);
    query.removeEventListener('change', listener);
  };
}

const notOnServer = () => false;

export function useMotionPaused() {
  return useSyncExternalStore(subscribe, isMotionPaused, notOnServer);
}

export function useReducedMotion() {
  return useSyncExternalStore(subscribe, prefersReducedMotion, notOnServer);
}
