import { WorkflowDef } from "types/WorkflowDef";
interface ShareWorkflowDialogProps {
    onClose: () => void;
    onSuccess: () => void;
    selectedWorkflow: WorkflowDef;
}
declare const ShareWorkflowDialog: ({ onClose, onSuccess, selectedWorkflow, }: ShareWorkflowDialogProps) => import("react").JSX.Element;
export default ShareWorkflowDialog;
