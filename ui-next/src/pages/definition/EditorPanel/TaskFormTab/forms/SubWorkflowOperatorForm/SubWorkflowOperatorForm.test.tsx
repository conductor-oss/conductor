/**
 * CCOR-13460: the backend resolves the latest sub-workflow definition when the
 * version is left blank, but the UI disabled Open unless a version was set —
 * so selecting a version and then clearing it left the button dead. Clearing
 * also wrote an explicit null into the definition instead of dropping the key,
 * which would pin the version rather than letting the backend decide.
 */
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { TaskDef, TaskType } from "types";
import { SubWorkflowOperatorForm } from "./SubWorkflowOperatorForm";

const WORKFLOW = "child_flow";
const OPTIONS = [WORKFLOW, "other_flow"];

vi.mock("@xstate/react", () => ({
  useInterpret: () => ({}),
  useSelector: () => undefined,
  useActor: () => [{ context: {} }, vi.fn()],
}));

vi.mock("../StartWorkflowTaskForm/state/hook", () => ({
  useStartSubWfNameVersionMachine: () => [
    { wfNameOptions: OPTIONS, availableVersions: [1, 2, 3], isFetching: false },
    { handleSelectWorkflowName: vi.fn() },
  ],
}));

vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({ "X-Authorization": "ui-token" }),
}));

vi.mock("pages/definition/commonService", () => ({
  getWorkflowDefinitionByNameAndVersion: vi.fn(async () => ({
    inputParameters: [],
  })),
}));

vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({ label, value, onInputChange }: any) => (
    <input
      aria-label={String(label)}
      value={value ?? ""}
      onChange={(event) => onInputChange(event.target.value)}
    />
  ),
}));

/** Exposes the version field's onChange so clearing can be triggered. */
vi.mock("components/ui/inputs/ConductorAutoComplete", () => ({
  ConductorAutoComplete: ({ label, value, onChange }: any) => (
    <div>
      <span data-testid="version-value">{value ?? "(none)"}</span>
      <button type="button" onClick={() => onChange(null, 2)}>
        {`set ${label}`}
      </button>
      <button type="button" onClick={() => onChange(null, null)}>
        {`clear ${label}`}
      </button>
    </div>
  ),
}));

vi.mock("components/ui/buttons/MuiButton", () => ({
  default: ({ children, disabled, onClick, id }: any) => (
    <button type="button" id={id} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  ),
}));

vi.mock("components/FlatMapForm/ConductorFlatMapForm", () => ({
  ConductorFlatMapFormBase: () => null,
}));
vi.mock("../ConductorObjectOrStringInput", () => ({
  ConductorObjectOrStringInput: () => null,
}));
vi.mock("../OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("pages/runWorkflow/IdempotencyForm", () => ({ default: () => null }));
vi.mock("components/ui/MuiCheckbox", () => ({ default: () => null }));

const openButton = () =>
  document.querySelector("#sub-workflow-main-form-workflow-open-btn");

const renderForm = (task: Partial<TaskDef>) => {
  const onChange = vi.fn();
  render(
    <SubWorkflowOperatorForm task={task as TaskDef} onChange={onChange} />,
  );
  return { onChange };
};

const taskWith = (subWorkflowParam: Record<string, unknown>) =>
  ({
    name: "sub",
    taskReferenceName: "sub_ref",
    type: TaskType.SUB_WORKFLOW,
    subWorkflowParam,
  }) as unknown as Partial<TaskDef>;

describe("SubWorkflowOperatorForm — Open with a blank version", () => {
  beforeEach(() => vi.clearAllMocks());

  it("enables Open when a known workflow has no version", () => {
    renderForm(taskWith({ name: WORKFLOW }));

    expect(openButton()).not.toBeDisabled();
  });

  it("enables Open when a version is set", () => {
    renderForm(taskWith({ name: WORKFLOW, version: 2 }));

    expect(openButton()).not.toBeDisabled();
  });

  it("keeps Open disabled for a name that is not a known workflow", () => {
    // Free text, or a definition that no longer exists.
    renderForm(taskWith({ name: "typed_by_hand" }));

    expect(openButton()).toBeDisabled();
  });

  it("keeps Open disabled when there is no name at all", () => {
    renderForm(taskWith({}));

    expect(openButton()).toBeDisabled();
  });

  it("opens the definition without a version segment, so the latest is shown", () => {
    const open = vi.fn();
    vi.stubGlobal("open", open);
    renderForm(taskWith({ name: WORKFLOW }));

    fireEvent.click(openButton()!);

    expect(open).toHaveBeenCalledWith(`/workflowDef/${WORKFLOW}`);
    vi.unstubAllGlobals();
  });

  it("opens the selected version when one is set", () => {
    const open = vi.fn();
    vi.stubGlobal("open", open);
    renderForm(taskWith({ name: WORKFLOW, version: 2 }));

    fireEvent.click(openButton()!);

    expect(open).toHaveBeenCalledWith(`/workflowDef/${WORKFLOW}/2`);
    vi.unstubAllGlobals();
  });

  it("treats a blank version string as no version", () => {
    const open = vi.fn();
    vi.stubGlobal("open", open);
    renderForm(taskWith({ name: WORKFLOW, version: "" }));

    fireEvent.click(openButton()!);

    expect(open).toHaveBeenCalledWith(`/workflowDef/${WORKFLOW}`);
    vi.unstubAllGlobals();
  });

  it("escapes a name that needs encoding", () => {
    const open = vi.fn();
    vi.stubGlobal("open", open);
    renderForm(taskWith({ name: OPTIONS[1], version: 3 }));

    fireEvent.click(openButton()!);

    expect(open).toHaveBeenCalledWith(`/workflowDef/${OPTIONS[1]}/3`);
    vi.unstubAllGlobals();
  });
});

describe("SubWorkflowOperatorForm — clearing the version", () => {
  beforeEach(() => vi.clearAllMocks());

  it("drops the version key rather than writing null", async () => {
    const { onChange } = renderForm(taskWith({ name: WORKFLOW, version: 2 }));

    fireEvent.click(screen.getByRole("button", { name: "clear Version" }));
    await waitFor(() => expect(onChange).toHaveBeenCalled());

    // An explicit "version": null would pin the field instead of letting the
    // backend resolve the latest.
    const updated = onChange.mock.calls.at(-1)?.[0];
    expect(updated.subWorkflowParam).not.toHaveProperty("version");
    expect(updated.subWorkflowParam.name).toBe(WORKFLOW);
  });

  it("still records a version that was chosen", async () => {
    const { onChange } = renderForm(taskWith({ name: WORKFLOW }));

    fireEvent.click(screen.getByRole("button", { name: "set Version" }));
    // Choosing a version fetches that definition to seed inputParameters, so
    // the change lands asynchronously.
    await waitFor(() => expect(onChange).toHaveBeenCalled());

    const updated = onChange.mock.calls.at(-1)?.[0];
    expect(updated.subWorkflowParam.version).toBe(2);
  });
});

describe("SubWorkflowOperatorForm — clearing the name", () => {
  beforeEach(() => vi.clearAllMocks());

  it("clears the version too, so it cannot attach to the next workflow", () => {
    const { onChange } = renderForm(taskWith({ name: WORKFLOW, version: 2 }));

    fireEvent.change(screen.getByLabelText("Workflow name"), {
      target: { value: "" },
    });

    const updated = onChange.mock.calls.at(-1)?.[0];
    expect(updated.subWorkflowParam).not.toHaveProperty("version");
  });

  it("keeps the version while the name is still being edited", () => {
    const { onChange } = renderForm(taskWith({ name: WORKFLOW, version: 2 }));

    fireEvent.change(screen.getByLabelText("Workflow name"), {
      target: { value: "child" },
    });

    const updated = onChange.mock.calls.at(-1)?.[0];
    expect(updated.subWorkflowParam.version).toBe(2);
    expect(updated.subWorkflowParam.name).toBe("child");
  });

  it("treats a whitespace-only name as cleared", () => {
    const { onChange } = renderForm(taskWith({ name: WORKFLOW, version: 2 }));

    fireEvent.change(screen.getByLabelText("Workflow name"), {
      target: { value: "   " },
    });

    const updated = onChange.mock.calls.at(-1)?.[0];
    expect(updated.subWorkflowParam).not.toHaveProperty("version");
  });
});
