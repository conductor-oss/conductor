import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
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

vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({
    label,
    value,
    onChange,
    onFocus,
    helperText,
    inputProps,
  }: any) => (
    <div>
      <input
        aria-label={String(label)}
        value={value ?? ""}
        onChange={(event) => onChange(event.target.value)}
        onFocus={() => onFocus?.()}
      />
      {helperText && (
        <span data-testid={`${label}-helperText`}>{helperText}</span>
      )}
      {inputProps?.tooltip && (
        <span data-testid={`${label}-tooltip`}>
          {inputProps.tooltip.title}: {inputProps.tooltip.content}
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
vi.mock("./ConductorCacheOutputForm", () => ({
  ConductorCacheOutput: () => null,
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));

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

describe("LLMChatCompleteTaskForm — Prompt Template field", () => {
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

  it("writes free text to instructions and sets allowRawPrompts (no registry match — the OSS / raw-text path)", async () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);
    await waitFor(() => expect(fetchWithContext).toHaveBeenCalled());

    fireEvent.change(screen.getByLabelText("Prompt Template"), {
      target: { value: "You are a helpful assistant." },
    });

    expect(savedTask().inputParameters?.instructions).toBe(
      "You are a helpful assistant.",
    );
    // ChatCompletion.getPrompt() returns instructions server-side, so this is
    // subject to the same checkPromptAccess/allowRawPrompts gate as promptName.
    expect(savedTask().inputParameters?.allowRawPrompts).toBe(true);
  });

  it("selecting a known template auto-populates variables/temperature/stopWords and clears allowRawPrompts", async () => {
    fetchWithContext.mockImplementation((path: string) => {
      if (path === "/prompts") {
        return Promise.resolve([
          {
            name: "system-greeting",
            variables: ["audience"],
            integrations: [],
            topP: 0.9,
            stopWords: ["END"],
          },
        ]);
      }
      return Promise.resolve([]);
    });

    render(<Harness initialTask={{ inputParameters: {} }} />);
    const field = screen.getByLabelText("Prompt Template");

    await waitFor(() => {
      fireEvent.focus(field);
      expect(fetchWithContext).toHaveBeenCalledWith(
        "/prompts",
        expect.anything(),
        expect.anything(),
      );
    });

    fireEvent.change(field, { target: { value: "system-greeting" } });

    await waitFor(() =>
      expect(savedTask().inputParameters).toMatchObject({
        instructions: "system-greeting",
        allowRawPrompts: false,
        topP: 0.9,
        stopWords: ["END"],
        promptVariables: { audience: "" },
      }),
    );
  });

  it("renders a tooltip and helper text explaining the Instructions field", async () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);
    await waitFor(() => expect(fetchWithContext).toHaveBeenCalled());

    expect(
      screen.getByTestId("Prompt Template-helperText"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("Prompt Template-tooltip")).toBeInTheDocument();
    expect(screen.getByTestId("Prompt Template-tooltip").textContent).toContain(
      "Instructions",
    );
  });
});
