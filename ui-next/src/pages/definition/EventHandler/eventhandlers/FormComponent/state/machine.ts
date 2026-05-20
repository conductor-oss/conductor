import {
  EventFormMachineContext,
  EventFormMachineStates,
  EventFormMachineTypes,
  FormHandlerEvents,
} from "./types";

import { createMachine } from "xstate";
import * as actions from "./actions";

export const eventFormMachine = createMachine<
  EventFormMachineContext,
  FormHandlerEvents
>(
  {
    id: "eventFormMachine",
    predictableActionArguments: true,
    context: {
      eventAsJson: {},
      originalSource: {},
    },
    on: {
      [EventFormMachineTypes.SAVE_EVT]: {
        target: EventFormMachineStates.EXIT,
      },
      [EventFormMachineTypes.TOGGLE_FORM_EDITOR_EVT]: {
        target: EventFormMachineStates.EXIT,
      },
      [EventFormMachineTypes.RESET_CONFIRM_EVT]: {
        actions: "resetForm",
      },
      [EventFormMachineTypes.CONFIRM_NEW_EVENT]: {
        actions: "resetFormToNewDefinition",
      },
    },
    initial: EventFormMachineStates.IDLE,
    states: {
      [EventFormMachineStates.IDLE]: {
        on: {
          [EventFormMachineTypes.INPUT_CHANGE]: {
            actions: ["handleInputChange"],
          },
          [EventFormMachineTypes.ADD_ACTION]: {
            actions: ["persistNewAction"],
          },
          [EventFormMachineTypes.DELETE_ACTION]: {
            actions: ["removeAction"],
          },
          [EventFormMachineTypes.EDIT_ACTION]: {
            actions: ["editAction"],
          },
        },
      },
      [EventFormMachineStates.EXIT]: {
        type: "final",
        data: (context, event) => {
          return { eventAsJson: context.eventAsJson, reason: event.type };
        },
      },
    },
  },
  { actions: actions as any },
);
