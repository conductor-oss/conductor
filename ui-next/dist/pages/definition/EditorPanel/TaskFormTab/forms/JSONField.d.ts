import { FunctionComponent } from "react";
export interface JSONFieldProps {
    path: string;
    onChange?: (value: any) => void;
    taskJson: any;
    checked?: boolean;
    children: any;
    enableCastToBoolean?: boolean;
}
declare const JSONField: FunctionComponent<JSONFieldProps>;
export default JSONField;
