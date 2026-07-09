import { useRef, useEffect } from "react";
import { Box, MenuItem, Select, Tooltip, Typography } from "@mui/material";
import { AgentTurn, AgentStatus } from "./types";
import { formatDuration } from "./agentExecutionUtils";

interface TurnBarProps {
  turns: AgentTurn[];
  selectedTurn: number;
  onSelectTurn: (turnNumber: number) => void;
}

const GREEN = "#40BA56";
const RED = "#DD2222";
const AMBER = "#f59e0b";

function turnColor(status: AgentStatus) {
  if (status === AgentStatus.FAILED) return RED;
  if (status === AgentStatus.RUNNING || status === AgentStatus.WAITING)
    return AMBER;
  return GREEN;
}

export function TurnBar({ turns, selectedTurn, onSelectTurn }: TurnBarProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current?.querySelector(
      `[data-turn="${selectedTurn}"]`,
    ) as HTMLElement | null;
    el?.scrollIntoView({
      block: "nearest",
      inline: "center",
      behavior: "smooth",
    });
  }, [selectedTurn]);

  if (turns.length === 0) return null;

  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 1, minHeight: 32 }}>
      <Box
        ref={scrollRef}
        sx={{
          display: "flex",
          alignItems: "stretch",
          flex: 1,
          overflowX: "auto",
          scrollbarWidth: "none",
          "&::-webkit-scrollbar": { display: "none" },
          // Segmented control — no gap between adjacent squares
          "& > *:first-of-type button": { borderRadius: "4px 0 0 4px" },
          "& > *:last-of-type button": { borderRadius: "0 4px 4px 0" },
        }}
      >
        {turns.map((turn, i) => {
          const active = turn.turnNumber === selectedTurn;
          const color = turnColor(turn.status);
          const isFirst = i === 0;
          const isLast = i === turns.length - 1;

          return (
            <Tooltip
              key={turn.turnNumber}
              title={
                <Box sx={{ fontSize: "0.72rem" }}>
                  <div style={{ fontWeight: 700, marginBottom: 2 }}>
                    Turn {turn.turnNumber}
                  </div>
                  {formatDuration(turn.durationMs) !== "—" && (
                    <div>{formatDuration(turn.durationMs)}</div>
                  )}
                  {turn.subAgents.length > 0 && (
                    <div>
                      {turn.subAgents.length} sub-agent
                      {turn.subAgents.length > 1 ? "s" : ""}
                    </div>
                  )}
                </Box>
              }
              placement="bottom"
              arrow
            >
              <Box
                component="button"
                data-turn={turn.turnNumber}
                onClick={() => onSelectTurn(turn.turnNumber)}
                sx={{
                  // Reset button styles
                  appearance: "none",
                  fontFamily: "inherit",
                  cursor: "pointer",
                  // Fixed-width pill
                  width: 80,
                  flexShrink: 0,
                  height: 26,
                  px: 0,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "3px",
                  // Visual
                  backgroundColor: active ? color : "#fff",
                  color: active ? "#fff" : "#858585",
                  borderTop: `1px solid ${active ? color : "#DDDDDD"}`,
                  borderBottom: `1px solid ${active ? color : "#DDDDDD"}`,
                  borderRight: `1px solid ${active ? color : "#DDDDDD"}`,
                  borderLeft: `1px solid ${active ? color : "#DDDDDD"}`,
                  // Radius — only on ends
                  borderRadius: isFirst
                    ? "3px 0 0 3px"
                    : isLast
                      ? "0 3px 3px 0"
                      : 0,
                  // Collapse adjacent borders
                  marginRight: isLast ? 0 : "-1px",
                  position: "relative",
                  zIndex: active ? 1 : 0,
                  transition: "all 0.1s ease",
                  outline: "none",
                  "&:hover": {
                    zIndex: 2,
                    borderColor: color,
                    color: active ? "#fff" : color,
                    backgroundColor: active ? color : `${color}12`,
                  },
                }}
              >
                <Typography
                  component="span"
                  sx={{
                    fontSize: "0.7rem",
                    fontWeight: active ? 700 : 500,
                    lineHeight: 1,
                    color: "inherit",
                    letterSpacing: "0.01em",
                  }}
                >
                  {turn.turnNumber}
                </Typography>

                {/* Tiny status dot — only failed/running */}
                {!active && turn.status !== AgentStatus.COMPLETED && (
                  <Box
                    sx={{
                      width: 3,
                      height: 3,
                      borderRadius: "50%",
                      backgroundColor: color,
                      flexShrink: 0,
                    }}
                  />
                )}
              </Box>
            </Tooltip>
          );
        })}
      </Box>

      {/* Jump dropdown — only when many turns */}
      {turns.length > 8 && (
        <Select
          value={selectedTurn}
          onChange={(e) => onSelectTurn(Number(e.target.value))}
          size="small"
          variant="outlined"
          sx={{
            flexShrink: 0,
            height: 28,
            fontSize: "0.72rem",
            "& .MuiSelect-select": { py: 0.4, px: 1 },
            "& .MuiOutlinedInput-notchedOutline": { borderColor: "#DDDDDD" },
          }}
        >
          {turns.map((t) => (
            <MenuItem
              key={t.turnNumber}
              value={t.turnNumber}
              sx={{ fontSize: "0.78rem" }}
            >
              Turn {t.turnNumber}
            </MenuItem>
          ))}
        </Select>
      )}
    </Box>
  );
}

export default TurnBar;
