import { useEffect, useId, useRef, useState } from "react";
import { api, qrUrl } from "../api.js";
import { href } from "../hooks.js";
import { ErrorNote, FieldError, useToast } from "../ui.jsx";

const DISCORD_INVITE = "https://discord.gg/jksZ2gaZnX";
const MAX_BIO = 280;

function TicketCard({ me }) {
  const { ticket } = me;
  return (
    <section className="card ticket" aria-labelledby="ticket-title">
      <div className="ticket-qr">
        <img src={qrUrl(ticket.token)} alt="Your PeachHacks ticket QR code" width="220" height="220" />
      </div>
      <div className="ticket-text">
        <h2 id="ticket-title">Your ticket</h2>
        <p className="ticket-name">
          {me.firstName} {me.lastName}
        </p>
        <p className="muted">{me.school}</p>
        <p className={`pill ${ticket.checkedIn ? "pill-good" : "pill-quiet"}`}>
          {ticket.checkedIn ? "Checked in" : "Not checked in yet"}
        </p>
        <p className="muted">Show this code when you arrive. We scan the same code at workshops.</p>
        <div className="row">
          {ticket.googleWalletUrl && (
            <a className="wallet-link" href={ticket.googleWalletUrl} target="_blank" rel="noreferrer">
              <img src="/assets/wallet-logo.png" alt="" width="22" height="22" />
              Add to Google Wallet
            </a>
          )}
          <a className="btn" href={ticket.url} target="_blank" rel="noreferrer">
            Open the ticket page
          </a>
        </div>
      </div>
    </section>
  );
}

function DiscordCard({ me, connectDiscord, focused }) {
  const card = useRef(null);

  useEffect(() => {
    if (focused) card.current?.scrollIntoView({ block: "center" });
  }, [focused]);

  return (
    <section ref={card} className={`card${focused ? " card-focused" : ""}`} aria-labelledby="discord-title">
      <div className="card-head">
        <h2 id="discord-title">Discord</h2>
        {me.discordUsername && <span className="pill pill-good">Connected</span>}
      </div>
      {me.discordUsername ? (
        <>
          <p>
            You are connected as <strong>{me.discordUsername}</strong> and have the Hacker role in the PeachHacks
            server. Other hackers see this username on your card.
          </p>
          <div className="row">
            <a className="btn btn-primary" href={DISCORD_INVITE} target="_blank" rel="noreferrer">
              Open the server
            </a>
            {connectDiscord && (
              <button type="button" className="btn" onClick={connectDiscord}>
                Use a different Discord account
              </button>
            )}
          </div>
        </>
      ) : (
        <>
          <p>
            Announcements, team chat and help from mentors all happen in the PeachHacks Discord. Connecting adds you to
            the server and gives you the Hacker role, which opens the hacker channels.
          </p>
          {connectDiscord ? (
            <div className="row">
              <button type="button" className="btn btn-primary" onClick={connectDiscord}>
                <img src="/assets/discord_logo.png" alt="" width="20" height="20" />
                Connect Discord
              </button>
            </div>
          ) : (
            <p className="note">Connecting from here is not switched on yet. Check back soon.</p>
          )}
        </>
      )}
    </section>
  );
}

function ProfileCard({ me, reloadMe }) {
  const ids = useId();
  const notify = useToast();
  const [form, setForm] = useState({
    bio: me.profile.bio || "",
    githubUrl: me.profile.githubUrl || "",
    linkedinUrl: me.profile.linkedinUrl || "",
    lookingForTeam: me.profile.lookingForTeam,
    listed: me.profile.listed,
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const set = (key) => (event) =>
    setForm((prev) => ({ ...prev, [key]: event.target.type === "checkbox" ? event.target.checked : event.target.value }));

  const save = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setFieldErrors({});
    try {
      await api.saveProfile({
        bio: form.bio.trim() || null,
        githubUrl: form.githubUrl.trim() || null,
        linkedinUrl: form.linkedinUrl.trim() || null,
        lookingForTeam: form.lookingForTeam,
        listed: form.listed,
      });
      notify("Profile saved.");
      reloadMe();
    } catch (failure) {
      if (failure.fieldErrors) setFieldErrors(failure.fieldErrors);
      else setError(failure);
    } finally {
      setSaving(false);
    }
  };

  const text = (key, label, placeholder) => (
    <div className="field">
      <label htmlFor={`${ids}-${key}`}>{label}</label>
      <input
        id={`${ids}-${key}`}
        type="url"
        inputMode="url"
        placeholder={placeholder}
        value={form[key]}
        onChange={set(key)}
        aria-invalid={fieldErrors[key] ? "true" : undefined}
        aria-describedby={`${ids}-${key}-error`}
      />
      <FieldError id={`${ids}-${key}-error`} message={fieldErrors[key]} />
    </div>
  );

  return (
    <section className="card" aria-labelledby={`${ids}-title`}>
      <div className="card-head">
        <h2 id={`${ids}-title`}>Your card</h2>
        <span className="muted">
          What other hackers see on the <a href={href("/hackers")}>Hackers</a> page, next to your name and school.
        </span>
      </div>
      <form className="stack" onSubmit={save} noValidate>
        <div className="field">
          <label htmlFor={`${ids}-bio`}>About you</label>
          <textarea
            id={`${ids}-bio`}
            rows={3}
            maxLength={MAX_BIO}
            placeholder="What you like to build, what you want to learn, what you are looking for in a team."
            value={form.bio}
            onChange={set("bio")}
            aria-invalid={fieldErrors.bio ? "true" : undefined}
            aria-describedby={`${ids}-bio-count ${ids}-bio-error`}
          />
          <p id={`${ids}-bio-count`} className="hint">
            {form.bio.length} of {MAX_BIO} characters
          </p>
          <FieldError id={`${ids}-bio-error`} message={fieldErrors.bio} />
        </div>
        <div className="grid-2">
          {text("githubUrl", "GitHub", "https://github.com/you")}
          {text("linkedinUrl", "LinkedIn", "https://www.linkedin.com/in/you")}
        </div>
        <div className="check">
          <input
            id={`${ids}-looking`}
            type="checkbox"
            checked={form.lookingForTeam}
            onChange={set("lookingForTeam")}
            disabled={Boolean(me.teamId)}
            aria-describedby={`${ids}-looking-hint`}
          />
          <div>
            <label htmlFor={`${ids}-looking`}>I am looking for a team</label>
            <p id={`${ids}-looking-hint`} className="hint">
              {me.teamId ? "You are on a team, so this is off." : "Shows a “Looking for a team” tag on your card."}
            </p>
          </div>
        </div>
        <div className="check">
          <input
            id={`${ids}-listed`}
            type="checkbox"
            checked={form.listed}
            onChange={set("listed")}
            aria-describedby={`${ids}-listed-hint`}
          />
          <div>
            <label htmlFor={`${ids}-listed`}>Show me on the Hackers page</label>
            <p id={`${ids}-listed-hint`} className="hint">
              Turn this off to stay out of the list. Your teammates still see you on your team.
            </p>
          </div>
        </div>
        <ErrorNote error={error} />
        <div className="row">
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </section>
  );
}

function PasswordCard({ me }) {
  const notify = useToast();
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  const send = async () => {
    setBusy(true);
    try {
      await api.passwordLink(me.email);
      setSent(true);
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="password-title">
      <div className="card-head">
        <h2 id="password-title">Password</h2>
      </div>
      <p>
        {me.hasPassword
          ? "You sign in with your email and a password. To change it, we email you a link."
          : "You signed in with Google and have no password yet. Choose one if you also want to sign in with your email."}
      </p>
      {sent ? (
        <p className="note" role="status">
          A link is on its way to {me.email}. It works for one hour.
        </p>
      ) : (
        <div className="row">
          <button type="button" className="btn" onClick={send} disabled={busy}>
            {busy ? "Sending…" : me.hasPassword ? "Email me a link to change it" : "Email me a link to choose one"}
          </button>
        </div>
      )}
    </section>
  );
}

export default function Home({ me, connectDiscord, reloadMe, focusDiscord }) {
  return (
    <>
      <header className="page-head">
        <h1>Hi {me.firstName}, you are in.</h1>
        <p className="muted">
          Your ticket, the Discord and your card are here. Find people to build with on the{" "}
          <a href={href("/hackers")}>Hackers</a> and <a href={href("/teams")}>Teams</a> pages.
        </p>
      </header>
      <TicketCard me={me} />
      <DiscordCard me={me} connectDiscord={connectDiscord} focused={focusDiscord} />
      <ProfileCard me={me} reloadMe={reloadMe} />
      <PasswordCard me={me} />
    </>
  );
}
