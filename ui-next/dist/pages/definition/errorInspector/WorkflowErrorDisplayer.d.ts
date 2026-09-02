import { FunctionComponent } from "react";
import { ValidationError } from "./state/types";
interface WorkflowErrorsDisplayerProps {
    workflowErrors: ValidationError[];
    expanded: boolean;
    onToggleExpand: () => void;
    title?: string;
    onClickReference?: (data: string) => void;
}
export declare const WorkflowErrorsDisplayer: FunctionComponent<WorkflowErrorsDisplayerProps>;
export {};
