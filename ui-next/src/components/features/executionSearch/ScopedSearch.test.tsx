import { fireEvent, render, screen, within } from "@testing-library/react";
import { Provider as ThemeProvider } from "theme/material/provider";
import { StatusFilterPanel } from "./FilterPanels";
import { ScopedSearchBar } from "./ScopedSearchBar";
import { SearchChips } from "./SearchChips";
import { searchScopesFor } from "./searchScopes";

describe("ScopedSearchBar", () => {
  it("submits on Enter and on the Search button, and switches field", () => {
    const onSubmit = vi.fn();
    const onScopeChange = vi.fn();

    render(
      <ThemeProvider>
        <ScopedSearchBar
          scope="correlationId"
          scopes={searchScopesFor("Workflow id")}
          onScopeChange={onScopeChange}
          value="abc"
          onChange={vi.fn()}
          onSubmit={onSubmit}
        />
      </ThemeProvider>,
    );

    const input = screen.getByLabelText("Search by correlation id");
    fireEvent.keyDown(input, { key: "Enter" });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(onSubmit).toHaveBeenCalledTimes(2);

    fireEvent.change(screen.getByLabelText("Search field"), {
      target: { value: "freeText" },
    });
    expect(onScopeChange).toHaveBeenCalledWith("freeText");
  });
});

describe("SearchChips", () => {
  it("lists a chip's values and removes one at a time", () => {
    const onRemoveValue = vi.fn();

    render(
      <ThemeProvider>
        <SearchChips
          chips={[
            {
              id: "status",
              label: "Status",
              values: ["FAILED", "TIMED_OUT"],
              formatValue: (value) => value.toLowerCase(),
              onRemoveValue,
              onRemove: vi.fn(),
            },
          ]}
        />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByText("Status is any of"));

    const dialog = screen.getByRole("dialog", {
      name: "Status · 2 values",
    });
    fireEvent.click(
      within(dialog).getByRole("button", { name: "Remove timed_out" }),
    );
    expect(onRemoveValue).toHaveBeenCalledWith("TIMED_OUT");
  });
});

describe("StatusFilterPanel", () => {
  it("only applies the draft selection when Apply is clicked", () => {
    const onApply = vi.fn();
    const onCancel = vi.fn();

    render(
      <ThemeProvider>
        <StatusFilterPanel
          selected={["FAILED"]}
          onApply={onApply}
          onCancel={onCancel}
        />
      </ThemeProvider>,
    );

    fireEvent.click(screen.getByRole("checkbox", { name: "COMPLETED" }));
    expect(onApply).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Apply (2)" }));
    expect(onApply).toHaveBeenCalledWith(["FAILED", "COMPLETED"]);
  });
});
