import { useState } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import ResultsTable, { ResultsTableProps } from "../ResultsTable";

vi.mock("utils/agentFetch", () => ({
  agentFetch: vi.fn((input: string) => {
    if (input === "/api/agent/list") {
      return Promise.resolve({ json: () => Promise.resolve([]) } as Response);
    }
    return Promise.resolve({
      json: () => Promise.resolve({ tasks: [] }),
    } as Response);
  }),
}));

// The bug this suite guards against lives in how `expandableRows` is wired
// to the DataTable, so the mock mirrors that one prop instead of pulling in
// the real react-data-table-component (which needs router/matchMedia setup
// unrelated to this fix).
vi.mock("components", () => ({
  DataTable: ({ data, expandableRows }: any) => (
    <div data-testid="data-table">
      {data.map((row: any) => (
        <div key={row.workflowId} data-testid={`row-${row.workflowId}`}>
          {expandableRows && (
            <button aria-label={`Expand row ${row.workflowId}`}>
              expand
            </button>
          )}
        </div>
      ))}
    </div>
  ),
  NavLink: ({ children, path }: any) => <a href={path}>{children}</a>,
  Paper: ({ children }: any) => <div>{children}</div>,
  Text: ({ children }: any) => <div>{children}</div>,
}));

vi.mock("../BulkActionModule", () => ({
  default: () => null,
}));

const resultObj = {
  totalHits: 1,
  results: [
    {
      workflowId: "wf-1",
      workflowType: "top_level_agent",
      status: "COMPLETED",
      startTime: 0,
      endTime: 1000,
    },
  ],
};

function baseProps(
  overrides: Partial<ResultsTableProps> = {},
): ResultsTableProps {
  return {
    resultObj,
    page: 1,
    rowsPerPage: 15,
    setPage: vi.fn(),
    setSort: vi.fn(),
    refetchExecution: vi.fn(),
    filterOn: false,
    handleReset: vi.fn(),
    hideSubWorkflows: false,
    setHideSubWorkflows: vi.fn(),
    ...overrides,
  };
}

function getExpander() {
  return screen.queryByRole("button", { name: "Expand row wf-1" });
}

function ControlledResultsTable({
  initialHideSubWorkflows,
}: {
  initialHideSubWorkflows: boolean;
}) {
  const [hideSubWorkflows, setHideSubWorkflows] = useState(
    initialHideSubWorkflows,
  );
  return (
    <ResultsTable {...baseProps({ hideSubWorkflows, setHideSubWorkflows })} />
  );
}

describe("ResultsTable", () => {
  it("renders the row expander when the toggle is off", () => {
    render(<ResultsTable {...baseProps({ hideSubWorkflows: false })} />);

    expect(getExpander()).toBeInTheDocument();
  });

  it("still renders the row expander when the toggle is on", () => {
    render(<ResultsTable {...baseProps({ hideSubWorkflows: true })} />);

    expect(getExpander()).toBeInTheDocument();
  });

  it("keeps the row expander after the toggle is switched on", () => {
    render(<ControlledResultsTable initialHideSubWorkflows={false} />);

    expect(getExpander()).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("checkbox", { name: "Hide sub-agent executions" }),
    );

    expect(getExpander()).toBeInTheDocument();
  });
});
