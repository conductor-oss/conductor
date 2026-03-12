import { TaskType } from "types";
import { createMachine } from "xstate";
import * as actions from "./actions";
import {
  TaskFormHeaderMachineContext,
  TaskFormHeaderEventTypes,
  TaskHeaderMachineEvents,
} from "./types";

export const taskFormHeaderMachine = createMachine<
  TaskFormHeaderMachineContext,
  TaskHeaderMachineEvents
>(
  {
    id: "taskFormHeaderMachine",
    initial: "focused",
    predictableActionArguments: true,
    context: {
      name: "",
      taskReferenceName: "",
      taskType: TaskType.SIMPLE,
    },
    on: {
      [TaskFormHeaderEventTypes.VALUES_UPDATED]: {
        // Only persist changes if not typing in the task reference name or name,
        // otherwise there is a race condition with
        // CHANGE_TASK_REFERENCE_VALUE or CHANGE_NAME_VALUE
        cond: (ctx) => !ctx.isEditingValues,
        actions: ["persistChanges"],
      },
      [TaskFormHeaderEventTypes.TASK_CREATED_SUCCESSFULLY]: {},
    },
    states: {
      focused: {
        on: {
          [TaskFormHeaderEventTypes.START_EDITING_VALUES]: {
            actions: ["startEditingValues"],
          },
          [TaskFormHeaderEventTypes.STOP_EDITING_VALUES]: {
            actions: ["stopEditingValues"],
          },
          [TaskFormHeaderEventTypes.CHANGE_TASK_REFERENCE_VALUE]: {
            actions: ["persistTaskReferenceNameChanges", "syncWithParent"],
          },
          [TaskFormHeaderEventTypes.CHANGE_NAME_VALUE]: {
            actions: ["persistNameChanges", "syncWithParent"],
          },
          [TaskFormHeaderEventTypes.GENERATE_TASK_REFERENCE_NAME]: {
            actions: ["generateTaskReferenceAndName", "syncWithParent"],
          },
        },
      },
    },
  },
  {
    actions: actions as any,
  },
);
