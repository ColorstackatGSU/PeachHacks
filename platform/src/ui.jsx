import { createContext, useCallback, useContext, useMemo, useState } from "react";

const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const dismiss = useCallback((id) => setToasts((list) => list.filter((toast) => toast.id !== id)), []);
  const notify = useCallback(
    (message, tone = "ok") => {
      const id = `${Date.now()}-${Math.random()}`;
      setToasts((list) => [...list.slice(-2), { id, message, tone }]);
      window.setTimeout(() => dismiss(id), tone === "error" ? 9000 : 5000);
    },
    [dismiss],
  );
  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast${toast.tone === "error" ? " toast-error" : ""}`}>
            <span>{toast.message}</span>
            <button type="button" className="toast-close" aria-label="Dismiss" onClick={() => dismiss(toast.id)}>
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);

export function ErrorNote({ error, onRetry }) {
  if (!error) return null;
  return (
    <p className="note note-error" role="alert">
      {error.message || "Something went wrong."}{" "}
      {onRetry && (
        <button type="button" className="link-btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </p>
  );
}

export function FieldError({ id, message }) {
  if (!message) return null;
  return (
    <p id={id} className="field-error">
      {message}
    </p>
  );
}

const AVATAR_TONES = ["peach", "sky", "mist", "cream"];

export function Avatar({ firstName, lastName, id }) {
  const initials = `${firstName?.[0] || ""}${lastName?.[0] || ""}`.toUpperCase() || "?";
  const tone = useMemo(() => {
    let sum = 0;
    for (const char of String(id || initials)) sum += char.charCodeAt(0);
    return AVATAR_TONES[sum % AVATAR_TONES.length];
  }, [id, initials]);
  return (
    <span className={`avatar avatar-${tone}`} aria-hidden="true">
      {initials}
    </span>
  );
}

export function DiscordHandle({ username }) {
  const notify = useToast();
  if (!username) return <span className="discord-handle discord-none">Discord not connected</span>;
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(username);
      notify(`Copied ${username}.`);
    } catch {
      notify("Could not copy. Select the name and copy it yourself.", "error");
    }
  };
  return (
    <span className="discord-handle">
      <img src="/assets/discord_logo.png" alt="Discord" width="20" height="20" />
      <span className="discord-name">{username}</span>
      <button type="button" className="btn btn-small" onClick={copy} aria-label={`Copy Discord username ${username}`}>
        Copy
      </button>
    </span>
  );
}

export const fullName = (person) => `${person.firstName} ${person.lastName}`.trim();
