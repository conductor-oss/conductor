import { ActorRef } from "xstate";
import {
  TaskDefinitionDialogsMachineEvent,
  TaskDefinitionDialogsMachineType,
} from "pages/definition/task/dialogs/state/types";
import { useActor } from "@xstate/react";

export const useTaskDefinitionDialogs = (
  actor: ActorRef<TaskDefinitionDialogsMachineEvent>,
) => {
  const [state, send] = useActor(actor);

  const confirmationDialogResetOpen = state.matches(
    "confirmationDialogResetOpen",
  );

  const confirmationDialogDefineNewOpen = state.matches(
    "confirmationDialogDefineNewOpen",
  );

  const confirmationDialogDeleteOpen = state.matches(
    "confirmationDialogDeleteOpen",
  );

  const modifiedTaskDefinition = state.context.modifiedTaskDefinition;

  const handleResetConfirmation = (isConfirm: boolean) => {
    send({
      type: TaskDefinitionDialogsMachineType.HANDLE_RESET_CONFIRMATION,
      isConfirm,
    });
  };

  const handleDefineNewConfirmation = (isConfirm: boolean) => {
    send({
      type: TaskDefinitionDialogsMachineType.HANDLE_DEFINE_NEW_CONFIRMATION,
      isConfirm,
    });
  };

  const handleDeleteTaskDefConfirmation = (isConfirm: boolean) => {
    send({
      type: TaskDefinitionDialogsMachineType.HANDLE_DELETE_TASK_DEF_CONFIRMATION,
      isConfirm,
    });
  };

  return [
    {
      confirmationDialogDefineNewOpen,
      confirmationDialogDeleteOpen,
      confirmationDialogResetOpen,
      modifiedTaskDefinition,
    },
    {
      handleDefineNewConfirmation,
      handleDeleteTaskDefConfirmation,
      handleResetConfirmation,
    },
  ] as const;
};
