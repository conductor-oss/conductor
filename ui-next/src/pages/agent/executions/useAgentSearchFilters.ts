import { useState } from "react";
import { useQueryState } from "react-router-use-location-state";
import {
  commonlyUsedDateTime,
  dateToEpoch,
  getSearchDateTime,
} from "utils/date";
import { tryToJson } from "utils/utils";
import type { DateControlComponentProps } from "./DateControlComponent";

/** Shared Basic/Advanced search filter state owned by AgentSearch. */
export type AgentSearchFilters = {
  freeText: string;
  setFreeText: (val: string) => void;
  status: string[];
  setStatus: (val: string[]) => void;
  startTimeFrom: string;
  setStartTimeFrom: (val: string) => void;
  startTimeTo: string;
  setStartTimeTo: (val: string) => void;
  endTimeFrom: string;
  setEndTimeFrom: (val: string) => void;
  endTimeTo: string;
  setEndTimeTo: (val: string) => void;
  fromDisplayTime: string;
  setFromDisplayTime: (val: string) => void;
  toDisplayTime: string;
  setToDisplayTime: (val: string) => void;
  openDateSelect: boolean;
  setOpenDateSelect: (val: boolean) => void;
  openStartDatePicker: boolean;
  setStartOpenDatePicker: (val: boolean) => void;
  openEndDatePicker: boolean;
  setEndOpenDatePicker: (val: boolean) => void;
  onStartFromChange: (val: string) => void;
  onStartToChange: (val: string) => void;
  onEndFromChange: (val: string) => void;
  onEndToChange: (val: string) => void;
  recentSearches: { start: string; end: string };
};

/** Map shared filters onto DateControlComponent's prop names. */
export function toDateControlProps(
  filters: AgentSearchFilters,
): Pick<
  DateControlComponentProps,
  | "startTime"
  | "onStartFromChange"
  | "startTimeEnd"
  | "onStartToChange"
  | "endTimeStart"
  | "onEndFromChange"
  | "endTime"
  | "onEndToChange"
  | "fromDisplayTime"
  | "setFromDisplayTime"
  | "toDisplayTime"
  | "setToDisplayTime"
  | "openDateSelect"
  | "setOpenDateSelect"
  | "openStartDatePicker"
  | "setStartOpenDatePicker"
  | "openEndDatePicker"
  | "setEndOpenDatePicker"
  | "recentSearches"
> {
  return {
    startTime: filters.startTimeFrom,
    onStartFromChange: filters.onStartFromChange,
    startTimeEnd: filters.startTimeTo,
    onStartToChange: filters.onStartToChange,
    endTimeStart: filters.endTimeFrom,
    onEndFromChange: filters.onEndFromChange,
    endTime: filters.endTimeTo,
    onEndToChange: filters.onEndToChange,
    fromDisplayTime: filters.fromDisplayTime,
    setFromDisplayTime: filters.setFromDisplayTime,
    toDisplayTime: filters.toDisplayTime,
    setToDisplayTime: filters.setToDisplayTime,
    openDateSelect: filters.openDateSelect,
    setOpenDateSelect: filters.setOpenDateSelect,
    openStartDatePicker: filters.openStartDatePicker,
    setStartOpenDatePicker: filters.setStartOpenDatePicker,
    openEndDatePicker: filters.openEndDatePicker,
    setEndOpenDatePicker: filters.setEndOpenDatePicker,
    recentSearches: filters.recentSearches,
  };
}

/** URL + local state shared by Basic and Advanced agent execution search. */
export function useAgentSearchFilters(): AgentSearchFilters {
  const [freeText, setFreeText] = useQueryState("freeText", "");
  const [status, setStatus] = useQueryState<string[]>("status", []);
  const [openDateSelect, setOpenDateSelect] = useState(false);
  const [openStartDatePicker, setStartOpenDatePicker] = useState(false);
  const [openEndDatePicker, setEndOpenDatePicker] = useState(false);
  const [startTimeFrom, setStartTimeFrom] = useQueryState(
    "startFrom",
    // Default lives here — no mount effect needed to backfill last-72h.
    commonlyUsedDateTime("last72Hours").rangeStart,
  );
  const [startTimeTo, setStartTimeTo] = useQueryState("startTo", "");
  const [endTimeFrom, setEndTimeFrom] = useQueryState("endTimeFrom", "");
  const [endTimeTo, setEndTimeTo] = useQueryState("endTimeTo", "");
  const [fromDisplayTime, setFromDisplayTime] = useState(
    startTimeFrom
      ? getSearchDateTime(startTimeFrom, startTimeTo)
      : "Last 72 Hours",
  );
  const [toDisplayTime, setToDisplayTime] = useState(
    endTimeTo ? getSearchDateTime(endTimeFrom, endTimeTo) : "Select time range",
  );

  const recentSearches =
    (tryToJson(localStorage.getItem("recentTaskSearch")) as {
      start: string;
      end: string;
    }) || {};

  const onStartFromChange = (val: string) => {
    setStartTimeFrom(val ? String(dateToEpoch(val)) : "");
  };
  const onStartToChange = (val: string) => {
    setStartTimeTo(val ? String(dateToEpoch(val)) : "");
  };
  const onEndFromChange = (val: string) => {
    setEndTimeFrom(val ? String(dateToEpoch(val)) : "");
  };
  const onEndToChange = (val: string) => {
    setEndTimeTo(val ? String(dateToEpoch(val)) : "");
  };

  return {
    freeText,
    setFreeText,
    status,
    setStatus,
    startTimeFrom,
    setStartTimeFrom,
    startTimeTo,
    setStartTimeTo,
    endTimeFrom,
    setEndTimeFrom,
    endTimeTo,
    setEndTimeTo,
    fromDisplayTime,
    setFromDisplayTime,
    toDisplayTime,
    setToDisplayTime,
    openDateSelect,
    setOpenDateSelect,
    openStartDatePicker,
    setStartOpenDatePicker,
    openEndDatePicker,
    setEndOpenDatePicker,
    onStartFromChange,
    onStartToChange,
    onEndFromChange,
    onEndToChange,
    recentSearches,
  };
}
