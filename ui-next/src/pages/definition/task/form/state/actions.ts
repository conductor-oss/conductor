import { assign, DoneInvokeEvent } from "xstate";
import {
  HandleChangeTaskFormEvent,
  TaskDefinitionFormContext,
} from "pages/definition/task/form/state/types";

export const handleChangeTask = assign<
  TaskDefinitionFormContext,
  HandleChangeTaskFormEvent
>(({ modifiedTaskDefinition }, { name, value }) => {
  // FIXME: Remove this patch after applying new inputs
  // Don't need to check array or object anymore
  const isArray = ["inputKeys", "outputKeys"].some((key) => key === name);
  const result = {
    ...modifiedTaskDefinition,
    [name]:
      isArray && typeof value === "object" && !Array.isArray(value)
        ? Object.keys(value!)
        : value,
  };

  return {
    modifiedTaskDefinition: result,
    modifiedTaskDefinitionString: JSON.stringify(result, null, 2),
  };
});

export const persistError = assign<
  TaskDefinitionFormContext,
  DoneInvokeEvent<{ error: { [key: string]: any }; numberOfError: number }>
>((context, { data }) => ({
  error: data.error,
  numberOfError: data.numberOfError,
}));

export const persistErrorMessage = assign<
  TaskDefinitionFormContext,
  DoneInvokeEvent<{ message: string }>
>((context, { data }) => ({
  popoverMessage: { severity: "error", text: data.message },
}));

export const resetForm = assign<TaskDefinitionFormContext>(
  ({ originTaskDefinition }) => ({
    modifiedTaskDefinition: originTaskDefinition,
    error: undefined,
    numberOfError: undefined,
  }),
);
