/**
 * Routes Configuration
 *
 * This module defines the application routes. Core routes are defined inline,
 * while enterprise routes are registered via the plugin system.
 *
 * Core routes (OSS):
 * - Workflow definitions and executions
 * - Task definitions
 * - Event handlers
 * - Scheduler definitions and executions
 * - Queue monitor
 * - Event monitor
 * - API reference
 * - Tags dashboard
 *
 * Enterprise routes (registered via plugins):
 * - Auth (login, callbacks, RBAC pages)
 * - Webhooks
 * - Human Tasks
 * - AI Prompts
 * - Secrets
 * - Integrations
 * - Gateway Services
 * - Remote Services
 * - Metrics
 * - Environment Variables
 * - Schemas
 * - Workers
 */

import { App } from "components/App";
import DefaultAuthGuard from "components/features/auth/AuthGuard";
import ApiReferencePage from "pages/apiDocs/ApiReferencePage";
import { CreatorFlags } from "pages/creatorFlags/CreatorFlags";
import { TaskDefinition } from "pages/definition/task";
import WorkflowDefinition from "pages/definition/WorkflowDefinition";
import {
  EventHandler as EventHandlerDefinitions,
  Schedules as ScheduleDefinitions,
  Task as TaskDefinitions,
  Workflow as WorkflowDefinitions,
} from "pages/definitions";
import ErrorPage from "pages/error/ErrorPage";
import { EventMonitor } from "pages/eventMonitor/EventMonitor";
import { EventMonitorDetail } from "pages/eventMonitor/EventMonitorDetail/EventMonitorDetail";
import { SchedulerExecutions, WorkflowSearch } from "pages/executions";
import { pluginRegistry } from "plugins/registry";
import { featureFlags, FEATURES } from "utils";
import {
  API_REFERENCE_URL,
  EVENT_HANDLERS_URL,
  EVENT_MONITOR_URL,
  NEW_TASK_DEF_URL,
  RUN_WORKFLOW_URL,
  SCHEDULER_DEFINITION_URL,
  TASK_DEF_URL,
  TASK_QUEUE_URL,
  WORKFLOW_DEFINITION_URL,
} from "utils/constants/route";
import EventHandlerDefinition from "../pages/definition/EventHandler/EventHandler";
import Execution from "../pages/execution/Execution";
import Examples from "../pages/kitchensink/Examples";
import Gantt from "../pages/kitchensink/Gantt";
import KitchenSink from "../pages/kitchensink/KitchenSink";
import ThemeSampler from "../pages/kitchensink/ThemeSampler";
import TaskQueue from "../pages/queueMonitor/TaskQueue";
import { Schedule } from "../pages/scheduler";

/**
 * Core authenticated routes (OSS)
 * These are the fundamental Conductor UI features available in open source.
 */
const getCoreAuthenticatedRoutes = () => [
  // Workflow Executions
  {
    path: "/executions",
    element: <WorkflowSearch />,
  },
  {
    path: "/schedulerExecs",
    element: <SchedulerExecutions />,
  },
  {
    path: "/execution/:id/:taskId?",
    element: <Execution />,
  },

  // Workflow Definitions
  {
    path: WORKFLOW_DEFINITION_URL.BASE,
    element: <WorkflowDefinitions />,
  },
  {
    path: WORKFLOW_DEFINITION_URL.NAME_VERSION,
    element: <WorkflowDefinition />,
  },
  {
    path: WORKFLOW_DEFINITION_URL.NEW,
    element: <WorkflowDefinition />,
  },
  {
    path: "/workFlowTemplate/:templateId",
    element: <WorkflowDefinition />,
  },

  // Task Definitions
  {
    path: NEW_TASK_DEF_URL,
    element: <TaskDefinition />,
  },
  {
    path: TASK_DEF_URL.BASE,
    element: <TaskDefinitions />,
  },
  {
    path: TASK_DEF_URL.NAME,
    element: <TaskDefinition />,
  },

  // Event Handlers
  {
    path: EVENT_HANDLERS_URL.BASE,
    element: <EventHandlerDefinitions />,
  },
  {
    path: EVENT_HANDLERS_URL.NAME,
    element: <EventHandlerDefinition />,
  },
  {
    path: EVENT_HANDLERS_URL.NEW,
    element: <EventHandlerDefinition />,
  },

  // Scheduler Definitions
  {
    path: SCHEDULER_DEFINITION_URL.BASE,
    element: <ScheduleDefinitions />,
  },
  {
    path: SCHEDULER_DEFINITION_URL.NAME,
    element: <Schedule />,
  },
  {
    path: SCHEDULER_DEFINITION_URL.NEW,
    element: <Schedule />,
  },

  // Queue Monitor
  {
    path: TASK_QUEUE_URL.BASE,
    element: <TaskQueue />,
  },

  // Event Monitor
  {
    path: EVENT_MONITOR_URL.BASE,
    element: <EventMonitor />,
  },
  {
    path: EVENT_MONITOR_URL.NAME,
    element: <EventMonitorDetail />,
  },

  // API Reference
  {
    path: API_REFERENCE_URL.BASE,
    element: <ApiReferencePage />,
  },

  // Dev/Debug pages (Kitchen Sink)
  {
    path: "/kitchen",
    element: <KitchenSink />,
  },
  {
    path: "/kitchen/examples",
    element: <Examples />,
  },
  {
    path: "/kitchen/gantt",
    element: <Gantt />,
  },
  {
    path: "/kitchen/theme",
    element: <ThemeSampler />,
  },
  {
    path: "/flags",
    element: <CreatorFlags />,
  },
];

/**
 * Get the default index route based on feature flags
 */
const getIndexRoute = (isPlayground: boolean) => {
  if (isPlayground) {
    // In playground mode, we need the hub pages - these come from plugins
    return null; // Will be provided by playground plugin
  }
  return {
    index: true,
    element: <WorkflowSearch />,
  };
};

/**
 * Build the complete route configuration
 */
export const getRoutes = () => {
  const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

  // Get routes from plugins
  const pluginAuthenticatedRoutes = pluginRegistry.getRoutes();
  const pluginPublicRoutes = pluginRegistry.getPublicRoutes();

  // Get auth guard from plugins (enterprise) or use default (OSS)
  const AuthGuard = pluginRegistry.getAuthGuard() || DefaultAuthGuard;

  // Core authenticated routes
  const coreRoutes = getCoreAuthenticatedRoutes();

  // Build the index route (either core WorkflowSearch or from playground plugin)
  const indexRoute = getIndexRoute(isPlayground);

  // Combine all authenticated routes
  const allAuthenticatedRoutes = [
    ...(indexRoute ? [indexRoute] : []),
    ...coreRoutes,
    ...pluginAuthenticatedRoutes,
  ];

  return [
    {
      path: "/",
      element: <App />,
      children: [
        // Main authenticated section
        {
          element: <AuthGuard />,
          children: allAuthenticatedRoutes,
        },

        // Special route for runWorkflow (has special AuthGuard behavior)
        {
          path: RUN_WORKFLOW_URL,
          element: <AuthGuard runWorkflow={true} />,
        },

        // Public routes from plugins (login pages, OAuth callbacks, etc.)
        ...pluginPublicRoutes,

        // Error page (catch-all)
        {
          path: "*",
          element: <ErrorPage />,
        },
      ],
    },
  ];
};
