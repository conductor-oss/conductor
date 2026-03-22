export interface EditInPlaceMachineContext {
  value: string;
  fieldName: string;
  allowedCharsRegEx?: string;
}

export enum EditInPlaceEventTypes {
  TOGGLE_EDITING = "TOGGLE_EDITING",
  CHANGE_VALUE = "CHANGE_VALUE",
  VALUE_UPDATED = "VALUE_UPDATED",
  DISABLE_EDITING = "DISABLE_EDITING",
}

export type ToggleEditingEvent = {
  type: EditInPlaceEventTypes.TOGGLE_EDITING;
};

export type ChangeValueEvent = {
  type: EditInPlaceEventTypes.CHANGE_VALUE;
  value: string;
};

export type ValueUpdatedEvent = {
  type: EditInPlaceEventTypes.VALUE_UPDATED;
  value: string;
};

export type DisableEditingEvent = {
  type: EditInPlaceEventTypes.DISABLE_EDITING;
};

export type EditInPlaceMachineEvents =
  | ToggleEditingEvent
  | ChangeValueEvent
  | DisableEditingEvent
  | ValueUpdatedEvent;
