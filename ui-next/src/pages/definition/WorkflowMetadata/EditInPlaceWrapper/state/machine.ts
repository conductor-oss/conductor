import { createMachine } from "xstate";
import * as actions from "./actions";
import {
  EditInPlaceMachineContext,
  EditInPlaceEventTypes,
  EditInPlaceMachineEvents,
} from "./types";
import * as guards from "./guards";

export const editInPlaceMachine = createMachine<
  EditInPlaceMachineContext,
  EditInPlaceMachineEvents
>(
  {
    id: "editInPlaceMachine",
    initial: "notEditing",
    predictableActionArguments: true,
    context: {
      value: "",
      fieldName: "",
    },
    on: {
      [EditInPlaceEventTypes.DISABLE_EDITING]: {
        target: "notEditing",
      },
    },
    states: {
      editing: {
        on: {
          [EditInPlaceEventTypes.CHANGE_VALUE]: {
            cond: "hasValidChars",
            actions: ["persistChanges", "debounceSyncWithParent"],
          },
          [EditInPlaceEventTypes.TOGGLE_EDITING]: {
            target: "notEditing",
          },
        },
      },
      notEditing: {
        on: {
          [EditInPlaceEventTypes.TOGGLE_EDITING]: {
            target: "editing",
          },
          [EditInPlaceEventTypes.VALUE_UPDATED]: {
            actions: ["persistChanges"],
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    guards: guards as any,
  },
);
