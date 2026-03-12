import { createMachine } from "xstate";
import * as services from "./service";
import * as actions from "./actions";
import {
  LocalCopyMachineContext,
  LocalCopyMachineEvents,
  LocalCopyMachineEventTypes,
} from "./types";
import _isEmpty from "lodash/isEmpty";

export const localCopyMachine = createMachine<
  LocalCopyMachineContext,
  LocalCopyMachineEvents
>(
  {
    predictableActionArguments: true,
    id: "localCopyMachine",
    initial: "lookForLocalCopies",
    context: {
      currentVersion: undefined,
      isNewWorkflow: false,
      workflowName: "",
      lastStoredVersion: {},
      currentWf: {},
    },
    states: {
      lookForLocalCopies: {
        invoke: {
          src: "consumeCopyFromLocalStorage",
          onDone: [
            {
              cond: (context) => context.isNewWorkflow,
              actions: ["storeLocalCopy"],
              target: "finish",
            },
            {
              actions: ["storeLocalCopy"],
              target: "promptUseLocalCopy",
            },
          ],
          onError: {
            target: "finish",
          },
        },
      },
      promptUseLocalCopy: {
        on: {
          [LocalCopyMachineEventTypes.USE_LOCAL_CHANGES_EVT]: {
            target: "finish",
          },
          [LocalCopyMachineEventTypes.CANCEL_EVENT_EVT]: {
            target: "removeWorkflowFromStorage",
          },
        },
      },
      removeWorkflowFromStorage: {
        invoke: {
          src: "removeCopyFromStorage",
          onDone: {
            target: "finish",
            actions: "cleanLocalChanges",
          },
        },
      },
      finish: {
        type: "final",
        data: ({ lastStoredVersion }, event) => {
          return {
            workflow: lastStoredVersion,
            // @ts-ignore
            isLocalStorageEmpty: _isEmpty(event?.data),
          };
        },
      },
    },
  },
  {
    services: services as any,
    actions: actions as any,
  },
);
