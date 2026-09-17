import { describe, expect, it, vi, beforeEach } from "vitest";
import type { MenuItemType } from "components/providers/sidebar/types";
import { findFirstNavigableSidebarPath } from "../resolveDefaultHomePath";

vi.mock("utils", () => ({
  featureFlags: {
    isEnabled: vi.fn(() => false),
  },
  FEATURES: { PLAYGROUND: "PLAYGROUND" },
}));

vi.mock("plugins/registry", () => ({
  pluginRegistry: {
    getSidebarItems: vi.fn(() => []),
  },
}));

vi.mock("components/providers/sidebar/sidebarCoreItems", () => ({
  getCoreSidebarItems: vi.fn(() => []),
}));

vi.mock("components/providers/sidebar/sidebarMenuUtils", () => ({
  mergePluginSidebarItems: vi.fn((_core, _plugins) => _core),
}));

function item(
  partial: Partial<MenuItemType> & Pick<MenuItemType, "id" | "title">,
): MenuItemType {
  return {
    icon: null,
    shortcuts: [],
    hidden: false,
    linkTo: "",
    ...partial,
  };
}

describe("findFirstNavigableSidebarPath", () => {
  it("returns the first in-app leaf by sidebar order", () => {
    const menu = [
      item({
        id: "executions",
        title: "Executions",
        position: 100,
        items: [
          item({
            id: "workflowExe",
            title: "Workflow",
            linkTo: "/executions",
            position: 100,
          }),
          item({
            id: "queue",
            title: "Queue",
            linkTo: "/queueMonitor",
            position: 200,
          }),
        ],
      }),
      item({
        id: "defs",
        title: "Definitions",
        position: 300,
        items: [
          item({
            id: "wfDef",
            title: "Workflow",
            linkTo: "/workflowDef",
            position: 100,
          }),
        ],
      }),
    ];
    expect(findFirstNavigableSidebarPath(menu)).toBe("/executions");
  });

  it("prefers an earlier root leaf (e.g. Get Started) over executions", () => {
    const menu = [
      item({
        id: "getStarted",
        title: "Get Started",
        linkTo: "/get-started",
        position: 75,
      }),
      item({
        id: "executions",
        title: "Executions",
        position: 100,
        items: [
          item({
            id: "workflowExe",
            title: "Workflow",
            linkTo: "/executions",
            position: 100,
          }),
        ],
      }),
    ];
    expect(findFirstNavigableSidebarPath(menu)).toBe("/get-started");
  });

  it("skips hidden, external, new-tab, and component-only entries", () => {
    const menu = [
      item({
        id: "hidden",
        title: "Hidden",
        linkTo: "/hidden",
        hidden: true,
        position: 10,
      }),
      item({
        id: "docs",
        title: "Docs",
        linkTo: "https://docs.example.com",
        position: 20,
      }),
      item({
        id: "metrics",
        title: "Metrics",
        linkTo: "/metrics",
        isOpenNewTab: true,
        position: 30,
      }),
      item({
        id: "toggle",
        title: "Toggle",
        component: () => null,
        position: 40,
      }),
      item({
        id: "hub",
        title: "Hub",
        linkTo: "/",
        position: 50,
      }),
      item({
        id: "ok",
        title: "OK",
        linkTo: "/workflowDef",
        position: 60,
      }),
    ];
    expect(findFirstNavigableSidebarPath(menu)).toBe("/workflowDef");
  });
});

describe("resolveDefaultHomePath", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("returns / when playground is enabled", async () => {
    const { featureFlags } = await import("utils");
    vi.mocked(featureFlags.isEnabled).mockReturnValue(true);
    const { resolveDefaultHomePath } =
      await import("../resolveDefaultHomePath");
    expect(resolveDefaultHomePath()).toBe("/");
  });

  it("falls back to /executions when the menu has no navigable leaves", async () => {
    const { featureFlags } = await import("utils");
    vi.mocked(featureFlags.isEnabled).mockReturnValue(false);
    const { mergePluginSidebarItems } =
      await import("components/providers/sidebar/sidebarMenuUtils");
    vi.mocked(mergePluginSidebarItems).mockReturnValue([]);
    const { resolveDefaultHomePath } =
      await import("../resolveDefaultHomePath");
    expect(resolveDefaultHomePath()).toBe("/executions");
  });
});
