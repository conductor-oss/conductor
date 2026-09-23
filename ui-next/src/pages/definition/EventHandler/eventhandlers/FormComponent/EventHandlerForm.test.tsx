/**
 * The Event tab must write every field Save / the Code tab depend on into the
 * form machine. Playwright create only fills name (+ description) and relies
 * on the default template for event/actions — so a miswired control for event,
 * condition, Active, or Add action would not fail E2E. These assert against
 * machine context the same way TaskDefinitionForm tests do.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { interpret } from "xstate";
import { describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import EventHandlerForm from "./EventHandlerForm";
import { eventFormMachine } from "./state/machine";
import { NEW_EVENT_HANDLER_TEMPLATE } from "../eventHandlerSchema";
import { Action } from "./state/types";
import { START_WORKFLOW_ACTION } from "../eventHandlerSchema";

vi.mock("utils/hooks/useEventNameSuggestions", () => ({
  useEventNameSuggestions: () => ["kafka:sampleConfig"],
}));

vi.mock("components/ui/inputs", () => ({
  ConductorAutoComplete: ({ label, value, onChange, onInputChange }: any) => (
    <input
      aria-label={String(label)}
      id="event-string-input"
      value={value ?? ""}
      onChange={(event) => {
        onChange?.(null, event.target.value);
        onInputChange?.(null, event.target.value);
      }}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value, onTextInputChange, id, name }: any) => (
    <input
      aria-label={label}
      id={id}
      name={name}
      value={value ?? ""}
      onChange={(event) => onTextInputChange?.(event.target.value)}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorSelect", () => ({
  default: ({ label, value, onChange, name, inputProps }: any) => (
    <select
      aria-label={inputProps?.["aria-label"] ?? label}
      name={name}
      value={value ?? ""}
      onChange={(event) =>
        onChange?.({ target: { value: event.target.value } })
      }
    >
      <option value="">Select</option>
      <option value="complete_task">Complete Task</option>
      <option value="fail_task">Fail Task</option>
      <option value="start_workflow">Start Workflow</option>
      <option value="start_agent">Start Agent</option>
      <option value="terminate_workflow">Terminate Workflow</option>
      <option value="update_workflow_variables">Update Variables</option>
    </select>
  ),
}));

vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({ label, value, onChange }: any) => (
    <textarea
      aria-label={label}
      value={value ?? ""}
      onChange={(event) => onChange?.(event.target.value)}
    />
  ),
}));

// Action sub-forms are covered elsewhere; stub them so this file stays on
// EventHandlerForm wiring (fields + Add action + Active).
vi.mock("./ActionForms/CompleteTask", () => ({
  CompleteTask: () => <div>Complete Task body</div>,
}));
vi.mock("./ActionForms/FailTask", () => ({
  FailTask: () => <div>Fail Task</div>,
}));
vi.mock("./ActionForms/StartWorkflowTask", () => ({
  StartWorkflowActionForm: () => <div>Start Workflow body</div>,
}));
vi.mock("./ActionForms/StartAgentTask", () => ({
  StartAgentActionForm: () => <div>Start Agent</div>,
}));
vi.mock("./ActionForms/TerminateWorkflowTask", () => ({
  TerminateWorkflowForm: () => <div>Terminate Workflow</div>,
}));
vi.mock("./ActionForms/UpdateWorkflowTask", () => ({
  UpdateWorkflowForm: () => <div>Update Variables</div>,
}));

/** The action union is narrowed per variant; tests poke at it structurally. */
const actionAt = (definition: { actions?: unknown[] }, index: number) =>
  (definition.actions ?? [])[index] as any;

const renderForm = (overrides: Record<string, unknown> = {}) => {
  const eventAsJson = {
    ...NEW_EVENT_HANDLER_TEMPLATE,
    active: true,
    ...overrides,
  };
  const service = interpret(
    eventFormMachine.withContext({
      eventAsJson,
      originalSource: { ...eventAsJson },
    }),
  ).start();

  render(
    <ThemeProvider>
      <EventHandlerForm actor={service as never} />
    </ThemeProvider>,
  );

  return {
    service,
    definition: () => service.getSnapshot().context.eventAsJson,
  };
};

describe("EventHandlerForm — fields write to machine context", () => {
  it("shows stored values for name, description, event, and condition", () => {
    renderForm({
      name: "eh_existing",
      description: "existing desc",
      event: "conductor:existing",
      condition: "true",
    });

    expect(screen.getByLabelText("Name")).toHaveValue("eh_existing");
    expect(screen.getByLabelText("Description")).toHaveValue("existing desc");
    expect(screen.getByLabelText("Event")).toHaveValue("conductor:existing");
    expect(
      screen.getByLabelText("Condition (Trigger if evaluated to true)"),
    ).toHaveValue("true");
  });

  it("writes name, description, event, and condition into context", () => {
    const { definition } = renderForm({ actions: [] });

    fireEvent.change(screen.getByLabelText("Name"), {
      target: { value: "eh_from_form" },
    });
    fireEvent.change(screen.getByLabelText("Description"), {
      target: { value: "wired description" },
    });
    fireEvent.change(screen.getByLabelText("Event"), {
      target: { value: "sqs:myQueue" },
    });
    fireEvent.change(
      screen.getByLabelText("Condition (Trigger if evaluated to true)"),
      { target: { value: "$.ok === true" } },
    );

    expect(definition()).toMatchObject({
      name: "eh_from_form",
      description: "wired description",
      event: "sqs:myQueue",
      condition: "$.ok === true",
    });
  });

  it("toggles Active into context", () => {
    const { definition } = renderForm({ active: true, actions: [] });

    fireEvent.click(screen.getByLabelText("Active"));

    expect(definition().active).toBe(false);
  });

  it("shows an empty state until an action is added", () => {
    renderForm({ actions: [] });

    expect(screen.getByText(/No actions yet/i)).toBeInTheDocument();
    expect(screen.getByText("Actions")).toBeInTheDocument();
  });

  it("adds a Start Workflow action from the Add action menu", () => {
    const { definition } = renderForm({ actions: [] });

    fireEvent.click(screen.getByRole("button", { name: /Add action/i }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Start Workflow/i }));

    expect(definition().actions).toEqual([START_WORKFLOW_ACTION]);
    // Action form stub rendered for the added start_workflow entry.
    expect(screen.getByText("Start Workflow body")).toBeInTheDocument();
  });

  // The Actions section is last on the page, so a newly added card can land
  // below the fold. jsdom has no scrollIntoView, so install one to assert on.
  it("scrolls the newly added action into view", () => {
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;

    renderForm({ actions: [] });
    expect(scrollIntoView).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: /Add action/i }));
    fireEvent.click(screen.getByRole("menuitem", { name: /Start Workflow/i }));

    expect(scrollIntoView).toHaveBeenCalled();
  });

  it("removes an action from the action card header", () => {
    const { definition } = renderForm();

    expect(definition().actions).toHaveLength(1);
    fireEvent.click(screen.getByLabelText("Remove action"));
    expect(definition().actions).toEqual([]);
  });

  it("swaps the action payload when the type is changed in place", () => {
    const { definition } = renderForm();

    expect(actionAt(definition(), 0).action).toBe(Action.COMPLETE_TASK);

    fireEvent.change(screen.getByLabelText("Action 1 type"), {
      target: { value: Action.START_WORKFLOW },
    });

    expect(definition().actions).toEqual([START_WORKFLOW_ACTION]);
    expect(screen.getByText("Start Workflow body")).toBeInTheDocument();
    expect(screen.queryByText("Complete Task body")).not.toBeInTheDocument();
  });

  // Collapse unmounts the body only once its transition ends, so both
  // directions need waitFor rather than a synchronous assertion.
  it("collapses and expands an action body", async () => {
    renderForm();

    expect(screen.getByText("Complete Task body")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Collapse action"));
    await waitFor(() =>
      expect(screen.queryByText("Complete Task body")).not.toBeInTheDocument(),
    );

    fireEvent.click(screen.getByLabelText("Expand action"));
    await waitFor(() =>
      expect(screen.getByText("Complete Task body")).toBeInTheDocument(),
    );
  });

  it("changing the type does not write through to the shared template", () => {
    const { definition } = renderForm();

    fireEvent.change(screen.getByLabelText("Action 1 type"), {
      target: { value: Action.START_WORKFLOW },
    });

    actionAt(definition(), 0).start_workflow.name = "mutated";

    expect(START_WORKFLOW_ACTION.start_workflow.name).toBe("sample_wf");
  });
});
