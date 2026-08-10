import { eventFormMachine } from "../FormComponent/state/machine";
import { createMachine } from "xstate";
import {
  SaveEventHandlerMachineEventTypes,
  SaveEventHandlerEvents,
  SaveEventHandlerMachineContext,
  SaveEventHandlerStates,
} from "./types";

import * as actions from "./actions";
import * as guards from "./guards";
import * as services from "./services";
import { tryToJson } from "utils";

export const saveEventHandlerMachine = createMachine<
  SaveEventHandlerMachineContext,
  SaveEventHandlerEvents
>(
  {
    id: "saveEventHandlerMachine",
    predictableActionArguments: true,
    initial: SaveEventHandlerStates.FETCH_EVENT_HANDLER_DEFINITION,
    context: {
      originalSource: "",
      editorChanges: "",
      isNewEventHandler: false,
      eventHandlerName: "",
      authHeaders: {},
      message: "",
      couldNotParseJson: false,
    },
    // Handle SAVED_SUCCESSFUL and SAVED_CANCELLED at the top level so they can be received from any state
    on: {
      [SaveEventHandlerMachineEventTypes.SAVED_SUCCESSFUL]: {
        actions: [], // No-op, just to receive the event so it can be detected
      },
      [SaveEventHandlerMachineEventTypes.SAVED_CANCELLED]: {
        actions: [], // No-op, just to receive the event so it can be detected
      },
    },
    states: {
      [SaveEventHandlerStates.IDLE]: {
        on: {
          [SaveEventHandlerMachineEventTypes.SAVE_EVT]: {
            target: SaveEventHandlerStates.CONFIRM_SAVE,
          },
          [SaveEventHandlerMachineEventTypes.EDIT_EVT]: {
            actions: ["editChanges"],
          },
          [SaveEventHandlerMachineEventTypes.CLEAR_ERROR_MESSAGE]: {
            target: SaveEventHandlerStates.IDLE,
            actions: ["clearErrorMessage"],
          },
        },
        initial: SaveEventHandlerStates.FORM,
        states: {
          [SaveEventHandlerStates.EDITOR]: {
            on: {
              [SaveEventHandlerMachineEventTypes.TOGGLE_FORM_EDITOR_EVT]: {
                target: SaveEventHandlerStates.FORM,
              },
              [SaveEventHandlerMachineEventTypes.RESET_EVT]: {
                target: `.${SaveEventHandlerStates.CONFIRM_RESET}`,
              },
              [SaveEventHandlerMachineEventTypes.DELETE_EVT]: {
                target: `.${SaveEventHandlerStates.CONFIRM_DELETE}`,
              },
              [SaveEventHandlerMachineEventTypes.NEW_EVENT_HANDLER_REQUEST]: {
                target: `.${SaveEventHandlerStates.CONFIRM_NEW}`,
              },
              [SaveEventHandlerMachineEventTypes.EDIT_DEBOUNCE_EVT]: {
                actions: ["cancelDebounceEditChanges", "debounceEditEvent"],
              },
            },
            initial: SaveEventHandlerStates.IDLE,
            states: {
              [SaveEventHandlerStates.IDLE]: {},

              [SaveEventHandlerStates.CONFIRM_RESET]: {
                on: {
                  [SaveEventHandlerMachineEventTypes.RESET_CONFIRM_EVT]: {
                    actions: ["revertToOriginalSource"],
                    target: SaveEventHandlerStates.IDLE,
                  },
                  [SaveEventHandlerMachineEventTypes.BACK_TO_IDLE]: {
                    target: SaveEventHandlerStates.IDLE,
                  },
                },
              },
              [SaveEventHandlerStates.CONFIRM_DELETE]: {
                on: {
                  [SaveEventHandlerMachineEventTypes.DELETE_CONFIRM_EVT]: {
                    target: SaveEventHandlerStates.DELETE_EVENT_HANDLER,
                  },
                  [SaveEventHandlerMachineEventTypes.BACK_TO_IDLE]: {
                    target: SaveEventHandlerStates.IDLE,
                  },
                },
              },
              [SaveEventHandlerStates.DELETE_EVENT_HANDLER]: {
                invoke: {
                  src: "deleteEventHandler",
                  id: "delete-event-handler",
                  onDone: {
                    target: SaveEventHandlerStates.IDLE,
                    actions: ["goBackToEventHandlersIndex"],
                  },
                  onError: {
                    target: SaveEventHandlerStates.IDLE,
                    actions: ["showErrorMessage"],
                  },
                },
              },
              [SaveEventHandlerStates.CONFIRM_NEW]: {
                on: {
                  [SaveEventHandlerMachineEventTypes.CONFIRM_NEW_EVENT]: {
                    target: SaveEventHandlerStates.IDLE,
                    actions: ["resetToNewDefinition", "redirectToNew"],
                  },
                  [SaveEventHandlerMachineEventTypes.BACK_TO_IDLE]: {
                    target: SaveEventHandlerStates.IDLE,
                  },
                },
              },
            },
          },
          [SaveEventHandlerStates.FORM]: {
            on: {
              [SaveEventHandlerMachineEventTypes.TOGGLE_FORM_EDITOR_EVT]: {
                actions: "forwardEventToFormMachine",
              },
              [SaveEventHandlerMachineEventTypes.SAVE_EVT]: {
                actions: "forwardEventToFormMachine",
              },
              [SaveEventHandlerMachineEventTypes.RESET_EVT]: {
                target: `.${SaveEventHandlerStates.CONFIRM_RESET}`,
              },
              [SaveEventHandlerMachineEventTypes.DELETE_EVT]: {
                target: `.${SaveEventHandlerStates.CONFIRM_DELETE}`,
              },
              [SaveEventHandlerMachineEventTypes.NEW_EVENT_HANDLER_REQUEST]: {
                target: `.${SaveEventHandlerStates.CONFIRM_NEW}`,
              },
            },
            invoke: {
              id: "eventFormMachine",
              src: eventFormMachine,
              data: (context: SaveEventHandlerMachineContext) => {
                const eventAsJson = tryToJson(context.editorChanges);
                const originalSource = tryToJson(context.originalSource);
                return {
                  eventAsJson,
                  originalSource,
                };
              },
              onDone: [
                {
                  cond: (__context, { data }) =>
                    data.reason === SaveEventHandlerMachineEventTypes.SAVE_EVT,
                  target: `#saveEventHandlerMachine.${SaveEventHandlerStates.CONFIRM_SAVE}`,
                  actions: "persistFormChanges",
                },
                {
                  target: SaveEventHandlerStates.EDITOR,
                  actions: "persistFormChanges",
                },
              ],
            },
            initial: SaveEventHandlerStates.IDLE,
            states: {
              [SaveEventHandlerStates.IDLE]: {},
              [SaveEventHandlerStates.CONFIRM_RESET]: {
                on: {
                  [SaveEventHandlerMachineEventTypes.RESET_CONFIRM_EVT]: {
                    actions: "forwardEventToFormMachine",
                    target: SaveEventHandlerStates.IDLE,
                  },
                  [SaveEventHandlerMachineEventTypes.BACK_TO_IDLE]: {
                    target: SaveEventHandlerStates.IDLE,
                  },
                },
              },
              [SaveEventHandlerStates.CONFIRM_DELETE]: {
                on: {
                  [SaveEventHandlerMachineEventTypes.DELETE_CONFIRM_EVT]: {
                    target: SaveEventHandlerStates.DELETE_EVENT_HANDLER,
                  },
                  [SaveEventHandlerMachineEventTypes.BACK_TO_IDLE]: {
                    target: SaveEventHandlerStates.IDLE,
                  },
                },
              },
              [SaveEventHandlerStates.DELETE_EVENT_HANDLER]: {
                invoke: {
                  src: "deleteEventHandler",
                  id: "delete-event-handler",
                  onDone: {
                    target: SaveEventHandlerStates.IDLE,
                    actions: ["goBackToEventHandlersIndex"],
                  },
                  onError: {
                    target: SaveEventHandlerStates.IDLE,
                    actions: ["showErrorMessage"],
                  },
                },
              },
              [SaveEventHandlerStates.CONFIRM_NEW]: {
                on: {
                  [SaveEventHandlerMachineEventTypes.CONFIRM_NEW_EVENT]: {
                    actions: ["forwardEventToFormMachine", "redirectToNew"],
                    target: SaveEventHandlerStates.IDLE,
                  },
                  [SaveEventHandlerMachineEventTypes.BACK_TO_IDLE]: {
                    target: SaveEventHandlerStates.IDLE,
                  },
                },
              },
            },
          },
        },
      },
      [SaveEventHandlerStates.FETCH_EVENT_HANDLER_DEFINITION]: {
        invoke: {
          src: "fetchEventHandler",
          onDone: {
            actions: ["updateEventHandler", "updateOriginalSource"],
            target: SaveEventHandlerStates.IDLE,
          },
          onError: {
            actions: ["showErrorMessage"],
          },
        },
      },
      [SaveEventHandlerStates.CONFIRM_SAVE]: {
        on: {
          [SaveEventHandlerMachineEventTypes.CONFIRM_SAVE_EVT]: [
            {
              target: SaveEventHandlerStates.CREATE_EVENT_HANDLER,
              cond: "isNewOrNameChanged",
            },
            { target: SaveEventHandlerStates.UPDATE_EVENT_HANDLER },
          ],
          [SaveEventHandlerMachineEventTypes.CANCEL_SAVE_EVT]: {
            target: SaveEventHandlerStates.IDLE,
            actions: ["sendSavedCancelled"],
          },
          [SaveEventHandlerMachineEventTypes.EDIT_EVT]: {
            actions: ["editChanges"],
          },
          [SaveEventHandlerMachineEventTypes.EDIT_DEBOUNCE_EVT]: {
            actions: ["cancelDebounceEditChanges", "debounceEditEvent"],
          },
        },
      },

      [SaveEventHandlerStates.CREATE_EVENT_HANDLER]: {
        invoke: {
          src: "createEventHandler",
          id: "create-event-handler",
          onDone: {
            actions: [
              "updateEventHandlerName",
              "pushToHistory",
              "showSaveSuccessMessage",
              "persistIsNewEventHandler",
              "sendSavedSuccessful",
            ],
            target: SaveEventHandlerStates.FETCH_EVENT_HANDLER_DEFINITION,
          },
          onError: [
            {
              target: SaveEventHandlerStates.IDLE,
              actions: ["showErrorMessage"],
            },
          ],
        },
      },
      [SaveEventHandlerStates.UPDATE_EVENT_HANDLER]: {
        invoke: {
          src: "updateEventHandler",
          id: "update-event-handler",
          onDone: {
            actions: [
              "updateEventHandlerName",
              "showSaveSuccessMessage",
              "sendSavedSuccessful",
            ],
            target: SaveEventHandlerStates.FETCH_EVENT_HANDLER_DEFINITION,
          },
          onError: {
            target: SaveEventHandlerStates.IDLE,
            actions: ["showErrorMessage"],
          },
        },
      },
      [SaveEventHandlerStates.SAVED_SUCCESSFULLY]: {
        type: "final",
        data: ({ editorChanges, isNewEventHandler, eventHandlerName }) => ({
          saved: true,
          editorChanges,
          isNewEventHandler,
          eventHandlerName,
        }),
      },
      [SaveEventHandlerStates.SAVED_CANCELLED]: {
        type: "final",
        data: ({ editorChanges }) => ({
          saved: false,
          editorChanges,
        }),
      },
    },
  },
  { actions: actions as any, guards, services },
);
