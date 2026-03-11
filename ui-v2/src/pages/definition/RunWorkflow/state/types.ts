import { ActorRef } from "xstate";
import { PopoverMessage, WorkflowDef } from "types";
import { AuthHeaders } from "types";
import { ErrorInspectorMachineEvents } from "pages/definition/errorInspector/state/types";

export enum IdempotencyStrategyEnum {
  FAIL = "FAIL",
  RETURN_EXISTING = "RETURN_EXISTING",
  FAIL_ON_RUNNING = "FAIL_ON_RUNNING",
}

export type IdempotencyValuesProp = {
  idempotencyKey: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
};

type CommonProperties = {
  correlationId: string;
  input: object;
  taskToDomain?: object;
  idempotencyKey?: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
};

export type RunWorkflowParamType = {
  name: string;
  version: string;
} & CommonProperties;

export type FieldsData = {
  input: string;
  taskToDomain: string;
  correlationId: string;
  idempotencyKey?: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
};

export type ExtendedFieldsData = FieldsData & {
  name: string;
  executionLink: string;
};

export interface RunMachineContext {
  authHeaders?: AuthHeaders;
  currentWf: Partial<WorkflowDef>;
  input?: string;
  correlationId?: string;
  taskToDomain?: string;
  popoverMessage: PopoverMessage | null;
  errorInspectorMachine?: ActorRef<ErrorInspectorMachineEvents>;
  idempotencyKey?: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
  workflowDefaultRunParam?: Record<string, unknown>;
}

export enum RunMachineStates {
  IDLE = "IDLE",
  CHECK_INPUT_PARAMS = "CHECK_INPUT_PARAMS",
  RUN_WORKFLOW = "RUN_WORKFLOW",
}

export enum RunMachineEventsTypes {
  UPDATE_INPUT_PARAMS = "UPDATE_INPUT_PARAMS",
  UPDATE_CORRELATION_ID = "UPDATE_CORRELATION_ID",
  UPDATE_TASKS_TO_DOMAIN_MAPPING = "UPDATE_TASKS_TO_DOMAIN_MAPPING",
  CLEAR_FORM = "CLEAR_FORM",
  TRIGGER_RUN_WORKFLOW = "TRIGGER_RUN_WORKFLOW",
  HANDLE_POPOVER_MESSAGE = "HANDLE_POPOVER_MESSAGE",
  UPDATE_ALL_FIELDS = "UPDATE_ALL_FIELDS",
  UPDATE_IDEMPOTENCY_STRATEGY = "UPDATE_IDEMPOTENCY_STRATEGY",
  UPDATE_IDEMPOTENCY_KEY = "UPDATE_IDEMPOTENCY_KEY",
  UPDATE_IDEMPOTENCY_VALUES = "UPDATE_IDEMPOTENCY_VALUES",
  CHANGE_TAB_EVT = "changeTab",
}

export type UpdateInputParamsEvent = {
  type: RunMachineEventsTypes.UPDATE_INPUT_PARAMS;
  changes: string;
};
export type UpdateCorrelationIdEvent = {
  type: RunMachineEventsTypes.UPDATE_CORRELATION_ID;
  changes: string;
};
export type UpdateIdempotencyKeyEvent = {
  type: RunMachineEventsTypes.UPDATE_IDEMPOTENCY_KEY;
  changes: string;
};
export type UpdateIdempotencyStrategyEvent = {
  type: RunMachineEventsTypes.UPDATE_IDEMPOTENCY_STRATEGY;
  changes: IdempotencyStrategyEnum;
};

export type UpdateIdempotencyValuesEvent = {
  type: RunMachineEventsTypes.UPDATE_IDEMPOTENCY_VALUES;
  changes: IdempotencyValuesProp;
};

export type UpdateTasksToDomainEvent = {
  type: RunMachineEventsTypes.UPDATE_TASKS_TO_DOMAIN_MAPPING;
  changes: string;
};

export type ClearFormEvent = {
  type: RunMachineEventsTypes.CLEAR_FORM;
};

export type RunWorkflowEvent = {
  type: RunMachineEventsTypes.TRIGGER_RUN_WORKFLOW;
};

export type HandlePopoverMessageEvent = {
  type: RunMachineEventsTypes.HANDLE_POPOVER_MESSAGE;
  popoverMessage: PopoverMessage | null;
};

export type UpdateAllFieldsEvent = {
  type: RunMachineEventsTypes.UPDATE_ALL_FIELDS;
  data: FieldsData;
};

export type ChangeTabEvent = {
  type: RunMachineEventsTypes.CHANGE_TAB_EVT;
};

export type RunMachineEvents =
  | UpdateInputParamsEvent
  | UpdateCorrelationIdEvent
  | UpdateTasksToDomainEvent
  | ClearFormEvent
  | RunWorkflowEvent
  | HandlePopoverMessageEvent
  | UpdateAllFieldsEvent
  | UpdateIdempotencyStrategyEvent
  | UpdateIdempotencyKeyEvent
  | UpdateIdempotencyValuesEvent
  | ChangeTabEvent;
