import { useState } from "react";
import { MOCK_MODE, api } from "../api/client.js";

export default function SignIn({ notice, onSignedIn }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

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
      if (err?.code === "INVALID_CREDENTIALS") setError("That email and password do not match an admin account.");
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
        <h1>Sign in</h1>
        {MOCK_MODE && (
          <p className="notice">Mock mode: any email and password work. Use the password “wrong” to see the error.</p>
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
        <p className="signin-foot">For PeachHacks organizers only. Ask an existing admin for an account.</p>
      </main>
    </div>
  );
}
