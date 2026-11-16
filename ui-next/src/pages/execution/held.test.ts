import { describe, expect, it } from "vitest";
import { WorkflowExecution } from "types/Execution";
import { heldAt, heldMismatch, heldReason } from "./held";

const execution = (status: string, fields: object) =>
  ({ status, tasks: [], ...fields }) as unknown as WorkflowExecution;
const held = { reasonForIncompletion: "diverged: the code changed" };

describe("heldReason", () => {
  it("reads the run's reason: one continuation runs at a time", () => {
    expect(heldReason(execution("RUNNING", held))).toEqual("the code changed");
  });

  it("names the step held: the one continuation leased", () => {
    const running = execution("RUNNING", held);
    expect(heldAt(running, { continuation: { leased: true } })).toEqual(
      "the code changed",
    );
    expect(heldAt(running, { continuation: { endTime: 5 } })).toBeUndefined();
  });

  it("ignores other deferrals", () => {
    expect(
      heldReason(
        execution("RUNNING", {
          reasonForIncompletion: "waiting for handlers: 1 message",
        }),
      ),
    ).toBeUndefined();
  });

  it("does not call a finished execution held", () => {
    expect(heldReason(execution("TERMINATED", held))).toBeUndefined();
  });

  it("handles an execution that has not loaded", () => {
    expect(heldReason(undefined)).toBeUndefined();
  });
});

describe("heldMismatch", () => {
  const message = (recorded: string) =>
    `The running code differs from what this execution recorded: at Held.run:52 it calls hold_other, but the recording has ${recorded} there. Deploy a compatible build to continue.`;

  it("reads what the code calls and what was recorded", () => {
    expect(heldMismatch(message("hold_first"))).toEqual({
      site: "Held.run:52",
      calls: "hold_other",
      recorded: "hold_first",
    });
  });

  it("knows when nothing further was recorded", () => {
    expect(heldMismatch(message("no further call"))?.recorded).toBeUndefined();
  });

  it("leaves any other reason alone", () => {
    expect(heldMismatch("switch s took case a but now takes b")).toBe(
      undefined,
    );
  });
});
