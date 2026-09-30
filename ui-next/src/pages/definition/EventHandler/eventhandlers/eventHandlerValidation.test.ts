import { describe, expect, it } from "vitest";
import { summarizeProblems } from "components/ui/CodeTab";
import { validateEventHandlerJson } from "./eventHandlerValidation";
import { NEW_EVENT_HANDLER_TEMPLATE } from "./eventHandlerSchema";

const toText = (obj: unknown) => JSON.stringify(obj, null, 2);

describe("validateEventHandlerJson", () => {
  it("flags invalid JSON as a single error", () => {
    const result = validateEventHandlerJson('{\n  "name": \n}');
    expect(result.parsed).toBeNull();
    expect(result.problems).toHaveLength(1);
    expect(result.problems[0].sev).toBe("error");
    expect(summarizeProblems(result)).toEqual({
      kind: "invalid",
      label: "Invalid JSON",
    });
  });

  it("requires a name on the new-handler template", () => {
    const result = validateEventHandlerJson(toText(NEW_EVENT_HANDLER_TEMPLATE));
    const nameProblem = result.problems.find((p) => p.msg.startsWith("name"));
    expect(nameProblem).toMatchObject({ sev: "error", line: 2 });
    expect(summarizeProblems(result)).toEqual({
      kind: "error",
      label: "1 error",
    });
  });

  it("warns on malformed event, evaluator and unknown actions", () => {
    const result = validateEventHandlerJson(
      toText({
        name: "h",
        description: "d",
        event: "kafka",
        evaluatorType: "lua",
        actions: [{ action: "explode" }],
      }),
    );
    expect(result.problems.map((p) => p.sev)).toEqual(["warn", "warn", "warn"]);
    expect(summarizeProblems(result).label).toBe("3 warnings");
  });

  it("reports valid handlers as Valid", () => {
    const result = validateEventHandlerJson(
      toText({ ...NEW_EVENT_HANDLER_TEMPLATE, name: "h", description: "d" }),
    );
    expect(result.problems).toEqual([]);
    expect(summarizeProblems(result)).toEqual({
      kind: "valid",
      label: "Valid",
    });
  });

  it("accepts a two-part event and every server evaluator", () => {
    for (const evaluatorType of [
      "javascript",
      "graaljs",
      "python",
      "value-param",
    ]) {
      const result = validateEventHandlerJson(
        toText({
          ...NEW_EVENT_HANDLER_TEMPLATE,
          name: "h",
          description: "d",
          event: "sqs:my_queue",
          evaluatorType,
        }),
      );
      expect(result.problems).toEqual([]);
    }
  });

  it("rejects non-object JSON", () => {
    expect(validateEventHandlerJson("[]").parsed).toBeNull();
  });
});
