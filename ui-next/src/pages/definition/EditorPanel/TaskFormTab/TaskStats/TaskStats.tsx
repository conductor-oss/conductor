import { Box, CircularProgress, Grid, Paper } from "@mui/material";
import { useActor, useSelector } from "@xstate/react";
import { useContext } from "react";
import { State } from "xstate";
import { TaskFormContext } from "../state";
import { CountBar } from "./CountBar";
import { RangeButtons } from "./RangeButtons";
import { TaskStatsEventTypes, TaskStatsMachineContext } from "./state";
import { TaskRateChart } from "./TaskRateChart";

export const TaskStats = () => {
  const { formTaskActor } = useContext(TaskFormContext);
  //@ts-ignore
  const taskStatsActor = formTaskActor?.children.get("taskStatsMachine");

  const [, send] = useActor(taskStatsActor);

  const completedPromethusData = useSelector(
    taskStatsActor,
    (state: State<TaskStatsMachineContext>) => {
      return state.context.completedRateSeries;
    },
  );

  const failedPromethusData = useSelector(
    taskStatsActor,
    (state: State<TaskStatsMachineContext>) => {
      return state.context.failedRateSeries;
    },
  );

  const startHoursBack = useSelector(
    taskStatsActor,
    (state: State<TaskStatsMachineContext>) => {
      return state.context.startHoursBack;
    },
  );

  const isIdle = useSelector(
    taskStatsActor,
    (state: State<TaskStatsMachineContext>) => state.matches("idle"),
  );

  const noStatsAvailable = useSelector(
    taskStatsActor,
    (state: State<TaskStatsMachineContext>) =>
      state.matches("noStatsAvailable"),
  );

  const changeRange = (newRange: number) => {
    send({
      type: TaskStatsEventTypes.CHANGE_START_TIME,
      value: newRange,
    });
  };

  if (noStatsAvailable) {
    return (
      <Paper variant="outlined" sx={{ backgroundColor: "rgb(250,250,250)" }}>
        <Box
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            paddingTop: 10,
          }}
        >
          Sorry! No stats available
        </Box>
      </Paper>
    );
  }

  return (
    <Paper variant="outlined" sx={{ backgroundColor: "rgb(250,250,250)" }}>
      <Box pt={4} pl={6} mb={1}>
        <b>TASK RATES</b>
      </Box>
      <div
        style={{
          paddingRight: "40px",
          paddingLeft: "8px",
          /* display: "flex", */
          /* justifyContent: "flex-end", */
        }}
      >
        <RangeButtons selected={startHoursBack} onChangeRange={changeRange} />
      </div>
      {isIdle ? (
        <Grid container sx={{ width: "100%" }} pb={10} spacing={2}>
          <Grid sx={{ textAlign: "center" }} mb={1} size={12}>
            <CountBar taskStatsActor={taskStatsActor} />
          </Grid>
          <Grid size={6}>
            <TaskRateChart
              data={completedPromethusData || []}
              color="#82ca9d"
              label="Completion rate"
            />
          </Grid>
          <Grid size={6}>
            <TaskRateChart
              data={failedPromethusData}
              color="#e55316"
              label="Failure rate"
            />
          </Grid>
        </Grid>
      ) : (
        <Box
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            paddingTop: 10,
          }}
        >
          <CircularProgress />
        </Box>
      )}
    </Paper>
  );
};
