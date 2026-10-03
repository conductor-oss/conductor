import { DateFilter } from "components/features/executionSearch";
import { commonlyUsedDateTime, getSearchDateTime } from "utils/date";
import { featureFlags, FEATURES } from "utils/flags";
import { DatePickerComponent } from "./DatePickerComponent";
import { DatePickerPanelHeader } from "./DatePickerPanelHeader";

export interface ExecutionDateFilterState {
  /** Shown under the "Execution Start Time" heading in the Started panel. */
  startHelpText: string;
  /** Shown under the "Execution End Time" heading in the Ended panel. */
  endHelpText: string;
  startTimeFrom: string;
  startTimeTo: string;
  onStartFromChange: (val: string) => void;
  onStartToChange: (val: string) => void;
  fromDisplayTime: string;
  setFromDisplayTime: (val: string) => void;
  endTimeFrom: string;
  endTimeTo: string;
  onEndFromChange: (val: string) => void;
  onEndToChange: (val: string) => void;
  toDisplayTime: string;
  setToDisplayTime: (val: string) => void;
}

const isUnset = (display: string) =>
  !display || display === "Select time range";

/**
 * The Started pill, plus Ended when the end-time picker is enabled, each opening
 * the Presets / Absolute / Relative date picker.
 */
export const buildExecutionDateFilters = (
  state: ExecutionDateFilterState,
): DateFilter[] => {
  const filters: DateFilter[] = [
    {
      id: "date-picker-start-time",
      label: "Started",
      value: isUnset(state.fromDisplayTime)
        ? "Any time"
        : state.fromDisplayTime,
      renderPanel: (close) => (
        <DatePickerPanelHeader
          title="Execution Start Time"
          helpText={state.startHelpText}
        >
          <DatePickerComponent
            label="Start"
            startDateTime={state.startTimeFrom}
            endDateTime={state.startTimeTo}
            handleFrom={state.onStartFromChange}
            handleTo={state.onStartToChange}
            openPicker={(open) => !open && close()}
            setDisplayName={state.setFromDisplayTime}
            maxDate={true}
            handleCommonDate={(time) => {
              const { rangeStart, rangeEnd } = commonlyUsedDateTime(time);
              state.setFromDisplayTime(getSearchDateTime(rangeStart, rangeEnd));
              state.onStartFromChange(rangeStart);
              state.onStartToChange(rangeEnd);
            }}
          />
        </DatePickerPanelHeader>
      ),
    },
  ];

  if (featureFlags.isEnabled(FEATURES.SHOW_END_TIME_IN_DATEPICKER)) {
    filters.push({
      id: "date-picker-end-time",
      label: "Ended",
      value:
        state.endTimeFrom || state.endTimeTo ? state.toDisplayTime : "Any time",
      renderPanel: (close) => (
        <DatePickerPanelHeader
          title="Execution End Time"
          helpText={state.endHelpText}
        >
          <DatePickerComponent
            label="End"
            startDateTime={state.endTimeFrom}
            endDateTime={state.endTimeTo}
            handleFrom={state.onEndFromChange}
            handleTo={state.onEndToChange}
            openPicker={(open) => !open && close()}
            setDisplayName={state.setToDisplayTime}
            maxDate={false}
            handleCommonDate={(time) => {
              const { rangeStart, rangeEnd } = commonlyUsedDateTime(time);
              state.setToDisplayTime(getSearchDateTime(rangeStart, rangeEnd));
              state.onEndFromChange(rangeStart);
              state.onEndToChange(rangeEnd);
            }}
          />
        </DatePickerPanelHeader>
      ),
    });
  }

  return filters;
};
