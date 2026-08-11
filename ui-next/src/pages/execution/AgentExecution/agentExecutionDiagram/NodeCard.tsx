import { ArrowRight } from "@phosphor-icons/react";
import { getCardVariant } from "components/features/flow/components/shapes/styles";
import CardIcon from "components/features/flow/components/shapes/TaskCard/CardIcon";
import { TaskStatus, TaskType } from "types";
import { AgentRunData } from "../types";
import {
  COLLAPSE_THRESHOLD,
  DiagramNodeData,
  KIND_LABEL,
  KIND_TYPE,
  MAX_EXPANDED,
  STRATEGY_BADGE,
} from "../buildAgentExecutionDiagram";
import { getModelIconPath } from "../agentExecutionUtils";
import { TypeBadge } from "./TypeBadge";
import { NodeStatusBadge } from "./NodeStatusBadge";

export function NodeCard({
  data,
  width,
  height,
  selected,
  onSelect,
  onDrillIn,
  onExpand,
  onBack,
  onToggleGroup,
}: {
  data: DiagramNodeData;
  width: number;
  height: number;
  selected: boolean;
  onSelect: () => void;
  onDrillIn?: (r: AgentRunData) => void;
  onExpand?: (r: AgentRunData) => void;
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
    const turnLabel = data.label ?? "Turn";
    const turnNumber = turnLabel.startsWith("Turn ")
      ? turnLabel.slice("Turn ".length)
      : undefined;
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
            width: 62,
            height: 62,
            borderRadius: "50%",
            border: "2px dashed #f59e0b",
            backgroundColor: "#fef3c7",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
            boxSizing: "border-box",
            overflow: "hidden",
            padding: "4px",
          }}
        >
          <span
            style={{
              fontSize: turnNumber ? "0.48rem" : "0.56rem",
              fontWeight: 700,
              color: "#92400e",
              lineHeight: 1.1,
              textAlign: "center",
              textTransform: turnNumber ? "uppercase" : undefined,
              letterSpacing: turnNumber ? "0.05em" : undefined,
              overflowWrap: "anywhere",
            }}
          >
            {turnNumber ? "Turn" : turnLabel}
          </span>
          {turnNumber && (
            <span
              style={{
                fontSize: "0.72rem",
                fontWeight: 700,
                color: "#92400e",
                lineHeight: 1.1,
              }}
            >
              {turnNumber}
            </span>
          )}
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
          <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
            <div
              onClick={(e) => {
                e.stopPropagation();
                onDrillIn?.(data.subAgentRun!);
              }}
              style={{
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

            {/* Expand in place — reveals this agent's own sub-agents inline
                instead of navigating away (issue #1452). Only shown when the
                definition says this agent actually has children. */}
            {!data.expanded &&
              !!data.subAgentCount &&
              data.subAgentCount > 0 && (
                <div
                  onClick={(e) => {
                    e.stopPropagation();
                    if (!data.expanding) onExpand?.(data.subAgentRun!);
                  }}
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 4,
                    padding: "3px 10px",
                    borderRadius: "5px",
                    backgroundColor: data.expandError ? "#fef2f2" : "#f3f4f6",
                    border: data.expandError ? "1px solid #fca5a5" : "none",
                    cursor: data.expanding ? "default" : "pointer",
                    fontSize: "0.78em",
                    color: data.expandError ? "#b91c1c" : "#374151",
                  }}
                >
                  {data.expanding
                    ? "Loading…"
                    : data.expandError
                      ? "Retry expand"
                      : `Expand (${data.subAgentCount})`}
                </div>
              )}
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
