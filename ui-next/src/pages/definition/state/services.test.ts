import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { extractKeyFromContext } from "pages/runWorkflow/runWorkflowUtils";
import { persistCopyInLocalStorage } from "./services";
import { DefinitionMachineContext } from "./types";

const context = {
  workflowName: "big_workflow",
  currentVersion: "1",
  isNewWorkflow: false,
  currentWf: { name: "big_workflow", version: 1, tasks: [] },
  workflowChanges: {
    name: "big_workflow",
    version: 1,
    tasks: [{ name: "edited" }],
  },
} as unknown as DefinitionMachineContext;

const wfKey = extractKeyFromContext({
  workflowName: "big_workflow",
  currentVersion: 1,
  isNewWorkflow: false,
});

describe("persistCopyInLocalStorage", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it("stores the draft when there are changes", async () => {
    await expect(persistCopyInLocalStorage(context)).resolves.toBe(
      "Saved to local storage",
    );
    expect(JSON.parse(localStorage.getItem(wfKey)!)).toEqual(
      context.workflowChanges,
    );
  });

  it("rejects and drops the stale draft when the quota is exceeded", async () => {
    localStorage.setItem(wfKey, JSON.stringify({ stale: true }));
    localStorage.setItem(`${wfKey}_localcopyupdatedtime`, "earlier");

    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("quota", "QuotaExceededError");
    });

    await expect(persistCopyInLocalStorage(context)).rejects.toMatchObject({
      name: "QuotaExceededError",
    });
    expect(localStorage.getItem(wfKey)).toBeNull();
    expect(localStorage.getItem(`${wfKey}_localcopyupdatedtime`)).toBeNull();
  });
});
