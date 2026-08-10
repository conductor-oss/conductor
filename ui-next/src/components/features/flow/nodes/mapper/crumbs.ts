import _findLast from "lodash/findLast";
import _head from "lodash/head";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import _nth from "lodash/nth";
import _tail from "lodash/tail";
import { Crumb, TaskDef, TaskType } from "types";

const taskForCurrentCrumb = (
  crumb: Crumb,
  tasks: TaskDef[],
  parentTask?: TaskDef,
): TaskDef | undefined => {
  if (_isNil(crumb?.parent)) {
    const maybeTask = _nth(tasks, crumb?.refIdx);
    if (maybeTask) {
      return maybeTask;
    }
  }

  switch (parentTask?.type) {
    case TaskType.FORK_JOIN: {
      const { forkIndex, refIdx: forkRefIndex } = crumb!;
      const forkTasks: TaskDef[][] = parentTask!.forkTasks!;
      return _nth(_nth(forkTasks, forkIndex), forkRefIndex);
    }
    case TaskType.SWITCH:
    case TaskType.DECISION: {
      const { decisionBranch, refIdx: switchRefIndex } = crumb!;
      const { decisionCases, defaultCase } = parentTask;
      const isDefault = decisionBranch === "defaultCase";

      const decisionCaseTasksAffected = isDefault
        ? defaultCase
        : decisionCases![decisionBranch!]!;

      return _nth(decisionCaseTasksAffected, switchRefIndex);
    }
    case TaskType.DO_WHILE: {
      const { loopOver } = parentTask!;
      return _nth(loopOver, crumb.refIdx);
    }
    default: {
      return _nth(tasks, crumb.refIdx);
    }
  }
};

export const crumbsToTaskSteps = (
  crumbs: Crumb[],
  tasks: TaskDef[],
  taskSteps: TaskDef[] = [],
  maybeParent?: TaskDef,
): TaskDef[] => {
  const restCrumbs = _tail(crumbs);
  const currentCrumb = _head(crumbs);
  const parent =
    maybeParent?.taskReferenceName === currentCrumb?.parent // parent was memorized use parent, else finde parent
      ? maybeParent
      : _findLast(
          taskSteps,
          (_ref) => _ref?.taskReferenceName === currentCrumb?.parent,
        );

  const task = taskForCurrentCrumb(currentCrumb!, tasks, parent);
  if (_isEmpty(restCrumbs)) {
    return task != null ? taskSteps.concat(task) : taskSteps;
  }
  return crumbsToTaskSteps(restCrumbs, tasks, taskSteps.concat(task!), parent);
};

export const crumbsToTask = (
  crumbs: Crumb[],
  tasks: TaskDef[],
): TaskDef | undefined => {
  return _isEmpty(crumbs) || _isEmpty(tasks)
    ? undefined
    : _last(crumbsToTaskSteps(crumbs, tasks));
};

const applyFuncToIndexIfParent = (
  crumbs: Crumb[],
  parent: string | null | undefined,
  func: (crumb: Crumb) => Crumb,
) => {
  return crumbs.map((crumb) => {
    if (crumb.parent === parent) {
      return func(crumb);
    }
    return crumb;
  });
};

export const removeTaskReferenceFromCrumbs = (
  crumbs: Crumb[],
  taskReferenceName: string,
) => {
  let newCrumbs: Crumb[] = [];
  for (let i = 0; i < crumbs.length; i++) {
    const currentCrumb = crumbs[i];
    if (currentCrumb.ref === taskReferenceName) {
      return newCrumbs.concat(
        applyFuncToIndexIfParent(
          crumbs.slice(i + 1),
          currentCrumb.parent,
          (crumb) => ({
            ...crumb,
            refIdx: crumb.refIdx - 1,
          }),
        ),
      );
    } else {
      newCrumbs = newCrumbs.concat(currentCrumb);
    }
  }
  return newCrumbs;
};

export const isTaskReferenceNestedInAnyTaskReference = (
  crumbs: Crumb[],
  targetTaskReference: string,
  maybeParentTaskReferenceName: string[],
): boolean => {
  const parentMap = new Map<string, string>();
  for (let i = 0; i < crumbs.length; i++) {
    const currentCrumb = crumbs[i];
    if (currentCrumb.parent != null) {
      parentMap.set(currentCrumb.ref, currentCrumb.parent);
    }
    if (currentCrumb.ref === targetTaskReference) {
      if (currentCrumb.parent != null) {
        const doesCurrentCrumbParentHasTargetParent =
          maybeParentTaskReferenceName.includes(currentCrumb.parent);
        const doesCurrentCrumbParentHasParent =
          parentMap.get(currentCrumb.parent) != null;

        const isParentOfParentTarget =
          doesCurrentCrumbParentHasParent &&
          maybeParentTaskReferenceName.includes(
            parentMap.get(currentCrumb.parent)!,
          );

        const parentOfParentIsNotTarget = () =>
          doesCurrentCrumbParentHasParent &&
          isTaskReferenceNestedInAnyTaskReference(
            crumbs,
            parentMap.get(currentCrumb.parent!)!, //we know its not null we've checked
            maybeParentTaskReferenceName,
          );

        return (
          doesCurrentCrumbParentHasTargetParent ||
          isParentOfParentTarget ||
          parentOfParentIsNotTarget()
        );
      }
    }
  }
  return false;
};
export const isTaskReferenceNestedInTaskReference = (
  crumbs: Crumb[],
  targetTaskReference: string,
  maybeParentTaskReferenceName: string,
): boolean => {
  return isTaskReferenceNestedInAnyTaskReference(crumbs, targetTaskReference, [
    maybeParentTaskReferenceName,
  ]);
};

/**
 * Takes the crumb
 * @param crumbs
 * @param forkTaskReferenceName
 * @param joinTaskReferenceName
 */
export const isTaskNext = (
  crumbs: Crumb[],
  targetTaskReferenceFirst: string,
  targetTaskReferenceSecond: string,
): boolean => {
  const firstTaskIndex = crumbs.findIndex(
    (crumb) => crumb.ref === targetTaskReferenceFirst,
  );
  const secondTaskIndex = crumbs.findIndex(
    (crumb) => crumb.ref === targetTaskReferenceSecond,
  );
  if (firstTaskIndex === -1 || secondTaskIndex === -1) return false;

  const firstTaskCrumb = _nth(crumbs, firstTaskIndex);
  const secondTaskCrumb = _nth(crumbs, secondTaskIndex);
  if (firstTaskCrumb != null && secondTaskCrumb != null) {
    const isSameParent = firstTaskCrumb?.parent === secondTaskCrumb?.parent;

    const isSecondTaskAfterFirstTask =
      secondTaskCrumb.refIdx === firstTaskCrumb.refIdx + 1;
    return isSameParent && isSecondTaskAfterFirstTask;
  }

  return false;
};

/**
 * Takes a crumbs list and a taskReference. will return the previous task crumb in the DAG tree
 * @param crumbs
 * @param taskReferenceName
 * @returns
 */
export const previousTaskCrumb = (
  crumbs: Crumb[],
  taskReferenceName: string,
): Crumb | undefined => {
  const taskIndex = crumbs.findIndex(
    (crumb) => crumb.ref === taskReferenceName,
  );
  if (taskIndex === -1) return undefined;
  const crumbAtIndex = _nth(crumbs, taskIndex);
  if (crumbAtIndex !== undefined) {
    const targetSlice = crumbs.slice(0, taskIndex);
    const maybeElement = _findLast(
      targetSlice,
      (crumb) =>
        crumb.parent === crumbAtIndex.parent &&
        crumb.refIdx === crumbAtIndex.refIdx - 1,
    );
    return maybeElement;
  }
  return undefined;
};

export const isSubWorkflowChild = (
  crumbs: Crumb[],
  taskReferenceName: string,
): boolean => {
  let availableSubworkflows;
  if (crumbs) {
    availableSubworkflows = crumbs
      .filter((item) => item.type === TaskType.SUB_WORKFLOW)
      .map((item) => item.ref);
    return isTaskReferenceNestedInAnyTaskReference(
      crumbs,
      taskReferenceName,
      availableSubworkflows,
    );
  }
  return false;
};
