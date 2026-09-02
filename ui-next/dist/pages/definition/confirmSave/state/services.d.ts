import { removeCopyFromStorage } from "pages/definition/ConfirmLocalCopyDialog/state";
import { SaveWorkflowMachineContext } from "./types";
export { removeCopyFromStorage };
export declare const resolveAgentSnapshots: ({ editorChanges, authHeaders, }: SaveWorkflowMachineContext) => Promise<string>;
export declare const createWorkflow: ({ editorChanges, authHeaders }: SaveWorkflowMachineContext, __: any) => Promise<any>;
export declare const updateWorkflow: ({ editorChanges, authHeaders, isNewVersion }: SaveWorkflowMachineContext, __: any) => Promise<any>;
export declare const refetchAllDefinitionsOfCurrentWorkflow: ({ authHeaders: headers, workflowName, }: SaveWorkflowMachineContext) => Promise<{}>;
