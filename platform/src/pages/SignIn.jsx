import { useEffect, useId, useRef, useState } from "react";
import { api } from "../api.js";
import { useLoad } from "../hooks.js";

const GOOGLE_SCRIPT = "https://accounts.google.com/gsi/client";
const GOOGLE_MAX_WIDTH = 400;

const STARS = [
  { x: "6%", y: "9%", size: 13 },
  { x: "17%", y: "26%", size: 9 },
  { x: "29%", y: "7%", size: 16 },
  { x: "41%", y: "19%", size: 10 },
  { x: "53%", y: "5%", size: 12 },
  { x: "64%", y: "24%", size: 9 },
  { x: "73%", y: "11%", size: 15 },
  { x: "88%", y: "29%", size: 11 },
  { x: "95%", y: "8%", size: 9 },
];

let googleScript = null;
function loadGoogle() {
  if (!googleScript) {
    googleScript = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = GOOGLE_SCRIPT;
      script.async = true;
      script.onload = resolve;
      script.onerror = () => {
        googleScript = null;
        reject(new Error("Google sign-in could not load."));
      };
      document.head.appendChild(script);
    });
  }
  return googleScript;
}

// Google draws its own button in an iframe at a fixed pixel width, so the width is
// measured from the column it sits in.
function GoogleButton({ clientId, onCredential }) {
  const holder = useRef(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    loadGoogle()
      .then(() => {
        if (!active || !holder.current) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => onCredential(response.credential),
        });
        window.google.accounts.id.renderButton(holder.current, {
          theme: "filled_blue",
          size: "large",
          shape: "pill",
          text: "continue_with",
          logo_alignment: "center",
          width: Math.min(GOOGLE_MAX_WIDTH, Math.floor(holder.current.clientWidth)),
        });
      })
      .catch(() => active && setFailed(true));
    return () => {
      active = false;
    };
  }, [clientId, onCredential]);

  if (failed) return <p className="tag-hint">Google sign-in could not load. Use your email and password instead.</p>;
  return <div ref={holder} className="google-button" />;
}

export function PasswordInput({ id, value, onChange, autoComplete, invalid, describedBy }) {
  const [shown, setShown] = useState(false);
  return (
    <div className="password-input">
      <input
        id={id}
        name="password"
        type={shown ? "text" : "password"}
        autoComplete={autoComplete}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={invalid ? "true" : undefined}
        aria-describedby={describedBy}
      />
      <button type="button" onClick={() => setShown((now) => !now)} aria-pressed={shown} aria-controls={id}>
        {shown ? "Hide" : "Show"}
        <span className="sr-only"> password</span>
      </button>
    </div>
  );
}

export function Alert({ error }) {
  return (
    <div className="tag-alert-slot" role="alert">
      {error && <p className="tag-alert">{error.message || "Something went wrong. Try again."}</p>}
    </div>
  );
}

export function AuthFrame({ title, intro, children }) {
  return (
    <div className="auth">
      <div className="auth-sky" aria-hidden="true">
        {STARS.map((star) => (
          <img
            key={`${star.x}-${star.y}`}
            className="auth-star"
            src="/assets/star.svg"
            alt=""
            style={{ left: star.x, top: star.y, width: star.size, height: star.size }}
          />
        ))}
        <img className="auth-cloud auth-cloud-a" src="/assets/Cloud.svg" alt="" />
        <img className="auth-cloud auth-cloud-b" src="/assets/Cloud.svg" alt="" />
        <img className="auth-skyline" src="/assets/Hero.svg" alt="" />
      </div>

      <div className="auth-layout">
        <header className="auth-brand">
          <div className="auth-mark">
            <span className="auth-moon" aria-hidden="true" />
            <img className="auth-logo" src="/assets/logo.svg" alt="PeachHacks" />
          </div>
          <p className="auth-theme">Midnight in the City</p>
          <p className="auth-kicker">
            February 5–7, 2027 <span aria-hidden="true">·</span> Georgia State University, Atlanta
          </p>
        </header>

        <main className="auth-card">
          <div className="auth-card-head">
            <p className="auth-eyebrow">Hacker Platform</p>
            <h1>{title}</h1>
            {intro && <p className="auth-intro">{intro}</p>}
          </div>
          {children}
        </main>
        <p className="auth-legal">
          <a href="https://www.peachhacks.com/privacy">Privacy Policy</a>
          <span aria-hidden="true"> · </span>
          <a href="https://www.peachhacks.com/terms">Terms of Service</a>
        </p>
      </div>
    </div>
  );
}

const loadConfig = (signal) => api.config(signal);
const isEmail = (value) => /^\S+@\S+\.\S+$/.test(value.trim());

export default function SignIn({ onSignedIn }) {
  const ids = useId();
  const config = useLoad(loadConfig);
  const [view, setView] = useState("sign-in");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const run = async (action) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (failure) {
      setError(failure);
    } finally {
      setBusy(false);
    }
  };

  const show = (next) => {
    setError(null);
    setView(next);
  };

  const signIn = (event) => {
    event.preventDefault();
    if (!isEmail(email)) return setError({ message: "Enter the email address you applied with." });
    if (password === "") return setError({ message: "Enter your password, or use the email link below." });
    return run(async () => {
      try {
        onSignedIn(await api.login(email.trim(), password));
      } catch (failure) {
        setPassword("");
        throw failure;
      }
    });
  };

  const sendLink = (event) => {
    event.preventDefault();
    if (!isEmail(email)) return setError({ message: "Enter the email address you applied with." });
    return run(async () => {
      await api.passwordLink(email.trim());
      setView("sent");
    });
  };

  const [onCredential] = useState(
    () => (credential) => run(async () => onSignedIn(await api.googleSignIn(credential))),
  );

  const emailField = (
    <div className="tag-field">
      <label htmlFor={`${ids}-email`}>Email</label>
      <input
        id={`${ids}-email`}
        name="email"
        type="email"
        inputMode="email"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck="false"
        placeholder="The email you applied with"
        value={email}
        onChange={(event) => setEmail(event.target.value)}
      />
    </div>
  );

  if (view === "sent") {
    return (
      <AuthFrame title="Check your inbox">
        <p className="tag-text">
          If <strong>{email.trim()}</strong> belongs to an accepted application, a link to choose your password is on its
          way. It works for one hour.
        </p>
        <p className="tag-hint">Nothing after a couple of minutes? Look in spam, then check the address is the one you applied with.</p>
        <div className="tag-actions">
          <button type="button" className="tag-button" onClick={() => show("sign-in")}>
            Back to sign in
          </button>
        </div>
        <p className="tag-foot">
          <button type="button" className="tag-link" onClick={() => show("link")}>
            Use a different email
          </button>
        </p>
      </AuthFrame>
    );
  }

  if (view === "link") {
    return (
      <AuthFrame title="Get a sign-in link" intro="For your first visit, or if you forgot your password.">
        <form className="tag-form" onSubmit={sendLink} noValidate>
          <p className="tag-text">
            You already have an account: it is your accepted application. We email you a link, and you choose a password.
          </p>
          {emailField}
          <Alert error={error} />
          <div className="tag-actions">
            <button type="submit" className="tag-button" disabled={busy} aria-busy={busy}>
              {busy ? "Sending…" : "Email me a link"}
            </button>
          </div>
        </form>
        <p className="tag-foot">
          <button type="button" className="tag-link" onClick={() => show("sign-in")}>
            Back to sign in
          </button>
        </p>
      </AuthFrame>
    );
  }

  return (
    <AuthFrame title="Sign in" intro="For accepted hackers.">
      {config.data?.googleClientId && (
        <>
          <GoogleButton clientId={config.data.googleClientId} onCredential={onCredential} />
          <p className="tag-divider">
            <span>or with your email</span>
          </p>
        </>
      )}
      <form className="tag-form" onSubmit={signIn} noValidate>
        {emailField}
        <div className="tag-field">
          <label htmlFor={`${ids}-password`}>Password</label>
          <PasswordInput id={`${ids}-password`} value={password} onChange={setPassword} autoComplete="current-password" />
        </div>
        <Alert error={error} />
        <div className="tag-actions">
          <button type="submit" className="tag-button" disabled={busy} aria-busy={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </div>
      </form>
      <p className="tag-foot">
        First time here, or forgot your password?{" "}
        <button type="button" className="tag-link" onClick={() => show("link")}>
          Email me a link
        </button>
      </p>
    </AuthFrame>
  );
}
