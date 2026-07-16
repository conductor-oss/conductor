import { render, screen } from "@testing-library/react";
import LeftPanelTabs from "./LeftPanelTabs";
import { ExecutionTabs } from "./state/types";
import { WorkflowExecution } from "types/Execution";

const baseExecution = {
  workflowId: "exec-1",
  tasks: [],
} as unknown as WorkflowExecution;

const agentExecution = {
  ...baseExecution,
  workflowDefinition: {
    metadata: { agentDef: { name: "some_agent" } },
  },
} as unknown as WorkflowExecution;

const plainExecution = {
  ...baseExecution,
  workflowDefinition: { metadata: {} },
} as unknown as WorkflowExecution;

function tabLabels() {
  return screen.getAllByRole("tab").map((el) => el.textContent);
}

describe("LeftPanelTabs", () => {
  it("shows the AgentSpan-matching curated tab set, in order, for an agent-classified execution", () => {
    render(
      <LeftPanelTabs
        execution={agentExecution}
        openedTab={ExecutionTabs.AGENT_EXECUTION_TAB}
        onChangeExecutionTab={() => {}}
      />,
    );
    expect(tabLabels()).toEqual([
      "Agent Execution",
      "Debug View",
      "Task List",
      "Timeline",
      "Agent Definition",
      "JSON",
    ]);
  });

  it("keeps the full, unrelabeled Conductor tab set for a plain workflow execution", () => {
    render(
      <LeftPanelTabs
        execution={plainExecution}
        openedTab={ExecutionTabs.DIAGRAM_TAB}
        onChangeExecutionTab={() => {}}
      />,
    );
    expect(tabLabels()).toEqual([
      "Diagram",
      "Task List",
      "Timeline",
      "Summary",
      "Workflow Input/Output",
      "JSON",
      "Variables",
      "Tasks to Domain",
    ]);
  });

  it("relabels the Summary tab as 'Agent Definition' but keeps the same underlying SUMMARY_TAB value", () => {
    const onChangeExecutionTab = vi.fn();
    render(
      <LeftPanelTabs
        execution={agentExecution}
        openedTab={ExecutionTabs.AGENT_EXECUTION_TAB}
        onChangeExecutionTab={onChangeExecutionTab}
      />,
    );
    screen.getByRole("tab", { name: "Agent Definition" }).click();
    expect(onChangeExecutionTab).toHaveBeenCalledWith(
      ExecutionTabs.SUMMARY_TAB,
    );
  });
});
