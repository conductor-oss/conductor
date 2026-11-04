import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { MCPTaskForm } from "./MCPTaskForm";
import { TaskType, TaskDef } from "types";
const metadata = vi.hoisted(() => ({
  fetch: vi.fn(),
  integrations: vi.fn(),
  tools: vi.fn(),
}));
vi.mock("react-router", () => ({ useNavigate: () => vi.fn() }));
vi.mock("utils/query", () => ({ useFetch: metadata.fetch }));
vi.mock("utils/flags", async (importOriginal) => ({
  ...(await importOriginal<typeof import("utils/flags")>()),
  FEATURES: { INTEGRATIONS: "INTEGRATIONS" },
}));
vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: metadata.integrations,
  useMCPTools: metadata.tools,
}));
vi.mock("components/IntegrationIcon", () => ({ IntegrationIcon: () => null }));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./TaskFormSection", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("@jsonforms/react", () => ({
  JsonForms: () => <div>Legacy schema editor</div>,
}));
vi.mock("components/ui/inputs", () => ({
  ConductorAutoComplete: ({
    label,
    value,
    options,
    onChange,
    getOptionLabel,
    disabled,
  }: any) => (
    <select
      aria-label={label}
      disabled={disabled}
      value={typeof value === "object" ? getOptionLabel(value) : value || ""}
      onChange={(event) =>
        onChange(
          null,
          options.find((x: any) => getOptionLabel(x) === event.target.value),
        )
      }
    >
      <option value="" />
      {options.map((x: any) => (
        <option key={getOptionLabel(x)}>{getOptionLabel(x)}</option>
      ))}
    </select>
  ),
}));
vi.mock("components/FlatMapForm/ConductorFlatMapForm", () => ({
  ConductorFlatMapFormBase: ({ value, onChange }: any) => (
    <textarea
      aria-label="Argument fields"
      value={JSON.stringify(value)}
      onChange={(e) => onChange(JSON.parse(e.target.value))}
    />
  ),
}));
vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({ value, onChange, error }: any) => (
    <textarea
      aria-label="Arguments JSON"
      aria-invalid={error}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    />
  ),
}));
const initial = {
  description: "",
  startDelay: 0,
  joinOn: [],
  optional: false,
  defaultExclusiveJoinTask: [],
  name: "invoke",
  taskReferenceName: "invoke_ref",
  type: TaskType.CONNECTED_APP,
  inputParameters: {
    integrationName: "prod",
    operation: "Invoke Function",
    arguments: { count: "${workflow.input.count}" },
  },
} as TaskDef;
function Harness({ task = initial }: { task?: TaskDef }) {
  const [value, setValue] = useState(task);
  return (
    <>
      <MCPTaskForm task={value} onChange={setValue} />
      <output data-testid="task">{JSON.stringify(value)}</output>
    </>
  );
}
const saved = () => JSON.parse(screen.getByTestId("task").textContent!);
beforeEach(() => {
  metadata.fetch.mockReturnValue({
    data: {
      inputSchema: {
        data: { type: "object", properties: { count: { type: "integer" } } },
      },
    },
    isLoading: false,
  });
  metadata.integrations.mockReturnValue({
    integrations: [
      { name: "prod", type: "plugin:aws-lambda", status: "active" },
      { name: "firebase", type: "plugin:firebase", status: "active" },
    ],
    isLoading: false,
  });
  metadata.tools.mockReturnValue({
    tools: [{ api: "Invoke Function" }, { api: "Other Operation" }],
    isLoading: false,
  });
});
describe("existing task form Connected App adaptation", () => {
  it("uses authenticated existing metadata endpoints and preserves expressions without schema coercion", () => {
    render(<Harness />);
    expect(metadata.integrations).toHaveBeenCalledWith(true);
    expect(metadata.fetch).toHaveBeenCalledWith(
      "/integrations/prod/def/api/Invoke%20Function",
      expect.objectContaining({
        when: true,
        keepPreviousData: false,
        enterpriseApiFeature: "INTEGRATIONS",
      }),
    );
    expect(screen.queryByText("Legacy schema editor")).not.toBeInTheDocument();
    expect(saved().inputParameters.arguments.count).toBe(
      "${workflow.input.count}",
    );
  });
  it("resets stale operation and arguments when saved integration changes", () => {
    render(<Harness />);
    fireEvent.change(screen.getByLabelText("Integration"), {
      target: { value: "firebase" },
    });
    expect(saved().inputParameters).toEqual({
      integrationName: "firebase",
      operation: "",
      arguments: {},
    });
  });
  it("resets arguments on operation change and emits only CONNECTED_APP input keys", () => {
    render(<Harness />);
    fireEvent.change(screen.getByLabelText("Operation"), {
      target: { value: "Other Operation" },
    });
    expect(saved().inputParameters).toEqual({
      integrationName: "prod",
      operation: "Other Operation",
      arguments: {},
    });
  });
  it("roundtrips nested JSON expressions and rejects invalid argument arrays", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "Arguments JSON" }));
    const args = {
      payload: { amount: "${workflow.input.amount}" },
      invocationType: "Event",
    };
    fireEvent.change(screen.getByLabelText("Arguments JSON"), {
      target: { value: JSON.stringify(args) },
    });
    expect(saved().inputParameters.arguments).toEqual(args);
    fireEvent.change(screen.getByLabelText("Arguments JSON"), {
      target: { value: "[]" },
    });
    expect(screen.getByLabelText("Arguments JSON")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(saved().inputParameters.arguments).toEqual(args);
    fireEvent.change(screen.getByLabelText("Integration"), {
      target: { value: "firebase" },
    });
    expect(screen.getByLabelText("Arguments JSON")).toHaveAttribute(
      "aria-invalid",
      "false",
    );
    expect(saved().inputParameters.arguments).toEqual({});
  });
  it("shows empty and forbidden metadata honestly, not execution availability", () => {
    metadata.integrations.mockReturnValue({
      integrations: [],
      isLoading: false,
    });
    const { unmount } = render(<Harness />);
    expect(
      screen.getByText("No Connected Apps configured."),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /Asynchronous acceptance is not eventual business success/,
      ),
    ).toBeInTheDocument();
    unmount();
    metadata.integrations.mockReturnValue({
      integrations: [],
      error: { status: 403 },
      isLoading: false,
    });
    render(<Harness />);
    expect(
      screen.getByText(/Unable to load Connected App metadata/),
    ).toBeInTheDocument();
  });
  it("preserves the existing INTEGRATION schema editor", () => {
    render(
      <Harness
        task={
          {
            ...initial,
            type: TaskType.INTEGRATION,
            inputParameters: { integrationName: "legacy", method: "tool" },
          } as TaskDef
        }
      />,
    );
    expect(metadata.integrations).toHaveBeenCalledWith(false);
    expect(screen.getByText("Legacy schema editor")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Arguments JSON" }),
    ).not.toBeInTheDocument();
  });
});

it("roundtrips dynamic integration and operation expressions without metadata fetches", () => {
  const inputParameters = {
    integrationName: "${workflow.input.integration}",
    operation: "${workflow.input.operation}",
    arguments: "${workflow.input.arguments}",
  };
  render(<Harness task={{ ...initial, inputParameters }} />);
  expect(metadata.fetch).toHaveBeenLastCalledWith(
    expect.any(String),
    expect.objectContaining({ when: false, keepPreviousData: false }),
  );
  expect(saved().inputParameters).toEqual(inputParameters);
});
