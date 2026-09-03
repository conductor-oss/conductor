import { describe, expect, it } from "vitest";
import {
  hasWorkflowInputParams,
  isSaveAndRunFromRunTab,
  isSaveAndRunWithInputParams,
  isWorkflowNotFound,
} from "./guards";
import { CODE_TAB, RUN_TAB } from "./constants";
import { DefinitionMachineEventTypes } from "./types";

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

describe("isSaveAndRunFromRunTab", () => {
  const workflow = { name: "wf", inputParameters: ["orderId"] };
  const saveAndRun = {
    type: DefinitionMachineEventTypes.SAVE_EVT,
    isSaveAndRun: true,
  } as any;

  const context = (overrides: Record<string, unknown>) =>
    ({
      currentWf: workflow,
      workflowChanges: workflow,
      ...overrides,
    }) as any;

  it("is true on the run tab, so executing keeps the edited inputs", () => {
    expect(
      isSaveAndRunFromRunTab(context({ openedTab: RUN_TAB }), saveAndRun),
    ).toBe(true);
  });

  it("is false on other tabs, where there is no run form to preserve", () => {
    expect(
      isSaveAndRunFromRunTab(context({ openedTab: CODE_TAB }), saveAndRun),
    ).toBe(false);
  });

  it("is false while there are unsaved changes, which have to be saved first", () => {
    expect(
      isSaveAndRunFromRunTab(
        context({
          openedTab: RUN_TAB,
          workflowChanges: { ...workflow, description: "edited" },
        }),
        saveAndRun,
      ),
    ).toBe(false);
  });

  it("is false for a plain save that was not a run request", () => {
    expect(
      isSaveAndRunFromRunTab(context({ openedTab: RUN_TAB }), {
        type: DefinitionMachineEventTypes.SAVE_EVT,
      } as any),
    ).toBe(false);
  });
});

describe("hasWorkflowInputParams", () => {
  it("is true when the current definition lists input parameters", () => {
    expect(
      hasWorkflowInputParams({
        workflowChanges: { inputParameters: ["orderId"] },
      } as any),
    ).toBe(true);
  });

  it("falls back to the saved definition when editor changes are missing", () => {
    expect(
      hasWorkflowInputParams({
        currentWf: { inputParameters: ["orderId"] },
      } as any),
    ).toBe(true);
  });

  it("is false when inputParameters is missing or empty", () => {
    expect(hasWorkflowInputParams({ workflowChanges: {} } as any)).toBe(false);
    expect(
      hasWorkflowInputParams({
        workflowChanges: { inputParameters: [] },
      } as any),
    ).toBe(false);
  });
});

describe("isSaveAndRunWithInputParams", () => {
  const workflow = { name: "wf", inputParameters: ["orderId"] };
  const saveAndRun = {
    type: DefinitionMachineEventTypes.SAVE_EVT,
    isSaveAndRun: true,
  } as any;

  it("is true off the run tab when the workflow declares input parameters", () => {
    expect(
      isSaveAndRunWithInputParams(
        {
          openedTab: CODE_TAB,
          currentWf: workflow,
          workflowChanges: workflow,
        } as any,
        saveAndRun,
      ),
    ).toBe(true);
  });

  it("is false on the run tab, where Execute should start the workflow", () => {
    expect(
      isSaveAndRunWithInputParams(
        {
          openedTab: RUN_TAB,
          currentWf: workflow,
          workflowChanges: workflow,
        } as any,
        saveAndRun,
      ),
    ).toBe(false);
  });

  it("is false when the workflow has no input parameters", () => {
    const noInputs = { name: "wf", inputParameters: [] };
    expect(
      isSaveAndRunWithInputParams(
        {
          openedTab: CODE_TAB,
          currentWf: noInputs,
          workflowChanges: noInputs,
        } as any,
        saveAndRun,
      ),
    ).toBe(false);
  });
});
