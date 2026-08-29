import { render, screen } from "@testing-library/react";
import TaskSummary from "./TaskSummary";
import { ExecutionTask } from "types";

const agentTask = (outputData: Record<string, unknown>) =>
  ({
    taskType: "AGENT",
    status: "IN_PROGRESS",
    workflowTask: { type: "AGENT", name: "ask_the_analyst" },
    referenceTaskName: "ask_the_analyst",
    inputData: { agentType: "microsoft-foundry" },
    outputData,
  }) as unknown as ExecutionTask;

describe("TaskSummary — hosted agent tool calls", () => {
  it("lists every tool the agent asked for", () => {
    render(
      <TaskSummary
        taskResult={agentTask({
          pendingTools: [
            { tool_name: "get_revenue", tool_call_id: "call-1" },
            { tool_name: "get_headcount", tool_call_id: "call-2" },
          ],
        })}
      />,
    );

    // Both, so a two-tool turn is visible without leaving the page.
    expect(screen.getByText("Tools requested")).toBeInTheDocument();
    expect(
      screen.getByText("get_revenue (call-1), get_headcount (call-2)"),
    ).toBeInTheDocument();
  });

  it("uses the singular label for a single tool", () => {
    render(
      <TaskSummary
        taskResult={agentTask({
          pendingTools: [{ tool_name: "get_revenue", tool_call_id: "call-1" }],
        })}
      />,
    );

    expect(screen.getByText("Tool requested")).toBeInTheDocument();
  });

  it("links to the run the tools execute in", () => {
    render(
      <TaskSummary taskResult={agentTask({ toolDispatchId: "tools-1" })} />,
    );

    expect(screen.getByText("Tool run")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "tools-1" })).toHaveAttribute(
      "href",
      expect.stringContaining("/execution/tools-1"),
    );
  });

  it("shows nothing when the agent is not waiting on tools", () => {
    render(<TaskSummary taskResult={agentTask({ executionId: "thread-1" })} />);

    expect(screen.queryByText("Tools requested")).not.toBeInTheDocument();
    expect(screen.queryByText("Tool requested")).not.toBeInTheDocument();
    expect(screen.queryByText("Tool run")).not.toBeInTheDocument();
  });

  it("ignores malformed tool entries rather than rendering junk", () => {
    render(
      <TaskSummary
        taskResult={agentTask({
          pendingTools: [null, { tool_call_id: "no-name" }, "nonsense"],
        })}
      />,
    );

    expect(screen.queryByText("Tools requested")).not.toBeInTheDocument();
  });
});
