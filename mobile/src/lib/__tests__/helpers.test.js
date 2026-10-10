import { pickApiBase } from "../apiBase";
import { cleanTicketCode, MAX_CODE_LENGTH } from "../ticket";
import { cleanUid, createRepeatGuard, looksLikeUid, uidKey } from "../uid";

describe("cleanUid", () => {
  test("only trims, so the server sees what the phone read", () => {
    expect(cleanUid("  04:a1:b2:c3:d4:e5:f6 \n")).toBe("04:a1:b2:c3:d4:e5:f6");
    expect(cleanUid(undefined)).toBe("");
  });
});

describe("uidKey", () => {
  test("the three wire formats compare equal", () => {
    const forms = ["04:A1:B2:C3:D4:E5:F6", "04-a1-b2-c3-d4-e5-f6", "04a1b2c3d4e5f6", "04 A1 B2 C3 D4 E5 F6"];
    expect(new Set(forms.map(uidKey))).toEqual(new Set(["04A1B2C3D4E5F6"]));
  });
});

describe("looksLikeUid", () => {
  test.each(["04A1B2C3", "04:A1:B2:C3:D4:E5:F6", "04a1b2c3d4e5f6a7b8c9"])("accepts %s", (uid) => {
    expect(looksLikeUid(uid)).toBe(true);
  });

  test.each(["", "04A1", "04A1B2C3D4", "04A1B2C3D4E5F6A7", "not-a-badge-id", "04A1B2C3D4E5FG", null])(
    "rejects %s",
    (uid) => {
      expect(looksLikeUid(uid)).toBe(false);
    },
  );
});

describe("createRepeatGuard", () => {
  test("ignores the same card while it rests on the phone, then reads it again", () => {
    let clock = 0;
    const isRepeat = createRepeatGuard(2000, () => clock);

    expect(isRepeat("04A1B2C3D4E5F6")).toBe(false);
    clock = 500;
    expect(isRepeat("04:a1:b2:c3:d4:e5:f6")).toBe(true);
    clock = 2400;
    expect(isRepeat("04A1B2C3D4E5F6")).toBe(true);
    clock = 4500;
    expect(isRepeat("04A1B2C3D4E5F6")).toBe(false);
  });

  test("a different card is never a repeat", () => {
    let clock = 0;
    const isRepeat = createRepeatGuard(2000, () => clock);
    expect(isRepeat("04A1B2C3D4E5F6")).toBe(false);
    clock = 100;
    expect(isRepeat("04A1B2C3D4E5F7")).toBe(false);
    clock = 200;
    expect(isRepeat("04A1B2C3D4E5F6")).toBe(false);
  });
});

describe("cleanTicketCode", () => {
  test("keeps a bare token or a ticket URL as scanned, trimmed", () => {
    expect(cleanTicketCode(" abc123 ")).toBe("abc123");
    expect(cleanTicketCode("https://www.peachhacks.com/ticket?t=abc123")).toBe(
      "https://www.peachhacks.com/ticket?t=abc123",
    );
  });

  test("refuses nothing, non-text and codes longer than the API allows", () => {
    expect(cleanTicketCode("   ")).toBeNull();
    expect(cleanTicketCode(undefined)).toBeNull();
    expect(cleanTicketCode("x".repeat(MAX_CODE_LENGTH))).not.toBeNull();
    expect(cleanTicketCode("x".repeat(MAX_CODE_LENGTH + 1))).toBeNull();
  });
});

describe("pickApiBase", () => {
  test("a configured URL wins and loses its trailing slash", () => {
    expect(pickApiBase({ configured: " http://192.168.1.20:8080/ ", dev: true, os: "android" })).toBe(
      "http://192.168.1.20:8080",
    );
  });

  test("a release build defaults to production", () => {
    expect(pickApiBase({ configured: "", dev: false, os: "ios" })).toBe("https://api.peachhacks.com");
  });

  test("a development build defaults to the laptop as the emulator or simulator sees it", () => {
    expect(pickApiBase({ configured: undefined, dev: true, os: "android" })).toBe("http://10.0.2.2:8080");
    expect(pickApiBase({ configured: undefined, dev: true, os: "ios" })).toBe("http://localhost:8080");
  });
});
