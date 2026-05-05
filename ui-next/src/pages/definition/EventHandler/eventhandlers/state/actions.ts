import _omit from "lodash/omit";
import _isEmpty from "lodash/isEmpty";
import { assign, DoneInvokeEvent, forwardTo, send } from "xstate";
import { cancel } from "xstate/lib/actions";
import { NEW_EVENT_HANDLER_TEMPLATE } from "../eventHandlerSchema";
import {
  SaveEventHandlerMachineEventTypes,
  SaveEventHandlerMachineContext,
  EditEvent,
  EditDebounceEvent,
  UpdateEventHandlerEvent,
  UpdateOriginalSourceEvent,
  ShowErrorMessageEvent,
  ClearErrorMessageEvent,
} from "./types";
import { tryToJson, logger } from "utils";

export const editChanges = assign<SaveEventHandlerMachineContext, EditEvent>(
  (_, { changes }) => {
    const isValidJSON = !!tryToJson(changes);
    if (!isValidJSON) {
      logger.info("Json is broken");
    }
    return {
      editorChanges: changes,
      couldNotParseJson: !isValidJSON,
    };
  },
);

export const debounceEditEvent = send<
  SaveEventHandlerMachineContext,
  EditDebounceEvent
>(
  (__, { changes }) => {
    return {
      type: SaveEventHandlerMachineEventTypes.EDIT_EVT,
      changes,
    };
  },
  { delay: 250, id: "debounce_edit_event" },
);

export const cancelDebounceEditChanges = cancel("debounce_edit_event");

export const updateEventHandlerName = assign<SaveEventHandlerMachineContext>(
  ({ editorChanges }) => {
    const eventHandlerJson = tryToJson<{ name: string }>(editorChanges);
    return {
      eventHandlerName: eventHandlerJson?.name,
    };
  },
);

export const updateEventHandler = assign<
  SaveEventHandlerMachineContext,
  UpdateEventHandlerEvent
>((__, { data: eventHandler }) => {
  const textVersion = JSON.stringify(eventHandler, null, 2);

  return {
    editorChanges: textVersion,
    originalSource: textVersion,
    isNewEventHandler: !eventHandler?.name,
  };
});

export const updateOriginalSource = assign<
  SaveEventHandlerMachineContext,
  UpdateOriginalSourceEvent
>(({ editorChanges }, { data: eventHandler }) => {
  const source = !_isEmpty(eventHandler)
    ? JSON.stringify(eventHandler, null, 2)
    : JSON.stringify(NEW_EVENT_HANDLER_TEMPLATE, null, 2);

  const newEditorChanges =
    !_isEmpty(editorChanges) && editorChanges !== "" ? editorChanges : source;

  return {
    originalSource: source,
    editorChanges: newEditorChanges,
  };
});

export const revertToOriginalSource = assign<SaveEventHandlerMachineContext>(
  ({ originalSource }) => {
    return {
      editorChanges: originalSource,
    };
  },
);

export const resetToNewDefinition = assign<SaveEventHandlerMachineContext>(
  () => {
    const source = JSON.stringify(NEW_EVENT_HANDLER_TEMPLATE, null, 2);
    return {
      originalSource: source,
      editorChanges: source,
    };
  },
);

export const showErrorMessage = assign<
  SaveEventHandlerMachineContext,
  ShowErrorMessageEvent
>((_, errorEvent) => {
  const message =
    errorEvent?.data?.message || errorEvent?.data?.originalError?.message;

  return {
    message,
  };
});

export const clearErrorMessage = assign<
  SaveEventHandlerMachineContext,
  ClearErrorMessageEvent
>(() => {
  return {
    message: "",
  };
});

export const forwardEventToFormMachine = forwardTo("eventFormMachine");

export const persistFormChanges = assign<
  SaveEventHandlerMachineContext,
  DoneInvokeEvent<{
    eventAsJson: {
      name?: string;
      event?: string;
      evaluatorType?: string;
      condition?: string;
      actions?: [];
    };
    reason: SaveEventHandlerMachineEventTypes;
  }>
>((__context, { data }) => ({
  editorChanges: JSON.stringify(_omit(data.eventAsJson, "action"), null, 2),
  reason: data.reason,
}));

export const persistIsNewEventHandler = assign<SaveEventHandlerMachineContext>(
  ({ eventHandlerName }) => ({ isNewEventHandler: !eventHandlerName }),
);

export const sendSavedSuccessful = send<SaveEventHandlerMachineContext, any>({
  type: SaveEventHandlerMachineEventTypes.SAVED_SUCCESSFUL,
});

export const sendSavedCancelled = send<SaveEventHandlerMachineContext, any>({
  type: SaveEventHandlerMachineEventTypes.SAVED_CANCELLED,
});
