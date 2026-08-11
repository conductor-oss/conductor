import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { cloneElement, isValidElement, useEffect } from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { TaskStatus } from "types";
import { AgentExecutionDiagram } from "./AgentExecutionDiagram";
import { NodeCard } from "./agentExecutionDiagram/NodeCard";
import { NodeStatusBadge } from "./agentExecutionDiagram/NodeStatusBadge";
import { TypeBadge } from "./agentExecutionDiagram/TypeBadge";
import { DiagramControls } from "./agentExecutionDiagram/DiagramControls";
import { AgentRunData, AgentStatus, AgentStrategy, EventType } from "./types";

vi.mock("@use-gesture/react", () => ({
  useDrag: () => () => undefined,
  usePinch: () => () => undefined,
  useWheel: () => () => undefined,
}));

vi.mock("reaflow", () => ({
  Canvas: ({
    nodes,
    onLayoutChange,
    node,
  }: {
    nodes: Array<{
      id: string;
      width?: number;
      height?: number;
      data: unknown;
    }>;
    onLayoutChange?: (result: {
      width: number;
      height: number;
      children: Array<{
        id: string;
        x: number;
        y: number;
        width: number;
        height: number;
      }>;
    }) => void;
    node: React.ReactElement;
  }) => {
    useEffect(() => {
      onLayoutChange?.({
        width: 800,
        height: 600,
        children: nodes.map((n, i) => ({
          id: n.id,
          x: 0,
          y: i * 100,
          width: n.width ?? 264,
          height: n.height ?? 80,
        })),
      });
    }, [nodes, onLayoutChange]);

    return (
      <div data-testid="mock-canvas">
        {nodes.map((n) => (
          <div key={n.id} data-testid={`canvas-node-${n.id}`}>
            {isValidElement(node)
              ? cloneElement(node, {
                  id: n.id,
                  properties: n,
                } as never)
              : null}
          </div>
        ))}
      </div>
    );
  },
  CanvasPosition: { CENTER: "center" },
  Edge: () => null,
  Node: ({
    children,
    properties,
  }: {
    children: (ev: { width: number; height: number }) => React.ReactNode;
    properties?: { width?: number; height?: number };
  }) => {
    // DiagramNode wraps content in SVG <g>/<foreignObject>; flatten for jsdom.
    const content =
      typeof children === "function"
        ? children({
            width: properties?.width ?? 264,
            height: properties?.height ?? 80,
          })
        : children;
    return <div data-testid="mock-reaflow-node">{content}</div>;
  },
}));

const ZERO_TOKENS = {
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
};

beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn();
});

function leaf(
  name: string,
  overrides: Partial<AgentRunData> = {},
): AgentRunData {
  return {
    id: name,
    agentName: name,
    status: AgentStatus.COMPLETED,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 10,
    turns: [],
    ...overrides,
  };
}

function simpleCompletedRun(): AgentRunData {
  return {
    id: "root",
    agentName: "root_agent",
    status: AgentStatus.COMPLETED,
    strategy: AgentStrategy.SEQUENTIAL,
    totalTokens: ZERO_TOKENS,
    totalDurationMs: 50,
    turns: [
      {
        turnNumber: 1,
        status: AgentStatus.COMPLETED,
        durationMs: 10,
        tokens: ZERO_TOKENS,
        events: [],
        subAgents: [
          leaf("child_a", {
            strategy: AgentStrategy.PARALLEL,
            subAgentCount: 2,
          }),
        ],
      },
      {
        turnNumber: 2,
        status: AgentStatus.COMPLETED,
        durationMs: 10,
        tokens: ZERO_TOKENS,
        events: [],
        subAgents: [leaf("child_b")],
      },
    ],
  };
}

describe("TypeBadge / NodeStatusBadge (extracted)", () => {
  it("renders a type label and hides empty badges", () => {
    const { rerender } = render(<TypeBadge label="AGENT" />);
    expect(screen.getByText("AGENT")).toBeInTheDocument();
    rerender(<TypeBadge label="" />);
    expect(screen.queryByText("AGENT")).not.toBeInTheDocument();
  });

  it("renders completed and failed status badges", () => {
    const { container, rerender } = render(
      <NodeStatusBadge status={TaskStatus.COMPLETED} />,
    );
    expect(container.firstChild).toBeTruthy();

    rerender(<NodeStatusBadge status={TaskStatus.FAILED} />);
    expect(container.firstChild).toBeTruthy();

    rerender(<NodeStatusBadge status={TaskStatus.SCHEDULED} />);
    expect(container.firstChild).toBeNull();
  });

  it("shows a spinner for in-progress status", () => {
    render(<NodeStatusBadge status={TaskStatus.IN_PROGRESS} />);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });
});

describe("NodeCard (extracted)", () => {
  const baseProps = {
    width: 264,
    height: 80,
    selected: false,
    onSelect: vi.fn(),
  };

  it("renders start/agent card label", () => {
    render(
      <NodeCard
        {...baseProps}
        data={{
          kind: "start",
          label: "root_agent",
          ts: TaskStatus.COMPLETED,
        }}
      />,
    );
    expect(screen.getByText("root_agent")).toBeInTheDocument();
  });

  it("renders handoff pill", () => {
    render(
      <NodeCard
        {...baseProps}
        data={{
          kind: "handoff",
          label: "researcher",
          ts: TaskStatus.COMPLETED,
        }}
      />,
    );
    expect(screen.getByText("researcher")).toBeInTheDocument();
    expect(screen.getByText("handoff")).toBeInTheDocument();
  });

  it("renders back node and calls onBack", () => {
    const onBack = vi.fn();
    render(
      <NodeCard
        {...baseProps}
        onBack={onBack}
        data={{ kind: "back", label: "", ts: TaskStatus.COMPLETED }}
      />,
    );
    fireEvent.click(screen.getByText("Back"));
    expect(onBack).toHaveBeenCalled();
  });

  it("renders next-turn separator", () => {
    render(
      <NodeCard
        {...baseProps}
        data={{
          kind: "next",
          label: "Turn 2",
          nextTurn: "turn-2",
          ts: TaskStatus.COMPLETED,
        }}
      />,
    );
    expect(screen.getByText("Turn")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("offers View execution and Expand for sub-agents with children", () => {
    const onDrillIn = vi.fn();
    const onExpand = vi.fn();
    const sub = leaf("nested_coord", { subAgentCount: 2 });
    render(
      <NodeCard
        {...baseProps}
        onDrillIn={onDrillIn}
        onExpand={onExpand}
        data={{
          kind: "subagent",
          label: "nested_coord",
          ts: TaskStatus.COMPLETED,
          subAgentRun: sub,
          subAgentCount: 2,
        }}
      />,
    );

    fireEvent.click(screen.getByText(/View execution/));
    expect(onDrillIn).toHaveBeenCalledWith(sub);

    fireEvent.click(screen.getByText("Expand (2)"));
    expect(onExpand).toHaveBeenCalledWith(sub);
  });

  it("renders collapsed group expand affordance", () => {
    const onToggleGroup = vi.fn();
    const agents = Array.from({ length: 12 }, (_, i) => leaf(`a${i}`));
    render(
      <NodeCard
        {...baseProps}
        onToggleGroup={onToggleGroup}
        data={{
          kind: "group",
          label: agents[0].agentName,
          ts: TaskStatus.COMPLETED,
          groupType: "agents",
          groupAgents: agents,
          groupCompleted: 12,
          groupFailed: 0,
          groupRunning: 0,
          strategy: AgentStrategy.PARALLEL,
        }}
      />,
    );
    expect(screen.getByText(/12 agents/)).toBeInTheDocument();
    fireEvent.click(screen.getByText(/Expand \(12 of 12\)/));
    expect(onToggleGroup).toHaveBeenCalled();
  });

  it("shows retry attempt badge when totalAttempts > 1", () => {
    render(
      <NodeCard
        {...baseProps}
        data={{
          kind: "tool",
          label: "search",
          ts: TaskStatus.COMPLETED,
          event: {
            id: "e1",
            type: EventType.TOOL_CALL,
            timestamp: 0,
            summary: "search",
            toolName: "search",
            taskMeta: { totalAttempts: 3 },
          },
        }}
      />,
    );
    expect(screen.getByText(/3 attempts/)).toBeInTheDocument();
  });
});

describe("DiagramControls (extracted)", () => {
  it("exposes fit/zoom controls and wires callbacks", () => {
    const onFit = vi.fn();
    const onZoomIn = vi.fn();
    const onZoomOut = vi.fn();
    const onReset = vi.fn();

    render(
      <DiagramControls
        zoom={1.25}
        onFit={onFit}
        onZoomIn={onZoomIn}
        onZoomOut={onZoomOut}
        onReset={onReset}
      />,
    );

    expect(screen.getByText("125%")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Fit to screen"));
    expect(onFit).toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText("Zoom in"));
    expect(onZoomIn).toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText("Zoom out"));
    expect(onZoomOut).toHaveBeenCalled();
    fireEvent.click(screen.getByLabelText("Reset position"));
    expect(onReset).toHaveBeenCalled();
  });
});

describe("AgentExecutionDiagram (orchestrator after split)", () => {
  it("renders nested run labels through DiagramNode → NodeCard", async () => {
    const onNodeSelect = vi.fn();
    render(
      <AgentExecutionDiagram
        agentRun={simpleCompletedRun()}
        activeTurn="turn-1"
        onSelectTurn={vi.fn()}
        selectedId={null}
        onNodeSelect={onNodeSelect}
      />,
    );

    await waitFor(() => {
      expect(screen.getByTestId("agent-execution-diagram")).toBeInTheDocument();
      expect(screen.getByText("root_agent")).toBeInTheDocument();
      expect(screen.getByText("child_a")).toBeInTheDocument();
      expect(screen.getByText("child_b")).toBeInTheDocument();
    });

    expect(screen.getByLabelText("Fit to screen")).toBeInTheDocument();
  });

  it("selects a start node via NodeCard click", async () => {
    const onNodeSelect = vi.fn();
    render(
      <AgentExecutionDiagram
        agentRun={simpleCompletedRun()}
        activeTurn="turn-1"
        onSelectTurn={vi.fn()}
        selectedId={null}
        onNodeSelect={onNodeSelect}
      />,
    );

    await waitFor(() => screen.getByText("root_agent"));
    fireEvent.click(screen.getByText("root_agent"));
    expect(onNodeSelect).toHaveBeenCalledWith(
      "start",
      expect.objectContaining({
        kind: "start",
        label: "root_agent",
        status: AgentStatus.COMPLETED,
      }),
    );
  });

  it("wires View execution drill-in from a sub-agent card", async () => {
    const onDrillIn = vi.fn();
    render(
      <AgentExecutionDiagram
        agentRun={simpleCompletedRun()}
        activeTurn="turn-1"
        onSelectTurn={vi.fn()}
        selectedId={null}
        onNodeSelect={vi.fn()}
        onDrillIn={onDrillIn}
      />,
    );

    await waitFor(() => screen.getByText("child_a"));
    fireEvent.click(screen.getAllByText(/View execution/)[0]);
    expect(onDrillIn).toHaveBeenCalledWith(
      expect.objectContaining({ agentName: "child_a" }),
    );
  });

  it("wires Expand for sub-agents that declare children", async () => {
    const onExpand = vi.fn();
    render(
      <AgentExecutionDiagram
        agentRun={simpleCompletedRun()}
        activeTurn="turn-1"
        onSelectTurn={vi.fn()}
        selectedId={null}
        onNodeSelect={vi.fn()}
        onExpand={onExpand}
      />,
    );

    await waitFor(() => screen.getByText("Expand (2)"));
    fireEvent.click(screen.getByText("Expand (2)"));
    expect(onExpand).toHaveBeenCalledWith(
      expect.objectContaining({ agentName: "child_a", subAgentCount: 2 }),
    );
  });

  it("renders a back node when onBack is provided", async () => {
    const onBack = vi.fn();
    render(
      <AgentExecutionDiagram
        agentRun={simpleCompletedRun()}
        activeTurn="turn-1"
        onSelectTurn={vi.fn()}
        selectedId={null}
        onNodeSelect={vi.fn()}
        onBack={onBack}
      />,
    );

    await waitFor(() => screen.getByText("Back"));
    fireEvent.click(screen.getByText("Back"));
    expect(onBack).toHaveBeenCalled();
  });
});
