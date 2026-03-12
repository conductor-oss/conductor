import { createMachine } from "xstate";
import * as customActions from "./actions";
import * as guards from "./guards";
import { TaskDefinitionDto } from "types";
import { TASK_DIALOGS_MACHINE_ID } from "pages/definition/task/state/helpers";
import {
  TaskDefinitionDialogsContext,
  TaskDefinitionDialogsMachineEvent,
  TaskDefinitionDialogsMachineType,
} from "pages/definition/task/dialogs/state/types";

export const taskDefinitionDialogsMachine = createMachine<
  TaskDefinitionDialogsContext,
  TaskDefinitionDialogsMachineEvent
>(
  {
    id: TASK_DIALOGS_MACHINE_ID,
    predictableActionArguments: true,
    initial: "idle",
    context: {
      modifiedTaskDefinition: {} as TaskDefinitionDto,
      originTaskDefinition: {} as TaskDefinitionDto,
    },
    on: {
      [TaskDefinitionDialogsMachineType.SET_DEFINE_NEW_TASK_OPEN]: [
        {
          target: "confirmationDialogDefineNewOpen",
        },
      ],
      [TaskDefinitionDialogsMachineType.SET_RESET_CONFIRMATION_OPEN]: [
        {
          target: "confirmationDialogResetOpen",
        },
      ],
      [TaskDefinitionDialogsMachineType.SET_DELETE_CONFIRMATION_OPEN]: [
        {
          target: "confirmationDialogDeleteOpen",
        },
      ],
    },
    states: {
      idle: {},
      confirmationDialogDefineNewOpen: {
        on: {
          [TaskDefinitionDialogsMachineType.HANDLE_DEFINE_NEW_CONFIRMATION]: [
            {
              cond: "isConfirm",
              actions: ["notifyGoToDefineNewTask"],
              target: `#${TASK_DIALOGS_MACHINE_ID}.finish`,
            },
            {
              target: `#${TASK_DIALOGS_MACHINE_ID}.finish`,
            },
          ],
        },
      },
      confirmationDialogResetOpen: {
        on: {
          [TaskDefinitionDialogsMachineType.HANDLE_RESET_CONFIRMATION]: [
            {
              cond: "isConfirm",
              actions: ["notifyResetTask"],
              target: `#${TASK_DIALOGS_MACHINE_ID}.finish`,
            },
            {
              target: `#${TASK_DIALOGS_MACHINE_ID}.finish`,
            },
          ],
        },
      },
      confirmationDialogDeleteOpen: {
        on: {
          [TaskDefinitionDialogsMachineType.HANDLE_DELETE_TASK_DEF_CONFIRMATION]:
            [
              {
                cond: "isConfirm",
                actions: ["notifyDeleteTask"],
                target: `#${TASK_DIALOGS_MACHINE_ID}.finish`,
              },
              {
                target: `#${TASK_DIALOGS_MACHINE_ID}.finish`,
              },
            ],
        },
      },
      finish: {
        type: "final",
        data: (_, event) => ({ event }),
      },
    },
  },
  {
    actions: customActions as any,
    guards: guards as any,
  },
);
