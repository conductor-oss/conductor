import {
  UPDATE_VARIABLES_ACTION,
  START_WORKFLOW_ACTION,
  TERMINATE_WORKFLOW_ACTION,
  COMPLETE_TASK_ACTION,
  FAIL_TASK_ACTION,
  NEW_EVENT_HANDLER_TEMPLATE,
} from "../../eventHandlerSchema";
import { Action, EventFormMachineContext } from "./types";
import { assign } from "xstate";
import { adjust } from "utils/array";

export const handleInputChange = assign((context: any, event: any) => {
  const { name, value } = event;
  return {
    ...context,
    eventAsJson: {
      ...context.eventAsJson,
      [name]: value !== undefined ? value : "",
    },
  };
});

export const persistNewAction = assign({
  eventAsJson: (context: any, event: any) => {
    const { actionType } = event;
    const { actions } = context.eventAsJson;
    switch (actionType) {
      case Action.COMPLETE_TASK:
        return {
          ...context.eventAsJson,
          actions: [COMPLETE_TASK_ACTION, ...actions],
        };
      case Action.TERMINATE_WORKFLOW:
        return {
          ...context.eventAsJson,
          actions: [TERMINATE_WORKFLOW_ACTION, ...actions],
        };
      case Action.UPDATE_WORKFLOW_VARIABLES:
        return {
          ...context.eventAsJson,
          actions: [UPDATE_VARIABLES_ACTION, ...actions],
        };
      case Action.FAIL_TASK:
        return {
          ...context.eventAsJson,
          actions: [FAIL_TASK_ACTION, ...actions],
        };
      case Action.START_WORKFLOW:
        return {
          ...context.eventAsJson,
          actions: [START_WORKFLOW_ACTION, ...actions],
        };
      default:
        return context.eventAsJson;
    }
  },
});

export const removeAction = assign({
  eventAsJson: (context: any, event: any) => {
    const index = event.index;
    const newActions = [...context.eventAsJson.actions];
    newActions.splice(index, 1);
    return {
      ...context.eventAsJson,
      actions: newActions,
    };
  },
});

export const editAction = assign({
  eventAsJson: (context: any, event: any) => {
    const { index, payload } = event;
    return {
      ...context.eventAsJson,
      actions: adjust(
        index,
        () => ({ ...payload }),
        context.eventAsJson.actions,
      ),
    };
  },
});

export const resetForm = assign<EventFormMachineContext>({
  eventAsJson: (context) => {
    return context.originalSource;
  },
});

export const resetFormToNewDefinition = assign(() => {
  return {
    originalSource: NEW_EVENT_HANDLER_TEMPLATE,
    eventAsJson: NEW_EVENT_HANDLER_TEMPLATE,
  };
});
