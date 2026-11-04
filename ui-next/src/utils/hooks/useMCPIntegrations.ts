import { useMemo } from "react";
import { FEATURES } from "utils/flags";
import { useFetch } from "utils/query";
import {
  connectedAppOperationPath,
  isConnectedAppDefinition,
} from "utils/connectedApps";

export const useMCPIntegrations = (connectedApp = false) => {
  const integrationsUrl = `/integrations/def`;
  const providersUrl = `/integrations/provider?category=${connectedApp ? "CLOUD" : "MCP"}&activeOnly=false`;

  const {
    data: integrationsData,
    isLoading: isLoadingIntegrations,
    error: integrationsError,
  } = useFetch(integrationsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
  });
  const {
    data: providersData,
    isLoading: isLoadingProviders,
    error: providersError,
  } = useFetch(providersUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
  });

  const combinedIntegrations = useMemo(() => {
    if (!providersData) return [];

    const supportedIntegrations =
      integrationsData?.filter((integration: any) =>
        connectedApp
          ? isConnectedAppDefinition(integration)
          : integration.category === "MCP",
      ) || [];

    const availableIntegrations =
      providersData?.filter((provider: any) =>
        connectedApp
          ? supportedIntegrations.some(
              (definition: any) => definition.type === provider.type,
            )
          : provider.category === "MCP",
      ) || [];

    // Combine both arrays with status information
    const combined = [
      ...availableIntegrations.map((integration: any) => ({
        ...integration,
        status:
          connectedApp && integration.enabled === false
            ? ("inactive" as const)
            : ("active" as const),
        iconName: supportedIntegrations.find(
          (supportedIntegration: any) =>
            supportedIntegration.type === integration.type,
        )?.iconName,
      })),
    ];

    return combined;
  }, [integrationsData, providersData, connectedApp]);

  return {
    integrations:
      integrationsError || providersError ? [] : combinedIntegrations,
    isLoading: isLoadingIntegrations || isLoadingProviders,
    error: integrationsError || providersError,
  };
};

export const useMCPTools = (integrationName?: string) => {
  const toolsUrl = integrationName
    ? connectedAppOperationPath(integrationName)
    : "";

  const {
    data: tools,
    isLoading,
    error,
  } = useFetch(toolsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: Boolean(integrationName) && !integrationName?.includes("${"),
    keepPreviousData: false,
  });

  return {
    tools:
      integrationName && !integrationName.includes("${") && !error
        ? tools || []
        : [],
    isLoading: isLoading,
    error,
  };
};
