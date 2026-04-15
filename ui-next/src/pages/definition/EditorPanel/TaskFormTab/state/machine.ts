import { createMachine } from "xstate";
import {
  FormMachineActionTypes,
  TaskFormEvents,
  TaskFormMachineContext,
} from "./types";
import { FlowActionTypes } from "components/features/flow/state";
import * as actions from "./actions";
import * as guards from "./guards";
import { taskStatsMachine } from "../TaskStats/state";

export const formMachine = createMachine<
  TaskFormMachineContext,
  TaskFormEvents
>(
  {
    id: "formMachine",
    predictableActionArguments: true,
    initial: "init",
    context: {
      originalTask: undefined,
      crumbs: [],
      tasksBranch: [],
      workflowInputParameters: [],
      taskHeaderActor: undefined,
      maybeSelectedSwitchBranch: undefined,
      authHeaders: undefined,
      taskChanges: undefined,
    },
    invoke: {
      id: "taskStatsMachine",
      src: taskStatsMachine,
      data: {
        completedRateSeries: [],
        failedRateSeries: [],
        completedAmount: 0,
        failedAmount: 0,
        startHoursBack: 24,
        authHeaders: ({ authHeaders }: TaskFormMachineContext) => authHeaders,
        taskName: ({ originalTask }: TaskFormMachineContext) =>
          originalTask?.name,
      },
    },
    states: {
      init: {
        entry: ["spawnTaskHeaderMachineActor"],
        always: "rendered",
      },
      noTask: {
        entry: "invalidTaskLeaveForm",
        type: "final",
      },
      rendered: {
        on: {
          [FormMachineActionTypes.UPDATE_TASK]: {
            cond: "isTaskChanged",
            actions: ["updateTask", "notifyChanges", "updateTaskHeaderMachine"],
          },
          [FormMachineActionTypes.UPDATE_CRUMBS]: {
            // Note it will use incoming task if task is SWITCH and has changes else it will ignore
            actions: ["updateCrumbsAndOriginalTask"],
          },
          [FlowActionTypes.SELECT_EDGE_EVT]: {
            actions: ["maybePersistSelectedSwitchBranch"],
          },
          [FlowActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST]: {
            actions: ["updateCollapseWorkflowList"],
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
