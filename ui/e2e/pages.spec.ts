/**
 * Page load smoke tests — verifies every main route renders without crashing
 * and shows its expected heading/content.
 */
import { expect, test } from "@playwright/test";
import {
  f,
  mockCommonApis,
  mockSchedulerApis,
  mockTaskSearch,
  mockWorkflowSearch,
} from "./helpers/mockApi";

test.describe("Page load smoke tests", () => {
  // ── Executions ──────────────────────────────────────────────────────────────

  test("Workflow Executions — home page loads", async ({ page }) => {
    await mockCommonApis(page);
    await mockWorkflowSearch(page);
    await page.goto("/");
    await expect(page.getByText("Search Executions")).toBeVisible();
    await expect(page.getByText("Page 1 of 1")).toBeVisible();
  });

  test("Workflow Executions — /executions route loads", async ({ page }) => {
    await mockCommonApis(page);
    await mockWorkflowSearch(page);
    await page.goto("/executions");
    await expect(page.getByText("Search Executions")).toBeVisible();
  });

  test("Task Executions — /search/tasks loads", async ({ page }) => {
    await mockCommonApis(page);
    await mockTaskSearch(page);
    await page.goto("/search/tasks");
    await expect(page.getByText("Search Executions")).toBeVisible();
    await expect(page.getByText("Task Name")).toBeVisible();
    await expect(
      page.getByText("There are no records to display")
    ).toBeVisible();
  });

  // ── Definitions ─────────────────────────────────────────────────────────────

  test("Workflow Definitions — /workflowDefs loads", async ({ page }) => {
    await mockCommonApis(page);
    await page.route("**/api/metadata/workflow", (route) =>
      route.fulfill({ path: f("metadataWorkflow.json") })
    );
    await page.goto("/workflowDefs");
    await expect(
      page.getByRole("heading", { name: "Definitions" })
    ).toBeVisible();
    await expect(
      page.getByRole("tab", { name: "Workflows" }).first()
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /New Workflow Definition/i })
    ).toBeVisible();
  });

  test("Task Definitions — /taskDefs loads", async ({ page }) => {
    await mockCommonApis(page);
    await page.goto("/taskDefs");
    await expect(
      page.getByRole("heading", { name: "Definitions" })
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /New Task Definition/i })
    ).toBeVisible();
  });

  test("Event Handler Definitions — /eventHandlerDefs loads", async ({
    page,
  }) => {
    await mockCommonApis(page);
    await page.route("**/api/event**", (route) =>
      route.fulfill({ path: f("eventHandlers.json") })
    );
    await page.goto("/eventHandlerDefs");
    await expect(
      page.getByRole("heading", { name: "Definitions" })
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /New Event Handler Definition/i })
    ).toBeVisible();
  });

  test("Scheduler Definitions — /schedulerDefs loads", async ({ page }) => {
    await mockCommonApis(page);
    await mockSchedulerApis(page);
    await page.goto("/schedulerDefs");
    await expect(
      page.getByRole("heading", { name: "Definitions" })
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /New Schedule/i })
    ).toBeVisible();
  });

  // ── Scheduler Executions ─────────────────────────────────────────────────────

  test("Scheduler Executions — /schedulerExecs loads", async ({ page }) => {
    await mockCommonApis(page);
    // mockSchedulerApis mocks both /schedules (needed by ScheduleNameInput) and
    // /search/executions. Without the schedules mock, serve -s returns index.html,
    // causing data.map() to throw and crash the component before the heading renders.
    await mockSchedulerApis(page);
    await page.goto("/schedulerExecs");
    await expect(
      page.getByRole("heading", { name: "Scheduler Executions" })
    ).toBeVisible();
    await expect(
      page.getByRole("columnheader", { name: "Schedule Name" })
    ).toBeVisible();
  });

  // ── Task Queue ───────────────────────────────────────────────────────────────

  test("Task Queues — /taskQueue loads", async ({ page }) => {
    await mockCommonApis(page);
    await page.goto("/taskQueue");
    await expect(
      page.getByRole("heading", { name: "Task Queues" })
    ).toBeVisible();
    await expect(page.getByText(/Select a Task Name/i)).toBeVisible();
  });

  test("Task Queues — poll data loads after selecting task", async ({
    page,
  }) => {
    await mockCommonApis(page);
    await page.route("**/api/tasks/queue/polldata**", (route) =>
      route.fulfill({ path: f("taskPollData.json") })
    );
    // Each /queue/size request is per-domain and expects a plain number in response.
    // Returning an object would crash the queueSize column renderer (React child error).
    await page.route("**/api/tasks/queue/size**", (route) =>
      route.fulfill({ body: "5", contentType: "application/json" })
    );
    await page.goto("/taskQueue");
    await page.locator(".MuiAutocomplete-inputRoot input").first().click();
    await page
      .locator("li.MuiAutocomplete-option")
      .filter({ hasText: "example_task_1" })
      .click();
    // Wait for the client-side navigation to /taskQueue/example_task_1 to settle
    // before asserting on the poll data table.
    await page.waitForURL("**/taskQueue/example_task_1");
    await expect(page.getByText("Poll Status by Domain")).toBeVisible();
    await expect(
      page.locator(".rdt_TableCell").filter({ hasText: "DEFAULT" }).first()
    ).toBeVisible();
  });

  // ── Workbench ────────────────────────────────────────────────────────────────

  test("Workbench — /workbench loads", async ({ page }) => {
    await mockCommonApis(page);
    await page.goto("/workbench");
    await expect(page.getByText("Workflow Workbench")).toBeVisible();
    await expect(page.getByText("Workflow Name")).toBeVisible();
  });

  test("Integrations route loads", async ({ page }) => {
    await mockCommonApis(page);
    await page.goto("/integrations");
    await expect(
      page.getByRole("heading", { name: "Integrations" })
    ).toBeVisible();
    await expect(page.getByText("Google Drive Read")).toBeVisible();
    await expect(page.getByText("read_g_drive")).toBeVisible();
    await expect(page.getByText("GDRIVE_READ")).toBeVisible();
    await expect(page.getByLabel("OAuth Client ID")).toBeVisible();
    await expect(page.getByLabel("OAuth Client Secret")).toBeVisible();
    await expect(page.getByText("Upload Client JSON")).toBeVisible();
    await expect(page.getByText("Upload OAuth Token JSON")).toBeVisible();
    await expect(page.getByText("Generate OAuth")).toBeVisible();
    await expect(page.getByText("Create Connection")).toBeVisible();
  });

  test("Integrations OAuth callback saves connection and creates Drive task", async ({
    page,
  }) => {
    await mockCommonApis(page);

    let createdTaskDefs: Array<{ inputKeys?: string[]; name?: string }> | undefined;
    await page.route("**/api/integrations/gdrive/oauth/token", (route) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          connectionId: "gdrive-prod",
        }),
      })
    );
    await page.route("**/api/metadata/taskdefs/read_g_drive", (route) =>
      route.fulfill({ status: 404, body: "Not found" })
    );
    await page.route("**/api/metadata/taskdefs", async (route) => {
      if (route.request().method() === "POST") {
        createdTaskDefs = route.request().postDataJSON();
        await route.fulfill({ status: 204, body: "" });
      } else {
        await route.fulfill({ path: f("metadataTasks.json") });
      }
    });

    await page.addInitScript(() => {
      window.sessionStorage.setItem(
        "conductor.gdrive.oauth",
        JSON.stringify({
          connectionId: "gdrive-prod",
          clientId: "client-id",
          clientSecret: "client-secret",
          oauthClientJson: JSON.stringify({
            installed: {
              client_id: "client-id",
              client_secret: "client-secret",
            },
          }),
        })
      );
    });

    await page.goto("/integrations?code=auth-code&state=gdrive-prod");

    await expect(
      page.getByText("Google Drive connection gdrive-prod saved.")
    ).toBeVisible();
    expect(createdTaskDefs?.[0]?.name).toBe("read_g_drive");
    expect(createdTaskDefs?.[0]?.inputKeys).toContain("connectionId");
    expect(createdTaskDefs?.[0]?.inputKeys).toContain("folderIds");
    expect(createdTaskDefs?.[0]?.inputKeys).toContain("fileIds");
  });
});
