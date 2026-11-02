import { beforeEach, expect, it, vi } from "vitest";
import { FEATURES } from "utils/flags";
import { fetchForIntegrationTools, fetchForMCPIntegrations } from "./services";
import { RichAddTaskMenuMachineContext } from "./types";

const { fetchRequest } = vi.hoisted(() => ({ fetchRequest: vi.fn() }));
vi.mock("plugins/fetch", () => ({
  fetchContextNonHook: () => ({ stack: "test" }),
  fetchWithContext: fetchRequest,
}));
vi.mock("queryClient", () => ({
  queryClient: {
    fetchQuery: (_key: unknown, request: () => unknown) => request(),
  },
}));
vi.mock("utils/logger", () => ({ logger: { info: vi.fn(), error: vi.fn() } }));

const context = {
  authHeaders: {},
  integrationDrillDownMenu: {
    selectedIntegration: { name: "lambda", integrationType: "aws-lambda" },
  },
} as RichAddTaskMenuMachineContext;

beforeEach(() => {
  window.conductor = {};
  fetchRequest.mockReset();
});

it("does not fetch Connected Apps when integration access or rollout is disabled", async () => {
  for (const config of [
    {},
    { [FEATURES.INTEGRATIONS]: true },
    {
      [FEATURES.CONNECTED_APPS_ENABLED]: true,
      [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
    },
  ]) {
    window.conductor = config;
    expect(await fetchForMCPIntegrations(context)).toEqual({
      supportedIntegrations: [],
      availableIntegrations: [],
    });
  }
  expect(fetchRequest).not.toHaveBeenCalled();
});

it("exposes advertised catalog independently of configured connections and excludes orphaned types", async () => {
  window.conductor = {
    [FEATURES.INTEGRATIONS]: true,
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
    [FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED]: true,
  };
  const entries = [
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
  ].map((type) => ({ type, category: "MCP", enabled: true }));
  fetchRequest.mockImplementation((url) =>
    Promise.resolve(
      url === "/integrations/def"
        ? entries
        : [
            entries[0],
            entries[1],
            { type: "unknown", category: "MCP", enabled: true },
            { ...entries[0], enabled: false },
          ],
    ),
  );
  const result = await fetchForMCPIntegrations(context);
  expect(
    result.supportedIntegrations.map((item: { type: string }) => item.type),
  ).toEqual(entries.map((entry) => entry.type));
  expect(result.availableIntegrations).toEqual(entries.slice(0, 2));
});

it("retains available catalog data when the saved connection request fails", async () => {
  window.conductor = {
    [FEATURES.INTEGRATIONS]: true,
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
  };
  fetchRequest.mockImplementation((url) =>
    url === "/integrations/def"
      ? Promise.resolve([{ type: "aws-lambda", category: "MCP" }])
      : Promise.reject(new Error("offline")),
  );
  const result = await fetchForMCPIntegrations(context);
  expect(result.supportedIntegrations).toHaveLength(1);
  expect(result.availableIntegrations).toEqual([]);
});

it("blocks stale drill-down requests for disabled providers", async () => {
  expect(await fetchForIntegrationTools(context)).toEqual([]);
  expect(fetchRequest).not.toHaveBeenCalled();
  window.conductor = {
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
  };
  fetchRequest.mockResolvedValue([{ api: "invoke" }]);
  expect(await fetchForIntegrationTools(context)).toEqual([{ api: "invoke" }]);
  expect(fetchRequest).toHaveBeenCalledWith(
    "/integrations/lambda/def/apis",
    expect.anything(),
    expect.anything(),
  );
});

it("does not admit saved providers if the authoritative catalog request fails", async () => {
  window.conductor = {
    [FEATURES.INTEGRATIONS]: true,
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
  };
  fetchRequest.mockImplementation((url) =>
    url === "/integrations/def"
      ? Promise.reject(new Error("offline"))
      : Promise.resolve([{ type: "slack", category: "MCP", enabled: true }]),
  );
  expect(await fetchForMCPIntegrations(context)).toEqual({
    supportedIntegrations: [],
    availableIntegrations: [],
  });
});

it("handles empty fulfilled catalog and saved-provider responses", async () => {
  window.conductor = {
    [FEATURES.INTEGRATIONS]: true,
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
  };
  fetchRequest.mockResolvedValue(undefined);
  expect(await fetchForMCPIntegrations(context)).toEqual({
    supportedIntegrations: [],
    availableIntegrations: [],
  });
});
