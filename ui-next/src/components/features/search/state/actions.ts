import { assign, DoneInvokeEvent } from "xstate";
import { PersistSearchTermEvent, SearchMachineContext } from "./types";
import { WorkflowDef } from "types/WorkflowDef";

export const persistSearchTerm = assign<
  SearchMachineContext,
  PersistSearchTermEvent
>({
  searchTerm: (_, { searchTerm }) => searchTerm,
  maxSearchResults: (_, { count }) => count,
});

export const persistTaskNames = assign<
  SearchMachineContext,
  DoneInvokeEvent<{ name: string; description?: string }[]>
>({
  taskDefinitions: (_, { data }) => data,
});

export const persistWorkflowNames = assign<
  SearchMachineContext,
  DoneInvokeEvent<WorkflowDef[]>
>({
  workflowDefinitions: (_, { data }) => data,
});

export const persistScheduleNames = assign<
  SearchMachineContext,
  DoneInvokeEvent<string[]>
>({
  schedulers: (_, { data }) => data,
});

export const persistEventNames = assign<
  SearchMachineContext,
  DoneInvokeEvent<string[]>
>({
  events: (_, { data }) => data,
});

export const persistErrorMessage = assign<
  SearchMachineContext,
  DoneInvokeEvent<{ message: string }>
>({
  error: (_context, { data }) => ({ ...data, severity: "error" }),
});
