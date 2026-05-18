import TuneIcon from "@mui/icons-material/Tune";
import { Box, Card } from "@mui/material";
import { Button } from "components";
import ResetIcon from "components/icons/ResetIcon";
import { RefObject } from "react";
import { DatePickerButton, DatePickerButtonHandle } from "./DatePickerButton";
import { StatusComboButton } from "./StatusComboButton";

export interface QuickFiltersCardProps {
  // Date picker refs (for cross-picker close coordination)
  startPickerRef: RefObject<DatePickerButtonHandle>;
  endPickerRef: RefObject<DatePickerButtonHandle>;
  // Status
  status: string[];
  onStatusChange: (val: string[]) => void;
  // Start time
  startTimeFrom: string;
  onStartFromChange: (val: string) => void;
  startTimeTo: string;
  onStartToChange: (val: string) => void;
  fromDisplayTime: string;
  setFromDisplayTime: (val: string) => void;
  // End time
  endTimeFrom: string;
  onEndFromChange: (val: string) => void;
  endTimeTo: string;
  onEndToChange: (val: string) => void;
  toDisplayTime: string;
  setToDisplayTime: (val: string) => void;
  // Shared
  recentSearches: { start: string; end: string };
  // Filters button
  advancedFilterCount: number;
  onOpenFilters: (anchor: HTMLElement) => void;
  // Reset
  onReset: () => void;
}

export function QuickFiltersCard({
  startPickerRef,
  endPickerRef,
  status,
  onStatusChange,
  startTimeFrom,
  onStartFromChange,
  startTimeTo,
  onStartToChange,
  fromDisplayTime,
  setFromDisplayTime,
  endTimeFrom,
  onEndFromChange,
  endTimeTo,
  onEndToChange,
  toDisplayTime,
  setToDisplayTime,
  recentSearches,
  advancedFilterCount,
  onOpenFilters,
  onReset,
}: QuickFiltersCardProps) {
  return (
    <Card>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          flexWrap: "wrap",
          gap: 1,
        }}
      >
        <StatusComboButton status={status} onStatusChange={onStatusChange} />

        <DatePickerButton
          ref={startPickerRef}
          onOpen={() => endPickerRef.current?.close()}
          startTime={startTimeFrom}
          onStartFromChange={onStartFromChange}
          startTimeEnd={startTimeTo}
          onStartToChange={onStartToChange}
          fromDisplayTime={fromDisplayTime}
          setFromDisplayTime={setFromDisplayTime}
          recentSearches={recentSearches}
          startTimeLabel="Start Time"
        />

        <DatePickerButton
          ref={endPickerRef}
          onOpen={() => startPickerRef.current?.close()}
          startTime={endTimeFrom}
          onStartFromChange={onEndFromChange}
          startTimeEnd={endTimeTo}
          onStartToChange={onEndToChange}
          fromDisplayTime={toDisplayTime}
          setFromDisplayTime={setToDisplayTime}
          recentSearches={recentSearches}
          startTimeLabel="End Time"
        />

        <Button
          variant="outlined"
          size="small"
          startIcon={<TuneIcon />}
          onClick={(e) => onOpenFilters(e.currentTarget)}
          sx={{
            textTransform: "none",
            height: 36,
            minWidth: "unset",
            fontSize: "13px",
            fontWeight: 500,
            letterSpacing: "normal",
            borderColor:
              advancedFilterCount > 0
                ? "primary.main"
                : (t) =>
                    t.palette.mode === "dark"
                      ? "rgba(255,255,255,0.23)"
                      : "rgba(0,0,0,0.23)",
            "&:hover": {
              borderColor:
                advancedFilterCount > 0 ? "primary.dark" : "primary.main",
              backgroundColor: "transparent",
            },
            "&&": {
              color: advancedFilterCount > 0 ? "primary.main" : "#060606",
            },
          }}
        >
          Filters{advancedFilterCount > 0 ? ` (${advancedFilterCount})` : ""}
        </Button>
      </Box>

      <Box
        sx={{
          display: "flex",
          justifyContent: "flex-end",
          alignItems: "center",
          pt: 2,
        }}
      >
        <Button
          id="reset-workflow-btn"
          variant="text"
          size="small"
          onClick={onReset}
          startIcon={<ResetIcon />}
          sx={{ textTransform: "none" }}
        >
          Reset All
        </Button>
      </Box>
    </Card>
  );
}
