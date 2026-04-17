import { Box, Grid } from "@mui/material";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { dateRangePickerStyle } from "shared/styles";
import { convertToDateObject, DateAdapter, formatDate } from "utils/date";

interface DateRangePickerProps {
  onFromChange: (val: any) => void;
  onToChange: (val: any) => void;
  from: Date | string;
  to: Date | string;
  label: string;
  labelFrom?: string;
  labelTo?: string;
  disabled: boolean;
}

export default function DateRangePicker({
  onFromChange,
  from,
  onToChange,
  to,
  label,
  labelFrom,
  labelTo,
  disabled,
}: DateRangePickerProps) {
  const actualLabelFrom =
    labelFrom == null ? label && `${label} - from` : labelFrom;
  const actualLabelTo = labelTo == null ? label && `${label} - to` : labelTo;

  return (
    <Box sx={dateRangePickerStyle.wrapper}>
      <LocalizationProvider dateAdapter={DateAdapter}>
        <Grid container spacing={3} sx={{ width: "100%" }}>
          <Grid
            size={{
              xs: 12,
              sm: 6,
            }}
          >
            <DateTimePicker
              disabled={disabled}
              label={actualLabelFrom}
              format={"YYYY-MM-DD hh:mm A"}
              value={convertToDateObject(from)}
              ampmInClock
              onChange={(value) => {
                onFromChange(formatDate(value, "yyyy-MM-dd'T'HH:mm:ss"));
              }}
              sx={dateRangePickerStyle.input}
              slotProps={{
                actionBar: {
                  actions: ["clear", "accept"],
                },
              }}
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              sm: 6,
            }}
          >
            <DateTimePicker
              label={actualLabelTo}
              disabled={disabled}
              value={convertToDateObject(to)}
              format={"YYYY-MM-DD hh:mm A"}
              onChange={(value) => {
                onToChange(formatDate(value, "yyyy-MM-dd'T'HH:mm:ss"));
              }}
              sx={dateRangePickerStyle.input}
              slotProps={{
                actionBar: {
                  actions: ["clear", "accept"],
                },
              }}
            />
          </Grid>
        </Grid>
      </LocalizationProvider>
    </Box>
  );
}
