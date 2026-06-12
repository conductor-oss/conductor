import { assign, sendParent } from "xstate";
import { cancel } from "xstate/lib/actions";
import {
  TaskFormHeaderMachineContext,
  ChangeNameValueEvent,
  ValuesUpdatedEvent,
  StartEditingValuesEvent,
  StopEditingValuesEvent,
} from "./types";
import { FormMachineActionTypes } from "pages/definition/EditorPanel/TaskFormTab/state/types";

export const persistNameChanges = assign<
  TaskFormHeaderMachineContext,
  ChangeNameValueEvent
>({
  name: (_context, { value }) => value,
});

export const persistTaskReferenceNameChanges = assign<
  TaskFormHeaderMachineContext,
  ChangeNameValueEvent
>({
  taskReferenceName: (_context, { value }) => value,
});

export const persistChanges = assign<
  TaskFormHeaderMachineContext,
  ValuesUpdatedEvent
>({
  taskReferenceName: (_context, { taskReferenceName }) => taskReferenceName,
  name: (_context, { name }) => name,
  taskType: (_context, { taskType }) => taskType,
});

export const syncWithParent = sendParent(
  ({ name, taskReferenceName }: TaskFormHeaderMachineContext) => ({
    type: FormMachineActionTypes.UPDATE_TASK,
    taskChanges: { name, taskReferenceName },
  }),
);

const referenceNameGenerator = (
  name: string,
  taskReferenceName: string,
  suffix: string,
) => {
  if (taskReferenceName === `${name}_ref`) {
    return `${name}_${suffix}_ref`;
  } else {
    return `${name}_ref`;
  }
};

export const generateTaskReferenceAndName = assign<
  TaskFormHeaderMachineContext,
  any
>(({ name, taskReferenceName, taskType }) => {
  const suffix = Math.random().toString(36).substring(2, 5);
  const taskName = name ? name : `${taskType.toLowerCase()}`;
  const refName = name
    ? referenceNameGenerator(name, taskReferenceName, suffix)
    : `${taskType.toLowerCase()}_ref`;
  return {
    name: taskName,
    taskReferenceName: refName,
  };
});

export const cancelSyncWithParent = cancel("sync_val_with_parent");

export const startEditingValues = assign<
  TaskFormHeaderMachineContext,
  StartEditingValuesEvent
>({
  isEditingValues: true,
});

export const stopEditingValues = assign<
  TaskFormHeaderMachineContext,
  StopEditingValuesEvent
>({
  isEditingValues: false,
});
