import { CONTACT_EMAIL } from '../home/site.js';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMAIL_LOCAL_MAX = 64;
const PHONE_CHARS_PATTERN = /^[0-9+()\-.\sx#]+$/i;
// The same characters the API accepts (Patterns.NAME in the backend).
const NAME_PATTERN = /^[\p{L}\p{M} .'’-]+$/u;
const NAME_MESSAGE = 'Use letters, spaces, apostrophes, periods and hyphens only';
const LINKEDIN_MAX_LENGTH = 255;

export function isEmail(value) {
  const email = value.trim();
  if (!EMAIL_PATTERN.test(email)) return false;
  const at = email.indexOf('@');
  return at <= EMAIL_LOCAL_MAX && !email.slice(at + 1).includes('..');
}

export function isPhone(value) {
  const digits = value.replace(/\D/g, '');
  return PHONE_CHARS_PATTERN.test(value.trim()) && digits.length >= 7 && digits.length <= 15;
}

// '' for a blank value, null for anything that is not a link on an accepted host, and
// otherwise the link as https.
function normalizeProfileUrl(value, acceptsHost) {
  const trimmed = value.trim();
  if (!trimmed) return '';
  try {
    const url = new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`);
    if (!acceptsHost(url.hostname.toLowerCase())) return null;
    url.protocol = 'https:';
    return url.toString();
  } catch {
    return null;
  }
}

export function normalizeLinkedinUrl(value) {
  return normalizeProfileUrl(value, (host) => host === 'linkedin.com' || host.endsWith('.linkedin.com'));
}

export function normalizeGithubUrl(value) {
  return normalizeProfileUrl(value, (host) => host === 'github.com' || host === 'www.github.com');
}

export function sameEmail(first, second) {
  const a = first.trim().toLowerCase();
  return a !== '' && a === second.trim().toLowerCase();
}

const RESUME_MAX_BYTES = 2 * 1024 * 1024;
const PDF_SIGNATURE = '%PDF-';

export function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} bytes`;
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Resolves to a message for the person, or null when the file can be uploaded.
// Browsers report no type, or the wrong one, for some PDFs, so the file's first bytes have the final say.
export async function checkResume(file) {
  const namedPdf = /\.pdf$/i.test(file.name);
  if (file.type !== 'application/pdf' && !namedPdf) return 'Your resume needs to be a PDF file.';
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

function splitFieldErrors(fieldErrors, knownFields) {
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
    // The API words these itself: how long to wait depends on which limit was hit.
    case 'RATE_LIMITED':
      return error.serverMessage ?? 'Too many attempts from this connection. Wait a few minutes, then try again.';
    case 'PAYLOAD_TOO_LARGE':
      return error.serverMessage ?? 'The request is too large.';
    case 'VALIDATION_ERROR':
      return 'Some answers need another look. Fix the highlighted fields and try again.';
    default:
      return `Something went wrong on our end. Try again in a moment, or email ${CONTACT_EMAIL}.`;
  }
}

// The arguments for a form's fail(): the API's errors for the fields the form
// has, and a message that spells out any others.
export function apiFailure(error, knownFields) {
  const { matched, unmatched } = splitFieldErrors(error?.fieldErrors, knownFields);
  return [matched, [describeFailure(error), ...unmatched].join(' ')];
}

export function anotherLookMessage(errorCount) {
  return errorCount === 1 ? 'One field needs another look.' : `${errorCount} fields need another look.`;
}

const nameRule = (missing) => (value) => {
  if (!value.trim()) return missing;
  return NAME_PATTERN.test(value.trim()) ? null : NAME_MESSAGE;
};

// One rule per field: the message for what was typed, or null when it is fine.
// A form applies a field's rule when the field loses focus (only once
// something has been typed) and applies them all on submit.
export const FIELD_RULES = {
  firstName: nameRule('Enter your first name.'),
  lastName: nameRule('Enter your last name.'),
  email: (value) => {
    if (!value.trim()) return 'Enter your email address.';
    return isEmail(value) ? null : 'Enter a valid email, like name@example.com.';
  },
  schoolEmail: (value) => {
    if (!value.trim()) return 'Enter your school email address.';
    return isEmail(value) ? null : 'Enter a valid email, like name@school.edu.';
  },
  phone: (value) => {
    if (!value.trim()) return 'Enter your phone number.';
    return isPhone(value) ? null : 'Enter a valid phone number, with area code.';
  },
  linkedinUrl: (value) => {
    const url = normalizeLinkedinUrl(value);
    if (url === null) return 'Enter a LinkedIn link, like linkedin.com/in/yourname, or leave this blank.';
    return url.length > LINKEDIN_MAX_LENGTH ? 'That link is too long. Use your profile link, like linkedin.com/in/yourname.' : null;
  },
  githubUrl: (value) => {
    const url = normalizeGithubUrl(value);
    if (url === null) return 'Enter a GitHub link, like github.com/yourname, or leave this blank.';
    return url.length > LINKEDIN_MAX_LENGTH ? 'That link is too long. Use your profile link, like github.com/yourname.' : null;
  },
};

export function checkFields(values, names) {
  const errors = {};
  for (const name of names) {
    const problem = FIELD_RULES[name](values[name]);
    if (problem) errors[name] = problem;
  }
  return errors;
}

export function validateIdentity(values) {
  const errors = checkFields(values, ['firstName', 'lastName', 'email', 'schoolEmail']);
  if (!values.school.trim()) errors.school = 'Choose your school.';
  return errors;
}

export function schoolEmailHint(values) {
  return sameEmail(values.schoolEmail, values.email)
    ? "Same as your personal email. That's fine if it's the only one you use."
    : 'The address your school gave you.';
}

export function focusFirstInvalid(form) {
  const target = form?.querySelector('[aria-invalid="true"]');
  if (!target) return false;
  target.focus();
  return true;
}
