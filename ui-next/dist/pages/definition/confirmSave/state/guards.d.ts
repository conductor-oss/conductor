import { DoneInvokeEvent } from "xstate";
import { SaveWorkflowMachineContext } from "./types";
export declare const isNewOrNameChanged: ({ isNewWorkflow, currentWf, editorChanges, }: SaveWorkflowMachineContext) => boolean;
export declare const returnedConflict: (_context: SaveWorkflowMachineContext, { data }: DoneInvokeEvent<{
    status: number;
}>) => boolean;
export declare const isNewVersion: (_context: SaveWorkflowMachineContext) => boolean | undefined;
