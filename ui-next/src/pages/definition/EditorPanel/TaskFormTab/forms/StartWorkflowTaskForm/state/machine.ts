import { createMachine } from "xstate";
import * as actions from "./actions";
import * as services from "./service";
import {
  StartSubWfNameVersionEvents,
  StartSubWfNameVersionMachineContext,
  StartSubWfNameVersionStates,
  StartSubWfNameVersionTypes,
} from "./types";

export const startSubWfNameVersionMachine = createMachine<
  StartSubWfNameVersionMachineContext,
  StartSubWfNameVersionEvents
>(
  {
    id: "startSubWfNameVersionMachine",
    predictableActionArguments: true,
    initial: "initial",
    context: {
      authHeaders: {},
      workflowName: "",
      fetchedNamesAndVersions: undefined,
    },
    states: {
      initial: {
        invoke: {
          id: "fetchWfNamesAndVersions",
          src: "fetchWfNamesAndVersions",
          onDone: {
            actions: ["persistFetchedNamesAndVersions", "persistOptions"],
            target: StartSubWfNameVersionStates.IDLE,
          },
        },
      },
      [StartSubWfNameVersionStates.IDLE]: {
        on: {
          [StartSubWfNameVersionTypes.SELECT_WORKFLOW_NAME]: {
            actions: ["persistWfName"],
            target: StartSubWfNameVersionStates.HANDLE_SELECT_WORKFLOW_NAME,
          },
        },
      },
      [StartSubWfNameVersionStates.HANDLE_SELECT_WORKFLOW_NAME]: {
        invoke: {
          id: "handleSelect",
          src: "handleSelect",
          onDone: {
            actions: ["persistOptions"],
            target: StartSubWfNameVersionStates.IDLE,
          },
          onError: {},
        },
      },
    },
  },
  {
    actions: actions as any,
    services: services as any,
  },
);
