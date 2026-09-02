import { FunctionComponent } from "react";
import { ExtendedFieldsData, FieldsData, RunWorkflowParamType } from "./state";
interface RunWorkflowHistoryTableProps {
    workflowName?: string;
    fillReRunWfFields: (data: RunWorkflowParamType) => void;
    workflowHistory: ExtendedFieldsData[];
    setWorkflowHistory: (data: FieldsData[]) => void;
}
export declare const RunWorkflowHistoryTable: FunctionComponent<RunWorkflowHistoryTableProps>;
export {};
