import { useId, useState } from "react";
import { api } from "../api.js";
import { href } from "../hooks.js";
import { Alert, AuthFrame, PasswordInput } from "./SignIn.jsx";

const MIN_PASSWORD_LENGTH = 10;
const MAX_PASSWORD_LENGTH = 72;

export default function SetPassword({ token, onSignedIn }) {
  const ids = useId();
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError({ message: `Use at least ${MIN_PASSWORD_LENGTH} characters.` });
      return;
    }
    if (password.length > MAX_PASSWORD_LENGTH) {
      setError({ message: `Use at most ${MAX_PASSWORD_LENGTH} characters.` });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onSignedIn(await api.setPassword(token, password));
    } catch (failure) {
      setError(failure.fieldErrors?.password ? { message: failure.fieldErrors.password } : failure);
    } finally {
      setBusy(false);
    }
  };

  if (!token) {
    return (
      <AuthFrame title="This link is incomplete">
        <p className="tag-text">Open the link from your email again, or ask for a new one from the sign-in page.</p>
        <div className="tag-actions">
          <a className="tag-button" href={href("/")}>
            Back to sign in
          </a>
        </div>
      </AuthFrame>
    );
  }

  return (
    <AuthFrame title="Choose your password" intro="You will sign in with the email you applied with.">
      <form className="tag-form" onSubmit={submit} noValidate>
        <div className="tag-field">
          <label htmlFor={`${ids}-password`}>New password</label>
          <PasswordInput
            id={`${ids}-password`}
            value={password}
            onChange={setPassword}
            autoComplete="new-password"
            describedBy={`${ids}-rule`}
          />
          <p id={`${ids}-rule`} className="tag-hint">
            At least {MIN_PASSWORD_LENGTH} characters. Press Show to check what you typed.
          </p>
        </div>
        <Alert error={error} />
        <div className="tag-actions">
          <button type="submit" className="tag-button" disabled={busy} aria-busy={busy}>
            {busy ? "Saving…" : "Save and sign in"}
          </button>
        </div>
      </form>
      <p className="tag-foot">
        <a className="tag-link" href={href("/")}>
          Back to sign in
        </a>
      </p>
    </AuthFrame>
  );
}
