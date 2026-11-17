import {
  convertToDateInTimeZone,
  DateAdapter,
  durationRenderer,
  formatDate,
  formatInTimeZone,
  formatRelativeDuration,
  getMomentStyleOffset,
  parseDateInTimeZone,
  printableUpdatedTime,
} from "utils/date";

// Every expectation in this file must hold no matter which timezone the
// machine running the tests is in. Verify with e.g.
//   TZ=UTC npx vitest run src/utils/__tests__/date.test.ts
//   TZ=America/Los_Angeles npx vitest run src/utils/__tests__/date.test.ts
//   TZ=Asia/Kolkata npx vitest run src/utils/__tests__/date.test.ts

// Instants on either side of US daylight saving time.
const WINTER = new Date("2026-01-15T12:00:00Z");
const SUMMER = new Date("2026-07-15T12:00:00Z");

describe("getMomentStyleOffset", () => {
  it.each([
    ["UTC", WINTER, "+00:00"],
    ["Asia/Kolkata", WINTER, "+05:30"],
    ["Asia/Kathmandu", WINTER, "+05:45"],
    ["Asia/Tokyo", WINTER, "+09:00"],
    ["America/Los_Angeles", WINTER, "-08:00"],
    ["America/Los_Angeles", SUMMER, "-07:00"],
    ["America/St_Johns", WINTER, "-03:30"],
    ["Europe/London", SUMMER, "+01:00"],
  ])("returns the offset of %s, not the machine's", (zone, date, expected) => {
    expect(getMomentStyleOffset(zone, date)).toBe(expected);
  });
});

describe("parseDateInTimeZone", () => {
  it.each([
    ["2026-01-15T09:00:00", "Asia/Kolkata", "2026-01-15T03:30:00.000Z"],
    ["2026-01-15T09:00:00", "America/New_York", "2026-01-15T14:00:00.000Z"],
    // Same wall time in summer is an hour earlier in UTC (EDT is UTC-4).
    ["2026-07-15T09:00:00", "America/New_York", "2026-07-15T13:00:00.000Z"],
    ["2026-01-15 09:00:00", "America/New_York", "2026-01-15T14:00:00.000Z"],
  ])("treats %s as wall time in %s", (input, zone, expected) => {
    expect(parseDateInTimeZone(input, zone).toISOString()).toBe(expected);
  });

  it("ignores the zone when the string already has Z", () => {
    expect(
      parseDateInTimeZone(
        "2026-01-15T09:00:00Z",
        "America/New_York",
      ).toISOString(),
    ).toBe("2026-01-15T09:00:00.000Z");
  });

  it("ignores the zone when the string already has an offset", () => {
    expect(
      parseDateInTimeZone(
        "2026-01-15T09:00:00+02:00",
        "America/New_York",
      ).toISOString(),
    ).toBe("2026-01-15T07:00:00.000Z");
  });

  it("resolves a wall time skipped by spring-forward DST", () => {
    // 02:30 doesn't exist in New York on 2026-03-08; it's read as EST.
    expect(
      parseDateInTimeZone(
        "2026-03-08T02:30:00",
        "America/New_York",
      ).toISOString(),
    ).toBe("2026-03-08T06:30:00.000Z");
  });
});

describe("formatInTimeZone", () => {
  it.each([
    [WINTER, "America/New_York", "2026-01-15 07:00:00 EST"],
    [SUMMER, "America/New_York", "2026-07-15 08:00:00 EDT"],
  ])("formats %s in %s with its abbreviation", (date, zone, expected) => {
    expect(formatInTimeZone(date, "yyyy-MM-dd HH:mm:ss zzz", zone)).toBe(
      expected,
    );
  });

  it("formats non-whole-hour offsets", () => {
    expect(
      formatInTimeZone(WINTER, "yyyy-MM-dd HH:mm xxx", "Asia/Kolkata"),
    ).toBe("2026-01-15 17:30 +05:30");
    expect(
      formatInTimeZone(WINTER, "yyyy-MM-dd HH:mm xxx", "Asia/Kathmandu"),
    ).toBe("2026-01-15 17:45 +05:45");
  });

  it("accepts epoch milliseconds and ISO strings", () => {
    expect(
      formatInTimeZone(WINTER.getTime(), "yyyy-MM-dd HH:mm", "Asia/Tokyo"),
    ).toBe("2026-01-15 21:00");
    expect(
      formatInTimeZone(
        "2026-01-15T12:00:00Z",
        "yyyy-MM-dd HH:mm",
        "Asia/Tokyo",
      ),
    ).toBe("2026-01-15 21:00");
  });

  it("uses the machine's zone when no zone is given", () => {
    const pad = (n: number) => String(n).padStart(2, "0");
    const expected =
      `${WINTER.getFullYear()}-${pad(WINTER.getMonth() + 1)}-` +
      `${pad(WINTER.getDate())} ${pad(WINTER.getHours())}:` +
      pad(WINTER.getMinutes());
    expect(formatInTimeZone(WINTER, "yyyy-MM-dd HH:mm")).toBe(expected);
  });

  // The scheduler preview parses each upcoming run in the schedule's zone,
  // then formats it back in that zone. It must show the original wall time.
  it.each([
    "UTC",
    "Asia/Kolkata",
    "Asia/Kathmandu",
    "America/New_York",
    "Australia/Sydney",
  ])("round-trips wall times through parseDateInTimeZone in %s", (zone) => {
    for (const wallTime of ["2026-01-15T09:00:00", "2026-07-15T09:00:00"]) {
      const parsed = parseDateInTimeZone(wallTime, zone);
      expect(formatInTimeZone(parsed, "yyyy-MM-dd'T'HH:mm:ss", zone)).toBe(
        wallTime,
      );
    }
  });
});

describe("convertToDateInTimeZone", () => {
  it("reads a zoneless string as wall time in the given zone", () => {
    expect(
      convertToDateInTimeZone(
        "2026-01-15T09:00:00",
        "Asia/Tokyo",
      ).toISOString(),
    ).toBe("2026-01-15T00:00:00.000Z");
  });
});

describe("DateAdapter", () => {
  // MUI's AdapterDateFnsV3 only works with date-fns 3. With the wrong
  // adapter/date-fns pairing these calls throw or return garbage.
  const adapter = new DateAdapter();

  it("formats and parses with date-fns 3", () => {
    const date = new Date(2026, 0, 15, 9, 30);
    expect(adapter.formatByString(date, "yyyy-MM-dd HH:mm")).toBe(
      "2026-01-15 09:30",
    );
    const parsed = adapter.parse("2026-01-15 09:30", "yyyy-MM-dd HH:mm");
    expect(parsed?.getTime()).toBe(date.getTime());
  });

  it("flags invalid input", () => {
    expect(adapter.isValid(adapter.parse("not a date", "yyyy-MM-dd"))).toBe(
      false,
    );
  });
});

describe("formatDate", () => {
  const local = new Date(2026, 0, 15, 9, 30, 0);

  it("accepts Dates, epoch milliseconds and epoch seconds", () => {
    const expected = "2026-01-15 09:30:00";
    const fmt = "yyyy-MM-dd HH:mm:ss";
    expect(formatDate(local, fmt)).toBe(expected);
    expect(formatDate(local.getTime(), fmt)).toBe(expected);
    expect(formatDate(local.getTime() / 1000, fmt)).toBe(expected);
  });

  it("returns an empty string for missing or invalid input", () => {
    expect(formatDate(null, "yyyy")).toBe("");
    expect(formatDate("not a date", "yyyy")).toBe("");
  });
});

describe("durationRenderer", () => {
  it.each([
    [1500, "1500ms"],
    [65_000, "1m 5s"],
    [3_725_000, "1h 2m 5s"],
    [90_061_000, "1d 1h 1m 1s"],
  ])("renders %ims as %s", (ms, expected) => {
    expect(durationRenderer(ms)).toBe(expected);
  });
});

describe("formatRelativeDuration", () => {
  it("keeps millisecond precision under a second", () => {
    expect(formatRelativeDuration(1000, 1250)).toBe("250 ms ago");
    expect(formatRelativeDuration(1250, 1000)).toBe("in 250 ms");
  });

  it("uses date-fns wording, rounded down, from a second up", () => {
    expect(formatRelativeDuration(0, 119_000)).toBe("1 minute ago");
    expect(formatRelativeDuration(119_000, 0)).toBe("in 1 minute");
  });
});

describe("printableUpdatedTime", () => {
  afterEach(() => {
    vi.useRealTimers(); // restore real timers after each test
  });

  it('should return "0" if updatedTimeInMillis is null', () => {
    const result = printableUpdatedTime(null as unknown as number);
    expect(result).toBe("0 minutes ago");
  });

  it('should return "0" if updatedTimeInMillis is 0', () => {
    const result = printableUpdatedTime(0);
    expect(result).toBe("0 minutes ago");
  });

  it("should handle time difference in minutes", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 1, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(new Date(2024, 9, 1, 11, 58).getTime()); // 2 minutes ago
    expect(result).toBe("2 minutes ago");
  });

  it("should handle time difference in days", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 10, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(
      new Date(2024, 9, 5, 12, 0, 0).getTime(),
    ); // 5 days ago
    expect(result).toBe("5 days ago");
  });

  it("should handle time difference in months", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 1, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(
      new Date(2024, 6, 1, 12, 0, 0).getTime(),
    ); // 3 months ago
    expect(result).toBe("3 months ago");
  });

  it("should handle time difference in years", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2024, 9, 1, 12, 0, 0)); // Set a fixed date
    const result = printableUpdatedTime(
      new Date(2022, 9, 1, 12, 0, 0).getTime(),
    ); // 2 years ago
    expect(result).toBe("about 2 years ago");
  });
});
