import { CONTACT_EMAIL } from './options.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_CHARS_PATTERN = /^[0-9+()\-.\sx#]+$/i;

export function isEmail(value) {
  return EMAIL_PATTERN.test(value.trim());
}

export function isPhone(value) {
  const digits = value.replace(/\D/g, '');
  return PHONE_CHARS_PATTERN.test(value.trim()) && digits.length >= 7 && digits.length <= 15;
}

export function normalizeLinkedinUrl(value) {
  const trimmed = value.trim();
  if (!trimmed) return '';
  try {
    const url = new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`);
    const host = url.hostname.toLowerCase();
    if (host !== 'linkedin.com' && !host.endsWith('.linkedin.com')) return null;
    url.protocol = 'https:';
    return url.toString();
  } catch {
    return null;
  }
}

export function blankToNull(value) {
  const trimmed = typeof value === 'string' ? value.trim() : value;
  return trimmed === '' ? null : trimmed;
}

export function splitFieldErrors(fieldErrors, knownFields) {
  const matched = {};
  const unmatched = [];
  for (const [field, message] of Object.entries(fieldErrors || {})) {
    if (knownFields.includes(field)) matched[field] = String(message);
    else unmatched.push(`${field}: ${message}`);
  }
  return { matched, unmatched };
}

export function describeFailure(error) {
  switch (error?.code) {
    case 'NETWORK':
      return "We couldn't reach the server. Check your connection and try again.";
    case 'RATE_LIMITED':
      return 'Too many attempts from this connection. Wait a minute, then try again.';
    case 'VALIDATION_ERROR':
      return 'Some answers need another look. Fix the highlighted fields and try again.';
    default:
      return `Something went wrong on our end. Try again in a moment, or email ${CONTACT_EMAIL}.`;
  }
}

export function focusFirstInvalid(form) {
  const target = form?.querySelector('[aria-invalid="true"]');
  if (!target) return false;
  target.focus();
  return true;
}
