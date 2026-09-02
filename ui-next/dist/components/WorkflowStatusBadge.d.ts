import { FunctionComponent } from "react";
import { WorkflowExecutionStatus } from "types/Execution";
export interface WorkflowStatusBadgeProps {
    status: WorkflowExecutionStatus;
}
declare const WorkflowStatusBadge: FunctionComponent<WorkflowStatusBadgeProps>;
export default WorkflowStatusBadge;
