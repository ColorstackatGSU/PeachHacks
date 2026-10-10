// Loaded only by the dev server when VITE_MOCK_API=1 (see client.js); never part
// of a production build. Data resets on every page reload.

import { hostShare, isHostSchool, needsAgeReview } from "../lib/acceptance.js";

const DAY = 86400000;
const now = Date.now();

function seeded(seed) {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}
const rand = seeded(20270205);
const pick = (list) => list[Math.floor(rand() * list.length)];
// Skewed pick so a few schools dominate, like real sign-up data.
const pickSkewed = (list) => list[Math.floor(Math.pow(rand(), 2.2) * list.length)];
let idCounter = 0;
const uuid = () => {
  idCounter += 1;
  return `00000000-0000-4000-8000-${String(idCounter).padStart(12, "0")}`;
};

const HOST_SCHOOL = { name: "Georgia State University", target: 0.7 };
const NON_HOST_MINIMUM_AGE = 18;
const ageReview = (r) => needsAgeReview(r, HOST_SCHOOL.name, NON_HOST_MINIMUM_AGE);
const SCHOOLS = [
  "Georgia State University",
  "Georgia State University Perimeter College",
  "Georgia Institute of Technology",
  "Kennesaw State University",
  "University of Georgia",
  "Emory University",
  "Spelman College",
  "Morehouse College",
  "Clark Atlanta University",
  "Georgia Gwinnett College",
  "Georgia Southern University",
  "Agnes Scott College",
  "Mercer University",
  "University of North Georgia",
  "Auburn University",
];
const FIRST = ["Amara", "Diego", "Priya", "Jordan", "Mei", "Kwame", "Sofia", "Noah", "Aaliyah", "Ethan", "Zainab", "Lucas", "Imani", "Mateo", "Hana", "Tunde", "Chloe", "Rahul", "Nia", "Omar", "Grace", "Andre", "Linh", "Camila"];
const LAST = ["Okafor", "Ramirez", "Patel", "Williams", "Chen", "Mensah", "Garcia", "Kim", "Johnson", "Nguyen", "Hassan", "Silva", "Brown", "Adeyemi", "Tanaka", "Lopez", "Davis", "Sharma", "Jackson", "Ali", "Thompson", "Moreau", "Tran", "Reyes"];
const LEVELS = ["Undergraduate University (3+ year)", "Undergraduate University (2 year - community college or similar)", "Graduate University (Masters, Professional, Doctoral, etc)", "Code School / Bootcamp", "High School"];
const MAJORS = ["Computer science, computer engineering, or software engineering", "Information systems, information technology, or system administration", "Mathematics or statistics", "Business discipline (such as accounting, finance, marketing, etc.)", "Undecided / No Declared Major"];
const STATUSES = ["PENDING", "PENDING", "PENDING", "ACCEPTED", "ACCEPTED", "WAITLISTED", "REJECTED"];

function person(index) {
  const firstName = pick(FIRST);
  const lastName = pick(LAST);
  const school = pickSkewed(SCHOOLS);
  const handle = `${firstName}.${lastName}${index}`.toLowerCase();
  return { firstName, lastName, school, handle };
}

const preRegistrations = [];
const registrations = [];

for (let i = 0; i < 187; i += 1) {
  const p = person(i);
  preRegistrations.push({
    id: uuid(),
    firstName: p.firstName,
    lastName: p.lastName,
    email: `${p.handle}@example.com`,
    school: p.school,
    schoolEmail: rand() > 0.4 ? `${p.handle}@student.example.edu` : null,
    unsubscribed: rand() > 0.95,
    createdAt: new Date(now - Math.floor(Math.pow(rand(), 1.6) * 40 * DAY)).toISOString(),
  });
}
// A person usually confirms within minutes of signing up.
preRegistrations.forEach((pre) => {
  const confirmed = Boolean(pre.schoolEmail) && rand() > 0.35;
  pre.schoolEmailConfirmed = confirmed;
  pre.schoolEmailConfirmedAt = confirmed ? new Date(Date.parse(pre.createdAt) + 5 * 60000).toISOString() : null;
});

function confirmation(base, createdAt) {
  if (base.schoolEmailConfirmed) return { schoolEmailConfirmed: true, schoolEmailConfirmedAt: base.schoolEmailConfirmedAt };
  const confirmed = rand() > 0.4;
  return {
    schoolEmailConfirmed: confirmed,
    schoolEmailConfirmedAt: confirmed ? new Date(Math.min(now, Date.parse(createdAt) + 5 * 60000)).toISOString() : null,
  };
}

// Most accepted people have been told; the rest are waiting in the acceptance bucket.
function acceptance(status, createdAt) {
  if (status !== "ACCEPTED") return { acceptedAt: null, acceptanceNotifiedAt: null };
  const acceptedAt = new Date(Math.min(now, Date.parse(createdAt) + DAY)).toISOString();
  return { acceptedAt, acceptanceNotifiedAt: rand() > 0.35 ? acceptedAt : null };
}

function makeRegistration(base, createdAt) {
  const veg = rand();
  const resumeRoll = rand();
  const status = pick(STATUSES);
  return {
    id: uuid(),
    firstName: base.firstName,
    lastName: base.lastName,
    age: 17 + Math.floor(rand() * 9),
    phone: `+1 404 555 ${String(1000 + Math.floor(rand() * 9000))}`,
    email: base.email,
    schoolEmail: base.schoolEmail || `${base.email.split("@")[0]}@student.example.edu`,
    // Confirmed at pre-registration carries over; otherwise most people confirm soon after.
    ...confirmation(base, createdAt),
    school: base.school,
    levelOfStudy: pickSkewed(LEVELS),
    graduationYear: 2027 + Math.floor(rand() * 4),
    graduationMonth: pick([5, 5, 5, 12, 8]),
    countryOfResidence: rand() > 0.08 ? "US" : pick(["CA", "NG", "IN", "MX"]),
    mlhCodeOfConduct: true,
    mlhDataSharing: true,
    mlhEmailOptIn: rand() > 0.5,
    dietaryRestrictions: veg > 0.8 ? ["Vegetarian"] : veg > 0.7 ? ["Halal"] : veg > 0.65 ? ["Vegan", "Allergies"] : [],
    dietaryDetails: veg > 0.65 && veg <= 0.7 ? "Tree nut allergy" : null,
    underrepresentedGroup: pick(["Yes", "No", "Unsure", null]),
    gender: pick(["Woman", "Man", "Non-Binary", "Prefer to self-describe", "Prefer Not to Answer", null]),
    genderSelfDescribe: null,
    pronouns: pick(["She/Her", "He/Him", "They/Them", "Other", null]),
    pronounsOther: null,
    raceEthnicity: rand() > 0.3 ? [pick(["Black or African", "Hispanic / Latino / Spanish Origin", "East Asian", "South Asian", "White", "Middle Eastern"])] : [],
    raceEthnicityOther: null,
    sexualOrientation: pick(["Heterosexual or straight", "Gay or lesbian", "Bisexual", "Prefer Not to Answer", null]),
    sexualOrientationOther: null,
    highestEducation: pick(["Secondary/High School", "Undergraduate University (3+ year)", "Less than Secondary / High School", null]),
    highestEducationOther: null,
    tshirtSize: pick(["S", "M", "L", "XL", null]),
    majorFieldOfStudy: pickSkewed(MAJORS),
    majorOther: null,
    githubUrl: rand() > 0.6 ? `https://github.com/${base.email.split("@")[0].replace(/\./g, "-")}` : null,
    linkedinUrl: rand() > 0.5 ? `https://www.linkedin.com/in/${base.email.split("@")[0].replace(/\./g, "-")}` : null,
    status,
    ...acceptance(status, createdAt),
    staff: false,
    ticketToken: `mockticket${String(idCounter).padStart(12, "0")}`,
    resume: resumeRoll > 0.45
      ? { fileName: `${base.firstName}_${base.lastName}_Resume.pdf`, size: 60000 + Math.floor(rand() * 900000), uploadedAt: createdAt }
      : null,
    createdAt,
  };
}

preRegistrations.forEach((pre) => {
  if (rand() > 0.68) {
    const offset = Math.floor(rand() * 9 * DAY);
    registrations.push(makeRegistration(pre, new Date(now - offset).toISOString()));
  }
});
for (let i = 0; i < 14; i += 1) {
  const p = person(500 + i);
  registrations.push(
    makeRegistration(
      { firstName: p.firstName, lastName: p.lastName, school: p.school, email: `${p.handle}@example.com` },
      new Date(now - Math.floor(rand() * 9 * DAY)).toISOString(),
    ),
  );
}

const admins = [
  { id: uuid(), email: "admin@peachhacks.local", name: "Local Admin", role: "ADMIN", createdAt: new Date(now - 60 * DAY).toISOString() },
  { id: uuid(), email: "logistics@peachhacks.local", name: "Logistics Lead", role: "ADMIN", createdAt: new Date(now - 21 * DAY).toISOString() },
  { id: uuid(), email: "volunteer@peachhacks.local", name: "Door Volunteer", role: "VOLUNTEER", createdAt: new Date(now - 2 * DAY).toISOString() },
  { id: uuid(), email: "lookup@peachhacks.local", name: "Venue Staff", role: "LOOKUP", createdAt: new Date(now - DAY).toISOString() },
];
const MOCK_PASSWORD_URL = `${window.location.origin}/#/set-password?token=mock`;
const settings = { registrationOpen: false, previewActive: false, webCheckInAdminOnly: false };
const discord = {
  configured: true,
  verified: 12,
  messagePostedAt: null,
  welcomes: true,
  gateway: "Connected. PeachBot hears when someone joins.",
  lastJoinSeen: null,
  lastWelcome: null,
  applications: true,
  message: "**Verify to get into PeachHacks**\nPress Verify to open the hacker channels.",
};
// One token per role so a reload keeps whichever account was signed in.
const TOKENS = { "mock-token": admins[0], "mock-volunteer-token": admins[2], "mock-lookup-token": admins[3] };
const sessions = new Set(Object.keys(TOKENS));

const events = [
  { id: uuid(), name: "General check-in", startsAt: null, general: true },
  { id: uuid(), name: "Intro to React workshop", startsAt: new Date(now + 3 * 3600000).toISOString(), general: false },
];
const generalEvent = events[0];
const checkIns = [];
// Active badges by registration id; the API also keeps revoked ones as history.
const badges = new Map();
const cardUid = () => `04${Array.from({ length: 6 }, () => Math.floor(rand() * 256).toString(16).padStart(2, "0")).join("")}`.toUpperCase();
registrations
  .filter((r) => r.status === "ACCEPTED")
  .forEach((r, index) => {
    if (index % 3 === 0) {
      const checkedInAt = new Date(now - index * 60000).toISOString();
      checkIns.push({ registrationId: r.id, eventId: generalEvent.id, checkedInAt, checkedInBy: "Door Volunteer" });
      // Most people checked in at the desk got a badge; the rest were checked in on the web.
      if (index % 4 !== 3) badges.set(r.id, { uid: cardUid(), boundAt: checkedInAt, boundBy: "Door Volunteer" });
    }
    if (index % 6 === 0) {
      checkIns.push({ registrationId: r.id, eventId: events[1].id, checkedInAt: new Date(now - index * 30000).toISOString(), checkedInBy: "Logistics Lead" });
    }
  });

registrations.filter((r) => r.status === "ACCEPTED").slice(0, 2).forEach((r) => {
  r.staff = true;
});
// Sponsor cards belong to nobody: only the card, who issued it and when.
const sponsorBadges = [18, 95, 240].map((minutes) => ({ uid: cardUid(), boundAt: new Date(now - minutes * 60000).toISOString(), boundBy: "Door Volunteer" }));

const findCheckIn = (registrationId, eventId) =>
  checkIns.find((c) => c.registrationId === registrationId && c.eventId === eventId) || null;
const eventCount = (eventId) => checkIns.filter((c) => c.eventId === eventId).length;
const eventView = (event) => ({ ...event, checkedIn: eventCount(event.id) });
const sortedEvents = () =>
  [...events].sort((a, b) => Number(b.general) - Number(a.general) || (a.startsAt || "9").localeCompare(b.startsAt || "9") || a.name.localeCompare(b.name));

function checkInItem(r, event) {
  const checkIn = findCheckIn(r.id, event.id);
  return {
    id: r.id,
    firstName: r.firstName,
    lastName: r.lastName,
    email: r.email,
    school: r.school,
    status: r.status,
    staff: r.staff,
    checkedInAt: checkIn?.checkedInAt || null,
    checkedInBy: checkIn?.checkedInBy || null,
    generalCheckedIn: Boolean(findCheckIn(r.id, generalEvent.id)),
  };
}

function detail(r) {
  const { ticketToken, ...fields } = r;
  const general = findCheckIn(r.id, generalEvent.id);
  const accepted = r.status === "ACCEPTED";
  return {
    ...fields,
    checkedInAt: general?.checkedInAt || null,
    checkedInBy: general?.checkedInBy || null,
    checkIns: checkIns
      .filter((c) => c.registrationId === r.id)
      .map((c) => {
        const event = events.find((e) => e.id === c.eventId);
        return { eventId: c.eventId, name: event?.name, general: Boolean(event?.general), checkedInAt: c.checkedInAt, checkedInBy: c.checkedInBy };
      }),
    ticketToken: accepted ? ticketToken : null,
    ticketUrl: accepted ? `https://www.peachhacks.com/ticket?t=${ticketToken}` : null,
    googleWalletUrl: null,
    ageReview: ageReview(r),
    badge: badges.get(r.id) || null,
  };
}

function tokenFrom(code) {
  const text = String(code || "").trim();
  const match = /[?&]t=([^&#\s]+)/.exec(text);
  return match ? decodeURIComponent(match[1]) : text;
}

const campaigns = [
  {
    id: uuid(),
    kind: "ANNOUNCEMENT",
    subject: "Thanks for pre-registering for PeachHacks",
    body: "Hi {{firstName}},\n\nThanks for pre-registering. We will email you the moment registration opens.\n\nThe PeachHacks team",
    audience: "PRE_REGISTRANTS",
    school: null,
    recipientCount: 142,
    sentCount: 140,
    failedCount: 2,
    status: "SENT",
    createdBy: "Local Admin",
    createdAt: new Date(now - 12 * DAY).toISOString(),
    completedAt: new Date(now - 12 * DAY + 90000).toISOString(),
  },
  {
    id: uuid(),
    kind: "ANNOUNCEMENT",
    subject: "PeachHacks pre-registration is open",
    body: "Hi {{firstName}},\n\nPre-registration is open. Sign up to hear first when registration opens.\n\nThe PeachHacks team",
    audience: "PRE_REGISTRANTS",
    school: null,
    recipientCount: 38,
    sentCount: 38,
    failedCount: 0,
    status: "SENT",
    createdBy: "Local Admin",
    createdAt: new Date(now - 45 * DAY).toISOString(),
    completedAt: new Date(now - 45 * DAY + 30000).toISOString(),
  },
];
// Who each campaign was addressed to, by campaign id. The older seeded campaign has no
// entry, like one sent before per-person records were kept.
const campaignPeople = new Map([[campaigns[0].id, preRegistrations.slice(0, campaigns[0].recipientCount)]]);

// Statuses follow the campaign's counters: the first `sentCount` people are sent, and
// whoever is left when it finishes has failed.
function campaignRecipients(campaign) {
  const finished = campaign.status === "SENT" || campaign.status === "FAILED";
  return (campaignPeople.get(campaign.id) || []).map((p, index) => {
    const sent = index < campaign.sentCount;
    return {
      email: p.email,
      firstName: p.firstName,
      lastName: p.lastName,
      status: sent ? "SENT" : finished ? "FAILED" : "PENDING",
      sentAt: sent ? campaign.completedAt || campaign.createdAt : null,
    };
  });
}

const byNewest = (a, b) => (a.createdAt < b.createdAt ? 1 : -1);
// Mock mode stores no files: every resume downloads as the same placeholder, and the
// resume book is a valid but empty archive (just the end-of-central-directory record).
const MOCK_PDF = "%PDF-1.4\n% Placeholder resume served by the admin mock API.\n%%EOF\n";
const EMPTY_ZIP = new Uint8Array([0x50, 0x4b, 0x05, 0x06, ...new Array(18).fill(0)]);

const registeredEmails = () => new Set(registrations.map((r) => r.email.toLowerCase()));

function respond(status, payload, headers = {}) {
  if (payload === undefined || payload === null) return new Response(null, { status, headers });
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}
const fail = (status, code, message, fieldErrors) =>
  respond(status, fieldErrors ? { code, message, fieldErrors } : { code, message });

function groupCount(list, keyOf, keyName) {
  const counts = new Map();
  list.forEach((item) => {
    const key = keyOf(item);
    counts.set(key, (counts.get(key) || 0) + 1);
  });
  return [...counts.entries()].map(([key, count]) => ({ [keyName]: key, count }));
}
const bySchool = (list) => groupCount(list, (i) => i.school, "school").sort((a, b) => b.count - a.count);
const byDay = (list) =>
  groupCount(list, (i) => i.createdAt.slice(0, 10), "date").sort((a, b) => (a.date < b.date ? -1 : 1));

function filterPeople(list, params) {
  const q = (params.get("q") || "").trim().toLowerCase();
  const school = params.get("school") || "";
  const status = params.get("status") || "";
  const checkedIn = params.get("checkedIn") || "";
  const resume = params.get("resume") || "";
  const schoolEmailConfirmed = params.get("schoolEmailConfirmed") || "";
  const ageReviewFilter = params.get("ageReview") || "";
  return list
    .filter((item) => {
      if (school && item.school !== school) return false;
      if (status && item.status !== status) return false;
      if (checkedIn && String(Boolean(findCheckIn(item.id, generalEvent.id))) !== checkedIn) return false;
      if (resume === "any" && !item.resume) return false;
      if (resume === "none" && item.resume) return false;
      if (schoolEmailConfirmed && String(Boolean(item.schoolEmailConfirmed)) !== schoolEmailConfirmed) return false;
      if (ageReviewFilter && String(ageReview(item)) !== ageReviewFilter) return false;
      if (!q) return true;
      return `${item.firstName} ${item.lastName} ${item.email} ${item.schoolEmail || ""}`.toLowerCase().includes(q);
    })
    .sort(byNewest);
}

function paginate(list, params) {
  const page = Math.max(0, Number(params.get("page") || 0));
  const size = Math.max(1, Number(params.get("size") || 25));
  return { items: list.slice(page * size, page * size + size), total: list.length, page, size };
}

function withRegistered(list) {
  const emails = registeredEmails();
  return list.map((item) => ({ ...item, registered: emails.has(item.email.toLowerCase()) }));
}

function summary(r) {
  const { id, firstName, lastName, email, schoolEmail, schoolEmailConfirmed, schoolEmailConfirmedAt, school, levelOfStudy, countryOfResidence, age, status, staff, acceptedAt, acceptanceNotifiedAt, createdAt } = r;
  const checkedInAt = findCheckIn(id, generalEvent.id)?.checkedInAt || null;
  return {
    id, firstName, lastName, email, schoolEmail, schoolEmailConfirmed, schoolEmailConfirmedAt, school, levelOfStudy, countryOfResidence, age, status, staff, acceptedAt, acceptanceNotifiedAt, createdAt, checkedInAt,
    hasResume: Boolean(r.resume),
    ageReview: ageReview(r),
  };
}

function csv(rows) {
  if (rows.length === 0) return "";
  const columns = Object.keys(rows[0]);
  const cell = (value) => {
    const text = value === null || value === undefined ? "" : typeof value === "object" ? JSON.stringify(value) : String(value);
    return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
  };
  return [columns.join(","), ...rows.map((row) => columns.map((c) => cell(row[c])).join(","))].join("\n");
}
const csvResponse = (rows, name) =>
  new Response(csv(rows), {
    status: 200,
    headers: { "Content-Type": "text/csv", "Content-Disposition": `attachment; filename="${name}"` },
  });

const REGISTERED_AUDIENCES = ["REGISTRANTS", "ACCEPTED"];
const ACTIVE_CAMPAIGN = ["QUEUED", "SENDING"];
const CAMPAIGN_KINDS = ["EVENT_UPDATE", "ANNOUNCEMENT"];

function campaignErrors(body) {
  const fieldErrors = {};
  if (!CAMPAIGN_KINDS.includes(body.kind)) fieldErrors.kind = "Choose the kind of email";
  else if (body.kind === "EVENT_UPDATE" && body.audience !== "ACCEPTED") {
    fieldErrors.audience = "An event update can only go to accepted hackers.";
  }
  return fieldErrors;
}

// Only an announcement leaves out people who unsubscribed. One entry per email address.
function audiencePeople(kind, audience, school) {
  const registered = registeredEmails();
  const unsubscribed = new Set(preRegistrations.filter((p) => p.unsubscribed).map((p) => p.email.toLowerCase()));
  let pool;
  if (REGISTERED_AUDIENCES.includes(audience)) {
    pool = registrations.filter((r) => audience === "REGISTRANTS" || (r.status === "ACCEPTED" && r.acceptanceNotifiedAt));
  } else pool = preRegistrations;
  if (kind === "ANNOUNCEMENT") pool = pool.filter((p) => !unsubscribed.has(p.email.toLowerCase()));
  if (audience === "PRE_REGISTRANTS_NOT_REGISTERED") pool = pool.filter((p) => !registered.has(p.email.toLowerCase()));
  if (school) pool = pool.filter((p) => p.school === school);
  return [...new Map(pool.map((p) => [p.email.toLowerCase(), p])).values()];
}

// Campaigns advance on a clock so the history list can be seen refreshing.
function tickCampaigns() {
  campaigns.forEach((c) => {
    if (!ACTIVE_CAMPAIGN.includes(c.status)) return;
    const elapsed = Date.now() - new Date(c.createdAt).getTime();
    if (elapsed < 3000) return;
    const progress = Math.min(1, (elapsed - 3000) / 12000);
    c.status = "SENDING";
    c.sentCount = Math.floor(c.recipientCount * progress);
    if (progress >= 1) {
      c.failedCount = c.recipientCount > 20 ? 1 : 0;
      c.sentCount = c.recipientCount - c.failedCount;
      c.status = "SENT";
      c.completedAt = new Date().toISOString();
    }
  });
}

const VALID_STATUSES = ["PENDING", "ACCEPTED", "WAITLISTED", "REJECTED"];

// Same rule as the API: a change of status starts the acceptance over.
function changeStatus(r, status) {
  if (r.status === status) return false;
  r.status = status;
  r.acceptedAt = status === "ACCEPTED" ? new Date().toISOString() : null;
  r.acceptanceNotifiedAt = null;
  return true;
}

const isWaiting = (r) => r.status === "ACCEPTED" && !r.acceptanceNotifiedAt;
const isHost = (r) => isHostSchool(r.school, HOST_SCHOOL.name);
const shareOf = (list) => hostShare(list.filter(isHost).length, list.length, HOST_SCHOOL.target);

let acceptanceSend = { state: "IDLE", queued: 0, sent: 0, failed: 0, skipped: 0, startedAt: null, finishedAt: null, startedBy: null };
let sendQueue = [];

// A send advances on a clock, about three people a second, so progress can be watched.
function tickAcceptanceSend() {
  if (acceptanceSend.state !== "SENDING") return;
  const due = Math.min(sendQueue.length, Math.floor((Date.now() - Date.parse(acceptanceSend.startedAt)) / 350));
  let { sent, skipped } = acceptanceSend;
  for (let i = sent + skipped; i < due; i += 1) {
    const r = registrations.find((reg) => reg.id === sendQueue[i]);
    if (r && isWaiting(r)) {
      r.acceptanceNotifiedAt = new Date().toISOString();
      sent += 1;
    } else {
      skipped += 1;
    }
  }
  const finished = sent + skipped >= sendQueue.length;
  acceptanceSend = {
    ...acceptanceSend,
    sent,
    skipped,
    state: finished ? "IDLE" : "SENDING",
    finishedAt: finished ? new Date().toISOString() : null,
  };
}

function acceptanceSummary() {
  const accepted = registrations.filter((r) => r.status === "ACCEPTED");
  const count = (status) => registrations.filter((r) => r.status === status).length;
  const waiting = accepted.filter(isWaiting).length;
  return {
    totals: {
      registrations: registrations.length,
      accepted: accepted.length,
      acceptedNotified: accepted.length - waiting,
      acceptedWaiting: waiting,
      pending: count("PENDING"),
      waitlisted: count("WAITLISTED"),
      rejected: count("REJECTED"),
      acceptanceRate: registrations.length > 0 ? accepted.length / registrations.length : null,
    },
    hostSchool: HOST_SCHOOL,
    shares: {
      accepted: shareOf(accepted),
      registrations: shareOf(registrations),
      pending: shareOf(registrations.filter((r) => r.status === "PENDING")),
      checkedIn: shareOf(registrations.filter((r) => findCheckIn(r.id, generalEvent.id))),
    },
    acceptedBySchool: bySchool(accepted).map((row) => ({ ...row, host: isHostSchool(row.school, HOST_SCHOOL.name) })),
    ageReview: {
      minimumAge: NON_HOST_MINIMUM_AGE,
      total: registrations.filter(ageReview).length,
      accepted: accepted.filter(ageReview).length,
    },
    send: acceptanceSend,
  };
}

function handle(method, path, params, body, token) {
  if (path === "/admin/auth/login" && method === "POST") {
    if (!body.email || !body.password || body.password === "wrong") {
      return fail(401, "INVALID_CREDENTIALS", "Incorrect email or password.");
    }
    const mockToken = /^volunteer/i.test(body.email) ? "mock-volunteer-token" : /^lookup/i.test(body.email) ? "mock-lookup-token" : "mock-token";
    sessions.add(mockToken);
    return respond(200, { token: mockToken, expiresAt: new Date(Date.now() + 8 * 3600000).toISOString(), admin: TOKENS[mockToken] });
  }

  if (path === "/admin/auth/forgot-password" && method === "POST") return respond(204);
  if (path === "/admin/auth/set-password/check" && method === "POST") {
    if (body.token === "expired") return fail(400, "INVALID_PASSWORD_LINK", "This link has expired or was already used. Ask for a new one.");
    const invited = admins.find((a) => a.pending) || admins[2];
    return respond(200, { email: invited.email, name: invited.name, invite: Boolean(invited.pending) });
  }
  if (path === "/admin/auth/set-password" && method === "POST") {
    admins.forEach((a) => {
      a.pending = false;
    });
    return respond(204);
  }

  if (!token || !sessions.has(token)) return fail(401, "UNAUTHORIZED", "Sign in to continue.");
  const currentAdmin = TOKENS[token];

  const authRoute = path.startsWith("/admin/auth/");
  const checkInRoute = path === "/admin/check-in" || path.startsWith("/admin/check-in/");
  const open = currentAdmin.role === "VOLUNTEER" ? authRoute || checkInRoute || (path === "/admin/events" && method === "GET") : authRoute;
  if (currentAdmin.role !== "ADMIN" && !open) return fail(403, "FORBIDDEN", "You do not have access to this resource.");
  if (currentAdmin.role === "VOLUNTEER" && checkInRoute && settings.webCheckInAdminOnly) {
    return fail(403, "WEB_CHECK_IN_ADMIN_ONLY", "Check-in is done in the PeachHacks staff app. Ask an organizer if you need it here.");
  }

  if (path === "/admin/auth/logout" && method === "POST") {
    sessions.delete(token);
    return respond(204);
  }
  if (path === "/admin/auth/change-password" && method === "POST") {
    if (body.currentPassword === "wrong") {
      return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", { currentPassword: "That is not your current password." });
    }
    return respond(204);
  }
  if (path === "/admin/auth/me") {
    return respond(200, { id: currentAdmin.id, email: currentAdmin.email, name: currentAdmin.name, role: currentAdmin.role });
  }

  if (path === "/admin/events") {
    if (method === "POST") {
      const name = (body.name || "").trim();
      if (!name) return fail(400, "VALIDATION_ERROR", "Name is required", { name: "Name is required" });
      if (events.some((e) => e.name.toLowerCase() === name.toLowerCase())) {
        return fail(400, "VALIDATION_ERROR", "An event with this name already exists", { name: "An event with this name already exists" });
      }
      const event = { id: uuid(), name, startsAt: body.startsAt || null, general: false };
      events.push(event);
      return respond(201, eventView(event));
    }
    return respond(200, sortedEvents().map(eventView));
  }
  const eventExport = /^\/admin\/events\/([^/]+)\/export\.csv$/.exec(path);
  if (eventExport) {
    const event = events.find((e) => e.id === eventExport[1]);
    if (!event) return fail(404, "NOT_FOUND", "Event not found.");
    const rows = checkIns
      .filter((c) => c.eventId === event.id)
      .map((c) => {
        const r = registrations.find((reg) => reg.id === c.registrationId);
        return { event: event.name, registrationId: c.registrationId, firstName: r?.firstName, lastName: r?.lastName, email: r?.email, school: r?.school, status: r?.status, checked_in_at: c.checkedInAt, checked_in_by: c.checkedInBy };
      });
    return csvResponse(rows, "attendees.csv");
  }
  const eventMatch = /^\/admin\/events\/([^/]+)$/.exec(path);
  if (eventMatch) {
    const index = events.findIndex((e) => e.id === eventMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Event not found.");
    if (method === "PATCH") {
      if ("name" in body) {
        const name = (body.name || "").trim();
        if (!name) return fail(400, "VALIDATION_ERROR", "Name is required", { name: "Name is required" });
        events[index].name = name;
      }
      if ("startsAt" in body) events[index].startsAt = body.startsAt || null;
      return respond(200, eventView(events[index]));
    }
    if (method === "DELETE") {
      if (events[index].general) return fail(400, "VALIDATION_ERROR", "The general check-in event cannot be deleted.");
      const checkedIn = checkIns.filter((c) => c.eventId === events[index].id).length;
      if (checkedIn > 0) {
        return fail(409, "EVENT_HAS_CHECK_INS", `"${events[index].name}" has ${checkedIn} check-in${checkedIn === 1 ? "" : "s"}, so it cannot be deleted. Undo the check-ins first if it really should go.`);
      }
      events.splice(index, 1);
      return respond(204);
    }
  }

  if (path === "/admin/check-in" || path.startsWith("/admin/check-in/")) {
    const requested = params.get("eventId") || body.eventId;
    const event = requested ? events.find((e) => e.id === requested) : generalEvent;
    if (!event) return fail(404, "NOT_FOUND", "Event not found.");
    const eventRef = { id: event.id, name: event.name, general: event.general };
    const record = (r) => {
      if (!findCheckIn(r.id, event.id)) {
        checkIns.push({ registrationId: r.id, eventId: event.id, checkedInAt: new Date().toISOString(), checkedInBy: currentAdmin.name });
      }
    };

    if (path === "/admin/check-in") {
      const q = (params.get("q") || "").trim().toLowerCase();
      const matches = registrations
        .filter((r) => !q || `${r.firstName} ${r.lastName} ${r.email}`.toLowerCase().includes(q))
        .map((r) => checkInItem(r, event))
        .sort((a, b) => Number(Boolean(a.checkedInAt)) - Number(Boolean(b.checkedInAt)) || a.lastName.localeCompare(b.lastName) || a.firstName.localeCompare(b.firstName));
      return respond(200, { event: eventRef, ...paginate(matches, params), checkedInTotal: eventCount(event.id), registrationTotal: registrations.length });
    }
    if (path === "/admin/check-in/scan" && method === "POST") {
      const r = registrations.find((reg) => reg.ticketToken === tokenFrom(body.code));
      if (!r) return respond(200, { result: "NOT_RECOGNISED", event: eventRef, item: null });
      if (findCheckIn(r.id, event.id)) return respond(200, { result: "ALREADY_CHECKED_IN", event: eventRef, item: checkInItem(r, event) });
      if (r.status !== "ACCEPTED") return respond(200, { result: "NOT_ACCEPTED", event: eventRef, item: checkInItem(r, event) });
      record(r);
      return respond(200, { result: "CHECKED_IN", event: eventRef, item: checkInItem(r, event) });
    }
    const r = registrations.find((reg) => reg.id === path.split("/").pop());
    if (!r) return fail(404, "NOT_FOUND", "Registration not found.");
    if (method === "POST") {
      if (r.status !== "ACCEPTED") {
        return fail(409, "NOT_ACCEPTED", "This person is not accepted, so they cannot be checked in.");
      }
      record(r);
    }
    if (method === "DELETE") {
      const index = checkIns.findIndex((c) => c.registrationId === r.id && c.eventId === event.id);
      if (index >= 0) checkIns.splice(index, 1);
      // Binding a badge is the general check-in, so undoing one undoes the other.
      if (event.general) badges.delete(r.id);
    }
    return respond(200, checkInItem(r, event));
  }

  if (path === "/admin/stats") {
    const registered = registeredEmails();
    return respond(200, {
      registrationOpen: settings.registrationOpen,
      preRegistrations: {
        total: preRegistrations.length,
        unsubscribed: preRegistrations.filter((p) => p.unsubscribed).length,
        schoolEmailConfirmed: preRegistrations.filter((p) => p.schoolEmailConfirmed).length,
        bySchool: bySchool(preRegistrations),
        byDay: byDay(preRegistrations),
      },
      registrations: {
        total: registrations.length,
        checkedIn: eventCount(generalEvent.id),
        withResume: registrations.filter((r) => r.resume).length,
        schoolEmailConfirmed: registrations.filter((r) => r.schoolEmailConfirmed).length,
        bySchool: bySchool(registrations),
        byDay: byDay(registrations),
        byLevelOfStudy: groupCount(registrations, (r) => r.levelOfStudy, "label").sort((a, b) => b.count - a.count),
        byStatus: groupCount(registrations, (r) => r.status, "label"),
      },
      preRegisteredNotRegistered: preRegistrations.filter((p) => !registered.has(p.email.toLowerCase())).length,
      events: sortedEvents().map((e) => ({ eventId: e.id, name: e.name, checkedIn: eventCount(e.id) })),
    });
  }

  if (path === "/admin/pre-registrations") {
    return respond(200, paginate(withRegistered(filterPeople(preRegistrations, params)), params));
  }
  if (path === "/admin/pre-registrations/export.csv") {
    return csvResponse(
      withRegistered(filterPeople(preRegistrations, params)).map((item) => {
        const row = { ...item, school_email_confirmed: item.schoolEmailConfirmed };
        ["schoolEmailConfirmed", "schoolEmailConfirmedAt"].forEach((key) => delete row[key]);
        return row;
      }),
      "pre-registrations.csv",
    );
  }
  if (path === "/admin/registrations") {
    return respond(200, paginate(filterPeople(registrations, params).map(summary), params));
  }
  if (path === "/admin/registrations/export.csv") {
    return csvResponse(
      filterPeople(registrations, params).map((r) => {
        const row = {
          ...r,
          checked_in_at: findCheckIn(r.id, generalEvent.id)?.checkedInAt || null,
          has_resume: Boolean(r.resume),
          school_email: r.schoolEmail,
          school_email_confirmed: r.schoolEmailConfirmed,
          age_review: ageReview(r),
        };
        ["ticketToken", "resume", "schoolEmail", "schoolEmailConfirmed", "schoolEmailConfirmedAt"].forEach((key) => delete row[key]);
        return row;
      }),
      "registrations.csv",
    );
  }

  const schoolEmailResend = /^\/admin\/(pre-registrations|registrations)\/([^/]+)\/school-email\/resend$/.exec(path);
  if (schoolEmailResend && method === "POST") {
    const pool = schoolEmailResend[1] === "registrations" ? registrations : preRegistrations;
    const item = pool.find((entry) => entry.id === schoolEmailResend[2]);
    if (!item) return fail(404, "NOT_FOUND", "Not found.");
    if (!item.schoolEmail) return fail(400, "VALIDATION_ERROR", "There is no school email to confirm.");
    if (item.schoolEmailConfirmed) return fail(400, "VALIDATION_ERROR", "This school email is already confirmed.");
    return respond(204);
  }

  const ticketEmail = /^\/admin\/registrations\/([^/]+)\/ticket-email$/.exec(path);
  if (ticketEmail && method === "POST") {
    const r = registrations.find((reg) => reg.id === ticketEmail[1]);
    if (!r) return fail(404, "NOT_FOUND", "Registration not found.");
    if (r.status !== "ACCEPTED") return fail(400, "VALIDATION_ERROR", "Only accepted registrations have a ticket to send.");
    if (!r.acceptanceNotifiedAt) r.acceptanceNotifiedAt = new Date().toISOString();
    return respond(204);
  }

  if (path === "/admin/registrations/status" && method === "POST") {
    const ids = [...new Set(Array.isArray(body.ids) ? body.ids : [])];
    if (ids.length === 0) return fail(400, "VALIDATION_ERROR", "Please check the highlighted fields.", { ids: "Choose at least one registration" });
    if (ids.length > 500) return fail(400, "VALIDATION_ERROR", "Please check the highlighted fields.", { ids: "At most 500 registrations at a time" });
    if (!VALID_STATUSES.includes(body.status)) return fail(400, "VALIDATION_ERROR", "Invalid status.", { status: "Unknown status" });
    const found = registrations.filter((r) => ids.includes(r.id));
    const changed = found.filter((r) => changeStatus(r, body.status));
    return respond(200, {
      changed: changed.length,
      unchanged: found.length - changed.length,
      notFound: ids.length - found.length,
      acceptedAgeReview: body.status === "ACCEPTED" ? changed.filter(ageReview).length : 0,
    });
  }

  if (path === "/admin/acceptances/summary") {
    tickAcceptanceSend();
    return respond(200, acceptanceSummary());
  }
  if (path === "/admin/acceptances/waiting") {
    tickAcceptanceSend();
    return respond(
      200,
      registrations
        .filter(isWaiting)
        .sort((a, b) => (a.acceptedAt < b.acceptedAt ? -1 : 1))
        .map((r) => ({ id: r.id, firstName: r.firstName, lastName: r.lastName, email: r.email, school: r.school, host: isHost(r), ageReview: ageReview(r), acceptedAt: r.acceptedAt })),
    );
  }
  if (path === "/admin/acceptances/send") {
    tickAcceptanceSend();
    if (method !== "POST") return respond(200, acceptanceSend);
    if (acceptanceSend.state === "SENDING") {
      return fail(409, "ACCEPTANCE_SEND_RUNNING", "Acceptance emails are already being sent. Wait for that to finish.");
    }
    const ids = registrations.filter(isWaiting).map((r) => r.id);
    if (ids.length === 0) return respond(200, { queued: 0, send: acceptanceSend });
    sendQueue = ids;
    acceptanceSend = { state: "SENDING", queued: ids.length, sent: 0, failed: 0, skipped: 0, startedAt: new Date().toISOString(), finishedAt: null, startedBy: currentAdmin.email };
    return respond(202, { queued: ids.length, send: acceptanceSend });
  }

  const resumeMatch = /^\/admin\/registrations\/([^/]+)\/resume$/.exec(path);
  if (resumeMatch) {
    const r = registrations.find((reg) => reg.id === resumeMatch[1]);
    if (!r) return fail(404, "NOT_FOUND", "Registration not found.");
    if (!r.resume) return fail(404, "NOT_FOUND", "This registration has no resume.");
    if (method === "DELETE") {
      r.resume = null;
      return respond(204);
    }
    return new Response(MOCK_PDF, {
      status: 200,
      headers: { "Content-Type": "application/pdf", "Content-Disposition": `attachment; filename="${r.resume.fileName}"` },
    });
  }
  if (path === "/admin/resumes/export.zip") {
    return new Response(EMPTY_ZIP, {
      status: 200,
      headers: { "Content-Type": "application/zip", "Content-Disposition": 'attachment; filename="peachhacks-resume-book-mock.zip"' },
    });
  }

  const badgeMatch = /^\/admin\/registrations\/([^/]+)\/badge$/.exec(path);
  if (badgeMatch && method === "DELETE") {
    if (!registrations.some((reg) => reg.id === badgeMatch[1])) return fail(404, "NOT_FOUND", "Registration not found.");
    if (!badges.delete(badgeMatch[1])) return fail(404, "NOT_FOUND", "This registration has no active badge.");
    return respond(204);
  }

  if (path === "/admin/badges/sponsors" && method === "GET") {
    return respond(200, [...sponsorBadges].sort((a, b) => (a.boundAt < b.boundAt ? 1 : -1)));
  }
  const sponsorMatch = /^\/admin\/badges\/sponsors\/([^/]+)$/.exec(path);
  if (sponsorMatch && method === "DELETE") {
    const index = sponsorBadges.findIndex((badge) => badge.uid === decodeURIComponent(sponsorMatch[1]).toUpperCase());
    if (index < 0) return fail(404, "NOT_FOUND", "This is not an active sponsor badge.");
    sponsorBadges.splice(index, 1);
    return respond(204);
  }

  const regMatch = /^\/admin\/registrations\/([^/]+)$/.exec(path);
  if (regMatch) {
    const index = registrations.findIndex((r) => r.id === regMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Registration not found.");
    if (method === "GET") return respond(200, detail(registrations[index]));
    if (method === "PATCH") {
      if ("status" in body && !VALID_STATUSES.includes(body.status)) {
        return fail(400, "VALIDATION_ERROR", "Invalid status.", { status: "Unknown status" });
      }
      if ("status" in body) changeStatus(registrations[index], body.status);
      if ("staff" in body) registrations[index].staff = Boolean(body.staff);
      return respond(200, detail(registrations[index]));
    }
  }

  if (path === "/admin/settings") {
    if (method === "PUT") {
      ["registrationOpen", "webCheckInAdminOnly"].forEach((key) => {
        if (key in body) settings[key] = Boolean(body[key]);
      });
    }
    return respond(200, { ...settings });
  }
  if (path === "/admin/settings/registration-preview") {
    settings.previewActive = method === "POST";
    if (method === "POST") return respond(200, { url: "https://www.peachhacks.com/?preview=mock-preview-key#register" });
    return respond(200, { ...settings });
  }

  if (path === "/admin/discord") return respond(200, discord);
  if (path === "/admin/discord/recap" && method === "POST") return respond(204);
  if (path === "/admin/discord/verification-message" && method === "POST") {
    discord.messagePostedAt = new Date().toISOString();
    if (body.message) discord.message = body.message;
    return respond(200, discord);
  }

  if (path === "/admin/emails/recipient-count" && method === "POST") {
    const fieldErrors = campaignErrors(body);
    if (Object.keys(fieldErrors).length) return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fieldErrors);
    return respond(200, { recipientCount: audiencePeople(body.kind, body.audience, body.school).length });
  }
  if (path === "/admin/emails/preview" && method === "POST") {
    const escape = (value) => String(value || "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
    const [firstName = "", ...rest] = currentAdmin.name.split(/\s+/);
    const personal = (value) =>
      String(value || "")
        .replace(/\{\{\s*firstName\s*\}\}/g, () => firstName)
        .replace(/\{\{\s*lastName\s*\}\}/g, () => rest.join(" "));
    const paragraphs = personal(body.body).split(/\n\s*\n/).map((p) => `<p>${escape(p.trim()).replaceAll("\n", "<br>")}</p>`).join("");
    const footer = body.kind === "EVENT_UPDATE"
      ? "You are receiving this because you registered for PeachHacks."
      : "You are receiving this because you signed up for PeachHacks. <u>Unsubscribe</u>";
    return respond(200, {
      subject: personal(body.subject),
      html: `<!doctype html><html><body style="margin:0;font-family:Arial,sans-serif;color:#233e56;background:#f4f6ed"><div style="background:#001f3a;color:#FCA324;padding:24px;font-size:24px;font-weight:bold;text-align:center">PeachHacks</div><div style="background:#fff;padding:24px">${paragraphs}</div><div style="background:#001f3a;color:#b9d3dc;padding:16px 24px;font-size:12px">${footer}<br>Mock preview: the real API renders the full design.</div></body></html>`,
    });
  }
  if (path === "/admin/emails/test" && method === "POST") {
    if (!body.subject || !body.body) return fail(400, "VALIDATION_ERROR", "Subject and body are required.");
    return respond(204);
  }
  if (path === "/admin/emails") {
    if (method === "POST") {
      const fieldErrors = campaignErrors(body);
      if (!body.subject?.trim()) fieldErrors.subject = "Subject is required";
      if (!body.body?.trim()) fieldErrors.body = "Body is required";
      if (Object.keys(fieldErrors).length) return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fieldErrors);
      tickCampaigns();
      const same = (c) =>
        ["kind", "audience", "subject", "body"].every((key) => c[key] === body[key]) && (c.school || null) === (body.school || null);
      if (campaigns.some((c) => ACTIVE_CAMPAIGN.includes(c.status) && same(c))) {
        return fail(409, "CAMPAIGN_ALREADY_SENDING", "This exact email is already being sent. Wait for it to finish before sending it again.");
      }
      const people = audiencePeople(body.kind, body.audience, body.school);
      const campaign = {
        id: uuid(),
        kind: body.kind,
        subject: body.subject,
        body: body.body,
        audience: body.audience,
        school: body.school || null,
        recipientCount: people.length,
        sentCount: 0,
        failedCount: 0,
        status: "QUEUED",
        createdBy: currentAdmin.name,
        createdAt: new Date().toISOString(),
        completedAt: null,
      };
      campaigns.unshift(campaign);
      campaignPeople.set(campaign.id, people);
      return respond(202, campaign);
    }
    tickCampaigns();
    return respond(200, campaigns);
  }

  const recipientsMatch = /^\/admin\/emails\/([^/]+)\/recipients$/.exec(path);
  if (recipientsMatch) {
    tickCampaigns();
    const campaign = campaigns.find((c) => c.id === recipientsMatch[1]);
    if (!campaign) return fail(404, "NOT_FOUND", "Email not found.");
    const status = params.get("status") || "";
    return respond(200, paginate(campaignRecipients(campaign).filter((r) => !status || r.status === status), params));
  }

  if (path === "/admin/admins") {
    if (method === "POST") {
      const fieldErrors = {};
      if (!body.name?.trim()) fieldErrors.name = "Name is required";
      if (!/^\S+@\S+\.\S+$/.test(body.email || "")) fieldErrors.email = "Must be a valid email";
      else if (admins.some((a) => a.email.toLowerCase() === body.email.toLowerCase())) fieldErrors.email = "An account with this email already exists";
      if (body.role && !["ADMIN", "VOLUNTEER", "LOOKUP"].includes(body.role)) fieldErrors.role = "Role must be ADMIN, VOLUNTEER or LOOKUP";
      if (Object.keys(fieldErrors).length) return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fieldErrors);
      const admin = { id: uuid(), email: body.email, name: body.name, role: body.role || "ADMIN", createdAt: new Date().toISOString(), pending: true };
      admins.push(admin);
      return respond(201, { ...admin, setPasswordUrl: MOCK_PASSWORD_URL });
    }
    return respond(200, admins);
  }
  const inviteMatch = /^\/admin\/admins\/([^/]+)\/invite$/.exec(path);
  if (inviteMatch && method === "POST") {
    const invited = admins.find((a) => a.id === inviteMatch[1]);
    if (!invited) return fail(404, "NOT_FOUND", "Admin not found.");
    if (!invited.pending) return fail(400, "VALIDATION_ERROR", "This account already has a password.");
    return respond(200, { ...invited, setPasswordUrl: MOCK_PASSWORD_URL });
  }
  const adminMatch = /^\/admin\/admins\/([^/]+)$/.exec(path);
  if (adminMatch && method === "DELETE") {
    const index = admins.findIndex((a) => a.id === adminMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Admin not found.");
    if (admins[index].id === currentAdmin.id) return fail(400, "VALIDATION_ERROR", "You cannot remove your own account.");
    if (admins[index].role === "ADMIN" && admins.filter((a) => a.role === "ADMIN").length === 1) {
      return fail(400, "VALIDATION_ERROR", "You cannot remove the last admin.");
    }
    admins.splice(index, 1);
    return respond(204);
  }

  return fail(404, "NOT_FOUND", `No mock for ${method} ${path}`);
}

export function mockFetch(url, init = {}) {
  const parsed = new URL(url);
  const method = (init.method || "GET").toUpperCase();
  const token = (init.headers?.Authorization || "").replace(/^Bearer\s+/i, "") || null;
  let body = {};
  try {
    body = init.body ? JSON.parse(init.body) : {};
  } catch {
    body = {};
  }
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      resolve(handle(method, parsed.pathname, parsed.searchParams, body, token));
    }, 180 + Math.random() * 220);
    init.signal?.addEventListener("abort", () => {
      window.clearTimeout(timer);
      reject(new DOMException("Aborted", "AbortError"));
    });
  });
}
