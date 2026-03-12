import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { getErrors } from "../utils/utils";
import { HasAuthHeaders } from "types/common";
import { featureFlags, FEATURES } from "utils/flags";

const fetchContext = fetchContextNonHook();
const isWorkflowIntrospectionEnabled = featureFlags.isEnabled(
  FEATURES.WORKFLOW_INTROSPECTION,
);

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
