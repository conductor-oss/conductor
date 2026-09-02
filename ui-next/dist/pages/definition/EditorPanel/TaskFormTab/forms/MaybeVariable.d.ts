import { FunctionComponent, ReactNode } from "react";
import { SxProps } from "@mui/material";
import { FormTaskType } from "types/TaskType";
interface MaybeVariableProps {
    value: string | any;
    onChange: (v: any) => void;
    path: string;
    taskType: FormTaskType;
    children?: ReactNode;
    helperTextStyle?: SxProps;
    fieldStyle?: SxProps;
}
export declare const MaybeVariable: FunctionComponent<MaybeVariableProps>;
export {};
