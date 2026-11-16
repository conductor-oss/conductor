import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useFetch } from "utils/query";
import { useMCPTools } from "./useMCPIntegrations";

vi.mock("utils/query", () => ({ useFetch: vi.fn() }));

const tools = [
  { api: "Manage" },
  {
    api: "First operation",
    inputSchema: {
      data: { "x-integration-configuration": { mode: "first", enabled: true } },
    },
  },
  {
    api: "Second operation",
    inputSchema: {
      data: { "x-integration-configuration": { mode: "second" } },
    },
  },
];

const serve = (
  data: ReturnType<typeof useMCPTools>["tools"] | undefined,
  isLoading = false,
) =>
  vi
    .mocked(useFetch)
    .mockReturnValue({ data, isLoading } as ReturnType<typeof useFetch>);

beforeEach(() => vi.resetAllMocks());

describe("useMCPTools", () => {
  it("matches every schema requirement and updates when the selected integration changes", () => {
    serve(tools);
    const { result, rerender } = renderHook(
      ({ name, configuration }) => useMCPTools(name, configuration),
      {
        initialProps: {
          name: "first-integration",
          configuration: { mode: "first", enabled: true },
        },
      },
    );
    expect(result.current.tools.map((tool) => tool.api)).toEqual([
      "Manage",
      "First operation",
    ]);
    rerender({
      name: "second-integration",
      configuration: { mode: "second", enabled: false },
    });
    expect(result.current.tools.map((tool) => tool.api)).toEqual([
      "Manage",
      "Second operation",
    ]);
    rerender({
      name: "first-integration",
      configuration: { mode: "first", enabled: false },
    });
    expect(result.current.tools.map((tool) => tool.api)).toEqual(["Manage"]);
    expect(useFetch).toHaveBeenLastCalledWith(
      "/integrations/first-integration/def/apis",
      expect.objectContaining({ when: true, keepPreviousData: false }),
    );
  });

  it("hides conditional operations until their saved configuration is available", () => {
    serve(tools);
    const { result } = renderHook(() => useMCPTools("loading-integration"));
    expect(result.current.tools.map((tool) => tool.api)).toEqual(["Manage"]);
  });

  it("keeps all operations without configuration requirements", () => {
    const unrestricted = [
      { api: "Plain operation" },
      { api: "Schema operation", inputSchema: { data: {} } },
    ];
    serve(unrestricted);
    const { result } = renderHook(() => useMCPTools("unrestricted"));
    expect(result.current.tools).toEqual(unrestricted);
  });

  it("returns an empty list while tools load and disables fetching without an integration", () => {
    serve(undefined, true);
    const { result } = renderHook(() => useMCPTools());
    expect(result.current.tools).toEqual([]);
    expect(result.current.isLoading).toBe(true);
    expect(useFetch).toHaveBeenCalledWith(
      "",
      expect.objectContaining({ when: false }),
    );
  });
});
