import { describe, expect, it } from "vitest";
import { isWorkflowNotFound } from "./guards";

describe("isWorkflowNotFound", () => {
  it("returns true for 404 fetch errors", () => {
    expect(
      isWorkflowNotFound({} as any, {
        type: "done.invoke.fetchWorkflow",
        data: { message: "Version 99 was not found", status: 404 },
      }),
    ).toBe(true);
  });

  it("returns false for non-404 errors", () => {
    expect(
      isWorkflowNotFound({} as any, {
        type: "done.invoke.fetchWorkflow",
        data: { message: "Failed to fetch workflow", status: 500 },
      }),
    ).toBe(false);
  });

  it("returns false when status is missing", () => {
    expect(
      isWorkflowNotFound({} as any, {
        type: "done.invoke.fetchWorkflow",
        data: { message: "Failed to fetch workflow" },
      }),
    ).toBe(false);
  });
});
