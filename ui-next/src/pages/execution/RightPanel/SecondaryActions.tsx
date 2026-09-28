import { Box } from "@mui/material";
import { Button } from "components";
import { ExecutionTask } from "types/Execution";
import { usePushHistory } from "utils/hooks/usePushHistory";

export interface SecondaryActionsProps {
  selectedTask: ExecutionTask;
  containerQueryState: any;
  dynamicForkInstances: any;
}

export const SecondaryActions = ({
  selectedTask,
  containerQueryState,
  dynamicForkInstances,
}: SecondaryActionsProps) => {
  const navigate = usePushHistory();
  // Within dynamic forks, tasks can't be SIMPLE — the task name is used as the
  // type since it's generated. SIMPLE means we're looking at a real task def.
  return selectedTask?.workflowTask?.type === "SIMPLE" ? (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 2,
        alignItems: containerQueryState["small"] ? "start" : "end",
      }}
    >
      <Box
        sx={{
          display: "flex",
          gap: 2,
          flexGrow: 0,
          flexShrink: 0,
        }}
      >
        <Button
          color="secondary"
          size="small"
          onClick={() => {
            navigate(`/taskDef/${encodeURIComponent(selectedTask.taskType)}`);
          }}
        >
          Go to definition
        </Button>
      </Box>
    </Box>
  ) : (
    dynamicForkInstances
  );
};
