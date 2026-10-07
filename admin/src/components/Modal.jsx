import { useEffect, useId, useRef } from "react";
import { InlineError } from "./ui.jsx";

// Native <dialog>: showModal() traps focus, inerts the page and handles Escape.
// Render it only while it should be open; focus returns to the previous element.
export function Modal({ title, onDismiss, variant = "dialog", busy = false, children, footer }) {
  const ref = useRef(null);
  const titleId = useId();
  const dismissRef = useRef(onDismiss);
  const busyRef = useRef(busy);

  useEffect(() => {
    dismissRef.current = onDismiss;
    busyRef.current = busy;
  });

  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement;
    if (!dialog.open) dialog.showModal();
    const preferred = dialog.querySelector("[data-autofocus]");
    if (preferred) preferred.focus();

    const tryDismiss = () => {
      if (!busyRef.current) dismissRef.current();
    };
    const onCancel = (event) => {
      event.preventDefault();
      tryDismiss();
    };
    // A click that lands on the <dialog> itself is a click on the backdrop.
    const onClick = (event) => {
      if (event.target === dialog) tryDismiss();
    };
    dialog.addEventListener("cancel", onCancel);
    dialog.addEventListener("click", onClick);
    return () => {
      dialog.removeEventListener("cancel", onCancel);
      dialog.removeEventListener("click", onClick);
      if (dialog.open) dialog.close();
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus();
    };
  }, []);

  return (
    <dialog ref={ref} className={`modal modal-${variant}`} aria-labelledby={titleId}>
      <div className="modal-panel">
        <header className="modal-header">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="icon-btn" aria-label="Close" disabled={busy} onClick={onDismiss}>
            ×
          </button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-footer">{footer}</footer>}
      </div>
    </dialog>
  );
}

export function ConfirmDialog({
  title,
  children,
  confirmLabel = "Confirm",
  danger = false,
  busy = false,
  error = null,
  onConfirm,
  onCancel,
}) {
  return (
    <Modal
      title={title}
      onDismiss={onCancel}
      busy={busy}
      footer={
        <>
          <button type="button" className="btn" data-autofocus disabled={busy} onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className={`btn ${danger ? "btn-danger" : "btn-primary"}`} disabled={busy} onClick={onConfirm}>
            {busy ? "Working…" : confirmLabel}
          </button>
        </>
      }
    >
      {children}
      <InlineError error={error} />
    </Modal>
  );
}
