import { FunctionComponent } from "react";
import { Grid, Paper } from "@mui/material";
import { State, ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import { TaskStatsMachineContext, TaskStatsEvents } from "./state";

interface CountBarProps {
  taskStatsActor: ActorRef<TaskStatsEvents>;
}

export const CountBar: FunctionComponent<CountBarProps> = ({
  taskStatsActor,
}) => {
  const [completed, failed, scheduled] = useSelector(
    taskStatsActor,
    (state: State<TaskStatsMachineContext>) => [
      state.context.completedAmount,
      state.context.failedAmount,
      state.context.scheduledAmount,
    ],
  );
  return (
    <Paper variant="outlined" sx={{ padding: 1, width: 500, margin: "auto" }}>
      <Grid
        container
        justifyContent="space-evenly"
        alignItems={"center"}
        direction="row"
        spacing={1}
        sx={{ width: "100%" }}
      >
        <Grid style={{ backgroundColor: "#1976d2", color: "white" }} size={12}>
          Current
        </Grid>
        <Grid sx={{ textAlign: "center" }} size={4}>
          <strong>{scheduled}</strong>
          <div>Scheduled</div>
        </Grid>
        <Grid alignItems="center" sx={{ textAlign: "center" }} size={4}>
          <strong>{completed}</strong>
          <div>Completed</div>
        </Grid>
        <Grid sx={{ textAlign: "center" }} size={4}>
          <strong>{failed}</strong>
          <div>Failed</div>
        </Grid>
      </Grid>
    </Paper>
  );
};
