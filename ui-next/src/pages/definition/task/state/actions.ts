import { TaskDefinitionFormContext } from "pages/definition/task/form/state";
import { newTaskTemplate } from "templates/JSONSchemaWorkflow";
import { TaskDefinitionDto } from "types/TaskDefinition";
import { logger, randomChars } from "utils";
import { assign, DoneInvokeEvent, send } from "xstate";
import { cancel } from "xstate/lib/actions";
import {
  DebounceHandleChangeTaskDefinitionEvent,
  HandleChangeTaskDefinitionEvent,
  SetInputParametersEvent,
  SetSaveConfirmationOpenEvent,
  SetTaskDefinitionEvent,
  SetTaskDomainEvent,
  TaskDefinitionMachineContext,
  TaskDefinitionMachineEventType,
  TaskDefinitionMachineState,
} from "./types";

export const handleChangeTaskDefinition = assign<
  TaskDefinitionMachineContext,
  HandleChangeTaskDefinitionEvent
>(({ modifiedTaskDefinition, couldNotParseJson }, event) => {
  let result = modifiedTaskDefinition;
  let parsingResultWentWell = couldNotParseJson;
  try {
    result = JSON.parse(event.modifiedTaskDefinitionString);
    parsingResultWentWell = true;
  } catch {
    logger.info("Json is broken");
    parsingResultWentWell = false;
  }

  return {
    modifiedTaskDefinitionString: event.modifiedTaskDefinitionString,
    modifiedTaskDefinition: result,
    couldNotParseJson: !parsingResultWentWell,
  };
});

export const debounceChangeTaskDefinition = send<
  TaskDefinitionMachineContext,
  DebounceHandleChangeTaskDefinitionEvent
>(
  (__, { modifiedTaskDefinitionString }) => {
    return {
      type: TaskDefinitionMachineEventType.HANDLE_CHANGE_TASK_DEFINITION,
      modifiedTaskDefinitionString,
    };
  },
  { delay: 10, id: "debounceChangeTaskDefinition" },
);

export const cancelDebounceChangeTaskDefinition = cancel(
  "debounceChangeTaskDefinition",
);

export const persistTaskDefinitionByName = assign<
  TaskDefinitionMachineContext,
  DoneInvokeEvent<TaskDefinitionDto>
>((context, { data }) => {
  const jsonString = JSON.stringify(data, null, 2);

  return {
    originTaskDefinitionString: jsonString, // Not necesary
    modifiedTaskDefinitionString: jsonString, // Not necesary
    originTaskDefinition: data,
    modifiedTaskDefinition: data,
  };
});

export const persistError = assign<
  TaskDefinitionFormContext,
  DoneInvokeEvent<{ error: { [key: string]: any }; numberOfError: number }>
>((context, { data }) => ({
  error: data.error,
  numberOfError: data.numberOfError,
}));

export const changeIsContinueCreate = assign<
  TaskDefinitionMachineContext,
  SetSaveConfirmationOpenEvent
>({
  isContinueCreate: (_, { isContinueCreate }) => isContinueCreate,
});

export const updateOriginTaskDefinition = assign<
  TaskDefinitionMachineContext,
  DoneInvokeEvent<TaskDefinitionDto>
>((context) => {
  const newVersion = context.modifiedTaskDefinition;
  const jsonString = JSON.stringify(newVersion, null, 2);

  return {
    originTaskDefinition: newVersion,
    originTaskDefinitionString: jsonString, // Not necesary
    modifiedTaskDefinitionString: jsonString, // Not necesary
    modifiedTaskDefinition: newVersion,
  };
});

// Maybe not needed
export const setIsEditTaskDef = assign<TaskDefinitionMachineContext>(() => ({
  isNewTaskDef: false,
}));

export const resetContext = assign<TaskDefinitionMachineContext>(
  ({ originTaskDefinition }) => {
    const jsonString = JSON.stringify(originTaskDefinition, null, 2);

    return {
      isContinueCreate: undefined,
      originTaskDefinitionString: jsonString,
      modifiedTaskDefinitionString: jsonString,
      modifiedTaskDefinition: originTaskDefinition,
      originTaskDefinition,
      popoverMessage: null,
      error: undefined,
      numberOfError: undefined,
    };
  },
);

export const prepareNewTaskContext = assign<TaskDefinitionMachineContext>(
  ({ user, authHeaders }) => {
    const initTaskDefinition = {
      ...newTaskTemplate(user?.id || "example@email.com"),
      name: `task-${randomChars(6)}`,
    } as Partial<TaskDefinitionDto>;
    const jsonString = JSON.stringify(initTaskDefinition, null, 2);

    return {
      authHeaders,
      user,
      bulkMode: false,
      isContinueCreate: undefined,
      isNewTaskDef: true,
      originTaskDefinitionString: jsonString,
      modifiedTaskDefinitionString: jsonString,
      modifiedTaskDefinition: initTaskDefinition,
      originTaskDefinition: initTaskDefinition,
      popoverMessage: null,
      error: undefined,
      numberOfError: undefined,
    };
  },
);

export const setInputParameters = assign<
  TaskDefinitionMachineContext,
  SetInputParametersEvent
>((_, { inputParameters }) => ({
  testInputParameters: inputParameters,
}));

export const setTaskDomain = assign<
  TaskDefinitionMachineContext,
  SetTaskDomainEvent
>((_, { domain }) => ({
  testTaskDomain: domain,
}));

export const persistWorkflowId = assign<
  TaskDefinitionMachineContext,
  DoneInvokeEvent<string>
>({
  testTaskWorkflowId: (_context, { data }) => data,
});

export const syncDataFromFormMachine = assign<
  TaskDefinitionMachineContext,
  DoneInvokeEvent<TaskDefinitionFormContext & { reason: string }>
>((_, { data }) => {
  return {
    modifiedTaskDefinition: data.modifiedTaskDefinition,
    modifiedTaskDefinitionString: JSON.stringify(
      data.modifiedTaskDefinition,
      null,
      2,
    ),
    error: data.error,
    numberOfError: data.numberOfError,
    lastSelectedTab: TaskDefinitionMachineState.FORM,
  };
});

export const cleanLastSelectedTab = assign<TaskDefinitionMachineContext>({
  lastSelectedTab: undefined,
});

export const setNameOnOriginTaskDefinition = assign<
  TaskDefinitionFormContext,
  SetTaskDefinitionEvent
>((context, { name, isNew }) => {
  const taskDefintionDto: TaskDefinitionDto = {
    ...context.modifiedTaskDefinition,
    name,
  };
  return {
    originTaskDefinition: taskDefintionDto,
    isNewTaskDef: isNew,
  };
});
