import { DoneInvokeEvent, assign } from "xstate";
import {
  SetTaskDomainEvent,
  UpdateTaskVariablesEvent,
  TestTaskButtonMachineContext,
} from "./types";
import { Execution } from "types/Execution";

export const setTaskDomain = assign<
  TestTaskButtonMachineContext,
  SetTaskDomainEvent
>((_, { domain }) => ({
  taskDomain: domain,
}));

export const persistTaskChanges = assign<
  TestTaskButtonMachineContext,
  UpdateTaskVariablesEvent
>((_, { inputParameters }) => ({
  taskChanges: inputParameters,
}));

export const persistExecutionId = assign<
  TestTaskButtonMachineContext,
  DoneInvokeEvent<string>
>({
  testExecutionId: (_context, { data }) => data,
});

export const persistTestedTaskExecutionResult = assign<
  TestTaskButtonMachineContext,
  DoneInvokeEvent<Execution>
>((_context, { data }) => ({ testedTaskExecutionResult: data }));
