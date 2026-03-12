import {
  assign,
  DoneEvent,
  DoneInvokeEvent,
  forwardTo,
  sendParent,
} from "xstate";
import { applyNodeSelectionHelpr } from "./helpers";
import _nth from "lodash/nth";
import { FLOW_FINISHED_RENDERING } from "pages/definition/state/constants";
import {
  FlowActionTypes,
  FlowContext,
  ResetNodeSelectionEvent,
  SelectEdgeEvent,
  SelectNodeEvent,
  OpenEdgeMenuEvent,
  ToggleNodeMenuEvent,
  UpdateWfDefinitionEvent,
  StartDraggingNodeEvent,
  StoppedDraggingNodeEvent,
  UpdateCollapseWorkflowListEvent,
  SelectTaskWithTaskRefEvent,
} from "./types";
import {
  END_TASK_FAKE_TASK_REFERENCE_NAME,
  START_TASK_FAKE_TASK_REFERENCE_NAME,
} from "../nodes";
import { ErrorInspectorEventTypes } from "pages/definition/errorInspector/state";
import { DefinitionMachineEventTypes } from "pages/definition/state/types";

export const spreadData = assign<FlowContext, DoneInvokeEvent<any>>(
  (__, { data }) => {
    return data;
  },
);

export const selectNode = assign<FlowContext, SelectNodeEvent>(
  ({ nodes }, { node: { id: selectNodeId, data } }) => {
    if (
      selectNodeId === START_TASK_FAKE_TASK_REFERENCE_NAME ||
      selectNodeId === END_TASK_FAKE_TASK_REFERENCE_NAME ||
      data?.withinExpandedSubWorkflow === true
    )
      return {};
    const newSelectedIndex = nodes.findIndex(({ id }) => id === selectNodeId);

    return {
      selectedNodeIdx: newSelectedIndex,
      nodes: applyNodeSelectionHelpr(nodes, newSelectedIndex),
    };
  },
);

export const resetNodeSelection = assign<FlowContext, ResetNodeSelectionEvent>({
  // Checking current editing task
  // don't reset node in case error and let user stay at form to fix the error
  selectedNodeIdx: (flowContext, __) => flowContext?.selectedNodeIdx,
  nodes: ({ nodes }) => applyNodeSelectionHelpr(nodes, -1), // non existant index
});

export const updateCollapseWorkflowList = assign<
  FlowContext,
  UpdateCollapseWorkflowListEvent
>((ctx: any, event) => {
  if (ctx && ctx.collapseWorkflowList) {
    if (ctx.collapseWorkflowList.includes(event.workflowName)) {
      const newCollapseWorkflowList = ctx.collapseWorkflowList.filter(
        (item: string) => item !== event.workflowName,
      );
      return {
        collapseWorkflowList: newCollapseWorkflowList,
      };
    } else {
      const newCollapseWorkflowList = ctx.collapseWorkflowList.concat(
        event.workflowName,
      );
      return {
        collapseWorkflowList: newCollapseWorkflowList,
      };
    }
  }
  return { collapseWorkflowList: [event?.workflowName] };
});

export const toggleEdgeMenu = assign<FlowContext, OpenEdgeMenuEvent>({
  menuOperationContext: ({ menuOperationContext }, { edge }) => {
    const r = menuOperationContext?.id === edge?.id ? undefined : edge;
    return r;
  },
});

export const toggleNodeMenu = assign<FlowContext, ToggleNodeMenuEvent>({
  openedNode: ({ openedNode }, { node }) =>
    openedNode?.id === node?.id ? undefined : node,
});

export const maybeCleanSelection = assign<FlowContext, UpdateWfDefinitionEvent>(
  {
    selectedNodeIdx: ({ selectedNodeIdx }, { cleanNodeSelection }) => {
      return cleanNodeSelection ? undefined : selectedNodeIdx;
    },
  },
);

export const notifyFinishedRender = sendParent((ctx: FlowContext) => {
  return {
    type: FLOW_FINISHED_RENDERING,
    nodes: ctx.nodes,
    node: _nth(ctx.nodes, ctx.selectedNodeIdx),
    collapseWorkflowList: ctx.collapseWorkflowList,
  };
});

export const notifyErrorToParent = sendParent<FlowContext, DoneEvent>(
  (ctx, { data }) => {
    return {
      type: ErrorInspectorEventTypes.REPORT_FLOW_ERROR,
      ...data,
    };
  },
);

export const notifySelectionToParent = sendParent<
  FlowContext,
  SelectNodeEvent | SelectEdgeEvent
>(({ nodes, selectedNodeIdx }, event) => {
  if (event.type === FlowActionTypes.SELECT_NODE_EVT) {
    return {
      type: FlowActionTypes.SELECT_NODE_EVT,
      node: _nth(nodes, selectedNodeIdx),
    };
  }

  if (event.type === FlowActionTypes.SELECT_EDGE_EVT) {
    return {
      ...event,
    };
  }

  return event;
});

export const notifyTaskSelectionToParent = sendParent<
  FlowContext,
  SelectTaskWithTaskRefEvent
>((_context, event) => {
  return event;
});

export const forwardToZoom = forwardTo("panAndZoomMachine");

export const selectEdge = assign<FlowContext, SelectEdgeEvent>(
  (_context, event) => {
    return {
      selectedEdge: event.edge,
    };
  },
);

export const cleanMenuOperationContext = assign<FlowContext>(() => ({
  menuOperationContext: undefined,
}));

export const persistDraggedTask = assign<FlowContext, StartDraggingNodeEvent>({
  draggedNodeData: (__context, { nodeData }) => nodeData,
});

export const sendMoveTask = sendParent<FlowContext, StoppedDraggingNodeEvent>(
  (__context, event) => {
    return {
      type: FlowActionTypes.MOVE_TASK_EVT,
      sourceTask: event.fromData?.task,
      sourceTaskCrumbs: event.fromData?.crumbs,
      targetTask: event.toData?.task,
      targetLocationCrumbs: event.toData?.crumbs,
      position: event.toData?.position,
    };
  },
);

export const clearDraggedElement = assign<FlowContext>((__context) => ({
  draggedNodeData: undefined,
}));

export const sendToDefinitionMachine = sendParent(() => {
  return {
    type: DefinitionMachineEventTypes.COLLAPSE_SIDEBAR_AND_RIGHT_PANEL,
    onSelectNode: false,
  };
});
