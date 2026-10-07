import { useId, useMemo, useState } from "react";
import { api } from "../api.js";
import { href, useLoad } from "../hooks.js";
import { Avatar, DiscordHandle, ErrorNote, fullName } from "../ui.jsx";

const loadHackers = (signal) => api.hackers(signal);

const STATUSES = [
  { value: "", label: "Any status" },
  { value: "looking", label: "Looking for a team" },
  { value: "teamed", label: "On a team" },
  { value: "solo", label: "Not on a team" },
];

const safeLink = (url) => (typeof url === "string" && url.startsWith("https://") ? url : null);

function HackerCard({ hacker, isMe }) {
  const linkedin = safeLink(hacker.linkedinUrl);
  const github = safeLink(hacker.githubUrl);
  return (
    <li className="card hacker">
      <div className="hacker-head">
        <Avatar firstName={hacker.firstName} lastName={hacker.lastName} id={hacker.id} />
        <div>
          <h2>
            {fullName(hacker)} {isMe && <span className="pill pill-quiet">You</span>}
          </h2>
          <p className="muted">{hacker.school}</p>
        </div>
      </div>
      <div className="pill-row">
        {hacker.teamName && <span className="pill pill-team">Team {hacker.teamName}</span>}
        {hacker.lookingForTeam && <span className="pill pill-good">Looking for a team</span>}
      </div>
      {hacker.bio && <p className="hacker-bio">{hacker.bio}</p>}
      <div className="hacker-foot">
        <DiscordHandle username={hacker.discordUsername} />
        {(linkedin || github) && (
          <p className="hacker-links">
            {linkedin && (
              <a href={linkedin} target="_blank" rel="noreferrer">
                LinkedIn
              </a>
            )}
            {github && (
              <a href={github} target="_blank" rel="noreferrer">
                GitHub
              </a>
            )}
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
