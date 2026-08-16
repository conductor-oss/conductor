import { render, screen, within } from "@testing-library/react";
import { AgentMetadataSnapshot } from "types";
import { AgentSnapshotDetails } from "./AgentSnapshotDetails";

describe("AgentSnapshotDetails", () => {
  it("renders Conductor model, instructions, categorized tools, and guardrails", () => {
    const snapshot: AgentMetadataSnapshot = {
      schemaVersion: 1,
      agentType: "conductor",
      displayName: "Research Agent",
      source: { name: "researcher" },
      resolved: true,
      conductor: {
        name: "researcher",
        resolvedVersion: 4,
        framework: "openai-agents",
        normalization: "normalized",
        agentConfig: {
          name: "Research Agent",
          model: "openai/gpt-5",
          instructions: "Research carefully and cite sources.",
          tools: [
            { name: "worker_search", toolType: "tool" },
            { name: "fetch_url", toolType: "http" },
            { name: "docs", toolType: "rag" },
            { name: "reviewer", toolType: "agent_tool" },
          ],
          input_guardrails: [{ guardrail_function: { name: "safe_input" } }],
          output_guardrails: [{ guardrail_function: { name: "safe_output" } }],
          strategy: "react",
          max_turns: 8,
        },
      },
    };

    render(<AgentSnapshotDetails snapshot={snapshot} />);

    expect(screen.getByTestId("agent-card")).toHaveAttribute(
      "data-agent-type",
      "conductor",
    );
    expect(screen.getAllByText("Research Agent")).toHaveLength(2);
    expect(screen.getByText("Conductor")).toBeInTheDocument();
    expect(screen.getByText("openai/gpt-5")).toBeInTheDocument();
    expect(
      screen.getByText("Research carefully and cite sources."),
    ).toBeInTheDocument();
    expect(screen.getByText("worker_search")).toBeInTheDocument();
    expect(screen.getByText("fetch_url")).toBeInTheDocument();
    expect(screen.getByText("docs")).toBeInTheDocument();
    expect(screen.getByText("reviewer")).toBeInTheDocument();
    expect(screen.getByText("Input: safe_input")).toBeInTheDocument();
    expect(screen.getByText("Output: safe_output")).toBeInTheDocument();
    expect(screen.getByText("react")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
  });

  it("renders standard A2A skills as skills rather than tools", () => {
    const snapshot: AgentMetadataSnapshot = {
      schemaVersion: 1,
      agentType: "a2a",
      displayName: "Travel Agent",
      source: { url: "https://agent.example" },
      resolved: true,
      a2a: {
        url: "https://agent.example",
        agentCard: {
          name: "Travel Agent",
          description: "Plans trips",
          version: "1.0",
          capabilities: { streaming: true, pushNotifications: true },
          supportedInterfaces: [
            {
              url: "https://agent.example/rpc",
              protocolBinding: "JSONRPC",
              protocolVersion: "1.0",
            },
          ],
          skills: [
            {
              id: "plan",
              name: "Plan trip",
              description: "Build an itinerary",
              tags: ["travel"],
              examples: ["Plan a week in Japan"],
            },
          ],
        },
      },
    };

    render(<AgentSnapshotDetails snapshot={snapshot} />);

    expect(screen.getByTestId("agent-card")).toHaveAttribute(
      "data-agent-type",
      "a2a",
    );
    expect(screen.getByText("Travel Agent")).toBeInTheDocument();
    expect(screen.getByText("A2A")).toBeInTheDocument();
    expect(screen.getByText("Protocol & capabilities")).toBeInTheDocument();
    expect(screen.getByText("Skills (1)")).toBeInTheDocument();
    expect(screen.getByText("Plan trip")).toBeInTheDocument();
    expect(screen.getByText("Streaming")).toBeInTheDocument();
    expect(screen.getByText("Push notifications")).toBeInTheDocument();
    expect(screen.queryByText(/Tools \(1\)/)).not.toBeInTheDocument();
  });

  it("shows implementation details only when extension parameters advertise them", () => {
    const snapshot: AgentMetadataSnapshot = {
      schemaVersion: 1,
      agentType: "a2a",
      displayName: "Enriched Agent",
      source: { url: "https://agent.example" },
      resolved: true,
      a2a: {
        url: "https://agent.example",
        agentCard: {
          name: "Enriched Agent",
          capabilities: {
            extensions: [
              {
                uri: "urn:conductor:agent-details:v1",
                description: "Public implementation details",
                params: {
                  model: "anthropic/claude",
                  instructions: "Use the advertised policy.",
                  tools: [{ name: "advertised_search", toolType: "tool" }],
                },
              },
            ],
          },
        },
      },
    };

    render(<AgentSnapshotDetails snapshot={snapshot} />);

    const advertised = screen
      .getByText("Configuration advertised by extensions")
      .closest("div");
    expect(advertised).not.toBeNull();
    expect(screen.getByText("anthropic/claude")).toBeInTheDocument();
    expect(screen.getByText("advertised_search")).toBeInTheDocument();
    expect(
      within(advertised as HTMLElement).getByText(
        "Configuration advertised by extensions",
      ),
    ).toBeInTheDocument();
  });
});
