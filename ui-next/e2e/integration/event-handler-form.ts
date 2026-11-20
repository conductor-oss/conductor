/**
 * Shared Playwright helpers for driving the Event Handler form
 * (`/newEventHandlerDef`, `/eventHandlerDef/:name`).
 */

import type { Locator, Page } from "@playwright/test";
import { expect } from "../coverage-fixture";
import { getEventHandlerByName, type EventHandlerDef } from "./api-client";

export const section = (page: Page, key: string) =>
  page.locator(`#event-handler-section-${key}`);

export const actionCard = (page: Page, index = 0) =>
  page.locator(`#event-handler-action-${index}`);

/** Opens a new handler and fills the fields every test needs. */
export async function startNewHandler(page: Page, name: string, event: string) {
  await page.goto("/newEventHandlerDef");
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#event-handler-form-wrapper")).toBeVisible();

  await page.locator("#event-name-input").fill(name);
  await page
    .locator("#event-description-field")
    .fill("Created by Playwright E2E test — safe to delete");
  await page.locator("#event-string-input").fill(event);

  // New handlers start inactive until the user turns them on. Turn it on so
  // the server listens on the handler's queue; tests that want an inactive
  // handler uncheck it again.
  const active = section(page, "details").getByLabel("Active");
  await expect(active).not.toBeChecked();
  await active.check();
}

/** Adds an action from the Add action menu. New actions land at index 0. */
export async function addAction(page: Page, label: string) {
  await section(page, "actions")
    .getByRole("button", { name: /Add action/i })
    .click();
  await page.getByRole("menuitem", { name: new RegExp(`^${label}`) }).click();
}

/**
 * The new-handler template starts with a Complete Task action. Add `label`
 * from the menu (it is prepended, so it lands at index 0), then remove the
 * template action that moved to index 1.
 */
export async function replaceTemplateAction(page: Page, label: string) {
  await addAction(page, label);
  await actionCard(page, 1).getByLabel("Remove action").click();
  await expect(actionCard(page, 1)).toHaveCount(0);
}

/**
 * Replaces the Condition editor's text. Monaco has no fillable input, and
 * select-all shortcuts don't reach its EditContext under Playwright on macOS,
 * so go through the editor API — setValue still fires the form's onChange.
 */
export async function setCondition(page: Page, condition: string) {
  const editor = section(page, "condition").locator(".monaco-editor").first();
  await expect(editor).toBeVisible();
  await editor.evaluate((node, value) => {
    const monaco = (
      window as unknown as {
        monaco?: {
          editor: {
            getEditors(): Array<{
              getDomNode(): HTMLElement | null;
              setValue(v: string): void;
            }>;
          };
        };
      }
    ).monaco;
    const target = monaco?.editor
      .getEditors()
      .find(
        (e) => e.getDomNode()?.contains(node) || node.contains(e.getDomNode()!),
      );
    if (!target) throw new Error("Condition Monaco editor not found");
    target.setValue(value);
  }, condition);
}

/** Adds one key/value row to the key/value form titled `title`. */
export async function addKeyValue(
  card: Locator,
  title: string,
  key: string,
  value: string,
) {
  await card.getByRole("button", { name: "Add parameter" }).first().click();
  const keyInput = card.getByLabel("Key", { exact: true }).last();
  await keyInput.fill(key);
  await card.getByLabel("Value", { exact: true }).last().fill(value);
  // Group title only anchors intent in failure output.
  await expect(card.getByText(title).first()).toBeVisible();
}

/** Save → confirm diff → wait for the success toast and saved URL. */
export async function saveHandler(page: Page, name: string) {
  await expect(page.locator("#save-event-handler")).toBeEnabled();
  await page.locator("#save-event-handler").click();
  await expect(page.locator("#confirm-save-event-handler")).toBeVisible();
  await page.locator("#confirm-save-event-handler").click();
  await expect(page.getByText("Event handler saved successfully.")).toBeVisible(
    { timeout: 15_000 },
  );
  await expect(page).toHaveURL(
    new RegExp(`/eventHandlerDef/${encodeURIComponent(name)}`),
    { timeout: 15_000 },
  );
}

export async function fetchSaved(name: string): Promise<EventHandlerDef> {
  const saved = await getEventHandlerByName(name);
  expect(saved, `handler ${name} should exist on the server`).toBeDefined();
  return saved!;
}

/** Reloads the saved handler so the form is populated from the server. */
export async function reopen(page: Page, name: string) {
  await page.goto(`/eventHandlerDef/${encodeURIComponent(name)}`);
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#event-name-input")).toHaveValue(name);
}
