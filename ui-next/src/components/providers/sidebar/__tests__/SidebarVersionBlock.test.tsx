import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Provider as ThemeProvider } from "theme/material/provider";
import { SidebarVersionBlock } from "../SidebarVersionBlock";

// ClipboardCopy renders a button alongside children — stub it so we can
// assert on the displayed text and the copy value independently.
vi.mock("components/ui/ClipboardCopy", () => ({
  default: ({
    children,
    value,
  }: {
    children: React.ReactNode;
    value: string;
  }) => (
    <div>
      <span data-testid="clipboard-value">{value}</span>
      {children}
    </div>
  ),
}));

vi.mock("utils", () => ({
  FEATURES: { PLAYGROUND: "PLAYGROUND" },
  featureFlags: { isEnabled: vi.fn(() => false) },
}));

function renderBlock(props: Parameters<typeof SidebarVersionBlock>[0]) {
  return render(
    <ThemeProvider>
      <SidebarVersionBlock {...props} />
    </ThemeProvider>,
  );
}

describe("SidebarVersionBlock", () => {
  describe("when conductorVersion is undefined (API still loading)", () => {
    it("renders a skeleton placeholder instead of version text", () => {
      renderBlock({
        open: true,
        uiVersion: "latest",
        conductorVersion: undefined,
      });

      // MUI Skeleton renders as a span with the animate class.
      expect(document.querySelector(".MuiSkeleton-root")).toBeInTheDocument();
    });

    it("does not render the ClipboardCopy or version text", () => {
      renderBlock({
        open: true,
        uiVersion: "latest",
        conductorVersion: undefined,
      });

      expect(screen.queryByTestId("clipboard-value")).toBeNull();
    });
  });

  describe("when conductorVersion is null (API error / unavailable)", () => {
    it("shows 'unknown | uiVersion' so users know the fetch failed", () => {
      renderBlock({ open: true, uiVersion: "latest", conductorVersion: null });

      const matches = screen.getAllByText("unknown | latest");
      expect(matches.length).toBeGreaterThan(0);
      expect(matches[0]).toBeVisible();
    });

    it("sets the clipboard copy value to 'unknown | uiVersion'", () => {
      renderBlock({ open: true, uiVersion: "latest", conductorVersion: null });

      expect(screen.getByTestId("clipboard-value").textContent).toBe(
        "unknown | latest",
      );
    });
  });

  describe("when conductorVersion is present (API loaded)", () => {
    it("shows conductorVersion | uiVersion", () => {
      renderBlock({
        open: true,
        uiVersion: "latest",
        conductorVersion: "3.9.1",
      });

      const matches = screen.getAllByText("3.9.1 | latest");
      expect(matches.length).toBeGreaterThan(0);
      expect(matches[0]).toBeVisible();
    });

    it("sets the clipboard copy value to conductorVersion | uiVersion", () => {
      renderBlock({
        open: true,
        uiVersion: "latest",
        conductorVersion: "3.9.1",
      });

      expect(screen.getByTestId("clipboard-value").textContent).toBe(
        "3.9.1 | latest",
      );
    });

    it("works when both versions are 'latest'", () => {
      renderBlock({
        open: true,
        uiVersion: "latest",
        conductorVersion: "latest",
      });

      const matches = screen.getAllByText("latest | latest");
      expect(matches.length).toBeGreaterThan(0);
      expect(matches[0]).toBeVisible();
    });
  });

  describe("logo", () => {
    it("shows the full logo when the sidebar is open", () => {
      renderBlock({ open: true, uiVersion: "latest" });

      expect(screen.getByAltText("Conductor")).toHaveAttribute(
        "src",
        "/conductorLogo.svg",
      );
    });

    it("shows the small logo when the sidebar is collapsed", () => {
      renderBlock({ open: false, uiVersion: "latest" });

      expect(screen.getByAltText("Conductor")).toHaveAttribute(
        "src",
        "/conductorLogoSmall.svg",
      );
    });
  });
});
