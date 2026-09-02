import { Dispatch } from "react";
import { QueryDispatch, SetStateAction } from "react-router-use-location-state";
interface AdvanceSearchComponentProps {
    queryText: string;
    freeText: string;
    startTime: string;
    startTimeEnd: string;
    fromDisplayTime: string;
    endTimeStart: string;
    endTime: string;
    toDisplayTime: string;
    openDateSelect: boolean;
    openStartDatePicker: boolean;
    setStartOpenDatePicker: Dispatch<SetStateAction<boolean>>;
    setOpenDateSelect: Dispatch<SetStateAction<boolean>>;
    setToDisplayTime: Dispatch<SetStateAction<string>>;
    openEndDatePicker: boolean;
    setFreeText: QueryDispatch<SetStateAction<string>>;
    setQueryText: QueryDispatch<SetStateAction<string>>;
    setShowCodeDialog: QueryDispatch<SetStateAction<string>>;
    handleReset: () => void;
    doSearch: () => void;
    onStartFromChange: (val: string) => void;
    onStartToChange: (val: string) => void;
    onEndFromChange: (val: string) => void;
    onEndToChange: (val: string) => void;
    setFromDisplayTime: Dispatch<SetStateAction<string>>;
    setEndOpenDatePicker: Dispatch<SetStateAction<boolean>>;
    recentSearches: {
        start: string;
        end: string;
    };
}
export declare const AdvanceSearch: ({ queryText, freeText, startTime, endTime, setQueryText, onStartFromChange, onStartToChange, setFreeText, handleReset, doSearch, setShowCodeDialog, toDisplayTime, setToDisplayTime, setOpenDateSelect, setStartOpenDatePicker, startTimeEnd, openDateSelect, endTimeStart, openEndDatePicker, fromDisplayTime, openStartDatePicker, setFromDisplayTime, setEndOpenDatePicker, onEndFromChange, onEndToChange, recentSearches, }: AdvanceSearchComponentProps) => import("react").JSX.Element;
export {};
