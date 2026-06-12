import { southPort } from "./ports";
import _flow from "lodash/flow";
import _last from "lodash/last";
import _property from "lodash/property";
import { taskToSize } from "./layout";

import { Crumb, CommonTaskDef, TaskStatus } from "types";
import { NodeData } from "reaflow";
import { NodeTaskData } from "./types";

export const extractTaskReference: (t: CommonTaskDef) => string =
  _property("taskReferenceName");

export const extractLastTaskReferenceFn = _flow([_last, extractTaskReference]);

export const extractExecutionDataOrEmpty = (
  task?: CommonTaskDef & { executionData?: any },
) => (task?.executionData == null ? {} : task.executionData);

export const taskHasCompleted = (
  task?: CommonTaskDef,
  consideredCompletedStatus = [
    TaskStatus.COMPLETED,
    TaskStatus.COMPLETED_WITH_ERRORS,
  ],
) =>
  consideredCompletedStatus.includes(extractExecutionDataOrEmpty(task)?.status);

export const taskIsPending = (
  task?: CommonTaskDef,
  consideredPendingTaskStatus = [TaskStatus.PENDING],
) =>
  consideredPendingTaskStatus.includes(
    extractExecutionDataOrEmpty(task)?.status,
  );

export const completedTaskStatusData = (
  unreachableEdge = false,
  delayedEdge?: boolean,
) => ({
  status: TaskStatus.COMPLETED,
  unreachableEdge,
  delayedEdge,
});

export const maybeEdgeData = (
  currentTask: CommonTaskDef,
  previousTask?: CommonTaskDef,
  unreachableEdge = false,
  delayedEdge?: boolean,
) => {
  const previousStatusIsCompleted = taskHasCompleted(previousTask);

  const previousAndCurrentStatusCompleted =
    previousStatusIsCompleted && !taskIsPending(currentTask);

  return previousAndCurrentStatusCompleted
    ? {
        data: completedTaskStatusData(unreachableEdge, delayedEdge),
      }
    : {
        data: { unreachableEdge, delayedEdge },
      };
};

export const edgeIdMapper = (
  { taskReferenceName: sourceTaskReferenceName }: CommonTaskDef,
  { taskReferenceName: destinationTaskReferenceName }: CommonTaskDef,
) => `edge_${sourceTaskReferenceName}-${destinationTaskReferenceName}`;

export const taskToNode = <T extends CommonTaskDef>(
  task: T,
  crumbs: Crumb[] = [],
  additionalProps = {},
): NodeData<NodeTaskData> => {
  const { taskReferenceName, name } = task;
  const { width, height } = taskToSize(task);

  return {
    id: taskReferenceName,
    text: name,
    ...{ ports: [southPort({ id: taskReferenceName })] },
    data: {
      task,
      crumbs,
      ...additionalProps,
      ...extractExecutionDataOrEmpty(task),
    },
    width,
    height,
  };
};
