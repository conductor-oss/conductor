/**
 * CCOR-13413: the "Matched" boolean column was searched as free text. The
 * filter regex-tests String(value), so the haystack is "true"/"false" — a user
 * had to guess those words, and single characters matched nonsense ("e" is in
 * both). Boolean columns now get an Any/Yes/No control instead.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { Filter } from "./Filter";
import { ColumnCustomType, RenderableColumn } from "./types";

const columns = [
  { id: "eventId", name: "eventId", label: "Event id" },
  {
    id: "matched",
    name: "matched",
    label: "Matched",
    type: ColumnCustomType.BOOLEAN,
  },
] as RenderableColumn[];

// The popover lays out Field first, then the value control. The OSS Select
// renders a bare InputLabel, so its label is not queryable by association.
const valueControl = () => screen.getAllByRole("combobox")[1];
const textInput = () => screen.queryByLabelText("Contains");

const renderFilter = (columnName: string, substring = "") => {
  const setFilterObj = vi.fn();
  render(
    <ThemeProvider>
      <Filter
        columns={columns}
        filterObj={{ columnName, substring }}
        setFilterObj={setFilterObj}
      />
    </ThemeProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: /search/i }));
  return { setFilterObj };
};

describe("DataTable Filter", () => {
  it("offers Any/Yes/No for a boolean column", () => {
    renderFilter("matched");

    fireEvent.mouseDown(valueControl());

    expect(screen.getByRole("option", { name: "Any" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Yes" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "No" })).toBeInTheDocument();
    // No free-text box to guess "true" into.
    expect(textInput()).not.toBeInTheDocument();
  });

  // Each case must be a real transition — MUI fires no change event when the
  // already-selected option is clicked.
  it.each([
    ["Yes", "true", ""],
    ["No", "false", ""],
    ["Any", "", "true"],
  ])(
    "stores %s as %s so the existing filter matches",
    (label, stored, from) => {
      const { setFilterObj } = renderFilter("matched", from);

      fireEvent.mouseDown(valueControl());
      fireEvent.click(screen.getByRole("option", { name: label }));

      expect(setFilterObj).toHaveBeenCalledWith({
        columnName: "matched",
        substring: stored,
      });
    },
  );

  it("keeps a text box for a non-boolean column, labelled Contains", () => {
    renderFilter("eventId");

    expect(textInput()).toBeInTheDocument();
    // Only the Field select — no second combobox.
    expect(screen.getAllByRole("combobox")).toHaveLength(1);
  });
});
