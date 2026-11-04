import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { logger } from "utils/logger";
import {
  connectedAppOperationPath,
  isConnectedAppDefinition,
} from "utils/connectedApps";

import { featureFlags, FEATURES } from "utils/flags";
import { RichAddTaskMenuMachineContext } from "./types";

const fetchContext = fetchContextNonHook();

const taskVisibility = featureFlags.getValue(FEATURES.TASK_VISIBILITY, "READ");

export const fetchForTaskDefinitions = async ({
  authHeaders: headers,
}: RichAddTaskMenuMachineContext) => {
  const taskDefinitionsUrl = `/metadata/taskdefs?access=${taskVisibility}`;

  logger.info("Will search for task definitions", taskDefinitionsUrl);
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, taskDefinitionsUrl],
      () => fetchWithContext(taskDefinitionsUrl, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error("Fetching task list page", error);
    return Promise.reject({ message: "Error fetching task list page" });
  }
};

export const fetchForWorkflowDefinitions = async ({
  authHeaders: headers,
}: RichAddTaskMenuMachineContext) => {
  const workflowDefinitionUrl = `/metadata/workflow?short=true`;

  logger.info("Will search for workflow definitions", workflowDefinitionUrl);
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, workflowDefinitionUrl],
      () => fetchWithContext(workflowDefinitionUrl, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error("Fetching task list page", error);
    return Promise.reject({ message: "Error fetching task list page" });
  }
};

export const fetchForMCPIntegrations = async ({
  authHeaders: headers,
}: RichAddTaskMenuMachineContext) => {
  if (!featureFlags.isEnabled(FEATURES.INTEGRATIONS)) {
    return {
      supportedIntegrations: [],
      availableIntegrations: [],
    };
  }
  const integrationsUrl = `/integrations/def`;
  const providersUrl = `/integrations/provider?category=CLOUD&activeOnly=false`;

  try {
    const [definitions, providers] = await Promise.all([
      queryClient.fetchQuery([fetchContext.stack, integrationsUrl], () =>
        fetchWithContext(integrationsUrl, fetchContext, { headers }),
      ),
      queryClient.fetchQuery([fetchContext.stack, providersUrl], () =>
        fetchWithContext(providersUrl, fetchContext, { headers }),
      ),
    ]);
    const supportedIntegrations = (definitions || []).filter(
      isConnectedAppDefinition,
    );
    return {
      supportedIntegrations,
      availableIntegrations: (providers || []).filter(
        (provider: any) =>
          provider.enabled !== false &&
          supportedIntegrations.some(
            (definition: any) => definition.type === provider.type,
          ),
      ),
    };
  } catch (error) {
    logger.error("Fetching integrations", error);
    return Promise.reject({ message: "Error fetching integrations" });
  }
};

export const fetchForIntegrationTools = async ({
  authHeaders: headers,
  integrationDrillDownMenu: { selectedIntegration },
}: RichAddTaskMenuMachineContext) => {
  const toolsUrl = connectedAppOperationPath(selectedIntegration?.name || "");

  logger.info("Will search for integration tools", toolsUrl);
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, toolsUrl],
      () => fetchWithContext(toolsUrl, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error("Fetching tools", error);
    return Promise.reject({ message: "Error fetching tools" });
  }
};
