import { createMachine } from "xstate";
import {
  TaskDefinitionFormContext,
  TaskDefinitionFormEventType,
  TaskDefinitionFormMachineEvent,
} from "pages/definition/task/form/state/types";
import * as customActions from "./actions";
import * as guards from "./guards";
import * as services from "./services";
import { TaskDefinitionDto } from "types";
import { TASK_FORM_MACHINE_ID } from "pages/definition/task/state/helpers";

export const taskDefinitionFormMachine = createMachine<
  TaskDefinitionFormContext,
  TaskDefinitionFormMachineEvent
>(
  {
    id: TASK_FORM_MACHINE_ID,
    predictableActionArguments: true,
    initial: "ready",
    context: {
      modifiedTaskDefinition: {} as TaskDefinitionDto,
      originTaskDefinition: {} as TaskDefinitionDto,
    },
    on: {
      [TaskDefinitionFormEventType.SET_EDITING_FORM_FIELD]: [
        // This is not needed and complex but works. Will refactor later
        {
          target: "ready.editingField.name",
          cond: "isNameField",
        },
        {
          target: "ready.editingField.description",
          cond: "isDescriptionField",
        },
        {
          target: "ready.editingField.none",
        },
      ],
    },
    states: {
      ready: {
        type: "parallel",
        on: {
          [TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM]: {
            actions: "handleChangeTask",
            /* target: ".validate.start", */
          },
          [TaskDefinitionFormEventType.TOGGLE_FORM_MODE]: {
            target: "finish",
          },
          [TaskDefinitionFormEventType.SET_SAVE_CONFIRMATION_OPEN]: {
            target: "finish",
          },
          [TaskDefinitionFormEventType.SET_RESET_CONFIRMATION_OPEN]: {
            target: "finish",
          },
          [TaskDefinitionFormEventType.SET_DELETE_CONFIRMATION_OPEN]: {
            target: "finish",
          },
          [TaskDefinitionFormEventType.CONFIRM_RESET_TASK]: {
            actions: ["resetForm"],
          },
          [TaskDefinitionFormEventType.RESET_FORM]: {
            actions: "resetForm",
          },
        },
        states: {
          exportFileState: {
            //No need to be a parallel state.[nor the others] but big refactor will handle this at a later time
            initial: "idle",
            states: {
              idle: {
                on: {
                  [TaskDefinitionFormEventType.EXPORT_TASK_TO_JSON_FILE]: {
                    target: "exportTask",
                  },
                },
              },
              exportTask: {
                invoke: {
                  src: "handleDownloadFile",
                  onDone: {
                    target: "idle",
                  },
                  onError: {
                    actions: ["persistErrorMessage"],
                  },
                },
              },
            },
          },
          editingField: {
            initial: "none",
            states: {
              none: {},
              name: {},
              description: {},
            },
          },
          validate: {
            initial: "stop",
            states: {
              stop: {},
              start: {
                invoke: {
                  src: "validateForm",
                  onDone: {
                    actions: "persistError",
                    target: "stop",
                  },
                  onError: {
                    actions: "persistErrorMessage",
                    target: "stop",
                  },
                },
              },
            },
          },
        },
      },
      finish: {
        type: "final",
        data: (context, event) => ({ ...context, reason: event.type }),
      },
    },
  },
  {
    actions: customActions as any,
    guards: guards as any,
    services: services as any,
  },
);
