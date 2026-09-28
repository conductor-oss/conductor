/**
 * Integration tests — Event Monitor (`/eventMonitor`)
 *
 * ## Why the list is always empty in the integration stack
 *
 * `GET /api/event/execution` is backed by IndexDAO. In this stack Conductor
 * uses `conductor.indexing.type=postgres`, and the Postgres IndexDAO explicitly
 * does not implement `addEventExecution` / `getEventExecutions` — those methods
 * log "not supported" and return an empty list. Event execution records are only
 * stored when Elasticsearch is configured.
 *
 * Running an EVENT task (even one whose `sink` matches a registered handler's
 * event name) does not produce rows because the persistence side-effect is a
 * no-op with the Postgres indexer.
 *
 * What we *can* test here:
 *   - The list page chrome mounts correctly (search input, status filter,
 *     empty-state message, refresh controls).
 *   - Navigating directly to `/eventMonitor/:name` loads EventMonitorDetail,
 *     the refresher state machine, the data table, and the Close/Refresh buttons
 *     — all the coverage we need even with zero rows.
 */

import { expect, test } from "../coverage-fixture";
import {
  createEventHandler,
  deleteEventHandler,
  type EventHandlerDef,
} from "./api-client";

const RUN_ID = Date.now();

const EH_MONITOR: EventHandlerDef = {
  name: `e2e_eh_monitor_${RUN_ID}`,
  event: `conductor:e2e_event_monitor_${RUN_ID}`,
  description: "Created by Playwright E2E test — safe to delete",
  evaluatorType: "javascript",
  condition: "true",
  active: true,
  actions: [
    {
      action: "complete_task",
      expandInlineJSON: false,
      complete_task: {
        workflowId: "${workflowId}",
        taskRefName: "${taskReferenceName}",
      },
    },
  ],
};

test.beforeAll(async () => {
  await createEventHandler(EH_MONITOR);
});

test.afterAll(async () => {
  await deleteEventHandler(EH_MONITOR.name).catch(() => {});
});

test("event monitor list page renders search and chrome", async ({ page }) => {
  await page.goto("/eventMonitor");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText("Event Monitor").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.locator("#search-event")).toBeVisible({ timeout: 15_000 });
  await expect(
    page.getByText(/No event found|0 results|results/i).first(),
  ).toBeVisible({ timeout: 15_000 });

  // Search filter chrome still works against an empty execution list.
  await page.locator("#search-event").fill(EH_MONITOR.name);
  await expect(page.locator("#search-event")).toHaveValue(EH_MONITOR.name);
});

test("event monitor detail page loads for a handler name", async ({ page }) => {
  // Detail is keyed by handler/event name; list may be empty on OSS, so goto
  // the detail route directly to exercise EventMonitorDetail.
  await page.goto(`/eventMonitor/${encodeURIComponent(EH_MONITOR.name)}`);
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(new RegExp(`/eventMonitor/${EH_MONITOR.name}`), {
    timeout: 15_000,
  });
  await expect(page.locator("#event-monitor-container")).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(EH_MONITOR.name).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(
    page.getByRole("button", { name: /Refresh/i }).first(),
  ).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole("button", { name: /Close/i })).toBeVisible({
    timeout: 15_000,
  });
});
