import { Box, Typography } from "@mui/material";
import { AgentRunData, ExecutionMetrics } from "./types";
import { formatDuration, formatTokens } from "./agentExecutionUtils";

const RED = "#DD2222";
const LABEL_COLOR = "#4969e4"; // dark blue — reused from app's indigo07

interface AgentExecutionHeaderProps {
  metrics: ExecutionMetrics;
  rootRun: AgentRunData;
}

interface MetricProps {
  label: string;
  value: string;
  unit?: string;
}

function Metric({ label, value, unit }: MetricProps) {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 0 }}>
      <Typography
        sx={{
          fontSize: "0.65rem",
          fontWeight: 600,
          letterSpacing: "0.06em",
          textTransform: "uppercase",
          color: LABEL_COLOR,
          lineHeight: 1,
          mb: 0.4,
        }}
      >
        {label}
      </Typography>
      <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.3 }}>
        <Typography
          sx={{
            fontWeight: 600,
            fontSize: "0.875rem",
            lineHeight: 1,
            color: "text.primary",
          }}
        >
          {value}
        </Typography>
        {unit && (
          <Typography
            sx={{ fontSize: "0.65rem", color: "text.secondary", lineHeight: 1 }}
          >
            {unit}
          </Typography>
        )}
      </Box>
    </Box>
  );
}

export function AgentExecutionHeader({
  metrics,
  rootRun,
}: AgentExecutionHeaderProps) {
  const { promptTokens, completionTokens } = metrics.totalTokens;
  const hasTokens = promptTokens + completionTokens > 0;

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 0,
        px: 0,
        height: 52,
        borderBottom: "1px solid",
        borderColor: "divider",
        backgroundColor: "#fff",
        overflow: "hidden",
      }}
    >
      {/* Metrics */}
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 0,
          flex: 1,
          height: "100%",
          overflowX: "auto",
          scrollbarWidth: "none",
          "&::-webkit-scrollbar": { display: "none" },
        }}
      >
        {/* Duration */}
        <Box
          sx={{
            px: 2.5,
            height: "100%",
            display: "flex",
            alignItems: "center",
            borderRight: "1px solid",
            borderColor: "divider",
          }}
        >
          <Metric
            label="Duration"
            value={formatDuration(metrics.totalDurationMs)}
          />
        </Box>

        {/* Tokens */}
        {hasTokens && (
          <Box
            sx={{
              px: 2.5,
              height: "100%",
              display: "flex",
              alignItems: "center",
              borderRight: "1px solid",
              borderColor: "divider",
            }}
          >
            <Metric
              label="Tokens"
              value={`${formatTokens(promptTokens)} / ${formatTokens(completionTokens)}`}
              unit="prompt / completion"
            />
          </Box>
        )}

        {/* Agents */}
        {metrics.totalAgents > 1 && (
          <Box
            sx={{
              px: 2.5,
              height: "100%",
              display: "flex",
              alignItems: "center",
              borderRight: "1px solid",
              borderColor: "divider",
            }}
          >
            <Metric label="Agents" value={String(metrics.totalAgents)} />
          </Box>
        )}

        {/* Finish reason */}
        {rootRun.finishReason && (
          <Box
            sx={{
              px: 2.5,
              height: "100%",
              display: "flex",
              alignItems: "center",
            }}
          >
            <Metric label="Finish Reason" value={rootRun.finishReason} />
          </Box>
        )}
      </Box>

      {/* Failures badge — only when something failed */}
      {metrics.failedAgents > 0 && (
        <Box
          sx={{
            px: 2,
            height: "100%",
            display: "flex",
            alignItems: "center",
            borderLeft: "1px solid",
            borderColor: "divider",
            backgroundColor: "#fff5f5",
            flexShrink: 0,
          }}
        >
          <Typography sx={{ fontSize: "0.75rem", fontWeight: 700, color: RED }}>
            {metrics.failedAgents} failed
          </Typography>
        </Box>
      )}
    </Box>
  );
}

export default AgentExecutionHeader;
