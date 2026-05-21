import path from "path";
import type { Page } from "@playwright/test";

const fixturesDir = path.join(__dirname, "../fixtures");

/** Resolve a fixture file path relative to e2e/fixtures/. */
export const f = (name: string) => path.join(fixturesDir, name);

/**
 * Register route mocks for the APIs that are required on every page:
 * workflow/task metadata used by nav dropdowns and search forms.
 */
export async function mockCommonApis(page: Page) {
  await page.route("**/api/metadata/workflow/names-and-versions", (route) =>
    route.fulfill({ path: f("metadataWorkflowNamesAndVersions.json") })
  );
  await page.route("**/api/metadata/workflow/names", (route) =>
    route.fulfill({ path: f("metadataWorkflowNames.json") })
  );
  await page.route("**/api/metadata/workflow/*/versions", (route) =>
    route.fulfill({ path: f("metadataWorkflowVersions.json") })
  );
  await page.route("**/api/metadata/taskdefs", (route) =>
    route.fulfill({ path: f("metadataTasks.json") })
  );
}

/** Mock the workflow execution search endpoint. */
export async function mockWorkflowSearch(page: Page) {
  await page.route("**/api/workflow/search**", (route) =>
    route.fulfill({ path: f("workflowSearch.json") })
  );
}

/** Mock the task execution search endpoint. */
export async function mockTaskSearch(page: Page) {
  await page.route("**/api/tasks/search**", (route) =>
    route.fulfill({ path: f("taskSearch.json") })
  );
}

/**
 * Mock scheduler endpoints.
 * The /schedules endpoint must be mocked on any page that uses ScheduleNameInput —
 * without it, serve -s returns index.html and data.map() throws, crashing the component.
 */
export async function mockSchedulerApis(page: Page) {
  await page.route("**/api/scheduler/schedules**", (route) =>
    route.fulfill({ path: f("schedulerDefs.json") })
  );
  await page.route("**/api/scheduler/search/executions**", (route) =>
    route.fulfill({ path: f("schedulerExecutions.json") })
  );
}
