/**
 * Integration tests — Run Agent
 *
 * Deploys a real agent via POST /api/agent/deploy, then exercises the
 * /runAgent page: list → preselect → start. Asserts the UI posts
 * /api/agent/start and surfaces the returned execution id.
 *
 * A second test (skipped without OPENAI_API_KEY) waits for the execution to
 * complete and checks API status/output plus the agent execution detail page.
 *
 * Requires conductor.integrations.ai.enabled=true on the ui-e2e server
 * (application.properties default) so /api/agent/* is registered.
 */

import { expect, test } from "../coverage-fixture";
import {
  deleteAgent,
  deployAgent,
  isAgentApiAvailable,
  waitForAgentExecution,
} from "./api-client";

const RUN_ID = Date.now();
const AGENT_NAME = `e2e_run_agent_${RUN_ID}`;
const RUN_AGENT_URL = "/runAgent";
const AGENT_DEFINITION_URL = "/agents";
const AGENT_EXECUTIONS_URL = "/agentExecutions";

const HAS_OPENAI = Boolean(process.env.OPENAI_API_KEY?.trim());
const OPENAI_SKIP_REASON =
  "OPENAI_API_KEY is required to wait for a real agent LLM completion";
const AGENT_EXECUTION_TIMEOUT_MS = 180_000;

test.beforeAll(async () => {
  const available = await isAgentApiAvailable();
  if (!available) {
    throw new Error(
      "GET /api/agent/list is not available. " +
        "Ensure the ui-e2e Conductor server has conductor.integrations.ai.enabled=true " +
        "(application.properties default; config-postgres.properties must not override it).",
    );
  }
  await deployAgent(AGENT_NAME, {
    model: "openai/gpt-4o-mini",
    instructions:
      "You are a concise test agent. Reply with exactly the single word ok and nothing else.",
    maxTurns: 1,
  });
});

test.afterAll(async () => {
  await deleteAgent(AGENT_NAME).catch(() => {});
});

async function startAgentFromUi(
  page: import("@playwright/test").Page,
  prompt: string,
) {
  await page.goto(AGENT_DEFINITION_URL);
  await page.waitForLoadState("networkidle");

  const agentLink = page.locator(`#${AGENT_NAME}-link-btn`);
  await expect(agentLink).toBeVisible({ timeout: 15_000 });

  await page.locator(`#run-${AGENT_NAME}-btn`).click();
  await expect(page).toHaveURL(new RegExp(RUN_AGENT_URL));
  await expect(page.locator("#run-agent-name")).toHaveValue(AGENT_NAME);

  await page.locator("#run-agent-prompt").fill(prompt);

  const startResponsePromise = page.waitForResponse(
    (response) =>
      response.url().includes("/api/agent/start") &&
      response.request().method() === "POST",
  );

  await page.locator("#run-agent-btn").click();

  const startResponse = await startResponsePromise;
  expect(startResponse.ok()).toBeTruthy();
  const body = (await startResponse.json()) as {
    executionId?: string;
    agentName?: string;
  };
  expect(body.executionId).toBeTruthy();
  expect(body.agentName).toBe(AGENT_NAME);

  await expect(
    page.getByText("Agent execution started:", { exact: false }),
  ).toBeVisible();
  await expect(
    page
      .locator(`a[href="${AGENT_EXECUTIONS_URL}/${body.executionId}"]`)
      .filter({ hasText: body.executionId! }),
  ).toBeVisible();

  return body.executionId!;
}

test("lists the deployed agent and runs it from the Run Agent page", async ({
  page,
}) => {
  await startAgentFromUi(page, "Reply with the word ok.");
});

test("agent execution completes and results are visible", async ({ page }) => {
  test.skip(!HAS_OPENAI, OPENAI_SKIP_REASON);
  test.setTimeout(AGENT_EXECUTION_TIMEOUT_MS + 60_000);

  const executionId = await startAgentFromUi(
    page,
    "Reply with exactly the single word ok.",
  );

  const status = await waitForAgentExecution(executionId, {
    timeoutMs: AGENT_EXECUTION_TIMEOUT_MS,
  });

  expect(status.status).toBe("COMPLETED");
  expect(status.isComplete).toBe(true);

  // Prefer structural proof — wording can vary slightly across models.
  const resultText = String(status.output?.result ?? "").trim();
  expect(resultText.length).toBeGreaterThan(0);
  expect(resultText.toLowerCase()).toMatch(/ok/);

  await page.goto(`${AGENT_EXECUTIONS_URL}/${executionId}`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(AGENT_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  // Status chips render title case ("Completed").
  await expect(
    page
      .locator(".MuiChip-label")
      .filter({ hasText: /^Completed$/ })
      .first(),
  ).toBeVisible({ timeout: 15_000 });

  // Agent executions default to the Agent Execution tab (no workflow right
  // panel). Input/Output exposes the submitted prompt and final agent result.
  await page.getByRole("tab", { name: "Input/Output" }).click();
  await expect(page.getByText(/ok/i).first()).toBeVisible({
    timeout: 15_000,
  });
});
