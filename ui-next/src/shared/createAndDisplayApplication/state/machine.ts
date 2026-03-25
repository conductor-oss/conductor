import { createMachine } from "xstate";
import {
  CreateAndDisplayApplicationMachineContext,
  CreateAndDisplayApplicationEvents,
  CreateAndDisplayApplicationMachineEventTypes,
} from "./types";
import * as services from "./services";
import * as actions from "./actions";
import * as guards from "./guards";

export const createAndDisplayApplicationMachine = createMachine<
  CreateAndDisplayApplicationMachineContext,
  CreateAndDisplayApplicationEvents
>(
  {
    id: "createAndDisplayApplicationMachine",
    predictableActionArguments: true,
    context: {
      applicationAccessKey: undefined,
    },
    initial: "fetchForExistingApplication",
    states: {
      fetchForExistingApplication: {
        invoke: {
          src: "checkIfAppExistsAndCompatible",
          onDone: [
            {
              cond: (_context, { data }) => data.id !== null,
              actions: ["persistApplicationId"],
              target: "idle",
            },
            {
              target: "idle",
            },
          ],
        },
      },
      idle: {
        on: {
          [CreateAndDisplayApplicationMachineEventTypes.CREATE_APPLICATION]: {
            target: "shouldCreateApplication",
          },
          [CreateAndDisplayApplicationMachineEventTypes.RECREATE_KEYS]: {
            target: "generateKeys",
          },
        },
      },
      shouldCreateApplication: {
        always: [
          {
            cond: "isApplicationCreated",
            target: "generateKeys",
          },
          {
            target: "createApplication",
          },
        ],
      },
      generateKeys: {
        invoke: {
          src: "generateKeys",
          onDone: {
            target: "displayKeys",
            actions: ["persistApplicationKeys"],
          },
          onError: {
            target: "errorCreatingApplication",
            actions: ["persistError"],
          },
        },
      },
      createApplication: {
        invoke: {
          src: "createApplicationWithRoles",
          onDone: {
            target: "generateKeys",
            actions: ["persistApplicationId"],
          },
          onError: {
            target: "errorCreatingApplication",
            actions: ["persistError"],
          },
        },
      },
      displayKeys: {
        tags: ["displayKeys"],
        on: {
          [CreateAndDisplayApplicationMachineEventTypes.CLOSE_KEYS_DIALOG]: {
            target: "idle",
          },
          [CreateAndDisplayApplicationMachineEventTypes.RECREATE_KEYS]: {
            target: "generateKeys",
          },
        },
      },
      errorCreatingApplication: {
        tags: ["displayError"],
        after: {
          3000: {
            target: "idle",
            actions: ["clearError"],
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    guards: guards as any,
    services: services as any,
  },
);
