import { beforeEach, describe, expect, it, vi } from "vitest";

// -----------------------------------------------------------------------------
// Mocks: only modules pulled in by `routes.tsx` / `router.tsx` (OSS). Host apps
// register extra routes via `pluginRegistry`; no `enterprise/*` imports here.
// -----------------------------------------------------------------------------

vi.mock("utils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("utils")>();
  return {
    ...actual,
    featureFlags: {
      isEnabled: vi.fn(() => false),
      getValue: vi.fn(),
      getContextValue: vi.fn(),
    },
    FEATURES: {
      PLAYGROUND: "PLAYGROUND",
      SHOW_GET_STARTED_PAGE: "SHOW_GET_STARTED_PAGE",
      TASK_INDEXING: "TASK_INDEXING",
    },
  };
});

vi.mock("utils/constants/route", () => ({
  API_REFERENCE_URL: { BASE: "/api-reference" },
  AI_PROMPTS_MANAGEMENT_URL: { BASE: "/ai_prompts" },
  APPLICATION_MANAGEMENT_URL: { BASE: "/applications" },
  AUTHENTICATION_URL: "/authentication",
  ENV_VARIABLES_URL: { BASE: "/env-variables" },
  EVENT_HANDLERS_URL: {
    BASE: "/eventHandlers",
    NAME: "/eventHandlers/:name",
    NEW: "/eventHandlers/new",
  },
  EVENT_MONITOR_URL: { BASE: "/event-monitor", NAME: "/event-monitor/:name" },
  GROUP_MANAGEMENT_URL: { BASE: "/groups" },
  ROLE_MANAGEMENT_URL: {
    BASE: "/roleManagement",
    TYPE_ID: "/roleManagement/:type?/:id?",
    LIST: "/roleManagement/roles",
    EDIT: "/roleManagement/roles/:id",
  },
  HUMAN_TASK_URL: {
    TASK_ID: "/human/:taskId",
    LIST: "/human",
    TEMPLATES: "/human/templates",
    TEMPLATES_NAME_VERSION: "/human/templates/:name/:version",
    TASK_INBOX: "/human/inbox",
  },
  INTEGRATIONS_MANAGEMENT_URL: { BASE: "/integrations" },
  NEW_TASK_DEF_URL: "/taskDef/new",
  REMOTE_SERVICES_URL: {
    BASE: "/remote-services",
    NEW: "/remote-services/new",
    EDIT: "/remote-services/:id/edit",
  },
  RUN_WORKFLOW_URL: "/runWorkflow",
  SCHEDULER_DEFINITION_URL: {
    BASE: "/scheduleDef",
    NAME: "/scheduleDef/:name",
    NEW: "/scheduleDef/new",
  },
  SCHEMAS_URL: { BASE: "/schemas", EDIT: "/schemas/:id/edit" },
  SECRETS_URL: { BASE: "/secrets" },
  SERVICE_URL: {
    LIST: "/services",
    SERVICE_ID: "/services/:serviceId",
    NEW: "/services/new",
    EDIT: "/services/:serviceId/edit",
    NEW_ROUTE: "/services/:serviceId/routes/new",
    ROUTE_DETAILS: "/services/:serviceId/routes/:routeId",
    ROUTE_EDIT: "/services/:serviceId/routes/:routeId/edit",
  },
  TASK_DEF_URL: { BASE: "/taskDef", NAME: "/taskDef/:name" },
  TASK_EXECUTION_URL: { LIST: "/taskExecution" },
  TASK_QUEUE_URL: { BASE: "/taskQueue" },
  USER_MANAGEMENT_URL: { BASE: "/users" },
  WEBHOOK_ROUTE_URL: {
    NEW: "/webhook/new",
    ID: "/webhook/:id",
    LIST: "/webhooks",
  },
  WORKFLOW_DEFINITION_URL: {
    BASE: "/workflowDef",
    NAME_VERSION: "/workflowDef/:name/:version",
    NEW: "/workflowDef/new",
  },
  WORKFLOW_EXPLORER_URL: "/workflow-explorer",
  WORKERS_URL: {
    BASE: "/workers",
  },
  TAGS_DASHBOARD_URL: { BASE: "/tags-dashboard" },
}));

vi.mock("components/features/auth/AuthGuard", () => ({
  default: () => ({ type: "AuthGuard" }),
}));
vi.mock("components/App", () => ({ App: () => ({ type: "App" }) }));
vi.mock("pages/apiDocs/ApiReferencePage", () => ({
  default: () => ({ type: "ApiReferencePage" }),
}));
vi.mock("pages/creatorFlags/CreatorFlags", () => ({
  CreatorFlags: () => ({ type: "CreatorFlags" }),
}));
vi.mock("pages/definition/task", () => ({
  TaskDefinition: () => ({ type: "TaskDefinition" }),
}));
vi.mock("pages/definition/WorkflowDefinition", () => ({
  default: () => ({ type: "WorkflowDefinition" }),
}));
vi.mock("pages/definitions", () => ({
  EventHandler: () => ({ type: "EventHandler" }),
  Schedules: () => ({ type: "Schedules" }),
  Task: () => ({ type: "Task" }),
  Workflow: () => ({ type: "Workflow" }),
}));
vi.mock("pages/error/ErrorPage", () => ({
  default: () => ({ type: "ErrorPage" }),
}));
vi.mock("pages/eventMonitor/EventMonitor", () => ({
  EventMonitor: () => ({ type: "EventMonitor" }),
}));
vi.mock("pages/eventMonitor/EventMonitorDetail/EventMonitorDetail", () => ({
  EventMonitorDetail: () => ({ type: "EventMonitorDetail" }),
}));
vi.mock("pages/executions", () => ({
  SchedulerExecutions: () => ({ type: "SchedulerExecutions" }),
  TaskSearch: () => ({ type: "TaskSearch" }),
  WorkflowSearch: () => ({ type: "WorkflowSearch" }),
}));
vi.mock("pages/tags/TagsDashboard", () => ({
  default: () => ({ type: "TagsDashboard" }),
}));
vi.mock("../pages/definition/EventHandler/EventHandler", () => ({
  default: () => ({ type: "EventHandlerDefinition" }),
}));
vi.mock("../pages/execution/Execution", () => ({
  default: () => ({ type: "Execution" }),
}));
vi.mock("../pages/kitchensink/Examples", () => ({
  default: () => ({ type: "Examples" }),
}));
vi.mock("../pages/kitchensink/Gantt", () => ({
  default: () => ({ type: "Gantt" }),
}));
vi.mock("../pages/kitchensink/KitchenSink", () => ({
  default: () => ({ type: "KitchenSink" }),
}));
vi.mock("../pages/kitchensink/ThemeSampler", () => ({
  default: () => ({ type: "ThemeSampler" }),
}));
vi.mock("../pages/queueMonitor/TaskQueue", () => ({
  default: () => ({ type: "TaskQueue" }),
}));
vi.mock("../pages/scheduler", () => ({
  Schedule: () => ({ type: "Schedule" }),
}));

vi.mock("react-router", () => ({
  createBrowserRouter: vi.fn(() => ({ type: "BrowserRouter" })),
  Link: ({ to, children, ...props }: any) => ({
    type: "Link",
    props: { to, children, ...props },
  }),
}));

vi.mock("react-vis-timeline", () => ({
  default: () => ({ type: "Timeline" }),
}));

import { router } from "../router";
import { getRoutes } from "../routes";

function flattenRoutes(routeList: any[]): any[] {
  const out: any[] = [];
  const walk = (routes: any[]) => {
    routes.forEach((route) => {
      out.push(route);
      if (route.children) {
        walk(route.children);
      }
    });
  };
  walk(routeList);
  return out;
}

function collectPaths(routeList: any[]): string[] {
  const paths: string[] = [];
  const walk = (routes: any[]) => {
    routes.forEach((route) => {
      if (route.path) {
        paths.push(route.path);
      }
      if (route.children) {
        walk(route.children);
      }
    });
  };
  walk(routeList);
  return paths;
}

/**
 * OSS `getRoutes()` + `router`: core Conductor UI routes only. Extra routes
 * (login, hub, human tasks, etc.) come from host apps via `registerPlugin()`.
 */
describe("router (OSS)", () => {
  let mockFeatureFlags: { isEnabled: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    const utils = await import("utils");
    mockFeatureFlags = utils.featureFlags as any;
    vi.clearAllMocks();
  });

  it("should create a browser router with routes from getRoutes", () => {
    expect(router).toBeDefined();
  });

  it("should export the router instance", () => {
    expect(router).toBeDefined();
  });

  describe("Route structure", () => {
    it("should have a single root route with App element", () => {
      const routes = getRoutes();

      expect(routes).toHaveLength(1);
      expect(routes[0]).toHaveProperty("path", "/");
      expect(routes[0]).toHaveProperty("element");
      expect(routes[0]).toHaveProperty("children");
    });

    it("should contain essential OSS routes with correct elements", () => {
      const routes = getRoutes();
      const children = routes[0].children;

      const errorRoute = children?.find((child: any) => child.path === "*");
      const runWorkflowRoute = children?.find(
        (child: any) => child.path === "/runWorkflow",
      );

      expect(errorRoute).toBeDefined();
      expect(runWorkflowRoute).toBeDefined();
      expect(errorRoute?.element).toBeDefined();
      expect(runWorkflowRoute?.element).toBeDefined();
    });

    it("should include runWorkflow and catch-all among top-level children", () => {
      const routes = getRoutes();
      const children = routes[0].children;

      const runWorkflowRoute = children?.find(
        (child: any) => child.path === "/runWorkflow",
      );
      expect(runWorkflowRoute).toBeDefined();

      expect(children?.length).toBeGreaterThanOrEqual(3);
    });

    it("should have routes with dynamic parameters and correct elements", () => {
      const routes = getRoutes();
      const allRoutes = flattenRoutes(routes);

      const dynamicRoutes = allRoutes.filter(
        (route) => route.path && route.path.includes(":"),
      );

      expect(dynamicRoutes.length).toBeGreaterThan(0);

      const executionRoute = allRoutes.find(
        (route) => route.path === "/execution/:id/:taskId?",
      );
      expect(executionRoute).toBeDefined();
      expect(executionRoute?.element).toBeDefined();

      const workflowDefRoute = allRoutes.find(
        (route) =>
          route.path && route.path.includes("/workflowDef/:name/:version"),
      );
      expect(workflowDefRoute).toBeDefined();
      expect(workflowDefRoute?.element).toBeDefined();

      const taskDefRoute = allRoutes.find(
        (route) => route.path && route.path.includes("/taskDef/:name"),
      );
      expect(taskDefRoute).toBeDefined();
      expect(taskDefRoute?.element).toBeDefined();

      const parameterRoutes = dynamicRoutes.filter((route) => {
        const path = route.path;
        return (
          path.includes(":id") ||
          path.includes(":name") ||
          path.includes(":version")
        );
      });
      expect(parameterRoutes.length).toBeGreaterThan(5);
    });

    it("should have wildcard routes for nested routing", () => {
      const routes = getRoutes();
      const allRoutes = flattenRoutes(routes);

      const wildcardRoutes = allRoutes.filter(
        (route) =>
          route.path && (route.path.includes("*") || route.path.includes("/*")),
      );

      expect(wildcardRoutes.length).toBeGreaterThan(0);
    });

    it("should have kitchen sink development routes with correct elements", () => {
      const routes = getRoutes();
      const allRoutes = flattenRoutes(routes);

      const kitchenRoutes = allRoutes.filter(
        (route) => route.path && route.path.includes("/kitchen"),
      );

      expect(kitchenRoutes.length).toBeGreaterThan(0);

      const kitchenSinkRoute = allRoutes.find(
        (route) => route.path === "/kitchen",
      );
      const examplesRoute = allRoutes.find(
        (route) => route.path === "/kitchen/examples",
      );
      const ganttRoute = allRoutes.find(
        (route) => route.path === "/kitchen/gantt",
      );
      const themeRoute = allRoutes.find(
        (route) => route.path === "/kitchen/theme",
      );

      expect(kitchenSinkRoute).toBeDefined();
      expect(kitchenSinkRoute?.element).toBeDefined();

      expect(examplesRoute).toBeDefined();
      expect(examplesRoute?.element).toBeDefined();

      expect(ganttRoute).toBeDefined();
      expect(ganttRoute?.element).toBeDefined();

      expect(themeRoute).toBeDefined();
      expect(themeRoute?.element).toBeDefined();

      kitchenRoutes.forEach((route) => {
        expect(route.element).toBeDefined();
        expect(route.path).toContain("/kitchen");
      });
    });

    it("should have a substantial number of routes", () => {
      const routes = getRoutes();

      let totalRoutes = 0;
      const countRoutes = (routeList: any[]) => {
        routeList.forEach((route) => {
          totalRoutes++;
          if (route.children) {
            countRoutes(route.children);
          }
        });
      };

      countRoutes(routes);

      expect(totalRoutes).toBeGreaterThan(25);
    });

    it("should have valid route structure with no duplicate paths at same level", () => {
      const routes = getRoutes();

      const checkDuplicates = (routeList: any[]) => {
        const paths = routeList
          .filter((route) => route.path)
          .map((route) => route.path);

        const uniquePaths = new Set(paths);
        expect(paths.length).toBe(uniquePaths.size);

        routeList.forEach((route) => {
          if (route.children) {
            checkDuplicates(route.children);
          }
        });
      };

      checkDuplicates(routes);
    });

    it("should have elements for all routes", () => {
      const routes = getRoutes();

      const validateRoutes = (routeList: any[]) => {
        routeList.forEach((route) => {
          expect(route).toHaveProperty("element");
          if (route.children) {
            validateRoutes(route.children);
          }
        });
      };

      validateRoutes(routes);
    });
  });

  describe("Feature flags (OSS)", () => {
    const countAllRoutes = (routes: any[]): number => {
      let count = 0;
      routes.forEach((route) => {
        count++;
        if (route.children) {
          count += countAllRoutes(route.children);
        }
      });
      return count;
    };

    let BASELINE_ROUTE_COUNT: number;
    beforeAll(async () => {
      const utils = await import("utils");
      (utils.featureFlags as any).isEnabled.mockImplementation(() => false);
      const baselineRoutes = getRoutes();
      BASELINE_ROUTE_COUNT = countAllRoutes(baselineRoutes);
    });

    it("should toggle PLAYGROUND and change route count by one", () => {
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const routesPlaygroundDisabled = getRoutes();
      const countPlaygroundDisabled = countAllRoutes(routesPlaygroundDisabled);

      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "PLAYGROUND",
      );
      const routesPlaygroundEnabled = getRoutes();
      const countPlaygroundEnabled = countAllRoutes(routesPlaygroundEnabled);

      expect(countPlaygroundEnabled).toBe(countPlaygroundDisabled - 1);
      expect(countPlaygroundDisabled).toBe(BASELINE_ROUTE_COUNT);
      expect(countPlaygroundEnabled).toBe(BASELINE_ROUTE_COUNT - 1);
    });

    it("should call feature flags when getRoutes runs", () => {
      vi.clearAllMocks();
      getRoutes();
      expect(mockFeatureFlags.isEnabled).toHaveBeenCalled();
    });

    it("should not include plugin-only paths when no plugins are registered", () => {
      const routes = getRoutes();
      const allPaths = collectPaths(routes);

      const hubPaths = allPaths.filter((path) => path.includes("/hub"));
      const getStartedPaths = allPaths.filter((path) =>
        path.includes("/get-started"),
      );
      const taskExecutionPaths = allPaths.filter((path) =>
        path.includes("/taskExecution"),
      );

      expect(hubPaths.length).toBe(0);
      expect(getStartedPaths.length).toBe(0);
      expect(taskExecutionPaths.length).toBe(0);

      expect(allPaths).toContain("*");
      expect(allPaths).toContain("/executions");
      expect(allPaths).toContain("/runWorkflow");
    });

    it("should not change route count for SHOW_GET_STARTED_PAGE without plugins", () => {
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countDisabled = countAllRoutes(getRoutes());
      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "SHOW_GET_STARTED_PAGE",
      );
      const countEnabled = countAllRoutes(getRoutes());
      expect(countEnabled).toBe(countDisabled);
      expect(countDisabled).toBe(BASELINE_ROUTE_COUNT);
    });

    it("should not change route count for TASK_INDEXING without plugins", () => {
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countDisabled = countAllRoutes(getRoutes());
      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "TASK_INDEXING",
      );
      const countEnabled = countAllRoutes(getRoutes());
      expect(countEnabled).toBe(countDisabled);
      expect(countDisabled).toBe(BASELINE_ROUTE_COUNT);
    });

    it("should only change count for PLAYGROUND when multiple flags are enabled", () => {
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countAllDisabled = countAllRoutes(getRoutes());
      mockFeatureFlags.isEnabled.mockImplementation((feature: string) =>
        ["PLAYGROUND", "SHOW_GET_STARTED_PAGE", "TASK_INDEXING"].includes(
          feature,
        ),
      );
      const countAllEnabled = countAllRoutes(getRoutes());
      expect(countAllEnabled).toBe(countAllDisabled - 1);
      expect(countAllDisabled).toBe(BASELINE_ROUTE_COUNT);
      expect(countAllEnabled).toBe(BASELINE_ROUTE_COUNT - 1);
    });

    it("should only change count when PLAYGROUND is enabled among these flags", () => {
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countAllDisabled = countAllRoutes(getRoutes());

      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "PLAYGROUND",
      );
      const countPlaygroundOnly = countAllRoutes(getRoutes());

      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "SHOW_GET_STARTED_PAGE",
      );
      const countGetStartedOnly = countAllRoutes(getRoutes());

      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "TASK_INDEXING",
      );
      const countTaskIndexingOnly = countAllRoutes(getRoutes());

      expect(countPlaygroundOnly).toBe(countAllDisabled - 1);
      expect(countGetStartedOnly).toBe(countAllDisabled);
      expect(countTaskIndexingOnly).toBe(countAllDisabled);
      expect(countAllDisabled).toBe(BASELINE_ROUTE_COUNT);
    });
  });
});
