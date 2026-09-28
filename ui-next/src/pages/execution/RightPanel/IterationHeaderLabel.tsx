import { Box } from "@mui/material";
import { TaskStatus } from "types/TaskStatus";
import { IterationStatusIcon } from "./IterationStatusIcon";

interface IterationHeaderLabelProps {
  status: TaskStatus;
  text: string;
}

/**
 * Accordion header label used by both DoWhileIteration and
 * InlineTaskIterations: a small status icon followed by a text label.
 */
export function IterationHeaderLabel({
  status,
  text,
}: IterationHeaderLabelProps) {
  return (
    <Box
      component="span"
      sx={{ display: "inline-flex", alignItems: "center", gap: 0.75 }}
    >
      <IterationStatusIcon status={status} size={13} />
      <span>{text}</span>
    </Box>
  );
}
