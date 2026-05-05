import { ActorRef } from "xstate";
import {
  TaskDefinitionFormEventType,
  TaskDefinitionFormMachineEvent,
} from "./types";
import { useActor, useSelector } from "@xstate/react";
import { ChangeEvent } from "react";

export const useTaskDefinitionFormActor = (
  actor: ActorRef<TaskDefinitionFormMachineEvent>,
) => {
  const [state, send] = useActor(actor);
  const { modifiedTaskDefinition, originTaskDefinition, error } = state.context;

  const isEditingName = useSelector(actor, (state) =>
    state.matches("ready.editingField.name"),
  );

  const isEditingDescription = useSelector(actor, (state) =>
    state.matches("ready.editingField.description"),
  );

  const handleChangeTaskForm = (
    value: number | string | Record<string, string> | null,
    event?: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    if (event) {
      const { name } = event.target;

      send({
        type: TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM,
        name,
        value,
      });
    }
  };

  const handleChangeInputForm = (
    name: string,
    value:
      | number
      | string
      | Record<string, string | number>
      | boolean
      | null
      | undefined,
  ) => {
    send({
      type: TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM,
      name,
      value,
    });
  };

  const handleChangeParameters = ({
    name,
    value,
  }: {
    name: string;
    value: Record<string, string> | string[];
  }) => {
    send({
      type: TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM,
      name,
      value,
    });
  };

  const setEditingFieldForm = (name: string) => {
    send({
      type: TaskDefinitionFormEventType.SET_EDITING_FORM_FIELD,
      name,
    });
  };

  return [
    {
      error,
      isEditingName,
      isEditingDescription,
      modifiedTaskDefinition,
      originTaskDefinition,
    },
    {
      handleChangeTaskForm,
      handleChangeParameters,
      setEditingFieldForm,
      handleChangeInputForm,
    },
  ] as const;
};
