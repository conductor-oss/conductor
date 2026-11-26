/**
 * The rows-per-page selector must reflect the rows the task list actually
 * fetches, not the global value DataTable persists for every other table.
 */
import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { LOCAL_STORAGE_KEY } from "utils/constants/common";
import { ActorRef } from "xstate";
import { TaskList } from "./TaskList";
import { taskListMachine } from "./state/machine";

vi.mock("./state", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./state")>()),
  useTaskListActor: () => [
    {
      // Pagination only renders when there are rows.
      taskListPage: [
        { seq: 1, taskId: "t1", referenceTaskName: "ref", status: "COMPLETED" },
      ],
      statusFilter: undefined,
      totalHits: 200,
      isFetching: false,
      rowsPerPage: 20,
      summary: undefined,
    },
    {
      handleChangeStatus: vi.fn(),
      handleChangePage: vi.fn(),
      handleChangeRowsPerPage: vi.fn(),
      handleSelectTask: vi.fn(),
    },
  ],
}));

vi.mock("./StatusSelect", () => ({ StatusSelect: () => null }));

describe("TaskList rows per page", () => {
  afterEach(() => localStorage.clear());

  it("shows the fetched page size, ignoring the global persisted value", () => {
    localStorage.setItem(LOCAL_STORAGE_KEY.ROWS_PER_PAGE, "100");

    render(
      <ThemeProvider>
        <TaskList taskListActor={{} as ActorRef<any>} executionAlert="" />
      </ThemeProvider>,
    );

    expect(
      screen.getByRole("combobox", { name: /rows per page/i }),
    ).toHaveValue("20");
  });

  it("defaults to a page size offered by the selector", () => {
    expect(taskListMachine().initialState.context.rowsPerPage).toBe(20);
  });
});
