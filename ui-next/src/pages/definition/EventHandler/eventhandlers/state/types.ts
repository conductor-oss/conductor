import { DoneInvokeEvent } from "xstate";

export enum SaveEventHandlerMachineEventTypes {
  SAVE_EVT = "SAVE_EVT",
  CONFIRM_SAVE_EVT = "CONFIRM_SAVE",
  CANCEL_SAVE_EVT = "CANCEL_SAVE",
  EDIT_EVT = "EDIT_EVT",
  EDIT_DEBOUNCE_EVT = "CANCEL_DEBOUNCE",
  RESET_EVT = "RESET_EVT",
  RESET_CONFIRM_EVT = "RESET_CONFIRM_EVT",
  DELETE_EVT = "DELETE_EVT",
  DELETE_CONFIRM_EVT = "DELETE_CONFIRM_EVT",
  UPDATE_EVENTHANDLER_EVT = "UPDATE_EVENTHANDLER_EVT",
  UPDATE_ORIGINAL_SOURCE_EVT = "UPDATE_ORIGINAL_SOURCE_EVT",
  NEW_EVENT_HANDLER_REQUEST = "NEW_EVENT_HANDLER_REQUEST",
  CONFIRM_NEW_EVENT = "CONFIRM_NEW_EVENT",
  BACK_TO_IDLE = "BACK_TO_IDLE",
  SHOW_ERROR_MESSAGE = "SHOW_ERROR_MESSAGE",
  CLEAR_ERROR_MESSAGE = "CLEAR_ERROR_MESSAGE",
  TOGGLE_FORM_EDITOR_EVT = "TOGGLE_FORM_EDITOR_EVT",
  SAVED_SUCCESSFUL = "SAVED_SUCCESSFUL",
  SAVED_CANCELLED = "SAVED_CANCELLED",
}

export type ResetEvent = {
  type: SaveEventHandlerMachineEventTypes.RESET_EVT;
};

export type ResetConfirmEvent = {
  type: SaveEventHandlerMachineEventTypes.RESET_CONFIRM_EVT;
};

export type DeleteEvent = {
  type: SaveEventHandlerMachineEventTypes.DELETE_EVT;
};

export type DeleteConfirmEvent = {
  type: SaveEventHandlerMachineEventTypes.DELETE_CONFIRM_EVT;
};

export type SaveEvent = {
  type: SaveEventHandlerMachineEventTypes.SAVE_EVT;
};

export type ConfirmSaveEvent = {
  type: SaveEventHandlerMachineEventTypes.CONFIRM_SAVE_EVT;
};

export type CancelSaveEvent = {
  type: SaveEventHandlerMachineEventTypes.CANCEL_SAVE_EVT;
};

export type EditEvent = {
  type: SaveEventHandlerMachineEventTypes.EDIT_EVT;
  changes: string;
};

export type EditDebounceEvent = {
  type: SaveEventHandlerMachineEventTypes.EDIT_DEBOUNCE_EVT;
  changes: string;
};

export type UpdateEventHandlerEvent = {
  type: SaveEventHandlerMachineEventTypes.UPDATE_EVENTHANDLER_EVT;
  data: any;
};

export type UpdateOriginalSourceEvent = {
  type: SaveEventHandlerMachineEventTypes.UPDATE_ORIGINAL_SOURCE_EVT;
  data: any;
};

export type NewEventHandlerRequestEvent = {
  type: SaveEventHandlerMachineEventTypes.NEW_EVENT_HANDLER_REQUEST;
};

export type ConfirmNewEventEvent = {
  type: SaveEventHandlerMachineEventTypes.CONFIRM_NEW_EVENT;
};

export type BackToIdleEvent = {
  type: SaveEventHandlerMachineEventTypes.BACK_TO_IDLE;
};

export type ShowErrorMessageEvent = {
  type: SaveEventHandlerMachineEventTypes.SHOW_ERROR_MESSAGE;
  data: Record<string, any>;
};

export type ClearErrorMessageEvent = {
  type: SaveEventHandlerMachineEventTypes.CLEAR_ERROR_MESSAGE;
};

export type ToggleFormModeEvent = {
  type: SaveEventHandlerMachineEventTypes.TOGGLE_FORM_EDITOR_EVT;
  isEditorMode: boolean;
};

export type SavedSuccessfulEvent = {
  type: SaveEventHandlerMachineEventTypes.SAVED_SUCCESSFUL;
};

export type SavedCancelledEvent = {
  type: SaveEventHandlerMachineEventTypes.SAVED_CANCELLED;
};

export type SaveEventHandlerEvents =
  | SaveEvent
  | ConfirmSaveEvent
  | CancelSaveEvent
  | EditEvent
  | EditDebounceEvent
  | DoneInvokeEvent<string>
  | ResetEvent
  | ResetConfirmEvent
  | DeleteEvent
  | DeleteConfirmEvent
  | UpdateEventHandlerEvent
  | UpdateOriginalSourceEvent
  | NewEventHandlerRequestEvent
  | ConfirmNewEventEvent
  | BackToIdleEvent
  | ShowErrorMessageEvent
  | ClearErrorMessageEvent
  | ToggleFormModeEvent
  | SavedSuccessfulEvent
  | SavedCancelledEvent;

export interface SaveEventHandlerMachineContext {
  editorChanges: string;
  originalSource: string;
  isNewEventHandler: boolean;
  eventHandlerName: string;
  authHeaders: Record<string, string | undefined>;
  message: string;
  couldNotParseJson: boolean;
}

export enum SaveEventHandlerStates {
  IDLE = "idle",
  EDITOR = "editor",
  FORM = "form",
  FETCH_EVENT_HANDLER_DEFINITION = "fetchEventHandlerDefinition",
  CONFIRM_SAVE = "confirmSave",
  CREATE_EVENT_HANDLER = "createEventHandler",
  UPDATE_EVENT_HANDLER = "updateEventHandler",
  SAVED_SUCCESSFULLY = "savedSuccessfully",
  SAVED_CANCELLED = "savedCancelled",
  CONFIRM_RESET = "confirmReset",
  CONFIRM_DELETE = "confirmDelete",
  CONFIRM_NEW = "confirmNew",
  DELETE_EVENT_HANDLER = "deleteEventHandler",
}
