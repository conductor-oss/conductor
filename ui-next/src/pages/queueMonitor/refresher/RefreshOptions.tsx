import {
  CircularProgress,
  FormControlLabel,
  Grid,
  Radio,
  RadioGroup,
} from "@mui/material";
import { ArrowClockwise as RefreshIcon } from "@phosphor-icons/react";
import { useActor, useSelector } from "@xstate/react";
import Button from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import { FunctionComponent, ReactNode, useContext, useMemo } from "react";
import { ActorRef, State } from "xstate";
import { QueueMonitorContext } from "../state";
import {
  RefreshMachineContext,
  RefreshMachineEventTypes,
  TimerEvents,
} from "./state";

const REFRESH_SECONDS_OPTIONS = [1, 10, 30, 60];

interface RefreshOptionsPresentationalProps {
  onRefresh: () => void;
  timerActor: ActorRef<TimerEvents>;
  startIcon: ReactNode;
}

export const RefreshButton: FunctionComponent<
  RefreshOptionsPresentationalProps
> = ({ onRefresh, timerActor, startIcon }) => {
  const refreshInterval = useSelector(
    timerActor,
    (state: State<RefreshMachineContext>) => state.context.durationSet,
  );

  const elapsed = useSelector(
    timerActor,
    (state: State<RefreshMachineContext>) => state.context.elapsed,
  );

  return (
    <Button
      size="small"
      startIcon={startIcon}
      key="refresh"
      onClick={onRefresh}
      sx={{ whiteSpace: "nowrap", minWidth: "auto" }}
    >
      {refreshInterval === 1
        ? "Refreshing every second"
        : `Refresh in ${refreshInterval - elapsed}`}
    </Button>
  );
};

export const RefreshOptions = () => {
  const { queueMachineActor } = useContext(QueueMonitorContext);

  const [, send] = useActor(queueMachineActor!);

  const canRefresh = useSelector(queueMachineActor!, (state) =>
    state.matches("ready.refresher.timer"),
  );

  const timerActor =
    // @ts-ignore
    queueMachineActor?.children?.get("refreshMachine");

  const refreshInterval = useSelector(
    queueMachineActor!,
    (state) => state.context.refetchDuration,
  );

  const changeRefreshRate = (value: number) => {
    send({
      type: RefreshMachineEventTypes.UPDATE_DURATION,
      value,
    });
  };
  const handleRefresh = () =>
    send({
      type: RefreshMachineEventTypes.REFRESH,
    });

  const startIcon = useMemo(() => {
    return refreshInterval === 1 ? (
      <CircularProgress size={16} sx={{ color: "white" }} />
    ) : (
      <RefreshIcon />
    );
  }, [refreshInterval]);

  const refreshButton =
    canRefresh && timerActor ? (
      <RefreshButton
        onRefresh={handleRefresh}
        timerActor={timerActor}
        startIcon={startIcon}
      />
    ) : (
      <Button
        size="small"
        startIcon={startIcon}
        key="refresh"
        sx={{ whiteSpace: "nowrap", minWidth: "auto" }}
        onClick={() => handleRefresh()}
      >
        {refreshInterval === 1
          ? "Refreshing every second"
          : `Refresh in ${refreshInterval}`}
      </Button>
    );

  const radioGroup = (
    <RadioGroup row name="refresh-radio-group-options">
      {REFRESH_SECONDS_OPTIONS.map((op) => (
        <FormControlLabel
          value={op}
          control={
            <Radio
              onChange={() => changeRefreshRate(op)}
              checked={op === refreshInterval}
            />
          }
          label={op}
          key={op}
        />
      ))}
    </RadioGroup>
  );

  const label = (
    <MuiTypography variant="caption" fontWeight={"500"}>
      Refresh seconds
    </MuiTypography>
  );

  return (
    <Grid
      container
      sx={{
        width: "100%",
        alignItems: { xs: "flex-start", md: "center" },
        justifyContent: { xs: "flex-start", md: "flex-end" },
        flexDirection: { xs: "column", md: "row" },
        gap: { xs: 1, md: 4 },
      }}
    >
      <Grid sx={{ display: { xs: "block", md: "none" }, width: "100%" }}>
        {label}
      </Grid>

      <Grid sx={{ display: { xs: "none", md: "block" } }}>{label}</Grid>

      <Grid sx={{ display: { xs: "block", sm: "none" }, width: "100%" }}>
        {radioGroup}
      </Grid>

      <Grid sx={{ display: { xs: "block", sm: "none" }, width: "100%" }}>
        {refreshButton}
      </Grid>

      <Grid
        sx={{
          display: { xs: "none", sm: "flex", md: "none" },
          width: "100%",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        {radioGroup}
        {refreshButton}
      </Grid>

      <Grid sx={{ display: { xs: "none", md: "block" } }}>{radioGroup}</Grid>

      <Grid sx={{ display: { xs: "none", md: "block" } }}>{refreshButton}</Grid>
    </Grid>
  );
};
