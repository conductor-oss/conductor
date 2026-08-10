import { ConductorEvent } from "types/Events";
export enum EventFormMachineTypes {
  CHANGE_NAME_EVT = "CHANGE_NAME_EVT",
  CHANGE_EVENT_EVT = "CHANGE_EVENT_EVT",
  CHANGE_CONDITION_EVT = "CHANGE_CONDITION_EVT",
  CHANGE_EVALUATOR_EVT = "CHANGE_EVALUATOR_EVT",
  ADD_ACTION = "ADD_ACTION",
  INPUT_CHANGE = "INPUT_CHANGE",
  EDIT_ACTION = "EDIT_ACTION",
  DELETE_ACTION = "DELETE_ACTION",
  SAVE_EVT = "SAVE_EVT",
  TOGGLE_FORM_EDITOR_EVT = "TOGGLE_FORM_EDITOR_EVT",
  RESET_CONFIRM_EVT = "RESET_CONFIRM_EVT",
  CONFIRM_NEW_EVENT = "CONFIRM_NEW_EVENT",
}

export type InputChangeEvent = {
  type: EventFormMachineTypes.INPUT_CHANGE;
};

export type AddEvent = {
  type: EventFormMachineTypes.ADD_ACTION;
};

export type EditActionEvent = {
  type: EventFormMachineTypes.EDIT_ACTION;
};

export type DeletActionEvent = {
  type: EventFormMachineTypes.DELETE_ACTION;
};

export type SaveEvent = {
  type: EventFormMachineTypes.SAVE_EVT;
};

export type ToggleFormModeEvent = {
  type: EventFormMachineTypes.TOGGLE_FORM_EDITOR_EVT;
};

export type ResetConfirmEvent = {
  type: EventFormMachineTypes.RESET_CONFIRM_EVT;
};

export type ConfirmNewEventEvent = {
  type: EventFormMachineTypes.CONFIRM_NEW_EVENT;
};

export type FormHandlerEvents =
  | InputChangeEvent
  | AddEvent
  | EditActionEvent
  | DeletActionEvent
  | SaveEvent
  | ToggleFormModeEvent
  | ResetConfirmEvent
  | ConfirmNewEventEvent;

export enum EventFormMachineStates {
  IDLE = "idle",
  EXIT = "exit",
}

export enum QueueTypeSource {
  KAFKA = "kafka",
  AMQP = "amqp",
  AZURE = "azure",
  SQS = "sqs",
}
export const queueTypeLabel: { [key in QueueTypeSource]: string } = {
  kafka: "kafka",
  amqp: "amqp",
  azure: "azure",
  sqs: "sqs",
};

export enum Evaluator {
  javascript = "javascript",
  "value-param" = "value-param",
}
export const evaluatorLabel: { [key in Evaluator]: string } = {
  javascript: "javascript",
  "value-param": "value-param",
};

export enum Action {
  COMPLETE_TASK = "complete_task",
  TERMINATE_WORKFLOW = "terminate_workflow",
  UPDATE_WORKFLOW_VARIABLES = "update_workflow_variables",
  FAIL_TASK = "fail_task",
  START_WORKFLOW = "start_workflow",
}

export type AddActionEvent = {
  type: EventFormMachineTypes.ADD_ACTION;
  actionType: Action;
};

export const actionLabel = {
  complete_task: "Complete Task",
  terminate_workflow: "Terminate Workflow",
  update_workflow_variables: "Update Variables",
  fail_task: "Fail Task",
  start_workflow: "Start Workflow",
} as { [key: string]: string };

export interface EventFormMachineContext {
  eventAsJson: Partial<ConductorEvent>;
  originalSource: Partial<ConductorEvent>;
}
