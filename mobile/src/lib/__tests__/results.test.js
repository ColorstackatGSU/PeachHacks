import {
  featuredEvents,
  firstTapLine,
  homeEvents,
  initials,
  lanyardTitle,
  lookupView,
  NOT_SAVED,
  sortEvents,
  tapView,
} from "../results";

const NOW = Date.parse("2027-02-06T17:00:00Z");
const minutes = (n) => new Date(NOW + n * 60 * 1000).toISOString();
const event = (id, startsAt, extra = {}) => ({ id, name: id, startsAt, general: false, checkedIn: 0, ...extra });

describe("featuredEvents", () => {
  test("an event is current from 30 minutes before it starts until 2 hours after", () => {
    const events = [event("early", minutes(31)), event("soon", minutes(30)), event("late", minutes(-120)), event("over", minutes(-121))];
    expect(featuredEvents(events, NOW)).toEqual({ kind: "now", events: [events[2], events[1]] });
  });

  test("at most two are featured: the ones starting closest to now, in start order", () => {
    const events = [event("a", minutes(-100)), event("b", minutes(-10)), event("c", minutes(20)), event("d", minutes(-60))];
    const { kind, events: shown } = featuredEvents(events, NOW);
    expect(kind).toBe("now");
    expect(shown.map((e) => e.id)).toEqual(["b", "c"]);
  });

  test("the general event and events without a time are never featured", () => {
    const events = [event("general", minutes(0), { general: true }), event("untimed", null), event("bad", "not a date")];
    expect(featuredEvents(events, NOW)).toEqual({ kind: null, events: [] });
  });

  test("with nothing current, the soonest upcoming event is up next", () => {
    const events = [event("later", minutes(240)), event("past", minutes(-300)), event("next", minutes(45))];
    expect(featuredEvents(events, NOW)).toEqual({ kind: "next", events: [events[2]] });
  });

  test("with nothing current or upcoming, nothing is featured", () => {
    expect(featuredEvents([event("past", minutes(-300))], NOW)).toEqual({ kind: null, events: [] });
    expect(featuredEvents(null, NOW)).toEqual({ kind: null, events: [] });
  });
});

describe("homeEvents", () => {
  test("the general event is left out, and featured events are not listed twice", () => {
    const events = [
      event("general", null, { general: true }),
      event("lunch", minutes(-10)),
      event("snack", null),
      event("workshop", minutes(200)),
    ];
    const { featured, others } = homeEvents(events, NOW);
    expect(featured.events.map((e) => e.id)).toEqual(["lunch"]);
    expect(others.map((e) => e.id)).toEqual(["workshop", "snack"]);
  });

  test("with no events at all", () => {
    expect(homeEvents(null, NOW)).toEqual({ featured: { kind: null, events: [] }, others: [] });
  });
});

describe("sortEvents", () => {
  test("by start time, then events without a time by name", () => {
    const events = [event("zeta", null), event("lunch", minutes(60)), event("alpha", null), event("breakfast", minutes(-60))];
    expect(sortEvents(events).map((e) => e.id)).toEqual(["breakfast", "lunch", "alpha", "zeta"]);
  });
});

describe("tapView", () => {
  const lunch = { id: "e", name: "Lunch", general: false };
  const item = (extra = {}) => ({ firstName: "Ada", lastName: "Lovelace", school: "Georgia State University", ...extra });

  test("checked in is a success with the full name that counts and clears itself", () => {
    const view = tapView({ result: "CHECKED_IN", item: item({ generalCheckedIn: true }) }, lunch);
    expect(view).toMatchObject({ family: "ok", word: "Checked in", title: "Ada Lovelace", staff: false, counts: true, clears: true });
    expect(view.detail).toBeNull();
  });

  test("a check-in at an event before Arrivals says so", () => {
    expect(tapView({ result: "CHECKED_IN", item: item({ generalCheckedIn: false }) }, lunch).detail).toMatch(/Arrivals/);
  });

  test("already in stays up and gives the time of the first tap, never who tapped", () => {
    const view = tapView(
      { result: "ALREADY_CHECKED_IN", item: item({ checkedInAt: "2027-02-06T16:30:00Z", checkedInBy: "Sam" }) },
      lunch,
    );
    expect(view).toMatchObject({ family: "repeat", word: "Already in", title: "Ada Lovelace" });
    expect(view.clears).toBeFalsy();
    expect(view.detail).toMatch(/^First tap at \S+/);
    expect(JSON.stringify(view)).not.toMatch(/Sam/);
  });

  test("the staff flag is carried, and a missing one is false", () => {
    expect(tapView({ result: "CHECKED_IN", item: item({ staff: true }) }, lunch).staff).toBe(true);
    expect(tapView({ result: "NOT_ACCEPTED", item: item() }, lunch).staff).toBe(false);
  });

  test("a sponsor badge is OK, has no name, does not count and clears itself", () => {
    const view = tapView({ result: "SPONSOR", item: null }, lunch);
    expect(view).toMatchObject({ family: "sponsor", title: "OK", titleIsName: false, clears: true });
    expect(view.counts).toBeFalsy();
  });

  test.each([
    ["NOT_ACCEPTED", "Not accepted", undefined],
    ["REVOKED_BADGE", "Badge turned off", "Send them to Arrivals"],
    ["UNKNOWN_BADGE", "Unknown badge", "Send them to Arrivals"],
    [undefined, "Unknown badge", "Send them to Arrivals"],
  ])("%s stays up as a refusal", (result, word, detail) => {
    const view = tapView({ result, item: result === "NOT_ACCEPTED" ? item() : null }, lunch);
    expect(view).toMatchObject({ family: "stop", word });
    expect(view.detail).toBe(detail);
    expect(view.clears).toBeFalsy();
  });

  test("not saved asks for the same badge again", () => {
    expect(NOT_SAVED).toMatchObject({ family: "void", word: "Not saved", detail: "Tap the badge again" });
  });
});

describe("lookupView", () => {
  const holder = (extra) => ({ firstName: "Ada", lastName: "Lovelace", school: "Emory University", ...extra });

  test.each([
    [{ accepted: true, checkedIn: true }, "ok", "Checked in"],
    [{ accepted: true, checkedIn: false }, "repeat", "Not checked in yet"],
    [{ accepted: false, checkedIn: false }, "stop", "Not accepted"],
  ])("a found holder %o is %s with one status line", (flags, family, word) => {
    expect(lookupView({ result: "FOUND", holder: holder(flags) })).toMatchObject({
      family,
      word,
      title: "Ada Lovelace",
      detail: "Emory University",
    });
  });

  test("the staff flag shows on a found holder", () => {
    expect(lookupView({ result: "FOUND", holder: holder({ accepted: true, checkedIn: true, staff: true }) }).staff).toBe(true);
  });

  test("sponsor, turned off and unknown badges", () => {
    expect(lookupView({ result: "SPONSOR", holder: null })).toMatchObject({ family: "sponsor", word: "Sponsor badge" });
    expect(lookupView({ result: "REVOKED_BADGE", holder: null })).toMatchObject({ family: "stop", word: "Badge turned off" });
    expect(lookupView({ result: "UNKNOWN_BADGE", holder: null })).toMatchObject({ family: "stop", word: "Unknown badge" });
  });
});

describe("lanyardTitle", () => {
  test("a configured colour names the lanyard", () => {
    expect(lanyardTitle({ group: "OTHER", label: "Hacker from another school", color: "Blue" })).toBe("Blue lanyard");
  });

  test("without a colour, the lanyard is named for who it is for", () => {
    expect(lanyardTitle({ group: "HOST", label: "Georgia State University hacker", color: null })).toBe(
      "Georgia State University lanyard",
    );
    expect(lanyardTitle({ group: "OTHER", label: "Hacker from another school", color: null })).toBe("Other schools lanyard");
    expect(lanyardTitle({ group: "STAFF", label: "Staff", color: "  " })).toBe("Staff lanyard");
    expect(lanyardTitle({ group: "SPONSOR", label: "Sponsor", color: null })).toBe("Sponsor lanyard");
    expect(lanyardTitle(null)).toBeNull();
  });
});

describe("small helpers", () => {
  test("firstTapLine needs a time", () => {
    expect(firstTapLine({ checkedInAt: null })).toBeNull();
    expect(firstTapLine({ checkedInAt: "2027-02-06T16:30:00Z" })).toMatch(/^First tap at \S/);
  });

  test("initials", () => {
    expect(initials("Local Admin")).toBe("LA");
    expect(initials("Ada Byron Lovelace")).toBe("AL");
    expect(initials("cher")).toBe("CH");
    expect(initials("")).toBe("?");
  });
});
