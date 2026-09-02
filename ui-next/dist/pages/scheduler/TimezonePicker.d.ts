type TimezonePickerProps = {
    timezone: string;
    onChange: (newValue: any) => void;
    error: boolean;
    helperText: string;
};
export declare const TimezonePicker: ({ timezone, onChange, error, helperText, }: TimezonePickerProps) => import("react").JSX.Element;
export {};
