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
    data: { inputSchema: { data: schema } },
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
