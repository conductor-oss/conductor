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
import { Box } from "@mui/material";
import { useDrag, usePinch, useWheel } from "@use-gesture/react";
import { Canvas, CanvasPosition, Edge, EdgeData } from "reaflow";
import { TaskStatus } from "types";
import { AgentRunData, AgentStatus } from "./types";
import { DetailNodeData } from "./AgentDetailPanel";
import { timelineItemId } from "./agentExecutionUtils";
import { buildAgentExecutionDiagram } from "./buildAgentExecutionDiagram";
import { DiagramControls } from "./agentExecutionDiagram/DiagramControls";
import { DiagramNode } from "./agentExecutionDiagram/DiagramNode";
import {
  EDGE_COMPLETED,
  EDGE_DEFAULT,
  MAX_ZOOM,
  MIN_ZOOM,
} from "./agentExecutionDiagram/constants";
import "components/features/flow/ReaflowOverrides.scss";

interface AgentExecutionDiagramProps {
  agentRun: AgentRunData;
  activeTurn: string;
  onSelectTurn: (id: string) => void;
  selectedId: string | null;
  onNodeSelect: (id: string | null, node: DetailNodeData | null) => void;
  onDrillIn?: (sub: AgentRunData) => void;
  /** Fetch a collapsed sub-agent's own execution and expand it in place (issue #1452). */
  onExpand?: (sub: AgentRunData) => void;
  onBack?: () => void;
}

export function AgentExecutionDiagram({
  agentRun,
  activeTurn,
  onSelectTurn,
  selectedId,
  onNodeSelect,
  onDrillIn,
  onExpand,
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
    () =>
      buildAgentExecutionDiagram(agentRun, activeTurn, hasBack, expandedGroups),
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
  const prevTurnRef = useRef<string | null>(null);
  useEffect(() => {
    if (prevTurnRef.current === null) {
      prevTurnRef.current = activeTurn;
      return;
    }
    if (prevTurnRef.current === activeTurn) return;
    prevTurnRef.current = activeTurn;

    if (!viewportRef.current || nodePositions.size === 0) return;

    const firstTurn = agentRun.turns[0]
      ? timelineItemId(agentRun.turns[0])
      : "turn-1";
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
          strategy: nd.strategy,
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
          strategy: nd.strategy,
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
        strategy: nd.strategy,
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
      data-testid="agent-execution-diagram"
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
              onExpand={onExpand}
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
