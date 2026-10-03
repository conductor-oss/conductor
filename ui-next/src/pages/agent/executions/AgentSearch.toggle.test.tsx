/**
 * Switching "SQL format" off must carry a typed query back into basic search's
 * fields, and ask first when the query says something basic search has no
 * control for. Previously the query was simply dropped.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { Provider as ThemeProvider } from "theme/material/provider";
import { beforeEach, describe, expect, it, vi } from "vitest";

// First render of this tree (theme + MUI inputs + Monaco stand-in) costs a few
// seconds, which overruns the 5s default once the whole suite is running.
vi.setConfig({ testTimeout: 20000 });

vi.mock("utils/query", () => ({
  useWorkflowSearch: () => ({
    data: undefined,
    error: null,
    isFetching: false,
    refetch: vi.fn(),
  }),
  useAgentNames: () => [],
}));

// Needs the app's env provider, and nothing here navigates.
vi.mock("utils/hooks/usePushHistory", () => ({
  usePushHistory: () => vi.fn(),
}));

// Page chrome, irrelevant to the filters.
vi.mock("components/layout/SectionHeader", () => ({ default: () => null }));

vi.mock("./ResultsTable", () => ({ default: () => null }));
vi.mock("./DateControlComponent", () => ({ DateControlComponent: () => null }));
vi.mock("./ApiSearchModalIntegration", () => ({
  ApiSearchModalIntegration: () => null,
}));
vi.mock("./SearchExampleQuery", () => ({ ExampleSearchQuery: () => null }));
vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({
    value,
    label,
  }: {
    value: string;
    label: string;
  }) => <textarea aria-label={label} value={value} readOnly />,
}));

const renderAgentSearch = async (search: string) => {
  const { default: AgentSearch } = await import("./AgentSearch");
  render(
    <MemoryRouter initialEntries={[`/agent-executions${search}`]}>
      <ThemeProvider>
        <AgentSearch />
      </ThemeProvider>
    </MemoryRouter>,
  );
};

describe("AgentSearch — Reset", () => {
  beforeEach(() => vi.clearAllMocks());

  /**
   * Each mode used to clear only the fields it renders, so resetting in SQL
   * format left the basic-only filters set and they reappeared on switching
   * back.
   */
  it("clears the basic-only filters when reset in SQL format", async () => {
    await renderAgentSearch("?asQuery=true&workflowId=abc-123");

    fireEvent.click(screen.getByRole("button", { name: "Reset" }));
    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(screen.getByLabelText("Execution id")).toHaveValue("");
  });
});

describe("AgentSearch — carrying a typed query back to basic search", () => {
  beforeEach(() => vi.clearAllMocks());

  it("reads a representable query back into the basic fields", async () => {
    await renderAgentSearch("?asQuery=true&query=workflowId%3D%27abc-123%27");

    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(screen.queryByLabelText("Search")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Execution id")).toHaveValue("abc-123");
  });

  it("asks before discarding a query basic search cannot express", async () => {
    await renderAgentSearch("?asQuery=true&query=version%3D2");

    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(await screen.findByText("Discard SQL query?")).toBeInTheDocument();
    // Still in SQL format until the choice is made.
    expect(screen.getByLabelText("Search")).toBeInTheDocument();
  });

  it("asks before discarding a clause only the workflow search can apply", async () => {
    // parentWorkflowId is parsed for the workflow search's "exclude
    // sub-executions" toggle. The agent page's equivalent goes out as the
    // topLevelOnly request param, so there is no field for the clause.
    await renderAgentSearch('?asQuery=true&query=parentWorkflowId%3D""');

    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(await screen.findByText("Discard SQL query?")).toBeInTheDocument();
  });
});
