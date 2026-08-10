import { describe, expect, it } from "vitest";

import {
  computeMetrics,
  isFailedTaskStatus,
  mapTaskStatus,
  replaceAgentRunNode,
  taskSuccess,
  timelineItemKind,
  transformWorkflowExecutionToAgentRun,
} from "./agentExecutionUtils";
import {
  AgentRunData,
  AgentStatus,
  AgentStrategy,
  AgentTimelineKind,
} from "./types";

function task(overrides: Record<string, unknown>) {
  return {
    taskId: String(overrides.taskId ?? overrides.referenceTaskName),
    referenceTaskName: "task",
    taskType: "SIMPLE",
    workflowTask: {
      name: "task",
      taskReferenceName: "task",
      type: "SIMPLE",
    },
    status: "COMPLETED",
    executed: true,
    loopOverTask: false,
    ...overrides,
  } as any;
}

function execution(tasks: any[], overrides: Record<string, unknown> = {}) {
  return {
    workflowId: "execution-id",
    workflowName: "agent",
    status: "COMPLETED",
    startTime: 0,
    endTime: 100,
    executionTime: 100,
    input: { prompt: "hello" },
    output: {},
    tasks,
    workflowDefinition: { metadata: {} },
    ...overrides,
  } as any;
}

// Regression coverage for issue #4260: a FAILED_WITH_TERMINAL_ERROR task (and
// the other terminal-failure statuses) previously fell through to RUNNING,
// rendering a perpetual "running" chip + spinner for an already-failed task.

describe("mapTaskStatus", () => {
  it("maps COMPLETED to COMPLETED", () => {
    expect(mapTaskStatus("COMPLETED")).toBe(AgentStatus.COMPLETED);
  });

  it.each(["FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"])(
    "maps terminal-failure status %s to FAILED",
    (status) => {
      expect(mapTaskStatus(status)).toBe(AgentStatus.FAILED);
    },
  );

  it.each(["IN_PROGRESS", "SCHEDULED", "PENDING"])(
    "maps in-progress status %s to RUNNING",
    (status) => {
      expect(mapTaskStatus(status)).toBe(AgentStatus.RUNNING);
    },
  );

  it("does not fall through to RUNNING for unknown/other terminal statuses", () => {
    expect(mapTaskStatus("SKIPPED")).toBe(AgentStatus.FAILED);
    expect(mapTaskStatus("COMPLETED_WITH_ERRORS")).toBe(AgentStatus.FAILED);
    expect(mapTaskStatus("SOME_FUTURE_STATUS")).toBe(AgentStatus.FAILED);
  });
});

describe("taskSuccess", () => {
  it("returns true for COMPLETED", () => {
    expect(taskSuccess("COMPLETED")).toBe(true);
  });

  it.each(["FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"])(
    "returns false for terminal-failure status %s",
    (status) => {
      expect(taskSuccess(status)).toBe(false);
    },
  );

  it.each(["IN_PROGRESS", "SCHEDULED", "PENDING"])(
    "returns undefined (spinner) for in-progress status %s",
    (status) => {
      expect(taskSuccess(status)).toBeUndefined();
    },
  );

  it("never returns undefined (perpetual spinner) for a terminal status", () => {
    expect(taskSuccess("SKIPPED")).toBe(false);
    expect(taskSuccess("COMPLETED_WITH_ERRORS")).toBe(false);
    expect(taskSuccess("NULL")).toBe(false);
  });
});

describe("isFailedTaskStatus", () => {
  it.each(["FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"])(
    "treats %s as a terminal failure",
    (status) => {
      expect(isFailedTaskStatus(status)).toBe(true);
    },
  );

  it.each(["COMPLETED", "IN_PROGRESS", "SCHEDULED", "SKIPPED"])(
    "does not treat %s as a terminal failure",
    (status) => {
      expect(isFailedTaskStatus(status)).toBe(false);
    },
  );
});

describe("transformWorkflowExecutionToAgentRun timeline", () => {
  it("uses the server-provided aggregate token usage", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution(
        [
          task({
            referenceTaskName: "root_llm",
            taskType: "LLM_CHAT_COMPLETE",
            inputData: { model: "gpt", messages: [] },
            outputData: { promptTokens: 10, completionTokens: 2 },
          }),
        ],
        {
          aggregateTokenUsage: {
            promptTokens: 60,
            completionTokens: 12,
            totalTokens: 72,
          },
        },
      ),
    );

    expect(run.totalTokens).toEqual({
      promptTokens: 60,
      completionTokens: 12,
      totalTokens: 72,
    });
  });

  it("preserves structured execution input, output, and runtime type for inspection", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution([], {
        input: { prompt: "hello", context: { customerId: "c-1" } },
        output: { answer: "done", sources: ["kb"] },
        workflowDefinition: {
          metadata: {
            agent_sdk: "conductor",
            agentDef: { name: "support_agent", strategy: "handoff" },
          },
        },
      }),
    );

    expect(run.agentType).toBe("conductor");
    expect(run.input).toEqual({
      prompt: "hello",
      context: { customerId: "c-1" },
    });
    expect(run.output).toEqual({ answer: "done", sources: ["kb"] });
  });

  it("places MCP discovery before loop turns instead of appending it last", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution([
        task({
          referenceTaskName: "agent_list_mcp_0",
          taskType: "LIST_MCP_TOOLS",
          seq: "1",
          startTime: 10,
          endTime: 12,
        }),
        task({
          referenceTaskName: "agent_mcp_filter_llm",
          taskType: "LLM_CHAT_COMPLETE",
          seq: "2",
          startTime: 15,
          endTime: 20,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "filtered" },
        }),
        task({
          referenceTaskName: "agent_loop",
          taskType: "DO_WHILE",
          loopOverTask: true,
          seq: "3",
          startTime: 25,
          endTime: 80,
        }),
        task({
          referenceTaskName: "agent_llm__1",
          taskType: "LLM_CHAT_COMPLETE",
          loopOverTask: true,
          seq: "4",
          startTime: 30,
          endTime: 40,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "TOOL_CALLS" },
        }),
        task({
          referenceTaskName: "agent_llm__2",
          taskType: "LLM_CHAT_COMPLETE",
          loopOverTask: true,
          seq: "5",
          startTime: 50,
          endTime: 60,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "answer" },
        }),
      ]),
    );

    expect(run.turns.map(timelineItemKind)).toEqual([
      AgentTimelineKind.PREPARATION,
      AgentTimelineKind.TURN,
      AgentTimelineKind.TURN,
    ]);
    expect(run.turns.map((turn) => turn.turnNumber)).toEqual([0, 1, 2]);
  });

  it("keeps forked prefill tools in preparation and preserves their fork group", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution([
        task({
          referenceTaskName: "agent_prefill_fork",
          taskType: "FORK_JOIN",
          seq: "1",
          startTime: 10,
          endTime: 10,
        }),
        task({
          referenceTaskName: "agent_prefill_0",
          taskType: "SIMPLE",
          seq: "2",
          startTime: 11,
          endTime: 13,
        }),
        task({
          referenceTaskName: "agent_prefill_1",
          taskType: "SIMPLE",
          seq: "3",
          startTime: 12,
          endTime: 14,
        }),
        task({
          referenceTaskName: "agent_prefill_join",
          taskType: "JOIN",
          seq: "4",
          startTime: 11,
          endTime: 15,
        }),
        task({
          referenceTaskName: "agent_llm",
          taskType: "LLM_CHAT_COMPLETE",
          seq: "5",
          startTime: 20,
          endTime: 30,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "answer" },
        }),
      ]),
    );

    expect(run.turns.map(timelineItemKind)).toEqual([
      AgentTimelineKind.PREPARATION,
      AgentTimelineKind.TURN,
    ]);
    expect(run.turns[0].events).toHaveLength(2);
    expect(run.turns[0].events.map((event) => event.parallelGroup)).toEqual([
      "agent_prefill_fork",
      "agent_prefill_fork",
    ]);
  });

  it("keeps sequential discovery calls separate from forked tool calls", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution([
        task({
          referenceTaskName: "agent_list_mcp_0",
          taskType: "LIST_MCP_TOOLS",
          seq: "1",
        }),
        task({
          referenceTaskName: "agent_list_mcp_1",
          taskType: "LIST_MCP_TOOLS",
          seq: "2",
        }),
      ]),
    );

    const discoveryCalls = run.turns[0].events.filter(
      (event) => event.type === "TOOL_CALL",
    );
    expect(discoveryCalls).toHaveLength(2);
    expect(
      discoveryCalls.every((event) => event.parallelGroup === undefined),
    ).toBe(true);
  });

  it("renders root work after a loop as finalization", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution([
        task({
          referenceTaskName: "agent_llm__1",
          taskType: "LLM_CHAT_COMPLETE",
          loopOverTask: true,
          seq: "1",
          startTime: 20,
          endTime: 30,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "turn answer" },
        }),
        task({
          referenceTaskName: "agent_final",
          taskType: "LLM_CHAT_COMPLETE",
          seq: "2",
          startTime: 40,
          endTime: 50,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "final answer" },
        }),
      ]),
    );

    expect(run.turns.map(timelineItemKind)).toEqual([
      AgentTimelineKind.TURN,
      AgentTimelineKind.FINALIZATION,
    ]);
  });

  it("excludes preparation and finalization from agent-turn metrics", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution([
        task({
          referenceTaskName: "agent_list_mcp_0",
          taskType: "LIST_MCP_TOOLS",
          seq: "1",
          startTime: 10,
          endTime: 11,
        }),
        task({
          referenceTaskName: "agent_llm__1",
          taskType: "LLM_CHAT_COMPLETE",
          loopOverTask: true,
          seq: "2",
          startTime: 20,
          endTime: 30,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "turn answer" },
        }),
        task({
          referenceTaskName: "agent_final",
          taskType: "LLM_CHAT_COMPLETE",
          seq: "3",
          startTime: 40,
          endTime: 50,
          inputData: { model: "gpt", messages: [] },
          outputData: { finishReason: "STOP", result: "final answer" },
        }),
      ]),
    );

    expect(computeMetrics(run).totalTurns).toBe(1);
  });

  // Regression coverage for issue #1454: sub-agent nodes were labelled with the
  // PARENT's invocation strategy instead of their own defined strategy — a
  // router-strategy sub-agent rendered as "handoff" (whatever spawned it)
  // instead of "router" (how it orchestrates its own children).
  it("gives each sub-agent its own defined strategy, not the parent's", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution(
        [
          task({
            referenceTaskName: "researcher__1",
            taskType: "SUB_WORKFLOW",
            outputData: { subWorkflowId: "wf-researcher", result: "done" },
          }),
        ],
        {
          workflowDefinition: {
            metadata: {
              agentDef: {
                name: "agent",
                strategy: "handoff",
                agents: [
                  {
                    name: "researcher",
                    strategy: "router",
                    agents: [{ name: "web_search" }, { name: "arxiv" }],
                  },
                ],
              },
            },
          },
        },
      ),
    );

    const researcher = run.turns
      .flatMap((turn) => turn.subAgents)
      .find((sub) => sub.agentName === "researcher");

    expect(researcher?.strategy).toBe(AgentStrategy.ROUTER);
    // Regression coverage for issue #1452: the Execution diagram needs this
    // count to know a collapsed sub-agent has its own children worth
    // expanding, even before that sub-agent's own execution has been fetched.
    expect(researcher?.subAgentCount).toBe(2);
  });

  it("shows no strategy for a leaf sub-agent (no nested agents of its own)", () => {
    // Task runs inside a DO_WHILE iteration so it goes through the
    // handoff/loop sub-agent path, which used to hardcode strategy=SINGLE
    // for every sub-agent regardless of its own definition.
    const run = transformWorkflowExecutionToAgentRun(
      execution(
        [
          task({
            referenceTaskName: "writer__1",
            taskType: "SUB_WORKFLOW",
            loopOverTask: true,
            outputData: { subWorkflowId: "wf-writer", result: "done" },
          }),
        ],
        {
          workflowDefinition: {
            metadata: {
              agentDef: {
                name: "agent",
                strategy: "handoff",
                agents: [{ name: "writer", strategy: "single" }],
              },
            },
          },
        },
      ),
    );

    const writer = run.turns
      .flatMap((turn) => turn.subAgents)
      .find((sub) => sub.agentName === "writer");

    expect(writer?.strategy).toBeUndefined();
    expect(writer?.subAgentCount).toBeUndefined();
  });

  it("derives the root agent's own strategy from its definition, not the runtime shape", () => {
    const run = transformWorkflowExecutionToAgentRun(
      execution(
        [
          task({
            referenceTaskName: "researcher__1",
            taskType: "SUB_WORKFLOW",
            outputData: { subWorkflowId: "wf-researcher", result: "done" },
          }),
        ],
        {
          workflowName: "agent",
          workflowDefinition: {
            metadata: {
              agentDef: {
                name: "agent",
                strategy: "router",
                agents: [{ name: "researcher" }],
              },
            },
          },
        },
      ),
    );

    expect(run.strategy).toBe(AgentStrategy.ROUTER);
  });
});

// Regression coverage for issue #1452: the Execution diagram only ever
// rendered one level of a sub-agent tree because the underlying AgentRunData
// for a sub-agent was a shallow stub — its own execution was never fetched
// unless the user fully navigated away via "drill in". replaceAgentRunNode
// is the pure tree-update primitive that splices a freshly-fetched
// sub-agent's real turns/subAgents into the existing tree in place, which is
// what makes lazy expand-in-place possible.
describe("replaceAgentRunNode", () => {
  function leaf(id: string): AgentRunData {
    return {
      id,
      agentName: id,
      turns: [],
      status: AgentStatus.COMPLETED,
      totalTokens: { promptTokens: 0, completionTokens: 0, totalTokens: 0 },
      totalDurationMs: 0,
    };
  }

  function withChild(root: AgentRunData, child: AgentRunData): AgentRunData {
    return {
      ...root,
      turns: [
        {
          turnNumber: 1,
          status: AgentStatus.COMPLETED,
          durationMs: 0,
          tokens: { promptTokens: 0, completionTokens: 0, totalTokens: 0 },
          subAgents: [child],
          events: [],
        },
      ],
    };
  }

  it("replaces a top-level sub-agent with the updater's result", () => {
    const root = withChild(leaf("root"), leaf("child"));

    const updated = replaceAgentRunNode(root, "child", (node) => ({
      ...node,
      expanded: true,
      turns: [
        {
          turnNumber: 1,
          status: AgentStatus.COMPLETED,
          durationMs: 0,
          tokens: { promptTokens: 0, completionTokens: 0, totalTokens: 0 },
          subAgents: [leaf("grandchild")],
          events: [],
        },
      ],
    }));

    const updatedChild = updated.turns[0].subAgents[0];
    expect(updatedChild.expanded).toBe(true);
    expect(updatedChild.turns[0].subAgents[0].id).toBe("grandchild");
  });

  it("finds and replaces a deeply nested sub-agent, several levels down", () => {
    const grandchild = leaf("grandchild");
    const child = withChild(leaf("child"), grandchild);
    const root = withChild(leaf("root"), child);

    const updated = replaceAgentRunNode(root, "grandchild", (node) => ({
      ...node,
      expanded: true,
    }));

    const updatedGrandchild =
      updated.turns[0].subAgents[0].turns[0].subAgents[0];
    expect(updatedGrandchild.expanded).toBe(true);
  });

  it("replaces the root itself when targetId matches the root's own id", () => {
    const root = leaf("root");
    const updated = replaceAgentRunNode(root, "root", (node) => ({
      ...node,
      expanded: true,
    }));
    expect(updated.expanded).toBe(true);
  });

  it("returns the same reference untouched when targetId isn't found", () => {
    const root = withChild(leaf("root"), leaf("child"));
    const updated = replaceAgentRunNode(root, "nonexistent", (node) => ({
      ...node,
      expanded: true,
    }));
    expect(updated).toBe(root);
  });

  it("leaves sibling branches referentially unchanged (no unnecessary re-renders)", () => {
    const untouchedSibling = leaf("sibling");
    const root: AgentRunData = {
      ...leaf("root"),
      turns: [
        {
          turnNumber: 1,
          status: AgentStatus.COMPLETED,
          durationMs: 0,
          tokens: { promptTokens: 0, completionTokens: 0, totalTokens: 0 },
          subAgents: [leaf("target"), untouchedSibling],
          events: [],
        },
      ],
    };

    const updated = replaceAgentRunNode(root, "target", (node) => ({
      ...node,
      expanded: true,
    }));

    expect(updated.turns[0].subAgents[1]).toBe(untouchedSibling);
  });
});
