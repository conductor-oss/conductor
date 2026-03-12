import { Typography } from "@mui/material";
import { Box } from "@mui/system";
import { ClockIcon } from "@phosphor-icons/react";
import { WaitTaskDef } from "types";

interface WaitTaskInfoProps {
  task: WaitTaskDef;
}

export const WaitTaskInfo = ({ task }: WaitTaskInfoProps) => {
  const duration = task?.inputParameters?.duration;
  const until = task?.inputParameters?.until;

  if (!duration && !until) {
    return null;
  }

  // Determine label and display value
  const isUntil = !!until;
  const label = isUntil ? "Until" : "Duration";

  const durationDisplay = duration ? `${duration}` : until ? `${until}` : "";
  const durationDisplayLineHeight =
    durationDisplay.length > 30 ? "14px" : "auto";

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: "12px",
      }}
    >
      {/* Duration/Until Section */}
      <Box
        sx={{
          padding: "2px 12px",
          lineHeight: durationDisplayLineHeight,
        }}
      >
        <Box
          sx={{
            display: "flex",
            fontWeight: 600,
            alignItems: "center",
            gap: "6px",
            textAlign: "left",
          }}
        >
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              fontSize: "1em",
            }}
          >
            <ClockIcon size={16} weight="regular" />
          </Box>
          <Typography
            fontWeight={600}
          >{`${label}: ${durationDisplay}`}</Typography>
        </Box>
      </Box>
    </Box>
  );
};
