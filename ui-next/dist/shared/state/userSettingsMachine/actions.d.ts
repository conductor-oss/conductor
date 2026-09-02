import { DoneInvokeEvent } from "xstate";
import { UserSettingsMachineContext, SetFirstWorkflowExecutedEvent, AddDismissedMessageEvent, SetDismissAllMessagesEvent } from "./types";
export declare const hydrateFromStorage: import("xstate").AssignAction<UserSettingsMachineContext, DoneInvokeEvent<Partial<UserSettingsMachineContext>>, DoneInvokeEvent<Partial<UserSettingsMachineContext>>>;
export declare const persistFirstWorkflowExecuted: import("xstate").AssignAction<UserSettingsMachineContext, SetFirstWorkflowExecutedEvent, SetFirstWorkflowExecutedEvent>;
export declare const persistDismissedMessage: import("xstate").AssignAction<UserSettingsMachineContext, AddDismissedMessageEvent, AddDismissedMessageEvent>;
export declare const persistDismissAllMessages: import("xstate").AssignAction<UserSettingsMachineContext, SetDismissAllMessagesEvent, SetDismissAllMessagesEvent>;
