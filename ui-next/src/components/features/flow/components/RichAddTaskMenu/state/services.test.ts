import { fetchForMCPIntegrations, fetchForIntegrationTools } from "./services";
import { RichAddTaskMenuMachineContext } from "./types";
const mocks = vi.hoisted(() => ({
  fetch: vi.fn(),
  enabled: vi.fn(),
  query: vi.fn(),
}));
vi.mock("plugins/fetch", () => ({
  fetchWithContext: mocks.fetch,
  fetchContextNonHook: () => ({ stack: "test-stack" }),
}));
vi.mock("queryClient", () => ({ queryClient: { fetchQuery: mocks.query } }));
vi.mock("utils/flags", () => ({
  FEATURES: {
    INTEGRATIONS: "INTEGRATIONS",
    TASK_VISIBILITY: "TASK_VISIBILITY",
  },
  featureFlags: { isEnabled: mocks.enabled, getValue: () => "READ" },
}));
vi.mock("utils/logger", () => ({ logger: { info: vi.fn(), error: vi.fn() } }));
const context = {
  authHeaders: { "X-Authorization": "test-only" },
  integrationDrillDownMenu: {
    selectedIntegration: { name: "production/firebase" },
  },
} as unknown as RichAddTaskMenuMachineContext;
beforeEach(() => {
  mocks.enabled.mockReturnValue(true);
  mocks.query.mockReset();
  mocks.query.mockImplementation((_key, callback) => callback());
  mocks.fetch.mockReset();
});
describe("Connected App task menu metadata", () => {
  it("filters definitions and saved instances by the backend packaged plugin catalog", async () => {
    mocks.fetch.mockImplementation((path: string) =>
      Promise.resolve(
        path === "/integrations/def"
          ? [
              {
                type: "plugin:new",
                category: "CLOUD",
                tags: ["packaged-plugin"],
              },
              { type: "legacy", category: "MCP" },
            ]
          : [
              { name: "production", type: "plugin:new" },
              { name: "legacy", type: "legacy" },
            ],
      ),
    );
    const result = await fetchForMCPIntegrations(context);
    expect(result.supportedIntegrations).toHaveLength(1);
    expect(result.availableIntegrations).toEqual([
      { name: "production", type: "plugin:new" },
    ]);
  });
  it("does not fetch when the existing integrations feature is disabled", async () => {
    mocks.enabled.mockReturnValue(false);
    expect(await fetchForMCPIntegrations(context)).toEqual({
      supportedIntegrations: [],
      availableIntegrations: [],
    });
    expect(mocks.fetch).not.toHaveBeenCalled();
  });
  it("surfaces forbidden metadata instead of returning a fake empty success", async () => {
    mocks.fetch.mockRejectedValue({ status: 403 });
    await expect(fetchForMCPIntegrations(context)).rejects.toEqual({
      message: "Error fetching integrations",
    });
  });
  it("uses stack and encoded saved integration in operation query keys", async () => {
    mocks.fetch.mockResolvedValue([{ api: "Invoke Function" }]);
    expect(await fetchForIntegrationTools(context)).toEqual([
      { api: "Invoke Function" },
    ]);
    expect(mocks.query).toHaveBeenCalledWith(
      ["test-stack", "/integrations/production%2Ffirebase/def/apis"],
      expect.any(Function),
    );
  });
});
