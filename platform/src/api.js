export const API_BASE = (
  import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? "http://localhost:8080" : "https://api.peachhacks.com")
).replace(/\/+$/, "");

// Mock mode is dev-server only: `import.meta.env.DEV` is `false` at build time, so the
// dynamic import below is stripped from production bundles.
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

const TOKEN_KEY = "peachhacks.platform.token";
let memoryToken = null;
let signedOutHandler = null;

export function getToken() {
  if (memoryToken) return memoryToken;
  try {
    memoryToken = window.localStorage.getItem(TOKEN_KEY);
  } catch {
    memoryToken = null;
  }
  return memoryToken;
}

export function setToken(token) {
  memoryToken = token || null;
  try {
    if (token) window.localStorage.setItem(TOKEN_KEY, token);
    else window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage can be unavailable (private mode); the in-memory copy still works.
  }
}

export function onSignedOut(handler) {
  signedOutHandler = handler;
}

async function request(method, path, { body, signal, auth = true } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = auth ? getToken() : null;
  if (token) headers.Authorization = `Bearer ${token}`;

  const transport = await getTransport();
  let response;
  try {
    response = await transport(`${API_BASE}${path}`, {
      method,
      headers,
      signal,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (cause) {
    if (cause?.name === "AbortError") throw cause;
    throw new ApiError({ code: "NETWORK", message: "Could not reach PeachHacks. Check your connection and try again." });
  }

  if (!response.ok) {
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
    const error = new ApiError({
      status: response.status,
      code: payload?.code || "UNKNOWN",
      message: payload?.message || fallback,
      fieldErrors: payload?.fieldErrors || null,
    });
    if (auth && error.status === 401 && error.code === "UNAUTHORIZED") {
      setToken(null);
      if (signedOutHandler) signedOutHandler();
    }
    throw error;
  }
  if (response.status === 204) return null;
  return response.json();
}

const id = encodeURIComponent;

export const api = {
  config: (signal) => request("GET", "/platform/config", { signal, auth: false }),
  login: (email, password) => request("POST", "/platform/auth/login", { body: { email, password }, auth: false }),
  passwordLink: (email) => request("POST", "/platform/auth/password-link", { body: { email }, auth: false }),
  setPassword: (token, password) =>
    request("POST", "/platform/auth/set-password", { body: { token, password }, auth: false }),
  googleSignIn: (credential) => request("POST", "/platform/auth/google", { body: { credential }, auth: false }),
  logout: () => request("POST", "/platform/auth/logout"),

  me: (signal) => request("GET", "/platform/me", { signal }),
  saveProfile: (profile) => request("PATCH", "/platform/me", { body: profile }),
  connectDiscord: (code) => request("POST", "/platform/discord", { body: { code } }),

  hackers: (signal) => request("GET", "/platform/hackers", { signal }),
  teams: (signal) => request("GET", "/platform/teams", { signal }),
  createTeam: (team) => request("POST", "/platform/teams", { body: team }),
  updateTeam: (team) => request("PATCH", "/platform/teams/mine", { body: team }),
  leaveTeam: () => request("POST", "/platform/teams/mine/leave"),
  removeMember: (memberId) => request("DELETE", `/platform/teams/mine/members/${id(memberId)}`),
  answerRequest: (requestId, accept) =>
    request("POST", `/platform/teams/mine/requests/${id(requestId)}/${accept ? "accept" : "decline"}`),
  requestToJoin: (teamId, message) =>
    request("POST", `/platform/teams/${id(teamId)}/requests`, { body: { message: message || null } }),
  withdrawRequest: (teamId) => request("DELETE", `/platform/teams/${id(teamId)}/requests`),
};

const MOCK_QR =
  "data:image/svg+xml," +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><rect width="10" height="10" fill="#fff"/>' +
      '<path d="M1 1h3v3H1zM6 1h3v3H6zM1 6h3v3H1zM6 6h1v1H6zM8 6h1v1H8zM7 7h1v1H7zM6 8h1v1H6zM8 8h1v1H8z"/></svg>',
  );

export const qrUrl = (ticketToken) =>
  MOCK_MODE ? MOCK_QR : `${API_BASE}/public/tickets/${id(ticketToken)}/qr.png`;
