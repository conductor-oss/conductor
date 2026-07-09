import {
  AgentEvent,
  AgentRunData,
  AgentStatus,
  AgentStrategy,
  EventType,
  FinishReason,
  TokenUsage,
} from "./types";

const mkTokens = (prompt: number, completion: number): TokenUsage => ({
  promptTokens: prompt,
  completionTokens: completion,
  totalTokens: prompt + completion,
});

const mkEvent = (
  id: string,
  type: EventType,
  summary: string,
  opts: Partial<AgentEvent> = {},
): AgentEvent => ({
  id,
  type,
  timestamp: Date.now(),
  summary,
  success: true,
  ...opts,
});

// --- Single agent, simple ---
const singleAgentRun: AgentRunData = {
  id: "run-001",
  agentName: "research_agent",
  model: "claude-sonnet-4-5",
  status: AgentStatus.COMPLETED,
  finishReason: FinishReason.STOP,
  strategy: AgentStrategy.SINGLE,
  totalTokens: mkTokens(1540, 320),
  totalDurationMs: 3200,
  turns: [
    {
      turnNumber: 1,
      status: AgentStatus.COMPLETED,
      durationMs: 800,
      tokens: mkTokens(520, 110),
      subAgents: [],
      events: [
        mkEvent(
          "e1",
          EventType.THINKING,
          "Let me analyze the user's request and plan my approach...",
          {
            detail:
              "The user wants me to research agentspan documentation. I should use the search tool to find relevant content.",
            durationMs: 200,
          },
        ),
        mkEvent("e2", EventType.TOOL_CALL, 'search_web("agentspan docs")', {
          toolName: "search_web",
          toolArgs: { query: "agentspan docs" },
          detail: { query: "agentspan docs", max_results: 5 },
        }),
        mkEvent("e3", EventType.TOOL_RESULT, "5 results found", {
          detail: [
            {
              title: "Agentspan Documentation",
              url: "https://docs.agentspan.dev",
            },
            {
              title: "Getting Started",
              url: "https://docs.agentspan.dev/getting-started",
            },
          ],
          success: true,
          tokens: mkTokens(0, 0),
        }),
        mkEvent("e4", EventType.GUARDRAIL_PASS, "content_filter ✓", {
          detail: { guardrail: "content_filter", passed: true },
        }),
      ],
    },
    {
      turnNumber: 2,
      status: AgentStatus.COMPLETED,
      durationMs: 1200,
      tokens: mkTokens(680, 150),
      subAgents: [],
      events: [
        mkEvent(
          "e5",
          EventType.THINKING,
          "I found the documentation. Let me synthesize the key points...",
          {
            detail:
              "Based on the search results, I can see that Agentspan is a platform for building and monitoring multi-agent systems. The key features include workflow visualization, agent execution tracking, and real-time monitoring.",
            durationMs: 300,
          },
        ),
        mkEvent(
          "e6",
          EventType.MESSAGE,
          "Based on my research, here's what I found about Agentspan: it's a comprehensive platform for building multi-agent AI systems with built-in observability...",
          {
            detail:
              "Based on my research, here's what I found about Agentspan: it's a comprehensive platform for building multi-agent AI systems with built-in observability, workflow management, and execution tracking capabilities.",
            tokens: mkTokens(680, 150),
          },
        ),
        mkEvent("e7", EventType.DONE, "Research complete", {
          detail: {
            output:
              "Research completed successfully with 2 turns and 5 sources.",
          },
          success: true,
        }),
      ],
    },
  ],
};

// --- Multi-agent handoff ---
const writerAgent: AgentRunData = {
  id: "run-003",
  agentName: "writer_agent",
  model: "claude-sonnet-4-5",
  status: AgentStatus.COMPLETED,
  finishReason: FinishReason.STOP,
  strategy: AgentStrategy.SINGLE,
  totalTokens: mkTokens(890, 200),
  totalDurationMs: 2100,
  turns: [
    {
      turnNumber: 1,
      status: AgentStatus.COMPLETED,
      durationMs: 600,
      tokens: mkTokens(300, 80),
      subAgents: [],
      events: [
        mkEvent(
          "ew1",
          EventType.THINKING,
          "I've received the research findings. Let me structure this into a report...",
          {
            detail:
              "The research agent has passed me comprehensive findings. I need to organize this into a well-structured report with sections for overview, features, and use cases.",
          },
        ),
        mkEvent("ew2", EventType.TOOL_CALL, 'write_file("report.md", ...)', {
          toolName: "write_file",
          toolArgs: { path: "report.md", content: "# Agentspan Report\n..." },
          detail: {
            path: "report.md",
            content: "# Agentspan Report\n\n## Overview\n...",
          },
        }),
        mkEvent("ew3", EventType.TOOL_RESULT, "File written successfully", {
          detail: { path: "report.md", bytesWritten: 2048 },
          success: true,
        }),
      ],
    },
    {
      turnNumber: 2,
      status: AgentStatus.COMPLETED,
      durationMs: 900,
      tokens: mkTokens(400, 90),
      subAgents: [],
      events: [
        mkEvent(
          "ew4",
          EventType.THINKING,
          "The file is written. Let me verify the content looks good...",
          { detail: "I should review the written content to ensure quality." },
        ),
        mkEvent(
          "ew5",
          EventType.MESSAGE,
          "Report has been written and saved to report.md. The document covers...",
          {
            detail:
              "Report has been written and saved to report.md. The document covers the overview, key features, installation guide, and use cases of Agentspan.",
            tokens: mkTokens(400, 90),
          },
        ),
        mkEvent("ew6", EventType.DONE, "Writing complete", {
          detail: { output: "report.md", status: "success" },
          success: true,
        }),
      ],
    },
  ],
};

const handoffAgentRun: AgentRunData = {
  id: "run-002",
  agentName: "research_agent",
  model: "claude-sonnet-4-5",
  status: AgentStatus.COMPLETED,
  finishReason: FinishReason.HANDOFF,
  strategy: AgentStrategy.HANDOFF,
  totalTokens: mkTokens(1540, 320),
  totalDurationMs: 5300,
  turns: [
    {
      turnNumber: 1,
      status: AgentStatus.COMPLETED,
      durationMs: 800,
      tokens: mkTokens(520, 110),
      subAgents: [],
      events: [
        mkEvent(
          "h1",
          EventType.THINKING,
          "I need to analyze the results and then hand off to writer_agent...",
          {
            detail:
              "After completing my research, I'll pass the findings to the writer agent for report generation.",
            durationMs: 200,
          },
        ),
        mkEvent("h2", EventType.TOOL_CALL, 'search_web("agentspan docs")', {
          toolName: "search_web",
          toolArgs: { query: "agentspan docs" },
        }),
        mkEvent("h3", EventType.TOOL_RESULT, "5 results found", {
          detail: [{ title: "Agentspan Documentation" }],
          success: true,
        }),
        mkEvent("h4", EventType.GUARDRAIL_PASS, "content_filter ✓", {
          detail: { guardrail: "content_filter", passed: true },
        }),
      ],
    },
    {
      turnNumber: 2,
      status: AgentStatus.COMPLETED,
      durationMs: 600,
      tokens: mkTokens(380, 100),
      subAgents: [writerAgent],
      strategy: AgentStrategy.HANDOFF,
      events: [
        mkEvent(
          "h5",
          EventType.THINKING,
          "I have all the information I need. Time to hand off to writer_agent...",
          {
            detail:
              "Research is complete. Handing off to writer_agent with the compiled findings.",
          },
        ),
        mkEvent("h6", EventType.HANDOFF, "→ writer_agent", {
          targetAgent: "writer_agent",
          detail: {
            target: "writer_agent",
            context: {
              research_findings: "Agentspan is a comprehensive platform...",
            },
          },
        }),
      ],
    },
  ],
};

// --- Parallel strategy with many sub-agents ---
const makeParallelAgent = (index: number, failed = false): AgentRunData => ({
  id: `parallel-agent-${index}`,
  agentName: `worker_agent_${String(index).padStart(3, "0")}`,
  model: "claude-haiku-4-5-20251001",
  status: failed ? AgentStatus.FAILED : AgentStatus.COMPLETED,
  finishReason: failed ? FinishReason.ERROR : FinishReason.STOP,
  strategy: AgentStrategy.SINGLE,
  totalTokens: mkTokens(200 + index * 10, 50 + index * 5),
  totalDurationMs: 200 + index * 30,
  turns: [
    {
      turnNumber: 1,
      status: failed ? AgentStatus.FAILED : AgentStatus.COMPLETED,
      durationMs: 200 + index * 30,
      tokens: mkTokens(200 + index * 10, 50 + index * 5),
      subAgents: [],
      events: [
        mkEvent(
          `p${index}-e1`,
          EventType.THINKING,
          `Processing chunk ${index}...`,
        ),
        ...(failed
          ? [
              mkEvent(
                `p${index}-e2`,
                EventType.ERROR,
                `Failed to process chunk: timeout after 500ms`,
                { success: false, errorMessage: "Timeout after 500ms" },
              ),
            ]
          : [
              mkEvent(
                `p${index}-e2`,
                EventType.DONE,
                `Chunk ${index} processed successfully`,
                { success: true },
              ),
            ]),
      ],
    },
  ],
});

const parallelSubAgents = Array.from({ length: 25 }, (_, i) =>
  makeParallelAgent(i + 1, i === 2 || i === 7 || i === 15),
);

const parallelAgentRun: AgentRunData = {
  id: "run-parallel",
  agentName: "orchestrator_agent",
  model: "claude-sonnet-4-5",
  status: AgentStatus.COMPLETED,
  finishReason: FinishReason.STOP,
  strategy: AgentStrategy.PARALLEL,
  totalTokens: mkTokens(3500, 800),
  totalDurationMs: 8500,
  turns: [
    {
      turnNumber: 1,
      status: AgentStatus.COMPLETED,
      durationMs: 400,
      tokens: mkTokens(800, 200),
      subAgents: [],
      events: [
        mkEvent(
          "orch1",
          EventType.THINKING,
          "I need to distribute this work across 25 parallel workers...",
        ),
        mkEvent("orch2", EventType.TOOL_CALL, "split_workload(chunks=25)", {
          toolName: "split_workload",
          toolArgs: { chunks: 25, data: "large_dataset.json" },
        }),
        mkEvent(
          "orch3",
          EventType.TOOL_RESULT,
          "Workload split into 25 chunks",
          {
            detail: { chunks: 25, totalItems: 2500 },
            success: true,
          },
        ),
      ],
    },
    {
      turnNumber: 2,
      status: AgentStatus.COMPLETED,
      durationMs: 6500,
      tokens: mkTokens(1800, 400),
      subAgents: parallelSubAgents,
      strategy: AgentStrategy.PARALLEL,
      events: [
        mkEvent(
          "orch4",
          EventType.THINKING,
          "Dispatching 25 parallel workers...",
        ),
        mkEvent(
          "orch5",
          EventType.MESSAGE,
          "All workers have completed. 22 succeeded, 3 failed.",
          {
            detail:
              "Dispatched 25 parallel worker agents. 22 completed successfully, 3 encountered errors.",
          },
        ),
        mkEvent("orch6", EventType.DONE, "Parallel processing complete", {
          detail: { total: 25, succeeded: 22, failed: 3 },
          success: true,
        }),
      ],
    },
  ],
};

// --- Guardrail failure scenario ---
const guardrailFailureRun: AgentRunData = {
  id: "run-guardrail",
  agentName: "content_agent",
  model: "claude-sonnet-4-5",
  status: AgentStatus.FAILED,
  finishReason: FinishReason.ERROR,
  strategy: AgentStrategy.SINGLE,
  totalTokens: mkTokens(450, 120),
  totalDurationMs: 1800,
  turns: [
    {
      turnNumber: 1,
      status: AgentStatus.FAILED,
      durationMs: 1800,
      tokens: mkTokens(450, 120),
      subAgents: [],
      events: [
        mkEvent(
          "g1",
          EventType.THINKING,
          "Processing the user's content request...",
        ),
        mkEvent(
          "g2",
          EventType.TOOL_CALL,
          'generate_content("marketing copy")',
          {
            toolName: "generate_content",
            toolArgs: { type: "marketing", topic: "product launch" },
          },
        ),
        mkEvent("g3", EventType.TOOL_RESULT, "Content generated", {
          detail: "Generated 500 words of marketing copy...",
          success: true,
        }),
        mkEvent("g4", EventType.GUARDRAIL_PASS, "content_filter ✓", {
          detail: { guardrail: "content_filter", passed: true },
        }),
        mkEvent(
          "g5",
          EventType.GUARDRAIL_FAIL,
          "pii_detector ✗ - Phone number detected in output",
          {
            success: false,
            detail: {
              guardrail: "pii_detector",
              passed: false,
              reason: "Phone number detected in generated content",
              matched: "+1-555-0123",
              context: "Call us at +1-555-0123 for more information",
            },
          },
        ),
        mkEvent(
          "g6",
          EventType.ERROR,
          "Guardrail violation: pii_detector blocked the response",
          {
            success: false,
            errorMessage:
              "Guardrail violation: pii_detector blocked the response",
            detail: {
              type: "guardrail_violation",
              guardrail: "pii_detector",
              message: "Output contained personally identifiable information",
            },
          },
        ),
      ],
    },
  ],
};

// Export all mock scenarios
export const MOCK_SCENARIOS = {
  singleAgent: singleAgentRun,
  handoff: handoffAgentRun,
  parallel: parallelAgentRun,
  guardrailFailure: guardrailFailureRun,
} as const;

export type MockScenarioKey = keyof typeof MOCK_SCENARIOS;

export const DEFAULT_MOCK_SCENARIO: MockScenarioKey = "handoff";
