import { Grid, MenuItem, Paper, useMediaQuery } from "@mui/material";
import Button from "components/ui/buttons/MuiButton";
import MuiTypography from "components/ui/MuiTypography";
import ConductorInput from "components/ui/inputs/ConductorInput";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import ConductorDateTimePicker from "components/ui/date-time/ConductorDateTimePicker";
import FilterIcon from "components/icons/FilterIcon";
import ResetIcon from "components/icons/ResetIcon";
import _isEmpty from "lodash/isEmpty";
import { ChangeEvent, FunctionComponent, ReactNode } from "react";
import { Link as RouterLink } from "react-router";
import { dateRangePickerStyle } from "shared/styles";
import { ActorRef } from "xstate";
import {
  FilterOption,
  QueueMonitorMachineEvents,
  RangeOptions,
} from "../state";
import { useFilterUpdate } from "./hook";

interface OptionSelectorProps {
  onChange: (payload?: FilterOption) => void;
  value?: FilterOption;
  label: string;
}

export const OptionSelector: FunctionComponent<OptionSelectorProps> = ({
  onChange,
  value,
  label,
}) => {
  const handleSelectChange = (event: ChangeEvent<HTMLInputElement>) => {
    const maybeSelectedOption = event.target.value as RangeOptions;

    onChange(
      _isEmpty(maybeSelectedOption)
        ? undefined
        : { size: value?.size || 0, option: maybeSelectedOption },
    );
  };

  return (
    <ConductorSelect
      value={value?.option || ""}
      label={label}
      onChange={handleSelectChange}
      size="small"
      fullWidth
      id="the-select"
    >
      <MenuItem value={RangeOptions.GT}>Greater than</MenuItem>
      <MenuItem value={RangeOptions.LT}>Lower than</MenuItem>
      <MenuItem value={""}>Empty</MenuItem>
    </ConductorSelect>
  );
};

interface FilterContainerProps {
  label: string;
  selector: ReactNode;
  valueField: ReactNode;
}

const FieldContainer: FunctionComponent<FilterContainerProps> = ({
  label,
  selector,
  valueField,
}) => (
  <Grid container direction={"row"} spacing={2} alignItems="center">
    <Grid size={{ xs: 2.4 }} alignSelf="center">
      <MuiTypography align={"center"} component={"strong"} fontWeight={600}>
        {label}
      </MuiTypography>
    </Grid>
    <Grid size={{ xs: 4.8 }}>{selector}</Grid>
    <Grid size={{ xs: 4.8 }}>{valueField}</Grid>
  </Grid>
);

export interface FilterSectionProps {
  queueMachineActor: ActorRef<QueueMonitorMachineEvents>;
}

export const FilterSection: FunctionComponent<FilterSectionProps> = ({
  queueMachineActor,
}) => {
  const [
    state,
    {
      handleUpdateQueue,
      handleUpdateWorkerCount,
      handleUpdateLastPollFilter,
      clearAllFields,
    },
    isDisabled,
    appliedFilterPath,
  ] = useFilterUpdate(queueMachineActor);

  const mediumScreen = useMediaQuery("(max-width:1200px)");

  return (
    <Paper variant={!mediumScreen ? "outlined" : "elevation"}>
      <Grid container spacing={2} p={4} alignItems="center" gap={2}>
        {/* First row: Queue size and Worker count */}
        <Grid container size={{ xs: 12 }} spacing={6}>
          <Grid size={{ xs: 12, md: 6 }}>
            <FieldContainer
              label="Queue size"
              selector={
                <OptionSelector
                  value={state?.queue}
                  label="Condition"
                  onChange={handleUpdateQueue}
                />
              }
              valueField={
                <ConductorInput
                  fullWidth
                  label="Value"
                  value={state?.queue?.size || ""}
                  disabled={state?.queue?.option === undefined}
                  onTextInputChange={(value) =>
                    handleUpdateQueue({
                      option: state?.queue?.option || RangeOptions.LT,
                      size: value,
                    })
                  }
                  type="number"
                />
              }
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <FieldContainer
              label="Worker count"
              selector={
                <OptionSelector
                  value={state?.worker}
                  label="Condition"
                  onChange={handleUpdateWorkerCount}
                />
              }
              valueField={
                <ConductorInput
                  fullWidth
                  label="Value"
                  value={state?.worker?.size || ""}
                  disabled={state?.worker?.option === undefined}
                  onTextInputChange={(value) =>
                    handleUpdateWorkerCount({
                      option: state?.worker?.option || RangeOptions.LT,
                      size: value,
                    })
                  }
                  type="number"
                />
              }
            />
          </Grid>
        </Grid>

        {/* Second row: Last poll time with Reset and Apply filter buttons */}
        <Grid container size={{ xs: 12 }} spacing={6} alignItems="center">
          <Grid size={{ xs: 12, md: 6 }}>
            <FieldContainer
              label="Last poll time"
              selector={
                <OptionSelector
                  value={state?.lastPollTime}
                  label="Condition"
                  onChange={handleUpdateLastPollFilter}
                />
              }
              valueField={
                <ConductorDateTimePicker
                  format={"yyyy-MM-dd HH:mm A"}
                  label="Date time"
                  value={
                    state?.lastPollTime?.size
                      ? new Date(Number(state.lastPollTime.size))
                      : new Date()
                  }
                  disabled={state?.lastPollTime?.option === undefined}
                  onChange={(value) => {
                    if (value) {
                      handleUpdateLastPollFilter({
                        option: state?.lastPollTime?.option || RangeOptions.LT,
                        size: value.valueOf(),
                      });
                    }
                  }}
                  sx={dateRangePickerStyle.input}
                />
              }
            />
          </Grid>
          <Grid
            size={{ xs: 12, md: 6 }}
            display="flex"
            gap={2}
            justifyContent="right"
            alignItems="center"
          >
            <Button
              to={window.location.pathname}
              component={RouterLink}
              disabled={!appliedFilterPath.includes("?")}
              size={!mediumScreen ? "small" : "medium"}
              startIcon={<ResetIcon />}
              variant="text"
              onClick={clearAllFields}
            >
              Reset
            </Button>
            <Button
              to={appliedFilterPath}
              disabled={isDisabled}
              size={!mediumScreen ? "small" : "medium"}
              component={RouterLink}
              startIcon={<FilterIcon />}
            >
              Apply filter
            </Button>
          </Grid>
        </Grid>
      </Grid>
    </Paper>
  );
};
