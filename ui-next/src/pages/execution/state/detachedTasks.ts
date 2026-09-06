import { TaskDef, WorkflowDef } from "types";
import { ExecutionTask } from "types/Execution";

/**
 * A detached task runs inside a workflow's execution without being a step of its definition.
 *
 * The diagram is built from the definition, so a detached task can never become a node — which is
 * the point: the workflow was never asked to run it and its progress does not wait on it. It is
 * drawn beside the task it was run for instead, and this module decides which tasks those are.
 *
 * The rule is the same one the server applies when it decides whether a workflow has finished
 * (io.orkes.conductor.common.utils.DetachedTasks), so the console and the server always agree: a
 * task is detached exactly when the definition declares no task with its reference name.
 */

const LOOP_TASK_DELIMITER = "__";

/** Every reference name the definition declares, including tasks nested in branches and loops. */
export const declaredReferenceNames = (
  definition?: Partial<WorkflowDef>,
): Set<string> => {
  const names = new Set<string>();
  const walk = (tasks?: TaskDef[]) => {
    (tasks || []).forEach((task) => {
      if (!task) return;
      if (task.taskReferenceName) names.add(task.taskReferenceName);
      (task.forkTasks || []).forEach(walk);
      Object.values(task.decisionCases || {}).forEach(walk);
      walk(task.defaultCase);
      walk(task.loopOver);
    });
  };
  walk(definition?.tasks);
  return names;
};

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
 * The detached tasks of an execution, grouped by the definition task each was run for.
 *
 * A dynamic fork's children also carry a parent reference, and they are real nodes of the graph, so
 * the definition check above is what keeps them out of this map.
 */
export const detachedTasksByParent = (
  executionTasks: ExecutionTask[] = [],
  definition?: Partial<WorkflowDef>,
): Record<string, ExecutionTask[]> => {
  const declared = declaredReferenceNames(definition);
  if (declared.size === 0) return {};

  return executionTasks.reduce(
    (grouped: Record<string, ExecutionTask[]>, task: ExecutionTask) => {
      const parent = task?.parentTaskReferenceName;
      if (!parent || !isDetached(declared, task)) return grouped;
      // Only ever attach to something the diagram actually draws.
      if (!declared.has(parent)) return grouped;
      return { ...grouped, [parent]: [...(grouped[parent] || []), task] };
    },
    {},
  );
};
