import { TaskDef, TaskType, WorkflowDef } from "types";
import { ExecutionTask } from "types/Execution";

/**
 * A detached task runs inside a workflow's execution without being a step of its definition.
 *
 * The diagram is built from the definition, so a detached task can never become a node — which is
 * the point: the workflow was never asked to run it and its progress does not wait on it. It is
 * drawn beside the task it was run for instead, and this module decides which tasks those are.
 *
 * The rule is the same one the server applies (io.orkes.conductor.common.utils.DetachedTasks), so
 * the console and the server always agree: a task is detached when the definition declares no task
 * with its reference name, and it is one of the side tasks drawn beside a node when it also names a
 * parent that the definition does declare and that does not fan out into branches of its own.
 */

const LOOP_TASK_DELIMITER = "__";

/** Reference name to declared type, including tasks nested in branches and loops. */
const declaredTasks = (
  definition?: Partial<WorkflowDef>,
): Map<string, string | undefined> => {
  const declared = new Map<string, string | undefined>();
  const walk = (tasks?: TaskDef[]) => {
    (tasks || []).forEach((task) => {
      if (!task) return;
      if (task.taskReferenceName)
        declared.set(task.taskReferenceName, task.type);
      (task.forkTasks || []).forEach(walk);
      Object.values(task.decisionCases || {}).forEach(walk);
      walk(task.defaultCase);
      walk(task.loopOver);
    });
  };
  walk(definition?.tasks);
  return declared;
};

/** Every reference name the definition declares, including tasks nested in branches and loops. */
export const declaredReferenceNames = (
  definition?: Partial<WorkflowDef>,
): Set<string> => new Set(declaredTasks(definition).keys());

/**
 * A loop iteration's reference name carries an iteration suffix its definition entry does not, so
 * the bare name is what has to be looked up.
 */
const withoutIteration = (referenceName: string): string =>
  referenceName.split(LOOP_TASK_DELIMITER)[0] || referenceName;

const referenceNameOf = (task: ExecutionTask): string | undefined =>
  task?.referenceTaskName || task?.workflowTask?.taskReferenceName;

/**
 * Whether this execution task is outside the workflow's DAG.
 *
 * False when there is no definition to compare against: unknown is not the same as detached, and
 * guessing would call every task detached.
 */
export const isDetached = (
  declared: Set<string>,
  task: ExecutionTask,
): boolean => {
  const referenceName = referenceNameOf(task);
  if (!referenceName || declared.size === 0) return false;
  return (
    !declared.has(referenceName) &&
    !declared.has(withoutIteration(referenceName))
  );
};

/**
 * Whether this task is one the engine attached to a task of the DAG — a guardrail detector.
 *
 * The parent's type is the only thing separating one from a dynamic fork's branch: a branch also
 * names a parent and is also absent from the definition, but it is the workflow's own work, drawn
 * by the diagram's dynamic-fork handling and rerunnable like any other task.
 */
export const isSideTask = (
  declared: Map<string, string | undefined>,
  task: ExecutionTask,
): boolean => {
  const parent = task?.parentTaskReferenceName;
  if (!parent || !declared.has(parent)) return false;
  if (declared.get(parent) === TaskType.FORK_JOIN_DYNAMIC) return false;
  return isDetached(new Set(declared.keys()), task);
};

/** The side tasks of an execution, grouped by the definition task each was run for. */
export const detachedTasksByParent = (
  executionTasks: ExecutionTask[] = [],
  definition?: Partial<WorkflowDef>,
): Record<string, ExecutionTask[]> => {
  const declared = declaredTasks(definition);
  if (declared.size === 0) return {};

  return executionTasks.reduce(
    (grouped: Record<string, ExecutionTask[]>, task: ExecutionTask) => {
      if (!isSideTask(declared, task)) return grouped;
      const parent = task.parentTaskReferenceName as string;
      return { ...grouped, [parent]: [...(grouped[parent] || []), task] };
    },
    {},
  );
};

/** The same question, asked of a single task by a caller that only has the definition. */
export const isSideTaskOf = (
  task: ExecutionTask | undefined,
  definition?: Partial<WorkflowDef>,
): boolean => (task ? isSideTask(declaredTasks(definition), task) : false);
