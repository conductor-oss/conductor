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
export declare const DatePickerComponent: ({ label, startDateTime, endDateTime, handleFrom, handleTo, openPicker, setDisplayName, maxDate, handleCommonDate, }: DatePickerProps) => import("react").JSX.Element;
