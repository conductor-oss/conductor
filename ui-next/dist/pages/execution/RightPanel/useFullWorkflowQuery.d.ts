import { AuthHeaders } from "types/common";
/**
 * Shared query for fetching the full (non-summarized) workflow execution.
 * Both DoWhileIteration and InlineTaskIterations use the same cache key so
 * only one network request is made regardless of which component triggers it.
 */
export declare function useFullWorkflowQuery(executionId: string | undefined, authHeaders: AuthHeaders | undefined, enabled: boolean): import("react-query").UseQueryResult<any, unknown>;
