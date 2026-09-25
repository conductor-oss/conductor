import { assign, DoneInvokeEvent } from "xstate";
import {
  HandleChangeTaskConfigEvent,
  SelectHostEvent,
  SelectTaskEvent,
  ServiceMethodsMachineContext,
} from "./types";

export const persistSelectedService = assign<
  ServiceMethodsMachineContext,
  DoneInvokeEvent<any>
>((_context, { data }) => {
  // For gRPC services, pre-select serviceURI as the host so the dropdown
  // always shows a value on first selection (gRPC has no multi-env discovery).
  const selectedHost =
    data?.type === "gRPC" ? (data?.serviceURI ?? null) : null;
  return {
    selectedService: data,
    selectedHost,
  };
});

export const persistSelectedHost = assign<
  ServiceMethodsMachineContext,
  SelectHostEvent
>((_context, { data }) => {
  return {
    selectedHost: data,
  };
});

export const persistServices = assign<
  ServiceMethodsMachineContext,
  DoneInvokeEvent<any>
>((_context, { data }) => {
  return {
    services: data,
  };
});

export const persistSelectedMethod = assign<
  ServiceMethodsMachineContext,
  DoneInvokeEvent<any>
>((_context, { data }) => {
  return {
    selectedMethod: data,
  };
});

export const persistSelectedSchema = assign<
  ServiceMethodsMachineContext,
  DoneInvokeEvent<any>
>((_context, { data }) => {
  return {
    selectedSchema: data,
  };
});

export const persistCurrentTaskDefName = assign<
  ServiceMethodsMachineContext,
  SelectTaskEvent
>((_context, { taskDefName }) => {
  return {
    currentTaskDefName: taskDefName,
  };
});

export const persistTaskDefinition = assign<
  ServiceMethodsMachineContext,
  DoneInvokeEvent<any>
>((_context, { data }) => {
  return {
    currentTaskDefinition: data,
    modifiedTaskDef: data,
  };
});

export const persistModifiedTaskDef = assign<
  ServiceMethodsMachineContext,
  HandleChangeTaskConfigEvent
>((context, { name, value }) => {
  const updatedTaskDef = {
    ...context.modifiedTaskDef,
    [name]: value,
  };
  return {
    modifiedTaskDef: updatedTaskDef,
  };
});

export const resetModifiedTaskDef = assign<
  ServiceMethodsMachineContext,
  HandleChangeTaskConfigEvent
>(({ currentTaskDefinition }) => {
  return {
    modifiedTaskDef: currentTaskDefinition,
  };
});
