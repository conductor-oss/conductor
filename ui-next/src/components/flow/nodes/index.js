import {
  workflowToNodeEdges as processWorkflow,
  PORT_NORTH,
  PORT_SOUTH,
  crumbsToTask,
  crumbsToTaskSteps,
  START_TASK_FAKE_TASK_REFERENCE_NAME,
  END_TASK_FAKE_TASK_REFERENCE_NAME,
} from "./mapper";

// This line should not be here, but it is:
export * from "../components/RichAddTaskMenu/taskGenerator";

export {
  processWorkflow,
  PORT_NORTH,
  PORT_SOUTH,
  START_TASK_FAKE_TASK_REFERENCE_NAME,
  END_TASK_FAKE_TASK_REFERENCE_NAME,
  crumbsToTask,
  crumbsToTaskSteps,
};
