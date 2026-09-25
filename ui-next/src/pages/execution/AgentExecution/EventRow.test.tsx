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

describe("Jev inference rendering", () => {
  it.each(["task", "jev event"])(
    "renders %s as jev_decision with structured inference details",
    (source) => {
      const run = transformWorkflowExecutionToAgentRun({
        workflowId: "run-1",
        workflowName: "jev_support_agent",
        status: "COMPLETED",
        tasks: [
          {
            taskId: "task-1",
            referenceTaskName: "support_jev",
            taskType: "JEV_AGENT",
            status: "COMPLETED",
            inputData: { model: "jev-1.13", state: "Duplicate charge" },
            outputData: output,
          },
        ],
        workflowDefinition: { metadata: { agentDef: { kind: "jev" } } },
      } as unknown as WorkflowExecution);
      const events = run.turns.flatMap((turn) => turn.events);
      expect(
        events.filter((event) => event.type === EventType.TOOL_CALL),
      ).toHaveLength(0);
      expect(run.output).toEqual(output);
      // The server's existing SSE payload puts structured answers in result.
      const event: AgentEvent =
        source === "task"
          ? events.find((event) => event.type === EventType.JEV)!
          : {
              id: "event-1",
              type: EventType.JEV,
              timestamp: 0,
              summary: "",
              result: output,
            };
      expect(event.type).toBe("jev");
      render(<EventRow event={event} />);
      const label = screen.getByText("jev_decision");
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
          type: EventType.JEV,
          timestamp: 0,
          summary: "",
          result: { ...output, requestId: undefined },
        }}
      />,
    );
    fireEvent.click(screen.getByText("jev_decision"));
    expect(screen.queryByText("Request ID")).not.toBeInTheDocument();
  });
});
