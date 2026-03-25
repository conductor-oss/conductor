import {
  HandleDefineNewConfirmationEvent,
  HandleDeleteTaskDefConfirmationEvent,
  HandleResetConfirmationEvent,
  TaskDefinitionDialogsContext,
} from "pages/definition/task/dialogs/state/types";

export const isConfirm = (
  _: TaskDefinitionDialogsContext,
  event:
    | HandleDefineNewConfirmationEvent
    | HandleDeleteTaskDefConfirmationEvent
    | HandleResetConfirmationEvent,
) => {
  return event.isConfirm;
};
