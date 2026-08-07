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
  it("builds agent execution breadcrumbs with the route execution ID", () => {
    const breadcrumbItems = getExecutionBreadcrumbItems(
      "/agentExecutions/agent-execution-id",
      "workflow-execution-id",
      "agent-execution-id",
    );

    expect(breadcrumbItems[0]).toMatchObject({
      label: "Agent Executions",
      to: "/agentExecutions",
    });
    expect(breadcrumbItems[1]).toMatchObject({
      label: "agent-execution-id",
      to: "",
      icon: {
        props: { text: "agent-execution-id" },
      },
    });
  });

  it("builds workflow execution breadcrumbs with the workflow ID", () => {
    const breadcrumbItems = getExecutionBreadcrumbItems(
      "/execution/workflow-execution-id",
      "workflow-execution-id",
      "agent-execution-id",
    );

    expect(breadcrumbItems[0]).toMatchObject({
      label: "Workflow Executions",
      to: "/executions",
    });
    expect(breadcrumbItems[1]).toMatchObject({
      label: "workflow-execution-id",
      to: "",
      icon: {
        props: { text: "workflow-execution-id" },
      },
    });
  });
});
