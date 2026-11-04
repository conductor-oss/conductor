import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { MCPTaskForm } from "./MCPTaskForm";

const { state, request } = vi.hoisted(() => ({
  state: { ready: true, token: "test-token" as string | null },
  request: vi.fn(),
}));
vi.mock("plugins/fetch", () => ({
  useFetchContext: () => ({ ready: state.ready, stack: "schema-gate" }),
  fetchContextNonHook: () => ({ ready: state.ready, stack: "schema-gate" }),
  fetchWithContext: request,
}));
vi.mock("plugins/registry", () => ({
  pluginRegistry: { getAccessToken: () => state.token },
}));
vi.mock("components/features/auth/tokenManagerJotai", () => ({
  getAccessToken: () => null,
}));
vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: () => ({
    integrations: [{ name: "lambda", type: "aws-lambda" }],
    isLoading: false,
  }),
  useMCPTools: () => ({ tools: [{ api: "invoke" }], isLoading: false }),
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: any) => <>{children}</>,
}));
vi.mock("./TaskFormSection", () => ({
  default: ({ children }: any) => <>{children}</>,
}));
vi.mock("@jsonforms/react", () => ({
  JsonForms: () => <div>Cached operation fields</div>,
}));
vi.mock("@jsonforms/material-renderers", () => ({
  materialCells: [],
  materialRenderers: [],
}));

const path = "/integrations/lambda/def/api/invoke";
const schema = {
  inputSchema: {
    data: { type: "object", properties: { functionName: { type: "string" } } },
  },
};
const task = {
  inputParameters: {
    integrationName: "lambda",
    integrationType: "aws-lambda",
    method: "invoke",
  },
};
const show = (cached = false) => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, cacheTime: 0 } },
  });
  if (cached) client.setQueryData(["schema-gate", path], schema);
  return render(
    <ThemeProvider>
      <MemoryRouter>
        <QueryClientProvider client={client}>
          <MCPTaskForm task={task} onChange={vi.fn()} />
        </QueryClientProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
};
beforeEach(() => {
  state.ready = true;
  state.token = "test-token";
  window.conductor = { INTEGRATIONS: true, ACCESS_MANAGEMENT: true };
  request.mockReset().mockResolvedValue(schema);
});
it.each(["feature", "readiness", "authentication"])(
  "does not dispatch or expose cached schema when %s is unavailable",
  async (unavailable) => {
    if (unavailable === "feature") window.conductor.INTEGRATIONS = false;
    if (unavailable === "readiness") state.ready = false;
    if (unavailable === "authentication") state.token = null;
    show(true);
    await waitFor(() =>
      expect(screen.queryByText("Cached operation fields")).toBeNull(),
    );
    expect(request).not.toHaveBeenCalled();
  },
);
it("retains the normal authenticated schema request and renders the response", async () => {
  show();
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      path,
      expect.objectContaining({ ready: true }),
      { headers: { "X-Authorization": "test-token" } },
    ),
  );
  expect(
    await screen.findByText("Cached operation fields"),
  ).toBeInTheDocument();
});

it.each(["feature", "readiness", "authentication"])(
  "does not dispatch a cold schema request when %s is unavailable",
  (unavailable) => {
    if (unavailable === "feature") window.conductor.INTEGRATIONS = false;
    if (unavailable === "readiness") state.ready = false;
    if (unavailable === "authentication") state.token = null;
    show();
    expect(request).not.toHaveBeenCalled();
  },
);
