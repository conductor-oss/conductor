import { describe, expect, it } from "vitest";

import { humanizeStatus } from "./utils";

describe("humanizeStatus", () => {
  it("title-cases multi-word snake-case statuses", () => {
    expect(humanizeStatus("IN_PROGRESS")).toBe("In Progress");
    expect(humanizeStatus("TIMED_OUT")).toBe("Timed Out");
    expect(humanizeStatus("FAILED_WITH_TERMINAL_ERROR")).toBe(
      "Failed With Terminal Error",
    );
  });

  it("leaves single-word statuses correctly capitalized", () => {
    expect(humanizeStatus("COMPLETED")).toBe("Completed");
    expect(humanizeStatus("FAILED")).toBe("Failed");
  });

  it("handles already-lowercased input", () => {
    expect(humanizeStatus("in_progress")).toBe("In Progress");
  });

  it("returns an empty string for empty or missing input", () => {
    expect(humanizeStatus("")).toBe("");
    expect(humanizeStatus(undefined)).toBe("");
  });
});
