import { useState, type ReactNode } from "react";
import { Box, Collapse, Typography, Chip } from "@mui/material";
import {
  Brain,
  Wrench,
  ShieldCheck,
  ShieldWarning,
  ArrowRight,
  Pause,
  ChatCircle,
  Warning,
  Flag,
  CheckCircle,
  XCircle,
  Scissors,
} from "@phosphor-icons/react";
import { AgentEvent, EventType } from "./types";

// ─── Visual config ─────────────────────────────────────────────────────────

interface EventVisual {
  icon: ReactNode;
  color: string;
  label: string;
}

function getEventVisual(event: AgentEvent): EventVisual {
  switch (event.type) {
    case EventType.THINKING:
      // Use model name as label when this is an LLM call
      return {
        icon: <Brain size={15} weight="regular" />,
        color: "#9e9e9e",
        label: event.toolName ?? "LLM",
      };
    case EventType.TOOL_CALL:
      return {
        icon: <Wrench size={15} weight="regular" />,
        color: event.success === false ? "#d32f2f" : "#1976d2",
        label: event.toolName ?? "Tool",
      };
    case EventType.TOOL_RESULT:
      return event.success === false
        ? {
            icon: <XCircle size={15} weight="regular" />,
            color: "#d32f2f",
            label: "Error",
          }
        : {
            icon: <CheckCircle size={15} weight="regular" />,
            color: "#388e3c",
            label: "Result",
          };
    case EventType.GUARDRAIL_PASS:
      return {
        icon: <ShieldCheck size={15} weight="regular" />,
        color: "#388e3c",
        label: "Guardrail ✓",
      };
    case EventType.GUARDRAIL_FAIL:
      return {
        icon: <ShieldWarning size={15} weight="regular" />,
        color: "#d32f2f",
        label: "Guardrail ✗",
      };
    case EventType.HANDOFF:
      return {
        icon: <ArrowRight size={15} weight="regular" />,
        color: "#7b1fa2",
        label: "Handoff",
      };
    case EventType.WAITING:
      return {
        icon: <Pause size={15} weight="regular" />,
        color: "#f57c00",
        label: "Waiting",
      };
    case EventType.MESSAGE:
      return {
        icon: <ChatCircle size={15} weight="regular" />,
        color: "#424242",
        label: "Output",
      };
    case EventType.ERROR:
      return {
        icon: <Warning size={15} weight="regular" />,
        color: "#d32f2f",
        label: "Error",
      };
    case EventType.DONE:
      return {
        icon: <Flag size={15} weight="regular" />,
        color: "#388e3c",
        label: "Output",
      };
    case EventType.CONTEXT_CONDENSED:
      return {
        icon: <Scissors size={15} weight="regular" />,
        color: "#0288d1",
        label: "Condensed",
      };
    default:
      return {
        icon: <ChatCircle size={15} weight="regular" />,
        color: "#757575",
        label: "Event",
      };
  }
}

// ─── Detail renderer ───────────────────────────────────────────────────────

function JsonBlock({ value, label }: { value: unknown; label: string }) {
  const text =
    typeof value === "string" ? value : JSON.stringify(value, null, 2);
  return (
    <Box sx={{ mb: 1 }}>
      <Typography
        variant="caption"
        sx={{
          fontWeight: 700,
          color: "text.secondary",
          display: "block",
          mb: 0.25,
        }}
      >
        {label}
      </Typography>
      <Box
        component="pre"
        sx={{
          m: 0,
          p: 1,
          backgroundColor: "grey.50",
          border: "1px solid",
          borderColor: "divider",
          borderRadius: 0.5,
          fontSize: "0.72rem",
          fontFamily: "monospace",
          whiteSpace: "pre-wrap",
          wordBreak: "break-word",
          overflowY: "auto",
          maxHeight: 240,
        }}
      >
        {text}
      </Box>
    </Box>
  );
}

/** Renders expanded detail. For combined tool calls (detail.input + detail.output), shows two sections. */
function ExpandedDetail({ event }: { event: AgentEvent }) {
  const { detail, type } = event;
  if (detail === null || detail === undefined) return null;

  // Combined input/output block (tool call or LLM call)
  if (
    typeof detail === "object" &&
    !Array.isArray(detail) &&
    "input" in (detail as object) &&
    "output" in (detail as object)
  ) {
    const d = detail as { input: unknown; output: unknown };
    return (
      <Box>
        <JsonBlock value={d.input} label="Input" />
        <JsonBlock value={d.output} label="Output" />
      </Box>
    );
  }

  // Plain string or other value
  return (
    <JsonBlock
      value={detail}
      label={type === EventType.DONE ? "Output" : "Detail"}
    />
  );
}

// ─── Context condensation separator ────────────────────────────────────────

function CondensedSeparator({ event }: { event: AgentEvent }) {
  const info = event.condensationInfo;
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1,
        my: 0.75,
        px: 1.5,
        py: 0.5,
        borderRadius: 0.5,
        backgroundColor: "#e1f5fe",
        border: "1px dashed #0288d1",
        color: "#0277bd",
      }}
    >
      <Scissors size={13} weight="regular" style={{ flexShrink: 0 }} />
      <Typography
        variant="caption"
        sx={{ fontWeight: 600, fontSize: "0.72rem" }}
      >
        Context condensed
      </Typography>
      {info && (
        <Typography variant="caption" sx={{ fontSize: "0.7rem", opacity: 0.8 }}>
          · {info.messagesBefore} → {info.messagesAfter} messages (
          {info.exchangesCondensed} exchange
          {info.exchangesCondensed !== 1 ? "s" : ""} summarized · {info.trigger}
          )
        </Typography>
      )}
    </Box>
  );
}

// ─── Main component ────────────────────────────────────────────────────────

interface EventRowProps {
  event: AgentEvent;
}

export function EventRow({ event }: EventRowProps) {
  const [expanded, setExpanded] = useState(false);

  if (event.type === EventType.CONTEXT_CONDENSED) {
    return <CondensedSeparator event={event} />;
  }
  const visual = getEventVisual(event);
  const hasDetail = event.detail !== undefined && event.detail !== null;

  const tokenLabel =
    event.tokens && event.tokens.totalTokens > 0
      ? `${event.tokens.promptTokens}↑ ${event.tokens.completionTokens}↓`
      : null;

  const durationLabel =
    event.durationMs != null && event.durationMs > 0
      ? event.durationMs < 1000
        ? `${event.durationMs}ms`
        : `${(event.durationMs / 1000).toFixed(1)}s`
      : null;

  return (
    <Box
      onClick={() => hasDetail && setExpanded((v) => !v)}
      sx={{
        cursor: hasDetail ? "pointer" : "default",
        px: 1.5,
        py: 0.6,
        borderRadius: 0.5,
        "&:hover": hasDetail ? { backgroundColor: "action.hover" } : undefined,
        transition: "background-color 0.1s",
      }}
    >
      {/* Row header */}
      <Box
        sx={{ display: "flex", alignItems: "center", gap: 0.75, minWidth: 0 }}
      >
        {/* Icon */}
        <Box
          sx={{
            color: visual.color,
            display: "flex",
            alignItems: "center",
            flexShrink: 0,
          }}
        >
          {visual.icon}
        </Box>

        {/* Label chip */}
        <Chip
          label={visual.label}
          size="small"
          sx={{
            height: 18,
            fontSize: "0.68rem",
            fontWeight: 600,
            backgroundColor: `${visual.color}15`,
            color: visual.color,
            border: `1px solid ${visual.color}35`,
            flexShrink: 0,
            "& .MuiChip-label": { px: 0.6 },
          }}
        />

        {/* Summary */}
        <Typography
          variant="body2"
          sx={{
            flex: 1,
            minWidth: 0,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
            fontSize: "0.8rem",
            color: "text.primary",
          }}
        >
          {event.summary}
        </Typography>

        {/* Token count */}
        {tokenLabel && (
          <Typography
            variant="caption"
            sx={{ color: "text.disabled", flexShrink: 0, fontSize: "0.65rem" }}
          >
            {tokenLabel}
          </Typography>
        )}

        {/* Duration */}
        {durationLabel && (
          <Typography
            variant="caption"
            sx={{
              color: "text.disabled",
              flexShrink: 0,
              fontSize: "0.65rem",
              ml: 0.5,
            }}
          >
            {durationLabel}
          </Typography>
        )}

        {/* Expand indicator */}
        {hasDetail && (
          <Typography
            sx={{
              color: "text.disabled",
              fontSize: "0.55rem",
              flexShrink: 0,
              lineHeight: 1,
              userSelect: "none",
            }}
          >
            {expanded ? "▼" : "▶"}
          </Typography>
        )}
      </Box>

      {/* Expanded detail */}
      {hasDetail && (
        <Collapse in={expanded} timeout="auto" unmountOnExit>
          <Box sx={{ mt: 0.75, ml: 3, mr: 0.5 }}>
            <ExpandedDetail event={event} />
          </Box>
        </Collapse>
      )}
    </Box>
  );
}

export default EventRow;
