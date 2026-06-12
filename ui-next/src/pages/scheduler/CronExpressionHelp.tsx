import { Box } from "@mui/material";
import { CRON_COLORS_BY_POSITION } from "./constants";

type Props = {
  highlightedPart?: number | null;
};

const CronExpressionHelp = ({ highlightedPart }: Props) => {
  const items = [
    { text: "Second (0-59)", color: CRON_COLORS_BY_POSITION[0] },
    { text: "Minute (0-59)", color: CRON_COLORS_BY_POSITION[1] },
    { text: "Hour (0-23)", color: CRON_COLORS_BY_POSITION[2] },
    { text: "Day of Month (1-31)", color: CRON_COLORS_BY_POSITION[3] },
    { text: "Month (1-12, JAN-DEC)", color: CRON_COLORS_BY_POSITION[4] },
    { text: "Day of Week (1-7 or MON-SUN)", color: CRON_COLORS_BY_POSITION[5] },
  ];
  const width = 180;
  const height = 120;

  return (
    <Box sx={{ display: "flex", padding: 3 }}>
      <Box>
        <Box
          sx={{
            position: "relative",
            height: `${height}px`,
            width: `${width}px`,
          }}
        >
          {items.map((item, index) => {
            const xStep = width / items.length;
            const yStep = height / items.length;
            return (
              <Box
                key={index}
                sx={{
                  position: "absolute",
                  top: `${index * yStep}px`,
                  left: `${index * xStep}px`,
                  width: `${width - index * xStep}px`,
                  height: `${height - index * yStep}px`,
                  borderRadius: "14px 0 0 0",
                  borderTop: `2px solid ${item.color}`,
                  borderLeft: `2px solid ${item.color}`,
                  opacity:
                    highlightedPart === index || highlightedPart === null
                      ? 1
                      : 0.3,
                }}
              ></Box>
            );
          })}
        </Box>
        <Box
          sx={{
            display: "flex",
            marginLeft: "-4px",
          }}
        >
          {items.map((item, index) => {
            const blockWidth = width / items.length;
            return (
              <Box
                key={index}
                sx={{
                  width: `${blockWidth}px`,
                  fontSize: "18px",
                  fontWeight: "bold",
                  textAlign: "left",
                  color: item.color,
                  opacity:
                    highlightedPart === index || highlightedPart === null
                      ? 1
                      : 0.3,
                }}
              >
                *
              </Box>
            );
          })}
        </Box>
      </Box>
      <Box>
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            marginLeft: "6px",
            marginTop: "-7px",
          }}
        >
          {items.map((item, index) => {
            const blockHeight = height / items.length;
            return (
              <Box
                key={index}
                sx={{
                  height: `${blockHeight}px`,
                  fontSize: "11px",
                  textAlign: "left",
                  whiteSpace: "nowrap",
                  textOverflow: "ellipsis",
                  overflow: "hidden",
                  color: item.color,
                  opacity:
                    highlightedPart === index || highlightedPart === null
                      ? 1
                      : 0.3,
                }}
              >
                {item.text}
              </Box>
            );
          })}
        </Box>
      </Box>
    </Box>
  );
};

export default CronExpressionHelp;
