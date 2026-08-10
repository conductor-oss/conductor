import { createMachine } from "xstate";
import * as actions from "./actions";
import * as services from "./services";
import {
  ServiceMethodsMachineEvents,
  ServiceMethodsMachineContext,
  ServiceMethodsMachineStates,
  ServiceMethodsMachineEventTypes,
} from "./types";
import { ServiceType } from "types/RemoteServiceTypes";

export const serviceMethodsMachine = createMachine<
  ServiceMethodsMachineContext,
  ServiceMethodsMachineEvents
>(
  {
    id: "serviceMethodsMachine",
    predictableActionArguments: true,
    initial: "initial",
    context: {
      authHeaders: {},
    },
    states: {
      initial: {
        invoke: {
          id: "fetchServices",
          src: "fetchServices",
          onDone: {
            actions: ["persistServices"],
            target: ServiceMethodsMachineStates.FETCH_FOR_TASK_DEFINITION,
          },
        },
      },
      [ServiceMethodsMachineStates.IDLE]: {
        on: {
          [ServiceMethodsMachineEventTypes.SELECT_SERVICE]: {
            actions: ["persistSelectedService", "maybeChangeTaskType"],
          },
          [ServiceMethodsMachineEventTypes.SELECT_HOST]: {
            actions: ["persistSelectedHost"],
          },
          [ServiceMethodsMachineEventTypes.SELECT_METHOD]: [
            {
              cond: ({ selectedService }) =>
                selectedService?.type === ServiceType.GRPC,
              actions: ["persistSelectedMethod"],
              target: ServiceMethodsMachineStates.FETCH_FOR_SERVICE_REGISTRY,
            },
            {
              actions: ["persistSelectedMethod"],
              target: ServiceMethodsMachineStates.FETCH_SCHEMA,
            },
          ],
          [ServiceMethodsMachineEventTypes.SELECT_TASK]: {
            actions: ["persistCurrentTaskDefName"],
            target: ServiceMethodsMachineStates.FETCH_FOR_TASK_DEFINITION,
          },
          [ServiceMethodsMachineEventTypes.HANDLE_CHANGE_TASK_CONFIG]: {
            actions: ["persistModifiedTaskDef"],
          },
          [ServiceMethodsMachineEventTypes.UPDATE_TASK_CONFIG]: {
            target: ServiceMethodsMachineStates.UPDATE_TASK,
          },
          [ServiceMethodsMachineEventTypes.RESET_MODIFIED_TASK_CONFIG]: {
            actions: ["resetModifiedTaskDef"],
          },
          [ServiceMethodsMachineEventTypes.FETCH_TASK_DEFINITION_EVENT]: {
            target: ServiceMethodsMachineStates.FETCH_FOR_TASK_DEFINITION,
          },
          [ServiceMethodsMachineEventTypes.HANDLE_UPDATE_TEMPLATE]: {
            actions: ["templateUpdate"],
            target: ServiceMethodsMachineStates.IDLE,
          },
        },
      },
      [ServiceMethodsMachineStates.FETCH_FOR_SERVICE_REGISTRY]: {
        invoke: {
          id: "fetchSchemaForServiceRegistry",
          src: "fetchSchemaForServiceRegistry",
          onDone: {
            actions: ["persistSelectedSchema"],
            target: ServiceMethodsMachineStates.IDLE,
          },
          onError: {
            target: ServiceMethodsMachineStates.IDLE,
          },
        },
      },
      [ServiceMethodsMachineStates.FETCH_SCHEMA]: {
        invoke: {
          id: "fetchSchema",
          src: "fetchSchema",
          onDone: {
            actions: ["persistSelectedSchema"],
            target: ServiceMethodsMachineStates.IDLE,
          },
          onError: {
            target: ServiceMethodsMachineStates.IDLE,
          },
        },
      },
      [ServiceMethodsMachineStates.FETCH_FOR_TASK_DEFINITION]: {
        invoke: {
          id: "fetchTaskDefinition",
          src: "fetchTaskDefinition",
          onDone: {
            actions: ["persistTaskDefinition"],
            target: ServiceMethodsMachineStates.IDLE,
          },
          onError: {
            target: ServiceMethodsMachineStates.IDLE,
          },
        },
      },
      [ServiceMethodsMachineStates.UPDATE_TASK]: {
        invoke: {
          id: "updateTaskDefinitionService",
          src: "updateTaskDefinitionService",
          onDone: {
            actions: ["setSuccessMessage"],
            target: ServiceMethodsMachineStates.FETCH_FOR_TASK_DEFINITION,
          },
          onError: {
            actions: ["setErrorMessage"],
            target: ServiceMethodsMachineStates.IDLE,
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services: services as any,
  },
);
