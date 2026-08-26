import {
  Box,
  ButtonGroup,
  Button as MuiRawButton,
  CircularProgress,
  Grid,
} from "@mui/material";
import { ArrowClockwise as RefreshIcon } from "@phosphor-icons/react";
import { useActor, useSelector } from "@xstate/react";
import Button from "components/ui/buttons/MuiButton";
import MuiTypography from "components/ui/MuiTypography";
import { FunctionComponent, ReactNode, useContext, useMemo } from "react";
import {
  blueLightMode,
  greyBorder,
  greyText,
  lightGrey,
} from "theme/tokens/colors";
import { ActorRef, State } from "xstate";
import { QueueMonitorContext } from "../state";
import {
  RefreshMachineContext,
  RefreshMachineEventTypes,
  TimerEvents,
} from "./state";

const REFRESH_SECONDS_OPTIONS = [1, 10, 30, 60];

// Matches the compound-filter segments in FilterSection: one bordered,
// button-per-value picker instead of the previous radio row + separate
// "Refresh in N" button competing for attention. No leading label chip
// here (unlike the filter fields) — "Refresh seconds" already labels the
// whole control externally, and a chip reading "Auto" inside a row of
// otherwise-identical buttons reads as a 5th, non-functional option
// rather than a category label.
const segmentedShellSx = {
  // inline-flex, not flex: on mobile this sits inside a width:100% Grid
  // item, and a block-level flex container stretches to fill that — which
  // left a large empty bordered strip trailing the actual 1s/10s/30s/60s
  // buttons. inline-flex hugs its own content at every width instead.
  display: "inline-flex",
  alignItems: "stretch",
  height: 32,
  border: `1px solid ${greyBorder}`,
  borderRadius: "4px",
  overflow: "hidden",
  flexShrink: 0,
};

// The app's own MuiButton theme defaults every <Button> to size="medium"
// (36px) and a "contained" variant with its own color/shadow language
// (theme/material/components/buttons.ts). Overriding height without also
// pinning the color to the same literal token that theme uses elsewhere
// (rather than the generic `primary.main` palette path) is what read as
// "default MUI" — the hex is identical, but nothing else about the
// interaction matched the rest of the app's buttons.
const intervalButtonSx = (selected: boolean) => ({
  minWidth: 38,
  height: 32,
  minHeight: 32,
  padding: "0 10px",
  fontSize: 12,
  fontWeight: 400,
  textTransform: "none",
  borderRadius: 0,
  border: "none",
  borderRight: `1px solid ${greyBorder}`,
  backgroundColor: selected ? blueLightMode : "#fff",
  color: selected ? "#fff" : greyText,
  "&:hover": {
    backgroundColor: selected ? blueLightMode : lightGrey,
    filter: selected ? "brightness(0.92)" : "none",
  },
  "&:last-of-type": { borderRight: "none" },
});

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
      // Fixed instead of auto: the label alternates between "Every second",
      // "Refresh in 60", and "Refresh in 3" as the countdown ticks, and
      // letting the button size to its content made the whole row (and the
      // interval buttons next to it) shift every second. 136px comfortably
      // fits the longest of those labels plus the icon.
      sx={{ whiteSpace: "nowrap", minWidth: 136 }}
    >
      {refreshInterval === 1
        ? "Every second"
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
        // Fixed instead of auto: the label alternates between "Every second",
        // "Refresh in 60", and "Refresh in 3" as the countdown ticks, and
        // letting the button size to its content made the whole row (and the
        // interval buttons next to it) shift every second. 136px comfortably
        // fits the longest of those labels plus the icon.
        sx={{ whiteSpace: "nowrap", minWidth: 136 }}
        onClick={() => handleRefresh()}
      >
        {refreshInterval === 1
          ? "Every second"
          : `Refresh in ${refreshInterval}`}
      </Button>
    );

  const intervalControl = (
    <Box sx={segmentedShellSx}>
      <ButtonGroup
        variant="text"
        disableElevation
        aria-label="refresh interval"
      >
        {REFRESH_SECONDS_OPTIONS.map((op) => (
          <MuiRawButton
            key={op}
            onClick={() => changeRefreshRate(op)}
            sx={intervalButtonSx(op === refreshInterval)}
          >
            {op}s
          </MuiRawButton>
        ))}
      </ButtonGroup>
    </Box>
  );

  const label = (
    <MuiTypography variant="caption" fontWeight={"500"}>
      Refresh Interval
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

      <Grid
        sx={{
          display: { xs: "flex", sm: "none" },
          width: "100%",
          flexWrap: "wrap",
          alignItems: "center",
          justifyContent: "space-between",
          rowGap: 1,
        }}
      >
        {intervalControl}
        {refreshButton}
      </Grid>

      <Grid
        sx={{
          display: { xs: "none", sm: "flex", md: "none" },
          width: "100%",
          alignItems: "center",
          gap: 2,
        }}
      >
        {intervalControl}
        {refreshButton}
      </Grid>

      <Grid sx={{ display: { xs: "none", md: "block" } }}>
        {intervalControl}
      </Grid>

      <Grid sx={{ display: { xs: "none", md: "block" } }}>{refreshButton}</Grid>
    </Grid>
  );
};
