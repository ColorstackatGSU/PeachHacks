import { formatWhen, fullName } from "./format";

// What each answer from the API looks like. `family` picks the colour and the
// haptic (see theme.js and feedback.js). A result has a verdict `word` and, when
// there is one, a `title` (the person's name, or "OK" for a sponsor) shown larger,
// plus at most one `detail` line. `clears` marks the results that go back to Ready
// on their own; the rest stay until the next badge.

const SEND_TO_ARRIVALS = "Send them to Arrivals";

export const NOT_A_BADGE = { family: "stop", icon: "help-circle-outline", word: "Not a PeachHacks badge" };

export const NOT_SAVED = {
  family: "void",
  icon: "wifi-off",
  word: "Not saved",
  detail: "Tap the badge again",
};

const TURNED_OFF = {
  family: "stop",
  icon: "credit-card-off-outline",
  word: "Badge turned off",
  detail: SEND_TO_ARRIVALS,
};

const UNKNOWN = { family: "stop", icon: "help-circle-outline", word: "Unknown badge", detail: SEND_TO_ARRIVALS };

export function firstTapLine(item) {
  const when = formatWhen(item?.checkedInAt);
  return when ? `First tap at ${when}` : null;
}

const person = (item) => (item ? { title: fullName(item), staff: Boolean(item.staff) } : {});

export function tapView(response, event) {
  const { result, item } = response || {};
  switch (result) {
    case "CHECKED_IN":
      return {
        family: "ok",
        icon: "check-circle-outline",
        word: "Checked in",
        ...person(item),
        detail: !event?.general && item?.generalCheckedIn === false ? "Has not been through Arrivals yet" : null,
        counts: true,
        clears: true,
      };
    case "ALREADY_CHECKED_IN":
      return { family: "repeat", icon: "repeat", word: "Already in", ...person(item), detail: firstTapLine(item) };
    case "NOT_ACCEPTED":
      return { family: "stop", icon: "close-circle-outline", word: "Not accepted", ...person(item) };
    case "SPONSOR":
      return { family: "sponsor", icon: "handshake-outline", word: "Sponsor badge", title: "OK", titleIsName: false, clears: true };
    case "REVOKED_BADGE":
      return TURNED_OFF;
    default:
      return UNKNOWN;
  }
}

export function lookupView(response) {
  const { result, holder } = response || {};
  if (result === "FOUND" && holder) {
    const state = !holder.accepted
      ? { family: "stop", icon: "close-circle-outline", word: "Not accepted" }
      : holder.checkedIn
        ? { family: "ok", icon: "check-circle-outline", word: "Checked in" }
        : { family: "repeat", icon: "clock-outline", word: "Not checked in yet" };
    return { ...state, ...person(holder), detail: holder.school || null };
  }
  if (result === "SPONSOR") return { family: "sponsor", icon: "handshake-outline", word: "Sponsor badge" };
  if (result === "REVOKED_BADGE") return { ...TURNED_OFF, detail: null };
  return { ...UNKNOWN, detail: null };
}

const GROUP_LANYARDS = { STAFF: "Staff lanyard", SPONSOR: "Sponsor lanyard", OTHER: "Other schools lanyard" };

// The lanyard to hand over, named the way a volunteer picks it off the table: by
// its colour once one is configured, otherwise by who it is for.
export function lanyardTitle(lanyard) {
  if (!lanyard) return null;
  const color = (lanyard.color || "").trim();
  if (color) return `${color} lanyard`;
  if (GROUP_LANYARDS[lanyard.group]) return GROUP_LANYARDS[lanyard.group];
  const label = (lanyard.label || "").trim().replace(/\s+hacker$/i, "");
  return label ? `${label} lanyard` : null;
}

const startTime = (event) => {
  const time = event?.startsAt ? new Date(event.startsAt).getTime() : NaN;
  return Number.isNaN(time) ? null : time;
};

// By start time; events without a time last, by name.
export function sortEvents(events) {
  return [...(events || [])].sort((a, b) => {
    const [at, bt] = [startTime(a), startTime(b)];
    if (at !== null && bt !== null && at !== bt) return at - bt;
    if ((at === null) !== (bt === null)) return at === null ? 1 : -1;
    return String(a.name).localeCompare(String(b.name));
  });
}

const CURRENT_BEFORE_MS = 30 * 60 * 1000;
const CURRENT_AFTER_MS = 2 * 60 * 60 * 1000;

// Events have a start and no end, so an event counts as happening from half an hour
// before it starts until two hours after. Up to two of those are featured, the ones
// starting closest to now; with none, the next one to start. The general event and
// events without a time are never featured.
export function featuredEvents(events, now = Date.now()) {
  const timed = (events || [])
    .filter((event) => !event.general && startTime(event) !== null)
    .map((event) => ({ event, start: startTime(event) }));
  const current = timed
    .filter(({ start }) => now >= start - CURRENT_BEFORE_MS && now <= start + CURRENT_AFTER_MS)
    .sort((a, b) => Math.abs(a.start - now) - Math.abs(b.start - now) || a.start - b.start)
    .slice(0, 2)
    .sort((a, b) => a.start - b.start);
  if (current.length > 0) return { kind: "now", events: current.map(({ event }) => event) };
  const upcoming = timed.filter(({ start }) => start > now).sort((a, b) => a.start - b.start);
  return upcoming.length > 0 ? { kind: "next", events: [upcoming[0].event] } : { kind: null, events: [] };
}

// The home screen's event section. The general event is left out everywhere:
// Arrivals is the general check-in.
export function homeEvents(events, now = Date.now()) {
  const featured = featuredEvents(events, now);
  const shown = new Set(featured.events.map((event) => event.id));
  const others = sortEvents((events || []).filter((event) => !event.general && !shown.has(event.id)));
  return { featured, others };
}

const dayTime = new Intl.DateTimeFormat(undefined, { weekday: "short", hour: "numeric", minute: "2-digit" });

export function eventTime(event) {
  const time = startTime(event);
  return time === null ? null : dayTime.format(new Date(time)).replace(",", "");
}

export function initials(name) {
  const words = String(name || "").trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const letters = words.length === 1 ? words[0].slice(0, 2) : words[0][0] + words[words.length - 1][0];
  return letters.toUpperCase();
}
