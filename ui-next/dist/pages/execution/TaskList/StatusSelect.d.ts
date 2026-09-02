import { FunctionComponent } from "react";
import { SelectableStatus } from "./state";
interface StatusSelectProps {
    onSelect: (selection?: SelectableStatus[]) => void;
    value: any[];
    summary?: Record<SelectableStatus, number>;
}
export declare const StatusSelect: FunctionComponent<StatusSelectProps>;
export {};
