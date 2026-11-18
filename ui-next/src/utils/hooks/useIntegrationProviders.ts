import { useMemo } from "react";
import { FEATURES } from "utils/flags";
import { useFetch } from "utils/query";

export const useIntegrationProviders = () => {
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

type IntegrationApiDefinition = {
  api: string;
  inputSchema?: {
    data?: {
      "x-integration-configuration"?: Record<string, unknown>;
    };
  };
};

export const useIntegrationApis = (
  integrationName?: string,
  configuration?: Record<string, unknown>,
) => {
  const toolsUrl = integrationName
    ? `/integrations/${integrationName}/def/apis`
    : "";

  const { data: tools, isLoading } = useFetch<IntegrationApiDefinition[]>(
    toolsUrl,
    {
      enterpriseApiFeature: FEATURES.INTEGRATIONS,
      when: Boolean(integrationName),
      keepPreviousData: false,
    },
  );

  // Match schema requirements against saved settings; unrestricted operations stay available.
  const availableTools = useMemo(
    () =>
      (tools || []).filter((tool) =>
        Object.entries(
          tool.inputSchema?.data?.["x-integration-configuration"] || {},
        ).every(([field, value]) => configuration?.[field] === value),
      ),
    [tools, configuration],
  );

  return {
    tools: availableTools,
    isLoading: isLoading,
  };
};
