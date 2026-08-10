import { ElkRoot, NodeData } from "reaflow";

export type SizeProps = { width: number; height: number };
export type PositionProps = { x: number; y: number };

export interface PanAndZoomMachineContext {
  zoom: number;
  canvasSize: SizeProps;
  viewportSize: SizeProps;
  position: PositionProps;
  layout?: ElkRoot;
  selectedNode?: NodeData;
  lastViewportOffsetWidth?: number;
  lastViewportOffsetHeight?: number;
  isFullScreen: boolean;
  draggingUpdatedPosition: boolean;
  notifiedEventType: string;
}

export enum PanAndZoomStates {
  INIT = "init",
  CHECK_IF_READY = "checkIfReady",
  IDLE = "idle",
  PAN_ENABLED = "panEnabled",
  PAN_DISABLED = "panDisabled",
  DRAGGING_TASK = "draggingTask",
  NOT_DRAGGING_TASK = "notDraggingTask",
  PAN = "pan",
  SEARCH_FIELD = "searchField",
  SEARCH_FIELD_VISIBLE = "searchFieldVisible",
  SEARCH_FIELD_HIDDEN = "searchFieldHidden",
}

export enum PanAndZoomEventTypes {
  RESET_ZOOM_POSITION_EVT = "RESET_ZOOM_POSITION",
  SET_LAYOUT_EVT = "SET_LAYOUT",
  SET_ZOOM_EVT = "SET_ZOOM",
  SET_POSITION_EVT = "SET_POSITION",
  CENTER_ON_SELECTED_TASK = "CENTER_ON_SELECTED_TASK",
  SELECT_NODE_EVENT_EVT = "SELECT_NODE_EVT",
  SET_READ_ONLY_EVT = "SET_READ_ONLY_EVT",
  SET_INITIAL_VIEWPORT_OFFSET = "SET_INITIAL_VIEWPORT_OFFSET",
  SET_FULL_SCREEN_EVT = "SET_FULL_SCREEN_EVT",
  SET_FIT_SCREEN_EVT = "SET_FIT_SCREEN_EVT",
  HANDLE_ZOOM_EVT = "HANDLE_ZOOM_EVT",
  TOGGLE_PAN_EVT = "TOGGLE_PAN_EVT",
  SET_ZOOM_TO_POSITION_EVT = "SET_ZOOM_TO_POSITION_EVT",
  INCREMENT_POSITION_Y_EVT = "INCREMENT_POSITION_Y_EVT",
  DECREMENT_POSITION_Y_EVT = "DECREMENT_POSITION_Y_EVT",
  INCREMENT_POSITION_X_EVT = "INCREMENT_POSITION_X_EVT",
  DECREMENT_POSITION_X_EVT = "DECREMENT_POSITION_X_EVT",
  DRAG_EVENT_EVT = "DRAG_EVENT_EVT",

  DRAG_TASK_BEGIN = "DRAG_TASK_BEGIN",
  DRAG_TASK_END = "DRAG_TASK_END",
  TOGGLE_SEARCH_EVT = "TOGGLE_SEARCH_EVT",
  SELECT_SEARCH_RESULT = "SELECT_SEARCH_RESULT",
  SET_NOTIFIED_EVENT_TYPE = "SET_NOTIFIED_EVENT_TYPE",
  TOGGLE_SHOW_DESCRIPTION_EVT = "TOGGLE_SHOW_DESCRIPTION_EVT",
}

export type ResetZoomPositionEvent = {
  type: PanAndZoomEventTypes.RESET_ZOOM_POSITION_EVT;
  viewportOffsetWidth: number;
  viewportOffsetHeight: number;
};

export type SetLayoutEvent = {
  type: PanAndZoomEventTypes.SET_LAYOUT_EVT;
  layout: ElkRoot;
};

export type SetZoomEvent = {
  type: PanAndZoomEventTypes.SET_ZOOM_EVT;
  zoom: number;
};

export type SetZoomToPositionEvent = {
  type: PanAndZoomEventTypes.SET_ZOOM_TO_POSITION_EVT;
  zoom: number;
  position: PositionProps;
};

export type SetPositionEvent = {
  type: PanAndZoomEventTypes.SET_POSITION_EVT;
  position: PositionProps;
};

export type DragEvent = {
  type: PanAndZoomEventTypes.DRAG_EVENT_EVT;
  position: PositionProps;
  clientMousePosition: PositionProps;
};

export type CenterOnSelectedTaskEvent = {
  type: PanAndZoomEventTypes.CENTER_ON_SELECTED_TASK;
  viewportOffsetWidth: number;
  viewportOffsetHeight: number;
};

export type SelectNodeEvent = {
  type: PanAndZoomEventTypes.SELECT_NODE_EVENT_EVT;
  node: NodeData;
};

export type SetInitialViewportOffsetEvent = {
  type: PanAndZoomEventTypes.SET_INITIAL_VIEWPORT_OFFSET;
  viewportOffsetWidth: number;
  viewportOffsetHeight: number;
};

export type SetFullScreenEvent = {
  type: PanAndZoomEventTypes.SET_FULL_SCREEN_EVT;
  fullScreen: boolean;
  viewportOffsetWidth: number;
};

export type SetFitScreenEvent = {
  type: PanAndZoomEventTypes.SET_FIT_SCREEN_EVT;
  viewportOffsetWidth: number;
  viewportOffsetHeight: number;
};

export type HandleZoomEvent = {
  type: PanAndZoomEventTypes.HANDLE_ZOOM_EVT;
  isZoomOut: boolean;
};

export type TogglePanEvent = {
  type: PanAndZoomEventTypes.TOGGLE_PAN_EVT;
};

export type EnableTaskDraggingEvent = {
  type: PanAndZoomEventTypes.DRAG_TASK_BEGIN;
};

export type DisableTaskDraggingEvent = {
  type: PanAndZoomEventTypes.DRAG_TASK_END;
};

export type ToggleSearchEvent = {
  type: PanAndZoomEventTypes.TOGGLE_SEARCH_EVT;
};

export type SelectSearchResultEvent = {
  type: PanAndZoomEventTypes.SELECT_SEARCH_RESULT;
  viewportOffsetWidth: number;
  viewportOffsetHeight: number;
};

export type SetNotifiedEventTypeEvent = {
  type: PanAndZoomEventTypes.SET_NOTIFIED_EVENT_TYPE;
  eventType: string;
};
export type ToggleShowDescriptionEvent = {
  type: PanAndZoomEventTypes.TOGGLE_SHOW_DESCRIPTION_EVT;
};
export type PanAndZoomEvents =
  | ResetZoomPositionEvent
  | SetLayoutEvent
  | SetFullScreenEvent
  | SetFitScreenEvent
  | SelectNodeEvent
  | SetInitialViewportOffsetEvent
  | CenterOnSelectedTaskEvent
  | HandleZoomEvent
  | SetZoomEvent
  | TogglePanEvent
  | SetZoomToPositionEvent
  | SetPositionEvent
  | DragEvent
  | EnableTaskDraggingEvent
  | DisableTaskDraggingEvent
  | ToggleSearchEvent
  | SelectSearchResultEvent
  | SetNotifiedEventTypeEvent
  | ToggleShowDescriptionEvent;
