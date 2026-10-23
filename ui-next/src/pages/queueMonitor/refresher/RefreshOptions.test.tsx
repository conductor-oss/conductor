import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useSelector } from "@xstate/react";
import { RefreshButton } from "./RefreshOptions";

vi.mock("@xstate/react", () => ({
  useSelector: vi.fn(),
}));

const mockedUseSelector = vi.mocked(useSelector);

describe("RefreshButton", () => {
  it("renders the countdown from duration minus elapsed", () => {
    mockedUseSelector.mockReturnValueOnce(60).mockReturnValueOnce(23);

    render(
      <RefreshButton
        onRefresh={() => {}}
        timerActor={{} as never}
        startIcon={null}
      />,
    );

    expect(screen.getByRole("button", { name: "Refresh in 37" })).toBeVisible();
  });

  it("renders the every-second label", () => {
    mockedUseSelector.mockReturnValueOnce(1).mockReturnValueOnce(0);

    render(
      <RefreshButton
        onRefresh={() => {}}
        timerActor={{} as never}
        startIcon={null}
      />,
    );

    expect(screen.getByRole("button", { name: "Every second" })).toBeVisible();
  });
});
