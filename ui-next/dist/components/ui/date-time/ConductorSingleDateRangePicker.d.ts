import "react-datepicker/dist/react-datepicker.css";
import "./CustomDateRangePicker.scss";
export interface SingleDateRangePickerProps {
    fromDate: string;
    toDate: string;
    setStartTime: (data: string) => void;
    setEndTime: (data: string) => void;
    maxDate?: boolean;
}
export declare const SingleDateRangePicker: ({ fromDate, toDate, setStartTime, setEndTime, maxDate, }: SingleDateRangePickerProps) => import("react").JSX.Element;
