import {
  UserSettingsMachineContext,
  SetFirstWorkflowExecutedEvent,
} from "./types";

export const isFirstWorkflowCompleted = (
  context: UserSettingsMachineContext,
  event: SetFirstWorkflowExecutedEvent,
) => {
  return !context.firstWorkflowExecuted && event.value === true;
};
