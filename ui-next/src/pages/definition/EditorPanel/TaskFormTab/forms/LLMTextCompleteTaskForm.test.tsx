import { fireEvent, render, screen } from "@testing-library/react";
import { ReactNode, useState } from "react";
import { queryClient } from "queryClient";
import { TaskDef } from "types";
import { LLMTextCompleteTaskForm } from "./LLMTextCompleteTaskForm";

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
  default: ({ label, value, onTextInputChange }: any) => (
    <textarea
      aria-label={label}
      value={value ?? ""}
      onChange={(event) => onTextInputChange?.(event.target.value)}
    />
  ),
}));

vi.mock("components/PromptVariables", () => ({ default: () => null }));
vi.mock(
  "pages/definition/EditorPanel/TaskFormTab/forms/ConductorValueInput",
  () => ({ ConductorValueInput: () => null }),
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
      <LLMTextCompleteTaskForm task={task} onChange={setTask} />
      <pre data-testid="task-json">{JSON.stringify(task)}</pre>
    </>
  );
}

const savedTask = (): Partial<TaskDef> =>
  JSON.parse(screen.getByTestId("task-json").textContent ?? "{}");

describe("LLMTextCompleteTaskForm — Prompt field", () => {
  beforeEach(() => {
    fetchWithContext.mockReset();
    fetchWithContext.mockResolvedValue([]);
    queryClient.clear();
  });

  it("labels the field Prompt (OSS plain textarea, not Prompt Template)", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(screen.getByLabelText("Prompt")).toBeInTheDocument();
    expect(screen.queryByLabelText("Prompt Template")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Prompt Name")).not.toBeInTheDocument();
  });

  it("renders the Prompt and Provider and Model section headers", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(document.getElementById("Prompt-header")).toBeInTheDocument();
    expect(
      document.getElementById("Provider and Model-header"),
    ).toBeInTheDocument();
  });

  it("displays existing prompt from the task", () => {
    render(
      <Harness
        initialTask={{
          inputParameters: { prompt: "Summarize the following text." },
        }}
      />,
    );

    expect(screen.getByLabelText("Prompt")).toHaveValue(
      "Summarize the following text.",
    );
  });

  it("writes free text to inputParameters.prompt", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    fireEvent.change(screen.getByLabelText("Prompt"), {
      target: { value: "Summarize: ${workflow.input.doc}" },
    });

    expect(savedTask().inputParameters?.prompt).toBe(
      "Summarize: ${workflow.input.doc}",
    );
  });

  it("does not set allowRawPrompts when editing prompt", () => {
    // OSS writes prompt directly via updateField. Enterprise plugins own the
    // prompt-template picker and allowRawPrompts gating.
    render(<Harness initialTask={{ inputParameters: {} }} />);

    fireEvent.change(screen.getByLabelText("Prompt"), {
      target: { value: "Free text prompt" },
    });

    expect(savedTask().inputParameters?.prompt).toBe("Free text prompt");
    expect(savedTask().inputParameters?.allowRawPrompts).toBeUndefined();
  });
});
