import fastDeepEqual from "fast-deep-equal";
import { DefinitionMachineContext } from "pages/definition/state/types";
import {
  addLocalCopyTime,
  extractKeyFromContext,
} from "pages/runWorkflow/runWorkflowUtils";
import { fetchContextNonHook, fetchWithContext } from "plugins/fetch";
import { queryClient } from "queryClient";
import { WorkflowDef } from "types/WorkflowDef";
import {
  fetchCloudTemplatesPreferCached,
  fetchWorkflowWithDependencies,
  ImportSummary,
} from "utils/cloudTemplates";
import { FEATURES, featureFlags } from "utils/flags";
import { logger } from "utils/logger";
import { getErrors } from "utils/utils";
import { getEnvVariables } from "../commonService";

export { fetchCloudTemplatesPreferCached };

const fetchContext = fetchContextNonHook();

export const fetchForImportedTemplateImportSummary = async (
  context: DefinitionMachineContext,
): Promise<ImportSummary | null> => {
  const { successfullyImportedWorkflowId: showImportSuccessfulDialog } =
    context;
  if (!showImportSuccessfulDialog) {
    return null;
  }
  const templates = await fetchCloudTemplatesPreferCached();
  const importedTemplate = templates.cloudTemplates.find(
    (t) => t.id === showImportSuccessfulDialog,
  );
  if (importedTemplate != null) {
    const importSummary = await fetchWorkflowWithDependencies(importedTemplate);
    return importSummary;
  }
  return null;
};

export const persistCopyInLocalStorage = (
  context: DefinitionMachineContext,
): Promise<string> => {
  const { workflowChanges, currentWf } = context;
  const isEqual = fastDeepEqual(currentWf, workflowChanges);

  if (!isEqual && context.workflowName != null) {
    const wfKey = extractKeyFromContext({
      workflowName: context.workflowName,
      currentVersion: context.currentVersion
        ? Number(context.currentVersion)
        : Number(context?.currentWf?.version),
      isNewWorkflow: context.isNewWorkflow,
    });

    localStorage.setItem(wfKey, JSON.stringify(workflowChanges));
    addLocalCopyTime(wfKey);

    logger.log("Saved to local storage");

    return Promise.resolve("Saved to local storage");
  }

  return Promise.resolve("Don't have any changes");
};

export const fetchSecrets = async ({
  authHeaders: headers,
}: DefinitionMachineContext) => {
  // OSS ships with `window.conductor.SECRETS: false` (see public/context.js).
  // Orkes / conductor-ui enables SECRETS so workflow validation can load names.
  if (!featureFlags.isEnabled(FEATURES.SECRETS)) {
    return [];
  }
  const url = `/secrets-v2`;
  try {
    const result = await queryClient.fetchQuery([fetchContext.stack, url], () =>
      fetchWithContext(url, fetchContext, { headers }),
    );
    return result;
  } catch {
    return {};
  }
};

export const fetchInputSchema = async ({
  authHeaders: headers,
  currentWf,
}: DefinitionMachineContext) => {
  if (!currentWf?.inputSchema?.name || !currentWf?.inputSchema?.version) {
    logger.warn("Missing input schema name or version in current workflow.");
    return {};
  }
  const schemaName = currentWf?.inputSchema?.name;
  const schemaVersion = currentWf?.inputSchema?.version;
  const url = `/schema/${schemaName}/${schemaVersion}`;
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );
    const properties = response?.data?.properties;
    if (!properties || typeof properties !== "object") {
      logger.warn("Schema response did not contain valid properties", response);
      return {};
    }
    return { schema: response?.data };
  } catch (error: any) {
    logger.error("Failed to fetch input schema:", {
      error,
      schemaName,
      schemaVersion,
    });
    const errorMessage = (await getErrors(error))?.message;

    if (errorMessage) {
      return Promise.reject({ message: errorMessage });
    }

    return {};
  }
};

export const fetchSecretsEndEnvironmentsList = async (
  context: DefinitionMachineContext,
) => {
  const secrets = await fetchSecrets(context);
  const envs = await getEnvVariables(context);

  return {
    secrets,
    envs: envs,
  };
};

export const refetchCurrentWorkflowVersionsService = async ({
  authHeaders: headers,
  workflowName,
}: DefinitionMachineContext) => {
  if (!workflowName) {
    return {};
  }

  const url = `/metadata/workflow?includeShared=false&name=${encodeURIComponent(
    workflowName,
  )}`;

  try {
    const result: WorkflowDef[] = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );
    const versions = result?.map((item) => item?.version) ?? [];
    return { versions };
  } catch {
    return {};
  }
};
