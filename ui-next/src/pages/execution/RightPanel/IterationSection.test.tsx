/**
 * Tests for IterationSection's WORKFLOW_SUMMARIZE feature-flag gate.
 *
 * `summarizeEnabled` is a module-level constant evaluated at import time.
 * To test both flag values without expensive per-test module resets, each
 * describe block calls vi.resetModules() + vi.doMock() once in beforeAll and
 * imports IterationSection a single time. Tests in the block reuse that import.
 *
 * InlineTaskIterations and DoWhileIteration are replaced with lightweight stubs
 * that render the real SummarizeToggle when they receive an onToggleSummarize
 * callback — the same conditional the real components implement. This keeps
 * the module tree small (fast) while still asserting on actual rendered UI.
 * Dedicated component tests for each child component belong in their own files.
 *
 * Only useFullWorkflowQuery is mocked to avoid requiring a QueryClientProvider.
 */

import { render, screen } from "@testing-library/react";
import React from "react";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { SummarizeToggle } from "./SummarizeToggle";

// ---------------------------------------------------------------------------
// Minimal props
// ---------------------------------------------------------------------------

const doWhileTask = {
  taskType: "DO_WHILE",
  taskId: "task-dw-1",
  referenceTaskName: "my_loop",
  status: "COMPLETED",
  iteration: 0,
  outputData: { "1": {}, "2": {} },
  inputData: {},
  workflowTask: {
    taskReferenceName: "my_loop",
    name: "my_loop",
    type: "DO_WHILE",
  },
};

const retryOptions = [
  {
    iteration: 2,
    status: "COMPLETED",
    taskId: "t2",
    workflowTask: { taskReferenceName: "inner" },
  },
  {
    iteration: 1,
    status: "COMPLETED",
    taskId: "t1",
    workflowTask: { taskReferenceName: "inner" },
  },
];

const baseProps = {
  selectedTask: doWhileTask as any,
  retryIterationOptions: retryOptions as any,
  isIteration: true,
  handleSelectTask: vi.fn(),
  handleSelectDoWhileIteration: vi.fn(),
  doWhileSelection: [],
};

// ---------------------------------------------------------------------------
// Child-component stubs.
// Each renders the real SummarizeToggle when onToggleSummarize is provided,
// mirroring the conditional the real components implement. This tests that
// IterationSection passes the right prop AND that the real toggle renders,
// without pulling in react-query / virtualizer module trees.
// ---------------------------------------------------------------------------

function IterationListStub({
  onToggleSummarize,
  isSummarized,
  "data-testid": testId,
}: any) {
  return (
    <div data-testid={testId}>
      {onToggleSummarize && (
        <SummarizeToggle
          checked={!!isSummarized}
          onChange={onToggleSummarize}
        />
      )}
    </div>
  );
}

function setupMocks(flagEnabled: boolean) {
  vi.doMock("utils/flags", () => ({
    featureFlags: {
      isEnabled: () => flagEnabled,
      getValue: () => undefined,
      getContextValue: () => undefined,
    },
    FEATURES: { WORKFLOW_SUMMARIZE: "WORKFLOW_SUMMARIZE" },
  }));
  vi.doMock("./useFullWorkflowQuery", () => ({
    useFullWorkflowQuery: () => ({ data: undefined, isFetching: false }),
  }));
  vi.doMock("./InlineTaskIterations", () => ({
    InlineTaskIterations: (props: any) =>
      React.createElement(IterationListStub, {
        ...props,
        "data-testid": "inline-iterations",
      }),
  }));
  vi.doMock("./DoWhileIteration", () => ({
    DoWhileIteration: (props: any) =>
      React.createElement(IterationListStub, {
        ...props,
        "data-testid": "do-while-iteration",
      }),
  }));
  vi.doMock("./SummarizeConfirmDialog", () => ({
    SummarizeConfirmDialog: () => null,
  }));
}

// ---------------------------------------------------------------------------
// flag = true
// ---------------------------------------------------------------------------

describe("IterationSection — WORKFLOW_SUMMARIZE enabled", () => {
  let IterationSection: React.ComponentType<any>;

  beforeAll(async () => {
    vi.resetModules();
    setupMocks(true);
    IterationSection = (await import("./IterationSection")).IterationSection;
  });

  afterAll(() => vi.resetModules());

  it("renders SummarizeToggle inside InlineTaskIterations", () => {
    render(<IterationSection {...baseProps} />);
    expect(screen.getByTestId("inline-iterations")).toHaveTextContent(
      "Summarize",
    );
  });

  it("renders SummarizeToggle inside DoWhileIteration", () => {
    render(<IterationSection {...baseProps} />);
    expect(screen.getByTestId("do-while-iteration")).toHaveTextContent(
      "Summarize",
    );
  });
});

// ---------------------------------------------------------------------------
// flag = false (OSS default via context.js)
// ---------------------------------------------------------------------------

describe("IterationSection — WORKFLOW_SUMMARIZE disabled (OSS)", () => {
  let IterationSection: React.ComponentType<any>;

  beforeAll(async () => {
    vi.resetModules();
    setupMocks(false);
    IterationSection = (await import("./IterationSection")).IterationSection;
  });

  afterAll(() => vi.resetModules());

  it("does not render SummarizeToggle inside InlineTaskIterations", () => {
    render(<IterationSection {...baseProps} />);
    expect(screen.getByTestId("inline-iterations")).not.toHaveTextContent(
      "Summarize",
    );
  });

  it("does not render SummarizeToggle inside DoWhileIteration", () => {
    render(<IterationSection {...baseProps} />);
    expect(screen.getByTestId("do-while-iteration")).not.toHaveTextContent(
      "Summarize",
    );
  });

  it("does not render SummarizeConfirmDialog", () => {
    render(<IterationSection {...baseProps} />);
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
