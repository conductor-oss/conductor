import { switchTaskToFakeNodeId, switchFakeTaskIDSouthPortId } from "./switch";
import { maybeEdgeData } from "./common";
import _isEmpty from "lodash/isEmpty";
import {
  TaskType,
  SwitchTaskDef,
  CommonTaskDef,
  ForkJoinDynamicDef,
} from "types";
import { isSwitchType } from "./predicates";

/**
 * validates if previous task is connectable. returns true if it is
 * @param previousTask
 * @param previousTaskTerminatedNoEdges
 * @returns
 */
const canConnectToPreviousTask = (previousTask?: CommonTaskDef) =>
  previousTask != null;

export const edgeMapper = (
  currentTask: CommonTaskDef,
  previousTask?: CommonTaskDef,
  previousTaskAllowsConnection = true,
) => {
  let sourceId = previousTask?.taskReferenceName;
  let previousTaskSouthPortId = `${sourceId}-south-port`;

  const isForkJoinTaskPair =
    currentTask.type === TaskType.JOIN &&
    previousTask?.type === TaskType.FORK_JOIN;

  if (isForkJoinTaskPair) return [];

  if (
    canConnectToPreviousTask(previousTask) &&
    previousTask?.type === TaskType.FORK_JOIN_DYNAMIC &&
    currentTask?.type === TaskType.JOIN &&
    !_isEmpty((previousTask as ForkJoinDynamicDef)?.forkTasks)
  ) {
    return [];
  }

  const target = currentTask.taskReferenceName;

  if (
    canConnectToPreviousTask(previousTask) &&
    isSwitchType(previousTask?.type)
  ) {
    const previousSwitchTask = previousTask as SwitchTaskDef;
    sourceId = switchTaskToFakeNodeId(previousSwitchTask);
    previousTaskSouthPortId = switchFakeTaskIDSouthPortId(sourceId);
  }

  if (sourceId == null) return [];

  return [
    {
      id: `edge_${sourceId}-${currentTask.taskReferenceName}`,
      from: sourceId,
      fromPort: previousTaskSouthPortId,
      toPort: `${target}-to`,
      to: target,
      ...maybeEdgeData(
        currentTask,
        previousTask,
        !previousTaskAllowsConnection,
      ),
    },
  ];
};
