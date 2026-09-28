import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { CompoundFilter } from "./CompoundFilter";

describe("CompoundFilter", () => {
  it("renders the label plus the supplied operator and value nodes", () => {
    render(
      <CompoundFilter
        label="Queue size"
        active={false}
        operator={<div>operator-slot</div>}
        value={<div>value-slot</div>}
      />,
    );

    expect(screen.getByText("Queue size")).toBeVisible();
    expect(screen.getByText("operator-slot")).toBeVisible();
    expect(screen.getByText("value-slot")).toBeVisible();
  });

  it("only renders the active-state dot when active is true", () => {
    const { rerender } = render(
      <CompoundFilter
        label="Queue size"
        active={false}
        operator={null}
        value={null}
      />,
    );

    expect(screen.queryByTestId("filter-active-dot")).not.toBeInTheDocument();

    rerender(
      <CompoundFilter label="Queue size" active operator={null} value={null} />,
    );

    expect(screen.getByTestId("filter-active-dot")).toBeInTheDocument();
  });
});
