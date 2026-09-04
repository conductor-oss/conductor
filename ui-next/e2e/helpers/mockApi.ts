/**
 * API mocking helpers for Playwright E2E tests.
 *
 * The Conductor UI proxies /api/* to a backend server. Tests call these
 * helpers via page.route() to intercept those requests so the suite can
 * run without a live Conductor backend.
 */

import type { Page } from "@playwright/test";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/**
 * A workflow definition with a SWITCH task that has five named cases plus a
 * default case. Use this to snapshot the reaflow diagram with a branchy graph.
 *
 * Topology:
 *   read_input_ref (SET_VARIABLE)
 *     └── route_by_priority_ref (SWITCH)
 *           ├── "urgent"  → notify_urgent_ref  (SIMPLE)
 *           ├── "high"    → notify_high_ref    (SIMPLE)
 *           ├── "medium"  → notify_medium_ref  (SIMPLE)
 *           ├── "low"     → notify_low_ref     (SIMPLE)
 *           ├── "bulk"    → notify_bulk_ref    (SIMPLE)
 *           └── default   → notify_fallback_ref (SIMPLE)
 */
export const FIVE_CASE_SWITCH_WORKFLOW = {
  name: "five_case_switch",
  version: 1,
  description: "Demo workflow — 5-case SWITCH with a default branch",
  ownerEmail: "test@example.com",
  schemaVersion: 2,
  restartable: true,
  timeoutSeconds: 0,
  workflowStatusListenerEnabled: false,
  failureWorkflow: "",
  inputParameters: ["priority"],
  tasks: [
    {
      name: "read_input",
      taskReferenceName: "read_input_ref",
      type: "SET_VARIABLE",
      description: "Capture workflow inputs as variables",
      startDelay: 0,
      optional: false,
      joinOn: [],
      defaultExclusiveJoinTask: [],
      inputParameters: {
        priority: "${workflow.input.priority}",
      },
    },
    {
      name: "route_by_priority",
      taskReferenceName: "route_by_priority_ref",
      type: "SWITCH",
      description: "Branch on the priority field",
      evaluatorType: "value-param",
      expression: "switchCaseValue",
      startDelay: 0,
      optional: false,
      joinOn: [],
      defaultExclusiveJoinTask: [],
      inputParameters: {
        switchCaseValue: "${workflow.input.priority}",
      },
      decisionCases: {
        urgent: [
          {
            name: "notify_urgent",
            taskReferenceName: "notify_urgent_ref",
            type: "SIMPLE",
            description: "Page on-call engineer immediately",
            startDelay: 0,
            optional: false,
            joinOn: [],
            defaultExclusiveJoinTask: [],
            inputParameters: { channel: "pagerduty", level: "P1" },
          },
        ],
        high: [
          {
            name: "notify_high",
            taskReferenceName: "notify_high_ref",
            type: "SIMPLE",
            description: "Send Slack alert to ops channel",
            startDelay: 0,
            optional: false,
            joinOn: [],
            defaultExclusiveJoinTask: [],
            inputParameters: { channel: "slack", level: "P2" },
          },
        ],
        medium: [
          {
            name: "notify_medium",
            taskReferenceName: "notify_medium_ref",
            type: "SIMPLE",
            description: "Create a Jira ticket",
            startDelay: 0,
            optional: false,
            joinOn: [],
            defaultExclusiveJoinTask: [],
            inputParameters: { channel: "jira", level: "P3" },
          },
        ],
        low: [
          {
            name: "notify_low",
            taskReferenceName: "notify_low_ref",
            type: "SIMPLE",
            description: "Send email digest",
            startDelay: 0,
            optional: false,
            joinOn: [],
            defaultExclusiveJoinTask: [],
            inputParameters: { channel: "email", level: "P4" },
          },
        ],
        bulk: [
          {
            name: "notify_bulk",
            taskReferenceName: "notify_bulk_ref",
            type: "SIMPLE",
            description: "Batch into nightly report",
            startDelay: 0,
            optional: false,
            joinOn: [],
            defaultExclusiveJoinTask: [],
            inputParameters: { channel: "report", level: "P5" },
          },
        ],
      },
      defaultCase: [
        {
          name: "notify_fallback",
          taskReferenceName: "notify_fallback_ref",
          type: "SIMPLE",
          description: "Log unknown priority and alert ops",
          startDelay: 0,
          optional: false,
          joinOn: [],
          defaultExclusiveJoinTask: [],
          inputParameters: { channel: "ops-log", level: "unknown" },
        },
      ],
    },
  ],
};

// ---------------------------------------------------------------------------
// Route helpers
// ---------------------------------------------------------------------------

/**
 * Register a mock for the five_case_switch workflow definition detail endpoint.
 *
 * Call this AFTER `mockCommonApis`. Playwright uses last-registered-wins
 * semantics (new handlers are unshifted to the front of the match list), so
 * registering this specific override last ensures it takes precedence over the
 * broader /api/metadata/workflow catch-all added by mockCommonApis.
 */
export async function mockSwitchWorkflowDef(page: Page): Promise<void> {
  await page.route("**/api/metadata/workflow/five_case_switch**", (route) =>
    route.fulfill({ json: FIVE_CASE_SWITCH_WORKFLOW }),
  );
}

/** Empty paginated workflow search response */
const EMPTY_WORKFLOW_SEARCH = {
  totalHits: 0,
  results: [],
};

/** Empty paginated task search response */
const EMPTY_TASK_SEARCH = {
  totalHits: 0,
  results: [],
};

/**
 * OSS queue monitor fetches sizes and poll records as two endpoints and joins
 * them in the client. Register these AFTER `mockCommonApis` so they win over
 * the `/api/**` catch-all.
 */
export const QUEUE_MONITOR_SIZES = {
  send_email: 12,
  process_payment: 0,
  idle_queue: 3,
};

export const QUEUE_MONITOR_POLL_DATA = [
  {
    queueName: "send_email",
    domain: "",
    workerId: "worker-east",
    lastPollTime: 0,
  },
  {
    queueName: "send_email",
    domain: "",
    workerId: "worker-west",
    lastPollTime: 0,
  },
  {
    queueName: "process_payment",
    domain: "",
    workerId: "worker-pay",
    lastPollTime: 0,
  },
];

export async function mockQueueMonitorApis(page: Page): Promise<void> {
  await page.route("**/api/tasks/queue/polldata/all**", (route) =>
    route.fulfill({ json: QUEUE_MONITOR_POLL_DATA }),
  );
  await page.route("**/api/tasks/queue/all**", (route) =>
    route.fulfill({ json: QUEUE_MONITOR_SIZES }),
  );
}

// ---------------------------------------------------------------------------
// Schema registry fixtures
// ---------------------------------------------------------------------------

/**
 * Two schemas, one of them with a version history, and one of a type this
 * server stores but does not validate. Enough for the management screen to show
 * every column it has and for a picker to offer a name and its versions.
 */
export const SCHEMAS = [
  {
    name: "order_input",
    version: 1,
    type: "JSON",
    data: {
      $schema: "http://json-schema.org/draft-07/schema",
      type: "object",
      properties: { orderId: { type: "string" } },
      required: ["orderId"],
    },
    createTime: 1735689600000,
    updateTime: 1735689600000,
  },
  {
    name: "order_input",
    version: 2,
    type: "JSON",
    data: {
      $schema: "http://json-schema.org/draft-07/schema",
      type: "object",
      properties: {
        orderId: { type: "string" },
        total: { type: "integer" },
      },
      required: ["orderId", "total"],
    },
    createTime: 1735776000000,
    updateTime: 1735776000000,
  },
  {
    name: "shipment_event",
    version: 1,
    type: "AVRO",
    data: { type: "record", name: "shipment" },
    createTime: 1735862400000,
    updateTime: 1735862400000,
  },
];

/** A task definition that references a registered schema on both sides. */
export const SCHEMA_REFERENCING_TASK_DEF = {
  name: "process_order",
  description: "Processes one order",
  retryCount: 3,
  timeoutSeconds: 3600,
  inputKeys: [],
  outputKeys: [],
  timeoutPolicy: "TIME_OUT_WF",
  retryLogic: "FIXED",
  retryDelaySeconds: 60,
  responseTimeoutSeconds: 600,
  concurrentExecLimit: 0,
  rateLimitPerFrequency: 0,
  rateLimitFrequencyInSeconds: 1,
  ownerEmail: "test@example.com",
  pollTimeoutSeconds: 3600,
  backoffScaleFactor: 1,
  enforceSchema: true,
  inputSchema: { name: "order_input", version: 2, type: "JSON" },
  outputSchema: { name: "order_input", version: 2, type: "JSON" },
};

/**
 * Register the schema-referencing task definition. Call AFTER `mockCommonApis`
 * so it wins over the `/api/**` catch-all.
 */
export async function mockSchemaReferencingTaskDef(page: Page): Promise<void> {
  await page.route("**/api/metadata/taskdefs/process_order**", (route) =>
    route.fulfill({ json: SCHEMA_REFERENCING_TASK_DEF }),
  );
}

/**
 * A server that serves the schema registry. Register AFTER `mockCommonApis`.
 *
 * Playwright matches the most recently registered route first, so the listing
 * pattern — which also matches every single-schema path — goes first and the
 * per-version overrides after it. Registered the other way round, a request for
 * one schema is answered with the whole list.
 */
export async function mockSchemaRegistry(page: Page): Promise<void> {
  await page.route("**/api/schema**", (route) =>
    route.fulfill({ json: SCHEMAS }),
  );
  await page.route("**/api/schema/order_input/2**", (route) =>
    route.fulfill({ json: SCHEMAS[1] }),
  );
  await page.route("**/api/schema/shipment_event/1**", (route) =>
    route.fulfill({ json: SCHEMAS[2] }),
  );
}

/**
 * A server with no schema registry: what a stock OSS server did before the
 * registry existed. The picker asks for the list and gets a 404.
 * Register AFTER `mockCommonApis`.
 */
export async function mockMissingSchemaRegistry(page: Page): Promise<void> {
  await page.route("**/api/schema**", (route) =>
    route.fulfill({
      status: 404,
      json: { status: 404, message: "No such endpoint" },
    }),
  );
}

/** Mock API endpoints that are fetched on initial page load */
export async function mockCommonApis(page: Page): Promise<void> {
  // Workflow execution search (WorkflowSearch page default load)
  await page.route("**/api/workflow/search**", (route) =>
    route.fulfill({ json: EMPTY_WORKFLOW_SEARCH }),
  );

  // Workflow definitions list
  await page.route("**/api/metadata/workflow**", (route) =>
    route.fulfill({ json: [] }),
  );

  // Task definitions list
  await page.route("**/api/metadata/taskdefs**", (route) =>
    route.fulfill({ json: [] }),
  );

  // Scheduler paginated search — must be registered before the broader
  // schedules pattern so this more-specific route wins first.
  await page.route("**/api/scheduler/schedules/search**", (route) =>
    route.fulfill({ json: { results: [], totalHits: 0 } }),
  );

  // Scheduler schedule definitions list
  await page.route("**/api/scheduler/schedules**", (route) =>
    route.fulfill({ json: [] }),
  );

  // Scheduler executions (/scheduler/search/executions)
  await page.route("**/api/scheduler/search**", (route) =>
    route.fulfill({ json: EMPTY_WORKFLOW_SEARCH }),
  );

  // Event handler queue names
  await page.route("**/api/event/queues**", (route) =>
    route.fulfill({ json: [] }),
  );

  // Task queue data
  await page.route("**/api/event**", (route) => route.fulfill({ json: [] }));

  // Task execution search
  await page.route("**/api/tasks/search**", (route) =>
    route.fulfill({ json: EMPTY_TASK_SEARCH }),
  );

  // Version / release info (used by useAPIReleaseVersion)
  await page.route("**/api/version**", (route) =>
    route.fulfill({ json: { version: "test", buildTime: "2026-01-01" } }),
  );

  // Tags
  await page.route("**/api/metadata/tags**", (route) =>
    route.fulfill({ json: [] }),
  );

  // Fall-through: return an empty array for any remaining /api calls.
  // Most Conductor list endpoints return arrays; returning [] is safer than {}
  // because components that call .map() on the result won't crash.
  await page.route("**/api/**", (route) => route.fulfill({ json: [] }));
}
