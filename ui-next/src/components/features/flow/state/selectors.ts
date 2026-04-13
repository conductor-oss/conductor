import { State } from "xstate";
import { FlowContext, FlowMachineStates } from "./types";

export const selectSelectedNode = (state: State<FlowContext>) =>
  state.context.selectedNodeIdx !== undefined
    ? state.context.nodes[state.context.selectedNodeIdx]
    : undefined;
export const selectSelectedEdge = (state: State<FlowContext>) =>
  state.context.selectedEdge;
export const selectNodes = (state: State<FlowContext>) => state.context.nodes;
export const selectEdges = (state: State<FlowContext>) => state.context.edges;
export const selectIsOpenedEdge = (state: State<FlowContext>) =>
  state.matches([
    FlowMachineStates.INIT,
    FlowMachineStates.DIAGRAM_RENDERER,
    FlowMachineStates.DIAGRAM_RENDERER_INIT,
    FlowMachineStates.DIAGRAM_RENDERER_MENU_OPENED,
  ]);
export const selectOpenedNode = (state: State<FlowContext>) =>
  state.context.openedNode;
export const selectWorkflowDefinition = (state: State<FlowContext>) =>
  state.context.currentWf;
export const selectWorkflowName = (state: State<FlowContext>) =>
  state.context.currentWf.name;
