/**
 * Integration tests — Scheduler Definitions
 *
 * Seeds schedules via the API (against a real SET_VARIABLE workflow) and
 * verifies the /scheduleDef list + editor. Also covers UI create/delete with
 * typed confirmation.
 */

import { expect, test } from "../coverage-fixture";
import {
  createSchedule,
  createWorkflowDef,
  deleteSchedule,
  deleteWorkflowDef,
  getSchedule,
  pauseSchedule,
  resumeSchedule,
  type WorkflowDef,
  type WorkflowSchedule,
} from "./api-client";
import { confirmDeleteByTyping, expectMainContentScreenshot } from "./helpers";

const RUN_ID = Date.now();
const WF_NAME = `e2e_sched_wf_${RUN_ID}`;
const SCHED_LIST = `e2e_sched_list_${RUN_ID}`;
const SCHED_EDITOR = `e2e_sched_editor_${RUN_ID}`;
const SCHED_CRUD = `e2e_sched_crud_${RUN_ID}`;
const DESCRIPTION = "Created by Playwright E2E test — safe to delete";

const WORKFLOW: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description: DESCRIPTION,
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  tasks: [
    {
      name: "set_var",
      taskReferenceName: "set_var_ref",
      type: "SET_VARIABLE",
      inputParameters: { ping: "ok" },
    },
  ],
};

function makeSchedule(name: string, paused = false): WorkflowSchedule {
  return {
    name,
    description: DESCRIPTION,
    // Every minute — definitions tests do not wait for fires.
    cronExpression: "0 * * ? * *",
    zoneId: "UTC",
    paused,
    runCatchupScheduleInstances: false,
    startWorkflowRequest: {
      name: WF_NAME,
      version: 1,
      input: {},
    },
  };
}

test.beforeAll(async () => {
  await createWorkflowDef(WORKFLOW);
  await createSchedule(makeSchedule(SCHED_LIST));
  await createSchedule(makeSchedule(SCHED_EDITOR));
});

test.afterAll(async () => {
  await deleteSchedule(SCHED_LIST).catch(() => {});
  await deleteSchedule(SCHED_EDITOR).catch(() => {});
  await deleteSchedule(SCHED_CRUD).catch(() => {});
  await deleteWorkflowDef(WF_NAME).catch(() => {});
});

async function searchScheduleList(
  page: import("@playwright/test").Page,
  name: string,
) {
  await page.goto("/scheduleDef");
  await page.waitForLoadState("networkidle");
  await page.getByPlaceholder("Search scheduler definitions").fill(name);
}

// ── List / editor ──────────────────────────────────────────────────────────────

test("schedule definition appears in the /scheduleDef list", async ({
  page,
}) => {
  await searchScheduleList(page, SCHED_LIST);

  await expect(page.locator("#main-content").getByText(SCHED_LIST)).toBeVisible(
    { timeout: 15_000 },
  );
  await expect(page.getByText(WF_NAME).first()).toBeVisible();
  await expect(page.getByText("Active").first()).toBeVisible();

  await expectMainContentScreenshot(page, "scheduler-definitions-list.png", {
    // Input values are not covered by getByText masks — mask the field itself.
    mask: [page.locator('input[placeholder="Search scheduler definitions"]')],
  });
});

test("clicking a schedule opens the definition editor", async ({ page }) => {
  await searchScheduleList(page, SCHED_EDITOR);
  await page.locator("#main-content").getByText(SCHED_EDITOR).first().click();

  await expect(page).toHaveURL(new RegExp(`/scheduleDef/${SCHED_EDITOR}`), {
    timeout: 15_000,
  });
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#schedule-name-field")).toHaveValue(SCHED_EDITOR);
  await expect(page.locator("#schedule-description-field")).toHaveValue(
    DESCRIPTION,
  );
  await expect(page.getByLabel("Cron expression")).toHaveValue("0 * * ? * *");
  await expect(
    page.getByRole("combobox", { name: "Workflow or agent" }),
  ).toHaveValue(WF_NAME);

  await expectMainContentScreenshot(page, "scheduler-definition-editor.png", {
    mask: [
      page.locator("#schedule-name-field"),
      page.locator("#schedule-description-field"),
      page.getByRole("combobox", { name: "Workflow or agent" }),
      page.locator("#next-run-schedule-examples-wrapper"),
    ],
  });
});

test("navigating to /newScheduleDef opens an empty schedule form", async ({
  page,
}) => {
  await page.goto("/newScheduleDef");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/newScheduleDef/);
  await expect(page.locator("#schedule-name-field")).toHaveValue("");
  await expect(page.getByRole("button", { name: "Save" })).toBeVisible();

  await expectMainContentScreenshot(page, "scheduler-definition-new.png");
});

test("API pause/resume is reflected as Inactive/Active in the list", async ({
  page,
}) => {
  await pauseSchedule(SCHED_LIST);

  const paused = await getSchedule(SCHED_LIST);
  expect(paused.paused).toBe(true);

  await searchScheduleList(page, SCHED_LIST);
  await expect(page.locator("#main-content").getByText(SCHED_LIST)).toBeVisible(
    { timeout: 15_000 },
  );
  await expect(
    page.locator("#main-content").getByText("Inactive").first(),
  ).toBeVisible({ timeout: 15_000 });

  await resumeSchedule(SCHED_LIST);
  const resumed = await getSchedule(SCHED_LIST);
  expect(resumed.paused).toBe(false);

  await page.getByRole("button", { name: "Refresh" }).click();
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("#main-content").getByText("Active").first(),
  ).toBeVisible({ timeout: 15_000 });
});

// ── Create / delete via UI ─────────────────────────────────────────────────────

test.describe("schedule create and delete", () => {
  test.describe.configure({ mode: "serial" });

  test("creates a schedule definition via the UI", async ({ page }) => {
    await page.goto("/newScheduleDef");
    await page.waitForLoadState("networkidle");

    await page.locator("#schedule-name-field").fill(SCHED_CRUD);
    await page.locator("#schedule-description-field").fill(DESCRIPTION);

    // Pick a cron template (every minute).
    await page.getByLabel("Choose a template to get started").click();
    await page.getByRole("option", { name: "0 * * ? * *" }).click();
    await expect(page.getByLabel("Cron expression")).toHaveValue("0 * * ? * *");

    const workflowField = page.getByRole("combobox", {
      name: "Workflow or agent",
    });
    await workflowField.fill(WF_NAME);
    await page.getByRole("option", { name: WF_NAME }).click();

    await page.getByRole("button", { name: "Save" }).click();
    await page.getByRole("button", { name: "Confirm" }).click();

    await expect(
      page.getByText("Schedule definition saved successfully."),
    ).toBeVisible({ timeout: 15_000 });

    await searchScheduleList(page, SCHED_CRUD);
    await expect(
      page.locator("#main-content").getByText(SCHED_CRUD),
    ).toBeVisible({ timeout: 15_000 });
  });

  test("deletes a schedule definition via the UI", async ({ page }) => {
    await searchScheduleList(page, SCHED_CRUD);
    await expect(
      page.locator("#main-content").getByText(SCHED_CRUD),
    ).toBeVisible({ timeout: 15_000 });

    // Scope delete to the matching row — action icons have no unique ids.
    const row = page.locator("#main-content").getByRole("row").filter({
      hasText: SCHED_CRUD,
    });
    await row.getByRole("button", { name: "Delete schedule" }).click();
    await confirmDeleteByTyping(page, SCHED_CRUD);

    await expect(
      page.locator("#main-content").getByText(SCHED_CRUD),
    ).toHaveCount(0, { timeout: 15_000 });
  });
});
