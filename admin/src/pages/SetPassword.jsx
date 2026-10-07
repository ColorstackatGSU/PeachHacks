import { useEffect, useState } from "react";
import { api } from "../api/client.js";
import { href } from "../lib/router.js";

export const MIN_PASSWORD_LENGTH = 10;
export const MAX_PASSWORD_LENGTH = 72;

export default function SetPassword({ token, onDone }) {
  const [link, setLink] = useState({ status: token ? "checking" : "invalid", account: null, error: null });
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();
    api.checkPasswordLink(token, controller.signal).then(
      (account) => setLink({ status: "ready", account, error: null }),
      (err) => {
        if (err?.name === "AbortError") return;
        if (err?.code === "INVALID_PASSWORD_LINK" || err?.code === "VALIDATION_ERROR") {
          setLink({ status: "invalid", account: null, error: null });
        } else setLink({ status: "error", account: null, error: err });
      },
    );
    return () => controller.abort();
  }, [token]);

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(`Use at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
      setError(`Use at most ${MAX_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (password !== repeat) {
      setError("The two passwords do not match.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.setPassword(token, password);
      onDone("Your password is saved. Sign in with it.");
    } catch (err) {
      setBusy(false);
      if (err?.code === "INVALID_PASSWORD_LINK") setLink({ status: "invalid", account: null, error: null });
      else setError(err?.fieldErrors?.password || err?.message || "Could not save your password. Try again.");
    }
  };

  const invite = link.account?.invite;

  return (
    <div className="signin">
      <main className="signin-card">
        <img className="signin-logo" src="/assets/logo.svg" alt="PeachHacks" width="210" height="79" />
        <p className="eyebrow">Organizer admin</p>
        <h1>{invite ? "Set your password" : "Choose a new password"}</h1>

        {link.status === "checking" && (
          <p className="notice" role="status">
            Checking your link…
          </p>
        )}

        {link.status === "error" && (
          <p className="inline-error" role="alert">
            {link.error?.message || "Could not check your link. Reload the page to try again."}
          </p>
        )}

        {link.status === "invalid" && (
          <>
            <p className="notice notice-warn" role="alert">
              This link has expired or was already used.
            </p>
            <p className="signin-foot">
              Use “Forgot password?” on the sign-in page to get a new one, or ask an admin to resend your invite.
            </p>
            <a className="btn btn-primary btn-block" href={href("/")} onClick={() => onDone(null)}>
              Go to sign in
            </a>
          </>
        )}

        {link.status === "ready" && (
          <form onSubmit={submit}>
            <p className="notice">
              {invite ? "Welcome" : "Hi"}
              {link.account.name ? `, ${link.account.name}` : ""}. You sign in as <strong>{link.account.email}</strong>.
            </p>
            {/* Lets a password manager file the new password under the right account. */}
            <input type="email" autoComplete="username" value={link.account.email} readOnly hidden />
            <div className="field">
              <label htmlFor="setpw-password">New password</label>
              <div className="input-with-button">
                <input
                  id="setpw-password"
                  type={show ? "text" : "password"}
                  autoComplete="new-password"
                  minLength={MIN_PASSWORD_LENGTH}
                  maxLength={MAX_PASSWORD_LENGTH}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-describedby={`setpw-hint${error ? " setpw-error" : ""}`}
                />
                <button type="button" className="btn btn-small" aria-pressed={show} onClick={() => setShow((v) => !v)}>
                  {show ? "Hide" : "Show"}
                </button>
              </div>
              <p id="setpw-hint" className="hint">
                At least {MIN_PASSWORD_LENGTH} characters.
              </p>
            </div>
            <div className="field">
              <label htmlFor="setpw-repeat">Type it again</label>
              <input
                id="setpw-repeat"
                type={show ? "text" : "password"}
                autoComplete="new-password"
                required
                value={repeat}
                onChange={(e) => setRepeat(e.target.value)}
                aria-describedby={error ? "setpw-error" : undefined}
              />
            </div>
            {error && (
              <p id="setpw-error" className="inline-error" role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
              {busy ? "Saving…" : "Save password"}
            </button>
          </form>
        )}
      </main>
    </div>
  );
}
