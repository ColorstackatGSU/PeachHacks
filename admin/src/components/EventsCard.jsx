import { useId, useState } from "react";
import { api } from "../api/client.js";
import { ConfirmDialog, Modal } from "./Modal.jsx";
import { ErrorBlock, InlineError, LoadingBlock, Tag, useToast } from "./ui.jsx";
import { errorText, formatDateTime, formatNumber, plural } from "../lib/format.js";
import { useEvents } from "../lib/hooks.js";

const pad = (n) => String(n).padStart(2, "0");

// <input type="datetime-local"> works in the organizer's own time zone; the API takes UTC.
function toLocalInput(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(value) {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

function EventFields({ ids, form, setForm, fieldErrors }) {
  return (
    <>
      <div className="field">
        <label htmlFor={`${ids}-name`}>Name</label>
        <input
          id={`${ids}-name`}
          type="text"
          autoComplete="off"
          maxLength={120}
          placeholder="Intro to React workshop"
          value={form.name}
          onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
          aria-invalid={fieldErrors.name ? true : undefined}
          aria-describedby={fieldErrors.name ? `${ids}-name-error` : undefined}
        />
        {fieldErrors.name && (
          <p id={`${ids}-name-error`} className="inline-error">
            {fieldErrors.name}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor={`${ids}-starts`}>Starts (optional)</label>
        <input
          id={`${ids}-starts`}
          type="datetime-local"
          value={form.startsAt}
          onChange={(e) => setForm((prev) => ({ ...prev, startsAt: e.target.value }))}
          aria-invalid={fieldErrors.startsAt ? true : undefined}
        />
        {fieldErrors.startsAt && <p className="inline-error">{fieldErrors.startsAt}</p>}
      </div>
    </>
  );
}

const EMPTY = { name: "", startsAt: "" };

export function EventsCard() {
  const notify = useToast();
  const ids = useId();
  const events = useEvents();
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(null);
  const [editForm, setEditForm] = useState(EMPTY);
  const [editErrors, setEditErrors] = useState({});
  const [editError, setEditError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState(null);
  const [removeBusy, setRemoveBusy] = useState(false);
  const [removeError, setRemoveError] = useState(null);
  const [exporting, setExporting] = useState(null);

  const rows = Array.isArray(events.data) ? events.data : [];

  const add = async (event) => {
    event.preventDefault();
    setFormError(null);
    if (form.name.trim() === "") {
      setFieldErrors({ name: "Enter a name." });
      return;
    }
    setFieldErrors({});
    setAdding(true);
    try {
      await api.createEvent({ name: form.name.trim(), startsAt: fromLocalInput(form.startsAt) });
      notify(`Added ${form.name.trim()}.`);
      setForm(EMPTY);
      events.reload();
    } catch (error) {
      if (error?.fieldErrors) setFieldErrors(error.fieldErrors);
      else setFormError(error);
    } finally {
      setAdding(false);
    }
  };

  const save = async (event) => {
    event.preventDefault();
    setEditError(null);
    if (editForm.name.trim() === "") {
      setEditErrors({ name: "Enter a name." });
      return;
    }
    setEditErrors({});
    setSaving(true);
    try {
      await api.updateEvent(editing.id, { name: editForm.name.trim(), startsAt: fromLocalInput(editForm.startsAt) });
      notify("Event saved.");
      setEditing(null);
      events.reload();
    } catch (error) {
      if (error?.fieldErrors) setEditErrors(error.fieldErrors);
      else setEditError(error);
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    setRemoveBusy(true);
    setRemoveError(null);
    try {
      await api.deleteEvent(removing.id);
      notify(`Deleted ${removing.name}.`);
      setRemoving(null);
      events.reload();
    } catch (error) {
      setRemoveError(error);
      // The count that made deleting possible is out of date; show the current one.
      if (error?.code === "EVENT_HAS_CHECK_INS") events.reload();
    } finally {
      setRemoveBusy(false);
    }
  };

  const exportAttendees = async (row) => {
    setExporting(row.id);
    try {
      await api.exportEventAttendees(row.id);
    } catch (error) {
      notify(`Export failed: ${errorText(error)}`, "error");
    } finally {
      setExporting(null);
    }
  };

  return (
    <section className="card" aria-labelledby={`${ids}-title`}>
      <div className="card-head">
        <h2 id={`${ids}-title`}>Check-in events</h2>
        <span className="muted">General check-in is attendance. Add a workshop to take a separate count for it.</span>
      </div>

      {events.error && (
        <ErrorBlock
          title={events.data ? "Could not refresh the list" : "Could not load events"}
          error={events.error}
          onRetry={events.reload}
        />
      )}
      {!events.data && !events.error && <LoadingBlock label="Loading events…" />}

      {rows.length > 0 && (
        <div className="table-wrap">
          <table className="data-table">
            <caption className="sr-only">Check-in events</caption>
            <thead>
              <tr>
                <th scope="col">Event</th>
                <th scope="col">Starts</th>
                <th scope="col">Checked in</th>
                <th scope="col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <th scope="row" data-label="Event">
                    {row.name} {row.general && <Tag tone="neutral">Built in</Tag>}
                  </th>
                  <td data-label="Starts" className="cell-nowrap">
                    {row.startsAt ? formatDateTime(row.startsAt) : <span className="muted">Not set</span>}
                  </td>
                  <td data-label="Checked in">{formatNumber(row.checkedIn)}</td>
                  <td className="cell-actions">
                    <span className="row-actions">
                      <button
                        type="button"
                        className="btn btn-small"
                        disabled={exporting === row.id}
                        aria-label={`Export attendees of ${row.name} as CSV`}
                        onClick={() => exportAttendees(row)}
                      >
                        {exporting === row.id ? "Preparing…" : "Export CSV"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-small"
                        aria-label={`Edit ${row.name}`}
                        onClick={() => {
                          setEditErrors({});
                          setEditError(null);
                          setEditForm({ name: row.name, startsAt: toLocalInput(row.startsAt) });
                          setEditing(row);
                        }}
                      >
                        Edit
                      </button>
                      {!row.general && (
                        <button
                          type="button"
                          className="btn btn-small btn-danger-quiet"
                          aria-label={`Delete ${row.name}`}
                          aria-describedby={row.checkedIn > 0 ? `${row.id}-keep` : undefined}
                          disabled={row.checkedIn > 0}
                          onClick={() => {
                            setRemoveError(null);
                            setRemoving(row);
                          }}
                        >
                          Delete
                        </button>
                      )}
                    </span>
                    {!row.general && row.checkedIn > 0 && (
                      <span id={`${row.id}-keep`} className="cell-note muted small">
                        Has {plural(row.checkedIn, "check-in")}, so it cannot be deleted
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form className="admin-form" onSubmit={add} noValidate aria-labelledby={`${ids}-add`}>
        <h3 id={`${ids}-add`}>Add a workshop</h3>
        <div className="admin-form-grid">
          <EventFields ids={`${ids}-new`} form={form} setForm={setForm} fieldErrors={fieldErrors} />
        </div>
        <InlineError>{formError ? errorText(formError) : null}</InlineError>
        <button type="submit" className="btn btn-primary" disabled={adding}>
          {adding ? "Adding…" : "Add workshop"}
        </button>
      </form>

      {editing && (
        <Modal
          title={`Edit ${editing.name}`}
          onDismiss={() => setEditing(null)}
          busy={saving}
          footer={
            <>
              <button type="button" className="btn" disabled={saving} onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button type="submit" form={`${ids}-edit-form`} className="btn btn-primary" disabled={saving}>
                {saving ? "Saving…" : "Save"}
              </button>
            </>
          }
        >
          <form id={`${ids}-edit-form`} className="stack" onSubmit={save} noValidate>
            <EventFields ids={`${ids}-edit`} form={editForm} setForm={setEditForm} fieldErrors={editErrors} />
          </form>
          <InlineError error={editError} />
        </Modal>
      )}

      {removing && (
        <ConfirmDialog
          title="Delete this event?"
          confirmLabel="Delete event"
          danger
          busy={removeBusy}
          error={removeError}
          onConfirm={remove}
          onCancel={() => setRemoving(null)}
        >
          <p>
            <strong>{removing.name}</strong> has no check-ins and will be permanently deleted. General check-in is not
            affected. If someone is checked in to it in the meantime, it is kept.
          </p>
        </ConfirmDialog>
      )}
    </section>
  );
}
