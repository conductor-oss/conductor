import { DataTable, TaskLink } from "../../components";

export default function TaskList({ selectedTask, tasks, workflowId }) {
  const taskDetailFields = [
    { name: "seq", grow: 0.2 },
    {
      name: "taskId",
      renderer: (taskId) => (
        <TaskLink workflowId={workflowId} taskId={taskId} />
      ),
      grow: 2,
    },
    { name: "workflowTask.name", id: "taskName", label: "Task Name" },
    { name: "referenceTaskName", label: "Ref" },
    { name: "workflowTask.type", id: "taskType", label: "Type", grow: 0.5 },
    { name: "scheduledTime", type: "date-ms" },
    { name: "startTime", type: "date-ms" },
    { name: "endTime", type: "date-ms" },
    { name: "status", grow: 0.8 },
    { name: "updateTime", type: "date-ms" },
    { name: "callbackAfterSeconds" },
    { name: "pollCount", grow: 0.5 },
  ];

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
