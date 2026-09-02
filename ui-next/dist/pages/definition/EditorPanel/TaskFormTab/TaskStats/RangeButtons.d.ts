import { FunctionComponent } from "react";
export interface RangeButtonsProps {
    onChangeRange: (from: number) => void;
    selected: number;
}
export declare const RangeButtons: FunctionComponent<RangeButtonsProps>;
