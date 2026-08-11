/**
 * AgentDefinitionView — reaflow block diagram of an agent's definition.
 *
 * Layout:
 *   - Root agent node at top (name, model, strategy, maxTurns)
 *   - Nested agents[] trees rendered recursively as a containment tree
 *     (parent → each child; strategy badges, not sibling chains)
 *   - Tools/guardrails also branch from root
 *
 * Architecture mirrors AgentExecutionDiagram (viewport → transform → Canvas).
 */
import { useRef, useCallback, useMemo, useState } from "react";
import { Box, Typography } from "@mui/material";
import { useDrag, usePinch, useWheel } from "@use-gesture/react";
import { ZoomControlsButton } from "components/ZoomControlsButton";
import HomeIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Home";
import MinusIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Minus";
import PlusIcon from "components/features/flow/components/graphs/PanAndZoomWrapper/icons/Plus";
import FitToFrame from "shared/icons/FitToFrame";
import { colors } from "theme/tokens/variables";
import { Canvas, CanvasPosition, Edge, EdgeData, Node } from "reaflow";
import { getModelIconPath } from "./agentExecutionUtils";
import { buildDefDiagram } from "./buildDefDiagram";
import type { DefNodeData } from "./buildDefDiagram";
import { WorkflowExecution } from "types/Execution";
import "components/features/flow/ReaflowOverrides.scss";

// ─── Constants ────────────────────────────────────────────────────────────────
const MIN_ZOOM = 0.1,
  MAX_ZOOM = 2.5;

// ─── Strategy colours ─────────────────────────────────────────────────────────
const STRATEGY_STYLE: Record<string, { bg: string; color: string }> = {
  random: { bg: "#fef3c7", color: "#92400e" },
  handoff: { bg: "#ede9fe", color: "#6d28d9" },
  sequential: { bg: "#e0f2fe", color: "#075985" },
  parallel: { bg: "#dcfce7", color: "#15803d" },
  router: { bg: "#f3f4f6", color: "#374151" },
  single: { bg: "#f3f4f6", color: "#374151" },
};
function strategyStyle(s?: string) {
  return (
    STRATEGY_STYLE[s?.toLowerCase() ?? ""] ?? {
      bg: "#f3f4f6",
      color: "#374151",
    }
  );
}

// ─── Gate (decision / conditional) card ──────────────────────────────────────
function GateNodeCard({
  data,
  width,
  height,
}: {
  data: DefNodeData;
  width: number;
  height: number;
}) {
  const typeLabel = (() => {
    switch (data.gateType) {
      case "text_contains":
        return "contains";
      case "text_not_contains":
        return "not contains";
      case "equals":
        return "equals";
      case "not_equals":
        return "≠";
      case "regex":
        return "regex";
      default:
        return data.gateType ?? "condition";
    }
  })();

  // Diamond: rotated inner square, text sits on top unrotated
  const d = Math.min(width, height) * 0.88;

  return (
    <div
      style={{
        width,
        height,
        position: "relative",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      {/* Rotated square → diamond shape */}
      <div
        style={{
          position: "absolute",
          width: d,
          height: d,
          transform: "rotate(45deg)",
          background: "#fffbeb",
          border: "2px solid #f59e0b",
          borderRadius: 6,
        }}
      />
      {/* Label (unrotated, centered) */}
      <div
        style={{
          position: "relative",
          zIndex: 1,
          textAlign: "center",
          maxWidth: "70%",
          padding: "0 4px",
        }}
      >
        <div
          style={{
            fontSize: "0.58rem",
            fontWeight: 800,
            color: "#b45309",
            letterSpacing: "0.1em",
            textTransform: "uppercase",
            marginBottom: 3,
          }}
        >
          gate
        </div>
        <div
          style={{
            fontSize: "0.68rem",
            color: "#78350f",
            fontFamily: "monospace",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {typeLabel}
        </div>
        {data.gateText && (
          <div
            style={{
              fontSize: "0.68rem",
              color: "#92400e",
              fontWeight: 600,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            "{data.gateText}"
          </div>
        )}
      </div>
    </div>
  );
}

// ─── Node card ────────────────────────────────────────────────────────────────
function NodeCard({
  data,
  width,
  height,
}: {
  data: DefNodeData;
  width: number;
  height: number;
}) {
  if (data.kind === "gate")
    return <GateNodeCard data={data} width={width} height={height} />;
  const isRoot = data.kind === "agent";
  const isGroup = data.kind === "group";
  const ss = strategyStyle(data.strategy);

  const cardContent = (
    <div
      style={{
        position: "relative",
        padding: "12px 16px 10px",
        width: "100%",
        height: "100%",
        borderRadius: 10,
        boxSizing: "border-box",
        color: "#111",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
      }}
    >
      {/* ── Top row: icon + label + type badge ── */}
      <div style={{ display: "flex", alignItems: "flex-start", gap: 8 }}>
        {/* Model icon */}
        {data.modelName &&
          (() => {
            const icon = getModelIconPath(data.modelName);
            return icon ? (
              <img
                src={icon}
                style={{
                  width: 18,
                  height: 18,
                  objectFit: "contain",
                  flexShrink: 0,
                  marginTop: 1,
                }}
                alt=""
              />
            ) : null;
          })()}

        <div style={{ flexGrow: 1, overflow: "hidden", minWidth: 0 }}>
          {/* Label */}
          <div
            style={{
              fontWeight: isRoot ? 700 : 500,
              fontSize: "0.875rem",
              lineHeight: 1.25,
              wordBreak: "break-word",
            }}
          >
            {data.label}
          </div>
          {/* Sub-label: model name or instruction snippet */}
          {data.sublabel && (
            <div
              style={{
                color: "#AAAAAA",
                fontSize: "0.75rem",
                lineHeight: 1.25,
                marginTop: 2,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {data.sublabel}
            </div>
          )}
          {/* Group item count */}
          {isGroup && data.count !== undefined && (
            <div style={{ color: "#888", fontSize: "0.72rem", marginTop: 2 }}>
              {data.count}{" "}
              {data.badge.toLowerCase() === "agents" ? "agents" : "items"}
            </div>
          )}
        </div>

        {/* Type badge */}
        <div
          style={{
            flexShrink: 0,
            padding: "3px 7px",
            fontSize: "0.68em",
            fontWeight: 700,
            letterSpacing: "0.04em",
            background: data.badgeBg,
            color: data.badgeColor,
            borderRadius: 4,
            whiteSpace: "nowrap",
          }}
        >
          {data.badge}
        </div>
      </div>

      {/* ── Bottom row: strategy pill + maxTurns + sub-agent count ── */}
      {(data.strategy ||
        data.maxTurns ||
        (data.subAgentCount && data.subAgentCount > 0)) && (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 6,
            marginTop: 6,
          }}
        >
          {data.strategy && (
            <span
              style={{
                display: "inline-flex",
                alignItems: "center",
                padding: "2px 7px",
                borderRadius: 20,
                fontSize: "0.67rem",
                fontWeight: 700,
                letterSpacing: "0.04em",
                textTransform: "uppercase",
                background: ss.bg,
                color: ss.color,
              }}
            >
              {data.strategy}
            </span>
          )}
          {data.maxTurns !== undefined && (
            <span style={{ fontSize: "0.67rem", color: "#9ca3af" }}>
              {data.maxTurns} turns
            </span>
          )}
          {data.subAgentCount != null && data.subAgentCount > 0 && (
            <span
              style={{
                fontSize: "0.67rem",
                color: "#9ca3af",
                marginLeft: "auto",
              }}
            >
              {data.subAgentCount} sub-agent
              {data.subAgentCount !== 1 ? "s" : ""}
            </span>
          )}
        </div>
      )}
    </div>
  );

  // Group: stacked card effect
  if (isGroup) {
    return (
      <div style={{ width, height, position: "relative" }}>
        <div
          style={{
            position: "absolute",
            top: 14,
            left: 14,
            width: "100%",
            height: "100%",
            borderRadius: 10,
            background: "#d0d0d0",
            border: `2px solid ${data.borderColor}`,
            opacity: 0.5,
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
            border: `2px solid ${data.borderColor}`,
            opacity: 0.8,
          }}
        />
        <div
          style={{
            position: "relative",
            width: "100%",
            height: "100%",
            borderRadius: 10,
            background: "#fff",
            border: `2.5px solid ${data.borderColor}`,
          }}
        >
          {cardContent}
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        width,
        height,
        borderRadius: 10,
        background: "#fff",
        border: `2px solid ${data.borderColor}`,
      }}
    >
      {cardContent}
    </div>
  );
}

// ─── Reaflow node wrapper ─────────────────────────────────────────────────────
const DiagramNode = (nodeProps: any) => {
  const { properties } = nodeProps;
  const data: DefNodeData = properties?.data;
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
            <NodeCard data={data} width={ev.width} height={ev.height} />
          </foreignObject>
        </g>
      )}
    </Node>
  );
};

// ─── Zoom controls (mirrors AgentExecutionDiagram's DiagramControls) ──────────
function ZoomControls({
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
        aria-label="Fit to screen"
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

// ─── Main diagram canvas ──────────────────────────────────────────────────────
export function AgentDefinitionDiagram({
  agentDef,
}: {
  agentDef: Record<string, unknown>;
}) {
  const { nodes, edges } = useMemo(() => buildDefDiagram(agentDef), [agentDef]);

  const viewportRef = useRef<HTMLDivElement>(null);
  const [panZoom, setPanZoom] = useState({ x: 40, y: 40, zoom: 1 });
  const [layoutSize, setLayoutSize] = useState({ width: 0, height: 0 });
  const panZoomRef = useRef(panZoom);
  panZoomRef.current = panZoom;

  const handleLayoutChange = useCallback((result: any) => {
    if (result?.width > 0 && result?.height > 0) {
      setLayoutSize({ width: result.width, height: result.height });
    }
  }, []);

  const handleReset = useCallback(
    () => setPanZoom({ x: 40, y: 40, zoom: 1 }),
    [],
  );
  const handleZoomIn = useCallback(
    () => setPanZoom((p) => ({ ...p, zoom: Math.min(MAX_ZOOM, p.zoom * 1.2) })),
    [],
  );
  const handleZoomOut = useCallback(
    () => setPanZoom((p) => ({ ...p, zoom: Math.max(MIN_ZOOM, p.zoom / 1.2) })),
    [],
  );
  const handleFit = useCallback(() => {
    if (!viewportRef.current || !layoutSize.width) return;
    const { offsetWidth: vw, offsetHeight: vh } = viewportRef.current;
    const nz = Math.max(
      MIN_ZOOM,
      Math.min(
        MAX_ZOOM,
        Math.min((vw - 80) / layoutSize.width, (vh - 80) / layoutSize.height),
      ),
    );
    setPanZoom({
      x: (vw - layoutSize.width * nz) / 2,
      y: (vh - layoutSize.height * nz) / 2,
      zoom: nz,
    });
  }, [layoutSize]);

  useDrag(
    ({ delta, tap }) => {
      if (tap) return;
      setPanZoom((p) => ({ ...p, x: p.x + delta[0], y: p.y + delta[1] }));
    },
    { target: viewportRef, filterTaps: true, eventOptions: { passive: false } },
  );
  useWheel(
    ({ delta, event, metaKey, ctrlKey }) => {
      event.preventDefault();
      if (metaKey || ctrlKey) {
        const rect = viewportRef.current?.getBoundingClientRect();
        const cx = (event as WheelEvent).clientX - (rect?.left ?? 0);
        const cy = (event as WheelEvent).clientY - (rect?.top ?? 0);
        setPanZoom((p) => {
          const nz = Math.max(
            MIN_ZOOM,
            Math.min(
              MAX_ZOOM,
              p.zoom * (1 - (event as WheelEvent).deltaY * 0.001),
            ),
          );
          const s = nz / p.zoom;
          return { x: cx - s * (cx - p.x), y: cy - s * (cy - p.y), zoom: nz };
        });
      } else {
        setPanZoom((p) => ({ ...p, x: p.x - delta[0], y: p.y - delta[1] }));
      }
    },
    { target: viewportRef, eventOptions: { passive: false } },
  );
  usePinch(
    ({ offset: [scale], event, origin: [ox, oy] }) => {
      event.preventDefault();
      const rect = viewportRef.current?.getBoundingClientRect();
      const cx = ox - (rect?.left ?? 0);
      const cy = oy - (rect?.top ?? 0);
      const nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale));
      setPanZoom((p) => {
        const f = nz / p.zoom;
        return { x: cx - f * (cx - p.x), y: cy - f * (cy - p.y), zoom: nz };
      });
    },
    {
      scaleBounds: { min: MIN_ZOOM, max: MAX_ZOOM },
      from: () => [panZoomRef.current.zoom, 0],
      target: viewportRef,
      eventOptions: { passive: false },
    },
  );

  const hasLayout = layoutSize.width > 0;

  return (
    <div
      ref={viewportRef}
      data-testid="agent-definition-diagram"
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
    >
      {/* Loading skeleton */}
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
            {[0, 1, 2].map((i) => (
              <Box
                key={i}
                sx={{
                  width: i === 0 ? 220 : 180,
                  height: 90,
                  borderRadius: 1,
                  backgroundColor: "#f3f3f3",
                  border: "1px solid #DDDDDD",
                  animation: "shimmer 1.5s ease-in-out infinite",
                  animationDelay: `${i * 0.2}s`,
                  "@keyframes shimmer": {
                    "0%,100%": { opacity: 0.6 },
                    "50%": { opacity: 1 },
                  },
                }}
              />
            ))}
          </Box>
        </Box>
      )}

      {hasLayout && (
        <ZoomControls
          zoom={panZoom.zoom}
          onReset={handleReset}
          onZoomIn={handleZoomIn}
          onZoomOut={handleZoomOut}
          onFit={handleFit}
        />
      )}

      {/* Transform container */}
      <div
        style={{
          position: "absolute",
          transformOrigin: "top left",
          transition: "transform .1s",
          transform: `translateX(${panZoom.x}px) translateY(${panZoom.y}px) scale(${panZoom.zoom})`,
          ...(hasLayout
            ? { width: layoutSize.width, height: layoutSize.height }
            : {}),
        }}
      >
        <Canvas
          nodes={nodes}
          edges={edges}
          fit={false}
          zoomable={false}
          pannable={false}
          defaultPosition={CanvasPosition.CENTER}
          maxWidth={6000}
          maxHeight={5000}
          onLayoutChange={handleLayoutChange}
          direction="DOWN"
          layoutOptions={{
            "org.eclipse.elk.spacing.nodeNode": "20",
            "elk.layered.spacing.nodeNodeBetweenLayers": "28",
            "org.eclipse.elk.padding": "[top=60,left=60,bottom=60,right=60]",
          }}
          node={<DiagramNode />}
          edge={(ed: EdgeData) => (
            <Edge {...ed} style={{ stroke: "#CCCCCC", strokeWidth: 1.5 }} />
          )}
        />
      </div>
    </div>
  );
}

// ─── Public wrapper ───────────────────────────────────────────────────────────
interface AgentDefinitionViewProps {
  execution: WorkflowExecution;
}

export function AgentDefinitionView({ execution }: AgentDefinitionViewProps) {
  const agentDef = execution?.workflowDefinition?.metadata?.agentDef as
    | Record<string, unknown>
    | undefined;

  if (!agentDef) {
    return (
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          height: "100%",
          color: "text.secondary",
        }}
      >
        <Typography variant="body2">
          No agent definition found in workflow metadata
        </Typography>
      </Box>
    );
  }

  return <AgentDefinitionDiagram agentDef={agentDef} />;
}

export default AgentDefinitionView;
