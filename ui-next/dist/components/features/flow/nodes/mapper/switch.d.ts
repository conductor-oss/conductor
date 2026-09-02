import { SwitchTaskDef, Crumb, TaskDef, CommonTaskDef } from "types";
import { NodeData, EdgeData } from "reaflow";
import { NodeTaskData, NodesAndEdges } from "./types";
type DecisionBranches = {
    defaultCase: CommonTaskDef[];
    [k: string]: CommonTaskDef[];
};
/**
 * Takes a Switch returns an object with decisionCases and defaultCase
 * *NOTE* defaulCase is added last so that when turning tu entries defaultCase is last
 * @param switchTask
 * @returns
 */
export declare const switchTaskToDecisionsToProcess: (switchTask: SwitchTaskDef) => DecisionBranches;
type NodesEdgesCrumbsPreviousTask = NodesAndEdges & {
    crumbs: Crumb[];
    previousTask: TaskDef;
    previousTaskAllowsConnection: boolean;
};
/**
 * Takes decisionBranches the switch task{taskReferenceName} initial crumbs
 * and a taskWalker. will return nodes,edges,crumbs,previous task by branch
 * @param switchTask
 * @param crumbs
 * @param taskWalkerFn
 * @returns
 */
export declare const decisionBranchesToNodesEdgesByCase: (switchTask: SwitchTaskDef, crumbs: Crumb[], taskWalkerFn: any) => Promise<{
    [k: string]: NodesEdgesCrumbsPreviousTask;
}>;
export type ProcessedSwitchTask = CommonTaskDef & {
    allowsTaskConnection?: boolean;
};
type SwitchTaskNodesEdgesEndingTasksDecisionKeysEndingNodes = NodesAndEdges & {
    decisionKeys: string[];
    lastSwitchTasks: Array<ProcessedSwitchTask | undefined>;
    lastSwitchNodes: Array<NodeData | undefined>;
};
/**
 * Returns every node that can be travered from the switchTask, every edge connected, every decision key
 * The last task of every branch. and the last switch node. will insert undefined if node is empty
 * so the order matches the decisionKeys
 *
 * @param switchTask
 * @param crumbs
 * @param taskWalkerFn
 * @returns
 */
export declare const processSwitchTasks: (switchTask: SwitchTaskDef, crumbs: Crumb[], taskWalkerFn: any) => Promise<SwitchTaskNodesEdgesEndingTasksDecisionKeysEndingNodes>;
export declare const switchTaskToNode: (task: SwitchTaskDef, crumbs: Crumb[], decisionKeys: string[]) => NodeData<NodeTaskData<SwitchTaskDef>>;
type SwitchTaskDriller = (task: SwitchTaskDef, endLeafTasks: CommonTaskDef[]) => Promise<ProcessedSwitchTask[]>;
/**
 * @deprecated This function made sense when no switch-join.
 * Returns a function that takes a task. Will look for non terminated tasks
 * used to identify missing connection edges
 * @param {*} tasksAsNodes
 * @returns
 */
export declare const drillForEndTasks: (tasksAsNodes: any) => SwitchTaskDriller;
export declare const nonTerminatedTasksGroupedAsTaskReferenceNameByType: (nonTerminatedTasks: TaskDef[]) => {
    switchTr: CommonTaskDef[];
    nonTerminatedTr: CommonTaskDef[];
};
export declare const switchTaskToFakeNodeId: ({ taskReferenceName }: SwitchTaskDef) => string;
export declare const switchFakeTaskIDSouthPortId: (fakeTaskId: string) => string;
export declare const createFakeNode: (switchCaseTask: SwitchTaskDef, crumbs: Crumb[], decisionCasesKeys: string[]) => NodeData;
export declare const lastNodeToFakeTaskEdge: (lastNode: NodeData<NodeTaskData>, switchNode: NodeData<NodeTaskData>, fakeNodeId: string, decisionBranch: string) => EdgeData;
export declare const switchFakeTaskEdges: (switchLastNodes: Array<NodeData | undefined>, lastSwitchTasks: Array<ProcessedSwitchTask | undefined>, switchCreatedNode: NodeData, decisionKeys: string[], fakeNode: NodeData, selectedCase?: string) => EdgeData[];
export declare const taskToSwitchNodesEdges: (currentTask: SwitchTaskDef, crumbs: Crumb[], taskWalkerFn: any) => Promise<NodesAndEdges & {
    everyTaskIsTerminate: boolean;
}>;
export declare const isSwitchPathEmpty: (portId: string, currentTask: SwitchTaskDef) => boolean | "";
export {};
