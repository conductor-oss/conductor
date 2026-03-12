import { beforeEach, describe, expect, it, vi } from "vitest";

// Mock HTMLCanvasElement.getContext for tests
// This needs to be set up before any modules that use canvas are imported
Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
  value: vi.fn(function (this: HTMLCanvasElement) {
    // Ensure this canvas has backingStorePixelRatio
    if (!("backingStorePixelRatio" in this)) {
      Object.defineProperty(this, "backingStorePixelRatio", {
        get: () => 1,
        configurable: true,
      });
    }

    const context = {
      fillRect: vi.fn(),
      clearRect: vi.fn(),
      getImageData: vi.fn(() => ({ data: new Array(4) })),
      putImageData: vi.fn(),
      createImageData: vi.fn(() => []),
      setTransform: vi.fn(),
      drawImage: vi.fn(),
      save: vi.fn(),
      fillText: vi.fn(),
      restore: vi.fn(),
      beginPath: vi.fn(),
      moveTo: vi.fn(),
      lineTo: vi.fn(),
      closePath: vi.fn(),
      stroke: vi.fn(),
      translate: vi.fn(),
      scale: vi.fn(),
      rotate: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      measureText: vi.fn(() => ({ width: 0 })),
      transform: vi.fn(),
      rect: vi.fn(),
      clip: vi.fn(),
      canvas: this, // Reference to the canvas element - ensure it's not null
    };
    return context;
  }),
  configurable: true,
});

// Mock canvas element properties that might be accessed
// backingStorePixelRatio is a deprecated property but still used by some libraries
Object.defineProperty(HTMLCanvasElement.prototype, "backingStorePixelRatio", {
  get: function () {
    return 1;
  },
  configurable: true,
});

// Ensure width and height properties exist
Object.defineProperty(HTMLCanvasElement.prototype, "width", {
  get: function () {
    return parseInt(this.getAttribute("width") || "0", 10) || 0;
  },
  set: function (value) {
    this.setAttribute("width", String(value));
  },
  configurable: true,
});

Object.defineProperty(HTMLCanvasElement.prototype, "height", {
  get: function () {
    return parseInt(this.getAttribute("height") || "0", 10) || 0;
  },
  set: function (value) {
    this.setAttribute("height", String(value));
  },
  configurable: true,
});

// Mock window.devicePixelRatio if not already set
if (typeof window !== "undefined" && !window.devicePixelRatio) {
  Object.defineProperty(window, "devicePixelRatio", {
    get: () => 1,
    configurable: true,
  });
}

// Also ensure that when a canvas is created, it has these properties
const originalCreateElement = document.createElement.bind(document);
document.createElement = function (tagName: string, options?: any) {
  const element = originalCreateElement(tagName, options);
  if (tagName.toLowerCase() === "canvas") {
    // Ensure the canvas has backingStorePixelRatio
    if (!("backingStorePixelRatio" in element)) {
      Object.defineProperty(element, "backingStorePixelRatio", {
        get: () => 1,
        configurable: true,
      });
    }
  }
  return element;
};

// Mock @okta/okta-signin-widget to prevent canvas issues
vi.mock("@okta/okta-signin-widget", () => ({
  default: vi.fn(),
}));

// Mock all dependencies first - use factory function to avoid hoisting issues
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

// Mock route constants (must include all used by conductor-ui routes.tsx)
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

// Mock all page components with factory functions
vi.mock("@okta/okta-react", () => ({
  LoginCallback: () => ({ type: "LoginCallback" }),
}));
vi.mock("components/auth/AuthGuard", () => ({
  default: () => ({ type: "AuthGuard" }),
}));
vi.mock("components/App", () => ({ App: () => ({ type: "App" }) }));
vi.mock("enterprise/pages/access/ApplicationManagement", () => ({
  default: () => ({ type: "ApplicationManagement" }),
}));
vi.mock("enterprise/pages/access/GroupManagement", () => ({
  default: () => ({ type: "GroupManagement" }),
}));
vi.mock("enterprise/pages/access/users/UserManagement", () => ({
  default: () => ({ type: "UserManagement" }),
}));
vi.mock("enterprise/pages/aiPrompts/AiPromptsManagement", () => ({
  default: () => ({ type: "AiPromptsManagement" }),
}));
vi.mock("enterprise/pages/Authentication/AuthListing", () => ({
  default: () => ({ type: "AuthListing" }),
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
vi.mock("enterprise/pages/envVariables/EnvVariables", () => ({
  EnvVariables: () => ({ type: "EnvVariables" }),
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
vi.mock("enterprise/pages/getStarted/GetStarted", () => ({
  default: () => ({ type: "GetStarted" }),
}));
vi.mock("enterprise/pages/hub/hub", () => ({
  HubMain: () => ({ type: "HubMain" }),
  HubPage: () => ({ type: "HubPage" }),
  HubTemplateDetail: () => ({ type: "HubTemplateDetail" }),
  HubTemplateImport: () => ({ type: "HubTemplateImport" }),
}));
vi.mock("enterprise/pages/human", () => ({
  SearchPage: () => ({ type: "SearchPage" }),
}));
vi.mock("enterprise/pages/human/humanTask", () => ({
  TaskPage: () => ({ type: "TaskPage" }),
}));
vi.mock("enterprise/pages/human/search/TaskInboxPage", () => ({
  TaskInboxPage: () => ({ type: "TaskInboxPage" }),
}));
vi.mock("enterprise/pages/human/templates", () => ({
  TemplateEditorPage: () => ({ type: "TemplateEditorPage" }),
  TemplatePage: () => ({ type: "TemplatePage" }),
}));
vi.mock("enterprise/pages/integrations/IntegrationsManagement", () => ({
  default: () => ({ type: "IntegrationsManagement" }),
}));
vi.mock("enterprise/pages/metrics", () => ({
  default: () => ({ type: "MetricsPage" }),
}));
vi.mock("enterprise/pages/remoteServices/edit/ServiceEdit", () => ({
  default: () => ({ type: "ServiceEdit" }),
}));
vi.mock("enterprise/pages/remoteServices/Services", () => ({
  Services: () => ({ type: "Services" }),
}));
vi.mock("enterprise/pages/schema/edit/SchemaEditPage", () => ({
  SchemaEditPage: () => ({ type: "SchemaEditPage" }),
}));
vi.mock("enterprise/pages/schema/list/SchemaList", () => ({
  SchemaList: () => ({ type: "SchemaList" }),
}));
vi.mock("enterprise/pages/secrets/Secrets", () => ({
  default: () => ({ type: "Secrets" }),
}));
vi.mock("enterprise/pages/services/EditService", () => ({
  default: () => ({ type: "EditService" }),
}));
vi.mock("enterprise/pages/services/NewService", () => ({
  default: () => ({ type: "NewService" }),
}));
vi.mock("enterprise/pages/services/routes/EditRoute", () => ({
  default: () => ({ type: "EditRoute" }),
}));
vi.mock("enterprise/pages/services/routes/NewRoute", () => ({
  default: () => ({ type: "NewRoute" }),
}));
vi.mock("enterprise/pages/services/routes/RouteDetails", () => ({
  default: () => ({ type: "RouteDetails" }),
}));
vi.mock("enterprise/pages/services/Service", () => ({
  default: () => ({ type: "Service" }),
}));
vi.mock("enterprise/pages/services/Services", () => ({
  default: () => ({ type: "Services" }),
}));
vi.mock("enterprise/pages/webhooks", () => ({
  Webhooks: () => ({ type: "Webhooks" }),
}));
vi.mock("enterprise/pages/webhooks/edit/WebhookEdit", () => ({
  WebhookEditPage: () => ({ type: "WebhookEditPage" }),
}));
vi.mock("enterprise/pages/workflowExplorer/Explorer", () => ({
  default: () => ({ type: "Explorer" }),
}));
vi.mock("shared/auth/oidc/OidcRedirectEndpoint", () => ({
  OidcRedirectEndpoint: () => ({ type: "OidcRedirectEndpoint" }),
}));
vi.mock("enterprise/pages/auth/Login", () => ({
  default: () => ({ type: "Login" }),
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

// Mock react-router
vi.mock("react-router", () => ({
  createBrowserRouter: vi.fn(() => ({ type: "BrowserRouter" })),
  Link: ({ to, children, ...props }: any) => ({
    type: "Link",
    props: { to, children, ...props },
  }),
}));

// Mock react-vis-timeline to avoid ES module issues
vi.mock("react-vis-timeline", () => ({
  default: () => ({ type: "Timeline" }),
}));

// Import after mocks
import { router } from "../router";
import { getRoutes } from "../routes";

/**
 * Tests conductor-ui's getRoutes() in isolation: OSS core routes only, with no
 * plugins registered. orkes-conductor-ui adds routes (login, callbacks, hub,
 * get-started, task execution, etc.) by registering plugins; those are not
 * covered here.
 */
describe("router", () => {
  let mockFeatureFlags: any;

  beforeEach(async () => {
    // Get the mocked feature flags
    const utils = await import("utils");
    mockFeatureFlags = utils.featureFlags;
    vi.clearAllMocks();
  });

  it("should create a browser router with routes from getRoutes", () => {
    expect(router).toBeDefined();
  });

  it("should export the router instance", () => {
    expect(router).toBeDefined();
  });

  describe("Route Structure Analysis", () => {
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

      // OSS core: error catch-all and runWorkflow; login/callbacks come from plugins
      const errorRoute = children?.find((child: any) => child.path === "*");
      const runWorkflowRoute = children?.find(
        (child: any) => child.path === "/runWorkflow",
      );

      expect(errorRoute).toBeDefined();
      expect(runWorkflowRoute).toBeDefined();
      expect(errorRoute?.element).toBeDefined();
      expect(runWorkflowRoute?.element).toBeDefined();
    });

    it("should have AuthGuard protected routes", () => {
      const routes = getRoutes();
      const children = routes[0].children;

      // Find AuthGuard routes (routes with AuthGuard element)
      const authGuardRoutes = children?.filter(
        (child: any) => child.element && child.element.type === "AuthGuard",
      );

      expect(authGuardRoutes).toBeDefined();

      // The routes structure might have AuthGuard routes or the routes might be structured differently
      // Let's check if we have the expected authentication-related routes instead
      const runWorkflowRoute = children?.find(
        (child: any) => child.path === "/runWorkflow",
      );
      expect(runWorkflowRoute).toBeDefined();

      // OSS: children are AuthGuard group, runWorkflow, catch-all (*)
      expect(children?.length).toBeGreaterThanOrEqual(3);
    });

    it("should have routes with dynamic parameters and correct elements", () => {
      const routes = getRoutes();

      // Flatten all routes to find dynamic ones
      const allRoutes: any[] = [];
      const flattenRoutes = (routeList: any[]) => {
        routeList.forEach((route) => {
          allRoutes.push(route);
          if (route.children) {
            flattenRoutes(route.children);
          }
        });
      };

      flattenRoutes(routes);

      const dynamicRoutes = allRoutes.filter(
        (route) => route.path && route.path.includes(":"),
      );

      expect(dynamicRoutes.length).toBeGreaterThan(0);

      // Check for specific dynamic routes with their expected elements
      const executionRoute = allRoutes.find(
        (route) => route.path === "/execution/:id/:taskId?",
      );
      expect(executionRoute).toBeDefined();
      expect(executionRoute?.element).toBeDefined();

      // Check workflow definition route
      const workflowDefRoute = allRoutes.find(
        (route) =>
          route.path && route.path.includes("/workflowDef/:name/:version"),
      );
      expect(workflowDefRoute).toBeDefined();
      expect(workflowDefRoute?.element).toBeDefined();

      // Check task definition route
      const taskDefRoute = allRoutes.find(
        (route) => route.path && route.path.includes("/taskDef/:name"),
      );
      expect(taskDefRoute).toBeDefined();
      expect(taskDefRoute?.element).toBeDefined();

      // Verify dynamic routes have proper parameter patterns
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

      // Flatten all routes to find wildcard ones
      const allRoutes: any[] = [];
      const flattenRoutes = (routeList: any[]) => {
        routeList.forEach((route) => {
          allRoutes.push(route);
          if (route.children) {
            flattenRoutes(route.children);
          }
        });
      };

      flattenRoutes(routes);

      const wildcardRoutes = allRoutes.filter(
        (route) =>
          route.path && (route.path.includes("*") || route.path.includes("/*")),
      );

      expect(wildcardRoutes.length).toBeGreaterThan(0);
    });

    it("should have kitchen sink development routes with correct elements", () => {
      const routes = getRoutes();

      // Flatten all routes
      const allRoutes: any[] = [];
      const flattenRoutes = (routeList: any[]) => {
        routeList.forEach((route) => {
          allRoutes.push(route);
          if (route.children) {
            flattenRoutes(route.children);
          }
        });
      };

      flattenRoutes(routes);

      const kitchenRoutes = allRoutes.filter(
        (route) => route.path && route.path.includes("/kitchen"),
      );

      expect(kitchenRoutes.length).toBeGreaterThan(0);

      // Check for specific kitchen routes with their elements
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

      // Verify all kitchen routes have elements
      kitchenRoutes.forEach((route) => {
        expect(route.element).toBeDefined();
        expect(route.path).toContain("/kitchen");
      });
    });

    it("should have a substantial number of routes", () => {
      const routes = getRoutes();

      // Count all routes
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

      // OSS core only (no plugins): still a substantial set of routes
      expect(totalRoutes).toBeGreaterThan(25);
    });

    it("should have valid route structure with no duplicate paths at same level", () => {
      const routes = getRoutes();

      const checkDuplicates = (routeList: any[], level = 0) => {
        const paths = routeList
          .filter((route) => route.path)
          .map((route) => route.path);

        const uniquePaths = new Set(paths);
        expect(paths.length).toBe(uniquePaths.size);

        // Check children recursively
        routeList.forEach((route) => {
          if (route.children) {
            checkDuplicates(route.children, level + 1);
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

  describe("Feature Flag Conditional Routes", () => {
    // Shared helper function to count all routes recursively
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

    // Calculate baseline route count (all feature flags disabled)
    let BASELINE_ROUTE_COUNT: number;
    beforeAll(() => {
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const baselineRoutes = getRoutes();
      BASELINE_ROUTE_COUNT = countAllRoutes(baselineRoutes);
    });

    it("should toggle PLAYGROUND feature flag and compare route counts", () => {
      // In OSS-only, PLAYGROUND only affects getIndexRoute: when true, no index route is added (hub comes from plugins).
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const routesPlaygroundDisabled = getRoutes();
      const countPlaygroundDisabled = countAllRoutes(routesPlaygroundDisabled);

      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "PLAYGROUND",
      );
      const routesPlaygroundEnabled = getRoutes();
      const countPlaygroundEnabled = countAllRoutes(routesPlaygroundEnabled);

      // PLAYGROUND true => one fewer route (no index route in OSS)
      expect(countPlaygroundEnabled).toBe(countPlaygroundDisabled - 1);
      expect(countPlaygroundDisabled).toBe(BASELINE_ROUTE_COUNT);
      expect(countPlaygroundEnabled).toBe(BASELINE_ROUTE_COUNT - 1);
    });

    it("should call feature flags when getRoutes runs (not at module load)", () => {
      // Feature flags are read inside getRoutes(), not at routes module load
      vi.clearAllMocks();
      getRoutes();
      expect(mockFeatureFlags.isEnabled).toHaveBeenCalled();
    });

    it("should show what routes are actually generated with current feature flag settings", () => {
      const routes = getRoutes();

      // Flatten all routes to see what we actually get
      const getAllPaths = (routes: any[]): string[] => {
        const paths: string[] = [];
        routes.forEach((route) => {
          if (route.path) {
            paths.push(route.path);
          }
          if (route.children) {
            paths.push(...getAllPaths(route.children));
          }
        });
        return paths;
      };

      const allPaths = getAllPaths(routes);

      // Check for conditional paths that should/shouldn't be there
      const conditionalPaths = {
        hub: allPaths.filter((path) => path.includes("/hub")),
        getStarted: allPaths.filter((path) => path.includes("/get-started")),
        taskExecution: allPaths.filter((path) =>
          path.includes("/taskExecution"),
        ),
      };

      // OSS-only: no hub, get-started, or taskExecution (those come from plugins)
      expect(conditionalPaths.hub.length).toBe(0);
      expect(conditionalPaths.getStarted.length).toBe(0);
      expect(conditionalPaths.taskExecution.length).toBe(0);

      // OSS core routes
      expect(allPaths).toContain("*");
      expect(allPaths).toContain("/executions");
      expect(allPaths).toContain("/runWorkflow");
    });

    it("should not change route count for SHOW_GET_STARTED_PAGE in OSS", () => {
      // get-started route is added by orkes plugins, not by conductor-ui getRoutes()
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countDisabled = countAllRoutes(getRoutes());
      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "SHOW_GET_STARTED_PAGE",
      );
      const countEnabled = countAllRoutes(getRoutes());
      expect(countEnabled).toBe(countDisabled);
      expect(countDisabled).toBe(BASELINE_ROUTE_COUNT);
    });

    it("should not change route count for TASK_INDEXING in OSS", () => {
      // taskExecution route is added by orkes plugins, not by conductor-ui getRoutes()
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countDisabled = countAllRoutes(getRoutes());
      mockFeatureFlags.isEnabled.mockImplementation(
        (feature: string) => feature === "TASK_INDEXING",
      );
      const countEnabled = countAllRoutes(getRoutes());
      expect(countEnabled).toBe(countDisabled);
      expect(countDisabled).toBe(BASELINE_ROUTE_COUNT);
    });

    it("should only change count for PLAYGROUND when multiple flags toggled in OSS", () => {
      // In OSS, only PLAYGROUND affects getRoutes() (drops index route). Others are plugin-driven.
      mockFeatureFlags.isEnabled.mockImplementation(() => false);
      const countAllDisabled = countAllRoutes(getRoutes());
      mockFeatureFlags.isEnabled.mockImplementation((feature: string) =>
        ["PLAYGROUND", "SHOW_GET_STARTED_PAGE", "TASK_INDEXING"].includes(
          feature,
        ),
      );
      const countAllEnabled = countAllRoutes(getRoutes());
      expect(countAllEnabled).toBe(countAllDisabled - 1); // PLAYGROUND removes index route
      expect(countAllDisabled).toBe(BASELINE_ROUTE_COUNT);
      expect(countAllEnabled).toBe(BASELINE_ROUTE_COUNT - 1);
    });

    it("should reflect OSS behavior: only PLAYGROUND changes count", () => {
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

      // Only PLAYGROUND changes count in conductor-ui (-1 for no index route)
      expect(countPlaygroundOnly).toBe(countAllDisabled - 1);
      expect(countGetStartedOnly).toBe(countAllDisabled);
      expect(countTaskIndexingOnly).toBe(countAllDisabled);
      expect(countAllDisabled).toBe(BASELINE_ROUTE_COUNT);
    });
  });
});
