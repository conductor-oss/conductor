import { useMemo } from "react";
import { useFetch } from "utils/query";

export const useMCPIntegrations = () => {
  const integrationsUrl = `/integrations/def`;
  const providersUrl = `/integrations/provider?category=MCP&activeOnly=false`;

  const { data: integrationsData, isLoading: isLoadingIntegrations } =
    useFetch(integrationsUrl);
  const { data: providersData, isLoading: isLoadingProviders } =
    useFetch(providersUrl);

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
    : null;

  const { data: tools, isLoading } = useFetch(toolsUrl || "");

  return {
    tools: tools || [],
    isLoading: isLoading,
  };
};
