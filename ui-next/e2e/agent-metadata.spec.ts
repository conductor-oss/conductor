import { expect, test, type Page } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

const conductorTools = Array.from({ length: 36 }, (_, index) => ({
  name: `worker_tool_${index + 1}`,
  toolType: "tool",
  description: `Worker tool ${index + 1}`,
}));

const advertisedTools = Array.from({ length: 35 }, (_, index) => ({
  name: `advertised_tool_${index + 1}`,
  toolType: "tool",
}));

const conductorSnapshot = {
  schemaVersion: 1,
  agentType: "conductor",
  displayName: "Research Coordinator",
  source: { name: "research_coordinator", requestedVersion: 4 },
  resolved: true,
  conductor: {
    name: "research_coordinator",
    requestedVersion: 4,
    resolvedVersion: 4,
    framework: "openai-agents",
    normalization: "normalized",
    agentConfig: {
      name: "Research Coordinator",
      model: "openai/gpt-5",
      instructions:
        "Research carefully, compare evidence, and cite every claim. ".repeat(
          20,
        ),
      tools: conductorTools,
      input_guardrails: [
        { guardrail_function: { name: "reject_sensitive_input" } },
      ],
      output_guardrails: [
        { guardrail_function: { name: "require_citations" } },
      ],
      agents: [{ name: "fact_checker" }, { name: "source_reviewer" }],
      strategy: "react",
      max_turns: 12,
      reasoning_effort: "high",
    },
  },
};

const a2aSnapshot = {
  schemaVersion: 1,
  agentType: "a2a",
  displayName: "Travel Planning Network",
  source: { url: "https://agents.example.test/travel" },
  resolved: true,
  a2a: {
    url: "https://agents.example.test/travel",
    agentCard: {
      name: "Travel Planning Network",
      description: "Plans complex, multi-city itineraries.",
      version: "2.1.0",
      protocolVersion: "0.3.0",
      provider: {
        organization: "Example Travel",
        url: "https://example.test",
      },
      supportedInterfaces: [
        {
          url: "https://agents.example.test/travel/rpc",
          protocolBinding: "JSONRPC",
          protocolVersion: "2.0",
        },
        {
          url: "https://agents.example.test/travel/http",
          protocolBinding: "HTTP+JSON",
          protocolVersion: "1.0",
        },
      ],
      defaultInputModes: ["text/plain", "application/json"],
      defaultOutputModes: ["text/markdown", "application/json"],
      capabilities: {
        streaming: true,
        pushNotifications: true,
        stateTransitionHistory: true,
        extendedAgentCard: true,
        extensions: [
          {
            uri: "urn:conductor:agent-details:v1",
            description: "Public implementation details",
            params: {
              model: "anthropic/claude-sonnet",
              instructions: "Use the public itinerary policy.",
              tools: advertisedTools,
              inputGuardrails: ["validate_destination"],
            },
          },
        ],
      },
      skills: Array.from({ length: 18 }, (_, index) => ({
        id: `travel-skill-${index + 1}`,
        name: `Travel skill ${index + 1}`,
        description: `Handles travel planning scenario ${index + 1}.`,
        tags: ["travel", `scenario-${index + 1}`],
        examples: [`Plan scenario ${index + 1}`],
        securityRequirements: [{ oauth2: ["travel:read"] }],
      })),
      signatures: [
        {
          protected: "eyJhbGciOiJFUzI1NiJ9",
          signature: "test-signature",
        },
      ],
    },
  },
};

const workflow = {
  name: "agent_metadata_visualization",
  version: 1,
  description: "Agent snapshot visualization fixture",
  ownerEmail: "test@example.com",
  schemaVersion: 2,
  restartable: true,
  tasks: [
    {
      name: "agent",
      taskReferenceName: "research_agent_ref",
      type: "AGENT",
      inputParameters: {
        agentType: "conductor",
        name: "research_coordinator",
        version: 4,
        prompt: "Research the destination.",
      },
      metadata: { agent: conductorSnapshot },
    },
    {
      name: "agent",
      taskReferenceName: "travel_agent_ref",
      type: "AGENT",
      inputParameters: {
        agentType: "a2a",
        agentUrl: "https://agents.example.test/travel",
        text: "Plan the trip.",
      },
      metadata: { agent: a2aSnapshot },
    },
    {
      name: "agent",
      taskReferenceName: "greeter_agent_ref",
      type: "AGENT",
      inputParameters: {
        agentType: "conductor",
        name: "greeter",
        prompt: "Greet the user.",
      },
    },
    {
      name: "agent",
      taskReferenceName: "dynamic_agent_ref",
      type: "AGENT",
      inputParameters: {
        agentType: "a2a",
        agentUrl: "${workflow.input.agentUrl}",
        text: "Run the selected agent.",
      },
      metadata: {
        agent: {
          schemaVersion: 1,
          agentType: "a2a",
          displayName: "${workflow.input.agentUrl}",
          source: {
            url: "${workflow.input.agentUrl}",
            expression: "${workflow.input.agentUrl}",
          },
          resolved: false,
          a2a: { url: "${workflow.input.agentUrl}" },
        },
      },
    },
  ],
  inputParameters: ["agentUrl"],
  outputParameters: {},
  timeoutSeconds: 600,
  timeoutPolicy: "TIME_OUT_WF",
};

const execution = {
  workflowId: "agent-metadata-execution",
  workflowType: workflow.name,
  workflowName: workflow.name,
  workflowVersion: workflow.version,
  version: workflow.version,
  status: "COMPLETED",
  startTime: 1_700_000_000_000,
  endTime: 1_700_000_004_000,
  input: { agentUrl: "https://agents.example.test/travel" },
  output: {},
  workflowDefinition: workflow,
  tasks: workflow.tasks.map((workflowTask, index) => ({
    taskType: "AGENT",
    taskDefName: "agent",
    referenceTaskName: workflowTask.taskReferenceName,
    workflowTask,
    status: "COMPLETED",
    executed: true,
    retryCount: 0,
    seq: index + 1,
    taskId: `agent-task-${index + 1}`,
    workflowInstanceId: "agent-metadata-execution",
    workflowType: workflow.name,
    scheduledTime: 1_700_000_000_000 + index * 1_000,
    startTime: 1_700_000_000_100 + index * 1_000,
    endTime: 1_700_000_000_900 + index * 1_000,
    updateTime: 1_700_000_000_900 + index * 1_000,
    inputData: workflowTask.inputParameters,
    outputData: { result: `agent result ${index + 1}` },
    loopOverTask: false,
    iteration: 0,
  })),
};

async function mockAgentMetadataApis(page: Page) {
  await mockCommonApis(page);
  await page.route(
    "**/api/metadata/workflow/agent_metadata_visualization**",
    (route) => route.fulfill({ json: workflow }),
  );
  await page.route("**/api/workflow/agent-metadata-execution?**", (route) =>
    route.fulfill({ json: execution }),
  );
}

test.beforeEach(async ({ page }) => {
  await mockAgentMetadataApis(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
});

test("renders agent identities and definition details", async ({ page }) => {
  await page.goto("/workflowDef/agent_metadata_visualization/1");
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#workflow-name-display")).toBeVisible();
  await expect(page.getByText("CONDUCTOR AGENT")).toHaveCount(2);
  await expect(page.getByText("A2A AGENT")).toHaveCount(2);
  await expect(page.getByText("Research Coordinator")).toBeVisible();
  await expect(page.getByText("Travel Planning Network")).toBeVisible();
  await expect(page.getByText("greeter", { exact: true })).toBeVisible();
  await expect(page.getByText("UNRESOLVED: greeter")).toHaveCount(0);
  await expect(
    page.getByText("UNRESOLVED: ${workflow.input.agentUrl}"),
  ).toBeVisible();

  await page.getByText("Research Coordinator").click();
  await expect(page.getByText("Agent Card")).toBeVisible();
  await expect(page.getByTestId("agent-card")).toHaveAttribute(
    "data-agent-type",
    "conductor",
  );
  await expect(page.getByText("openai/gpt-5")).toBeVisible();
  await expect(page.getByText("Worker (36)")).toBeVisible();
  await expect(page.getByText("worker_tool_36")).toBeAttached();

  await page.getByText("Travel Planning Network").first().click();
  await expect(page.getByText("Agent Card")).toBeVisible();
  await expect(page.getByTestId("agent-card")).toHaveAttribute(
    "data-agent-type",
    "a2a",
  );
  await expect(page.getByText("Protocol & capabilities")).toBeVisible();
  await expect(page.getByText("Skills (18)")).toBeVisible();
  await expect(page.getByText("Declared extensions (1)")).toBeVisible();
  await expect(page.getByText("Worker (35)")).toBeVisible();
  await expect(
    page.getByText("advertised_tool_35", { exact: true }),
  ).toBeAttached();

  await page.screenshot({
    path: "/private/tmp/conductor-agent-definition.png",
  });
});

test("renders agent identities and execution details", async ({ page }) => {
  await page.goto("/execution/agent-metadata-execution");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText("Research Coordinator")).toBeVisible();
  await expect(page.getByText("Travel Planning Network")).toBeVisible();
  await expect(page.getByText("greeter", { exact: true })).toBeVisible();
  await expect(page.getByText("UNRESOLVED: greeter")).toHaveCount(0);
  await expect(
    page.getByText("UNRESOLVED: ${workflow.input.agentUrl}"),
  ).toBeVisible();

  await page.getByText("Travel Planning Network").click();
  await expect(page.locator("#execution-page-right-panel")).toBeVisible();
  await expect(
    page.locator("#execution-page-right-panel [role='tab']"),
  ).toHaveText([
    "Summary",
    "Input",
    "Output",
    "Agent Card",
    "Logs",
    "JSON",
    "Definition",
  ]);
  const agentCardTab = page.getByRole("tab", { name: "Agent Card" });
  await agentCardTab.click();
  await expect(agentCardTab).toHaveAttribute("aria-selected", "true");
  await expect(page.getByTestId("agent-card-panel")).toBeVisible();
  await expect(page.getByTestId("agent-card")).toHaveAttribute(
    "data-agent-type",
    "a2a",
  );
  await expect(page.getByText("Protocol & capabilities")).toBeVisible();
  await expect(page.getByText("Travel skill 18")).toBeAttached();

  await page.getByText("Research Coordinator").click();
  await expect(page.getByTestId("agent-card")).toHaveAttribute(
    "data-agent-type",
    "conductor",
  );
  await expect(page.getByText("openai/gpt-5")).toBeVisible();
  await expect(page.getByText("worker_tool_36")).toBeAttached();

  await page.screenshot({
    path: "/private/tmp/conductor-agent-execution.png",
  });
});
