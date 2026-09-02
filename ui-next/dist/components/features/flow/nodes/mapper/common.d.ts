import { Crumb, CommonTaskDef, TaskStatus } from "types";
import { NodeData } from "reaflow";
import { NodeTaskData } from "./types";
export declare const extractTaskReference: (t: CommonTaskDef) => string;
export declare const extractLastTaskReferenceFn: (...args: any[]) => any;
export declare const extractExecutionDataOrEmpty: (task?: CommonTaskDef & {
    executionData?: any;
}) => any;
export declare const taskHasCompleted: (task?: CommonTaskDef, consideredCompletedStatus?: TaskStatus[]) => boolean;
export declare const taskIsPending: (task?: CommonTaskDef, consideredPendingTaskStatus?: TaskStatus[]) => boolean;
export declare const completedTaskStatusData: (unreachableEdge?: boolean, delayedEdge?: boolean) => {
    status: TaskStatus;
    unreachableEdge: boolean;
    delayedEdge: boolean | undefined;
};
export declare const maybeEdgeData: (currentTask: CommonTaskDef, previousTask?: CommonTaskDef, unreachableEdge?: boolean, delayedEdge?: boolean) => {
    data: {
        status: TaskStatus;
        unreachableEdge: boolean;
        delayedEdge: boolean | undefined;
    };
} | {
    data: {
        unreachableEdge: boolean;
        delayedEdge: boolean | undefined;
    };
};
export declare const edgeIdMapper: ({ taskReferenceName: sourceTaskReferenceName }: CommonTaskDef, { taskReferenceName: destinationTaskReferenceName }: CommonTaskDef) => string;
export declare const taskToNode: <T extends CommonTaskDef>(task: T, crumbs?: Crumb[], additionalProps?: {}) => NodeData<NodeTaskData>;
