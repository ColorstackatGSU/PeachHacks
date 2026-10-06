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

function makeRegistration(base, createdAt) {
  const veg = rand();
  return {
    id: uuid(),
    firstName: base.firstName,
    lastName: base.lastName,
    age: 17 + Math.floor(rand() * 9),
    phone: `+1 404 555 ${String(1000 + Math.floor(rand() * 9000))}`,
    email: base.email,
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
  { id: uuid(), email: "admin@peachhacks.local", name: "Local Admin", createdAt: new Date(now - 60 * DAY).toISOString() },
  { id: uuid(), email: "logistics@peachhacks.local", name: "Logistics Lead", createdAt: new Date(now - 21 * DAY).toISOString() },
];
const currentAdmin = admins[0];
const settings = { registrationOpen: false };
const sessions = new Set(["mock-token"]);

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
  return list
    .filter((item) => {
      if (school && item.school !== school) return false;
      if (status && item.status !== status) return false;
      if (!q) return true;
      return `${item.firstName} ${item.lastName} ${item.email}`.toLowerCase().includes(q);
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
  const { id, firstName, lastName, email, school, levelOfStudy, countryOfResidence, age, status, createdAt } = r;
  return { id, firstName, lastName, email, school, levelOfStudy, countryOfResidence, age, status, createdAt };
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
    sessions.add("mock-token");
    return respond(200, { token: "mock-token", expiresAt: new Date(Date.now() + 8 * 3600000).toISOString(), admin: currentAdmin });
  }

  if (!token || !sessions.has(token)) return fail(401, "UNAUTHORIZED", "Sign in to continue.");

  if (path === "/admin/auth/logout" && method === "POST") {
    sessions.delete(token);
    return respond(204);
  }
  if (path === "/admin/auth/me") {
    return respond(200, { id: currentAdmin.id, email: currentAdmin.email, name: currentAdmin.name });
  }

  if (path === "/admin/stats") {
    const registered = registeredEmails();
    return respond(200, {
      registrationOpen: settings.registrationOpen,
      preRegistrations: {
        total: preRegistrations.length,
        unsubscribed: preRegistrations.filter((p) => p.unsubscribed).length,
        bySchool: bySchool(preRegistrations),
        byDay: byDay(preRegistrations),
      },
      registrations: {
        total: registrations.length,
        bySchool: bySchool(registrations),
        byDay: byDay(registrations),
        byLevelOfStudy: groupCount(registrations, (r) => r.levelOfStudy, "label").sort((a, b) => b.count - a.count),
        byStatus: groupCount(registrations, (r) => r.status, "label"),
      },
      preRegisteredNotRegistered: preRegistrations.filter((p) => !registered.has(p.email.toLowerCase())).length,
    });
  }

  if (path === "/admin/pre-registrations") {
    return respond(200, paginate(withRegistered(filterPeople(preRegistrations, params)), params));
  }
  if (path === "/admin/pre-registrations/export.csv") {
    return csvResponse(withRegistered(filterPeople(preRegistrations, params)), "pre-registrations.csv");
  }
  if (path === "/admin/registrations") {
    return respond(200, paginate(filterPeople(registrations, params).map(summary), params));
  }
  if (path === "/admin/registrations/export.csv") {
    return csvResponse(filterPeople(registrations, params), "registrations.csv");
  }

  const preMatch = /^\/api\/admin\/pre-registrations\/([^/]+)$/.exec(path);
  if (preMatch && method === "DELETE") {
    const index = preRegistrations.findIndex((p) => p.id === preMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Pre-registration not found.");
    preRegistrations.splice(index, 1);
    return respond(204);
  }

  const regMatch = /^\/api\/admin\/registrations\/([^/]+)$/.exec(path);
  if (regMatch) {
    const index = registrations.findIndex((r) => r.id === regMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Registration not found.");
    if (method === "GET") return respond(200, registrations[index]);
    if (method === "PATCH") {
      if (!["PENDING", "ACCEPTED", "WAITLISTED", "REJECTED"].includes(body.status)) {
        return fail(400, "VALIDATION_ERROR", "Invalid status.", { status: "Unknown status" });
      }
      registrations[index] = { ...registrations[index], status: body.status };
      return respond(200, registrations[index]);
    }
    if (method === "DELETE") {
      registrations.splice(index, 1);
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
      else if (admins.some((a) => a.email.toLowerCase() === body.email.toLowerCase())) fieldErrors.email = "An admin with this email already exists";
      if ((body.password || "").length < 10) fieldErrors.password = "Must be at least 10 characters";
      if (Object.keys(fieldErrors).length) return fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fieldErrors);
      const admin = { id: uuid(), email: body.email, name: body.name, createdAt: new Date().toISOString() };
      admins.push(admin);
      return respond(201, admin);
    }
    return respond(200, admins);
  }
  const adminMatch = /^\/api\/admin\/admins\/([^/]+)$/.exec(path);
  if (adminMatch && method === "DELETE") {
    const index = admins.findIndex((a) => a.id === adminMatch[1]);
    if (index < 0) return fail(404, "NOT_FOUND", "Admin not found.");
    if (admins[index].id === currentAdmin.id) return fail(400, "VALIDATION_ERROR", "You cannot remove your own account.");
    if (admins.length === 1) return fail(400, "VALIDATION_ERROR", "You cannot remove the last admin.");
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
