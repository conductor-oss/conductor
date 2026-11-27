import { fireEvent, render, screen } from "@testing-library/react";
import { getExecutionBreadcrumbItems } from "./Execution";
import { ReasonForIncompletion } from "./ReasonForIncompletion";

describe("ReasonForIncompletion", () => {
  it("shows a long failure reason in place instead of navigating away", () => {
    const reason = `Task failed: ${"details ".repeat(50)}`;

    render(<ReasonForIncompletion reason={reason} />);

    fireEvent.click(screen.getByText("View full message"));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName === "PRE" && element.textContent === reason,
      ),
    ).toBeInTheDocument();
  });
});

describe("getExecutionBreadcrumbItems", () => {
  it("builds agent execution breadcrumbs from the agent execution route ID", () => {
    expect(
      getExecutionBreadcrumbItems({
        pathname: "/agentExecutions/agent-execution-id",
        executionId: "agent-execution-id",
        workflowId: "workflow-id",
      }),
    ).toEqual([
      { label: "Agent Executions", to: "/agentExecutions" },
      { label: "agent-execution-id", to: "" },
    ]);
  });

  it("builds workflow execution breadcrumbs from the workflow execution ID", () => {
    expect(
      getExecutionBreadcrumbItems({
        pathname: "/execution/route-execution-id",
        executionId: "route-execution-id",
        workflowId: "workflow-id",
      }),
    ).toEqual([
      { label: "Workflow Executions", to: "/executions" },
      { label: "workflow-id", to: "" },
    ]);
  });
});
