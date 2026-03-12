import { Grid, SxProps } from "@mui/material";
import ConductorDateTimePicker from "components/v1/date-time/ConductorDateTimePicker";
import { ConductorTooltipProps } from "components/conductorTooltip/ConductorTooltip";
import { ReactNode } from "react";

export interface ConductorDateRangePickerProps {
  disabled?: boolean;
  error?: boolean;
  from: Date | null;
  helperTextFrom?: ReactNode;
  helperTextTo?: ReactNode;
  inputSx?: SxProps;
  labelFrom?: string;
  labelTo?: string;
  onFromChange: (val: any) => void;
  onToChange: (val: any) => void;
  sx?: SxProps;
  to: Date | null;
  tooltipTo?: Omit<ConductorTooltipProps, "children">;
  tooltipFrom?: Omit<ConductorTooltipProps, "children">;
}

const ConductorDateRangePicker = ({
  disabled,
  error,
  from,
  helperTextFrom,
  helperTextTo,
  inputSx,
  labelFrom,
  labelTo,
  onFromChange,
  onToChange,
  sx,
  to,
  tooltipTo,
  tooltipFrom,
}: ConductorDateRangePickerProps) => {
  return (
    <Grid container spacing={2} sx={sx}>
      <Grid
        size={{
          xs: 12,
          sm: 6,
        }}
      >
        <ConductorDateTimePicker
          ampmInClock
          disabled={disabled}
          label={labelFrom}
          onChange={onFromChange}
          sx={inputSx}
          value={from}
          maxDate={to}
          inputProps={{
            fullWidth: true,
            error,
            label: labelFrom,
            helperText: helperTextFrom,
            tooltip: tooltipFrom,
          }}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 6,
        }}
      >
        <ConductorDateTimePicker
          ampmInClock
          disabled={disabled}
          label={labelTo}
          onChange={onToChange}
          sx={inputSx}
          value={to}
          minDate={from}
          inputProps={{
            fullWidth: true,
            error,
            label: labelTo,
            helperText: helperTextTo,
            tooltip: tooltipTo,
          }}
        />
      </Grid>
    </Grid>
  );
};

export default ConductorDateRangePicker;
