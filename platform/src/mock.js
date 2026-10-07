// In-memory stand-in for the API, used only by `npm run dev` with VITE_MOCK_API=1.
// Any email and password sign in.

const MAX_TEAM_SIZE = 4;
const ME = "h-me";

const hackers = [
  { id: ME, firstName: "Ada", lastName: "Example", school: "Georgia State University", bio: "Second-year CS. I like maps and small tools.", discordUsername: null, githubUrl: "https://github.com/ada", linkedinUrl: null, lookingForTeam: true, listed: true },
  { id: "h-2", firstName: "Grace", lastName: "Okafor", school: "Georgia State University", bio: "Backend and databases. First hackathon.", discordUsername: "graceo", githubUrl: "https://github.com/graceo", linkedinUrl: "https://www.linkedin.com/in/graceo", lookingForTeam: false, listed: true },
  { id: "h-3", firstName: "Linus", lastName: "Tran", school: "Georgia Institute of Technology", bio: null, discordUsername: "linus.t", githubUrl: null, linkedinUrl: null, lookingForTeam: true, listed: true },
  { id: "h-4", firstName: "Maya", lastName: "Reyes", school: "Kennesaw State University", bio: "Design student who codes. Looking for people who want to ship something polished.", discordUsername: "mayareyes", githubUrl: null, linkedinUrl: "https://www.linkedin.com/in/mayareyes", lookingForTeam: false, listed: true },
  { id: "h-5", firstName: "Sam", lastName: "Whitfield", school: "Georgia State University", bio: "Hardware, embedded, anything with a soldering iron.", discordUsername: null, githubUrl: "https://github.com/samw", linkedinUrl: null, lookingForTeam: true, listed: true },
];

let teams = [
  { id: "t-1", name: "Night Owls", description: "A transit helper for Atlanta. Need someone who likes frontend.", ownerId: "h-2", members: ["h-2", "h-4"], requests: [] },
];

const session = { hasPassword: true };
const person = (id) => hackers.find((hacker) => hacker.id === id);
const teamOf = (id) => teams.find((team) => team.members.includes(id)) || null;

function respond(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const fail = (status, code, message, fieldErrors) => respond(status, { code, message, fieldErrors });

function teamView(team) {
  return {
    id: team.id,
    name: team.name,
    description: team.description,
    members: team.members.map((id) => ({ ...person(id), owner: id === team.ownerId })),
    openSpots: Math.max(0, MAX_TEAM_SIZE - team.members.length),
    requested: team.requests.some((request) => request.hackerId === ME),
    requests: team.members.includes(ME)
      ? team.requests.map((request) => ({ ...person(request.hackerId), ...request }))
      : [],
  };
}

function meView() {
  const me = person(ME);
  const team = teamOf(ME);
  return {
    id: ME,
    firstName: me.firstName,
    lastName: me.lastName,
    email: "ada@example.com",
    school: me.school,
    profile: { bio: me.bio, githubUrl: me.githubUrl, linkedinUrl: me.linkedinUrl, lookingForTeam: me.lookingForTeam && !team, listed: me.listed },
    discordUsername: me.discordUsername,
    hasPassword: session.hasPassword,
    ticket: { token: "mock", url: "https://www.peachhacks.com/ticket?t=mock", googleWalletUrl: "https://pay.google.com/gp/v/save/mock", checkedIn: false },
    teamId: team?.id || null,
  };
}

export async function mockFetch(url, init = {}) {
  await new Promise((resolve) => setTimeout(resolve, 150));
  const path = new URL(url).pathname;
  const method = init.method || "GET";
  const body = init.body ? JSON.parse(init.body) : {};

  if (path === "/platform/config") {
    return respond(200, { googleSignIn: true, discordClientId: null, discordRedirectUri: `${window.location.origin}/`, maxTeamSize: MAX_TEAM_SIZE });
  }
  if (path === "/platform/auth/login" || path === "/platform/auth/set-password") {
    return respond(200, { token: "mock-session", expiresAt: new Date(Date.now() + 864e5).toISOString() });
  }
  if (path.startsWith("/platform/auth/")) return respond(204);

  if (path === "/platform/me") {
    if (method === "PATCH") {
      if (body.githubUrl && !body.githubUrl.startsWith("https://github.com/")) {
        return fail(400, "VALIDATION_ERROR", "Please check the highlighted fields.", { githubUrl: "Use your profile address, like https://github.com/you" });
      }
      Object.assign(person(ME), body);
    }
    return respond(200, meView());
  }
  if (path === "/platform/hackers") {
    return respond(
      200,
      hackers
        .filter((hacker) => hacker.listed)
        .map((hacker) => {
          const team = teamOf(hacker.id);
          return { ...hacker, lookingForTeam: hacker.lookingForTeam && !team, teamId: team?.id || null, teamName: team?.name || null };
        }),
    );
  }
  if (path === "/platform/teams" && method === "GET") {
    const views = teams.map(teamView);
    views.sort((a, b) => Number(b.members.some((m) => m.id === ME)) - Number(a.members.some((m) => m.id === ME)));
    return respond(200, views);
  }
  if (path === "/platform/teams" && method === "POST") {
    if (teams.some((team) => team.name.toLowerCase() === body.name.toLowerCase())) {
      return fail(400, "VALIDATION_ERROR", "Please check the highlighted fields.", { name: "There is already a team with that name." });
    }
    const team = { id: `t-${Date.now()}`, name: body.name, description: body.description, ownerId: ME, members: [ME], requests: [{ id: "r-1", hackerId: "h-3", message: "I can do the backend." }] };
    teams.push(team);
    return respond(201, { id: team.id });
  }
  const mine = teamOf(ME);
  if (path === "/platform/teams/mine" && mine) {
    Object.assign(mine, { name: body.name, description: body.description });
    return respond(204);
  }
  if (path === "/platform/teams/mine/leave" && mine) {
    mine.members = mine.members.filter((id) => id !== ME);
    if (mine.members.length === 0) teams = teams.filter((team) => team !== mine);
    else mine.ownerId = mine.members[0];
    return respond(204);
  }
  const answer = path.match(/^\/platform\/teams\/mine\/requests\/([^/]+)\/(accept|decline)$/);
  if (answer && mine) {
    const request = mine.requests.find((item) => item.id === answer[1]);
    mine.requests = mine.requests.filter((item) => item !== request);
    if (request && answer[2] === "accept") mine.members.push(request.hackerId);
    return respond(204);
  }
  const member = path.match(/^\/platform\/teams\/mine\/members\/([^/]+)$/);
  if (member && mine) {
    mine.members = mine.members.filter((id) => id !== member[1]);
    return respond(204);
  }
  const requests = path.match(/^\/platform\/teams\/([^/]+)\/requests$/);
  if (requests) {
    const team = teams.find((item) => item.id === requests[1]);
    if (!team) return fail(404, "NOT_FOUND", "That team no longer exists.");
    team.requests = team.requests.filter((request) => request.hackerId !== ME);
    if (method === "POST") team.requests.push({ id: `r-${Date.now()}`, hackerId: ME, message: body.message });
    return respond(204);
  }
  return fail(404, "NOT_FOUND", "Not found.");
}
