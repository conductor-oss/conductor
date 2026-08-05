import { render, screen, fireEvent } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, it, expect, vi } from "vitest";
import theme from "theme/theme";
import Error from "./Error";

const mockNavigate = vi.fn();
vi.mock("react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

function renderAtKey(locationKey: string) {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter
        initialEntries={[{ pathname: "/error", key: locationKey }]}
      >
        <Routes>
          <Route path="*" element={<Error title="404" description="Not found" />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

describe("Error — GO BACK button", () => {
  it("navigates to / when location.key is 'default' (direct URL load)", () => {
    mockNavigate.mockClear();
    renderAtKey("default");
    fireEvent.click(screen.getByText("GO BACK"));
    expect(mockNavigate).toHaveBeenCalledWith("/");
  });

  it("navigates -1 when location.key is a UUID (navigated within app)", () => {
    mockNavigate.mockClear();
    renderAtKey("abc123");
    fireEvent.click(screen.getByText("GO BACK"));
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });
});
