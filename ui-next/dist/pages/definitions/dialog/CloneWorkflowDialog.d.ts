import { WorkflowDef } from "types/WorkflowDef";
export interface CloneWorkflowDialogProps {
    selectedWorkflow: WorkflowDef;
    workflowList: WorkflowDef[];
    onClose: () => void;
    onSuccess: () => void;
}
declare const CloneWorkflowDialog: ({ selectedWorkflow, onClose, onSuccess, workflowList, }: CloneWorkflowDialogProps) => import("react").JSX.Element;
export default CloneWorkflowDialog;
