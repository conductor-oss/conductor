import { assign, DoneInvokeEvent } from "xstate";
import { CreateAndDisplayApplicationMachineContext } from "./types";

export const persistApplicationKeys = assign<
  CreateAndDisplayApplicationMachineContext,
  DoneInvokeEvent<{ id: string; secret: string }>
>((_context, { data }) => {
  return {
    applicationAccessKey: {
      id: data.id,
      secret: data.secret,
    },
  };
});

export const persistApplicationId = assign<
  CreateAndDisplayApplicationMachineContext,
  DoneInvokeEvent<{ id: string }>
>((_context, { data }) => {
  return {
    applicationId: data.id,
  };
});

export const persistError = assign<
  CreateAndDisplayApplicationMachineContext,
  DoneInvokeEvent<{ message: string }>
>((_context, { data }) => {
  return { errorCreatingAppMessage: data.message };
});

export const clearError = assign<CreateAndDisplayApplicationMachineContext>(
  () => {
    return { errorCreatingAppMessage: undefined };
  },
);
