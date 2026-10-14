import { useCallback, useEffect, useState } from "react";
import { AppState } from "react-native";

import { api } from "../api/client";

// The event list, fetched when asked for and again whenever the app comes back to
// the front. `version` goes up with every successful fetch and `loadedAt` is when
// it arrived, which is the "now" the happening-now rule is judged against.
export function useEvents() {
  const [state, setState] = useState({ events: null, error: null, loading: true, version: 0, loadedAt: 0 });

  const load = useCallback(async () => {
    setState((prev) => ({ ...prev, loading: true }));
    try {
      const events = await api.events();
      setState((prev) => ({
        events: Array.isArray(events) ? events : [],
        error: null,
        loading: false,
        version: prev.version + 1,
        loadedAt: Date.now(),
      }));
    } catch (error) {
      if (error?.status !== 401) setState((prev) => ({ ...prev, error, loading: false }));
    }
  }, []);

  useEffect(() => {
    let live = true;
    api.events().then(
      (events) =>
        live &&
        setState((prev) => ({
          events: Array.isArray(events) ? events : [],
          error: null,
          loading: false,
          version: prev.version + 1,
          loadedAt: Date.now(),
        })),
      (error) => live && error?.status !== 401 && setState((prev) => ({ ...prev, error, loading: false })),
    );
    const subscription = AppState.addEventListener("change", (next) => {
      if (next === "active") load();
    });
    return () => {
      live = false;
      subscription.remove();
    };
  }, [load]);

  return { ...state, reload: load };
}
