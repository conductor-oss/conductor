import { Box, SxProps, TextFieldProps } from "@mui/material";
import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { TimePicker } from "@mui/x-date-pickers/TimePicker";
import ConductorInput from "components/v1/ConductorInput";
import React from "react";

export interface ConductorTimePickerProps {
  id?: string;
  timeValue: string;
  label: string;
  sx?: SxProps;
  updateTime: (data: string) => void;
  error?: string;
}

export const ConductorTimePicker = ({
  id = "timepicker-time",
  label,
  timeValue,
  sx,
  updateTime,
  error,
  ...restProps
}: ConductorTimePickerProps) => {
  const time = timeValue ? new Date(Number(timeValue)) : new Date();

  const handleTimeChange = (newValue: Date | null) => {
    if (newValue) updateTime(String(newValue?.valueOf()));
  };

  return (
    <Box id={id}>
      <LocalizationProvider dateAdapter={AdapterDateFns}>
        <TimePicker
          {...restProps}
          label={label}
          value={time}
          onChange={(newValue) => handleTimeChange(newValue)}
          views={["hours", "minutes", "seconds"]}
          slots={{
            textField: ConductorInput as React.ComponentType<TextFieldProps>,
          }}
          sx={{
            ...sx,
            "& .MuiInputAdornment-root": {
              display: "none",
            },
            "& fieldset": {
              borderColor: error ? "#d6413a !important" : "#AFAFAF",
            },
            "& label": {
              color: error ? "#d6413a" : "#494949",
            },
          }}
        />
      </LocalizationProvider>
    </Box>
  );
};
