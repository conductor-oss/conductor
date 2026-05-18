import {
  Box,
  ClickAwayListener,
  IconButton,
  Tooltip,
  TooltipProps,
  Typography,
  styled,
} from "@mui/material";
import { DatePickerComponent } from "./DatePickerComponent";
import MuiTypography from "components/ui/MuiTypography";
import CloseOutlinedIcon from "@mui/icons-material/CloseOutlined";

import { commonlyUsedDateTime, getSearchDateTime } from "utils/date";

import { featureFlags, FEATURES } from "utils/flags";
import { forwardRef, useImperativeHandle, useState } from "react";

const textStyle = {
  fontWeight: "500",
  color: "#060606",
  fontSize: "13px",
};

const timeTextStyle = {
  fontWeight: "500",
  color: "#1976D2",
  fontSize: "13px",
  paddingLeft: "5px",
  paddingRight: "5px",
  cursor: "pointer",
};

const CustomisedTooltip = styled(({ className, ...props }: TooltipProps) => (
  <Tooltip
    arrow
    placement="bottom-start"
    disableFocusListener
    disableHoverListener
    disableTouchListener
    {...props}
    classes={{ popper: className }}
  />
))(() => ({
  "& .MuiTooltip-tooltip": {
    backgroundColor: "white",
    color: "rgba(6, 6, 6, 1)",
    width: "100%",
    filter: "drop-shadow(0px 0px 6px rgba(89, 89, 89, 0.41))",
    borderRadius: "6px",
    padding: "15px 10px 10px 15px",
    border: "1px solid #0D94DB",
  },
  "& .MuiTooltip-arrow": {
    color: "white",
    fontSize: "28px",
    "&:before": {
      border: "1px solid #0D94DB",
    },
  },
}));

export interface DateControlComponentProps {
  startTime: string;
  onStartFromChange: (val: string) => void;
  startTimeEnd: string;
  onStartToChange: (val: string) => void;
  endTimeStart: string;
  onEndFromChange: (val: string) => void;
  endTime: string;
  onEndToChange: (val: string) => void;
  fromDisplayTime: string;
  setFromDisplayTime: (val: string) => void;
  toDisplayTime: string;
  setToDisplayTime: (val: string) => void;
  disabled?: boolean;
  recentSearches: { start: string; end: string };
  startTimeLabel?: string;
  endTimeLabel?: string;
  tooltipZIndex?: number;
  startDialogTitle?: string | null;
  startDialogHelpText?: string | null;
  endDialogTitle?: string | null;
  endDialogHelpText?: string | null;
}

/** Imperative handle exposed via ref so a parent ButtonBase can own all toggle/close
 *  logic without holding any open/close state internally. */
export interface DateControlHandle {
  toggle: () => void;
  close: () => void;
}

export const DateControlComponent = forwardRef<
  DateControlHandle,
  DateControlComponentProps
>(function DateControlComponent(
  {
    startTime,
    onStartFromChange,
    startTimeEnd,
    onStartToChange,
    endTimeStart,
    onEndFromChange,
    endTime,
    onEndToChange,
    fromDisplayTime,
    setFromDisplayTime,
    toDisplayTime,
    setToDisplayTime,
    startTimeLabel = "Start Time",
    endTimeLabel = "End Time",
    tooltipZIndex = 1200,
    startDialogTitle = null,
    startDialogHelpText = null,
    endDialogTitle = null,
    endDialogHelpText = null,
  },
  ref,
) {
  const [openStartDatePicker, setOpenStartDatePicker] = useState(false);
  const [openEndDatePicker, setOpenEndDatePicker] = useState(false);

  useImperativeHandle(ref, () => ({
    toggle: () => setOpenStartDatePicker((prev) => !prev),
    close: () => setOpenStartDatePicker(false),
  }));

  const handleCommonStartDate = (time: string) => {
    const { rangeStart, rangeEnd } = commonlyUsedDateTime(time);
    setFromDisplayTime(getSearchDateTime(rangeStart, rangeEnd));
    onStartFromChange(rangeStart);
    onStartToChange(rangeEnd);
  };

  const handleCommonEndDate = (time: string) => {
    const { rangeStart, rangeEnd } = commonlyUsedDateTime(time);
    setToDisplayTime(getSearchDateTime(rangeStart, rangeEnd));
    onEndFromChange(rangeStart);
    onEndToChange(rangeEnd);
  };

  const showEndDatePicker = featureFlags.isEnabled(
    FEATURES.SHOW_END_TIME_IN_DATEPICKER,
  );

  return (
    <Box
      sx={{
        display: "flex",
        height: "100%",
        alignItems: "center",
        justifyContent: "start",
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center" }}>
        {/* ── Start date picker ── */}
        <ClickAwayListener
          mouseEvent="onMouseDown"
          onClickAway={() =>
            openStartDatePicker && setOpenStartDatePicker(false)
          }
        >
          <Box>
            <CustomisedTooltip
              open={openStartDatePicker}
              slotProps={{
                popper: {
                  modifiers: [
                    { name: "offset", options: { offset: [-90, 10] } },
                  ],
                  style: { zIndex: tooltipZIndex },
                },
              }}
              sx={{ "& .MuiTooltip-tooltip": { minWidth: "500px" } }}
              title={
                <Box
                  onMouseDown={(e) => e.stopPropagation()}
                  onClick={(e) => e.stopPropagation()}
                >
                  {startDialogTitle && startDialogHelpText ? (
                    <Box sx={{ mx: 2, py: 2, mb: 2 }}>
                      <Typography variant="h6" sx={{ pb: 1, fontSize: "11pt" }}>
                        {startDialogTitle}
                      </Typography>
                      <Typography>{startDialogHelpText}</Typography>
                    </Box>
                  ) : null}
                  <DatePickerComponent
                    startDateTime={startTime}
                    endDateTime={startTimeEnd}
                    label="Start"
                    handleFrom={onStartFromChange}
                    handleTo={onStartToChange}
                    openPicker={setOpenStartDatePicker}
                    setDisplayName={setFromDisplayTime}
                    maxDate={true}
                    handleCommonDate={handleCommonStartDate}
                  />
                </Box>
              }
            >
              <Box sx={{ display: "flex", alignItems: "center" }}>
                <Box
                  id="date-picker-start-time"
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    cursor: "pointer",
                  }}
                  onClick={() => setOpenStartDatePicker((prev) => !prev)}
                >
                  <MuiTypography sx={{ ...textStyle }}>
                    {startTimeLabel}:
                  </MuiTypography>
                  <MuiTypography
                    sx={{
                      ...timeTextStyle,
                      background: openStartDatePicker ? "#E3F2FD" : "none",
                    }}
                  >
                    {fromDisplayTime}
                  </MuiTypography>
                </Box>
                {startTime || startTimeEnd ? (
                  <IconButton
                    size="small"
                    color="primary"
                    disableRipple
                    sx={{ padding: 0, height: "fit-content", minHeight: 0 }}
                    onClick={(e) => {
                      e.stopPropagation();
                      onStartFromChange("");
                      onStartToChange("");
                      setFromDisplayTime("Select time range");
                    }}
                  >
                    <CloseOutlinedIcon
                      color="primary"
                      sx={{ fontSize: "12pt" }}
                    />
                  </IconButton>
                ) : null}
              </Box>
            </CustomisedTooltip>
          </Box>
        </ClickAwayListener>

        {/* ── End date picker (feature-flagged) ── */}
        {showEndDatePicker ? (
          <ClickAwayListener
            mouseEvent="onMouseDown"
            onClickAway={() => openEndDatePicker && setOpenEndDatePicker(false)}
          >
            <Box>
              <CustomisedTooltip
                open={openEndDatePicker}
                slotProps={{
                  popper: {
                    modifiers: [
                      { name: "offset", options: { offset: [-90, 10] } },
                    ],
                    style: { zIndex: tooltipZIndex },
                  },
                }}
                sx={{ "& .MuiTooltip-tooltip": { minWidth: "500px" } }}
                title={
                  <Box
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => e.stopPropagation()}
                  >
                    {endDialogTitle && endDialogHelpText ? (
                      <Box sx={{ mx: 2, py: 2, mb: 2 }}>
                        <Typography
                          variant="h6"
                          sx={{ pb: 1, fontSize: "11pt" }}
                        >
                          {endDialogTitle}
                        </Typography>
                        <Typography>{endDialogHelpText}</Typography>
                      </Box>
                    ) : null}
                    <DatePickerComponent
                      startDateTime={endTimeStart}
                      endDateTime={endTime}
                      label="End"
                      handleFrom={onEndFromChange}
                      handleTo={onEndToChange}
                      openPicker={setOpenEndDatePicker}
                      setDisplayName={setToDisplayTime}
                      maxDate={false}
                      handleCommonDate={handleCommonEndDate}
                    />
                  </Box>
                }
              >
                <Box sx={{ display: "flex", alignItems: "center" }}>
                  <Box
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      cursor: "pointer",
                    }}
                    onClick={() => setOpenEndDatePicker((prev) => !prev)}
                  >
                    <MuiTypography sx={{ ...textStyle }}>
                      {endTimeLabel}:
                    </MuiTypography>
                    <MuiTypography
                      sx={{
                        ...timeTextStyle,
                        background: openEndDatePicker ? "#E3F2FD" : "none",
                      }}
                    >
                      {toDisplayTime}
                    </MuiTypography>
                  </Box>
                  {endTimeStart || endTime ? (
                    <IconButton
                      size="small"
                      color="primary"
                      disableRipple
                      sx={{ padding: 0, height: "fit-content", minHeight: 0 }}
                      onClick={() => {
                        onEndFromChange("");
                        onEndToChange("");
                        setToDisplayTime("Select time range");
                      }}
                    >
                      <CloseOutlinedIcon
                        color="primary"
                        sx={{ fontSize: "11pt" }}
                      />
                    </IconButton>
                  ) : null}
                </Box>
              </CustomisedTooltip>
            </Box>
          </ClickAwayListener>
        ) : null}
      </Box>
    </Box>
  );
});
