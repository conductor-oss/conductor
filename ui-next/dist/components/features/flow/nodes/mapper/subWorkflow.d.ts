import { SubWorkflowTaskDef, Crumb } from "types";
import { NodeData } from "reaflow";
import { SubWorkflowFunction } from "./types";
export declare const processSubWorkflow: (subWorkflowTask: SubWorkflowTaskDef, crumbs: Crumb[], taskWalkerFn: any, subWorkflowFetcher: SubWorkflowFunction) => Promise<{
    nodes: NodeData<import("./types").NodeTaskData<import("types").CommonTaskDef>>[];
    edges: any;
    crumbs: Crumb[];
} | {
    nodes: NodeData<import("./types").NodeTaskData<import("types").CommonTaskDef>>[];
    edges: never[];
}>;
