import { FEATURES } from "utils/flags";
import { IntegrationDef, IntegrationI } from "types/Integrations";
import { useFetch } from "utils/query";
import {
  isConnectedAppsEnabled,
  isServerlessIntegrationEnabled,
} from "utils/serverlessIntegrations";

export const useMCPIntegrations = () => {
  const integrationsUrl = `/integrations/def`;
  const providersUrl = `/integrations/provider?category=MCP&activeOnly=false`;

  const { data: integrationsData, isLoading: isLoadingIntegrations } = useFetch<
    IntegrationDef[]
  >(integrationsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: isConnectedAppsEnabled(),
  });
  const { data: providersData, isLoading: isLoadingProviders } = useFetch<
    IntegrationI[]
  >(providersUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: isConnectedAppsEnabled(),
  });

  // Cached responses can retain their identity while rollout flags change.
  // Derive the exposed providers on every render, rather than memoizing only data.
  const supportedIntegrations =
    integrationsData?.filter(
      (integration) =>
        integration.category === "MCP" &&
        isServerlessIntegrationEnabled(integration.type),
    ) || [];
  const combinedIntegrations = (providersData || [])
    .filter(
      (provider) =>
        provider.category === "MCP" &&
        provider.enabled &&
        isServerlessIntegrationEnabled(provider.type),
    )
    .map((integration) => ({
      ...integration,
      status: "active" as const,
      iconName: supportedIntegrations.find(
        (supportedIntegration) =>
          supportedIntegration.type === integration.type,
      )?.iconName,
    }));

  return {
    integrations: combinedIntegrations,
    isLoading: isLoadingIntegrations || isLoadingProviders,
  };
};

export const useMCPTools = (integrationName?: string) => {
  const toolsUrl = integrationName
    ? `/integrations/${integrationName}/def/apis`
    : "";

  const selectionAvailable =
    Boolean(integrationName) && isConnectedAppsEnabled();
  const { data: tools, isLoading } = useFetch(toolsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: selectionAvailable,
  });

  return {
    tools: selectionAvailable ? tools || [] : [],
    isLoading: isLoading,
  };
};
