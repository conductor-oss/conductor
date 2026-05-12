/**
 * API mocking helpers for Playwright E2E tests.
 *
 * The Conductor UI proxies /api/* to a backend server. Tests call these
 * helpers via page.route() to intercept those requests so the suite can
 * run without a live Conductor backend.
 */

import type { Page } from "@playwright/test";

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
