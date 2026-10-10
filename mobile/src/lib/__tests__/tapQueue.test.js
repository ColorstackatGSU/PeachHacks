import { classifyError, createTapQueue, retryDelayMs, syncSummaryLines } from "../tapQueue";

const EVENT = "11111111-1111-1111-1111-111111111111";
const OTHER_EVENT = "22222222-2222-2222-2222-222222222222";

function memoryStorage(initial = null) {
  const box = { value: initial, saves: 0 };
  return {
    box,
    load: async () => box.value,
    save: async (value) => {
      box.value = value;
      box.saves += 1;
    },
  };
}

const apiError = (status, code, message = "Refused.") => Object.assign(new Error(message), { status, code });
const networkError = () => apiError(0, "NETWORK", "Could not reach the PeachHacks API.");

function harness({ storage = memoryStorage(), send, canSend } = {}) {
  let clock = Date.parse("2027-02-06T12:00:00Z");
  const timers = [];
  const queue = createTapQueue({
    storage,
    send,
    canSend,
    now: () => {
      clock += 1000;
      return clock;
    },
    setTimer: (fn, ms) => {
      const handle = { ms, cleared: false };
      handle.fire = () => {
        handle.cleared = true;
        fn();
      };
      timers.push(handle);
      return handle;
    },
    clearTimer: (handle) => {
      handle.cleared = true;
    },
  });
  const liveTimers = () => timers.filter((timer) => !timer.cleared);
  return { queue, storage, timers, liveTimers };
}

const tap = (uid, extra = {}) => ({ uid, eventId: EVENT, eventName: "Lunch", ...extra });

describe("enqueue", () => {
  test("saves the tap with its event and time, and persists it", async () => {
    const { queue, storage } = harness({ send: jest.fn() });
    const outcome = await queue.enqueue(tap("04:A1:B2:C3:D4:E5:F6", { tappedAt: "2027-02-06T12:00:00.000Z" }));

    expect(outcome).toEqual({ queued: true, earlierTapAt: null });
    expect(queue.getState().pending).toEqual([
      expect.objectContaining({
        uid: "04:A1:B2:C3:D4:E5:F6",
        eventId: EVENT,
        eventName: "Lunch",
        tappedAt: "2027-02-06T12:00:00.000Z",
      }),
    ]);
    await queue.whenSaved();
    expect(storage.box.value.pending).toHaveLength(1);
  });

  test("schedules a retry so the tap is sent without anyone asking", async () => {
    const { queue, liveTimers } = harness({ send: jest.fn() });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    expect(liveTimers()).toHaveLength(1);
    expect(liveTimers()[0].ms).toBe(retryDelayMs(0));
  });

  test("a queue saved by an earlier run of the app is still there", async () => {
    const first = harness({ send: jest.fn() });
    await first.queue.enqueue(tap("04A1B2C3D4E5F6"));
    await first.queue.whenSaved();

    const second = harness({ storage: memoryStorage(first.storage.box.value), send: jest.fn() });
    await second.queue.load();
    expect(second.queue.getState().pending.map((item) => item.uid)).toEqual(["04A1B2C3D4E5F6"]);
  });

  test("unreadable storage starts an empty queue instead of failing", async () => {
    const storage = { load: async () => { throw new Error("corrupt"); }, save: async () => {} };
    const { queue } = harness({ storage, send: jest.fn() });
    await queue.load();
    expect(queue.getState().pending).toEqual([]);
  });
});

describe("local duplicate warning", () => {
  test("the same card for the same event is reported and not queued twice", async () => {
    const { queue } = harness({ send: jest.fn() });
    await queue.enqueue(tap("04:a1:b2:c3:d4:e5:f6", { tappedAt: "2027-02-06T12:00:00.000Z" }));
    const again = await queue.enqueue(tap("04A1B2C3D4E5F6"));

    expect(again).toEqual({ queued: false, earlierTapAt: "2027-02-06T12:00:00.000Z" });
    expect(queue.getState().pending).toHaveLength(1);
  });

  test("the same card for a different event is not a duplicate", async () => {
    const { queue } = harness({ send: jest.fn() });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    const other = await queue.enqueue(tap("04A1B2C3D4E5F6", { eventId: OTHER_EVENT }));

    expect(other).toEqual({ queued: true, earlierTapAt: null });
    expect(queue.getState().pending).toHaveLength(2);
  });

  test("a tap checked in online earlier is remembered when the phone goes offline", async () => {
    const { queue } = harness({ send: jest.fn() });
    await queue.load();
    expect(queue.noteTap({ uid: "04A1B2C3D4E5F6", eventId: EVENT, tappedAt: "2027-02-06T11:00:00.000Z" })).toBeNull();

    const offline = await queue.enqueue(tap("04-A1-B2-C3-D4-E5-F6"));
    expect(offline).toEqual({ queued: true, earlierTapAt: "2027-02-06T11:00:00.000Z" });
  });

  test("the memory of earlier taps survives a restart", async () => {
    const first = harness({ send: jest.fn() });
    await first.queue.load();
    first.queue.noteTap({ uid: "04A1B2C3D4E5F6", eventId: EVENT, tappedAt: "2027-02-06T11:59:00.000Z" });
    await first.queue.whenSaved();

    const second = harness({ storage: memoryStorage(first.storage.box.value), send: jest.fn() });
    const outcome = await second.queue.enqueue(tap("04A1B2C3D4E5F6"));
    expect(outcome.earlierTapAt).toBe("2027-02-06T11:59:00.000Z");
  });
});

describe("sync", () => {
  test("replays oldest first, one at a time, with the time the card was read", async () => {
    const order = [];
    let inFlight = 0;
    let mostAtOnce = 0;
    const send = jest.fn(async (body) => {
      inFlight += 1;
      mostAtOnce = Math.max(mostAtOnce, inFlight);
      await Promise.resolve();
      order.push(body);
      inFlight -= 1;
      return { result: "CHECKED_IN", item: { firstName: "Ada", lastName: "Lovelace" } };
    });
    const { queue } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6", { tappedAt: "2027-02-06T12:00:01.000Z" }));
    await queue.enqueue(tap("04A1B2C3D4E5F7", { tappedAt: "2027-02-06T12:00:02.000Z" }));
    await queue.enqueue(tap("04A1B2C3D4E5F8", { tappedAt: "2027-02-06T12:00:03.000Z" }));

    const summary = await queue.sync();

    expect(order).toEqual([
      { uid: "04A1B2C3D4E5F6", eventId: EVENT, tappedAt: "2027-02-06T12:00:01.000Z" },
      { uid: "04A1B2C3D4E5F7", eventId: EVENT, tappedAt: "2027-02-06T12:00:02.000Z" },
      { uid: "04A1B2C3D4E5F8", eventId: EVENT, tappedAt: "2027-02-06T12:00:03.000Z" },
    ]);
    expect(mostAtOnce).toBe(1);
    expect(summary).toEqual({ synced: 3, failed: 0, stopped: null });
    expect(queue.getState().pending).toEqual([]);
    expect(queue.getState().status).toBe("idle");
  });

  test("two calls at once share one replay", async () => {
    const send = jest.fn(async () => ({ result: "CHECKED_IN", item: null }));
    const { queue } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await Promise.all([queue.sync(), queue.sync()]);
    expect(send).toHaveBeenCalledTimes(1);
  });

  test("200 records each result so staff can see what the queued taps turned out to be", async () => {
    const answers = [
      { result: "CHECKED_IN", item: { firstName: "Ada", lastName: "Lovelace" } },
      { result: "UNKNOWN_BADGE", item: null },
      { result: "UNKNOWN_BADGE", item: null },
      { result: "ALREADY_CHECKED_IN", item: { firstName: "Alan", lastName: "Turing" } },
    ];
    const send = jest.fn(async () => answers.shift());
    const { queue, storage } = harness({ send });
    for (const uid of ["04A1B2C3D4E5F1", "04A1B2C3D4E5F2", "04A1B2C3D4E5F3", "04A1B2C3D4E5F4"]) {
      await queue.enqueue(tap(uid));
    }
    await queue.sync();

    const { synced } = queue.getState();
    expect(synced.map((item) => [item.result, item.name]).reverse()).toEqual([
      ["CHECKED_IN", "Ada Lovelace"],
      ["UNKNOWN_BADGE", null],
      ["UNKNOWN_BADGE", null],
      ["ALREADY_CHECKED_IN", "Alan Turing"],
    ]);
    expect(syncSummaryLines(synced)).toEqual([
      "1 queued tap checked in",
      "1 queued tap was already checked in",
      "2 queued taps were unknown badges",
    ]);
    await queue.whenSaved();
    expect(storage.box.value.pending).toEqual([]);
    expect(storage.box.value.synced).toHaveLength(4);
  });

  test("a network error keeps every tap and retries later with a growing delay", async () => {
    const send = jest.fn(async () => {
      throw networkError();
    });
    const { queue, liveTimers } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.enqueue(tap("04A1B2C3D4E5F7"));

    const first = await queue.sync();
    expect(first).toEqual({ synced: 0, failed: 0, stopped: "retry" });
    expect(send).toHaveBeenCalledTimes(1);
    expect(queue.getState().pending).toHaveLength(2);
    expect(queue.getState().status).toBe("waiting");
    expect(liveTimers().map((timer) => timer.ms)).toEqual([retryDelayMs(1)]);

    await queue.sync();
    expect(liveTimers().map((timer) => timer.ms)).toEqual([retryDelayMs(2)]);
    expect(retryDelayMs(2)).toBeGreaterThan(retryDelayMs(1));
    expect(retryDelayMs(50)).toBe(60000);
  });

  test("the retry timer replays the queue once the connection is back", async () => {
    let online = false;
    const send = jest.fn(async () => {
      if (!online) throw networkError();
      return { result: "CHECKED_IN", item: null };
    });
    const { queue, liveTimers } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.sync();
    expect(queue.getState().pending).toHaveLength(1);

    online = true;
    liveTimers()[0].fire();
    await queue.sync();
    expect(queue.getState().pending).toEqual([]);
    expect(liveTimers()).toEqual([]);
  });

  test("a server error is retried later, not dropped", async () => {
    const send = jest.fn(async () => {
      throw apiError(503, "UNKNOWN");
    });
    const { queue } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    expect((await queue.sync()).stopped).toBe("retry");
    expect(queue.getState().pending).toHaveLength(1);
    expect(queue.getState().failed).toEqual([]);
  });

  test("401 stops, keeps the whole queue and sets no retry timer", async () => {
    const send = jest.fn(async () => {
      throw apiError(401, "UNAUTHORIZED");
    });
    const { queue, storage, liveTimers } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.enqueue(tap("04A1B2C3D4E5F7"));

    const summary = await queue.sync();
    expect(summary).toEqual({ synced: 0, failed: 0, stopped: "signed-out" });
    expect(send).toHaveBeenCalledTimes(1);
    expect(queue.getState().status).toBe("signed-out");
    expect(queue.getState().pending).toHaveLength(2);
    expect(liveTimers()).toEqual([]);
    await queue.whenSaved();
    expect(storage.box.value.pending).toHaveLength(2);
  });

  test("the queue survives a 401 and is sent after signing in again", async () => {
    let signedIn = false;
    const send = jest.fn(async () => {
      if (!signedIn) throw apiError(401, "UNAUTHORIZED");
      return { result: "CHECKED_IN", item: null };
    });
    const { queue } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.sync();

    signedIn = true;
    const summary = await queue.sync();
    expect(summary.synced).toBe(1);
    expect(queue.getState().pending).toEqual([]);
  });

  test("nothing is sent while signed out", async () => {
    const send = jest.fn();
    const { queue } = harness({ send, canSend: () => false });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    const summary = await queue.sync();
    expect(send).not.toHaveBeenCalled();
    expect(summary.stopped).toBe("signed-out");
    expect(queue.getState().pending).toHaveLength(1);
  });

  test("403 stops and keeps the queue", async () => {
    const send = jest.fn(async () => {
      throw apiError(403, "FORBIDDEN");
    });
    const { queue, liveTimers } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.enqueue(tap("04A1B2C3D4E5F7"));

    const summary = await queue.sync();
    expect(summary.stopped).toBe("forbidden");
    expect(send).toHaveBeenCalledTimes(1);
    expect(queue.getState().status).toBe("forbidden");
    expect(queue.getState().pending).toHaveLength(2);
    expect(liveTimers()).toEqual([]);
  });

  test("404 and 400 move the tap to the could-not-sync list with the reason and carry on", async () => {
    const send = jest.fn(async ({ uid }) => {
      if (uid === "04A1B2C3D4E5F1") throw apiError(404, "NOT_FOUND", "Event not found.");
      if (uid === "ZZ") throw apiError(400, "VALIDATION_ERROR", "Check the badge and try again.");
      return { result: "CHECKED_IN", item: null };
    });
    const { queue, storage, liveTimers } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F1"));
    await queue.enqueue(tap("ZZ"));
    await queue.enqueue(tap("04A1B2C3D4E5F3"));

    const summary = await queue.sync();
    expect(summary).toEqual({ synced: 1, failed: 2, stopped: null });
    expect(send).toHaveBeenCalledTimes(3);
    const { pending, failed, synced } = queue.getState();
    expect(pending).toEqual([]);
    expect(synced.map((item) => item.uid)).toEqual(["04A1B2C3D4E5F3"]);
    expect(failed.map((item) => [item.uid, item.code, item.reason])).toEqual([
      ["ZZ", "VALIDATION_ERROR", "Check the badge and try again."],
      ["04A1B2C3D4E5F1", "NOT_FOUND", "Event not found."],
    ]);
    expect(liveTimers()).toEqual([]);

    await queue.sync();
    expect(send).toHaveBeenCalledTimes(3);
    await queue.whenSaved();
    expect(storage.box.value.failed).toHaveLength(2);
  });

  test("could-not-sync taps can be put back and tried again", async () => {
    let eventExists = false;
    const send = jest.fn(async () => {
      if (!eventExists) throw apiError(404, "NOT_FOUND", "Event not found.");
      return { result: "CHECKED_IN", item: null };
    });
    const { queue } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.sync();
    expect(queue.getState().failed).toHaveLength(1);

    eventExists = true;
    await queue.retryFailed();
    await queue.sync();
    expect(queue.getState().failed).toEqual([]);
    expect(queue.getState().synced).toHaveLength(1);
  });

  test("a tap saved while a replay is running is sent in the same replay", async () => {
    let release;
    const gate = new Promise((resolve) => {
      release = resolve;
    });
    const send = jest.fn(async ({ uid }) => {
      if (uid === "04A1B2C3D4E5F1") await gate;
      return { result: "CHECKED_IN", item: null };
    });
    const { queue } = harness({ send });
    await queue.enqueue(tap("04A1B2C3D4E5F1"));
    const running = queue.sync();
    await queue.enqueue(tap("04A1B2C3D4E5F2"));
    release();
    await running;
    expect(send.mock.calls.map(([body]) => body.uid)).toEqual(["04A1B2C3D4E5F1", "04A1B2C3D4E5F2"]);
    expect(queue.getState().pending).toEqual([]);
  });

  test("listeners hear about every change", async () => {
    const send = jest.fn(async () => ({ result: "CHECKED_IN", item: null }));
    const { queue } = harness({ send });
    const seen = [];
    queue.subscribe((snapshot) => seen.push([snapshot.pending.length, snapshot.status]));
    await queue.enqueue(tap("04A1B2C3D4E5F6"));
    await queue.sync();
    expect(seen).toContainEqual([1, "syncing"]);
    expect(seen[seen.length - 1]).toEqual([0, "idle"]);
  });
});

describe("classifyError", () => {
  test.each([
    [networkError(), "retry"],
    [apiError(0, "UNKNOWN"), "retry"],
    [apiError(401, "UNAUTHORIZED"), "signed-out"],
    [apiError(403, "FORBIDDEN"), "forbidden"],
    [apiError(400, "VALIDATION_ERROR"), "permanent"],
    [apiError(404, "NOT_FOUND"), "permanent"],
    [apiError(429, "UNKNOWN"), "retry"],
    [apiError(500, "UNKNOWN"), "retry"],
  ])("%#", (error, kind) => {
    expect(classifyError(error)).toBe(kind);
  });
});
