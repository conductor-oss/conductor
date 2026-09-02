import { ChangeEvent, FunctionComponent } from "react";
import { ConductorInputProps } from "components/ui/inputs/ConductorInput";
export type ConductorInputNumberProps = Omit<ConductorInputProps, "onChange" | "onBlur"> & {
    value: number | null;
    onChange: (val: number | null, event?: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void;
};
declare const ConductorInputNumber: FunctionComponent<ConductorInputNumberProps>;
export default ConductorInputNumber;
