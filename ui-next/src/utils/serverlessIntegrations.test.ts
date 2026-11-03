import { beforeEach, expect, it } from "vitest";
import { isIntegrationVisible } from "./serverlessIntegrations";
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
  "shows advertised MCP provider %s without rollout configuration",
  (type) => {
    expect(isIntegrationVisible({ type, category: "MCP" })).toBe(true);

    expect(isIntegrationVisible({ type, category: "MCP" })).toBe(true);
  },
);
it("rejects malformed MCP types and preserves native integrations", () => {
  for (const type of ["aws", "gcp", "openai", "relational_db"]) {
    expect(isIntegrationVisible({ type, category: "CLOUD" })).toBe(true);
  }

  expect(isIntegrationVisible({ category: "MCP" })).toBe(false);
  expect(isIntegrationVisible({ type: "", category: "MCP" })).toBe(false);
});
