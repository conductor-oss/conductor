export interface MetadataFieldMachineContext {
  value: string;
  fieldName: string;
  someKey?: string;
}

export enum MetadataFieldMachineEventTypes {
  // TOGGLE_EDITING = "TOGGLE_EDITING",
  CHANGE_VALUE = "CHANGE_VALUE",
  VALUE_UPDATED = "VALUE_UPDATED",
  // DISABLE_EDITING = "DISABLE_EDITING",
}

export type ChangeValueEvent = {
  type: MetadataFieldMachineEventTypes.CHANGE_VALUE;
  value: string;
};

export type ValueUpdatedEvent = {
  type: MetadataFieldMachineEventTypes.VALUE_UPDATED;
  value: string;
};

// export type DisableEditingEvent = {
//   type: EditInPlaceEventTypes.DISABLE_EDITING;
// };

export type MetdataFieldMachineEvents = ChangeValueEvent | ValueUpdatedEvent;
