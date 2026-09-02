import { FunctionComponent } from "react";
import { WorkflowExecution } from "types/Execution";
interface WorkflowIntrospectionProps {
    selectTask: (taskSel: {
        ref?: string;
        taskId?: string;
    }) => void;
    workflow: WorkflowExecution;
}
export declare const WorkflowIntrospection: FunctionComponent<WorkflowIntrospectionProps>;
export {};
