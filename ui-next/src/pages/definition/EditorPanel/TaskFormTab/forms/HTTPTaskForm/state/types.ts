import { Method, ServiceDefDto } from "types/RemoteServiceTypes";
import { AuthHeaders } from "types/common";
import { TaskDefinitionDto } from "types/TaskDefinition";

export interface ServiceMethodsMachineContext {
  authHeaders: AuthHeaders;
  services?: Partial<ServiceDefDto>[];
  selectedService?: Partial<ServiceDefDto>;
  selectedMethod?: Method;
  selectedSchema?: Record<string, unknown>;
  currentTaskDefName?: string;
  currentTaskDefinition?: Partial<TaskDefinitionDto>;
  modifiedTaskDef?: Partial<TaskDefinitionDto>;
  selectedHost?: string;
}

export enum ServiceMethodsMachineEventTypes {
  SELECT_SERVICE = "SELECT_SERVICE",
  SELECT_HOST = "SELECT_HOST",
  SELECT_METHOD = "SELECT_METHOD",
  SELECT_TASK = "SELECT_TASK",
  UPDATE_TASK_CONFIG = "UPDATE_TASK_CONFIG",
  HANDLE_CHANGE_TASK_CONFIG = "HANDLE_CHANGE_TASK_CONFIG",
  RESET_MODIFIED_TASK_CONFIG = "RESET_MODIFIED_TASK_CONFIG",
  FETCH_TASK_DEFINITION_EVENT = "FETCH_TASK_DEFINITION_EVENT",
  HANDLE_UPDATE_TEMPLATE = "HANDLE_UPDATE_TEMPLATE",
}

export enum ServiceMethodsMachineStates {
  IDLE = "IDLE",
  HANDLE_SELECT_SERVICE = "HANDLE_SELECT_SERVICE",
  GO_BACK_TO_IDLE = "GO_BACK_TO_IDLE",
  FETCH_SCHEMA = "FETCH_SCHEMA",
  FETCH_FOR_SERVICE_REGISTRY = "FETCH_FOR_SERVICE_REGISTRY",
  FETCH_FOR_TASK_DEFINITION = "FETCH_FOR_TASK_DEFINITION",
  UPDATE_TASK = "UPDATE_TASK",
  UPDATE_TEMPLATE = "UPDATE_TEMPLATE",
}

export type SelectServiceNameEvent = {
  type: ServiceMethodsMachineEventTypes.SELECT_SERVICE;
  data: Partial<ServiceDefDto>;
};

export type SelectMethodEvent = {
  type: ServiceMethodsMachineEventTypes.SELECT_METHOD;
  data: Method;
};

export type SelectTaskEvent = {
  type: ServiceMethodsMachineEventTypes.SELECT_TASK;
  taskDefName: string;
};

export type UpdateTaskConfigEvent = {
  type: ServiceMethodsMachineEventTypes.UPDATE_TASK_CONFIG;
};

export type HandleChangeTaskConfigEvent = {
  type: ServiceMethodsMachineEventTypes.HANDLE_CHANGE_TASK_CONFIG;
  name: string;
  value: number | string | null;
};

export type HandleResetModifiedTaskConfigEvent = {
  type: ServiceMethodsMachineEventTypes.RESET_MODIFIED_TASK_CONFIG;
};

export type FetchForTaskDefinitionEvent = {
  type: ServiceMethodsMachineEventTypes.FETCH_TASK_DEFINITION_EVENT;
};

export type HandleUpdateTemplateEvent = {
  type: ServiceMethodsMachineEventTypes.HANDLE_UPDATE_TEMPLATE;
  url: string;
  headers?: Record<string, string>;
};

export type SelectHostEvent = {
  type: ServiceMethodsMachineEventTypes.SELECT_HOST;
  data: string;
};

export type ServiceMethodsMachineEvents =
  | SelectServiceNameEvent
  | SelectMethodEvent
  | SelectTaskEvent
  | UpdateTaskConfigEvent
  | HandleChangeTaskConfigEvent
  | HandleResetModifiedTaskConfigEvent
  | FetchForTaskDefinitionEvent
  | HandleUpdateTemplateEvent
  | SelectHostEvent;
