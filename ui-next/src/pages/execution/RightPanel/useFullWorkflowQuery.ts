import { fetchExecutionFull } from "commonServices";
import { useQuery } from "react-query";
import { AuthHeaders } from "types/common";

/**
 * Shared query for fetching the full (non-summarized) workflow execution.
 * Both DoWhileIteration and InlineTaskIterations use the same cache key so
 * only one network request is made regardless of which component triggers it.
 */
export function useFullWorkflowQuery(
  executionId: string | undefined,
  authHeaders: AuthHeaders | undefined,
  enabled: boolean,
) {
  return useQuery(
    ["workflow-full", executionId],
    () =>
      fetchExecutionFull({
        authHeaders: authHeaders as any,
        executionId: executionId!,
      }),
    {
      enabled: enabled && !!executionId,
      staleTime: Infinity,
    },
  );
}
