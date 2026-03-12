import { MAX_EXPAND_TASKS } from "components/flow/nodes/constants";
import _curry from "lodash/curry";
import _findLast from "lodash/findLast";
import _first from "lodash/first";
import _path from "lodash/fp/path";
import _identity from "lodash/identity";
import _mapValues from "lodash/mapValues";
import _nth from "lodash/nth";
import _omit from "lodash/omit";
import _pick from "lodash/pick";
import _xor from "lodash/xor";
import {
  DoWhileSelection,
  ExecutedData,
  Execution,
  ExecutionTask,
  TaskDef,
  TaskStatus,
  TaskType,
} from "types";
import {
  DynamicForkRelations,
  StatusMap,
  TypeStatusMap,
} from "./StatusMapTypes";
import { TaskDefExecutionContext, WorkflowDefExecutionContext } from "./types";

export const relatedNamesToTaskDef = (
  names: string[],
  executionTasks: ExecutionTask[],
  parentTaskReferenceName: string,
) => {
  const relationTasks = names.map((tn) =>
    executionTasks.find((t) => t.workflowTask.taskReferenceName === tn),
  );
  const taskWithSiblings = names.reduce(
    (tnAcc, tn) => ({
      ...tnAcc,
      [tn]: {
        siblings: relationTasks,
        parentTaskReferenceName,
      },
    }),
    {},
  );
  return taskWithSiblings;
};

type StatusMapTupleAcumulator = [
  StatusMap,
  Record<string, DynamicForkRelations>,
];
export const executionTasksToStatusMap = (executionTasks: ExecutionTask[]) => {
  const [statusMap] = executionTasks.reduce(
    (
      [acc, related]: StatusMapTupleAcumulator,
      task: ExecutionTask,
      idx: number,
    ): StatusMapTupleAcumulator => {
      const loopOver = acc[task.workflowTask.taskReferenceName]?.loopOver || [];
      let newRelated = related;
      if (task.workflowTask.type === TaskType.FORK_JOIN_DYNAMIC) {
        newRelated = {
          ...related,
          ...relatedNamesToTaskDef(
            task.inputData?.forkedTasks || [],
            executionTasks,
            task.workflowTask.taskReferenceName,
          ),
        };
      }

      const targetSlice = executionTasks?.slice(0, idx);

      return [
        {
          ...acc,
          [task.workflowTask.taskReferenceName]: {
            ...task,
            loopOver: loopOver.concat(task),
            related: related[task.workflowTask.taskReferenceName],
            parentLoop: task.loopOverTask
              ? _findLast(targetSlice, (t) => t.taskType === TaskType.DO_WHILE)
              : undefined,
          },
        } as Record<string, TypeStatusMap>,
        newRelated,
      ];
    },
    [{}, {}],
  );
  return statusMap;
};

const extractFirstTaskReferenceName = (tasks: TaskDef[]) => {
  const firstTask = _first(tasks);
  return firstTask == null ? "no_task" : firstTask.taskReferenceName;
};

const entireCollapsedStatus = (
  executionTask: TypeStatusMap,
  firstTaskExecutionDataRaw: Pick<
    TypeStatusMap,
    "status" | "executed" | "loopOver"
  >,
  statusMap: StatusMap,
  returnStatusArray = false,
) => {
  const forkedTaskDefs: TaskDef[] =
    _path("inputData.forkedTaskDefs", executionTask) ?? [];
  const collapsedTaskRefNames = forkedTaskDefs
    ? forkedTaskDefs.map((item) => item.taskReferenceName)
    : [];
  const collapsedTasksStatus =
    collapsedTaskRefNames.map((item) => statusMap[item]?.status) ?? [];
  if (returnStatusArray) {
    return collapsedTasksStatus;
  }
  if (collapsedTasksStatus.includes(TaskStatus.FAILED)) {
    return TaskStatus.FAILED;
  } else if (collapsedTasksStatus.includes(TaskStatus.TIMED_OUT)) {
    return TaskStatus.TIMED_OUT;
  } else if (collapsedTasksStatus.includes(TaskStatus.SKIPPED)) {
    return TaskStatus.SKIPPED;
  } else if (collapsedTasksStatus.includes(TaskStatus.CANCELED)) {
    return TaskStatus.CANCELED;
  } else {
    return firstTaskExecutionDataRaw.status;
  }
};

export const taskStatusUpdater = (
  tasks: TaskDef[] = [],
  statusMap: StatusMap,
  expandDynamic: string[],
): TaskDefExecutionContext[] => {
  return tasks.map((task) => {
    const { type, taskReferenceName } = task;
    const executionTask = statusMap[taskReferenceName];

    const executionDataRaw = _pick(
      executionTask || {
        status: TaskStatus.PENDING,
        executed: false,
        loopOver: [],
      },
      ["status", "executed", "loopOver"],
    );
    const executionData: ExecutedData = {
      status: executionDataRaw.status as TaskStatus,
      executed: executionDataRaw.executed,
      attempts: executionDataRaw.loopOver.length,
      outputData: executionTask?.outputData,
      parentLoop: executionTask?.parentLoop,
    };

    if (type === TaskType.FORK_JOIN) {
      const forkTasks: Array<TaskDefExecutionContext[]> = (
        task.forkTasks || []
      ).map((taa) => taskStatusUpdater(taa, statusMap, expandDynamic));
      return {
        ...task,
        forkTasks,
        executionData,
      } as TaskDefExecutionContext;
    } else if (type === TaskType.DECISION || type === TaskType.SWITCH) {
      const decisionCases: Record<string, TaskDefExecutionContext[]> =
        _mapValues(task.decisionCases, (decisionTasks: TaskDef[]) =>
          taskStatusUpdater(decisionTasks, statusMap, expandDynamic),
        );
      return {
        ...task,
        decisionCases,
        defaultCase: taskStatusUpdater(
          task.defaultCase!,
          statusMap,
          expandDynamic,
        ),
        executionData,
      } as TaskDefExecutionContext;
    } else if (type === TaskType.DO_WHILE) {
      return {
        ...task,
        loopOver: taskStatusUpdater(task.loopOver!, statusMap, expandDynamic),
        executionData,
      } as TaskDefExecutionContext;
    } else if (
      type === TaskType.FORK_JOIN_DYNAMIC &&
      executionData.status !== TaskStatus.PENDING
    ) {
      const doesNotRequireCollapse =
        (executionTask!.inputData?.forkedTaskDefs || []).length <
        MAX_EXPAND_TASKS;

      if (expandDynamic.includes(taskReferenceName) || doesNotRequireCollapse) {
        const forkTasks: Array<TaskDefExecutionContext[]> = taskStatusUpdater(
          executionTask!.inputData!.forkedTaskDefs,
          statusMap,
          expandDynamic,
        ).map((t: TaskDefExecutionContext) => [t]);
        return {
          ...task,
          forkTasks,
          joinOn: executionTask?.inputData?.forkedTasks,
          executionData: {
            ...executionData,
            ...(doesNotRequireCollapse ? {} : { collapsed: false }),
          },
        } as TaskDefExecutionContext;
      }

      const firstTaskReferenceName = extractFirstTaskReferenceName(
        executionTask!.inputData!.forkedTaskDefs,
      );

      const firstTaskExecutionDataRaw = _pick(
        statusMap[firstTaskReferenceName] || {
          status: TaskStatus.PENDING,
          executed: false,
        },
        ["status", "executed", "loopOver"],
      );

      const firstTaskExecutionData = {
        status: entireCollapsedStatus(
          executionTask,
          firstTaskExecutionDataRaw,
          statusMap,
        ),
        executed: firstTaskExecutionDataRaw?.executed,
        attempts: firstTaskExecutionDataRaw?.loopOver?.length,
      };
      return {
        ...task,
        forkTasks: [
          [
            {
              type: "FORK_JOIN_COLLAPSED" as TaskType,
              taskReferenceName: firstTaskReferenceName,
              executionData: {
                ...firstTaskExecutionData,
                collapsedTasks: executionTask?.inputData?.forkedTaskDefs,
                parentTaskReferenceName: task.taskReferenceName,
                collapsedTasksStatus: entireCollapsedStatus(
                  executionTask,
                  firstTaskExecutionDataRaw,
                  statusMap,
                  true,
                ),
              },
            },
          ],
        ],
        executionData: {
          ...executionData,
          collapsed: true,
        },
      } as TaskDefExecutionContext;
    } else if (type === TaskType.SIMPLE) {
      return {
        ...task,
        executionData: {
          ...executionData,
          domain: executionTask?.domain,
        },
      } as TaskDefExecutionContext;
    } else if (type === TaskType.TASK_SUMMARY) {
      return {
        ...task,
        executionData: {
          ...executionData,
          summary: executionTask?.outputData?.summary,
        },
      } as TaskDefExecutionContext;
    }
    return {
      ...task,
      executionData,
    } as TaskDefExecutionContext;
  });
};

const getTasksOfCurrentIteration = (
  selectedIteration: number,
  selectedDowhileRef?: string,
  statusMap: StatusMap = {},
) => {
  let result = [];
  for (const key in statusMap) {
    if (
      Object.prototype.hasOwnProperty.call(statusMap, key) &&
      statusMap[key]["iteration"] === selectedIteration
    ) {
      result.push({ taskRefName: key, taskType: statusMap[key].taskType });
    }
  }
  if (selectedDowhileRef) {
    result = result.filter((item) => item.taskType !== TaskType.DO_WHILE);
  }
  return result?.map((item) => item.taskRefName);
};

export const doWhileSelectionForStatusMap = (
  doWhileSelection?: DoWhileSelection[],
  statusMap?: StatusMap,
) => {
  const [keysToOmmit, finalMapArray] = doWhileSelection
    ? doWhileSelection.reduce<
        [keysToOmmit: string[], finalMapArray: Omit<any, string>[]]
      >(
        (acc, item) => {
          const doWhileTask = statusMap![item?.doWhileTaskReferenceName];

          const { outputData } = doWhileTask;
          const taskReferencesForIteration = Object.keys(
            _path(item?.selectedIteration, outputData) || {},
          );

          const allReferences = Array.from(
            new Set(
              Object.values(outputData ?? {}).flatMap((obj) =>
                Object.keys(obj ?? {}),
              ),
            ),
          );

          // if tasksReferencesForIteration is there, use it. if not, we have get the tasks of currentIteration from the statusMap using getTasksOfCurrentIteration()
          const updatedTaskReferenceForIteration =
            taskReferencesForIteration && taskReferencesForIteration.length > 0
              ? taskReferencesForIteration
              : getTasksOfCurrentIteration(
                  item?.selectedIteration,
                  item?.doWhileTaskReferenceName,
                  statusMap,
                );

          const updatedStatusMap = updatedTaskReferenceForIteration.reduce(
            (acc: any, taskReferenceName: string) => {
              const currentAssociatedTask = statusMap![taskReferenceName];

              const loopOverForAssociatedTask = currentAssociatedTask?.loopOver;
              const maybeParentLoop = currentAssociatedTask?.parentLoop;
              const taskForIterationNumber = loopOverForAssociatedTask?.find(
                (task: Partial<TaskDef>) =>
                  task.iteration === item?.selectedIteration,
              );

              return {
                ...acc,
                [taskReferenceName]: {
                  ...taskForIterationNumber,
                  loopOver: loopOverForAssociatedTask,
                  parentLoop: maybeParentLoop,
                },
              };
            },
            {},
          );
          const keysToRemove = _xor(
            updatedTaskReferenceForIteration,
            allReferences,
          );
          const latestMap = _omit(updatedStatusMap, keysToRemove);

          return [
            [...acc[0], ...keysToRemove],
            [...acc[1], latestMap],
          ];
        },

        [[], []],
      )
    : [[], []];
  const latestMap = _omit(statusMap, keysToOmmit);
  // Spreads finalMap as arguments to the Object.assign call
  return Object.assign({}, latestMap, ...(finalMapArray ?? []));
};

const updateStatusMapWithLatestRetryCount = (
  statusMap: StatusMap,
  selectedTask: ExecutionTask,
) => {
  const { retryCount, referenceTaskName } = selectedTask;
  const currentTaskLoopOverFromMap =
    statusMap[referenceTaskName]?.loopOver ?? [];

  const currentTaskWithUpdatedRetryCount =
    _nth(
      currentTaskLoopOverFromMap?.filter(
        (item) => item.retryCount === retryCount,
      ),
      0,
    ) ?? {};

  const updatedStatusMap = {
    ...statusMap,
    [referenceTaskName]: {
      ...currentTaskWithUpdatedRetryCount,
      loopOver: currentTaskLoopOverFromMap,
    },
  };
  return updatedStatusMap;
};

export const executionToWorkflowDef = (
  execution: Execution,
  expandDynamic = [],
  doWhileSelection?: DoWhileSelection[],
  selectedTask?: ExecutionTask,
): [WorkflowDefExecutionContext, StatusMap] => {
  const doWhileModifier =
    doWhileSelection == null
      ? _identity
      : _curry(doWhileSelectionForStatusMap)(doWhileSelection);

  const basicExecutionMap = executionTasksToStatusMap(execution.tasks);

  const updatedExecutionMapWithLatestRetryCount =
    selectedTask && selectedTask?.taskType === TaskType.DO_WHILE
      ? updateStatusMapWithLatestRetryCount(basicExecutionMap, selectedTask)
      : basicExecutionMap;

  const taskExecutionMap = doWhileModifier(
    updatedExecutionMapWithLatestRetryCount,
  );

  return [
    {
      ...execution.workflowDefinition,
      tasks: taskStatusUpdater(
        execution.workflowDefinition.tasks,
        taskExecutionMap,
        expandDynamic,
      ),
    },
    taskExecutionMap,
  ];
};
