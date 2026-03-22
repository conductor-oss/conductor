import { useSelector } from "@xstate/react";
import { EventFormMachineTypes } from "./types";

export const useEventHandlerFormActor = (actor: any) => {
  const { eventAsJson } = useSelector(actor, (state: any) => state.context);

  const { name, event, condition, actions, action, active, description } =
    eventAsJson;

  const { send } = actor;

  const handleChangeAction = (index: number, payload: any) => {
    send({
      type: EventFormMachineTypes.EDIT_ACTION,
      index,
      payload,
    });
  };

  const handleChange = (name: string, value: string | boolean) => {
    send({
      type: EventFormMachineTypes.INPUT_CHANGE,
      name,
      value,
    });
  };

  const handleAction = (action: string) => {
    send({
      type: EventFormMachineTypes.ADD_ACTION,
      actionType: action,
    });
  };

  const removeAction = (index: number) => {
    send({
      type: EventFormMachineTypes.DELETE_ACTION,
      index,
    });
  };

  // Logic in the Event task form is similar. Consider refactoring.
  const handleEventChange = (event: string) => handleChange("event", event);

  return [
    {
      action,
      name,
      condition,
      actions,
      event,
      active,
      description,
    },
    {
      handleChangeAction,
      handleChange,
      handleAction,
      removeAction,
      handleEventChange,
    },
  ] as const;
};
