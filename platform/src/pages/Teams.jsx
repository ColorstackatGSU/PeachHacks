import { useId, useState } from "react";
import { api } from "../api.js";
import { useLoad } from "../hooks.js";
import { Avatar, DiscordHandle, ErrorNote, FieldError, fullName, useToast } from "../ui.jsx";

const loadTeams = (signal) => api.teams(signal);

const spots = (count) => (count === 0 ? "Full" : `${count} ${count === 1 ? "spot" : "spots"} open`);

function MemberList({ team, me, onRemove }) {
  const leading = team.members.some((member) => member.owner && member.id === me.id);
  return (
    <ul className="members">
      {team.members.map((member) => (
        <li key={member.id}>
          <Avatar firstName={member.firstName} lastName={member.lastName} id={member.id} />
          <div className="member-text">
            <p>
              <strong>{fullName(member)}</strong> {member.owner && <span className="pill pill-quiet">Team lead</span>}
            </p>
            <p className="muted">{member.school}</p>
          </div>
          {onRemove && <DiscordHandle username={member.discordUsername} />}
          {onRemove && leading && member.id !== me.id && (
            <button type="button" className="btn btn-small btn-danger-quiet" onClick={() => onRemove(member)}>
              Remove
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

function TeamForm({ initial, submitLabel, onSubmit, onCancel }) {
  const ids = useId();
  const [name, setName] = useState(initial?.name || "");
  const [description, setDescription] = useState(initial?.description || "");
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (name.trim().length < 2) {
      setFieldErrors({ name: "Give the team a name of 2 to 60 characters." });
      return;
    }
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await onSubmit({ name: name.trim(), description: description.trim() || null });
    } catch (failure) {
      if (failure.fieldErrors) setFieldErrors(failure.fieldErrors);
      else setError(failure);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="stack" onSubmit={submit} noValidate>
      <div className="field">
        <label htmlFor={`${ids}-name`}>Team name</label>
        <input
          id={`${ids}-name`}
          type="text"
          maxLength={60}
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={fieldErrors.name ? "true" : undefined}
          aria-describedby={`${ids}-name-error`}
        />
        <FieldError id={`${ids}-name-error`} message={fieldErrors.name} />
      </div>
      <div className="field">
        <label htmlFor={`${ids}-description`}>What you want to build, and who you are looking for</label>
        <textarea
          id={`${ids}-description`}
          rows={3}
          maxLength={280}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={fieldErrors.description ? "true" : undefined}
          aria-describedby={`${ids}-description-error`}
        />
        <FieldError id={`${ids}-description-error`} message={fieldErrors.description} />
      </div>
      <ErrorNote error={error} />
      <div className="row">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Saving…" : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}

function MyTeam({ team, me, act }) {
  const [editing, setEditing] = useState(false);
  const leading = team.members.some((member) => member.owner && member.id === me.id);
  const alone = team.members.length === 1;

  const leave = () => {
    const warning = alone
      ? `Leave ${team.name}? You are the only member, so the team will be removed.`
      : leading
        ? `Leave ${team.name}? The member who joined first becomes team lead.`
        : `Leave ${team.name}?`;
    if (window.confirm(warning)) act(() => api.leaveTeam(), `You left ${team.name}.`);
  };

  const remove = (member) => {
    if (window.confirm(`Remove ${fullName(member)} from ${team.name}?`)) {
      act(() => api.removeMember(member.id), `${fullName(member)} is no longer on the team.`);
    }
  };

  return (
    <section className="card my-team" aria-labelledby="my-team-title">
      <div className="card-head">
        <div>
          <p className="eyebrow">Your team</p>
          <h2 id="my-team-title">{team.name}</h2>
        </div>
        <span className={`pill ${team.openSpots === 0 ? "pill-quiet" : "pill-good"}`}>{spots(team.openSpots)}</span>
      </div>

      {editing ? (
        <TeamForm
          initial={team}
          submitLabel="Save"
          onCancel={() => setEditing(false)}
          onSubmit={async (values) => {
            await api.updateTeam(values);
            setEditing(false);
            act(null, "Team updated.");
          }}
        />
      ) : (
        team.description && <p>{team.description}</p>
      )}

      <MemberList team={team} me={me} onRemove={remove} />

      {team.requests.length > 0 && (
        <div className="requests">
          <h3>Asking to join</h3>
          <ul className="members">
            {team.requests.map((request) => (
              <li key={request.id}>
                <Avatar firstName={request.firstName} lastName={request.lastName} id={request.hackerId} />
                <div className="member-text">
                  <p>
                    <strong>{fullName(request)}</strong>
                  </p>
                  <p className="muted">{request.school}</p>
                  {request.message && <p className="request-message">“{request.message}”</p>}
                </div>
                <DiscordHandle username={request.discordUsername} />
                {leading && (
                  <span className="row">
                    <button
                      type="button"
                      className="btn btn-small btn-primary"
                      disabled={team.openSpots === 0}
                      onClick={() => act(() => api.answerRequest(request.id, true), `${fullName(request)} joined the team.`)}
                    >
                      Accept
                    </button>
                    <button
                      type="button"
                      className="btn btn-small"
                      onClick={() => act(() => api.answerRequest(request.id, false), "Request declined.")}
                    >
                      Decline
                    </button>
                  </span>
                )}
              </li>
            ))}
          </ul>
          {!leading && <p className="hint">Your team lead answers requests.</p>}
        </div>
      )}

      {!editing && (
        <div className="row">
          {leading && (
            <button type="button" className="btn" onClick={() => setEditing(true)}>
              Edit name and description
            </button>
          )}
          <button type="button" className="btn btn-danger-quiet" onClick={leave}>
            Leave team
          </button>
        </div>
      )}
    </section>
  );
}

function TeamCard({ team, me, canJoin, act }) {
  const ids = useId();
  const [asking, setAsking] = useState(false);
  const [message, setMessage] = useState("");

  const ask = (event) => {
    event.preventDefault();
    act(() => api.requestToJoin(team.id, message.trim()), `Request sent to ${team.name}.`).then((ok) => {
      if (ok) setAsking(false);
    });
  };

  return (
    <li className="card team">
      <div className="card-head">
        <h2>{team.name}</h2>
        <span className={`pill ${team.openSpots === 0 ? "pill-quiet" : "pill-good"}`}>{spots(team.openSpots)}</span>
      </div>
      {team.description && <p>{team.description}</p>}
      <MemberList team={team} me={me} />

      {team.requested && (
        <div className="row">
          <span className="pill pill-team">Request sent</span>
          <button
            type="button"
            className="btn btn-small"
            onClick={() => act(() => api.withdrawRequest(team.id), "Request withdrawn.")}
          >
            Withdraw
          </button>
        </div>
      )}

      {canJoin && !team.requested && team.openSpots > 0 && !asking && (
        <div className="row">
          <button type="button" className="btn btn-primary" onClick={() => setAsking(true)}>
            Ask to join
          </button>
        </div>
      )}

      {asking && (
        <form className="stack" onSubmit={ask}>
          <div className="field">
            <label htmlFor={`${ids}-message`}>A note for the team (optional)</label>
            <textarea
              id={`${ids}-message`}
              rows={2}
              maxLength={280}
              placeholder="What you would bring, what you want to work on."
              value={message}
              onChange={(event) => setMessage(event.target.value)}
            />
          </div>
          <div className="row">
            <button type="submit" className="btn btn-primary">
              Send request
            </button>
            <button type="button" className="btn" onClick={() => setAsking(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </li>
  );
}

export default function Teams({ me, maxTeamSize, reloadMe }) {
  const notify = useToast();
  const teams = useLoad(loadTeams);
  const [creating, setCreating] = useState(false);

  // Runs one change, says what happened, and refreshes both the teams and the hacker's own
  // record (whose team may have changed). Resolves to whether it worked.
  const act = async (change, done) => {
    try {
      if (change) await change();
      if (done) notify(done);
      return true;
    } catch (error) {
      notify(error.message, "error");
      return false;
    } finally {
      teams.reload();
      reloadMe();
    }
  };

  const all = teams.data || [];
  const mine = all.find((team) => team.members.some((member) => member.id === me.id)) || null;
  const others = all.filter((team) => team !== mine);

  return (
    <>
      <header className="page-head">
        <h1>Teams</h1>
        <p className="muted">
          {maxTeamSize ? `Teams have up to ${maxTeamSize} hackers. ` : ""}Start one, or ask to join one with room. You
          can be on one team at a time.
        </p>
      </header>

      <ErrorNote error={teams.error} onRetry={teams.reload} />
      {!teams.data && !teams.error && <p className="muted">Loading teams…</p>}

      {teams.data && mine && <MyTeam team={mine} me={me} act={act} />}

      {teams.data && !mine && (
        <section className="card" aria-labelledby="create-title">
          <div className="card-head">
            <h2 id="create-title">Start a team</h2>
            <span className="muted">You become team lead and decide who joins.</span>
          </div>
          {creating ? (
            <TeamForm
              submitLabel="Create team"
              onCancel={() => setCreating(false)}
              onSubmit={async (values) => {
                await api.createTeam(values);
                setCreating(false);
                act(null, `${values.name} is ready. Hackers can ask to join.`);
              }}
            />
          ) : (
            <div className="row">
              <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
                Create a team
              </button>
            </div>
          )}
        </section>
      )}

      {teams.data && (
        <>
          <h2 className="section-title">{mine ? "Other teams" : "All teams"}</h2>
          {others.length === 0 ? (
            <p className="empty">{mine ? "No other teams yet." : "No teams yet. Yours could be the first."}</p>
          ) : (
            <ul className="card-grid card-grid-wide">
              {others.map((team) => (
                <TeamCard key={team.id} team={team} me={me} canJoin={!mine} act={act} />
              ))}
            </ul>
          )}
        </>
      )}
    </>
  );
}
