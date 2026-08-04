import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
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

vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({
    label,
    value,
    onChange,
    onFocus,
  }: any) => (
    <input
      aria-label={String(label)}
      value={value ?? ""}
      onChange={(event) => onChange(event.target.value)}
      onFocus={() => onFocus?.()}
    />
  ),
}));

vi.mock("components/PromptVariables", () => ({ default: () => null }));
vi.mock(
  "pages/definition/EditorPanel/TaskFormTab/forms/ConductorValueInput",
  () => ({ ConductorValueInput: () => null }),
);
vi.mock("./ConductorCacheOutputForm", () => ({
  ConductorCacheOutput: () => null,
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));

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

describe("LLMTextCompleteTaskForm — Prompt Template field", () => {
  beforeEach(() => {
    fetchWithContext.mockReset();
    fetchWithContext.mockResolvedValue([]);
    queryClient.clear();
  });

  it("labels the field Prompt Template, not Prompt Name", async () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);
    expect(screen.getByLabelText("Prompt Template")).toBeInTheDocument();
    expect(screen.queryByLabelText("Prompt Name")).not.toBeInTheDocument();
    await waitFor(() => expect(fetchWithContext).toHaveBeenCalled());
  });

  it("writes free text to promptName and sets allowRawPrompts (no registry match — the OSS / raw-text path)", async () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);
    await waitFor(() => expect(fetchWithContext).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText("Prompt Template"), {
      target: { value: "Summarize: ${workflow.input.doc}" },
    });

    expect(savedTask().inputParameters?.promptName).toBe(
      "Summarize: ${workflow.input.doc}",
    );
    // Without this, Orkes' checkPromptAccess terminates the workflow at runtime
    // because free text is never an "associated" prompt name.
    expect(savedTask().inputParameters?.allowRawPrompts).toBe(true);
  });

  it("falls back to the legacy prompt key for display when promptName is unset", async () => {
    render(
      <Harness
        initialTask={{ inputParameters: { prompt: "Legacy free text" } }}
      />,
    );
    expect(screen.getByLabelText("Prompt Template")).toHaveValue(
      "Legacy free text",
    );
    await waitFor(() => expect(fetchWithContext).toHaveBeenCalled());
  });

  it("selecting a known template auto-populates variables/temperature/stopWords and clears allowRawPrompts", async () => {
    fetchWithContext.mockImplementation((path: string) => {
      if (path === "/prompts") {
        return Promise.resolve([
          {
            name: "greeting",
            variables: ["name"],
            integrations: [],
            temperature: 0.4,
            stopWords: ["STOP"],
          },
        ]);
      }
      return Promise.resolve([]);
    });

    render(<Harness initialTask={{ inputParameters: {} }} />);
    const field = screen.getByLabelText("Prompt Template");

    // FOCUS_PROMPT_NAME is only handled once the machine reaches IDLE (after the
    // LLM provider options fetch that's kicked off on mount settles) — retry focus
    // until the /prompts fetch it should trigger has actually fired.
    await waitFor(() => {
      fireEvent.focus(field);
      expect(fetchWithContext).toHaveBeenCalledWith(
        "/prompts",
        expect.anything(),
        expect.anything(),
      );
    });

    fireEvent.change(field, { target: { value: "greeting" } });

    await waitFor(() =>
      expect(savedTask().inputParameters).toMatchObject({
        promptName: "greeting",
        allowRawPrompts: false,
        temperature: 0.4,
        stopWords: ["STOP"],
        promptVariables: { name: "" },
      }),
    );
  });
});
