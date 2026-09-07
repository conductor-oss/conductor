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
    nodes: Array<{
      id: string;
      data: { sublabel?: string; strategy?: string; maxTurns?: number };
    }>;
  }) => (
    <div>
      {nodes.map((node) => (
        <div key={node.id}>
          {node.data.sublabel && <span>{node.data.sublabel}</span>}
          {node.data.strategy && (
            <span data-testid={`strategy-${node.id}`}>
              {node.data.strategy}
            </span>
          )}
          {node.data.maxTurns !== undefined && (
            <span data-testid={`turns-${node.id}`}>
              {node.data.maxTurns} turns
            </span>
          )}
        </div>
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

describe("AgentDefinitionDiagram — strategy / maxTurns visibility", () => {
  it("does not render strategy badge or turn count on a lone agent with no sub-agents", () => {
    // The SDK defaults strategy=HANDOFF and maxTurns=25 even for single agents.
    // The diagram must suppress these on nodes that have no sub-agents.
    render(
      <AgentDefinitionDiagram
        agentDef={{
          name: "LeafAgent",
          model: "openai/gpt-4o-mini",
          strategy: "handoff",
          maxTurns: 25,
        }}
      />,
    );

    expect(screen.queryByTestId("strategy-agent")).not.toBeInTheDocument();
    expect(screen.queryByTestId("turns-agent")).not.toBeInTheDocument();
  });

  it("renders strategy and maxTurns on a coordinator that has sub-agents", () => {
    render(
      <AgentDefinitionDiagram
        agentDef={{
          name: "Coordinator",
          model: "openai/gpt-4o-mini",
          strategy: "sequential",
          maxTurns: 10,
          agents: [
            { name: "Step1", instructions: "do step 1" },
            { name: "Step2", instructions: "do step 2" },
          ],
        }}
      />,
    );

    expect(screen.getByTestId("strategy-agent")).toHaveTextContent(
      "sequential",
    );
    expect(screen.getByTestId("turns-agent")).toHaveTextContent("10 turns");
  });

  it("does not render strategy badge on a leaf sub-agent even when the server sends a default strategy", () => {
    // The server may echo the SDK-level HANDOFF default on leaf sub-agents.
    // The coordinator's own strategy should still be shown; only the leaf's should be suppressed.
    render(
      <AgentDefinitionDiagram
        agentDef={{
          name: "Coordinator",
          strategy: "sequential",
          maxTurns: 5,
          agents: [
            {
              name: "LeafChild",
              strategy: "handoff",
              maxTurns: 25,
              // no agents[] — this is a leaf
            },
          ],
        }}
      />,
    );

    // Coordinator root: strategy and turns shown
    expect(screen.getByTestId("strategy-agent")).toHaveTextContent(
      "sequential",
    );
    expect(screen.getByTestId("turns-agent")).toHaveTextContent("5 turns");

    // Leaf sub-agent: strategy and turns suppressed
    expect(screen.queryByTestId("strategy-subagent-0")).not.toBeInTheDocument();
    expect(screen.queryByTestId("turns-subagent-0")).not.toBeInTheDocument();
  });
});
