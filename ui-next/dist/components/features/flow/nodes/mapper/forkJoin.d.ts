import { ForkJoinTaskDef, Crumb, CommonTaskDef, ForkableTask } from "types";
import { NodeData, EdgeData } from "reaflow";
import { NodesAndEdges, NodeTaskData } from "./types";
export declare const innerTaskConnectingEdge: (taskHoldingTasks: CommonTaskDef, processedInnerNodes: NodeData[], suffix?: string) => EdgeData;
export declare const processForkJoinTasks: <T extends ForkableTask>(forkJoinTask: T, crumbs: Crumb[], taskWalkerFn: any) => Promise<NodesAndEdges>;
export declare const forkJoinTaskToNode: <T extends ForkableTask>(task: T, crumbs: Crumb[]) => NodeData<NodeTaskData<T>>;
export declare const taskToForkJoinNodesEdges: (task: ForkJoinTaskDef, crumbs: Crumb[], taskWalkerFn: any) => Promise<{
    nodes: NodeData<NodeTaskData<CommonTaskDef>>[];
    edges: EdgeData<Partial<{
        unreachableEdge?: boolean;
        status?: import("types").TaskStatus;
    }>>[];
}>;
export declare const isForkJoinPathEmpty: (forkIndex: number, currentTask: ForkJoinTaskDef) => boolean | 0;
