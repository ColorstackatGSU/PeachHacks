import { useId, useState } from "react";
import { api } from "../api/client.js";
import { EventsCard } from "../components/EventsCard.jsx";
import { ConfirmDialog } from "../components/Modal.jsx";
import {
  EmptyBlock,
  ErrorBlock,
  FieldError,
  InlineError,
  LoadingBlock,
  PageHeader,
  Tag,
  errorProps,
  useToast,
} from "../components/ui.jsx";
import { ROLES, errorText, formatDate, roleLabel } from "../lib/format.js";
import { useAsync } from "../lib/hooks.js";
import { href } from "../lib/router.js";
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from "./SetPassword.jsx";

const EMAIL_SHORTCUT = "/email?template=registration-open&audience=PRE_REGISTRANTS_NOT_REGISTERED";

const loadSettings = (signal) => api.settings(signal);

function RegistrationPreview({ initiallyActive }) {
  const notify = useToast();
  const [active, setActive] = useState(initiallyActive);
  const [link, setLink] = useState(null);
  const [busy, setBusy] = useState(false);

  const run = async (action) => {
    setBusy(true);
    try {
      await action();
    } catch (error) {
      notify(errorText(error), "error");
    } finally {
      setBusy(false);
    }
  };

  const create = () =>
    run(async () => {
      const result = await api.createPreviewLink();
      setLink(result?.url || null);
      setActive(true);
    });

  const end = () =>
    run(async () => {
      await api.endPreview();
      setLink(null);
      setActive(false);
      notify("Preview ended. The link no longer works.");
    });

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link);
      notify("Link copied.");
    } catch {
      notify("Could not copy. Select the link and copy it yourself.", "error");
    }
  };

  return (
    <div className="notice">
      <p>
        <strong>Try registration before it opens.</strong> A preview link shows the Register button and the full form on
        the device that opens it, while everyone else still sees pre-registration. Registrations made through it are
        real: you can accept them, send their ticket and sign in to the hacker platform with them.
      </p>
      {link && (
        <>
          <p>Open this link, or send it to another organizer. It will not be shown again.</p>
          <div className="input-with-button">
            <input type="text" readOnly value={link} aria-label="Registration preview link" onFocus={(e) => e.target.select()} />
            <button type="button" className="btn btn-small" onClick={copy}>
              Copy
            </button>
          </div>
        </>
      )}
      {active && !link && <p>A preview link is active. Making a new one stops the old link working.</p>}
      <p className="tag-row">
        <button type="button" className="btn btn-small" onClick={create} disabled={busy}>
          {active ? "Make a new preview link" : "Make a preview link"}
        </button>
        {active && (
          <button type="button" className="btn btn-small btn-danger-quiet" onClick={end} disabled={busy}>
            End preview
          </button>
        )}
      </p>
    </div>
  );
}

function RegistrationGate() {
  const notify = useToast();
  const ids = useId();
  const settings = useAsync(loadSettings);
  const [saved, setSaved] = useState(null);
  const [target, setTarget] = useState(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [justOpened, setJustOpened] = useState(false);

  const known = saved !== null || settings.data !== null;
  const open = saved !== null ? saved : Boolean(settings.data?.registrationOpen);

  const confirm = async () => {
    setSaving(true);
    setSaveError(null);
    try {
      const result = await api.saveSettings(target);
      const now = Boolean(result?.registrationOpen ?? target);
      setSaved(now);
      setJustOpened(now);
      setTarget(null);
      notify(now ? "Registration is now open." : "Registration is now closed.");
    } catch (error) {
      setSaveError(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="card" aria-labelledby={`${ids}-title`}>
      <div className="card-head">
        <h2 id={`${ids}-title`}>Registration gate</h2>
      </div>

      {!known && settings.error && <ErrorBlock error={settings.error} onRetry={settings.reload} />}
      {!known && !settings.error && <LoadingBlock label="Loading the current setting…" />}

      {known && (
        <>
          <div className={`gate${open ? " is-open" : ""}`}>
            <div className="gate-text">
              <strong id={`${ids}-label`}>Registration is {open ? "open" : "closed"}</strong>
              <p id={`${ids}-desc`}>
                {open
                  ? "Anyone can submit the full registration form on the public site right now."
                  : "The public registration page says registration is not open yet and points people to pre-registration."}
              </p>
            </div>
            <button
              type="button"
              role="switch"
              className="switch"
              aria-checked={open}
              aria-labelledby={`${ids}-switch-label`}
              aria-describedby={`${ids}-desc`}
              onClick={() => {
                setSaveError(null);
                setTarget(!open);
              }}
            >
              <span className="switch-track" aria-hidden="true">
                <span className="switch-thumb" />
              </span>
              <span id={`${ids}-switch-label`} className="switch-label">
                Accept registrations
                <span className="switch-state">{open ? "On" : "Off"}</span>
              </span>
            </button>
          </div>

          <dl className="explain">
            <div>
              <dt>When open</dt>
              <dd>The full MLH registration form on the public site accepts submissions from anyone, and each registrant gets a confirmation email.</dd>
            </div>
            <div>
              <dt>When closed</dt>
              <dd>The form is replaced by a “not open yet” message with a link to pre-register. Existing registrations and pre-registrations are kept.</dd>
            </div>
            <div>
              <dt>Not automatic</dt>
              <dd>Flipping the switch does not email anyone. Tell pre-registrants yourself from the Email screen.</dd>
            </div>
          </dl>

          {!open && <RegistrationPreview initiallyActive={Boolean(settings.data?.previewActive)} />}

          {justOpened && open && (
            <div className="callout" role="status">
              <div>
                <strong>Registration is open. Tell the people who are waiting.</strong>
                <p>Start an email to everyone who pre-registered but has not registered yet, with the “Registration is open” template filled in. You review it before anything is sent.</p>
              </div>
              <a className="btn btn-primary" href={href(EMAIL_SHORTCUT)}>
                Write the announcement
              </a>
            </div>
          )}
        </>
      )}

      {target !== null && (
        <ConfirmDialog
          title={target ? "Open registration?" : "Close registration?"}
          confirmLabel={target ? "Open registration" : "Close registration"}
          danger={!target}
          busy={saving}
          error={saveError}
          onConfirm={confirm}
          onCancel={() => setTarget(null)}
        >
          {target ? (
            <>
              <p>The public registration form will start accepting submissions immediately. Anyone with the link can register.</p>
              <p>Pre-registrants are not notified automatically; you can email them right after this.</p>
            </>
          ) : (
            <>
              <p>The public registration form will stop accepting submissions immediately. Visitors will see that registration is not open and be pointed to pre-registration.</p>
              <p>Registrations already submitted are not affected. You can reopen at any time.</p>
            </>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}

const loadDiscord = (signal) => api.discord(signal);

const MAX_DISCORD_MESSAGE = 900;

function DiscordVerification() {
  const notify = useToast();
  const ids = useId();
  const discord = useAsync(loadDiscord);
  const [published, setPublished] = useState(null);
  const [draft, setDraft] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishError, setPublishError] = useState(null);

  const status = published || discord.data;
  const text = draft ?? status?.message ?? "";
  const tooLong = text.length > MAX_DISCORD_MESSAGE;
  const changed = draft !== null && draft !== status?.message;

  const [recapping, setRecapping] = useState(false);

  const recap = async () => {
    setRecapping(true);
    try {
      await api.postDiscordRecap();
      notify("The recap is in the applications channel.");
    } catch (error) {
      notify(errorText(error), "error");
    } finally {
      setRecapping(false);
    }
  };

  const publish = async () => {
    setPublishing(true);
    setPublishError(null);
    try {
      const wasPosted = Boolean(status?.messagePostedAt);
      setPublished(await api.publishDiscordVerification(text));
      setDraft(null);
      setConfirming(false);
      notify(wasPosted ? "The verification message in Discord was updated." : "The verification message is up in Discord.");
    } catch (error) {
      setPublishError(error);
    } finally {
      setPublishing(false);
    }
  };

  return (
    <section className="card" aria-labelledby={`${ids}-title`}>
      <div className="card-head">
        <h2 id={`${ids}-title`}>PeachBot</h2>
        <span className="muted">Gives the Hacker role in Discord to accepted hackers, and takes it away when they are no longer accepted.</span>
      </div>

      {!status && discord.error && <ErrorBlock error={discord.error} onRetry={discord.reload} />}
      {!status && !discord.error && <LoadingBlock label="Checking PeachBot…" />}

      {status && !status.configured && (
        <p className="notice">
          PeachBot is not set up on the server yet. Add the <code>DISCORD_*</code> values to the backend (see its README, “PeachBot”) and redeploy.
        </p>
      )}

      {status?.configured && (
        <>
          <dl className="explain">
            <div>
              <dt>Connected so far</dt>
              <dd>
                {status.verified} {status.verified === 1 ? "hacker has" : "hackers have"} connected a Discord account.
              </dd>
            </div>
            <div>
              <dt>Verification message</dt>
              <dd>{status.messagePostedAt ? `Last published ${formatDate(status.messagePostedAt)}.` : "Not posted yet."}</dd>
            </div>
            <div>
              <dt>How hackers verify</dt>
              <dd>They press Connect Discord on the hacker platform. The Verify button in Discord gives the role to anyone who has done that, and points everyone else to the platform.</dd>
            </div>
          </dl>

          <div className="field">
            <label htmlFor={`${ids}-message`}>Message above the Verify button</label>
            <textarea
              id={`${ids}-message`}
              className="discord-message"
              value={text}
              onChange={(event) => setDraft(event.target.value)}
              {...errorProps(tooLong ? "too long" : null, `${ids}-message-hint`)}
            />
            <p id={`${ids}-message-hint`} className="hint">
              {text.length} of {MAX_DISCORD_MESSAGE} characters. Discord formatting works: **bold**, *italic*, and a blank line for a new paragraph. Mentions such as @everyone do not ping anyone.
            </p>
          </div>

          <p className="tag-row">
            <button
              type="button"
              className="btn btn-primary"
              disabled={tooLong || text.trim() === ""}
              onClick={() => {
                setPublishError(null);
                setConfirming(true);
              }}
            >
              {status.messagePostedAt ? (changed ? "Save and update in Discord" : "Update in Discord") : "Post the verification message"}
            </button>
            {changed && (
              <button type="button" className="btn" onClick={() => setDraft(null)}>
                Discard changes
              </button>
            )}
          </p>

          <dl className="explain">
            <div>
              <dt>Welcomes</dt>
              <dd>{status.welcomes ? "On. Everyone who joins the server gets a welcome card, once." : "Off. Set DISCORD_WELCOME_CHANNEL_ID on the backend to turn them on."}</dd>
            </div>
            <div>
              <dt>Applications channel</dt>
              <dd>{status.applications ? "On. Each new application is posted, with a recap every evening at 9 PM Atlanta time." : "Off. Set DISCORD_APPLICATIONS_CHANNEL_ID on the backend to turn it on."}</dd>
            </div>
            <div>
              <dt>Recap now</dt>
              <dd>
                <button type="button" className="btn btn-small" disabled={!status.applications || recapping} onClick={recap}>
                  {recapping ? "Posting…" : "Post the recap now"}
                </button>
              </dd>
            </div>
          </dl>
        </>
      )}

      {confirming && (
        <ConfirmDialog
          title={status?.messagePostedAt ? "Update the verification message?" : "Post the verification message?"}
          confirmLabel={status?.messagePostedAt ? "Update in Discord" : "Post to Discord"}
          busy={publishing}
          error={publishError}
          onConfirm={publish}
          onCancel={() => setConfirming(false)}
        >
          {status?.messagePostedAt ? (
            <p>The message that is already in the verification channel is edited in place, so it keeps its spot. If someone deleted it, a new one is posted.</p>
          ) : (
            <p>PeachBot posts this message with a Verify button in the verification channel. Everyone who can see that channel can press it.</p>
          )}
        </ConfirmDialog>
      )}
    </section>
  );
}

const loadAdmins = (signal) => api.admins(signal);
const EMPTY_FORM = { name: "", email: "", role: "ADMIN" };

function AdminAccounts({ admin }) {
  const notify = useToast();
  const ids = useId();
  const admins = useAsync(loadAdmins);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [adding, setAdding] = useState(false);
  const [invite, setInvite] = useState(null);
  const [resending, setResending] = useState(null);
  const [pendingRemove, setPendingRemove] = useState(null);
  const [removing, setRemoving] = useState(false);
  const [removeError, setRemoveError] = useState(null);

  const rows = Array.isArray(admins.data) ? admins.data : [];
  const set = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const add = async (event) => {
    event.preventDefault();
    const errors = {};
    if (form.name.trim() === "") errors.name = "Enter a name.";
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) errors.email = "Enter a valid email address.";
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) return;

    setAdding(true);
    try {
      const created = await api.createAdmin({ name: form.name.trim(), email: form.email.trim(), role: form.role });
      notify(`Invited ${form.name.trim()} as ${form.role === "VOLUNTEER" ? "a volunteer" : "an admin"}.`);
      setInvite({ name: form.name.trim(), email: form.email.trim(), url: created?.setPasswordUrl || null });
      setForm(EMPTY_FORM);
      admins.reload();
    } catch (error) {
      if (error?.fieldErrors) setFieldErrors(error.fieldErrors);
      else setFormError(error);
    } finally {
      setAdding(false);
    }
  };

  const resend = async (row) => {
    setResending(row.id);
    try {
      const result = await api.resendInvite(row.id);
      notify(`Sent ${row.name || row.email} a new invite.`);
      setInvite({ name: row.name || row.email, email: row.email, url: result?.setPasswordUrl || null });
    } catch (error) {
      notify(errorText(error), "error");
      admins.reload();
    } finally {
      setResending(null);
    }
  };

  const copyInvite = async () => {
    try {
      await navigator.clipboard.writeText(invite.url);
      notify("Link copied.");
    } catch {
      notify("Could not copy. Select the link and copy it yourself.", "error");
    }
  };

  const remove = async () => {
    setRemoving(true);
    setRemoveError(null);
    try {
      await api.deleteAdmin(pendingRemove.id);
      notify(`Removed ${pendingRemove.name || pendingRemove.email}.`);
      setPendingRemove(null);
      admins.reload();
    } catch (error) {
      setRemoveError(error);
    } finally {
      setRemoving(false);
    }
  };

  const fieldError = (key) => <FieldError id={`${ids}-${key}-error`} message={fieldErrors[key]} />;

  return (
    <section className="card" aria-labelledby={`${ids}-title`}>
      <div className="card-head">
        <h2 id={`${ids}-title`}>Accounts</h2>
        <span className="muted">Admins can do everything on this site. Volunteers can only check people in.</span>
      </div>

      {admins.error && (
        <ErrorBlock
          title={admins.data ? "Could not refresh the list" : "Could not load accounts"}
          error={admins.error}
          onRetry={admins.reload}
        />
      )}
      {!admins.data && !admins.error && <LoadingBlock label="Loading accounts…" />}
      {admins.data && rows.length === 0 && <EmptyBlock title="No accounts found" />}

      {rows.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <caption className="sr-only">Accounts</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">Type</th>
                <th scope="col">Added</th>
                <th scope="col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const isSelf = row.id === admin?.id || (row.email && row.email === admin?.email);
                return (
                  <tr key={row.id}>
                    <th scope="row" data-label="Name">
                      {row.name || "(no name)"} {isSelf && <Tag tone="neutral">You</Tag>}{" "}
                      {row.pending && <Tag tone="pending">Invited, no password yet</Tag>}
                    </th>
                    <td data-label="Email" className="cell-break">
                      {row.email}
                    </td>
                    <td data-label="Type">
                      <Tag tone={row.role === "VOLUNTEER" ? "waitlisted" : "accepted"}>{roleLabel(row.role)}</Tag>
                    </td>
                    <td data-label="Added" className="cell-nowrap">
                      {formatDate(row.createdAt)}
                    </td>
                    <td className="cell-actions">
                      {isSelf ? (
                        <span className="muted small">You cannot remove yourself</span>
                      ) : (
                        <>
                          {row.pending && (
                            <button
                              type="button"
                              className="btn btn-small"
                              disabled={resending === row.id}
                              aria-label={`Resend invite to ${row.name || row.email}`}
                              onClick={() => resend(row)}
                            >
                              {resending === row.id ? "Sending…" : "Resend invite"}
                            </button>
                          )}{" "}
                          <button
                            type="button"
                            className="btn btn-small btn-danger-quiet"
                            aria-label={`Remove ${roleLabel(row.role).toLowerCase()} ${row.name || row.email}`}
                            onClick={() => {
                              setRemoveError(null);
                              setPendingRemove(row);
                            }}
                          >
                            Remove
                          </button>
                        </>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {invite && (
        <div className="notice invite-notice" role="status">
          <p>
            <strong>{invite.name}</strong> was emailed a link at {invite.email} to set their own password. It works for 7
            days and can be used once.
          </p>
          {invite.url && (
            <>
              <p>If the email does not arrive, send them this link yourself. It will not be shown again.</p>
              <div className="input-with-button">
                <input type="text" readOnly value={invite.url} aria-label="Set-password link" onFocus={(e) => e.target.select()} />
                <button type="button" className="btn btn-small" onClick={copyInvite}>
                  Copy
                </button>
              </div>
            </>
          )}
          <button type="button" className="link-btn" onClick={() => setInvite(null)}>
            Dismiss
          </button>
        </div>
      )}

      <form className="admin-form" onSubmit={add} noValidate aria-labelledby={`${ids}-add`}>
        <h3 id={`${ids}-add`}>Add an account</h3>
        <p className="hint">They get an email with a link to choose their own password.</p>
        <div className="field account-type">
          <label htmlFor={`${ids}-role`}>Account type</label>
          <select id={`${ids}-role`} value={form.role} onChange={set("role")} aria-describedby={`${ids}-role-hint`}>
            {ROLES.map((role) => (
              <option key={role.value} value={role.value}>
                {role.option}
              </option>
            ))}
          </select>
          <p id={`${ids}-role-hint`} className="hint">
            {ROLES.find((role) => role.value === form.role)?.hint}
          </p>
          {fieldError("role")}
        </div>
        <div className="admin-form-grid">
          <div className="field">
            <label htmlFor={`${ids}-name`}>Name</label>
            <input id={`${ids}-name`} type="text" autoComplete="off" value={form.name} onChange={set("name")} {...errorProps(fieldErrors.name, `${ids}-name-error`)} />
            {fieldError("name")}
          </div>
          <div className="field">
            <label htmlFor={`${ids}-email`}>Email</label>
            <input id={`${ids}-email`} type="email" autoComplete="off" value={form.email} onChange={set("email")} {...errorProps(fieldErrors.email, `${ids}-email-error`)} />
            {fieldError("email")}
          </div>
        </div>
        <InlineError error={formError} />
        <button type="submit" className="btn btn-primary" disabled={adding}>
          {adding ? "Sending invite…" : form.role === "VOLUNTEER" ? "Invite volunteer" : "Invite admin"}
        </button>
      </form>

      {pendingRemove && (
        <ConfirmDialog
          title="Remove this account?"
          confirmLabel="Remove account"
          danger
          busy={removing}
          error={removeError}
          onConfirm={remove}
          onCancel={() => setPendingRemove(null)}
        >
          <p>
            <strong>{pendingRemove.name || pendingRemove.email}</strong> ({pendingRemove.email}) will no longer be able to sign in to
            the admin site. This cannot be undone, but you can add them again later.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}

function ChangePassword() {
  const notify = useToast();
  const ids = useId();
  const [form, setForm] = useState({ current: "", next: "", repeat: "" });
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [saving, setSaving] = useState(false);

  const set = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const save = async (event) => {
    event.preventDefault();
    const errors = {};
    if (form.current === "") errors.currentPassword = "Enter your current password.";
    if (form.next.length < MIN_PASSWORD_LENGTH) errors.newPassword = `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
    else if (form.next.length > MAX_PASSWORD_LENGTH) errors.newPassword = `Use at most ${MAX_PASSWORD_LENGTH} characters.`;
    else if (form.next !== form.repeat) errors.repeat = "The two passwords do not match.";
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    try {
      await api.changePassword(form.current, form.next);
      notify("Password changed. Other devices signed in to your account were signed out.");
      setForm({ current: "", next: "", repeat: "" });
    } catch (error) {
      if (error?.fieldErrors) setFieldErrors(error.fieldErrors);
      else setFormError(error);
    } finally {
      setSaving(false);
    }
  };

  const field = (key, name, label, autoComplete) => (
    <div className="field">
      <label htmlFor={`${ids}-${key}`}>{label}</label>
      <input
        id={`${ids}-${key}`}
        type="password"
        autoComplete={autoComplete}
        value={form[key]}
        onChange={set(key)}
        {...errorProps(fieldErrors[name], `${ids}-${key}-error`)}
      />
      <FieldError id={`${ids}-${key}-error`} message={fieldErrors[name]} />
    </div>
  );

  return (
    <section className="card" aria-labelledby={`${ids}-title`}>
      <div className="card-head">
        <h2 id={`${ids}-title`}>Your password</h2>
        <span className="muted">Forgot it? Sign out and use “Forgot password?” on the sign-in page.</span>
      </div>
      <form className="admin-form" onSubmit={save} noValidate aria-labelledby={`${ids}-title`}>
        <div className="admin-form-grid">
          {field("current", "currentPassword", "Current password", "current-password")}
          {field("next", "newPassword", "New password", "new-password")}
          {field("repeat", "repeat", "New password again", "new-password")}
        </div>
        <InlineError error={formError} />
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? "Saving…" : "Change password"}
        </button>
      </form>
    </section>
  );
}

export default function Settings({ admin }) {
  return (
    <>
      <PageHeader title="Settings" description="Open or close registration, set up check-in events and manage who can sign in." />
      <RegistrationGate />
      <EventsCard />
      <DiscordVerification />
      <AdminAccounts admin={admin} />
      <ChangePassword />
    </>
  );
}
