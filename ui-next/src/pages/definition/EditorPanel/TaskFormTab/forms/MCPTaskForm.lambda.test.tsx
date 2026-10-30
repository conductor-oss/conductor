import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import Ajv from "ajv";
import { PropsWithChildren } from "react";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { MCPTaskForm } from "./MCPTaskForm";
import { downgradeSchemaToDraft7 } from "utils/json";
// Captured from the Lambda plugin's discoverTools snapshot, then passed through
// the runtime mapper's additionalProperties=true normalization.
import schema from "./__fixtures__/lambdaInvokeSchema.json";

import createSchema from "./__fixtures__/lambdaCreateSchema.json";
const response = vi.hoisted(() => ({ schema: undefined as any }));

vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: () => ({
    integrations: [{ name: "lambda", type: "aws-lambda", status: "active" }],
    isLoading: false,
  }),
  useMCPTools: () => ({
    tools: [{ api: "aws-lambda-Invoke Function" }],
    isLoading: false,
  }),
}));
vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({}),
  computeFetchBaseEnabled: () => true,
  useFetch: () => ({
    data: { inputSchema: { data: response.schema ?? schema } },
    isLoading: false,
  }),
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

beforeEach(() => {
  response.schema = undefined;
  window.conductor = { INTEGRATIONS: true };
});

const invocation = {
  integrationName: "lambda",
  integrationType: "aws-lambda",
  method: "aws-lambda-Invoke Function",
  functionName: "my-function",
  payload: "42",
};
const queue = "arn:aws:sqs:us-east-1:123456789012:lambda-results";

it.each([undefined, "RequestResponse"])(
  "hides async-only controls for Sync (%s) and exposes the optional qualifier",
  (invocationType) => {
    render(
      <ThemeProvider>
        <MemoryRouter>
          <MCPTaskForm
            task={{ inputParameters: { ...invocation, invocationType } }}
            onChange={vi.fn()}
          />
        </MemoryRouter>
      </ThemeProvider>,
    );
    expect(
      screen.queryByRole("textbox", { name: /Result Queue Arn/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("spinbutton", { name: /Poll Interval Seconds/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("spinbutton", { name: /Completion Timeout Seconds/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("textbox", { name: /Qualifier/ }),
    ).not.toBeRequired();
  },
);

it("clears inactive async settings when switching to Sync while retaining the qualifier and payload", async () => {
  const onChange = vi.fn();
  render(
    <ThemeProvider>
      <MemoryRouter>
        <MCPTaskForm
          task={{
            inputParameters: {
              ...invocation,
              invocationType: "Event",
              resultQueueArn: queue,
              pollIntervalSeconds: 5,
              completionTimeoutSeconds: 900,
              qualifier: "prod",
            },
          }}
          onChange={onChange}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
  fireEvent.mouseDown(
    screen.getByRole("combobox", { name: /Invocation Type/ }),
  );
  fireEvent.click(screen.getByRole("option", { name: "Sync" }));
  await waitFor(() =>
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: {
          ...invocation,
          invocationType: "RequestResponse",
          qualifier: "prod",
        },
      }),
    ),
  );
});

it.each([
  ["RequestResponse", "Sync", "Async", "Event"],
  ["Event", "Async", "Sync", "RequestResponse"],
])(
  "displays %s as %s and saves AWS values when selecting %s",
  async (stored, label, nextLabel, nextValue) => {
    const onChange = vi.fn();
    render(
      <ThemeProvider>
        <MemoryRouter>
          <MCPTaskForm
            task={{
              inputParameters: { ...invocation, invocationType: stored },
            }}
            onChange={onChange}
          />
        </MemoryRouter>
      </ThemeProvider>,
    );
    const selection = screen.getByRole("combobox", { name: /Invocation Type/ });
    expect(selection).toHaveValue(label);
    fireEvent.mouseDown(selection);
    expect(
      screen.queryByRole("option", { name: "RequestResponse" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("option", { name: "Event" }),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("option", { name: nextLabel }));
    await waitFor(() =>
      expect(onChange).toHaveBeenCalledWith(
        expect.objectContaining({
          inputParameters: { ...invocation, invocationType: nextValue },
        }),
      ),
    );
  },
);

it("validates Event-specific requirements without requiring a destination for synchronous calls", () => {
  const validate = new Ajv({ strict: false }).compile(
    downgradeSchemaToDraft7(schema),
  );
  expect(validate(invocation)).toBe(true);
  expect(validate({ ...invocation, invocationType: "RequestResponse" })).toBe(
    true,
  );
  expect(validate({ ...invocation, invocationType: "Event" })).toBe(false);
  expect(
    validate({ ...invocation, invocationType: "Event", resultQueueArn: queue }),
  ).toBe(true);
  expect(
    validate({
      ...invocation,
      invocationType: "Event",
      resultQueueArn: `${queue}.fifo`,
    }),
  ).toBe(false);
  expect(
    validate({
      ...invocation,
      invocationType: "RequestResponse",
      resultQueueArn: queue,
    }),
  ).toBe(false);
  expect(validate({ ...invocation, pollIntervalSeconds: 5 })).toBe(false);
  expect(validate({ ...invocation, completionTimeoutSeconds: 900 })).toBe(
    false,
  );
  for (const [field, value] of [
    ["pollIntervalSeconds", 0],
    ["pollIntervalSeconds", 301],
    ["completionTimeoutSeconds", 0],
    ["completionTimeoutSeconds", 86401],
  ]) {
    expect(
      validate({
        ...invocation,
        invocationType: "Event",
        resultQueueArn: queue,
        [field]: value,
      }),
    ).toBe(false);
  }
});

it("renders editable SQS completion settings through the real schema form and preserves routing and payload", async () => {
  const onChange = vi.fn();
  render(
    <ThemeProvider>
      <MemoryRouter>
        <MCPTaskForm
          task={{
            inputParameters: {
              ...invocation,
              invocationType: "Event",
              resultQueueArn: queue,
              pollIntervalSeconds: 5,
              completionTimeoutSeconds: 900,
            },
          }}
          onChange={onChange}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
  const destination = screen.getByRole("textbox", { name: /Result Queue Arn/ });
  expect(destination).toHaveValue(queue);
  expect(
    screen.getByRole("spinbutton", { name: /Poll Interval Seconds/ }),
  ).toHaveValue(5);
  expect(
    screen.getByRole("spinbutton", { name: /Completion Timeout Seconds/ }),
  ).toHaveValue(900);
  fireEvent.change(destination, { target: { value: `${queue}-new` } });
  await waitFor(() =>
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: {
          ...invocation,
          invocationType: "Event",
          resultQueueArn: `${queue}-new`,
          pollIntervalSeconds: 5,
          completionTimeoutSeconds: 900,
        },
      }),
    ),
  );
});

it("renders the package source control for Create Function while retaining schema-driven inputs", () => {
  response.schema = createSchema;
  const onChange = vi.fn();
  render(
    <ThemeProvider>
      <MemoryRouter>
        <MCPTaskForm
          task={{
            taskReferenceName: "create",
            inputParameters: {
              integrationName: "lambda",
              method: "Create Function",
              integrationType: "aws-lambda",
              functionName: "f",
            },
          }}
          onChange={onChange}
        />
      </MemoryRouter>
    </ThemeProvider>,
  );
  expect(
    screen.getByRole("combobox", { name: "Package source" }),
  ).toHaveTextContent("Upload ZIP");
  expect(
    screen.getByRole("textbox", { name: "Lambda execution role ARN" }),
  ).not.toBeRequired();
  expect(
    screen.getByText(
      /If omitted, Conductor creates a basic Lambda execution role with CloudWatch Logs permissions/,
    ),
  ).toBeVisible();
  expect(screen.getByRole("textbox", { name: /Function Name/ })).toHaveValue(
    "f",
  );
  expect(
    screen.queryByRole("textbox", { name: /Zip File Base64/ }),
  ).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Upload deployment ZIP"), {
    target: {
      files: [new File([new Uint8Array([0x50, 0x4b, 3, 4])], "code.zip")],
    },
  });
  return waitFor(() =>
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        inputParameters: expect.objectContaining({
          zipFileBase64: "UEsDBA==",
          functionName: "f",
          integrationName: "lambda",
        }),
      }),
    ),
  );
});
