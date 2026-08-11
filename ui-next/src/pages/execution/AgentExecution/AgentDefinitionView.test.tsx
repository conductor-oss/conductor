import { render, screen } from "@testing-library/react";
import { vi } from "vitest";
import {
  AgentDefinitionDiagram,
  AgentDefinitionView,
} from "./AgentDefinitionView";
import { buildDefDiagram } from "./buildDefDiagram";
import { WorkflowExecution } from "types/Execution";

vi.mock("reaflow", () => ({
  Canvas: ({
    nodes,
  }: {
    nodes: Array<{
      id: string;
      data: {
        label?: string;
        sublabel?: string;
        strategy?: string;
        maxTurns?: number;
      };
    }>;
  }) => (
    <div>
      {nodes.map((node) => (
        <div key={node.id} data-testid={`node-${node.id}`}>
          {node.data.label && <span>{node.data.label}</span>}
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

  it("renders strategy on a nested coordinator that itself has sub-agents", () => {
    render(
      <AgentDefinitionDiagram
        agentDef={{
          name: "Root",
          strategy: "sequential",
          agents: [
            {
              name: "Router",
              strategy: "router",
              maxTurns: 3,
              agents: [{ name: "LeafA" }, { name: "LeafB" }],
            },
          ],
        }}
      />,
    );

    expect(screen.getByTestId("strategy-subagent-0")).toHaveTextContent(
      "router",
    );
    expect(screen.getByTestId("turns-subagent-0")).toHaveTextContent("3 turns");
    expect(
      screen.queryByTestId("strategy-subagent-0-0"),
    ).not.toBeInTheDocument();
  });
});

describe("buildDefDiagram — nested strategies", () => {
  /** Mirrors the ztest_15_three_level repro: sequential → router → parallel. */
  const threeLevelAgentDef = {
    name: "ztest_15_three_level",
    strategy: "sequential",
    agents: [
      { name: "ztest_15a", instructions: "classify" },
      {
        name: "ztest_15rou",
        strategy: "router",
        agents: [
          { name: "ztest_15r", instructions: "the router/classifier" },
          {
            name: "ztest_15par",
            strategy: "parallel",
            agents: [
              { name: "ztest_15b", instructions: "infra cause" },
              { name: "ztest_15c", instructions: "code cause" },
            ],
          },
          { name: "ztest_15d", instructions: "known-issue lookup" },
        ],
      },
      { name: "ztest_15e", instructions: "postmortem" },
    ],
  };

  it("draws every nested agent, not only the root's direct children", () => {
    const { nodes } = buildDefDiagram(threeLevelAgentDef);
    const labels = nodes
      .filter((n) => n.data?.kind === "agent" || n.data?.kind === "subagent")
      .map((n) => n.data!.label);

    expect(labels).toEqual([
      "ztest_15_three_level",
      "ztest_15a",
      "ztest_15rou",
      "ztest_15r",
      "ztest_15par",
      "ztest_15b",
      "ztest_15c",
      "ztest_15d",
      "ztest_15e",
    ]);
  });

  it("wires a containment tree so nested coordinators do not absorb later siblings", () => {
    const { edges } = buildDefDiagram(threeLevelAgentDef);
    const pairs = edges.map((e) => `${e.from}→${e.to}`);

    // Root fans out to each direct child (including postmortem) — not a→rou→e.
    expect(pairs).toContain("agent→subagent-0"); // a
    expect(pairs).toContain("agent→subagent-1"); // rou
    expect(pairs).toContain("agent→subagent-2"); // e
    expect(pairs).not.toContain("subagent-1→subagent-2");

    // Router children hang under rou only
    expect(pairs).toContain("subagent-1→subagent-1-0"); // r
    expect(pairs).toContain("subagent-1→subagent-1-1"); // par
    expect(pairs).toContain("subagent-1→subagent-1-2"); // d

    // Parallel children hang under par only
    expect(pairs).toContain("subagent-1-1→subagent-1-1-0"); // b
    expect(pairs).toContain("subagent-1-1→subagent-1-1-1"); // c
  });

  it("hangs sequential gates off their step without reparenting the next sibling", () => {
    const { nodes, edges } = buildDefDiagram({
      name: "Root",
      strategy: "sequential",
      agents: [
        {
          name: "Outer",
          strategy: "sequential",
          agents: [
            {
              name: "StepWithGate",
              gate: { type: "text_contains", text: "ok" },
            },
            { name: "AfterGate" },
          ],
        },
      ],
    });

    expect(nodes.some((n) => n.id === "subagent-0-gate-0")).toBe(true);
    const pairs = edges.map((e) => `${e.from}→${e.to}`);
    expect(pairs).toEqual(
      expect.arrayContaining([
        "subagent-0→subagent-0-0",
        "subagent-0→subagent-0-1",
        "subagent-0-0→subagent-0-gate-0",
      ]),
    );
    expect(pairs).not.toContain("subagent-0-gate-0→subagent-0-1");
  });
});
