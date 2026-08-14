import { fireEvent, render, screen } from "@testing-library/react";
import { ReactNode, useState } from "react";
import { queryClient } from "queryClient";
import { TaskDef } from "types";
import { LLMChatCompleteTaskForm } from "./LLMChatCompleteTaskForm";

const fetchWithContext = vi.hoisted(() => vi.fn());

vi.mock("plugins/fetch", () => ({
  fetchWithContext,
  fetchContextNonHook: () => ({ stack: "test" }),
  useFetchContext: () => ({}),
}));

vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({}),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({
    label,
    value,
    onTextInputChange,
    helperText,
    tooltip,
  }: any) => (
    <div>
      <textarea
        aria-label={label}
        value={value ?? ""}
        onChange={(event) => onTextInputChange?.(event.target.value)}
      />
      {helperText && (
        <span data-testid={`${label}-helperText`}>{helperText}</span>
      )}
      {tooltip && (
        <span data-testid={`${label}-tooltip`}>
          {tooltip.title}: {tooltip.content}
        </span>
      )}
    </div>
  ),
}));

vi.mock("components/PromptVariables", () => ({ default: () => null }));
vi.mock(
  "pages/definition/EditorPanel/TaskFormTab/forms/ConductorValueInput",
  () => ({ ConductorValueInput: () => null }),
);
vi.mock(
  "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields/ConductorArrayMapForm",
  () => ({ ConductorArrayMapFormBase: () => null }),
);
vi.mock("./LLMFormFields/LLMFormFields", () => ({
  LLMFormFields: () => null,
}));
vi.mock("./ConductorCacheOutputForm", () => ({
  ConductorCacheOutput: () => null,
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./TaskFormSection", () => ({
  default: ({ title, children }: { title?: string; children: ReactNode }) => (
    <section data-testid={title ? `section-${title}` : "section-untitled"}>
      {title ? <h3 id={`${title}-header`}>{title}</h3> : null}
      {children}
    </section>
  ),
}));

function Harness({ initialTask }: { initialTask: Partial<TaskDef> }) {
  const [task, setTask] = useState(initialTask);
  return (
    <>
      <LLMChatCompleteTaskForm task={task} onChange={setTask} />
      <pre data-testid="task-json">{JSON.stringify(task)}</pre>
    </>
  );
}

const savedTask = (): Partial<TaskDef> =>
  JSON.parse(screen.getByTestId("task-json").textContent ?? "{}");

describe("LLMChatCompleteTaskForm — Instructions field", () => {
  beforeEach(() => {
    fetchWithContext.mockReset();
    fetchWithContext.mockResolvedValue([]);
    queryClient.clear();
  });

  it("labels the field Instructions (OSS plain textarea, not Prompt Template)", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(screen.getByLabelText("Instructions")).toBeInTheDocument();
    expect(screen.queryByLabelText("Prompt Template")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Prompt Name")).not.toBeInTheDocument();
  });

  it("renders the Instructions and Provider and Model section headers", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(document.getElementById("Instructions-header")).toBeInTheDocument();
    expect(
      document.getElementById("Provider and Model-header"),
    ).toBeInTheDocument();
  });

  it("displays existing instructions from the task", () => {
    render(
      <Harness
        initialTask={{
          inputParameters: { instructions: "You are a helpful assistant." },
        }}
      />,
    );

    expect(screen.getByLabelText("Instructions")).toHaveValue(
      "You are a helpful assistant.",
    );
  });

  it("writes free text to inputParameters.instructions", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    fireEvent.change(screen.getByLabelText("Instructions"), {
      target: { value: "You are a concise assistant." },
    });

    expect(savedTask().inputParameters?.instructions).toBe(
      "You are a concise assistant.",
    );
  });

  it("does not set allowRawPrompts when editing instructions", () => {
    // OSS writes instructions directly via updateField. Enterprise plugins own
    // the prompt-template picker and allowRawPrompts gating.
    render(<Harness initialTask={{ inputParameters: {} }} />);

    fireEvent.change(screen.getByLabelText("Instructions"), {
      target: { value: "System prompt" },
    });

    expect(savedTask().inputParameters?.instructions).toBe("System prompt");
    expect(savedTask().inputParameters?.allowRawPrompts).toBeUndefined();
  });

  it("renders a tooltip and helper text explaining the Instructions field", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(
      screen.getByTestId("Instructions-helperText"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("Instructions-tooltip")).toBeInTheDocument();
    expect(screen.getByTestId("Instructions-tooltip").textContent).toContain(
      "Instructions",
    );
  });
});
