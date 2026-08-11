import { describe, expect, it } from "vitest";

import { buildAgentExecutionDiagram } from "./buildAgentExecutionDiagram";
import { AgentRunData, AgentStatus, AgentStrategy, AgentTurn } from "./types";

const ZERO_TOKENS = {
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
};

function completedTurn(
  turnNumber: number,
  subAgents: AgentRunData[],
): AgentTurn {
  return {
    turnNumber,
    status: AgentStatus.COMPLETED,
    durationMs: 10,
    tokens: ZERO_TOKENS,
    events: [],
    subAgents,
  };
}

function leaf(name: string): AgentRunData {
  return {
    id: name,
    agentName: name,
    status: AgentStatus.COMPLETED,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 10,
    turns: [],
  };
}

/**
 * Fixture matching the nested sequential → parallel → leaves shape used by
 * definition nesting e2e — but fully COMPLETED, no live LLM.
 */
function nestedCompletedRun(): AgentRunData {
  const parallel: AgentRunData = {
    id: "nest_par",
    agentName: "nest_par",
    status: AgentStatus.COMPLETED,
    strategy: AgentStrategy.PARALLEL,
    subAgentCount: 2,
    // Expanded in place so grandchildren appear on the same canvas.
    expanded: true,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 30,
    turns: [completedTurn(1, [leaf("nest_b"), leaf("nest_c")])],
  };

  return {
    id: "nest_root",
    agentName: "nest_root",
    status: AgentStatus.COMPLETED,
    strategy: AgentStrategy.SEQUENTIAL,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 100,
    turns: [
      completedTurn(1, [leaf("nest_a")]),
      completedTurn(2, [parallel]),
      completedTurn(3, [leaf("nest_e")]),
    ],
  };
}

describe("buildAgentExecutionDiagram nesting + COMPLETED", () => {
  it("keeps the root COMPLETED and draws nested parallel grandchildren", () => {
    const run = nestedCompletedRun();
    expect(run.status).toBe(AgentStatus.COMPLETED);

    const { nodes } = buildAgentExecutionDiagram(
      run,
      "",
      false,
      // Expand the parallel siblings group under nest_par (2 < COLLAPSE? wait - 2 might be below threshold)
      new Set(),
    );

    const labels = nodes
      .map((n) => n.data?.label)
      .filter((label): label is string => Boolean(label));

    expect(labels).toContain("nest_root");
    expect(labels).toContain("nest_a");
    expect(labels).toContain("nest_par");
    expect(labels).toContain("nest_b");
    expect(labels).toContain("nest_c");
    expect(labels).toContain("nest_e");

    const agentish = nodes.filter(
      (n) =>
        n.data?.kind === "start" ||
        n.data?.kind === "subagent" ||
        n.data?.kind === "group",
    );
    for (const node of agentish) {
      expect(
        node.data?.ts,
        `${node.data?.label} should render as COMPLETED`,
      ).toBe("COMPLETED");
    }
  });
});
