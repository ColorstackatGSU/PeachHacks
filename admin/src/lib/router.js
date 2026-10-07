import { useMemo, useSyncExternalStore } from "react";

function subscribe(callback) {
  window.addEventListener("hashchange", callback);
  return () => window.removeEventListener("hashchange", callback);
}

const getHash = () => window.location.hash;

function parseHash(hash) {
  const raw = hash.replace(/^#/, "") || "/";
  const [rawPath, search = ""] = raw.split("?");
  const trimmed = rawPath.replace(/\/+$/, "");
  const path = trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
  return { path: path || "/", query: new URLSearchParams(search) };
}

export function useRoute() {
  const hash = useSyncExternalStore(subscribe, getHash);
  return useMemo(() => parseHash(hash), [hash]);
}

export function navigate(to) {
  window.location.hash = to;
}

export const href = (to) => `#${to}`;
