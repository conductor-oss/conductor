import React from "react";
import { Link } from "@material-ui/core";
import { DataTable } from "../../components";
import _ from "lodash";

export default function TaskList({ selectedTask, tasks, dag, onClick }) {
  const taskDetailFields = [
    { name: "seq", grow: 0.2 },
    {
      name: "taskId",
      renderer: (taskId, row, idx) => {
        return (
          <Link href="#" onClick={() => handleClick(row)}>
            {taskId}
          </Link>
        );
      },
    },
    { name: "workflowTask.name", id: "taskName", label: "Task Name" },
    { name: "referenceTaskName", label: "Ref" },
    { name: "workflowTask.type", id: "taskType", label: "Type", grow: 0.5 },
    { name: "scheduledTime", type: "date" },
    { name: "startTime", type: "date" },
    { name: "endTime", type: "date" },
    { name: "status", grow: 0.5 },
    { name: "updateTime", type: "date" },
    { name: "callbackAfterSeconds" },
    { name: "pollCount" },
  ];

  let selectedTaskIdx = -1;
  if (selectedTask) {
    const { ref, taskId } = selectedTask;
    if (taskId) {
      selectedTaskIdx = tasks.findIndex((t) => t.taskId === taskId);
    } else {
      selectedTaskIdx = _.findLastIndex(
        tasks,
        (t) => t.referenceTaskName === ref
      );
    }
  }

  if (selectedTaskIdx === -1) selectedTaskIdx = null;

  function handleClick(row) {
    if (!_.isEmpty(row)) {
      if (onClick) {
        const task = row;
        const node = dag.graph.node(task.referenceTaskName);

        // If there are more than 1 task associated, use task ID
        if (node.taskResults.length > 1) {
          onClick({
            ref: task.referenceTaskName,
            taskId: task.taskId,
          });
        } else {
          onClick({
            ref: task.referenceTaskName,
          });
        }
      }
    } else {
      if (onClick) onClick(null);
    }
  }

  return (
    <DataTable
      style={{ minHeight: 400 }}
      data={tasks}
      columns={taskDetailFields}
      defaultShowColumns={[
        "seq",
        "taskId",
        "taskName",
        "referenceTaskName",
        "taskType",
        "startTime",
        "endTime",
        "status",
      ]}
      localStorageKey="taskListTable"
    />
  );
}
