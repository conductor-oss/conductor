import { TaskDef, AuthHeaders, Crumb } from "types";
import { TaskHeaderMachineEvents } from "pages/definition/EditorPanel/TaskFormTab/forms/TaskFormHeader/state/types";
import { ActorRef } from "xstate";
import { EdgeData } from "reaflow";
import { FlowActionTypes } from "components/features/flow/state";

export enum FormMachineActionTypes {
  UPDATE_TASK = "UPDATE_TASK",
  CHECK_FOR_TASK_ERRORS = "CHECK_FOR_TASK_ERRORS",

  UPDATE_CRUMBS = "UPDATE_CRUMBS",

  UPDATE_COLLAPSE_WORKFLOW_LIST = "UPDATE_COLLAPSE_WORKFLOW_LIST",
}

export type ErrorType = {
  id: "Form Error";
  message: string;
  path: string;
  hint?: string;
  type: "TASK";
};

export type UpdateTaskEvent = {
  type: FormMachineActionTypes.UPDATE_TASK;
  taskChanges: Partial<TaskDef>;
};

export type UpdateCollapseWorkflowListEvent = {
  type: FormMachineActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST;
  workflowName: string;
};

export type UpdateCrumbsEvent = {
  type: FormMachineActionTypes.UPDATE_CRUMBS;
  crumbs: Crumb[];
  task?: Partial<TaskDef>;
};

export type CheckForTaskErrorsEvent = {
  type: FormMachineActionTypes.CHECK_FOR_TASK_ERRORS;
};

export type SelectEdgeEvent = {
  type: FlowActionTypes.SELECT_EDGE_EVT;
  edge: EdgeData;
};

export interface TaskFormMachineContext {
  originalTask?: Partial<TaskDef>;
  taskChanges?: Partial<TaskDef>;
  tasksBranch: TaskDef[];
  crumbs: Crumb[];
  workflowInputParameters: string[];
  taskHeaderActor?: ActorRef<TaskHeaderMachineEvents>;
  maybeSelectedSwitchBranch?: string;
  authHeaders?: AuthHeaders;
  workflowName?: string;
}

export type TaskFormEvents =
  | UpdateTaskEvent
  | CheckForTaskErrorsEvent
  | SelectEdgeEvent
  | UpdateCrumbsEvent
  | UpdateCollapseWorkflowListEvent;
