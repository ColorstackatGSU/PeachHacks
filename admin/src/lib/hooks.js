import { useCallback, useEffect, useState } from "react";
import { api, onAcceptanceChange } from "../api/client.js";
import { useToast } from "../components/ui.jsx";
import { errorText } from "./format.js";

// Refetches when `fn` changes identity, so wrap it in useCallback. Stale data stays
// visible during a refresh; aborted or superseded requests never touch state.
export function useAsync(fn) {
  const [nonce, setNonce] = useState(0);
  const [state, setState] = useState({ src: null, nonce: -1, data: null, error: null });

  useEffect(() => {
    const controller = new AbortController();
    let live = true;
    fn(controller.signal).then(
      (data) => {
        if (live) setState({ src: fn, nonce, data, error: null });
      },
      (error) => {
        if (!live || error?.name === "AbortError") return;
        setState((prev) => ({ src: fn, nonce, data: prev.src === fn ? prev.data : null, error }));
      },
    );
    return () => {
      live = false;
      controller.abort();
    };
  }, [fn, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  const loading = state.src !== fn || state.nonce !== nonce;
  // Data fetched for a different query is still shown (dimmed) until the new
  // page arrives, but an old error is not.
  return { data: state.data, error: loading ? null : state.error, loading, reload };
}

export function useDebounced(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

// `exporting` is the `key` of the download in flight, or null.
export function useExport() {
  const notify = useToast();
  const [exporting, setExporting] = useState(null);
  const run = async (download, key = true) => {
    setExporting(key);
    try {
      await download();
    } catch (error) {
      notify(`Export failed: ${errorText(error)}`, "error");
    } finally {
      setExporting(null);
    }
  };
  return [exporting, run];
}

const loadEvents = (signal) => api.events(signal);

export function useEvents() {
  return useAsync(loadEvents);
}

const loadStats = (signal) => api.stats(signal);

export function useStats() {
  return useAsync(loadStats);
}

const loadAcceptanceSummary = (signal) => api.acceptanceSummary(signal);

// Refetches after every change made from this browser, every few seconds while a send
// is running, and slowly otherwise so another organizer's changes show up too.
export function useAcceptanceSummary() {
  const result = useAsync(loadAcceptanceSummary);
  const reload = result.reload;
  const sending = result.data?.send?.state === "SENDING";

  useEffect(() => onAcceptanceChange(reload), [reload]);
  useEffect(() => {
    const timer = window.setInterval(
      () => {
        if (!document.hidden) reload();
      },
      sending ? 2000 : 15000,
    );
    return () => window.clearInterval(timer);
  }, [sending, reload]);

  return result;
}

// School names for filter dropdowns, taken from the stats breakdown so the
// values match what the list endpoints expect for an exact `school` match.
export function schoolOptions(stats, kind) {
  const rows = stats?.[kind]?.bySchool || [];
  return rows
    .map((row) => row.school)
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b));
}
