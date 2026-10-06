const numberFormat = new Intl.NumberFormat("en-US");
const dateTimeFormat = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
});
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

export function formatDate(value) {
  const date = toDate(value);
  return date ? dateFormat.format(date) : "";
}

// For `byDay` buckets ("2026-10-05"), which are calendar days, not instants.
export function formatDay(isoDay) {
  const date = toDate(`${isoDay}T00:00:00Z`);
  return date ? dayFormat.format(date) : isoDay;
}

export const fullName = (person) => [person?.firstName, person?.lastName].filter(Boolean).join(" ") || "(no name)";

export const plural = (count, one, many = `${one}s`) => `${formatNumber(count)} ${count === 1 ? one : many}`;

export const errorText = (error) => error?.message || "Something went wrong.";

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
  },
];

export const audienceLabel = (value) => AUDIENCES.find((a) => a.value === value)?.label || value;

export const STATUSES = ["PENDING", "ACCEPTED", "WAITLISTED", "REJECTED"];

export const statusLabel = (value) =>
  value ? value.charAt(0) + value.slice(1).toLowerCase() : "";
