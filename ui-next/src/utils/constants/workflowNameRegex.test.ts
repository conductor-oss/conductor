import { describe, expect, it } from "vitest";

import { WORKFLOW_NAME_REGEX } from "./regex";

describe("WORKFLOW_NAME_REGEX", () => {
  it("accepts ordinary workflow names", () => {
    expect(WORKFLOW_NAME_REGEX.test("bughunt_demo")).toBe(true);
    expect(WORKFLOW_NAME_REGEX.test("bug hunt demo")).toBe(true);
    expect(WORKFLOW_NAME_REGEX.test("wf<x>{y}#1")).toBe(true);
  });

  it("rejects slash and percent, which the UI route cannot round-trip", () => {
    expect(WORKFLOW_NAME_REGEX.test("bughunt/slash2")).toBe(false);
    expect(WORKFLOW_NAME_REGEX.test("bughunt%25")).toBe(false);
  });

  it("rejects leading and trailing spaces", () => {
    expect(WORKFLOW_NAME_REGEX.test(" leading")).toBe(false);
    expect(WORKFLOW_NAME_REGEX.test("trailing ")).toBe(false);
  });
});
