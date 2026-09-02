import { ForkJoinDynamicDef, Crumb } from "types";
export declare const taskToForkJoinDynamicNodesEdges: (task: ForkJoinDynamicDef, crumbs: Crumb[], taskWalkerFn: any) => Promise<{
    nodes: import("reaflow").NodeData<import("./types").NodeTaskData<import("types").CommonTaskDef>>[];
    edges: import("reaflow").EdgeData<Partial<{
        unreachableEdge?: boolean;
        status?: import("types").TaskStatus;
    }>>[];
}>;
