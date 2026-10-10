// The card's UID is sent as the phone read it; the server does the normalising.
export const cleanUid = (value) => (typeof value === "string" ? value.trim() : "");

// Used only to compare two reads on this device.
export const uidKey = (value) => cleanUid(value).replace(/[\s:-]/g, "").toUpperCase();

// The server accepts 4, 7 or 10 byte UIDs. Anything else is not a badge.
export function looksLikeUid(value) {
  const key = uidKey(value);
  return /^[0-9A-F]+$/.test(key) && [8, 14, 20].includes(key.length);
}

// A card resting on the phone is read again and again; the same UID inside the
// window is ignored, and each ignored read keeps the window open.
// `relax(ms)` shortens the window for the card just read, for when its tap failed
// and the person has to tap again; the next card accepted restores the full window.
export function createRepeatGuard(windowMs = 2000, now = Date.now) {
  let lastKey = null;
  let lastAt = 0;
  let currentWindow = windowMs;
  const isRepeat = (uid) => {
    const key = uidKey(uid);
    const at = now();
    const repeat = key === lastKey && at - lastAt < currentWindow;
    if (!repeat) currentWindow = windowMs;
    lastKey = key;
    lastAt = at;
    return repeat;
  };
  isRepeat.relax = (ms = 0) => {
    currentWindow = Math.min(currentWindow, ms);
  };
  return isRepeat;
}
