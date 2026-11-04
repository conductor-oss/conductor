import { renderHook } from "@testing-library/react";
import { useMCPIntegrations, useMCPTools } from "./useMCPIntegrations";
const fetch = vi.hoisted(() => vi.fn());
vi.mock("utils/query", () => ({ useFetch: fetch }));
vi.mock("utils/flags", () => ({ FEATURES: { INTEGRATIONS: "INTEGRATIONS" } }));
const definitions = [
  { type: "plugin:firebase", category: "CLOUD", tags: ["packaged-plugin"] },
  { type: "legacy", category: "MCP" },
  { type: "cloud-other", category: "CLOUD" },
];
beforeEach(() => {
  fetch.mockReset();
  fetch.mockImplementation((path: string) => ({
    data:
      path === "/integrations/def"
        ? definitions
        : [
            { name: "prod", type: "plugin:firebase", category: "CLOUD" },
            { name: "legacy", type: "legacy", category: "MCP" },
            { name: "other", type: "cloud-other", category: "CLOUD" },
          ],
    isLoading: false,
  }));
});
describe("integration metadata hooks", () => {
  it("lists only saved packaged-plugin instances in Connected App mode", () => {
    const { result } = renderHook(() => useMCPIntegrations(true));
    expect(result.current.integrations.map((x) => x.name)).toEqual(["prod"]);
    expect(fetch).toHaveBeenCalledWith(
      "/integrations/provider?category=CLOUD&activeOnly=false",
      expect.anything(),
    );
  });
  it("preserves existing MCP callers", () => {
    const { result } = renderHook(() => useMCPIntegrations());
    expect(result.current.integrations.map((x) => x.name)).toEqual(["legacy"]);
    expect(fetch).toHaveBeenCalledWith(
      "/integrations/provider?category=MCP&activeOnly=false",
      expect.anything(),
    );
  });
  it("does not retain stale data after forbidden catalog responses", () => {
    fetch.mockReturnValue({
      data: definitions,
      error: { status: 403 },
      isLoading: false,
    });
    const { result } = renderHook(() => useMCPIntegrations(true));
    expect(result.current.integrations).toEqual([]);
    expect(result.current.error).toEqual({ status: 403 });
  });
  it.each([undefined, "", "${workflow.input.integration}"])(
    "disables operation requests for %s without bypassing auth gating",
    (integration) => {
      const { result } = renderHook(() => useMCPTools(integration));
      expect(fetch).toHaveBeenLastCalledWith(
        expect.any(String),
        expect.objectContaining({
          when: false,
          keepPreviousData: false,
          enterpriseApiFeature: "INTEGRATIONS",
        }),
      );
      expect(result.current.tools).toEqual([]);
    },
  );
  it("scopes operation metadata to encoded saved integration and clears stale results", () => {
    renderHook(() => useMCPTools("prod/firebase"));
    expect(fetch).toHaveBeenLastCalledWith(
      "/integrations/prod%2Ffirebase/def/apis",
      expect.objectContaining({ when: true, keepPreviousData: false }),
    );
  });
});

it("does not present an explicitly disabled integration as active", () => {
  fetch.mockImplementation((path: string) => ({
    data:
      path === "/integrations/def"
        ? definitions
        : [
            {
              name: "disabled",
              type: "plugin:firebase",
              category: "CLOUD",
              enabled: false,
            },
          ],
    isLoading: false,
  }));
  const { result } = renderHook(() => useMCPIntegrations(true));
  expect(result.current.integrations[0].status).toBe("inactive");
});
