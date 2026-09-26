import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EventRow } from "./EventRow";
import { transformWorkflowExecutionToAgentRun } from "./agentExecutionUtils";
import { AgentEvent, EventType } from "./types";
import { WorkflowExecution } from "types/Execution";

const output = {
  model: "jev-1.13",
  answers: {
    department: { type: "choice", choice: "billing", confidence: 0.9 },
  },
  usage: { inputTokens: 12, outputTokens: 2, cost: 0.001, currency: "USD" },
  latencyMs: 25,
  requestId: "request-1",
};

describe("Decision inference rendering", () => {
  it.each(["task", "decision event"])(
    "renders %s as decision with structured inference details",
    (source) => {
      const run = transformWorkflowExecutionToAgentRun({
        workflowId: "run-1",
        workflowName: "decision_support_agent",
        status: "COMPLETED",
        tasks: [
          {
            taskId: "task-1",
            referenceTaskName: "support_decision",
            taskType: "AI_DECISION",
            status: "COMPLETED",
            inputData: { model: "jev-1.13", state: "Duplicate charge" },
            outputData: output,
          },
        ],
        workflowDefinition: { metadata: { agentDef: {} } },
      } as unknown as WorkflowExecution);
      const events = run.turns.flatMap((turn) => turn.events);
      expect(
        events.filter((event) => event.type === EventType.TOOL_CALL),
      ).toHaveLength(0);
      expect(run.output).toEqual(output);
      // The server's existing SSE payload puts structured answers in result.
      const event: AgentEvent =
        source === "task"
          ? events.find((event) => event.type === EventType.DECISION)!
          : {
              id: "event-1",
              type: EventType.DECISION,
              timestamp: 0,
              summary: "",
              result: output,
            };
      expect(event.type).toBe("decision");
      render(<EventRow event={event} />);
      const label = screen.getByText("decision");
      expect(label.closest(".MuiChip-root")).toHaveStyle({ color: "#9e9e9e" });
      expect(screen.queryByText("Tool")).not.toBeInTheDocument();
      fireEvent.click(label);
      expect(screen.getByText("Model")).toBeInTheDocument();
      expect(screen.getAllByText("jev-1.13").length).toBeGreaterThan(0);
      expect(
        screen.getByText(JSON.stringify(output.answers, null, 2), {
          normalizer: (text) => text,
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByText(JSON.stringify(output.usage, null, 2), {
          normalizer: (text) => text,
        }),
      ).toBeInTheDocument();
      expect(screen.getByText("latencyMs")).toBeInTheDocument();
      expect(screen.getByText("25")).toBeInTheDocument();
      expect(screen.getByText("request-1")).toBeInTheDocument();
    },
  );

  it("omits an unreported request ID", () => {
    render(
      <EventRow
        event={{
          id: "event-2",
          type: EventType.DECISION,
          timestamp: 0,
          summary: "",
          result: { ...output, requestId: undefined },
        }}
      />,
    );
    fireEvent.click(screen.getByText("decision"));
    expect(screen.queryByText("Request ID")).not.toBeInTheDocument();
  });
});
