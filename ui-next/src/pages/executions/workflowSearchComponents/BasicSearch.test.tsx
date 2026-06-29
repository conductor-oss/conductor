import "@testing-library/jest-dom";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { createStore, Provider as JotaiProvider } from "jotai";
import { Provider as ThemeProvider } from "theme/material/provider";
import BasicSearch, { BasicSearchProps } from "./BasicSearch";

// ─── Module mocks ─────────────────────────────────────────────────────────────

vi.mock("react-router-use-location-state", async () => {
  const { useState } = await import("react");
  return {
    useQueryState: (_key: string, defaultValue: unknown) =>
      useState(defaultValue),
  };
});

vi.mock("utils/query", () => ({
  useWorkflowSearch: vi.fn(() => ({
    data: undefined,
    error: undefined,
    isFetching: false,
    refetch: vi.fn(),
  })),
  useWorkflowNames: vi.fn(() => ["WorkflowA", "WorkflowB"]),
}));

vi.mock("utils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("utils")>();
  return {
    ...actual,
    dateToEpoch: vi.fn((v: string) => v),
    useLocalStorage: vi.fn(() => [{}, vi.fn()]),
  };
});

vi.mock("react-hotkeys-hook", () => ({
  useHotkeys: vi.fn(),
}));

vi.mock("../DateControlComponent", () => ({
  DateControlComponent: ({
    startTimeLabel,
    fromDisplayTime,
  }: {
    startTimeLabel?: string;
    fromDisplayTime?: string;
  }) => (
    <div
      data-testid={`date-picker-${(startTimeLabel ?? "picker")
        .toLowerCase()
        .replace(/\s+/g, "-")}`}
    >
      {fromDisplayTime || startTimeLabel}
    </div>
  ),
}));

vi.mock("../ResultsTable", () => ({
  default: () => <div data-testid="results-table" />,
}));

vi.mock("../ApiSearchModalIntegration", () => ({
  ApiSearchModalIntegration: () => <div data-testid="api-search-modal" />,
}));

vi.mock("react-router", () => ({
  Navigate: ({ to }: { to: string }) => (
    <div data-testid="navigate" data-to={to} />
  ),
}));

// ─── Test helpers ──────────────────────────────────────────────────────────────

function makeProps(
  overrides: Partial<BasicSearchProps> = {},
): BasicSearchProps {
  return {
    doSearch: vi.fn(),
    SwitchComponent: null,
    getTableTitle: vi.fn(() => null),
    freeText: "",
    setFreeText: vi.fn(),
    status: [],
    setStatus: vi.fn(),
    startTimeFrom: "",
    setStartTimeFrom: vi.fn(),
    onStartFromChange: vi.fn(),
    startTimeTo: "",
    setStartTimeTo: vi.fn(),
    onStartToChange: vi.fn(),
    endTimeFrom: "",
    setEndTimeFrom: vi.fn(),
    onEndFromChange: vi.fn(),
    endTimeTo: "",
    setEndTimeTo: vi.fn(),
    onEndToChange: vi.fn(),
    fromDisplayTime: "Last 72 Hours",
    setFromDisplayTime: vi.fn(),
    toDisplayTime: "Now",
    setToDisplayTime: vi.fn(),
    excludeSubWorkflows: false,
    setExcludeSubWorkflows: vi.fn(),
    recentSearches: { start: "", end: "" },
    ...overrides,
  };
}

/**
 * Renders BasicSearch with fresh Jotai and Theme providers.
 * A new Jotai store is created per call so atom state never leaks between tests.
 */
function setup(overrides: Partial<BasicSearchProps> = {}) {
  const props = makeProps(overrides);
  const store = createStore();

  render(
    <JotaiProvider store={store}>
      <ThemeProvider>
        <BasicSearch {...props} />
      </ThemeProvider>
    </JotaiProvider>,
  );

  return { props, store };
}

// ─── Rendering ────────────────────────────────────────────────────────────────

describe("BasicSearch — rendering", () => {
  it("renders the workflow name search input", () => {
    setup();
    // MUI Autocomplete combobox accessible name comes from the tooltip, not the label.
    // Query by ID to be precise.
    expect(
      document.getElementById("workflow-search-name-dropdown"),
    ).toBeInTheDocument();
  });

  it("renders the Search button", () => {
    setup();
    expect(screen.getByRole("button", { name: /search/i })).toBeInTheDocument();
  });

  it("renders the Status button with default label", () => {
    setup();
    expect(
      screen.getByRole("button", { name: /^status$/i }),
    ).toBeInTheDocument();
  });

  it("renders the Filters button", () => {
    setup();
    expect(
      screen.getByRole("button", { name: /^filters$/i }),
    ).toBeInTheDocument();
  });

  it("renders the Reset All button", () => {
    setup();
    expect(
      screen.getByRole("button", { name: /reset all/i }),
    ).toBeInTheDocument();
  });

  it("renders the Start Time date picker", () => {
    setup();
    expect(screen.getByTestId("date-picker-start-time")).toBeInTheDocument();
  });

  it("renders the End Time date picker", () => {
    setup();
    expect(screen.getByTestId("date-picker-end-time")).toBeInTheDocument();
  });

  it("renders the results table", () => {
    setup();
    expect(screen.getByTestId("results-table")).toBeInTheDocument();
  });
});

// ─── Status filter ─────────────────────────────────────────────────────────────

describe("BasicSearch — Status filter", () => {
  it("opens the status menu when the Status button is clicked", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: /^status$/i }));
    expect(
      screen.getByRole("menuitem", { name: /running/i }),
    ).toBeInTheDocument();
  });

  it("shows all workflow execution statuses in the menu", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: /^status$/i }));

    for (const s of [
      "RUNNING",
      "COMPLETED",
      "FAILED",
      "TIMED_OUT",
      "TERMINATED",
      "PAUSED",
    ]) {
      expect(
        screen.getByRole("menuitem", { name: new RegExp(s, "i") }),
      ).toBeInTheDocument();
    }
  });

  it("closes the menu when Cancel is clicked without calling setStatus", () => {
    const setStatus = vi.fn();
    setup({ setStatus });

    fireEvent.click(screen.getByRole("button", { name: /^status$/i }));
    fireEvent.click(screen.getByRole("button", { name: /^cancel$/i }));

    expect(setStatus).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("menuitem", { name: /running/i }),
    ).not.toBeInTheDocument();
  });

  it("calls setStatus with selected values when Apply is clicked", () => {
    const setStatus = vi.fn();
    setup({ setStatus });

    fireEvent.click(screen.getByRole("button", { name: /^status$/i }));
    fireEvent.click(screen.getByRole("menuitem", { name: /running/i }));
    fireEvent.click(screen.getByRole("menuitem", { name: /completed/i }));
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    expect(setStatus).toHaveBeenCalledWith(
      expect.arrayContaining(["RUNNING", "COMPLETED"]),
    );
  });

  it("closes the menu after Apply is clicked", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: /^status$/i }));
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    expect(
      screen.queryByRole("menuitem", { name: /running/i }),
    ).not.toBeInTheDocument();
  });

  it("shows a single status name when exactly one status is selected", () => {
    setup({ status: ["RUNNING"] });
    expect(
      screen.getByRole("button", { name: /^running$/i }),
    ).toBeInTheDocument();
  });

  it("shows a count badge when multiple statuses are selected", () => {
    setup({ status: ["RUNNING", "COMPLETED", "FAILED"] });
    expect(
      screen.getByRole("button", { name: /status \(3\)/i }),
    ).toBeInTheDocument();
  });

  it("pre-checks already-applied statuses when the menu is opened", () => {
    setup({ status: ["RUNNING"] });
    fireEvent.click(screen.getByRole("button", { name: /^running$/i }));

    const runningItem = screen.getByRole("menuitem", { name: /running/i });
    const checkbox = within(runningItem).getByRole("checkbox");
    expect(checkbox).toBeChecked();
  });

  it("does not apply changes if Cancel is clicked after toggling a checkbox", () => {
    const setStatus = vi.fn();
    setup({ setStatus, status: ["RUNNING"] });

    fireEvent.click(screen.getByRole("button", { name: /^running$/i }));
    fireEvent.click(screen.getByRole("menuitem", { name: /running/i }));
    fireEvent.click(screen.getByRole("button", { name: /^cancel$/i }));

    expect(setStatus).not.toHaveBeenCalled();
  });

  it("applies an empty array when all checkboxes are un-checked and Apply is clicked", () => {
    const setStatus = vi.fn();
    setup({ setStatus, status: ["RUNNING"] });

    // Open menu — draft is pre-populated with ["RUNNING"]
    fireEvent.click(screen.getByRole("button", { name: /^running$/i }));
    // Un-check RUNNING
    fireEvent.click(screen.getByRole("menuitem", { name: /running/i }));
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    expect(setStatus).toHaveBeenCalledWith([]);
  });
});

// ─── More Filters popover ──────────────────────────────────────────────────────

describe("BasicSearch — More Filters popover", () => {
  function openFilters() {
    fireEvent.click(screen.getByRole("button", { name: /^filters$/i }));
  }

  it("opens the More Filters popover when the Filters button is clicked", () => {
    setup();
    openFilters();
    expect(screen.getByText("More Filters")).toBeInTheDocument();
  });

  it("shows Workflow ID and Free text search inputs", () => {
    setup();
    openFilters();

    expect(screen.getByLabelText(/workflow id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/free text search/i)).toBeInTheDocument();
  });

  it("shows Correlation ID and Idempotency key inputs", () => {
    setup();
    openFilters();

    expect(screen.getByLabelText(/correlation id/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/idempotency key/i)).toBeInTheDocument();
  });

  it("shows the Exclude sub-workflows toggle", () => {
    setup();
    openFilters();

    // MUI Switch renders an input[type=checkbox]; getByLabelText finds it via
    // the wrapping FormControlLabel element.
    expect(screen.getByLabelText(/exclude sub-workflows/i)).toBeInTheDocument();
  });

  it("closes the popover without committing when Cancel is clicked", () => {
    const setFreeText = vi.fn();
    setup({ setFreeText });

    openFilters();
    fireEvent.click(screen.getByRole("button", { name: /^cancel$/i }));

    // MUI Popover keeps its children in the DOM during the exit transition,
    // so check visibility rather than presence.
    expect(setFreeText).not.toHaveBeenCalled();
    expect(screen.getByText("More Filters")).not.toBeVisible();
  });

  it("commits Free text value when Apply is clicked", () => {
    const setFreeText = vi.fn();
    setup({ setFreeText });

    openFilters();
    const freeTextInput = screen.getByLabelText(/free text search/i);
    fireEvent.change(freeTextInput, { target: { value: "my-search" } });
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    expect(setFreeText).toHaveBeenCalledWith("my-search");
  });

  it("commits Exclude sub-workflows toggle when Apply is clicked", () => {
    const setExcludeSubWorkflows = vi.fn();
    setup({ setExcludeSubWorkflows });

    openFilters();
    fireEvent.click(screen.getByLabelText(/exclude sub-workflows/i));
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    expect(setExcludeSubWorkflows).toHaveBeenCalledWith(true);
  });

  it("commits Workflow ID value typed in the input when Apply is clicked", () => {
    // workflowIdDraft is driven by onTextInputChange on the ConductorInput.
    // The committed value is handed back to URL state (mocked as useState).
    const setFreeText = vi.fn();
    setup({ setFreeText });

    openFilters();
    const wfIdInput = screen.getByLabelText(/workflow id/i);
    fireEvent.change(wfIdInput, { target: { value: "wf-abc" } });
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    // After Apply the popover closes (content becomes hidden)
    expect(screen.getByText("More Filters")).not.toBeVisible();
  });

  it("closes the popover after Apply is clicked", () => {
    setup();
    openFilters();
    fireEvent.click(screen.getByRole("button", { name: /^apply$/i }));

    // MUI Popover stays in DOM during exit transition — check visibility
    expect(screen.getByText("More Filters")).not.toBeVisible();
  });

  it("shows no count on Filters button when no advanced filters are active", () => {
    setup();
    expect(
      screen.getByRole("button", { name: /^filters$/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /filters \(\d/i }),
    ).not.toBeInTheDocument();
  });

  it("shows count (1) on Filters button when freeText is non-empty", () => {
    setup({ freeText: "test-value" });
    expect(
      screen.getByRole("button", { name: /filters \(1\)/i }),
    ).toBeInTheDocument();
  });

  it("shows count (1) on Filters button when excludeSubWorkflows is true", () => {
    setup({ excludeSubWorkflows: true });
    expect(
      screen.getByRole("button", { name: /filters \(1\)/i }),
    ).toBeInTheDocument();
  });

  it("shows correct cumulative count when multiple advanced filters are active", () => {
    setup({ freeText: "foo", excludeSubWorkflows: true });
    expect(
      screen.getByRole("button", { name: /filters \(2\)/i }),
    ).toBeInTheDocument();
  });
});

// ─── Reset All ─────────────────────────────────────────────────────────────────

describe("BasicSearch — Reset All", () => {
  it("resets status, freeText, excludeSubWorkflows and display time labels", () => {
    const setStatus = vi.fn();
    const setStartTimeFrom = vi.fn();
    const setFreeText = vi.fn();
    const setExcludeSubWorkflows = vi.fn();
    const setFromDisplayTime = vi.fn();
    const setToDisplayTime = vi.fn();

    setup({
      setStatus,
      setStartTimeFrom,
      setFreeText,
      setExcludeSubWorkflows,
      setFromDisplayTime,
      setToDisplayTime,
    });

    fireEvent.click(screen.getByRole("button", { name: /reset all/i }));

    expect(setStatus).toHaveBeenCalledWith([]);
    expect(setFreeText).toHaveBeenCalledWith("");
    expect(setExcludeSubWorkflows).toHaveBeenCalledWith(false);
    expect(setFromDisplayTime).toHaveBeenCalledWith("Last 72 Hours");
    expect(setToDisplayTime).toHaveBeenCalledWith("Now");
    // After reset, startTimeFrom is set to a 72-hour ago timestamp
    expect(setStartTimeFrom).toHaveBeenCalled();
  });

  it("does not throw when clicked with no active filters", () => {
    setup();
    expect(() =>
      fireEvent.click(screen.getByRole("button", { name: /reset all/i })),
    ).not.toThrow();
  });
});

// ─── Search button ─────────────────────────────────────────────────────────────

describe("BasicSearch — Search button", () => {
  it("calls doSearch when the Search button is clicked", () => {
    const doSearch = vi.fn();
    setup({ doSearch });

    // SplitButton renders the primary "Search" action as the first button
    fireEvent.click(screen.getAllByRole("button", { name: /search/i })[0]);

    expect(doSearch).toHaveBeenCalledTimes(1);
  });
});

// ─── Date pickers ──────────────────────────────────────────────────────────────

describe("BasicSearch — Date pickers", () => {
  it("passes fromDisplayTime to the Start Time DateControlComponent", () => {
    setup({ fromDisplayTime: "Last 24 Hours" });
    expect(screen.getByTestId("date-picker-start-time")).toHaveTextContent(
      "Last 24 Hours",
    );
  });

  it("passes toDisplayTime to the End Time DateControlComponent", () => {
    setup({ toDisplayTime: "Next Week" });
    expect(screen.getByTestId("date-picker-end-time")).toHaveTextContent(
      "Next Week",
    );
  });

  it("reflects updated fromDisplayTime after prop changes", () => {
    const { rerender } = render(
      <JotaiProvider store={createStore()}>
        <ThemeProvider>
          <BasicSearch {...makeProps({ fromDisplayTime: "Last 72 Hours" })} />
        </ThemeProvider>
      </JotaiProvider>,
    );

    rerender(
      <JotaiProvider store={createStore()}>
        <ThemeProvider>
          <BasicSearch {...makeProps({ fromDisplayTime: "Today" })} />
        </ThemeProvider>
      </JotaiProvider>,
    );

    expect(screen.getByTestId("date-picker-start-time")).toHaveTextContent(
      "Today",
    );
  });
});

// ─── Workflow name search ──────────────────────────────────────────────────────

describe("BasicSearch — Workflow name search", () => {
  it("renders the workflow name autocomplete with options from useWorkflowNames", async () => {
    setup();
    const input = document.getElementById(
      "workflow-search-name-dropdown",
    ) as HTMLElement;
    fireEvent.focus(input);
    fireEvent.mouseDown(input);
    await waitFor(() => {
      expect(screen.getByText("WorkflowA")).toBeInTheDocument();
    });
  });
});
