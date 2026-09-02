export interface WorkflowSizeResponse {
    sizeBytes: number;
    limitBytes: number;
    ratio: number;
}
export declare function WorkflowSizeIndicator({ sizeData, }: {
    sizeData: WorkflowSizeResponse;
}): import("react").JSX.Element;
