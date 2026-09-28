import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import CardAttemptsBadge from "./CardAttemptsBadge";

describe("CardAttemptsBadge", () => {
  it("renders the total attempt count on the task card", () => {
    const { container } = render(<CardAttemptsBadge attempts={3} />);
    expect(container).toMatchSnapshot();
  });
});
