import { removeCopyFromStorage } from "pages/runWorkflow/runWorkflowUtils";
import { WorkflowDef } from "types/WorkflowDef";
export { removeCopyFromStorage };
export declare const consumeCopyFromLocalStorage: (context: any) => Promise<Partial<WorkflowDef> | null>;
