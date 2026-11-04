import { fireEvent, render, screen } from "@testing-library/react";
import { PropsWithChildren } from "react";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { FEATURES } from "utils/flags";
import { MCPTaskForm } from "./MCPTaskForm";
import { Provider as ThemeProvider } from "theme/material/provider";

const { connections, toolsHook, fetchHook } = vi.hoisted(() => ({
  connections: vi.fn(),
  toolsHook: vi.fn(),
  fetchHook: vi.fn(),
}));
vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: connections,
  useMCPTools: toolsHook,
}));
vi.mock("utils/query", () => ({
  useFetch: fetchHook,
  useAuthHeaders: () => ({}),
  computeFetchBaseEnabled: () => true,
}));
vi.mock("plugins/fetch", () => ({
  useFetchContext: () => ({ ready: true }),
  fetchContextNonHook: () => ({ ready: true }),
  fetchWithContext: vi.fn(),
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: PropsWithChildren) => <>{children}</>,
}));
vi.mock("./TaskFormSection", () => ({
  default: ({ children }: PropsWithChildren) => <>{children}</>,
}));
vi.mock("@jsonforms/react", () => ({
  JsonForms: () => <div>Operation fields</div>,
}));
vi.mock("@jsonforms/material-renderers", () => ({
  materialCells: [],
  materialRenderers: [],
}));

beforeEach(() => {
  window.conductor = { [FEATURES.INTEGRATIONS]: true };
  connections
    .mockReset()
    .mockReturnValue({ integrations: [], isLoading: false });
  toolsHook.mockReset().mockReturnValue({ tools: [], isLoading: false });
  fetchHook.mockReset().mockReturnValue({ data: undefined, isLoading: false });
});
const task = {
  inputParameters: {
    integrationName: "lambda",
    integrationType: "aws-lambda",
    method: "invoke",
  },
};
const show = () =>
  render(
    <ThemeProvider>
      <MemoryRouter>
        <MCPTaskForm task={task} onChange={vi.fn()} />
      </MemoryRouter>
    </ThemeProvider>,
  );

it("warns for a stale disabled connection without fetching its tools or input schema", () => {
  fetchHook.mockReturnValue({
    data: {
      inputSchema: {
        data: {
          type: "object",
          properties: { functionName: { type: "string" } },
        },
      },
    },
    isLoading: false,
  });
  show();
  expect(screen.getByRole("alert")).toHaveTextContent(
    "unavailable or disabled",
  );
  expect(toolsHook).toHaveBeenCalledWith(undefined);
  expect(fetchHook).toHaveBeenCalledWith(expect.any(String), {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: false,
  });
  expect(screen.queryByText("Operation fields")).toBeNull();
});

it("loads operation metadata only for a currently enabled configured connection", () => {
  connections.mockReturnValue({
    integrations: [{ name: "lambda", type: "aws-lambda", status: "active" }],
    isLoading: false,
  });
  toolsHook.mockReturnValue({ tools: [{ api: "invoke" }], isLoading: false });
  fetchHook.mockReturnValue({
    data: {
      inputSchema: {
        data: {
          type: "object",
          properties: { functionName: { type: "string" } },
        },
      },
    },
    isLoading: false,
  });
  show();
  expect(screen.queryByRole("alert")).toBeNull();
  expect(toolsHook).toHaveBeenCalledWith("lambda");
  expect(fetchHook).toHaveBeenCalledWith(expect.any(String), {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: true,
  });
  expect(screen.getByText("Operation fields")).toBeInTheDocument();
});

it("does not show a disabled warning before the connection query finishes", () => {
  connections.mockReturnValue({ integrations: [], isLoading: true });
  show();
  expect(screen.queryByRole("alert")).toBeNull();
});

it("disables cached tool choices for an unavailable stored connection", () => {
  toolsHook.mockReturnValue({ tools: [{ api: "invoke" }], isLoading: false });
  show();
  expect(screen.getByLabelText(/^Tool/)).toBeDisabled();
});

it("suppresses a cached operation schema when integrations become unavailable", () => {
  window.conductor[FEATURES.INTEGRATIONS] = false;
  connections.mockReturnValue({
    integrations: [{ name: "lambda", type: "aws-lambda", status: "active" }],
    isLoading: false,
  });
  fetchHook.mockReturnValue({
    data: {
      inputSchema: {
        data: {
          type: "object",
          properties: { functionName: { type: "string" } },
        },
      },
    },
    isLoading: false,
  });
  show();
  expect(fetchHook).toHaveBeenCalledWith(expect.any(String), {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: true,
  });
  expect(screen.queryByText("Operation fields")).toBeNull();
});

it("opens a connection-only task with a required operation selector and no premature schema fetch", () => {
  connections.mockReturnValue({
    integrations: [{ name: "lambda", type: "aws-lambda", status: "active" }],
    isLoading: false,
  });
  toolsHook.mockReturnValue({
    tools: [{ api: "aws-lambda-Invoke Function" }],
    isLoading: false,
  });
  const onChange = vi.fn();
  render(
    <ThemeProvider>
      <MemoryRouter>
        <MCPTaskForm
          task={{
            inputParameters: {
              integrationName: "lambda",
              integrationType: "aws-lambda",
            },
          }}
          onChange={onChange}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
  expect(screen.getByLabelText(/^Tool/)).toBeEnabled();
  expect(fetchHook).toHaveBeenCalledWith(expect.any(String), {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: false,
  });
});

it.each(["aws-s3", undefined])(
  "limits the connection choices to the selected provider (%s)",
  (integrationType) => {
    connections.mockReturnValue({
      integrations: [
        { name: "s3-primary", type: "aws-s3", status: "active" },
        { name: "s3-other", type: "aws-s3", status: "active" },
        { name: "s3-disabled", type: "aws-s3", status: "inactive" },
        { name: "lambda", type: "aws-lambda", status: "active" },
        { name: "gcp", type: "google-cloud-functions", status: "active" },
      ],
      isLoading: false,
    });
    const onChange = vi.fn();
    render(
      <ThemeProvider>
        <MemoryRouter>
          <MCPTaskForm
            task={{
              inputParameters: {
                integrationName: "s3-primary",
                integrationType,
                method: "aws-s3-List Buckets",
              },
            }}
            onChange={onChange}
          />
        </MemoryRouter>
      </ThemeProvider>,
    );
    fireEvent.keyDown(screen.getByLabelText(/^Integration/), {
      key: "ArrowDown",
    });
    expect(
      screen.getAllByRole("option").map((option) => option.textContent),
    ).toEqual(["s3-primary", "s3-other"]);
    fireEvent.click(screen.getByRole("option", { name: "s3-other" }));
    expect(onChange).toHaveBeenLastCalledWith({
      inputParameters: {
        integrationName: "s3-other",
        integrationType: "aws-s3",
      },
    });
  },
);

it("does not fetch metadata for a connection belonging to a different provider", () => {
  connections.mockReturnValue({
    integrations: [{ name: "lambda", type: "aws-s3", status: "active" }],
    isLoading: false,
  });
  show();
  expect(toolsHook).toHaveBeenCalledWith(undefined);
  expect(fetchHook).toHaveBeenCalledWith(expect.any(String), {
    enterpriseApiFeature: FEATURES.INTEGRATIONS,
    when: false,
  });
});

it("retains initial connection selection for a generic task without a provider", () => {
  connections.mockReturnValue({
    integrations: [
      { name: "s3-primary", type: "aws-s3", status: "active" },
      { name: "lambda", type: "aws-lambda", status: "active" },
    ],
    isLoading: false,
  });
  render(
    <ThemeProvider>
      <MemoryRouter>
        <MCPTaskForm task={{ inputParameters: {} }} onChange={vi.fn()} />
      </MemoryRouter>
    </ThemeProvider>,
  );
  fireEvent.keyDown(screen.getByLabelText(/^Integration/), {
    key: "ArrowDown",
  });
  expect(
    screen.getAllByRole("option").map((option) => option.textContent),
  ).toEqual(["s3-primary", "lambda"]);
});
