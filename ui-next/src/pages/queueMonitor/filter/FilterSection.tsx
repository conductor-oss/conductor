import { Box, MenuItem, Select, SelectChangeEvent } from "@mui/material";
import Button from "components/ui/buttons/MuiButton";
import ConductorDateTimePicker from "components/ui/date-time/ConductorDateTimePicker";
import FilterIcon from "components/icons/FilterIcon";
import ResetIcon from "components/icons/ResetIcon";
import { FunctionComponent, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router";
import { greyBorder, greyText2 } from "theme/tokens/colors";
import { FORMAT_DATE_TIME_PICKER } from "utils/constants/common";
import { ActorRef } from "xstate";
import {
  FilterOption,
  QueueMonitorMachineEvents,
  RangeOptions,
} from "../state";
import { useFilterUpdate } from "./hook";
import { CompoundFilter } from "./CompoundFilter";

// A native <select>'s own popup menu can't be styled at all (no colors,
// radius, or spacing — it's rendered by the OS), so this uses MUI's Select
// instead: its own default arrow (a solid filled triangle) already reads
// unambiguously next to "<", and — unlike the app's ConductorSelect, which
// pairs a Select with a custom clear-button adornment that fights the
// arrow for the same hit area — a bare Select has no adornment to fight,
// so its click-anywhere-opens behavior just works.
const operatorMenuSx = {
  borderRadius: "4px",
  border: `1px solid ${greyBorder}`,
  boxShadow: "0 4px 12px rgba(0,0,0,0.1)",
};

const operatorSx = {
  height: "100%",
  borderRight: `1px solid ${greyBorder}`,
  "& .MuiSelect-select": {
    height: "100% !important",
    display: "flex",
    alignItems: "center",
    padding: "0 26px 0 10px !important",
    fontSize: 13,
    color: "text.primary",
  },
  "& .MuiSelect-icon": {
    color: greyText2,
    right: 4,
  },
};

interface OperatorSelectProps {
  value: RangeOptions;
  onChange: (event: SelectChangeEvent<RangeOptions>) => void;
  gt: string;
  lt: string;
  label: string;
  sx?: Record<string, unknown>;
}

const OperatorSelect: FunctionComponent<OperatorSelectProps> = ({
  value,
  onChange,
  gt,
  lt,
  label,
  sx,
}) => (
  <Select<RangeOptions>
    value={value}
    onChange={onChange}
    variant="standard"
    disableUnderline
    inputProps={{ "aria-label": `${label} condition` }}
    MenuProps={{ PaperProps: { sx: operatorMenuSx } }}
    sx={{ ...operatorSx, ...sx }}
  >
    <MenuItem value={RangeOptions.GT} sx={{ fontSize: 13 }}>
      {gt}
    </MenuItem>
    <MenuItem value={RangeOptions.LT} sx={{ fontSize: 13 }}>
      {lt}
    </MenuItem>
  </Select>
);

const valueSx = {
  flex: 1,
  minWidth: 0,
  height: "100%",
  border: "none",
  outline: "none",
  background: "transparent",
  fontSize: 13,
  fontWeight: 300,
  color: "text.primary",
  padding: "0 10px 0 4px",
  "&::placeholder": { color: greyText2, opacity: 1 },
  "&::-webkit-outer-spin-button, &::-webkit-inner-spin-button": {
    WebkitAppearance: "none",
    margin: 0,
  },
  MozAppearance: "textfield",
};

const dateValueSx = {
  flex: 1,
  minWidth: 0,
  "& .MuiOutlinedInput-root": {
    height: 32,
    border: "none !important",
    borderRadius: 0,
    backgroundColor: "transparent !important",
  },
  "& .MuiOutlinedInput-notchedOutline": { border: "none !important" },
  "& .MuiInputBase-input": {
    padding: "0 8px !important",
    fontSize: 13,
    fontWeight: 300,
  },
};

export interface FilterSectionProps {
  queueMachineActor: ActorRef<QueueMonitorMachineEvents>;
}

// The applied filter is undefined once the value is cleared, but the
// operator dropdown should keep showing whatever > / < (or After /
// Before) the user last picked — not snap back to the default.
const useStickyRangeOption = (applied?: RangeOptions) => {
  const [sticky, setSticky] = useState(applied ?? RangeOptions.GT);
  useEffect(() => {
    if (applied) {
      setSticky(applied);
    }
  }, [applied]);
  return [applied ?? sticky, setSticky] as const;
};

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

  const [queueOperator, setQueueOperator] = useStickyRangeOption(
    state?.queue?.option,
  );
  const [workerOperator, setWorkerOperator] = useStickyRangeOption(
    state?.worker?.option,
  );
  const [lastPollOperator, setLastPollOperator] = useStickyRangeOption(
    state?.lastPollTime?.option,
  );

  const handleNumberChange = (
    current: FilterOption | undefined,
    fallbackOption: RangeOptions,
    onChange: (payload?: FilterOption) => void,
    rawValue: string,
  ) => {
    if (rawValue === "") {
      onChange(undefined);
      return;
    }
    onChange({
      option: current?.option || fallbackOption,
      size: Number(rawValue),
    });
  };

  const handleOperatorChange = (
    current: FilterOption | undefined,
    setOperator: (option: RangeOptions) => void,
    onChange: (payload?: FilterOption) => void,
    option: RangeOptions,
  ) => {
    setOperator(option);
    if (current) {
      onChange({ option, size: current.size });
    }
  };

  const autofillLastPollTimeIfEmpty = () => {
    if (!state?.lastPollTime?.size) {
      handleUpdateLastPollFilter({
        option: lastPollOperator,
        size: Date.now(),
      });
    }
  };

  const handleReset = () => {
    setQueueOperator(RangeOptions.GT);
    setWorkerOperator(RangeOptions.GT);
    setLastPollOperator(RangeOptions.GT);
    clearAllFields();
  };

  return (
    // The sidebar can eat into this panel's actual rendered width well
    // before the viewport itself counts as narrow, so padding and the
    // field columns below both read the panel's own box (a container
    // query) rather than the viewport (a media query) to decide when to
    // tighten up. containerType has to live on this outer box, one level
    // up from the properties it's queried by, since a container can't
    // condition its own padding/layout on its own size.
    <Box sx={{ containerType: "inline-size" }}>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "1fr",
          gap: 2,
          // Below this, three columns leaves each field too narrow to
          // use — a Condition dropdown and Value input barely fit. Once
          // there's room, give the columns more breathing room too —
          // 8px reads as cramped once they're sitting side by side.
          "@container (min-width: 1040px)": {
            gridTemplateColumns: "repeat(3, 1fr)",
            columnGap: 6,
          },
        }}
      >
        <CompoundFilter
          label="Queue size"
          active={!!state?.queue}
          operator={
            <OperatorSelect
              label="Queue size"
              value={queueOperator}
              onChange={(e) =>
                handleOperatorChange(
                  state?.queue,
                  setQueueOperator,
                  handleUpdateQueue,
                  e.target.value as RangeOptions,
                )
              }
              gt=">"
              lt="<"
              sx={{ fontFamily: "monospace" }}
            />
          }
          value={
            <Box
              component="input"
              type="number"
              placeholder="tasks"
              value={state?.queue?.size || ""}
              onChange={(e) =>
                handleNumberChange(
                  state?.queue,
                  queueOperator,
                  handleUpdateQueue,
                  e.target.value,
                )
              }
              sx={valueSx}
            />
          }
        />
        <CompoundFilter
          label="Worker count"
          active={!!state?.worker}
          operator={
            <OperatorSelect
              label="Worker count"
              value={workerOperator}
              onChange={(e) =>
                handleOperatorChange(
                  state?.worker,
                  setWorkerOperator,
                  handleUpdateWorkerCount,
                  e.target.value as RangeOptions,
                )
              }
              gt=">"
              lt="<"
              sx={{ fontFamily: "monospace" }}
            />
          }
          value={
            <Box
              component="input"
              type="number"
              placeholder="count"
              value={state?.worker?.size || ""}
              onChange={(e) =>
                handleNumberChange(
                  state?.worker,
                  workerOperator,
                  handleUpdateWorkerCount,
                  e.target.value,
                )
              }
              sx={valueSx}
            />
          }
        />
        <CompoundFilter
          label="Last poll time"
          active={!!state?.lastPollTime}
          operator={
            <OperatorSelect
              label="Last poll time"
              value={lastPollOperator}
              onChange={(e) => {
                const option = e.target.value as RangeOptions;
                setLastPollOperator(option);
                handleUpdateLastPollFilter({
                  option,
                  size: state?.lastPollTime?.size || Date.now(),
                });
              }}
              gt="After"
              lt="Before"
            />
          }
          value={
            <ConductorDateTimePicker
              format={FORMAT_DATE_TIME_PICKER}
              inputProps={{
                fullWidth: true,
                onFocus: autofillLastPollTimeIfEmpty,
              }}
              onOpen={autofillLastPollTimeIfEmpty}
              value={
                state?.lastPollTime?.size
                  ? new Date(Number(state.lastPollTime.size))
                  : null
              }
              onChange={(value) => {
                if (value) {
                  handleUpdateLastPollFilter({
                    option: state?.lastPollTime?.option || lastPollOperator,
                    size: value.valueOf(),
                  });
                } else {
                  handleUpdateLastPollFilter(undefined);
                }
              }}
              sx={dateValueSx}
            />
          }
        />
      </Box>

      <Box
        display="flex"
        gap={2}
        justifyContent="flex-end"
        alignItems="center"
        py={4}
      >
        <Button
          to={window.location.pathname}
          component={RouterLink}
          disabled={!appliedFilterPath.includes("?")}
          size="small"
          startIcon={<ResetIcon />}
          variant="text"
          onClick={handleReset}
        >
          Reset
        </Button>
        <Button
          to={appliedFilterPath}
          disabled={isDisabled}
          size="small"
          component={RouterLink}
          startIcon={<FilterIcon />}
        >
          Apply filter
        </Button>
      </Box>
    </Box>
  );
};
