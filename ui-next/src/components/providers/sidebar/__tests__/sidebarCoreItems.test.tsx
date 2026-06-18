import { describe, it, expect, vi } from "vitest";

vi.mock("@mui/icons-material/Code", () => ({ default: () => null }));
vi.mock("@mui/icons-material/PlayArrowOutlined", () => ({
  default: () => null,
}));
vi.mock("@mui/icons-material/PlaylistPlay", () => ({ default: () => null }));
vi.mock("@mui/icons-material/Support", () => ({ default: () => null }));
vi.mock("@mui/icons-material/WebhookOutlined", () => ({ default: () => null }));
vi.mock("components/providers/sidebar/RunWorkflowButton", () => ({
  default: () => null,
}));

vi.mock("utils", () => ({
  FEATURES: {
    PLAYGROUND: "PLAYGROUND",
    SHOW_FEEDBACK_FORM: "SHOW_FEEDBACK_FORM",
    SCHEDULER: "SCHEDULER",
  },
  featureFlags: {
    isEnabled: vi.fn((feature: string) => feature === "SCHEDULER"),
    getValue: vi.fn(() => undefined),
  },
}));

vi.mock("utils/constants/route", () => ({
  EVENT_HANDLERS_URL: {
    BASE: "/eventHandlers",
    NAME: "/eventHandlers/:name",
    NEW: "/eventHandlers/new",
  },
  NEW_TASK_DEF_URL: "/taskDef/new",
  RUN_WORKFLOW_URL: "/runWorkflow",
  SCHEDULER_DEFINITION_URL: {
    BASE: "/scheduleDef",
    NAME: "/scheduleDef/:name?",
    NEW: "/newScheduleDef",
  },
  SCHEDULER_EXECUTION_URL: "/schedulerExecs",
  TASK_DEF_URL: { BASE: "/taskDef", NAME: "/taskDef/:name" },
  TASK_QUEUE_URL: { BASE: "/taskQueue" },
  WORKFLOW_DEFINITION_URL: {
    BASE: "/workflowDef",
    NAME_VERSION: "/workflowDef/:name/:version",
    NEW: "/workflowDef/new",
  },
  WORKFLOW_EXECUTION_URL: { WF_ID_TASK_ID: "/execution/:id/:taskId?" },
}));

import { getCoreSidebarItems } from "../sidebarCoreItems";
import type { MenuItemType } from "../types";

function findItem(items: MenuItemType[], id: string): MenuItemType | undefined {
  return items.find((i) => i.id === id);
}

function findNestedItem(
  items: MenuItemType[],
  parentId: string,
  childId: string,
): MenuItemType | undefined {
  const parent = findItem(items, parentId);
  return parent?.items?.find((i) => i.id === childId);
}

describe("getCoreSidebarItems", () => {
  let items: MenuItemType[];

  beforeEach(() => {
    items = getCoreSidebarItems(true);
  });

  describe("scheduler execution item", () => {
    it("is present in the executionsSubMenu", () => {
      const schedulerExe = findNestedItem(
        items,
        "executionsSubMenu",
        "schedulerExeItem",
      );
      expect(schedulerExe).toBeDefined();
    });

    it("links to the scheduler executions page", () => {
      const schedulerExe = findNestedItem(
        items,
        "executionsSubMenu",
        "schedulerExeItem",
      );
      expect(schedulerExe?.linkTo).toBe("/schedulerExecs");
    });

    it("is visible (not hidden) by default", () => {
      const schedulerExe = findNestedItem(
        items,
        "executionsSubMenu",
        "schedulerExeItem",
      );
      expect(schedulerExe?.hidden).toBe(false);
    });

    it("is titled Scheduler", () => {
      const schedulerExe = findNestedItem(
        items,
        "executionsSubMenu",
        "schedulerExeItem",
      );
      expect(schedulerExe?.title).toBe("Scheduler");
    });

    it("is positioned after workflowExeItem (100) and before queueMonitorItem (200)", () => {
      const execMenu = findItem(items, "executionsSubMenu");
      const schedulerExe = execMenu?.items?.find(
        (i) => i.id === "schedulerExeItem",
      );
      const workflowExe = execMenu?.items?.find(
        (i) => i.id === "workflowExeItem",
      );
      const queueMonitor = execMenu?.items?.find(
        (i) => i.id === "queueMonitorItem",
      );
      expect(schedulerExe?.position).toBeGreaterThan(
        workflowExe?.position ?? 0,
      );
      expect(schedulerExe?.position).toBeLessThan(
        queueMonitor?.position ?? Infinity,
      );
    });
  });

  describe("scheduler definition item", () => {
    it("is present in the definitionsSubMenu", () => {
      const schedulerDef = findNestedItem(
        items,
        "definitionsSubMenu",
        "schedulerDefItem",
      );
      expect(schedulerDef).toBeDefined();
    });

    it("links to the scheduler definitions page", () => {
      const schedulerDef = findNestedItem(
        items,
        "definitionsSubMenu",
        "schedulerDefItem",
      );
      expect(schedulerDef?.linkTo).toBe("/scheduleDef");
    });

    it("is visible (not hidden) by default", () => {
      const schedulerDef = findNestedItem(
        items,
        "definitionsSubMenu",
        "schedulerDefItem",
      );
      expect(schedulerDef?.hidden).toBe(false);
    });

    it("is titled Scheduler", () => {
      const schedulerDef = findNestedItem(
        items,
        "definitionsSubMenu",
        "schedulerDefItem",
      );
      expect(schedulerDef?.title).toBe("Scheduler");
    });

    it("includes activeRoutes for scheduler name and new routes", () => {
      const schedulerDef = findNestedItem(
        items,
        "definitionsSubMenu",
        "schedulerDefItem",
      );
      expect(schedulerDef?.activeRoutes).toContain("/newScheduleDef");
      expect(schedulerDef?.activeRoutes).toContain("/scheduleDef/:name?");
    });

    it("is positioned after eventHandlerDefItem (300)", () => {
      const defMenu = findItem(items, "definitionsSubMenu");
      const schedulerDef = defMenu?.items?.find(
        (i) => i.id === "schedulerDefItem",
      );
      const eventHandler = defMenu?.items?.find(
        (i) => i.id === "eventHandlerDefItem",
      );
      expect(schedulerDef?.position).toBeGreaterThan(
        eventHandler?.position ?? 0,
      );
    });
  });

  describe("overall structure", () => {
    it("returns executionsSubMenu with workflow, scheduler, and queue monitor", () => {
      const execMenu = findItem(items, "executionsSubMenu");
      const childIds = execMenu?.items?.map((i) => i.id) ?? [];
      expect(childIds).toContain("workflowExeItem");
      expect(childIds).toContain("schedulerExeItem");
      expect(childIds).toContain("queueMonitorItem");
    });

    it("returns definitionsSubMenu with workflow, task, event handler, and scheduler", () => {
      const defMenu = findItem(items, "definitionsSubMenu");
      const childIds = defMenu?.items?.map((i) => i.id) ?? [];
      expect(childIds).toContain("workflowDefItem");
      expect(childIds).toContain("taskDefItem");
      expect(childIds).toContain("eventHandlerDefItem");
      expect(childIds).toContain("schedulerDefItem");
    });
  });
});
