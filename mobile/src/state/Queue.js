import NetInfo from "@react-native-community/netinfo";
import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { AppState } from "react-native";

import { api, getToken } from "../api/client";
import { readJson, writeJson } from "../lib/storage";
import { createTapQueue } from "../lib/tapQueue";

const QUEUE_KEY = "peachhacks.staff.tapQueue";

const queue = createTapQueue({
  storage: { load: () => readJson(QUEUE_KEY), save: (value) => writeJson(QUEUE_KEY, value) },
  send: api.tapBadge,
  canSend: () => Boolean(getToken()),
});

const isOnline = (state) => state.isConnected !== false && state.isInternetReachable !== false;

const QueueContext = createContext(null);
const OnlineContext = createContext(true);

// `active` is true while someone who may record taps is signed in. The queue itself
// is loaded and kept regardless, so nothing is lost across sign-outs or restarts.
export function QueueProvider({ active, children }) {
  const [snapshot, setSnapshot] = useState(queue.getState);
  const [online, setOnline] = useState(true);

  useEffect(() => {
    const off = queue.subscribe(setSnapshot);
    queue.load();
    return off;
  }, []);

  useEffect(() => NetInfo.addEventListener((state) => setOnline(isOnline(state))), []);

  useEffect(() => {
    if (!active) {
      queue.stop();
      return undefined;
    }
    if (online) queue.sync();
    const subscription = AppState.addEventListener("change", (next) => {
      if (next === "active") queue.sync();
    });
    return () => subscription.remove();
  }, [active, online]);

  const value = useMemo(() => ({ ...snapshot, queue }), [snapshot]);
  return (
    <OnlineContext.Provider value={online}>
      <QueueContext.Provider value={value}>{children}</QueueContext.Provider>
    </OnlineContext.Provider>
  );
}

export const useQueue = () => useContext(QueueContext);
export const useOnline = () => useContext(OnlineContext);
