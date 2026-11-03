import { FEATURES } from "utils/flags";
import { IntegrationDef, IntegrationI } from "types/Integrations";
import { useFetch } from "utils/query";
import { isServerlessIntegrationEnabled } from "utils/serverlessIntegrations";

export const useMCPIntegrations = () => {
  const integrationsUrl = `/integrations/def`;
  const providersUrl = `/integrations/provider?category=MCP&activeOnly=false`;

  const { data: integrationsData, isLoading: isLoadingIntegrations } = useFetch<
    IntegrationDef[]
  >(integrationsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
  });
  const { data: providersData, isLoading: isLoadingProviders } = useFetch<
    IntegrationI[]
  >(providersUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
  });

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
        supportedIntegrations.some(
          (definition) => definition.type === provider.type,
        ),
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

  const selectionAvailable = Boolean(integrationName);
  const { data: tools, isLoading } = useFetch(toolsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: selectionAvailable,
  });

  return {
    tools: selectionAvailable ? tools || [] : [],
    isLoading: isLoading,
  };
};
