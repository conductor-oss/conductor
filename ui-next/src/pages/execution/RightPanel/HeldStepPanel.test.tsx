import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WorkflowExecution } from "types/Execution";
import { HeldStepPanel } from "./HeldStepPanel";

const execution = (status: string, deferred?: string) =>
  ({
    status,
    workflowName: "orders",
    workflowVersion: 3,
    tasks: [
      {
        referenceTaskName: "sleep",
        continuation: { attempts: 5, queue: "orders.v3.code", deferred },
      },
    ],
  }) as unknown as WorkflowExecution;

const open = (e: WorkflowExecution) =>
  render(<HeldStepPanel pausedOn="sleep" execution={e} onClose={vi.fn()} />);

describe("HeldStepPanel", () => {
  it("says what was expected, what was found and what to do", () => {
    open(
      execution(
        "RUNNING",
        "diverged: The running code differs from what this execution recorded: at Held.run:52 it calls hold_other, but the recording has hold_first there. Deploy a compatible build to continue.",
      ),
    );
    expect(screen.getByText("hold_first")).toBeTruthy();
    expect(screen.getByText("hold_other")).toBeTruthy();
    expect(screen.getByText("Held.run:52")).toBeTruthy();
    expect(screen.getByText("orders.v3.code")).toBeTruthy();
    expect(screen.getByText(/It has not failed/)).toBeTruthy();
  });

  it("shows any other reason as it is", () => {
    open(execution("RUNNING", "diverged: switch s now takes case b"));
    expect(screen.getByText(/switch s now takes case b/)).toBeTruthy();
  });

  it.each([
    ["the code matched again", execution("RUNNING")],
    [
      "the execution finished",
      execution("TERMINATED", "diverged: the code changed"),
    ],
  ])("stops claiming to wait once %s", (_name, e) => {
    open(e);
    expect(screen.getByText(/no longer waiting/)).toBeTruthy();
    expect(screen.queryByText(/What you can do/)).toBeNull();
  });
});
