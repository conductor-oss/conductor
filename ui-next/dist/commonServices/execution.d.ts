import { HasAuthHeaders } from "types/common";
/**
 * Fetches the full workflow without any output summarization.
 * Used when the user explicitly disables the summarize toggle to see real
 * iteration data. Distinct from {@link fetchExecution} which uses
 * summarize=true to keep the initial page load lightweight.
 */
export declare const fetchExecutionFull: ({ authHeaders: headers, executionId, }: HasAuthHeaders & {
    executionId: string;
}) => Promise<any>;
export declare const fetchExecution: ({ authHeaders: headers, executionId, }: HasAuthHeaders & {
    executionId: string;
}) => Promise<any>;
/** Fetch an agent execution with server-aggregated descendant metrics. */
export declare const fetchAgentExecution: ({ authHeaders: headers, executionId, }: HasAuthHeaders & {
    executionId: string;
}) => Promise<any>;
