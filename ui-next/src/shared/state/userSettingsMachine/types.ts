export interface UserSettingsMachineContext {
  firstWorkflowExecuted: boolean;
  dismissedMessages: string[];
  dismissAllMessages: boolean;
  isShowingConfettiThisSession: boolean;
}

export enum UserSettingsStates {
  INIT = "init",
  LOADING_FROM_STORAGE = "loadingFromStorage",
  READY = "ready",
  SHOWING_CONFETTI = "showingConfetti",
  CONFETTI_VISIBLE = "confettiVisible",
  SAVING_TO_STORAGE = "savingToStorage",
}

export enum UserSettingsEventTypes {
  SET_FIRST_WORKFLOW_EXECUTED = "SET_FIRST_WORKFLOW_EXECUTED",
  ADD_DISMISSED_MESSAGE = "ADD_DISMISSED_MESSAGE",
  SET_DISMISS_ALL_MESSAGES = "SET_DISMISS_ALL_MESSAGES",
}

export type SetFirstWorkflowExecutedEvent = {
  type: UserSettingsEventTypes.SET_FIRST_WORKFLOW_EXECUTED;
  value: boolean;
};

export type AddDismissedMessageEvent = {
  type: UserSettingsEventTypes.ADD_DISMISSED_MESSAGE;
  messageId: string;
};

export type SetDismissAllMessagesEvent = {
  type: UserSettingsEventTypes.SET_DISMISS_ALL_MESSAGES;
  value: boolean;
};

export type UserSettingsEvents =
  | SetFirstWorkflowExecutedEvent
  | AddDismissedMessageEvent
  | SetDismissAllMessagesEvent;
