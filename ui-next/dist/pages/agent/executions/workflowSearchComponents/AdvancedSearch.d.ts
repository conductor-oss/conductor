import { ReactNode } from "react";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
export interface AdvancedSearchProps {
    doSearch: ({ resultObj, queryFT, buildQuery, setQueryFT, refetch, setPage, setRecentTaskSearch, }: DoSearchProps) => void;
    SwitchComponent: ReactNode;
    getTableTitle: (resultObj: TaskExecutionResult) => ReactNode;
    freeText: string;
    setFreeText: (val: string) => void;
    status: string[];
    setStatus: (val: string[]) => void;
    startTimeFrom: string;
    setStartTimeFrom: (val: string) => void;
    onStartFromChange: (val: string) => void;
    startTimeTo: string;
    setStartTimeTo: (val: string) => void;
    onStartToChange: (val: string) => void;
    endTimeFrom: string;
    setEndTimeFrom: (val: string) => void;
    onEndFromChange: (val: string) => void;
    endTimeTo: string;
    setEndTimeTo: (val: string) => void;
    onEndToChange: (val: string) => void;
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
    recentSearches: {
        start: string;
        end: string;
    };
}
export default function AdvancedSearch({ doSearch, SwitchComponent, getTableTitle, freeText, setFreeText, status, setStatus, startTimeFrom, setStartTimeFrom, onStartFromChange, startTimeTo, setStartTimeTo, onStartToChange, endTimeFrom, setEndTimeFrom, onEndFromChange, endTimeTo, setEndTimeTo, onEndToChange, fromDisplayTime, setFromDisplayTime, toDisplayTime, setToDisplayTime, openDateSelect, setOpenDateSelect, openStartDatePicker, setStartOpenDatePicker, openEndDatePicker, setEndOpenDatePicker, recentSearches, }: AdvancedSearchProps): import("react").JSX.Element;
