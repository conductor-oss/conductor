import { WorkflowDef } from "types/WorkflowDef";
import { assign, DoneInvokeEvent, sendParent } from "xstate";
import { LocalCopyMachineContext, LocalCopyMachineEventTypes } from "./types";
import { WorkflowWithNoErrorsEvent } from "../../errorInspector/state";

export const storeLocalCopy = assign<
  LocalCopyMachineContext,
  DoneInvokeEvent<Partial<WorkflowDef>>
>({
  lastStoredVersion: (_ctxt, event) => event.data,
});

export const sendLocalChanges = sendParent<LocalCopyMachineContext, any>(
  (context) => ({
    type: LocalCopyMachineEventTypes.USE_LOCAL_COPY_WORKFLOW,
    workflow: context.lastStoredVersion,
  }),
);

export const persistLastStoredVersion = assign<
  LocalCopyMachineContext,
  WorkflowWithNoErrorsEvent
>((__context, { workflow }) => ({
  lastStoredVersion: workflow,
}));

export const cleanLocalChanges = assign<LocalCopyMachineContext>({
  lastStoredVersion: (__context) => undefined,
});
