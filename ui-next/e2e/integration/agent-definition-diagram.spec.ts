/**
 * Integration tests — nested agent diagrams (definition + execution)
 *
 * Deploys a multi-level agent (sequential → parallel → leaves), asserts the
 * definition Diagram tab renders nested grandchildren, then starts an
 * execution and asserts the same nesting on the execution page Agent
 * Definition tab (uses agentDef metadata — no OpenAI required).
 *
 * Execution-diagram nesting + COMPLETED status are covered by the unit test
 * AgentExecutionDiagram.nesting.test.ts (fixture AgentRunData, no LLM).
 *
 * Requires conductor.integrations.ai.enabled=true on the ui-e2e server.
 *
 * Snapshot baselines live under e2e/integration/__snapshots__/ (platform-
 * specific). Update with:
 *   pnpm test:e2e:integration -- --update-snapshots=all e2e/integration/agent-definition-diagram.spec.ts
 */

import { expect, test } from "../coverage-fixture";
import {
  deleteAgent,
  deployAgent,
  isAgentApiAvailable,
  startAgent,
} from "./api-client";

const ROOT = "e2e_def_nest_root";
const CHILD_A = "e2e_def_nest_a";
const CHILD_PAR = "e2e_def_nest_par";
const GRAND_B = "e2e_def_nest_b";
const GRAND_C = "e2e_def_nest_c";
const CHILD_E = "e2e_def_nest_e";

const DEFAULT_MODEL = "openai/gpt-4o-mini";
const AGENT_EXECUTIONS_URL = "/agentExecutions";

const NESTED_DEPLOY = {
  model: DEFAULT_MODEL,
  strategy: "sequential" as const,
  synthesize: false,
  instructions: "Root sequential coordinator for diagram nesting e2e.",
  maxTurns: 1,
  agents: [
    {
      name: CHILD_A,
      model: DEFAULT_MODEL,
      instructions: "classify",
    },
    {
      name: CHILD_PAR,
      model: DEFAULT_MODEL,
      strategy: "parallel",
      synthesize: false,
      instructions: "parallel branch",
      agents: [
        {
          name: GRAND_B,
          model: DEFAULT_MODEL,
          instructions: "infra cause",
        },
        {
          name: GRAND_C,
          model: DEFAULT_MODEL,
          instructions: "code cause",
        },
      ],
    },
    {
      name: CHILD_E,
      model: DEFAULT_MODEL,
      instructions: "postmortem",
    },
  ],
};

async function assertNestedDefinitionDiagram(
  page: import("@playwright/test").Page,
  snapshotName: string,
) {
  const diagram = page.getByTestId("agent-definition-diagram");
  await expect(diagram).toBeVisible({ timeout: 15_000 });

  await expect(page.getByText(CHILD_A, { exact: true })).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(CHILD_PAR, { exact: true })).toBeVisible();
  await expect(page.getByText(CHILD_E, { exact: true })).toBeVisible();
  await expect(page.getByText(GRAND_B, { exact: true })).toBeVisible();
  await expect(page.getByText(GRAND_C, { exact: true })).toBeVisible();

  await expect(
    diagram.getByRole("button", { name: "Fit to screen" }),
  ).toBeVisible({ timeout: 15_000 });
  await diagram.getByRole("button", { name: "Fit to screen" }).click();
  await page.mouse.move(0, 0);
  await expect(page.getByRole("tooltip")).toHaveCount(0);
  await expect(diagram).toHaveScreenshot(snapshotName);
}

test.beforeAll(async () => {
  const available = await isAgentApiAvailable();
  if (!available) {
    throw new Error(
      "GET /api/agent/list is not available. " +
        "Ensure the ui-e2e Conductor server has conductor.integrations.ai.enabled=true " +
        "(application.properties default; config-postgres.properties must not override it).",
    );
  }

  await deleteAgent(ROOT).catch(() => {});
  await deployAgent(ROOT, NESTED_DEPLOY);
});

test.afterAll(async () => {
  await deleteAgent(ROOT).catch(() => {});
});

test("agent definition diagram shows nested parallel grandchildren", async ({
  page,
}) => {
  await page.goto(`/agents/${encodeURIComponent(ROOT)}`);
  await page.waitForLoadState("networkidle");

  await expect(page.getByRole("tab", { name: "Diagram" })).toBeVisible({
    timeout: 15_000,
  });

  await assertNestedDefinitionDiagram(
    page,
    "agent-definition-nested-diagram.png",
  );
});

test("execution page Agent Definition tab shows the same nested tree", async ({
  page,
}) => {
  const { executionId } = await startAgent(
    ROOT,
    "Classify then investigate in parallel, then write a short postmortem.",
  );

  await page.goto(`${AGENT_EXECUTIONS_URL}/${executionId}`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(ROOT).first()).toBeVisible({ timeout: 15_000 });

  await page.getByRole("tab", { name: "Agent Definition" }).click();
  await assertNestedDefinitionDiagram(
    page,
    "agent-execution-definition-nested-diagram.png",
  );
});
