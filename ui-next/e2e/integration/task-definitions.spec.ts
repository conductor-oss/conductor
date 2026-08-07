/**
 * Integration tests — Task Definitions
 *
 * Creates real task definitions via the API and verifies the UI shows them
 * correctly in the list and in the per-task editor.
 */

import { expect, test } from "../coverage-fixture";
import { createTaskDef, deleteTaskDef, type TaskDef } from "./api-client";

const RUN_ID = Date.now();

function makeTaskDef(suffix: string): TaskDef {
  return {
    name: `e2e_task_${suffix}_${RUN_ID}`,
    description: "Created by Playwright E2E test — safe to delete",
    retryCount: 0,
    inputKeys: ["input_value"],
    outputKeys: ["output_value"],
  };
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const TASK_LIST = makeTaskDef("list");
const TASK_EDITOR = makeTaskDef("editor");

test.beforeAll(async () => {
  await createTaskDef(TASK_LIST);
  await createTaskDef(TASK_EDITOR);
});

test.afterAll(async () => {
  await deleteTaskDef(TASK_LIST.name).catch(() => {});
  await deleteTaskDef(TASK_EDITOR.name).catch(() => {});
});

// ── Tests ─────────────────────────────────────────────────────────────────────

test("task definition list shows correct column headers", async ({ page }) => {
  await page.goto("/taskDef");
  await page.waitForLoadState("networkidle");

  // The DataTable renders many columns in a horizontally scrollable container.
  // Only the first few (Task name, Executable?, Description) are guaranteed to
  // be in the visible viewport without scrolling.
  await expect(page.getByText("Task name").first()).toBeVisible();
  await expect(page.getByText("Executable?").first()).toBeVisible();
  await expect(page.getByText("Description").first()).toBeVisible();
});

test("task definition appears in the /taskDef list with expected row data", async ({
  page,
}) => {
  await page.goto("/taskDef");
  await page.waitForLoadState("networkidle");

  // Name column: rendered as a NavLink.
  await expect(
    page.getByRole("link", { name: TASK_LIST.name }),
  ).toBeVisible();

  // Description column: exact value used when creating the task.
  await expect(
    page.getByText("Created by Playwright E2E test — safe to delete").first(),
  ).toBeVisible();

  // Input keys and Output keys columns are further right and may be
  // off-screen without horizontal scrolling — verified in the editor form
  // tests instead.
});

test("clicking a task definition opens the task editor", async ({ page }) => {
  await page.goto("/taskDef");
  await page.waitForLoadState("networkidle");

  await page
    .locator("#main-content")
    .getByText(TASK_EDITOR.name)
    .first()
    .click();

  await expect(page).toHaveURL(new RegExp(`/taskDef/${TASK_EDITOR.name}`));
  await expect(page.locator("#main-content")).toBeVisible();

  // Page header shows the task name as the section title.
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible();
});

test("task editor form tab shows field values matching the API definition", async ({
  page,
}) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible({
    timeout: 15_000,
  });

  // Switch to Task (form) tab if not already there.
  await page.getByRole("tab", { name: "Task" }).click();
  await expect(page.locator("#task-form-container")).toBeVisible({
    timeout: 15_000,
  });

  // ── Basic settings ─────────────────────────────────────────────────────────
  await expect(page.getByText("Basic settings").first()).toBeVisible();

  // Name field: MUI TextField puts the id on the <input> element.
  await expect(page.locator("#task-name-field")).toHaveValue(TASK_EDITOR.name);

  // Description field: multiline → <textarea> element.
  await expect(page.locator("#task-description-field")).toHaveValue(
    "Created by Playwright E2E test — safe to delete",
  );

  // ── Rate limit settings ────────────────────────────────────────────────────
  await expect(page.getByText("Rate limit settings").first()).toBeVisible();
  await expect(
    page.locator("#task-rateLimitPerFrequency-field"),
  ).toBeVisible();
  await expect(
    page.locator("#task-rateLimitFrequencyInSeconds-field"),
  ).toBeVisible();
  await expect(
    page.locator("#task-concurrentExecLimit-field"),
  ).toBeVisible();

  // ── Retry settings ─────────────────────────────────────────────────────────
  await expect(page.getByText("Retry settings").first()).toBeVisible();
  // retryCount was set to 0.
  await expect(page.locator("#task-retryCount-field")).toHaveValue("0");
  await expect(
    page.getByText("No. of times to retry the task upon failure?").first(),
  ).toBeVisible();
  await expect(
    page.getByText("Delay between retries in seconds").first(),
  ).toBeVisible();
  await expect(page.getByText("Retry policy").first()).toBeVisible();

  // ── Timeout settings ───────────────────────────────────────────────────────
  await expect(page.getByText("Timeout settings").first()).toBeVisible();
  await expect(
    page.getByText("Response Timeout Seconds").first(),
  ).toBeVisible();
  await expect(page.getByText("Timeout Seconds").first()).toBeVisible();
  await expect(page.getByText("Poll Timeout Seconds").first()).toBeVisible();
  await expect(page.getByText("Timeout policy").first()).toBeVisible();

  // ── Input / Output keys ────────────────────────────────────────────────────
  await expect(page.getByText("Input keys:").first()).toBeVisible();
  // ConductorArrayFieldBase renders each key as a ConductorInput (<input>),
  // not a text node.  Check the DOM .value property via evaluate since
  // Playwright's getByDisplayValue is not available in this version.
  await expect
    .poll(
      () =>
        page.evaluate(() =>
          Array.from(
            document.querySelectorAll("#task-form-container input"),
          ).some((el) => (el as HTMLInputElement).value === "input_value"),
        ),
      { timeout: 10_000 },
    )
    .toBe(true);

  await expect(page.getByText("Output keys:").first()).toBeVisible();
  await expect
    .poll(
      () =>
        page.evaluate(() =>
          Array.from(
            document.querySelectorAll("#task-form-container input"),
          ).some((el) => (el as HTMLInputElement).value === "output_value"),
        ),
      { timeout: 10_000 },
    )
    .toBe(true);
});

test("task editor header buttons are present", async ({ page }) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible({
    timeout: 15_000,
  });

  // Delete, Reset, Download, and Save buttons are always rendered for an existing task.
  await expect(page.locator("#task-delete-btn")).toBeVisible();
  await expect(page.locator("#task-reset-btn")).toBeVisible();
  await expect(page.locator("#task-download-btn")).toBeVisible();
  await expect(page.locator("#task-save-btn")).toBeVisible();

  // Reset and Save are disabled until the form is dirty — their default state.
  await expect(page.locator("#task-reset-btn")).toBeDisabled();
  await expect(page.locator("#task-save-btn")).toBeDisabled();
});

test("task editor Code tab shows task definition JSON", async ({ page }) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Code" }).click();
  await expect(page.locator(".monaco-editor").first()).toBeVisible({
    timeout: 15_000,
  });

  // Monaco (DiffEditor) virtualizes off-screen lines — only the first visible
  // lines are in the DOM.  Query the Monaco model directly to verify the full
  // JSON content regardless of scroll position.
  await expect
    .poll(
      async () => {
        const text = await page.evaluate(() => {
          const monaco = (window as { monaco?: { editor: { getModels(): Array<{ getValue(): string }> } } }).monaco;
          return (
            monaco?.editor
              .getModels()
              .map((m) => m.getValue())
              .join("\n") ?? ""
          );
        });
        return text;
      },
      { timeout: 15_000 },
    )
    .toContain(TASK_EDITOR.name);

  const codeContent = await page.evaluate(() => {
    const monaco = (window as { monaco?: { editor: { getModels(): Array<{ getValue(): string }> } } }).monaco;
    return (
      monaco?.editor
        .getModels()
        .map((m) => m.getValue())
        .join("\n") ?? ""
    );
  });
  expect(codeContent).toContain("Created by Playwright E2E test");
  expect(codeContent).toContain("inputKeys");
  expect(codeContent).toContain("input_value");
  expect(codeContent).toContain("outputKeys");
  expect(codeContent).toContain("output_value");
  expect(codeContent).toContain("retryCount");
});

test("task editor Reset dialog opens on a dirty form", async ({ page }) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Task" }).click();
  await expect(page.locator("#task-form-container")).toBeVisible({
    timeout: 15_000,
  });

  // Dirty the form: fill the description textarea (always present).
  const description = page.locator("#task-description-field");
  await expect(description).toBeVisible({ timeout: 15_000 });
  await description.fill("e2e dirty for reset test");

  // Reset enables only once the form is dirty.
  const resetBtn = page.locator("#task-reset-btn");
  await expect(resetBtn).toBeEnabled({ timeout: 15_000 });
  await resetBtn.click();

  // Confirmation dialog appears.
  await expect(
    page.getByText(/Resetting Confirmation/i).first(),
  ).toBeVisible({ timeout: 15_000 });
  await page.keyboard.press("Escape");

  // After dismissing, description field retains the dirty value (reset was cancelled).
  await expect(description).toHaveValue("e2e dirty for reset test");
});

test("task editor Test Task button opens the test panel", async ({ page }) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.locator("#test-test-button").click();
  await expect(
    page.getByText(/Test Task|Task Input|Execute/i).first(),
  ).toBeVisible({ timeout: 15_000 });
});

test("navigating directly to a task definition URL opens the editor", async ({
  page,
}) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible();

  // Breadcrumb shows "Task Definitions" link back to the list.
  await expect(
    page.getByRole("link", { name: "Task Definitions" }).first(),
  ).toBeVisible();

  // "Task docs" doc link is rendered next to the tabs.
  await expect(page.getByText("Task docs").first()).toBeVisible({
    timeout: 15_000,
  });
});

test("navigating to /newTaskDef opens the new task form", async ({ page }) => {
  await page.goto("/newTaskDef");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/newTaskDef/);
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#task-form-container")).toBeVisible();

  // New Task page header.
  await expect(page.getByText("New Task").first()).toBeVisible();

  // Name field is pre-populated with a generated name (task-XXXXXX pattern).
  const nameValue = await page.locator("#task-name-field").inputValue();
  expect(nameValue).toMatch(/^task-/);

  // Save button is present (split button for new task).
  await expect(page.locator("#task-save-btn")).toBeVisible();
  // Reset is disabled on a fresh new-task form (nothing to reset).
  await expect(page.locator("#task-reset-btn")).toBeDisabled();
});
