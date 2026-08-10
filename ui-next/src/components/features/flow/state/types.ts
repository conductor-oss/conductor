import { DoneInvokeEvent } from "xstate";
import { NodeData, EdgeData, ElkRoot } from "reaflow";
import {
  AuthHeaders,
  CommonTaskDef,
  Crumb,
  WorkflowExecutionStatus,
  WorkflowDef,
} from "types";
import { OperationContextData } from "../components/RichAddTaskMenu/state/types";

export enum FlowActionTypes {
  SELECT_NODE_EVT = "SELECT_NODE_EVT",
  SELECT_NODE_INTERNAL_EVT = "selectNodeInternal",
  PERFORM_OPERATION_EVT = "performOperation",
  OPEN_EDGE_MENU_EVT = "openEdgeMenu",
  CLOSE_EDGE_MENU_EVT = "closeEdgeMenu",
  OPEN_NODE_MENU_EVT = "openNodeMenu",
  UPDATE_WF_DEFINITION_EVT = "updateWfDefinition",
  SET_READ_ONLY_EVT = "SET_READ_ONLY_EVT",
  PERFORM_OPERATION_DEBOUNCE = "performOperationDebounce",
  CANCEL_DEBOUNCE_PERFORM_OPERATION = "cancelDebouncePerformOperation",
  UPDATE_WF_METADATA = "updateWorkflowMetadata",
  RESET_NODE_SELECTION = "resetNodeSelection",
  SET_LAYOUT = "SET_LAYOUT",
  SELECT_EDGE_EVT = "SELECT_EDGE_EVT",
  RESET_ZOOM_POSITION = "RESET_ZOOM_POSITION",
  CENTER_ON_SELECTED_TASK = "CENTER_ON_SELECTED_TASK",
  FLOW_FINISHED_RENDERING = "FLOW_FINISHED_RENDERING",
  SELECT_TASK_WITH_TASK_REF = "SELECT_TASK_WITH_TASK_REF",

  DRAG_TASK_BEGIN = "DRAG_TASK_BEGIN",
  DRAG_TASK_END = "DRAG_TASK_END",
  MOVE_TASK_EVT = "MOVE_TASK_EVT",
  UPDATE_COLLAPSE_WORKFLOW_LIST = "UPDATE_COLLAPSE_WORKFLOW_LIST",
  TOGGLE_SHOW_DESCRIPTION = "TOGGLE_SHOW_DESCRIPTION",
}

export type DraggedNodeData = {
  task: CommonTaskDef;
  crumbs: Crumb[];
  height?: number;
  width?: number;
};

export type DropPosition = { position: "ABOVE" | "BELOW" };

export interface FlowContext {
  currentWf: Partial<WorkflowDef>;
  selectedNodeIdx?: number;
  nodes: NodeData[];
  edges: EdgeData[];
  menuOperationContext?: OperationContextData;
  openedNode?: NodeData;
  layout?: ElkRoot;
  authHeaders?: AuthHeaders;
  selectedEdge?: EdgeData;
  draggedNodeData?: DraggedNodeData;
  collapseWorkflowList?: string[];
}
export type SelectNodeEvent = {
  type: FlowActionTypes.SELECT_NODE_EVT;
  node: NodeData;
};

export type SelectTaskWithTaskRefEvent = {
  type: FlowActionTypes.SELECT_TASK_WITH_TASK_REF;
  node: NodeData;
  exactTaskRef: string;
};

export type SelectEdgeEvent = {
  type: FlowActionTypes.SELECT_EDGE_EVT;
  node: NodeData;
  edge: EdgeData;
};

export type ResetNodeSelectionEvent = {
  type: FlowActionTypes.RESET_NODE_SELECTION;
};

export type PerformOperationEvent = {
  type: FlowActionTypes.PERFORM_OPERATION_EVT;
  operation: any;
  task: any;
  crumbs: string[];
};

export type OpenEdgeMenuEvent = {
  type: FlowActionTypes.OPEN_EDGE_MENU_EVT;
  edge?: OperationContextData;
};

export type CloseEdgeMenuEvent = {
  type: FlowActionTypes.CLOSE_EDGE_MENU_EVT;
};

export type ToggleNodeMenuEvent = {
  type: FlowActionTypes.OPEN_NODE_MENU_EVT;
  node?: NodeData;
};

export type UpdateWfDefinitionEvent = {
  type: FlowActionTypes.UPDATE_WF_DEFINITION_EVT;
  workflow: any;
  cleanNodeSelection: boolean;
  workflowExecutionStatus?: WorkflowExecutionStatus;
  showPorts?: boolean;
};

export type UpdateCollapseWorkflowListEvent = {
  type: FlowActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST;
  workflowName: string;
};

export type SetLayoutEvent = {
  type: FlowActionTypes.SET_LAYOUT;
  layout: ElkRoot;
};

export type ResetZoomPositionEvent = {
  type: FlowActionTypes.RESET_ZOOM_POSITION;
};

export type SetCenterPositionEvent = {
  type: FlowActionTypes.CENTER_ON_SELECTED_TASK;
};

export type StartDraggingNodeEvent = {
  type: FlowActionTypes.DRAG_TASK_BEGIN;
  nodeData: DraggedNodeData;
};

export type StoppedDraggingNodeEvent = {
  type: FlowActionTypes.DRAG_TASK_END;
  fromData?: DraggedNodeData;
  toData?: DraggedNodeData & DropPosition;
};

export type ToggleShowDescriptionEvent = {
  type: FlowActionTypes.TOGGLE_SHOW_DESCRIPTION;
};

export type FlowEvents =
  | SelectNodeEvent
  | SelectEdgeEvent
  | SelectTaskWithTaskRefEvent
  | PerformOperationEvent
  | OpenEdgeMenuEvent
  | CloseEdgeMenuEvent
  | SetLayoutEvent
  | ToggleNodeMenuEvent
  | UpdateWfDefinitionEvent
  | ResetNodeSelectionEvent
  | ResetZoomPositionEvent
  | StoppedDraggingNodeEvent
  | StartDraggingNodeEvent
  | SetCenterPositionEvent
  | UpdateCollapseWorkflowListEvent
  | DoneInvokeEvent<any>
  | ToggleShowDescriptionEvent;

export enum FlowMachineStates {
  INIT = "init",
  DIAGRAM_RENDERER = "diagramRenderer",
  DIAGRAM_RENDERER_INIT = "diagramRenderer_init",
  DIAGRAM_RENDERER_BEGIN_DRAGGING = "diagramRenderer_beginDragging",
  DIAGRAM_RENDERER_MENU_CLOSED = "diagramRenderer_menuClosed",
  DIAGRAM_RENDERER_MENU_OPENED = "diagramRenderer_menuOpened",
}

export type FlowStates =
  | {
      value: "idle";
      context: FlowContext;
    }
  | {
      value: "updatingWfDefinition";
      context: FlowContext;
    }
  | {
      value: "init";
      context: FlowContext;
    }
  | {
      value: "notifyParent";
      context: FlowContext;
    };
