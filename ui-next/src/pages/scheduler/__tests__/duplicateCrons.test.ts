import { describe, expect, it } from "vitest";
import {
  duplicateCronMessage,
  findDuplicateCrons,
} from "../utils/duplicateCrons";

const cron = (cronExpression: string, zoneId = "UTC") => ({
  cronExpression,
  zoneId,
});

describe("findDuplicateCrons", () => {
  it("reports nothing when every expression differs", () => {
    expect(
      findDuplicateCrons([cron("0 0 9 * * ?"), cron("0 0 18 * * ?")]),
    ).toEqual([undefined, undefined]);
  });

  it("points a repeat back at the expression it repeats", () => {
    // The server queues one job per entry, so this would run the workflow twice at 09:00.
    expect(
      findDuplicateCrons([
        cron("0 0 9 * * ?"),
        cron("0 0 18 * * ?"),
        cron("0 0 9 * * ?"),
      ]),
    ).toEqual([undefined, undefined, 1]);
  });

  it("treats the same expression in different timezones as distinct", () => {
    expect(
      findDuplicateCrons([
        cron("0 0 9 * * ?", "UTC"),
        cron("0 0 9 * * ?", "Asia/Kolkata"),
      ]),
    ).toEqual([undefined, undefined]);
  });

  it("ignores surrounding whitespace", () => {
    expect(
      findDuplicateCrons([cron("0 0 9 * * ?"), cron("  0 0 9 * * ?  ")]),
    ).toEqual([undefined, 1]);
  });

  it("skips rows that are still empty", () => {
    expect(
      findDuplicateCrons([cron(""), cron(""), cron("0 0 9 * * ?")]),
    ).toEqual([undefined, undefined, undefined]);
  });

  it("points every later repeat at the first one", () => {
    expect(
      findDuplicateCrons([
        cron("0 0 9 * * ?"),
        cron("0 0 9 * * ?"),
        cron("0 0 9 * * ?"),
      ]),
    ).toEqual([undefined, 1, 1]);
  });
});

describe("duplicateCronMessage", () => {
  it("says nothing when the expressions all differ", () => {
    expect(
      duplicateCronMessage([cron("0 0 9 * * ?"), cron("0 0 18 * * ?")]),
    ).toBeNull();
  });

  it("names both positions so the author knows which to remove", () => {
    expect(
      duplicateCronMessage([cron("0 0 9 * * ?"), cron("0 0 9 * * ?")]),
    ).toBe(
      "Cron expression 2 is the same as cron expression 1. " +
        "Remove one — the workflow would run twice.",
    );
  });
});
