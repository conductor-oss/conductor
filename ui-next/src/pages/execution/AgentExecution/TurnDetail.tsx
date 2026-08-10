import { Box, Typography } from "@mui/material";
import { AgentRunData, AgentTurn } from "./types";
import { formatDuration, formatTokens } from "./agentExecutionUtils";
import { EventRow } from "./EventRow";
import { SubAgentTree } from "./SubAgentTree";

interface TurnDetailProps {
  turn: AgentTurn;
  onDrillIn: (agentRun: AgentRunData) => void;
}

export function TurnDetail({ turn, onDrillIn }: TurnDetailProps) {
  return (
    <Box>
      {/* Header bar */}
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          px: 2,
          py: 1,
          backgroundColor: "grey.100",
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
      >
        <Typography
          variant="overline"
          sx={{ fontWeight: 700, letterSpacing: 1, lineHeight: 1.5 }}
        >
          Turn {turn.turnNumber}
        </Typography>

        <Box sx={{ flex: 1 }} />

        <Typography variant="caption" color="text.secondary" sx={{ mr: 2 }}>
          {formatTokens(turn.tokens.totalTokens)} tokens
        </Typography>

        <Typography variant="caption" color="text.secondary">
          {formatDuration(turn.durationMs)}
        </Typography>
      </Box>

      {/* Events list */}
      {turn.events.length > 0 && (
        <Box sx={{ py: 0.5 }}>
          {turn.events.map((event) => (
            <EventRow key={event.id} event={event} />
          ))}
        </Box>
      )}

      {/* Sub-agent section */}
      {turn.subAgents.length > 0 && (
        <Box sx={{ mt: 1 }}>
          <SubAgentTree
            subAgents={turn.subAgents}
            strategy={turn.strategy}
            onDrillIn={onDrillIn}
          />
        </Box>
      )}
    </Box>
  );
}

export default TurnDetail;
