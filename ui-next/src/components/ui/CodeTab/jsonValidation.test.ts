import { describe, expect, it } from "vitest";
import {
  createJsonValidator,
  parseJson,
  summarizeProblems,
} from "./jsonValidation";

describe("parseJson", () => {
  it("anchors a syntax error to a line", () => {
    const result = parseJson('{\n  "a": 1,\n  "b": \n}');
    expect(result.parsed).toBeNull();
    expect(result.problems).toHaveLength(1);
    expect(result.problems[0]).toMatchObject({ sev: "error" });
    expect(result.problems[0].line).toBeGreaterThanOrEqual(1);
    expect(result.problems[0].msg).toMatch(/^Invalid JSON — /);
  });
});

describe("createJsonValidator", () => {
  it("defaults to requiring a JSON object", () => {
    const validate = createJsonValidator(undefined, { documentName: "Thing" });
    expect(validate("{}")).toEqual({ parsed: {}, problems: [] });
    expect(validate("[1]")).toEqual({
      parsed: null,
      problems: [
        { sev: "error", line: 1, msg: "Thing must be a JSON object." },
      ],
    });
    expect(validate("null").parsed).toBeNull();
  });

  it("anchors reported problems to the line naming the key", () => {
    const validate = createJsonValidator<{ name?: string }>(
      (data, { report }) => {
        if (!data.name) report("error", "name", "name is required.");
      },
    );
    const result = validate('{\n  "other": 1,\n  "name": ""\n}');
    expect(result.problems).toEqual([
      { sev: "error", line: 3, msg: "name is required." },
    ]);
  });

  it("falls back to line 1 when the key is absent", () => {
    const validate = createJsonValidator((_, { report }) =>
      report("warn", "missing", "m"),
    );
    expect(validate("{}").problems[0].line).toBe(1);
  });
});

describe("summarizeProblems", () => {
  it("labels each status", () => {
    const e = { sev: "error" as const, line: 1, msg: "" };
    const w = { sev: "warn" as const, line: 1, msg: "" };
    const i = { sev: "info" as const, line: 1, msg: "" };
    expect(summarizeProblems({ parsed: null, problems: [e] }).kind).toBe(
      "invalid",
    );
    expect(summarizeProblems({ parsed: {}, problems: [e, e, w] })).toEqual({
      kind: "error",
      label: "2 errors, 1 warning",
    });
    expect(summarizeProblems({ parsed: {}, problems: [w, w, i] })).toEqual({
      kind: "warn",
      label: "2 warnings",
    });
    expect(summarizeProblems({ parsed: {}, problems: [i] })).toEqual({
      kind: "valid",
      label: "Valid",
    });
  });
});
