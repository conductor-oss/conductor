import { fireEvent, render, screen } from "@testing-library/react";
import { ReasonForIncompletion } from "./ReasonForIncompletion";

describe("ReasonForIncompletion", () => {
  it("shows a long failure reason in place instead of navigating away", () => {
    const reason = `Task failed: ${"details ".repeat(50)}`;

    render(<ReasonForIncompletion reason={reason} />);

    fireEvent.click(screen.getByText("View full message"));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName === "PRE" && element.textContent === reason,
      ),
    ).toBeInTheDocument();
  });
});
