import { ExecutionTask } from "types/Execution";

/**
 * Summary rows for a guardrail detector task.
 *
 * A detector runs as a task of the workflow it guards, so the generic task summary describes it
 * accurately and tells you nothing: "examples_guardrail_worker, Completed, 3ms" is true of every
 * detector ever run. What a reader wants is which guardrail this was, what it looked at, and what
 * it decided.
 *
 * All of it is on the task itself — the dispatch writes the request and the worker writes the
 * verdicts — so this needs nothing from the parent task or the evidence stamp.
 */

export type GuardrailRow = { label: string; value: string };

type Verdict = {
  passed?: boolean;
  reason?: string;
  detections?: {
    type?: string;
    severity?: string;
    start?: number;
    end?: number;
  }[];
  fixedValue?: string;
  scoreType?: string;
  score?: number;
  detectorVersion?: string;
};

/** True for a task dispatched by the guardrail engine, whatever its task name happens to be. */
export const isGuardrailDetectorTask = (task?: ExecutionTask): boolean =>
  Boolean(
    (task?.inputData as Record<string, unknown> | undefined)?.guardrailContext,
  );

const describeDetections = (verdicts: Verdict[]): string | undefined => {
  const detections = verdicts.flatMap((v) => v.detections ?? []);
  if (detections.length === 0) return undefined;
  return detections
    .map((d) => {
      const span =
        typeof d.start === "number" && typeof d.end === "number"
          ? ` at ${d.start}–${d.end}`
          : "";
      return `${d.type ?? "detection"}${d.severity ? ` (${d.severity})` : ""}${span}`;
    })
    .join(", ");
};

/**
 * Reads as a verdict rather than a boolean: "Flagged" is the outcome people talk about, and a
 * batch reports how much of it was flagged rather than collapsing to a single true/false.
 */
const describeVerdict = (verdicts: Verdict[]): string | undefined => {
  if (verdicts.length === 0) return undefined;
  const flagged = verdicts.filter((v) => v.passed === false).length;
  if (verdicts.length === 1) return flagged === 1 ? "Flagged" : "Passed";
  return flagged === 0
    ? `Passed (${verdicts.length} items)`
    : `Flagged ${flagged} of ${verdicts.length} items`;
};

export const guardrailSummaryRows = (task?: ExecutionTask): GuardrailRow[] => {
  if (!isGuardrailDetectorTask(task)) return [];

  const input = (task?.inputData ?? {}) as Record<string, any>;
  const output = (task?.outputData ?? {}) as Record<string, any>;
  const request = input.request ?? {};
  const identity = request.identity ?? {};
  const context = input.guardrailContext ?? {};
  const verdicts: Verdict[] = Array.isArray(output.verdicts)
    ? output.verdicts
    : [];

  const rows: GuardrailRow[] = [];
  const add = (label: string, value: unknown) => {
    if (value === undefined || value === null || value === "") return;
    rows.push({ label, value: String(value) });
  };

  add(
    "Guardrail",
    identity.guardrail
      ? `${identity.guardrail}${identity.version ? ` v${identity.version}` : ""}`
      : undefined,
  );
  add("Applied at", request.point);
  add("Detector type", identity.kind);
  add("Guards task", task?.parentTaskReferenceName);
  add("Verdict", describeVerdict(verdicts));
  add("Reason", verdicts.find((v) => v.reason)?.reason);
  add("Detections", describeDetections(verdicts));
  // Only worth a row when the detector actually rewrote something.
  if (verdicts.some((v) => v.fixedValue)) add("Text rewritten", "Yes");
  const scored = verdicts.find((v) => typeof v.score === "number");
  if (scored)
    add(
      "Score",
      `${scored.score}${scored.scoreType ? ` (${scored.scoreType})` : ""}`,
    );
  add(
    "Items inspected",
    Array.isArray(request.items) ? request.items.length : undefined,
  );
  add(
    "Detector version",
    output.detectorVersion ??
      verdicts.find((v) => v.detectorVersion)?.detectorVersion,
  );
  add("Attempt", context.attempt);
  add("Binding id", context.bindingId);
  add("Stamp id", context.stampId);

  return rows;
};
