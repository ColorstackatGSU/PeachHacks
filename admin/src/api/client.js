export const API_BASE = (
  import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? "http://localhost:8080" : "https://api.peachhacks.com")
).replace(/\/+$/, "");

export const WEB_BASE = (import.meta.env.VITE_WEB_BASE_URL || "https://www.peachhacks.com").replace(/\/+$/, "");

// Mock mode is dev-server only: `import.meta.env.DEV` is `false` at build time, so this
// flag and the dynamic import below are stripped from production bundles.
export const MOCK_MODE = import.meta.env.DEV && import.meta.env.VITE_MOCK_API === "1";

let transportReady = null;
function getTransport() {
  if (!transportReady) {
    if (import.meta.env.DEV && import.meta.env.VITE_MOCK_API === "1") {
      transportReady = import("./mock.js").then((mod) => mod.mockFetch);
    } else {
      transportReady = Promise.resolve((url, init) => fetch(url, init));
    }
  }
  return transportReady;
}

export class ApiError extends Error {
  constructor({ status = 0, code = "UNKNOWN", message, fieldErrors = null }) {
    super(message || "Something went wrong.");
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

const TOKEN_KEY = "peachhacks.admin.token";
let memoryToken = null;
let unauthorizedHandler = null;

export function getToken() {
  if (memoryToken) return memoryToken;
  try {
    memoryToken = window.sessionStorage.getItem(TOKEN_KEY);
  } catch {
    memoryToken = null;
  }
  return memoryToken;
}

export function setToken(token) {
  memoryToken = token || null;
  try {
    if (token) window.sessionStorage.setItem(TOKEN_KEY, token);
    else window.sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage can be unavailable (private mode); the in-memory copy still works.
  }
}

export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) unauthorizedHandler = null;
  };
}

function buildUrl(path, query) {
  const params = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") params.set(key, String(value));
  });
  const qs = params.toString();
  return `${API_BASE}${path}${qs ? `?${qs}` : ""}`;
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

async function send(method, path, { query, body, signal, auth = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = auth ? getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  const transport = await getTransport();
  let response;
  try {
    response = await transport(buildUrl(path, query), {
      method,
      headers,
      signal,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (cause) {
    if (cause?.name === "AbortError") throw cause;
    throw new ApiError({
      code: "NETWORK",
      message: "Could not reach the PeachHacks API. Check your connection and try again.",
    });
  }

  if (!response.ok) {
    const error = await toApiError(response);
    if (error.status === 401 && error.code === "UNAUTHORIZED") {
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
    throw new ApiError({ status: response.status, message: "The server sent a response this page could not read." });
  }
}

function filenameFrom(response, fallback) {
  const header = response.headers.get("Content-Disposition") || "";
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  if (!match) return fallback;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}

// CSV exports need the bearer header, so they are fetched and saved from a blob.
async function download(path, query, fallbackName) {
  const response = await send("GET", path, { query });
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filenameFrom(response, fallbackName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function stamp() {
  return new Date().toISOString().slice(0, 10);
}

export const ticketQrUrl = (token) => `${API_BASE}/public/tickets/${encodeURIComponent(token)}/qr.png`;

export const api = {
  login: (email, password) =>
    json("POST", "/admin/auth/login", { body: { email, password }, auth: false }),
  logout: () => json("POST", "/admin/auth/logout"),
  me: (signal) => json("GET", "/admin/auth/me", { signal }),

  stats: (signal) => json("GET", "/admin/stats", { signal }),

  preRegistrations: (query, signal) => json("GET", "/admin/pre-registrations", { query, signal }),
  exportPreRegistrations: (query) =>
    download("/admin/pre-registrations/export.csv", query, `peachhacks-pre-registrations-${stamp()}.csv`),
  deletePreRegistration: (id) => json("DELETE", `/admin/pre-registrations/${encodeURIComponent(id)}`),

  registrations: (query, signal) => json("GET", "/admin/registrations", { query, signal }),
  registration: (id, signal) => json("GET", `/admin/registrations/${encodeURIComponent(id)}`, { signal }),
  setRegistrationStatus: (id, status) =>
    json("PATCH", `/admin/registrations/${encodeURIComponent(id)}`, { body: { status } }),
  exportRegistrations: (query) =>
    download("/admin/registrations/export.csv", query, `peachhacks-registrations-${stamp()}.csv`),
  deleteRegistration: (id) => json("DELETE", `/admin/registrations/${encodeURIComponent(id)}`),
  resendTicketEmail: (id) => json("POST", `/admin/registrations/${encodeURIComponent(id)}/ticket-email`),

  events: (signal) => json("GET", "/admin/events", { signal }),
  createEvent: ({ name, startsAt }) => json("POST", "/admin/events", { body: { name, startsAt: startsAt || null } }),
  updateEvent: (id, { name, startsAt }) =>
    json("PATCH", `/admin/events/${encodeURIComponent(id)}`, { body: { name, startsAt: startsAt || null } }),
  deleteEvent: (id) => json("DELETE", `/admin/events/${encodeURIComponent(id)}`),
  exportEventAttendees: (id) =>
    download(`/admin/events/${encodeURIComponent(id)}/export.csv`, null, `peachhacks-attendees-${stamp()}.csv`),

  checkInList: (query, signal) => json("GET", "/admin/check-in", { query, signal }),
  checkIn: (id, eventId) => json("POST", `/admin/check-in/${encodeURIComponent(id)}`, { query: { eventId } }),
  undoCheckIn: (id, eventId) => json("DELETE", `/admin/check-in/${encodeURIComponent(id)}`, { query: { eventId } }),
  scanTicket: ({ code, eventId, override = false }) =>
    json("POST", "/admin/check-in/scan", { body: { code, eventId: eventId || null, override } }),

  settings: (signal) => json("GET", "/admin/settings", { signal }),
  saveSettings: (registrationOpen) => json("PUT", "/admin/settings", { body: { registrationOpen } }),

  recipientCount: (audience, school, signal) =>
    json("POST", "/admin/emails/recipient-count", { body: { audience, school: school || null }, signal }),
  sendTestEmail: (subject, body) => json("POST", "/admin/emails/test", { body: { subject, body } }),
  sendEmail: ({ audience, school, subject, body }) =>
    json("POST", "/admin/emails", { body: { audience, school: school || null, subject, body } }),
  campaigns: (signal) => json("GET", "/admin/emails", { signal }),

  admins: (signal) => json("GET", "/admin/admins", { signal }),
  createAdmin: ({ name, email, password, role }) =>
    json("POST", "/admin/admins", { body: { name, email, password, role } }),
  deleteAdmin: (id) => json("DELETE", `/admin/admins/${encodeURIComponent(id)}`),
};
