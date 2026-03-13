import {
  CancelConfirmSaveEvent,
  HandleChangeTaskDefinitionEvent,
  HandleDefineNewConfirmationEvent,
  HandleDeleteTaskDefConfirmationEvent,
  HandleResetConfirmationEvent,
  SaveTaskDefinitionEvent,
  SetResetConfirmationOpenEvent,
  TaskDefinitionMachineContext,
  TaskDefinitionMachineEventType,
  TaskDefinitionMachineState,
} from "pages/definition/task/state/types";
import fastDeepEqual from "fast-deep-equal";
import { DoneInvokeEvent, GuardMeta } from "xstate";

export const isChanged = (
  context: TaskDefinitionMachineContext,
  event: HandleChangeTaskDefinitionEvent | SetResetConfirmationOpenEvent,
) => {
  switch (event.type) {
    case TaskDefinitionMachineEventType.HANDLE_CHANGE_TASK_DEFINITION:
      return !fastDeepEqual(
        context.originTaskDefinitionString,
        event.modifiedTaskDefinitionString,
      );

    case TaskDefinitionMachineEventType.SET_RESET_CONFIRMATION_OPEN:
      return !fastDeepEqual(
        context.originTaskDefinition,
        context.modifiedTaskDefinition,
      );

    default:
      return false;
  }
};

export const isNewTaskDef = (context: TaskDefinitionMachineContext) =>
  context.isNewTaskDef;

export const isEditTaskDefinition = (context: TaskDefinitionMachineContext) =>
  !context.isNewTaskDef;

export const isConfirm = (
  _: TaskDefinitionMachineContext,
  event:
    | HandleDefineNewConfirmationEvent
    | HandleDeleteTaskDefConfirmationEvent
    | HandleResetConfirmationEvent,
) => {
  return event.isConfirm;
};

export const isSaveConfirmationOpen = (
  context: TaskDefinitionMachineContext,
  event: HandleDefineNewConfirmationEvent,
  {
    state,
  }: GuardMeta<TaskDefinitionMachineContext, HandleDefineNewConfirmationEvent>,
) => {
  //@ts-ignore
  return state.value?.dialog?.saveConfirmationOpen === "open";
};

export const lastTabWasForm = (context: TaskDefinitionMachineContext) =>
  context.lastSelectedTab === TaskDefinitionMachineState.FORM;

export const isFormModeHist = (
  context: TaskDefinitionMachineContext,
  event: CancelConfirmSaveEvent | SaveTaskDefinitionEvent,
  {
    state,
  }: GuardMeta<
    TaskDefinitionMachineContext,
    | HandleDefineNewConfirmationEvent
    | SaveTaskDefinitionEvent
    | DoneInvokeEvent<any>
  >,
) => {
  return !!state.history?.matches("form");
};

export const isContinueCreate = (context: TaskDefinitionMachineContext) =>
  context.isContinueCreate;
