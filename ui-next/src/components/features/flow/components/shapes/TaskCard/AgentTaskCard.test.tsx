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

  it("renders configured identity as unresolved in the execution diagram", () => {
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
      screen.getByText("UNRESOLVED: ${workflow.input.agentUrl}"),
    ).toBeInTheDocument();
    expect(screen.getByText("A2A AGENT")).toBeInTheDocument();
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
