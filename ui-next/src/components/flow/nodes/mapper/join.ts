import {
  extractTaskReference,
  extractExecutionDataOrEmpty,
  taskHasCompleted,
  maybeEdgeData,
} from "./common";
import { NodeData, EdgeData } from "reaflow";
import { northPort, southPort, DiagramPort } from "./ports";
import _isEmpty from "lodash/isEmpty";
import _last from "lodash/last";
import {
  drillForEndTasks,
  switchTaskToFakeNodeId,
  switchFakeTaskIDSouthPortId,
} from "./switch";
import { taskToSize } from "./layout";
import { logger } from "utils/logger";
import { isSwitchTask, isForkableTask } from "./predicates";
import {
  JoinTaskDef,
  SwitchTaskDef,
  Crumb,
  CommonTaskDef,
  TaskType,
} from "types";

import { NodesAndEdges, NodeTaskData } from "./types";

type JoinOnDirectPathsEdgesDiagramPorts = {
  joinOn: EdgeData[];
  directPaths: EdgeData[];
  northPorts: DiagramPort[];
};

export const forkLastTasks = async (
  tasks: CommonTaskDef[],
  taskWalkerFn: any,
): Promise<CommonTaskDef[]> => {
  const lastTask = _last(tasks);

  if (lastTask != null && isSwitchTask(lastTask)) {
    const switchEndTaskDriller = drillForEndTasks(taskWalkerFn);
    const tasksToConnect = await switchEndTaskDriller(lastTask, []);

    return _isEmpty(tasksToConnect)
      ? [lastTask]
      : tasksToConnect.filter(
          ({ allowsTaskConnection }) => allowsTaskConnection,
        );
  }

  return lastTask == null ? [] : [lastTask];
};

export const forkLastTaskReferences = async (
  tasks: CommonTaskDef[],
  taskWalkerFn: any,
): Promise<string[]> => {
  const lastTasks = await forkLastTasks(tasks, taskWalkerFn);
  return lastTasks.map(extractTaskReference);
};

export const isTaskNotInJoinOn = (
  joinOn: string[] = [],
  currentTaskRef: string,
): boolean => {
  return !joinOn.includes(currentTaskRef);
};

export const joinEdgeForSwitch = (
  switchTask: SwitchTaskDef,
  index: number,
  joinTask: JoinTaskDef,
): JoinOnDirectPathsEdgesDiagramPorts => {
  if (isSwitchTask(switchTask)) {
    const fakeSwitchTaskId = switchTaskToFakeNodeId(switchTask);
    const joinTaskReferenceName = joinTask.taskReferenceName;
    const isDelayedEdge = isTaskNotInJoinOn(
      joinTask.joinOn,
      switchTask.taskReferenceName,
    );
    return {
      joinOn: [
        {
          id: `edge_jj_is_${fakeSwitchTaskId}-${joinTaskReferenceName}`,
          from: fakeSwitchTaskId,
          fromPort: switchFakeTaskIDSouthPortId(fakeSwitchTaskId),
          toPort: `${joinTaskReferenceName}-joinOnTask-${index}-north-port`,
          to: joinTaskReferenceName,
          //
          ...(taskHasCompleted(switchTask)
            ? {
                data: {
                  status: "COMPLETED",
                  delayedEdge: isDelayedEdge,
                },
              }
            : {
                data: {
                  delayedEdge: isDelayedEdge,
                },
              }),
        },
      ],
      northPorts: [
        northPort(
          { id: `${joinTaskReferenceName}-joinOnTask-${index}` },
          index,
          true,
        ),
      ],
      directPaths: [],
    };
  }

  logger.warn(
    "Expected switch task and got something else. Returning identity",
    switchTask,
  );

  return {
    joinOn: [],
    northPorts: [],
    directPaths: [],
  };
};

export const createJoinNode = (
  joinTask: JoinTaskDef,
  crumbs: Crumb[],
  previousTask?: CommonTaskDef,
) => {
  const { width, height } = taskToSize(joinTask);
  return {
    id: joinTask.taskReferenceName,
    text: joinTask.name,
    ports: [southPort({ id: joinTask.taskReferenceName })],
    data: {
      task: joinTask,
      crumbs,
      previousTask,
      // TODO fix when using sdk types
      ...extractExecutionDataOrEmpty(joinTask as CommonTaskDef),
    },
    width,
    height,
  } as const;
};

export const joinTasksToNodesEdges = (
  joinTask: JoinTaskDef,
  previousTask: CommonTaskDef,
  crumbs: Crumb[],
  currentNodes: NodeData<NodeTaskData>[],
): NodesAndEdges => {
  const joinNode = createJoinNode(joinTask, crumbs, previousTask);

  let result: JoinOnDirectPathsEdgesDiagramPorts = {
    joinOn: [],
    directPaths: [],
    northPorts: [],
  };

  if (isForkableTask(previousTask) && previousTask?.forkTasks != null) {
    const { forkTasks, taskReferenceName: forkTaskReferenceName } =
      previousTask;
    // Special case there is no inner-array in forkTasks
    if (_isEmpty(forkTasks) && previousTask.type === TaskType.FORK_JOIN) {
      result = {
        joinOn: result.joinOn,
        northPorts: [],
        directPaths: result.directPaths.concat({
          id: `edge_dp_${forkTaskReferenceName}_${joinTask.taskReferenceName}_0`,
          from: forkTaskReferenceName,
          to: joinTask.taskReferenceName,
          ...maybeEdgeData(joinTask, previousTask, true), // mark as unreachable
        }),
      };
    }

    for (const [idx, tasks] of forkTasks.entries()) {
      const invertedIndex = forkTasks.length - 1 - idx;
      if (_isEmpty(tasks)) {
        result = {
          joinOn: result.joinOn,
          northPorts: result.northPorts.concat(
            northPort(
              { id: `${joinNode.id}-direct-${invertedIndex}` },
              invertedIndex,
              true,
            ),
          ),
          directPaths: result.directPaths.concat({
            id: `edge_dp_${forkTaskReferenceName}_${joinTask.taskReferenceName}_${invertedIndex}`,
            from: forkTaskReferenceName,
            fromPort: `${forkTaskReferenceName}_[key=${idx}]-south-port`,
            toPort: `${joinTask.taskReferenceName}-direct-${invertedIndex}-north-port`,
            to: joinTask.taskReferenceName,
            ...maybeEdgeData(joinTask, previousTask),
          }),
        };
      } else {
        const maybeLastTask = _last(tasks);
        if (isSwitchTask(maybeLastTask)) {
          const innerSwitchEdges = joinEdgeForSwitch(
            maybeLastTask,
            invertedIndex,
            joinTask,
          );

          // If we only have a switch statement with no tasks in it. then connect default to join
          result = {
            joinOn: result.joinOn.concat(innerSwitchEdges.joinOn),
            northPorts: result.northPorts.concat(innerSwitchEdges.northPorts),
            directPaths: result.directPaths,
          };
        } else {
          const forkLastTasksF = _isEmpty(maybeLastTask)
            ? []
            : [maybeLastTask!];
          result = {
            joinOn: result.joinOn.concat(
              forkLastTasksF.map((lt: CommonTaskDef) => ({
                id: `edge_jj_${lt.taskReferenceName}-${joinTask.taskReferenceName}`,
                from: lt.taskReferenceName,
                fromPort: `${lt.taskReferenceName}-south-port`,
                toPort: `${joinTask.taskReferenceName}-joinOnTask-${invertedIndex}-north-port`,
                to: joinTask.taskReferenceName,
                ...maybeEdgeData(
                  joinTask,
                  lt,
                  false,
                  isTaskNotInJoinOn(joinTask.joinOn, lt.taskReferenceName),
                ),
              })),
            ),
            northPorts: result.northPorts.concat(
              northPort(
                { id: `${joinNode.id}-joinOnTask-${invertedIndex}` },
                invertedIndex,
                true,
              ),
            ),
            directPaths: result.directPaths,
          };
        }
      }
    }
  }

  const nodes = currentNodes.concat({
    ...joinNode,
    ports: joinNode.ports.concat(result.northPorts),
  });

  return {
    nodes,
    edges: result.directPaths.concat(result.joinOn),
  };
};
