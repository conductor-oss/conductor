import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { getErrors } from "../utils/utils";
import { HasAuthHeaders } from "types/common";
import { featureFlags, FEATURES } from "utils/flags";
import { TaskStatus } from "types/TaskStatus";

export interface DoWhileIterationOutput {
  /** 1-based iteration number. */
  iteration: number;
  /**
   * Map of inner-task reference name (without iteration suffix) to that task's output data.
   * null when summarized.
   */
  output: Record<string, unknown> | null;
  /**
   * Map of inner-task reference name (without iteration suffix) to that task's input data.
   * null when task records have been pruned (e.g. via keepLastN).
   */
  inputData: Record<string, unknown> | null;
  /**
   * Map of inner-task reference name to taskId for that task at this iteration.
   * null when task records have been pruned.
   */
  taskIds: Record<string, string> | null;
  /** true when the server replaced full output with a lightweight sentinel. */
  summarized: boolean;
  /**
   * Overall status of this iteration. Optional for backward compatibility with
   * older server versions that do not return this field.
   */
  status?: TaskStatus;
}

const fetchContext = fetchContextNonHook();
const isWorkflowIntrospectionEnabled = featureFlags.isEnabled(
  FEATURES.WORKFLOW_INTROSPECTION,
);

export const fetchDoWhileIterations = async ({
  authHeaders: headers,
  executionId,
  taskReferenceName,
  start = 0,
  count = 50,
}: HasAuthHeaders & {
  executionId: string;
  taskReferenceName: string;
  start?: number;
  count?: number;
}): Promise<{ totalHits: number; results: DoWhileIterationOutput[] }> => {
  const url = `/workflow/${executionId}/dowhile/${encodeURIComponent(taskReferenceName)}/iterations?start=${start}&count=${count}`;
  try {
    return await fetchWithContext(url, fetchContext, { headers });
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};

export const fetchExecution = async ({
  authHeaders: headers,
  executionId,
}: HasAuthHeaders & { executionId: string }) => {
  const url = `/workflow/${executionId}?summarize=true`;
  const introspectionUrl = `/workflow/introspection/records?workflowId=${executionId}`;

  try {
    const workflowExecution = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );

    if (isWorkflowIntrospectionEnabled) {
      workflowExecution.workflowIntrospection = await queryClient.fetchQuery(
        [fetchContext.stack, introspectionUrl],
        () => fetchWithContext(introspectionUrl, fetchContext, { headers }),
      );
    }

    return workflowExecution;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};
