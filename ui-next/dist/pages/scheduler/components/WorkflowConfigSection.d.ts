import { IdempotencyValuesProp } from "../../definition/RunWorkflow/state";
interface WorkflowConfigSectionProps {
    workflowType: string | null;
    setWorkflowType: (workflowType: string) => void;
    workflowVersion: string | null;
    setWorkflowVersion: (workflowVersion: string | null) => void;
    workflowVersions: string[];
    workflowNames: string[];
    agentNames?: string[];
    workflowInputTemplate: string;
    setWorkflowInputTemplate: (value: string) => void;
    workflowCorrelationId: string;
    setWorkflowCorrelationId: (value: string) => void;
    idempotencyValues: {
        idempotencyKey?: string;
        idempotencyStrategy?: any;
    };
    handleIdempotencyValues: (data: IdempotencyValuesProp) => void;
    errors?: any;
}
export declare function WorkflowConfigSection({ workflowType, setWorkflowType, workflowVersion, setWorkflowVersion, workflowVersions, workflowNames, agentNames, workflowInputTemplate, setWorkflowInputTemplate, workflowCorrelationId, setWorkflowCorrelationId, idempotencyValues, handleIdempotencyValues, errors, }: WorkflowConfigSectionProps): import("react").JSX.Element;
export {};
