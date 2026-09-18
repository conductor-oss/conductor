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
 * - Schemas
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
import { SchemaEditPage, SchemaList } from "pages/schema";
import { pluginRegistry } from "plugins/registry";
import { Navigate, RouteObject } from "react-router-dom";
import { featureFlags, FEATURES } from "utils";
import { resolveDefaultHomePath } from "utils/resolveDefaultHomePath";
import {
  API_REFERENCE_URL,
  EVENT_HANDLERS_URL,
  EVENT_MONITOR_URL,
  NEW_TASK_DEF_URL,
  RUN_WORKFLOW_URL,
  SCHEDULER_DEFINITION_URL,
  SCHEMAS_URL,
  TASK_DEF_URL,
  TASK_QUEUE_URL,
  WORKFLOW_DEFINITION_URL,
} from "utils/constants/route";
import {
  AgentDefinition,
  AgentDefinitions,
  CreateAgentGuide,
  AgentExecutions as AgentExecutionsPage,
  RunAgent,
  Secrets as AgentSecretsPage,
  Skills as SkillsPage,
} from "pages/agent";
import {
  AGENT_DEFINITION_URL,
  AGENT_EXECUTIONS_URL,
  AGENT_SECRETS_URL,
  RUN_AGENT_URL,
  SKILLS_URL,
} from "utils/constants/route";
import EventHandlerDefinition from "../pages/definition/EventHandler/EventHandler";
import Execution from "../pages/execution/Execution";
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

  // Dev/Debug pages
  {
    path: "/flags",
    element: <CreatorFlags />,
  },

  // Embedded Conductor-Agents pages (registered only when CONDUCTOR_INTEGRATIONS_AI_ENABLED, i.e.
  // the server's conductor.integrations.ai.enabled is true).
  ...(featureFlags.isEnabled(FEATURES.CONDUCTOR_INTEGRATIONS_AI_ENABLED)
    ? [
        { path: AGENT_DEFINITION_URL.BASE, element: <AgentDefinitions /> },
        { path: AGENT_DEFINITION_URL.NEW, element: <CreateAgentGuide /> },
        {
          path: AGENT_DEFINITION_URL.NAME_VERSION,
          element: <AgentDefinition />,
        },
        { path: AGENT_EXECUTIONS_URL.BASE, element: <AgentExecutionsPage /> },
        { path: RUN_AGENT_URL, element: <RunAgent /> },
        // Same Execution page/component as "/execution/:id/:taskId?" — just
        // reached from the Agents section, so the sidebar keeps "Executions"
        // (under Agents) highlighted instead of the plain Workflow item.
        { path: AGENT_EXECUTIONS_URL.ID_TASK_ID, element: <Execution /> },
        { path: SKILLS_URL.BASE, element: <SkillsPage /> },
        { path: AGENT_SECRETS_URL, element: <AgentSecretsPage /> },
      ]
    : []),
];

/**
 * Schema registry routes.
 *
 * Withheld when a plugin has already claimed the same paths. Core routes are
 * matched ahead of plugin routes, so registering these unconditionally would
 * displace a plugin-provided schema screen rather than defer to it.
 */
export const getSchemaRoutes = (pluginRoutes: RouteObject[]): RouteObject[] => {
  const routes = [
    {
      path: SCHEMAS_URL.BASE,
      element: <SchemaList />,
    },
    {
      path: SCHEMAS_URL.EDIT,
      element: <SchemaEditPage />,
    },
  ];
  const claimedByPlugin = routes.some((route) =>
    pluginRoutes.some((pluginRoute) => pluginRoute.path === route.path),
  );
  return claimedByPlugin ? [] : routes;
};

/**
 * Get the default index route based on feature flags
 */
const getIndexRoute = (isPlayground: boolean) => {
  if (isPlayground) {
    // In playground mode, Launch Pad / Hub is the public index from plugins
    return null;
  }
  // Redirect `/` to the first visible in-app sidebar destination (usually /executions).
  return {
    index: true,
    element: <Navigate to={resolveDefaultHomePath()} replace />,
  };
};

/**
 * Build the complete route configuration
 */
export const getRoutes = (): RouteObject[] => {
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
    ...getSchemaRoutes(pluginAuthenticatedRoutes),
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
