import { createMachine } from "xstate";
import {
  UserSettingsMachineContext,
  UserSettingsStates,
  UserSettingsEvents,
  UserSettingsEventTypes,
} from "./types";
import * as actions from "./actions";
import * as services from "./services";
import * as guards from "./guards";

export const userSettingsMachine = createMachine<
  UserSettingsMachineContext,
  UserSettingsEvents
>(
  {
    id: "userSettingsMachine",
    predictableActionArguments: true,
    initial: UserSettingsStates.INIT,
    context: {
      firstWorkflowExecuted: false,
      dismissedMessages: [],
      dismissAllMessages: false,
      isShowingConfettiThisSession: false,
    },
    states: {
      [UserSettingsStates.INIT]: {
        always: {
          target: UserSettingsStates.LOADING_FROM_STORAGE,
        },
      },
      [UserSettingsStates.LOADING_FROM_STORAGE]: {
        invoke: {
          src: "loadFromLocalStorage",
          onDone: {
            target: UserSettingsStates.READY,
            actions: "hydrateFromStorage",
          },
          onError: {
            target: UserSettingsStates.READY,
          },
        },
      },
      [UserSettingsStates.READY]: {
        on: {
          [UserSettingsEventTypes.SET_FIRST_WORKFLOW_EXECUTED]: [
            {
              cond: "isFirstWorkflowCompleted",
              actions: "persistFirstWorkflowExecuted",
              target: UserSettingsStates.SHOWING_CONFETTI,
            },
            {
              actions: "persistFirstWorkflowExecuted",
              target: UserSettingsStates.SAVING_TO_STORAGE,
            },
          ],
          [UserSettingsEventTypes.ADD_DISMISSED_MESSAGE]: {
            actions: "persistDismissedMessage",
            target: UserSettingsStates.SAVING_TO_STORAGE,
          },
          [UserSettingsEventTypes.SET_DISMISS_ALL_MESSAGES]: {
            actions: "persistDismissAllMessages",
            target: UserSettingsStates.SAVING_TO_STORAGE,
          },
        },
      },
      [UserSettingsStates.SHOWING_CONFETTI]: {
        always: {
          target: UserSettingsStates.SAVING_TO_STORAGE,
        },
      },
      [UserSettingsStates.SAVING_TO_STORAGE]: {
        invoke: {
          src: "saveToLocalStorage",
          onDone: {
            target: UserSettingsStates.READY,
          },
          onError: {
            target: UserSettingsStates.READY,
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services: services as any,
    guards: guards as any,
  },
);
