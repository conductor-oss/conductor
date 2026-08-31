/**
 * Switching "SQL format" on must carry over the filters that only basic search
 * renders a control for. Previously the SQL box came up empty AND the clauses
 * were dropped from the request, so the results quietly widened.
 *
 * This asserts the params handed to useWorkflowSearch, which is what the box
 * and the request are both built from.
 */
import "@testing-library/jest-dom";
import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { Provider as ThemeProvider } from "theme/material/provider";
import { beforeEach, describe, expect, it, vi } from "vitest";

// First render of this tree (theme + MUI inputs + Monaco stand-in) costs a few
// seconds, which overruns the 5s default once the whole suite is running.
vi.setConfig({ testTimeout: 20000 });

const searchArgs = vi.fn();

vi.mock("utils/query", () => ({
  useWorkflowSearch: (params: unknown) => {
    searchArgs(params);
    return {
      data: undefined,
      error: null,
      isFetching: false,
      refetch: vi.fn(),
    };
  },
  useWorkflowNames: () => [],
}));

vi.mock("../ResultsTable", () => ({ default: () => null }));
vi.mock("../DateControlComponent", () => ({
  DateControlComponent: () => null,
}));
vi.mock("../ApiSearchModalIntegration", () => ({
  ApiSearchModalIntegration: () => null,
}));
vi.mock("../SearchExampleQuery", () => ({ ExampleSearchQuery: () => null }));
vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({
    value,
    label,
  }: {
    value: string;
    label: string;
  }) => <textarea aria-label={label} value={value} readOnly />,
}));

const renderAdvanced = async (search: string) => {
  const { default: AdvancedSearch } = await import("./AdvancedSearch");
  render(
    <MemoryRouter initialEntries={[`/executions${search}`]}>
      <ThemeProvider>
        <AdvancedSearch
          classifier="workflow"
          doSearch={vi.fn()}
          SwitchComponent={null}
          getTableTitle={() => null}
          freeText=""
          setFreeText={vi.fn()}
          status={[]}
          setStatus={vi.fn()}
          startTimeFrom=""
          setStartTimeFrom={vi.fn()}
          onStartFromChange={vi.fn()}
          startTimeTo=""
          setStartTimeTo={vi.fn()}
          onStartToChange={vi.fn()}
          endTimeFrom=""
          setEndTimeFrom={vi.fn()}
          onEndFromChange={vi.fn()}
          endTimeTo=""
          setEndTimeTo={vi.fn()}
          onEndToChange={vi.fn()}
          fromDisplayTime=""
          setFromDisplayTime={vi.fn()}
          toDisplayTime=""
          setToDisplayTime={vi.fn()}
          openDateSelect={false}
          setOpenDateSelect={vi.fn()}
          openStartDatePicker={false}
          setStartOpenDatePicker={vi.fn()}
          openEndDatePicker={false}
          setEndOpenDatePicker={vi.fn()}
          recentSearches={{ start: "", end: "" }}
        />
      </ThemeProvider>
    </MemoryRouter>,
  );
};

const lastQuery = () =>
  (
    searchArgs.mock.calls[searchArgs.mock.calls.length - 1][0] as {
      query: string;
    }
  ).query;

describe("AdvancedSearch — carrying basic-only filters into SQL format", () => {
  beforeEach(() => vi.clearAllMocks());

  it("searches with the workflow name filter from basic search", async () => {
    await renderAdvanced("?asQuery=true&workflowType=TestWorkflow-Aug");

    expect(lastQuery()).toContain("workflowType IN (TestWorkflow-Aug)");
  });

  it("carries workflow id, correlation ids and the exclude toggle", async () => {
    await renderAdvanced(
      "?asQuery=true&workflowId=abc-123&correlationIds=c1&excludeSubExecutions=true",
    );

    expect(lastQuery()).toContain("workflowId='abc-123'");
    expect(lastQuery()).toContain("correlationId IN (c1)");
    expect(lastQuery()).toContain('parentWorkflowId=""');
  });

  it("shows the carried-over filters in the SQL box", async () => {
    const { screen } = await import("@testing-library/react");
    await renderAdvanced("?asQuery=true&workflowType=TestWorkflow-Aug");

    expect(screen.getByLabelText("Search")).toHaveValue(
      "workflowType IN (TestWorkflow-Aug)",
    );
  });

  it("does not override a query the user already typed", async () => {
    await renderAdvanced(
      "?asQuery=true&workflowType=TestWorkflow-Aug&query=workflowId%3D%27typed%27",
    );

    expect(lastQuery()).toContain("workflowId='typed'");
    expect(lastQuery()).not.toContain("TestWorkflow-Aug");
  });

  it("respects an explicitly cleared query box", async () => {
    await renderAdvanced("?asQuery=true&workflowType=TestWorkflow-Aug&query=");

    expect(lastQuery()).not.toContain("TestWorkflow-Aug");
  });

  it("sends no clauses when there is nothing to carry over", async () => {
    await renderAdvanced("?asQuery=true");

    expect(lastQuery()).toBe("");
  });
});
