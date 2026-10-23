import { describe, expect, it, vi, beforeEach } from "vitest";
import { refetchCurrentWorkflowVersionsService } from "./services";
import { refetchAllDefinitionsOfCurrentWorkflow } from "../confirmSave/state/services";
import { queryClient } from "queryClient";
import * as fetchModule from "plugins/fetch";

describe("refetchCurrentWorkflowVersionsService", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("fetches versions using the dedicated /metadata/workflow/{name}/versions endpoint", async () => {
    const fetchQuerySpy = vi
      .spyOn(queryClient, "fetchQuery")
      .mockImplementation(async (_key, _fetcher: any) => {
        return [
          { name: "test_wf", version: 1 },
          { name: "test_wf", version: 2 },
        ];
      });

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "test_wf",
      authHeaders: {},
    } as any);

    expect(fetchQuerySpy).toHaveBeenCalledTimes(1);
    const queriedUrl = fetchQuerySpy.mock.calls[0][0][1];
    expect(queriedUrl).toBe("/metadata/workflow/test_wf/versions");
    expect(result).toEqual({ versions: [1, 2] });
  });

  it("encodes special characters in workflowName in the url", async () => {
    const fetchQuerySpy = vi
      .spyOn(queryClient, "fetchQuery")
      .mockImplementation(async () => {
        return [{ name: "my workflow/test", version: 1 }];
      });

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "my workflow/test",
      authHeaders: {},
    } as any);

    const queriedUrl = fetchQuerySpy.mock.calls[0][0][1];
    expect(queriedUrl).toBe("/metadata/workflow/my%20workflow%2Ftest/versions");
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

  it("returns empty versions array when workflowName is 'NEW' without making a request", async () => {
    const fetchQuerySpy = vi.spyOn(queryClient, "fetchQuery");

    const result = await refetchCurrentWorkflowVersionsService({
      workflowName: "NEW",
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

  it("calls /metadata/workflow/{name}/versions and returns summary list", async () => {
    const fetchWithContextSpy = vi
      .spyOn(fetchModule, "fetchWithContext")
      .mockResolvedValue([
        { name: "sample_wf", version: 1, updateTime: 100 },
        { name: "sample_wf", version: 2, updateTime: 200 },
      ] as any);

    const result = await refetchAllDefinitionsOfCurrentWorkflow({
      workflowName: "sample_wf",
      authHeaders: {},
    } as any);

    expect(fetchWithContextSpy).toHaveBeenCalledTimes(1);
    expect(fetchWithContextSpy.mock.calls[0][0]).toBe(
      "/metadata/workflow/sample_wf/versions",
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
