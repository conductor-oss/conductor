import { useSelector } from "@xstate/react";
import { EventFormMachineTypes } from "./types";

export const useEventHandlerFormActor = (actor: any) => {
  const { eventAsJson } = useSelector(actor, (state: any) => state.context);

  const { name, event, condition, actions, active, description } = eventAsJson;

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

  const moveAction = (from: number, to: number) => {
    send({
      type: EventFormMachineTypes.MOVE_ACTION,
      from,
      to,
    });
  };

  // Logic in the Event task form is similar. Consider refactoring.
  const handleEventChange = (event: string) => handleChange("event", event);

  return [
    {
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
      moveAction,
      handleEventChange,
    },
  ] as const;
};
