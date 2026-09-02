import { DoWhileTaskDef, Crumb, TaskType } from "types";
import { NodeData, EdgeData } from "reaflow";
/**
 * Anything drawn as a container of child tasks.
 *
 * DO_WHILE is the original, and an AGENT that ran tools has the same shape: a task whose children
 * belong inside it rather than after it.
 */
type ContainerTaskDefWithMaybeExecutionData = Omit<DoWhileTaskDef, "type"> & {
    type: DoWhileTaskDef["type"] | TaskType.AGENT;
    executionData?: any;
};
type NodesEdgesAndCrumbs = {
    nodes: NodeData[];
    edges: EdgeData[];
    crumbs: Crumb[];
};
export declare const processDoWhile: (doWhileTask: ContainerTaskDefWithMaybeExecutionData, crumbs: Crumb[], taskWalkerFn: any) => Promise<NodesEdgesAndCrumbs>;
export {};
