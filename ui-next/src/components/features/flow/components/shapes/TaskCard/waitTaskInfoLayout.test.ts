import { WaitTaskDef } from "types";
import {
  WAIT_INFO_LINE_HEIGHT,
  waitTaskInfoExtraHeight,
  waitTaskInfoLineCount,
  waitTaskInfoText,
} from "./waitTaskInfoLayout";

const waitTask = (inputParameters: Record<string, string>) =>
  ({ type: "WAIT", inputParameters }) as unknown as WaitTaskDef;

// jsdom has no canvas text measurement, so these run against the
// fixed-width fallback. The browser path is the same algorithm.
describe("waitTaskInfoText", () => {
  it("shows until, then duration", () => {
    expect(waitTaskInfoText(waitTask({ until: "2026-11-05 14:30" }))).toBe(
      "Until: 2026-11-05 14:30",
    );
    expect(waitTaskInfoText(waitTask({ duration: "30 minutes" }))).toBe(
      "Duration: 30 minutes",
    );
    expect(waitTaskInfoText(waitTask({}))).toBeNull();
  });
});

describe("waitTaskInfoLineCount", () => {
  it("keeps short text on one line", () => {
    expect(waitTaskInfoLineCount("Until: 2026-11-05 14:30 GMT+05:30")).toBe(1);
  });

  it("wraps at spaces", () => {
    // Each word fits on a line, but no two fit together.
    expect(waitTaskInfoLineCount("aaaa bbbb cccc", 40)).toBe(3);
    expect(waitTaskInfoLineCount("aa bb cc", 50)).toBe(2);
  });

  it("breaks inside a word too long for one line", () => {
    // 11 chars per 85px line at the fallback width of ~7.15px per char.
    expect(waitTaskInfoLineCount("x".repeat(25), 85)).toBe(3);
  });

  it("wraps a long ${...} expression", () => {
    // Like the browser: break at the space first ("Until:" alone), then
    // split the expression, which is too long for a line by itself.
    expect(
      waitTaskInfoLineCount(
        "Until: ${workflow.input.waitUntilTimestampWithTimezone}",
      ),
    ).toBe(3);
  });
});

describe("waitTaskInfoExtraHeight", () => {
  it("adds nothing for a single line or no text", () => {
    expect(waitTaskInfoExtraHeight(waitTask({ until: "2026-11-05" }))).toBe(0);
    expect(waitTaskInfoExtraHeight(waitTask({}))).toBe(0);
  });

  it("adds one line height per extra line", () => {
    const task = waitTask({ until: "${" + "x".repeat(80) + "}" });
    const lines = waitTaskInfoLineCount(waitTaskInfoText(task)!);
    expect(lines).toBeGreaterThan(1);
    expect(waitTaskInfoExtraHeight(task)).toBe(
      (lines - 1) * WAIT_INFO_LINE_HEIGHT,
    );
  });
});
