/**
 * CCOR-13413: proves the Any/Yes/No values actually filter rows. The filter
 * engine was left untouched — it regex-tests String(selector(row)), and a
 * boolean column's selector returns the raw boolean, so "true"/"false" match.
 */
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import DataTable from "./DataTable";
import { ColumnCustomType } from "./types";

const columns = [
  { id: "eventId", name: "eventId", label: "Event id" },
  {
    id: "matched",
    name: "matched",
    label: "Matched",
    type: ColumnCustomType.BOOLEAN,
    renderer: (val: boolean) => (val ? "hit" : "miss"),
  },
];

const data = [
  { eventId: "evt-matched", matched: true },
  { eventId: "evt-unmatched", matched: false },
];

const openFilterOnMatched = () => {
  fireEvent.click(screen.getByRole("button", { name: /search/i }));
  fireEvent.mouseDown(screen.getAllByRole("combobox")[0]);
  fireEvent.click(screen.getByRole("option", { name: "Matched" }));
};

const chooseValue = (label: string) => {
  fireEvent.mouseDown(screen.getAllByRole("combobox")[1]);
  fireEvent.click(screen.getByRole("option", { name: label }));
};

describe("boolean column filtering", () => {
  const renderTable = () =>
    render(
      <ThemeProvider>
        <DataTable data={data} columns={columns} keyField="eventId" />
      </ThemeProvider>,
    );

  it("shows both rows before filtering", () => {
    renderTable();

    expect(screen.getByText("evt-matched")).toBeInTheDocument();
    expect(screen.getByText("evt-unmatched")).toBeInTheDocument();
  });

  it("Yes keeps only matched rows", () => {
    renderTable();
    openFilterOnMatched();

    chooseValue("Yes");

    expect(screen.getByText("evt-matched")).toBeInTheDocument();
    expect(screen.queryByText("evt-unmatched")).not.toBeInTheDocument();
  });

  it("No keeps only unmatched rows", () => {
    renderTable();
    openFilterOnMatched();

    chooseValue("No");

    expect(screen.getByText("evt-unmatched")).toBeInTheDocument();
    expect(screen.queryByText("evt-matched")).not.toBeInTheDocument();
  });

  it("Any clears the filter", () => {
    renderTable();
    openFilterOnMatched();
    chooseValue("Yes");

    chooseValue("Any");

    expect(screen.getByText("evt-matched")).toBeInTheDocument();
    expect(screen.getByText("evt-unmatched")).toBeInTheDocument();
  });
});
