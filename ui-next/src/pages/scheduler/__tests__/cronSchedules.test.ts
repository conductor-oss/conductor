import { describe, expect, it } from "vitest";
import {
  DEFAULT_CRON_ZONE,
  getEarliestCronScheduleNextRun,
  getNormalizedCronSchedules,
  getScheduleCronSchedules,
  hasMultiCronSchedules,
  normalizeCronSchedule,
} from "../utils/cronSchedules";

// ---------------------------------------------------------------------------
// normalizeCronSchedule
// ---------------------------------------------------------------------------

describe("normalizeCronSchedule", () => {
  it("preserves a fully-populated entry", () => {
    expect(
      normalizeCronSchedule({
        cronExpression: "0 0 12 * * ?",
        zoneId: "Asia/Tokyo",
      }),
    ).toEqual({ cronExpression: "0 0 12 * * ?", zoneId: "Asia/Tokyo" });
  });

  it("defaults a missing zoneId to UTC", () => {
    expect(normalizeCronSchedule({ cronExpression: "0 0 12 * * ?" })).toEqual({
      cronExpression: "0 0 12 * * ?",
      zoneId: DEFAULT_CRON_ZONE,
    });
  });

  it("defaults a missing cronExpression to empty string", () => {
    expect(normalizeCronSchedule({ zoneId: "Asia/Tokyo" })).toEqual({
      cronExpression: "",
      zoneId: "Asia/Tokyo",
    });
  });

  it("normalizes null/undefined to empty defaults", () => {
    expect(normalizeCronSchedule(null)).toEqual({
      cronExpression: "",
      zoneId: DEFAULT_CRON_ZONE,
    });
    expect(normalizeCronSchedule(undefined)).toEqual({
      cronExpression: "",
      zoneId: DEFAULT_CRON_ZONE,
    });
  });

  it("treats empty-string zoneId as the default zone", () => {
    expect(
      normalizeCronSchedule({ cronExpression: "0 0 12 * * ?", zoneId: "" }),
    ).toEqual({ cronExpression: "0 0 12 * * ?", zoneId: DEFAULT_CRON_ZONE });
  });
});

// ---------------------------------------------------------------------------
// getNormalizedCronSchedules
// ---------------------------------------------------------------------------

describe("getNormalizedCronSchedules", () => {
  it("returns [] for null/undefined/empty", () => {
    expect(getNormalizedCronSchedules(null)).toEqual([]);
    expect(getNormalizedCronSchedules(undefined)).toEqual([]);
    expect(getNormalizedCronSchedules([])).toEqual([]);
  });

  it("normalizes each entry, filling default zones", () => {
    expect(
      getNormalizedCronSchedules([
        { cronExpression: "0 0 8 * * ?", zoneId: "Europe/London" },
        { cronExpression: "0 0 20 * * ?" },
      ]),
    ).toEqual([
      { cronExpression: "0 0 8 * * ?", zoneId: "Europe/London" },
      { cronExpression: "0 0 20 * * ?", zoneId: DEFAULT_CRON_ZONE },
    ]);
  });
});

// ---------------------------------------------------------------------------
// hasMultiCronSchedules
// ---------------------------------------------------------------------------

describe("hasMultiCronSchedules", () => {
  it("is false for null/undefined/empty", () => {
    expect(hasMultiCronSchedules(null)).toBe(false);
    expect(hasMultiCronSchedules(undefined)).toBe(false);
    expect(hasMultiCronSchedules([])).toBe(false);
  });

  it("is true when at least one entry exists", () => {
    expect(hasMultiCronSchedules([{ cronExpression: "0 0 12 * * ?" }])).toBe(
      true,
    );
  });
});

// ---------------------------------------------------------------------------
// getScheduleCronSchedules
// ---------------------------------------------------------------------------

describe("getScheduleCronSchedules", () => {
  it("returns [] when the schedule is null/undefined", () => {
    expect(getScheduleCronSchedules(null)).toEqual([]);
    expect(getScheduleCronSchedules(undefined)).toEqual([]);
  });

  it("prefers cronSchedules (multi) when present", () => {
    expect(
      getScheduleCronSchedules({
        cronExpression: "0 0 1 * * ?",
        zoneId: "Asia/Tokyo",
        cronSchedules: [
          { cronExpression: "0 0 8 * * ?", zoneId: "Europe/London" },
          { cronExpression: "0 0 20 * * ?" },
        ],
      }),
    ).toEqual([
      { cronExpression: "0 0 8 * * ?", zoneId: "Europe/London" },
      { cronExpression: "0 0 20 * * ?", zoneId: DEFAULT_CRON_ZONE },
    ]);
  });

  it("falls back to the single cronExpression when cronSchedules is empty", () => {
    expect(
      getScheduleCronSchedules({
        cronExpression: "0 0 1 * * ?",
        zoneId: "Asia/Tokyo",
        cronSchedules: [],
      }),
    ).toEqual([{ cronExpression: "0 0 1 * * ?", zoneId: "Asia/Tokyo" }]);
  });

  it("defaults zone to UTC for the single-cron fallback", () => {
    expect(getScheduleCronSchedules({ cronExpression: "0 0 1 * * ?" })).toEqual(
      [{ cronExpression: "0 0 1 * * ?", zoneId: DEFAULT_CRON_ZONE }],
    );
  });

  it("returns [] when neither cronSchedules nor cronExpression is set", () => {
    expect(getScheduleCronSchedules({ name: "no-cron" })).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// getEarliestCronScheduleNextRun
// ---------------------------------------------------------------------------

describe("getEarliestCronScheduleNextRun", () => {
  it("returns undefined for null/undefined/empty", () => {
    expect(getEarliestCronScheduleNextRun(null)).toBeUndefined();
    expect(getEarliestCronScheduleNextRun(undefined)).toBeUndefined();
    expect(getEarliestCronScheduleNextRun([])).toBeUndefined();
  });

  it("returns undefined when every expression is invalid", () => {
    expect(
      getEarliestCronScheduleNextRun([
        { cronExpression: "not-a-cron" },
        { cronExpression: "" },
      ]),
    ).toBeUndefined();
  });

  it("returns a finite future timestamp for a single valid expression", () => {
    const next = getEarliestCronScheduleNextRun([
      { cronExpression: "0 0 12 * * ?", zoneId: "UTC" },
    ]);
    expect(typeof next).toBe("number");
    expect(Number.isFinite(next)).toBe(true);
  });

  it("returns the earliest run across multiple schedules", () => {
    const a = { cronExpression: "0 0 1 * * ?", zoneId: "UTC" };
    const b = { cronExpression: "0 0 23 * * ?", zoneId: "UTC" };

    const earliestA = getEarliestCronScheduleNextRun([a]);
    const earliestB = getEarliestCronScheduleNextRun([b]);
    const combined = getEarliestCronScheduleNextRun([a, b]);

    expect(combined).toBe(Math.min(earliestA!, earliestB!));
  });

  it("ignores invalid entries when a valid one exists", () => {
    const valid = { cronExpression: "0 0 12 * * ?", zoneId: "UTC" };

    expect(
      getEarliestCronScheduleNextRun([{ cronExpression: "garbage" }, valid]),
    ).toBe(getEarliestCronScheduleNextRun([valid]));
  });
});
