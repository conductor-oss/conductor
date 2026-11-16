import { KeyValueTable, NavLink, Paper } from "components";
import { type KeyValueTableRow } from "components/KeyValueTable";
import { WorkflowExecution } from "types/Execution";
import { useFetch } from "utils/query";
import { continuationsOf, heldReason } from "./held";
import { useWaitingForWorker } from "./useWaitingForWorker";
import { appliedContinuations, codeTypeOf } from "./waiting";
import {
  WorkflowSizeIndicator,
  type WorkflowSizeResponse,
} from "./WorkflowSizeIndicator";

const style = {
  paper: {
    minHeight: 300,
  },
};

export default function ExecutionSummary({
  execution,
}: {
  execution: WorkflowExecution;
}) {
  const { data: sizeData } = useFetch<WorkflowSizeResponse>(
    `/workflow/${execution.workflowId}/size`,
    {
      when: !!execution.workflowId,
      // Don't retry 404s — endpoint only exists in Orkes; OSS returns 404 and the row is suppressed
      retry: (failureCount: number, error: unknown) =>
        (error as { status?: number })?.status !== 404 && failureCount < 3,
    },
  );

  const held = heldReason(execution);
  const continuations = continuationsOf(execution);
  const first = execution.nativeState?.bootstrap ? continuations[0] : undefined;
  const handlers = continuations.find((c) => c.lane === "handlers" && !c.task);
  const waitingForWorker = useWaitingForWorker(execution);

  // To accommodate unexecuted tasks, read type & name out of workflowTask
  const data: KeyValueTableRow[] = (
    [
      { label: "Workflow id", value: execution.workflowId },
      { label: "Status", value: execution.status, type: "status" },
      // a hold is explained on its own row
      execution.reasonForIncompletion &&
        !held && {
          label: "Reason for incompletion",
          value: execution.reasonForIncompletion,
          type: "error",
        },
      held && {
        label: "Waiting for compatible code",
        value: held,
        type: "error",
      },
      waitingForWorker && {
        label: "Waiting for a worker",
        value: waitingForWorker,
        type: "error",
      },
      execution.codePreview && {
        label: "Diagram",
        value:
          "A preview: the code has not run yet, so it shows what the code calls, read from the registered definition",
      },
      execution.codeAhead && {
        label: "Diagram",
        value:
          "What the code has yet to do is previewed after the step it is at, read from the registered definition",
      },
      { label: "Version", value: execution.workflowVersion },
      { label: "Start time", value: execution.startTime, type: "date" },
      { label: "End time", value: execution.endTime, type: "date" },
      {
        label: "Duration",
        value: Number(execution.endTime) - Number(execution.startTime),
        type: "duration",
      },
      sizeData && {
        label: "Workflow size",
        value: <WorkflowSizeIndicator sizeData={sizeData} />,
      },
      execution.parentWorkflowId && {
        label: "Parent workflow id",
        value: (
          <NavLink newTab path={`/execution/${execution.parentWorkflowId}`}>
            {execution.parentWorkflowId}
          </NavLink>
        ),
      },
      execution.parentWorkflowTaskId && {
        label: "Parent task id",
        value: execution.parentWorkflowTaskId,
      },
      execution.correlationId && {
        label: "Correlation id",
        value: execution.correlationId,
      },
      execution.idempotencyKey && {
        label: "Idempotency key",
        value: execution.idempotencyKey,
      },
      execution.event && {
        label: "Trigger event",
        value: execution.event,
      },
      // Native (code-first) workflows: the code type, and where its first continuation stands
      execution.nativeState && {
        label: "Native workflow",
        value: `${codeTypeOf(execution) ?? "code"} · ${appliedContinuations(execution)} continuation(s) applied`,
      },
      first &&
        first.state !== "applied" && {
          label: "First continuation",
          value: first.state,
        },
      handlers && {
        label: "Handler lane",
        value: `run #${handlers.n} ${handlers.state === "leased" ? "running" : "idle"}`,
      },
    ] as (KeyValueTableRow | false)[]
  ).filter(Boolean) as KeyValueTableRow[];

  return (
    <Paper sx={style.paper} elevation={0}>
      <KeyValueTable data={data} />
    </Paper>
  );
}
