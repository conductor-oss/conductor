/**
 * Integration tests — Event Handler Definitions
 *
 * Covers list/editor navigation (API-seeded fixtures) plus UI-driven create
 * and delete flows with typed confirmation.
 */

import { expect, test } from "@playwright/test";
import {
  createEventHandler,
  deleteEventHandler,
  type EventHandlerDef,
} from "./api-client";
import { confirmDeleteByTyping } from "./helpers";

const RUN_ID = Date.now();

function makeEventHandler(suffix: string): EventHandlerDef {
  return {
    name: `e2e_eh_${suffix}_${RUN_ID}`,
    event: `conductor:e2e_event_${suffix}_${RUN_ID}`,
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
}

const EH_LIST = makeEventHandler("list");
const EH_EDITOR = makeEventHandler("editor");
const EH_CRUD_NAME = `e2e_eh_crud_${RUN_ID}`;

test.beforeAll(async () => {
  await createEventHandler(EH_LIST);
  await createEventHandler(EH_EDITOR);
});

test.afterAll(async () => {
  await deleteEventHandler(EH_LIST.name).catch(() => {});
  await deleteEventHandler(EH_EDITOR.name).catch(() => {});
  await deleteEventHandler(EH_CRUD_NAME).catch(() => {});
});

// ── List / editor ──────────────────────────────────────────────────────────────

test("event handler appears in the /eventHandlerDef list", async ({ page }) => {
  await page.goto("/eventHandlerDef");
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#event-handler-list")).toBeVisible();
  await expect(page.getByText(EH_LIST.name)).toBeVisible();
});

test("event handler event string is shown in the list", async ({ page }) => {
  await page.goto("/eventHandlerDef");
  await page.waitForLoadState("networkidle");

  // Description is not in the default column set; event is.
  await expect(page.getByText(EH_LIST.event).first()).toBeVisible();
});

test("clicking an event handler opens the editor", async ({ page }) => {
  await page.goto("/eventHandlerDef");
  await page.waitForLoadState("networkidle");

  await page.locator("#main-content").getByText(EH_EDITOR.name).first().click();

  await expect(page).toHaveURL(
    new RegExp(`/eventHandlerDef/${EH_EDITOR.name}`),
  );
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#event-handler-form-wrapper")).toBeVisible();
  await expect(page.locator("#event-name-input")).toHaveValue(EH_EDITOR.name);
});

test("navigating to /newEventHandlerDef opens an empty form", async ({
  page,
}) => {
  await page.goto("/newEventHandlerDef");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/newEventHandlerDef/);
  await expect(page.locator("#event-handler-form-wrapper")).toBeVisible();
  await expect(page.locator("#event-name-input")).toHaveValue("");
  await expect(page.locator("#save-event-handler")).toBeVisible();
});

// ── Create / delete ────────────────────────────────────────────────────────────

test.describe("event handler create and delete", () => {
  test.describe.configure({ mode: "serial" });

  test("creates an event handler via the UI", async ({ page }) => {
    await page.goto("/newEventHandlerDef");
    await page.waitForLoadState("networkidle");

    await expect(page.locator("#event-handler-form-wrapper")).toBeVisible();

    await page.locator("#event-name-input").fill(EH_CRUD_NAME);
    await page
      .locator("#event-description-field")
      .fill("Created by Playwright E2E test — safe to delete");

    // Template already provides a default event string; leave it in place.
    await expect(page.locator("#save-event-handler")).toBeEnabled();
    await page.locator("#save-event-handler").click();

    await expect(page.locator("#confirm-save-event-handler")).toBeVisible();
    await page.locator("#confirm-save-event-handler").click();

    await expect(
      page.getByText("Event handler saved successfully."),
    ).toBeVisible({ timeout: 15_000 });

    await expect(page).toHaveURL(
      new RegExp(`/eventHandlerDef/${EH_CRUD_NAME}`),
      { timeout: 15_000 },
    );

    await page.goto("/eventHandlerDef");
    await page.waitForLoadState("networkidle");
    await page.locator("#quick-search-field").fill(EH_CRUD_NAME);
    await expect(
      page.locator("#main-content").getByText(EH_CRUD_NAME),
    ).toBeVisible();
  });

  test("deletes an event handler via the UI", async ({ page }) => {
    await page.goto("/eventHandlerDef");
    await page.waitForLoadState("networkidle");
    await page.locator("#quick-search-field").fill(EH_CRUD_NAME);
    await expect(
      page.locator("#main-content").getByText(EH_CRUD_NAME),
    ).toBeVisible();

    await page.locator(`#delete-${EH_CRUD_NAME}-btn`).click();
    await confirmDeleteByTyping(page, EH_CRUD_NAME);

    await expect(
      page.locator("#main-content").getByText(EH_CRUD_NAME),
    ).toHaveCount(0, { timeout: 15_000 });
  });
});
