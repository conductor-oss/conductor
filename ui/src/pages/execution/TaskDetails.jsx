import React, { useState } from "react";
import { Tabs, Tab, Paper } from "../../components";
import Timeline from "./Timeline";
import TaskList from "./TaskList";
import WorkflowGraph from "../../components/diagram/WorkflowGraph";

export default function TaskDetails({
  execution,
  dag,
  selectedTask,
  setSelectedTask,
}) {
  const [tabIndex, setTabIndex] = useState(0);

  return (
    <Paper style={{ width: "100%" }}>
      <Tabs value={tabIndex} contextual>
        <Tab label="Diagram" onClick={() => setTabIndex(0)} />
        <Tab label="Task List" onClick={() => setTabIndex(1)} />
        <Tab label="Timeline" onClick={() => setTabIndex(2)} />
      </Tabs>

      {tabIndex === 0 && (
        <WorkflowGraph
          selectedTask={selectedTask}
          executionMode={true}
          dag={dag}
          onClick={setSelectedTask}
        />
      )}
      {tabIndex === 1 && (
        <TaskList
          selectedTask={selectedTask}
          tasks={execution.tasks}
          dag={dag}
          onClick={setSelectedTask}
        />
      )}
      {tabIndex === 2 && (
        <Timeline
          selectedTask={selectedTask}
          tasks={execution.tasks}
          dag={dag}
          onClick={setSelectedTask}
        />
      )}
    </Paper>
  );
}
