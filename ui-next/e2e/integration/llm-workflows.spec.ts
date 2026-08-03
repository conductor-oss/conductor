/**
 * Integration tests — Workflow Definitions + Executions with LLM Tasks
 *
 * Creates workflow definitions containing various LLM/AI task types via the
 * API, verifies the UI renders them correctly, and starts real executions of
 * LLM_CHAT_COMPLETE / LLM_TEXT_COMPLETE.
 *
 * OSS LLM chat/text forms use plain Instructions / Prompt textareas (not the
 * enterprise prompt-template picker).
 *
 * Execution tests require OPENAI_API_KEY on the host (forwarded into
 * docker/docker-compose-ui-e2e.yaml) and assert the workflow completes
 * successfully. Without a key they are skipped.
 */

import type { Locator } from "@playwright/test";
import { expect, test } from "../coverage-fixture";
import {
  createTaskDef,
  createWorkflowDef,
  deleteWorkflowDef,
  startWorkflow,
  terminateWorkflow,
  waitForWorkflow,
  type WorkflowDef,
} from "./api-client";

const RUN_ID = Date.now();
const HAS_OPENAI = Boolean(process.env.OPENAI_API_KEY?.trim());
const OPENAI_SKIP_REASON =
  "OPENAI_API_KEY is required for successful LLM workflow execution";
const LLM_EXECUTION_TIMEOUT_MS = 90_000;

const CHAT_INSTRUCTIONS =
  "You are a helpful math assistant. Be concise. Reply with just the number.";
const TEXT_PROMPT =
  "Reply with exactly the single word HELLO and nothing else.";
const MULTI_CHAT_INSTRUCTIONS =
  "Answer the user question using the provided context. Be concise.";
const TOOLS_INSTRUCTIONS =
  "Use the provided tools to answer the user's question.";

/**
 * OSS chat/text forms use a plain textarea. Assert the enterprise AI Prompt
 * lookup (Prompt Template autocomplete, create-prompt link, prompt variables)
 * is not present.
 */
async function expectNoAiPromptLookup(taskForm: Locator) {
  await expect(taskForm.getByLabel("Prompt Template")).toHaveCount(0);
  await expect(taskForm.getByLabel("Prompt Name")).toHaveCount(0);
  await expect(
    taskForm.getByText(/reference a saved AI Prompt by name/i),
  ).toHaveCount(0);
  await expect(
    taskForm.getByRole("link", { name: /create a new one/i }),
  ).toHaveCount(0);
  await expect(
    taskForm.getByText(/Variables to be used in the prompt/i),
  ).toHaveCount(0);
}

/** Status chips render title case ("Completed" / "Failed"). */
async function expectExecutionStatusChip(
  page: import("@playwright/test").Page,
  status: string,
) {
  const label = status.charAt(0) + status.slice(1).toLowerCase();
  await expect(
    page
      .locator(".MuiChip-label")
      .filter({ hasText: new RegExp(`^${label}$`) })
      .first(),
  ).toBeVisible();
}

// ── Workflow definitions ────────────────────────────────────────────────────

const WF_CHAT_COMPLETE: WorkflowDef = {
  name: `e2e_llm_chat_${RUN_ID}`,
  version: 1,
  description: "LLM_CHAT_COMPLETE with inline instructions",
  tasks: [
    {
      name: "LLM_CHAT_COMPLETE",
      taskReferenceName: "chat_complete_ref",
      type: "LLM_CHAT_COMPLETE",
      inputParameters: {
        llmProvider: "openai",
        model: "gpt-4.1-mini",
        instructions: CHAT_INSTRUCTIONS,
        messages: [
          {
            role: "user",
            message: "What is 2 + 2? Reply with just the number.",
          },
        ],
        temperature: 0,
        maxTokens: 32,
      },
    },
  ],
};

const WF_TEXT_COMPLETE: WorkflowDef = {
  name: `e2e_llm_text_${RUN_ID}`,
  version: 1,
  description: "LLM_TEXT_COMPLETE with inline prompt",
  tasks: [
    {
      name: "LLM_TEXT_COMPLETE",
      taskReferenceName: "text_complete_ref",
      type: "LLM_TEXT_COMPLETE",
      inputParameters: {
        llmProvider: "openai",
        model: "gpt-4.1-mini",
        prompt: TEXT_PROMPT,
        temperature: 0,
        maxTokens: 32,
      },
    },
  ],
};

const WF_GENERATE_EMBEDDINGS: WorkflowDef = {
  name: `e2e_llm_embed_${RUN_ID}`,
  version: 1,
  description: "LLM_GENERATE_EMBEDDINGS task",
  tasks: [
    {
      name: "LLM_GENERATE_EMBEDDINGS",
      taskReferenceName: "gen_embeddings_ref",
      type: "LLM_GENERATE_EMBEDDINGS",
      inputParameters: {
        llmProvider: "openai",
        model: "text-embedding-3-small",
        text: "${workflow.input.text}",
      },
    },
  ],
  inputParameters: ["text"],
};

const WF_MULTI_LLM: WorkflowDef = {
  name: `e2e_llm_multi_${RUN_ID}`,
  version: 1,
  description: "Multiple LLM task types in one workflow",
  tasks: [
    {
      name: "LLM_GENERATE_EMBEDDINGS",
      taskReferenceName: "embed_step",
      type: "LLM_GENERATE_EMBEDDINGS",
      inputParameters: {
        llmProvider: "openai",
        model: "text-embedding-3-small",
        text: "${workflow.input.document}",
      },
    },
    {
      name: "LLM_INDEX_TEXT",
      taskReferenceName: "index_step",
      type: "LLM_INDEX_TEXT",
      inputParameters: {
        llmProvider: "openai",
        embeddingModelProvider: "openai",
        embeddingModel: "text-embedding-3-small",
        vectorDB: "pinecone",
        index: "e2e-index",
        namespace: "test",
        text: "${workflow.input.document}",
        docId: "doc-${workflow.workflowId}",
      },
    },
    {
      name: "LLM_SEARCH_INDEX",
      taskReferenceName: "search_step",
      type: "LLM_SEARCH_INDEX",
      inputParameters: {
        llmProvider: "openai",
        embeddingModelProvider: "openai",
        embeddingModel: "text-embedding-3-small",
        vectorDB: "pinecone",
        index: "e2e-index",
        namespace: "test",
        query: "${workflow.input.query}",
      },
    },
    {
      name: "LLM_CHAT_COMPLETE",
      taskReferenceName: "answer_step",
      type: "LLM_CHAT_COMPLETE",
      inputParameters: {
        llmProvider: "openai",
        model: "gpt-4.1-mini",
        instructions: MULTI_CHAT_INSTRUCTIONS,
        messages: [
          {
            role: "user",
            message:
              "Context: ${search_step.output.result}\n\nQuestion: ${workflow.input.query}",
          },
        ],
      },
    },
  ],
  inputParameters: ["document", "query"],
};

const WF_CHAT_TOOLS: WorkflowDef = {
  name: `e2e_llm_tools_${RUN_ID}`,
  version: 1,
  description: "LLM_CHAT_COMPLETE with tool definitions",
  tasks: [
    {
      name: "LLM_CHAT_COMPLETE",
      taskReferenceName: "chat_with_tools_ref",
      type: "LLM_CHAT_COMPLETE",
      inputParameters: {
        llmProvider: "openai",
        model: "gpt-4.1-mini",
        instructions: TOOLS_INSTRUCTIONS,
        userInput: "${workflow.input.question}",
        tools: [
          {
            name: "get_weather",
            description: "Get the current weather for a city",
            inputSchema: {
              type: "object",
              properties: {
                city: { type: "string", description: "City name" },
              },
              required: ["city"],
            },
          },
          {
            name: "get_stock_price",
            description: "Get the current stock price for a ticker symbol",
            inputSchema: {
              type: "object",
              properties: {
                ticker: {
                  type: "string",
                  description: "Stock ticker symbol (e.g. AAPL)",
                },
              },
              required: ["ticker"],
            },
          },
        ],
      },
    },
  ],
  inputParameters: ["question"],
};

const ALL_WORKFLOWS = [
  WF_CHAT_COMPLETE,
  WF_TEXT_COMPLETE,
  WF_GENERATE_EMBEDDINGS,
  WF_MULTI_LLM,
  WF_CHAT_TOOLS,
];

// IDs of executions we start — cleaned up in afterAll.
const startedWorkflowIds: string[] = [];

// ── Setup / teardown ────────────────────────────────────────────────────────

test.beforeAll(async () => {
  // retryCount: 0 so a missing/invalid provider key fails fast instead of
  // burning the wait budget on retries (same pattern as conductor e2e LLM tests).
  await createTaskDef({
    name: "LLM_CHAT_COMPLETE",
    retryCount: 0,
  }).catch(() => {});
  await createTaskDef({
    name: "LLM_TEXT_COMPLETE",
    retryCount: 0,
  }).catch(() => {});

  for (const wf of ALL_WORKFLOWS) {
    await createWorkflowDef(wf);
  }
});

test.afterAll(async () => {
  await Promise.allSettled(
    startedWorkflowIds.map((id) => terminateWorkflow(id)),
  );
  for (const wf of ALL_WORKFLOWS) {
    await deleteWorkflowDef(wf.name).catch(() => {});
  }
});

// ── Tests: list page ────────────────────────────────────────────────────────

test("LLM_CHAT_COMPLETE workflow appears in the list", async ({ page }) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_CHAT_COMPLETE.name)).toBeVisible();
});

test("LLM_TEXT_COMPLETE workflow appears in the list", async ({ page }) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_TEXT_COMPLETE.name)).toBeVisible();
});

test("LLM_GENERATE_EMBEDDINGS workflow appears in the list", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_GENERATE_EMBEDDINGS.name)).toBeVisible();
});

test("multi-LLM workflow appears in the list with correct description", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_MULTI_LLM.name)).toBeVisible();
  await expect(
    page.getByText("Multiple LLM task types in one workflow"),
  ).toBeVisible();
});

// ── Tests: Task tab — LLM_CHAT_COMPLETE ─────────────────────────────────────

test("clicking LLM_CHAT_COMPLETE node opens the task form", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_CHAT_COMPLETE.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("chat_complete_ref").first().click();

  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  await expect(taskForm.getByText("LLM_CHAT_COMPLETE")).toBeVisible();

  await expect(
    page.locator("#task-form-header-task-reference-field"),
  ).toHaveValue("chat_complete_ref");
});

test("LLM_CHAT_COMPLETE task form shows Instructions textarea with saved value", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_CHAT_COMPLETE.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("chat_complete_ref").first().click();
  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  // TaskFormSection is non-collapsible here — titles are plain text, not *-header ids.
  await expect(taskForm.getByText("Provider and Model")).toBeVisible();

  const instructions = taskForm.getByLabel("Instructions");
  await expect(instructions).toBeVisible();
  await expect(instructions).toHaveValue(CHAT_INSTRUCTIONS);
  await expectNoAiPromptLookup(taskForm);

  await instructions.fill("Updated system instructions for e2e.");
  await expect(instructions).toHaveValue(
    "Updated system instructions for e2e.",
  );
});

// ── Tests: Task tab — LLM_TEXT_COMPLETE ─────────────────────────────────────

test("clicking LLM_TEXT_COMPLETE node opens the task form", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_TEXT_COMPLETE.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("text_complete_ref").first().click();

  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  await expect(taskForm.getByText("LLM_TEXT_COMPLETE")).toBeVisible();

  await expect(
    page.locator("#task-form-header-task-reference-field"),
  ).toHaveValue("text_complete_ref");
});

test("LLM_TEXT_COMPLETE task form shows Prompt textarea with saved value", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_TEXT_COMPLETE.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("text_complete_ref").first().click();
  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  // TaskFormSection is non-collapsible here — titles are plain text, not *-header ids.
  await expect(taskForm.getByText("Provider and Model")).toBeVisible();

  const prompt = taskForm.getByLabel("Prompt");
  await expect(prompt).toBeVisible();
  await expect(prompt).toHaveValue(TEXT_PROMPT);
  await expectNoAiPromptLookup(taskForm);
});

// ── Tests: Task tab — LLM_GENERATE_EMBEDDINGS ───────────────────────────────

test("clicking LLM_GENERATE_EMBEDDINGS node opens the task form", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_GENERATE_EMBEDDINGS.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("gen_embeddings_ref").first().click();

  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  await expect(taskForm.getByText("LLM_GENERATE_EMBEDDINGS")).toBeVisible();

  await expect(
    page.locator("#task-form-header-task-reference-field"),
  ).toHaveValue("gen_embeddings_ref");
});

// ── Tests: Task tab — multi-LLM workflow ────────────────────────────────────

test("clicking each task in multi-LLM workflow opens correct task form", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_MULTI_LLM.name}/1`);
  await page.waitForLoadState("networkidle");

  const taskForm = page.locator("#maybe-task-form");
  const refField = page.locator("#task-form-header-task-reference-field");

  // 1. embed_step — LLM_GENERATE_EMBEDDINGS
  await page.getByText("embed_step").first().click();
  await expect(taskForm).toBeVisible();
  await expect(taskForm.getByText("LLM_GENERATE_EMBEDDINGS")).toBeVisible();
  await expect(refField).toHaveValue("embed_step");

  // 2. index_step — LLM_INDEX_TEXT
  await page.getByText("index_step").first().click();
  await expect(taskForm.getByText("LLM_INDEX_TEXT")).toBeVisible();
  await expect(refField).toHaveValue("index_step");

  // 3. search_step — LLM_SEARCH_INDEX
  await page.getByText("search_step").first().click();
  await expect(taskForm.getByText("LLM_SEARCH_INDEX")).toBeVisible();
  await expect(refField).toHaveValue("search_step");

  // 4. answer_step — LLM_CHAT_COMPLETE
  await page.getByText("answer_step").first().click();
  await expect(taskForm.getByText("LLM_CHAT_COMPLETE")).toBeVisible();
  await expect(refField).toHaveValue("answer_step");
});

test("LLM_CHAT_COMPLETE task in multi-LLM workflow shows Instructions textarea", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_MULTI_LLM.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("answer_step").first().click();
  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  await expect(taskForm.getByText("Provider and Model")).toBeVisible();
  await expect(taskForm.getByLabel("Instructions")).toHaveValue(
    MULTI_CHAT_INSTRUCTIONS,
  );
  await expectNoAiPromptLookup(taskForm);
});

// ── Tests: Task tab — tool-calling workflow ─────────────────────────────────

test("clicking LLM_CHAT_COMPLETE with tools opens the task form", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_CHAT_TOOLS.name}/1`);
  await page.waitForLoadState("networkidle");

  await page.getByText("chat_with_tools_ref").first().click();

  const taskForm = page.locator("#maybe-task-form");
  await expect(taskForm).toBeVisible();

  await expect(taskForm.getByText("LLM_CHAT_COMPLETE")).toBeVisible();

  await expect(
    page.locator("#task-form-header-task-reference-field"),
  ).toHaveValue("chat_with_tools_ref");

  await expect(taskForm.getByLabel("Instructions")).toHaveValue(
    TOOLS_INSTRUCTIONS,
  );
  await expectNoAiPromptLookup(taskForm);
});

// ── Tests: execute LLM workflows ────────────────────────────────────────────

test("LLM_CHAT_COMPLETE workflow execution completes successfully", async ({
  page,
}) => {
  test.skip(!HAS_OPENAI, OPENAI_SKIP_REASON);

  const workflowId = await startWorkflow(WF_CHAT_COMPLETE.name);
  startedWorkflowIds.push(workflowId);

  const wf = await waitForWorkflow(workflowId, {
    timeoutMs: LLM_EXECUTION_TIMEOUT_MS,
  });

  expect(wf.status).toBe("COMPLETED");

  const chatTask = wf.tasks?.find(
    (t) => t.referenceTaskName === "chat_complete_ref",
  );
  expect(chatTask).toBeTruthy();
  expect(chatTask?.taskType).toBe("LLM_CHAT_COMPLETE");
  expect(chatTask?.status).toBe("COMPLETED");
  expect(String(chatTask?.outputData?.result ?? "")).toMatch(/4/);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(WF_CHAT_COMPLETE.name)).toBeVisible();
  await expect(page.getByText("chat_complete_ref")).toBeVisible();
  await expectExecutionStatusChip(page, "COMPLETED");
  await expect(page.getByText(/4/)).toBeVisible();
});

test("LLM_TEXT_COMPLETE workflow execution completes successfully", async ({
  page,
}) => {
  test.skip(!HAS_OPENAI, OPENAI_SKIP_REASON);

  const workflowId = await startWorkflow(WF_TEXT_COMPLETE.name);
  startedWorkflowIds.push(workflowId);

  const wf = await waitForWorkflow(workflowId, {
    timeoutMs: LLM_EXECUTION_TIMEOUT_MS,
  });

  expect(wf.status).toBe("COMPLETED");

  const textTask = wf.tasks?.find(
    (t) => t.referenceTaskName === "text_complete_ref",
  );
  expect(textTask).toBeTruthy();
  expect(textTask?.taskType).toBe("LLM_TEXT_COMPLETE");
  expect(textTask?.status).toBe("COMPLETED");
  expect(String(textTask?.outputData?.result ?? "").toLowerCase()).toMatch(
    /hello/,
  );

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(WF_TEXT_COMPLETE.name)).toBeVisible();
  await expect(page.getByText("text_complete_ref")).toBeVisible();
  await expectExecutionStatusChip(page, "COMPLETED");
});

test("LLM_CHAT_COMPLETE completed execution appears in the executions search", async ({
  page,
}) => {
  test.skip(!HAS_OPENAI, OPENAI_SKIP_REASON);

  const workflowId = await startWorkflow(WF_CHAT_COMPLETE.name);
  startedWorkflowIds.push(workflowId);

  const wf = await waitForWorkflow(workflowId, {
    timeoutMs: LLM_EXECUTION_TIMEOUT_MS,
  });
  expect(wf.status).toBe("COMPLETED");

  await page.goto(
    `/executions?workflowType=${encodeURIComponent(WF_CHAT_COMPLETE.name)}`,
  );
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_CHAT_COMPLETE.name).first()).toBeVisible();
  const idPrefix = workflowId.substring(0, 8);
  await expect(page.getByText(new RegExp(idPrefix))).toBeVisible();
});
