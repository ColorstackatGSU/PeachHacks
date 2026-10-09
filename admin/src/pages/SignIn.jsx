import { useState } from "react";
import { MOCK_MODE, api } from "../api/client.js";

function ForgotPassword({ initialEmail, onBack }) {
  const [email, setEmail] = useState(initialEmail);
  const [busy, setBusy] = useState(false);
  const [sentTo, setSentTo] = useState(null);
  const [error, setError] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await api.forgotPassword(email.trim());
      setSentTo(email.trim());
    } catch (err) {
      if (err?.code === "RATE_LIMITED") setError("Too many attempts. Wait a minute and try again.");
      else if (err?.code === "VALIDATION_ERROR") setError("Enter your email address.");
      else setError(err?.message || "Could not send the link. Try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <h1>Forgot password</h1>
      {sentTo ? (
        <>
          <p className="notice" role="status">
            If <strong>{sentTo}</strong> has an account, a link to choose a new password is on its way. It works for one
            hour.
          </p>
          <p className="signin-foot">Nothing after a few minutes? Check junk or quarantine, or ask an admin.</p>
        </>
      ) : (
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="forgot-email">Email</label>
            <input
              id="forgot-email"
              type="email"
              autoComplete="username"
              inputMode="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-describedby={error ? "forgot-error" : undefined}
            />
          </div>
          {error && (
            <p id="forgot-error" className="inline-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
            {busy ? "Sending…" : "Email me a link"}
          </button>
        </form>
      )}
      <button type="button" className="link-btn signin-switch" onClick={onBack}>
        Back to sign in
      </button>
    </>
  );
}

export default function SignIn({ notice, onSignedIn }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [forgot, setForgot] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await api.login(email.trim(), password);
      if (!result?.token) throw new Error("The server did not return a session.");
      onSignedIn(result);
    } catch (err) {
      setBusy(false);
      if (err?.code === "INVALID_CREDENTIALS") setError("That email and password do not match an account.");
      else if (err?.code === "RATE_LIMITED") setError("Too many sign-in attempts. Wait a minute and try again.");
      else if (err?.code === "VALIDATION_ERROR") setError("Enter your email and password.");
      else setError(err?.message || "Could not sign in. Try again.");
    }
  };

  return (
    <div className="signin">
      <main className="signin-card">
        <img className="signin-logo" src="/assets/logo.svg" alt="PeachHacks" width="210" height="79" />
        <p className="eyebrow">Organizer admin</p>
        {forgot ? (
          <ForgotPassword initialEmail={email} onBack={() => setForgot(false)} />
        ) : (
          <>
            <h1>Sign in</h1>
            {MOCK_MODE && (
              <p className="notice">
                Mock mode: any email and password work. An email starting with “volunteer” signs in as a check-in
                volunteer. Use the password “wrong” to see the error.
              </p>
            )}
            {notice && !error && (
              <p className="notice" role="status">
                {notice}
              </p>
            )}
            <form onSubmit={submit}>
              <div className="field">
                <label htmlFor="signin-email">Email</label>
                <input
                  id="signin-email"
                  type="email"
                  autoComplete="username"
                  inputMode="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  aria-describedby={error ? "signin-error" : undefined}
                />
              </div>
              <div className="field">
                <label htmlFor="signin-password">Password</label>
                <input
                  id="signin-password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  aria-describedby={error ? "signin-error" : undefined}
                />
              </div>
              {error && (
                <p id="signin-error" className="inline-error" role="alert">
                  {error}
                </p>
              )}
              <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
                {busy ? "Signing in…" : "Sign in"}
              </button>
            </form>
            <button
              type="button"
              className="link-btn signin-switch"
              onClick={() => {
                setError(null);
                setForgot(true);
              }}
            >
              Forgot password?
            </button>
            <p className="signin-foot">For PeachHacks organizers and volunteers. Ask an admin for an account.</p>
          </>
        )}
      </main>
    </div>
  );
}
