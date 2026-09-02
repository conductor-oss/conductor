import { FunctionComponent } from "react";
import { SelectableOption } from "./types";
interface SubjectMultiPickerProps {
    multiple: boolean;
    options: SelectableOption[];
    onChange: (val: SelectableOption | SelectableOption[]) => void;
    label: string;
    value?: any;
    required?: boolean;
    growPopper?: boolean;
}
export declare const SubjectMultiPicker: FunctionComponent<SubjectMultiPickerProps>;
export {};
