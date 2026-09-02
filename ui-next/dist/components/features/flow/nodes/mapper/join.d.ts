import { NodeData, EdgeData } from "reaflow";
import { DiagramPort } from "./ports";
import { JoinTaskDef, SwitchTaskDef, Crumb, CommonTaskDef } from "types";
import { NodesAndEdges, NodeTaskData } from "./types";
type JoinOnDirectPathsEdgesDiagramPorts = {
    joinOn: EdgeData[];
    directPaths: EdgeData[];
    northPorts: DiagramPort[];
};
export declare const forkLastTasks: (tasks: CommonTaskDef[], taskWalkerFn: any) => Promise<CommonTaskDef[]>;
export declare const forkLastTaskReferences: (tasks: CommonTaskDef[], taskWalkerFn: any) => Promise<string[]>;
export declare const isTaskNotInJoinOn: (joinOn: string[] | undefined, currentTaskRef: string) => boolean;
export declare const joinEdgeForSwitch: (switchTask: SwitchTaskDef, index: number, joinTask: JoinTaskDef) => JoinOnDirectPathsEdgesDiagramPorts;
export declare const createJoinNode: (joinTask: JoinTaskDef, crumbs: Crumb[], previousTask?: CommonTaskDef) => {
    readonly id: string;
    readonly text: string;
    readonly ports: readonly [DiagramPort];
    readonly data: any;
    readonly width: number;
    readonly height: number;
};
export declare const joinTasksToNodesEdges: (joinTask: JoinTaskDef, previousTask: CommonTaskDef, crumbs: Crumb[], currentNodes: NodeData<NodeTaskData>[]) => NodesAndEdges;
export {};
