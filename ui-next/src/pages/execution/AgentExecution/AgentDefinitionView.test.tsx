import { render, screen } from "@testing-library/react";
import { vi } from "vitest";
import {
  AgentDefinitionDiagram,
  AgentDefinitionView,
} from "./AgentDefinitionView";
import { WorkflowExecution } from "types/Execution";

vi.mock("reaflow", () => ({
  Canvas: ({
    nodes,
  }: {
    nodes: Array<{ id: string; data: { sublabel?: string } }>;
  }) => (
    <div>
      {nodes.map((node) => (
        <span key={node.id}>{node.data.sublabel}</span>
      ))}
    </div>
  ),
  CanvasPosition: { CENTER: "center" },
  Edge: () => null,
  Node: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

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

  it("renders prompt-template instruction references without crashing", () => {
    render(
      <AgentDefinitionDiagram
        agentDef={{
          name: "Publisher",
          instructions: {
            type: "prompt_template",
            name: "content-publisher",
            version: 2,
          },
          agents: [
            {
              name: "Editor",
              instructions: {
                type: "prompt_template",
                name: "editorial-style",
              },
            },
          ],
        }}
      />,
    );

    expect(
      screen.getByText("Prompt template: content-publisher (v2)"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Prompt template: editorial-style"),
    ).toBeInTheDocument();
  });
});
