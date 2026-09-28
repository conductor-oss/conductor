import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router";
import ErrorBoundary from "./ErrorBoundary";

vi.mock("utils", () => ({
  reportErrorToLogRocket: vi.fn(),
  isLogRocketEnabled: vi.fn(() => false),
}));

function ThrowError({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error("Agent diagram failed to render");
  }

  return <div>Recovered content</div>;
}

function RouteRecoveryFixture() {
  const navigate = useNavigate();

  return (
    <>
      <button onClick={() => navigate("/healthy")}>Navigate away</button>
      <ErrorBoundary>
        <Routes>
          <Route path="/broken" element={<ThrowError shouldThrow />} />
          <Route path="/healthy" element={<div>Healthy page</div>} />
        </Routes>
      </ErrorBoundary>
    </>
  );
}

describe("ErrorBoundary", () => {
  let consoleError: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleError.mockRestore();
    vi.clearAllMocks();
  });

  it("shows the captured error message and retries rendering its children", () => {
    const { rerender } = render(
      <MemoryRouter>
        <ErrorBoundary>
          <ThrowError shouldThrow />
        </ErrorBoundary>
      </MemoryRouter>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Agent diagram failed to render",
    );

    rerender(
      <MemoryRouter>
        <ErrorBoundary>
          <ThrowError shouldThrow={false} />
        </ErrorBoundary>
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByText("Recovered content")).toBeInTheDocument();
  });

  it("recovers automatically when navigation changes the route", () => {
    render(
      <MemoryRouter initialEntries={["/broken"]}>
        <RouteRecoveryFixture />
      </MemoryRouter>,
    );

    expect(screen.getByRole("alert")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Navigate away" }));

    expect(screen.getByText("Healthy page")).toBeInTheDocument();
  });
});
