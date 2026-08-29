import _isFinite from "lodash/isFinite";
import _get from "lodash/get";
import { durationRenderer } from "utils/date";
import { NavLink, KeyValueTable } from "components";
import { Link, Paper } from "@mui/material";
import { ExecutionTask, TaskType } from "types";
import { ReactNode, useMemo } from "react";

interface TaskSummaryProps {
  taskResult: ExecutionTask;
}

type DataType = {
  label: string;
  value: ReactNode | string | number | null;
  type?: string;
};

type PendingTool = { tool_name: string; tool_call_id?: string };

/** Tolerant of shape: task output is provider data, not something the UI controls. */
const asPendingTools = (value: unknown): PendingTool[] => {
  if (!Array.isArray(value)) return [];
  return value.flatMap((entry) => {
    if (entry == null || typeof entry !== "object") return [];
    const name = (entry as Record<string, unknown>).tool_name;
    if (typeof name !== "string" || name.length === 0) return [];
    const callId = (entry as Record<string, unknown>).tool_call_id;
    return [
      {
        tool_name: name,
        tool_call_id: typeof callId === "string" ? callId : undefined,
      },
    ];
  });
};

export default function TaskSummary({ taskResult }: TaskSummaryProps) {
  const shouldDisplayEvaluatedCase = useMemo(
    () => ["DECISION", "SWITCH"].includes(taskResult.taskType),
    [taskResult.taskType],
  );

  // To accommodate unexecuted tasks, read type & name & ref out of workflowTask
  const data = [
    { label: "Task type", value: taskResult.workflowTask.type },
    {
      label: "Status",
      value: taskResult.status || "Not executed",
      type: "status",
    },
    { label: "Task name", value: taskResult.workflowTask.name },
    {
      label: "Task reference",
      value: taskResult.workflowTask.taskReferenceName,
    },
  ] as DataType[];

  if (taskResult.domain) {
    data.push({ label: "Domain", value: taskResult.domain });
  }

  if (taskResult.taskId) {
    data.push({ label: "Task execution id", value: taskResult.taskId });
  }

  if (taskResult.correlationId) {
    data.push({ label: "Correlation id", value: taskResult.correlationId });
  }

  if (_isFinite(taskResult.retryCount)) {
    data.push({ label: "Retry count", value: taskResult.retryCount });
  }

  if (taskResult.scheduledTime) {
    data.push({
      label: "Scheduled time",
      value: taskResult.scheduledTime > 0 && taskResult.scheduledTime,
      type: "date",
    });
  }
  if (taskResult.startTime) {
    data.push({
      label: "Start time",
      value: taskResult.startTime > 0 && taskResult.startTime,
      type: "date",
    });
  }
  if (taskResult.endTime) {
    data.push({ label: "End time", value: taskResult.endTime, type: "date" });
  }
  if (taskResult.startTime && taskResult.endTime) {
    data.push({
      label: "Duration",
      value:
        taskResult.startTime > 0 && taskResult.endTime - taskResult.startTime,
      type: "duration",
    });
  }
  if (taskResult.reasonForIncompletion) {
    const statusIndex = data.findIndex((item) => item.label === "Status");
    data.splice(statusIndex + 1, 0, {
      label: "Reason for incompletion",
      value: taskResult.reasonForIncompletion,
      type: "error",
    });
  }
  if (taskResult.workerId) {
    data.push({
      label: "Worker",
      value: taskResult.workerId,
      type: "workerId",
    });
  }
  if (taskResult.pollCount) {
    data.push({
      label: "Poll count",
      value: taskResult.pollCount,
    });
  }
  if (taskResult.seq) {
    data.push({
      label: "Sequence",
      value: taskResult.seq,
    });
  }
  if (_isFinite(taskResult.queueWaitTime)) {
    // queueWaitTime is startTime - scheduledTime; sub-millisecond clock skew can
    // make it slightly negative. Clamp to 0 and render with a unit so it reads
    // consistently with the Duration row (e.g. "0ms") instead of a raw signed int.
    data.push({
      label: "Queue wait time",
      value: durationRenderer(Math.max(0, taskResult.queueWaitTime as number)),
    });
  }
  if (shouldDisplayEvaluatedCase) {
    const caseOutput = taskResult.outputData?.caseOutput;
    data.push({
      label: "Evaluated case",
      value: caseOutput ? caseOutput[0] : null,
    });
  }
  if (taskResult?.inputData?.integrationName) {
    data.push({
      label: "Integration name",
      value: (
        <NavLink
          path={`/integrations/${encodeURIComponent(
            taskResult.inputData?.integrationName ?? "",
          )}/configuration`}
        >
          {taskResult.inputData?.integrationName}
        </NavLink>
      ),
    });
  }
  if (taskResult.workflowTask.type === TaskType.SUB_WORKFLOW) {
    data.push({
      label: "Subworkflow definition",
      value: (
        <NavLink
          path={`/workflowDef/${encodeURIComponent(
            taskResult.inputData?.subWorkflowName ?? "",
          )}`}
        >
          {taskResult.inputData?.subWorkflowName}
        </NavLink>
      ),
    });
    if (_get(taskResult, "outputData.subWorkflowId")) {
      data.push({
        label: "Subworkflow id",
        value: (
          <Link
            href={`${window.location.origin}/execution/${taskResult.outputData?.subWorkflowId}`}
            target="_blank"
            rel="noreferrer"
          >
            {taskResult.outputData?.subWorkflowId}
          </Link>
        ),
      });
    }
  }

  if (
    taskResult.workflowTask.type === TaskType.AGENT &&
    taskResult.inputData?.agentType === "conductor" &&
    taskResult.outputData?.executionId
  ) {
    data.push({
      label: "Agent execution id",
      value: (
        <Link
          href={`${window.location.origin}/execution/${taskResult.outputData?.executionId}`}
          target="_blank"
          rel="noreferrer"
        >
          {taskResult.outputData?.executionId}
        </Link>
      ),
    });
  }

  // A hosted agent (microsoft-foundry, bedrock, openai-assistants) reports the tools it is waiting on.
  // Listing them here shows what the agent asked for without leaving the page; the run they execute
  // in is linked separately below.
  if (taskResult.workflowTask.type === TaskType.AGENT) {
    const pendingTools = asPendingTools(taskResult.outputData?.pendingTools);
    if (pendingTools.length > 0) {
      data.push({
        label: pendingTools.length === 1 ? "Tool requested" : "Tools requested",
        value: (
          <span>
            {pendingTools
              .map((tool) =>
                tool.tool_call_id
                  ? `${tool.tool_name} (${tool.tool_call_id})`
                  : tool.tool_name,
              )
              .join(", ")}
          </span>
        ),
      });
    }
    if (taskResult.outputData?.toolDispatchId) {
      data.push({
        label: "Tool run",
        value: (
          <Link
            href={`${window.location.origin}/execution/${taskResult.outputData.toolDispatchId}`}
            target="_blank"
            rel="noreferrer"
          >
            {String(taskResult.outputData.toolDispatchId)}
          </Link>
        ),
      });
    }
  }

  if (
    taskResult.workflowTask.type === TaskType.START_WORKFLOW &&
    taskResult.outputData?.workflowId
  ) {
    data.push({
      label: "Start workflow",
      value: (
        <Link
          href={`${window.location.origin}/execution/${taskResult.outputData?.workflowId}`}
          target="_blank"
          rel="noreferrer"
        >
          {`${window.location.origin}/execution/${taskResult.outputData?.workflowId}`}
        </Link>
      ),
    });
  }
  if (
    taskResult.workflowTask.type === TaskType.DYNAMIC &&
    taskResult.inputData?.taskToExecute === "SUB_WORKFLOW"
  ) {
    const { subWorkflowName } = taskResult.inputData ?? {};
    const { subWorkflowId } = taskResult.outputData ?? {};

    if (subWorkflowName) {
      data.push({
        label: "Subworkflow definition",
        value: (
          <NavLink path={`/workflowDef/${subWorkflowName}`}>
            {subWorkflowName}
          </NavLink>
        ),
      });
    }
    if (subWorkflowId) {
      data.push({
        label: "Subworkflow id",
        value: (
          <Link
            href={`${window.location.origin}/execution/${subWorkflowId}`}
            target="_blank"
            rel="noreferrer"
          >
            {subWorkflowId}
          </Link>
        ),
      });
    }
  }

  return (
    <Paper
      variant="outlined"
      sx={{
        margin: 3,
      }}
    >
      <KeyValueTable data={data} />
    </Paper>
  );
}
