import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import { MOCK_MODE, api } from "../api/client.js";
import { ConfirmDialog } from "../components/Modal.jsx";
import { Scanner } from "../components/Scanner.jsx";
import {
  EmptyBlock,
  ErrorBlock,
  LoadingBlock,
  PageHeader,
  Pagination,
  StatusBadge,
  isStaffAppOnly,
} from "../components/ui.jsx";
import { errorText, formatNumber, formatWhen, fullName, statusLabel } from "../lib/format.js";
import { useAsync, useDebounced, useEvents } from "../lib/hooks.js";
import { cameraSupported, primeFeedback, signal } from "../lib/scanner.js";

const PAGE_SIZE = 25;
const MODE_KEY = "peachhacks.admin.checkin.mode";
// How long a result stays up before scanning resumes on its own.
const RESULT_HOLD_MS = { CHECKED_IN: 2200, ALREADY_CHECKED_IN: 3500, NOT_RECOGNISED: 3000 };
// A ticket still in frame after its result clears is ignored for this long.
const SAME_CODE_QUIET_MS = 6000;

function initialMode() {
  try {
    const saved = window.sessionStorage.getItem(MODE_KEY);
    if (saved === "scan" || saved === "search") return saved;
  } catch {
    // Fall through to the default.
  }
  const phone = window.matchMedia?.("(pointer: coarse)").matches;
  return MOCK_MODE || (phone && cameraSupported()) ? "scan" : "search";
}

const isRefusal = (error) => error?.code === "NOT_ACCEPTED";

// `status` is left out when the API refused a row that still read as accepted.
function NotAccepted({ status }) {
  return (
    <p className="checkin-warning">
      {status && <StatusBadge status={status} />}
      <span>Not accepted. They can’t be checked in until an organizer accepts them.</span>
    </p>
  );
}

function MissingGeneral({ event, item }) {
  if (!event || event.general || !item || item.generalCheckedIn) return null;
  return <p className="checkin-note">Has not done general check-in yet. Send them to the front desk afterwards.</p>;
}

function ScanResult({ scan, event, busy, onRetry, onDismiss }) {
  const { outcome, item, error } = scan;
  let tone = "bad";
  let title = "Ticket not recognised";
  let body = <p>This is not a PeachHacks ticket, or it is no longer valid. Try searching for their name.</p>;
  let actions = null;

  if (error) {
    title = "Could not check in";
    body = <p>{errorText(error)} Nothing was recorded.</p>;
    actions = (
      <>
        <button type="button" className="btn btn-primary btn-big" disabled={busy} onClick={onRetry}>
          {busy ? "Trying…" : "Try again"}
        </button>
        <button type="button" className="btn btn-big" disabled={busy} onClick={onDismiss}>
          Dismiss
        </button>
      </>
    );
  } else if (outcome === "CHECKED_IN") {
    tone = "good";
    title = "Checked in";
    body = (
      <>
        <p className="scan-name">{fullName(item)}</p>
        <p>{item.school}</p>
        <MissingGeneral event={event} item={item} />
      </>
    );
  } else if (outcome === "ALREADY_CHECKED_IN") {
    tone = "warn";
    title = "Already checked in";
    body = (
      <>
        <p className="scan-name">{fullName(item)}</p>
        <p>
          at {formatWhen(item.checkedInAt)}
          {item.checkedInBy ? ` by ${item.checkedInBy}` : ""}
        </p>
        <MissingGeneral event={event} item={item} />
      </>
    );
  } else if (outcome === "NOT_ACCEPTED") {
    title = "Not accepted";
    body = (
      <>
        <p className="scan-name">{fullName(item)}</p>
        <p>
          {item.school} · status: {statusLabel(item.status)}
        </p>
        <p>They have not been checked in, and can’t be until an organizer accepts them.</p>
      </>
    );
    actions = (
      <button type="button" className="btn btn-primary btn-big" disabled={busy} onClick={onDismiss}>
        Next ticket
      </button>
    );
  }

  return (
    <div className={`scan-result scan-${tone}`} role="alert">
      <strong className="scan-title">{title}</strong>
      {body}
      {actions && <div className="scan-actions">{actions}</div>}
    </div>
  );
}

function ScanMode({ event, eventId, onCheckedIn, onRestricted, onUseSearch }) {
  const ids = useId();
  const [scan, setScan] = useState(null);
  const [busy, setBusy] = useState(false);
  const [pasted, setPasted] = useState("");
  const busyRef = useRef(false);
  const showingRef = useRef(false);
  const last = useRef({ code: null, at: 0 });
  const timer = useRef(null);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const clear = useCallback(() => {
    window.clearTimeout(timer.current);
    showingRef.current = false;
    last.current = { ...last.current, at: Date.now() };
    setScan(null);
  }, []);

  const submit = useCallback(
    async (code) => {
      if (busyRef.current) return;
      busyRef.current = true;
      showingRef.current = true;
      window.clearTimeout(timer.current);
      last.current = { code, at: Date.now() };
      setBusy(true);
      try {
        const result = await api.scanTicket({ code, eventId });
        const outcome = result?.result;
        setScan({ code, outcome, item: result?.item || null });
        signal(outcome === "CHECKED_IN" ? "success" : "problem");
        if (outcome === "CHECKED_IN") onCheckedIn();
        const hold = RESULT_HOLD_MS[outcome];
        if (hold) timer.current = window.setTimeout(clear, hold);
      } catch (error) {
        setScan({ code, error });
        signal("problem");
        if (isStaffAppOnly(error)) onRestricted();
      } finally {
        busyRef.current = false;
        setBusy(false);
      }
    },
    [eventId, onCheckedIn, onRestricted, clear],
  );

  const handleRead = useCallback(
    (code) => {
      if (busyRef.current || showingRef.current) return;
      if (code === last.current.code && Date.now() - last.current.at < SAME_CODE_QUIET_MS) return;
      submit(code);
    },
    [submit],
  );

  return (
    <div className="scan-mode">
      {MOCK_MODE ? (
        <form
          className="card"
          onSubmit={(e) => {
            e.preventDefault();
            if (pasted.trim()) submit(pasted.trim());
          }}
        >
          <div className="field">
            <label htmlFor={`${ids}-code`}>Ticket code (mock mode has no camera)</label>
            <div className="input-with-button">
              <input
                id={`${ids}-code`}
                type="text"
                autoComplete="off"
                placeholder="Paste a ticket token or ticket URL"
                value={pasted}
                onChange={(e) => setPasted(e.target.value)}
              />
              <button type="submit" className="btn btn-primary" disabled={busy || !pasted.trim()}>
                Scan
              </button>
            </div>
            <p className="hint">
              Each accepted registration’s token is shown in its detail drawer under Ticket. Anything else reads as not
              recognised.
            </p>
          </div>
        </form>
      ) : (
        <Scanner paused={Boolean(scan) || busy} onRead={handleRead} onUseSearch={onUseSearch} />
      )}
      {scan ? (
        <ScanResult
          scan={scan}
          event={event}
          busy={busy}
          onRetry={() => submit(scan.code)}
          onDismiss={clear}
        />
      ) : (
        <p className="scan-idle muted" role="status">
          {busy ? "Checking the ticket…" : "Ready for the next ticket."}
        </p>
      )}
    </div>
  );
}

function PersonRow({ item, event, pending, error, onCheckIn, onUndo }) {
  const checkedIn = Boolean(item.checkedInAt);
  const accepted = item.status === "ACCEPTED";
  return (
    <li className={`person${checkedIn ? " is-in" : ""}`}>
      <div className="person-main">
        <strong className="person-name">{fullName(item)}</strong>
        <span className="person-school">{item.school}</span>
        <span className="person-email">{item.email}</span>
        {!accepted && <NotAccepted status={item.status} />}
        {accepted && isRefusal(error) && <NotAccepted />}
        <MissingGeneral event={event} item={item} />
        {error && !isRefusal(error) && (
          <p className="inline-error" role="alert">
            {checkedIn ? "Could not undo" : "Could not check in"}: {errorText(error)}
          </p>
        )}
      </div>
      <div className="person-action">
        {checkedIn ? (
          <>
            <p className="person-done" role="status">
              <span aria-hidden="true">✓</span> Checked in {formatWhen(item.checkedInAt)}
              {item.checkedInBy ? ` by ${item.checkedInBy}` : ""}
            </p>
            <button type="button" className="btn btn-small" disabled={pending} onClick={onUndo}>
              {pending ? "Undoing…" : error ? "Retry undo" : "Undo"}
            </button>
          </>
        ) : (
          accepted && (
            <button
              type="button"
              className="btn btn-primary btn-big"
              disabled={pending}
              aria-label={`Check in ${fullName(item)}`}
              onClick={onCheckIn}
            >
              {pending ? "Checking in…" : error ? "Try again" : "Check in"}
            </button>
          )
        )}
      </div>
    </li>
  );
}

function SearchMode({ event, list, search, setSearch, page, setPage, changes, onChange }) {
  const ids = useId();
  const inputRef = useRef(null);
  const [pending, setPending] = useState({});
  const [errors, setErrors] = useState({});
  const [undoing, setUndoing] = useState(null);

  // Deferred a tick so it wins over the layout moving focus to the page after navigation.
  useEffect(() => {
    const timer = window.setTimeout(() => inputRef.current?.focus(), 0);
    return () => window.clearTimeout(timer);
  }, []);

  const items = (list.data?.items || []).map((item) => changes[item.id] || item);
  const total = list.data?.total || 0;

  const act = async (item, undo) => {
    setPending((prev) => ({ ...prev, [item.id]: true }));
    setErrors((prev) => ({ ...prev, [item.id]: null }));
    try {
      const updated = undo ? await api.undoCheckIn(item.id, event?.id) : await api.checkIn(item.id, event?.id);
      onChange(item, updated);
      if (!undo) signal("success");
    } catch (error) {
      setErrors((prev) => ({ ...prev, [item.id]: error }));
      if (!undo) signal("problem");
      // The row was out of date, or web check-in was restricted since the list loaded.
      if (isRefusal(error) || isStaffAppOnly(error)) list.reload();
    } finally {
      setPending((prev) => ({ ...prev, [item.id]: false }));
    }
  };

  return (
    <>
      <form className="checkin-search" role="search" onSubmit={(e) => e.preventDefault()}>
        <label htmlFor={`${ids}-q`} className="sr-only">
          Search by name or email
        </label>
        <input
          id={`${ids}-q`}
          ref={inputRef}
          type="search"
          inputMode="search"
          enterKeyHint="search"
          autoComplete="off"
          autoCapitalize="off"
          spellCheck={false}
          placeholder="Search by name or email"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </form>

      {list.error && <ErrorBlock title="Could not load the list" error={list.error} onRetry={list.reload} />}
      {!list.error && !list.data && <LoadingBlock label="Loading people…" />}
      {!list.error && list.data && items.length === 0 && (
        <EmptyBlock title={search.trim() ? "No one matches" : "No registrations yet"}>
          {search.trim()
            ? "Check the spelling, try their email, or ask an organizer. They may not be registered."
            : "People appear here once they have registered."}
        </EmptyBlock>
      )}

      {list.data && items.length > 0 && (
        <ul className={`people${list.loading ? " is-loading" : ""}`} aria-busy={list.loading}>
          {items.map((item) => (
            <PersonRow
              key={item.id}
              item={item}
              event={event}
              pending={Boolean(pending[item.id])}
              error={errors[item.id]}
              onCheckIn={() => act(item, false)}
              onUndo={() => setUndoing(item)}
            />
          ))}
        </ul>
      )}

      {list.data && total > PAGE_SIZE && (
        <Pagination page={page} size={list.data.size || PAGE_SIZE} total={total} onPage={setPage} disabled={list.loading} />
      )}

      {undoing && (
        <ConfirmDialog
          title="Undo this check-in?"
          confirmLabel="Undo check-in"
          danger
          onConfirm={() => {
            const item = undoing;
            setUndoing(null);
            act(item, true);
          }}
          onCancel={() => setUndoing(null)}
        >
          <p>
            <strong>{fullName(undoing)}</strong> will be marked as not checked in
            {event && !event.general ? ` for ${event.name}` : ""}.
            {event && !event.general ? "" : " If they were given a badge, it is revoked and has to be bound again."} Only
            do this to fix a mistake.
          </p>
        </ConfirmDialog>
      )}
    </>
  );
}

export default function CheckIn() {
  const ids = useId();
  const events = useEvents();
  const [chosenEvent, setChosenEvent] = useState("");
  const [mode, setMode] = useState(initialMode);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [changed, setChanged] = useState({ src: null, map: {} });

  const eventList = useMemo(() => (Array.isArray(events.data) ? events.data : []), [events.data]);
  const event = eventList.find((e) => e.id === chosenEvent) || eventList.find((e) => e.general) || null;
  const eventId = event?.id || "";

  const q = useDebounced(search.trim(), 250);
  const load = useCallback(
    (abort) => api.checkInList({ eventId, q, page, size: PAGE_SIZE }, abort),
    [eventId, q, page],
  );
  const list = useAsync(load);

  // Rows changed here keep their place until the list is fetched again, so the person a
  // volunteer just checked in does not jump away from under their finger.
  const changes = changed.src === list.data ? changed.map : {};
  const delta = Object.entries(changes).reduce((sum, [id, item]) => {
    const original = list.data?.items.find((row) => row.id === id);
    return sum + (item.checkedInAt ? 1 : 0) - (original?.checkedInAt ? 1 : 0);
  }, 0);
  const checkedIn = (list.data?.checkedInTotal ?? event?.checkedIn ?? 0) + delta;
  const registered = list.data?.registrationTotal;

  const switchMode = (next) => {
    if (next === "scan") primeFeedback();
    setMode(next);
    try {
      window.sessionStorage.setItem(MODE_KEY, next);
    } catch {
      // The choice just will not be remembered.
    }
  };

  // A volunteer while web check-in is restricted to admins: every check-in route refuses,
  // so the message stands in for the whole screen.
  if (isStaffAppOnly(list.error)) {
    return (
      <>
        <PageHeader title="Check-in" />
        <ErrorBlock error={list.error} />
      </>
    );
  }

  return (
    <>
      <PageHeader title="Check-in" description="Scan a ticket, or search for a name, and check people in as they arrive." />

      <section className="checkin-bar" aria-label="What you are checking in">
        <div className="field">
          <label htmlFor={`${ids}-event`}>Checking in for</label>
          <select
            id={`${ids}-event`}
            value={eventId}
            disabled={eventList.length === 0}
            onChange={(e) => {
              setChosenEvent(e.target.value);
              setPage(0);
            }}
          >
            {eventList.length === 0 && <option value="">General check-in</option>}
            {eventList.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </div>
        <p className="checkin-count" aria-live="polite">
          <strong>{formatNumber(checkedIn)}</strong>
          {typeof registered === "number" ? ` of ${formatNumber(registered)}` : ""} checked in
        </p>
        <div className="segmented checkin-modes" role="group" aria-label="How to check in">
          <button type="button" aria-pressed={mode === "scan"} onClick={() => switchMode("scan")}>
            Scan
          </button>
          <button type="button" aria-pressed={mode === "search"} onClick={() => switchMode("search")}>
            Search
          </button>
        </div>
      </section>

      {events.error && (
        <ErrorBlock title="Could not load the event list" error={events.error} onRetry={events.reload} />
      )}

      {mode === "scan" ? (
        <ScanMode
          event={event}
          eventId={eventId}
          onCheckedIn={list.reload}
          onRestricted={list.reload}
          onUseSearch={() => switchMode("search")}
        />
      ) : (
        <SearchMode
          key={chosenEvent}
          event={event}
          list={list}
          search={search}
          setSearch={setSearch}
          page={page}
          setPage={setPage}
          changes={changes}
          onChange={(item, updated) =>
            setChanged((prev) => ({
              src: list.data,
              map: { ...(prev.src === list.data ? prev.map : {}), [item.id]: updated || item },
            }))
          }
        />
      )}
    </>
  );
}
