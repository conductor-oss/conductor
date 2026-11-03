/**
 * BaseLayout's extension points.
 *
 * The enterprise AgentLayout used to reproduce this entire shell — grid,
 * banner, sidebar, app bar, content area — and that copy silently drifted:
 * when BaseLayout moved onto the version query, the copy kept reading a
 * localStorage key nothing wrote, so the sidebar showed a blank version.
 * These slots exist so a wrapper can add to the shell instead of copying it.
 */
import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("components/features/auth", () => ({
  useAuth: () => ({
    isTrialExpired: false,
    trialExpiryDate: undefined,
    isAnnouncementBannerDismissed: true,
    dismissAnnouncementBanner: vi.fn(),
  }),
}));

vi.mock("utils", async () => {
  const actual = await vi.importActual<Record<string, unknown>>("utils");
  return {
    ...actual,
    useAPIReleaseVersion: () => ({
      data: "9.9.9",
      isLoading: false,
      isError: false,
    }),
  };
});

// Only the wiring matters here, not what the sidebar renders.
const sidebarProps: Record<string, unknown>[] = [];
vi.mock("components/providers/sidebar/UiSidebar", () => ({
  UISidebar: (props: Record<string, unknown>) => {
    sidebarProps.push(props);
    return <div data-testid="ui-sidebar" />;
  },
}));

vi.mock("components/layout/header/AnnouncementBanner", () => ({
  default: () => null,
}));
vi.mock("components/features/search/SearchWrapper", () => ({
  default: () => null,
}));
vi.mock("plugins/AppBarModules", () => ({ default: () => null }));

import BaseLayout from "./BaseLayout";

describe("BaseLayout slots", () => {
  it("renders children in the content area", () => {
    render(<BaseLayout>{<div>page content</div>}</BaseLayout>);

    const content = document.querySelector("#main-content");
    expect(content).toHaveTextContent("page content");
  });

  it("renders contentExtras alongside children in the content area", () => {
    render(
      <BaseLayout contentExtras={<div>assistant panel</div>}>
        <div>page content</div>
      </BaseLayout>,
    );

    const content = document.querySelector("#main-content");
    // Both live in the content grid area — that is what lets AgentLayout put
    // its panel here instead of rebuilding the grid.
    expect(content).toHaveTextContent("page content");
    expect(content).toHaveTextContent("assistant panel");
  });

  it("omits contentExtras when not supplied", () => {
    render(<BaseLayout>{<div>page content</div>}</BaseLayout>);

    expect(screen.queryByText("assistant panel")).not.toBeInTheDocument();
  });

  it("forwards customUserBlock to the sidebar", () => {
    sidebarProps.length = 0;
    const block = <div>enterprise footer</div>;
    render(<BaseLayout customUserBlock={block}>{<div />}</BaseLayout>);

    expect(sidebarProps).toHaveLength(1);
    expect(sidebarProps[0].customUserBlock).toBe(block);
  });

  it("still owns the version it passes to the sidebar", () => {
    sidebarProps.length = 0;
    render(<BaseLayout>{<div />}</BaseLayout>);

    // The whole point of the refactor: one place fetches this.
    expect(sidebarProps[0].apiVersion).toBe("9.9.9");
  });
});
