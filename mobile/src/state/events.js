import { useCallback, useEffect, useState } from "react";
import { AppState } from "react-native";

import { api } from "../api/client";

// The event list, fetched when asked for and again whenever the app comes back to
// the front. `version` goes up with every successful fetch and `loadedAt` is when
// it arrived, which is the "now" the happening-now rule is judged against.
// `load` is the call that fetches the list: `api.events` for the event jobs, or
// `api.attendanceEvents`, which every role may call. Without one nothing is fetched.
export function useEvents(load = api.events) {
  const [state, setState] = useState({ events: null, error: null, loading: true, version: 0, loadedAt: 0 });

  const reload = useCallback(async () => {
    if (!load) return;
    setState((prev) => ({ ...prev, loading: true }));
    try {
      const events = await load();
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
  }, [load]);

  useEffect(() => {
    if (!load) return undefined;
    let live = true;
    load().then(
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
      if (next === "active") reload();
    });
    return () => {
      live = false;
      subscription.remove();
    };
  }, [load, reload]);

  return { ...state, reload };
}
