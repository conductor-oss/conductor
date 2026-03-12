import { StartSubWfNameVersionMachineContext } from "./types";

import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";

import { logger } from "utils/logger";
import { getUniqueWorkflowsWithVersions } from "utils/workflow";
import { WORKFLOW_METADATA_BASE_URL_SHORT } from "utils/constants/api";

const fetchContext = fetchContextNonHook();

export const fetchWfNamesAndVersions = async ({
  authHeaders: headers,
}: StartSubWfNameVersionMachineContext) => {
  const url = WORKFLOW_METADATA_BASE_URL_SHORT;
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );
    return getUniqueWorkflowsWithVersions(response);
  } catch (error) {
    logger.error("Fetching Wf short", error);
    return Promise.reject({ message: "Error fetching wf short" });
  }
};
