import { FEATURES, featureFlags } from "./flags";

export const isConnectedAppsEnabled = (): boolean =>
  featureFlags.isEnabled(FEATURES.CONNECTED_APPS_ENABLED);

/** Compatibility helper: provider availability comes from the backend catalog. */
export const isServerlessIntegrationEnabled = (type?: string): boolean =>
  isConnectedAppsEnabled() && typeof type === "string" && type.length > 0;

/** Connected Apps share the master control; native integration categories are unchanged. */
export const isIntegrationVisible = (integration: {
  category?: string;
  type?: string;
}): boolean =>
  integration.category === "MCP"
    ? isServerlessIntegrationEnabled(integration.type)
    : true;
