import { describe, expect, it } from "vitest";
import { WorkflowExecution } from "types/Execution";
import { heldMismatch, heldReason } from "./held";

const execution = (status: string, fields: object) =>
  ({ status, tasks: [], ...fields }) as unknown as WorkflowExecution;
const held = {
  lane: "main",
  n: 1,
  state: "RUNNABLE",
  deferred: "diverged: the code changed",
};

describe("heldReason", () => {
  it("finds a hold on a task", () => {
    expect(
      heldReason(execution("RUNNING", { tasks: [{ continuation: held }] })),
    ).toEqual("the code changed");
  });

  it("finds a hold on a lane that has no task", () => {
    expect(
      heldReason(execution("RUNNING", { nativeState: { bootstrap: held } })),
    ).toEqual("the code changed");
  });

  it("ignores other deferrals", () => {
    const waiting = { ...held, deferred: "waiting for handlers: 1 message" };
    expect(
      heldReason(execution("RUNNING", { tasks: [{ continuation: waiting }] })),
    ).toBeUndefined();
  });

  it("does not call a finished execution held", () => {
    expect(
      heldReason(execution("TERMINATED", { tasks: [{ continuation: held }] })),
    ).toBeUndefined();
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
