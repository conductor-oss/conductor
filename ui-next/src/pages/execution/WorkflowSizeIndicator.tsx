import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import { Box, LinearProgress, Tooltip } from "@mui/material";

export interface WorkflowSizeResponse {
  sizeBytes: number;
  limitBytes: number;
  ratio: number;
}

const SIZE_WARNING_THRESHOLD = 0.8;

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export function WorkflowSizeIndicator({
  sizeData,
}: {
  sizeData: WorkflowSizeResponse;
}) {
  const { sizeBytes, limitBytes, ratio } = sizeData;
  const isNearLimit = ratio > SIZE_WARNING_THRESHOLD;
  const hasLimit = limitBytes > 0;
  const pct = Math.min(ratio * 100, 100);

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 0.5,
        width: "100%",
        maxWidth: 320,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
        <Box component="span" sx={{ fontWeight: 500 }}>
          {formatBytes(sizeBytes)}
        </Box>
        {hasLimit && (
          <Box component="span" sx={{ opacity: 0.6, fontSize: "0.85em" }}>
            / {formatBytes(limitBytes)} limit ({pct.toFixed(0)}% used)
          </Box>
        )}
        {isNearLimit && (
          <Tooltip title="Workflow size is above 80% of the limit. Consider reducing task payloads.">
            <WarningAmberIcon
              sx={{
                fontSize: 18,
                color: "warning.main",
                verticalAlign: "middle",
              }}
            />
          </Tooltip>
        )}
      </Box>
      {hasLimit && (
        <LinearProgress
          variant="determinate"
          value={pct}
          sx={{
            height: 6,
            borderRadius: 3,
            backgroundColor: "rgba(0,0,0,0.1)",
            "& .MuiLinearProgress-bar": {
              backgroundColor: isNearLimit ? "warning.main" : "primary.main",
              borderRadius: 3,
            },
          }}
        />
      )}
    </Box>
  );
}
