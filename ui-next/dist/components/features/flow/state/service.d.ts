import { FlowContext } from "./types";
import { EdgeData, NodeData } from "reaflow";
export declare const updateWorkflowDefinitionService: ({ selectedNodeIdx, authHeaders, collapseWorkflowList }: FlowContext, { workflow, showPorts, workflowExecutionStatus }: any) => Promise<{
    nodes: NodeData[];
    edges: EdgeData[];
    currentWf: any;
} | {
    severity: "error";
    text: string;
}>;
