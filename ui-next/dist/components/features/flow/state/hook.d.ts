import { ActorRef } from "xstate";
import { ElkRoot, EdgeData, NodeData } from "reaflow";
import { DraggedNodeData } from "./types";
import { WorkflowDef } from "types/WorkflowDef";
export declare const useFlowMachine: (flowActor: ActorRef<any, any>) => readonly [{
    readonly toggleEdgeMenu: (edge: EdgeData) => void;
    readonly selectNode: (node: NodeData) => void;
    readonly selectEdge: ({ edge }: {
        edge: EdgeData;
    }) => void;
    readonly toggleNodeMenu: (node: NodeData) => void;
    readonly updateWorkflowDefinition: (workflow: WorkflowDef) => void;
    readonly draggingStarts: (nodeData: DraggedNodeData) => void;
    readonly draggingNodeEnds: (fromData: DraggedNodeData, toData: DraggedNodeData) => void;
    readonly handleSetLayout: (layout: ElkRoot) => void;
    readonly selectTaskWithTaskRef: (node: NodeData, exactTaskRef: string) => void;
}, {
    readonly selectedNode: NodeData<any> | undefined;
    readonly selectedEdge: EdgeData<any> | undefined;
    readonly nodes: NodeData<any>[];
    readonly edges: EdgeData<any>[];
    readonly openedEdge: boolean;
    readonly openedNode: NodeData<any> | undefined;
    readonly isInconsistent: any;
    readonly workflowDefinition: Partial<WorkflowDef>;
    readonly panAndZoomActor: any;
    readonly isShowDescription: any;
}];
