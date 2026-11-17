/**
 * Guards the footer extension point end to end.
 *
 * customUserBlock existed on SidebarFooter and SidebarMenu but not on the two
 * components consumers actually render, so an enterprise footer passed to
 * UISidebar was silently dropped — which is how Copy Token stopped working
 * (CCOR-13474). Every link in the chain has to forward it.
 */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { Sidebar } from "../Sidebar";

vi.mock("components/features/auth", () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: { given_name: "Test" },
    conductorUser: { id: "test@example.com" },
    logOut: vi.fn(),
    isTrialExpired: false,
    trialExpiryDate: undefined,
    isAnnouncementBannerDismissed: true,
  }),
}));

const SENTINEL = "enterprise-account-block";

describe("sidebar footer extension point", () => {
  it("renders a consumer's user block instead of the built-in one", () => {
    render(
      <MemoryRouter>
        <Sidebar menuItems={[]} open customUserBlock={<div>{SENTINEL}</div>} />
      </MemoryRouter>,
    );

    expect(screen.getByText(SENTINEL)).toBeInTheDocument();
    // The built-in block is replaced, not stacked on top of.
    expect(screen.queryByText("Copy Token")).not.toBeInTheDocument();
  });

  it("renders the built-in user block when no block is supplied", () => {
    render(
      <MemoryRouter>
        <Sidebar menuItems={[]} open />
      </MemoryRouter>,
    );

    expect(screen.queryByText(SENTINEL)).not.toBeInTheDocument();
    expect(screen.getByText("Copy Token")).toBeInTheDocument();
  });

  it("UISidebar forwards the block down to Sidebar", async () => {
    const seen: Record<string, unknown>[] = [];
    vi.doMock("components/providers/sidebar", () => ({
      Sidebar: (props: Record<string, unknown>) => {
        seen.push(props);
        return null;
      },
    }));
    const { UISidebar } = await import("../UiSidebar");

    render(<UISidebar customUserBlock={<div>{SENTINEL}</div>} />);

    expect(seen).toHaveLength(1);
    expect(seen[0].customUserBlock).toBeDefined();
    vi.doUnmock("components/providers/sidebar");
  });
});
