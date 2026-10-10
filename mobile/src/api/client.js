import { Platform } from "react-native";

import { pickApiBase } from "../lib/apiBase";

export const API_BASE = pickApiBase({
  configured: process.env.EXPO_PUBLIC_API_BASE_URL,
  dev: __DEV__,
  os: Platform.OS,
});

export const ADMIN_SITE = "https://admin.peachhacks.com";

const DEFAULT_TIMEOUT_MS = 15000;
// A tap that hangs holds up the line; past this it is saved and synced later.
const TAP_TIMEOUT_MS = 8000;

export class ApiError extends Error {
  constructor({ status = 0, code = "UNKNOWN", message, fieldErrors = null }) {
    super(message || "Something went wrong.");
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export const isNetworkError = (error) => error?.code === "NETWORK";

let memoryToken = null;
let unauthorizedHandler = null;

export const getToken = () => memoryToken;

export function setToken(token) {
  memoryToken = token || null;
}

export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) unauthorizedHandler = null;
  };
}

function buildUrl(path, query) {
  const pairs = Object.entries(query || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  return `${API_BASE}${path}${pairs.length ? `?${pairs.join("&")}` : ""}`;
}

async function toApiError(response) {
  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  const fallback =
    response.status === 429
      ? "Too many attempts. Wait a moment and try again."
      : response.status >= 500
        ? "The server had a problem. Try again in a moment."
        : `Request failed (${response.status}).`;
  return new ApiError({
    status: response.status,
    code: payload?.code || (response.status === 401 ? "UNAUTHORIZED" : "UNKNOWN"),
    message: payload?.message || fallback,
    fieldErrors: payload?.fieldErrors || null,
  });
}

async function send(method, path, { query, body, signal, auth = true, timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
  const headers = { Accept: "application/json", "X-PeachHacks-Client": "staff-app" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = auth ? getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  const controller = new AbortController();
  const abort = () => controller.abort();
  if (signal?.aborted) abort();
  signal?.addEventListener?.("abort", abort);
  const timer = setTimeout(abort, timeoutMs);

  let response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      signal: controller.signal,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (cause) {
    if (signal?.aborted) throw cause;
    throw new ApiError({
      code: "NETWORK",
      message: "Could not reach the PeachHacks API. Check your connection and try again.",
    });
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener?.("abort", abort);
  }

  if (!response.ok) {
    const error = await toApiError(response);
    if (auth && error.status === 401 && error.code === "UNAUTHORIZED") {
      setToken(null);
      if (unauthorizedHandler) unauthorizedHandler();
    }
    throw error;
  }
  return response;
}

async function json(method, path, options) {
  const response = await send(method, path, options);
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError({ status: response.status, message: "The server sent a response this app could not read." });
  }
}

export const api = {
  login: (email, password) => json("POST", "/admin/auth/login", { body: { email, password }, auth: false }),
  logout: () => json("POST", "/admin/auth/logout"),
  me: (signal) => json("GET", "/admin/auth/me", { signal }),

  events: (signal) => json("GET", "/admin/events", { signal }),

  checkInList: (query, signal) => json("GET", "/admin/check-in", { query, signal }),
  // Someone who is not accepted is refused with 409 `NOT_ACCEPTED`.
  checkIn: (id) => json("POST", `/admin/check-in/${encodeURIComponent(id)}`),

  // Exactly one of `code` (a scanned ticket) or `registrationId`. Records nothing.
  resolveBadge: (body) => json("POST", "/admin/badges/resolve", { body }),
  bindBadge: ({ registrationId, uid, replace = false }) =>
    json("POST", "/admin/badges/bind", { body: { registrationId, uid, replace } }),
  tapBadge: ({ uid, eventId, tappedAt }) =>
    json("POST", "/admin/badges/tap", { body: { uid, eventId, tappedAt }, timeoutMs: TAP_TIMEOUT_MS }),
  lookupBadge: (uid) => json("POST", "/admin/badges/lookup", { body: { uid } }),
};
