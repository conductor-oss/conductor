import { sendParent } from "xstate";
import { TaskDefinitionDialogsMachineType } from "pages/definition/task/dialogs/state/types";

export const notifyResetTask = sendParent({
  type: TaskDefinitionDialogsMachineType.CONFIRM_RESET_TASK,
});

export const notifyDeleteTask = sendParent({
  type: TaskDefinitionDialogsMachineType.CONFIRM_DELETE_TASK,
});

export const notifyGoToDefineNewTask = sendParent({
  type: TaskDefinitionDialogsMachineType.CONFIRM_GO_TO_DEFINE_NEW_TASK,
});
