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

it("filters both catalog and saved connections, including stale unrelated plugins", async () => {
  window.conductor = {
    [FEATURES.INTEGRATIONS]: true,
    [FEATURES.CONNECTED_APPS_ENABLED]: true,
    [FEATURES.SERVERLESS_AWS_LAMBDA_ENABLED]: true,
    [FEATURES.SERVERLESS_FIREBASE_FUNCTIONS_ENABLED]: true,
  };
  const entries = [
    "aws-lambda",
    "azure-functions",
    "firebase-functions",
    "firebase-firestore-v5",
  ].map((type) => ({ type, category: "MCP", enabled: true }));
  fetchRequest.mockImplementation((url) =>
    Promise.resolve(
      url === "/integrations/def"
        ? entries
        : [...entries, { ...entries[0], enabled: false }],
    ),
  );
  const result = await fetchForMCPIntegrations(context);
  expect(
    result.supportedIntegrations.map((item: { type: string }) => item.type),
  ).toEqual(["aws-lambda"]);
  expect(result.availableIntegrations).toEqual(result.supportedIntegrations);
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
