import { vi } from "vitest";
import { FEATURES } from "utils";

/**
 * Mock feature flags utility
 */
export const createMockFeatureFlags = (enabledFeatures: string[] = []) => {
  return {
    isEnabled: vi.fn((feature: string) => enabledFeatures.includes(feature)),
    getValue: vi.fn((feature: string, defaultValue?: string) => defaultValue),
    getContextValue: vi.fn(() => undefined),
  };
};

/**
 * Common feature flag configurations for testing
 */
export const FEATURE_FLAG_SCENARIOS = {
  PLAYGROUND_ENABLED: [FEATURES.PLAYGROUND],
  GET_STARTED_ENABLED: [FEATURES.SHOW_GET_STARTED_PAGE],
  TASK_INDEXING_ENABLED: [FEATURES.TASK_INDEXING],
  ALL_FEATURES_ENABLED: [
    FEATURES.PLAYGROUND,
    FEATURES.SHOW_GET_STARTED_PAGE,
    FEATURES.TASK_INDEXING,
    FEATURES.SCHEDULER,
    FEATURES.HUMAN_TASK,
    FEATURES.SECRETS,
    FEATURES.WEBHOOKS,
    FEATURES.RBAC,
    FEATURES.INTEGRATIONS,
    FEATURES.REMOTE_SERVICES,
  ],
  NO_FEATURES_ENABLED: [],
};

/**
 * Mock all page components with consistent test IDs
 */
export const mockPageComponents = () => {
  // Mock Okta components
  vi.mock("@okta/okta-react", () => ({
    LoginCallback: () => <div data-testid="login-callback">LoginCallback</div>,
  }));

  // Mock core components
  vi.mock("components/features/auth/AuthGuard", () => ({
    default: ({ children, runWorkflow }: any) => (
      <div data-testid="auth-guard" data-run-workflow={runWorkflow}>
        {children}
      </div>
    ),
  }));

  vi.mock("components/App", () => ({
    App: ({ children }: any) => <div data-testid="app-root">{children}</div>,
  }));

  // Mock auth components
  vi.mock("components/features/auth/oidc/OidcRedirectEndpoint", () => ({
    OidcRedirectEndpoint: () => (
      <div data-testid="oidc-redirect-endpoint">OidcRedirectEndpoint</div>
    ),
  }));

  vi.mock("enterprise/pages/auth/Login", () => ({
    default: () => <div data-testid="login">Login</div>,
  }));

  // Mock page components
  const pageComponentMocks = {
    // Access management
    "enterprise/pages/access/ApplicationManagement": "application-management",
    "enterprise/pages/access/GroupManagement": "group-management",
    "enterprise/pages/access/users/UserManagement": "user-management",

    // AI and integrations
    "enterprise/pages/aiPrompts/AiPromptsManagement": "ai-prompts-management",
    "enterprise/pages/integrations/IntegrationsManagement":
      "integrations-management",

    // Authentication
    "enterprise/pages/Authentication/AuthListing": "auth-listing",

    // Definitions
    "pages/definition/task": { TaskDefinition: "task-definition" },
    "pages/definition/WorkflowDefinition": "workflow-definition",
    "../pages/definition/EventHandler/EventHandler": "event-handler-definition",

    // Executions
    "pages/executions": {
      SchedulerExecutions: "scheduler-executions",
      TaskSearch: "task-search",
      WorkflowSearch: "workflow-search",
    },
    "../pages/execution/Execution": "execution",

    // Hub
    "enterprise/pages/hub/hub": {
      HubMain: "hub-main",
      HubTemplateDetail: "hub-template-detail",
      HubTemplateImport: "hub-template-import",
    },

    // Human tasks
    "enterprise/pages/human": { SearchPage: "search-page" },
    "enterprise/pages/human/humanTask": { TaskPage: "task-page" },
    "enterprise/pages/human/search/TaskInboxPage": {
      TaskInboxPage: "task-inbox-page",
    },
    "enterprise/pages/human/templates": {
      TemplateEditorPage: "template-editor-page",
      TemplatePage: "template-page",
    },

    // Other pages
    "pages/creatorFlags/CreatorFlags": { CreatorFlags: "creator-flags" },
    "enterprise/pages/envVariables/EnvVariables": {
      EnvVariables: "env-variables",
    },
    "pages/error/ErrorPage": "error-page",
    "pages/eventMonitor/EventMonitor": { EventMonitor: "event-monitor" },
    "pages/eventMonitor/EventMonitorDetail/EventMonitorDetail": {
      EventMonitorDetail: "event-monitor-detail",
    },
    "enterprise/pages/getStarted/GetStarted": "get-started",
    "enterprise/pages/metrics": "metrics-page",
    "enterprise/pages/secrets/Secrets": "secrets",
    "enterprise/pages/workflowExplorer/Explorer": "explorer",

    // Remote services
    "enterprise/pages/remoteServices/edit/ServiceEdit": "service-edit",
    "enterprise/pages/remoteServices/Services": { Services: "remote-services" },

    // Schema
    "enterprise/pages/schema/edit/SchemaEditPage": {
      SchemaEditPage: "schema-edit-page",
    },
    "enterprise/pages/schema/list/SchemaList": { SchemaList: "schema-list" },

    // Services
    "enterprise/pages/services/EditService": "edit-service",
    "enterprise/pages/services/NewService": "new-service",
    "enterprise/pages/services/routes/EditRoute": "edit-route",
    "enterprise/pages/services/routes/NewRoute": "new-route",
    "enterprise/pages/services/routes/RouteDetails": "route-details",
    "enterprise/pages/services/Service": "service",
    "enterprise/pages/services/Services": "services",

    // Webhooks
    "enterprise/pages/webhooks": { Webhooks: "webhooks" },
    "enterprise/pages/webhooks/edit/WebhookEdit": {
      WebhookEditPage: "webhook-edit-page",
    },

    // Kitchen sink
    "../pages/kitchensink/Examples": "examples",
    "../pages/kitchensink/Gantt": "gantt",
    "../pages/kitchensink/KitchenSink": "kitchen-sink",
    "../pages/kitchensink/ThemeSampler": "theme-sampler",

    // Queue and scheduler
    "../pages/queueMonitor/TaskQueue": "task-queue",
    "../pages/scheduler": { Schedule: "schedule" },

    // Definitions (additional)
    "pages/definitions": {
      EventHandler: "event-handler-definitions",
      Schedules: "schedule-definitions",
      Task: "task-definitions",
      Workflow: "workflow-definitions",
    },
  };

  // Create mocks for each page component
  Object.entries(pageComponentMocks).forEach(([path, mockConfig]) => {
    if (typeof mockConfig === "string") {
      // Simple default export
      vi.mock(path, () => ({
        default: () => <div data-testid={mockConfig}>{mockConfig}</div>,
      }));
    } else {
      // Named exports
      const namedExports: any = {};
      Object.entries(mockConfig).forEach(([exportName, testId]) => {
        namedExports[exportName] = () => (
          <div data-testid={testId}>{testId}</div>
        );
      });
      vi.mock(path, () => namedExports);
    }
  });
};

/**
 * Helper to find routes in the route tree
 */
export const findRouteByPath = (routes: any[], path: string): any => {
  for (const route of routes) {
    if (route.path === path) {
      return route;
    }
    if (route.children) {
      const found = findRouteByPath(route.children, path);
      if (found) return found;
    }
  }
  return null;
};

/**
 * Helper to find all routes with a specific property
 */
export const findRoutesByProperty = (
  routes: any[],
  property: string,
  value?: any,
): any[] => {
  const found: any[] = [];

  for (const route of routes) {
    if (value !== undefined) {
      if (route[property] === value) {
        found.push(route);
      }
    } else {
      if (Object.hasOwn(route, property)) {
        found.push(route);
      }
    }

    if (route.children) {
      found.push(...findRoutesByProperty(route.children, property, value));
    }
  }

  return found;
};

/**
 * Helper to get all paths from route tree
 */
export const getAllPaths = (routes: any[]): string[] => {
  const paths: string[] = [];

  for (const route of routes) {
    if (route.path) {
      paths.push(route.path);
    }
    if (route.children) {
      paths.push(...getAllPaths(route.children));
    }
  }

  return paths;
};

/**
 * Helper to count routes at each level
 */
export const getRouteStats = (routes: any[]) => {
  let totalRoutes = 0;
  let dynamicRoutes = 0;
  let wildcardRoutes = 0;
  let indexRoutes = 0;

  const countRoutes = (routeList: any[]) => {
    for (const route of routeList) {
      totalRoutes++;

      if (route.index) {
        indexRoutes++;
      }

      if (route.path) {
        if (route.path.includes(":")) {
          dynamicRoutes++;
        }
        if (route.path.includes("*")) {
          wildcardRoutes++;
        }
      }

      if (route.children) {
        countRoutes(route.children);
      }
    }
  };

  countRoutes(routes);

  return {
    totalRoutes,
    dynamicRoutes,
    wildcardRoutes,
    indexRoutes,
  };
};
