import { render, screen } from "@testing-library/react";
import { AgentDefinitionView } from "./AgentDefinitionView";
import { WorkflowExecution } from "types/Execution";

describe("AgentDefinitionView", () => {
  it("shows a fallback message when the workflow definition has no agentDef metadata", () => {
    const execution = {
      workflowDefinition: { metadata: {} },
    } as unknown as WorkflowExecution;

    render(<AgentDefinitionView execution={execution} />);

    expect(
      screen.getByText("No agent definition found in workflow metadata"),
    ).toBeInTheDocument();
  });
});
