const API_BASE = (
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

class ApiError extends Error {
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
  // The filename* form carries non-ASCII names (a resume's own file name) intact.
  const match = /filename\*=UTF-8''([^";]+)/i.exec(header) || /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  if (!match) return fallback;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}

// Exports and resumes need the bearer header, so they are fetched and saved from a blob.
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

// Screens that show acceptance numbers subscribe here, and every call that can move
// those numbers reports through `changing`, so they refresh wherever the change was made.
const acceptanceListeners = new Set();

export function onAcceptanceChange(listener) {
  acceptanceListeners.add(listener);
  return () => acceptanceListeners.delete(listener);
}

async function changing(request) {
  const result = await request;
  acceptanceListeners.forEach((listener) => listener());
  return result;
}

export const ticketQrUrl = (token) => `${API_BASE}/public/tickets/${encodeURIComponent(token)}/qr.png`;

export const api = {
  login: (email, password) =>
    json("POST", "/admin/auth/login", { body: { email, password }, auth: false }),
  logout: () => json("POST", "/admin/auth/logout"),
  me: (signal) => json("GET", "/admin/auth/me", { signal }),
  forgotPassword: (email) => json("POST", "/admin/auth/forgot-password", { body: { email }, auth: false }),
  checkPasswordLink: (token, signal) =>
    json("POST", "/admin/auth/set-password/check", { body: { token }, signal, auth: false }),
  setPassword: (token, password) =>
    json("POST", "/admin/auth/set-password", { body: { token, password }, auth: false }),
  changePassword: (currentPassword, newPassword) =>
    json("POST", "/admin/auth/change-password", { body: { currentPassword, newPassword } }),

  stats: (signal) => json("GET", "/admin/stats", { signal }),

  preRegistrations: (query, signal) => json("GET", "/admin/pre-registrations", { query, signal }),
  exportPreRegistrations: (query) =>
    download("/admin/pre-registrations/export.csv", query, `peachhacks-pre-registrations-${stamp()}.csv`),
  resendPreRegistrationSchoolEmail: (id) =>
    json("POST", `/admin/pre-registrations/${encodeURIComponent(id)}/school-email/resend`),

  registrations: (query, signal) => json("GET", "/admin/registrations", { query, signal }),
  registration: (id, signal) => json("GET", `/admin/registrations/${encodeURIComponent(id)}`, { signal }),
  setRegistrationStatus: (id, status) =>
    changing(json("PATCH", `/admin/registrations/${encodeURIComponent(id)}`, { body: { status } })),
  setRegistrationStatuses: (ids, status) =>
    changing(json("POST", "/admin/registrations/status", { body: { ids, status } })),
  exportRegistrations: (query) =>
    download("/admin/registrations/export.csv", query, `peachhacks-registrations-${stamp()}.csv`),
  // Tells someone still in the acceptance bucket now; resends for someone already told.
  sendTicketEmail: (id) => changing(json("POST", `/admin/registrations/${encodeURIComponent(id)}/ticket-email`)),

  acceptanceSummary: (signal) => json("GET", "/admin/acceptances/summary", { signal }),
  acceptancesWaiting: (signal) => json("GET", "/admin/acceptances/waiting", { signal }),
  sendAcceptanceEmails: () => changing(json("POST", "/admin/acceptances/send")),
  resendSchoolEmail: (id) => json("POST", `/admin/registrations/${encodeURIComponent(id)}/school-email/resend`),
  downloadResume: (id) => download(`/admin/registrations/${encodeURIComponent(id)}/resume`, null, "resume.pdf"),
  deleteResume: (id) => json("DELETE", `/admin/registrations/${encodeURIComponent(id)}/resume`),
  // The resume book holds accepted registrants who uploaded a resume, so the same filters
  // on the list endpoint give the number of resumes it will contain.
  resumeBookCount: async (attendedOnly, signal) => {
    const query = { resume: "any", status: "ACCEPTED", checkedIn: attendedOnly ? "true" : "", size: 1 };
    const result = await json("GET", "/admin/registrations", { query, signal });
    return result?.total ?? 0;
  },
  exportResumeBook: (attendedOnly) =>
    download("/admin/resumes/export.zip", { checkedIn: attendedOnly ? "true" : "" }, `peachhacks-resume-book-${stamp()}.zip`),

  events: (signal) => json("GET", "/admin/events", { signal }),
  createEvent: ({ name, startsAt }) => json("POST", "/admin/events", { body: { name, startsAt: startsAt || null } }),
  updateEvent: (id, { name, startsAt }) =>
    json("PATCH", `/admin/events/${encodeURIComponent(id)}`, { body: { name, startsAt: startsAt || null } }),
  deleteEvent: (id) => json("DELETE", `/admin/events/${encodeURIComponent(id)}`),
  exportEventAttendees: (id) =>
    download(`/admin/events/${encodeURIComponent(id)}/export.csv`, null, `peachhacks-attendees-${stamp()}.csv`),

  checkInList: (query, signal) => json("GET", "/admin/check-in", { query, signal }),
  // Someone who is not accepted is refused with 409 `NOT_ACCEPTED`.
  checkIn: (id, eventId) => changing(json("POST", `/admin/check-in/${encodeURIComponent(id)}`, { query: { eventId } })),
  undoCheckIn: (id, eventId) =>
    changing(json("DELETE", `/admin/check-in/${encodeURIComponent(id)}`, { query: { eventId } })),
  scanTicket: ({ code, eventId }) => json("POST", "/admin/check-in/scan", { body: { code, eventId: eventId || null } }),

  settings: (signal) => json("GET", "/admin/settings", { signal }),
  saveSettings: (registrationOpen) => json("PUT", "/admin/settings", { body: { registrationOpen } }),
  // The link is only in this answer; making another one stops the previous link working.
  createPreviewLink: () => json("POST", "/admin/settings/registration-preview"),
  endPreview: () => json("DELETE", "/admin/settings/registration-preview"),

  discord: (signal) => json("GET", "/admin/discord", { signal }),
  // Saves the text and posts it, or edits the message that is already up.
  postDiscordRecap: () => json("POST", "/admin/discord/recap"),
  publishDiscordVerification: (message) => json("POST", "/admin/discord/verification-message", { body: { message } }),

  recipientCount: (kind, audience, school, signal) =>
    json("POST", "/admin/emails/recipient-count", { body: { kind, audience, school: school || null }, signal }),
  // The draft as the server would render it for the signed-in admin; nothing is sent.
  emailPreview: (kind, subject, body, signal) =>
    json("POST", "/admin/emails/preview", { body: { kind, subject, body }, signal }),
  sendTestEmail: (kind, subject, body) => json("POST", "/admin/emails/test", { body: { kind, subject, body } }),
  sendEmail: ({ kind, audience, school, subject, body }) =>
    json("POST", "/admin/emails", { body: { kind, audience, school: school || null, subject, body } }),
  campaigns: (signal) => json("GET", "/admin/emails", { signal }),
  campaignRecipients: (id, query, signal) =>
    json("GET", `/admin/emails/${encodeURIComponent(id)}/recipients`, { query, signal }),

  admins: (signal) => json("GET", "/admin/admins", { signal }),
  // Both answer with `setPasswordUrl`, the link the invite email carries.
  createAdmin: ({ name, email, role }) => json("POST", "/admin/admins", { body: { name, email, role } }),
  resendInvite: (id) => json("POST", `/admin/admins/${encodeURIComponent(id)}/invite`),
  deleteAdmin: (id) => json("DELETE", `/admin/admins/${encodeURIComponent(id)}`),
};
