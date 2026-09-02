export interface MetadataFieldMachineContext {
    value: string;
    fieldName: string;
    someKey?: string;
}
export declare enum MetadataFieldMachineEventTypes {
    CHANGE_VALUE = "CHANGE_VALUE",
    VALUE_UPDATED = "VALUE_UPDATED"
}
export type ChangeValueEvent = {
    type: MetadataFieldMachineEventTypes.CHANGE_VALUE;
    value: string;
};
export type ValueUpdatedEvent = {
    type: MetadataFieldMachineEventTypes.VALUE_UPDATED;
    value: string;
};
export type MetdataFieldMachineEvents = ChangeValueEvent | ValueUpdatedEvent;
