import { renderHook } from "@testing-library/react";
import { useAiModelOptions } from "./useAiModelOptions";

const useFetch = vi.hoisted(() => vi.fn());
const useQueries = vi.hoisted(() => vi.fn());

vi.mock("react-query", () => ({
  useQueries: (...args: unknown[]) => useQueries(...args),
}));

vi.mock("plugins/fetch", () => ({
  fetchWithContext: vi.fn(),
  useFetchContext: () => ({ stack: "default", ready: true }),
}));

vi.mock("utils/query", () => ({
  STALE_TIME_DROPDOWN: 600000,
  useAuthHeaders: () => ({ "X-Authorization": "ui-token" }),
  useFetch: (...args: unknown[]) => useFetch(...args),
}));

describe("useAiModelOptions", () => {
  beforeEach(() => {
    useFetch.mockReset();
    useQueries.mockReset();
  });

  it("returns an empty list when no AI model providers are configured", () => {
    useFetch.mockReturnValue({ data: [] });
    useQueries.mockReturnValue([]);

    const { result } = renderHook(() => useAiModelOptions());

    expect(result.current).toEqual([]);
    expect(useFetch).toHaveBeenCalledWith(
      "/integrations/provider?category=AI_MODEL&activeOnly=true",
      expect.objectContaining({ staleTime: 600000 }),
    );
  });

  it("loads sorted provider/model options from each provider", () => {
    useFetch.mockReturnValue({
      data: [{ name: "openai" }, { name: "anthropic" }],
    });
    useQueries.mockReturnValue([
      { data: [{ api: "gpt-5" }, { api: "gpt-4o" }] },
      { data: [{ api: "claude-sonnet" }] },
    ]);

    const { result } = renderHook(() => useAiModelOptions());

    expect(useQueries).toHaveBeenCalledWith(
      expect.arrayContaining([
        expect.objectContaining({
          queryKey: ["default", "/integrations/provider/openai/integration"],
          enabled: true,
        }),
        expect.objectContaining({
          queryKey: ["default", "/integrations/provider/anthropic/integration"],
          enabled: true,
        }),
      ]),
    );
    expect(result.current).toEqual([
      "anthropic/claude-sonnet",
      "openai/gpt-4o",
      "openai/gpt-5",
    ]);
  });
});
