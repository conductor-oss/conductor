import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { queryClient } from "queryClient";
import { TaskDef } from "types";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents } from "utils/fieldHelpers";
import { LLMInstructionsWithPromptPicker } from "./LLMInstructionsWithPromptPicker";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";

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
    placeholder,
  }: any) => (
    <input
      aria-label={String(label)}
      value={value ?? ""}
      placeholder={placeholder}
      onChange={(event) => onChange(event.target.value)}
      onFocus={() => onFocus?.()}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value, onTextInputChange, placeholder }: any) => (
    <textarea
      aria-label={label}
      value={value ?? ""}
      placeholder={placeholder}
      onChange={(event) => onTextInputChange?.(event.target.value)}
    />
  ),
}));

vi.mock("components/PromptVariables", () => ({
  default: ({ currentVariables }: any) => (
    <div data-testid="prompt-variables">{JSON.stringify(currentVariables)}</div>
  ),
}));

vi.mock(
  "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields/ConductorArrayMapForm",
  () => ({ ConductorArrayMapFormBase: () => null }),
);

vi.mock(
  "pages/definition/EditorPanel/TaskFormTab/forms/ConductorValueInput",
  () => ({ ConductorValueInput: () => null }),
);

const instructionsField = fieldsToFieldsFieldsComponents([
  UiIntegrationsFieldType.INSTRUCTIONS,
]);

function Harness({ initialTask }: { initialTask: Partial<TaskDef> }) {
  const [task, setTask] = useState(initialTask);
  return (
    <>
      <LLMFormFieldsWrapper
        task={task}
        onChange={setTask}
        allFieldComponents={instructionsField}
      >
        {(actor) => (
          <LLMInstructionsWithPromptPicker
            task={task}
            onChange={setTask}
            actor={actor}
          />
        )}
      </LLMFormFieldsWrapper>
      <pre data-testid="task-json">{JSON.stringify(task)}</pre>
    </>
  );
}

const savedTask = (): Partial<TaskDef> =>
  JSON.parse(screen.getByTestId("task-json").textContent ?? "{}");

describe("LLMInstructionsWithPromptPicker", () => {
  beforeEach(() => {
    fetchWithContext.mockReset();
    fetchWithContext.mockResolvedValue([]);
    queryClient.clear();
  });

  it("renders the AI Prompt picker and custom instructions toggle", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(screen.getByLabelText("Prompt Template")).toBeInTheDocument();
    expect(screen.getByText("Write custom instructions")).toBeInTheDocument();
  });

  it("auto-expands custom instructions when prompt registry is empty", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    expect(screen.getByLabelText("Instructions")).toBeInTheDocument();
  });

  it("writes custom instructions and sets allowRawPrompts=true", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    fireEvent.change(screen.getByLabelText("Instructions"), {
      target: { value: "You are a concise assistant." },
    });

    const task = savedTask();
    expect(task.inputParameters?.instructions).toBe(
      "You are a concise assistant.",
    );
    expect(task.inputParameters?.allowRawPrompts).toBe(true);
  });

  it("clears promptVariables when writing custom instructions", () => {
    render(
      <Harness
        initialTask={{
          inputParameters: {
            instructions: "old-prompt",
            promptVariables: { key: "value" },
          },
        }}
      />,
    );

    fireEvent.change(screen.getByLabelText("Instructions"), {
      target: { value: "New custom text" },
    });

    const task = savedTask();
    expect(task.inputParameters?.promptVariables).toEqual({});
    expect(task.inputParameters?.allowRawPrompts).toBe(true);
  });

  it("displays existing custom instructions on load", () => {
    render(
      <Harness
        initialTask={{
          inputParameters: {
            instructions: "You are helpful.",
            allowRawPrompts: true,
          },
        }}
      />,
    );

    expect(screen.getByLabelText("Instructions")).toHaveValue(
      "You are helpful.",
    );
  });

  it("shows the AI Prompt picker with empty value when no prompt is selected", () => {
    render(<Harness initialTask={{ inputParameters: {} }} />);

    const picker = screen.getByLabelText("Prompt Template");
    expect(picker).toHaveValue("");
    expect(picker).toHaveAttribute(
      "placeholder",
      "Select a saved AI Prompt...",
    );
  });
});
