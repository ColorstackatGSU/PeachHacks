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

export function sameEmail(first, second) {
  const a = first.trim().toLowerCase();
  return a !== '' && a === second.trim().toLowerCase();
}

export const RESUME_MAX_BYTES = 2 * 1024 * 1024;
const PDF_SIGNATURE = '%PDF-';

export function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} bytes`;
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Resolves to a message for the person, or null when the file can be uploaded.
// Phones often report no type at all, so the file's first bytes have the final say.
export async function checkResume(file) {
  const namedPdf = /\.pdf$/i.test(file.name);
  if (file.type ? file.type !== 'application/pdf' : !namedPdf) return 'Your resume needs to be a PDF file.';
  if (file.size > RESUME_MAX_BYTES) {
    return `That file is ${formatFileSize(file.size)}. Choose a PDF of 2 MB or less.`;
  }
  try {
    const head = new Uint8Array(await file.slice(0, PDF_SIGNATURE.length).arrayBuffer());
    const signature = String.fromCharCode(...head);
    if (signature !== PDF_SIGNATURE) return "That file isn't a real PDF. Export your resume as a PDF and try again.";
  } catch {
    return "We couldn't read that file. Choose it again.";
  }
  return null;
}

// FileReader encodes off the main thread, so a 2 MB file does not freeze the page.
export function readFileAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).slice(String(reader.result).indexOf(',') + 1));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
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
