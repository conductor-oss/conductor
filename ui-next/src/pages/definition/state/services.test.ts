import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as fetchModule from "plugins/fetch";
import { extractKeyFromContext } from "pages/runWorkflow/runWorkflowUtils";
import { queryClient } from "queryClient";
import { refetchAllDefinitionsOfCurrentWorkflow } from "../confirmSave/state/services";
import {
  persistCopyInLocalStorage,
  refetchCurrentWorkflowVersionsService,
} from "./services";
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

describe("refetchCurrentWorkflowVersionsService", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches from /metadata/workflow?includeShared=false&name={name} and filters by workflowName", async () => {
    const fetchQuerySpy = vi
      .spyOn(queryClient, "fetchQuery")
      .mockImplementation(async (_key, _fetcher: any) => {
        return [
          { name: "test_wf", version: 1 },
          { name: "test_wf", version: 2 },
          { name: "other_wf", version: 99 },
        ] as any;
      });

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "test_wf",
      authHeaders: {},
    } as any);

    expect(fetchQuerySpy).toHaveBeenCalledTimes(1);
    const queriedUrl = fetchQuerySpy.mock.calls[0][0][1];
    expect(queriedUrl).toBe(
      "/metadata/workflow?includeShared=false&name=test_wf",
    );
    // Only versions belonging to test_wf are kept; other_wf's version 99 is filtered out
    expect(result).toEqual({ versions: [1, 2] });
  });

  it("encodes special characters in workflowName in the url and matches name", async () => {
    const fetchQuerySpy = vi
      .spyOn(queryClient, "fetchQuery")
      .mockImplementation(async () => {
        return [
          { name: "my workflow/test", version: 1 },
          { name: "unrelated_wf", version: 5 },
        ] as any;
      });

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "my workflow/test",
      authHeaders: {},
    } as any);

    const queriedUrl = fetchQuerySpy.mock.calls[0][0][1];
    expect(queriedUrl).toBe(
      "/metadata/workflow?includeShared=false&name=my%20workflow%2Ftest",
    );
    expect(result).toEqual({ versions: [1] });
  });

  it("returns empty versions array when isNewWorkflow is true without making a request", async () => {
    const fetchQuerySpy = vi.spyOn(queryClient, "fetchQuery");

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "test_wf",
      isNewWorkflow: true,
      authHeaders: {},
    } as any);

    expect(fetchQuerySpy).not.toHaveBeenCalled();
    expect(result).toEqual({ versions: [] });
  });

  it("returns empty versions array when workflowName is empty or undefined", async () => {
    const fetchQuerySpy = vi.spyOn(queryClient, "fetchQuery");

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: undefined,
      authHeaders: {},
    } as any);

    expect(fetchQuerySpy).not.toHaveBeenCalled();
    expect(result).toEqual({ versions: [] });
  });

  it("gracefully catches 404 / errors and returns empty versions array", async () => {
    vi.spyOn(queryClient, "fetchQuery").mockRejectedValue(
      new Error("404 Not Found"),
    );

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "unsaved_wf",
      authHeaders: {},
    } as any);

    expect(result).toEqual({ versions: [] });
  });
});

describe("refetchAllDefinitionsOfCurrentWorkflow", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("calls /metadata/workflow?name={name} and filters definitions by workflowName", async () => {
    const fetchWithContextSpy = vi
      .spyOn(fetchModule, "fetchWithContext")
      .mockResolvedValue([
        { name: "sample_wf", version: 1, updateTime: 100 },
        { name: "sample_wf", version: 2, updateTime: 200 },
        { name: "another_wf", version: 50, updateTime: 300 },
      ] as any);

    const result = await refetchAllDefinitionsOfCurrentWorkflow({
      workflowName: "sample_wf",
      authHeaders: {},
    } as any);

    expect(fetchWithContextSpy).toHaveBeenCalledTimes(1);
    expect(fetchWithContextSpy.mock.calls[0][0]).toBe(
      "/metadata/workflow?name=sample_wf",
    );
    expect(result).toEqual([
      { name: "sample_wf", version: 1, updateTime: 100 },
      { name: "sample_wf", version: 2, updateTime: 200 },
    ]);
  });

  it("gracefully catches errors and returns empty array", async () => {
    vi.spyOn(fetchModule, "fetchWithContext").mockRejectedValue(
      new Error("Network error"),
    );

    const result = await refetchAllDefinitionsOfCurrentWorkflow({
      workflowName: "sample_wf",
      authHeaders: {},
    } as any);

    expect(result).toEqual([]);
  });

  it("returns empty array when workflowName is empty", async () => {
    const fetchWithContextSpy = vi.spyOn(fetchModule, "fetchWithContext");

    const result = await refetchAllDefinitionsOfCurrentWorkflow({
      workflowName: "",
      authHeaders: {},
    } as any);

    expect(fetchWithContextSpy).not.toHaveBeenCalled();
    expect(result).toEqual([]);
  });
});
