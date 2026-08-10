import { KeyValueTable, NavLink, Paper } from "components";
import { type KeyValueTableRow } from "components/KeyValueTable";
import { WorkflowExecution } from "types/Execution";
import { useFetch } from "utils/query";
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

  // To accommodate unexecuted tasks, read type & name out of workflowTask
  const data: KeyValueTableRow[] = (
    [
      { label: "Workflow id", value: execution.workflowId },
      { label: "Status", value: execution.status, type: "status" },
      execution.reasonForIncompletion && {
        label: "Reason for incompletion",
        value: execution.reasonForIncompletion,
        type: "error",
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
    ] as (KeyValueTableRow | false)[]
  ).filter(Boolean) as KeyValueTableRow[];

  return (
    <Paper sx={style.paper} elevation={0}>
      <KeyValueTable data={data} />
    </Paper>
  );
}
