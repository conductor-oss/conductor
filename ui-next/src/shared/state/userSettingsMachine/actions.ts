import { assign, DoneInvokeEvent } from "xstate";
import {
  UserSettingsMachineContext,
  SetFirstWorkflowExecutedEvent,
  AddDismissedMessageEvent,
  SetDismissAllMessagesEvent,
} from "./types";

export const hydrateFromStorage = assign<
  UserSettingsMachineContext,
  DoneInvokeEvent<Partial<UserSettingsMachineContext>>
>((context, event) => {
  const loadedData = event.data;
  return {
    firstWorkflowExecuted:
      loadedData.firstWorkflowExecuted ?? context.firstWorkflowExecuted,
    dismissedMessages:
      loadedData.dismissedMessages ?? context.dismissedMessages,
    dismissAllMessages:
      loadedData.dismissAllMessages ?? context.dismissAllMessages,
    isShowingConfettiThisSession: false,
  };
});

export const persistFirstWorkflowExecuted = assign<
  UserSettingsMachineContext,
  SetFirstWorkflowExecutedEvent
>({
  firstWorkflowExecuted: (_, event) => event.value,
  isShowingConfettiThisSession: (context, event) =>
    !context.firstWorkflowExecuted && event.value === true
      ? true
      : context.isShowingConfettiThisSession,
});

export const persistDismissedMessage = assign<
  UserSettingsMachineContext,
  AddDismissedMessageEvent
>({
  dismissedMessages: (context, event) => {
    if (context.dismissedMessages.includes(event.messageId)) {
      return context.dismissedMessages;
    }
    return [...context.dismissedMessages, event.messageId];
  },
});

export const persistDismissAllMessages = assign<
  UserSettingsMachineContext,
  SetDismissAllMessagesEvent
>({
  dismissAllMessages: (_, event) => event.value,
});
