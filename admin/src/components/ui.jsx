import { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";
import { isAccepted, isNotified } from "../lib/acceptance.js";
import { errorText, formatNumber, statusLabel } from "../lib/format.js";
import { href } from "../lib/router.js";

export function Spinner({ label = "Loading" }) {
  return (
    <span className="spinner" role="status">
      <span className="spinner-dot" aria-hidden="true" />
      <span className="sr-only">{label}</span>
    </span>
  );
}

export function LoadingBlock({ label = "Loading…" }) {
  return (
    <div className="state-block" role="status">
      <span className="spinner-dot" aria-hidden="true" />
      <p>{label}</p>
    </div>
  );
}

// 403s by API error code: a retry cannot fix them, so they get no "Try again".
const REFUSALS = {
  FORBIDDEN: {
    title: "You don’t have access to this",
    body: "Your account cannot open this part of the admin site. Ask an organizer if you think it should.",
  },
  WEB_CHECK_IN_ADMIN_ONLY: {
    title: "Check-in is done in the PeachHacks staff app",
    body: "Volunteers check people in with the staff app, so this screen is for admins only right now. Ask an organizer if you need it here.",
  },
};

export const isStaffAppOnly = (error) => error?.code === "WEB_CHECK_IN_ADMIN_ONLY";

export function ErrorBlock({ error, onRetry, title = "Could not load this" }) {
  const refusal = REFUSALS[error?.code];
  if (refusal) {
    return (
      <div className="state-block state-error" role="alert">
        <strong>{refusal.title}</strong>
        <p>{refusal.body}</p>
      </div>
    );
  }
  return (
    <div className="state-block state-error" role="alert">
      <strong>{title}</strong>
      <p>{errorText(error)}</p>
      {onRetry && (
        <button type="button" className="btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyBlock({ title, children }) {
  return (
    <div className="state-block">
      <strong>{title}</strong>
      {children && <p>{children}</p>}
    </div>
  );
}

export function InlineError({ error, children }) {
  if (!error && !children) return null;
  return (
    <p className="inline-error" role="alert">
      {children || errorText(error)}
    </p>
  );
}

// Spread onto the input that a FieldError with the same id belongs to.
export const errorProps = (message, id) => ({
  "aria-invalid": message ? true : undefined,
  "aria-describedby": message ? id : undefined,
});

export function FieldError({ id, message }) {
  if (!message) return null;
  return (
    <p id={id} className="inline-error">
      {message}
    </p>
  );
}

export function StatusBadge({ status }) {
  return <span className={`badge badge-${String(status).toLowerCase()}`}>{statusLabel(status)}</span>;
}

// Accepted people are either still in the acceptance bucket or already emailed.
export function AcceptanceBadge({ item }) {
  if (!isAccepted(item)) return <StatusBadge status={item.status} />;
  return (
    <span className="tag-row">
      <StatusBadge status={item.status} />
      <ToldTag told={isNotified(item)} />
    </span>
  );
}

export function Tag({ tone = "neutral", children }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export const ToldTag = ({ told }) => (told ? <Tag tone="accepted">Told</Tag> : <Tag tone="waitlisted">Not told yet</Tag>);

export const ConfirmedTag = ({ confirmed }) =>
  confirmed ? <Tag tone="accepted">Confirmed</Tag> : <Tag tone="waitlisted">Unconfirmed</Tag>;

export function Tile({ label, value, note, to }) {
  return (
    <div className="tile">
      <span className="tile-label">{label}</span>
      <strong className="tile-value">{formatNumber(value)}</strong>
      {note && <span className="tile-note">{note}</span>}
      {to && (
        <a className="tile-link" href={href(to)}>
          View list
        </a>
      )}
    </div>
  );
}

export function PageHeader({ title, description, children }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {children && <div className="page-actions">{children}</div>}
    </header>
  );
}

export function Pagination({ page, size, total, onPage, disabled }) {
  const pages = Math.max(1, Math.ceil(total / size));
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);
  return (
    <nav className="pagination" aria-label="Pagination">
      <p aria-live="polite">
        {total === 0 ? "No results" : `${formatNumber(from)}–${formatNumber(to)} of ${formatNumber(total)}`}
      </p>
      <div className="pagination-buttons">
        <button type="button" className="btn" disabled={disabled || page <= 0} onClick={() => onPage(page - 1)}>
          Previous
        </button>
        <span className="pagination-page">
          Page {formatNumber(page + 1)} of {formatNumber(pages)}
        </span>
        <button type="button" className="btn" disabled={disabled || page >= pages - 1} onClick={() => onPage(page + 1)}>
          Next
        </button>
      </div>
    </nav>
  );
}

const ToastContext = createContext(() => {});

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(0);

  const notify = useCallback((message, tone = "success") => {
    nextId.current += 1;
    const id = nextId.current;
    setToasts((list) => [...list.slice(-2), { id, message, tone }]);
    window.setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), tone === "error" ? 8000 : 4500);
  }, []);

  const dismiss = useCallback((id) => setToasts((list) => list.filter((t) => t.id !== id)), []);
  const value = useMemo(() => notify, [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" aria-live="polite" aria-atomic="false">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast${toast.tone === "error" ? " toast-error" : ""}`} role={toast.tone === "error" ? "alert" : "status"}>
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
