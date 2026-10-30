import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PropsWithChildren } from "react";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { MCPTaskForm } from "./MCPTaskForm";

vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: () => ({
    integrations: [
      { name: "firebase", type: "firebase-functions", status: "active" },
    ],
    isLoading: false,
  }),
  useMCPTools: () => ({
    tools: [{ api: "invoke_http" }, { api: "invoke_callable" }],
    isLoading: false,
  }),
}));
vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({}),
  computeFetchBaseEnabled: () => true,
  useFetch: () => ({
    // Actual FirebaseFunctionsMcpServer contract: JSON encoded as a string.
    data: {
      inputSchema: {
        data: {
          type: "object",
          properties: {
            payloadJson: { type: "string", description: "A valid JSON value" },
          },
          required: ["payloadJson"],
          additionalProperties: false,
        },
      },
    },
    isLoading: false,
  }),
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: PropsWithChildren) => <>{children}</>,
}));
vi.mock("./TaskFormSection", () => ({
  default: ({ children }: PropsWithChildren) => <>{children}</>,
}));

it.each(["invoke_http", "invoke_callable"])(
  "preserves Firebase routing and the payloadJson string through the real schema form (%s)",
  async (method) => {
    const onChange = vi.fn();
    render(
      <ThemeProvider>
        <MemoryRouter>
          <MCPTaskForm
            task={{
              inputParameters: {
                integrationName: "firebase",
                integrationType: "firebase-functions",
                method,
                payloadJson: '{"answer":42}',
              },
            }}
            onChange={onChange}
          />
        </MemoryRouter>
      </ThemeProvider>,
    );
    const payload = screen.getByRole("textbox", { name: /Payload Json/ });
    expect(payload).toHaveValue('{"answer":42}');
    fireEvent.change(payload, { target: { value: '{"answer":43}' } });
    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({
          inputParameters: {
            integrationName: "firebase",
            integrationType: "firebase-functions",
            method,
            payloadJson: '{"answer":43}',
          },
        }),
      ),
    );
  },
);

vi.mock("plugins/fetch", () => ({
  useFetchContext: () => ({ ready: true }),
  fetchContextNonHook: () => ({ ready: true }),
  fetchWithContext: vi.fn(),
}));
beforeEach(() => {
  window.conductor = { INTEGRATIONS: true };
});
