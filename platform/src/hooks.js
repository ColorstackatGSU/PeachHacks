import { useCallback, useEffect, useMemo, useState, useSyncExternalStore } from "react";

function subscribe(callback) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

const getHash = () => window.location.hash;

export function useRoute() {
  const hash = useSyncExternalStore(subscribe, getHash);
  return useMemo(() => {
    const [rawPath, search = ""] = (hash.replace(/^#/, "") || "/").split("?");
    const path = `/${rawPath.replace(/^\/+|\/+$/g, "")}`;
    return { path, query: new URLSearchParams(search) };
  }, [hash]);
}

export const href = (to) => `#${to}`;

export function navigate(to) {
  window.location.hash = to;
}

// Loads once and again on reload(); a refresh keeps showing the data it already has.
export function useLoad(load) {
  const [state, setState] = useState({ data: null, error: null });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal)
      .then((data) => setState({ data, error: null }))
      .catch((error) => {
        if (error?.name !== "AbortError") setState((prev) => ({ data: prev.data, error }));
      });
    return () => controller.abort();
  }, [load, version]);

  const reload = useCallback(() => setVersion((value) => value + 1), []);
  return { ...state, reload };
}
