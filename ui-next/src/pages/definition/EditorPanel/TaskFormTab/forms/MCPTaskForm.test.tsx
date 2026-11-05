import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
vi.mock("components/IntegrationIcon", () => ({
  IntegrationIcon: () => null,
  ConnectedAppIcon: () => null,
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./TaskFormSection", () => ({
  default: ({ children }: any) => <div>{children}</div>,
}));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: any) => <div>{children}</div>,
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
      onKeyDown={(event) => {
        if (event.key === "Enter") onChange(null, event.currentTarget.value);
      }}
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
    expect(
      screen.getByRole("spinbutton", { name: /Count/ }),
    ).toBeInTheDocument();
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
    fireEvent.change(screen.getByLabelText("Tool"), {
      target: { value: "Other Operation" },
    });
    expect(saved().inputParameters).toEqual({
      integrationName: "prod",
      operation: "Other Operation",
      arguments: {},
    });
  });
  it("matches the reference core controls without duplicate advanced editors", () => {
    render(<Harness />);
    expect(screen.getByLabelText("Tool")).toBeInTheDocument();
    expect(screen.queryByLabelText("Operation")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Arguments JSON" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Custom arguments" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Use custom arguments/)).not.toBeInTheDocument();
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
    expect(
      screen.getByRole("spinbutton", { name: /Count/ }),
    ).toBeInTheDocument();
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

describe("Connected App operation schema editing", () => {
  it("edits typed numeric arguments without losing unknown saved arguments or envelope fields", async () => {
    render(
      <Harness
        task={{
          ...initial,
          inputParameters: {
            ...initial.inputParameters,
            arguments: { count: 3, oldSavedArgument: { nested: "keep" } },
          },
        }}
      />,
    );
    fireEvent.change(screen.getByRole("spinbutton", { name: /Count/ }), {
      target: { value: "10" },
    });
    await waitFor(() =>
      expect(saved().inputParameters.arguments.count).toBe(10),
    );
    expect(saved().inputParameters).toEqual({
      integrationName: "prod",
      operation: "Invoke Function",
      arguments: { count: 10, oldSavedArgument: { nested: "keep" } },
    });
  });
  it("shows required validation, nested arrays and booleans using the real schema editor", async () => {
    metadata.fetch.mockReturnValue({
      data: {
        inputSchema: {
          data: {
            type: "object",
            properties: {
              range: { type: "string" },
              values: {
                type: "array",
                items: { type: "array", items: { type: "string" } },
              },
              includeValuesInResponse: { type: "boolean" },
            },
            required: ["range", "values"],
          },
        },
      },
      isLoading: false,
    });
    render(
      <Harness
        task={{
          ...initial,
          inputParameters: { ...initial.inputParameters, arguments: {} },
        }}
      />,
    );
    expect(
      await screen.findByText(/is a required property/),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: /Range/ }), {
      target: { value: "A1" },
    });
    fireEvent.click(
      screen.getByRole("checkbox", { name: /Include Values In Response/ }),
    );
    fireEvent.click(screen.getByRole("button", { name: /Add to Values/ }));
    await waitFor(() =>
      expect(saved().inputParameters.arguments).toEqual({
        range: "A1",
        values: [[]],
        includeValuesInResponse: true,
      }),
    );
  });
  it("preserves JSON, unknown fields and expressions supplied by the existing Code tab", async () => {
    const argumentsValue = {
      count: 4,
      custom: { arbitrary: [1, true], expression: "${workflow.input.next}" },
    };
    render(
      <Harness
        task={{
          ...initial,
          inputParameters: {
            ...initial.inputParameters,
            arguments: argumentsValue,
          },
        }}
      />,
    );
    fireEvent.change(screen.getByRole("spinbutton", { name: /Count/ }), {
      target: { value: "7" },
    });
    await waitFor(() =>
      expect(saved().inputParameters.arguments.count).toBe(7),
    );
    expect(saved().inputParameters.arguments.custom).toEqual(
      argumentsValue.custom,
    );
  });
  it("uses generic arguments for absent/freeform schemas", () => {
    metadata.fetch.mockReturnValue({
      data: { inputSchema: { data: { type: "object" } } },
      isLoading: false,
    });
    render(<Harness />);
    expect(screen.getByLabelText("Argument fields")).toBeInTheDocument();
  });
});

it("does not discard arguments when the current connection or operation is reselected", () => {
  render(<Harness />);
  fireEvent.change(screen.getByLabelText("Integration"), {
    target: { value: "prod" },
  });
  fireEvent.change(screen.getByLabelText("Tool"), {
    target: { value: "Invoke Function" },
  });
  fireEvent.keyDown(screen.getByLabelText("Tool"), { key: "Enter" });
  expect(saved().inputParameters).toEqual(initial.inputParameters);
});

it("opens older tasks with omitted arguments using operation fields without inventing values", () => {
  render(
    <Harness
      task={{
        ...initial,
        inputParameters: {
          integrationName: "prod",
          operation: "Invoke Function",
        },
      }}
    />,
  );
  expect(screen.getByRole("spinbutton", { name: /Count/ })).toHaveValue(null);
});

it("opens omitted arguments with the generic editor when no input schema is declared", () => {
  metadata.fetch.mockReturnValue({
    data: { inputSchema: { data: { type: "object" } } },
    isLoading: false,
  });
  render(
    <Harness
      task={{
        ...initial,
        inputParameters: {
          integrationName: "prod",
          operation: "Invoke Function",
        },
      }}
    />,
  );
  expect(screen.getByLabelText("Argument fields")).toHaveValue("{}");
});
