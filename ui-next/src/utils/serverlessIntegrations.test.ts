import { beforeEach, expect, it } from "vitest";
import { FEATURES } from "./flags";
import {
  isConnectedAppsEnabled,
  isIntegrationVisible,
} from "./serverlessIntegrations";
beforeEach(() => {
  window.conductor = {};
});
const providers = [
  "aws-lambda",
  "aws-s3",
  "azure-functions",
  "azure-storage",
  "commonroom",
  "discourse",
  "github",
  "google-calendar",
  "google-cloud-functions",
  "google-cloud-storage",
  "google-docs",
  "google-drive",
  "google-sheets",
  "google-slides",
  "hubspot",
  "jira",
  "mysql",
  "notion",
  "postgres",
  "redis",
  "slack",
  "stripe-v2",
  "wordpress-com",
];
it.each(providers)(
  "gates advertised MCP provider %s only on the master",
  (type) => {
    expect(isIntegrationVisible({ type, category: "MCP" })).toBe(false);
    window.conductor = { [FEATURES.CONNECTED_APPS_ENABLED]: true };
    expect(isConnectedAppsEnabled()).toBe(true);
    expect(isIntegrationVisible({ type, category: "MCP" })).toBe(true);
    window.conductor[FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED] = false;
    expect(isIntegrationVisible({ type, category: "MCP" })).toBe(true);
  },
);
it("defaults off, rejects malformed MCP types, and preserves native integrations", () => {
  expect(isConnectedAppsEnabled()).toBe(false);
  for (const type of ["aws", "gcp", "openai", "relational_db"]) {
    expect(isIntegrationVisible({ type, category: "CLOUD" })).toBe(true);
  }
  window.conductor[FEATURES.CONNECTED_APPS_ENABLED] = true;
  expect(isIntegrationVisible({ category: "MCP" })).toBe(false);
  expect(isIntegrationVisible({ type: "", category: "MCP" })).toBe(false);
});
