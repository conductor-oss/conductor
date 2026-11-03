/** Provider availability comes from the backend catalog, not rollout flags. */
export const isServerlessIntegrationEnabled = (type?: string): boolean =>
  typeof type === "string" && type.length > 0;

export const isIntegrationVisible = (integration: {
  category?: string;
  type?: string;
}): boolean =>
  integration.category === "MCP"
    ? isServerlessIntegrationEnabled(integration.type)
    : true;
