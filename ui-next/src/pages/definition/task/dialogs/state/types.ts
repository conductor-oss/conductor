import { ActorRef } from "xstate";
import { TaskDefinitionMachineEvent } from "pages/definition/task/state";
import { TaskDefinitionDto } from "types/TaskDefinition";

export enum TaskDefinitionDialogsMachineType {
  HANDLE_DEFINE_NEW_CONFIRMATION = "HANDLE_DEFINE_NEW_CONFIRMATION",
  HANDLE_DELETE_TASK_DEF_CONFIRMATION = "HANDLE_DELETE_TASK_DEF_CONFIRMATION",
  HANDLE_RESET_CONFIRMATION = "HANDLE_RESET_CONFIRMATION",
  SET_DEFINE_NEW_TASK_OPEN = "SET_DEFINE_NEW_TASK_OPEN",
  SET_DELETE_CONFIRMATION_OPEN = "SET_DELETE_CONFIRMATION_OPEN",
  SET_RESET_CONFIRMATION_OPEN = "SET_RESET_CONFIRMATION_OPEN",
  CONFIRM_DELETE_TASK = "CONFIRM_DELETE_TASK",
  CONFIRM_GO_TO_DEFINE_NEW_TASK = "CONFIRM_GO_TO_DEFINE_NEW_TASK",
  CONFIRM_RESET_TASK = "CONFIRM_RESET_TASK",
}

export interface TaskDefinitionDialogsContext {
  modifiedTaskDefinition: TaskDefinitionDto;
  originTaskDefinition: TaskDefinitionDto;
}

export type SetDefineNewTaskOpenEvent = {
  type: TaskDefinitionDialogsMachineType.SET_DEFINE_NEW_TASK_OPEN;
};

export type SetDeleteConfirmationOpenEvent = {
  type: TaskDefinitionDialogsMachineType.SET_DELETE_CONFIRMATION_OPEN;
};

export type SetResetConfirmationOpenEvent = {
  type: TaskDefinitionDialogsMachineType.SET_RESET_CONFIRMATION_OPEN;
};

export type HandleResetConfirmationEvent = {
  type: TaskDefinitionDialogsMachineType.HANDLE_RESET_CONFIRMATION;
  isConfirm: boolean;
};

export type HandleDefineNewConfirmationEvent = {
  type: TaskDefinitionDialogsMachineType.HANDLE_DEFINE_NEW_CONFIRMATION;
  isConfirm: boolean;
};

export type HandleDeleteTaskDefConfirmationEvent = {
  type: TaskDefinitionDialogsMachineType.HANDLE_DELETE_TASK_DEF_CONFIRMATION;
  isConfirm: boolean;
};

export type ConfirmDeleteTaskEvent = {
  type: TaskDefinitionDialogsMachineType.CONFIRM_DELETE_TASK;
};

export type ConfirmGoToDefineNewTask = {
  type: TaskDefinitionDialogsMachineType.CONFIRM_GO_TO_DEFINE_NEW_TASK;
};

export type ConfirmResetTaskEvent = {
  type: TaskDefinitionDialogsMachineType.CONFIRM_RESET_TASK;
};

export type TaskDefinitionDialogsMachineEvent =
  | ConfirmDeleteTaskEvent
  | ConfirmGoToDefineNewTask
  | ConfirmResetTaskEvent
  | HandleDefineNewConfirmationEvent
  | HandleDeleteTaskDefConfirmationEvent
  | HandleResetConfirmationEvent
  | SetDefineNewTaskOpenEvent
  | SetDeleteConfirmationOpenEvent
  | SetResetConfirmationOpenEvent;

export interface TaskDefinitionDialogsProps {
  taskDefActor: ActorRef<TaskDefinitionMachineEvent>;
}

export interface TaskDefinitionDialogsFinalContext {
  event:
    | HandleDefineNewConfirmationEvent
    | HandleDeleteTaskDefConfirmationEvent
    | HandleResetConfirmationEvent;
}
