/**
 * Workflow execution search — the "SQL format" toggle.
 *
 * Behavioural (not snapshot) coverage of switching between basic and SQL
 * search. Unit tests cover the clause translation itself; this covers the glue
 * in WorkflowSearch that the unit suite cannot reach, because two separate
 * react-router instances in the jsdom module graph make the page unrenderable
 * there.
 *
 * All /api/* calls are mocked, so no live backend is needed.
 */

import { expect, Page, test } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

// The start-time filter defaults to now - 72h; pin the clock so nothing drifts.
const FIXED_NOW = new Date("2026-06-01T12:00:00.000Z");

const WORKFLOW_NAME = "TestWorkflow-Aug";
const NAME_CLAUSE = `workflowType IN (${WORKFLOW_NAME})`;

const sqlToggle = (page: Page) => page.getByLabel("SQL format");
const basicNameField = (page: Page) =>
  page.locator("#workflow-search-name-dropdown");
const sqlEditor = (page: Page) => page.locator(".monaco-editor").first();
const discardDialog = (page: Page) => page.locator("#discard-sql-query-dialog");

/**
 * Loads /executions with the given query string, returning an array that
 * collects every workflow-search request URL the page issues.
 */
const gotoSearch = async (page: Page, search: string) => {
  await mockCommonApis(page);
  const searchUrls: string[] = [];
  // Registered after mockCommonApis so this handler wins.
  await page.route("**/api/workflow/search**", (route) => {
    searchUrls.push(decodeURIComponent(route.request().url()));
    return route.fulfill({ json: { results: [], totalHits: 0 } });
  });
  await page.clock.setFixedTime(FIXED_NOW);
  await page.addInitScript(() => {
    localStorage.setItem(
      "tooltipFlags",
      JSON.stringify({ executionSearch: true }),
    );
  });
  await page.goto(`/executions${search}`);
  await page.waitForLoadState("domcontentloaded");
  return searchUrls;
};

test.describe("Workflow execution search - SQL format toggle", () => {
  test("carries the workflow name filter into the SQL box and the request", async ({
    page,
  }) => {
    const searchUrls = await gotoSearch(page, `?workflowType=${WORKFLOW_NAME}`);
    await expect(basicNameField(page)).toBeVisible();

    await sqlToggle(page).click();

    await expect(sqlEditor(page)).toBeVisible();
    await expect(sqlEditor(page)).toContainText("workflowType");
    await expect(sqlEditor(page)).toContainText(WORKFLOW_NAME);

    await page.locator("#search-workflow-btn").click();

    await expect
      .poll(() => searchUrls.some((url) => url.includes(NAME_CLAUSE)))
      .toBe(true);
  });

  test("keeps a status chosen in the dropdown when switching back", async ({
    page,
  }) => {
    await gotoSearch(
      page,
      `?asQuery=true&status=FAILED&query=${encodeURIComponent(NAME_CLAUSE)}`,
    );
    await expect(sqlEditor(page)).toBeVisible();

    await sqlToggle(page).click();

    await expect(basicNameField(page)).toBeVisible();
    // The status dropdown is a live control in SQL format, so its value was
    // part of the search and must survive the switch.
    await expect(
      page.locator("#workflow-search-status").locator("xpath=.."),
    ).toContainText("Failed");
    // ...and the name comes back out of the query text.
    await expect(basicNameField(page).locator("xpath=..")).toContainText(
      WORKFLOW_NAME,
    );
  });

  test("leaves the fields alone when the SQL box was never edited", async ({
    page,
  }) => {
    await gotoSearch(page, `?asQuery=true&workflowType=${WORKFLOW_NAME}`);
    await expect(sqlEditor(page)).toBeVisible();

    await sqlToggle(page).click();

    await expect(basicNameField(page)).toBeVisible();
    await expect(basicNameField(page).locator("xpath=..")).toContainText(
      WORKFLOW_NAME,
    );
  });

  test("writes every parsed field back to its own filter", async ({ page }) => {
    // Guards the block of twelve setters in applyParsedFilters: a value routed
    // to the wrong field would land under the wrong url param here.
    const query = [
      `workflowType IN (${WORKFLOW_NAME})`,
      "workflowId='wf-abc'",
      "correlationId IN (corr-1)",
      "idempotencyKey IN (idem-1)",
      "modifiedTime>1000",
      "modifiedTime<2000",
      'parentWorkflowId=""',
    ].join(" AND ");

    await gotoSearch(page, `?asQuery=true&query=${encodeURIComponent(query)}`);
    await expect(sqlEditor(page)).toBeVisible();

    await sqlToggle(page).click();
    await expect(basicNameField(page)).toBeVisible();

    const url = decodeURIComponent(page.url());
    expect(url).toContain(`workflowType=${WORKFLOW_NAME}`);
    expect(url).toContain("workflowId=wf-abc");
    expect(url).toContain("correlationIds=corr-1");
    expect(url).toContain("idempotencyKey=idem-1");
    expect(url).toContain("modifiedFrom=1000");
    expect(url).toContain("modifiedTo=2000");
    expect(url).toContain("excludeSubExecutions=true");
    // The query itself is consumed, not left behind.
    expect(url).not.toContain("query=");

    // And the values are visible in the fields, not just the url.
    await expect(page.locator("#workflow-search-id")).toHaveValue("wf-abc");
    await expect(
      page.locator("#workflow-search-correlation-id").locator("xpath=../.."),
    ).toContainText("corr-1");
    await expect(
      page.locator("#workflow-search-idempotency-key").locator("xpath=../.."),
    ).toContainText("idem-1");
  });

  test("asks before discarding a query basic search cannot express", async ({
    page,
  }) => {
    await gotoSearch(
      page,
      `?asQuery=true&query=${encodeURIComponent("taskType='HTTP'")}`,
    );

    await sqlToggle(page).click();

    await expect(discardDialog(page)).toBeVisible();
    // Still in SQL format until the choice is made.
    await expect(sqlEditor(page)).toBeVisible();
  });

  test("keeps the query when the prompt is cancelled", async ({ page }) => {
    await gotoSearch(
      page,
      `?asQuery=true&query=${encodeURIComponent("taskType='HTTP'")}`,
    );
    await sqlToggle(page).click();

    await page.locator("#choice-dialog-cancel-btn").click();

    await expect(discardDialog(page)).toBeHidden();
    await expect(sqlEditor(page)).toBeVisible();
    expect(decodeURIComponent(page.url())).toContain("taskType");
  });

  test("discards the query and clears it from the url", async ({ page }) => {
    await gotoSearch(
      page,
      `?asQuery=true&query=${encodeURIComponent("taskType='HTTP'")}`,
    );
    await sqlToggle(page).click();

    await page.locator("#choice-dialog-confirm-btn").click();

    await expect(basicNameField(page)).toBeVisible();
    expect(decodeURIComponent(page.url())).not.toContain("taskType");
  });
});
