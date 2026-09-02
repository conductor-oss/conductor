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
    openDateSelect: boolean;
    setOpenDateSelect: (val: boolean) => void;
    openStartDatePicker: boolean;
    setStartOpenDatePicker: (val: boolean) => void;
    openEndDatePicker: boolean;
    setEndOpenDatePicker: (val: boolean) => void;
    disabled?: boolean;
    recentSearches: {
        start: string;
        end: string;
    };
    startTimeLabel?: string;
    endTimeLabel?: string;
    startDialogTitle?: string | null;
    startDialogHelpText?: string | null;
    endDialogTitle?: string | null;
    endDialogHelpText?: string | null;
}
export declare const DateControlComponent: ({ startTime, onStartFromChange, startTimeEnd, onStartToChange, endTimeStart, onEndFromChange, endTime, onEndToChange, fromDisplayTime, setFromDisplayTime, toDisplayTime, setToDisplayTime, setOpenDateSelect, openStartDatePicker, setStartOpenDatePicker, openEndDatePicker, setEndOpenDatePicker, startTimeLabel, endTimeLabel, startDialogTitle, startDialogHelpText, endDialogTitle, endDialogHelpText, }: DateControlComponentProps) => import("react").JSX.Element;
