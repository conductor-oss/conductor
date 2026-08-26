import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useSelector } from "@xstate/react";
import { RefreshButton } from "./RefreshOptions";

vi.mock("@xstate/react", () => ({
  useSelector: vi.fn(),
}));

const mockedUseSelector = vi.mocked(useSelector);

describe("RefreshButton", () => {
  it("renders the countdown label", () => {
    mockedUseSelector
      .mockReturnValueOnce(60) // durationSet
      .mockReturnValueOnce(23); // elapsed

    const { container } = render(
      <RefreshButton
        onRefresh={() => {}}
        timerActor={{} as never}
        startIcon={null}
      />,
    );

    expect(container).toMatchSnapshot();
  });

  it("renders the every-second label", () => {
    mockedUseSelector
      .mockReturnValueOnce(1) // durationSet
      .mockReturnValueOnce(0); // elapsed

    const { container } = render(
      <RefreshButton
        onRefresh={() => {}}
        timerActor={{} as never}
        startIcon={null}
      />,
    );

    expect(container).toMatchSnapshot();
  });
});
