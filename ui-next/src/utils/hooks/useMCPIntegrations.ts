import { useMemo } from "react";
import { FEATURES } from "utils/flags";
import { useFetch } from "utils/query";

export const useMCPIntegrations = () => {
  const integrationsUrl = `/integrations/def`;
  const providersUrl = `/integrations/provider?category=MCP&activeOnly=false`;

  const { data: integrationsData, isLoading: isLoadingIntegrations } = useFetch(
    integrationsUrl,
    {
      enterpriseApiFeature: FEATURES.INTEGRATIONS,
    },
  );
  const { data: providersData, isLoading: isLoadingProviders } = useFetch(
    providersUrl,
    {
      enterpriseApiFeature: FEATURES.INTEGRATIONS,
    },
  );

  const combinedIntegrations = useMemo(() => {
    if (!providersData) return [];

    const supportedIntegrations =
      integrationsData?.filter(
        (integration: any) => integration.category === "MCP",
      ) || [];

    const availableIntegrations =
      providersData?.filter((provider: any) => provider.category === "MCP") ||
      [];

    // Combine both arrays with status information
    const combined = [
      ...availableIntegrations.map((integration: any) => ({
        ...integration,
        status: "active" as const,
        iconName: supportedIntegrations.find(
          (supportedIntegration: any) =>
            supportedIntegration.type === integration.type,
        )?.iconName,
      })),
    ];

    return combined;
  }, [integrationsData, providersData]);

  return {
    integrations: combinedIntegrations,
    isLoading: isLoadingIntegrations || isLoadingProviders,
  };
};

export const useMCPTools = (integrationName?: string) => {
  const toolsUrl = integrationName
    ? `/integrations/${integrationName}/def/apis`
    : "";

  const { data: tools, isLoading } = useFetch(toolsUrl, {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: Boolean(integrationName),
  });

  return {
    tools: tools || [],
    isLoading: isLoading,
  };
};
