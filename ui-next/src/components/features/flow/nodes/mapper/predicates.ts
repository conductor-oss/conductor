import {
  CommonTaskDef,
  JoinTaskDef,
  TaskType,
  ForkJoinTaskDef,
  ForkJoinDynamicDef,
  DoWhileTaskDef,
  TerminateTaskDef,
  SubWorkflowTaskDef,
  SwitchTaskDef,
  ForkableTask,
} from "types";

export const isJoinTask = (task: CommonTaskDef): task is JoinTaskDef =>
  task?.type === TaskType.JOIN || task?.type === TaskType.EXCLUSIVE_JOIN;

export const isForkJoinTask = (task: CommonTaskDef): task is ForkJoinTaskDef =>
  task?.type === TaskType.FORK_JOIN;

export const isForkJoinDynamicTask = (
  task: CommonTaskDef,
): task is ForkJoinDynamicDef => task?.type === TaskType.FORK_JOIN_DYNAMIC;

export const isDoWhileTask = (task: CommonTaskDef): task is DoWhileTaskDef =>
  task?.type === TaskType.DO_WHILE;

/**
 * An agent that ran tools, which are drawn nested inside it.
 *
 * Only when it has children: an agent that called none is an ordinary card, and giving it a
 * container would draw an empty box round a leaf.
 */
export const isAgentWithTools = (task: CommonTaskDef): boolean =>
  task?.type === TaskType.AGENT &&
  Array.isArray((task as { loopOver?: unknown[] }).loopOver) &&
  ((task as { loopOver?: unknown[] }).loopOver?.length ?? 0) > 0;

export const isTerminateTask = (
  task: CommonTaskDef,
): task is TerminateTaskDef => task?.type === TaskType.TERMINATE;

export const isSubWorkflowTask = (
  task: CommonTaskDef,
): task is SubWorkflowTaskDef => task?.type === TaskType.SUB_WORKFLOW;

/**
 *
 * @param type Test if the task type will be processed as switch
 * @returns
 */
export const isSwitchType = (type?: TaskType): boolean =>
  type != null && [TaskType.DECISION, TaskType.SWITCH].includes(type);

export const isSwitchTask = (task?: CommonTaskDef): task is SwitchTaskDef =>
  isSwitchType(task?.type);

export const isForkableTask = (task: CommonTaskDef): task is ForkableTask =>
  [TaskType.FORK_JOIN, TaskType.FORK_JOIN_DYNAMIC].includes(task.type);
