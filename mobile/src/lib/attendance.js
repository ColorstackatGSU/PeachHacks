import { formatWhen } from "./format";
import { featuredEvents, sortEvents } from "./results";

export const ARRIVED = "arrived";

// The scope chips on Who's here: "Arrived" first, then the events happening now,
// then the rest in start order. The general event is Arrived, so it is left out.
export function attendanceScopes(events, now = Date.now()) {
  const list = (events || []).filter((event) => !event.general);
  const featured = featuredEvents(list, now);
  const current = featured.kind === "now" ? featured.events : [];
  const shown = new Set(current.map((event) => event.id));
  const rest = sortEvents(list.filter((event) => !shown.has(event.id)));
  return [
    { key: ARRIVED, label: "Arrived" },
    ...[...current, ...rest].map((event) => ({ key: event.id, label: event.name, event, now: shown.has(event.id) })),
  ];
}

export function scopeTotal(scope, total) {
  if (typeof total !== "number") return "";
  return scope?.key === ARRIVED ? `${total} arrived` : `${total} at ${scope?.label || "this event"}`;
}

export function emptyText(scope, q) {
  if (q && q.trim()) return `No one matches “${q.trim()}”.`;
  return scope?.key === ARRIVED ? "No one has arrived yet." : `No one is checked in to ${scope?.label || "this event"} yet.`;
}

// The time on a row: when they arrived, or when they were checked in to the event.
export const rowTime = (person) => formatWhen(person?.arrivedAt || person?.checkedInAt);

export const arrivedLine = (person) => (person?.arrivedAt ? `Arrived ${formatWhen(person.arrivedAt)}` : null);

// A person's events for the detail sheet, earliest check-in first.
export function personEvents(person) {
  return [...(person?.events || [])]
    .filter((event) => event?.checkedInAt)
    .sort((a, b) => new Date(a.checkedInAt) - new Date(b.checkedInAt))
    .map((event) => ({ key: event.eventId, name: event.name, time: formatWhen(event.checkedInAt) }));
}

// Adds a page to what is already listed without repeating anyone.
export function mergePage(items, page) {
  const seen = new Set((items || []).map((item) => item.id));
  return [...(items || []), ...(page || []).filter((item) => !seen.has(item.id))];
}
