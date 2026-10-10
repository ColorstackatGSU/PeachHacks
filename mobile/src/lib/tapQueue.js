import { fullName } from "./format";
import { cleanUid, uidKey } from "./uid";

const SYNCED_KEPT = 100;
const SEEN_MAX_AGE_MS = 72 * 60 * 60 * 1000;
const MAX_RETRY_MS = 60000;

export const retryDelayMs = (attempt) => Math.min(MAX_RETRY_MS, 2000 * 2 ** Math.min(Math.max(attempt, 0), 5));

// What a failed replay means for the tap: "retry" keeps it and tries again later,
// "signed-out" and "forbidden" keep it and stop until someone signs in, and
// "permanent" will never succeed, so it moves to the could-not-sync list.
export function classifyError(error) {
  const status = error?.status || 0;
  if (error?.code === "NETWORK" || status === 0) return "retry";
  if (status === 401) return "signed-out";
  if (status === 403) return "forbidden";
  if (status === 408 || status === 429 || status >= 500) return "retry";
  return "permanent";
}

const RESULT_WORDING = {
  CHECKED_IN: ["checked in", "checked in"],
  ALREADY_CHECKED_IN: ["was already checked in", "were already checked in"],
  NOT_ACCEPTED: ["was for someone who is not accepted", "were for people who are not accepted"],
  REVOKED_BADGE: ["was a revoked badge", "were revoked badges"],
  UNKNOWN_BADGE: ["was an unknown badge", "were unknown badges"],
};

export function countResults(synced) {
  return synced.reduce((counts, tap) => ({ ...counts, [tap.result]: (counts[tap.result] || 0) + 1 }), {});
}

export function syncSummaryLines(synced) {
  const counts = countResults(synced);
  return Object.keys(counts)
    .sort((a, b) => Object.keys(RESULT_WORDING).indexOf(a) - Object.keys(RESULT_WORDING).indexOf(b))
    .map((result) => {
      const count = counts[result];
      const [one, many] = RESULT_WORDING[result] || ["had an unexpected result", "had an unexpected result"];
      return `${count} queued ${count === 1 ? "tap" : "taps"} ${count === 1 ? one : many}`;
    });
}

const emptyState = () => ({ pending: [], failed: [], synced: [], seen: {} });

function restore(saved, nowMs) {
  const state = emptyState();
  if (!saved || typeof saved !== "object") return state;
  ["pending", "failed", "synced"].forEach((list) => {
    if (Array.isArray(saved[list])) state[list] = saved[list].filter((tap) => tap && tap.uid && tap.eventId);
  });
  Object.entries(saved.seen && typeof saved.seen === "object" ? saved.seen : {}).forEach(([key, at]) => {
    const time = new Date(at).getTime();
    if (!Number.isNaN(time) && nowMs - time < SEEN_MAX_AGE_MS) state.seen[key] = at;
  });
  return state;
}

// `storage` is { load(): Promise<object|null>, save(object): Promise<void> }.
// `send(tap)` posts one tap and rejects with an error carrying `status` and `code`.
export function createTapQueue({
  storage,
  send,
  canSend = () => true,
  now = Date.now,
  setTimer = setTimeout,
  clearTimer = clearTimeout,
}) {
  let state = emptyState();
  let status = "idle";
  let loading = null;
  let running = null;
  let saving = Promise.resolve();
  let timer = null;
  let attempt = 0;
  let counter = 0;
  const listeners = new Set();

  const snapshot = () => ({
    pending: state.pending,
    failed: state.failed,
    synced: state.synced,
    status,
  });

  const emit = () => listeners.forEach((listener) => listener(snapshot()));

  function persist() {
    const copy = JSON.parse(JSON.stringify(state));
    saving = saving.then(() => storage.save(copy)).catch(() => {});
    return saving;
  }

  function change(next, nextStatus = status) {
    state = { ...state, ...next };
    status = nextStatus;
    emit();
    return persist();
  }

  function load() {
    if (!loading) {
      loading = Promise.resolve()
        .then(() => storage.load())
        .catch(() => null)
        .then((saved) => {
          const restored = restore(saved, now());
          state = {
            pending: [...restored.pending, ...state.pending],
            failed: [...restored.failed, ...state.failed],
            synced: [...state.synced, ...restored.synced],
            seen: { ...state.seen, ...restored.seen },
          };
          emit();
        });
    }
    return loading;
  }

  const seenKey = (uid, eventId) => `${eventId}|${uidKey(uid)}`;

  // Remembers that this card was tapped for this event on this device and answers
  // with the time of the earlier tap, if there was one.
  function noteTap({ uid, eventId, tappedAt }) {
    const key = seenKey(uid, eventId);
    const earlier = state.seen[key] || null;
    if (!earlier) {
      state = { ...state, seen: { ...state.seen, [key]: tappedAt || new Date(now()).toISOString() } };
      persist();
    }
    return earlier;
  }

  function cancelTimer() {
    if (timer !== null) clearTimer(timer);
    timer = null;
  }

  function scheduleRetry() {
    cancelTimer();
    timer = setTimer(() => {
      timer = null;
      sync();
    }, retryDelayMs(attempt));
  }

  async function enqueue({ uid, eventId, eventName, tappedAt }) {
    await load();
    const at = tappedAt || new Date(now()).toISOString();
    const earlier = noteTap({ uid, eventId, tappedAt: at });
    const key = seenKey(uid, eventId);
    const alreadyQueued = state.pending.some((tap) => seenKey(tap.uid, tap.eventId) === key);
    if (!alreadyQueued) {
      counter += 1;
      const tap = { id: `${now()}-${counter}`, uid: cleanUid(uid), eventId, eventName: eventName || "", tappedAt: at };
      await change({ pending: [...state.pending, tap] });
    }
    if (!running && timer === null) scheduleRetry();
    return { queued: !alreadyQueued, earlierTapAt: earlier };
  }

  async function run() {
    await load();
    cancelTimer();
    const summary = { synced: 0, failed: 0, stopped: null };
    if (state.pending.length === 0) {
      if (status !== "idle") await change({}, "idle");
      return summary;
    }
    await change({}, "syncing");

    while (state.pending.length > 0) {
      if (!canSend()) {
        summary.stopped = "signed-out";
        break;
      }
      const tap = state.pending[0];
      const rest = () => state.pending.filter((other) => other.id !== tap.id);
      try {
        const response = await send({ uid: tap.uid, eventId: tap.eventId, tappedAt: tap.tappedAt });
        attempt = 0;
        summary.synced += 1;
        const done = {
          ...tap,
          result: response?.result || "UNKNOWN",
          name: response?.item ? fullName(response.item) : null,
          syncedAt: new Date(now()).toISOString(),
        };
        await change({ pending: rest(), synced: [done, ...state.synced].slice(0, SYNCED_KEPT) });
      } catch (error) {
        const kind = classifyError(error);
        if (kind === "permanent") {
          summary.failed += 1;
          const lost = {
            ...tap,
            reason: error?.message || "The server refused this tap.",
            code: error?.code || null,
            failedAt: new Date(now()).toISOString(),
          };
          await change({ pending: rest(), failed: [lost, ...state.failed] });
        } else {
          summary.stopped = kind;
          break;
        }
      }
    }

    if (summary.stopped === "retry") {
      attempt += 1;
      scheduleRetry();
    }
    const stoppedStatus = { retry: "waiting", "signed-out": "signed-out", forbidden: "forbidden" };
    await change({}, summary.stopped ? stoppedStatus[summary.stopped] : "idle");
    return summary;
  }

  // One replay at a time, oldest first. A call made while one is running joins it.
  function sync() {
    if (!running) {
      running = run().finally(() => {
        running = null;
      });
    }
    return running;
  }

  async function retryFailed() {
    await load();
    if (state.failed.length === 0) return;
    const back = state.failed.map(({ reason, code, failedAt, ...tap }) => tap).reverse();
    await change({ pending: [...state.pending, ...back], failed: [] });
    sync();
  }

  return {
    load,
    enqueue,
    noteTap,
    sync,
    retryFailed,
    clearFailed: () => change({ failed: [] }),
    clearSynced: () => change({ synced: [] }),
    getState: snapshot,
    whenSaved: () => saving,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    stop: cancelTimer,
  };
}
