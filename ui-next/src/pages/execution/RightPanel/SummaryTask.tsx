import { FunctionComponent } from "react";
import { Grid, Paper } from "@mui/material";
import { X as CloseIcon } from "@phosphor-icons/react";
import { ExecutionTask, TaskStatus } from "types";
import { taskStatusCompareFn } from "utils";

import { KeyValueTable } from "components";
import StatusBadge from "components/StatusBadge";
import { useQueryState } from "react-router-use-location-state";
import { ExecutionTabs } from "pages/execution/state/types";
import IconButton from "components/ui/buttons/MuiIconButton";
import MuiTypography from "components/ui/MuiTypography";

interface TaskSummaryProps {
  selectedTask: ExecutionTask;
  onClose: () => void;
}

export const SummaryTask: FunctionComponent<TaskSummaryProps> = ({
  selectedTask,
  onClose,
}) => {
  // Goes through useQueryState like the rest of the execution page, so the
  // query param clearing done by onClose merges with "tab" instead of
  // rebuilding the URL from the pre-click location and dropping it. Pushed so
  // Back returns to the tab the summary was opened from.
  const [, setTab] = useQueryState("tab", "");

  return (
    <Paper square elevation={0}>
      <IconButton
        color="secondary"
        size="small"
        aria-label="Close button"
        onClick={onClose}
      >
        <CloseIcon />
      </IconButton>
      <Grid container sx={{ width: "100%" }} mt={4} p={4} spacing={2}>
        <Grid size={12}>
          <MuiTypography paddingLeft="8px">
            There are way too much tasks to render here is a summary of the
            nested tasks.{" "}
            <span
              style={{
                color: "#1976d2",
                fontWeight: "bold",
                cursor: "pointer",
              }}
              onClick={() => {
                setTab(ExecutionTabs.TASK_LIST_TAB, { method: "push" });
                onClose();
              }}
            >
              Click here
            </span>{" "}
            to see the list of tasks.
          </MuiTypography>
        </Grid>
        <Grid size={12}>
          <KeyValueTable
            data={Object.entries(
              (selectedTask?.outputData as any)?.summary
                ?.taskCountByStatus as Record<string, string>,
            )

              .sort(([key1], [key2]) => taskStatusCompareFn(key1, key2))
              .map(([key, value]: [string, string]) => ({
                label: <StatusBadge status={key as TaskStatus} />,
                value,
              }))}
          />
        </Grid>
      </Grid>
    </Paper>
  );
};
