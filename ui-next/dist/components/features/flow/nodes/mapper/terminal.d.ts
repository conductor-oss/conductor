import { TaskType, CommonTaskDef, WorkflowExecutionStatus } from "types";
import { NodeData, EdgeData } from "reaflow";
export declare const START_TASK_FAKE_TASK_REFERENCE_NAME = "start";
export declare const END_TASK_FAKE_TASK_REFERENCE_NAME = "end";
type NodesAndEdges = {
    nodes: NodeData[];
    edges: EdgeData[];
};
export declare const terminalNode: (task: CommonTaskDef) => {
    ports: undefined;
    id: string;
    disabled?: boolean;
    text?: any;
    height?: number;
    width?: number;
    parent?: string;
    icon?: import("reaflow").IconData;
    nodePadding?: number | [number, number] | [number, number, number, number];
    data?: import("./types").NodeTaskData<CommonTaskDef> | undefined;
    className?: string;
    layoutOptions?: import("reaflow").ElkNodeLayoutOptions;
    selectionDisabled?: boolean;
};
export declare const firstTask: {
    name: string;
    taskReferenceName: string;
    type: TaskType;
};
export declare const lastTask: {
    name: string;
    taskReferenceName: string;
    type: TaskType;
};
export declare const startNode: NodeData<import("./types").NodeTaskData<CommonTaskDef>>;
export declare const endNode: NodeData<import("./types").NodeTaskData<CommonTaskDef>>;
export declare const processLastTask: ({ nodes, edges, previousTask, previousTaskAllowsConnection, }: NodesAndEdges & {
    previousTask?: CommonTaskDef;
    previousTaskAllowsConnection: boolean;
}, executionStatus?: WorkflowExecutionStatus) => {
    nodes: NodeData<any>[];
    edges: EdgeData<any>[];
};
export {};
