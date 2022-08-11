import React from "react";
import _ from "lodash";
import { NavLink, KeyValueTable } from "../../components";
import { useTime } from "../../hooks/useTime";

export default function TaskSummary({ taskResult }) {
  const now = useTime();

  // To accommodate unexecuted tasks, read type & name & ref out of workflow
  const data = [
    { label: "Task Type", value: taskResult.workflowTask.type },
    { label: "Status", value: taskResult.status || "Not executed" },
    { label: "Task Name", value: taskResult.workflowTask.name },
    {
      label: "Task Reference",
      value:
        taskResult.referenceTaskName ||
        taskResult.workflowTask.aliasForRef ||
        taskResult.workflowTask.taskReferenceName,
    },
  ];

  if (taskResult.domain) {
    data.push({ label: "Domain", value: taskResult.domain });
  }

  if (taskResult.taskId) {
    data.push({ label: "Task Execution ID", value: taskResult.taskId });
  }

  if (_.isFinite(taskResult.retryCount)) {
    data.push({ label: "Retry Count", value: taskResult.retryCount });
  }

  if (taskResult.scheduledTime) {
    data.push({
      label: "Scheduled Time",
      value: taskResult.scheduledTime > 0 && taskResult.scheduledTime,
      type: "date-ms",
    });
  }
  if (taskResult.startTime) {
    data.push({
      label: "Start Time",
      value: taskResult.startTime > 0 && taskResult.startTime,
      type: "date-ms",
    });
  }
  if (taskResult.endTime) {
    data.push({
      label: "End Time",
      value: taskResult.endTime,
      type: "date-ms",
    });
  }
  if (taskResult.startTime && taskResult.endTime) {
    data.push({
      label: "Duration",
      value:
        taskResult.startTime > 0 && taskResult.endTime - taskResult.startTime,
      type: "duration",
    });
  }
  if (taskResult.startTime && taskResult.status === "IN_PROGRESS") {
    data.push({
      label: "Current Elapsed Time",
      value: taskResult.startTime > 0 && now - taskResult.startTime,
      type: "duration",
    });
  }
  if (!_.isNil(taskResult.retrycount)) {
    data.push({ label: "Retry Count", value: taskResult.retryCount });
  }
  if (taskResult.reasonForIncompletion) {
    data.push({
      label: "Reason for Incompletion",
      value: taskResult.reasonForIncompletion,
    });
  }
  if (taskResult.workerId) {
    data.push({
      label: "Worker",
      value: taskResult.workerId,
      type: "workerId",
    });
  }
  if (taskResult.taskType === "DECISION") {
    data.push({
      label: "Evaluated Case",
      value:
        _.has(taskResult, "outputData.caseOutput[0]") &&
        taskResult.outputData.caseOutput[0],
    });
  }
  if (taskResult.workflowTask.type === "SUB_WORKFLOW") {
    data.push({
      label: "Subworkflow Definition",
      value: (
        <NavLink
          newTab
          path={`/workflowDef/${taskResult.workflowTask.subWorkflowParam.name}`}
        >
          {taskResult.workflowTask.subWorkflowParam.name}{" "}
        </NavLink>
      ),
    });
    if (_.has(taskResult, "subWorkflowId")) {
      data.push({
        label: "Subworkflow ID",
        value: (
          <NavLink newTab path={`/execution/${taskResult.subWorkflowId}`}>
            {taskResult.subWorkflowId}
          </NavLink>
        ),
      });
    }
  }

  if (taskResult.externalInputPayloadStoragePath) {
    data.push({
      label: "Externalized Input",
      value: taskResult.externalInputPayloadStoragePath,
    });
  }

  if (taskResult.externalOutputPayloadStoragePath) {
    data.push({
      label: "Externalized Output",
      value: taskResult.externalOutputPayloadStoragePath,
    });
  }

  return <KeyValueTable data={data} />;
}
