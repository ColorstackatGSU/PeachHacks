const numberFormat = new Intl.NumberFormat("en-US");
const dateTimeFormat = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
});
const timeFormat = new Intl.DateTimeFormat("en-US", { hour: "numeric", minute: "2-digit" });
const dateFormat = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });
const dayFormat = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", timeZone: "UTC" });

export const formatNumber = (value) => (typeof value === "number" ? numberFormat.format(value) : "0");

function toDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

// API timestamps are UTC; these render in the organizer's local time zone.
export function formatDateTime(value) {
  const date = toDate(value);
  return date ? dateTimeFormat.format(date) : "";
}

export function formatTime(value) {
  const date = toDate(value);
  return date ? timeFormat.format(date) : "";
}

// Check-in times: just the clock time on the day itself, the full date afterwards.
export function formatWhen(value) {
  const date = toDate(value);
  if (!date) return "";
  return date.toDateString() === new Date().toDateString() ? timeFormat.format(date) : dateTimeFormat.format(date);
}

export function formatDate(value) {
  const date = toDate(value);
  return date ? dateFormat.format(date) : "";
}

// For `byDay` buckets ("2026-10-05"), which are calendar days, not instants.
export function formatDay(isoDay) {
  const date = toDate(`${isoDay}T00:00:00Z`);
  return date ? dayFormat.format(date) : isoDay;
}

export function formatBytes(bytes) {
  if (typeof bytes !== "number") return "";
  if (bytes < 1024) return `${bytes} bytes`;
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const fullName = (person) => [person?.firstName, person?.lastName].filter(Boolean).join(" ") || "(no name)";

export const plural = (count, one, many = `${one}s`) => `${formatNumber(count)} ${count === 1 ? one : many}`;

export const errorText = (error) => error?.message || "Something went wrong.";

// An event update is always delivered, so it may only go to people who registered.
export const CAMPAIGN_KINDS = [
  {
    value: "EVENT_UPDATE",
    label: "Event update",
    hint: "Logistics for people who are coming. Always delivered, cannot be unsubscribed from. Use it only for information they need.",
    footer: "You are receiving this because you registered for PeachHacks.",
    unsubscribe: false,
  },
  {
    value: "ANNOUNCEMENT",
    label: "Announcement",
    hint: "News and promotion. People who unsubscribed are skipped.",
    footer: "You are receiving this because you signed up for PeachHacks.",
    unsubscribe: true,
  },
];

export const campaignKind = (value) => CAMPAIGN_KINDS.find((kind) => kind.value === value) || CAMPAIGN_KINDS[1];

export const AUDIENCES = [
  {
    value: "PRE_REGISTRANTS",
    label: "All pre-registrants",
    hint: "Everyone who pre-registered, whether or not they have registered since.",
    schoolsFrom: "preRegistrations",
  },
  {
    value: "PRE_REGISTRANTS_NOT_REGISTERED",
    label: "Pre-registered, not yet registered",
    hint: "People who pre-registered but have not completed the full registration.",
    schoolsFrom: "preRegistrations",
  },
  {
    value: "REGISTRANTS",
    label: "Registrants",
    hint: "Everyone who completed the full registration.",
    schoolsFrom: "registrations",
    registered: true,
  },
  {
    value: "ACCEPTED",
    label: "Accepted hackers",
    hint: "People who are accepted and have already been sent their acceptance email.",
    schoolsFrom: "registrations",
    registered: true,
  },
];

export const audiencesFor = (kind) => AUDIENCES.filter((a) => kind !== "EVENT_UPDATE" || a.registered);

export const audienceLabel = (value) => AUDIENCES.find((a) => a.value === value)?.label || value;

export const ROLES = [
  { value: "ADMIN", label: "Admin", option: "Admin (full access)", hint: "Can see and change everything on this site, including registrations, email and accounts." },
  { value: "VOLUNTEER", label: "Volunteer", option: "Volunteer (check-in only)", hint: "Can only open the Check-in screen to scan tickets and check people in." },
];

export const roleLabel = (value) => ROLES.find((role) => role.value === value)?.label || "Admin";

export const isVolunteer = (account) => account?.role === "VOLUNTEER";

export const STATUSES = ["PENDING", "ACCEPTED", "WAITLISTED", "REJECTED"];

export const statusLabel = (value) =>
  value ? value.charAt(0) + value.slice(1).toLowerCase() : "";
