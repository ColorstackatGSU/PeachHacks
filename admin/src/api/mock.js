// Loaded only by the dev server when VITE_MOCK_API=1 (see client.js); never part
// of a production build. Data resets on every page reload.

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

const SCHOOLS = [
  "Georgia State University",
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
const LEVELS = ["Undergraduate University (3+ year)", "Undergraduate University (2 year)", "Graduate University (Masters, Professional, Doctoral, etc)", "Code School / Bootcamp", "High School"];
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

function makeRegistration(base, createdAt) {
  const veg = rand();
  const resumeRoll = rand();
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
    shippingAddress: rand() > 0.6
      ? { line1: `${100 + Math.floor(rand() * 900)} Peachtree St NE`, line2: rand() > 0.7 ? "Apt 4B" : null, city: "Atlanta", state: "GA", country: "US", postalCode: "30303" }
      : null,
    majorFieldOfStudy: pickSkewed(MAJORS),
    majorOther: null,
    linkedinUrl: rand() > 0.5 ? `https://www.linkedin.com/in/${base.email.split("@")[0].replace(/\./g, "-")}` : null,
    status: pick(STATUSES),
    ticketToken: `mockticket${String(idCounter).padStart(12, "0")}`,
    resume: resumeRoll > 0.45
      ? { fileName: `${base.firstName}_${base.lastName}_Resume.pdf`, size: 60000 + Math.floor(rand() * 900000), uploadedAt: createdAt }
      : null,
    resumeOptIn: resumeRoll > 0.65,
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
];
const settings = { registrationOpen: false };
// One token per role so a reload keeps whichever account was signed in.
const TOKENS = { "mock-token": admins[0], "mock-volunteer-token": admins[2] };
const sessions = new Set(Object.keys(TOKENS));

const events = [
  { id: uuid(), name: "General check-in", startsAt: null, general: true },
  { id: uuid(), name: "Intro to React workshop", startsAt: new Date(now + 3 * 3600000).toISOString(), general: false },
];
const generalEvent = events[0];
const checkIns = [];
registrations
  .filter((r) => r.status === "ACCEPTED")
  .forEach((r, index) => {
    if (index % 3 === 0) {
      checkIns.push({ registrationId: r.id, eventId: generalEvent.id, checkedInAt: new Date(now - index * 60000).toISOString(), checkedInBy: "Door Volunteer" });
    }
    if (index % 6 === 0) {
      checkIns.push({ registrationId: r.id, eventId: events[1].id, checkedInAt: new Date(now - index * 30000).toISOString(), checkedInBy: "Logistics Lead" });
    }
  });

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
];

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
  return list
    .filter((item) => {
      if (school && item.school !== school) return false;
      if (status && item.status !== status) return false;
      if (checkedIn && String(Boolean(findCheckIn(item.id, generalEvent.id))) !== checkedIn) return false;
      if (resume === "any" && !item.resume) return false;
      if (resume === "none" && item.resume) return false;
      if (resume === "opted-in" && !(item.resume && item.resumeOptIn)) return false;
      if (schoolEmailConfirmed && String(Boolean(item.schoolEmailConfirmed)) !== schoolEmailConfirmed) return false;
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
  const { id, firstName, lastName, email, schoolEmail, schoolEmailConfirmed, schoolEmailConfirmedAt, school, levelOfStudy, countryOfResidence, age, status, createdAt } = r;
  const checkedInAt = findCheckIn(id, generalEvent.id)?.checkedInAt || null;
  return {
    id, firstName, lastName, email, schoolEmail, schoolEmailConfirmed, schoolEmailConfirmedAt, school, levelOfStudy, countryOfResidence, age, status, createdAt, checkedInAt,
    hasResume: Boolean(r.resume),
    resumeOptIn: Boolean(r.resume && r.resumeOptIn),
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

function audienceEmails(audience, school) {
  const registered = registeredEmails();
  let pool;
  if (audience === "REGISTRANTS") pool = registrations.map((r) => ({ email: r.email, school: r.school }));
  else pool = preRegistrations.filter((p) => !p.unsubscribed);
  if (audience === "PRE_REGISTRANTS_NOT_REGISTERED") pool = pool.filter((p) => !registered.has(p.email.toLowerCase()));
  if (school) pool = pool.filter((p) => p.school === school);
  return new Set(pool.map((p) => p.email.toLowerCase()));
}

// Campaigns advance on a clock so the history list can be seen refreshing.
function tickCampaigns() {
  campaigns.forEach((c) => {
    if (c.status !== "QUEUED" && c.status !== "SENDING") return;
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

function handle(method, path, params, body, token) {
  if (path === "/admin/auth/login" && method === "POST") {
    if (!body.email || !body.password || body.password === "wrong") {
      return fail(401, "INVALID_CREDENTIALS", "Incorrect email or password.");
    }
    const mockToken = /^volunteer/i.test(body.email) ? "mock-volunteer-token" : "mock-token";
    sessions.add(mockToken);
    return respond(200, { token: mockToken, expiresAt: new Date(Date.now() + 8 * 3600000).toISOString(), admin: TOKENS[mockToken] });
  }

  if (!token || !sessions.has(token)) return fail(401, "UNAUTHORIZED", "Sign in to continue.");
  const currentAdmin = TOKENS[token];

  const open = path.startsWith("/admin/auth/") || path === "/admin/check-in" || path.startsWith("/admin/check-in/") || (path === "/admin/events" && method === "GET");
  if (currentAdmin.role === "VOLUNTEER" && !open) return fail(403, "FORBIDDEN", "You do not have access to this resource.");

  if (path === "/admin/auth/logout" && method === "POST") {
    sessions.delete(token);
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
      const [removed] = events.splice(index, 1);
      for (let i = checkIns.length - 1; i >= 0; i -= 1) {
        if (checkIns[i].eventId === removed.id) checkIns.splice(i, 1);
      }
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
      if (r.status !== "ACCEPTED" && !body.override) return respond(200, { result: "NOT_ACCEPTED", event: eventRef, item: checkInItem(r, event) });
      record(r);
      return respond(200, { result: "CHECKED_IN", event: eventRef, item: checkInItem(r, event) });
    }
    const r = registrations.find((reg) => reg.id === path.split("/").pop());
    if (!r) return fail(404, "NOT_FOUND", "Registration not found.");
    if (method === "POST") record(r);
    if (method === "DELETE") {
      const index = checkIns.findIndex((c) => c.registrationId === r.id && c.eventId === event.id);
      if (index >= 0) checkIns.splice(index, 1);
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
        resumeOptIn: registrations.filter((r) => r.resume && r.resumeOptIn).length,
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
          resume_opt_in: Boolean(r.resume && r.resumeOptIn),
          school_email: r.schoolEmail,
          school_email_confirmed: r.schoolEmailConfirmed,
        };
        ["ticketToken", "resume", "resumeOptIn", "schoolEmail", "schoolEmailConfirmed", "schoolEmailConfirmedAt"].forEach((key) => delete row[key]);
        return row;
      }),
      "registrations.csv",
    );
  }

  const preMatch = /^\/admin\/pre-registrations\/([^/]+)$/.exec(path);
  if (preMatch && method === "DELETE") {
    const index = preRegistrations.findIndex((p) => p.id === preMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Pre-registration not found.");
    preRegistrations.splice(index, 1);
    return respond(204);
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
    return respond(204);
  }

  const resumeMatch = /^\/admin\/registrations\/([^/]+)\/resume$/.exec(path);
  if (resumeMatch) {
    const r = registrations.find((reg) => reg.id === resumeMatch[1]);
    if (!r) return fail(404, "NOT_FOUND", "Registration not found.");
    if (!r.resume) return fail(404, "NOT_FOUND", "This registration has no resume.");
    if (method === "DELETE") {
      r.resume = null;
      r.resumeOptIn = false;
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

  const regMatch = /^\/admin\/registrations\/([^/]+)$/.exec(path);
  if (regMatch) {
    const index = registrations.findIndex((r) => r.id === regMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Registration not found.");
    if (method === "GET") return respond(200, detail(registrations[index]));
    if (method === "PATCH") {
      if (!["PENDING", "ACCEPTED", "WAITLISTED", "REJECTED"].includes(body.status)) {
        return fail(400, "VALIDATION_ERROR", "Invalid status.", { status: "Unknown status" });
      }
      registrations[index] = { ...registrations[index], status: body.status };
      return respond(200, detail(registrations[index]));
    }
    if (method === "DELETE") {
      const [removed] = registrations.splice(index, 1);
      for (let i = checkIns.length - 1; i >= 0; i -= 1) {
        if (checkIns[i].registrationId === removed.id) checkIns.splice(i, 1);
      }
      return respond(204);
    }
  }

  if (path === "/admin/settings") {
    if (method === "PUT") settings.registrationOpen = Boolean(body.registrationOpen);
    return respond(200, { registrationOpen: settings.registrationOpen });
  }

  if (path === "/admin/emails/recipient-count" && method === "POST") {
    return respond(200, { recipientCount: audienceEmails(body.audience, body.school).size });
  }
  if (path === "/admin/emails/test" && method === "POST") {
    if (!body.subject || !body.body) return fail(400, "VALIDATION_ERROR", "Subject and body are required.");
    return respond(204);
  }
  if (path === "/admin/emails") {
    if (method === "POST") {
      const fieldErrors = {};
      if (!body.subject?.trim()) fieldErrors.subject = "Subject is required";
      if (!body.body?.trim()) fieldErrors.body = "Body is required";
      if (Object.keys(fieldErrors).length) return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fieldErrors);
      const campaign = {
        id: uuid(),
        subject: body.subject,
        body: body.body,
        audience: body.audience,
        school: body.school || null,
        recipientCount: audienceEmails(body.audience, body.school).size,
        sentCount: 0,
        failedCount: 0,
        status: "QUEUED",
        createdBy: currentAdmin.name,
        createdAt: new Date().toISOString(),
        completedAt: null,
      };
      campaigns.unshift(campaign);
      return respond(202, campaign);
    }
    tickCampaigns();
    return respond(200, campaigns);
  }

  if (path === "/admin/admins") {
    if (method === "POST") {
      const fieldErrors = {};
      if (!body.name?.trim()) fieldErrors.name = "Name is required";
      if (!/^\S+@\S+\.\S+$/.test(body.email || "")) fieldErrors.email = "Must be a valid email";
      else if (admins.some((a) => a.email.toLowerCase() === body.email.toLowerCase())) fieldErrors.email = "An account with this email already exists";
      if ((body.password || "").length < 10) fieldErrors.password = "Must be at least 10 characters";
      if (body.role && !["ADMIN", "VOLUNTEER"].includes(body.role)) fieldErrors.role = "Role must be ADMIN or VOLUNTEER";
      if (Object.keys(fieldErrors).length) return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fieldErrors);
      const admin = { id: uuid(), email: body.email, name: body.name, role: body.role || "ADMIN", createdAt: new Date().toISOString() };
      admins.push(admin);
      return respond(201, admin);
    }
    return respond(200, admins);
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
