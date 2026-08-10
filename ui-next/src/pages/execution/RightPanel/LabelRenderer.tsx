import { Box } from "@mui/material";
import { dropdownIcon } from "./dropdownIcon";

export interface LabelRendererProps {
  iterationTask: any;
  isIteration?: boolean;
  hideTaskId?: boolean;
}

export const LabelRenderer = ({
  iterationTask,
  isIteration,
  hideTaskId = false,
}: LabelRendererProps) => {
  const isSummarized = iterationTask._summarized === true;
  // Use the iteration number from the task data when available — this is more
  // reliable than the `isIteration` prop, which depends on `loopOverTask`
  // being set correctly on the raw task (not always the case for INLINE tasks).
  const iterationNumber: number | undefined =
    typeof iterationTask.iteration === "number" && iterationTask.iteration > 0
      ? iterationTask.iteration
      : undefined;
  const showAsIteration = isIteration || iterationNumber != null;

  const textLabel = showAsIteration
    ? `Iteration ${iterationNumber ?? iterationTask.iteration}${
        iterationTask?.retryCount > 0
          ? " - retry attempt " + iterationTask.retryCount
          : ""
      }`
    : `Attempt #${iterationTask.retryCount ?? 0}`;

  return (
    <Box sx={{ marginRight: "auto" }}>
      {dropdownIcon(iterationTask.status)} {`${textLabel}`}
      {!hideTaskId && !isSummarized && iterationTask.taskId
        ? ` - ${iterationTask.taskId}`
        : null}
      {isSummarized ? (
        <Box
          component="span"
          sx={{ fontSize: "8pt", opacity: 0.6, marginLeft: 1 }}
        >
          (summarized)
        </Box>
      ) : null}
    </Box>
  );
};
