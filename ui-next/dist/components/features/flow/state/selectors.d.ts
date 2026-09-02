import { State } from "xstate";
import { FlowContext, FlowMachineStates } from "./types";
export declare const selectSelectedNode: (state: State<FlowContext>) => import("reaflow").NodeData<any> | undefined;
export declare const selectSelectedEdge: (state: State<FlowContext>) => import("reaflow").EdgeData<any> | undefined;
export declare const selectNodes: (state: State<FlowContext>) => import("reaflow").NodeData<any>[];
export declare const selectEdges: (state: State<FlowContext>) => import("reaflow").EdgeData<any>[];
export declare const selectIsOpenedEdge: (state: State<FlowContext>) => state is State<FlowContext, import("xstate").EventObject, any, {
    value: any;
    context: FlowContext;
}, import("xstate").TypegenDisabled> & {
    value: FlowMachineStates[];
};
export declare const selectOpenedNode: (state: State<FlowContext>) => import("reaflow").NodeData<any> | undefined;
export declare const selectWorkflowDefinition: (state: State<FlowContext>) => Partial<import("../../../..").WorkflowDef>;
export declare const selectWorkflowName: (state: State<FlowContext>) => string | undefined;
