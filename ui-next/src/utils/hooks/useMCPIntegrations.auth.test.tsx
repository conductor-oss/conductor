import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { ReactNode } from "react";
import { useMCPTools } from "./useMCPIntegrations";
const state = vi.hoisted(() => ({
  token: null as string | null,
  stack: "stack-a",
  integrations: true,
  fetch: vi.fn(),
}));
vi.mock("plugins/fetch", () => ({
  useFetchContext: () => ({ ready: true, stack: state.stack }),
  fetchWithContext: state.fetch,
}));
vi.mock("plugins/registry", () => ({
  pluginRegistry: { getAccessToken: () => state.token },
}));
vi.mock("components/features/auth/tokenManagerJotai", () => ({
  getAccessToken: () => null,
}));
vi.mock("react-router", () => ({
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: "/workflowDef/mock/1" }),
}));
vi.mock("utils/flags", () => ({
  FEATURES: {
    INTEGRATIONS: "INTEGRATIONS",
    ACCESS_MANAGEMENT: "ACCESS_MANAGEMENT",
  },
  featureFlags: {
    isEnabled: (feature: string) =>
      feature === "INTEGRATIONS" ? state.integrations : true,
  },
}));
const wrapper = ({ children }: { children: ReactNode }) => (
  <QueryClientProvider client={client}>{children}</QueryClientProvider>
);
let client: QueryClient;
beforeEach(() => {
  client = new QueryClient({
    defaultOptions: { queries: { retry: false, cacheTime: 0 } },
  });
  state.token = null;
  state.stack = "stack-a";
  state.integrations = true;
  state.fetch.mockReset();
  state.fetch.mockResolvedValue([{ api: "Invoke Function" }]);
});
afterEach(() => client.clear());
describe("Connected App operations with the real useFetch auth framework", () => {
  it("waits for authentication and scopes metadata cache by stack", async () => {
    const { result, rerender } = renderHook(() => useMCPTools("prod/app"), {
      wrapper,
    });
    expect(state.fetch).not.toHaveBeenCalled();
    state.token = "mock-local-token";
    rerender();
    await waitFor(() =>
      expect(result.current.tools).toEqual([{ api: "Invoke Function" }]),
    );
    expect(state.fetch).toHaveBeenCalledWith(
      "/integrations/prod%2Fapp/def/apis",
      expect.objectContaining({ stack: "stack-a" }),
      expect.objectContaining({
        headers: { "X-Authorization": "mock-local-token" },
      }),
    );
    state.stack = "stack-b";
    rerender();
    await waitFor(() => expect(state.fetch).toHaveBeenCalledTimes(2));
    expect(
      client
        .getQueryCache()
        .find(["stack-b", "/integrations/prod%2Fapp/def/apis"]),
    ).toBeDefined();
  });
  it("does not request metadata when integrations are disabled even when authenticated", () => {
    state.token = "mock-local-token";
    state.integrations = false;
    renderHook(() => useMCPTools("prod"), { wrapper });
    expect(state.fetch).not.toHaveBeenCalled();
  });
});
