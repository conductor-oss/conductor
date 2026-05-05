import {
  crumbsToTask,
  removeTaskReferenceFromCrumbs,
  START_TASK_FAKE_TASK_REFERENCE_NAME,
} from "components/features/flow/nodes/mapper";
import _prop from "lodash/fp/prop";
import _head from "lodash/head";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import _nth from "lodash/nth";
import _omit from "lodash/omit";
import _reverse from "lodash/reverse";
import _tail from "lodash/tail";
import { NodeData, PortData } from "reaflow";
import { Crumb, TaskDef, TaskType, WorkflowDef } from "types";
import { adjust, insert, logger, remove } from "utils";
import {
  ADD_NEW_SWITCH_PATH,
  ADD_TASK,
  ADD_TASK_ABOVE,
  ADD_TASK_BELOW,
  ADD_TASK_IN_DO_WHILE,
  DELETE_TASK,
  REMOVE_BRANCH,
  REPLACE_TASK,
} from "./constants";

export const findTaskModificationPath = (
  crumbs: Crumb[],
  taskReferenceName: string,
) => {
  if (_isEmpty(crumbs)) return [];
  const taskRefMap: Record<string, Crumb> = crumbs.reduce(
    (acc, cur) => ({ ...acc, [cur.ref]: cur }),
    {},
  );
  let currentTask: Crumb | undefined = taskRefMap[taskReferenceName];
  const taskPath = [currentTask];

  while (!_isNil(currentTask?.parent)) {
    currentTask = taskRefMap[currentTask.parent];
    taskPath.push(currentTask);
  }
  return _reverse(taskPath);
};

const applyDeleteOperation = (taskArray: TaskDef[], idx: number) => {
  const task = taskArray[idx];
  const taskType = task.type;
  switch (taskType) {
    case TaskType.FORK_JOIN_DYNAMIC:
    case TaskType.FORK_JOIN: {
      return remove(idx, 2, taskArray); // Removes the FORK_JOIN and the JOIN task
    }
    case TaskType.JOIN: {
      const previousIndex = idx - 1;
      if (previousIndex >= 0) {
        // If the previous task is a FORK_JOIN remove it as well
        const previousTask = _nth(taskArray, previousIndex);
        if (
          previousTask?.type === TaskType.FORK_JOIN ||
          previousTask?.type === TaskType.FORK_JOIN_DYNAMIC
        )
          return remove(previousIndex, 2, taskArray);
      }
      return remove(idx, 1, taskArray); //If not just remove the task
    }
    default: {
      return remove(idx, 1, taskArray);
    }
  }
};

const applyAddDecisionCase = (
  taskArray: TaskDef[],
  idx: number,
  { branchName }: { branchName?: string | number },
) => {
  const task = taskArray[idx];
  const { type, decisionCases } = task;
  if (type === TaskType.SWITCH || type === TaskType.DECISION) {
    return adjust(
      idx,
      () => ({
        ...task,
        decisionCases: { ...decisionCases, [branchName!]: [] },
      }),
      taskArray,
    );
  } else if (type === TaskType.FORK_JOIN) {
    const result = adjust(
      idx,
      () => ({
        ...task,
        forkTasks: (task?.forkTasks ?? []).concat([[]]),
      }),
      taskArray,
    );
    return result;
  }

  logger.warn("Got wrong task as reference type is ", type);

  return taskArray;
};

// TODO Add unit test for this
const applyAddTaskInDoWhile = (
  taskArray: TaskDef[],
  idx: number,
  taskToAdd: TaskDef,
) => {
  const task = taskArray[idx];
  const { type, loopOver = [] } = task;
  if (type === TaskType.DO_WHILE) {
    return adjust(
      idx,
      () => ({
        ...task,
        loopOver: loopOver.concat(taskToAdd),
      }),
      taskArray,
    );
  }
  logger.warn("Got wrong task as reference type expected DO_WHILE ", type);

  return taskArray;
};

export const applyAddTask = (
  taskArray: TaskDef[],
  idx: number,
  payload: Record<string, unknown> | TaskDef[],
  crumbProps: { onDecisionBranch?: string; forkIdx?: number },
) => {
  const task = taskArray[idx];

  if (task.type === TaskType.SWITCH || task.type === TaskType.DECISION) {
    const { onDecisionBranch } = crumbProps;
    return adjust<TaskDef>(
      idx,
      () =>
        ({
          ...task!,
          ...(onDecisionBranch === "defaultCase"
            ? {
                defaultCase: Array.isArray(payload)
                  ? [...payload, ...(task?.defaultCase || [])]
                  : [payload, ...(task?.defaultCase || [])],
              }
            : {
                decisionCases: {
                  ...task.decisionCases,
                  [onDecisionBranch!]: Array.isArray(payload)
                    ? [
                        ...payload,
                        ...(_prop(onDecisionBranch!, task?.decisionCases) ||
                          []),
                      ]
                    : [
                        payload,
                        ...(_prop(onDecisionBranch!, task?.decisionCases) ||
                          []),
                      ],
                },
              }),
        }) as TaskDef,
      taskArray,
    );
  } else if (task.type === TaskType.FORK_JOIN) {
    const { forkIdx } = crumbProps;
    const tasksToAppend = Array.isArray(payload) ? [...payload] : [payload];

    const forkTasks = adjust(
      forkIdx!,
      () => [...tasksToAppend, ...(_nth(task?.forkTasks, forkIdx) || [])],
      task?.forkTasks || [],
    );

    return adjust<TaskDef>(
      idx,
      () => ({
        ...task,
        forkTasks,
      }),
      taskArray,
    );
  }

  logger.warn("Got wrong task as reference type is ", task.type);
  return [];
};

const applyRemoveBranch = (
  taskArray: TaskDef[],
  idx: number,
  { branchName }: { branchName?: string | number },
) => {
  const task = taskArray[idx];
  const { type, decisionCases } = task;
  if (type === TaskType.SWITCH || type === TaskType.DECISION) {
    const taskModification =
      branchName === "defaultCase"
        ? { defaultCase: [] }
        : {
            decisionCases: _omit(decisionCases, branchName!),
          };

    return adjust<TaskDef>(
      idx,
      () => ({
        ...task,
        ...taskModification,
      }),
      taskArray,
    );
  } else if (type === TaskType.FORK_JOIN) {
    const tasksWithoutForkBranch = adjust<TaskDef>(
      idx,
      () => ({
        ...task,
        forkTasks: remove(
          branchName! as number,
          1,
          task.forkTasks as TaskDef[][],
        ),
      }),
      taskArray,
    );

    const nextTaskIndex = idx + 1;
    const maybeNextTask = _nth(tasksWithoutForkBranch, nextTaskIndex) as
      | TaskDef
      | undefined;

    // Remove join on items in JOIN task
    if (maybeNextTask != null && maybeNextTask?.type === TaskType.JOIN) {
      const maybeLastTaskInBranch = _last(
        _nth(task.forkTasks, branchName as number),
      );

      const originalJoinOn = maybeNextTask?.joinOn || [];
      const joinOn =
        maybeLastTaskInBranch == null
          ? originalJoinOn
          : originalJoinOn.filter(
              (joinTask) =>
                joinTask !== maybeLastTaskInBranch?.taskReferenceName,
            );

      return adjust(
        nextTaskIndex,
        () => ({
          ...maybeNextTask,
          joinOn,
        }),
        tasksWithoutForkBranch,
      );
    }

    return tasksWithoutForkBranch;
  }

  logger.warn("Got wrong task as reference type is ", type);

  return taskArray;
};

const applySingleOperationOnTaskArray = (
  taskArray: TaskDef[],
  operation: {
    type: string;
    parameters: {
      idx: number;
      payload: { branchName?: string | number; crumb?: Crumb };
    };
  },
): TaskDef[] => {
  const {
    type,
    parameters: { idx, payload, ...otherCrumbProps },
  } = operation;

  switch (type) {
    case ADD_TASK_ABOVE: {
      return insert(idx, payload as TaskDef, taskArray);
    }
    case ADD_TASK_BELOW: {
      const taskIdxInc = idx + 1;
      return insert(taskIdxInc, payload as TaskDef, taskArray);
    }
    case DELETE_TASK: {
      return applyDeleteOperation(taskArray, idx);
    }
    case ADD_NEW_SWITCH_PATH: {
      return applyAddDecisionCase(taskArray, idx, payload);
    }
    case ADD_TASK: {
      return applyAddTask(taskArray, idx, payload, otherCrumbProps);
    }
    case REPLACE_TASK: {
      return adjust(idx, () => payload as TaskDef, taskArray);
    }
    case ADD_TASK_IN_DO_WHILE: {
      return applyAddTaskInDoWhile(taskArray, idx, payload as TaskDef);
    }
    case REMOVE_BRANCH: {
      return applyRemoveBranch(taskArray, idx, payload);
    }
    default: {
      return taskArray;
    }
  }
};

type OperationType = {
  payload: any;
  type: string;
};

export const applyOperationArrayOnTasks = (
  fwCrumb: Crumb[],
  tasks: TaskDef[],
  operation: OperationType = { payload: {}, type: "" },
): TaskDef[] => {
  if (_isEmpty(fwCrumb) || _isNil(_head(fwCrumb))) {
    return applySingleOperationOnTaskArray(tasks, {
      ...operation,
      parameters: {
        idx: 0,
        payload: operation.payload || {},
      },
    });
  }
  const { refIdx, ...otherCrumbProps } = _head(fwCrumb) as Crumb;
  const restCrumbs = _tail(fwCrumb);

  const isLastCrumb = restCrumbs.length === 0;

  if (isLastCrumb) {
    return applySingleOperationOnTaskArray(tasks, {
      ...operation,

      parameters: {
        payload: operation.payload || {},
        idx: refIdx,
        ...otherCrumbProps,
      },
    });
  }

  const currentTask = tasks[refIdx];
  if (currentTask.type === TaskType.FORK_JOIN) {
    const { forkTasks = [] } = currentTask;
    const joinTask = tasks[refIdx + 1];
    const { ref: targetInnerForkTaskRef, refIdx: targetInnerForkTaskRefIdx } =
      _head(restCrumbs) as Crumb;
    const innerForkTaskReference = forkTasks.findIndex((innerTasks) => {
      return (
        innerTasks[targetInnerForkTaskRefIdx]?.taskReferenceName ===
        targetInnerForkTaskRef
      );
    });
    if (innerForkTaskReference === -1) {
      throw Error("Task not found inconsistent state");
    }
    // Cleanup join task joinOn if the task is deleted
    if (
      joinTask?.type === TaskType.JOIN &&
      operation.type === DELETE_TASK &&
      joinTask.joinOn.includes(targetInnerForkTaskRef)
    ) {
      joinTask.joinOn = joinTask.joinOn.filter(
        (joinOn) => joinOn !== targetInnerForkTaskRef,
      );
    }
    const updatedForkTasks = applyOperationArrayOnTasks(
      restCrumbs,
      forkTasks[innerForkTaskReference],
      operation,
    );
    return adjust(
      refIdx,
      () => ({
        ...currentTask,
        forkTasks: adjust(
          innerForkTaskReference,
          () => updatedForkTasks,
          forkTasks,
        ),
      }),
      tasks,
    );
  } else if (currentTask.type === TaskType.DO_WHILE) {
    const { loopOver = [] } = currentTask;
    const updatedLoopOver = applyOperationArrayOnTasks(
      restCrumbs,
      loopOver,
      operation,
    );

    return adjust(
      refIdx,
      () => ({ ...currentTask, loopOver: updatedLoopOver }),
      tasks,
    );
  } else if (
    currentTask.type === TaskType.SWITCH ||
    currentTask.type === TaskType.DECISION
  ) {
    const { decisionBranch } = _head(restCrumbs) as Crumb;
    const { decisionCases = {}, defaultCase } = currentTask;
    const isDefault = decisionBranch === "defaultCase";

    const decisionCaseTasksAfected = isDefault
      ? defaultCase
      : decisionCases[decisionBranch!];

    const updatedDecisionCase = applyOperationArrayOnTasks(
      restCrumbs,
      decisionCaseTasksAfected || [],
      operation,
    );
    const updated = isDefault
      ? { defaultCase: updatedDecisionCase }
      : {
          decisionCases: {
            ...decisionCases,
            [decisionBranch as string]: updatedDecisionCase,
          },
        };

    return adjust(
      refIdx,
      () => ({
        ...currentTask,
        ...updated,
      }),
      tasks,
    );
  }

  return tasks;
};

export function updateTaskReferenceName(
  tasks: TaskDef[],
  oldRef: string,
  newRef: string,
): TaskDef[] {
  return tasks.map((task) =>
    task.type === TaskType.JOIN && Array.isArray(task.joinOn)
      ? {
          ...task,
          joinOn: task.joinOn.map((ref) => (ref === oldRef ? newRef : ref)),
        }
      : task,
  );
}

type PerformOperationArgs = {
  workflow?: Partial<WorkflowDef>;
  crumbs: Crumb[];
  taskDef: TaskDef;
  operation: OperationType;
};

export const performOperation = ({
  workflow,
  crumbs,
  taskDef: { taskReferenceName },
  operation,
}: PerformOperationArgs) => {
  if (!workflow) {
    throw new Error("No context workflow provided");
  }

  return {
    ...workflow,
    tasks: applyOperationArrayOnTasks(
      findTaskModificationPath(crumbs, taskReferenceName),
      workflow?.tasks || [],
      operation,
    ),
  };
};
type TaskAndCrumbs = { task: TaskDef; crumbs: Crumb[] };

type MoveTaskArgs = {
  workflow?: Partial<WorkflowDef>;
  source: TaskAndCrumbs;
  target: TaskAndCrumbs;
  position: string;
};

export const moveTask = ({
  workflow,
  source: { task: originTaskToMove, crumbs: originCrumbsToMove },
  target: { task: belowDestinationTask, crumbs: belowDestinationTaskCrumbs },
  position,
}: MoveTaskArgs) => {
  if (!workflow) {
    throw new Error("No context workflow provided");
  }

  const PAYLOAD_MODIFICATION_OPERATION =
    belowDestinationTask.type === TaskType.TERMINAL &&
    belowDestinationTask.taskReferenceName ===
      START_TASK_FAKE_TASK_REFERENCE_NAME
      ? ADD_TASK_ABOVE
      : position;

  if (
    [TaskType.FORK_JOIN, TaskType.FORK_JOIN_DYNAMIC].includes(
      originTaskToMove.type,
    )
  ) {
    const maybeLastCrumb = _last(originCrumbsToMove);
    if (maybeLastCrumb?.refIdx != null) {
      const pseudoJoinCrumbs = [
        ...originCrumbsToMove,
        {
          ...maybeLastCrumb,
          refIdx: maybeLastCrumb?.refIdx + 1, // Kind of dangerous operation but we are removing the task after the fork
          ref: "fake_join",
        },
      ];

      // original join task
      const maybeJoinTask = crumbsToTask(
        pseudoJoinCrumbs,
        workflow.tasks || [],
      );

      if (maybeJoinTask?.type === TaskType.JOIN) {
        // Lets assert is a join
        // removes the fork and the join
        const removeTaskResult = performOperation({
          workflow,
          crumbs: originCrumbsToMove,
          taskDef: originTaskToMove,
          operation: {
            type: DELETE_TASK,
            payload: {},
          },
        });

        // remove crumb for fork
        let updatedCrumbs = removeTaskReferenceFromCrumbs(
          belowDestinationTaskCrumbs,
          originTaskToMove.taskReferenceName,
        );

        //remove crumb for join
        updatedCrumbs = removeTaskReferenceFromCrumbs(
          updatedCrumbs,
          maybeJoinTask.taskReferenceName,
        );
        // add original fork and join
        const addTaskResult = performOperation({
          workflow: removeTaskResult,
          crumbs: updatedCrumbs,
          taskDef: belowDestinationTask,
          operation: {
            type: PAYLOAD_MODIFICATION_OPERATION,
            payload: [originTaskToMove, maybeJoinTask],
          },
        });

        return addTaskResult;
      }
      logger.warn("Undefined behavior, join not found");
    }
  }

  const removeTaskResult = performOperation({
    workflow,
    crumbs: originCrumbsToMove,
    taskDef: originTaskToMove,
    operation: {
      type: DELETE_TASK,
      payload: {},
    },
  });

  const updatedCrumbs = removeTaskReferenceFromCrumbs(
    belowDestinationTaskCrumbs,
    originTaskToMove.taskReferenceName,
  );

  const addTaskResult = performOperation({
    workflow: removeTaskResult,
    crumbs: updatedCrumbs,
    taskDef: belowDestinationTask,
    operation: {
      type: PAYLOAD_MODIFICATION_OPERATION,
      payload: originTaskToMove,
    },
  });
  return addTaskResult;
};

// TODO Change this to a reducer

const keyIdentifier = "[key="; //This should not be here extract to constant file

const portIdToDecisionBranch = (portId: string) => {
  const keyIdx = portId.indexOf(keyIdentifier);
  const endIdx = portId.indexOf("]");
  if (keyIdx === -1) {
    throw new Error("Port id is not a decision branch");
  }

  return portId.substring(keyIdx + keyIdentifier.length, endIdx);
};
export const buildDataForRemoveBranchOperation = ({
  port,
  node,
}: {
  port: PortData;
  node: NodeData;
}) => {
  const branchName = portIdToDecisionBranch(port.id);
  return {
    ...node.data,
    branchName,
  };
};

export const buildDataForOperation = (
  port: PortData & { properties: { id?: string; side: string } },
  { data, ports = [] }: NodeData,
) => {
  const portId = port?.properties?.id;
  const { task, crumbs } = data;
  if (task.type === TaskType.TERMINAL) {
    return {
      data: {
        ...data,
        action: ADD_TASK_ABOVE,
      },
    };
  } else if (
    task.type === TaskType.FORK_JOIN &&
    port?.properties?.side === "SOUTH"
  ) {
    const forkIdx = ports.findIndex(({ id }: { id: string }) => portId === id);
    return {
      data: {
        ...data,
        crumbs: adjust(
          crumbs.length - 1,
          () => ({
            ...(_last(crumbs) || {}),
            forkIdx,
          }),
          crumbs,
        ),
        action: ADD_TASK,
      },
    };
  } else if (task.type === TaskType.SWITCH) {
    if (port?.properties?.side === "SOUTH") {
      const decisionBranch = portIdToDecisionBranch(portId!);
      return {
        data: {
          ...data,
          crumbs: adjust(
            crumbs.length - 1,
            () => ({
              ...(_last(crumbs) || {}),
              onDecisionBranch: decisionBranch,
            }),
            crumbs,
          ),
          action: ADD_TASK,
        },
      };
    } else if (port?.properties?.side === "INNER") {
      // Special case this port does not exist but references a button
      return {
        data: {
          ...data,
          action: ADD_TASK_BELOW,
        },
      };
    }
  } else if (
    task.type === TaskType.DO_WHILE &&
    data.action === ADD_TASK_IN_DO_WHILE
  ) {
    return { data };
  }
  return {
    data: {
      ...data,
      action:
        port.properties.side === "SOUTH" ? ADD_TASK_BELOW : ADD_TASK_ABOVE,
    },
  };
};

export const positionIdentifier = (position: string) => {
  if (position === "BELOW") {
    return ADD_TASK_BELOW;
  }
  if (position === "ADD_TASK_IN_DO_WHILE") {
    return ADD_TASK_IN_DO_WHILE;
  }
  return ADD_TASK_ABOVE;
};
