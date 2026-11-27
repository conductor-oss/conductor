/**
 * The rows-per-page selector must reflect the rows the schedules list actually
 * fetches, not the global value DataTable persists for every other table.
 */
import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { LOCAL_STORAGE_KEY } from "utils/constants/common";
import { useGetSchedulerDefinitionsWithPagination } from "utils/hooks/useGetSchedulerDefinitions";
import ScheduleDefinitions from "../Schedules";

vi.mock("utils/hooks/useGetSchedulerDefinitions", () => ({
  useGetSchedulerDefinitionsWithPagination: vi.fn(() => ({
    // Pagination only renders when there are rows.
    data: {
      results: [
        {
          name: "s1",
          cronExpression: "0 0 9 * * ?",
          zoneId: "UTC",
          startWorkflowRequest: { name: "wf" },
        },
      ],
      totalHits: 200,
    },
    isFetching: false,
    refetch: vi.fn(),
  })),
}));

// The package resolves its own react-router copy, which MemoryRouter can't reach.
vi.mock("react-router-use-location-state", async () => {
  const { useState } = await import("react");
  return {
    useQueryState: (_key: string, initial: unknown) => useState(initial),
  };
});

vi.mock("components/features/auth", () => ({
  useAuth: () => ({ isTrialExpired: false }),
}));

vi.mock("utils/query", async (importOriginal) => ({
  ...(await importOriginal<typeof import("utils/query")>()),
  useAction: () => ({ mutate: vi.fn(), isLoading: false }),
  useActionWithPath: () => ({ mutate: vi.fn(), isLoading: false }),
}));

describe("Schedules rows per page", () => {
  afterEach(() => localStorage.clear());

  it("shows the fetched page size, ignoring the global persisted value", () => {
    localStorage.setItem(LOCAL_STORAGE_KEY.ROWS_PER_PAGE, "100");

    render(
      <ThemeProvider>
        <MemoryRouter>
          <ScheduleDefinitions />
        </MemoryRouter>
      </ThemeProvider>,
    );

    expect(
      screen.getByRole("combobox", { name: /rows per page/i }),
    ).toHaveValue("15");
    expect(useGetSchedulerDefinitionsWithPagination).toHaveBeenLastCalledWith(
      expect.objectContaining({ size: 15 }),
    );
  });
});
