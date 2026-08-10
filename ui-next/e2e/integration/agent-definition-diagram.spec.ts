/**
 * Integration tests — Agent Definition diagram nesting
 *
 * Deploys a multi-level agent (sequential → parallel → leaves) and opens
 * /agents/:name. Asserts the Diagram tab renders nested grandchildren, not
 * only the root's direct children, and matches a visual snapshot of the tree.
 *
 * Requires conductor.integrations.ai.enabled=true on the ui-e2e server.
 * Does not start an execution or need OPENAI_API_KEY.
 *
 * Snapshot baselines live under e2e/integration/__snapshots__/ and are
 * platform-specific (`{platform}` in the path — commit both darwin + linux).
 * Update locally with:
 *   pnpm test:e2e:integration -- --update-snapshots=all e2e/integration/agent-definition-diagram.spec.ts
 */

import { expect, test } from "../coverage-fixture";
import { deleteAgent, deployAgent, isAgentApiAvailable } from "./api-client";

// Stable names so the diagram snapshot is deterministic across runs.
const ROOT = "e2e_def_nest_root";
const CHILD_A = "e2e_def_nest_a";
const CHILD_PAR = "e2e_def_nest_par";
const GRAND_B = "e2e_def_nest_b";
const GRAND_C = "e2e_def_nest_c";
const CHILD_E = "e2e_def_nest_e";

const DEFAULT_MODEL = "openai/gpt-4o-mini";

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
  await deployAgent(ROOT, {
    model: DEFAULT_MODEL,
    strategy: "sequential",
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
  });
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

  const diagram = page.getByTestId("agent-definition-diagram");
  await expect(diagram).toBeVisible({ timeout: 15_000 });

  // Direct children (always drawn, even before the nesting fix).
  await expect(page.getByText(CHILD_A, { exact: true })).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(CHILD_PAR, { exact: true })).toBeVisible();
  await expect(page.getByText(CHILD_E, { exact: true })).toBeVisible();

  // Grandchildren under the parallel coordinator — these were missing when
  // buildDefDiagram only walked the root's agents[] once.
  await expect(page.getByText(GRAND_B, { exact: true })).toBeVisible();
  await expect(page.getByText(GRAND_C, { exact: true })).toBeVisible();

  // Zoom controls only mount after reaflow layout — wait so the snapshot is not
  // the loading skeleton, then fit so nested leaves are in frame.
  await expect(
    diagram.getByRole("button", { name: "Fit to screen" }),
  ).toBeVisible({ timeout: 15_000 });
  await diagram.getByRole("button", { name: "Fit to screen" }).click();
  // Dismiss the Fit tooltip so it is not baked into the baseline.
  await page.mouse.move(0, 0);
  await expect(page.getByRole("tooltip")).toHaveCount(0);
  await expect(diagram).toHaveScreenshot("agent-definition-nested-diagram.png");
});
