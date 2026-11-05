import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { ThemeProvider } from "@mui/material";
import getTheme from "theme/theme";
import { MCPTaskForm } from "./MCPTaskForm";
import { TaskType, TaskDef } from "types";
import { ConductorStringOrJsonInput } from "components/FlatMapForm/ConductorStringOrJsonInput";
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
vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({ value, onChange, error }: any) => (
    <textarea
      aria-label="Value JSON"
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
      <ThemeProvider theme={getTheme()}>
        <MCPTaskForm task={value} onChange={setValue} />
      </ThemeProvider>
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

import firebaseOperations from "./firebase-schemas.fixture.json";
const taskFor = (
  operation: string,
  data: unknown = {
    nested: [true, 2, null],
    expression: "${workflow.input.next}",
  },
) => ({
  ...initial,
  inputParameters: {
    integrationName: "firebase",
    operation,
    arguments: { data, retained: "keep" },
  },
});

describe("unconstrained Connected App payloads with the existing real map editor", () => {
  for (const definition of firebaseOperations) {
    it(`${definition.api}: edits nested JSON and rejects incomplete JSON without losing saved input`, () => {
      metadata.fetch.mockReturnValue({ data: definition, isLoading: false });
      const { unmount } = render(<Harness task={taskFor(definition.api)} />);
      const editor = screen.getByLabelText("Value JSON");
      expect(JSON.parse((editor as HTMLTextAreaElement).value)).toEqual(
        taskFor(definition.api).inputParameters.arguments.data,
      );
      fireEvent.change(editor, { target: { value: "{" } });
      expect(editor).toHaveAttribute("aria-invalid", "true");
      expect(saved().inputParameters).toEqual(
        taskFor(definition.api).inputParameters,
      );
      const data = {
        items: [false, 0, null, { id: "${workflow.input.id}" }],
        empty: {},
        array: [],
      };
      fireEvent.change(editor, { target: { value: JSON.stringify(data) } });
      expect(saved().inputParameters).toEqual({
        integrationName: "firebase",
        operation: definition.api,
        arguments: { data, retained: "keep" },
      });
      const persisted = JSON.parse(JSON.stringify(saved()));
      unmount();
      render(<Harness task={persisted} />);
      expect(
        JSON.parse(
          (screen.getByLabelText("Value JSON") as HTMLTextAreaElement).value,
        ),
      ).toEqual(data);
    });
    it.each(
      [
        7,
        true,
        null,
        "hello",
        "${workflow.input.data}",
        [1, null, "${workflow.input.x}"],
      ].map((data) => ({ data })),
    )(`${definition.api}: preserves and edits JSON value $data`, ({ data }) => {
      metadata.fetch.mockReturnValue({ data: definition, isLoading: false });
      render(<Harness task={taskFor(definition.api, data)} />);
      expect(saved().inputParameters.arguments.data).toEqual(data);
      const expected = Array.isArray(data)
        ? [false, 3, null]
        : "${workflow.input.changed}";
      if (Array.isArray(data)) {
        fireEvent.change(screen.getByLabelText("Value JSON"), {
          target: { value: "[false,3,null]" },
        });
      } else {
        const inputs = screen.getAllByRole("combobox", { name: "Value" });
        fireEvent.change(inputs[0], {
          target: { value: "${workflow.input.changed}" },
        });
      }
      expect(saved().inputParameters.arguments.data).toEqual(expected);
      expect(saved().inputParameters.arguments.retained).toBe("keep");
    });
    it(`${definition.api}: authors data from an empty argument map`, () => {
      metadata.fetch.mockReturnValue({ data: definition, isLoading: false });
      render(
        <Harness
          task={{
            ...initial,
            inputParameters: {
              integrationName: "firebase",
              operation: definition.api,
              arguments: {},
            },
          }}
        />,
      );
      fireEvent.click(screen.getByRole("button", { name: "Add argument" }));
      fireEvent.change(screen.getByRole("textbox", { name: "Argument" }), {
        target: { value: "data" },
      });
      fireEvent.change(screen.getByRole("combobox", { name: "Value" }), {
        target: { value: "null" },
      });
      expect(saved().inputParameters).toEqual({
        integrationName: "firebase",
        operation: definition.api,
        arguments: { data: null },
      });
    });
  }
  it.each([
    {},
    { type: "object", properties: { data: {} } },
    { type: "object", properties: { data: true } },
  ])("allows generic JSON schemas %j", (schema) => {
    metadata.fetch.mockReturnValue({
      data: { inputSchema: { data: schema } },
      isLoading: false,
    });
    render(<Harness task={taskFor("generic")} />);
    expect(screen.getByLabelText("Value JSON")).toBeInTheDocument();
  });
});

describe("constrained schemas retain the existing JSON Forms path", () => {
  it.each([true, {}, { type: "string" }])(
    "retains numeric required/minimum controls with additionalProperties=%j",
    async (additionalProperties) => {
      metadata.fetch.mockReturnValue({
        data: {
          inputSchema: {
            data: {
              type: "object",
              properties: { count: { type: "integer", minimum: 1 } },
              required: ["count"],
              additionalProperties,
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
      const count = screen.getByRole("spinbutton", { name: /Count/ });
      fireEvent.change(count, { target: { value: "0" } });
      expect(await screen.findByText(/must be >= 1/)).toBeInTheDocument();
      fireEvent.change(count, { target: { value: "2" } });
      await waitFor(() =>
        expect(saved().inputParameters.arguments.count).toBe(2),
      );
      await waitFor(() =>
        expect(screen.queryByText(/must be >= 1/)).not.toBeInTheDocument(),
      );
      expect(
        screen.queryByRole("button", { name: "Add argument" }),
      ).not.toBeInTheDocument();
    },
  );
  it("retains untyped enum controls and validation", async () => {
    metadata.fetch.mockReturnValue({
      data: {
        inputSchema: {
          data: {
            type: "object",
            properties: { choice: { enum: ["small", "large"] } },
          },
        },
      },
      isLoading: false,
    });
    render(
      <Harness
        task={{
          ...initial,
          inputParameters: {
            ...initial.inputParameters,
            arguments: { choice: "invalid" },
          },
        }}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Add argument" }),
    ).not.toBeInTheDocument();
    expect(await screen.findByText(/must be equal to/)).toBeInTheDocument();
    expect(
      screen.getByRole("combobox", { name: /Choice/ }),
    ).toBeInTheDocument();
  });
  it("retains the baseline untyped const schema path without inventing a renderer", () => {
    metadata.fetch.mockReturnValue({
      data: {
        inputSchema: {
          data: {
            type: "object",
            properties: { choice: { const: "fixed" } },
          },
        },
      },
      isLoading: false,
    });
    render(
      <Harness
        task={{
          ...initial,
          inputParameters: {
            ...initial.inputParameters,
            arguments: { choice: "invalid" },
          },
        }}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Add argument" }),
    ).not.toBeInTheDocument();
    // Baseline JSON Forms omits an untyped const control entirely.
    expect(
      screen.queryByRole("textbox", { name: /Choice/ }),
    ).not.toBeInTheDocument();
    expect(saved().inputParameters.arguments).toEqual({ choice: "invalid" });
  });
  it("does not replace mixed freeform and typed fields", () => {
    metadata.fetch.mockReturnValue({
      data: {
        inputSchema: {
          data: {
            type: "object",
            properties: { data: {}, count: { type: "integer" } },
          },
        },
      },
      isLoading: false,
    });
    render(<Harness />);
    expect(
      screen.getByRole("spinbutton", { name: /Count/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Add argument" }),
    ).not.toBeInTheDocument();
  });
  it.each([
    { required: ["data"] },
    { minProperties: 1 },
    { enum: [{}] },
    { properties: { data: { const: null } } },
    { properties: { data: { type: "null" } } },
    { properties: { data: { type: "boolean" } } },
    { properties: { data: false } },
  ])("does not classify constrained schema %j as freeform", (constraints) => {
    metadata.fetch.mockReturnValue({
      data: {
        inputSchema: {
          data: {
            type: "object",
            properties: { data: {} },
            ...constraints,
          },
        },
      },
      isLoading: false,
    });
    render(<Harness />);
    expect(
      screen.queryByRole("button", { name: "Add argument" }),
    ).not.toBeInTheDocument();
  });
});

describe("boolean/null metadata boundaries inherited from the baseline", () => {
  it.each([true, false, null])(
    "preserves the existing root %j metadata fallback, not a validation contract",
    (schema) => {
      metadata.fetch.mockReturnValue({
        data: { inputSchema: { data: schema } },
        isLoading: false,
      });
      render(<Harness task={taskFor("generic")} />);
      expect(
        screen.getByRole("button", { name: "Add argument" }),
      ).toBeInTheDocument();
      expect(saved().inputParameters).toEqual(
        taskFor("generic").inputParameters,
      );
    },
  );
});

describe("Connected App freeform mode switching is lossless", () => {
  for (const definition of firebaseOperations) {
    it.each(
      [
        {},
        [],
        { nested: [null, true, "${workflow.input.x}"] },
        [1, null],
        null,
        false,
        true,
        0,
        "",
        "123",
        "hello",
        "${workflow.input.data}",
      ].map((data) => ({ data })),
    )(`${definition.api}: toggles without altering $data`, ({ data }) => {
      metadata.fetch.mockReturnValue({ data: definition, isLoading: false });
      render(<Harness task={taskFor(definition.api, data)} />);
      const startJson = data !== null && typeof data === "object";
      const toggle = (name: string) =>
        fireEvent.click(screen.getAllByRole("button", { name })[0]);
      if (startJson) toggle("Switch to text");
      expect(saved().inputParameters.arguments.data).toEqual(data);
      toggle("Switch to JSON");
      expect(
        JSON.parse(
          (screen.getByLabelText("Value JSON") as HTMLTextAreaElement).value,
        ),
      ).toEqual(data);
      expect(saved().inputParameters.arguments.data).toEqual(data);
      toggle("Switch to text");
      expect(saved().inputParameters).toEqual(
        taskFor(definition.api, data).inputParameters,
      );
    });
    it(`${definition.api}: switches after a text edit using the current expression`, () => {
      metadata.fetch.mockReturnValue({ data: definition, isLoading: false });
      render(<Harness task={taskFor(definition.api, { nested: true })} />);
      fireEvent.click(screen.getByRole("button", { name: "Switch to text" }));
      fireEvent.change(screen.getAllByRole("combobox", { name: "Value" })[0], {
        target: { value: "${workflow.input.changed}" },
      });
      fireEvent.click(
        screen.getAllByRole("button", { name: "Switch to JSON" })[0],
      );
      expect(
        JSON.parse(
          (screen.getByLabelText("Value JSON") as HTMLTextAreaElement).value,
        ),
      ).toBe("${workflow.input.changed}");
      expect(saved().inputParameters.arguments.data).toBe(
        "${workflow.input.changed}",
      );
    });
    it.each([{}, []].map((data) => ({ data })))(
      `${definition.api}: retains invalid draft starting at $data without changing saved JSON`,
      ({ data }) => {
        metadata.fetch.mockReturnValue({ data: definition, isLoading: false });
        render(<Harness task={taskFor(definition.api, data)} />);
        const editor = screen.getByLabelText("Value JSON");
        fireEvent.change(editor, { target: { value: "{" } });
        expect(editor).toHaveValue("{");
        expect(editor).toHaveAttribute("aria-invalid", "true");
        expect(saved().inputParameters.arguments.data).toEqual(data);
      },
    );
  }
});

it("does not opt other shared-editor callers into lossless switching", () => {
  const onChange = vi.fn();
  render(
    <ThemeProvider theme={getTheme()}>
      <ConductorStringOrJsonInput
        value={{ nested: true }}
        onChange={onChange}
        showFieldTypes
      />
    </ThemeProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "Switch to text" }));
  expect(onChange).toHaveBeenCalledWith("");
});

it("leaves invalid null property metadata on the existing schema-error path", () => {
  metadata.fetch.mockReturnValue({
    data: {
      inputSchema: {
        data: {
          type: "object",
          properties: { data: null },
        },
      },
    },
    isLoading: false,
  });
  const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    expect(() => render(<Harness />)).toThrow(
      "Cannot read properties of null (reading '$ref')",
    );
  } finally {
    consoleError.mockRestore();
  }
});
