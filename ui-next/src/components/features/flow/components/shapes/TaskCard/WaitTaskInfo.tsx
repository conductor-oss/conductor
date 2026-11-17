import { Typography } from "@mui/material";
import { Box } from "@mui/system";
import { ClockIcon } from "@phosphor-icons/react";
import { WaitTaskDef } from "types";
import { WAIT_INFO_LINE_HEIGHT, waitTaskInfoText } from "./waitTaskInfoLayout";

interface WaitTaskInfoProps {
  task: WaitTaskDef;
}

export const WaitTaskInfo = ({ task }: WaitTaskInfoProps) => {
  const text = waitTaskInfoText(task);

  if (!text) {
    return null;
  }

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: "12px",
      }}
    >
      {/* Duration/Until Section */}
      <Box sx={{ padding: "2px 12px" }}>
        <Box
          sx={{
            display: "flex",
            fontWeight: 600,
            alignItems: "flex-start",
            gap: "6px",
            textAlign: "left",
          }}
        >
          {/* As tall as one line, so the icon lines up with the first line. */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              height: WAIT_INFO_LINE_HEIGHT,
              flexShrink: 0,
            }}
          >
            <ClockIcon size={16} weight="regular" />
          </Box>
          {/* Long values wrap, breaking inside ${...} expressions if needed.
              The node's height grows to fit (see waitTaskInfoExtraHeight). */}
          <Typography
            fontWeight={600}
            sx={{ minWidth: 0, overflowWrap: "anywhere" }}
          >
            {text}
          </Typography>
        </Box>
      </Box>
    </Box>
  );
};
