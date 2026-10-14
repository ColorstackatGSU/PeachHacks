import {
  ARRIVED,
  arrivedLine,
  attendanceScopes,
  emptyText,
  mergePage,
  personEvents,
  rowTime,
  scopeTotal,
} from "../attendance";

const NOW = Date.parse("2027-02-06T17:00:00Z");
const minutes = (n) => new Date(NOW + n * 60 * 1000).toISOString();
const event = (id, startsAt, extra = {}) => ({ id, name: id, startsAt, general: false, ...extra });

describe("attendanceScopes", () => {
  test("Arrived first, then events happening now, then the rest in start order; no general event", () => {
    const events = [
      event("general", null, { general: true }),
      event("dinner", minutes(300)),
      event("lunch", minutes(-10)),
      event("breakfast", minutes(-400)),
      event("snack", null),
    ];
    const scopes = attendanceScopes(events, NOW);
    expect(scopes.map((s) => s.key)).toEqual([ARRIVED, "lunch", "breakfast", "dinner", "snack"]);
    expect(scopes[1].now).toBe(true);
    expect(scopes[2].now).toBe(false);
  });

  test("with no events there is only Arrived", () => {
    expect(attendanceScopes(null, NOW)).toEqual([{ key: ARRIVED, label: "Arrived" }]);
  });
});

describe("words", () => {
  const lunch = { key: "lunch", label: "Saturday lunch" };
  const arrived = { key: ARRIVED, label: "Arrived" };

  test("scopeTotal", () => {
    expect(scopeTotal(arrived, 214)).toBe("214 arrived");
    expect(scopeTotal(lunch, 88)).toBe("88 at Saturday lunch");
    expect(scopeTotal(lunch, undefined)).toBe("");
  });

  test("emptyText", () => {
    expect(emptyText(arrived, "")).toBe("No one has arrived yet.");
    expect(emptyText(lunch, " ")).toBe("No one is checked in to Saturday lunch yet.");
    expect(emptyText(lunch, " Ada ")).toBe("No one matches “Ada”.");
  });
});

describe("times", () => {
  test("a row shows when they arrived, or when they were checked in to the event", () => {
    expect(rowTime({ arrivedAt: "2027-02-06T14:12:00Z" })).not.toBe("");
    expect(rowTime({ checkedInAt: "2027-02-06T14:12:00Z" })).not.toBe("");
    expect(rowTime({})).toBe("");
  });

  test("arrivedLine", () => {
    expect(arrivedLine({ arrivedAt: "2027-02-06T14:12:00Z" })).toMatch(/^Arrived \S/);
    expect(arrivedLine({})).toBeNull();
  });

  test("personEvents lists checked-in events earliest first", () => {
    const person = {
      events: [
        { eventId: "b", name: "Dinner", checkedInAt: "2027-02-06T23:00:00Z" },
        { eventId: "a", name: "Lunch", checkedInAt: "2027-02-06T17:00:00Z" },
        { eventId: "c", name: "Never", checkedInAt: null },
      ],
    };
    expect(personEvents(person).map((e) => e.name)).toEqual(["Lunch", "Dinner"]);
    expect(personEvents({})).toEqual([]);
  });
});

describe("mergePage", () => {
  test("adds a page without repeating anyone", () => {
    expect(mergePage([{ id: 1 }, { id: 2 }], [{ id: 2 }, { id: 3 }]).map((x) => x.id)).toEqual([1, 2, 3]);
    expect(mergePage(null, [{ id: 1 }])).toEqual([{ id: 1 }]);
  });
});
