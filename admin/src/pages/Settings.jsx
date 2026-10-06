import { useId, useState } from "react";
import { api } from "../api/client.js";
import { EventsCard } from "../components/EventsCard.jsx";
import { ConfirmDialog } from "../components/Modal.jsx";
import { EmptyBlock, ErrorBlock, InlineError, LoadingBlock, PageHeader, Tag, useToast } from "../components/ui.jsx";
import { ROLES, errorText, formatDate, roleLabel } from "../lib/format.js";
import { useAsync } from "../lib/hooks.js";
import { href } from "../lib/router.js";

const EMAIL_SHORTCUT = "/email?template=registration-open&audience=PRE_REGISTRANTS_NOT_REGISTERED";

const loadSettings = (signal) => api.settings(signal);

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
          <div className={`gate ${open ? "is-open" : "is-closed"}`}>
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

const loadAdmins = (signal) => api.admins(signal);
const EMPTY_FORM = { name: "", email: "", password: "", role: "ADMIN" };

function AdminAccounts({ admin }) {
  const notify = useToast();
  const ids = useId();
  const admins = useAsync(loadAdmins);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [adding, setAdding] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
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
    if (form.password.length < 10) errors.password = "Use at least 10 characters.";
    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) return;

    setAdding(true);
    try {
      await api.createAdmin({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      notify(`Added ${form.name.trim()} as ${form.role === "VOLUNTEER" ? "a volunteer" : "an admin"}.`);
      setForm(EMPTY_FORM);
      setShowPassword(false);
      admins.reload();
    } catch (error) {
      if (error?.fieldErrors) setFieldErrors(error.fieldErrors);
      else setFormError(error);
    } finally {
      setAdding(false);
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

  const describe = (key) => (fieldErrors[key] ? `${ids}-${key}-error` : undefined);
  const fieldError = (key) =>
    fieldErrors[key] ? (
      <p id={`${ids}-${key}-error`} className="inline-error">
        {fieldErrors[key]}
      </p>
    ) : null;

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
                      {row.name || "(no name)"} {isSelf && <Tag tone="neutral">You</Tag>}
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
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <form className="admin-form" onSubmit={add} noValidate aria-labelledby={`${ids}-add`}>
        <h3 id={`${ids}-add`}>Add an account</h3>
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
            <input id={`${ids}-name`} type="text" autoComplete="off" value={form.name} onChange={set("name")} aria-invalid={fieldErrors.name ? true : undefined} aria-describedby={describe("name")} />
            {fieldError("name")}
          </div>
          <div className="field">
            <label htmlFor={`${ids}-email`}>Email</label>
            <input id={`${ids}-email`} type="email" autoComplete="off" value={form.email} onChange={set("email")} aria-invalid={fieldErrors.email ? true : undefined} aria-describedby={describe("email")} />
            {fieldError("email")}
          </div>
          <div className="field">
            <label htmlFor={`${ids}-password`}>Password</label>
            <div className="input-with-button">
              <input
                id={`${ids}-password`}
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                minLength={10}
                value={form.password}
                onChange={set("password")}
                aria-invalid={fieldErrors.password ? true : undefined}
                aria-describedby={`${ids}-password-hint${fieldErrors.password ? ` ${ids}-password-error` : ""}`}
              />
              <button type="button" className="btn btn-small" aria-pressed={showPassword} onClick={() => setShowPassword((v) => !v)}>
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>
            <p id={`${ids}-password-hint`} className="hint">
              At least 10 characters. Share it with them privately; they sign in with this email and password.
            </p>
            {fieldError("password")}
          </div>
        </div>
        <InlineError>{formError ? errorText(formError) : null}</InlineError>
        <button type="submit" className="btn btn-primary" disabled={adding}>
          {adding ? "Adding…" : form.role === "VOLUNTEER" ? "Add volunteer" : "Add admin"}
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

export default function Settings({ admin }) {
  return (
    <>
      <PageHeader title="Settings" description="Open or close registration, set up check-in events and manage who can sign in." />
      <RegistrationGate />
      <EventsCard />
      <AdminAccounts admin={admin} />
    </>
  );
}
