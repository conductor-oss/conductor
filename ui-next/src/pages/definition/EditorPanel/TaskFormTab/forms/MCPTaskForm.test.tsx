import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReactNode, useState } from "react";
import { MemoryRouter } from "react-router";
import { TaskDef } from "types";
import { MCPTaskForm } from "./MCPTaskForm";

const query = vi.hoisted(() => ({ data: undefined as unknown }));
vi.mock("utils/query", () => ({
  useFetch: () => ({ ...query, isLoading: false }),
}));
vi.mock("utils/hooks/useMCPIntegrations", () => ({
  useMCPIntegrations: () => ({ integrations: [], isLoading: false }),
  useMCPTools: () => ({ tools: [], isLoading: false }),
}));
vi.mock("components/ui/inputs", () => ({ ConductorAutoComplete: () => null }));
vi.mock("./TaskFormSection", () => ({
  default: ({ children }: { children: ReactNode }) => (
    <section>{children}</section>
  ),
}));
vi.mock("components/IntegrationIcon", () => ({ IntegrationIcon: () => null }));
vi.mock("./MaybeVariable", () => ({
  MaybeVariable: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));

const inputs = {
  integrationName: "azure-test",
  method: "Invoke HTTP Function",
};
const schema = {
  inputSchema: {
    data: {
      type: "object",
      properties: {
        useFunctionKey: {
          type: "boolean",
          title: "Use Function Key",
          default: true,
        },
      },
    },
  },
};

function Harness({ initialTask }: { initialTask: Partial<TaskDef> }) {
  const [task, setTask] = useState(initialTask);
  return (
    <MemoryRouter>
      <MCPTaskForm task={task} onChange={setTask} />
      <pre data-testid="saved-task">{JSON.stringify(task)}</pre>
    </MemoryRouter>
  );
}

const savedInputs = () =>
  JSON.parse(screen.getByTestId("saved-task").textContent ?? "{}")
    .inputParameters;

describe("MCPTaskForm schema defaults", () => {
  beforeEach(() => {
    query.data = undefined;
  });

  it("renders and persists a missing default when metadata arrives", async () => {
    const task = { inputParameters: inputs };
    const { rerender } = render(<Harness initialTask={task} />);
    expect(savedInputs()).toEqual(inputs);
    query.data = schema;
    rerender(<Harness initialTask={task} />);
    expect(
      await screen.findByRole("checkbox", { name: /Use Function Key/ }),
    ).toBeChecked();
    await waitFor(() => expect(savedInputs().useFunctionKey).toBe(true));
  });

  it("preserves an explicitly saved false", async () => {
    query.data = schema;
    render(
      <Harness
        initialTask={{ inputParameters: { ...inputs, useFunctionKey: false } }}
      />,
    );
    expect(
      await screen.findByRole("checkbox", { name: /Use Function Key/ }),
    ).not.toBeChecked();
    await waitFor(() => expect(savedInputs().useFunctionKey).toBe(false));
  });

  it("allows users to uncheck the initialized default", async () => {
    query.data = schema;
    render(<Harness initialTask={{ inputParameters: inputs }} />);
    const checkbox = await screen.findByRole("checkbox", {
      name: /Use Function Key/,
    });
    expect(checkbox).toBeChecked();
    fireEvent.click(checkbox);
    await waitFor(() => expect(savedInputs().useFunctionKey).toBe(false));
    expect(checkbox).not.toBeChecked();
  });

  it("preserves false, zero, empty strings, nulls and workflow expressions", () => {
    query.data = {
      inputSchema: {
        data: {
          type: "object",
          properties: Object.fromEntries(
            ["disabled", "count", "text", "nullable", "expression"].map(
              (key) => [key, { default: "replacement" }],
            ),
          ),
        },
      },
    };
    const existing = {
      ...inputs,
      disabled: false,
      count: 0,
      text: "",
      nullable: null,
      expression: "${workflow.input.token}",
    };
    render(<Harness initialTask={{ inputParameters: existing }} />);
    expect(savedInputs()).toEqual(existing);
  });

  it("does not add defaults to expression-based input parameters", () => {
    query.data = schema;
    const onChange = vi.fn();
    render(
      <MemoryRouter>
        <MCPTaskForm
          task={{
            inputParameters:
              "${workflow.input.arguments}" as unknown as TaskDef["inputParameters"],
          }}
          onChange={onChange}
        />
      </MemoryRouter>,
    );
    expect(onChange).not.toHaveBeenCalled();
  });
});
