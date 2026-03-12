import { createMachine } from "xstate";
import * as actions from "./actions";
import {
  MetadataFieldMachineContext,
  MetadataFieldMachineEventTypes,
  MetdataFieldMachineEvents,
} from "./types";

export const metadataFieldMachine = createMachine<
  MetadataFieldMachineContext,
  MetdataFieldMachineEvents
>(
  {
    id: "workflowMetadataField",
    initial: "focused",
    predictableActionArguments: true,
    context: {
      value: "",
      fieldName: "",
      someKey: "",
    },
    on: {
      [MetadataFieldMachineEventTypes.VALUE_UPDATED]: {
        actions: ["persistChanges", "addSomeKey"],
      },
    },
    states: {
      focused: {
        on: {
          [MetadataFieldMachineEventTypes.CHANGE_VALUE]: {
            actions: ["persistChanges", "debounceSyncWithParent"],
          },
        },
      },
    },
  },
  {
    actions: actions as any,
  },
);
