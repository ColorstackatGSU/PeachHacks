const DEFAULT_BASE_URL = import.meta.env.DEV ? 'http://localhost:8080' : 'https://api.peachhacks.com';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || DEFAULT_BASE_URL).replace(/\/+$/, '');

// `code` is the API's error code, NETWORK when the request never got a response,
// or UNKNOWN when the response was not the documented error shape.
export class ApiError extends Error {
  constructor({ status = 0, code = 'UNKNOWN', message = 'Request failed', fieldErrors = {} } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

async function request(path, { method = 'GET', body, signal } = {}) {
  let response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error?.name === 'AbortError') throw error;
    throw new ApiError({ code: 'NETWORK', message: 'Could not reach the server' });
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
      message: typeof data?.message === 'string' ? data.message : `Request failed with status ${response.status}`,
      fieldErrors: data?.fieldErrors && typeof data.fieldErrors === 'object' ? data.fieldErrors : {},
    });
  }

  return data;
}

export function submitPreRegistration(payload) {
  return request('/public/pre-registrations', { method: 'POST', body: payload });
}

export function submitRegistration(payload) {
  return request('/public/registrations', { method: 'POST', body: payload });
}

export function unsubscribe(token) {
  return request('/public/unsubscribe', { method: 'POST', body: { token } });
}

export function confirmSchoolEmail(token) {
  return request('/public/school-email/confirm', { method: 'POST', body: { token } });
}

// Resolves whether or not the email is known; the API does not say which.
export function resendSchoolEmailConfirmation(email) {
  return request('/public/school-email/resend', { method: 'POST', body: { email } });
}

export function getTicket(token, signal) {
  return request(`/public/tickets/${encodeURIComponent(token)}`, { signal });
}
