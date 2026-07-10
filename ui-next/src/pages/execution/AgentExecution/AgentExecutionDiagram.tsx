/**
 * AgentExecutionDiagram — same visual language as Conductor Debug View.
 *
 * Pan/zoom architecture matches PanAndZoomWrapper exactly:
 *   - Canvas: pannable={false}, zoomable={false}  (no built-in scroll)
 *   - Outer viewport div: overflow:hidden, captures gestures via @use-gesture
 *   - Inner transform div: CSS translate+scale for unrestricted panning
 *   - Layout sizing: track ELK result dimensions in state, give Canvas container
 *     explicit pixel size so reaflow's useDimensions can measure correctly.
 */
import { useRef, useCallback, useEffect, useMemo, useState } from "react";
import { Box, CircularProgress } from "@mui/material";
import { useDrag, usePinch, useWheel } from "@use-gesture/react";
import { ZoomControlsButton } from "components/ZoomControlsButton";
import HomeIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Home";
import MinusIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Minus";
import PlusIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Plus";
import FitToFrame from "shared/icons/FitToFrame";
import { colors } from "theme/tokens/variables";
import {
  Canvas,
  CanvasPosition,
  Edge,
  Node,
  NodeData,
  EdgeData,
  PortData,
  PortSide,
} from "reaflow";
import { getCardVariant } from "components/features/flow/components/shapes/styles";
import { ArrowRight, Check, Prohibit } from "@phosphor-icons/react";
import CardIcon from "components/features/flow/components/shapes/TaskCard/CardIcon";
import { TaskStatus, TaskType } from "types";
import {
  AgentEvent,
  AgentRunData,
  AgentStatus,
  AgentStrategy,
  AgentTurn,
  EventType,
} from "./types";
import { DetailNodeData } from "./AgentDetailPanel";
import {
  formatTokens,
  formatDuration,
  getModelIconPath,
} from "./agentExecutionUtils";
import "components/features/flow/ReaflowOverrides.scss";

// ─── Constants ────────────────────────────────────────────────────────────────
const EDGE_DEFAULT = "#757575";
const EDGE_COMPLETED = "#40BA56";
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 2.5;

// ─── Types ────────────────────────────────────────────────────────────────────
type Kind =
  | "start"
  | "llm"
  | "tool"
  | "handoff"
  | "subagent"
  | "output"
  | "error"
  | "next"
  | "back"
  | "group"
  | "junction"
  | "ellipsis";

const KIND_TYPE: Record<Kind, TaskType> = {
  start: TaskType.SUB_WORKFLOW,
  subagent: TaskType.SUB_WORKFLOW,
  handoff: TaskType.SET_VARIABLE,
  llm: TaskType.LLM_CHAT_COMPLETE,
  tool: TaskType.SIMPLE,
  output: TaskType.SIMPLE,
  error: TaskType.TERMINATE,
  next: TaskType.SIMPLE,
  back: TaskType.SIMPLE,
  group: TaskType.SIMPLE,
  junction: TaskType.SIMPLE,
  ellipsis: TaskType.SIMPLE,
};

const KIND_LABEL: Record<Kind, string> = {
  start: "AGENT",
  subagent: "AGENT",
  handoff: "HANDOFF",
  llm: "LLM CALL",
  tool: "TOOL",
  output: "OUTPUT",
  error: "ERROR",
  next: "",
  back: "",
  group: "",
  junction: "",
  ellipsis: "",
};

function toTS(s?: AgentStatus): TaskStatus {
  if (s === AgentStatus.FAILED) return TaskStatus.FAILED;
  if (s === AgentStatus.RUNNING) return TaskStatus.IN_PROGRESS;
  if (s === AgentStatus.WAITING) return TaskStatus.SCHEDULED;
  return TaskStatus.COMPLETED;
}

const STRATEGY_BADGE: Record<AgentStrategy, string> = {
  [AgentStrategy.HANDOFF]: "HANDOFF",
  [AgentStrategy.PARALLEL]: "PARALLEL",
  [AgentStrategy.SEQUENTIAL]: "SEQUENTIAL",
  [AgentStrategy.ROUTER]: "ROUTER",
  [AgentStrategy.SINGLE]: "AGENT",
};

interface DiagramNodeData {
  kind: Kind;
  label: string;
  sublabel?: string;
  meta?: string;
  /** Overrides KIND_LABEL for the TypeBadge (e.g. "GUARDRAIL" on an output/error node) */
  typeLabel?: string;
  /** Strategy used to spawn this node's sub-agent(s) */
  strategy?: AgentStrategy;
  /** Model name for provider icon (LLM and agent nodes) */
  modelName?: string;
  ts: TaskStatus;
  event?: AgentEvent;
  subAgentRun?: AgentRunData;
  nextTurn?: number;
  /** For group nodes */
  groupType?: "agents" | "tools";
  groupAgents?: AgentRunData[];
  groupEvents?: AgentEvent[];
  groupCompleted?: number;
  groupFailed?: number;
  groupRunning?: number;
}

// ─── CardLabel-matching type badge (same CSS as CardLabel.jsx) ─────────────────
function TypeBadge({ label }: { label: string }) {
  if (!label) return null;
  return (
    <div
      style={{
        position: "absolute",
        top: "0px",
        right: "0px",
        height: "fit-content",
        padding: "4px 8px",
        fontSize: "0.8em",
        background: "#dddddd",
        color: "black",
        borderRadius: "5px",
        marginLeft: "8px",
      }}
    >
      {label}
    </div>
  );
}

// ─── Small status badge (20×20 instead of CardStatusBadge's 30×30) ──────────
function NodeStatusBadge({ status }: { status: TaskStatus }) {
  const size = 20;
  const half = size / 2;
  if (status === TaskStatus.IN_PROGRESS) {
    return (
      <div
        style={{
          position: "absolute",
          top: -half,
          right: -half,
          width: size,
          height: size,
          zIndex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <CircularProgress size={size} thickness={3} sx={{ color: "#f59e0b" }} />
        <div
          style={{
            position: "absolute",
            width: 6,
            height: 6,
            borderRadius: "50%",
            backgroundColor: "#f59e0b",
          }}
        />
      </div>
    );
  }
  if (status !== TaskStatus.COMPLETED && status !== TaskStatus.FAILED)
    return null;
  const bg = status === TaskStatus.COMPLETED ? "#40BA56" : "#DD2222";
  return (
    <div
      style={{
        position: "absolute",
        top: -half,
        right: -half,
        width: size,
        height: size,
        borderRadius: "50%",
        backgroundColor: bg,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: "0 0 4px rgba(0,0,0,0.4)",
        zIndex: 1,
      }}
    >
      {status === TaskStatus.COMPLETED ? (
        <Check size={11} color="white" weight="bold" />
      ) : (
        <Prohibit size={11} color="white" />
      )}
    </div>
  );
}

// ─── Node card — all nodes use white TaskCard styling ─────────────────────────
function NodeCard({
  data,
  width,
  height,
  selected,
  onSelect,
  onDrillIn,
  onBack,
  onToggleGroup,
}: {
  data: DiagramNodeData;
  width: number;
  height: number;
  selected: boolean;
  onSelect: () => void;
  onDrillIn?: (r: AgentRunData) => void;
  onBack?: () => void;
  onToggleGroup?: () => void;
}) {
  // ── Fork/join junction node — thin bar spanning full node width ───────────────
  if (data.kind === "junction") {
    return (
      <div
        style={{
          width,
          height,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <div
          style={{
            width: width - 16,
            height: 4,
            borderRadius: 2,
            backgroundColor: "#c0c0c0",
          }}
        />
      </div>
    );
  }

  // ── Ellipsis node ("... N more") ────────────────────────────────────────────
  if (data.kind === "ellipsis") {
    return (
      <div
        onClick={(e) => {
          e.stopPropagation();
          onSelect();
        }}
        style={{
          width,
          height,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <div
          style={{
            padding: "8px 16px",
            borderRadius: 8,
            border: "2px dashed #d1d5db",
            backgroundColor: "#f9fafb",
            color: "#6b7280",
            fontSize: "0.78rem",
            fontWeight: 500,
            textAlign: "center",
            cursor: "pointer",
          }}
        >
          {data.label}
        </div>
      </div>
    );
  }

  // ── "Back to parent" node ─────────────────────────────────────────────────────
  if (data.kind === "back") {
    return (
      <div
        onClick={(e) => {
          e.stopPropagation();
          onBack?.();
        }}
        style={{
          width,
          height,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: "50%",
            border: "2px dashed #6366f1",
            backgroundColor: "#ede9fe",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
          }}
        >
          <span style={{ fontSize: "0.9rem", color: "#4f46e5", lineHeight: 1 }}>
            ↑
          </span>
          <span
            style={{
              fontSize: "0.48rem",
              color: "#6366f1",
              lineHeight: 1.2,
              textTransform: "uppercase",
              letterSpacing: "0.06em",
            }}
          >
            Back
          </span>
        </div>
      </div>
    );
  }

  // ── "Next turn" node ─────────────────────────────────────────────────────────
  if (data.kind === "next") {
    return (
      <div
        onClick={(e) => {
          e.stopPropagation();
          onSelect();
        }}
        style={{
          width,
          height,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <div
          style={{
            width: 44,
            height: 44,
            borderRadius: "50%",
            border: "2px dashed #f59e0b",
            backgroundColor: "#fef3c7",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
          }}
        >
          <span
            style={{
              fontSize: "0.5rem",
              color: "#b45309",
              lineHeight: 1,
              textTransform: "uppercase",
              letterSpacing: "0.06em",
            }}
          >
            Turn
          </span>
          <span
            style={{
              fontSize: "0.82rem",
              fontWeight: 700,
              color: "#92400e",
              lineHeight: 1.2,
            }}
          >
            {data.nextTurn}
          </span>
        </div>
      </div>
    );
  }

  // ── Stacked group node (parallel agents / tool calls) ────────────────────────
  if (data.kind === "group") {
    const isAgent = data.groupType === "agents";
    const type = isAgent ? TaskType.SUB_WORKFLOW : TaskType.SIMPLE;
    const variant = getCardVariant(type, data.ts, selected) as any;
    const borderColor: string =
      (variant.border as string | undefined)?.match(/solid\s+(.+)$/)?.[1] ??
      "#DDDDDD";
    const total =
      (data.groupAgents?.length ?? 0) || (data.groupEvents?.length ?? 0);
    const failed = data.groupFailed ?? 0;
    const running = data.groupRunning ?? 0;
    const completed = data.groupCompleted ?? 0;

    return (
      <div
        onClick={(e) => {
          e.stopPropagation();
          onSelect();
        }}
        style={{ width, height, position: "relative", cursor: "pointer" }}
      >
        {/* Back cards — extend slightly beyond boundary for stacking illusion */}
        <div
          style={{
            position: "absolute",
            top: 14,
            left: 14,
            width: "100%",
            height: "100%",
            borderRadius: 10,
            background: "#d0d0d0",
            border: `2px solid ${borderColor}`,
            opacity: 0.6,
          }}
        />
        <div
          style={{
            position: "absolute",
            top: 7,
            left: 7,
            width: "100%",
            height: "100%",
            borderRadius: 10,
            background: "#ebebeb",
            border: `2px solid ${borderColor}`,
            opacity: 0.85,
          }}
        />
        {/* Front card */}
        <div
          style={{
            position: "relative",
            width: "100%",
            height: "100%",
            borderRadius: 10,
            cursor: "pointer",
            transition: "box-shadow 250ms",
            ...variant,
            background: "#fff",
            border: `2.5px solid ${borderColor}`,
          }}
        >
          <div
            style={{
              position: "relative",
              padding: "16px 20px",
              width: "100%",
              height: "100%",
              borderRadius: 10,
              boxSizing: "border-box",
              color: "#111",
            }}
          >
            <NodeStatusBadge status={data.ts} />
            <div
              style={{ display: "flex", width: "100%", position: "relative" }}
            >
              <CardIcon type={type} integrationType={undefined} />
              <div style={{ flexGrow: 1, overflow: "hidden" }}>
                <div
                  style={{
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {data.label}
                </div>
                <div
                  style={{ color: "#888", fontSize: "0.72rem", marginTop: 2 }}
                >
                  {total} {isAgent ? "agents" : "calls"}
                  {completed > 0 && ` · ${completed} ✓`}
                  {failed > 0 && ` · ${failed} ✗`}
                  {running > 0 && ` · ${running} ⟳`}
                </div>
              </div>
              <TypeBadge
                label={
                  data.strategy ? STRATEGY_BADGE[data.strategy] : "PARALLEL"
                }
              />
            </div>
            {/* Expand button for collapsed groups (tools or agents) */}
            {total >= COLLAPSE_THRESHOLD && onToggleGroup && (
              <div
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleGroup?.();
                }}
                style={{
                  marginTop: 6,
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 4,
                  padding: "3px 10px",
                  borderRadius: "5px",
                  backgroundColor: "#4969e4",
                  cursor: "pointer",
                  fontSize: "0.72em",
                  color: "white",
                }}
              >
                Expand ({Math.min(total, MAX_EXPANDED)} of {total})
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  // ── Handoff pill ─────────────────────────────────────────────────────────────
  if (data.kind === "handoff") {
    const isSelected = selected;
    return (
      <div
        onClick={(e) => {
          e.stopPropagation();
          onSelect();
        }}
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          borderRadius: 8,
          cursor: "pointer",
          backgroundColor: isSelected ? "#ede9fe" : "#f5f3ff",
          border: `1.5px solid ${isSelected ? "#7c3aed" : "#c4b5fd"}`,
          boxSizing: "border-box",
          padding: "0 16px",
          gap: 10,
          transition: "background-color 0.15s, border-color 0.15s",
          position: "relative",
          overflow: "hidden",
        }}
      >
        {/* Arrow accent stripe on the left */}
        <div
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            bottom: 0,
            width: 4,
            backgroundColor: "#7c3aed",
            borderRadius: "8px 0 0 8px",
          }}
        />
        <span
          style={{
            fontSize: "1rem",
            color: "#7c3aed",
            marginLeft: 4,
            flexShrink: 0,
            lineHeight: 1,
          }}
        >
          →
        </span>
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            minWidth: 0,
            flexGrow: 1,
          }}
        >
          <span
            style={{
              fontSize: "0.8rem",
              fontWeight: 600,
              color: "#4c1d95",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {data.label || "handoff"}
          </span>
          <span
            style={{
              fontSize: "0.68rem",
              color: "#7c3aed",
              letterSpacing: "0.04em",
              textTransform: "uppercase",
            }}
          >
            handoff
          </span>
        </div>
      </div>
    );
  }

  const type = KIND_TYPE[data.kind];

  // Extract border color from getCardVariant, then reapply at half thickness
  const variant = getCardVariant(type, data.ts, selected) as any;
  const borderColor: string =
    (variant.border as string | undefined)?.match(/solid\s+(.+)$/)?.[1] ??
    "transparent";

  // ── All other nodes: unified white TaskCard style ─────────────────────────────
  return (
    <div
      onClick={(e) => {
        e.stopPropagation();
        onSelect();
      }}
      style={{
        width: "100%",
        height: "100%",
        borderRadius: "10px",
        cursor: "pointer",
        transition: "box-shadow 250ms",
        transitionDelay: "40ms",
        ...variant,
        background: "#fff",
        border: `1.5px solid ${borderColor}`,
      }}
    >
      <div
        style={{
          position: "relative",
          padding: "20px",
          width: "100%",
          height: "100%",
          borderRadius: "10px",
          boxSizing: "border-box",
          color: "#111111",
        }}
      >
        {/* Agent container nodes don't show spinner — the LLM child node represents active work */}
        {!(data.kind === "start" && data.ts === TaskStatus.IN_PROGRESS) && (
          <NodeStatusBadge status={data.ts} />
        )}

        <div style={{ display: "flex", width: "100%", position: "relative" }}>
          {(() => {
            const iconPath = getModelIconPath(data.modelName);
            return iconPath ? (
              <img
                src={iconPath}
                style={{
                  width: 24,
                  height: 24,
                  marginRight: 8,
                  flexShrink: 0,
                  objectFit: "contain",
                }}
                alt=""
              />
            ) : (
              <CardIcon type={type} integrationType={undefined} />
            );
          })()}
          <div style={{ flexGrow: 1, overflow: "hidden" }}>
            <div
              style={{
                display: "block",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {data.label}
            </div>
            {(data.sublabel || data.meta) && (
              <div
                style={{
                  color: "#AAAAAA",
                  display: "block",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {data.sublabel ?? data.meta}
              </div>
            )}
          </div>
          <TypeBadge
            label={
              data.typeLabel ??
              (data.strategy
                ? STRATEGY_BADGE[data.strategy]
                : KIND_LABEL[data.kind])
            }
          />
        </div>

        {/* "View execution" drill-in for sub-agents */}
        {data.kind === "subagent" && data.subAgentRun && (
          <div
            onClick={(e) => {
              e.stopPropagation();
              onDrillIn?.(data.subAgentRun!);
            }}
            style={{
              marginTop: 6,
              display: "inline-flex",
              alignItems: "center",
              gap: 4,
              padding: "3px 10px",
              borderRadius: "5px",
              backgroundColor: "#4969e4",
              cursor: "pointer",
              fontSize: "0.78em",
              color: "white",
            }}
          >
            View execution <ArrowRight size={10} />
          </div>
        )}

        {/* Retry attempt badge — shown when task was retried (totalAttempts > 1) */}
        {(() => {
          const attempts = data.event?.taskMeta?.totalAttempts;
          if (!attempts || attempts <= 1) return null;
          return (
            <div
              style={{
                position: "absolute",
                bottom: 6,
                right: 8,
                display: "inline-flex",
                alignItems: "center",
                gap: 3,
                padding: "2px 7px",
                borderRadius: 4,
                backgroundColor: "#fff7ed",
                border: "1px solid #f59e0b",
                fontSize: "0.66rem",
                fontWeight: 600,
                color: "#b45309",
                lineHeight: 1,
              }}
            >
              <span style={{ fontSize: "0.72rem" }}>⟳</span> {attempts} attempts
            </div>
          );
        })()}
      </div>
    </div>
  );
}

// ─── Reaflow node wrapper ─────────────────────────────────────────────────────
const DiagramNode = (nodeProps: any) => {
  const { selectedId, onSelect, onDrillIn, onBack, onToggleGroup, properties } =
    nodeProps;
  const data: DiagramNodeData = properties?.data;
  return (
    <Node
      {...nodeProps}
      onClick={() => null}
      label={<></>}
      style={{ stroke: "none", fill: "none" }}
    >
      {(ev: any) => (
        <g>
          <foreignObject
            width={ev.width}
            height={ev.height}
            style={{ overflow: "visible" }}
          >
            <NodeCard
              data={data}
              width={ev.width}
              height={ev.height}
              selected={selectedId === properties?.id}
              onSelect={() => onSelect(properties?.id)}
              onDrillIn={onDrillIn}
              onBack={onBack}
              onToggleGroup={
                onToggleGroup ? () => onToggleGroup(properties?.id) : undefined
              }
            />
          </foreignObject>
        </g>
      )}
    </Node>
  );
};

// ─── Build diagram nodes/edges ────────────────────────────────────────────────
const W = 264,
  H = 80,
  H_HANDOFF = 48;
// Parallel tool-call batches with fewer than this many calls are shown individually (not collapsed)
const COLLAPSE_THRESHOLD = 10;
// Maximum individual nodes to render when a collapsed group is expanded.
// When the total exceeds this, the first EXPAND_HEAD and last EXPAND_TAIL items
// are shown with an ellipsis node in between.
const MAX_EXPANDED = 20;
const EXPAND_HEAD = 10;
const EXPAND_TAIL = 10;

function buildTurnNodes(
  turn: AgentTurn,
  nodes: NodeData<DiagramNodeData>[],
  edges: EdgeData[],
  done: Set<string>,
  prevRef: { id: string },
  expandedGroups: Set<string>,
) {
  const push = (id: string, data: DiagramNodeData, h = H) => {
    nodes.push({ id, width: W, height: h, data });
    // Use join junction's south port for clean outgoing routing
    const fromPort = prevRef.id.endsWith("-join")
      ? `${prevRef.id}-south-port`
      : undefined;
    edges.push({
      id: `${prevRef.id}→${id}`,
      from: prevRef.id,
      to: id,
      ...(fromPort ? { fromPort } : {}),
    });
    if (data.ts === TaskStatus.COMPLETED) done.add(id);
    prevRef.id = id;
  };

  /**
   * Fan-out: create a fork node → N parallel branch nodes → join node.
   * Each branch node is displayed side-by-side horizontally.
   */
  const pushParallel = (
    forkId: string,
    branches: { id: string; data: DiagramNodeData; h?: number }[],
    joinId: string,
  ) => {
    if (branches.length === 0) return;
    const n = branches.length;

    // Fork junction — indexed SOUTH ports, same pattern as debug view's FORK_JOIN.
    // Width matches regular nodes so ELK keeps layers aligned.
    const forkPorts: PortData[] = branches.map((_, i) => ({
      id: `${forkId}_[key=${i}]-south-port`,
      width: 2,
      height: 2,
      side: "SOUTH" as PortSide,
      disabled: true,
      hidden: true,
      index: i,
    }));
    nodes.push({
      id: forkId,
      width: W,
      height: 16,
      data: { kind: "junction" as Kind, label: "", ts: TaskStatus.COMPLETED },
      ports: forkPorts,
    });
    edges.push({ id: `${prevRef.id}→${forkId}`, from: prevRef.id, to: forkId });
    done.add(forkId);

    // Join junction — INVERTED indexed NORTH ports (key anti-crossing trick
    // from the debug view: branch 0 → highest port index, branch N-1 → index 0).
    // Plus a standard SOUTH port for the outgoing edge.
    const joinPorts: PortData[] = [
      {
        id: `${joinId}-south-port`,
        width: 2,
        height: 2,
        side: "SOUTH" as PortSide,
        disabled: true,
        hidden: true,
      },
      ...branches.map((_, i) => {
        const inv = n - 1 - i;
        return {
          id: `${joinId}-n${inv}-north-port`,
          width: 2,
          height: 2,
          side: "NORTH" as PortSide,
          disabled: true,
          hidden: true,
          index: inv,
        };
      }),
    ];
    nodes.push({
      id: joinId,
      width: W,
      height: 16,
      data: { kind: "junction" as Kind, label: "", ts: TaskStatus.COMPLETED },
      ports: joinPorts,
    });
    done.add(joinId);

    // Branch nodes — each gets a south port for the branch→join edge.
    // Fully port-bound edges on both ends (fromPort + toPort).
    for (let i = 0; i < n; i++) {
      const b = branches[i];
      const inv = n - 1 - i;
      nodes.push({
        id: b.id,
        width: W,
        height: b.h ?? H,
        data: b.data,
        ports: [
          {
            id: `${b.id}-south-port`,
            width: 2,
            height: 2,
            side: "SOUTH" as PortSide,
            disabled: true,
            hidden: true,
          },
        ],
      });
      edges.push({
        id: `${forkId}→${b.id}`,
        from: forkId,
        fromPort: `${forkId}_[key=${i}]-south-port`,
        to: b.id,
      });
      edges.push({
        id: `${b.id}→${joinId}`,
        from: b.id,
        fromPort: `${b.id}-south-port`,
        to: joinId,
        toPort: `${joinId}-n${inv}-north-port`,
      });
      if (b.data.ts === TaskStatus.COMPLETED) done.add(b.id);
    }

    prevRef.id = joinId;
  };

  // Sequential chain turns: sub-agent FIRST, then gate event — entirely separate flow
  if (
    turn.strategy === AgentStrategy.SEQUENTIAL &&
    turn.subAgents.length === 1
  ) {
    const sub = turn.subAgents[0];
    push(`sub-${sub.id}`, {
      kind: "subagent",
      label: sub.agentName,
      meta: sub.model,
      modelName: sub.model,
      sublabel: sub.output?.slice(0, 55) ?? sub.failureReason?.slice(0, 55),
      strategy: turn.strategy,
      ts: toTS(sub.status),
      subAgentRun: sub,
    });
    for (const ev of turn.events) {
      if (
        ev.type === EventType.GUARDRAIL_PASS ||
        ev.type === EventType.GUARDRAIL_FAIL
      ) {
        push(ev.id, {
          kind: ev.type === EventType.GUARDRAIL_FAIL ? "error" : "output",
          label: "Gate",
          typeLabel: "GATE",
          sublabel: ev.summary,
          ts:
            ev.type === EventType.GUARDRAIL_FAIL
              ? TaskStatus.FAILED
              : TaskStatus.COMPLETED,
          event: ev,
        });
      }
    }
    return; // Skip normal event + sub-agent processing below
  }

  // Group consecutive TOOL_CALL events so large parallel batches collapse into one node
  type Grp = AgentEvent | { type: "__toolGroup"; events: AgentEvent[] };
  const groups: Grp[] = [];
  let toolBatch: AgentEvent[] = [];
  const flushBatch = () => {
    if (toolBatch.length === 0) return;
    groups.push({ type: "__toolGroup", events: [...toolBatch] });
    toolBatch = [];
  };
  for (const ev of turn.events) {
    if (ev.type === EventType.TOOL_CALL) {
      toolBatch.push(ev);
    } else {
      flushBatch();
      groups.push(ev);
    }
  }
  flushBatch();

  for (const grp of groups) {
    if ("type" in grp && grp.type === "__toolGroup") {
      const batch = (grp as any).events as AgentEvent[];
      const groupId = `toolgroup-${turn.turnNumber}`;
      const isExpanded = expandedGroups.has(groupId);

      if (batch.length < COLLAPSE_THRESHOLD || isExpanded) {
        // Build visible list: when expanded and over MAX_EXPANDED, show head + ellipsis + tail
        let visible: AgentEvent[];
        let ellipsisCount = 0;
        if (isExpanded && batch.length > MAX_EXPANDED) {
          const head = batch.slice(0, EXPAND_HEAD);
          ellipsisCount = batch.length - EXPAND_HEAD - EXPAND_TAIL;
          visible = [...head]; // tail is handled separately below
        } else {
          visible = batch;
        }

        const makeBranch = (ev: AgentEvent) => {
          const out = ev.result
            ? (() => {
                try {
                  return JSON.stringify(ev.result)
                    .replace(/[{}"]/g, "")
                    .slice(0, 55);
                } catch {
                  return undefined;
                }
              })()
            : undefined;
          return {
            id: ev.id,
            data: {
              kind: "tool" as Kind,
              label: ev.toolName ?? "tool",
              sublabel: out,
              meta: ev.durationMs ? formatDuration(ev.durationMs) : undefined,
              ts:
                ev.success === false
                  ? TaskStatus.FAILED
                  : ev.success === undefined
                    ? TaskStatus.IN_PROGRESS
                    : TaskStatus.COMPLETED,
              event: ev,
            },
          };
        };

        if (ellipsisCount > 0) {
          // Head + ellipsis + tail in fan-out
          const headBranches = visible.map(makeBranch);
          const ellipsisBranch = {
            id: `${groupId}-ellipsis`,
            data: {
              kind: "ellipsis" as Kind,
              label: `… ${ellipsisCount} more …`,
              ts: TaskStatus.COMPLETED,
            },
            h: 56,
          };
          const tailBranches = batch
            .slice(batch.length - EXPAND_TAIL)
            .map(makeBranch);
          pushParallel(
            `${groupId}-fork`,
            [...headBranches, ellipsisBranch, ...tailBranches],
            `${groupId}-join`,
          );
        } else if (visible.length === 1) {
          const ev = visible[0];
          const out = ev.result
            ? (() => {
                try {
                  return JSON.stringify(ev.result)
                    .replace(/[{}"]/g, "")
                    .slice(0, 55);
                } catch {
                  return undefined;
                }
              })()
            : undefined;
          push(ev.id, {
            kind: "tool",
            label: ev.toolName ?? "tool",
            sublabel: out,
            meta: ev.durationMs ? formatDuration(ev.durationMs) : undefined,
            ts:
              ev.success === false
                ? TaskStatus.FAILED
                : ev.success === undefined
                  ? TaskStatus.IN_PROGRESS
                  : TaskStatus.COMPLETED,
            event: ev,
          });
        } else {
          pushParallel(
            `${groupId}-fork`,
            visible.map(makeBranch),
            `${groupId}-join`,
          );
        }
      } else {
        const completed = batch.filter((e) => e.success === true).length;
        const failed = batch.filter((e) => e.success === false).length;
        const running = batch.filter((e) => e.success === undefined).length;
        const ts =
          failed > 0
            ? TaskStatus.FAILED
            : running > 0
              ? TaskStatus.IN_PROGRESS
              : TaskStatus.COMPLETED;
        push(groupId, {
          kind: "group",
          label: batch[0].toolName ?? "tool calls",
          groupType: "tools",
          groupEvents: batch,
          groupCompleted: completed,
          groupFailed: failed,
          groupRunning: running,
          ts,
        });
      }
    } else {
      const ev = grp as AgentEvent;
      switch (ev.type) {
        case EventType.THINKING: {
          const tok = ev.tokens;
          push(ev.id, {
            kind: "llm",
            label: "LLM",
            sublabel: ev.toolName,
            modelName: ev.toolName,
            meta: tok
              ? `${formatTokens(tok.promptTokens)}↑  ${formatTokens(tok.completionTokens)}↓`
              : undefined,
            ts:
              ev.success === false
                ? TaskStatus.FAILED
                : ev.success === undefined
                  ? TaskStatus.IN_PROGRESS
                  : TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        }
        case EventType.HANDOFF: {
          const target =
            ev.targetAgent ?? ev.summary.replace(/^→\s*/, "") ?? "";
          push(
            ev.id,
            {
              kind: "handoff",
              label: target,
              ts: TaskStatus.COMPLETED,
              event: ev,
            },
            H_HANDOFF,
          );
          break;
        }
        case EventType.MESSAGE: {
          const txt = typeof ev.detail === "string" ? ev.detail : undefined;
          push(ev.id, {
            kind: "output",
            label: "response",
            sublabel: txt?.slice(0, 70) + (txt && txt.length > 70 ? "…" : ""),
            ts: TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        }
        case EventType.DONE: {
          const txt = typeof ev.detail === "string" ? ev.detail : undefined;
          push(ev.id, {
            kind: "output",
            label: "output",
            sublabel: txt?.slice(0, 70) + (txt && txt.length > 70 ? "…" : ""),
            ts: TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        }
        case EventType.ERROR:
          push(ev.id, {
            kind: "error",
            label: "error",
            sublabel: ev.summary,
            ts: TaskStatus.FAILED,
            event: ev,
          });
          break;
        case EventType.GUARDRAIL_PASS:
          push(ev.id, {
            kind: "output",
            label:
              ev.toolName === "gate" ? "Gate" : (ev.toolName ?? "Guardrail"),
            typeLabel: ev.toolName === "gate" ? "GATE" : "GUARDRAIL",
            sublabel: ev.toolName === "gate" ? ev.summary : "passed",
            ts: TaskStatus.COMPLETED,
            event: ev,
          });
          break;
        case EventType.GUARDRAIL_FAIL:
          push(ev.id, {
            kind: "error",
            label:
              ev.toolName === "gate" ? "Gate" : (ev.toolName ?? "Guardrail"),
            typeLabel: ev.toolName === "gate" ? "GATE" : "GUARDRAIL",
            sublabel: ev.summary,
            ts: TaskStatus.FAILED,
            event: ev,
          });
          break;
        case EventType.WAITING:
          push(ev.id, {
            kind: "output",
            label: "Waiting",
            typeLabel: "WAITING",
            sublabel: ev.summary,
            ts: TaskStatus.IN_PROGRESS,
            event: ev,
          });
          break;
        default:
          break;
      }
    }
  }

  // Sub-agents: single node if one; inline if < threshold; collapsed group if >= threshold
  if (turn.subAgents.length > 0) {
    const subGroupId = `subgroup-${turn.turnNumber}`;
    const isSubExpanded = expandedGroups.has(subGroupId);

    if (turn.subAgents.length < COLLAPSE_THRESHOLD || isSubExpanded) {
      const makeSubBranch = (sub: AgentRunData) => ({
        id: `sub-${sub.id}`,
        data: {
          kind: "subagent" as Kind,
          label: sub.agentName,
          meta: sub.model,
          modelName: sub.model,
          sublabel: sub.output?.slice(0, 55) ?? sub.failureReason?.slice(0, 55),
          strategy: turn.strategy,
          ts: toTS(sub.status),
          subAgentRun: sub,
        },
      });

      if (isSubExpanded && turn.subAgents.length > MAX_EXPANDED) {
        // Head + ellipsis + tail
        const head = turn.subAgents.slice(0, EXPAND_HEAD).map(makeSubBranch);
        const tail = turn.subAgents
          .slice(turn.subAgents.length - EXPAND_TAIL)
          .map(makeSubBranch);
        const ellipsisCount = turn.subAgents.length - EXPAND_HEAD - EXPAND_TAIL;
        const ellipsisBranch = {
          id: `${subGroupId}-ellipsis`,
          data: {
            kind: "ellipsis" as Kind,
            label: `… ${ellipsisCount} more …`,
            ts: TaskStatus.COMPLETED,
          },
          h: 56,
        };
        pushParallel(
          `${subGroupId}-fork`,
          [...head, ellipsisBranch, ...tail],
          `${subGroupId}-join`,
        );
      } else if (turn.subAgents.length === 1) {
        const sub = turn.subAgents[0];
        push(`sub-${sub.id}`, {
          kind: "subagent",
          label: sub.agentName,
          meta: sub.model,
          modelName: sub.model,
          sublabel: sub.output?.slice(0, 55) ?? sub.failureReason?.slice(0, 55),
          strategy: turn.strategy,
          ts: toTS(sub.status),
          subAgentRun: sub,
        });
      } else {
        pushParallel(
          `${subGroupId}-fork`,
          turn.subAgents.map(makeSubBranch),
          `${subGroupId}-join`,
        );
      }
    } else {
      const completed = turn.subAgents.filter(
        (s) => s.status === AgentStatus.COMPLETED,
      ).length;
      const failed = turn.subAgents.filter(
        (s) => s.status === AgentStatus.FAILED,
      ).length;
      const running = turn.subAgents.length - completed - failed;
      const ts =
        failed > 0
          ? TaskStatus.FAILED
          : running > 0
            ? TaskStatus.IN_PROGRESS
            : TaskStatus.COMPLETED;
      push(subGroupId, {
        kind: "group",
        label: turn.subAgents[0].agentName,
        strategy: turn.strategy,
        groupType: "agents",
        groupAgents: turn.subAgents,
        groupCompleted: completed,
        groupFailed: failed,
        groupRunning: running,
        ts,
      });
    }
  }
}

function buildDiagram(
  agentRun: AgentRunData,
  _activeTurnNum: number,
  hasBack: boolean,
  expandedGroups: Set<string>,
) {
  const nodes: NodeData<DiagramNodeData>[] = [];
  const edges: EdgeData[] = [];
  const done = new Set<string>();
  const prevRef = { id: "start" };

  // "Back to parent" node — first in the chain
  if (hasBack) {
    nodes.push({
      id: "back",
      width: 56,
      height: 56,
      data: { kind: "back", label: "", ts: TaskStatus.COMPLETED },
    });
    edges.push({ id: "back→start", from: "back", to: "start" });
    done.add("back");
  }

  nodes.push({
    id: "start",
    width: W,
    height: H,
    data: {
      kind: "start",
      label: agentRun.agentName,
      sublabel: agentRun.input?.slice(0, 55),
      meta: agentRun.model,
      modelName: agentRun.model,
      ts: toTS(agentRun.status),
    },
  });
  if (agentRun.status === AgentStatus.COMPLETED) done.add("start");

  const allTurns = agentRun.turns;
  for (let i = 0; i < allTurns.length; i++) {
    const turn = allTurns[i];

    // Insert orange "Turn N" separator before every turn after the first
    if (i > 0) {
      const ntId = `turn-sep-${turn.turnNumber}`;
      nodes.push({
        id: ntId,
        width: 56,
        height: 56,
        data: {
          kind: "next",
          label: String(turn.turnNumber),
          nextTurn: turn.turnNumber,
          ts: toTS(turn.status),
        },
      });
      const fromPort = prevRef.id.endsWith("-join")
        ? `${prevRef.id}-south-port`
        : undefined;
      edges.push({
        id: `${prevRef.id}→${ntId}`,
        from: prevRef.id,
        to: ntId,
        ...(fromPort ? { fromPort } : {}),
      });
      if (turn.status === AgentStatus.COMPLETED) done.add(ntId);
      prevRef.id = ntId;
    }

    buildTurnNodes(turn, nodes, edges, done, prevRef, expandedGroups);
  }

  return { nodes, edges, done };
}

// ─── Zoom controls bar (matches PanAndZoomWrapper's ZoomControls visually) ────
function DiagramControls({
  zoom,
  onReset,
  onZoomIn,
  onZoomOut,
  onFit,
}: {
  zoom: number;
  onReset: () => void;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onFit: () => void;
}) {
  const border = `1px solid ${colors.lightGrey}`;
  const col = colors.greyText;
  return (
    <Box
      sx={{
        position: "absolute",
        top: 5,
        left: 5,
        borderRadius: "6px",
        boxShadow: "0px 4px 12px 0px #0000001F",
        backgroundColor: "#fff",
        display: "flex",
        userSelect: "none",
        zIndex: 100,
      }}
    >
      <ZoomControlsButton onClick={onReset} tooltip="Reset position">
        <HomeIcon color={col} />
      </ZoomControlsButton>
      <ZoomControlsButton
        style={{ borderLeft: border, borderRight: border, width: 60 }}
      >
        {Math.round(zoom * 100)}%
      </ZoomControlsButton>
      <ZoomControlsButton onClick={onZoomOut} tooltip="Zoom out">
        <MinusIcon color={col} />
      </ZoomControlsButton>
      <ZoomControlsButton
        onClick={onZoomIn}
        disabled={zoom >= MAX_ZOOM}
        tooltip="Zoom in"
        style={{ borderLeft: border }}
      >
        <PlusIcon color={col} />
      </ZoomControlsButton>
      <ZoomControlsButton
        onClick={onFit}
        tooltip="Fit to screen"
        style={{
          borderLeft: border,
          borderTopRightRadius: 5,
          borderBottomRightRadius: 5,
        }}
      >
        <FitToFrame color={col} />
      </ZoomControlsButton>
    </Box>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────
interface AgentExecutionDiagramProps {
  agentRun: AgentRunData;
  activeTurn: number;
  onSelectTurn: (n: number) => void;
  selectedId: string | null;
  onNodeSelect: (id: string | null, node: DetailNodeData | null) => void;
  onDrillIn?: (sub: AgentRunData) => void;
  onBack?: () => void;
}

export function AgentExecutionDiagram({
  agentRun,
  activeTurn,
  onSelectTurn,
  selectedId,
  onNodeSelect,
  onDrillIn,
  onBack,
}: AgentExecutionDiagramProps) {
  const hasBack = !!onBack;
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  // Reset expanded groups when the agent changes
  useEffect(() => {
    setExpandedGroups(new Set());
  }, [agentRun]);

  const toggleGroup = useCallback((groupId: string) => {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupId)) next.delete(groupId);
      else next.add(groupId);
      return next;
    });
  }, []);

  const { nodes, edges, done } = useMemo(
    () => buildDiagram(agentRun, activeTurn, hasBack, expandedGroups),
    [agentRun, hasBack, expandedGroups], // eslint-disable-line react-hooks/exhaustive-deps
  );

  const viewportRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<any>(null);

  // Pan/zoom state — CSS transform applied to the inner container
  const [panZoom, setPanZoom] = useState({ x: 40, y: 40, zoom: 1 });
  // Stable ref so gesture handlers always see latest zoom without stale closure
  const panZoomRef = useRef(panZoom);
  panZoomRef.current = panZoom;

  // ELK layout dimensions + per-node positions (populated after ELK runs)
  const [layoutSize, setLayoutSize] = useState({ width: 0, height: 0 });
  type NodePos = { x: number; y: number; width: number; height: number };
  const [nodePositions, setNodePositions] = useState<Map<string, NodePos>>(
    new Map(),
  );

  // Reset pan + layout when the agent changes (NOT on turn change — we pan instead)
  useEffect(() => {
    setPanZoom({ x: 40, y: 40, zoom: 1 });
    setLayoutSize({ width: 0, height: 0 });
    setNodePositions(new Map());
  }, [agentRun.id]);

  // Called by reaflow after ELK computes layout — capture dimensions + per-node positions
  const handleLayoutChange = useCallback((result: any) => {
    if (result?.width > 0 && result?.height > 0) {
      setLayoutSize({ width: result.width, height: result.height });
      const positions = new Map<string, NodePos>();
      for (const child of result.children ?? []) {
        if (child.id && child.x != null) {
          positions.set(child.id, {
            x: child.x,
            y: child.y,
            width: child.width,
            height: child.height,
          });
        }
      }
      setNodePositions(positions);
    }
  }, []);

  // Pan to center the selected turn's node when activeTurn changes
  const prevTurnRef = useRef<number | null>(null);
  useEffect(() => {
    if (prevTurnRef.current === null) {
      prevTurnRef.current = activeTurn;
      return;
    }
    if (prevTurnRef.current === activeTurn) return;
    prevTurnRef.current = activeTurn;

    if (!viewportRef.current || nodePositions.size === 0) return;

    const firstTurn = agentRun.turns[0]?.turnNumber ?? 1;
    const targetId =
      activeTurn === firstTurn ? "start" : `turn-sep-${activeTurn}`;
    const pos = nodePositions.get(targetId);
    if (!pos) return;

    const { offsetHeight: vh } = viewportRef.current;
    const nodeCenterY = pos.y + pos.height / 2;
    setPanZoom((prev) => ({
      ...prev,
      y: vh / 2 - nodeCenterY * prev.zoom,
    }));
  }, [activeTurn]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Zoom control callbacks ────────────────────────────────────────────────────
  const handleReset = useCallback(() => {
    setPanZoom({ x: 40, y: 40, zoom: 1 });
  }, []);

  const handleZoomIn = useCallback(() => {
    setPanZoom((prev) => ({
      ...prev,
      zoom: Math.min(MAX_ZOOM, prev.zoom * 1.2),
    }));
  }, []);

  const handleZoomOut = useCallback(() => {
    setPanZoom((prev) => ({
      ...prev,
      zoom: Math.max(MIN_ZOOM, prev.zoom / 1.2),
    }));
  }, []);

  const handleFitToScreen = useCallback(() => {
    if (!viewportRef.current || !layoutSize.width) return;
    const { offsetWidth: vw, offsetHeight: vh } = viewportRef.current;
    const scaleX = (vw - 80) / layoutSize.width;
    const scaleY = (vh - 80) / layoutSize.height;
    const newZoom = Math.max(
      MIN_ZOOM,
      Math.min(MAX_ZOOM, Math.min(scaleX, scaleY)),
    );
    const cx = (vw - layoutSize.width * newZoom) / 2;
    const cy = (vh - layoutSize.height * newZoom) / 2;
    setPanZoom({ x: cx, y: cy, zoom: newZoom });
  }, [layoutSize]);

  // ── Drag-to-pan via @use-gesture (same as PanAndZoomWrapper) ────────────────
  useDrag(
    ({ delta, tap }) => {
      if (tap) return;
      setPanZoom((prev) => ({
        ...prev,
        x: prev.x + delta[0],
        y: prev.y + delta[1],
      }));
    },
    { target: viewportRef, filterTaps: true, eventOptions: { passive: false } },
  );

  // ── Scroll-to-pan + Ctrl/Meta-scroll-to-zoom ─────────────────────────────────
  useWheel(
    ({ delta, event, metaKey, ctrlKey }) => {
      event.preventDefault();
      if (metaKey || ctrlKey) {
        const rect = viewportRef.current?.getBoundingClientRect();
        const cx = (event as WheelEvent).clientX - (rect?.left ?? 0);
        const cy = (event as WheelEvent).clientY - (rect?.top ?? 0);
        setPanZoom((prev) => {
          const newZoom = Math.max(
            MIN_ZOOM,
            Math.min(
              MAX_ZOOM,
              prev.zoom * (1 - (event as WheelEvent).deltaY * 0.001),
            ),
          );
          const scale = newZoom / prev.zoom;
          return {
            x: cx - scale * (cx - prev.x),
            y: cy - scale * (cy - prev.y),
            zoom: newZoom,
          };
        });
      } else {
        setPanZoom((prev) => ({
          ...prev,
          x: prev.x - delta[0],
          y: prev.y - delta[1],
        }));
      }
    },
    { target: viewportRef, eventOptions: { passive: false } },
  );

  // ── Pinch-to-zoom (trackpad two-finger pinch, same as PanAndZoomWrapper) ─────
  usePinch(
    ({ offset: [scale], event, origin: [ox, oy] }) => {
      event.preventDefault();
      const rect = viewportRef.current?.getBoundingClientRect();
      const cx = ox - (rect?.left ?? 0);
      const cy = oy - (rect?.top ?? 0);
      const newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale));
      setPanZoom((prev) => {
        const factor = newZoom / prev.zoom;
        return {
          x: cx - factor * (cx - prev.x),
          y: cy - factor * (cy - prev.y),
          zoom: newZoom,
        };
      });
    },
    {
      scaleBounds: { min: MIN_ZOOM, max: MAX_ZOOM },
      from: () => [panZoomRef.current.zoom, 0],
      target: viewportRef,
      eventOptions: { passive: false },
    },
  );

  // ── Node click handler ────────────────────────────────────────────────────────
  const handle = useCallback(
    (id: string) => {
      const nd = nodes.find((n) => n.id === id)?.data;
      if (nd?.kind === "back") {
        onBack?.();
        return;
      }
      if (nd?.kind === "next" && nd.nextTurn) {
        onSelectTurn(nd.nextTurn);
        return;
      }
      if (id === selectedId) {
        onNodeSelect(null, null);
        return;
      }
      if (!nd) {
        onNodeSelect(null, null);
        return;
      }
      const status =
        nd.ts === TaskStatus.COMPLETED
          ? AgentStatus.COMPLETED
          : nd.ts === TaskStatus.FAILED
            ? AgentStatus.FAILED
            : AgentStatus.RUNNING;
      if (nd.kind === "start") {
        onNodeSelect(id, {
          kind: "start",
          label: nd.label,
          status,
          subAgentRun: agentRun,
        });
        return;
      }
      if (nd.kind === "group") {
        onNodeSelect(id, {
          kind: "group",
          label: nd.label,
          status,
          groupType: nd.groupType,
          groupAgents: nd.groupAgents,
          groupEvents: nd.groupEvents,
        });
        return;
      }
      onNodeSelect(id, {
        kind: nd.kind as any,
        label: nd.label,
        status,
        event: nd.event,
        subAgentRun: nd.subAgentRun,
      });
    },
    [nodes, selectedId, onSelectTurn, onNodeSelect, agentRun],
  );

  const hasLayout = layoutSize.width > 0;

  return (
    /* Viewport: overflow:hidden, captures all gestures */
    <div
      ref={viewportRef}
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        position: "relative",
        cursor: "grab",
        touchAction: "none",
        backgroundImage: "url('/diagramDotBg.svg')",
        backgroundColor: "#fff",
      }}
      onClick={() => onNodeSelect(null, null)}
    >
      {/* Loading skeleton while ELK computes layout */}
      {!hasLayout && (
        <Box
          sx={{
            position: "absolute",
            inset: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: "#fff",
            backgroundImage: "url('/diagramDotBg.svg')",
          }}
        >
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 3,
            }}
          >
            {/* Skeleton nodes */}
            {[0, 1, 2].map((i) => (
              <Box
                key={i}
                sx={{
                  width: i === 0 ? 56 : 220,
                  height: 80,
                  borderRadius: 1,
                  backgroundColor: "#f3f3f3",
                  border: "1px solid #DDDDDD",
                  animation: "shimmer 1.5s ease-in-out infinite",
                  animationDelay: `${i * 0.2}s`,
                  "@keyframes shimmer": {
                    "0%, 100%": { opacity: 0.6 },
                    "50%": { opacity: 1 },
                  },
                }}
              />
            ))}
          </Box>
        </Box>
      )}
      {/* Transform container: CSS translate+scale for unrestricted pan/zoom */}
      {hasLayout && (
        <DiagramControls
          zoom={panZoom.zoom}
          onReset={handleReset}
          onZoomIn={handleZoomIn}
          onZoomOut={handleZoomOut}
          onFit={handleFitToScreen}
        />
      )}
      <div
        style={{
          position: "absolute",
          transformOrigin: "top left",
          transition: "transform .1s",
          transform: `translateX(${panZoom.x}px) translateY(${panZoom.y}px) scale(${panZoom.zoom})`,
          // Give the Canvas container explicit pixel dimensions matching the ELK layout.
          // This is required for reaflow's useDimensions to measure the container correctly
          // when pannable=false (same technique as debug view's diagram-canvas-container).
          ...(hasLayout
            ? { width: layoutSize.width, height: layoutSize.height }
            : {}),
        }}
      >
        <Canvas
          ref={canvasRef}
          nodes={nodes}
          edges={edges}
          fit={false}
          zoomable={false}
          pannable={false}
          defaultPosition={CanvasPosition.CENTER}
          maxWidth={5000}
          maxHeight={4000}
          onLayoutChange={handleLayoutChange}
          direction="DOWN"
          layoutOptions={{
            "org.eclipse.elk.spacing.nodeNode": "18",
            "org.eclipse.elk.spacing.edgeEdge": "8",
            "elk.layered.spacing.nodeNodeBetweenLayers": "24",
            "org.eclipse.elk.padding": "[top=60,left=60,bottom=60,right=60]",
          }}
          node={
            <DiagramNode
              selectedId={selectedId}
              onSelect={handle}
              onDrillIn={onDrillIn}
              onBack={onBack}
              onToggleGroup={toggleGroup}
            />
          }
          edge={(ed: EdgeData) => (
            <Edge
              {...ed}
              style={{
                stroke: done.has(ed.from ?? "") ? EDGE_COMPLETED : EDGE_DEFAULT,
                strokeWidth: done.has(ed.from ?? "") ? 2 : 1,
              }}
            />
          )}
        />
      </div>
    </div>
  );
}

export default AgentExecutionDiagram;
