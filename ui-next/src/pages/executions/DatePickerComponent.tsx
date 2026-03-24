import {
  Box,
  Grid,
  MenuItem,
  Tabs,
  Tab,
  Switch,
  IconButton,
} from "@mui/material";
import MuiButton from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import CheckCircleOutlineOutlinedIcon from "@mui/icons-material/CheckCircleOutlineOutlined";
import ConductorSelect from "components/v1/ConductorSelect";
import { COUNT_OPTIONS, TIME_OPTIONS } from "utils/constants/dateTimePicker";
import { ConductorAutoComplete } from "components/v1";
import { useState } from "react";
import { ConductorTimePicker } from "components/v1/date-time/ConductorTimePicker";
import { SingleDateRangePicker } from "components/v1/date-time/ConductorSingleDateRangePicker";
import { getCombineDateTime, getDateTime, getSearchDateTime } from "utils/date";
import _isEmpty from "lodash/isEmpty";
import XCloseIcon from "components/v1/icons/XCloseIcon";

import { COMMONLY_USED } from "utils/constants/dateTimePicker";

const blueTextStyle = {
  fontWeight: "500",
  color: "#1976D2",
  fontSize: "13px",
};

const headerStyle = {
  fontSize: "12px",
  fontWeight: 500,
  lineHeight: "16px",
  padding: "10px 0 5px",
};

const selectStyle = {
  "& .MuiInputBase-root": {
    fontSize: "12px",
  },
};

const inputStyle = {
  "& .MuiInputBase-root": {
    fontSize: "12px",
  },
};

const tabStyles = {
  "& .MuiTabs-flexContainer": {
    justifyContent: "space-between",
    borderBottom: "1px solid #DAD9D9",
  },
  "& .MuiTab-root": {
    color: "#a8a3a3",
    fontWeight: 500,
    fontSize: "16px",
    width: "25%",
  },
  "& .MuiTabs-scroller": {
    padding: "0 10px",
  },
};

const closeIconStyle = {
  position: "absolute",
  right: "5px",
  top: "5px",
  cursor: "pointer",
};

export interface DatePickerProps {
  startDateTime: string;
  endDateTime: string;
  label: string;
  handleFrom: (data: string) => void;
  handleTo: (data: string) => void;
  openPicker: (val: boolean) => void;
  setDisplayName: (val: string) => void;
  maxDate: boolean;
  handleCommonDate: (time: string) => void;
}

export const DatePickerComponent = ({
  label,
  startDateTime,
  endDateTime,
  handleFrom,
  handleTo,
  openPicker,
  setDisplayName,
  maxDate,
  handleCommonDate,
}: DatePickerProps) => {
  const [selectedTab, setSelectedTab] = useState(0);
  const [roundToMinute, setRoundToMinute] = useState(true);
  const [startDate, setStartDate] = useState(startDateTime);
  const [endDate, setEndDate] = useState(endDateTime);
  const [startTime, setStartTime] = useState(startDateTime);
  const [endTime, setEndTime] = useState(endDateTime);
  const [count, setCount] = useState("72");
  const [timeUnit, setTimeUnit] = useState("hours");
  const [error, setError] = useState({ start: "", end: "" });

  const handleDateTime = () => {
    const updatedStartDateTime = getCombineDateTime(startDate, startTime);
    const updatedEndDateTime = getCombineDateTime(endDate, endTime);
    if (updatedEndDateTime < updatedStartDateTime) {
      setError({
        start: "",
        end: "Start time cannot be greater than the end time.",
      });
    } else {
      handleFrom(updatedStartDateTime);
      handleTo(updatedEndDateTime);
      setDisplayName(
        getSearchDateTime(updatedStartDateTime, updatedEndDateTime),
      );
      openPicker(false);
      setError({ start: "", end: "" });
    }
  };

  const handleRelativeTime = () => {
    const rangeStartDate = new Date(
      getDateTime("last", count, timeUnit, roundToMinute),
    );
    handleFrom(rangeStartDate.getTime().toString());
    handleTo("");
    setDisplayName(getSearchDateTime(rangeStartDate.getTime().toString(), ""));
    openPicker(false);
  };

  const setStartDateAndTime = (value: string) => {
    setStartDate(value);
    setStartTime(value);
  };

  const setEndDateAndTime = (value: string) => {
    setEndDate(value);
    setEndTime(value);
  };

  return (
    <Box>
      <Tabs
        value={selectedTab}
        onChange={(_event: any, val) => setSelectedTab(val)}
        sx={tabStyles}
      >
        <Tab label="Presets" />
        <Tab label="Absolute" id="date-picker-absolute-tab" />
        <Tab label="Relative" />
      </Tabs>
      <Box sx={{ display: "flex" }}>
        <IconButton onClick={() => openPicker(false)} sx={closeIconStyle}>
          <XCloseIcon size="20px" />
        </IconButton>
      </Box>
      {selectedTab === 0 && (
        <Box sx={{ padding: 4 }}>
          <Grid container spacing={2} sx={{ width: "100%" }}>
            {Object.entries(COMMONLY_USED)?.map(([key, val]) => (
              <Grid key={key} size={6}>
                <MuiTypography
                  onClick={() => {
                    handleCommonDate(key);
                    openPicker(false);
                  }}
                  sx={{ ...blueTextStyle, cursor: "pointer" }}
                >
                  {val.name}
                </MuiTypography>
              </Grid>
            ))}
          </Grid>
        </Box>
      )}
      {selectedTab === 1 && (
        <Grid
          container
          spacing={3}
          sx={{ width: "100%", padding: "10px 10px 10px 0" }}
        >
          <Grid
            size={{
              md: 8,
            }}
          >
            <SingleDateRangePicker
              fromDate={startDate}
              toDate={endDate}
              setStartTime={setStartDateAndTime}
              setEndTime={setEndDateAndTime}
              maxDate={maxDate}
            />
          </Grid>
          <Grid
            size={{
              md: 4,
            }}
          >
            <Box
              sx={{ position: "relative", height: "100%", padding: "15px 0 0" }}
              justifyContent="flex-end"
            >
              <ConductorTimePicker
                id="timepicker-time-from"
                timeValue={startTime}
                label={`${label} time - From`}
                updateTime={setStartTime}
                error={error.start}
              />
              {error.start && (
                <Box>
                  <MuiTypography sx={{ color: "#E50914", fontSize: "11px" }}>
                    {error.start}
                  </MuiTypography>
                </Box>
              )}
              <ConductorTimePicker
                id="timepicker-time-to"
                timeValue={endTime}
                label={`${label} time - To`}
                sx={{ marginTop: "10px" }}
                updateTime={setEndTime}
                error={error.end}
              />
              {error.end && (
                <Box>
                  <MuiTypography sx={{ color: "#E50914", fontSize: "11px" }}>
                    {error.end}
                  </MuiTypography>
                </Box>
              )}
              <MuiButton
                id="react-datepicker-apply-button"
                startIcon={<CheckCircleOutlineOutlinedIcon />}
                color="primary"
                sx={{ position: "absolute", bottom: 0, right: 0 }}
                onClick={handleDateTime}
                disabled={_isEmpty(endDate)}
              >
                Apply
              </MuiButton>
            </Box>
          </Grid>
        </Grid>
      )}
      {selectedTab === 2 && (
        <Box sx={{ padding: "20px 10px" }}>
          <Grid container spacing={2} sx={{ width: "100%" }}>
            <Grid
              size={{
                md: 4,
              }}
            >
              <ConductorAutoComplete
                label="Count"
                fullWidth
                options={COUNT_OPTIONS}
                onChange={(__, value) => setCount(value)}
                onInputChange={(__, value) => setCount(value)}
                value={count}
                freeSolo
                disableClearable
                sx={inputStyle}
              />
            </Grid>
            <Grid
              size={{
                md: 8,
              }}
            >
              <ConductorSelect
                label="Time"
                name="time"
                value={timeUnit}
                fullWidth
                onChange={(event: any) => setTimeUnit(event.target.value)}
                sx={selectStyle}
              >
                {Object.entries(TIME_OPTIONS).map(([key, val]) => (
                  <MenuItem key={key} value={key}>
                    {val}
                  </MenuItem>
                ))}
              </ConductorSelect>
            </Grid>
          </Grid>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "end",
              padding: "10px 15px",
              "& .MuiSwitch-root": {
                margin: "0 10px 0 0",
              },
            }}
          >
            <Switch
              sx={{ marginLeft: 0, marginTop: "12px" }}
              color="primary"
              checked={roundToMinute}
              onChange={() => setRoundToMinute(!roundToMinute)}
            />
            <MuiTypography sx={headerStyle}>
              Round to nearest minute
            </MuiTypography>
          </Box>

          {(startDateTime || endDateTime) && (
            <Box>
              <MuiTypography sx={headerStyle}>{`${label} time`}</MuiTypography>
              <MuiTypography sx={{ fontSize: "12px" }}>
                {getSearchDateTime(startDateTime, endDateTime)}
              </MuiTypography>
            </Box>
          )}
          <Box
            sx={{ display: "flex", padding: "15px 0 0" }}
            justifyContent="flex-end"
          >
            <MuiButton
              startIcon={<CheckCircleOutlineOutlinedIcon />}
              color="primary"
              onClick={handleRelativeTime}
            >
              Apply
            </MuiButton>
          </Box>
        </Box>
      )}
    </Box>
  );
};
