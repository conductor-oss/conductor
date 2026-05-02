import { fetchContextNonHook, fetchWithContext } from "plugins/fetch";
import { HasAuthHeaders } from "types/common";
import type { EnvironmentVariables } from "types/EnvVariables";
import { FEATURES, featureFlags } from "utils/flags";
import { AUTH_HEADER_NAME, logger } from "utils";
import {
  WORKFLOW_METADATA_BASE_URL,
  WORKFLOW_METADATA_SHORT_URL,
} from "utils/constants/api";
import { queryClient } from "../../queryClient";

const fetchContext = fetchContextNonHook();

export const refetchAllWorkflowDefinitions = async ({
  authHeaders: headers,
}: HasAuthHeaders) => {
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, WORKFLOW_METADATA_SHORT_URL],
      () =>
        fetchWithContext(WORKFLOW_METADATA_SHORT_URL, fetchContext, {
          headers,
        }),
    );
    return response;
  } catch {
    logger.error("Refetching for workflow definitions");
    return Promise.reject({ text: "Unable to fetch for definitions" });
  }
};

export const getWorkflowDefinitionByNameAndVersion = async ({
  name,
  version,
  authHeaders: headers,
}: {
  name: string;
  version: number;
  authHeaders: { [AUTH_HEADER_NAME]?: string };
}) => {
  try {
    const path = `${WORKFLOW_METADATA_BASE_URL}/${encodeURIComponent(
      name,
    )}?version=${version}`;

    return await queryClient.fetchQuery([fetchContext.stack, path], () =>
      fetchWithContext(path, fetchContext, { headers }),
    );
  } catch {
    logger.error("Re-fetching for workflow definition by name and version");
    return Promise.reject({ text: "Unable to fetch for definition" });
  }
};

export const getEnvVariables = async ({
  authHeaders: headers,
}: HasAuthHeaders): Promise<Record<string, string>> => {
  // OSS: `INTEGRATIONS: false` in public/context.js — no hosted /environment API.
  if (!featureFlags.isEnabled(FEATURES.INTEGRATIONS)) {
    return {};
  }
  const url = `/environment`;
  try {
    const result: EnvironmentVariables[] = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );
    return result.reduce(
      (acc, { name, value }) => ({ ...acc, [name]: value }),
      {},
    );
  } catch {
    return {};
  }
};
