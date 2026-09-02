import { DateTimePickerProps } from "@mui/x-date-pickers";
import React, { RefAttributes } from "react";
import { ConductorInputProps } from "components/ui/inputs/ConductorInput";
export type ConductorDateTimePickerProps<TDate> = DateTimePickerProps<TDate> & RefAttributes<HTMLDivElement> & {
    inputProps?: ConductorInputProps;
};
declare const ConductorDateTimePicker: React.ForwardRefExoticComponent<Omit<ConductorDateTimePickerProps<Date | null>, "ref"> & React.RefAttributes<HTMLInputElement>>;
export default ConductorDateTimePicker;
