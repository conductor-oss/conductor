import { render, screen } from "@testing-library/react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import { AgentTaskDef, TaskStatus, TaskType } from "types";
import TaskCard from "./TaskCard";

vi.mock("./CardIcon", () => ({ default: () => null }));

const baseTask: AgentTaskDef = {
  name: "agent",
  taskReferenceName: "research_ref",
  type: TaskType.AGENT,
  inputParameters: {
    agentType: "conductor" as const,
    name: "researcher",
  },
};

describe("AGENT task diagram card", () => {
  it("renders resolved identity and type in the definition diagram", () => {
    render(
      <TaskCard
        nodeData={{
          crumbs: [],
          task: {
            ...baseTask,
            metadata: {
              agent: {
                schemaVersion: 1,
                agentType: "conductor",
                displayName: "Research Agent",
                source: { name: "researcher" },
                resolved: true,
                conductor: { name: "researcher", resolvedVersion: 4 },
              },
            },
          } as AgentTaskDef,
        }}
        onClick={vi.fn()}
        isInconsistent={false}
      />,
    );

    expect(screen.getByText("Research Agent")).toBeInTheDocument();
    expect(screen.getByText("research_ref")).toBeInTheDocument();
    expect(screen.getByText("CONDUCTOR AGENT")).toBeInTheDocument();
  });

  it("renders a configured Conductor agent without requiring a snapshot", () => {
    render(
      <TaskCard
        nodeData={{
          crumbs: [],
          task: {
            ...baseTask,
            taskReferenceName: "agent__ref",
            inputParameters: {
              agentType: "conductor",
              name: "greeter",
            },
          } as AgentTaskDef,
        }}
        onClick={vi.fn()}
        isInconsistent={false}
      />,
    );

    expect(screen.getByText("greeter")).toBeInTheDocument();
    expect(screen.getByText("agent__ref")).toBeInTheDocument();
    expect(screen.getByText("CONDUCTOR AGENT")).toBeInTheDocument();
    expect(screen.queryByText(/UNRESOLVED/)).not.toBeInTheDocument();
  });

  it("never shows an UNRESOLVED label, even for a dynamic identity with a failed snapshot", () => {
    render(
      <TaskCard
        nodeData={{
          crumbs: [],
          status: TaskStatus.IN_PROGRESS,
          task: {
            ...baseTask,
            inputParameters: {
              agentUrl: "${workflow.input.agentUrl}",
            },
            metadata: {
              agent: {
                schemaVersion: 1,
                agentType: "a2a",
                displayName: "${workflow.input.agentUrl}",
                source: {
                  url: "${workflow.input.agentUrl}",
                  expression: "${workflow.input.agentUrl}",
                },
                resolved: false,
                a2a: { url: "${workflow.input.agentUrl}" },
              },
            },
          } as AgentTaskDef,
        }}
        onClick={vi.fn()}
        isInconsistent={false}
      />,
    );

    expect(
      screen.getByText("${workflow.input.agentUrl}"),
    ).toBeInTheDocument();
    expect(screen.getByText("A2A AGENT")).toBeInTheDocument();
    expect(screen.queryByText(/UNRESOLVED/)).not.toBeInTheDocument();
  });

  it("renders a static A2A agent URL without a snapshot as resolved, not unresolved", () => {
    // Workflows registered outside the UI editor (curl, SDK, an older save) never get a
    // `metadata.agent` snapshot stamped — absence of a snapshot must not read as a failed
    // resolution for a concrete, non-expression agentUrl.
    render(
      <TaskCard
        nodeData={{
          crumbs: [],
          status: TaskStatus.IN_PROGRESS,
          task: {
            ...baseTask,
            taskReferenceName: "call_summarizer_ref",
            inputParameters: {
              agentType: "a2a",
              agentUrl: "https://agents.example.com/summarizer",
            },
          } as AgentTaskDef,
        }}
        onClick={vi.fn()}
        isInconsistent={false}
      />,
    );

    expect(
      screen.getByText("https://agents.example.com/summarizer"),
    ).toBeInTheDocument();
    expect(screen.getByText("A2A AGENT")).toBeInTheDocument();
    expect(screen.queryByText(/UNRESOLVED/)).not.toBeInTheDocument();
  });

  it("badges Bedrock and Azure Foundry by their own type, not A2A", () => {
    render(
      <TaskCard
        nodeData={{
          crumbs: [],
          task: {
            ...baseTask,
            taskReferenceName: "bedrock_ref",
            inputParameters: {
              agentType: "bedrock",
              agentUrl: "bedrock://AGENTID/ALIASID",
            },
          } as AgentTaskDef,
        }}
        onClick={vi.fn()}
        isInconsistent={false}
      />,
    );

    expect(screen.getByText("bedrock://AGENTID/ALIASID")).toBeInTheDocument();
    expect(screen.getByText("BEDROCK AGENT")).toBeInTheDocument();
    expect(screen.queryByText("A2A AGENT")).not.toBeInTheDocument();
  });

  it("prefers the live configured agentType over a stale cached snapshot", () => {
    // Switching the radio from A2A to Azure Foundry updates inputParameters immediately, but
    // metadata.agent only refreshes on the next save — the badge must reflect the live value.
    render(
      <TaskCard
        nodeData={{
          crumbs: [],
          task: {
            ...baseTask,
            taskReferenceName: "switched_ref",
            inputParameters: {
              agentType: "azure-foundry",
              agentUrl: "https://my-resource.openai.azure.com/openai",
            },
            metadata: {
              agent: {
                schemaVersion: 1,
                agentType: "a2a",
                displayName: "Stale A2A Agent",
                source: { url: "https://old-url.example" },
                resolved: true,
                a2a: { url: "https://old-url.example" },
              },
            },
          } as AgentTaskDef,
        }}
        onClick={vi.fn()}
        isInconsistent={false}
      />,
    );

    expect(screen.getByText("AZURE FOUNDRY AGENT")).toBeInTheDocument();
    expect(
      screen.getByText("https://my-resource.openai.azure.com/openai"),
    ).toBeInTheDocument();
    expect(screen.queryByText("A2A AGENT")).not.toBeInTheDocument();
    expect(screen.queryByText("Stale A2A Agent")).not.toBeInTheDocument();
  });

  it("keeps the compact agent identity legible in dark mode", () => {
    const { container } = render(
      <ColorModeContext.Provider value={{ mode: "dark" }}>
        <TaskCard
          nodeData={{
            crumbs: [],
            task: {
              ...baseTask,
              metadata: {
                agent: {
                  schemaVersion: 1,
                  agentType: "conductor",
                  displayName: "Research Agent",
                  source: { name: "researcher" },
                  resolved: true,
                  conductor: { name: "researcher", resolvedVersion: 4 },
                },
              },
            } as AgentTaskDef,
          }}
          onClick={vi.fn()}
          isInconsistent={false}
        />
      </ColorModeContext.Provider>,
    );

    const cardContent = container.firstElementChild?.firstElementChild;
    expect(cardContent).toHaveStyle({
      background: colors.gray04,
      color: colors.gray14,
      boxShadow: "0 0 10px gray",
    });
    expect(screen.getByText("Research Agent")).toBeInTheDocument();
    expect(screen.getByText("CONDUCTOR AGENT")).toBeInTheDocument();
  });
});
