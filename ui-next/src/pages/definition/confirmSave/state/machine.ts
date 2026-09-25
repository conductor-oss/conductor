import { createMachine } from "xstate";
import {
  SaveWorkflowMachineEventTypes,
  SaveWorkflowEvents,
  SaveWorkflowMachineContext,
} from "./types";

import * as actions from "./actions";
import * as guards from "./guards";
import * as services from "./services";

export const saveMachine = createMachine<
  SaveWorkflowMachineContext,
  SaveWorkflowEvents
>(
  {
    id: "saveWorkflowMachine",
    predictableActionArguments: true,
    initial: "confirmSave",
    context: {
      currentWf: {},
      editorChanges: "",
      isNewWorkflow: false,
      workflowName: "",
      errorInspectorMachine: undefined,
      authHeaders: {},
      currentVersion: 1,
      isNewVersion: undefined,
      isContinueCreate: undefined,
    },
    states: {
      confirmSave: {
        on: {
          [SaveWorkflowMachineEventTypes.CONFIRM_SAVE_EVT]: {
            target: "resolveAgentSnapshots",
          },
          [SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT]: {
            target: "savedCancelled",
          },
          [SaveWorkflowMachineEventTypes.EDIT_EVT]: {
            actions: ["editChanges", "checkForErrorsInWorkflow"],
          },
          [SaveWorkflowMachineEventTypes.EDIT_DEBOUNCE_EVT]: {
            actions: ["cancelDebounceEditChanges", "debounceEditEvent"],
          },
        },
      },
      resolveAgentSnapshots: {
        invoke: {
          src: "resolveAgentSnapshots",
          id: "resolve-agent-snapshots",
          onDone: {
            actions: "storeResolvedAgentSnapshots",
            target: "selectSaveOperation",
          },
          onError: { target: "confirmSave", actions: "reportServerErrors" },
        },
      },
      selectSaveOperation: {
        always: [
          { target: "removeWorkflowFromStorage", cond: "isNewOrNameChanged" },
          { target: "updateWorkflow" },
        ],
      },
      confirmOverride: {
        on: {
          [SaveWorkflowMachineEventTypes.CONFIRM_OVERRIDE_EVT]: {
            target: "updateWorkflow",
          },
          [SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT]: {
            target: "savedCancelled",
          },
        },
      },
      createWorkflow: {
        invoke: [
          {
            src: "createWorkflow",
            id: "create-workflow",
            onDone: {
              actions: ["updateWorkflowVersionAndName"],
              target: "refetchWorkflowDefinitions",
            },
            onError: [
              {
                target: "confirmOverride",
                cond: "returnedConflict",
              },
              { target: "savedCancelled", actions: ["reportServerErrors"] },
            ],
          },
        ],
      },
      updateWorkflow: {
        invoke: {
          src: "updateWorkflow",
          id: "update-workflow",
          onDone: {
            actions: ["updateWorkflowVersionAndName"],
            target: "refetchWorkflowDefinitions",
          },
          onError: { target: "savedCancelled", actions: "reportServerErrors" },
        },
      },
      removeWorkflowFromStorage: {
        invoke: {
          src: "removeCopyFromStorage",
          onDone: {
            target: "createWorkflow",
          },
        },
      },
      cleanWorkflowFromStorageAndExit: {
        invoke: {
          src: "removeCopyFromStorage",
          onDone: {
            target: "done",
          },
        },
      },
      refetchWorkflowDefinitions: {
        invoke: {
          src: "refetchAllDefinitionsOfCurrentWorkflow",
          id: "refetch-all-wf-definitions-of-current-wf",
          onError: { target: "confirmSave", actions: "reportServerErrors" },
          onDone: [
            {
              cond: "isNewVersion",
              actions: [
                "grabLastVersionAndPersistAsNew",
                "sendSuccessSave",
                "cleanServerErrors",
              ],
              target: "cleanWorkflowFromStorageAndExit",
            },
            {
              actions: ["sendSuccessSave", "cleanServerErrors"],
              target: "cleanWorkflowFromStorageAndExit",
            },
          ],
        },
      },
      done: {
        type: "final",
      },
      savedCancelled: {
        entry: "sendCancelSave",
        type: "final",
      },
    },
  },
  {
    actions: actions as any,
    guards: guards as any,
    services,
  },
);
