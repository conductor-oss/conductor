import { beforeEach, describe, expect, it } from "vitest";
import { FEATURES } from "./flags";
import {
  isConnectedAppsEnabled,
  isIntegrationVisible,
  isServerlessIntegrationEnabled,
} from "./serverlessIntegrations";

const providers = [
  ["aws-lambda", FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED],
  ["azure-functions", FEATURES.SERVERLESS_AZURE_FUNCTIONS_ENABLED],
  ["google-cloud-functions", FEATURES.SERVERLESS_GCP_FUNCTIONS_ENABLED],
  ["firebase-functions", FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED],
] as const;

beforeEach(() => {
  window.conductor = {};
});

describe("serverless Connected Apps rollout", () => {
  it("defaults off and leaves existing non-plugin integrations available", () => {
    expect(isConnectedAppsEnabled()).toBe(false);
    expect(isServerlessIntegrationEnabled()).toBe(false);
    for (const type of ["aws", "gcp", "aws_sqs", "openai", "relational_db"]) {
      expect(isIntegrationVisible({ type, category: "CLOUD" })).toBe(true);
    }
  });

  it.each(Array.from({ length: 32 }, (_, mask) => [mask]))(
    "applies master AND individual provider flags for combination %i",
    (mask) => {
      const master = Boolean(mask & 1);
      window.conductor[FEATURES.CONNECTED_APPS_ENABLED] = master;
      providers.forEach(([type, flag], index) => {
        const enabled = Boolean(mask & (1 << (index + 1)));
        window.conductor[flag] = enabled ? "true" : "false";
        expect(isServerlessIntegrationEnabled(type)).toBe(
          master && enabled && type === "aws-lambda",
        );
        expect(isIntegrationVisible({ category: "MCP", type })).toBe(
          master && enabled && type === "aws-lambda",
        );
        // A stale server category must not bypass the provider gate.
        expect(isIntegrationVisible({ category: "CLOUD", type })).toBe(
          master && enabled && type === "aws-lambda",
        );
      });
      expect(isConnectedAppsEnabled()).toBe(master && Boolean(mask & 2));
    },
  );

  it("preserves unrelated MCP connections but never opts them into the embedded runtime", () => {
    window.conductor = Object.fromEntries(
      [
        FEATURES.CONNECTED_APPS_ENABLED,
        ...providers.map(([, flag]) => flag),
      ].map((flag) => [flag, true]),
    );
    for (const type of [
      "firebase-firestore-v5",
      "slack",
      "toString",
      "__proto__",
    ]) {
      expect(isServerlessIntegrationEnabled(type)).toBe(false);
      expect(isIntegrationVisible({ category: "MCP", type })).toBe(true);
    }
  });
});

it.each([false, true])(
  "preserves Slack, Postgres and Firestore with serverless master %s",
  (master) => {
    window.conductor = { [FEATURES.CONNECTED_APPS_ENABLED]: master };
    for (const type of ["slack", "postgres", "firebase-firestore-v5"]) {
      expect(isIntegrationVisible({ type, category: "MCP" })).toBe(true);
      expect(isServerlessIntegrationEnabled(type)).toBe(false);
    }
  },
);
