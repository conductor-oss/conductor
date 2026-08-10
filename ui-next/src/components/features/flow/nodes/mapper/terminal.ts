import { taskToNode } from "./common";
import {
  TaskType,
  CommonTaskDef,
  TaskStatus,
  WorkflowExecutionStatus,
} from "types";
import { NodeData, EdgeData } from "reaflow";
import { edgeMapper } from "./edgeMapper";
import _isEmpty from "lodash/isEmpty";

export const START_TASK_FAKE_TASK_REFERENCE_NAME = "start";
export const END_TASK_FAKE_TASK_REFERENCE_NAME = "end";

type NodesAndEdges = {
  nodes: NodeData[];
  edges: EdgeData[];
};

const wfExecutionStatusToTaskStatus = (
  wfExecutionStatus: WorkflowExecutionStatus,
) => {
  switch (wfExecutionStatus) {
    case WorkflowExecutionStatus.COMPLETED:
      return TaskStatus.COMPLETED;
    default:
      return TaskStatus.PENDING;
  }
};

const endPseudoTask = (
  executionStatus: WorkflowExecutionStatus,
): CommonTaskDef => ({
  name: "end",
  taskReferenceName: END_TASK_FAKE_TASK_REFERENCE_NAME,
  type: TaskType.TERMINAL,
  executionData: {
    status: wfExecutionStatusToTaskStatus(executionStatus),
    executed:
      wfExecutionStatusToTaskStatus(executionStatus) !== TaskStatus.PENDING,
    attempts: 0,
  },
});

export const terminalNode = (task: CommonTaskDef) => ({
  ...taskToNode(task, [], false),
  ports: undefined,
});

export const firstTask = {
  name: "start",
  taskReferenceName: START_TASK_FAKE_TASK_REFERENCE_NAME,
  type: TaskType.TERMINAL,
};

export const lastTask = {
  name: "end",
  taskReferenceName: END_TASK_FAKE_TASK_REFERENCE_NAME,
  type: TaskType.TERMINAL,
};

export const startNode = taskToNode(firstTask);

export const endNode = taskToNode(lastTask);

export const processLastTask = (
  {
    nodes = [],
    edges = [],
    previousTask,
    previousTaskAllowsConnection = true,
  }: NodesAndEdges & {
    previousTask?: CommonTaskDef;
    previousTaskAllowsConnection: boolean;
  },
  executionStatus: WorkflowExecutionStatus = WorkflowExecutionStatus.RUNNING,
) => {
  const pseudoEndTask = endPseudoTask(executionStatus);
  const endNode: NodeData = terminalNode(pseudoEndTask);
  const mappedEdges = edgeMapper(
    pseudoEndTask,
    previousTask,
    previousTaskAllowsConnection,
  );
  return {
    nodes: nodes.concat(_isEmpty(mappedEdges) ? [] : endNode), // If there is no way to connect the endNode. then we dont put it
    edges: edges.concat(mappedEdges),
  };
};
