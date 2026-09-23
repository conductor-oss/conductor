/**
 * Malformed Input Parameters used to be swallowed by the JSON editor's change
 * handler: the parse failed, the model kept its last good value, and Run Test
 * happily executed the task with whatever input the user thought they had
 * replaced. These pin the validation that replaced that silent drop.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TestTask } from "./TestTask";
import { WorkflowExecution } from "types/Execution";

vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({ label, value, onChange, helperText }: any) => (
    <>
      <textarea
        aria-label={String(label)}
        value={value ?? ""}
        onChange={(event) => onChange?.(event.target.value)}
      />
      {helperText && <span role="alert">{helperText}</span>}
    </>
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value, onChange }: any) => (
    <input aria-label={String(label)} value={value ?? ""} onChange={onChange} />
  ),
}));

const MALFORMED_JSON = '{"userId": "user-123", "orderId": "order-456",}';

function renderTestTask(overrides: Record<string, unknown> = {}) {
  const onChangeModel = vi.fn();
  const handleRunTestTask = vi.fn();

  render(
    <TestTask
      taskModel={{}}
      onChangeModel={onChangeModel}
      domain=""
      onChangeDomain={vi.fn()}
      value={{}}
      maxHeight={600}
      handleRunTestTask={handleRunTestTask}
      isInProgress={false}
      onDismiss={vi.fn()}
      testedTaskExecutionResult={{} as WorkflowExecution}
      showForm={false}
      {...overrides}
    />,
  );

  // The panel's trigger button is labelled by the whole popper, so queries have
  // to be scoped to the popper itself to stay unambiguous.
  const panel = within(screen.getByRole("tooltip"));

  return {
    onChangeModel,
    handleRunTestTask,
    panel,
    editor: panel.getByLabelText("Input Parameters"),
    runTest: panel.getByRole("button", { name: /run test/i }),
  };
}

describe("TestTask input parameter validation", () => {
  it("blocks Run Test and explains why when the JSON is malformed", () => {
    const { editor, runTest, onChangeModel, handleRunTestTask } =
      renderTestTask();

    fireEvent.change(editor, { target: { value: MALFORMED_JSON } });

    expect(screen.getByRole("alert")).toHaveTextContent(/invalid json/i);
    expect(runTest).toBeDisabled();
    expect(onChangeModel).not.toHaveBeenCalled();

    fireEvent.click(runTest);
    expect(handleRunTestTask).not.toHaveBeenCalled();
  });

  it("rejects valid JSON that is not an object", () => {
    const { editor, runTest, onChangeModel } = renderTestTask();

    fireEvent.change(editor, { target: { value: "[1, 2, 3]" } });

    expect(screen.getByRole("alert")).toHaveTextContent(
      /must be a JSON object/i,
    );
    expect(runTest).toBeDisabled();
    expect(onChangeModel).not.toHaveBeenCalled();
  });

  it("clears the error and runs once the JSON is corrected", () => {
    const { editor, runTest, onChangeModel, handleRunTestTask } =
      renderTestTask();

    fireEvent.change(editor, { target: { value: MALFORMED_JSON } });
    fireEvent.change(editor, {
      target: { value: '{"userId": "user-123", "orderId": "order-456"}' },
    });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(onChangeModel).toHaveBeenCalledWith({
      userId: "user-123",
      orderId: "order-456",
    });

    expect(runTest).toBeEnabled();
    fireEvent.click(runTest);
    expect(handleRunTestTask).toHaveBeenCalled();
  });

  it("treats an emptied editor as empty input rather than an error", () => {
    const { editor, runTest, onChangeModel } = renderTestTask();

    fireEvent.change(editor, { target: { value: "   " } });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(onChangeModel).toHaveBeenCalledWith({});
    expect(runTest).toBeEnabled();
  });
});
