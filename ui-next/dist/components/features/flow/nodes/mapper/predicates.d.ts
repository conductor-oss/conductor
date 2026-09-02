import { CommonTaskDef, JoinTaskDef, TaskType, ForkJoinTaskDef, ForkJoinDynamicDef, DoWhileTaskDef, TerminateTaskDef, SubWorkflowTaskDef, SwitchTaskDef, ForkableTask } from "types";
export declare const isJoinTask: (task: CommonTaskDef) => task is JoinTaskDef;
export declare const isForkJoinTask: (task: CommonTaskDef) => task is ForkJoinTaskDef;
export declare const isForkJoinDynamicTask: (task: CommonTaskDef) => task is ForkJoinDynamicDef;
export declare const isDoWhileTask: (task: CommonTaskDef) => task is DoWhileTaskDef;
/**
 * An agent that ran tools, which are drawn nested inside it.
 *
 * Only when it has children: an agent that called none is an ordinary card, and giving it a
 * container would draw an empty box round a leaf.
 */
export declare const isAgentWithTools: (task: CommonTaskDef) => boolean;
export declare const isTerminateTask: (task: CommonTaskDef) => task is TerminateTaskDef;
export declare const isSubWorkflowTask: (task: CommonTaskDef) => task is SubWorkflowTaskDef;
/**
 *
 * @param type Test if the task type will be processed as switch
 * @returns
 */
export declare const isSwitchType: (type?: TaskType) => boolean;
export declare const isSwitchTask: (task?: CommonTaskDef) => task is SwitchTaskDef;
export declare const isForkableTask: (task: CommonTaskDef) => task is ForkableTask;
