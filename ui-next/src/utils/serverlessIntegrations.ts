import { FEATURES, featureFlags } from "./flags";

const providerFlags: Record<string, string> = {
  "aws-lambda": FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED,
  "azure-functions": FEATURES.SERVERLESS_AZURE_FUNCTIONS_ENABLED,
  "google-cloud-functions": FEATURES.SERVERLESS_GCP_FUNCTIONS_ENABLED,
  "firebase-functions": FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED,
};

/** The embedded Connected Apps rollout exposes only explicitly enabled providers. */
export const isServerlessIntegrationEnabled = (type?: string): boolean =>
  featureFlags.isEnabled(FEATURES.CONNECTED_APPS_ENABLED) &&
  typeof type === "string" &&
  type === "aws-lambda" &&
  featureFlags.isEnabled(providerFlags[type]);

export const isConnectedAppsEnabled = (): boolean =>
  Object.keys(providerFlags).some(isServerlessIntegrationEnabled);

/** Gate only the four embedded providers; unrelated MCP and cloud connections are unchanged. */
export const isIntegrationVisible = (integration: {
  category?: string;
  type?: string;
}): boolean =>
  typeof integration.type === "string" &&
  Object.hasOwn(providerFlags, integration.type)
    ? isServerlessIntegrationEnabled(integration.type)
    : true;
