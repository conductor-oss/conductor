import { describe, expect, it } from "vitest";
import {
  guardrailSummaryRows,
  isGuardrailDetectorTask,
} from "./guardrailTaskSummary";

/** The shape a real detector task carries, taken from a live execution. */
const detectorTask = {
  referenceTaskName: "_guardrail_grb_a456de59b2c5ea4ec53eeb89_0",
  parentTaskReferenceName: "llm",
  inputData: {
    request: {
      identity: {
        guardrail: "examples.worker-detector",
        version: 1,
        kind: "WORKER",
      },
      point: "PROMPT",
      items: [{ id: "item-0", text: "This is forbidden knowledge." }],
    },
    guardrailContext: {
      stampId: "grs_e0c346c2",
      bindingId: "grb_a456de59b2c5ea4ec53eeb89",
      attempt: 0,
    },
  },
  outputData: {
    detectorVersion: "examples-worker-v1",
    verdicts: [
      {
        itemId: "item-0",
        passed: false,
        reason: "contains a banned word",
        detections: [
          { type: "FORBIDDEN", start: 8, end: 17, severity: "HIGH" },
        ],
        fixedValue: "This is [REDACTED:FORBIDDEN] knowledge.",
      },
    ],
  },
} as any;

const rowsOf = (task: any) =>
  Object.fromEntries(guardrailSummaryRows(task).map((r) => [r.label, r.value]));

describe("guardrail detector summary", () => {
  it("recognises a detector task by the context the dispatch writes", () => {
    expect(isGuardrailDetectorTask(detectorTask)).toBe(true);
    expect(isGuardrailDetectorTask({ inputData: {} } as any)).toBe(false);
    expect(isGuardrailDetectorTask(undefined)).toBe(false);
  });

  it("says which guardrail ran, where, and of what type", () => {
    const rows = rowsOf(detectorTask);

    expect(rows["Guardrail"]).toBe("examples.worker-detector v1");
    expect(rows["Applied at"]).toBe("PROMPT");
    expect(rows["Detector type"]).toBe("WORKER");
    expect(rows["Guards task"]).toBe("llm");
  });

  it("reports the verdict, why, and what was found", () => {
    const rows = rowsOf(detectorTask);

    expect(rows["Verdict"]).toBe("Flagged");
    expect(rows["Reason"]).toBe("contains a banned word");
    expect(rows["Detections"]).toBe("FORBIDDEN (HIGH) at 8–17");
    expect(rows["Text rewritten"]).toBe("Yes");
  });

  it("summarises a batch rather than collapsing it to one verdict", () => {
    const batch = {
      ...detectorTask,
      inputData: {
        ...detectorTask.inputData,
        request: { ...detectorTask.inputData.request, items: [{}, {}, {}] },
      },
      outputData: {
        verdicts: [{ passed: true }, { passed: false }, { passed: true }],
      },
    };

    expect(rowsOf(batch)["Verdict"]).toBe("Flagged 1 of 3 items");
    expect(rowsOf(batch)["Items inspected"]).toBe("3");
  });

  it("reads a clean pass as passed, and omits rows it has nothing for", () => {
    const clean = {
      ...detectorTask,
      outputData: { verdicts: [{ passed: true }] },
    };
    const rows = rowsOf(clean);

    expect(rows["Verdict"]).toBe("Passed");
    expect(rows["Reason"]).toBeUndefined();
    expect(rows["Detections"]).toBeUndefined();
    expect(rows["Text rewritten"]).toBeUndefined();
  });

  it("adds nothing at all to an ordinary task", () => {
    expect(guardrailSummaryRows({ inputData: { foo: 1 } } as any)).toEqual([]);
  });
});
