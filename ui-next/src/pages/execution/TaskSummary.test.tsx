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

  it("shows the tools the platform ran itself, with what each was given", () => {
    render(
      <TaskSummary
        taskResult={agentTask({
          executedTools: [
            {
              type: "web_search_call",
              tool_call_id: "ws_1",
              status: "completed",
              action: { type: "search", query: "GOOGL 1 month return" },
            },
            {
              type: "code_interpreter",
              tool_call_id: "ci_1",
              input: "print(6*7)",
            },
          ],
        })}
      />,
    );

    // These never pause the run, so the execution view is the only place they can surface.
    expect(screen.getByText("Tools run by agent")).toBeInTheDocument();
    expect(screen.getByText("web_search_call")).toBeInTheDocument();
    expect(screen.getByText(/GOOGL 1 month return/)).toBeInTheDocument();
    expect(screen.getByText("code_interpreter")).toBeInTheDocument();
    expect(screen.getByText("print(6*7)")).toBeInTheDocument();
  });

  it("ignores an executedTools entry with no type", () => {
    render(
      <TaskSummary taskResult={agentTask({ executedTools: [{}, null, 7] })} />,
    );
    expect(screen.queryByText(/Tools? run by agent/)).not.toBeInTheDocument();
  });

  it("says when a task is scheduled but nothing is polling for it", () => {
    // The usual reason an agent looks stuck: it asked for a tool no worker serves.
    render(
      <TaskSummary
        taskResult={
          {
            taskType: "get_revenue",
            status: "SCHEDULED",
            workflowTask: { type: "SIMPLE", name: "get_revenue" },
            referenceTaskName: "agent_ref__t1__get_revenue",
            inputData: {},
            outputData: {},
          } as unknown as ExecutionTask
        }
      />,
    );

    expect(screen.getByText("Waiting for a worker")).toBeInTheDocument();
    expect(
      screen.getByText(/No worker has polled "get_revenue"/),
    ).toBeInTheDocument();
  });

  it("says nothing about workers once one has polled", () => {
    render(
      <TaskSummary
        taskResult={
          {
            taskType: "get_revenue",
            status: "SCHEDULED",
            pollCount: 3,
            workflowTask: { type: "SIMPLE", name: "get_revenue" },
            referenceTaskName: "agent_ref__t1__get_revenue",
            inputData: {},
            outputData: {},
          } as unknown as ExecutionTask
        }
      />,
    );

    expect(screen.queryByText("Waiting for a worker")).not.toBeInTheDocument();
  });
});
