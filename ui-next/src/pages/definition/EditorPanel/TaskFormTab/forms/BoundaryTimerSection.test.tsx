/**
 * The section edits three fields that sit directly on the WorkflowTask, not inside its
 * taskDefinition — a boundary timer has to reach WAIT, HUMAN and the other system tasks, which
 * have no registered definition. These assert against the form machine's context, which is what
 * the Code tab renders, so a control wired to the wrong path fails here. The compiler cannot
 * catch it: taskChanges reaches the section through a loosely typed actor.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { ReactNode } from "react";
import { TaskFormContext } from "pages/definition/EditorPanel/TaskFormTab/state";
import BoundaryTimerSection from "./BoundaryTimerSection";

// The section reads the task through useSelector; the stub actor below is not a real xstate
// service, so read straight from its snapshot.
vi.mock("@xstate/react", () => ({
  useSelector: (actor: any, selector: any) => selector(actor.getSnapshot()),
}));

vi.mock("./TaskFormSection", () => ({
  default: ({ title, children }: { title: string; children: ReactNode }) => (
    <section aria-label={title}>{children}</section>
  ),
}));

vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({ label, value, onChange }: any) => (
    <textarea
      aria-label={label}
      value={value ?? ""}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorSelect", () => ({
  default: ({ label, value, onChange, items }: any) => (
    <select aria-label={label} value={value} onChange={onChange}>
      {items.map((item: any) => (
        <option key={item.value} value={item.value}>
          {item.label}
        </option>
      ))}
    </select>
  ),
}));

vi.mock("./WaitTaskForm/DurationWaitTaskForm", () => ({
  default: ({ value, onChange }: any) => (
    <input
      aria-label="Duration"
      value={value ?? ""}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}));

/**
 * A stand-in for the form actor. The real machine only accepts UPDATE_TASK once rendered and
 * forwards it to a parent, neither of which exists around a single section — and the merge it
 * then performs is the machine's business, covered with the machine. What belongs here is the
 * payload this section builds, so the stub records it.
 */
const renderSection = (task: Record<string, unknown> = {}) => {
  const baseTask = {
    name: "wait",
    taskReferenceName: "wait_ref",
    type: "WAIT",
    ...task,
  };
  const sent: Record<string, any>[] = [];
  const snapshot = { context: { taskChanges: baseTask } };
  const actor = {
    send: (event: { taskChanges: Record<string, any> }) =>
      sent.push(event.taskChanges),
    subscribe: () => ({ unsubscribe: () => {} }),
    getSnapshot: () => snapshot,
  };

  render(
    <TaskFormContext.Provider value={{ formTaskActor: actor } as never}>
      <BoundaryTimerSection />
    </TaskFormContext.Provider>,
  );

  /** The task as this section would hand it to the machine. */
  return { definition: () => sent[sent.length - 1] ?? {}, updates: () => sent };
};

const duration = () => screen.getByLabelText("Duration") as HTMLInputElement;
const status = () =>
  screen.getByLabelText("Completion status") as HTMLSelectElement;

describe("BoundaryTimerSection", () => {
  it("shows the stored duration, status and output", () => {
    renderSection({
      boundaryTimerDuration: "2 days",
      boundaryTimerCompletionStatus: "FAILED",
      boundaryTimerOutput: { reason: "nobody picked it up" },
    });

    expect(duration()).toHaveValue("2 days");
    expect(status()).toHaveValue("FAILED");
    expect(screen.getByLabelText("Output (JSON)")).toHaveValue(
      JSON.stringify({ reason: "nobody picked it up" }, null, 2),
    );
  });

  it("writes the duration at the top level, not under taskDefinition", () => {
    const { definition } = renderSection();

    fireEvent.change(duration(), { target: { value: "2 days" } });

    expect(definition().boundaryTimerDuration).toBe("2 days");
    expect(definition().taskDefinition).toBeUndefined();
  });

  it("persists the default status alongside a new duration", () => {
    // The dropdown reads COMPLETED whether or not one was chosen, so the definition has to
    // say so too rather than stay silent on a status the form shows as selected.
    const { definition } = renderSection();

    fireEvent.change(duration(), { target: { value: "2 days" } });

    expect(definition().boundaryTimerCompletionStatus).toBe("COMPLETED");
  });

  it("does not overwrite a status the user already chose", () => {
    const { definition } = renderSection({
      boundaryTimerCompletionStatus: "FAILED",
    });

    fireEvent.change(duration(), { target: { value: "3 hours" } });

    expect(definition().boundaryTimerCompletionStatus).toBe("FAILED");
  });

  it("keeps the rest when the duration is cleared mid-edit", () => {
    // The duration control emits on every keystroke, so clearing it must not discard a
    // typed-out output.
    const { definition } = renderSection({
      boundaryTimerDuration: "2 days",
      boundaryTimerCompletionStatus: "FAILED",
      boundaryTimerOutput: { reason: "timed out" },
    });

    fireEvent.change(duration(), { target: { value: "" } });

    expect(definition().boundaryTimerDuration).toBeUndefined();
    expect(definition().boundaryTimerCompletionStatus).toBe("FAILED");
    expect(definition().boundaryTimerOutput).toEqual({ reason: "timed out" });
  });

  it("writes a chosen status back", () => {
    const { definition } = renderSection({ boundaryTimerDuration: "2 days" });

    fireEvent.change(status(), { target: { value: "COMPLETED_WITH_ERRORS" } });

    expect(definition().boundaryTimerCompletionStatus).toBe(
      "COMPLETED_WITH_ERRORS",
    );
  });

  it("parses the output JSON into the definition", () => {
    const { definition } = renderSection();

    fireEvent.change(screen.getByLabelText("Output (JSON)"), {
      target: { value: '{"result":"settled"}' },
    });

    expect(definition().boundaryTimerOutput).toEqual({ result: "settled" });
  });

  it("holds the last parsed output while the JSON is mid-edit", () => {
    // Half-typed JSON must not reach the definition at all, or every keystroke would
    // overwrite a valid output with a broken one.
    const { updates } = renderSection({
      boundaryTimerOutput: { result: "settled" },
    });

    fireEvent.change(screen.getByLabelText("Output (JSON)"), {
      target: { value: '{"result":' },
    });

    expect(updates()).toHaveLength(0);
  });
});
