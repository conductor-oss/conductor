import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { getErrors } from "../utils/utils";
import { HasAuthHeaders } from "types/common";
import { featureFlags, FEATURES } from "utils/flags";
const fetchContext = fetchContextNonHook();
const isWorkflowIntrospectionEnabled = featureFlags.isEnabled(
  FEATURES.WORKFLOW_INTROSPECTION,
);

/**
 * Fetches the full workflow without any output summarization.
 * Used when the user explicitly disables the summarize toggle to see real
 * iteration data. Distinct from {@link fetchExecution} which uses
 * summarize=true to keep the initial page load lightweight.
 */
export const fetchExecutionFull = async ({
  authHeaders: headers,
  executionId,
}: HasAuthHeaders & { executionId: string }) => {
  const url = `/workflow/${executionId}?summarize=false`;
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
