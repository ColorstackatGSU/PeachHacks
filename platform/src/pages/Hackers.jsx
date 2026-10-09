import { useId, useMemo, useState } from "react";
import { api } from "../api.js";
import { href, useLoad } from "../hooks.js";
import { ErrorNote, fullName, toneOf } from "../ui.jsx";

const loadHackers = (signal) => api.hackers(signal);

const STATUSES = [
  { value: "", label: "Any status" },
  { value: "looking", label: "Looking for a team" },
  { value: "teamed", label: "On a team" },
  { value: "solo", label: "Not on a team" },
];

const safeLink = (url) => (typeof url === "string" && url.startsWith("https://") ? url : null);

const ICONS = {
  linkedin:
    "M20.45 20.45h-3.56v-5.57c0-1.33-.02-3.04-1.85-3.04-1.85 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.06 2.06 0 1 1 0-4.13 2.06 2.06 0 0 1 0 4.13zM7.12 20.45H3.56V9h3.56v11.45z",
  github:
    "M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2c-3.2.7-3.88-1.36-3.88-1.36-.52-1.33-1.28-1.69-1.28-1.69-1.05-.72.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.42-2.7 5.4-5.26 5.68.41.36.78 1.06.78 2.14v3.17c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5z",
};

function ProfileLink({ kind, label, url, name }) {
  return (
    <a className="hacker-link" href={url} target="_blank" rel="noreferrer" aria-label={`${name} on ${label}`}>
      <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
        <path fill="currentColor" d={ICONS[kind]} />
      </svg>
      {label}
    </a>
  );
}

// The whole row copies the username: on a phone that is a far easier target than a small button.
function DiscordChip({ username }) {
  const [copied, setCopied] = useState(false);
  if (!username) return <p className="hacker-discord hacker-discord-none">Discord not connected yet</p>;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(username);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };
  return (
    <button type="button" className="hacker-discord" onClick={copy} aria-label={`Copy Discord username ${username}`}>
      <img src="/assets/discord_logo.png" alt="" width="22" height="22" />
      <span className="hacker-discord-name">{username}</span>
      <span className="hacker-discord-copy" aria-live="polite">
        {copied ? "Copied" : "Copy"}
      </span>
    </button>
  );
}

// A badge: a strip of the PeachHacks night sky across the top with a lanyard slot, the
// hacker's initials stamped over its edge, then who they are and how to reach them.
function HackerCard({ hacker, isMe }) {
  const linkedin = safeLink(hacker.linkedinUrl);
  const github = safeLink(hacker.githubUrl);
  const name = fullName(hacker);
  const initials = `${hacker.firstName?.[0] || ""}${hacker.lastName?.[0] || ""}`.toUpperCase() || "?";
  return (
    <li className={`hacker hacker-tone-${toneOf(hacker.id)}${isMe ? " hacker-me" : ""}`}>
      <div className="hacker-band" aria-hidden="true">
        <span className="hacker-slot" />
        <span className="hacker-moon" />
      </div>
      <div className="hacker-body">
        <div className="hacker-top">
          <span className="hacker-avatar" aria-hidden="true">
            {initials}
          </span>
          {isMe && <span className="hacker-you">You</span>}
        </div>
        <h2>{name}</h2>
        <p className="hacker-school">{hacker.school}</p>
        <div className="pill-row">
          {hacker.teamName && <span className="pill pill-team">Team {hacker.teamName}</span>}
          {hacker.lookingForTeam && (
            <span className="pill pill-good">
              <span className="pill-dot" />
              Looking for a team
            </span>
          )}
        </div>
        {hacker.bio && <p className="hacker-bio">{hacker.bio}</p>}
      </div>
      <div className="hacker-foot">
        <DiscordChip username={hacker.discordUsername} />
        {(linkedin || github) && (
          <p className="hacker-links">
            {linkedin && <ProfileLink kind="linkedin" label="LinkedIn" url={linkedin} name={name} />}
            {github && <ProfileLink kind="github" label="GitHub" url={github} name={name} />}
          </p>
        )}
      </div>
    </li>
  );
}

export default function Hackers({ me }) {
  const ids = useId();
  const hackers = useLoad(loadHackers);
  const [query, setQuery] = useState("");
  const [school, setSchool] = useState("");
  const [status, setStatus] = useState("");

  const all = useMemo(() => hackers.data || [], [hackers.data]);
  const schools = useMemo(() => [...new Set(all.map((hacker) => hacker.school))].sort(), [all]);

  const shown = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return all.filter((hacker) => {
      if (school && hacker.school !== school) return false;
      if (status === "looking" && !hacker.lookingForTeam) return false;
      if (status === "teamed" && !hacker.teamId) return false;
      if (status === "solo" && hacker.teamId) return false;
      if (!needle) return true;
      return [fullName(hacker), hacker.teamName, hacker.bio, hacker.discordUsername]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(needle));
    });
  }, [all, query, school, status]);

  return (
    <>
      <header className="page-head">
        <h1>Hackers</h1>
        <p className="muted">
          Accepted hackers who have signed in here. Copy a Discord username to say hello, or ask to join a team on the{" "}
          <a href={href("/teams")}>Teams</a> page.
        </p>
      </header>

      {!me.profile.listed && (
        <p className="note">
          You are hidden from this list. Turn on “Show me on the Hackers page” on <a href={href("/")}>Home</a> to appear.
        </p>
      )}

      <div className="filters">
        <div className="field">
          <label htmlFor={`${ids}-q`}>Search</label>
          <input
            id={`${ids}-q`}
            type="search"
            placeholder="Name, team, Discord or a word from their card"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor={`${ids}-school`}>School</label>
          <select id={`${ids}-school`} value={school} onChange={(event) => setSchool(event.target.value)}>
            <option value="">All schools</option>
            {schools.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${ids}-status`}>Team status</label>
          <select id={`${ids}-status`} value={status} onChange={(event) => setStatus(event.target.value)}>
            {STATUSES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <ErrorNote error={hackers.error} onRetry={hackers.reload} />
      {!hackers.data && !hackers.error && <p className="muted">Loading hackers…</p>}

      {hackers.data && (
        <>
          <p className="count" role="status">
            {shown.length === all.length
              ? `${all.length} ${all.length === 1 ? "hacker" : "hackers"}`
              : `${shown.length} of ${all.length} hackers`}
          </p>
          {shown.length === 0 ? (
            <p className="empty">
              {all.length === 0 ? "Nobody is listed yet. You are early." : "Nobody matches those filters."}
            </p>
          ) : (
            <ul className="card-grid">
              {shown.map((hacker) => (
                <HackerCard key={hacker.id} hacker={hacker} isMe={hacker.id === me.id} />
              ))}
            </ul>
          )}
        </>
      )}
    </>
  );
}
