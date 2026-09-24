/**
 * Switching "SQL format" on must carry over the filters that only basic search
 * renders a control for. Previously the SQL box came up empty AND the clauses
 * were dropped from the request, so the results quietly widened.
 *
 * This asserts the params handed to useTaskExecutionsSearch, which is what the
 * box and the request are both built from.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router";
import { Provider as ThemeProvider } from "theme/material/provider";
import { beforeEach, describe, expect, it, vi } from "vitest";

// First render of this tree (theme + MUI inputs + Monaco stand-in) costs a few
// seconds, which overruns the 5s default once the whole suite is running.
vi.setConfig({ testTimeout: 20000 });

const searchArgs = vi.fn();

vi.mock("utils/query", () => ({
  useTaskExecutionsSearch: (params: unknown) => {
    searchArgs(params);
    return {
      data: undefined,
      error: null,
      isFetching: false,
      refetch: vi.fn(),
    };
  },
}));

// Needs the app's env provider, and nothing here navigates.
vi.mock("utils/hooks/usePushHistory", () => ({
  usePushHistory: () => vi.fn(),
}));

// Page chrome, irrelevant to what the search is built from.
vi.mock("components/layout/SectionHeader", () => ({ default: () => null }));

vi.mock("./TaskResultsTable", () => ({ default: () => null }));
vi.mock("./DateControlComponent", () => ({
  DateControlComponent: () => null,
}));
vi.mock("./SearchExampleQuery", () => ({ ExampleSearchQuery: () => null }));
vi.mock("./Task/TaskApiSearchModal", () => ({
  TaskApiSearchModal: () => null,
}));
vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: ({
    value,
    label,
    onChange,
  }: {
    value: string;
    label: string;
    onChange?: (value: string) => void;
  }) => (
    <textarea
      aria-label={label}
      value={value}
      onChange={(event) => onChange?.(event.target.value)}
    />
  ),
}));

/** Records the current query string so tests can assert on the url state. */
let currentSearch = "";
const LocationProbe = () => {
  currentSearch = useLocation().search;
  return null;
};

const renderTaskSearch = async (search: string) => {
  const { TaskSearch } = await import("./TaskSearch");
  render(
    <MemoryRouter initialEntries={[`/task-executions${search}`]}>
      <ThemeProvider>
        <TaskSearch />
        <LocationProbe />
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

describe("TaskSearch — carrying basic-only filters into SQL format", () => {
  beforeEach(() => vi.clearAllMocks());

  it("searches with the task name filter from basic search", async () => {
    await renderTaskSearch("?asQuery=true&taskDefName=send_email");

    expect(lastQuery()).toContain("taskDefName='send_email'");
  });

  it("carries the task id, reference name, workflow name, type and status", async () => {
    await renderTaskSearch(
      "?asQuery=true&taskId=abc-123&taskRefName=send_email_ref&workflowName=TestWorkflow-Aug&taskType=SIMPLE&status=FAILED",
    );

    expect(lastQuery()).toContain("taskId='abc-123'");
    expect(lastQuery()).toContain("referenceTaskName='send_email_ref'");
    expect(lastQuery()).toContain("workflowName='TestWorkflow-Aug'");
    expect(lastQuery()).toContain("taskType IN (SIMPLE)");
    expect(lastQuery()).toContain("status IN (FAILED)");
  });

  it("shows the carried-over filters in the SQL box", async () => {
    await renderTaskSearch("?asQuery=true&taskDefName=send_email");

    expect(screen.getByLabelText("Search")).toHaveValue(
      "taskDefName='send_email'",
    );
  });

  it("does not override a query the user already typed", async () => {
    await renderTaskSearch(
      "?asQuery=true&taskDefName=send_email&query=taskId%3D%27typed%27",
    );

    expect(lastQuery()).toContain("taskId='typed'");
    expect(lastQuery()).not.toContain("send_email");
  });

  it("does not put the seed back once the box is cleared", async () => {
    await renderTaskSearch("?asQuery=true&taskDefName=send_email");
    expect(screen.getByLabelText("Search")).toHaveValue(
      "taskDefName='send_email'",
    );

    fireEvent.change(screen.getByLabelText("Search"), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(screen.getByLabelText("Search")).toHaveValue("");
    expect(lastQuery()).not.toContain("send_email");
  });
});

describe("TaskSearch — seeding from filters set after page load", () => {
  beforeEach(() => vi.clearAllMocks());

  /**
   * Both search modes live in one component here, so the toggle does not
   * remount anything. A seed computed once at mount would hold the filters as
   * they were on page load and miss everything typed in afterwards.
   */
  it("seeds from a filter typed in after the page loaded", async () => {
    await renderTaskSearch("");

    fireEvent.change(screen.getByLabelText("Task definition name"), {
      target: { value: "send_email" },
    });
    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(screen.getByLabelText("Search")).toHaveValue(
      "taskDefName='send_email'",
    );
  });

  it("re-seeds after switching back, editing, and switching on again", async () => {
    await renderTaskSearch("?taskDefName=send_email");

    fireEvent.click(screen.getByLabelText("SQL format"));
    expect(screen.getByLabelText("Search")).toHaveValue(
      "taskDefName='send_email'",
    );

    fireEvent.click(screen.getByLabelText("SQL format"));
    fireEvent.change(screen.getByLabelText("Task definition name"), {
      target: { value: "send_sms" },
    });
    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(screen.getByLabelText("Search")).toHaveValue(
      "taskDefName='send_sms'",
    );
  });
});

describe("TaskSearch — Reset", () => {
  beforeEach(() => vi.clearAllMocks());

  /**
   * Reset used to clear only the fields the current mode renders, so resetting
   * in SQL format left the basic filters set and they reappeared on switching
   * back.
   */
  it("clears the basic filters when reset in SQL format", async () => {
    await renderTaskSearch("?asQuery=true&taskDefName=send_email");
    expect(currentSearch).toContain("taskDefName");

    fireEvent.click(screen.getByRole("button", { name: "Reset" }));

    // Asserted on the url rather than the fields: basic search is not on
    // screen in SQL format, and switching back would clear them anyway by
    // reading the (now empty) box, which would pass either way.
    expect(currentSearch).not.toContain("taskDefName");
  });
});

describe("TaskSearch — carrying a typed query back to basic search", () => {
  beforeEach(() => vi.clearAllMocks());

  it("reads a representable query back into the basic fields", async () => {
    await renderTaskSearch(
      "?asQuery=true&query=taskDefName%3D%27send_email%27%20AND%20workflowName%3D%27TestWorkflow-Aug%27",
    );

    fireEvent.click(screen.getByLabelText("SQL format"));

    // Back in basic search, and the clauses survived as field values.
    expect(screen.queryByLabelText("Search")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Task definition name")).toHaveValue(
      "send_email",
    );
    expect(screen.getByLabelText("Workflow name")).toHaveValue(
      "TestWorkflow-Aug",
    );
  });

  it("asks before discarding a query basic search cannot express", async () => {
    await renderTaskSearch("?asQuery=true&query=version%3D2");

    fireEvent.click(screen.getByLabelText("SQL format"));

    expect(await screen.findByText("Discard SQL query?")).toBeInTheDocument();
    // Still in SQL format until the choice is made.
    expect(screen.getByLabelText("Search")).toBeInTheDocument();
  });
});
