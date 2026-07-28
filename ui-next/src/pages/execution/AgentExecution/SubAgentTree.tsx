import { useState } from "react";
import { Box, Chip, Collapse, IconButton, Typography } from "@mui/material";
import {
  ArrowRight,
  CaretDown,
  CaretRight,
  CheckCircle,
  Spinner,
  XCircle,
} from "@phosphor-icons/react";
import { AgentRunData, AgentStatus, AgentStrategy } from "./types";
import {
  agentValuePreview,
  formatDuration,
  formatTokens,
} from "./agentExecutionUtils";

interface SubAgentTreeProps {
  subAgents: AgentRunData[];
  strategy?: AgentStrategy;
  onDrillIn: (agentRun: AgentRunData) => void;
  depth?: number;
}

function StatusIcon({
  status,
  size = 14,
}: {
  status: AgentStatus;
  size?: number;
}) {
  switch (status) {
    case AgentStatus.COMPLETED:
      return <CheckCircle size={size} color="#388e3c" weight="fill" />;
    case AgentStatus.FAILED:
      return <XCircle size={size} color="#d32f2f" weight="fill" />;
    default:
      return <Spinner size={size} color="#f57c00" />;
  }
}

const OUTPUT_PREVIEW_LINES = 4;

interface SubAgentNodeProps {
  agent: AgentRunData;
  onDrillIn: (run: AgentRunData) => void;
  depth: number;
}

function SubAgentNode({ agent, onDrillIn, depth }: SubAgentNodeProps) {
  const [outputExpanded, setOutputExpanded] = useState(false);
  const [childrenExpanded, setChildrenExpanded] = useState(false);

  const allSubAgents = agent.turns.flatMap((t) => t.subAgents);
  const hasChildren = allSubAgents.length > 0;
  const hasOutput = !!agent.output;
  const hasFailure =
    !!agent.failureReason && agent.status === AgentStatus.FAILED;

  const indent = depth * 16;

  // Determine if output is long enough to need truncation
  const outputText = agentValuePreview(agent.output, 10_000);
  const outputLines = outputText?.split("\n") ?? [];
  const isLongOutput =
    outputLines.length > OUTPUT_PREVIEW_LINES ||
    (outputText?.length ?? 0) > 400;
  const previewText =
    isLongOutput && !outputExpanded
      ? outputLines.slice(0, OUTPUT_PREVIEW_LINES).join("\n") + "\n…"
      : outputText;

  return (
    <Box>
      {/* Agent row */}
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 0.75,
          px: 1,
          py: 0.75,
          pl: `${8 + indent}px`,
          borderBottom: "1px solid",
          borderColor: "divider",
          backgroundColor: depth > 0 ? "grey.50" : "background.paper",
        }}
      >
        {/* Status icon */}
        <StatusIcon status={agent.status} size={14} />

        {/* Agent name */}
        <Typography
          variant="body2"
          sx={{
            fontFamily: "monospace",
            fontWeight: 600,
            fontSize: "0.8rem",
            flex: 1,
            minWidth: 0,
          }}
        >
          {agent.agentName}
        </Typography>

        {/* Model */}
        {agent.model && (
          <Typography
            variant="caption"
            color="text.disabled"
            sx={{ fontSize: "0.68rem" }}
          >
            {agent.model}
          </Typography>
        )}

        {/* Tokens */}
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ minWidth: 48, textAlign: "right", fontSize: "0.7rem" }}
        >
          {formatTokens(agent.totalTokens?.totalTokens ?? 0)} tok
        </Typography>

        {/* Duration */}
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ minWidth: 44, textAlign: "right", fontSize: "0.7rem" }}
        >
          {formatDuration(agent.totalDurationMs)}
        </Typography>

        {/* Children toggle */}
        {hasChildren && (
          <IconButton
            size="small"
            onClick={() => setChildrenExpanded((v) => !v)}
            title={`${childrenExpanded ? "Collapse" : "Expand"} sub-agents`}
          >
            {childrenExpanded ? (
              <CaretDown size={13} />
            ) : (
              <CaretRight size={13} />
            )}
          </IconButton>
        )}

        {/* Drill-in */}
        <IconButton
          size="small"
          onClick={() => onDrillIn(agent)}
          aria-label={`Drill into ${agent.agentName}`}
          title="View full execution"
        >
          <ArrowRight size={14} />
        </IconButton>
      </Box>

      {/* Output / failure — always visible, no click needed */}
      {(hasOutput || hasFailure) && (
        <Box
          sx={{
            pl: `${24 + indent}px`,
            pr: 2,
            py: 0.75,
            borderBottom: "1px solid",
            borderColor: "divider",
            backgroundColor: hasFailure ? "#fff5f5" : "grey.50",
          }}
        >
          {hasFailure && (
            <Box sx={{ mb: hasOutput ? 1 : 0 }}>
              <Typography
                variant="caption"
                sx={{
                  color: "#d32f2f",
                  fontWeight: 700,
                  display: "block",
                  mb: 0.25,
                }}
              >
                ✗ Failed: {agent.failureReason}
              </Typography>
            </Box>
          )}

          {hasOutput && (
            <>
              <Typography
                component="pre"
                sx={{
                  fontFamily: "monospace",
                  fontSize: "0.72rem",
                  whiteSpace: "pre-wrap",
                  wordBreak: "break-word",
                  color: "text.primary",
                  m: 0,
                  lineHeight: 1.5,
                }}
              >
                {previewText}
              </Typography>
              {isLongOutput && (
                <Typography
                  component="span"
                  variant="caption"
                  onClick={() => setOutputExpanded((v) => !v)}
                  sx={{
                    color: "primary.main",
                    cursor: "pointer",
                    display: "block",
                    mt: 0.5,
                  }}
                >
                  {outputExpanded ? "Show less" : "Show more"}
                </Typography>
              )}
            </>
          )}
        </Box>
      )}

      {/* Nested sub-agents (expanded on demand) */}
      {hasChildren && (
        <Collapse in={childrenExpanded} timeout="auto" unmountOnExit>
          <Box sx={{ borderBottom: "1px solid", borderColor: "divider" }}>
            <SubAgentTree
              subAgents={allSubAgents}
              onDrillIn={onDrillIn}
              depth={depth + 1}
            />
          </Box>
        </Collapse>
      )}
    </Box>
  );
}

export function SubAgentTree({
  subAgents,
  strategy,
  onDrillIn,
  depth = 0,
}: SubAgentTreeProps) {
  if (subAgents.length === 0) return null;

  const completedCount = subAgents.filter(
    (a) => a.status === AgentStatus.COMPLETED,
  ).length;
  const failedCount = subAgents.filter(
    (a) => a.status === AgentStatus.FAILED,
  ).length;
  const activeCount = subAgents.filter(
    (a) => a.status === AgentStatus.RUNNING || a.status === AgentStatus.WAITING,
  ).length;

  return (
    <Box>
      {/* Header — only at root depth */}
      {depth === 0 && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1,
            px: 1,
            py: 0.5,
            backgroundColor: "grey.100",
            borderBottom: "1px solid",
            borderColor: "divider",
          }}
        >
          <Typography
            variant="overline"
            sx={{
              fontWeight: 700,
              letterSpacing: 1,
              lineHeight: 1.5,
              fontSize: "0.65rem",
            }}
          >
            SUB-AGENTS ({subAgents.length})
          </Typography>
          {strategy &&
            (() => {
              const cfg: Record<
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
                [AgentStrategy.ROUTER]: {
                  label: "Router",
                  color: "#b45309",
                  bg: "#fef3c7",
                },
                [AgentStrategy.SINGLE]: {
                  label: "Single",
                  color: "#6b7280",
                  bg: "#f3f4f6",
                },
              };
              const c = cfg[strategy] ?? {
                label: strategy,
                color: "#6b7280",
                bg: "#f3f4f6",
              };
              return (
                <Box
                  sx={{
                    px: 0.75,
                    py: 0.25,
                    borderRadius: 1,
                    backgroundColor: c.bg,
                    color: c.color,
                    fontSize: "0.65rem",
                    fontWeight: 700,
                    letterSpacing: "0.03em",
                  }}
                >
                  {c.label}
                </Box>
              );
            })()}
          <Box sx={{ flex: 1 }} />
          {completedCount > 0 && (
            <Chip
              label={`${completedCount} ✓`}
              size="small"
              sx={{
                bgcolor: "#e8f5e9",
                color: "#388e3c",
                fontWeight: 600,
                height: 18,
                fontSize: "0.65rem",
              }}
            />
          )}
          {failedCount > 0 && (
            <Chip
              label={`${failedCount} ✗`}
              size="small"
              sx={{
                bgcolor: "#ffebee",
                color: "#d32f2f",
                fontWeight: 600,
                height: 18,
                fontSize: "0.65rem",
              }}
            />
          )}
          {activeCount > 0 && (
            <Chip
              label={`${activeCount} ⏳`}
              size="small"
              sx={{
                bgcolor: "#fff3e0",
                color: "#f57c00",
                fontWeight: 600,
                height: 18,
                fontSize: "0.65rem",
              }}
            />
          )}
        </Box>
      )}

      {subAgents.map((agent) => (
        <SubAgentNode
          key={agent.id}
          agent={agent}
          onDrillIn={onDrillIn}
          depth={depth}
        />
      ))}
    </Box>
  );
}

export default SubAgentTree;
