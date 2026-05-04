import { KeyValueTable, NavLink, Paper } from "components";
import { type KeyValueTableRow } from "components/KeyValueTable";
import { WorkflowExecution } from "types/Execution";

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
  // To accommodate unexecuted tasks, read type & name out of workflowTask
  const data: KeyValueTableRow[] = [
    { label: "Workflow id", value: execution.workflowId },
    { label: "Status", value: execution.status, type: "status" },
    { label: "Version", value: execution.workflowVersion },
    { label: "Start time", value: execution.startTime, type: "date" },
    { label: "End time", value: execution.endTime, type: "date" },
    {
      label: "Duration",
      value: Number(execution.endTime) - Number(execution.startTime),
      type: "duration",
    },
  ];

  if (execution.parentWorkflowId) {
    data.push({
      label: "Parent workflow id",
      value: (
        <NavLink newTab path={`/execution/${execution.parentWorkflowId}`}>
          {execution.parentWorkflowId}
        </NavLink>
      ),
    });
  }

  if (execution.parentWorkflowTaskId) {
    data.push({
      label: "Parent task id",
      value: execution.parentWorkflowTaskId,
    });
  }

  if (execution.reasonForIncompletion) {
    const statusIndex = data.findIndex((item) => item.label === "Status");
    data.splice(statusIndex + 1, 0, {
      label: "Reason for incompletion",
      value: execution.reasonForIncompletion,
      type: "error",
    });
  }

  if (execution.correlationId) {
    data.push({
      label: "Correlation id",
      value: execution.correlationId,
    });
  }
  if (execution.idempotencyKey) {
    data.push({
      label: "Idempotency key",
      value: execution.idempotencyKey,
    });
  }
  if (execution.event) {
    data.push({
      label: "Trigger event",
      value: execution.event,
    });
  }

  return (
    <Paper sx={style.paper} elevation={0}>
      <KeyValueTable data={data} />
    </Paper>
  );
}
