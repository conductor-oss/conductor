import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeAll, vi } from "vitest";
import { AgentRunView } from "./AgentRunView";
import { AgentRunData, AgentStatus, AgentStrategy } from "./types";

vi.mock("./AgentExecutionDiagram", () => ({
  AgentExecutionDiagram: ({ agentRun, onNodeSelect }: any) => {
    const subAgent = agentRun.turns[0].subAgents[0];
    return (
      <button
        onClick={() =>
          onNodeSelect(`sub-${subAgent.id}`, {
            kind: "subagent",
            label: subAgent.agentName,
            status: subAgent.status,
            strategy: AgentStrategy.HANDOFF,
            subAgentRun: subAgent,
          })
        }
      >
        Select claim verifier
      </button>
    );
  },
}));

vi.mock("./agentExecutionUtils", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./agentExecutionUtils")>()),
  transformWorkflowExecutionToAgentRun: (execution: any) => execution.__run,
}));

const ZERO_TOKENS = {
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
};

beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn();
});

function subAgent(
  status: AgentStatus,
  output?: string,
  executionId = "claim-verifier-execution",
): AgentRunData {
  return {
    id: "claim-verifier-run",
    subWorkflowId: executionId,
    agentName: "claim_verifier",
    turns: [],
    status,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 0,
    strategy: AgentStrategy.SINGLE,
    output,
  };
}

function rootRun(child: AgentRunData): AgentRunData {
  return {
    id: "root-run",
    agentName: "root_agent",
    status: child.status,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 0,
    turns: [
      {
        turnNumber: 1,
        events: [],
        status: child.status,
        durationMs: 0,
        tokens: ZERO_TOKENS,
        subAgents: [child],
      },
    ],
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AgentRunView", () => {
  it.each(["RUNNING", "PAUSED"])(
    "refreshes a selected sub-agent after %s execution detail completes",
    async (initialExecutionStatus) => {
      const executionId = `claim-verifier-${initialExecutionStatus}`;
      const runningChild = subAgent(
        AgentStatus.RUNNING,
        undefined,
        executionId,
      );
      const completedChild = subAgent(
        AgentStatus.COMPLETED,
        "verified claim output",
        executionId,
      );
      const fetchMock = vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({
            status: initialExecutionStatus,
            __run: runningChild,
          }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({ status: "COMPLETED", __run: completedChild }),
        });
      vi.stubGlobal("fetch", fetchMock);

      const { rerender } = render(
        <AgentRunView
          agentRun={rootRun(runningChild)}
          onDrillIn={vi.fn()}
          isRoot
        />,
      );

      fireEvent.click(screen.getByText("Select claim verifier"));
      await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
      fireEvent.click(screen.getByRole("tab", { name: "Output" }));
      expect(
        screen.getByText(/No output captured for this execution/),
      ).toBeInTheDocument();

      rerender(
        <AgentRunView
          agentRun={rootRun(completedChild)}
          onDrillIn={vi.fn()}
          isRoot
        />,
      );

      await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
      expect(
        await screen.findByText("verified claim output"),
      ).toBeInTheDocument();
    },
  );
});
