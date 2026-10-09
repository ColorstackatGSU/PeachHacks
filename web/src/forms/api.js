import { previewHeaders } from './preview.js';

const DEFAULT_BASE_URL = import.meta.env.DEV ? 'http://localhost:8080' : 'https://api.peachhacks.com';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');

const SUBMIT_TIMEOUT_MS = 30000;
// A registration can carry a 2 MB resume, which a slow phone connection needs longer to upload.
const REGISTRATION_TIMEOUT_MS = 120000;

// `code` is the API's error code, NETWORK when the request never got a response,
// or UNKNOWN when the response was not the documented error shape.
// `serverMessage` is the API's own wording, or null when it sent none.
class ApiError extends Error {
  constructor({ status = 0, code = 'UNKNOWN', message = 'Request failed', serverMessage = null, fieldErrors = {} } = {}) {
    super(serverMessage ?? message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.serverMessage = serverMessage;
    this.fieldErrors = fieldErrors;
  }
}

// A request that passes `timeoutMs` gives up after that long and fails as NETWORK;
// one that passes its own `signal` rethrows the AbortError for the caller to ignore.
async function request(path, { method = 'GET', body, signal, timeoutMs, headers } = {}) {
  const timeout = timeoutMs ? new AbortController() : null;
  const timer = timeout ? setTimeout(() => timeout.abort(), timeoutMs) : undefined;
  let response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: timeout ? timeout.signal : signal,
    });
  } catch (error) {
    if (error?.name === 'AbortError' && !timeout) throw error;
    throw new ApiError({ code: 'NETWORK', message: 'Could not reach the server' });
  } finally {
    clearTimeout(timer);
  }

  if (response.status === 204) return null;

  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }

  if (!response.ok) {
    throw new ApiError({
      status: response.status,
      code: typeof data?.code === 'string' ? data.code : 'UNKNOWN',
      message: `Request failed with status ${response.status}`,
      serverMessage: typeof data?.message === 'string' && data.message.trim() ? data.message : null,
      fieldErrors: data?.fieldErrors && typeof data.fieldErrors === 'object' ? data.fieldErrors : {},
    });
  }

  return data;
}

function submit(path, body, timeoutMs = SUBMIT_TIMEOUT_MS, headers = undefined) {
  return request(path, { method: 'POST', body, timeoutMs, headers });
}

export function submitPreRegistration(payload) {
  return submit('/public/pre-registrations', payload);
}

export function submitRegistration(payload) {
  return submit('/public/registrations', payload, REGISTRATION_TIMEOUT_MS, previewHeaders());
}

// Emailed to the organizers' inbox; resolves once it has been sent.
export function submitSponsorInquiry(payload) {
  return submit('/public/sponsor-inquiries', payload);
}

export function unsubscribe(token) {
  return submit('/public/unsubscribe', { token });
}

export function confirmSchoolEmail(token) {
  return submit('/public/school-email/confirm', { token });
}

// Resolves whether or not the email is known; the API does not say which.
export function resendSchoolEmailConfirmation(email) {
  return submit('/public/school-email/resend', { email });
}

export function getTicket(token, signal) {
  return request(`/public/tickets/${encodeURIComponent(token)}`, { signal });
}
