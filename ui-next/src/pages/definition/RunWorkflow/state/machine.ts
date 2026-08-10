import { createMachine } from "xstate";
import * as actions from "./actions";
import * as services from "./services";
import {
  RunMachineContext,
  RunMachineEventsTypes,
  RunMachineEvents,
  RunMachineStates,
} from "./types";

export const runMachine = createMachine<RunMachineContext, RunMachineEvents>(
  {
    predictableActionArguments: true,
    id: "runWorkflowMachine",
    initial: RunMachineStates.CHECK_INPUT_PARAMS,
    context: {
      authHeaders: undefined,
      currentWf: {},
      input: "{}",
      workflowDefaultRunParam: undefined,
      correlationId: undefined,
      errorInspectorMachine: undefined,
      taskToDomain: "{}",
      popoverMessage: null,
      idempotencyKey: undefined,
      idempotencyStrategy: undefined,
    },
    states: {
      [RunMachineStates.IDLE]: {
        on: {
          [RunMachineEventsTypes.UPDATE_INPUT_PARAMS]: {
            actions: ["persistInputParams"],
          },
          [RunMachineEventsTypes.UPDATE_CORRELATION_ID]: {
            actions: ["persistCorrelationId"],
          },
          [RunMachineEventsTypes.UPDATE_IDEMPOTENCY_KEY]: {
            actions: ["persistIdempotencyKey"],
          },
          [RunMachineEventsTypes.UPDATE_IDEMPOTENCY_VALUES]: {
            actions: ["persistIdempotencyValues"],
          },
          [RunMachineEventsTypes.UPDATE_IDEMPOTENCY_STRATEGY]: {
            actions: ["persistIdempotencyStrategy"],
          },
          [RunMachineEventsTypes.UPDATE_TASKS_TO_DOMAIN_MAPPING]: {
            actions: ["persistTasksToDomain"],
          },
          [RunMachineEventsTypes.CLEAR_FORM]: {
            actions: ["clearForm"],
          },
          [RunMachineEventsTypes.TRIGGER_RUN_WORKFLOW]: {
            target: RunMachineStates.RUN_WORKFLOW,
          },
          [RunMachineEventsTypes.HANDLE_POPOVER_MESSAGE]: {
            actions: ["persistPopupMessage"],
          },
          [RunMachineEventsTypes.UPDATE_ALL_FIELDS]: {
            actions: ["persistAllFields"],
          },
          [RunMachineEventsTypes.CHANGE_TAB_EVT]: {
            actions: ["sendContextToParent"],
          },
        },
      },
      [RunMachineStates.CHECK_INPUT_PARAMS]: {
        entry: ["checkForExistingInputParams"],
        always: RunMachineStates.IDLE,
      },
      [RunMachineStates.RUN_WORKFLOW]: {
        invoke: {
          src: "runWorkflow",
          onDone: {
            actions: ["redirectToNewExecution"],
            target: RunMachineStates.IDLE,
          },
          onError: {
            actions: ["reportErrorToErrorInspector"],
            target: RunMachineStates.IDLE,
          },
        },
      },
    },
  },
  {
    services: services as any,
    actions: actions as any,
  },
);
