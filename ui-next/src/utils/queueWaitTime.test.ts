import { describe, expect, it } from "vitest";

import { durationRenderer } from "./date";

// Covers the queue-wait-time formatting used in the task Summary panel:
// the value (startTime - scheduledTime) is clamped to >= 0 and rendered with a
// unit so sub-millisecond clock skew never surfaces as a raw negative integer.
describe("queue wait time formatting", () => {
  const format = (value: number) => durationRenderer(Math.max(0, value));

  it("clamps negative clock-skew values to 0ms", () => {
    expect(format(-6)).toBe("0ms");
  });

  it("renders zero with a unit", () => {
    expect(format(0)).toBe("0ms");
  });

  it("renders small positive waits with a ms unit", () => {
    expect(format(42)).toBe("42ms");
  });
});
