import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
} from "react";
import {
  Box,
  Chip,
  CircularProgress,
  IconButton,
  Tooltip,
  Typography,
} from "@mui/material";
import { ArrowLeft, Graph, ListBullets } from "@phosphor-icons/react";
import { agentValuePreview, getModelIconPath } from "./agentExecutionUtils";
import { AgentRunData, AgentStatus, AgentStrategy } from "./types";
import {
  formatDuration,
  formatTokens,
  timelineItemId,
} from "./agentExecutionUtils";
import { AgentExecutionDiagram } from "./AgentExecutionDiagram";
import { AgentDetailPanel, DetailNodeData } from "./AgentDetailPanel";
import { TurnBar } from "./TurnBar";
import { TurnDetail } from "./TurnDetail";

interface AgentRunViewProps {
  agentRun: AgentRunData;
  onDrillIn: (subAgentRun: AgentRunData) => void;
  onBack?: () => void;
  isRoot?: boolean;
}

type ViewMode = "diagram" | "timeline";

// ─── Status chip ──────────────────────────────────────────────────────────────

function StatusChip({ status }: { status: AgentStatus }) {
  const cfg = {
    [AgentStatus.COMPLETED]: {
      label: "Completed",
      bg: "#40BA56",
      color: "#fff",
    },
    [AgentStatus.FAILED]: { label: "Failed", bg: "#DD2222", color: "#fff" },
    [AgentStatus.RUNNING]: { label: "Running", bg: "#f59e0b", color: "#fff" },
    [AgentStatus.WAITING]: { label: "Waiting", bg: "#f59e0b", color: "#fff" },
  }[status] ?? { label: status, bg: "#9e9e9e", color: "#fff" };

  return (
    <Box
      sx={{
        display: "inline-flex",
        alignItems: "center",
        px: 1.5,
        py: 0.5,
        borderRadius: 1.5,
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontWeight: 700,
        fontSize: "0.8rem",
        letterSpacing: "0.02em",
        flexShrink: 0,
      }}
    >
      {cfg.label}
    </Box>
  );
}

// ─── Strategy chip ────────────────────────────────────────────────────────────

const STRATEGY_CHIP_CFG: Record<
  AgentStrategy,
  { label: string; color: string; bg: string }
> = {
  [AgentStrategy.HANDOFF]: {
    label: "Handoff",
    color: "#7c3aed",
    bg: "#ede9fe",
  },
  [AgentStrategy.PARALLEL]: {
    label: "Parallel",
    color: "#0369a1",
    bg: "#e0f2fe",
  },
  [AgentStrategy.SEQUENTIAL]: {
    label: "Sequential",
    color: "#0369a1",
    bg: "#e0f2fe",
  },
  [AgentStrategy.ROUTER]: { label: "Router", color: "#b45309", bg: "#fef3c7" },
  [AgentStrategy.SINGLE]: { label: "Single", color: "#6b7280", bg: "#f3f4f6" },
};

function StrategyChip({ strategy }: { strategy: AgentStrategy }) {
  const cfg = STRATEGY_CHIP_CFG[strategy] ?? {
    label: strategy,
    color: "#6b7280",
    bg: "#f3f4f6",
  };
  return (
    <Box
      sx={{
        display: "inline-flex",
        alignItems: "center",
        px: 0.75,
        py: 0.25,
        borderRadius: 1,
        backgroundColor: cfg.bg,
        color: cfg.color,
        fontSize: "0.7rem",
        fontWeight: 600,
        letterSpacing: "0.02em",
        flexShrink: 0,
      }}
    >
      {cfg.label}
    </Box>
  );
}

// ─── Agent run header ─────────────────────────────────────────────────────────

function AgentRunHeader({
  agentRun,
  isRoot,
  onBack,
}: {
  agentRun: AgentRunData;
  isRoot?: boolean;
  onBack?: () => void;
}) {
  const promptTok = agentRun.totalTokens.promptTokens;
  const completionTok = agentRun.totalTokens.completionTokens;
  const hasTokens = promptTok + completionTok > 0;

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
        px: 2,
        borderBottom: "1px solid",
        borderColor: "divider",
        backgroundColor: "#f3f3f3",
        minHeight: 44,
        flexWrap: "wrap",
        flexShrink: 0,
      }}
    >
      {/* Left: back button + name + model */}
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
        {onBack && (
          <Tooltip title="Back to parent agent" placement="bottom">
            <IconButton
              size="small"
              onClick={onBack}
              sx={{
                width: 28,
                height: 28,
                borderRadius: 1,
                color: "text.secondary",
                "&:hover": { backgroundColor: "action.hover" },
              }}
            >
              <ArrowLeft size={16} />
            </IconButton>
          </Tooltip>
        )}
        <Typography
          sx={{
            fontWeight: 700,
            fontSize: "0.875rem",
            color: "text.primary",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {agentRun.agentName}
        </Typography>

        {agentRun.model && (
          <Box
            sx={{
              display: "inline-flex",
              alignItems: "center",
              gap: 0.5,
              flexShrink: 0,
            }}
          >
            {(() => {
              const icon = getModelIconPath(agentRun.model);
              return icon ? (
                <img
                  src={icon}
                  style={{ width: 14, height: 14, objectFit: "contain" }}
                  alt=""
                />
              ) : null;
            })()}
            <Chip
              label={agentRun.model}
              size="small"
              sx={{
                height: 20,
                fontSize: "0.75rem",
                fontWeight: 500,
                backgroundColor: "transparent",
                color: "text.primary",
                border: "1px solid",
                borderColor: "divider",
                "& .MuiChip-label": { px: 0.75 },
              }}
            />
          </Box>
        )}

        {agentRun.strategy && agentRun.strategy !== AgentStrategy.SINGLE && (
          <StrategyChip strategy={agentRun.strategy} />
        )}
      </Box>

      {/* Status — skip at root since execution header already shows it */}
      {!isRoot && <StatusChip status={agentRun.status} />}

      <Box sx={{ flex: 1 }} />

      {/* Metrics — skip at root since execution header already shows totals */}
      {!isRoot && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.4 }}>
            <Typography
              sx={{ fontWeight: 600, fontSize: "0.875rem", lineHeight: 1 }}
            >
              {formatDuration(agentRun.totalDurationMs)}
            </Typography>
            <Typography
              variant="caption"
              color="text.disabled"
              sx={{ fontSize: "0.7rem" }}
            >
              dur
            </Typography>
          </Box>

          {hasTokens && (
            <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.4 }}>
              <Typography
                sx={{ fontWeight: 600, fontSize: "0.875rem", lineHeight: 1 }}
              >
                {formatTokens(promptTok)}
              </Typography>
              <Typography
                variant="caption"
                color="text.disabled"
                sx={{ fontSize: "0.7rem" }}
              >
                ↑
              </Typography>
              <Typography
                sx={{ fontWeight: 600, fontSize: "0.875rem", lineHeight: 1 }}
              >
                {formatTokens(completionTok)}
              </Typography>
              <Typography
                variant="caption"
                color="text.disabled"
                sx={{ fontSize: "0.7rem" }}
              >
                ↓ tok
              </Typography>
            </Box>
          )}
        </Box>
      )}
    </Box>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export function AgentRunView({
  agentRun,
  onDrillIn,
  onBack,
  isRoot,
}: AgentRunViewProps) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedNode, setSelectedNode] = useState<DetailNodeData | null>(null);
  const [selectedTurn, setSelectedTurn] = useState(
    agentRun.turns[0] ? timelineItemId(agentRun.turns[0]) : "turn-1",
  );
  const [panelWidth, setPanelWidth] = useState<number>(1125);
  const [viewMode, setViewMode] = useState<ViewMode>("diagram");

  const isDragging = useRef(false);
  const dragStartX = useRef(0);
  const dragStartWidth = useRef(0);
  const panelRef = useRef<HTMLDivElement>(null);

  // Reset selection and turn when agent changes
  useEffect(() => {
    setSelectedId(null);
    setSelectedNode(null);
    setSelectedTurn(
      agentRun.turns[0] ? timelineItemId(agentRun.turns[0]) : "turn-1",
    );
  }, [agentRun.id]);

  const handleDragStart = useCallback((e: ReactMouseEvent) => {
    isDragging.current = true;
    dragStartX.current = e.clientX;
    dragStartWidth.current = panelRef.current?.offsetWidth ?? 480;
    const onMove = (ev: MouseEvent) => {
      if (!isDragging.current) return;
      const delta = dragStartX.current - ev.clientX;
      setPanelWidth(Math.max(300, dragStartWidth.current + delta));
    };
    const onUp = () => {
      isDragging.current = false;
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("mouseup", onUp);
    };
    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  }, []);

  // Node click: open detail panel. Sub-agent navigation is intentional only
  // (via "View full execution" button in the panel, not auto-navigate on click).
  const handleNodeSelect = (id: string | null, node: DetailNodeData | null) => {
    setSelectedId(id);
    setSelectedNode(node);
  };

  const activeTurnData =
    agentRun.turns.find((t) => timelineItemId(t) === selectedTurn) ??
    agentRun.turns[0];

  return (
    <Box
      sx={{
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {/* Agent identity bar: name + model + strategy + back button */}
      <AgentRunHeader agentRun={agentRun} isRoot={isRoot} onBack={onBack} />

      {/* Top bar: back button + turn bar + view-mode toggle */}
      <Box
        sx={{
          px: 2,
          py: 1,
          borderBottom: "1px solid",
          borderColor: "divider",
          backgroundColor: "#fff",
          flexShrink: 0,
          display: "flex",
          alignItems: "center",
          gap: 1,
        }}
      >
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {agentRun.turns.length > 0 && (
            <TurnBar
              turns={agentRun.turns}
              selectedTurn={selectedTurn}
              onSelectTurn={setSelectedTurn}
            />
          )}
        </Box>

        {/* View toggle: Timeline | Diagram */}
        <Box sx={{ display: "flex", gap: 0.5, flexShrink: 0, ml: 1 }}>
          <Tooltip title="Timeline view" placement="bottom">
            <IconButton
              size="small"
              onClick={() => setViewMode("timeline")}
              sx={{
                width: 28,
                height: 28,
                borderRadius: 1,
                color: viewMode === "timeline" ? "#4969e4" : "text.disabled",
                backgroundColor:
                  viewMode === "timeline" ? "#ebedfb" : "transparent",
                "&:hover": {
                  backgroundColor:
                    viewMode === "timeline" ? "#dde2f8" : "action.hover",
                },
              }}
            >
              <ListBullets size={16} />
            </IconButton>
          </Tooltip>
          <Tooltip title="Diagram view" placement="bottom">
            <IconButton
              size="small"
              onClick={() => setViewMode("diagram")}
              sx={{
                width: 28,
                height: 28,
                borderRadius: 1,
                color: viewMode === "diagram" ? "#4969e4" : "text.disabled",
                backgroundColor:
                  viewMode === "diagram" ? "#ebedfb" : "transparent",
                "&:hover": {
                  backgroundColor:
                    viewMode === "diagram" ? "#dde2f8" : "action.hover",
                },
              }}
            >
              <Graph size={16} />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* Two-pane content */}
      <Box sx={{ flex: 1, minHeight: 0, display: "flex", overflow: "hidden" }}>
        {/* Left: main content */}
        <Box
          sx={{
            flex: 1,
            minWidth: 0,
            overflow: "hidden",
            position: "relative",
          }}
        >
          {agentRun.turns.length === 0 ? (
            <Box
              sx={{
                height: "100%",
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
                  gap: 2,
                  maxWidth: 480,
                  px: 3,
                }}
              >
                {agentRun.status === AgentStatus.RUNNING && (
                  <CircularProgress size={28} sx={{ color: "#f59e0b" }} />
                )}
                <Typography
                  sx={{
                    fontWeight: 700,
                    fontSize: "1rem",
                    color: "text.primary",
                  }}
                >
                  {agentRun.agentName}
                </Typography>
                {agentRun.model && (
                  <Box
                    sx={{ display: "flex", alignItems: "center", gap: 0.75 }}
                  >
                    {(() => {
                      const icon = getModelIconPath(agentRun.model);
                      return icon ? (
                        <img
                          src={icon}
                          style={{
                            width: 16,
                            height: 16,
                            objectFit: "contain",
                          }}
                          alt=""
                        />
                      ) : null;
                    })()}
                    <Typography
                      sx={{ fontSize: "0.8rem", color: "text.secondary" }}
                    >
                      {agentRun.model}
                    </Typography>
                  </Box>
                )}
                {agentRun.input != null && (
                  <Box
                    sx={{
                      mt: 0.5,
                      px: 2,
                      py: 1.5,
                      borderRadius: 2,
                      backgroundColor: "#f8f9fa",
                      border: "1px solid",
                      borderColor: "divider",
                      width: "100%",
                    }}
                  >
                    <Typography
                      sx={{
                        fontSize: "0.7rem",
                        fontWeight: 600,
                        color: "text.disabled",
                        mb: 0.5,
                        textTransform: "uppercase",
                        letterSpacing: "0.05em",
                      }}
                    >
                      Input
                    </Typography>
                    <Typography
                      sx={{
                        fontSize: "0.8rem",
                        color: "text.secondary",
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word",
                      }}
                    >
                      {agentValuePreview(agentRun.input, 500)}
                    </Typography>
                  </Box>
                )}
                {agentRun.status === AgentStatus.RUNNING && (
                  <Typography variant="caption" color="text.disabled">
                    Waiting for agent to start processing...
                  </Typography>
                )}
              </Box>
            </Box>
          ) : viewMode === "diagram" ? (
            <AgentExecutionDiagram
              agentRun={agentRun}
              activeTurn={selectedTurn}
              onSelectTurn={setSelectedTurn}
              selectedId={selectedId}
              onNodeSelect={handleNodeSelect}
              onDrillIn={onDrillIn}
              onBack={onBack}
            />
          ) : (
            /* Timeline view — all events for selected turn, scrollable */
            <Box
              sx={{
                height: "100%",
                overflowY: "auto",
                backgroundColor: "#fff",
                scrollbarWidth: "none",
                "&::-webkit-scrollbar": { display: "none" },
              }}
            >
              {activeTurnData && (
                <TurnDetail turn={activeTurnData} onDrillIn={onDrillIn} />
              )}
            </Box>
          )}
        </Box>

        {/* Right: resizable detail panel (diagram mode only) */}
        {selectedNode && viewMode === "diagram" && (
          <Box
            sx={{
              display: "flex",
              height: "100%",
              flexShrink: 0,
              backgroundColor: "#fff",
            }}
          >
            {/* Drag handle */}
            <Box
              onMouseDown={handleDragStart}
              sx={{
                width: 5,
                cursor: "col-resize",
                flexShrink: 0,
                backgroundColor: "transparent",
                transition: "background-color 0.15s",
                "&:hover": { backgroundColor: "primary.main" },
                zIndex: 10,
              }}
            />
            <Box
              ref={panelRef}
              sx={{
                width: panelWidth,
                flexShrink: 0,
                height: "100%",
                overflow: "hidden",
                boxShadow: "-3px 0 12px rgba(0,0,0,0.06)",
              }}
            >
              <AgentDetailPanel
                node={selectedNode}
                onClose={() => {
                  setSelectedId(null);
                  setSelectedNode(null);
                }}
                onDrillIn={onDrillIn}
              />
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  );
}

export default AgentRunView;
