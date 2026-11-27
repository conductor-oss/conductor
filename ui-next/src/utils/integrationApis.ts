export type IntegrationApiDefinition = {
  api: string;
  inputSchema?: {
    data?: {
      "x-integration-configuration"?: Record<string, unknown>;
    };
  };
};

export function availableIntegrationApis(
  tools: IntegrationApiDefinition[],
  configuration?: Record<string, unknown>,
) {
  return tools.filter((tool) =>
    Object.entries(
      tool.inputSchema?.data?.["x-integration-configuration"] ?? {},
    ).every(([field, value]) => configuration?.[field] === value),
  );
}
