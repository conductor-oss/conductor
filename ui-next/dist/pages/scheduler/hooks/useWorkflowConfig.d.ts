export interface UseWorkflowConfigReturn {
    workflowNames: string[];
    workflowVersions: string[];
    workflowInputTemplate: string;
    setWorkflowType: (workflowType: string) => {
        workflowVersions: string[];
        workflowInputTemplate: string;
    };
    setWorkflowVersion: (workflowVersion: string | null, workflowType: string | null) => {
        workflowInputTemplate: string;
    };
}
export declare function useWorkflowConfig(workflowDefByVersions: any, currentWorkflowType: string | null, currentWorkflowVersions: string[], currentWorkflowInputTemplate: string, agentNames?: string[]): UseWorkflowConfigReturn;
